/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.http.client.dispatcher;

import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.NonNull;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import okhttp3.HttpUrl;

import java.util.Optional;
import java.util.Set;

/**
 * Test dispatcher serving the redirect shapes {@code HttpHandler}'s validated follow loop is
 * exercised against, plus a terminal route that echoes what the final hop actually received.
 * <p>
 * The follow loop lives in cui-http, not in the JDK client: every hop is revalidated against the
 * handler's {@code RedirectPolicy} before it is requested, and the underlying {@code HttpClient}
 * keeps {@code Redirect.NEVER} throughout. These routes cover both sides of that contract — the hops
 * that are followed and rewritten, and the hops that are refused.
 *
 * <h3>Routes</h3>
 * <ul>
 *   <li>{@value #PATH_REDIRECT} — 302 pointing at {@value #PATH_TARGET}</li>
 *   <li>{@value #PATH_TARGET} — 200 whose body <em>and</em> {@value #HEADER_ECHO} header describe the
 *       request that reached it; see {@link #echo(RecordedRequest)}</li>
 *   <li>{@value #PATH_STATUS_PREFIX}{@code {code}} — the named followable status (301, 302, 303, 307,
 *       308) pointing at {@value #PATH_TARGET}, used to pin the per-status method/body rewrite rules</li>
 *   <li>{@value #PATH_CROSS_HOST} — 302 whose {@code Location} names an off-server host</li>
 *   <li>{@value #PATH_CROSS_PORT} — 302 whose {@code Location} keeps the host but changes the port</li>
 *   <li>{@value #PATH_CROSS_SCHEME} — 302 whose {@code Location} keeps host and port but switches to
 *       {@code https}</li>
 *   <li>{@value #PATH_ALLOWLISTED} — 302 whose {@code Location} names the same server under the
 *       {@value #ALLOWLISTED_HOST} alias, so an allowlisted cross-origin hop can be followed for real</li>
 *   <li>{@value #PATH_CHAIN} — 302 pointing at itself, so any hop bound can be exhausted</li>
 *   <li>{@value #PATH_RELATIVE} — 302 whose {@code Location} is the relative reference
 *       {@value #RELATIVE_LOCATION}</li>
 *   <li>{@value #PATH_STREAMING_REDIRECT} — 302 pointing at {@value #PATH_TARGET} that carries a
 *       {@value #REDIRECT_HOP_BODY_SIZE}-byte body of its own, so a leaked intermediate body is
 *       observable</li>
 *   <li>{@value #PATH_NO_LOCATION} — 302 with no {@code Location} header at all, answering
 *       {@value #UNFOLLOWED_BODY} as its (terminal) body</li>
 *   <li>{@value #PATH_MALFORMED_LOCATION} — 302 whose {@code Location} is the unparseable
 *       {@value #MALFORMED_LOCATION}</li>
 *   <li>{@value #PATH_MALFORMED_LOCATION_OVERLONG} — 302 whose {@code Location} is unparseable
 *       and exceeds the sanitizer's retained-length bound</li>
 * </ul>
 * The dispatcher is stateless; tests read the terminal echo (or the client's own record of the
 * exchange) rather than server-side counters, because the dispatcher resolver serves requests from
 * its own instance rather than the one held by the test.
 *
 * @author Oliver Wolff
 */
public class RedirectDispatcher implements ModuleDispatcherElement {

    /** Base path this dispatcher claims. */
    public static final String BASE_PATH = "/redirect";

    /** Redirect source answering 302. */
    public static final String PATH_REDIRECT = BASE_PATH + "/start";

    /** Terminal path that answers 200 and echoes the request that reached it. */
    public static final String PATH_TARGET = BASE_PATH + "/target";

    /** Prefix of the per-status redirect routes; append the status code (e.g. {@code /redirect/status/303}). */
    public static final String PATH_STATUS_PREFIX = BASE_PATH + "/status/";

    /** 302 whose {@code Location} names a host this server does not serve. */
    public static final String PATH_CROSS_HOST = BASE_PATH + "/cross-host";

    /** 302 whose {@code Location} keeps the host but names a different port. */
    public static final String PATH_CROSS_PORT = BASE_PATH + "/cross-port";

    /** 302 whose {@code Location} keeps host and port but switches the scheme to {@code https}. */
    public static final String PATH_CROSS_SCHEME = BASE_PATH + "/cross-scheme";

    /** 302 whose {@code Location} names this same server under {@value #ALLOWLISTED_HOST}. */
    public static final String PATH_ALLOWLISTED = BASE_PATH + "/allowlisted";

    /** 302 pointing at itself, so any configured hop bound can be exhausted. */
    public static final String PATH_CHAIN = BASE_PATH + "/chain";

    /** 302 whose {@code Location} is the relative reference {@value #RELATIVE_LOCATION}. */
    public static final String PATH_RELATIVE = BASE_PATH + "/relative";

    /** 302 carrying no {@code Location} header. */
    public static final String PATH_NO_LOCATION = BASE_PATH + "/no-location";

    /**
     * The body served by {@value #PATH_NO_LOCATION}. That response is <em>terminal</em> (a followable
     * status without a usable {@code Location} names no target), so this body must reach the caller's
     * own body handler — it is the negative control for the intermediate-hop discard.
     */
    public static final String UNFOLLOWED_BODY = "unfollowed-redirect-body";

    /**
     * 302 pointing at {@value #PATH_TARGET} whose own body is {@value #REDIRECT_HOP_BODY_SIZE} bytes
     * of filler. Distinct from {@value #PATH_REDIRECT}, whose body is empty: a redirect that carries
     * a real body is what makes a leaked intermediate stream observable, since an empty one is
     * indistinguishable from a drained one.
     */
    public static final String PATH_STREAMING_REDIRECT = BASE_PATH + "/streaming";

    /** Size of the filler body served by {@value #PATH_STREAMING_REDIRECT}. */
    public static final int REDIRECT_HOP_BODY_SIZE = 4096;

    /** The filler body served by {@value #PATH_STREAMING_REDIRECT}. */
    public static final String REDIRECT_HOP_BODY = "x".repeat(REDIRECT_HOP_BODY_SIZE);

    /** 302 whose {@code Location} is present and non-blank but not a parseable URI reference. */
    public static final String PATH_MALFORMED_LOCATION = BASE_PATH + "/malformed-location";

    /**
     * The unparseable {@code Location} served by {@value #PATH_MALFORMED_LOCATION}. The literal space
     * in the authority is illegal in a URI reference, so {@code URI.create} rejects it — while
     * remaining a legal HTTP header value, so it survives the wire and reaches the follow loop.
     */
    public static final String MALFORMED_LOCATION = "http://ex ample.org/target";

    /** 302 whose {@code Location} is unparseable and exceeds the sanitizer's retained-length bound. */
    public static final String PATH_MALFORMED_LOCATION_OVERLONG = BASE_PATH + "/malformed-location-overlong";

    /**
     * The unparseable {@code Location} served by {@value #PATH_MALFORMED_LOCATION_OVERLONG}: an
     * embedded space makes it unparseable (the same technique as {@value #MALFORMED_LOCATION}), and
     * its length exceeds the sanitizer's retained-length bound so truncation is exercised too.
     */
    public static final String MALFORMED_LOCATION_OVERLONG = "http://" + "a".repeat(500) + " space/target";

    /** Host alias naming this same MockWebServer instance; allowlist it to follow a real cross-origin hop. */
    public static final String ALLOWLISTED_HOST = "127.0.0.1";

    /** Host this server never serves; a hop to it must be refused before any request is issued. */
    public static final String OFF_SERVER_HOST = "off-server.example.org";

    /** The relative {@code Location} served by {@value #PATH_RELATIVE}; resolves to {@value #PATH_TARGET}. */
    public static final String RELATIVE_LOCATION = "target";

    /** Leading token of the terminal echo, kept stable so a "the target was reached" assertion needs no parsing. */
    public static final String TARGET_BODY = "redirect-target-reached";

    /** Response header carrying the same echo as the body, so a HEAD response is assertable too. */
    public static final String HEADER_ECHO = "X-Echo";

    @Override
    public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
        return handle(request);
    }

    @Override
    public Optional<MockResponse> handleHead(@NonNull RecordedRequest request) {
        return handle(request);
    }

    @Override
    public Optional<MockResponse> handlePost(@NonNull RecordedRequest request) {
        return handle(request);
    }

    /**
     * Describes the request that reached the terminal hop, as a {@code ;}-separated list of
     * {@code key=value} fields: {@code method}, {@code body} ({@code absent} or the byte count),
     * {@code contentType}, {@code contentLength}, {@code authorization} and {@code cookie} (each
     * {@code present} or {@code absent}). The leading token is {@value #TARGET_BODY}.
     *
     * @param request the request that reached {@value #PATH_TARGET}
     * @return the echo string, served both as the body and as the {@value #HEADER_ECHO} header
     */
    public static String echo(RecordedRequest request) {
        long bodySize = request.getBodySize();
        return TARGET_BODY
                + ";method=" + request.getMethod()
                + ";body=" + (bodySize == 0 ? "absent" : Long.toString(bodySize))
                + ";contentType=" + presence(request, "Content-Type")
                + ";contentLength=" + presence(request, "Content-Length")
                + ";authorization=" + presence(request, "Authorization")
                + ";cookie=" + presence(request, "Cookie");
    }

    private static String presence(RecordedRequest request, String header) {
        return request.getHeaders().get(header) != null ? "present" : "absent";
    }

    private Optional<MockResponse> handle(RecordedRequest request) {
        String path = request.getUrl().encodedPath();
        if (path.startsWith(PATH_STATUS_PREFIX)) {
            return parseStatus(path.substring(PATH_STATUS_PREFIX.length()))
                    .map(status -> redirect(status, absolute(request, PATH_TARGET)));
        }
        return Optional.ofNullable(switch (path) {
            case PATH_REDIRECT -> redirect(302, absolute(request, PATH_TARGET));
            case PATH_TARGET -> terminal(request);
            case PATH_CROSS_HOST -> redirect(302, request.getUrl().newBuilder()
                    .host(OFF_SERVER_HOST).encodedPath(PATH_TARGET).build().toString());
            case PATH_CROSS_PORT -> redirect(302, request.getUrl().newBuilder()
                    .port(otherPort(request.getUrl())).encodedPath(PATH_TARGET).build().toString());
            case PATH_CROSS_SCHEME -> redirect(302, request.getUrl().newBuilder()
                    .scheme("https").encodedPath(PATH_TARGET).build().toString());
            case PATH_ALLOWLISTED -> redirect(302, request.getUrl().newBuilder()
                    .host(ALLOWLISTED_HOST).encodedPath(PATH_TARGET).build().toString());
            case PATH_CHAIN -> redirect(302, absolute(request, PATH_CHAIN));
            case PATH_RELATIVE -> redirect(302, RELATIVE_LOCATION);
            case PATH_STREAMING_REDIRECT -> redirect(302, absolute(request, PATH_TARGET), REDIRECT_HOP_BODY);
            case PATH_NO_LOCATION -> new MockResponse(302, Headers.of(), UNFOLLOWED_BODY);
            case PATH_MALFORMED_LOCATION -> redirect(302, MALFORMED_LOCATION);
            case PATH_MALFORMED_LOCATION_OVERLONG -> redirect(302, MALFORMED_LOCATION_OVERLONG);
            default -> null;
        });
    }

    private static Optional<Integer> parseStatus(String segment) {
        return switch (segment) {
            case "301" -> Optional.of(301);
            case "302" -> Optional.of(302);
            case "303" -> Optional.of(303);
            case "307" -> Optional.of(307);
            case "308" -> Optional.of(308);
            default -> Optional.empty();
        };
    }

    private static MockResponse terminal(RecordedRequest request) {
        String echo = echo(request);
        return new MockResponse(200, new Headers.Builder()
                .add("Content-Type", "text/plain")
                .add(HEADER_ECHO, echo)
                .build(), echo);
    }

    private static MockResponse redirect(int status, String location) {
        return redirect(status, location, "");
    }

    private static MockResponse redirect(int status, String location, String body) {
        return new MockResponse(status, new Headers.Builder()
                .add("Location", location)
                .add("Content-Type", "text/plain")
                .build(), body);
    }

    /** A port this server does not listen on, so a cross-port hop is refused before it is attempted. */
    private static int otherPort(HttpUrl url) {
        return url.port() == 1 ? 2 : url.port() - 1;
    }

    private static String absolute(RecordedRequest request, String path) {
        return request.getUrl().newBuilder().encodedPath(path).build().toString();
    }

    @Override
    public String getBaseUrl() {
        return BASE_PATH;
    }

    @Override
    public @NonNull Set<HttpMethodMapper> supportedMethods() {
        return Set.of(HttpMethodMapper.GET, HttpMethodMapper.HEAD, HttpMethodMapper.POST);
    }
}

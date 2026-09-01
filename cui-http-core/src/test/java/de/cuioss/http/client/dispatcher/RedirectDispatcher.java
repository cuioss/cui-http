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

import java.util.Optional;
import java.util.Set;

/**
 * Test dispatcher serving a 302 with a {@code Location} header under {@value #BASE_PATH}, used to
 * verify that {@code HttpHandler} does <em>not</em> follow redirects — it configures no redirect
 * policy, so the JDK default {@code Redirect.NEVER} applies.
 * <p>
 * Routes:
 * <ul>
 *   <li>{@value #PATH_REDIRECT} — 302 with {@code Location} pointing at {@value #PATH_TARGET}</li>
 *   <li>{@value #PATH_TARGET} — 200 with body {@value #TARGET_BODY}</li>
 * </ul>
 * {@value #PATH_TARGET} exists so the {@code Location} header names a route that would in fact
 * answer: a test asserting the redirect was not followed is only meaningful if following it could
 * have succeeded. The dispatcher is stateless; tests read the client's own record of the exchange
 * ({@code HttpResponse.previousResponse()}) rather than server-side counters, because the dispatcher
 * resolver serves requests from its own instance rather than the one held by the test.
 *
 * @author Oliver Wolff
 */
public class RedirectDispatcher implements ModuleDispatcherElement {

    /** Base path this dispatcher claims. */
    public static final String BASE_PATH = "/redirect";

    /** Redirect source answering 302. */
    public static final String PATH_REDIRECT = BASE_PATH + "/start";

    /** Terminal path that answers 200; the {@code Location} target that is never fetched. */
    public static final String PATH_TARGET = BASE_PATH + "/target";

    /** Body served by {@value #PATH_TARGET}. */
    public static final String TARGET_BODY = "redirect-target-reached";

    @Override
    public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
        return handle(request);
    }

    @Override
    public Optional<MockResponse> handleHead(@NonNull RecordedRequest request) {
        return handle(request);
    }

    private Optional<MockResponse> handle(RecordedRequest request) {
        String path = request.getUrl().encodedPath();
        return switch (path) {
            case PATH_REDIRECT -> Optional.of(redirectTo(absolute(request, PATH_TARGET)));
            case PATH_TARGET -> Optional.of(new MockResponse(200,
                    new Headers.Builder().add("Content-Type", "text/plain").build(), TARGET_BODY));
            default -> Optional.empty();
        };
    }

    private static MockResponse redirectTo(String location) {
        return new MockResponse(302, new Headers.Builder()
                .add("Location", location)
                .add("Content-Type", "text/plain")
                .build(), "");
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
        return Set.of(HttpMethodMapper.GET, HttpMethodMapper.HEAD);
    }
}

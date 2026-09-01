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
package de.cuioss.http.client.handler;

import de.cuioss.http.client.dispatcher.RedirectDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.NonNull;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for {@link HttpHandler}'s bounded, revalidating redirect loop.
 * <p>
 * {@link HttpHandler#send(HttpRequest, HttpResponse.BodyHandler)} and
 * {@link HttpHandler#sendAsync(HttpRequest, HttpResponse.BodyHandler)} follow hops in cui-http code,
 * revalidating each target against the handler's {@link RedirectPolicy} before it is requested. The
 * underlying JDK client keeps {@code Redirect.NEVER} throughout — that separation is asserted in
 * {@code HttpHandlerIntegrationTest}; here the concern is which hops the loop takes, how it rewrites
 * them, and which it refuses.
 * <p>
 * <strong>Known end-to-end gap.</strong> The HTTPS&nbsp;&rarr;&nbsp;HTTP downgrade refusal
 * ({@link RedirectPolicy.RedirectRefusal#PROTOCOL_DOWNGRADE}) is asserted at the
 * {@link RedirectPolicy} validation seam in {@code RedirectPolicyTest} rather than end-to-end here:
 * driving it would need a second, TLS-terminated MockWebServer instance issuing a cleartext
 * {@code Location}, which the single-instance {@code @EnableMockWebServer} fixture does not offer.
 * The seam-level assertion pins the verdict; the transport-level path is unasserted. Recorded per
 * lesson 2026-08-29-12-001.
 *
 * @author Oliver Wolff
 * @since 2.2
 */
@EnableTestLogger
@EnableMockWebServer(useHttps = false)
@DisplayName("HttpHandler Redirect Following Tests")
class HttpHandlerRedirectTest {

    private static final String POST_BODY = "{\"payload\":\"value\"}";
    private static final String AUTHORIZATION_VALUE = "Bearer test-token";
    private static final String COOKIE_VALUE = "session=abc";

    private final RedirectDispatcher redirectDispatcher = new RedirectDispatcher();

    private final NotModifiedDispatcher notModifiedDispatcher = new NotModifiedDispatcher();

    public ModuleDispatcherElement getRedirectDispatcher() {
        return redirectDispatcher;
    }

    public ModuleDispatcherElement getNotModifiedDispatcher() {
        return notModifiedDispatcher;
    }


    @Test
    @DisplayName("send should follow a same-origin 302 and return the terminal 200")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sendShouldFollowSameOriginRedirect(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_REDIRECT)) {
            HttpResponse<String> response = get(handler);

            assertEquals(200, response.statusCode(), "the terminal hop's status must reach the caller");
            assertTrue(response.body().startsWith(RedirectDispatcher.TARGET_BODY),
                    "the terminal hop's body must reach the caller");
            assertTrue(response.uri().getPath().endsWith(RedirectDispatcher.PATH_TARGET),
                    "the terminal response must come from the redirect target");
        }
    }

    @Test
    @DisplayName("sendAsync should follow the same hop and complete with the same terminal response")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sendAsyncShouldFollowSameOriginRedirect(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_REDIRECT)) {
            HttpResponse<String> response = handler.sendAsync(handler.requestBuilder().GET().build(),
                    HttpResponse.BodyHandlers.ofString()).get();

            assertEquals(200, response.statusCode(), "the async path must follow the same hop");
            assertTrue(response.body().startsWith(RedirectDispatcher.TARGET_BODY),
                    "the async path must return the terminal body");
        }
    }

    @Test
    @DisplayName("pingGet should report SUCCESS for a followed redirect, not REDIRECTION")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void pingGetShouldReportTerminalStatus(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_REDIRECT)) {
            assertEquals(HttpStatusFamily.SUCCESS, handler.pingGet(),
                    "the ping send site is routed through the follow loop");
        }
    }

    @Test
    @DisplayName("pingHead should report SUCCESS for a followed redirect, not REDIRECTION")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void pingHeadShouldReportTerminalStatus(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_REDIRECT)) {
            assertEquals(HttpStatusFamily.SUCCESS, handler.pingHead(),
                    "the ping send site is routed through the follow loop");
        }
    }

    @Test
    @DisplayName("A relative Location should resolve against the in-flight hop")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void relativeLocationShouldResolveAgainstTheInFlightHop(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_RELATIVE)) {
            HttpResponse<String> response = get(handler);

            assertEquals(200, response.statusCode(), "a relative Location names a followable target");
            assertTrue(response.uri().getPath().endsWith(RedirectDispatcher.PATH_TARGET),
                    "the relative reference must resolve against the hop that produced it");
        }
    }

    @Test
    @DisplayName("An allowlisted cross-origin hop should be followed")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void allowlistedCrossOriginHopShouldBeFollowed(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = allowlistingHandlerFor(uriBuilder, RedirectDispatcher.PATH_ALLOWLISTED,
                     RedirectPolicy.CredentialForwarding.STRIP_ON_CROSS_ORIGIN)) {
            HttpResponse<String> response = get(handler);

            assertEquals(200, response.statusCode(), "an allowlisted host is a permitted hop target");
            assertEquals(RedirectDispatcher.ALLOWLISTED_HOST, response.uri().getHost(),
                    "the terminal response must come from the allowlisted host");
        }
    }


    @Test
    @DisplayName("A 304 should surface verbatim with no follow attempted")
    @ModuleDispatcher(providerMethod = "getNotModifiedDispatcher")
    void notModifiedShouldSurfaceVerbatim(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, NotModifiedDispatcher.PATH_NOT_MODIFIED)) {
            HttpResponse<String> response = get(handler);

            assertEquals(304, response.statusCode(), "304 is not a followable status");
            assertTrue(response.uri().getPath().endsWith(NotModifiedDispatcher.PATH_NOT_MODIFIED),
                    "no hop may have been walked");
        }
    }

    @Test
    @DisplayName("A 302 with no Location header should surface verbatim")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void redirectWithoutLocationShouldSurfaceVerbatim(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_NO_LOCATION)) {
            HttpResponse<String> response = get(handler);

            assertEquals(302, response.statusCode(), "without a Location there is no target to validate");
            assertTrue(response.uri().getPath().endsWith(RedirectDispatcher.PATH_NO_LOCATION),
                    "no hop may have been walked");
        }
    }

    @Test
    @DisplayName("A 302 with a blank Location header should surface verbatim")
    @ModuleDispatcher(providerMethod = "getNotModifiedDispatcher")
    void redirectWithBlankLocationShouldSurfaceVerbatim(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, NotModifiedDispatcher.PATH_BLANK_LOCATION)) {
            HttpResponse<String> response = get(handler);

            assertEquals(302, response.statusCode(), "a blank Location names no target either");
            assertTrue(response.uri().getPath().endsWith(NotModifiedDispatcher.PATH_BLANK_LOCATION),
                    "no hop may have been walked");
        }
    }


    @ParameterizedTest
    @ValueSource(strings = {RedirectDispatcher.PATH_CROSS_HOST, RedirectDispatcher.PATH_CROSS_PORT,
            RedirectDispatcher.PATH_CROSS_SCHEME})
    @DisplayName("A cross-origin hop should be refused with CROSS_ORIGIN under the same-origin default")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void crossOriginHopsShouldBeRefused(String path, URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, path)) {
            RedirectNotAllowedException thrown = assertThrows(RedirectNotAllowedException.class,
                    () -> get(handler), "the same-origin default must refuse this hop");

            assertEquals(RedirectPolicy.RedirectRefusal.CROSS_ORIGIN, thrown.getReason());
        }
    }

    @Test
    @DisplayName("A refused cross-host hop must never contact the off-server host")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void refusedCrossHostHopShouldNotContactTheTarget(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_CROSS_HOST)) {
            RedirectNotAllowedException thrown = assertThrows(RedirectNotAllowedException.class,
                    () -> get(handler));

            // A refusal (rather than an I/O failure) is the proof: the request was never issued,
            // so no name resolution or connection to the off-server host was ever attempted.
            assertEquals(RedirectPolicy.RedirectRefusal.CROSS_ORIGIN, thrown.getReason());
            assertEquals(RedirectDispatcher.OFF_SERVER_HOST,
                    Optional.ofNullable(thrown.getTo()).map(URI::getHost).orElse(null),
                    "the refusal must name the target it refused");
        }
    }

    @Test
    @DisplayName("Exhausting the hop bound should raise TOO_MANY_HOPS and log the HTTP-117 WARN")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void hopBoundShouldBeEnforced(URIBuilder uriBuilder) {
        try (HttpHandler handler = HttpHandler.builder()
                     .uri(targetUri(uriBuilder, RedirectDispatcher.PATH_CHAIN))
                     .allowInsecureHttp(true)
                     .redirectPolicy(RedirectPolicy.builder().maxHops(2).build())
                     .build()) {
            RedirectNotAllowedException thrown = assertThrows(RedirectNotAllowedException.class,
                    () -> get(handler), "a self-looping chain must exhaust the bound");

            assertEquals(RedirectPolicy.RedirectRefusal.TOO_MANY_HOPS, thrown.getReason());
            assertNull(thrown.getTo(), "no single target is at fault for an exhausted budget");

            assertEquals(HttpStatusFamily.UNKNOWN, handler.pingGet(),
                    "the same exhaustion leaves a ping without a usable status");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "HTTP-117");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    RedirectPolicy.RedirectRefusal.TOO_MANY_HOPS.name());
        }
    }

    @Test
    @DisplayName("A refused hop on the ping path should log the HTTP-117 WARN and report UNKNOWN")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void refusedHopShouldLogRedirectRefusedWarning(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_CROSS_HOST)) {
            assertEquals(HttpStatusFamily.UNKNOWN, handler.pingGet(),
                    "a refused hop leaves the ping without a usable status");

            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "HTTP-117");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, RedirectDispatcher.OFF_SERVER_HOST);
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    RedirectPolicy.RedirectRefusal.CROSS_ORIGIN.name());
        }
    }


    @ParameterizedTest
    @CsvSource({"301", "302", "303"})
    @DisplayName("A body-carrying POST should arrive as a bodyless GET with both body headers dropped")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void postShouldBeRewrittenToGet(int status, URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_STATUS_PREFIX + status)) {
            String echo = echoOf(handler.send(postRequest(handler), HttpResponse.BodyHandlers.ofString()));

            assertEquals("GET", field(echo, "method"), status + " must rewrite the method to GET");
            assertEquals("absent", field(echo, "body"), status + " must drop the request body");
            assertEquals("absent", field(echo, "contentType"), "a dropped body drops Content-Type with it");
            assertEquals("absent", field(echo, "contentLength"), "a dropped body drops Content-Length with it");
        }
    }

    @ParameterizedTest
    @CsvSource({"307", "308"})
    @DisplayName("A body-carrying POST should be replayed verbatim with the body and Content-Type intact")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void postShouldBeReplayedVerbatim(int status, URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_STATUS_PREFIX + status)) {
            String echo = echoOf(handler.send(postRequest(handler), HttpResponse.BodyHandlers.ofString()));

            assertEquals("POST", field(echo, "method"), status + " must preserve the method");
            assertEquals(Integer.toString(POST_BODY.length()), field(echo, "body"),
                    status + " must replay the original body");
            assertEquals("present", field(echo, "contentType"), "a preserved body keeps Content-Type");
        }
    }

    @ParameterizedTest
    @CsvSource({"301,GET", "302,GET", "301,HEAD", "302,HEAD"})
    @DisplayName("A bodyless method should be preserved across 301 and 302")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void bodylessMethodShouldBePreserved(int status, String method, URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_STATUS_PREFIX + status)) {
            HttpRequest request = handler.requestBuilder()
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();

            String echo = echoOf(handler.send(request, HttpResponse.BodyHandlers.ofString()));

            assertEquals(method, field(echo, "method"), status + " must preserve a bodyless method");
        }
    }


    @Test
    @DisplayName("STRIP_ON_CROSS_ORIGIN should strip Authorization and Cookie on an allowlisted cross-origin hop")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void stripStrategyShouldDropCredentialsCrossOrigin(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = allowlistingHandlerFor(uriBuilder, RedirectDispatcher.PATH_ALLOWLISTED,
                     RedirectPolicy.CredentialForwarding.STRIP_ON_CROSS_ORIGIN)) {
            String echo = echoOf(handler.send(credentialedRequest(handler), HttpResponse.BodyHandlers.ofString()));

            assertEquals("absent", field(echo, "authorization"),
                    "the secure default drops Authorization on a cross-origin hop");
            assertEquals("absent", field(echo, "cookie"),
                    "the secure default drops Cookie on a cross-origin hop");
        }
    }

    @Test
    @DisplayName("FORWARD_TO_ALLOWLISTED should carry Authorization and Cookie across the allowlisted hop")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void forwardStrategyShouldKeepCredentialsCrossOrigin(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = allowlistingHandlerFor(uriBuilder, RedirectDispatcher.PATH_ALLOWLISTED,
                     RedirectPolicy.CredentialForwarding.FORWARD_TO_ALLOWLISTED)) {
            String echo = echoOf(handler.send(credentialedRequest(handler), HttpResponse.BodyHandlers.ofString()));

            assertEquals("present", field(echo, "authorization"),
                    "the opt-in keeps Authorization on a hop the policy already permitted");
            assertEquals("present", field(echo, "cookie"),
                    "the opt-in keeps Cookie on a hop the policy already permitted");
        }
    }

    @ParameterizedTest
    @CsvSource({"STRIP_ON_CROSS_ORIGIN", "FORWARD_TO_ALLOWLISTED"})
    @DisplayName("A same-origin hop should keep Authorization and Cookie under either strategy")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sameOriginHopShouldKeepCredentials(RedirectPolicy.CredentialForwarding strategy, URIBuilder uriBuilder)
            throws Exception {
        try (HttpHandler handler = HttpHandler.builder()
                     .uri(targetUri(uriBuilder, RedirectDispatcher.PATH_REDIRECT))
                     .allowInsecureHttp(true)
                     .redirectPolicy(RedirectPolicy.builder().credentialForwarding(strategy).build())
                     .build()) {
            String echo = echoOf(handler.send(credentialedRequest(handler), HttpResponse.BodyHandlers.ofString()));

            assertEquals("present", field(echo, "authorization"),
                    "a same-origin hop never strips credentials");
            assertEquals("present", field(echo, "cookie"),
                    "a same-origin hop never strips credentials");
        }
    }

    @Test
    @DisplayName("FORWARD_TO_ALLOWLISTED should still refuse a non-allowlisted cross-host hop")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void forwardStrategyShouldNotWidenTheAllowlist(URIBuilder uriBuilder) {
        try (HttpHandler handler = allowlistingHandlerFor(uriBuilder, RedirectDispatcher.PATH_CROSS_HOST,
                     RedirectPolicy.CredentialForwarding.FORWARD_TO_ALLOWLISTED)) {
            RedirectNotAllowedException thrown = assertThrows(RedirectNotAllowedException.class,
                    () -> get(handler), "the strategy changes no refusal verdict");

            assertEquals(RedirectPolicy.RedirectRefusal.CROSS_ORIGIN, thrown.getReason());
        }
    }

    private static HttpResponse<String> get(HttpHandler handler) throws Exception {
        return handler.send(handler.requestBuilder().GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest postRequest(HttpHandler handler) {
        return handler.requestBuilder()
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(POST_BODY))
                .build();
    }

    private static HttpRequest credentialedRequest(HttpHandler handler) {
        return handler.requestBuilder()
                .header("Authorization", AUTHORIZATION_VALUE)
                .header("Cookie", COOKIE_VALUE)
                .GET()
                .build();
    }

    /** Reads the terminal echo from the response header, so a HEAD response is assertable too. */
    private static String echoOf(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), "the hop must have reached the terminal route");
        return response.headers().firstValue(RedirectDispatcher.HEADER_ECHO)
                .orElseThrow(() -> new AssertionError("terminal response carried no "
                        + RedirectDispatcher.HEADER_ECHO + " header"));
    }

    /** Extracts one {@code key=value} field from a {@link RedirectDispatcher#echo} string. */
    private static String field(String echo, String key) {
        for (String segment : echo.split(";")) {
            if (segment.startsWith(key + "=")) {
                return segment.substring(key.length() + 1);
            }
        }
        throw new AssertionError("echo '" + echo + "' carries no field '" + key + "'");
    }

    private static URI targetUri(URIBuilder uriBuilder, String path) {
        return URI.create(uriBuilder.build().toString().replaceAll("/$", "") + path);
    }

    private static HttpHandler handlerFor(URIBuilder uriBuilder, String path) {
        return HttpHandler.builder()
                .uri(targetUri(uriBuilder, path))
                .allowInsecureHttp(true)
                .build();
    }

    /**
     * Builds a handler whose policy allowlists {@link RedirectDispatcher#ALLOWLISTED_HOST} — the same
     * MockWebServer instance under a second name, so a genuinely cross-origin hop can be followed for
     * real and its terminal request inspected.
     */
    private static HttpHandler allowlistingHandlerFor(URIBuilder uriBuilder, String path,
            RedirectPolicy.CredentialForwarding strategy) {
        return HttpHandler.builder()
                .uri(targetUri(uriBuilder, path))
                .allowInsecureHttp(true)
                .redirectPolicy(RedirectPolicy.builder()
                        .allowedHosts(List.of(RedirectDispatcher.ALLOWLISTED_HOST))
                        .credentialForwarding(strategy)
                        .build())
                .build();
    }

    /**
     * Serves the two shapes {@link RedirectDispatcher} cannot express as a followable redirect: a
     * non-followable 3xx, and a followable one whose {@code Location} is present but blank.
     */
    static final class NotModifiedDispatcher implements ModuleDispatcherElement {

        static final String BASE_PATH = "/redirect-edge";
        static final String PATH_NOT_MODIFIED = BASE_PATH + "/not-modified";
        static final String PATH_BLANK_LOCATION = BASE_PATH + "/blank-location";

        @Override
        public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
            return switch (request.getUrl().encodedPath()) {
                case PATH_NOT_MODIFIED -> Optional.of(new MockResponse(304, Headers.of(), ""));
                case PATH_BLANK_LOCATION -> Optional.of(new MockResponse(302,
                        new Headers.Builder().add("Location", "   ").build(), ""));
                default -> Optional.empty();
            };
        }

        @Override
        public String getBaseUrl() {
            return BASE_PATH;
        }

        @Override
        public @NonNull Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET);
        }
    }
}

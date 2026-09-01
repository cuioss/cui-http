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

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for {@link HttpHandler}'s bounded, revalidating redirect loop.
 * <p>
 * {@link HttpHandler#send(java.net.http.HttpRequest, HttpResponse.BodyHandler)} and
 * {@link HttpHandler#sendAsync(java.net.http.HttpRequest, HttpResponse.BodyHandler)} follow hops in
 * cui-http code, revalidating each target against the handler's {@link RedirectPolicy} before it is
 * requested. The underlying JDK client keeps {@code Redirect.NEVER} throughout — that separation is
 * asserted in {@code HttpHandlerIntegrationTest}; here the concern is which hops the loop takes and
 * which it refuses.
 *
 * @author Oliver Wolff
 * @since 2.2
 */
@EnableTestLogger
@EnableMockWebServer(useHttps = false)
@DisplayName("HttpHandler Redirect Following Tests")
class HttpHandlerRedirectTest {

    private final RedirectDispatcher redirectDispatcher = new RedirectDispatcher();

    private final EdgeCaseDispatcher edgeCaseDispatcher = new EdgeCaseDispatcher();

    public ModuleDispatcherElement getRedirectDispatcher() {
        return redirectDispatcher;
    }

    public ModuleDispatcherElement getEdgeCaseDispatcher() {
        return edgeCaseDispatcher;
    }

    @Test
    @DisplayName("send should follow a same-origin 302 and return the terminal 200")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sendShouldFollowSameOriginRedirect(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_REDIRECT)) {
            HttpResponse<String> response = handler.send(handler.requestBuilder().GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), "the terminal hop's status must reach the caller");
            assertEquals(RedirectDispatcher.TARGET_BODY, response.body(),
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
            assertEquals(RedirectDispatcher.TARGET_BODY, response.body(),
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
    @DisplayName("A 304 should surface verbatim with no follow attempted")
    @ModuleDispatcher(providerMethod = "getEdgeCaseDispatcher")
    void notModifiedShouldSurfaceVerbatim(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, EdgeCaseDispatcher.PATH_NOT_MODIFIED)) {
            HttpResponse<String> response = handler.send(handler.requestBuilder().GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(304, response.statusCode(), "304 is not a followable status");
            assertTrue(response.uri().getPath().endsWith(EdgeCaseDispatcher.PATH_NOT_MODIFIED),
                    "no hop may have been walked");
        }
    }

    @Test
    @DisplayName("A 302 with no Location header should surface verbatim")
    @ModuleDispatcher(providerMethod = "getEdgeCaseDispatcher")
    void redirectWithoutLocationShouldSurfaceVerbatim(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, EdgeCaseDispatcher.PATH_NO_LOCATION)) {
            HttpResponse<String> response = handler.send(handler.requestBuilder().GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(302, response.statusCode(), "without a Location there is no target to validate");
            assertTrue(response.uri().getPath().endsWith(EdgeCaseDispatcher.PATH_NO_LOCATION),
                    "no hop may have been walked");
        }
    }

    @Test
    @DisplayName("A 302 with a blank Location header should surface verbatim")
    @ModuleDispatcher(providerMethod = "getEdgeCaseDispatcher")
    void redirectWithBlankLocationShouldSurfaceVerbatim(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, EdgeCaseDispatcher.PATH_BLANK_LOCATION)) {
            HttpResponse<String> response = handler.send(handler.requestBuilder().GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(302, response.statusCode(), "a blank Location names no target either");
            assertTrue(response.uri().getPath().endsWith(EdgeCaseDispatcher.PATH_BLANK_LOCATION),
                    "no hop may have been walked");
        }
    }

    @Test
    @DisplayName("A cross-host hop should be refused with CROSS_ORIGIN under the same-origin default")
    @ModuleDispatcher(providerMethod = "getEdgeCaseDispatcher")
    void crossHostHopShouldBeRefused(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, EdgeCaseDispatcher.PATH_OFF_SERVER)) {
            RedirectNotAllowedException thrown = assertThrows(RedirectNotAllowedException.class,
                    () -> handler.send(handler.requestBuilder().GET().build(), HttpResponse.BodyHandlers.ofString()),
                    "an allowlist-free policy must refuse an off-server target");

            assertEquals(RedirectPolicy.RedirectRefusal.CROSS_ORIGIN, thrown.getReason());
            assertEquals(URI.create(EdgeCaseDispatcher.OFF_SERVER_LOCATION), thrown.getTo(),
                    "the refusal must name the target it refused");
        }
    }

    @Test
    @DisplayName("A refused hop on the ping path should log the HTTP-117 WARN and report UNKNOWN")
    @ModuleDispatcher(providerMethod = "getEdgeCaseDispatcher")
    void refusedHopShouldLogRedirectRefusedWarning(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, EdgeCaseDispatcher.PATH_OFF_SERVER)) {
            assertEquals(HttpStatusFamily.UNKNOWN, handler.pingGet(),
                    "a refused hop leaves the ping without a usable status");

            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "HTTP-117");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    EdgeCaseDispatcher.OFF_SERVER_LOCATION);
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    RedirectPolicy.RedirectRefusal.CROSS_ORIGIN.name());
        }
    }

    private static HttpHandler handlerFor(URIBuilder uriBuilder, String path) {
        URI target = URI.create(uriBuilder.build().toString().replaceAll("/$", "") + path);
        return HttpHandler.builder()
                .uri(target)
                .allowInsecureHttp(true)
                .build();
    }

    /**
     * Serves the redirect shapes the loop must NOT follow, plus one off-server target the
     * same-origin default must refuse. Kept local to this test because
     * {@link RedirectDispatcher} owns only the happy-path same-origin route.
     */
    static final class EdgeCaseDispatcher implements ModuleDispatcherElement {

        static final String BASE_PATH = "/redirect-edge";
        static final String PATH_NOT_MODIFIED = BASE_PATH + "/not-modified";
        static final String PATH_NO_LOCATION = BASE_PATH + "/no-location";
        static final String PATH_BLANK_LOCATION = BASE_PATH + "/blank-location";
        static final String PATH_OFF_SERVER = BASE_PATH + "/off-server";

        /** An absolute target on a host the MockWebServer instance does not serve. */
        static final String OFF_SERVER_LOCATION = "http://off-server.example.org/target";

        @Override
        public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
            return handle(request);
        }

        @Override
        public Optional<MockResponse> handleHead(@NonNull RecordedRequest request) {
            return handle(request);
        }

        private Optional<MockResponse> handle(RecordedRequest request) {
            return switch (request.getUrl().encodedPath()) {
                case PATH_NOT_MODIFIED -> Optional.of(new MockResponse(304, Headers.of(), ""));
                case PATH_NO_LOCATION -> Optional.of(new MockResponse(302, Headers.of(), ""));
                case PATH_BLANK_LOCATION -> Optional.of(new MockResponse(302,
                        new Headers.Builder().add("Location", "   ").build(), ""));
                case PATH_OFF_SERVER -> Optional.of(new MockResponse(302,
                        new Headers.Builder().add("Location", OFF_SERVER_LOCATION).build(), ""));
                default -> Optional.empty();
            };
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
}

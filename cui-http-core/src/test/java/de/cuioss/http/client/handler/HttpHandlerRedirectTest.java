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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

    /**
     * Every representation-metadata field {@link RedirectDispatcher#echo} reports, named as its echo
     * key. A body-dropping rewrite must strip all of them, so the list is asserted as a whole rather
     * than spot-checking {@code Content-Type} / {@code Content-Length} — the two that were already
     * dropped before the RFC 9110 errata eid8138 field set was applied.
     */
    private static final List<String> REPRESENTATION_ECHO_FIELDS = List.of("contentType", "contentLength",
            "contentEncoding", "contentLanguage", "contentLocation", "digest", "lastModified");

    /**
     * The subset of {@link #REPRESENTATION_ECHO_FIELDS} a caller can actually set on an
     * {@link HttpRequest}: {@code Content-Length} is a restricted header the JDK derives from the
     * body publisher rather than accepting from the caller, so it is the one field a positive
     * "preserved verbatim" control cannot arrange.
     */
    private static final List<String> SETTABLE_REPRESENTATION_ECHO_FIELDS = REPRESENTATION_ECHO_FIELDS.stream()
            .filter(field -> !"contentLength".equals(field))
            .toList();

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
    @DisplayName("send should never hand an intermediate hop's streaming body to the caller's handler")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sendShouldDiscardIntermediateHopBody(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_STREAMING_REDIRECT)) {
            RecordingBodyHandler<InputStream> recorder = streamRecorder();

            HttpResponse<InputStream> response = handler.send(handler.requestBuilder().GET().build(), recorder);

            assertEquals(List.of(200), recorder.appliedStatuses(),
                    "only the terminal response may be materialized through the caller's handler; the 302 hop's "
                            + "body must be drained and discarded rather than become an unread, unclosed stream");
            assertEquals(RedirectDispatcher.TARGET_BODY, leadingToken(response.body()),
                    "the terminal hop's streamed body must still reach the caller intact");
        }
    }

    @Test
    @DisplayName("sendAsync should discard the intermediate hop's streaming body too")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sendAsyncShouldDiscardIntermediateHopBody(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_STREAMING_REDIRECT)) {
            RecordingBodyHandler<InputStream> recorder = streamRecorder();

            HttpResponse<InputStream> response = handler
                    .sendAsync(handler.requestBuilder().GET().build(), recorder).get();

            assertEquals(List.of(200), recorder.appliedStatuses(),
                    "the async recursion must apply the same discard, so the caller's handler sees only the "
                            + "terminal response");
            assertEquals(RedirectDispatcher.TARGET_BODY, leadingToken(response.body()),
                    "the async path must still deliver the terminal hop's streamed body");
        }
    }

    @Test
    @DisplayName("A refused hop's body should be discarded rather than handed to the caller's handler")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void refusedHopBodyShouldBeDiscarded(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_CROSS_HOST)) {
            RecordingBodyHandler<InputStream> recorder = streamRecorder();

            assertThrows(RedirectNotAllowedException.class,
                    () -> handler.send(handler.requestBuilder().GET().build(), recorder),
                    "the same-origin default must refuse this hop");

            assertEquals(List.of(), recorder.appliedStatuses(),
                    "a refused hop's response is discarded rather than returned, so its body must not be "
                            + "materialized through the caller's handler either — that stream would have no owner");
        }
    }

    @Test
    @DisplayName("A followable status with no Location is terminal, so its body reaches the caller's handler")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void unfollowedRedirectBodyShouldReachTheCallersHandler(URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_NO_LOCATION)) {
            RecordingBodyHandler<InputStream> recorder = streamRecorder();

            HttpResponse<InputStream> response = handler.send(handler.requestBuilder().GET().build(), recorder);

            assertEquals(List.of(302), recorder.appliedStatuses(),
                    "a followable status without a usable Location is not an intermediate hop, so the discard "
                            + "must not swallow it");
            assertEquals(RedirectDispatcher.UNFOLLOWED_BODY, readFully(response.body()),
                    "the verbatim-surfaced 302 must deliver its own body to the caller");
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


    @Test
    @DisplayName("send should refuse an unparseable Location with MALFORMED_LOCATION, not an IllegalArgumentException")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sendShouldRefuseMalformedLocation(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_MALFORMED_LOCATION)) {
            RedirectNotAllowedException thrown = assertThrows(RedirectNotAllowedException.class,
                    () -> get(handler), "a remote-controlled Location must never escape as a raw parse failure");

            assertEquals(RedirectPolicy.RedirectRefusal.MALFORMED_LOCATION, thrown.getReason());
            assertNull(thrown.getTo(), "no target was ever resolved, so the refusal names none");
            assertTrue(thrown.getFrom().getPath().endsWith(RedirectDispatcher.PATH_MALFORMED_LOCATION),
                    "the refusal must name the hop that produced the malformed Location");
            assertTrue(thrown.getMessage().contains(RedirectDispatcher.MALFORMED_LOCATION),
                    "the refusal must quote the offending Location value");
            assertInstanceOf(IllegalArgumentException.class, thrown.getCause(),
                    "the underlying parse failure must be retained as the cause");
        }
    }

    @Test
    @DisplayName("sendAsync should complete exceptionally with the same MALFORMED_LOCATION refusal")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sendAsyncShouldRefuseMalformedLocation(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_MALFORMED_LOCATION)) {
            CompletableFuture<HttpResponse<String>> future = handler.sendAsync(
                    handler.requestBuilder().GET().build(), HttpResponse.BodyHandlers.ofString());

            ExecutionException thrown = assertThrows(ExecutionException.class, future::get,
                    "the async recursion must refuse the hop rather than fail on the parse");
            RedirectNotAllowedException refusal = assertInstanceOf(RedirectNotAllowedException.class,
                    thrown.getCause(), "the async path must surface the same typed refusal");

            assertEquals(RedirectPolicy.RedirectRefusal.MALFORMED_LOCATION, refusal.getReason());
            assertNull(refusal.getTo(), "no target was ever resolved, so the refusal names none");
        }
    }

    /**
     * Unit-level, not end-to-end: a real {@code Location} header cannot carry most control
     * characters through the wire (okhttp's {@code Headers.Builder} rejects every C0/C1 control
     * except {@code TAB}) and the JDK {@code HttpClient} itself collapses an embedded {@code TAB} in
     * a received header value down to a single space (RFC 7230 optional-whitespace folding) before
     * {@link HttpHandler} ever sees it — so the sanitizer's control-character neutralization is
     * asserted directly against the package-private helper it exercises inside
     * {@link HttpHandler#resolveTarget}, mirroring the class-level "Known end-to-end gap" note above
     * for {@code PROTOCOL_DOWNGRADE}.
     */
    @Test
    @DisplayName("sanitizeForMessage should neutralize control characters and preserve the rest of the value")
    void sanitizeForMessageShouldNeutralizeControlCharacters() {
        String withControlChars = "http://example.org/target\r\nInjected: evil\tvalue\u0000\u0007end";

        String sanitized = HttpHandler.sanitizeForMessage(withControlChars);

        assertEquals("http://example.org/target??Injected: evil?value??end", sanitized,
                "each control character (CR, LF, TAB, NUL, BEL) must become a single visible placeholder");
        assertFalse(sanitized.contains("\r") || sanitized.contains("\n") || sanitized.contains("\t")
                || sanitized.contains("\u0000") || sanitized.contains("\u0007"),
                "no raw control character may survive sanitization");
    }

    @Test
    @DisplayName("sanitizeForMessage should bound length and mark truncation")
    void sanitizeForMessageShouldTruncateOverlongValues() {
        String overlong = "a".repeat(500);

        String sanitized = HttpHandler.sanitizeForMessage(overlong);

        assertTrue(sanitized.endsWith("...[truncated]"),
                "an overlong value must carry a visible truncation marker");
        assertTrue(sanitized.length() < overlong.length(),
                "the sanitized value must be shorter than the unbounded original");
    }

    @Test
    @DisplayName("An overlong malformed Location should be truncated in the refusal message")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void overlongMalformedLocationShouldBeTruncated(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_MALFORMED_LOCATION_OVERLONG)) {
            RedirectNotAllowedException thrown = assertThrows(RedirectNotAllowedException.class,
                    () -> get(handler), "an overlong Location must still refuse the hop");

            assertEquals(RedirectPolicy.RedirectRefusal.MALFORMED_LOCATION, thrown.getReason());
            assertFalse(thrown.getMessage().contains(RedirectDispatcher.MALFORMED_LOCATION_OVERLONG),
                    "the full, unbounded Location must never reach the exception message verbatim");
            assertTrue(thrown.getMessage().contains("...[truncated]"),
                    "the refusal must carry a visible truncation marker so a reader can tell the value was cut");
        }
    }

    @Test
    @DisplayName("An unparseable Location on the ping path should log the HTTP-117 WARN and report UNKNOWN")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void malformedLocationShouldLogRedirectRefusedWarning(URIBuilder uriBuilder) {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_MALFORMED_LOCATION)) {
            assertEquals(HttpStatusFamily.UNKNOWN, handler.pingGet(),
                    "an unparseable Location leaves the ping without a usable status");

            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "HTTP-117");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    RedirectPolicy.RedirectRefusal.MALFORMED_LOCATION.name());
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
    @CsvSource({"301", "302", "303"})
    @DisplayName("A body-dropping rewrite should strip every representation-metadata header")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void bodyDroppingRewriteShouldStripAllRepresentationHeaders(int status, URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_STATUS_PREFIX + status)) {
            String echo = echoOf(handler.send(representationHeaderedPost(handler),
                    HttpResponse.BodyHandlers.ofString()));

            assertEquals("absent", field(echo, "body"), status + " must drop the request body");
            for (String key : REPRESENTATION_ECHO_FIELDS) {
                assertEquals("absent", field(echo, key),
                        key + " describes the representation that " + status + " just discarded, so it must be "
                                + "dropped with the body (RFC 9110, errata eid8138) — not only Content-Type "
                                + "and Content-Length");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"307", "308"})
    @DisplayName("A body-preserving rewrite should keep the representation-metadata headers")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void bodyPreservingRewriteShouldKeepRepresentationHeaders(int status, URIBuilder uriBuilder) throws Exception {
        try (HttpHandler handler = handlerFor(uriBuilder, RedirectDispatcher.PATH_STATUS_PREFIX + status)) {
            String echo = echoOf(handler.send(representationHeaderedPost(handler),
                    HttpResponse.BodyHandlers.ofString()));

            assertEquals(Integer.toString(POST_BODY.length()), field(echo, "body"),
                    status + " must replay the original body");
            for (String key : SETTABLE_REPRESENTATION_ECHO_FIELDS) {
                assertEquals("present", field(echo, key),
                        key + " still describes the body " + status + " replays verbatim, so stripping it would "
                                + "misdescribe the request");
            }
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

    private static RecordingBodyHandler<InputStream> streamRecorder() {
        return new RecordingBodyHandler<>(HttpResponse.BodyHandlers.ofInputStream());
    }

    /** Reads the stream to exhaustion and closes it, so no test leaves a body stream open itself. */
    private static String readFully(InputStream body) throws Exception {
        try (InputStream stream = body) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The leading {@code ;}-delimited token of a streamed terminal echo. */
    private static String leadingToken(InputStream body) throws Exception {
        return readFully(body).split(";", 2)[0];
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

    /**
     * A body-carrying POST that additionally declares every settable representation-metadata field.
     * {@code Content-Encoding} is {@code identity} rather than a real codec so the body on the wire
     * stays exactly what the publisher wrote.
     */
    private static HttpRequest representationHeaderedPost(HttpHandler handler) {
        return handler.requestBuilder()
                .header("Content-Type", "application/json")
                .header("Content-Encoding", "identity")
                .header("Content-Language", "en")
                .header("Content-Location", "/original-representation")
                .header("Digest", "sha-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=")
                .header("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
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
     * A {@link HttpResponse.BodyHandler} that records the status of every response it is applied to
     * before delegating.
     * <p>
     * The recorded list is the direct evidence for the intermediate-hop discard contract: the JDK
     * applies a body handler exactly once per response it reads a body for, so a status that never
     * appears here is a response whose body was never materialized through the caller's handler.
     * That is precisely what stops a streaming handler from producing an intermediate
     * {@code InputStream} nobody ever consumes or closes.
     */
    private static final class RecordingBodyHandler<T> implements HttpResponse.BodyHandler<T> {

        private final HttpResponse.BodyHandler<T> delegate;
        // The async path applies the handler on an HttpClient thread, not the test thread.
        private final List<Integer> appliedStatuses = Collections.synchronizedList(new ArrayList<>());

        private RecordingBodyHandler(HttpResponse.BodyHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
            appliedStatuses.add(responseInfo.statusCode());
            return delegate.apply(responseInfo);
        }

        private List<Integer> appliedStatuses() {
            return List.copyOf(appliedStatuses);
        }
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

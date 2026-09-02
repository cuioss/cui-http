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
import de.cuioss.http.client.dispatcher.TestContentDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link HttpHandler} against a TLS-terminated MockWebServer.
 * <p>
 * Every other MockWebServer-backed test in this module runs the fixture in cleartext
 * ({@code useHttps = false}), so no test exercised a TLS handshake at all. This class is the one
 * that does: it drives a real request over TLS through {@link HttpHandler} and asserts what came
 * back <em>from the server</em>. The point is the evidence, not the coverage count — a green suite
 * that never completed a handshake cannot tell anyone whether the HTTPS path works, and this class
 * is the standing regression guard against that state returning.
 * <p>
 * Every assertion here reads state the server produced — the status and body received on the wire,
 * and the dispatcher's own record that it served the exchange — never a flag the client reports
 * about itself. A client-side flag would still read "configured for HTTPS" on a handler whose
 * handshake never happened, which is precisely the failure this class exists to detect.
 * <p>
 * The response body is generated rather than a fixed literal: an arbitrary per-run value cannot
 * match by coincidence, so observing it on the client side proves the bytes travelled the
 * connection.
 * <p>
 * Having a TLS-terminated server is also what makes the HTTPS&nbsp;&rarr;&nbsp;HTTP downgrade
 * refusal ({@link RedirectPolicy.RedirectRefusal#PROTOCOL_DOWNGRADE}) assertable end-to-end rather
 * than only at the {@link RedirectPolicy} seam. One TLS server suffices: the refusal is decided
 * before the next hop is contacted, so no second, cleartext MockWebServer instance is needed.
 * <p>
 * The TLS fixture is likewise what makes the positive half of
 * {@link RedirectPolicy#forwardsCredentials(java.net.URI, java.net.URI)} assertable end-to-end:
 * credentials survive a same-origin hop only when the target is {@code https}, and every hop in the
 * cleartext {@code HttpHandlerRedirectTest} fixture is refused them by the unconditional
 * cleartext rule.
 * <p>
 * Cleartext {@code HttpHandler} integration coverage lives in {@code HttpHandlerIntegrationTest};
 * the remaining redirect-following behaviour lives in {@code HttpHandlerRedirectTest}.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer(useHttps = true)
@DisplayName("HttpHandler HTTPS Integration Tests")
class HttpHandlerHttpsIntegrationTest {

    private final TestContentDispatcher dispatcher = new TestContentDispatcher();

    private final RedirectDispatcher redirectDispatcher = new RedirectDispatcher();

    public ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher;
    }

    public ModuleDispatcherElement getRedirectDispatcher() {
        return redirectDispatcher;
    }

    @Test
    @DisplayName("A GET over a real TLS handshake returns the server's status and body")
    @ModuleDispatcher
    void getOverRealTlsReturnsServerStatusAndBody(URIBuilder uriBuilder, SSLContext sslContext) throws Exception {
        String payload = Generators.letterStrings(16, 64).next();
        dispatcher.withSuccess(payload, null);

        URI target = uriBuilder.addPathSegments("api", "data").build();
        HttpResponse<String> response;
        try (HttpHandler handler = HttpHandler.builder()
                     .uri(target)
                     .sslContext(sslContext)
                     .build()) {
            response = handler.send(handler.requestBuilder().GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        assertAll("the exchange observed on the wire",
                () -> assertEquals("https", response.uri().getScheme(),
                        "the response must have been received over TLS, not over a cleartext fallback"),
                () -> assertEquals(200, response.statusCode(),
                        "the server's status must reach the caller through the TLS connection"),
                () -> assertEquals(payload, response.body(),
                        "the server's body must reach the caller byte-for-byte through the TLS connection"),
                () -> assertEquals(1, dispatcher.getCallCounter(),
                        "the server must have served exactly one exchange, so the handshake completed"));
    }

    @Test
    @DisplayName("A 302 off TLS to a cleartext target is refused before the target is contacted")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void redirectFromTlsToCleartextIsRefusedBeforeContactingTheTarget(
            MockWebServer server, URIBuilder uriBuilder, SSLContext sslContext) {
        URI start = URI.create(uriBuilder.build().toString().replaceAll("/$", "")
                + RedirectDispatcher.PATH_DOWNGRADE_SCHEME);

        RedirectNotAllowedException refusal;
        try (HttpHandler handler = HttpHandler.builder()
                     .uri(start)
                     .sslContext(sslContext)
                     .build()) {
            HttpRequest request = handler.requestBuilder().GET().build();
            refusal = assertThrows(RedirectNotAllowedException.class,
                    () -> handler.send(request, HttpResponse.BodyHandlers.ofString()),
                    "a hop off TLS must be refused, so no response ever reaches the caller");
        }

        URI refusedTarget = refusal.getTo();
        assertNotNull(refusedTarget, "the refusal must name the cleartext target it refused");

        // The target keeps this server's own host and port, so a client that wrongly followed the
        // hop would speak cleartext to the TLS listener and fail on transport. A typed refusal
        // rather than an I/O error is therefore the proof that the request was never issued; the
        // request count below corroborates it from the server's side.
        assertAll("the downgrade refusal observed end-to-end over TLS",
                () -> assertEquals(RedirectPolicy.RedirectRefusal.PROTOCOL_DOWNGRADE, refusal.getReason(),
                        "dropping off TLS must be refused as a downgrade, not as a generic cross-origin hop"),
                () -> assertEquals("https", refusal.getFrom().getScheme(),
                        "the refused hop must have originated on the TLS connection"),
                () -> assertEquals("http", refusedTarget.getScheme(),
                        "the refused target must be the cleartext one the server offered"),
                () -> assertEquals(RedirectDispatcher.PATH_TARGET, refusedTarget.getPath(),
                        "the refused target must be the redirect's declared destination"),
                () -> assertEquals(1, server.getRequestCount(),
                        "only the redirect exchange may appear on the wire: the cleartext target was never contacted"));
    }

    @Test
    @DisplayName("A same-origin hop over TLS carries Authorization and Cookie to the terminal request")
    @ModuleDispatcher(providerMethod = "getRedirectDispatcher")
    void sameOriginHopOverTlsKeepsCredentials(URIBuilder uriBuilder, SSLContext sslContext) throws Exception {
        URI start = URI.create(uriBuilder.build().toString().replaceAll("/$", "")
                + RedirectDispatcher.PATH_REDIRECT);

        HttpResponse<String> response;
        try (HttpHandler handler = HttpHandler.builder()
                     .uri(start)
                     .sslContext(sslContext)
                     .build()) {
            HttpRequest request = handler.requestBuilder()
                    .header("Authorization", "Bearer test-token")
                    .header("Cookie", "session=abc")
                    .GET()
                    .build();
            response = handler.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertEquals(200, response.statusCode(), "the hop must have reached the terminal route");
        String echo = response.headers().firstValue(RedirectDispatcher.HEADER_ECHO)
                .orElseThrow(() -> new AssertionError("terminal response carried no "
                        + RedirectDispatcher.HEADER_ECHO + " header"));

        // The positive control for the cleartext credential rule: the same hop shape that drops both
        // headers over http in HttpHandlerRedirectTest carries both when the target is https.
        assertAll("the credentials the terminal request actually received over TLS",
                () -> assertEquals("https", response.uri().getScheme(),
                        "the terminal request must have been issued over TLS"),
                () -> assertEquals("present", echoField(echo, "authorization"),
                        "an https same-origin hop must keep Authorization"),
                () -> assertEquals("present", echoField(echo, "cookie"),
                        "an https same-origin hop must keep Cookie"));
    }

    /** Extracts one {@code key=value} field from a {@link RedirectDispatcher#echo} string. */
    private static String echoField(String echo, String key) {
        for (String segment : echo.split(";")) {
            if (segment.startsWith(key + "=")) {
                return segment.substring(key.length() + 1);
            }
        }
        throw new AssertionError("echo '" + echo + "' carries no field '" + key + "'");
    }
}

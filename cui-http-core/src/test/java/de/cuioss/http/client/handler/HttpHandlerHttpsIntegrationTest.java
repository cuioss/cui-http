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

import de.cuioss.http.client.dispatcher.TestContentDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * Cleartext {@code HttpHandler} integration coverage lives in {@code HttpHandlerIntegrationTest};
 * redirect-following behaviour lives in {@code HttpHandlerRedirectTest}.
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

    public ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher;
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
}

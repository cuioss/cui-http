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

import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test guarding TLS hostname verification for {@link HttpHandler} (F-19).
 *
 * <p>PR #76 manipulates {@link javax.net.ssl.SSLParameters} - the object that, if mishandled,
 * would disable hostname verification. Verification currently stays on only because
 * {@code java.net.http.HttpClient} forces {@code "HTTPS"} endpoint identification internally, but
 * no test guarded it. These tests connect over real TLS to an in-JVM server and assert that a
 * certificate whose SAN does <em>not</em> match the connected host is rejected, while an otherwise
 * identical matching certificate is accepted - so a future {@code SSLParameters} refactor cannot
 * silently disable hostname verification.</p>
 *
 * <p>These tests pin the <em>default-on</em> hostname-verification behaviour; the in-JVM TLS server
 * helper they drive now lives in the shared {@link OneShotTlsServer} class so sibling tests in this
 * package can reuse the single implementation.</p>
 */
@DisplayName("HttpHandler TLS hostname verification (F-19)")
class HttpHandlerHostnameVerificationTest {

    /**
     * A matching-hostname certificate ({@code SAN=localhost}) is accepted: the handshake succeeds
     * and the request returns SUCCESS. This confirms the trust chain and server are sound, isolating
     * the SAN as the only variable in the negative test below.
     */
    @Test
    @DisplayName("Matching-SAN certificate is accepted")
    void shouldAcceptMatchingHostname() throws Exception {
        HeldCertificate cert = new HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build();
        try (OneShotTlsServer server = OneShotTlsServer.start(cert)) {
            HttpHandler handler = HttpHandler.builder()
                    .uri("https://localhost:" + server.port())
                    .sslContext(clientTrusting(cert))
                    .connectionTimeoutSeconds(5)
                    .readTimeoutSeconds(5)
                    .build();

            assertEquals(HttpStatusFamily.SUCCESS, handler.pingGet(),
                    "A certificate whose SAN matches the connected host must be accepted");
        }
    }

    /**
     * A mismatched-hostname certificate ({@code SAN=wrong.host.invalid}) is rejected: the handshake
     * fails, so the ping does not reach a 200 and returns UNKNOWN. A client with hostname
     * verification disabled would instead complete the handshake and observe SUCCESS.
     */
    @Test
    @DisplayName("Mismatched-SAN certificate is rejected")
    void shouldRejectWrongHostname() throws Exception {
        HeldCertificate cert = new HeldCertificate.Builder()
                .commonName("wrong.host.invalid")
                .addSubjectAlternativeName("wrong.host.invalid")
                .build();
        try (OneShotTlsServer server = OneShotTlsServer.start(cert)) {
            HttpHandler handler = HttpHandler.builder()
                    .uri("https://localhost:" + server.port())
                    .sslContext(clientTrusting(cert))
                    .connectionTimeoutSeconds(5)
                    .readTimeoutSeconds(5)
                    .build();

            assertEquals(HttpStatusFamily.UNKNOWN, handler.pingGet(),
                    "A certificate whose SAN does not match the connected host must be rejected "
                            + "(hostname verification active)");
        }
    }

    /**
     * Builds a client {@link SSLContext} that trusts exactly the given certificate, so that any
     * handshake failure is attributable to hostname verification rather than trust.
     */
    private static SSLContext clientTrusting(HeldCertificate cert) {
        return new HandshakeCertificates.Builder()
                .addTrustedCertificate(cert.certificate())
                .build()
                .sslContext();
    }
}

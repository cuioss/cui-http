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

import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioural proof for {@link HostnameVerificationRelaxingTrustManager}.
 *
 * <p>The tests drive a real TLS handshake against the shared {@link OneShotTlsServer} helper with
 * the client's endpoint-identification algorithm pinned to {@code "HTTPS"} - the setting that makes
 * hostname matching happen at all. A matched positive/negative control pair proves the wrapper is
 * the only reason a mismatched SAN is accepted, and the remaining cases prove that chain trust and
 * validity-period enforcement survive the relaxation.</p>
 *
 * <p>No {@code javax.net.ssl.trustStore} system property is mutated: the client trust material is
 * built per test from okhttp {@link HandshakeCertificates}.</p>
 */
@DisplayName("HostnameVerificationRelaxingTrustManager relaxes hostname checks only")
class HostnameVerificationRelaxingTrustManagerTest {

    private static final long ONE_DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    @Test
    @DisplayName("Should wrap extended trust managers and pass other entries through unchanged")
    void shouldWrapExtendedTrustManagersOnly() {
        HeldCertificate anchor = new HeldCertificate.Builder().commonName("anchor").build();
        TrustManager extended = platformTrustManager(anchor);
        TrustManager plain = new PlainTrustManager();

        TrustManager[] relaxed = HostnameVerificationRelaxingTrustManager
                .relaxHostnameVerification(new TrustManager[]{extended, plain});

        assertAll("relaxHostnameVerification wrapping",
                () -> assertEquals(2, relaxed.length, "The wrapped array must keep its length"),
                () -> assertInstanceOf(HostnameVerificationRelaxingTrustManager.class, relaxed[0],
                        "An X509ExtendedTrustManager entry must be wrapped"),
                () -> assertInstanceOf(X509ExtendedTrustManager.class, extended,
                        "The platform trust manager is expected to be an X509ExtendedTrustManager"),
                () -> assertSame(plain, relaxed[1],
                        "A non-extended entry carries no identity context and must pass through unchanged"));
    }

    @Test
    @DisplayName("Should accept a mismatched-SAN certificate when the wrapper is applied")
    void shouldAcceptMismatchedHostnameWhenRelaxed() throws Exception {
        HeldCertificate ca = certificateAuthority();
        HeldCertificate leaf = leafSignedBy(ca, "wrong.host.invalid");

        try (OneShotTlsServer server = OneShotTlsServer.start(leaf)) {
            TrustManager[] relaxed = HostnameVerificationRelaxingTrustManager
                    .relaxHostnameVerification(new TrustManager[]{platformTrustManager(ca)});

            assertDoesNotThrow(() -> handshake(relaxed, server.port()),
                    "The relaxing wrapper must accept a certificate whose SAN does not match the host");
        }
    }

    @Test
    @DisplayName("Should reject a mismatched-SAN certificate without the wrapper (control)")
    void shouldRejectMismatchedHostnameWithoutWrapper() throws Exception {
        HeldCertificate ca = certificateAuthority();
        HeldCertificate leaf = leafSignedBy(ca, "wrong.host.invalid");

        try (OneShotTlsServer server = OneShotTlsServer.start(leaf)) {
            TrustManager[] strict = {platformTrustManager(ca)};

            assertThrows(SSLHandshakeException.class, () -> handshake(strict, server.port()),
                    "Without the wrapper the same certificate must be rejected - "
                            + "otherwise the positive case proves nothing");
        }
    }

    @Test
    @DisplayName("Should still reject a certificate signed by an untrusted CA when relaxed")
    void shouldRejectUntrustedCaWhenRelaxed() throws Exception {
        HeldCertificate servingCa = certificateAuthority();
        HeldCertificate otherCa = certificateAuthority();
        HeldCertificate leaf = leafSignedBy(servingCa, "localhost");

        try (OneShotTlsServer server = OneShotTlsServer.start(leaf)) {
            TrustManager[] relaxed = HostnameVerificationRelaxingTrustManager
                    .relaxHostnameVerification(new TrustManager[]{platformTrustManager(otherCa)});

            assertThrows(SSLHandshakeException.class, () -> handshake(relaxed, server.port()),
                    "Chain trust must survive the relaxation: an unknown CA must still be rejected");
        }
    }

    @Test
    @DisplayName("Should still reject an expired certificate when relaxed")
    void shouldRejectExpiredCertificateWhenRelaxed() throws Exception {
        HeldCertificate ca = certificateAuthority();
        long now = System.currentTimeMillis();
        HeldCertificate expiredLeaf = new HeldCertificate.Builder()
                .signedBy(ca)
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .validityInterval(now - 2 * ONE_DAY_MILLIS, now - ONE_DAY_MILLIS)
                .build();

        try (OneShotTlsServer server = OneShotTlsServer.start(expiredLeaf)) {
            TrustManager[] relaxed = HostnameVerificationRelaxingTrustManager
                    .relaxHostnameVerification(new TrustManager[]{platformTrustManager(ca)});

            assertThrows(SSLHandshakeException.class, () -> handshake(relaxed, server.port()),
                    "Validity-period enforcement must survive the relaxation");
        }
    }

    @Test
    @DisplayName("Should still reject a not-yet-valid certificate when relaxed")
    void shouldRejectNotYetValidCertificateWhenRelaxed() throws Exception {
        HeldCertificate ca = certificateAuthority();
        long now = System.currentTimeMillis();
        HeldCertificate futureLeaf = new HeldCertificate.Builder()
                .signedBy(ca)
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .validityInterval(now + ONE_DAY_MILLIS, now + 2 * ONE_DAY_MILLIS)
                .build();

        try (OneShotTlsServer server = OneShotTlsServer.start(futureLeaf)) {
            TrustManager[] relaxed = HostnameVerificationRelaxingTrustManager
                    .relaxHostnameVerification(new TrustManager[]{platformTrustManager(ca)});

            assertThrows(SSLHandshakeException.class, () -> handshake(relaxed, server.port()),
                    "A not-yet-valid certificate must still be rejected under the relaxing wrapper");
        }
    }

    /**
     * Opens a real TLS connection to {@code localhost:port} using the supplied trust managers with
     * the endpoint-identification algorithm pinned to {@code "HTTPS"} - the switch that makes the
     * JSSE trust manager perform hostname matching in the first place.
     */
    private static void handshake(TrustManager[] trustManagers, int port) throws Exception {
        SSLContext context = SSLContext.getInstance(SecureSSLContextProvider.TLS_V1_2);
        context.init(null, trustManagers, new SecureRandom());
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.setSoTimeout(5_000);
            socket.connect(new InetSocketAddress("localhost", port), 5_000);
            socket.startHandshake();
        }
    }

    /**
     * Builds the platform trust manager that trusts exactly the given certificate as a trust anchor.
     */
    private static TrustManager platformTrustManager(HeldCertificate anchor) {
        return new HandshakeCertificates.Builder()
                .addTrustedCertificate(anchor.certificate())
                .build()
                .trustManager();
    }

    private static HeldCertificate certificateAuthority() {
        return new HeldCertificate.Builder()
                .certificateAuthority(0)
                .commonName("cui-http-test-ca")
                .build();
    }

    private static HeldCertificate leafSignedBy(HeldCertificate ca, String hostname) {
        return new HeldCertificate.Builder()
                .signedBy(ca)
                .commonName(hostname)
                .addSubjectAlternativeName(hostname)
                .build();
    }

    /**
     * A trust manager that is deliberately <em>not</em> an {@link X509ExtendedTrustManager}, used to
     * prove that {@code relaxHostnameVerification} passes such entries through untouched.
     */
    private static final class PlainTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // no-op: never consulted by these tests
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // no-op: never consulted by these tests
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}

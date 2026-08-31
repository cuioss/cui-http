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

import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SecureSSLContextProvider} class.
 */
@EnableTestLogger
@DisplayName("Tests SecureSSLContextProvider functionality")
class SecureSSLContextProviderTest {

    @Test
    @DisplayName("Should define correct TLS version constants")
    void shouldDefineCorrectConstants() {
        assertEquals("TLSv1.2", SecureSSLContextProvider.TLS_V1_2);
        assertEquals("TLSv1.3", SecureSSLContextProvider.TLS_V1_3);
        assertEquals("TLS", SecureSSLContextProvider.TLS);
        assertEquals(SecureSSLContextProvider.TLS_V1_2, SecureSSLContextProvider.DEFAULT_TLS_VERSION);
    }

    @Test
    @DisplayName("Should have correct allowed TLS versions")
    void shouldHaveCorrectAllowedVersions() {
        assertEquals(3, SecureSSLContextProvider.ALLOWED_TLS_VERSIONS.size());
        assertTrue(SecureSSLContextProvider.ALLOWED_TLS_VERSIONS.contains(SecureSSLContextProvider.TLS_V1_2));
        assertTrue(SecureSSLContextProvider.ALLOWED_TLS_VERSIONS.contains(SecureSSLContextProvider.TLS_V1_3));
        assertTrue(SecureSSLContextProvider.ALLOWED_TLS_VERSIONS.contains(SecureSSLContextProvider.TLS));
    }

    @ParameterizedTest
    @MethodSource("allowedTlsVersions")
    @DisplayName("Every allowed version is accepted as a minimum and reported back verbatim")
    void shouldAcceptEveryAllowedVersionAsMinimum(String minimum) {
        SecureSSLContextProvider secureSSLContextProvider = new SecureSSLContextProvider(minimum);

        assertEquals(minimum, secureSSLContextProvider.minimumTlsVersion());
    }

    /**
     * Derived from the production constant rather than restated as a literal, so a version added to
     * {@link SecureSSLContextProvider#ALLOWED_TLS_VERSIONS} is covered automatically instead of
     * silently escaping this test.
     *
     * @return every allowed minimum-TLS-version token
     */
    static Stream<String> allowedTlsVersions() {
        return SecureSSLContextProvider.ALLOWED_TLS_VERSIONS.stream();
    }

    @Test
    @DisplayName("Should report TLS 1.2 as the default minimum")
    void shouldReportDefaultMinimum() {
        assertEquals(SecureSSLContextProvider.TLS_V1_2, new SecureSSLContextProvider().minimumTlsVersion());
    }

    @Test
    @DisplayName("Should report TLS 1.3 as the minimum when configured with TLS 1.3")
    void shouldReportTls13Minimum() {
        SecureSSLContextProvider secureSSLContextProvider =
                new SecureSSLContextProvider(SecureSSLContextProvider.TLS_V1_3);

        assertEquals(SecureSSLContextProvider.TLS_V1_3, secureSSLContextProvider.minimumTlsVersion());
    }

    @Test
    @DisplayName("Should enable TLS 1.2 and 1.3 when minimum is TLS 1.2")
    void shouldEnableTls12AndTls13ForDefaultMinimum() {
        String[] protocols = new SecureSSLContextProvider().getEnabledProtocols();

        assertArrayEquals(
                new String[]{SecureSSLContextProvider.TLS_V1_2, SecureSSLContextProvider.TLS_V1_3},
                protocols);
    }

    @Test
    @DisplayName("Should enable TLS 1.2 and 1.3 when minimum is generic TLS")
    void shouldEnableTls12AndTls13ForGenericTls() {
        String[] protocols = new SecureSSLContextProvider(SecureSSLContextProvider.TLS).getEnabledProtocols();

        assertArrayEquals(
                new String[]{SecureSSLContextProvider.TLS_V1_2, SecureSSLContextProvider.TLS_V1_3},
                protocols);
    }

    @Test
    @DisplayName("Should enable only TLS 1.3 when minimum is TLS 1.3")
    void shouldEnableOnlyTls13ForTls13Minimum() {
        String[] protocols = new SecureSSLContextProvider(SecureSSLContextProvider.TLS_V1_3).getEnabledProtocols();

        assertArrayEquals(new String[]{SecureSSLContextProvider.TLS_V1_3}, protocols);
    }

    /**
     * The floor invariant, expressed positively against the surviving API: whatever minimum is
     * configured, the enabled protocol list is non-empty and contains only concrete TLS 1.2 / 1.3
     * versions. Any pre-1.2 protocol is excluded by construction, because nothing outside that pair
     * can appear in the list.
     */
    @Test
    @DisplayName("Enabled protocols are always a non-empty subset of the concrete secure versions")
    void enabledProtocolsAreAlwaysSecureVersions() {
        Set<String> concreteSecureVersions =
                Set.of(SecureSSLContextProvider.TLS_V1_2, SecureSSLContextProvider.TLS_V1_3);

        for (String minimum : SecureSSLContextProvider.ALLOWED_TLS_VERSIONS) {
            String[] protocols = new SecureSSLContextProvider(minimum).getEnabledProtocols();

            assertTrue(protocols.length > 0,
                    "Enabled protocols for minimum " + minimum + " must never be empty");
            for (String protocol : protocols) {
                assertTrue(concreteSecureVersions.contains(protocol),
                        "Enabled protocols for minimum " + minimum + " must contain only TLS 1.2/1.3, but had "
                                + protocol);
            }
        }
    }

    @Test
    @DisplayName("Should create secure SSL context with default minimum")
    void shouldCreateSecureSSLContextWithDefaultMinimum() throws Exception {
        // When: Creating a secure SSL context with default minimum
        SecureSSLContextProvider secureSSLContextProvider = new SecureSSLContextProvider();
        SSLContext sslContext = secureSSLContextProvider.createSecureSSLContext();

        // Then: The context should not be null
        assertNotNull(sslContext, "SSL context should not be null");

        // And: The protocol should be the default TLS version
        assertEquals(SecureSSLContextProvider.TLS_V1_2, sslContext.getProtocol(),
                "SSL context should use the default TLS version");
    }

    @Test
    @DisplayName("Should create secure SSL context with TLS 1.3 minimum")
    void shouldCreateSecureSSLContextWithTls13Minimum() throws Exception {
        // When: Creating a secure SSL context with TLS 1.3 minimum
        SecureSSLContextProvider secureSSLContextProvider = new SecureSSLContextProvider(SecureSSLContextProvider.TLS_V1_3);
        SSLContext sslContext = secureSSLContextProvider.createSecureSSLContext();

        // Then: The context should not be null
        assertNotNull(sslContext, "SSL context should not be null");

        // And: The protocol should be TLS 1.3
        assertEquals(SecureSSLContextProvider.TLS_V1_3, sslContext.getProtocol(),
                "SSL context should use TLS 1.3");
    }

    @Test
    @DisplayName("Should create hostname-relaxed SSL context honoring the configured minimum")
    void shouldCreateHostnameRelaxedSSLContext() {
        SecureSSLContextProvider provider = new SecureSSLContextProvider(SecureSSLContextProvider.TLS_V1_3);

        SSLContext relaxed = provider.createHostnameRelaxedSSLContext();

        assertAll("hostname-relaxed context",
                () -> assertNotNull(relaxed, "Hostname-relaxed context should not be null"),
                () -> assertEquals(SecureSSLContextProvider.TLS_V1_3, relaxed.getProtocol(),
                        "Hostname-relaxed context must use the configured minimum TLS version"),
                () -> assertNotNull(relaxed.getSocketFactory(),
                        "Hostname-relaxed context must be initialized and usable"));
    }

    @Test
    @DisplayName("Should create a distinct instance per hostname-relaxed context request")
    void shouldCreateDistinctHostnameRelaxedContexts() {
        SecureSSLContextProvider provider = new SecureSSLContextProvider();

        SSLContext relaxed = provider.createHostnameRelaxedSSLContext();

        assertAll("hostname-relaxed context identity",
                () -> assertNotSame(relaxed, provider.createHostnameRelaxedSSLContext(),
                        "Each call must yield a fresh context"),
                () -> assertNotSame(relaxed, provider.createSecureSSLContext(),
                        "The relaxed context must never be the strict context instance"),
                () -> assertEquals(SecureSSLContextProvider.TLS_V1_2, relaxed.getProtocol(),
                        "The default minimum TLS version must be honored"));
    }

    @Test
    @DisplayName("Should throw exception for invalid minimum TLS version")
    void shouldThrowExceptionForInvalidMinimumTlsVersion() {
        assertThrows(IllegalArgumentException.class, () -> new SecureSSLContextProvider("invalid"));
    }

    @Test
    @DisplayName("Should return caller-provided SSL context unchanged, preserving its trust material")
    void shouldReturnCallerProvidedContextUnchanged() throws Exception {
        // Given: a caller-provided TLS 1.2 context and a stricter (TLS 1.3 minimum) provider
        SecureSSLContextProvider provider = new SecureSSLContextProvider();
        SSLContext baseContext = provider.createSecureSSLContext();
        SecureSSLContextProvider strictProvider = new SecureSSLContextProvider(SecureSSLContextProvider.TLS_V1_3);

        // When: resolving the caller-provided context with the strict provider
        SSLContext result = strictProvider.getOrCreateSecureSSLContext(baseContext);

        // Then: the same context is returned unchanged - the provider must never silently swap out a
        // caller-supplied context (which could carry TrustManager/KeyManager mTLS material). The TLS
        // floor is enforced separately by HttpHandler via SSLParameters pinning.
        assertSame(baseContext, result,
                "Caller-provided context must be returned unchanged to preserve its trust/key material");
    }

    @Test
    @DisplayName("Should validate and return secure SSLContext")
    void shouldValidateAndReturnSecureSSLContext() throws Exception {
        // Given: A SecureSSLContextProvider instance and a secure SSLContext
        SecureSSLContextProvider secureSSLContextProvider = new SecureSSLContextProvider();
        SSLContext secureContext = SSLContext.getInstance(SecureSSLContextProvider.TLS_V1_2);
        secureContext.init(null, null, null);

        // When: Validating the secure context
        SSLContext result = secureSSLContextProvider.getOrCreateSecureSSLContext(secureContext);

        // Then: The same context should be returned
        assertSame(secureContext, result, "Should return the same context when it's secure");
    }

    @Test
    @DisplayName("Should return provided context even when its protocol is below the configured minimum")
    void shouldReturnProvidedContextEvenWhenProtocolBelowMinimum() throws Exception {
        // Given: A SecureSSLContextProvider instance with TLS 1.3 as minimum and a TLS 1.2 context
        SecureSSLContextProvider secureSSLContextProvider = new SecureSSLContextProvider(SecureSSLContextProvider.TLS_V1_3);
        SSLContext providedContext = SSLContext.getInstance(SecureSSLContextProvider.TLS_V1_2);
        providedContext.init(null, null, null);

        // When: resolving the provided context
        SSLContext result = secureSSLContextProvider.getOrCreateSecureSSLContext(providedContext);

        // Then: the provider must not swap out the caller-supplied context based on its protocol
        // string; the wire-level TLS floor is enforced by HttpHandler via SSLParameters pinning.
        assertSame(providedContext, result,
                "Provider must return the caller-supplied context unchanged regardless of its protocol string");
    }

    @Test
    @DisplayName("Should create new SSLContext when null is provided")
    void shouldCreateNewSSLContextWhenNullIsProvided() {
        // Given: A SecureSSLContextProvider instance
        SecureSSLContextProvider secureSSLContextProvider = new SecureSSLContextProvider();

        // When: Validating a null context
        SSLContext result = secureSSLContextProvider.getOrCreateSecureSSLContext(null);

        // Then: A new context should be created
        assertNotNull(result, "Should create a new context when null is provided");
        assertEquals(SecureSSLContextProvider.TLS_V1_2, result.getProtocol(), "New context should use TLS 1.2");
    }
}

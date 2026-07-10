/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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

import de.cuioss.tools.collect.CollectionLiterals;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.*;
import java.util.Set;

/**
 * Provider for secure SSL contexts used in HTTPS communications.
 * <p>
 * This class enforces secure TLS versions when establishing HTTPS connections.
 * It ensures that only modern, secure TLS protocols are used:
 * <ul>
 *   <li>TLS 1.2 - Minimum recommended version</li>
 *   <li>TLS 1.3 - Preferred when available</li>
 * </ul>
 * <p>
 * The class prevents the use of insecure, deprecated protocols:
 * <ul>
 *   <li>TLS 1.0 - Deprecated due to security vulnerabilities</li>
 *   <li>TLS 1.1 - Deprecated due to security vulnerabilities</li>
 *   <li>SSL 3.0 - Deprecated due to security vulnerabilities (POODLE attack)</li>
 * </ul>
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/cui-jwt-validation/tree/main/doc/specification/security.adoc">Security Specification</a>
 *
 * @param minimumTlsVersion The minimum TLS version that is considered secure for this instance.
 * @author Oliver Wolff
 * @since 1.0
 */
public record SecureSSLContextProvider(String minimumTlsVersion) {

    private static final CuiLogger LOGGER = new CuiLogger(SecureSSLContextProvider.class);

    /**
     * TLS version 1.2 - Secure
     */
    public static final String TLS_V1_2 = "TLSv1.2";

    /**
     * TLS version 1.3 - Secure
     */
    public static final String TLS_V1_3 = "TLSv1.3";

    /**
     * Generic TLS - Secure if implemented correctly by the JVM
     */
    public static final String TLS = "TLS";

    /**
     * Default secure TLS version to use when creating a new context
     */
    public static final String DEFAULT_TLS_VERSION = TLS_V1_2;

    /**
     * TLS version 1.0 - Insecure, deprecated.
     * <p>
     * Uses the canonical JSSE protocol name {@code "TLSv1"} (not {@code "TLSv1.0"}), so that
     * {@link #FORBIDDEN_TLS_VERSIONS} can match the protocol string reported by a real TLS 1.0
     * {@link SSLContext}.
     * </p>
     */
    public static final String TLS_V1_0 = "TLSv1";

    /**
     * TLS version 1.1 - Insecure, deprecated
     */
    public static final String TLS_V1_1 = "TLSv1.1";

    /**
     * SSL version 3 - Insecure, deprecated
     */
    public static final String SSL_V3 = "SSLv3";

    /**
     * Set of allowed (secure) TLS versions
     */
    public static final Set<String> ALLOWED_TLS_VERSIONS = CollectionLiterals.immutableSet(TLS_V1_2, TLS_V1_3, TLS);

    /**
     * Set of forbidden (insecure) TLS versions
     */
    public static final Set<String> FORBIDDEN_TLS_VERSIONS = CollectionLiterals.immutableSet(TLS_V1_0, TLS_V1_1, SSL_V3);

    /**
     * Creates a new SecureSSLContextProvider instance with the default minimum TLS version (TLS 1.2).
     */
    public SecureSSLContextProvider() {
        this(DEFAULT_TLS_VERSION);
    }

    /**
     * Creates a new SecureSSLContextProvider instance with the specified minimum TLS version.
     *
     * @param minimumTlsVersion the minimum TLS version to consider secure
     * @throws IllegalArgumentException if the specified version is not in the allowed set
     */
    public SecureSSLContextProvider {
        if (!ALLOWED_TLS_VERSIONS.contains(minimumTlsVersion)) {
            throw new IllegalArgumentException("Minimum TLS version must be one of the allowed versions: " + ALLOWED_TLS_VERSIONS);
        }
    }

    /**
     * Checks if the given protocol is a secure TLS version according to the minimum version set for this instance.
     * <p>
     * For TLS_V1_2 and TLS_V1_3, the comparison is based on the version number.
     * For TLS (generic), it's considered secure if it's in the allowed versions set.
     *
     * @param protocol the protocol to check
     * @return true if the protocol is a secure TLS version, false otherwise
     */
    public boolean isSecureTlsVersion(@Nullable String protocol) {
        if (protocol == null) {
            return false;
        }

        if (!ALLOWED_TLS_VERSIONS.contains(protocol)) {
            return false;
        }

        // If the minimum is TLS_V1_3, only TLS_V1_3 and TLS are considered secure
        if (TLS_V1_3.equals(minimumTlsVersion)) {
            return TLS_V1_3.equals(protocol) || TLS.equals(protocol);
        }

        // If the minimum is TLS_V1_2, all allowed versions are secure
        return true;
    }

    /**
     * Returns the concrete TLS protocol versions that must be enabled on a connection
     * to enforce this instance's minimum version as a hard floor.
     * <p>
     * An {@link SSLContext} created with a protocol string only governs the context's
     * <em>default</em> protocol object; it does not by itself prevent the JVM from
     * negotiating an older enabled protocol. Applying these versions via
     * {@code SSLParameters.setProtocols(...)} on the client makes the floor real.
     * <ul>
     *   <li>Minimum {@link #TLS_V1_3} → {@code [TLSv1.3]}</li>
     *   <li>Minimum {@link #TLS_V1_2} or generic {@link #TLS} → {@code [TLSv1.2, TLSv1.3]}</li>
     * </ul>
     * <p>
     * The generic {@link #TLS} value deliberately enforces the same {@code [TLSv1.2, TLSv1.3]}
     * floor as an explicit 1.2 minimum - this is a security improvement over JVM-default
     * negotiation (which could include older enabled protocols). A consequence is that a caller
     * <strong>cannot</strong> express a "TLS&nbsp;1.3-only" policy via the generic {@code "TLS"}
     * string; select {@link #TLS_V1_3} explicitly for that.
     *
     * @return the ordered array of enabled protocol versions (never empty)
     */
    public String[] getEnabledProtocols() {
        if (TLS_V1_3.equals(minimumTlsVersion)) {
            return new String[]{TLS_V1_3};
        }
        // TLS_V1_2 and generic TLS both enforce a 1.2 floor with 1.3 available
        return new String[]{TLS_V1_2, TLS_V1_3};
    }

    /**
     * Creates a secure SSLContext configured with the minimum TLS version set for this instance.
     * <p>
     * This method:
     * <ol>
     *   <li>Creates an SSLContext instance with the secure protocol version</li>
     *   <li>Initializes a TrustManagerFactory with the default algorithm</li>
     *   <li>Configures the TrustManagerFactory to use the default trust store</li>
     *   <li>Initializes the SSLContext with the trust managers and a secure random source</li>
     * </ol>
     * <p>
     * The resulting SSLContext is configured to trust the certificates in the JVM's default trust store
     * and does not perform client authentication (no KeyManager is provided).
     *
     * @return a configured SSLContext that uses a secure TLS protocol version
     * @throws NoSuchAlgorithmException if the specified protocol or trust manager algorithm is not available
     * @throws KeyStoreException        if there's an issue accessing the default trust store
     * @throws KeyManagementException   if there's an issue initializing the SSLContext
     */
    public SSLContext createSecureSSLContext() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        // Create a secure SSL context with the minimum TLS version
        SSLContext secureContext = SSLContext.getInstance(minimumTlsVersion);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        secureContext.init(null, trustManagers, new SecureRandom());
        return secureContext;
    }

    /**
     * Returns an SSLContext for HTTPS, honoring a caller-supplied context as-is.
     * <p>
     * This method:
     * <ol>
     *   <li>If the provided SSLContext is null, creates a new secure SSLContext backed by the
     *       JVM default trust store</li>
     *   <li>If the provided SSLContext is not null, returns it <strong>unchanged</strong> so that
     *       any custom {@code TrustManager}/{@code KeyManager} (including mutual-TLS identity
     *       material) is preserved</li>
     *   <li>If a secure context cannot be created (null case only), fails hard with
     *       {@link IllegalStateException}</li>
     * </ol>
     * <p>
     * The context's reported protocol string is intentionally <em>not</em> used to accept or reject
     * a caller-supplied context. That string is unreliable (e.g. {@code SSLContext.getDefault()}
     * reports {@code "Default"}, and a {@code "TLSv1.2"} context can still negotiate TLS 1.3), and
     * rejecting on it would silently drop the caller's trust material. The minimum version is
     * instead enforced on the wire by {@link HttpHandler}, which pins the enabled protocols via
     * {@link #getEnabledProtocols()} using {@code SSLParameters.setProtocols(...)}. That pinning is
     * the real TLS floor, so an older protocol simply cannot be negotiated regardless of the
     * provided context's default protocol object.
     *
     * @param sslContext the SSLContext to use, may be null
     * @return the caller-supplied context unchanged, or a newly created secure context when null
     *         (never null)
     * @throws IllegalStateException if a secure SSLContext cannot be created (null-input case)
     */
    public SSLContext getOrCreateSecureSSLContext(@Nullable SSLContext sslContext) {
        if (sslContext != null) {
            // Trust the caller's context verbatim. Its TrustManager/KeyManager (e.g. mTLS identity)
            // must not be discarded; the TLS floor is enforced separately via SSLParameters pinning
            // in HttpHandler (see getEnabledProtocols()).
            LOGGER.debug("Using caller-provided SSL context (protocol=%s); TLS floor is enforced "
                    + "on the wire via SSLParameters pinning", sslContext.getProtocol());
            return sslContext;
        }
        try {
            // If no context provided, create a new secure one
            SSLContext secureContext = createSecureSSLContext();
            LOGGER.debug("No SSL context provided, created secure context with minimum TLS version: %s", minimumTlsVersion);
            return secureContext;
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            // If a secure context cannot be created, we must fail hard.
            throw new IllegalStateException("Failed to create a secure SSL context", e);
        }
    }
}

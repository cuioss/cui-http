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

import de.cuioss.http.client.HttpLogMessages;
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
 * This class enforces secure TLS versions when establishing connections to JWKS endpoints
 * and other services. It ensures that only modern, secure TLS protocols are used:
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
     * TLS version 1.0 - Insecure, deprecated
     */
    public static final String TLS_V1_0 = "TLSv1.0";

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
     * Validates the provided SSLContext and returns a secure SSLContext.
     * <p>
     * This method:
     * <ol>
     *   <li>If the provided SSLContext is null, creates a new secure SSLContext</li>
     *   <li>If the provided SSLContext is not null, checks if its protocol is secure</li>
     *   <li>If the protocol is secure, returns the provided SSLContext</li>
     *   <li>If the protocol is not secure, creates a new secure SSLContext</li>
     *   <li>If an exception occurs during validation or creation, falls back to the provided SSLContext or the default SSLContext</li>
     * </ol>
     *
     * @param sslContext the SSLContext to validate, may be null
     * @return a secure SSLContext, either the validated input or a newly created one
     */
    public SSLContext getOrCreateSecureSSLContext(@Nullable SSLContext sslContext) {
        try {
            if (sslContext != null) {
                // Validate the provided SSL context
                String protocol = sslContext.getProtocol();
                LOGGER.debug(HttpLogMessages.DEBUG.SSL_CONTEXT_PROTOCOL.format(protocol));

                // Check if the protocol is secure according to the configured TLS versions
                if (isSecureTlsVersion(protocol)) {
                    // The provided context was secure and is being used
                    LOGGER.debug(HttpLogMessages.DEBUG.SSL_USING_PROVIDED_CONTEXT.format(protocol));
                    return sslContext;
                }

                // If not secure, create a new secure context
                LOGGER.warn(HttpLogMessages.WARN.SSL_INSECURE_PROTOCOL.format(protocol));
                SSLContext secureContext = createSecureSSLContext();
                LOGGER.debug(HttpLogMessages.DEBUG.SSL_CREATED_SECURE_CONTEXT.format(minimumTlsVersion));
                return secureContext;
            } else {
                // If no context provided, create a new secure one
                SSLContext secureContext = createSecureSSLContext();
                LOGGER.debug(HttpLogMessages.DEBUG.SSL_NO_CONTEXT_PROVIDED.format(minimumTlsVersion));
                return secureContext;
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            // If a secure context cannot be created, we must fail hard.
            throw new IllegalStateException("Failed to create a secure SSL context", e);
        }
    }
}

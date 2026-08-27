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

import de.cuioss.http.client.HttpLogMessages;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * HTTP client wrapper with builder API and SSL support.
 * <p>
 * Wraps Java's {@link HttpClient} with builder-based configuration.
 * Creates secure SSL contexts automatically for HTTPS URLs.
 * Validates URIs and configures timeouts at build time.
 * <p>
 * Thread-safe and immutable after construction.
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Basic HTTPS request
 * HttpHandler handler = HttpHandler.builder()
 *     .uri("https://api.example.com/users")
 *     .connectionTimeoutSeconds(5)
 *     .readTimeoutSeconds(10)
 *     .build();
 *
 * // Execute a GET request via the shared client and the pre-configured request builder
 * HttpClient client = handler.createHttpClient();
 * HttpResponse&lt;String&gt; response = client.send(
 *     handler.requestBuilder().GET().build(),
 *     HttpResponse.BodyHandlers.ofString());
 * if (response.statusCode() == 200) {
 *     String body = response.body();
 *     // Process response
 * }
 *
 * // Or, for a lightweight reachability check
 * HttpStatusFamily status = handler.pingGet();
 *
 * // Custom SSL context
 * SSLContext customSSL = mySecureSSLProvider.getSSLContext();
 * HttpHandler secureHandler = HttpHandler.builder()
 *     .uri("https://secure.example.com/api")
 *     .sslContext(customSSL)
 *     .build();
 *
 * // URI object
 * URI apiEndpoint = URI.create("https://example.com/api/v1/data");
 * HttpHandler uriHandler = HttpHandler.builder()
 *     .uri(apiEndpoint)
 *     .build();
 * </pre>
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>URI must be valid and convertible to URL (validated at build time)</li>
 *   <li>SSL context created automatically for HTTPS if not provided</li>
 *   <li>Default timeout: 10 seconds for both connection and read</li>
 *   <li>Schemeless string URLs default to HTTPS</li>
 * </ul>
 *
 * <h3>Scheme policy (fail-secure)</h3>
 * <p>HTTPS is required by default. {@link HttpHandlerBuilder#build()} rejects an {@code http://}
 * URI with {@link IllegalArgumentException} unless {@link HttpHandlerBuilder#allowInsecureHttp(boolean)}
 * is set (default {@code false}); when permitted, a cleartext handler is built and a WARN is logged.
 * Any scheme other than {@code http}/{@code https} is always rejected. This applies uniformly to
 * {@code uri(URI)}, {@code url(URL)}, and string inputs.</p>
 *
 * <h3>TLS floor</h3>
 * <p>For HTTPS, the configured minimum TLS version is pinned on the wire via
 * {@link SSLParameters#setProtocols}. A minimum of TLS&nbsp;1.2 and the generic {@code "TLS"}
 * context both enforce {@code [TLSv1.2, TLSv1.3]} (deliberate); a 1.3 minimum enforces
 * {@code [TLSv1.3]}. A caller cannot express "TLS&nbsp;1.3-only" via the generic {@code "TLS"}
 * string. See {@link SecureSSLContextProvider}.</p>
 *
 * <h3>Hostname verification</h3>
 * <p>TLS hostname verification is <strong>on by default</strong>
 * ({@link HttpHandlerBuilder#verifyHostname(boolean)}, default {@code true}). Setting it to
 * {@code false} is a deliberate opt-in that skips <em>only</em> the match between the peer
 * certificate's identity (SAN / CN) and the connected host. Certificate-chain trust, validity
 * period, revocation posture, and algorithm constraints remain fully enforced, and a WARN
 * ({@code HTTP-116}) is logged for every handler built this way.</p>
 * <p>The relaxation is confined to the default-trust-store context this class derives itself, so
 * combining {@code verifyHostname(false)} with a caller-supplied
 * {@link HttpHandlerBuilder#sslContext(SSLContext)} is rejected at {@link HttpHandlerBuilder#build()}
 * time with {@link IllegalArgumentException}. A context {@link #asBuilder()} carries over from a
 * handler's own derivation is not caller-supplied and round-trips cleanly.</p>
 * <pre>
 * // Opt in to hostname relaxation (e.g. connecting to an internal host by IP)
 * HttpHandler relaxed = HttpHandler.builder()
 *     .uri("https://10.0.0.7/api")
 *     .verifyHostname(false)
 *     .build();
 * </pre>
 *
 * @since 1.0
 * @see HttpClient
 * @see SecureSSLContextProvider
 * @see HttpStatusFamily
 */
@EqualsAndHashCode
@ToString
@Builder(builderClassName = "HttpHandlerBuilder", access = AccessLevel.PRIVATE)
public final class HttpHandler {

    private static final CuiLogger LOGGER = new CuiLogger(HttpHandler.class);

    /**
     * Pre-compiled pattern for detecting URLs with scheme.
     * Matches RFC 3986 scheme format: scheme:remainder
     */
    private static final Pattern URL_SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    public static final int DEFAULT_CONNECTION_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 10;

    @Getter
    private final URI uri;
    // Excluded from equals/hashCode: java.net.URL#equals/hashCode perform blocking DNS
    // resolution, and the URL is fully derivable from the (included) uri.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter
    private final URL url;
    // Excluded from equals/hashCode: SSLContext has no value semantics (identity equality),
    // which would make two identically-configured handlers never equal. The TLS floor that
    // actually matters for configuration identity is captured by secureSSLContextProvider.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter
    private final @Nullable SSLContext sslContext;
    // Retained so asBuilder() can preserve a caller-configured TLS floor. For HTTP
    // handlers this holds the default provider and is never used (no TLS). It is a value
    // (record) type, so it participates in equals/hashCode to distinguish handlers that
    // differ only by their configured TLS floor.
    @ToString.Exclude
    private final SecureSSLContextProvider secureSSLContextProvider;
    @Getter
    private final int connectionTimeoutSeconds;
    @Getter
    private final int readTimeoutSeconds;
    /**
     * Whether this handler was built with the cleartext-HTTP opt-in
     * ({@link HttpHandlerBuilder#allowInsecureHttp(boolean)}). A build-time scheme policy retained
     * so {@link #asBuilder()} preserves the opt-in. It is part of the handler's configuration
     * identity ({@code equals}/{@code hashCode}) but excluded from {@code toString}.
     *
     * @return {@code true} if cleartext HTTP was explicitly permitted for this handler
     */
    @ToString.Exclude
    @Getter
    private final boolean allowInsecureHttp;
    /**
     * Whether TLS hostname/endpoint-identification verification is performed for this handler
     * ({@code true} by default). When {@code false}, the handler was built through the explicit
     * {@link HttpHandlerBuilder#verifyHostname(boolean)} opt-in and its TLS peer identity is not
     * checked against the connected host; certificate-chain trust, validity period, and algorithm
     * constraints remain enforced. It is part of the handler's configuration identity
     * ({@code equals}/{@code hashCode}) and is rendered in {@code toString}.
     *
     * @return {@code true} if TLS hostname verification is active for this handler
     */
    @Getter
    private final boolean verifyHostname;
    // Build-time provenance only: records whether the SSLContext was supplied by the caller rather
    // than derived by this class. It is not configuration - two handlers that differ only in how
    // their (identical) context was obtained are the same configuration - so it is excluded from
    // equals/hashCode and toString, and no getter is exposed. asBuilder() reads it to decide
    // whether to re-inject the context through the caller-facing or the derived seam.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final boolean sslContextCallerSupplied;
    // Excluded from equals/hashCode/toString: HttpClient has identity equality (two
    // identically-configured handlers hold distinct client instances) and is derived from
    // the configuration above.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final HttpClient httpClient;

    // Constructor for HTTP URIs (no SSL context needed)
    private HttpHandler(URI uri, URL url, int connectionTimeoutSeconds, int readTimeoutSeconds) {
        this.uri = uri;
        this.url = url;
        this.sslContext = null;
        // Unused for HTTP; holds a default so asBuilder() has a non-null value to carry
        this.secureSSLContextProvider = new SecureSSLContextProvider();
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        // Reached only via the opt-in, so this handler was explicitly permitted to use cleartext.
        this.allowInsecureHttp = true;
        // No TLS is involved, so hostname verification keeps its secure default and no SSLContext
        // was consumed from the caller.
        this.verifyHostname = true;
        this.sslContextCallerSupplied = false;

        // Create the HttpClient for HTTP
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
                .build();
    }

    // Constructor for HTTPS URIs (SSL context required)
    private HttpHandler(URI uri, URL url, SSLContext sslContext, SecureSSLContextProvider secureSSLContextProvider,
            int connectionTimeoutSeconds, int readTimeoutSeconds, boolean allowInsecureHttp,
            boolean verifyHostname, boolean sslContextCallerSupplied) {
        this.uri = uri;
        this.url = url;
        this.sslContext = sslContext;
        this.secureSSLContextProvider = secureSSLContextProvider;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.allowInsecureHttp = allowInsecureHttp;
        this.verifyHostname = verifyHostname;
        this.sslContextCallerSupplied = sslContextCallerSupplied;

        // JDK 11+ HttpClient enables hostname verification by default.
        // Pin the enabled TLS protocols so the configured minimum version is a hard
        // floor on the wire, not merely the context's default protocol object.
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(secureSSLContextProvider.getEnabledProtocols());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .build();
    }

    public static HttpHandlerBuilder builder() {
        return new HttpHandlerBuilder();
    }

    /**
     * Creates a pre-configured {@link HttpRequest.Builder} for the URI contained in this handler.
     * The builder is configured with the read timeout from this handler.
     *
     * @return A pre-configured {@link HttpRequest.Builder}
     */
    public HttpRequest.Builder requestBuilder() {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(readTimeoutSeconds));
    }

    /**
     * Creates a pre-configured {@link HttpHandlerBuilder} with the same configuration as this handler.
     * The builder is configured with the connection timeout, read timeout and sslContext from this handler.
     *
     * <p>This method allows creating a new builder based on the current handler's configuration,
     * which can be used to create a new handler with modified URL.</p>
     *
     * <p>A context this handler <em>derived</em> for itself is re-injected through the
     * package-private {@link HttpHandlerBuilder#derivedSslContext(SSLContext)} seam, so it does not
     * become "caller-supplied" on the returned builder. This is what lets a handler built with
     * {@code verifyHostname(false)} round-trip through {@code asBuilder().build()} without tripping
     * the mutual-exclusion rejection. A context that genuinely came from the caller is re-injected
     * through {@link HttpHandlerBuilder#sslContext(SSLContext)} and keeps that provenance.</p>
     *
     * @return A pre-configured {@link HttpHandlerBuilder} with the same timeouts as this handler
     */
    public HttpHandlerBuilder asBuilder() {
        HttpHandlerBuilder handlerBuilder = builder()
                .connectionTimeoutSeconds(connectionTimeoutSeconds)
                .readTimeoutSeconds(readTimeoutSeconds)
                .tlsVersions(secureSSLContextProvider)
                .allowInsecureHttp(allowInsecureHttp)
                .verifyHostname(verifyHostname);
        return sslContextCallerSupplied
                ? handlerBuilder.sslContext(sslContext)
                : handlerBuilder.derivedSslContext(sslContext);
    }

    /**
     * Pings the URI using the HEAD method and returns the HTTP status code.
     *
     * @return The HTTP status code family, or {@link HttpStatusFamily#UNKNOWN} if an error occurred
     */
    // HttpClient implements AutoCloseable in Java 17 but doesn't need to be closed
    @SuppressWarnings("try")
    public HttpStatusFamily pingHead() {
        return pingWithMethod("HEAD", HttpRequest.BodyPublishers.noBody());
    }

    /**
     * Pings the URI using the GET method and returns the HTTP status code.
     *
     * @return The HTTP status code family, or {@link HttpStatusFamily#UNKNOWN} if an error occurred
     */
    // HttpClient implements AutoCloseable in Java 17 but doesn't need to be closed
    @SuppressWarnings("try")
    public HttpStatusFamily pingGet() {
        return pingWithMethod("GET", HttpRequest.BodyPublishers.noBody());
    }

    /**
     * Pings the URI using the specified HTTP method and returns the HTTP status code.
     *
     * @param method The HTTP method to use (e.g., "HEAD", "GET")
     * @param bodyPublisher The body publisher to use for the request
     * @return The HTTP status code family, or {@link HttpStatusFamily#UNKNOWN} if an error occurred
     */
    // HttpClient implements AutoCloseable in Java 17 but doesn't need to be closed
    @SuppressWarnings("try")
    private HttpStatusFamily pingWithMethod(String method, HttpRequest.BodyPublisher bodyPublisher) {
        try {
            HttpClient client = createHttpClient();
            HttpRequest request = requestBuilder()
                    .method(method, bodyPublisher)
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return HttpStatusFamily.fromStatusCode(response.statusCode());
        } catch (IOException e) {
            LOGGER.warn(e, HttpLogMessages.WARN.HTTP_PING_IO_ERROR, uri, e.getMessage());
            return HttpStatusFamily.UNKNOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn(HttpLogMessages.WARN.HTTP_PING_INTERRUPTED, uri, e.getMessage());
            return HttpStatusFamily.UNKNOWN;
        }
    }

    /**
     * Returns the configured {@link HttpClient} for making HTTP requests.
     * <p>
     * Despite the {@code create} prefix (retained for backward compatibility), this does not create
     * a new client per call: the client is created once during construction and this method returns
     * that same shared, thread-safe instance for every call.
     * </p>
     *
     * @return the shared {@link HttpClient} configured with SSL context and connection timeout
     */
    public HttpClient createHttpClient() {
        return httpClient;
    }

    /**
     * Builder for creating {@link HttpHandler} instances.
     */
    public static class HttpHandlerBuilder {
        private @Nullable URI uri;
        private @Nullable URL url;
        private @Nullable String urlString;
        private @Nullable SSLContext sslContext;
        private @Nullable SecureSSLContextProvider secureSSLContextProvider;
        private @Nullable Integer connectionTimeoutSeconds;
        private @Nullable Integer readTimeoutSeconds;
        private boolean allowInsecureHttp = false;
        private boolean verifyHostname = true;
        private boolean sslContextCallerSupplied = false;

        /**
         * Sets the URI as a string.
         * <p>
         * <strong>Resolution precedence:</strong> the source used at {@link #build()} time is chosen
         * in the order {@code uri(URI)} &gt; {@code url(URL)} &gt; string form. The string form is a
         * single slot shared with {@link #url(String)}, so the last of {@code uri(String)} /
         * {@code url(String)} wins, and a typed {@code uri(URI)} or {@code url(URL)} set on the same
         * builder takes precedence over it.
         * </p>
         *
         * @param uriString The string representation of the URI.
         *                  Must not be null or empty.
         * @return This builder instance.
         * @throws IllegalArgumentException if the URI string is null, empty, or malformed
         *                                  (thrown during the {@link #build()} method execution,
         *                                  not by this setter method)
         */
        public HttpHandlerBuilder uri(String uriString) {
            this.urlString = uriString;
            return this;
        }

        /**
         * Sets the URI directly.
         * <p>
         * Note: If both URI and URL are set, the URI takes precedence.
         * </p>
         *
         * @param uri The URI to be used for HTTP requests.
         *            Must not be null.
         * @return This builder instance.
         */
        public HttpHandlerBuilder uri(URI uri) {
            this.uri = uri;
            return this;
        }

        /**
         * Sets the URL as a string.
         * <p>
         * Note: This method is provided for backward compatibility.
         * Consider using {@link #uri(String)} instead.
         * </p>
         * <p>
         * This shares a single string slot with {@link #uri(String)} (last call wins) and has the
         * lowest resolution precedence; see {@link #uri(String)} for the full ordering.
         * </p>
         *
         * @param urlString The string representation of the URL.
         *                  Must not be null or empty.
         * @return This builder instance.
         * @throws IllegalArgumentException if the URL string is null, empty, or malformed
         *                                  (thrown during the {@link #build()} method execution,
         *                                  not by this setter method)
         */
        public HttpHandlerBuilder url(String urlString) {
            this.urlString = urlString;
            return this;
        }

        /**
         * Sets the URL directly.
         * <p>
         * Note: This method is provided for backward compatibility.
         * Consider using {@link #uri(URI)} instead.
         * </p>
         * <p>
         * If both URI and URL are set, the URI takes precedence.
         * </p>
         *
         * @param url The URL to be used for HTTP requests.
         *            Must not be null.
         * @return This builder instance.
         */
        public HttpHandlerBuilder url(URL url) {
            this.url = url;
            return this;
        }

        /**
         * Sets the SSL context to use for HTTPS connections.
         * <p>
         * If not set, a default secure SSL context will be created.
         * </p>
         * <p>
         * Passing a non-null context marks it as <em>caller-supplied</em>, which is mutually
         * exclusive with {@link #verifyHostname(boolean) verifyHostname(false)} and makes
         * {@link #build()} reject that combination. Passing {@code null} clears both the context and
         * the caller-supplied claim.
         * </p>
         *
         * @param sslContext The SSL context to use.
         * @return This builder instance.
         */
        public HttpHandlerBuilder sslContext(@Nullable SSLContext sslContext) {
            this.sslContext = sslContext;
            this.sslContextCallerSupplied = sslContext != null;
            return this;
        }

        /**
         * Re-injects an already-resolved SSL context without marking it caller-supplied.
         * <p>
         * This seam exists for {@link HttpHandler#asBuilder()}: the context it carries over was
         * <em>derived</em> by {@link HttpHandler} itself, not handed in by the caller, so it must not
         * assert the caller-supplied provenance that
         * {@link #verifyHostname(boolean) verifyHostname(false)} is rejected against. It is otherwise
         * identical to {@link #sslContext(SSLContext)}.
         * </p>
         *
         * @param sslContext The already-resolved SSL context to carry over.
         * @return This builder instance.
         */
        HttpHandlerBuilder derivedSslContext(@Nullable SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Sets the TLS versions configuration.
         *
         * @param secureSSLContextProvider The TLS versions configuration to use.
         * @return This builder instance.
         */
        public HttpHandlerBuilder tlsVersions(@Nullable SecureSSLContextProvider secureSSLContextProvider) {
            this.secureSSLContextProvider = secureSSLContextProvider;
            return this;
        }

        /**
         * Sets the connection timeout in seconds for HTTP requests.
         * <p>
         * If not set, a default timeout of 10 seconds will be used.
         * </p>
         *
         * @param connectionTimeoutSeconds The connection timeout in seconds.
         *                                Must be positive.
         * @return This builder instance.
         */
        public HttpHandlerBuilder connectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
            return this;
        }

        /**
         * Sets the read timeout in seconds for HTTP requests.
         * <p>
         * If not set, a default timeout of 10 seconds will be used.
         * </p>
         *
         * @param readTimeoutSeconds The read timeout in seconds.
         *                          Must be positive.
         * @return This builder instance.
         */
        public HttpHandlerBuilder readTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
            return this;
        }

        /**
         * Permits building a handler for a plaintext {@code http://} URI.
         * <p>
         * HTTPS is required by default (fail-secure): {@link #build()} rejects an {@code http://}
         * URI unless this flag is set. When enabled, a cleartext handler is built and a WARN is
         * logged recording the unencrypted connection. Schemes other than {@code http}/{@code https}
         * are always rejected. Default: {@code false}.
         * </p>
         *
         * @param allowInsecureHttp {@code true} to permit cleartext HTTP.
         * @return This builder instance.
         */
        public HttpHandlerBuilder allowInsecureHttp(boolean allowInsecureHttp) {
            this.allowInsecureHttp = allowInsecureHttp;
            return this;
        }

        /**
         * Controls whether the TLS hostname/endpoint-identification check is performed for HTTPS
         * connections. Default: {@code true}.
         * <p>
         * Setting this to {@code false} is a deliberate, narrowly scoped opt-in: the <strong>only</strong>
         * check that is skipped is the match between the peer certificate's identity (SAN / CN) and
         * the connected host. Certificate-chain trust against the JVM default trust store, validity
         * period, revocation posture, and algorithm constraints all remain fully enforced - see
         * {@link SecureSSLContextProvider#createHostnameRelaxedSSLContext()}. A WARN
         * ({@code HTTP-116}) is logged for every handler built this way.
         * </p>
         * <p>
         * The relaxation is confined to the default-trust-store context this class builds itself.
         * Combining {@code verifyHostname(false)} with a caller-supplied
         * {@link #sslContext(SSLContext)} is therefore rejected by {@link #build()} with
         * {@link IllegalArgumentException} - the relaxation cannot be silently applied to, or
         * silently dropped from, trust material this class does not own. A context that
         * {@link HttpHandler#asBuilder()} carries over from a handler's own derivation is not
         * caller-supplied and does not trip the rejection.
         * </p>
         *
         * @param verifyHostname {@code false} to skip TLS hostname verification.
         * @return This builder instance.
         */
        public HttpHandlerBuilder verifyHostname(boolean verifyHostname) {
            this.verifyHostname = verifyHostname;
            return this;
        }

        /**
         * Builds a new {@link HttpHandler} instance with the configured parameters.
         *
         * @return A new {@link HttpHandler} instance.
         * @throws IllegalArgumentException If any parameter is invalid, or if
         *                                  {@code verifyHostname(false)} is combined with a
         *                                  caller-supplied {@link #sslContext(SSLContext)}.
         */
        public HttpHandler build() {
            // The relaxation only applies to the default-trust-store context this class derives
            // itself; it must never be applied to (or silently dropped from) caller-owned trust
            // material. Keyed off the provenance flag, not off sslContext != null, so a context
            // asBuilder() re-injected via derivedSslContext(...) does not trip it.
            if (!verifyHostname && sslContextCallerSupplied) {
                throw new IllegalArgumentException("verifyHostname(false) cannot be combined with a "
                        + "caller-supplied sslContext(...); the hostname relaxation only applies to the "
                        + "default-trust-store context this builder derives. Either drop the custom "
                        + "sslContext(...) or keep verifyHostname(true).");
            }

            // Resolve the URI from the provided inputs
            resolveUri();

            // Validate connection timeout
            int actualConnectionTimeoutSeconds = connectionTimeoutSeconds != null ?
                    connectionTimeoutSeconds : DEFAULT_CONNECTION_TIMEOUT_SECONDS;
            if (actualConnectionTimeoutSeconds <= 0) {
                throw new IllegalArgumentException("Connection timeout must be positive");
            }

            // Validate read timeout
            int actualReadTimeoutSeconds = readTimeoutSeconds != null ?
                    readTimeoutSeconds : DEFAULT_READ_TIMEOUT_SECONDS;
            if (actualReadTimeoutSeconds <= 0) {
                throw new IllegalArgumentException("Read timeout must be positive");
            }

            // Convert the URI to a URL
            // Note: URI.toURL() is deprecated but all alternatives (URL constructors) are also deprecated.
            // We suppress the warning since we need to create a URL for backward compatibility.
            // At this point, uri is guaranteed to be non-null because resolveUri() was called above.
            URL verifiedUrl;
            try {
                verifiedUrl = uri.toURL();
            } catch (MalformedURLException | IllegalArgumentException | NullPointerException e) {
                throw new IllegalStateException("Failed to convert URI to URL: " + uri, e);
            }

            // Fail-secure scheme policy: HTTPS is required; http is opt-in; anything else is rejected.
            String scheme = uri.getScheme();
            if ("https".equalsIgnoreCase(scheme)) {
                // For HTTPS, create or validate SSL context and pin the enabled protocols
                SecureSSLContextProvider actualSecureSSLContextProvider = secureSSLContextProvider != null ?
                        secureSSLContextProvider : new SecureSSLContextProvider();
                SSLContext secureContext;
                if (verifyHostname) {
                    secureContext = actualSecureSSLContextProvider.getOrCreateSecureSSLContext(sslContext);
                } else {
                    secureContext = actualSecureSSLContextProvider.createHostnameRelaxedSSLContext();
                    LOGGER.warn(HttpLogMessages.WARN.HOSTNAME_VERIFICATION_DISABLED, uri);
                }
                return new HttpHandler(uri, verifiedUrl, secureContext, actualSecureSSLContextProvider,
                        actualConnectionTimeoutSeconds, actualReadTimeoutSeconds, allowInsecureHttp,
                        verifyHostname, sslContextCallerSupplied);
            }
            if ("http".equalsIgnoreCase(scheme)) {
                if (!allowInsecureHttp) {
                    throw new IllegalArgumentException("Refusing to build a plaintext HTTP handler for " + uri
                            + "; HTTPS is required. Call allowInsecureHttp(true) to permit cleartext HTTP, "
                            + "or use an https:// URI.");
                }
                LOGGER.warn(HttpLogMessages.WARN.INSECURE_HTTP_CONNECTION, uri);
                // For HTTP, no SSL context needed
                return new HttpHandler(uri, verifiedUrl, actualConnectionTimeoutSeconds, actualReadTimeoutSeconds);
            }
            throw new IllegalArgumentException("Unsupported URI scheme '" + scheme + "' for " + uri
                    + "; only http and https are supported.");
        }

        /**
         * Resolves the URI from the provided inputs.
         * Priority: 1. uri, 2. url, 3. urlString
         */
        private void resolveUri() {
            // If URI is already set, use it
            if (uri != null) {
                return;
            }

            // If URL is set, convert it to URI
            if (url != null) {
                try {
                    uri = url.toURI();
                    return;
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Invalid URL: " + url, e);
                }
            }

            // If urlString is set, convert it to URI
            if (!MoreStrings.isBlank(urlString)) {
                // Check if the URL has a scheme, if not prepend https://
                String urlToUse = urlString;
                if (!URL_SCHEME_PATTERN.matcher(urlToUse).matches()) {
                    LOGGER.debug("URL missing scheme, prepending https:// to %s", urlString);
                    urlToUse = "https://" + urlToUse;
                }

                uri = URI.create(urlToUse);
                return;
            }

            // If we get here, no valid URI source was provided
            throw new IllegalArgumentException("URI must not be null or empty.");
        }

    }
}

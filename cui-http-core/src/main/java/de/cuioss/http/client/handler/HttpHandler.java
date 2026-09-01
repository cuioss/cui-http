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
 * <p>
 * Owns the underlying {@link HttpClient} and implements {@link AutoCloseable}: call {@link #close()}
 * — or use the handler in a try-with-resources block — to deterministically release the client's
 * connection pool and executor. See <a href="#lifecycle">Lifecycle</a>.
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
 * SSLContext customSSL = new SecureSSLContextProvider().getOrCreateSecureSSLContext(null);
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
 *   <li>Redirects are <strong>not followed</strong>: no redirect policy is configured on either the
 *       HTTP or the HTTPS client, so the JDK default {@link HttpClient.Redirect#NEVER} applies. Every
 *       3xx response — followable status or not — reaches the caller verbatim, with any
 *       {@code Location} header it supplies left intact and no further request issued. Adapters
 *       classify such a response as a non-retryable {@code INVALID_CONTENT} failure; see
 *       {@link HttpStatusFamily#toErrorCategory()}. A caller that wants to act on a redirect can
 *       read and validate the {@code Location} header when the response supplies one — a 3xx is not
 *       required to carry it — and issue the follow-up request explicitly.</li>
 * </ul>
 * <p>
 * <strong>Why redirects are not followed:</strong> a followed redirect would send the request to a
 * target the caller never validated. This class runs no {@code de.cuioss.http.security} pipeline over
 * a redirect destination, so a same-scheme redirect to an attacker-chosen host or port would be
 * honoured silently. Leaving the JDK default in place keeps the caller-validated URI the only request
 * target. Validated redirect following — same-origin by default, with an opt-in host allowlist — is
 * planned as follow-up work; the current behaviour is a fail-secure baseline, not the intended
 * permanent end state.
 *
 * <h3 id="lifecycle">Lifecycle</h3>
 * <p>A handler creates exactly one {@link HttpClient} during construction and shares it across every
 * call to {@link #createHttpClient()}, {@link #pingGet()}, and {@link #pingHead()}. That client owns
 * a connection pool and — for the default configuration — a virtual-thread executor, neither of which
 * is released until the client is closed. {@link #close()} delegates to {@link HttpClient#close()},
 * which blocks until all in-flight requests on that client have completed. A handler is unusable
 * after {@code close()}: requests issued through the returned client fail. Handlers held for the
 * lifetime of the JVM need not be closed; short-lived handlers should be:</p>
 * <pre>
 * try (HttpHandler handler = HttpHandler.builder().uri("https://api.example.com").build()) {
 *     HttpStatusFamily status = handler.pingGet();
 * }
 * </pre>
 * <p>Adapters such as {@code ETagAwareHttpAdapter} <em>borrow</em> the client from the handler they
 * are configured with and never close it — closing the handler is the caller's responsibility.</p>
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
 * period, and algorithm constraints remain fully enforced; revocation posture is unchanged from
 * whatever the JVM's default PKIX trust manager already applies (the JDK's default PKIX
 * configuration does not itself enable revocation checking - see
 * {@link SecureSSLContextProvider#createHostnameRelaxedSSLContext()}). A WARN
 * ({@code HTTP-116}) is logged for every handler built this way.</p>
 * <p>The relaxation is confined to the default-trust-store context this class derives itself, so
 * combining {@code verifyHostname(false)} with a caller-supplied
 * {@link HttpHandlerBuilder#sslContext(SSLContext)} is rejected at {@link HttpHandlerBuilder#build()}
 * time with {@link IllegalArgumentException}. A context {@link #asBuilder()} carries over from a
 * handler's own derivation is not caller-supplied and round-trips cleanly. Flipping
 * {@code verifyHostname} back to {@code true} on a builder cloned from a relaxed handler never
 * silently keeps the relaxed context: {@link HttpHandlerBuilder#build()} discards it and derives a
 * fresh secure context instead, so the relaxation cannot leak into a handler that reports hostname
 * verification as enabled.</p>
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
public final class HttpHandler implements AutoCloseable {

    private static final CuiLogger LOGGER = new CuiLogger(HttpHandler.class);

    /**
     * Pre-compiled pattern for detecting URLs with scheme.
     * Matches RFC 3986 scheme format: scheme:remainder
     */
    private static final Pattern URL_SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    /**
     * Pre-compiled pattern disambiguating a schemeless {@code host:port} shorthand from a genuine
     * {@code scheme:remainder} URI.
     * <p>
     * {@link #URL_SCHEME_PATTERN} alone cannot tell the two apart: {@code localhost:8443} satisfies
     * it, because {@code localhost} is a syntactically valid scheme name and {@code 8443} a valid
     * remainder. Treating that input as scheme-bearing yields a URI with scheme {@code localhost}
     * and no host, which {@link HttpHandlerBuilder#build()} then rejects as an unsupported scheme.
     * <p>
     * The disambiguator is that a genuine scheme is never followed <em>solely</em> by digits in the
     * forms this builder accepts: {@code build()} admits only {@code http} and {@code https}, whose
     * remainder always begins with {@code //}. So when the text immediately after the first colon is
     * one or more digits terminated by end-of-string or one of {@code /}, {@code ?}, {@code #}, the
     * input is a {@code host:port} shorthand and {@code https://} is prepended.
     */
    private static final Pattern HOST_PORT_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:\\d+(?:[/?#].*)?$");

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
    /**
     * The egress host policy applied to every redirect hop this handler would follow. Never
     * {@code null}: a handler built without an explicit policy carries {@link RedirectPolicy#sameOrigin()},
     * so same-origin hops are followed, cross-origin hops are refused, and {@code Authorization} /
     * {@code Cookie} are stripped on any non-same-origin hop. It is part of the handler's
     * configuration identity ({@code equals}/{@code hashCode}) and is rendered in {@code toString}.
     *
     * @return the redirect policy for this handler, never {@code null}
     */
    @Getter
    private final RedirectPolicy redirectPolicy;
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
    private HttpHandler(URI uri, URL url, int connectionTimeoutSeconds, int readTimeoutSeconds,
            RedirectPolicy redirectPolicy) {
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
        this.redirectPolicy = redirectPolicy;

        // Create the HttpClient for HTTP.
        // No JDK redirect policy is configured: the JDK default is Redirect.NEVER, and it stays that
        // way deliberately. Redirect following is application-level, driven by this handler against
        // its RedirectPolicy, because the JDK follower would send the request to a target no policy
        // ever evaluated. Every hop must be revalidated before it is requested, so the JDK must never
        // follow one on its own.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
                .build();
    }

    // Constructor for HTTPS URIs (SSL context required)
    // NOSONAR java:S107 - private constructor invoked only from HttpHandlerBuilder#build(); the wide
    // parameter list mirrors the builder's own fields one-for-one and is the intended shape for a
    // Lombok-style immutable value object assembled by a single caller-facing builder. Splitting it
    // into a parameter object would only move the same fields to a second private type.
    @SuppressWarnings("java:S107")
    private HttpHandler(URI uri, URL url, SSLContext sslContext, SecureSSLContextProvider secureSSLContextProvider,
            int connectionTimeoutSeconds, int readTimeoutSeconds, boolean allowInsecureHttp,
            boolean verifyHostname, boolean sslContextCallerSupplied, RedirectPolicy redirectPolicy) {
        this.uri = uri;
        this.url = url;
        this.sslContext = sslContext;
        this.secureSSLContextProvider = secureSSLContextProvider;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.allowInsecureHttp = allowInsecureHttp;
        this.verifyHostname = verifyHostname;
        this.sslContextCallerSupplied = sslContextCallerSupplied;
        this.redirectPolicy = redirectPolicy;

        // JDK 11+ HttpClient enables hostname verification by default.
        // Pin the enabled TLS protocols so the configured minimum version is a hard
        // floor on the wire, not merely the context's default protocol object.
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(secureSSLContextProvider.getEnabledProtocols());
        // No JDK redirect policy is configured here either — see the HTTP constructor above for why
        // the JDK default (Redirect.NEVER) is deliberately left in place and the follow loop is
        // application-level.
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
     * package-private {@link HttpHandlerBuilder#derivedSslContext(SSLContext, boolean)} seam, so it
     * does not become "caller-supplied" on the returned builder. This is what lets a handler built
     * with {@code verifyHostname(false)} round-trip through {@code asBuilder().build()} without
     * tripping the mutual-exclusion rejection. A context that genuinely came from the caller is
     * re-injected through {@link HttpHandlerBuilder#sslContext(SSLContext)} and keeps that
     * provenance.</p>
     * <p>The re-injected derived context also carries whether it is <em>hostname-relaxed</em> — i.e.
     * whether this handler itself was built with {@code verifyHostname(false)}. {@link
     * HttpHandlerBuilder#build()} consults that flag to refuse silently reusing a relaxed context for
     * a handler that flips {@code verifyHostname} back to {@code true} on the returned builder: see
     * {@link HttpHandlerBuilder#build()} for the mechanism.</p>
     *
     * @return A pre-configured {@link HttpHandlerBuilder} with the same timeouts as this handler
     */
    public HttpHandlerBuilder asBuilder() {
        HttpHandlerBuilder handlerBuilder = builder()
                .connectionTimeoutSeconds(connectionTimeoutSeconds)
                .readTimeoutSeconds(readTimeoutSeconds)
                .tlsVersions(secureSSLContextProvider)
                .allowInsecureHttp(allowInsecureHttp)
                .verifyHostname(verifyHostname)
                .redirectPolicy(redirectPolicy);
        return sslContextCallerSupplied
                ? handlerBuilder.sslContext(sslContext)
                : handlerBuilder.derivedSslContext(sslContext, !verifyHostname);
    }

    /**
     * Pings the URI using the HEAD method and returns the HTTP status code family.
     *
     * @return The HTTP status code family, or {@link HttpStatusFamily#UNKNOWN} if an error occurred
     */
    // Uses this handler's shared HttpClient and deliberately does not close it: the client's
    // lifetime is the handler's, released by HttpHandler#close().
    @SuppressWarnings("try")
    public HttpStatusFamily pingHead() {
        return pingWithMethod("HEAD", HttpRequest.BodyPublishers.noBody());
    }

    /**
     * Pings the URI using the GET method and returns the HTTP status code family.
     *
     * @return The HTTP status code family, or {@link HttpStatusFamily#UNKNOWN} if an error occurred
     */
    // Uses this handler's shared HttpClient and deliberately does not close it: the client's
    // lifetime is the handler's, released by HttpHandler#close().
    @SuppressWarnings("try")
    public HttpStatusFamily pingGet() {
        return pingWithMethod("GET", HttpRequest.BodyPublishers.noBody());
    }

    /**
     * Pings the URI using the specified HTTP method and returns the HTTP status code family.
     *
     * @param method The HTTP method to use (e.g., "HEAD", "GET")
     * @param bodyPublisher The body publisher to use for the request
     * @return The HTTP status code family, or {@link HttpStatusFamily#UNKNOWN} if an error occurred
     */
    // Uses this handler's shared HttpClient and deliberately does not close it: the client's
    // lifetime is the handler's, released by HttpHandler#close().
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
     * Releases the {@link HttpClient} this handler owns, shutting down its connection pool and
     * executor.
     * <p>
     * Delegates to {@link HttpClient#close()}, which initiates an orderly shutdown and blocks until
     * every request in flight <em>on that client</em> has completed. This handler — and any client
     * reference previously obtained from {@link #createHttpClient()} — is unusable afterwards.
     * </p>
     * <p>
     * Closing is idempotent: {@link HttpClient#close()} returns immediately once the client is
     * already shut down, so calling this method more than once is safe. A handler retained for the
     * lifetime of the JVM does not need to be closed; a short-lived one should be, ideally via
     * try-with-resources. Note that this closes only the client, not any adapter that borrows it.
     * </p>
     *
     * @since 2.2
     */
    @Override
    public void close() {
        httpClient.close();
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
        private @Nullable RedirectPolicy redirectPolicy;
        // True when the current (derived, non-caller-supplied) sslContext was produced by
        // createHostnameRelaxedSSLContext() on the handler asBuilder() cloned this builder from.
        // build() consults this to refuse silently reusing a relaxed context once verifyHostname
        // has been flipped back to true on this builder - see build() for the rationale.
        private boolean derivedSslContextRelaxed = false;

        /**
         * Sets the URI as a string.
         * <p>
         * <strong>Resolution precedence:</strong> the source used at {@link #build()} time is chosen
         * in the order {@code uri(URI)} &gt; {@code url(URL)} &gt; string form. The string form is a
         * single slot shared with {@link #url(String)}, so the last of {@code uri(String)} /
         * {@code url(String)} wins, and a typed {@code uri(URI)} or {@code url(URL)} set on the same
         * builder takes precedence over it.
         * </p>
         * <p>
         * <strong>Schemeless input:</strong> a string without a scheme resolves to {@code https://}.
         * This includes the {@code host:port} shorthand — {@code localhost:8443} resolves to
         * {@code https://localhost:8443}, not to a URI with scheme {@code localhost}.
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
         * <p>
         * <strong>Schemeless input:</strong> a string without a scheme resolves to {@code https://},
         * including the {@code host:port} shorthand — {@code localhost:8443} resolves to
         * {@code https://localhost:8443}.
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
            this.derivedSslContextRelaxed = false;
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
         * <p>
         * {@code relaxed} records whether the carried-over context is the hostname-relaxed context
         * produced by {@link SecureSSLContextProvider#createHostnameRelaxedSSLContext()} - i.e.
         * whether the source handler was built with {@code verifyHostname(false)}. {@link #build()}
         * uses this to detect a builder that has since flipped {@code verifyHostname} back to
         * {@code true}: reusing the relaxed context in that case would silently keep hostname
         * verification disabled on a handler that reports it as enabled, so {@link #build()} discards
         * the relaxed context instead of reusing it - see {@link #build()}.
         * </p>
         *
         * @param sslContext The already-resolved SSL context to carry over.
         * @param relaxed    {@code true} when {@code sslContext} is the hostname-relaxed context the
         *                   source handler was built with.
         * @return This builder instance.
         */
        HttpHandlerBuilder derivedSslContext(@Nullable SSLContext sslContext, boolean relaxed) {
            this.sslContext = sslContext;
            this.derivedSslContextRelaxed = relaxed;
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
         * period, and algorithm constraints all remain fully enforced; revocation posture is
         * unchanged from whatever the JVM's default PKIX trust manager already applies (the JDK's
         * default PKIX configuration does not itself enable revocation checking) - see
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
         * Sets the egress host policy applied to every redirect hop the handler would follow.
         * <p>
         * This is the single seam through which all three redirect knobs are configured: the hop
         * bound ({@link RedirectPolicy.RedirectPolicyBuilder#maxHops(int)}), the host allowlist
         * ({@link RedirectPolicy.RedirectPolicyBuilder#allowedHosts(java.util.Collection)}), and the
         * credential-forwarding strategy
         * ({@link RedirectPolicy.RedirectPolicyBuilder#credentialForwarding(RedirectPolicy.CredentialForwarding)}).
         * There is no separate builder method for any of them.
         * </p>
         * <p>
         * Default: {@link RedirectPolicy#sameOrigin()} — same-origin hops are followed, every
         * cross-origin hop is refused, and {@code Authorization} / {@code Cookie} are stripped on any
         * non-same-origin hop. <strong>Credential forwarding across a cross-origin hop is opt-in</strong>:
         * it requires naming {@link RedirectPolicy.CredentialForwarding#FORWARD_TO_ALLOWLISTED} on a
         * policy that also allowlists the target host, and even then it changes no refusal verdict.
         * </p>
         * <p>
         * Passing {@code null} <strong>restores the {@link RedirectPolicy#sameOrigin()} default</strong>
         * — it does not disable redirect validation, and it does not leave a forwarding strategy
         * previously set on this builder in force: the secure
         * {@link RedirectPolicy.CredentialForwarding#STRIP_ON_CROSS_ORIGIN} default is restored with it.
         * </p>
         *
         * @param redirectPolicy the policy to apply, or {@code null} to restore the same-origin default.
         * @return This builder instance.
         */
        public HttpHandlerBuilder redirectPolicy(@Nullable RedirectPolicy redirectPolicy) {
            this.redirectPolicy = redirectPolicy;
            return this;
        }

        /**
         * Builds a new {@link HttpHandler} instance with the configured parameters.
         *
         * @return A new {@link HttpHandler} instance.
         * @throws IllegalArgumentException If any parameter is invalid, or if
         *                                  {@code verifyHostname(false)} is combined with a
         *                                  caller-supplied {@link #sslContext(SSLContext)}.
         * @throws IllegalStateException    If the resolved URI cannot be converted to a
         *                                  {@link URL} — the URI is syntactically valid but names
         *                                  no protocol handler this JVM can resolve.
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

            // Resolve the URI from the provided inputs. The result is bound to a local and threaded
            // through the rest of build() so the builder's own uri field is never mutated - a reused
            // builder therefore re-resolves from its current inputs instead of pinning the first result.
            URI resolvedUri = resolveUri();

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

            // Materialise the URL eagerly so build() is the single place a URI that names no
            // resolvable protocol handler is rejected; the handler then exposes url() without any
            // later failure path. URI.toURL() is the non-deprecated route (the URL constructors are
            // the deprecated ones).
            // At this point, resolvedUri is guaranteed to be non-null because resolveUri() either
            // returns a non-null URI or throws.
            URL verifiedUrl;
            try {
                verifiedUrl = resolvedUri.toURL();
            } catch (MalformedURLException | IllegalArgumentException | NullPointerException e) {
                throw new IllegalStateException("Failed to convert URI to URL: " + resolvedUri, e);
            }

            // A null policy restores the same-origin default rather than disabling validation, so a
            // forwarding strategy set earlier on this builder cannot survive redirectPolicy(null).
            RedirectPolicy resolvedRedirectPolicy = redirectPolicy != null ? redirectPolicy : RedirectPolicy.sameOrigin();

            // Fail-secure scheme policy: HTTPS is required; http is opt-in; anything else is rejected.
            String scheme = resolvedUri.getScheme();
            if ("https".equalsIgnoreCase(scheme)) {
                // For HTTPS, create or validate SSL context and pin the enabled protocols
                SecureSSLContextProvider actualSecureSSLContextProvider = secureSSLContextProvider != null ?
                        secureSSLContextProvider : new SecureSSLContextProvider();
                SSLContext secureContext = resolveHttpsSecureContext(actualSecureSSLContextProvider, resolvedUri);
                return new HttpHandler(resolvedUri, verifiedUrl, secureContext, actualSecureSSLContextProvider,
                        actualConnectionTimeoutSeconds, actualReadTimeoutSeconds, allowInsecureHttp,
                        verifyHostname, sslContextCallerSupplied, resolvedRedirectPolicy);
            }
            if ("http".equalsIgnoreCase(scheme)) {
                if (!allowInsecureHttp) {
                    throw new IllegalArgumentException("Refusing to build a plaintext HTTP handler for " + resolvedUri
                            + "; HTTPS is required. Call allowInsecureHttp(true) to permit cleartext HTTP, "
                            + "or use an https:// URI.");
                }
                LOGGER.warn(HttpLogMessages.WARN.INSECURE_HTTP_CONNECTION, resolvedUri);
                // For HTTP, no SSL context needed
                return new HttpHandler(resolvedUri, verifiedUrl, actualConnectionTimeoutSeconds, actualReadTimeoutSeconds,
                        resolvedRedirectPolicy);
            }
            throw new IllegalArgumentException("Unsupported URI scheme '" + scheme + "' for " + resolvedUri
                    + "; only http and https are supported.");
        }

        /**
         * Resolves the {@link SSLContext} for an HTTPS handler, honoring the {@link #verifyHostname}
         * opt-in and refusing to silently reuse a stale hostname-relaxed context.
         * <p>
         * When {@link #verifyHostname} is {@code false}, a fresh hostname-relaxed context is created
         * and the {@code HTTP-116} WARN is logged. When {@code true}, a derived (non-caller-supplied)
         * context carried over from {@link HttpHandler#asBuilder()} may be the hostname-relaxed
         * context of a source handler built with {@code verifyHostname(false)}; reusing it verbatim
         * here would silently keep hostname verification disabled underneath a handler that reports
         * {@code isVerifyHostname() == true}. Such a context is discarded (falling back to
         * {@code null}, which {@link SecureSSLContextProvider#getOrCreateSecureSSLContext(SSLContext)}
         * resolves to a fresh secure default context) rather than let the relaxation leak through a
         * {@code verifyHostname(true)} rebuild. A genuinely caller-supplied context is never
         * discarded - {@link #sslContextCallerSupplied} gates it.
         *
         * @param provider    the TLS-floor provider used to create or validate the context
         * @param resolvedUri the URI resolved for the handler under construction, used for logging
         * @return the resolved {@link SSLContext} for the handler under construction
         */
        private SSLContext resolveHttpsSecureContext(SecureSSLContextProvider provider, URI resolvedUri) {
            if (!verifyHostname) {
                SSLContext relaxedContext = provider.createHostnameRelaxedSSLContext();
                LOGGER.warn(HttpLogMessages.WARN.HOSTNAME_VERIFICATION_DISABLED, resolvedUri);
                return relaxedContext;
            }
            boolean discardStaleRelaxedContext = !sslContextCallerSupplied && derivedSslContextRelaxed;
            return provider.getOrCreateSecureSSLContext(discardStaleRelaxedContext ? null : sslContext);
        }

        /**
         * Resolves the URI from the provided inputs without mutating builder state.
         * <p>
         * Resolution precedence is unchanged: {@code uri(URI)} wins over {@code url(URL)}, which
         * wins over the shared string slot (where the last string setter wins). Because this method
         * is pure, a builder reused after {@link #build()} re-resolves from its <em>current</em>
         * inputs — a newly supplied string URI is honored rather than silently ignored in favor of
         * a URI pinned by an earlier build.
         *
         * @return the resolved URI, never {@code null}
         * @throws IllegalArgumentException if no valid URI source was provided, or if a supplied
         *                                  {@link URL} cannot be converted to a {@link URI}
         */
        private URI resolveUri() {
            // If URI is already set, use it
            if (uri != null) {
                return uri;
            }

            // If URL is set, convert it to URI
            if (url != null) {
                try {
                    return url.toURI();
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Invalid URL: " + url, e);
                }
            }

            // If urlString is set, convert it to URI
            if (!MoreStrings.isBlank(urlString)) {
                // Check if the URL has a scheme, if not prepend https://. A host:port shorthand
                // also satisfies URL_SCHEME_PATTERN, so HOST_PORT_PATTERN excludes it from being
                // mistaken for a scheme-bearing URI - see HOST_PORT_PATTERN.
                String urlToUse = urlString;
                boolean schemeBearing = URL_SCHEME_PATTERN.matcher(urlToUse).matches()
                        && !HOST_PORT_PATTERN.matcher(urlToUse).matches();
                if (!schemeBearing) {
                    LOGGER.debug("URL missing scheme, prepending https:// to %s", urlString);
                    urlToUse = "https://" + urlToUse;
                }

                return URI.create(urlToUse);
            }

            // If we get here, no valid URI source was provided
            throw new IllegalArgumentException("URI must not be null or empty.");
        }

    }
}

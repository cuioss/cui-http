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
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
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
 * Secure HTTP client wrapper providing simplified HTTP request execution with robust SSL handling.
 *
 * <p>This class provides a builder-based wrapper around Java's {@link HttpClient} that simplifies
 * HTTP request configuration and execution while ensuring secure defaults for SSL/TLS connections.
 * It handles common HTTP client setup patterns and provides consistent timeout and SSL management.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Security First</strong> - Automatic secure SSL context creation for HTTPS</li>
 *   <li><strong>Builder Pattern</strong> - Fluent API for easy configuration</li>
 *   <li><strong>Immutable</strong> - Thread-safe after construction</li>
 *   <li><strong>Fail Fast</strong> - Validates configuration at build time</li>
 * </ul>
 *
 * <h3>Security Features</h3>
 * <ul>
 *   <li><strong>Automatic SSL Context</strong> - Creates secure SSL contexts when not provided</li>
 *   <li><strong>TLS Version Control</strong> - Uses {@link SecureSSLContextProvider} for modern TLS versions</li>
 *   <li><strong>URL Validation</strong> - Validates URI format and convertibility at build time</li>
 *   <li><strong>Timeout Protection</strong> - Configurable timeouts prevent resource exhaustion</li>
 * </ul>
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
 * // Execute GET request
 * HttpResponse&lt;String&gt; response = handler.executeGetRequest();
 * if (response.statusCode() == 200) {
 *     String body = response.body();
 *     // Process response
 * }
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
 * <h3>Configuration Contract</h3>
 * <ul>
 *   <li><strong>URI Validation</strong> - URI must be valid and convertible to URL (checked at build time)</li>
 *   <li><strong>HTTPS SSL Context</strong> - Automatically created if not provided for HTTPS URIs</li>
 *   <li><strong>Timeout Defaults</strong> - Uses 10 seconds for both connection and read timeouts if not specified</li>
 *   <li><strong>URL Scheme Detection</strong> - Automatically handles URLs with or without explicit schemes</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>HttpHandler instances are immutable and thread-safe after construction. The underlying
 * {@link HttpClient} is also thread-safe and can be used concurrently from multiple threads.</p>
 *
 * <h3>Error Handling</h3>
 * <ul>
 *   <li><strong>Build-time Validation</strong> - {@link IllegalStateException} for invalid URIs or configuration</li>
 *   <li><strong>Runtime Exceptions</strong> - {@link IOException} for network errors, {@link InterruptedException} for thread interruption</li>
 * </ul>
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

    @Getter private final URI uri;
    @Getter private final URL url;
    @Getter private final @Nullable SSLContext sslContext;
    @Getter private final int connectionTimeoutSeconds;
    @Getter private final int readTimeoutSeconds;
    private final HttpClient httpClient;

    // Constructor for HTTP URIs (no SSL context needed)
    private HttpHandler(URI uri, URL url, int connectionTimeoutSeconds, int readTimeoutSeconds) {
        this.uri = uri;
        this.url = url;
        this.sslContext = null;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;

        // Create the HttpClient for HTTP
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
                .build();
    }

    // Constructor for HTTPS URIs (SSL context required)
    private HttpHandler(URI uri, URL url, SSLContext sslContext, int connectionTimeoutSeconds, int readTimeoutSeconds) {
        this.uri = uri;
        this.url = url;
        this.sslContext = sslContext;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;

        // Create the HttpClient for HTTPS
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
                .sslContext(sslContext)
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
     * @return A pre-configured {@link HttpHandlerBuilder} with the same timeouts as this handler
     */
    public HttpHandlerBuilder asBuilder() {
        return builder()
                .connectionTimeoutSeconds(connectionTimeoutSeconds)
                .readTimeoutSeconds(readTimeoutSeconds)
                .sslContext(sslContext);
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
            LOGGER.warn(e, HttpLogMessages.WARN.HTTP_PING_IO_ERROR.format(uri, e.getMessage()));
            return HttpStatusFamily.UNKNOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn(HttpLogMessages.WARN.HTTP_PING_INTERRUPTED.format(uri, e.getMessage()));
            return HttpStatusFamily.UNKNOWN;
        } catch (IllegalArgumentException | SecurityException e) {
            LOGGER.warn(e, HttpLogMessages.WARN.HTTP_PING_ERROR.format(uri, e.getMessage()));
            return HttpStatusFamily.UNKNOWN;
        }
    }

    /**
     * Gets the configured {@link HttpClient} for making HTTP requests.
     * The HttpClient is created once during construction and reused for all requests,
     * improving performance by leveraging connection pooling.
     *
     * @return A configured {@link HttpClient} with the SSL context and connection timeout
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

        /**
         * Sets the URI as a string.
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
         *
         * @param sslContext The SSL context to use.
         * @return This builder instance.
         */
        public HttpHandlerBuilder sslContext(@Nullable SSLContext sslContext) {
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
         * Builds a new {@link HttpHandler} instance with the configured parameters.
         *
         * @return A new {@link HttpHandler} instance.
         * @throws IllegalArgumentException If any parameter is invalid.
         */
        public HttpHandler build() {
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
            if (uri == null) {
                throw new IllegalArgumentException("URI cannot be null");
            }

            URL verifiedUrl;
            try {
                verifiedUrl = uri.toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Failed to convert URI to URL: " + uri, e);
            }

            // Use appropriate constructor based on scheme
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                // For HTTPS, create or validate SSL context
                SecureSSLContextProvider actualSecureSSLContextProvider = secureSSLContextProvider != null ?
                        secureSSLContextProvider : new SecureSSLContextProvider();
                SSLContext secureContext = actualSecureSSLContextProvider.getOrCreateSecureSSLContext(sslContext);
                return new HttpHandler(uri, verifiedUrl, secureContext, actualConnectionTimeoutSeconds, actualReadTimeoutSeconds);
            } else {
                // For HTTP, no SSL context needed
                return new HttpHandler(uri, verifiedUrl, actualConnectionTimeoutSeconds, actualReadTimeoutSeconds);
            }
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
                    LOGGER.debug(() -> "URL missing scheme, prepending https:// to %s".formatted(urlString));
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

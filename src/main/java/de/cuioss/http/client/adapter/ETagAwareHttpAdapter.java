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
package de.cuioss.http.client.adapter;

import de.cuioss.http.client.HttpMethod;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.converter.VoidResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP adapter with built-in, configurable ETag caching for bandwidth optimization.
 *
 * <h2>ETag Caching</h2>
 * <p>
 * Implements RFC 7232 conditional requests using ETags:
 * </p>
 * <ul>
 *   <li>GET requests with cached responses send If-None-Match header</li>
 *   <li>Server responds with 304 Not Modified if content unchanged</li>
 *   <li>Adapter returns cached content without re-downloading</li>
 *   <li>Only GET responses are cached (POST/PUT/DELETE never cached)</li>
 *   <li>ETags extracted from all responses for optimistic locking patterns</li>
 * </ul>
 *
 * <h2>Example: Basic Usage</h2>
 * <pre>{@code
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .build();
 *
 * // First request: 200 OK, full response
 * HttpResult<User> result1 = adapter.getBlocking();
 *
 * // Second request: 304 Not Modified, cached content returned
 * HttpResult<User> result2 = adapter.getBlocking();
 * }</pre>
 *
 * <h2>Example: POST with Request Converter</h2>
 * <pre>{@code
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .requestConverter(userConverter)  // Same converter for bidirectional use
 *     .build();
 *
 * User newUser = new User("John", "john@example.com");
 * HttpResult<User> result = adapter.postBlocking(newUser);
 * }</pre>
 *
 * <h2>Example: Status-Code-Only Operations (DELETE/HEAD)</h2>
 * <pre>{@code
 * HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);
 *
 * // DELETE returns no body, only status code
 * HttpResult<Void> result = adapter.deleteBlocking();
 * if (result.isSuccess()) {
 *     System.out.println("Deleted successfully");
 * }
 * }</pre>
 *
 * <h2>Example: Token Refresh Without Cache Bloat</h2>
 * <pre>{@code
 * // Mobile app with frequent token refresh - exclude Authorization from cache key
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization"))
 *     .build();
 *
 * // Token refresh doesn't create duplicate cache entries
 * Map<String, String> headers1 = Map.of("Authorization", "Bearer old-token");
 * HttpResult<User> result1 = adapter.get(headers1).join();
 *
 * // After token refresh - same cache key!
 * Map<String, String> headers2 = Map.of("Authorization", "Bearer new-token");
 * HttpResult<User> result2 = adapter.get(headers2).join();  // 304 Not Modified
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Fully thread-safe:
 * </p>
 * <ul>
 *   <li>Builder: NOT thread-safe (build once per adapter)</li>
 *   <li>Built adapter: Immutable fields, concurrent cache, safe for shared use</li>
 *   <li>HttpClient: Created once in constructor, reused for all requests</li>
 *   <li>Cache: ConcurrentHashMap with local reference pattern for 304 handling</li>
 * </ul>
 *
 * @param <T> Response body type
 * @since 1.0
 * @see HttpAdapter
 * @see CacheKeyHeaderFilter
 * @see VoidResponseConverter
 */
public class ETagAwareHttpAdapter<T> implements HttpAdapter<T> {

    private static final CuiLogger LOGGER = new CuiLogger(ETagAwareHttpAdapter.class);

    private final HttpHandler httpHandler;
    private final HttpClient httpClient;
    private final HttpResponseConverter<T> responseConverter;
    @Nullable
    private final HttpRequestConverter<T> requestConverter;
    private final boolean etagCachingEnabled;
    private final CacheKeyHeaderFilter cacheKeyHeaderFilter;
    private final int maxCacheSize;
    private final ConcurrentHashMap<String, CacheEntry<T>> cache;

    /**
     * Cache entry with content, ETag, and timestamp for eviction.
     *
     * @param <T> Content type
     */
    public record CacheEntry<T>(
    T content,
    String etag,
    long timestamp
    ) {
    }

    /**
     * Private constructor - use Builder.
     */
    private ETagAwareHttpAdapter(Builder<T> builder) {
        this.httpHandler = Objects.requireNonNull(builder.httpHandler, "httpHandler is required");
        this.responseConverter = Objects.requireNonNull(builder.responseConverter, "responseConverter is required");
        this.requestConverter = builder.requestConverter;
        this.etagCachingEnabled = builder.etagCachingEnabled;
        this.cacheKeyHeaderFilter = Objects.requireNonNull(builder.cacheKeyHeaderFilter, "cacheKeyHeaderFilter is required");
        this.maxCacheSize = builder.maxCacheSize;
        this.cache = new ConcurrentHashMap<>();

        // Create HttpClient ONCE in constructor for thread-safe reuse
        this.httpClient = httpHandler.createHttpClient();

        LOGGER.debug("Created ETagAwareHttpAdapter: etagCachingEnabled=%s, maxCacheSize=%s",
                etagCachingEnabled, maxCacheSize);
    }

    /**
     * Factory method for status-code-only operations (DELETE, HEAD, OPTIONS).
     *
     * <p>
     * Returns an adapter that discards response bodies and only tracks HTTP status codes.
     * Useful for operations where the status code is the only meaningful result.
     * </p>
     *
     * <h3>Example: DELETE Operation</h3>
     * <pre>{@code
     * HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);
     *
     * HttpResult<Void> result = adapter.deleteBlocking();
     * if (result.isSuccess()) {
     *     System.out.println("Resource deleted (status: " + result.getHttpStatus().orElse(0) + ")");
     * }
     * }</pre>
     *
     * <h3>Example: HEAD Operation</h3>
     * <pre>{@code
     * HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);
     *
     * HttpResult<Void> result = adapter.headBlocking();
     * if (result.isSuccess()) {
     *     String etag = result.getETag().orElse("none");
     *     System.out.println("Resource exists with ETag: " + etag);
     * }
     * }</pre>
     *
     * @param httpHandler HTTP handler with base configuration
     * @return Adapter for status-code-only operations
     * @since 1.0
     */
    public static HttpAdapter<Void> statusCodeOnly(HttpHandler httpHandler) {
        return ETagAwareHttpAdapter.<Void>builder()
                .httpHandler(httpHandler)
                .responseConverter(VoidResponseConverter.INSTANCE)
                .etagCachingEnabled(false)  // No caching for status-only operations
                .build();
    }

    /**
     * Clears all ETag cache entries immediately.
     *
     * <p>
     * Thread-safe: In-flight requests holding local cache references are unaffected.
     * </p>
     *
     * <h3>When to Use</h3>
     * <ul>
     *   <li>User logout - Clear user-specific cached data</li>
     *   <li>Configuration change - Application settings changed (e.g., switching servers)</li>
     * </ul>
     *
     * <h3>Not Needed For</h3>
     * <ul>
     *   <li>Memory pressure - Automatic eviction handles this</li>
     *   <li>Token refresh - Use {@link CacheKeyHeaderFilter#excluding(String...)} instead</li>
     *   <li>Periodic maintenance - Cache self-manages at maxCacheSize</li>
     * </ul>
     *
     * @since 1.0
     */
    public void clearETagCache() {
        int sizeBefore = cache.size();
        cache.clear();
        LOGGER.debug("Cleared ETag cache: %s entries removed", sizeBefore);
    }

    @Override
    public CompletableFuture<HttpResult<T>> get() {
        return get(Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> get(Map<String, String> headers) {
        return send(HttpMethod.GET, null, headers);
    }

    @Override
    public CompletableFuture<HttpResult<T>> post(@Nullable T body) {
        return post(body, Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> post(@Nullable T body, Map<String, String> headers) {
        return send(HttpMethod.POST, body, headers);
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> post(HttpRequestConverter<R> converter, @Nullable R body) {
        return post(converter, body, Map.of());
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> post(HttpRequestConverter<R> converter, @Nullable R body, Map<String, String> headers) {
        return sendWithConverter(HttpMethod.POST, converter, body, headers);
    }

    @Override
    public CompletableFuture<HttpResult<T>> put(@Nullable T body) {
        return put(body, Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> put(@Nullable T body, Map<String, String> headers) {
        return send(HttpMethod.PUT, body, headers);
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> put(HttpRequestConverter<R> converter, @Nullable R body) {
        return put(converter, body, Map.of());
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> put(HttpRequestConverter<R> converter, @Nullable R body, Map<String, String> headers) {
        return sendWithConverter(HttpMethod.PUT, converter, body, headers);
    }

    @Override
    public CompletableFuture<HttpResult<T>> patch(@Nullable T body) {
        return patch(body, Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> patch(@Nullable T body, Map<String, String> headers) {
        return send(HttpMethod.PATCH, body, headers);
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> patch(HttpRequestConverter<R> converter, @Nullable R body) {
        return patch(converter, body, Map.of());
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> patch(HttpRequestConverter<R> converter, @Nullable R body, Map<String, String> headers) {
        return sendWithConverter(HttpMethod.PATCH, converter, body, headers);
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete() {
        return delete(Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete(Map<String, String> headers) {
        return send(HttpMethod.DELETE, null, headers);
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete(@Nullable T body) {
        return delete(body, Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete(@Nullable T body, Map<String, String> headers) {
        return send(HttpMethod.DELETE, body, headers);
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> delete(HttpRequestConverter<R> converter, @Nullable R body) {
        return delete(converter, body, Map.of());
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> delete(HttpRequestConverter<R> converter, @Nullable R body, Map<String, String> headers) {
        return sendWithConverter(HttpMethod.DELETE, converter, body, headers);
    }

    @Override
    public CompletableFuture<HttpResult<T>> head() {
        return head(Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> head(Map<String, String> headers) {
        return send(HttpMethod.HEAD, null, headers);
    }

    @Override
    public CompletableFuture<HttpResult<T>> options() {
        return options(Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> options(Map<String, String> headers) {
        return send(HttpMethod.OPTIONS, null, headers);
    }

    /**
     * Sends request with explicit request converter for different body type.
     *
     * <p>
     * Used for generic body methods where request type (R) differs from response type (T).
     * Creates request body using provided converter instead of adapter's default converter.
     * </p>
     *
     * @param <R> Request body type
     * @param method HTTP method
     * @param requestConverter Converter for request body serialization
     * @param body Request body (nullable)
     * @param headers Additional HTTP headers
     * @return CompletableFuture with HttpResult
     */
    private <R> CompletableFuture<HttpResult<T>> sendWithConverter(
            HttpMethod method,
            HttpRequestConverter<R> requestConverter,
            @Nullable R body,
            Map<String, String> headers
    ) {
        // Validate safe methods don't have bodies
        if (method.isSafe() && body != null) {
            throw new IllegalArgumentException(
                    "Safe method %s must not have a request body".formatted(method.methodName())
            );
        }

        // Generate cache key (only meaningful for GET with caching enabled)
        String cacheKey = generateCacheKey(httpHandler.getUri(), headers, cacheKeyHeaderFilter);

        // Retrieve cache entry BEFORE building request (hold local reference for 304 handling)
        @Nullable CacheEntry<T> cachedEntry = etagCachingEnabled ? cache.get(cacheKey) : null;

        try {
            // Build HTTP request with explicit converter
            HttpRequest.BodyPublisher bodyPublisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : requestConverter.toBodyPublisher(body);

            HttpRequest.Builder requestBuilder = httpHandler.requestBuilder()
                    .method(method.methodName(), bodyPublisher);

            // Add custom headers
            headers.forEach(requestBuilder::header);

            // Add If-None-Match header if cached entry exists (GET only)
            if (cachedEntry != null && method == HttpMethod.GET) {
                requestBuilder.header("If-None-Match", cachedEntry.etag());
                LOGGER.debug("Adding If-None-Match header for GET request: %s", cachedEntry.etag());
            }

            HttpRequest request = requestBuilder.build();

            // Execute async request (reuse response handling logic from send())
            return httpClient.sendAsync(request, responseConverter.getBodyHandler())
                    .thenApply(response -> {
                        int statusCode = response.statusCode();

                        // Handle 304 Not Modified - return cached content
                        if (statusCode == 304 && cachedEntry != null) {
                            LOGGER.debug("304 Not Modified - returning cached content");
                            return HttpResult.success(cachedEntry.content(), cachedEntry.etag(), 304);
                        }

                        // Extract ETag from response (all methods, not just GET)
                        @Nullable String etag = response.headers()
                                .firstValue("ETag")
                                .orElse(null);

                        // Convert response body
                        Optional<T> content = responseConverter.convert(response.body());

                        // Handle conversion failure
                        if (content.isEmpty() && statusCode >= 200 && statusCode < 300) {
                            /*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*//*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.warn("Response conversion failed for status %s", statusCode);
                            return HttpResult.<T>failure(
                                    "Failed to convert response body",
                                    null,
                                    HttpErrorCategory.INVALID_CONTENT
                            );
                        }

                        // Cache successful GET responses with ETag
                        if (method == HttpMethod.GET && statusCode == 200 && etag != null && content.isPresent()) {
                            CacheEntry<T> newEntry = new CacheEntry<>(content.get(), etag, System.currentTimeMillis());
                            putInCache(cacheKey, newEntry);
                            LOGGER.debug("Cached GET response with ETag: %s", etag);
                        }

                        // Return success for 2xx status codes
                        if (statusCode >= 200 && statusCode < 300) {
                            return HttpResult.success(content.orElse(null), etag, statusCode);
                        }

                        // Return failure for error status codes
                        HttpErrorCategory errorCategory;
                        if (statusCode >= 400 && statusCode < 500) {
                            errorCategory = HttpErrorCategory.CLIENT_ERROR;
                        } else if (statusCode >= 500 && statusCode < 600) {
                            errorCategory = HttpErrorCategory.SERVER_ERROR;
                        } else {
                            // 3xx (other than 304), 1xx, or unknown codes
                            errorCategory = HttpErrorCategory.INVALID_CONTENT;
                        }

                        return HttpResult.<T>failureWithFallback(
                                "HTTP %d: %s".formatted(statusCode, method.methodName()),
                                null,
                                null, // no fallback content
                                errorCategory,
                                null, // no cached ETag
                                statusCode // include HTTP status code
                        );
                    })
                    .exceptionally(throwable -> {
                        // Classify exceptions
                        HttpErrorCategory category;
                        if (throwable instanceof IOException) {
                            category = HttpErrorCategory.NETWORK_ERROR;
                            /*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*//*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.warn("Network error during %s request: %s", method.methodName(), throwable.getMessage());
                        } else {
                            category = HttpErrorCategory.CONFIGURATION_ERROR;
                            /*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*//*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.error("Configuration error during %s request: %s", method.methodName(), throwable.getMessage());
                        }

                        return HttpResult.<T>failure(
                                "Request failed: %s".formatted(throwable.getMessage()),
                                throwable,
                                category
                        );
                    });

        } /*~~(TODO: Catch specific not Exception. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe)~~>*/catch (Exception e) {
            // Any exception during request building
            /*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*//*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.error("Failed to build HTTP request for %s: %s", method.methodName(), e.getMessage());
            return CompletableFuture.completedFuture(
                    HttpResult.failure(
                            "Failed to build HTTP request: " + e.getMessage(),
                            e,
                            HttpErrorCategory.CONFIGURATION_ERROR
                    )
            );
        }
    }

    /**
     * Core request execution method with async CompletableFuture.
     *
     * <p>
     * Implements the structural 304 Not Modified handling pattern:
     * </p>
     * <ol>
     *   <li>Retrieve cache entry BEFORE building request (local reference held)</li>
     *   <li>Add If-None-Match header if cache entry exists (GET only)</li>
     *   <li>Execute request asynchronously via HttpClient</li>
     *   <li>Handle 304 using cached entry (structurally guaranteed non-null)</li>
     * </ol>
     *
     * @param method HTTP method (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS)
     * @param body Request body (nullable, ignored for GET/HEAD/OPTIONS)
     * @param headers Additional HTTP headers
     * @return CompletableFuture with HttpResult (never null)
     * @throws IllegalArgumentException if safe methods (GET/HEAD/OPTIONS) called with body
     */
    private CompletableFuture<HttpResult<T>> send(HttpMethod method, @Nullable T body, Map<String, String> headers) {
        // Validate safe methods don't have bodies
        if (method.isSafe() && body != null) {
            throw new IllegalArgumentException(
                    "Safe method %s must not have a request body".formatted(method.methodName())
            );
        }

        // Generate cache key (only meaningful for GET with caching enabled)
        String cacheKey = generateCacheKey(httpHandler.getUri(), headers, cacheKeyHeaderFilter);

        // Retrieve cache entry BEFORE building request (hold local reference for 304 handling)
        @Nullable CacheEntry<T> cachedEntry = etagCachingEnabled ? cache.get(cacheKey) : null;

        try {
            // Build HTTP request
            HttpRequest.Builder requestBuilder = httpHandler.requestBuilder()
                    .method(method.methodName(), buildBodyPublisher(body));

            // Add custom headers
            headers.forEach(requestBuilder::header);

            // Add If-None-Match header if cached entry exists (GET only)
            if (cachedEntry != null && method == HttpMethod.GET) {
                requestBuilder.header("If-None-Match", cachedEntry.etag());
                LOGGER.debug("Adding If-None-Match header for GET request: %s", cachedEntry.etag());
            }

            HttpRequest request = requestBuilder.build();

            // Execute async request (no blocking!)
            return httpClient.sendAsync(request, responseConverter.getBodyHandler())
                    .thenApply(response -> {
                        int statusCode = response.statusCode();

                        // Handle 304 Not Modified - return cached content
                        if (statusCode == 304 && cachedEntry != null) {
                            LOGGER.debug("304 Not Modified - returning cached content");
                            return HttpResult.success(cachedEntry.content(), cachedEntry.etag(), 304);
                        }

                        // Extract ETag from response (all methods, not just GET)
                        @Nullable String etag = response.headers()
                                .firstValue("ETag")
                                .orElse(null);

                        // Convert response body
                        Optional<T> content = responseConverter.convert(response.body());

                        // Handle conversion failure
                        if (content.isEmpty() && statusCode >= 200 && statusCode < 300) {
                            /*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*//*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.warn("Response conversion failed for status %s", statusCode);
                            return HttpResult.<T>failure(
                                    "Failed to convert response body",
                                    null,
                                    HttpErrorCategory.INVALID_CONTENT
                            );
                        }

                        // Cache successful GET responses with ETag
                        if (method == HttpMethod.GET && statusCode == 200 && etag != null && content.isPresent()) {
                            CacheEntry<T> newEntry = new CacheEntry<>(content.get(), etag, System.currentTimeMillis());
                            putInCache(cacheKey, newEntry);
                            LOGGER.debug("Cached GET response with ETag: %s", etag);
                        }

                        // Return success for 2xx status codes
                        if (statusCode >= 200 && statusCode < 300) {
                            return HttpResult.success(content.orElse(null), etag, statusCode);
                        }

                        // Return failure for error status codes
                        HttpErrorCategory errorCategory;
                        if (statusCode >= 400 && statusCode < 500) {
                            errorCategory = HttpErrorCategory.CLIENT_ERROR;
                        } else if (statusCode >= 500 && statusCode < 600) {
                            errorCategory = HttpErrorCategory.SERVER_ERROR;
                        } else {
                            // 3xx (other than 304), 1xx, or unknown codes
                            errorCategory = HttpErrorCategory.INVALID_CONTENT;
                        }

                        return HttpResult.<T>failureWithFallback(
                                "HTTP %d: %s".formatted(statusCode, method.methodName()),
                                null,
                                null, // no fallback content
                                errorCategory,
                                null, // no cached ETag
                                statusCode // include HTTP status code
                        );
                    })
                    .exceptionally(throwable -> {
                        // Classify exceptions
                        HttpErrorCategory category;
                        if (throwable instanceof IOException) {
                            category = HttpErrorCategory.NETWORK_ERROR;
                            /*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*//*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.warn("Network error during %s request: %s", method.methodName(), throwable.getMessage());
                        } else {
                            category = HttpErrorCategory.CONFIGURATION_ERROR;
                            /*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*//*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.error("Configuration error during %s request: %s", method.methodName(), throwable.getMessage());
                        }

                        return HttpResult.<T>failure(
                                "Request failed: %s".formatted(throwable.getMessage()),
                                throwable,
                                category
                        );
                    });

        } /*~~(TODO: Catch specific not Exception. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe)~~>*/catch (Exception e) {
            // Any exception during request building
            /*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*//*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.error("Failed to build HTTP request for %s: %s", method.methodName(), e.getMessage());
            return CompletableFuture.completedFuture(
                    HttpResult.failure(
                            "Failed to build HTTP request: " + e.getMessage(),
                            e,
                            HttpErrorCategory.CONFIGURATION_ERROR
                    )
            );
        }
    }

    /**
     * Generates cache key from URI and filtered headers.
     *
     * <p>
     * Cache key format: URI + sorted headers (filtered by predicate)
     * </p>
     *
     * <h3>Example Cache Keys</h3>
     * <pre>
     * // With ALL filter:
     * "https://api.example.com/users|Accept:application/json|Authorization:Bearer token123"
     *
     * // With NONE filter (URI only):
     * "https://api.example.com/users"
     *
     * // With excluding("Authorization"):
     * "https://api.example.com/users|Accept:application/json"
     * </pre>
     *
     * @param uri Request URI
     * @param headers HTTP headers
     * @param filter Header filter predicate
     * @return Cache key string
     */
    private String generateCacheKey(URI uri, Map<String, String> headers, CacheKeyHeaderFilter filter) {
        StringBuilder keyBuilder = new StringBuilder(uri.toString());

        // Sort headers alphabetically for consistent cache keys
        headers.entrySet().stream()
                .filter(entry -> filter.includeInCacheKey(entry.getKey()))
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    keyBuilder.append('|');
                    keyBuilder.append(entry.getKey());
                    keyBuilder.append(':');
                    keyBuilder.append(entry.getValue());
                });

        return keyBuilder.toString();
    }

    /**
     * Builds HTTP request body publisher.
     *
     * <p>
     * Returns noBody() if:
     * </p>
     * <ul>
     *   <li>Body is null (safe methods like GET/HEAD/OPTIONS)</li>
     *   <li>Request converter not configured</li>
     * </ul>
     *
     * @param body Request body (nullable)
     * @return BodyPublisher for the request
     * @throws IllegalArgumentException if body serialization fails
     */
    private HttpRequest.BodyPublisher buildBodyPublisher(@Nullable T body) {
        if (body == null || requestConverter == null) {
            return HttpRequest.BodyPublishers.noBody();
        }

        return requestConverter.toBodyPublisher(body);
    }

    /**
     * Adds entry to cache and triggers eviction if needed.
     *
     * <p>
     * Thread-safe: ConcurrentHashMap allows concurrent puts.
     * Eviction uses weakly-consistent iterator safe for concurrent modification.
     * </p>
     *
     * @param key Cache key
     * @param entry Cache entry to store
     */
    private void putInCache(String key, CacheEntry<T> entry) {
        if (!etagCachingEnabled) {
            return;
        }

        cache.put(key, entry);
        checkAndEvict();
    }

    /**
     * Evicts oldest 10% of entries when cache size exceeds maxCacheSize.
     *
     * <p>
     * Thread-safe: Uses weakly-consistent iterator that doesn't throw
     * ConcurrentModificationException. In-flight requests holding local references
     * are unaffected by eviction.
     * </p>
     */
    private void checkAndEvict() {
        if (cache.size() <= maxCacheSize) {
            return;
        }

        int evictionCount = maxCacheSize / 10; // Remove oldest 10%
        if (evictionCount == 0) {
            evictionCount = 1; // Always remove at least one entry
        }

        // Find oldest entries by timestamp
        var oldestEntries = cache.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        (e1, e2) -> Long.compare(e1.timestamp(), e2.timestamp())
                ))
                .limit(evictionCount)
                .map(Map.Entry::getKey)
                .toList();

        // Remove oldest entries
        int removed = 0;
        for (String key : oldestEntries) {
            if (cache.remove(key) != null) {
                removed++;
            }
        }

        LOGGER.debug("Cache eviction: removed %s oldest entries (cache size: %s → %s)",
                removed, cache.size() + removed, cache.size());
    }

    /**
     * Builder for ETagAwareHttpAdapter.
     *
     * <p>
     * Not thread-safe - build once per adapter instance.
     * </p>
     *
     * @param <T> Response body type
     * @since 1.0
     */
    public static class Builder<T> {
        @Nullable
        private HttpHandler httpHandler;
        @Nullable
        private HttpResponseConverter<T> responseConverter;
        @Nullable
        private HttpRequestConverter<T> requestConverter;
        private boolean etagCachingEnabled = true;
        private CacheKeyHeaderFilter cacheKeyHeaderFilter = CacheKeyHeaderFilter.ALL;
        private int maxCacheSize = 1000;

        /**
         * Sets the HTTP handler (required).
         *
         * @param httpHandler Handler with base URI, SSL, and timeout configuration
         * @return this builder
         */
        public Builder<T> httpHandler(HttpHandler httpHandler) {
            this.httpHandler = httpHandler;
            return this;
        }

        /**
         * Sets the response converter (required).
         *
         * @param responseConverter Converter for HTTP response body to type T
         * @return this builder
         */
        public Builder<T> responseConverter(HttpResponseConverter<T> responseConverter) {
            this.responseConverter = responseConverter;
            return this;
        }

        /**
         * Sets the request converter (optional).
         *
         * <p>
         * Required for POST/PUT/PATCH operations with body of type T.
         * Can use different type for generic body methods.
         * </p>
         *
         * @param requestConverter Converter for type T to HTTP request body
         * @return this builder
         */
        public Builder<T> requestConverter(@Nullable HttpRequestConverter<T> requestConverter) {
            this.requestConverter = requestConverter;
            return this;
        }

        /**
         * Enables or disables ETag caching (default: true).
         *
         * @param enabled true to enable ETag caching, false to disable
         * @return this builder
         */
        public Builder<T> etagCachingEnabled(boolean enabled) {
            this.etagCachingEnabled = enabled;
            return this;
        }

        /**
         * Sets the cache key header filter (default: ALL).
         *
         * <p>
         * Controls which headers are included in cache key generation.
         * </p>
         *
         * <h3>Recommendations</h3>
         * <ul>
         *   <li>Single-user apps with token refresh: Use {@link CacheKeyHeaderFilter#excluding(String...)} to exclude "Authorization"</li>
         *   <li>Multi-user shared adapters: Use default {@link CacheKeyHeaderFilter#ALL} for security</li>
         *   <li>Per-user adapter instances: Safe to use {@link CacheKeyHeaderFilter#NONE} for efficiency</li>
         * </ul>
         *
         * @param filter Filter predicate for cache key header inclusion
         * @return this builder
         */
        public Builder<T> cacheKeyHeaderFilter(CacheKeyHeaderFilter filter) {
            this.cacheKeyHeaderFilter = Objects.requireNonNull(filter, "cacheKeyHeaderFilter cannot be null");
            return this;
        }

        /**
         * Sets the maximum cache size (default: 1000).
         *
         * <p>
         * When exceeded, 10% oldest entries (by timestamp) are automatically evicted.
         * </p>
         *
         * <h3>Sizing Guidelines</h3>
         * <ul>
         *   <li>Default (1000): Good for most applications (~100-300 unique URIs)</li>
         *   <li>Small (100-500): Mobile apps, embedded systems, memory-constrained environments</li>
         *   <li>Large (5000+): High-traffic servers with many unique endpoints</li>
         * </ul>
         *
         * @param maxCacheSize Maximum number of cache entries (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if maxCacheSize <= 0
         */
        public Builder<T> maxCacheSize(int maxCacheSize) {
            if (maxCacheSize <= 0) {
                throw new IllegalArgumentException("maxCacheSize must be positive, got: " + maxCacheSize);
            }
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        /**
         * Builds the adapter.
         *
         * @return Configured ETagAwareHttpAdapter
         * @throws NullPointerException if httpHandler or responseConverter not set
         */
        public ETagAwareHttpAdapter<T> build() {
            return new ETagAwareHttpAdapter<>(this);
        }
    }

    /**
     * Creates a new builder.
     *
     * @param <T> Response body type
     * @return New builder instance
     * @since 1.0
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
}

package de.cuioss.http.client.adapter;

import de.cuioss.http.client.HttpMethod;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.converter.VoidResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

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
    ) {}

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

        LOGGER.debug("Created ETagAwareHttpAdapter: etagCachingEnabled={}, maxCacheSize={}",
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
        LOGGER.debug("Cleared ETag cache: {} entries removed", sizeBefore);
    }

    @Override
    public CompletableFuture<HttpResult<T>> get() {
        return get(Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> get(Map<String, String> headers) {
        // TODO: Implement in Task 9
        throw new UnsupportedOperationException("Not yet implemented - Task 9");
    }

    @Override
    public CompletableFuture<HttpResult<T>> post(@Nullable T body) {
        return post(body, Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> post(@Nullable T body, Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> post(HttpRequestConverter<R> converter, @Nullable R body) {
        return post(converter, body, Map.of());
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> post(HttpRequestConverter<R> converter, @Nullable R body, Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public CompletableFuture<HttpResult<T>> put(@Nullable T body) {
        return put(body, Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> put(@Nullable T body, Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> put(HttpRequestConverter<R> converter, @Nullable R body) {
        return put(converter, body, Map.of());
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> put(HttpRequestConverter<R> converter, @Nullable R body, Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public CompletableFuture<HttpResult<T>> patch(@Nullable T body) {
        return patch(body, Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> patch(@Nullable T body, Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> patch(HttpRequestConverter<R> converter, @Nullable R body) {
        return patch(converter, body, Map.of());
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> patch(HttpRequestConverter<R> converter, @Nullable R body, Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete() {
        return delete(Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete(Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete(@Nullable T body) {
        return delete(body, Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete(@Nullable T body, Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> delete(HttpRequestConverter<R> converter, @Nullable R body) {
        return delete(converter, body, Map.of());
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> delete(HttpRequestConverter<R> converter, @Nullable R body, Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public CompletableFuture<HttpResult<T>> head() {
        return head(Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> head(Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
    }

    @Override
    public CompletableFuture<HttpResult<T>> options() {
        return options(Map.of());
    }

    @Override
    public CompletableFuture<HttpResult<T>> options(Map<String, String> headers) {
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("Not yet implemented - Task 11");
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

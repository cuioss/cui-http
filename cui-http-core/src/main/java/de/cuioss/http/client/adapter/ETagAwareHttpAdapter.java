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
package de.cuioss.http.client.adapter;

import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.HttpMethod;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.converter.VoidResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.HttpStatusFamily;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static de.cuioss.http.client.HttpLogMessages.ERROR;
import static de.cuioss.http.client.HttpLogMessages.WARN;

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
 *   <li>Only GET responses are cached (POST/PUT/DELETE never cached)</li>
 *   <li>ETags extracted from all responses for optimistic locking patterns</li>
 * </ul>
 *
 * <h3>What a 304 yields, per method</h3>
 * <p>
 * RFC 7232 gives {@code 304 Not Modified} a meaning only for a conditional request on a safe
 * method, so the adapter resolves it differently depending on the method used:
 * </p>
 * <ul>
 *   <li><strong>GET</strong> — success carrying the cached content, the cached ETag and status
 *       {@code 304}; the cache entry's timestamp is refreshed.</li>
 *   <li><strong>HEAD</strong> — success carrying status {@code 304} and the ETag but
 *       <strong>no body</strong>, because a HEAD response has none. Returning a prior GET's cached
 *       body here would fabricate content the server never sent.</li>
 *   <li><strong>POST / PUT / DELETE / PATCH / OPTIONS</strong> — an {@code INVALID_CONTENT}
 *       failure with status {@code 304}, carrying neither content nor ETag. RFC 7232 requires a
 *       failed precondition on an unsafe method to be answered with {@code 412 Precondition
 *       Failed}, so a 304 here is a server protocol violation, never a success.</li>
 * </ul>
 *
 * <p><strong>Do not supply your own {@code If-None-Match} header.</strong> The adapter manages
 * conditional GETs itself: when it holds a cached entry for a GET it sets {@code If-None-Match} to
 * the cached ETag, replacing (not appending to) any caller-supplied value. A caller-driven
 * conditional request is therefore only partially honored. Because caller headers are applied to
 * <em>every</em> method, a caller-supplied {@code If-None-Match} is also the only way a non-GET
 * request can draw a {@code 304} at all — which then resolves per the table above rather than as a
 * success. On a caching-disabled adapter a caller-triggered {@code 304} has no cached entry to
 * return and is reported as an {@code INVALID_CONTENT} failure. Let the adapter drive revalidation
 * instead.</p>
 *
 * <h3>ETag caching is optional</h3>
 * <p>
 * Despite the name, caching is a switchable feature rather than a precondition for using this
 * adapter. Building with {@code etagCachingEnabled(false)} yields a straight pass-through: no
 * {@code If-None-Match} header is ever sent, no response is cached, and every request goes to the
 * origin. {@link #statusCodeOnly(de.cuioss.http.client.handler.HttpHandler)} uses exactly that
 * configuration. ETags are still extracted from responses in either mode.
 * </p>
 *
 * <h3>Role in the adapter stack</h3>
 * <p>
 * This is the only {@link HttpAdapter} implementation that <em>originates</em> requests — it owns
 * the {@link de.cuioss.http.client.handler.HttpHandler} and performs the actual HTTP exchange.
 * {@link ResilientHttpAdapter} is <strong>not</strong> an alternative to it: it is a decorator that
 * wraps another {@code HttpAdapter} via {@link ResilientHttpAdapter#wrap} to add retry behaviour,
 * and delegates the exchange to the adapter it wraps. The two compose rather than compete — a
 * resilient, ETag-caching client is a {@code ResilientHttpAdapter} wrapping an
 * {@code ETagAwareHttpAdapter}.
 * </p>
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
 * Safe for concurrent use, subject to the one documented benign race below:
 * </p>
 * <ul>
 *   <li>Builder: NOT thread-safe (build once per adapter)</li>
 *   <li>Built adapter: Immutable fields, concurrent cache, safe for shared use</li>
 *   <li>HttpClient: Borrowed once in the constructor from the configured handler, reused for all
 *       requests</li>
 *   <li>Cache: ConcurrentHashMap with local reference pattern for 304 handling</li>
 * </ul>
 *
 * <h2>Client Ownership</h2>
 * <p>
 * This adapter <strong>borrows</strong> the {@link HttpClient} from the {@link HttpHandler} it is
 * configured with — the handler creates and owns that client, and the adapter merely holds a
 * reference to it. The adapter is deliberately <em>not</em> {@link AutoCloseable}: closing a
 * borrowed client would shut down a resource shared with the handler and with every other adapter
 * built from it. Release the client by closing the <em>handler</em>
 * ({@link HttpHandler#close()}), once every adapter built from it is done.
 * </p>
 *
 * <p><strong>Benign last-writer-wins race on cache maintenance.</strong> Two cache writes are
 * check-then-act sequences on the {@code ConcurrentHashMap} rather than atomic compare-and-set
 * operations: the 304 timestamp refresh (read the entry, then write it back with a new timestamp)
 * and the eviction of a stale entry after a {@code 200} that could not replace it (observe that no
 * replacement entry can be built, then remove). Concurrent revalidations of the same key can
 * therefore interleave, and the last writer wins.</p>
 *
 * <p>This is benign, and deliberately not locked. Every writer for a given key stores a
 * fully-formed immutable {@link CacheEntry}, so no reader can observe a torn or mismatched entry;
 * concurrent 304 refreshes write the same content and the same ETag, differing only in a timestamp
 * that feeds the eviction heuristic alone. The observable outcomes are an entry surviving a moment
 * longer than a competing eviction intended, or an eviction discarding a just-refreshed timestamp
 * — neither affects correctness, and both resolve on the next request. Adding locking or a
 * compute-style CAS here would cost contention on the hot path to buy nothing.</p>
 *
 * @param <T> Response body type
 * @since 1.0
 * @see HttpAdapter
 * @see CacheKeyHeaderFilter
 * @see VoidResponseConverter
 */
@SuppressWarnings("RedundantTypeArguments")
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
     * Cache context for request processing.
     *
     * @param cacheKey Cache key for the request
     * @param cachedEntry Cached entry if available, null otherwise
     * @param <T> Content type
     */
    private record CacheContext<T>(
    String cacheKey,
    @Nullable
    CacheEntry<T> cachedEntry
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

    // The no-argument and no-converter overloads below re-implement the HttpAdapter interface
    // defaults verbatim: each simply forwards to its Map/converter-taking sibling exactly as the
    // default does. Inspection surfaced no behavioural reason for the duplication — no override
    // adds caching, header, or dispatch logic of its own — so it is recorded here as deliberate
    // redundancy rather than removed, since deleting it would change no behaviour but would churn a
    // published class's method table for no gain. Any future divergence belongs in send(...), which
    // is the single point every overload funnels through.
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
        CacheContext<T> cacheContext = prepareCacheContext(method, body, headers);

        // Serialize the body via the explicit converter. Serialization failures map to
        // INVALID_CONTENT per the HttpRequestConverter contract (see serializationFailure).
        HttpRequest.BodyPublisher bodyPublisher;
        try {
            bodyPublisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : requestConverter.toBodyPublisher(body);
        } catch (IllegalArgumentException e) {
            return serializationFailure(method, e);
        }

        ContentType requestContentType = body != null ? requestConverter.contentType() : null;
        return buildAndExecute(method, bodyPublisher, requestContentType, headers, cacheContext);
    }

    /**
     * Applies converter-derived {@code Accept} and {@code Content-Type} headers.
     *
     * <p>Each default is applied <strong>only when the caller has not already supplied that
     * header</strong> (checked case-insensitively against {@code callerHeaders}), so a
     * caller-provided value always wins. {@code Accept} is defaulted from the response
     * converter's content type; {@code Content-Type} is defaulted only when a request body is
     * present.</p>
     *
     * @param requestBuilder the request builder to configure
     * @param callerHeaders caller-supplied headers (used to detect explicit overrides)
     * @param requestContentType the request body content type, or null when there is no body
     */
    private void applyContentTypeHeaders(HttpRequest.Builder requestBuilder,
            Map<String, String> callerHeaders, @Nullable ContentType requestContentType) {
        if (!hasHeaderIgnoreCase(callerHeaders, "Accept")) {
            requestBuilder.setHeader("Accept", responseConverter.contentType().mediaType());
        }
        if (requestContentType != null && !hasHeaderIgnoreCase(callerHeaders, "Content-Type")) {
            requestBuilder.setHeader("Content-Type", requestContentType.toHeaderValue());
        }
    }

    /**
     * Checks whether the header map contains the given header name, ignoring case.
     *
     * @param headers the caller-supplied header map to search
     * @param name the header name to look for (matched case-insensitively)
     * @return true if a header with that name is present, false otherwise
     */
    private static boolean hasHeaderIgnoreCase(Map<String, String> headers, String name) {
        for (String key : headers.keySet()) {
            // name is a non-null literal - calling on it is null-key safe for maps allowing null keys
            if (name.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
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
     *   <li>Route every 304 through {@link #handleNotModified}, which resolves it against the
     *       cached entry when one is held and reports the RFC 7232 violation when none is</li>
     * </ol>
     *
     * @param method HTTP method (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS)
     * @param body Request body (nullable, ignored for GET/HEAD/OPTIONS)
     * @param headers Additional HTTP headers
     * @return CompletableFuture with HttpResult (never null)
     * @throws IllegalArgumentException if safe methods (GET/HEAD/OPTIONS) called with body
     * @throws IllegalStateException if a body is supplied but no request converter is configured
     */
    private CompletableFuture<HttpResult<T>> send(HttpMethod method, @Nullable T body, Map<String, String> headers) {
        CacheContext<T> cacheContext = prepareCacheContext(method, body, headers);

        // Enforce the documented contract: a request body requires a configured request converter.
        // Thrown (not returned as a failure) so callers see the promised IllegalStateException rather
        // than an empty body being sent silently.
        if (body != null && requestConverter == null) {
            throw new IllegalStateException(
                    "No request converter configured: cannot send a %s request body. Configure a requestConverter on the adapter, or use the explicit-converter method overload."
                            .formatted(method.methodName()));
        }

        // Serialize the body via the adapter's converter. Serialization failures map to
        // INVALID_CONTENT per the HttpRequestConverter contract (see serializationFailure).
        HttpRequest.BodyPublisher bodyPublisher;
        try {
            bodyPublisher = buildBodyPublisher(body);
        } catch (IllegalArgumentException e) {
            return serializationFailure(method, e);
        }

        // Content-Type only applies when a body is serialized via the request converter.
        ContentType requestContentType = body != null && requestConverter != null
                ? requestConverter.contentType() : null;
        return buildAndExecute(method, bodyPublisher, requestContentType, headers, cacheContext);
    }

    /**
     * Builds the HTTP request from an already-serialized body publisher, negotiates content-type
     * headers, applies the conditional {@code If-None-Match} header for cached GETs, and executes
     * the request asynchronously. Shared by {@link #send} and {@link #sendWithConverter} so that
     * fixes to header/cache/If-None-Match handling land in one place.
     *
     * @param method HTTP method
     * @param bodyPublisher already-serialized request body publisher
     * @param requestContentType request body content type, or null when there is no body
     * @param headers caller-supplied headers
     * @param cacheContext prepared cache key and (optional) cached entry
     * @return CompletableFuture with HttpResult
     */
    private CompletableFuture<HttpResult<T>> buildAndExecute(
            HttpMethod method,
            HttpRequest.BodyPublisher bodyPublisher,
            @Nullable ContentType requestContentType,
            Map<String, String> headers,
            CacheContext<T> cacheContext) {
        String cacheKey = cacheContext.cacheKey();
        CacheEntry<T> cachedEntry = cacheContext.cachedEntry();

        try {
            HttpRequest.Builder requestBuilder = httpHandler.requestBuilder()
                    .method(method.methodName(), bodyPublisher);

            // Negotiate content types from the converters (caller headers take precedence)
            applyContentTypeHeaders(requestBuilder, headers, requestContentType);

            // Add custom headers
            headers.forEach(requestBuilder::header);

            // Add If-None-Match header if cached entry exists (GET only). Uses setHeader (replace),
            // not header (append), so the adapter's conditional validator wins over any caller-
            // supplied If-None-Match instead of sending two conflicting values.
            if (cachedEntry != null && method == HttpMethod.GET) {
                requestBuilder.setHeader("If-None-Match", cachedEntry.etag());
                LOGGER.debug("Adding If-None-Match header for GET request: %s", cachedEntry.etag());
            }

            HttpRequest request = requestBuilder.build();

            // Execute async request with extracted response handler
            return executeAsyncRequest(request, method, cachedEntry, cacheKey);

        } catch (IllegalArgumentException | IllegalStateException e) {
            // Request building failed: invalid method, headers, or builder state
            LOGGER.error(ERROR.REQUEST_BUILD_FAILED, method.methodName(), e.getMessage());
            return CompletableFuture.completedFuture(
                    HttpResult.failure(
                            "Failed to build HTTP request: " + e.getMessage(),
                            e,
                            HttpErrorCategory.fromException(e)
                    )
            );
        }
    }

    /**
     * Produces the failure result for a request-body serialization error.
     *
     * <p>Per the {@link HttpRequestConverter} contract, a serialization failure surfaces as an
     * {@link IllegalArgumentException} and is mapped to {@link HttpErrorCategory#INVALID_CONTENT}
     * (a client-data problem), not {@code CONFIGURATION_ERROR}.</p>
     *
     * @param method HTTP method for logging
     * @param e the serialization failure
     * @return a completed future with an INVALID_CONTENT failure result
     */
    private CompletableFuture<HttpResult<T>> serializationFailure(HttpMethod method, IllegalArgumentException e) {
        LOGGER.warn(WARN.REQUEST_SERIALIZATION_FAILED, method.methodName(), e.getMessage());
        return CompletableFuture.completedFuture(
                HttpResult.failure(
                        "Failed to serialize request body: " + e.getMessage(),
                        e,
                        HttpErrorCategory.INVALID_CONTENT
                )
        );
    }

    /**
     * Executes async HTTP request with error handling.
     *
     * <p>
     * Centralizes the async request execution and exception handling logic
     * to eliminate code duplication between send() and sendWithConverter().
     * </p>
     *
     * @param request HTTP request to execute
     * @param method HTTP method for logging
     * @param cachedEntry Cached entry for 304 handling
     * @param cacheKey Cache key for storing response
     * @return CompletableFuture with HttpResult
     */
    private CompletableFuture<HttpResult<T>> executeAsyncRequest(
            HttpRequest request,
            HttpMethod method,
            @Nullable CacheEntry<T> cachedEntry,
            String cacheKey
    ) {
        return httpClient.sendAsync(request, responseConverter.getBodyHandler())
                .thenApply(response -> handleHttpResponse(response, method, cachedEntry, cacheKey))
                .exceptionally(throwable -> {
                    // Classify via the single source of truth in client.result
                    HttpErrorCategory category = HttpErrorCategory.fromException(throwable);
                    if (category == HttpErrorCategory.NETWORK_ERROR) {
                        LOGGER.warn(WARN.NETWORK_ERROR_DURING_REQUEST, method.methodName(), throwable.getMessage());
                    } else {
                        LOGGER.error(ERROR.CONFIGURATION_ERROR_DURING_REQUEST, method.methodName(), throwable.getMessage());
                    }

                    return HttpResult.<T>failure(
                            "Request failed: %s".formatted(throwable.getMessage()),
                            throwable,
                            category
                    );
                });
    }

    /**
     * Generates cache key from URI and filtered headers.
     *
     * <p>
     * Cache key format: URI + sorted headers (filtered by predicate)
     * </p>
     *
     * <h3>Example Cache Keys</h3>
     * <pre>{@code
     * // With ALL filter:
     * "https://api.example.com/users|Accept:application/json|Authorization:Bearer token123"
     *
     * // With NONE filter (URI only):
     * "https://api.example.com/users"
     *
     * // With excluding("Authorization"):
     * "https://api.example.com/users|Accept:application/json"
     * }</pre>
     *
     * @param uri Request URI
     * @param headers HTTP headers
     * @param filter Header filter predicate
     * @return Cache key string
     */
    // Package-private for testing
    String generateCacheKey(URI uri, Map<String, String> headers, CacheKeyHeaderFilter filter) {
        StringBuilder keyBuilder = new StringBuilder(uri.toString());

        // Filter first, then sort for consistent cache keys
        var filteredEntries = new ArrayList<Map.Entry<String, String>>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (filter.includeInCacheKey(entry.getKey())) {
                filteredEntries.add(entry);
            }
        }
        filteredEntries.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
        for (Map.Entry<String, String> entry : filteredEntries) {
            keyBuilder.append('|');
            // Normalize the header name to lower case so that maps differing only in header-name
            // case (e.g. "Accept-Language" vs "accept-language" - identical on the wire) resolve to
            // the same cache entry instead of duplicate downloads.
            keyBuilder.append(escapeCacheKeyToken(entry.getKey().toLowerCase(Locale.ROOT)));
            keyBuilder.append(':');
            keyBuilder.append(escapeCacheKeyToken(entry.getValue()));
        }

        return keyBuilder.toString();
    }

    /**
     * Escapes the cache key delimiters {@code |} and {@code :} inside header
     * names and values so that a malicious value cannot forge additional
     * key-value entries in the cache key string.
     */
    private static String escapeCacheKeyToken(String token) {
        // Use backslash as escape character: \ -> \\, | -> \|, : -> \:
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '\\' || c == '|' || c == ':') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Builds the HTTP request body publisher using the adapter's request converter.
     *
     * <p>Returns {@code noBody()} for a null body (e.g. GET/HEAD/OPTIONS). A non-null body with no
     * configured request converter is rejected by the caller ({@link #send}) with
     * {@link IllegalStateException} before this method is reached, so the null-converter guard here
     * only ever applies to the null-body case.</p>
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
     * Removes the entry stored under {@code key}, if any.
     *
     * <p>Guarded by {@code etagCachingEnabled} exactly as {@link #putInCache} is, so a
     * caching-disabled adapter never touches the map.</p>
     *
     * @param key Cache key to drop
     */
    private void evictFromCache(String key) {
        if (!etagCachingEnabled) {
            return;
        }

        if (cache.remove(key) != null) {
            // The key embeds request header values, so it is deliberately not logged.
            LOGGER.debug("Removed stale cache entry after a GET 200 that could not replace it");
        }
    }

    /**
     * Evicts oldest 10% of entries when cache size exceeds maxCacheSize.
     *
     * <p>
     * Thread-safe: Uses weakly-consistent iterator that doesn't throw
     * ConcurrentModificationException. In-flight requests holding local references
     * are unaffected by eviction.
     * </p>
     *
     * <h3>Cost</h3>
     * <p>
     * Each over-limit put sorts the whole cache by timestamp, so the eviction pass is
     * {@code O(n log n)} in the cache size. That cost is amortized across the batch: one pass frees
     * 10% of {@code maxCacheSize} entries, so it recurs only once per that many over-limit puts. At
     * the default size of 1000 the pass is measured in microseconds. A very large
     * {@code maxCacheSize} trades a proportionally more expensive eviction pass for a higher hit
     * rate — see {@link Builder#maxCacheSize(int)}.
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
                        Comparator.comparingLong(CacheEntry::timestamp)
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
     * Handles HTTP response with caching, conversion, and error categorization.
     *
     * <p>
     * This method encapsulates the complete response handling logic including:
     * </p>
     * <ul>
     *   <li>304 Not Modified detection, resolved per method by {@link #handleNotModified}</li>
     *   <li>ETag extraction from response headers</li>
     *   <li>Response body conversion using configured converter</li>
     *   <li>GET {@code 200} cache maintenance — store when the response yields both a validator and
     *       content, evict otherwise. Runs ahead of the conversion-failure return so a {@code 200}
     *       the converter rejects still drops the entry it superseded</li>
     *   <li>Conversion failure handling for 2xx responses, excluding the no-body statuses
     *       {@code 204} and {@code 205} (see {@link #isNoBodyStatus})</li>
     *   <li>Success/failure result creation based on status code</li>
     * </ul>
     *
     * @param response HTTP response from server
     * @param method HTTP method used for request
     * @param cachedEntry Cached entry if available (for 304 handling)
     * @param cacheKey Cache key for storing new entries
     * @return HttpResult with success or failure status
     */
    private HttpResult<T> handleHttpResponse(
            HttpResponse<?> response,
            HttpMethod method,
            @Nullable CacheEntry<T> cachedEntry,
            String cacheKey
    ) {
        int statusCode = response.statusCode();

        // Extract ETag from response (all methods, not just GET). Extracted ahead of the 304 branch
        // so a HEAD revalidation can prefer the validator the 304 itself returned.
        String etag = response.headers()
                .firstValue("ETag")
                .orElse(null);

        // Handle 304 Not Modified - the outcome depends on the request method (RFC 7232). The gate
        // is the status alone: a cache entry exists only for GET and HEAD (see canReadCache), so
        // additionally requiring one here would make the unsafe-method branch unreachable and let a
        // POST/PUT/PATCH/DELETE/OPTIONS 304 fall through to the generic error path below, where the
        // RFC 7232 violation is never named. handleNotModified resolves the null-entry cases itself.
        if (statusCode == 304) {
            return handleNotModified(method, cachedEntry, cacheKey, etag);
        }

        // Convert response body
        Optional<T> content = responseConverter.convert(response.body());

        // Cache maintenance for a fresh GET 200. With an ETag and content, store (or refresh) the
        // entry. Otherwise evict: the 200 is a fresh representation that supersedes whatever was
        // cached, and no replacement entry could be built from it, so the old one is provably
        // stale. Both non-replaceable cases reach the eviction, and the reason does not matter -
        // a missing validator (content the server no longer identifies) and an unparseable body
        // under an ETag (a validator with nothing to pair it with) are equally unable to produce a
        // replacement. Leaving the superseded entry behind would let a later failure serve it as
        // fallback content, or revalidate it with a dead If-None-Match.
        //
        // This runs BEFORE the conversion-failure return below, which is what makes the
        // unparseable-body cases reachable at all: evicting after that early return would never
        // execute for them, leaving a later failure free to serve content the 200 replaced.
        if (method == HttpMethod.GET && statusCode == 200) {
            if (etag != null && content.isPresent()) {
                putInCache(cacheKey, new CacheEntry<>(content.get(), etag, System.currentTimeMillis()));
                LOGGER.debug("Cached GET response with ETag: %s", etag);
            } else {
                evictFromCache(cacheKey);
            }
        }

        // Handle conversion failure. Two exemptions: converters that intentionally produce no
        // content (e.g. VoidResponseConverter for status-code-only operations), and statuses RFC
        // 7231 defines as carrying no body at all, where emptiness is the protocol-mandated
        // outcome rather than a failed conversion.
        if (content.isEmpty() && HttpStatusFamily.isSuccess(statusCode)
                && !isNoBodyStatus(statusCode)
                && !responseConverter.emptyContentIsValid()) {
            LOGGER.warn(WARN.RESPONSE_CONVERSION_FAILED, statusCode);
            // Carry the status and the response ETag: both are in hand here, and discarding them
            // leaves the caller unable to tell "200 but unparseable" from any other invalid-content
            // case. fallbackContent stays null - a conversion failure implies no cached content.
            return HttpResult.<T>failureWithFallback(
                    "Failed to convert response body (HTTP %d)".formatted(statusCode),
                    null,
                    null,
                    HttpErrorCategory.INVALID_CONTENT,
                    etag,
                    statusCode
            );
        }

        // Return success for 2xx status codes
        if (HttpStatusFamily.isSuccess(statusCode)) {
            return HttpResult.<T>success(content.orElse(null), etag, statusCode);
        }

        // Return failure for error status codes. When a cached entry is in hand (a GET that was
        // revalidated but the server returned an error instead of 304), surface it as fallback
        // content so callers can degrade gracefully - the documented "Failure with fallback" state.
        // Only GET may use cached fallback: the cache is populated exclusively by GET (see above),
        // so gating on the method prevents a non-GET failure from surfacing a prior GET's body/ETag
        // through a method-agnostic cache key.
        HttpErrorCategory errorCategory = HttpStatusFamily.fromStatusCode(statusCode).toErrorCategory();
        boolean canUseCachedFallback = method == HttpMethod.GET && cachedEntry != null;

        return HttpResult.<T>failureWithFallback(
                "HTTP %d: %s".formatted(statusCode, method.methodName()),
                null,
                canUseCachedFallback ? cachedEntry.content() : null, // fallback to cached GET content when available
                errorCategory,
                canUseCachedFallback ? cachedEntry.etag() : null, // cached GET ETag when available
                statusCode // include HTTP status code
        );
    }

    /**
     * Reports whether {@code method} can resolve a cache entry.
     *
     * <p>GET populates and reads the cache; HEAD reads it so a conditional HEAD answered with
     * {@code 304} can be resolved against the stored validator (see {@link #handleNotModified}).
     * No other method touches it.</p>
     *
     * <p><strong>Do not narrow this to GET.</strong> Dropping HEAD would leave a conditional HEAD
     * with no cached entry, silently turning its {@code 304} into a plain failure and deleting the
     * documented HEAD behaviour.</p>
     *
     * @param method the request method
     * @return true for GET and HEAD, false otherwise
     */
    private static boolean canReadCache(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD;
    }

    /**
     * Reports whether RFC 7231 defines {@code statusCode} as carrying no response body.
     *
     * <p>A converter that maps an empty payload to {@link Optional#empty()} would otherwise turn a
     * perfectly valid {@code 204}/{@code 205} into an {@code INVALID_CONTENT} failure. Emptiness is
     * the protocol-mandated outcome for these two statuses, so they bypass the conversion-failure
     * guard regardless of the converter in use.</p>
     *
     * <p>Deliberately limited to {@code 204} and {@code 205}. An empty {@code 200} is <em>not</em> a
     * protocol-defined no-body response; whether it is acceptable stays governed by
     * {@link HttpResponseConverter#emptyContentIsValid()}.</p>
     *
     * @param statusCode the response status code
     * @return true for {@code 204 No Content} and {@code 205 Reset Content}, false otherwise
     */
    private static boolean isNoBodyStatus(int statusCode) {
        return statusCode == 204 || statusCode == 205;
    }

    /**
     * Produces the result for every {@code 304 Not Modified} response. RFC 7232 gives 304 a meaning
     * only for a conditional request on a safe method, so the method is examined <em>before</em> the
     * cache entry:
     *
     * <ul>
     *   <li><strong>Any method other than GET or HEAD</strong> — a protocol violation, decided first
     *       and independently of the cache. RFC 7232 requires a failed precondition on an unsafe
     *       method to be answered with {@code 412 Precondition Failed}, never 304. Reported as an
     *       {@link HttpErrorCategory#INVALID_CONTENT} failure carrying neither cached content nor a
     *       cached validator, so no prior GET's data can leak out through the method-agnostic cache
     *       key. This branch is reachable precisely because it does not require a cache entry:
     *       {@link #prepareCacheContext} yields a null entry for every unsafe method, so a gate that
     *       demanded one would be dead code and the violation would never be named.</li>
     *   <li><strong>GET or HEAD with no cached entry</strong> — an unsolicited 304. The adapter only
     *       sends {@code If-None-Match} when it holds an entry, so a 304 with nothing to resolve it
     *       against (a caller-supplied conditional header, or a caching-disabled adapter) is equally
     *       an {@link HttpErrorCategory#INVALID_CONTENT} failure with no content and no validator.</li>
     *   <li><strong>GET</strong> — the cached body <em>is</em> the response body. The entry's
     *       timestamp is refreshed so a frequently revalidated entry is not evicted ahead of a
     *       colder one under the timestamp-based eviction heuristic.</li>
     *   <li><strong>HEAD</strong> — status and validator only. A HEAD response carries no body, so
     *       surfacing a prior GET's cached body here would fabricate content the server never sent.
     *       The cache is populated by GET alone, so no timestamp refresh applies.</li>
     * </ul>
     *
     * @param method HTTP method the request used
     * @param cachedEntry cached entry held for this request, or null when none is held
     * @param cacheKey cache key the entry is stored under
     * @param responseEtag ETag the 304 carried, or null when it carried none
     * @return the method-appropriate result
     */
    private HttpResult<T> handleNotModified(
            HttpMethod method,
            @Nullable CacheEntry<T> cachedEntry,
            String cacheKey,
            @Nullable String responseEtag
    ) {
        if (!canReadCache(method)) {
            LOGGER.debug("304 Not Modified is not a valid response to %s - reporting a protocol violation",
                    method.methodName());
            return HttpResult.<T>failureWithFallback(
                    "HTTP 304 is not a valid response to a %s request (RFC 7232 requires 412)".formatted(method.methodName()),
                    null,
                    null,
                    HttpErrorCategory.INVALID_CONTENT,
                    null,
                    304
            );
        }

        if (cachedEntry == null) {
            LOGGER.debug("304 Not Modified for %s with no cached entry - reporting an unsolicited 304",
                    method.methodName());
            return HttpResult.<T>failureWithFallback(
                    "HTTP 304 for a %s request with no cached entry to resolve it against (RFC 7232 permits 304 only in response to a conditional request the adapter issued)"
                            .formatted(method.methodName()),
                    null,
                    null,
                    HttpErrorCategory.INVALID_CONTENT,
                    null,
                    304
            );
        }

        if (method == HttpMethod.GET) {
            LOGGER.debug("304 Not Modified - returning cached content");
            putInCache(cacheKey, new CacheEntry<>(cachedEntry.content(), cachedEntry.etag(), System.currentTimeMillis()));
            return HttpResult.<T>success(cachedEntry.content(), cachedEntry.etag(), 304);
        }

        // Only HEAD remains: the unsafe methods returned above and GET was handled just now.
        LOGGER.debug("304 Not Modified for HEAD - returning status and ETag without a body");
        return HttpResult.<T>success(null, responseEtag != null ? responseEtag : cachedEntry.etag(), 304);
    }

    /**
     * Validates request and prepares cache context.
     *
     * @param method HTTP method
     * @param body Request body (nullable)
     * @param headers HTTP headers
     * @return Cache context with key and cached entry
     * @throws IllegalArgumentException if safe method called with body
     */
    private CacheContext<T> prepareCacheContext(HttpMethod method, @Nullable Object body, Map<String, String> headers) {
        // Defensive guard: the public methods for safe methods (GET/HEAD/OPTIONS) always pass a null
        // body, so this is not reachable via the public API. It is retained to fail fast on a
        // programming error if a body is ever routed to a safe method internally.
        if (method.isSafe() && body != null) {
            throw new IllegalArgumentException(
                    "Safe method %s must not have a request body".formatted(method.methodName())
            );
        }

        // Only GET and HEAD can interact with the cache: GET populates and reads it, HEAD reads it
        // to resolve a 304 revalidation. For any other method - and for an adapter built with
        // etagCachingEnabled(false), which includes every statusCodeOnly adapter - the key would be
        // built, sorted and escaped on every request and then never read, so skip the work.
        if (!etagCachingEnabled || !canReadCache(method)) {
            return new CacheContext<>("", null);
        }

        String cacheKey = generateCacheKey(httpHandler.getUri(), headers, cacheKeyHeaderFilter);

        // Retrieve cache entry BEFORE building request (hold local reference for 304 handling)
        return new CacheContext<>(cacheKey, cache.get(cacheKey));
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
         * <p>
         * The built adapter <strong>borrows</strong> this handler's {@link HttpClient}; it does not
         * own it and never closes it. Releasing the client stays the caller's responsibility, via
         * {@link HttpHandler#close()} on the handler supplied here — and only once every adapter
         * built from that handler is done with it, since the client is shared.
         * </p>
         *
         * @param httpHandler Handler with base URI, SSL, and timeout configuration; its
         *                    {@link HttpClient} is borrowed, not owned, by the built adapter
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
         * <h3>Cost Trade-off</h3>
         * <p>
         * Eviction sorts the whole cache by timestamp, so each over-limit put that triggers a pass
         * costs {@code O(n log n)} in this value — amortized across the 10% batch it frees. Raising
         * {@code maxCacheSize} therefore buys hit rate at the price of a proportionally more
         * expensive (though proportionally rarer) eviction pass.
         * </p>
         *
         * @param maxCacheSize Maximum number of cache entries (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if maxCacheSize &lt;= 0
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

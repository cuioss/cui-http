package de.cuioss.http.client.adapter;

import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.result.HttpResult;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter for sending HTTP requests and receiving structured results.
 * Provides method-specific operations following HTTP semantics.
 *
 * <p><b>Async-First Design:</b> All methods return {@code CompletableFuture<HttpResult<T>>}
 * for non-blocking operation. Use {@code .join()} or blocking convenience methods for
 * synchronous usage.
 *
 * <p>The adapter is configured with a HttpResponseConverter&lt;T&gt; for responses.
 * Request bodies can be sent using:
 * <ul>
 *   <li>Same type T (if adapter has request converter configured)</li>
 *   <li>Different type R with explicit HttpRequestConverter&lt;R&gt;</li>
 * </ul>
 *
 * <h2>Method Naming Convention</h2>
 *
 * <p>This API uses an <b>async-first design philosophy</b> where non-blocking operation
 * is the default, primary behavior. The naming convention reflects this priority:
 *
 * <ul>
 *   <li><b>Primary methods</b> ({@code get()}, {@code post()}, etc.) return
 *       {@code CompletableFuture<HttpResult<T>>} - non-blocking by default</li>
 *   <li><b>Convenience methods</b> ({@code getBlocking()}, {@code postBlocking()}, etc.)
 *       add the {@code Blocking} suffix to indicate deviation from the default</li>
 * </ul>
 *
 * <h3>Design Rationale</h3>
 *
 * <ul>
 *   <li>Modern HTTP clients are inherently async - {@code java.net.http.HttpClient}
 *       uses {@code sendAsync()} as the foundation</li>
 *   <li>Most use cases benefit from async - reduces thread blocking, improves scalability,
 *       better resource utilization</li>
 *   <li>Blocking is the exception, not the rule - mark the less-common pattern
 *       (blocking) with a suffix</li>
 *   <li>Consistency with reactive patterns - reactive frameworks (Project Reactor, RxJava)
 *       use blocking suffix: {@code .block()}, {@code .toBlocking()}</li>
 *   <li>API guidance - method names guide developers toward better practices (async-first)</li>
 *   <li>CompletableFuture is explicit - return type makes async nature unmistakable</li>
 * </ul>
 *
 * <h3>Usage Pattern</h3>
 *
 * <pre>{@code
 * // Primary async pattern (recommended)
 * CompletableFuture<HttpResult<User>> future = adapter.get();
 * future.thenAccept(result -> {
 *     if (result.isSuccess()) {
 *         processUser(result.getContent().orElseThrow());
 *     }
 * });
 *
 * // Blocking convenience (simple synchronous cases)
 * HttpResult<User> result = adapter.getBlocking();
 * if (result.isSuccess()) {
 *     processUser(result.getContent().orElseThrow());
 * }
 * }</pre>
 *
 * <p><b>Important:</b> Always check return types. If you see {@code CompletableFuture<T>},
 * you're working with async code and must handle it appropriately ({@code .thenAccept()},
 * {@code .thenApply()}, {@code .exceptionally()}, etc.). Never call {@code .get()} or
 * {@code .join()} on a CompletableFuture unless you specifically need blocking behavior.
 *
 * @param <T> Response body type
 * @since 1.0
 */
public interface HttpAdapter<T> {

    // ========== NO-BODY METHODS (ASYNC) ==========

    /**
     * Sends GET request to retrieve resource (async).
     * GET requests do not have a body (RFC 7231).
     *
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with response or error information
     */
    CompletableFuture<HttpResult<T>> get(Map<String, String> additionalHeaders);

    /**
     * Sends GET request to retrieve resource (async).
     * GET requests do not have a body (RFC 7231).
     *
     * @return CompletableFuture containing result with response or error information
     */
    default CompletableFuture<HttpResult<T>> get() {
        return get(Map.of());
    }

    /**
     * Sends HEAD request to retrieve headers only (async, no body in response).
     *
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with response metadata
     */
    CompletableFuture<HttpResult<T>> head(Map<String, String> additionalHeaders);

    /**
     * Sends HEAD request to retrieve headers only (async, no body in response).
     *
     * @return CompletableFuture containing result with response metadata
     */
    default CompletableFuture<HttpResult<T>> head() {
        return head(Map.of());
    }

    /**
     * Sends OPTIONS request to query supported methods (async).
     *
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with server capabilities
     */
    CompletableFuture<HttpResult<T>> options(Map<String, String> additionalHeaders);

    /**
     * Sends OPTIONS request to query supported methods (async).
     *
     * @return CompletableFuture containing result with server capabilities
     */
    default CompletableFuture<HttpResult<T>> options() {
        return options(Map.of());
    }

    /**
     * Sends DELETE request to remove resource (async, no body).
     * Most DELETE requests don't have a body.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with response or error information
     */
    CompletableFuture<HttpResult<T>> delete(Map<String, String> additionalHeaders);

    /**
     * Sends DELETE request to remove resource (async, no body).
     * Most DELETE requests don't have a body.
     *
     * @return CompletableFuture containing result with response or error information
     */
    default CompletableFuture<HttpResult<T>> delete() {
        return delete(Map.of());
    }

    // ========== BODY METHODS (T → T, uses configured request converter) ==========

    /**
     * Sends POST request with body of type T (async).
     * Requires adapter to have a request converter configured for type T.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with created resource or error
     * @throws IllegalStateException if no request converter configured for type T
     */
    CompletableFuture<HttpResult<T>> post(@Nullable T requestBody, Map<String, String> additionalHeaders);

    /**
     * Sends POST request with body of type T (async).
     * Requires adapter to have a request converter configured for type T.
     *
     * @param requestBody Request body content, may be null
     * @return CompletableFuture containing result with created resource or error
     * @throws IllegalStateException if no request converter configured for type T
     */
    default CompletableFuture<HttpResult<T>> post(@Nullable T requestBody) {
        return post(requestBody, Map.of());
    }

    /**
     * Sends PUT request with body of type T (async).
     * Requires adapter to have a request converter configured for type T.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with updated resource or error
     * @throws IllegalStateException if no request converter configured for type T
     */
    CompletableFuture<HttpResult<T>> put(@Nullable T requestBody, Map<String, String> additionalHeaders);

    /**
     * Sends PUT request with body of type T (async).
     * Requires adapter to have a request converter configured for type T.
     *
     * @param requestBody Request body content, may be null
     * @return CompletableFuture containing result with updated resource or error
     * @throws IllegalStateException if no request converter configured for type T
     */
    default CompletableFuture<HttpResult<T>> put(@Nullable T requestBody) {
        return put(requestBody, Map.of());
    }

    /**
     * Sends PATCH request with body of type T (async).
     * Requires adapter to have a request converter configured for type T.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with updated resource or error
     * @throws IllegalStateException if no request converter configured for type T
     */
    CompletableFuture<HttpResult<T>> patch(@Nullable T requestBody, Map<String, String> additionalHeaders);

    /**
     * Sends PATCH request with body of type T (async).
     * Requires adapter to have a request converter configured for type T.
     *
     * @param requestBody Request body content, may be null
     * @return CompletableFuture containing result with updated resource or error
     * @throws IllegalStateException if no request converter configured for type T
     */
    default CompletableFuture<HttpResult<T>> patch(@Nullable T requestBody) {
        return patch(requestBody, Map.of());
    }

    /**
     * Sends DELETE request with body of type T (async).
     * Requires adapter to have a request converter configured for type T.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with response or error
     * @throws IllegalStateException if no request converter configured for type T
     */
    CompletableFuture<HttpResult<T>> delete(@Nullable T requestBody, Map<String, String> additionalHeaders);

    /**
     * Sends DELETE request with body of type T (async).
     * Requires adapter to have a request converter configured for type T.
     *
     * @param requestBody Request body content, may be null
     * @return CompletableFuture containing result with response or error
     * @throws IllegalStateException if no request converter configured for type T
     */
    default CompletableFuture<HttpResult<T>> delete(@Nullable T requestBody) {
        return delete(requestBody, Map.of());
    }

    // ========== BODY METHODS (R → T, explicit request converter) ==========

    /**
     * Sends POST request with explicit request converter for different type (async).
     * Use when request type differs from response type.
     *
     * @param <R> Request body type
     * @param requestConverter Converter for request body serialization
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with created resource (type T) or error
     */
    <R> CompletableFuture<HttpResult<T>> post(HttpRequestConverter<R> requestConverter,
                                               @Nullable R requestBody,
                                               Map<String, String> additionalHeaders);

    /**
     * Sends POST request with explicit request converter for different type (async).
     * Use when request type differs from response type.
     *
     * @param <R> Request body type
     * @param requestConverter Converter for request body serialization
     * @param requestBody Request body content, may be null
     * @return CompletableFuture containing result with created resource (type T) or error
     */
    default <R> CompletableFuture<HttpResult<T>> post(HttpRequestConverter<R> requestConverter,
                                                       @Nullable R requestBody) {
        return post(requestConverter, requestBody, Map.of());
    }

    /**
     * Sends PUT request with explicit request converter for different type (async).
     *
     * @param <R> Request body type
     * @param requestConverter Converter for request body serialization
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with updated resource (type T) or error
     */
    <R> CompletableFuture<HttpResult<T>> put(HttpRequestConverter<R> requestConverter,
                                              @Nullable R requestBody,
                                              Map<String, String> additionalHeaders);

    /**
     * Sends PUT request with explicit request converter for different type (async).
     *
     * @param <R> Request body type
     * @param requestConverter Converter for request body serialization
     * @param requestBody Request body content, may be null
     * @return CompletableFuture containing result with updated resource (type T) or error
     */
    default <R> CompletableFuture<HttpResult<T>> put(HttpRequestConverter<R> requestConverter,
                                                      @Nullable R requestBody) {
        return put(requestConverter, requestBody, Map.of());
    }

    /**
     * Sends PATCH request with explicit request converter for different type (async).
     *
     * @param <R> Request body type
     * @param requestConverter Converter for request body serialization
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with updated resource (type T) or error
     */
    <R> CompletableFuture<HttpResult<T>> patch(HttpRequestConverter<R> requestConverter,
                                                @Nullable R requestBody,
                                                Map<String, String> additionalHeaders);

    /**
     * Sends PATCH request with explicit request converter for different type (async).
     *
     * @param <R> Request body type
     * @param requestConverter Converter for request body serialization
     * @param requestBody Request body content, may be null
     * @return CompletableFuture containing result with updated resource (type T) or error
     */
    default <R> CompletableFuture<HttpResult<T>> patch(HttpRequestConverter<R> requestConverter,
                                                        @Nullable R requestBody) {
        return patch(requestConverter, requestBody, Map.of());
    }

    /**
     * Sends DELETE request with explicit request converter for different type (async).
     *
     * @param <R> Request body type
     * @param requestConverter Converter for request body serialization
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return CompletableFuture containing result with response or error
     */
    <R> CompletableFuture<HttpResult<T>> delete(HttpRequestConverter<R> requestConverter,
                                                 @Nullable R requestBody,
                                                 Map<String, String> additionalHeaders);

    /**
     * Sends DELETE request with explicit request converter for different type (async).
     *
     * @param <R> Request body type
     * @param requestConverter Converter for request body serialization
     * @param requestBody Request body content, may be null
     * @return CompletableFuture containing result with response or error
     */
    default <R> CompletableFuture<HttpResult<T>> delete(HttpRequestConverter<R> requestConverter,
                                                         @Nullable R requestBody) {
        return delete(requestConverter, requestBody, Map.of());
    }

    // ========== BLOCKING CONVENIENCE METHODS ==========

    /**
     * Blocking convenience method for GET.
     * Equivalent to {@code get().join()}.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing response or error information
     */
    default HttpResult<T> getBlocking(Map<String, String> additionalHeaders) {
        return get(additionalHeaders).join();
    }

    /**
     * Blocking convenience method for GET.
     * Equivalent to {@code get().join()}.
     *
     * @return Result containing response or error information
     */
    default HttpResult<T> getBlocking() {
        return get().join();
    }

    /**
     * Blocking convenience method for POST.
     * Equivalent to {@code post(requestBody).join()}.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing created resource or error
     */
    default HttpResult<T> postBlocking(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return post(requestBody, additionalHeaders).join();
    }

    /**
     * Blocking convenience method for POST.
     * Equivalent to {@code post(requestBody).join()}.
     *
     * @param requestBody Request body content, may be null
     * @return Result containing created resource or error
     */
    default HttpResult<T> postBlocking(@Nullable T requestBody) {
        return post(requestBody).join();
    }

    /**
     * Blocking convenience method for PUT.
     * Equivalent to {@code put(requestBody).join()}.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing updated resource or error
     */
    default HttpResult<T> putBlocking(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return put(requestBody, additionalHeaders).join();
    }

    /**
     * Blocking convenience method for PUT.
     * Equivalent to {@code put(requestBody).join()}.
     *
     * @param requestBody Request body content, may be null
     * @return Result containing updated resource or error
     */
    default HttpResult<T> putBlocking(@Nullable T requestBody) {
        return put(requestBody).join();
    }

    /**
     * Blocking convenience method for PATCH.
     * Equivalent to {@code patch(requestBody).join()}.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing updated resource or error
     */
    default HttpResult<T> patchBlocking(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return patch(requestBody, additionalHeaders).join();
    }

    /**
     * Blocking convenience method for PATCH.
     * Equivalent to {@code patch(requestBody).join()}.
     *
     * @param requestBody Request body content, may be null
     * @return Result containing updated resource or error
     */
    default HttpResult<T> patchBlocking(@Nullable T requestBody) {
        return patch(requestBody).join();
    }

    /**
     * Blocking convenience method for DELETE.
     * Equivalent to {@code delete().join()}.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing response or error information
     */
    default HttpResult<T> deleteBlocking(Map<String, String> additionalHeaders) {
        return delete(additionalHeaders).join();
    }

    /**
     * Blocking convenience method for DELETE.
     * Equivalent to {@code delete().join()}.
     *
     * @return Result containing response or error information
     */
    default HttpResult<T> deleteBlocking() {
        return delete().join();
    }

    /**
     * Blocking convenience method for HEAD.
     * Equivalent to {@code head().join()}.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing response metadata
     */
    default HttpResult<T> headBlocking(Map<String, String> additionalHeaders) {
        return head(additionalHeaders).join();
    }

    /**
     * Blocking convenience method for HEAD.
     * Equivalent to {@code head().join()}.
     *
     * @return Result containing response metadata
     */
    default HttpResult<T> headBlocking() {
        return head().join();
    }

    /**
     * Blocking convenience method for OPTIONS.
     * Equivalent to {@code options().join()}.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing server capabilities
     */
    default HttpResult<T> optionsBlocking(Map<String, String> additionalHeaders) {
        return options(additionalHeaders).join();
    }

    /**
     * Blocking convenience method for OPTIONS.
     * Equivalent to {@code options().join()}.
     *
     * @return Result containing server capabilities
     */
    default HttpResult<T> optionsBlocking() {
        return options().join();
    }
}

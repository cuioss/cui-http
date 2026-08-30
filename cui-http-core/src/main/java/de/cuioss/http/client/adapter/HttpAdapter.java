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

import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.result.HttpResult;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Adapter for sending HTTP requests and receiving structured results.
 * Provides method-specific operations following HTTP semantics.
 *
 * <p><b>Async-First Design:</b> All methods return {@code CompletableFuture<HttpResult<T>>}
 * for non-blocking operation. For synchronous usage prefer the {@code *Blocking()} convenience
 * methods, which are interrupt-aware — see <a href="#blocking">Blocking and interruption</a>.
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
 * {@code .thenApply()}, {@code .exceptionally()}, etc.). Do not await a returned future by hand
 * unless you specifically need blocking behavior — when you do, use the {@code *Blocking()}
 * convenience methods below rather than an ad-hoc wait, so interruption is handled correctly.
 *
 * <h3 id="blocking">Blocking and interruption</h3>
 *
 * <p>Every {@code *Blocking()} method waits on the underlying future in an interrupt-aware way:
 *
 * <ul>
 *   <li>If the request fails, the failure is reported as a {@link CompletionException} wrapping the
 *       original cause — the same shape a caller awaiting the future by hand would observe.</li>
 *   <li>If the <em>calling</em> thread is interrupted while waiting, the thread's interrupt flag is
 *       restored, the in-flight request is cancelled, and a {@link CompletionException} wrapping the
 *       {@link InterruptedException} is thrown. Interruption is therefore neither swallowed nor left
 *       to leak an orphaned request — which is precisely what a bare wait would do.</li>
 * </ul>
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
     * Awaits {@code future} on the calling thread, preserving interrupt semantics.
     * <p>
     * This is the single wait primitive behind every {@code *Blocking()} method. It exists because
     * {@link CompletableFuture#join()} — the obvious implementation — is <em>not</em> interruptible:
     * it neither observes nor restores the calling thread's interrupt status, and it leaves the
     * in-flight request running after the caller has given up on it. This method uses
     * {@link CompletableFuture#get()} instead and, on {@link InterruptedException}, restores the
     * interrupt flag and cancels {@code future} before reporting the failure.
     * </p>
     * <p>
     * Failures are reported as {@link CompletionException}, matching {@code join()}'s contract so
     * the observable exception shape is unchanged: an execution failure surfaces the original cause,
     * and an interruption surfaces the {@link InterruptedException}.
     * </p>
     *
     * @param <T>    Response body type
     * @param future the future to await; cancelled if the calling thread is interrupted
     * @return the completed {@link HttpResult}
     * @throws CompletionException if the future completed exceptionally, or if the calling thread was
     *                             interrupted while waiting
     */
    private static <T> HttpResult<T> await(CompletableFuture<HttpResult<T>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new CompletionException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new CompletionException(cause != null ? cause : e);
        }
    }

    /**
     * Blocking convenience method for GET.
     * Blocks until the GET completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing response or error information
     */
    default HttpResult<T> getBlocking(Map<String, String> additionalHeaders) {
        return await(get(additionalHeaders));
    }

    /**
     * Blocking convenience method for GET.
     * Blocks until the GET completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @return Result containing response or error information
     */
    default HttpResult<T> getBlocking() {
        return await(get());
    }

    /**
     * Blocking convenience method for POST.
     * Blocks until the POST completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing created resource or error
     */
    default HttpResult<T> postBlocking(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return await(post(requestBody, additionalHeaders));
    }

    /**
     * Blocking convenience method for POST.
     * Blocks until the POST completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param requestBody Request body content, may be null
     * @return Result containing created resource or error
     */
    default HttpResult<T> postBlocking(@Nullable T requestBody) {
        return await(post(requestBody));
    }

    /**
     * Blocking convenience method for PUT.
     * Blocks until the PUT completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing updated resource or error
     */
    default HttpResult<T> putBlocking(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return await(put(requestBody, additionalHeaders));
    }

    /**
     * Blocking convenience method for PUT.
     * Blocks until the PUT completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param requestBody Request body content, may be null
     * @return Result containing updated resource or error
     */
    default HttpResult<T> putBlocking(@Nullable T requestBody) {
        return await(put(requestBody));
    }

    /**
     * Blocking convenience method for PATCH.
     * Blocks until the PATCH completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param requestBody Request body content, may be null
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing updated resource or error
     */
    default HttpResult<T> patchBlocking(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return await(patch(requestBody, additionalHeaders));
    }

    /**
     * Blocking convenience method for PATCH.
     * Blocks until the PATCH completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param requestBody Request body content, may be null
     * @return Result containing updated resource or error
     */
    default HttpResult<T> patchBlocking(@Nullable T requestBody) {
        return await(patch(requestBody));
    }

    /**
     * Blocking convenience method for DELETE.
     * Blocks until the DELETE completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing response or error information
     */
    default HttpResult<T> deleteBlocking(Map<String, String> additionalHeaders) {
        return await(delete(additionalHeaders));
    }

    /**
     * Blocking convenience method for DELETE.
     * Blocks until the DELETE completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @return Result containing response or error information
     */
    default HttpResult<T> deleteBlocking() {
        return await(delete());
    }

    /**
     * Blocking convenience method for HEAD.
     * Blocks until the HEAD completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing response metadata
     */
    default HttpResult<T> headBlocking(Map<String, String> additionalHeaders) {
        return await(head(additionalHeaders));
    }

    /**
     * Blocking convenience method for HEAD.
     * Blocks until the HEAD completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @return Result containing response metadata
     */
    default HttpResult<T> headBlocking() {
        return await(head());
    }

    /**
     * Blocking convenience method for OPTIONS.
     * Blocks until the OPTIONS completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @param additionalHeaders Additional HTTP headers
     * @return Result containing server capabilities
     */
    default HttpResult<T> optionsBlocking(Map<String, String> additionalHeaders) {
        return await(options(additionalHeaders));
    }

    /**
     * Blocking convenience method for OPTIONS.
     * Blocks until the OPTIONS completes; see <a href="#blocking">Blocking and interruption</a>.
     *
     * @return Result containing server capabilities
     */
    default HttpResult<T> optionsBlocking() {
        return await(options());
    }
}

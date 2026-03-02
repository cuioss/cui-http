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
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static de.cuioss.http.client.HttpLogMessages.WARN;
import static java.util.Objects.requireNonNull;

/**
 * Wraps any HttpAdapter to add retry support with exponential backoff.
 * Retries transient failures (NETWORK_ERROR, SERVER_ERROR) up to configured attempts.
 * All operations are non-blocking using CompletableFuture.
 *
 * <h2>Async Retry Pattern</h2>
 * <p>
 * This implementation uses non-blocking delays via {@link CompletableFuture#delayedExecutor}
 * and tail recursion via {@code .thenCompose()} to avoid thread blocking and stack overflow.
 * Each retry attempt is scheduled after a delay, but no threads are blocked during the wait.
 *
 * <h2>Idempotency Safety</h2>
 * <p>
 * By default, only idempotent methods (GET, PUT, DELETE, HEAD, OPTIONS) are retried.
 * POST and PATCH are skipped unless {@code idempotentOnly=false} is explicitly configured.
 * This prevents duplicate resource creation or repeated non-idempotent operations.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Retry with Defaults</h3>
 * <pre>{@code
 * // Wrap any adapter with retry
 * HttpAdapter<User> resilient = ResilientHttpAdapter.wrap(baseAdapter);
 *
 * // GET automatically retried on network/server errors
 * CompletableFuture<HttpResult<User>> future = resilient.get();
 *
 * // POST NOT retried by default (non-idempotent)
 * CompletableFuture<HttpResult<User>> created = resilient.post(newUser);
 * }</pre>
 *
 * <h3>Custom Retry Configuration</h3>
 * <pre>{@code
 * RetryConfig config = RetryConfig.builder()
 *     .maxAttempts(3)
 *     .initialDelay(Duration.ofSeconds(2))
 *     .multiplier(3.0)
 *     .maxDelay(Duration.ofMinutes(2))
 *     .jitter(0.2)
 *     .idempotentOnly(true)
 *     .build();
 *
 * HttpAdapter<Order> resilient = ResilientHttpAdapter.wrap(baseAdapter, config);
 * }</pre>
 *
 * <h3>Composition with ETag Caching</h3>
 * <pre>{@code
 * // Base adapter with ETag caching
 * HttpAdapter<User> caching = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .build();
 *
 * // Wrap with retry for resilience
 * HttpAdapter<User> resilient = ResilientHttpAdapter.wrap(caching);
 *
 * // Benefits from both caching (304 Not Modified) and retry on failures
 * HttpResult<User> result = resilient.getBlocking();
 * }</pre>
 *
 * <h3>Unsafe: Retry POST with Idempotency Key</h3>
 * <pre>{@code
 * // ONLY enable POST retry if using idempotency keys
 * RetryConfig unsafeConfig = RetryConfig.builder()
 *     .idempotentOnly(false)  // ⚠️ REQUIRED to retry POST - risk of duplicates!
 *     .build();
 *
 * HttpAdapter<Order> adapter = ResilientHttpAdapter.wrap(baseAdapter, unsafeConfig);
 *
 * String idempotencyKey = UUID.randomUUID().toString();
 * Map<String, String> headers = Map.of("Idempotency-Key", idempotencyKey);
 *
 * // Safe to retry with idempotency key (server deduplicates)
 * HttpResult<Order> order = adapter.post(newOrder, headers).join();
 * }</pre>
 *
 * @param <T> Response body type
 * @author CUI-HTTP Development Team
 * @see RetryConfig
 * @see HttpMethod#isIdempotent()
 * @since 1.0
 */
public class ResilientHttpAdapter<T> implements HttpAdapter<T> {
    private static final CuiLogger LOGGER = new CuiLogger(ResilientHttpAdapter.class);

    private final HttpAdapter<T> delegate;
    private final RetryConfig config;

    /**
     * Constructs a resilient adapter wrapping another adapter.
     *
     * @param delegate the underlying adapter to wrap, must not be null
     * @param config retry configuration, must not be null
     * @throws NullPointerException if delegate or config is null
     */
    public ResilientHttpAdapter(HttpAdapter<T> delegate, RetryConfig config) {
        this.delegate = requireNonNull(delegate, "delegate");
        this.config = requireNonNull(config, "config");
    }

    /**
     * Wrap adapter with retry using default configuration.
     * <p>
     * Default configuration:
     * <ul>
     *   <li>maxAttempts: 5</li>
     *   <li>initialDelay: 1 second</li>
     *   <li>multiplier: 2.0 (exponential backoff)</li>
     *   <li>maxDelay: 1 minute</li>
     *   <li>jitter: 0.1 (10% randomization)</li>
     *   <li>idempotentOnly: true (POST/PATCH NOT retried)</li>
     * </ul>
     *
     * @param <T> response type
     * @param delegate the adapter to wrap
     * @return wrapped adapter with retry support
     */
    public static <T> HttpAdapter<T> wrap(HttpAdapter<T> delegate) {
        return new ResilientHttpAdapter<>(delegate, RetryConfig.defaults());
    }

    /**
     * Wrap adapter with retry using custom configuration.
     *
     * @param <T> response type
     * @param delegate the adapter to wrap
     * @param config retry configuration
     * @return wrapped adapter with retry support
     */
    public static <T> HttpAdapter<T> wrap(HttpAdapter<T> delegate, RetryConfig config) {
        return new ResilientHttpAdapter<>(delegate, config);
    }

    @Override
    public CompletableFuture<HttpResult<T>> get(Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.get(additionalHeaders), HttpMethod.GET, 1);
    }

    @Override
    public CompletableFuture<HttpResult<T>> post(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.post(requestBody, additionalHeaders), HttpMethod.POST, 1);
    }

    @Override
    public CompletableFuture<HttpResult<T>> put(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.put(requestBody, additionalHeaders), HttpMethod.PUT, 1);
    }

    @Override
    public CompletableFuture<HttpResult<T>> patch(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.patch(requestBody, additionalHeaders), HttpMethod.PATCH, 1);
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete(Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.delete(additionalHeaders), HttpMethod.DELETE, 1);
    }

    @Override
    public CompletableFuture<HttpResult<T>> delete(@Nullable T requestBody, Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.delete(requestBody, additionalHeaders), HttpMethod.DELETE, 1);
    }

    @Override
    public CompletableFuture<HttpResult<T>> head(Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.head(additionalHeaders), HttpMethod.HEAD, 1);
    }

    @Override
    public CompletableFuture<HttpResult<T>> options(Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.options(additionalHeaders), HttpMethod.OPTIONS, 1);
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> post(HttpRequestConverter<R> requestConverter,
            @Nullable R requestBody,
            Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.post(requestConverter, requestBody, additionalHeaders), HttpMethod.POST, 1);
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> put(HttpRequestConverter<R> requestConverter,
            @Nullable R requestBody,
            Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.put(requestConverter, requestBody, additionalHeaders), HttpMethod.PUT, 1);
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> patch(HttpRequestConverter<R> requestConverter,
            @Nullable R requestBody,
            Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.patch(requestConverter, requestBody, additionalHeaders), HttpMethod.PATCH, 1);
    }

    @Override
    public <R> CompletableFuture<HttpResult<T>> delete(HttpRequestConverter<R> requestConverter,
            @Nullable R requestBody,
            Map<String, String> additionalHeaders) {
        return executeWithRetry(() -> delegate.delete(requestConverter, requestBody, additionalHeaders), HttpMethod.DELETE, 1);
    }

    /**
     * Executes HTTP operation with retry support using non-blocking delays.
     * The delegate call is already async (returns CompletableFuture), so no
     * additional thread wrapping is needed.
     *
     * <h3>Retry Logic</h3>
     * <ul>
     *   <li>Success: Return immediately</li>
     *   <li>Idempotency check: Skip retry for non-idempotent methods if configured</li>
     *   <li>Non-retryable error: Return immediately (CLIENT_ERROR, INVALID_CONTENT, CONFIGURATION_ERROR)</li>
     *   <li>Max attempts reached: Return failure</li>
     *   <li>Retryable error: Schedule retry after exponential backoff delay</li>
     * </ul>
     *
     * @param operation Supplier that returns CompletableFuture of the HTTP operation
     * @param method HTTP method for logging and idempotency checking
     * @param attempt Current attempt number (1-based)
     * @return CompletableFuture containing the result or recursive retry
     */
    private CompletableFuture<HttpResult<T>> executeWithRetry(
            Supplier<CompletableFuture<HttpResult<T>>> operation,
            HttpMethod method,
            int attempt) {

        LOGGER.debug("Attempt %s/%s for %s request", attempt, config.maxAttempts(), method.methodName());

        // Delegate is already async - no supplyAsync needed!
        return operation.get()
                .thenCompose(result -> {
                    // Success - return immediately
                    if (result.isSuccess()) {
                        if (attempt > 1) {
                            LOGGER.debug("%s request succeeded on attempt %s", method.methodName(), attempt);
                        }
                        return CompletableFuture.completedFuture(result);
                    }

                    // Idempotency check - skip retry for non-idempotent methods if configured
                    if (config.idempotentOnly() && !method.isIdempotent()) {
                        LOGGER.warn(WARN.RETRY_SKIPPED_NON_IDEMPOTENT, method.methodName());
                        return CompletableFuture.completedFuture(result);
                    }

                    // Non-retryable failure - return immediately
                    if (!result.isRetryable()) {
                        LOGGER.debug("%s request failed with non-retryable error: %s",
                                method.methodName(), result.getErrorCategory().orElse(null));
                        return CompletableFuture.completedFuture(result);
                    }

                    // Max attempts reached
                    if (attempt >= config.maxAttempts()) {
                        LOGGER.warn(WARN.REQUEST_FAILED_MAX_ATTEMPTS, method.methodName(), config.maxAttempts());
                        return CompletableFuture.completedFuture(result);
                    }

                    // Retryable failure - calculate delay and schedule retry
                    Duration delay = config.calculateDelay(attempt);

                    LOGGER.warn(WARN.REQUEST_RETRY_AFTER_FAILURE,
                            method.methodName(), attempt, delay.toMillis());

                    int nextAttempt = attempt + 1;

                    // Non-blocking delay using delayedExecutor
                    Executor delayedExecutor = CompletableFuture.delayedExecutor(
                            delay.toMillis(), TimeUnit.MILLISECONDS
                    );

                    // Schedule next attempt after delay - no nested futures
                    return CompletableFuture
                            .supplyAsync(() -> null, delayedExecutor)
                            .thenCompose(ignored -> executeWithRetry(operation, method, nextAttempt));
                });
    }
}

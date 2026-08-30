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

import de.cuioss.http.client.HttpMethod;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
 * This implementation uses non-blocking delays via {@link CompletableFuture#delayedExecutor} and
 * schedules each subsequent attempt from the previous one's completion callback, avoiding both
 * thread blocking and the stack growth of a synchronous retry loop. Each retry attempt is scheduled
 * after a delay, but no threads are blocked during the wait. Attempts drive a single caller-facing
 * controller future rather than a chain of derived stages — see <a href="#cancellation">
 * Cancellation</a> for why that distinction matters.
 *
 * <h2>Idempotency Safety</h2>
 * <p>
 * By default, only idempotent methods (GET, PUT, DELETE, HEAD, OPTIONS) are retried.
 * POST and PATCH are skipped unless {@code idempotentOnly=false} is explicitly configured.
 * This prevents duplicate resource creation or repeated non-idempotent operations.
 *
 * <h2 id="cancellation">Cancellation</h2>
 * <p>
 * The future returned by every operation is cancellable, and cancellation reaches the whole retry
 * chain: the in-flight delegate call is cancelled, a pending backoff delay is abandoned, and no
 * further attempt is scheduled. Cancelling therefore genuinely stops the work rather than merely
 * detaching the caller from a chain that keeps retrying in the background.
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
     *   <li>Non-retryable error: Return immediately (CLIENT_ERROR, INVALID_CONTENT, CONFIGURATION_ERROR).
     *       Checked <em>before</em> the idempotency check so that failures which would never be retried
     *       for any method do not emit the misleading non-idempotent-skip warning.</li>
     *   <li>Idempotency check: Skip retry for non-idempotent methods if configured</li>
     *   <li>Max attempts reached: Return failure</li>
     *   <li>Retryable error: Schedule retry after exponential backoff delay</li>
     * </ul>
     *
     * <p>Both normally-completed failures (an {@code HttpResult} that is not a success) and
     * exceptionally-completed futures from the delegate are routed through this retry path. An
     * exceptionally-completed future is treated as a retryable transient failure (subject to the
     * idempotency and max-attempts checks); if it is not retried, the original exception is
     * re-propagated so callers still observe the failure.
     *
     * <h3>Cancellation</h3>
     * <p>The future returned here is a dedicated <em>controller</em>, not the delegate's future and
     * not a derived stage of it. Cancelling it propagates: the stage currently in flight — either the
     * delegate call or the pending backoff delay — is tracked in an {@link AtomicReference} and
     * cancelled as soon as the controller completes, and no further attempt is scheduled afterwards.
     * Returning a {@code thenCompose} stage instead would leave cancellation inert, because
     * cancelling a derived stage never reaches the upstream request and never stops the retry chain.
     *
     * @param operation Supplier that returns CompletableFuture of the HTTP operation
     * @param method HTTP method for logging and idempotency checking
     * @param attempt Current attempt number (1-based)
     * @return a cancellable controller future carrying the final result
     */
    private CompletableFuture<HttpResult<T>> executeWithRetry(
            Supplier<CompletableFuture<HttpResult<T>>> operation,
            HttpMethod method,
            int attempt) {

        CompletableFuture<HttpResult<T>> controller = new CompletableFuture<>();
        AtomicReference<CompletableFuture<?>> inFlight = new AtomicReference<>();

        // Cancelling (or otherwise completing) the controller tears down whatever stage is running
        // underneath it. On a normal completion the tracked stage is already done, so the cancel is
        // a no-op; on a cancellation it is what actually stops the in-flight request or the pending
        // backoff delay.
        controller.whenComplete((ignoredResult, ignoredThrowable) -> {
            CompletableFuture<?> stage = inFlight.getAndSet(null);
            if (stage != null) {
                stage.cancel(true);
            }
        });

        runAttempt(controller, inFlight, operation, method, attempt);
        return controller;
    }

    /**
     * Runs one attempt against the delegate and routes its outcome into
     * {@link #decideNextStep}.
     *
     * @param controller the caller-facing controller future to complete
     * @param inFlight tracks the stage a controller cancellation must tear down
     * @param operation Supplier that returns CompletableFuture of the HTTP operation
     * @param method HTTP method for logging and idempotency checking
     * @param attempt current attempt number (1-based)
     */
    private void runAttempt(
            CompletableFuture<HttpResult<T>> controller,
            AtomicReference<CompletableFuture<?>> inFlight,
            Supplier<CompletableFuture<HttpResult<T>>> operation,
            HttpMethod method,
            int attempt) {

        if (controller.isDone()) {
            return;
        }

        LOGGER.debug("Attempt %s/%s for %s request", attempt, config.maxAttempts(), method.methodName());

        // Delegate is already async - no supplyAsync needed! whenComplete(...) captures both normal
        // completions and exceptional completions so the latter also go through the retry path.
        CompletableFuture<HttpResult<T>> delegateFuture = operation.get();
        publishInFlight(controller, inFlight, delegateFuture);

        delegateFuture.whenComplete((result, throwable) -> {
            try {
                decideNextStep(controller, inFlight, operation, method, attempt, result, throwable);
            }
            /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
            catch (RuntimeException e) {
                // The stage this callback returns is discarded, so an escaping exception would
                // otherwise leave the controller uncompleted and the caller blocked forever.
                controller.completeExceptionally(e);
            }
        });
    }

    /**
     * Publishes the stage a controller cancellation must tear down, closing the check-then-act
     * window against a concurrent cancellation.
     * <p>
     * The controller may complete between the {@code isDone()} check that admitted this stage and
     * this publication. In that window the controller's cancellation callback has already run and
     * seen a stale (or absent) reference, so it cannot cancel the stage published afterwards. The
     * re-check below is what closes it: whoever observes the completion last performs the cancel.
     *
     * @param controller the controller future whose completion cancels the stage
     * @param inFlight the tracking reference to publish into
     * @param stage the newly started stage
     */
    private static void publishInFlight(CompletableFuture<?> controller,
            AtomicReference<CompletableFuture<?>> inFlight,
            CompletableFuture<?> stage) {
        inFlight.set(stage);
        if (controller.isDone()) {
            stage.cancel(true);
        }
    }

    /**
     * Decides whether to complete the controller with the current outcome or schedule a retry.
     *
     * @param controller the caller-facing controller future to complete
     * @param inFlight tracks the stage a controller cancellation must tear down
     * @param operation the operation supplier for a possible retry
     * @param method HTTP method for logging and idempotency checking
     * @param attempt current attempt number (1-based)
     * @param result the normal completion result, or {@code null} if the future completed exceptionally
     * @param throwable the exceptional completion cause, or {@code null} on normal completion
     */
    private void decideNextStep(
            CompletableFuture<HttpResult<T>> controller,
            AtomicReference<CompletableFuture<?>> inFlight,
            Supplier<CompletableFuture<HttpResult<T>>> operation,
            HttpMethod method,
            int attempt,
            @Nullable HttpResult<T> result,
            @Nullable Throwable throwable) {

        // The caller may have cancelled while this attempt was in flight. Abandon the chain rather
        // than completing or rescheduling behind a controller that is already done.
        if (controller.isDone()) {
            return;
        }

        // Success - complete immediately
        if (throwable == null && result != null && result.isSuccess()) {
            if (attempt > 1) {
                LOGGER.debug("%s request succeeded on attempt %s", method.methodName(), attempt);
            }
            controller.complete(result);
            return;
        }

        // An exceptionally-completed future is retryable only when its root cause classifies as a
        // transient category. HttpErrorCategory.fromException unwraps CompletionException/
        // ExecutionException wrappers and maps IOException to a retryable NETWORK_ERROR while
        // programming/configuration errors (NPE, IllegalArgumentException, ...) map to the
        // non-retryable CONFIGURATION_ERROR. A normal failure is retryable only when its own
        // error category says so.
        boolean retryable = throwable != null
                ? HttpErrorCategory.fromException(throwable).isRetryable()
                : (result != null && result.isRetryable());

        // Non-retryable failure - complete immediately. Checked before the idempotency check (CLI-5)
        // so failures that would never be retried do not emit the non-idempotent-skip warning.
        if (!retryable) {
            LOGGER.debug("%s request failed with non-retryable error: %s",
                    method.methodName(), result != null ? result.getErrorCategory().orElse(null) : null);
            // completeFinalOutcome re-propagates the throwable for an exceptionally-completed future
            // and completes with the failing result for a normal completion; completing with the
            // null result here would swallow a non-retryable exception into a null outcome.
            completeFinalOutcome(controller, result, throwable);
            return;
        }

        // Idempotency check - skip retry for non-idempotent methods if configured
        if (config.idempotentOnly() && !method.isIdempotent()) {
            LOGGER.warn(WARN.RETRY_SKIPPED_NON_IDEMPOTENT, method.methodName());
            completeFinalOutcome(controller, result, throwable);
            return;
        }

        // Max attempts reached
        if (attempt >= config.maxAttempts()) {
            LOGGER.warn(WARN.REQUEST_FAILED_MAX_ATTEMPTS, method.methodName(), config.maxAttempts());
            completeFinalOutcome(controller, result, throwable);
            return;
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

        // The pending delay is itself the in-flight stage: cancelling the controller during backoff
        // completes it exceptionally, so the thenRun below never fires and no further attempt starts.
        CompletableFuture<Void> scheduled = CompletableFuture.runAsync(() -> {
            // Intentionally empty: the delayed executor supplies the backoff, and completing this
            // stage is the signal that the delay has elapsed.
        }, delayedExecutor);
        publishInFlight(controller, inFlight, scheduled);

        scheduled.thenRun(() -> {
            try {
                runAttempt(controller, inFlight, operation, method, nextAttempt);
            }
            /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
            catch (RuntimeException e) {
                // Mirrors the previous thenCompose behaviour: a supplier that throws synchronously
                // on a retry surfaces through the returned future rather than stranding the caller.
                controller.completeExceptionally(e);
            }
        });
    }

    /**
     * Completes the controller with the terminal outcome when no further retry is performed: the
     * failing result for a normal completion, or a re-propagated exception for an
     * exceptionally-completed future.
     */
    private void completeFinalOutcome(CompletableFuture<HttpResult<T>> controller,
            @Nullable HttpResult<T> result, @Nullable Throwable throwable) {
        if (throwable != null) {
            controller.completeExceptionally(throwable);
        } else {
            controller.complete(result);
        }
    }
}

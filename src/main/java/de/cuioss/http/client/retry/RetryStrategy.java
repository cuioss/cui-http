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
package de.cuioss.http.client.retry;

import de.cuioss.http.client.result.HttpResultObject;

import java.util.concurrent.CompletableFuture;

/**
 * HTTP-specific retry strategy interface using virtual threads and asynchronous execution.
 *
 * <h2>Async Design with Virtual Threads</h2>
 * This interface leverages Java 21's virtual threads to provide efficient, non-blocking retry operations:
 *
 * <ul>
 *   <li><strong>Non-blocking delays</strong> - Uses CompletableFuture.delayedExecutor() instead of Thread.sleep()</li>
 *   <li><strong>Virtual thread execution</strong> - Operations run on lightweight virtual threads</li>
 *   <li><strong>Composable operations</strong> - CompletableFuture API enables natural async composition</li>
 *   <li><strong>Resource efficient</strong> - No blocked threads during retry delays</li>
 *   <li><strong>Scalable</strong> - Handles thousands of concurrent retry operations</li>
 * </ul>
 *
 * <h2>Result Pattern Approach</h2>
 * Continues to use the CUI result pattern with enhanced async capabilities:
 *
 * <ul>
 *   <li><strong>No exceptions for flow control</strong> - All error states become result states</li>
 *   <li><strong>Rich error context</strong> - HttpResultObject contains retry metrics, error codes, and details</li>
 *   <li><strong>Forced error handling</strong> - Cannot access result without checking state</li>
 *   <li><strong>Graceful degradation</strong> - Built-in fallback support with default results</li>
 *   <li><strong>State-based flow</strong> - FRESH, CACHED, STALE, RECOVERED, ERROR states</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 * <h3>Blocking Usage (Legacy Compatibility)</h3>
 * <pre>
 * RetryStrategy strategy = RetryStrategies.exponentialBackoff();
 * HttpResultObject&lt;String&gt; result = strategy.execute(operation, context).get();
 *
 * if (!result.isValid()) {
 *     // Handle error cases
 *     useFallbackContent(result.getResult());
 * } else {
 *     processResult(result.getResult());
 * }
 * </pre>
 *
 * <h3>Async Composition (Recommended)</h3>
 * <pre>
 * strategy.execute(operation, context)
 *     .thenCompose(result -> {
 *         if (result.isValid()) {
 *             return processResult(result.getResult());
 *         } else {
 *             return handleError(result);
 *         }
 *     })
 *     .thenAccept(processed -> updateCache(processed))
 *     .exceptionally(ex -> handleException(ex));
 * </pre>
 */
@FunctionalInterface
public interface RetryStrategy {

    /**
     * Executes the given HTTP operation with retry logic using virtual threads and async execution.
     *
     * <p>This method runs operations on virtual threads with non-blocking delays between retry attempts.
     * The implementation uses {@code CompletableFuture.delayedExecutor()} with virtual thread executors
     * to provide efficient, scalable retry operations without blocking threads during delays.</p>
     *
     * @param <T> the type of result returned by the operation
     * @param operation the HTTP operation to retry
     * @param context retry context with operation name and attempt info
     * @return CompletableFuture containing HttpResultObject with result and comprehensive error/retry information
     */
    <T> CompletableFuture<HttpResultObject<T>> execute(HttpOperation<T> operation, RetryContext context);

    /**
     * Creates a no-op retry strategy (single attempt only).
     * Useful for disabling retry in specific scenarios or configurations.
     *
     * @return a retry strategy that executes the operation exactly once using virtual threads
     */
    static RetryStrategy none() {
        return new RetryStrategy() {
            @Override
            public <T> CompletableFuture<HttpResultObject<T>> execute(HttpOperation<T> operation, RetryContext context) {
                // No retry - just execute once on virtual thread and return completed future
                return CompletableFuture.completedFuture(operation.execute());
            }
        };
    }

}
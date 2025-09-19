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

/**
 * HTTP-specific retry strategy interface using the result pattern.
 *
 * <h2>Result Pattern Approach</h2>
 * This interface has evolved from exception-based error handling to the CUI result pattern,
 * providing several key benefits:
 *
 * <ul>
 *   <li><strong>No exceptions for flow control</strong> - All error states become result states</li>
 *   <li><strong>Rich error context</strong> - HttpResultObject contains retry metrics, error codes, and details</li>
 *   <li><strong>Forced error handling</strong> - Cannot access result without checking state</li>
 *   <li><strong>Graceful degradation</strong> - Built-in fallback support with default results</li>
 *   <li><strong>State-based flow</strong> - FRESH, CACHED, STALE, RECOVERED, ERROR states</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <pre>
 * RetryStrategy strategy = RetryStrategy.exponentialBackoff();
 * HttpResultObject&lt;String&gt; result = strategy.execute(operation, context);
 *
 * if (!result.isValid()) {
 *     // Handle error cases
 *     if (result.isRetryable()) {
 *         // Error is retryable, but all attempts were exhausted
 *         scheduleBackgroundRetry();
 *     } else {
 *         // Non-retryable error, use fallback
 *         useFallbackContent(result.getResult());
 *     }
 * } else {
 *     // Success cases - check specific states if needed
 *     if (result.getState() == HttpResultState.RECOVERED) {
 *         logger.info("Operation recovered after {} attempts",
 *             result.getRetryMetrics().getTotalAttempts());
 *     }
 *     processResult(result.getResult());
 * }
 * </pre>
 */
@FunctionalInterface
public interface RetryStrategy {

    /**
     * Executes the given HTTP operation with retry logic using the result pattern.
     *
     * @param <T> the type of result returned by the operation
     * @param operation the HTTP operation to retry
     * @param context retry context with operation name and attempt info
     * @return HttpResultObject containing the result and comprehensive error/retry information
     */
    <T> HttpResultObject<T> execute(HttpOperation<T> operation, RetryContext context);

    /**
     * Creates a no-op retry strategy (single attempt only).
     * Useful for disabling retry in specific scenarios or configurations.
     *
     * @return a retry strategy that executes the operation exactly once
     */
    static RetryStrategy none() {
        return new RetryStrategy() {
            @Override
            public <T> HttpResultObject<T> execute(HttpOperation<T> operation, RetryContext context) {
                // No retry - just execute once and return result
                return operation.execute();
            }
        };
    }

    /**
     * Creates exponential backoff retry strategy with sensible defaults.
     * This is the recommended strategy for most HTTP operations requiring retry.
     *
     * Default configuration:
     * - Maximum attempts: 5
     * - Initial delay: 1 second
     * - Backoff multiplier: 2.0
     * - Maximum delay: 1 minute
     * - Jitter factor: 0.1 (±10% randomization)
     *
     * @return a retry strategy with exponential backoff and jitter
     */
    static RetryStrategy exponentialBackoff() {
        return ExponentialBackoffRetryStrategy.builder().build();
    }
}
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
import de.cuioss.tools.logging.CuiLogger;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import static de.cuioss.http.client.HttpLogMessages.DEBUG;
import static de.cuioss.http.client.HttpLogMessages.INFO;
import static de.cuioss.http.client.HttpLogMessages.WARN;

/**
 * Exponential backoff retry strategy with jitter to prevent thundering herd.
 * <p>
 * Algorithm based on AWS Architecture Blog recommendations:
 * - Base delay starts at initialDelay
 * - Each retry multiplies by backoffMultiplier
 * - Random jitter applied: delay * (1 ± jitterFactor)
 * - Maximum delay capped at maxDelay
 * - Total attempts limited by maxAttempts
 * <p>
 * The strategy includes intelligent exception classification to determine
 * which exceptions should trigger retries versus immediate failure.
 */
public class ExponentialBackoffRetryStrategy implements RetryStrategy {

    private static final CuiLogger LOGGER = new CuiLogger(ExponentialBackoffRetryStrategy.class);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double backoffMultiplier;
    private final Duration maxDelay;
    private final double jitterFactor;
    private final RetryMetrics retryMetrics;

    ExponentialBackoffRetryStrategy(int maxAttempts, Duration initialDelay, double backoffMultiplier,
            Duration maxDelay, double jitterFactor, RetryMetrics retryMetrics) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay");
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelay = Objects.requireNonNull(maxDelay, "maxDelay");
        this.jitterFactor = jitterFactor;
        this.retryMetrics = Objects.requireNonNull(retryMetrics, "retryMetrics");
    }

    @Override
    @SuppressWarnings({"java:S3776", "java:S135"})
    public <T> HttpResultObject<T> execute(HttpOperation<T> operation, RetryContext context) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(context, "context");

        long totalStartTime = System.nanoTime();
        retryMetrics.recordRetryStart(context);

        HttpResultObject<T> lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long attemptStartTime = System.nanoTime();

            /*~~(TODO: DEBUG no LogRecord)~~>*/LOGGER.debug(DEBUG.RETRY_ATTEMPT_STARTING.format(attempt, context.operationName()));

            // Execute operation - no exceptions to catch
            HttpResultObject<T> result = operation.execute();
            lastResult = result;

            Duration attemptDuration = Duration.ofNanos(System.nanoTime() - attemptStartTime);

            if (result.isValid()) {
                // Success - record and return
                retryMetrics.recordRetryAttempt(context, attempt, attemptDuration, true);

                Duration totalDuration = Duration.ofNanos(System.nanoTime() - totalStartTime);
                retryMetrics.recordRetryComplete(context, totalDuration, true, attempt);

                if (attempt > 1) {
                    LOGGER.info(INFO.RETRY_OPERATION_SUCCEEDED_AFTER_ATTEMPTS.format(context.operationName(), attempt, maxAttempts));
                    // This is a recovery after retries - modify state to indicate recovery
                    return result.copyStateAndDetails(result.getResult()); // Keep original result but could modify state if needed
                } else {
                    // First attempt succeeded
                    return result;
                }
            } else {
                // Operation failed - record attempt
                retryMetrics.recordRetryAttempt(context, attempt, attemptDuration, false);

                if (attempt == maxAttempts) {
                    LOGGER.warn(WARN.RETRY_MAX_ATTEMPTS_REACHED.format(context.operationName(), maxAttempts, "Final attempt failed"));
                    break;
                }

                // Check if this error is retryable
                if (!result.isRetryable()) {
                    /*~~(TODO: DEBUG no LogRecord)~~>*/LOGGER.debug(DEBUG.RETRY_NON_RETRYABLE_ERROR.format(context.operationName(), attemptDuration.toMillis()));
                    break;
                }

                /*~~(TODO: DEBUG no LogRecord)~~>*/LOGGER.debug(DEBUG.RETRY_ATTEMPT_FAILED.format(attempt, context.operationName(), attemptDuration.toMillis()));

                Duration delay = calculateDelay(attempt);

                try {
                    delayBeforeRetry(context, delay, attempt + 1);
                } catch (RetryException delayException) {
                    // Delay interrupted - return the actual operation failure (not synthetic result)
                    Duration totalDuration = Duration.ofNanos(System.nanoTime() - totalStartTime);
                    retryMetrics.recordRetryComplete(context, totalDuration, false, attempt);
                    /*~~(TODO: DEBUG no LogRecord)~~>*/LOGGER.debug(DEBUG.RETRY_DELAY_INTERRUPTED.format(context.operationName()));
                    return lastResult; // Return real operation result, not synthetic one
                }
            }
        }

        // All attempts failed or non-retryable error
        Duration totalDuration = Duration.ofNanos(System.nanoTime() - totalStartTime);
        retryMetrics.recordRetryComplete(context, totalDuration, false, maxAttempts);
        LOGGER.warn(WARN.RETRY_OPERATION_FAILED.format(context.operationName(), maxAttempts, totalDuration.toMillis()));

        // Return the last result from the operation (which contains the error details)
        return lastResult;
    }


    /**
     * Performs delay using ScheduledExecutorService instead of Thread.sleep().
     * This allows for proper interruption handling and doesn't block thread pools.
     */
    private void delayBeforeRetry(RetryContext context, Duration plannedDelay, int nextAttemptNumber)
            throws RetryException {

        long delayStartTime = System.nanoTime();

        try {
            // Simple blocking delay - honest about being synchronous
            Thread.sleep(plannedDelay.toMillis());

            Duration actualDelay = Duration.ofNanos(System.nanoTime() - delayStartTime);
            retryMetrics.recordRetryDelay(context, nextAttemptNumber, plannedDelay, actualDelay);

            // Log significant delay deviations
            long delayDifference = Math.abs(actualDelay.toMillis() - plannedDelay.toMillis());
            if (delayDifference > 50) {
                /*~~(TODO: DEBUG no LogRecord)~~>*/LOGGER.debug(DEBUG.RETRY_DELAY_DEVIATION.format(
                        context.operationName(), plannedDelay.toMillis(), actualDelay.toMillis(), delayDifference));
            }

        } catch (InterruptedException e) {
            Duration actualDelay = Duration.ofNanos(System.nanoTime() - delayStartTime);
            retryMetrics.recordRetryDelay(context, nextAttemptNumber, plannedDelay, actualDelay);
            Thread.currentThread().interrupt();
            throw new RetryException("Retry interrupted for " + context.operationName(), e);
        }
    }

    /**
     * Calculates the delay for the given attempt using exponential backoff with jitter.
     *
     * @param attemptNumber the current attempt number (1-based)
     * @return the calculated delay duration
     */
    @SuppressWarnings("java:S2245")
    private Duration calculateDelay(int attemptNumber) {
        // Exponential backoff: initialDelay * (backoffMultiplier ^ (attempt - 1))
        double exponentialDelay = initialDelay.toMillis() * Math.pow(backoffMultiplier, (double) attemptNumber - 1);

        // Apply jitter: delay * (1 ± jitterFactor)
        // Random value between -1.0 and 1.0
        double randomFactor = 2.0 * ThreadLocalRandom.current().nextDouble() - 1.0;
        double jitter = 1.0 + (randomFactor * jitterFactor);
        long delayMs = Math.round(exponentialDelay * jitter);

        // Cap at maximum delay
        return Duration.ofMillis(Math.min(delayMs, maxDelay.toMillis()));
    }


    /**
     * Creates a builder for configuring the exponential backoff strategy.
     *
     * @return a new builder instance with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating ExponentialBackoffRetryStrategy instances with custom configuration.
     */
    public static class Builder {
        private int maxAttempts = 5;
        private Duration initialDelay = Duration.ofSeconds(1);
        private double backoffMultiplier = 2.0;
        private Duration maxDelay = Duration.ofMinutes(1);
        private double jitterFactor = 0.1; // ±10% jitter
        private RetryMetrics retryMetrics = RetryMetrics.noOp();

        /**
         * Sets the maximum number of retry attempts.
         *
         * @param maxAttempts maximum attempts (must be positive)
         * @return this builder
         */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be positive, got: " + maxAttempts);
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the initial delay before the first retry.
         *
         * @param initialDelay initial delay (must not be null or negative)
         * @return this builder
         */
        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay");
            if (initialDelay.isNegative()) {
                throw new IllegalArgumentException("initialDelay cannot be negative");
            }
            return this;
        }

        /**
         * Sets the backoff multiplier for exponential delay increase.
         *
         * @param backoffMultiplier multiplier (must be >= 1.0)
         * @return this builder
         */
        public Builder backoffMultiplier(double backoffMultiplier) {
            if (backoffMultiplier < 1.0) {
                throw new IllegalArgumentException("backoffMultiplier must be >= 1.0, got: " + backoffMultiplier);
            }
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /**
         * Sets the maximum delay between retries.
         *
         * @param maxDelay maximum delay (must not be null or negative)
         * @return this builder
         */
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = Objects.requireNonNull(maxDelay, "maxDelay");
            if (maxDelay.isNegative()) {
                throw new IllegalArgumentException("maxDelay cannot be negative");
            }
            return this;
        }

        /**
         * Sets the jitter factor for randomizing delays.
         *
         * @param jitterFactor jitter factor (0.0 = no jitter, 1.0 = 100% jitter, must be between 0.0 and 1.0)
         * @return this builder
         */
        public Builder jitterFactor(double jitterFactor) {
            if (jitterFactor < 0.0 || jitterFactor > 1.0) {
                throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0, got: " + jitterFactor);
            }
            this.jitterFactor = jitterFactor;
            return this;
        }


        /**
         * Sets the metrics recorder for retry operations.
         *
         * @param retryMetrics metrics recorder (must not be null, use RetryMetrics.noOp() for no metrics)
         * @return this builder
         */
        public Builder retryMetrics(RetryMetrics retryMetrics) {
            this.retryMetrics = Objects.requireNonNull(retryMetrics, "retryMetrics");
            return this;
        }

        /**
         * Builds the ExponentialBackoffRetryStrategy with the configured parameters.
         *
         * @return configured retry strategy
         */
        public ExponentialBackoffRetryStrategy build() {
            return new ExponentialBackoffRetryStrategy(maxAttempts, initialDelay, backoffMultiplier,
                    maxDelay, jitterFactor, retryMetrics);
        }
    }
}
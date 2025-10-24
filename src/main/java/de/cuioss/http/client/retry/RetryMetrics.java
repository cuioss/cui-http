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

import java.time.Duration;

/**
 * Interface for recording retry operation metrics.
 * <p>
 * This interface provides a clean abstraction for metrics recording that can be
 * implemented by different metrics systems (Micrometer, custom metrics, etc.)
 * without creating dependencies in the core retry infrastructure.
 * <p>
 * All methods in this interface should be implemented to be non-blocking and
 * should handle any internal exceptions gracefully to avoid impacting retry logic.
 */
public interface RetryMetrics {

    /**
     * Records the start of a complete retry operation.
     * This should be called once per retry operation, before any attempts.
     *
     * @param context retry context information
     */
    void recordRetryStart(RetryContext context);

    /**
     * Records the completion of a retry operation (success or failure after all attempts).
     *
     * @param context retry context information
     * @param totalDuration total duration including all attempts and delays
     * @param successful whether the retry operation ultimately succeeded
     * @param totalAttempts total number of attempts made
     */
    void recordRetryComplete(RetryContext context, Duration totalDuration, boolean successful, int totalAttempts);

    /**
     * Records a single retry attempt.
     *
     * @param context retry context information
     * @param attemptNumber the attempt number (1-based)
     * @param attemptDuration duration of this specific attempt (excluding delay)
     * @param successful whether this specific attempt succeeded
     */
    void recordRetryAttempt(RetryContext context, int attemptNumber, Duration attemptDuration,
            boolean successful);

    /**
     * Records the actual delay time between retry attempts.
     *
     * @param context retry context information
     * @param attemptNumber the attempt number that will follow this delay
     * @param plannedDelay the calculated delay duration
     * @param actualDelay the actual delay duration (may differ due to interruption)
     */
    void recordRetryDelay(RetryContext context, int attemptNumber, Duration plannedDelay, Duration actualDelay);

    /**
     * Creates a no-op implementation that performs no metrics recording.
     * Useful when metrics are disabled or not available.
     *
     * @return a metrics recorder that does nothing
     */
    static RetryMetrics noOp() {
        return NoOpRetryMetrics.INSTANCE;
    }

    /**
     * No-op implementation for when metrics are disabled.
     */
    @SuppressWarnings("java:S6548")
    final class NoOpRetryMetrics implements RetryMetrics {
        static final NoOpRetryMetrics INSTANCE = new NoOpRetryMetrics();

        private NoOpRetryMetrics() {
        }

        @Override
        public void recordRetryStart(RetryContext context) {
            // No-op
        }

        @Override
        public void recordRetryComplete(RetryContext context, Duration totalDuration, boolean successful, int totalAttempts) {
            // No-op
        }

        @Override
        public void recordRetryAttempt(RetryContext context, int attemptNumber, Duration attemptDuration,
                boolean successful) {
            // No-op
        }

        @Override
        public void recordRetryDelay(RetryContext context, int attemptNumber, Duration plannedDelay, Duration actualDelay) {
            // No-op
        }
    }
}
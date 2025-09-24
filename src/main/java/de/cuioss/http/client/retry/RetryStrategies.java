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

/**
 * Utility class providing factory methods for creating retry strategies.
 * <p>
 * This class breaks circular dependencies by providing static factory methods
 * for retry strategy implementations without requiring the interface to depend
 * on concrete implementations.
 * </p>
 */
public final class RetryStrategies {

    private RetryStrategies() {
        // Utility class
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
    public static RetryStrategy exponentialBackoff() {
        return ExponentialBackoffRetryStrategy.builder().build();
    }
}
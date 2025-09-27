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

import java.io.Serializable;

/**
 * Context information for retry operations.
 *
 * Provides immutable context data that can be used by retry strategies
 * to make decisions about retry behavior, logging, and metrics.
 *
 * @param operationName descriptive name of the operation being retried
 * @param attemptNumber current attempt number (1-based)
 */
public record RetryContext(String operationName, int attemptNumber) implements Serializable {

    /**
     * Creates a new retry context for the first attempt.
     *
     * @param operationName descriptive name of the operation
     * @return retry context with attempt number 1
     */
    public static RetryContext initial(String operationName) {
        return new RetryContext(operationName, 1);
    }

    /**
     * Creates a new retry context for the next attempt.
     *
     * @return retry context with incremented attempt number
     */
    public RetryContext nextAttempt() {
        return new RetryContext(operationName, attemptNumber + 1);
    }

    /**
     * Checks if this is the first attempt.
     *
     * @return true if this is attempt number 1
     */
    public boolean isFirstAttempt() {
        return attemptNumber == 1;
    }
}
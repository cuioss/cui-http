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

import java.io.Serial;

/**
 * Exception thrown when all retry attempts for an operation have failed.
 *
 * This exception wraps the original exception that caused the final failure
 * and provides context about the retry operation that was attempted.
 */
public class RetryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new RetryException with the specified message.
     *
     * @param message the detail message
     */
    public RetryException(String message) {
        super(message);
    }

    /**
     * Creates a new RetryException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the retry failure (typically the last exception encountered)
     */
    public RetryException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new RetryException with the specified cause.
     *
     * @param cause the cause of the retry failure
     */
    public RetryException(Throwable cause) {
        super(cause);
    }
}
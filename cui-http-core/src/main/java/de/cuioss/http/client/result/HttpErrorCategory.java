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
package de.cuioss.http.client.result;

/**
 * Error categories for HTTP operations, used to classify failures for retry decisions.
 * Each category indicates whether the error is transient (retryable) or permanent.
 *
 * <h2>Usage Pattern</h2>
 * <pre>
 * HttpResult&lt;String&gt; result = httpHandler.load();
 * if (!result.isSuccess()) {
 *     result.getErrorCategory().ifPresent(category -> {
 *         if (category.isRetryable()) {
 *             scheduleRetry();
 *         } else {
 *             useFallback();
 *         }
 *     });
 * }
 * </pre>
 *
 * @author Oliver Wolff
 * @see HttpResult
 * @since 1.0
 */
public enum HttpErrorCategory {

    // === Network Errors (Retryable) ===

    /**
     * Network connectivity problems: timeouts, connection failures, DNS resolution failures.
     * Transient errors that may resolve with retry.
     */
    NETWORK_ERROR,

    /**
     * Server-side errors (HTTP 5xx responses).
     * Indicates remote server problems that may be transient.
     */
    SERVER_ERROR,

    /**
     * Client-side errors (HTTP 4xx responses).
     * Indicates request problems requiring configuration or input changes.
     */
    CLIENT_ERROR,

    // === Content Errors (Non-retryable) ===

    /**
     * Invalid or unparseable response content.
     * Includes empty responses, malformed JSON, invalid data structures.
     */
    INVALID_CONTENT,

    /**
     * Configuration or setup errors.
     * Includes invalid URLs, missing settings, SSL configuration issues, authentication failures.
     */
    CONFIGURATION_ERROR;

    /**
     * Returns whether this error category represents a transient condition worth retrying.
     * Only {@link #NETWORK_ERROR} and {@link #SERVER_ERROR} are retryable.
     *
     * @return true if error is retryable, false otherwise
     */
    public boolean isRetryable() {
        return this == NETWORK_ERROR || this == SERVER_ERROR;
    }

}
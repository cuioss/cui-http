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
 * Essential HTTP error codes for resilient operations.
 * Provides minimal but sufficient error classification focused on actionable distinctions
 * needed for retry logic, monitoring, and error handling.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>Only includes error codes that require different handling</li>
 *   <li>Focuses on operational concerns rather than detailed HTTP semantics</li>
 *   <li>Enables effective retry and fallback strategies</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <pre>
 * HttpResultObject&lt;String&gt; result = httpHandler.load();
 * if (!result.isValid()) {
 *     result.getErrorCode().ifPresent(code -> {
 *         if (code.isRetryable()) {
 *             scheduleRetry();
 *         } else {
 *             useFallback();
 *         }
 *     });
 * }
 * </pre>
 *
 * @author Implementation for HTTP operations
 * @see HttpResultObject
 * @see de.cuioss.uimodel.result.ResultDetail
 * @since 1.0
 */
public enum HttpErrorCategory {

    // === Network Errors (Retryable) ===

    /**
     * Network connectivity problems - timeouts, connection failures, etc.
     * Covers all transient network issues that may resolve with retry.
     */
    NETWORK_ERROR,

    /**
     * Server-side errors (HTTP 5xx).
     * Remote server problems that may be transient.
     */
    SERVER_ERROR,

    /**
     * Client-side errors (HTTP 4xx).
     * Request problems that typically require configuration changes.
     */
    CLIENT_ERROR,

    // === Content Errors (Non-retryable) ===

    /**
     * Response content is invalid or unparseable.
     * Covers all content validation failures including empty responses,
     * malformed JSON, invalid JWKS, invalid well-known configs, etc.
     */
    INVALID_CONTENT,

    /**
     * Configuration or setup errors.
     * Invalid URLs, missing settings, SSL issues, authentication problems.
     * Typically requires human intervention to resolve.
     */
    CONFIGURATION_ERROR;

    /**
     * Determines if this error code represents a retryable condition.
     * Only network and server errors are typically transient and worth retrying.
     *
     * @return true if the error condition is typically retryable
     */
    public boolean isRetryable() {
        return this == NETWORK_ERROR || this == SERVER_ERROR;
    }

}
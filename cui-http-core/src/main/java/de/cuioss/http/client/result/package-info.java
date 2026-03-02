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
/**
 * Result patterns for HTTP operations with ETag caching support.
 *
 * <h2>Core Types</h2>
 * <ul>
 *   <li>{@link de.cuioss.http.client.result.HttpResult} - Sealed interface for HTTP operation results</li>
 *   <li>{@link de.cuioss.http.client.result.HttpErrorCategory} - Error classification for retry decisions</li>
 *   <li>{@link de.cuioss.http.client.result.HttpResultState} - State constants for HTTP result tracking</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>1. Basic HTTP Operations</h3>
 * <pre>
 * // HTTP operation with result pattern
 * HttpResult&lt;String&gt; result = httpClient.get("https://api.example.com/data");
 *
 * if (result.isSuccess()) {
 *     result.getContent().ifPresent(this::processContent);
 * } else {
 *     result.getErrorMessage().ifPresent(logger::error);
 * }
 * </pre>
 *
 * <h3>2. ETag-Aware Caching</h3>
 * <pre>
 * // ETag-aware HTTP loading
 * HttpResult&lt;JwksKeys&gt; result = jwksLoader.loadWithETag(previousETag);
 *
 * if (result.isSuccess()) {
 *     // Process successful result
 *     result.getContent().ifPresent(keys ->
 *         updateCache(keys, result.getETag().orElse("")));
 *
 *     // Check HTTP status for caching behavior
 *     if (result.getHttpStatus().orElse(0) == 304) {
 *         logger.debug("JWKS content unchanged, using cache");
 *     } else {
 *         logger.debug("JWKS content updated");
 *     }
 * } else {
 *     // Handle error case with fallback
 *     result.getErrorMessage().ifPresent(msg ->
 *         logger.warn("JWKS loading failed: {}", msg));
 * }
 * </pre>
 *
 * <h3>3. Error Handling with Retry Logic</h3>
 * <pre>
 * // HTTP operation with error handling
 * HttpResult&lt;Config&gt; result = httpHandler.loadConfig();
 *
 * if (!result.isSuccess()) {
 *     // Check if error is retryable
 *     if (result.isRetryable()) {
 *         logger.info("Retryable error, scheduling retry");
 *         scheduleRetry();
 *     } else {
 *         // Handle non-retryable error
 *         result.getErrorCategory().ifPresent(category -> {
 *             if (category == HttpErrorCategory.INVALID_CONTENT) {
 *                 logger.error("Invalid content received");
 *             }
 *         });
 *     }
 * }
 * </pre>
 *
 * <h3>4. Factory Methods for Common Scenarios</h3>
 * <pre>
 * // Successful HTTP response
 * HttpResult&lt;Document&gt; result = HttpResult.success(document, etag, 200);
 *
 * // Error with fallback content
 * HttpResult&lt;Document&gt; errorResult = HttpResult.failureWithFallback(
 *     "Connection failed",
 *     null,
 *     fallbackDocument,
 *     HttpErrorCategory.NETWORK_ERROR,
 *     cachedEtag,
 *     null
 * );
 * </pre>
 *
 * <h2>Pattern Comparison</h2>
 *
 * <h3>HttpResult</h3>
 * <ul>
 *   <li>Sealed interface with Success/Failure records</li>
 *   <li>Type-safe pattern matching</li>
 *   <li>Immutable records</li>
 *   <li>Optional-based content access</li>
 *   <li>No external dependencies</li>
 *   <li>Simple string-based error messages</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @see de.cuioss.http.client.result.HttpResult
 * @see de.cuioss.http.client.result.HttpErrorCategory
 * @since 1.0
 */
package de.cuioss.http.client.result;
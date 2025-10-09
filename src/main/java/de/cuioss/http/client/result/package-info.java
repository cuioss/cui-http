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
 * HTTP-specific result pattern implementation that extends the CUI result framework
 * with semantics tailored for HTTP operations and ETag caching.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link de.cuioss.http.client.result.HttpResultObject} - HTTP-specialized result wrapper</li>
 *   <li>{@link de.cuioss.http.client.result.HttpResultState} - HTTP-specific state constants</li>
 *   <li>{@link de.cuioss.uimodel.result.ResultDetail} - CUI error details with user messages</li>
 *   <li>{@link de.cuioss.http.client.result.HttpErrorCategory} - HTTP error classifications with retry logic</li>
 * </ul>
 *
 * <h2>Key Benefits</h2>
 * <ul>
 *   <li><strong>Unified API</strong> - Single result type across all HTTP operations</li>
 *   <li><strong>HTTP Semantics</strong> - Built-in support for ETag caching and HTTP status codes</li>
 *   <li><strong>Error Classification</strong> - Standardized error codes for consistent handling</li>
 *   <li><strong>CUI Integration</strong> - Full compatibility with existing CUI result patterns</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>1. Basic HTTP Operations</h3>
 * <pre>
 * // HTTP operation with result pattern
 * HttpResultObject&lt;String&gt; result = httpClient.get("https://api.example.com/data");
 *
 * if (result.isValid()) {
 *     String content = result.getResult();
 *     processContent(content);
 * } else {
 *     handleError(result);
 * }
 * </pre>
 *
 * <h3>2. ETag-Aware Caching</h3>
 * <pre>
 * // ETag-aware HTTP loading
 * HttpResultObject&lt;JwksKeys&gt; result = jwksLoader.loadWithETag(previousETag);
 *
 * if (result.isValid()) {
 *     // Process successful result
 *     updateCache(result.getResult(), result.getETag().orElse(""));
 *
 *     // Check HTTP status for caching behavior
 *     if (result.getHttpStatus().orElse(0) == 304) {
 *         logger.debug("JWKS content unchanged, using cache");
 *     } else {
 *         logger.debug("JWKS content updated");
 *     }
 * } else {
 *     // Handle error case with fallback
 *     result.getResultDetail().ifPresent(detail ->
 *         logger.warn("JWKS loading failed: {}", detail.getDetail().getDisplayName()));
 * }
 * </pre>
 *
 * <h3>3. Error Handling with Retry Logic</h3>
 * <pre>
 * // HTTP operation with error handling
 * HttpResultObject&lt;Config&gt; result = httpHandler.loadConfig();
 *
 * if (!result.isValid()) {
 *     // Check if error is retryable
 *     if (result.isRetryable()) {
 *         logger.info("Retryable error, scheduling retry");
 *         scheduleRetry();
 *     } else {
 *         // Handle non-retryable error
 *         result.getHttpErrorCategory().ifPresent(code -> {
 *             if (code == HttpErrorCategory.INVALID_CONTENT) {
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
 * HttpResultObject&lt;Document&gt; result = HttpResultObject.success(document, etag, 200);
 *
 * // Error with fallback content
 * HttpResultObject&lt;Document&gt; errorResult = HttpResultObject.error(
 *     fallbackDocument,
 *     HttpErrorCategory.NETWORK_ERROR,
 *     new ResultDetail(new DisplayName("Connection failed"))
 * );
 * </pre>
 *
 * <h2>Result States</h2>
 * <p>
 * HttpResultObject uses the standard CUI result states:
 * </p>
 * <ul>
 *   <li><strong>VALID</strong> - HTTP operation succeeded (status 200, 304, etc.)</li>
 *   <li><strong>WARNING</strong> - Degraded state (fallback content, recovery scenarios)</li>
 *   <li><strong>ERROR</strong> - Operation failed (with optional fallback content)</li>
 * </ul>
 *
 * <p>
 * HTTP-specific context is provided through:
 * </p>
 * <ul>
 *   <li>ETag for caching optimization</li>
 *   <li>HTTP status code for protocol semantics</li>
 *   <li>HttpErrorCategory for retry decision making</li>
 * </ul>
 *
 * <h2>Modern Result Pattern</h2>
 * <p>
 * This package provides a modern result pattern using sealed interfaces,
 * adding HTTP-specific semantics with type safety:
 * </p>
 * <ul>
 *   <li>Uses {@link HttpResult} sealed interface for type-safe pattern matching</li>
 *   <li>Success and Failure records for immutable results</li>
 *   <li>Optional pattern for explicit content absence</li>
 *   <li>Plain string error messages without i18n coupling</li>
 *   <li>Thread-safe by design (immutable records)</li>
 * </ul>
 *
 * <h2>Migration from Legacy Patterns</h2>
 * <p>
 * This framework replaces custom result types like {@code LoadResult} and {@code HttpFetchResult}
 * with a unified approach:
 * </p>
 *
 * <h3>Before (Legacy)</h3>
 * <pre>
 * LoadResult result = httpHandler.load();
 * if (result.content() == null) {
 *     // Handle error with exceptions
 *     throw new HttpException("Failed to load");
 * }
 * String content = result.content();
 * </pre>
 *
 * <h3>After (Result Pattern)</h3>
 * <pre>
 * HttpResultObject&lt;String&gt; result = httpHandler.load();
 * if (!result.isValid()) {
 *     // Handle error with structured details
 *     result.getResultDetail().ifPresent(this::logError);
 *     return result.copyStateAndDetails(defaultContent);
 * }
 * String content = result.getResult();
 * </pre>
 *
 * @author Implementation for JWT HTTP operations
 * @see de.cuioss.uimodel.result.ResultObject
 * @since 1.0
 */
package de.cuioss.http.client.result;
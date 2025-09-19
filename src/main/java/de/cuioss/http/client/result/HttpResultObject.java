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

import de.cuioss.uimodel.result.ResultDetail;
import de.cuioss.uimodel.result.ResultObject;
import de.cuioss.uimodel.result.ResultState;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serial;
import java.util.Optional;
import java.util.function.Function;

/**
 * HTTP-specific result object that extends the CUI result pattern with HTTP protocol semantics.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>CUI result pattern integration (VALID/WARNING/ERROR states)</li>
 *   <li>ETag support for efficient caching</li>
 *   <li>HTTP status code tracking</li>
 *   <li>Fluent API for common HTTP operations</li>
 *   <li>Built-in fallback and default result handling</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 *
 * <h3>1. Basic HTTP Result Handling</h3>
 * <pre>
 * HttpResultObject&lt;String&gt; result = httpHandler.load();
 *
 * if (!result.isValid()) {
 *     // Handle error case with retry logic
 *     if (result.isRetryable()) {
 *         scheduleRetry();
 *     } else {
 *         result.getResultDetail().ifPresent(detail ->
 *             logger.error(detail.getDetail().getDisplayName()));
 *         return result.copyStateAndDetails(fallbackContent);
 *     }
 * }
 *
 * // Process successful result
 * String content = result.getResult();
 * String etag = result.getETag().orElse("");
 * processContent(content, etag);
 * </pre>
 *
 * <h3>2. Factory Methods</h3>
 * <pre>
 * // Successful HTTP operations
 * HttpResultObject&lt;String&gt; fresh = HttpResultObject.success(content, etag, 200);
 * HttpResultObject&lt;String&gt; cached = HttpResultObject.success(cachedContent, etag, 304);
 *
 * // Error with fallback content
 * HttpResultObject&lt;String&gt; error = HttpResultObject.error(fallback, errorCode, detail);
 * </pre>
 *
 * <h2>CUI Result Pattern Integration</h2>
 * <ul>
 *   <li><strong>VALID</strong>: HTTP operation succeeded (fresh or cached content)</li>
 *   <li><strong>WARNING</strong>: Degraded state (stale cache, partial recovery)</li>
 *   <li><strong>ERROR</strong>: Operation failed (with optional fallback content)</li>
 * </ul>
 *
 * @param <T> The type of the HTTP response content
 * @author Implementation for JWT HTTP operations
 * @see HttpResultState
 * @see de.cuioss.uimodel.result.ResultDetail
 * @see HttpErrorCategory
 * @since 1.0
 */
@ToString(callSuper = true, doNotUseGetters = true)
@EqualsAndHashCode(callSuper = true, doNotUseGetters = true)
public class HttpResultObject<T> extends ResultObject<T> {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * HTTP ETag from the response for caching optimization.
     */
    private final String etag;

    /**
     * HTTP status code from the response.
     */
    private final Integer httpStatus;

    /**
     * HTTP-specific error classification.
     */
    private final HttpErrorCategory httpErrorCategory;


    /**
     * Comprehensive constructor for HTTP result objects.
     *
     * @param result the wrapped result value
     * @param state the result state (using CUI base types)
     * @param resultDetail result detail
     * @param httpErrorCategory HTTP-specific error code
     * @param etag optional HTTP ETag
     * @param httpStatus optional HTTP status code
     */
    public HttpResultObject(T result, ResultState state, ResultDetail resultDetail,
            HttpErrorCategory httpErrorCategory, String etag, Integer httpStatus) {
        super(result, state, resultDetail, httpErrorCategory);
        this.etag = etag;
        this.httpStatus = httpStatus;
        this.httpErrorCategory = httpErrorCategory;
    }

    /**
     * Copy constructor that transforms the result while preserving HTTP metadata.
     *
     * @param previousResult the previous HTTP result to copy from
     * @param mapper function to transform the result value
     * @param validDefault default value if previous result was invalid
     * @param <R> type of the previous result
     */
    public <R> HttpResultObject(HttpResultObject<R> previousResult, Function<R, T> mapper, T validDefault) {
        super(previousResult, mapper, validDefault);
        this.etag = previousResult.etag;
        this.httpStatus = previousResult.httpStatus;
        this.httpErrorCategory = previousResult.httpErrorCategory;
    }


    // === HTTP Metadata Access ===

    /**
     * Gets the HTTP ETag if present.
     *
     * @return Optional containing ETag, or empty if not available
     */
    public Optional<String> getETag() {
        return Optional.ofNullable(etag);
    }

    /**
     * Gets the HTTP status code if present.
     *
     * @return Optional containing status code, or empty if not available
     */
    public Optional<Integer> getHttpStatus() {
        return Optional.ofNullable(httpStatus);
    }

    /**
     * Gets the HTTP-specific error code if present.
     *
     * @return Optional containing HTTP error code, or empty if not available
     */
    public Optional<HttpErrorCategory> getHttpErrorCategory() {
        return Optional.ofNullable(httpErrorCategory);
    }

    /**
     * Checks if the error condition is retryable.
     * Only meaningful when the result is not valid.
     *
     * @return true if error is retryable, false otherwise
     */
    public boolean isRetryable() {
        return getHttpErrorCategory().map(HttpErrorCategory::isRetryable).orElse(false);
    }


    // === Transformation Methods ===

    /**
     * Transforms this result to a different type while preserving HTTP metadata.
     *
     * @param mapper function to transform the result value
     * @param defaultValue default value if this result is invalid
     * @param <U> target result type
     * @return new HttpResultObject with transformed value
     */
    public <U> HttpResultObject<U> map(Function<T, U> mapper, U defaultValue) {
        return new HttpResultObject<>(this, mapper, defaultValue);
    }

    /**
     * Creates a new result object copying state and details from this one.
     * Useful for error propagation without changing the result type.
     *
     * @param newResult the new result value
     * @param <U> new result type
     * @return new HttpResultObject with copied state and details
     */
    public <U> HttpResultObject<U> copyStateAndDetails(U newResult) {
        return new HttpResultObject<>(
                newResult,
                getState(),
                getResultDetail().orElse(null),
                httpErrorCategory,
                etag,
                httpStatus
        );
    }


    // === Factory Methods ===

    /**
     * Creates a successful HTTP result.
     *
     * @param result the result content
     * @param etag optional ETag
     * @param httpStatus HTTP status code
     * @param <U> result type
     * @return HttpResultObject in VALID state
     */
    public static <U> HttpResultObject<U> success(U result, String etag, int httpStatus) {
        return new HttpResultObject<>(
                result,
                ResultState.VALID,
                null,
                null,
                etag,
                httpStatus
        );
    }

    /**
     * Creates an error result with optional fallback content.
     *
     * @param fallbackResult optional fallback content
     * @param httpErrorCategory the error classification
     * @param detail error details
     * @param <U> result type
     * @return HttpResultObject in ERROR state
     */
    public static <U> HttpResultObject<U> error(U fallbackResult, HttpErrorCategory httpErrorCategory, ResultDetail detail) {
        return new HttpResultObject<>(
                fallbackResult,
                ResultState.ERROR,
                detail,
                httpErrorCategory,
                null,
                null
        );
    }
}
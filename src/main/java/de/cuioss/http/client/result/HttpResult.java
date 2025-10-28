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

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * Sealed interface representing HTTP operation results as either success or failure.
 * Modern result pattern for HTTP operations with type-safe pattern matching.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Type-safe</strong>: Sealed interface enables exhaustive pattern matching</li>
 *   <li><strong>Explicit absence</strong>: Optional pattern instead of mandatory defaults</li>
 *   <li><strong>Immutable</strong>: Records ensure thread-safe, immutable results</li>
 *   <li><strong>HTTP-aware</strong>: Native support for ETag, status codes, retry logic</li>
 *   <li><strong>Simple errors</strong>: Plain string messages without framework dependencies</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 *
 * <h3>1. Traditional Style (Recommended for Simple Cases)</h3>
 * <pre>
 * HttpResult&lt;Jwks&gt; result = handler.load();
 *
 * // Check success
 * if (result.isSuccess()) {
 *     result.getContent().ifPresent(jwks -&gt; {
 *         processKeys(jwks);
 *         logger.info("Loaded {} keys", jwks.keys().size());
 *     });
 *
 *     result.getETag().ifPresent(etag -&gt;
 *         logger.debug("Content ETag: {}", etag));
 * }
 *
 * // Handle errors
 * if (!result.isSuccess()) {
 *     result.getErrorMessage().ifPresent(logger::error);
 *     result.getCause().ifPresent(ex -&gt;
 *         logger.error("Underlying cause", ex));
 *
 *     if (result.isRetryable()) {
 *         scheduleRetry();
 *     }
 * }
 * </pre>
 *
 * <h3>2. Pattern Matching Style (Recommended for Complex Logic)</h3>
 * <pre>
 * HttpResult&lt;Config&gt; result = handler.load();
 *
 * return switch (result) {
 *     case HttpResult.Success&lt;Config&gt;(var config, var etag, var status) -&gt; {
 *         logger.info("Loaded configuration successfully");
 *         updateCache(config, etag);
 *         yield true; // Success
 *     }
 *
 *     case HttpResult.Failure&lt;Config&gt; failure -&gt; {
 *         logger.error(failure.errorMessage(), failure.cause());
 *
 *         // Graceful degradation with fallback
 *         if (failure.fallbackContent() != null) {
 *             logger.warn("Using cached configuration");
 *             yield true; // Degraded but functional
 *         }
 *
 *         yield failure.isRetryable(); // Retry logic
 *     }
 * };
 * </pre>
 *
 * <h3>3. Error Category Based Handling</h3>
 * <pre>
 * HttpResult&lt;String&gt; result = handler.load();
 *
 * result.getErrorCategory().ifPresent(category -&gt; {
 *     switch (category) {
 *         case NETWORK_ERROR -&gt; {
 *             logger.warn("Network error, will retry");
 *             retryStrategy.scheduleRetry();
 *         }
 *         case SERVER_ERROR -&gt; {
 *             logger.warn("Server error (5xx), will retry");
 *             retryStrategy.scheduleRetry();
 *         }
 *         case CLIENT_ERROR -&gt; {
 *             logger.error("Client error (4xx), check request configuration");
 *             alertOperations("Invalid HTTP request");
 *         }
 *         case INVALID_CONTENT -&gt; {
 *             logger.error("Response content invalid");
 *             useFallbackSource();
 *         }
 *         case CONFIGURATION_ERROR -&gt; {
 *             logger.error("Configuration error, check SSL/URL settings");
 *             alertOperations("HTTP handler misconfigured");
 *         }
 *     }
 * });
 * </pre>
 *
 * <h2>Result States</h2>
 * <ul>
 *   <li><strong>Success with fresh content</strong>: HTTP 200 OK with newly loaded content and ETag</li>
 *   <li><strong>Success with cached content</strong>: HTTP 304 Not Modified, using existing cached content</li>
 *   <li><strong>Failure with fallback</strong>: Operation failed but cached content available for degraded operation</li>
 *   <li><strong>Failure without fallback</strong>: Operation failed with no cached content available</li>
 * </ul>
 *
 * @param <T> the content type
 * @author Oliver Wolff
 * @see HttpErrorCategory
 * @since 1.0
 */
public sealed interface HttpResult<T>
permits HttpResult.Success, HttpResult.Failure {

    // === State Checks ===

    /**
     * Checks if this result represents a successful HTTP operation.
     *
     * @return true if Success, false if Failure
     */
    boolean isSuccess();

    /**
     * Checks if this error is retryable (only meaningful for Failure).
     * Delegates to the error category's retryable status.
     *
     * @return true if the error category is retryable, false otherwise
     */
    default boolean isRetryable() {
        return getErrorCategory()
                .map(HttpErrorCategory::isRetryable)
                .orElse(false);
    }

    // === Content Access ===

    /**
     * Gets the content if available.
     * <ul>
     *   <li>For Success: always present (contains the result)</li>
     *   <li>For Failure: present only if fallback content exists</li>
     * </ul>
     *
     * @return Optional containing content, or empty if not available
     */
    Optional<T> getContent();

    /**
     * Gets the HTTP ETag header value if present.
     * Used for HTTP caching with conditional requests (If-None-Match).
     *
     * @return Optional containing ETag, or empty if not available
     */
    Optional<String> getETag();

    /**
     * Gets the HTTP status code if present.
     *
     * @return Optional containing status code, or empty if not available
     */
    Optional<Integer> getHttpStatus();

    // === Error Information ===

    /**
     * Gets the error message if this is a failure.
     * For Success results, returns empty Optional.
     *
     * @return Optional containing human-readable error message
     */
    default Optional<String> getErrorMessage() {
        return Optional.empty();
    }

    /**
     * Gets the underlying exception cause if this is a failure.
     * For Success results, returns empty Optional.
     *
     * @return Optional containing the exception that caused the failure
     */
    default Optional<Throwable> getCause() {
        return Optional.empty();
    }

    /**
     * Gets the HTTP error category if this is a failure.
     * For Success results, returns empty Optional.
     *
     * @return Optional containing error category for retry/handling decisions
     */
    Optional<HttpErrorCategory> getErrorCategory();

    // === Transformations ===

    /**
     * Transforms content to a different type while preserving metadata.
     * Applies mapper to success content or fallback content if present.
     * Metadata (ETag, status, error info) is preserved.
     *
     * @param mapper function to transform content
     * @param <U> target type
     * @return new HttpResult with transformed content
     */
    <U> HttpResult<U> map(Function<T, U> mapper);

    // === Factory Methods ===

    /**
     * Creates a successful result with content and HTTP metadata.
     *
     * <h3>Usage Example</h3>
     * <pre>
     * // Fresh content from HTTP 200 OK
     * return HttpResult.success(parsedContent, etag, 200);
     *
     * // Cached content from HTTP 304 Not Modified
     * return HttpResult.success(cachedContent, cachedEtag, 304);
     * </pre>
     *
     * @param content the response content, must not be null
     * @param etag optional ETag header value for caching
     * @param httpStatus HTTP status code
     * @param <T> content type
     * @return Success result
     */
    static <T> HttpResult<T> success(T content, @Nullable String etag, int httpStatus) {
        return new Success<>(content, etag, httpStatus);
    }

    /**
     * Creates a failure result without fallback content.
     *
     * <h3>Usage Example</h3>
     * <pre>
     * return HttpResult.failure(
     *     "Network timeout connecting to " + url,
     *     timeoutException,
     *     HttpErrorCategory.NETWORK_ERROR
     * );
     * </pre>
     *
     * @param errorMessage human-readable error description
     * @param cause optional underlying exception
     * @param category error classification for retry decisions
     * @param <T> content type
     * @return Failure result with no fallback
     */
    static <T> HttpResult<T> failure(
            String errorMessage,
            @Nullable Throwable cause,
            HttpErrorCategory category) {
        return new Failure<>(errorMessage, cause, null, category, null, null);
    }

    /**
     * Creates a failure result with fallback content.
     * Used when an error occurs but cached/fallback content is available for graceful degradation.
     *
     * <h3>Usage Example</h3>
     * <pre>
     * return HttpResult.failureWithFallback(
     *     "Server error 503, using cached JWKS",
     *     null,
     *     cachedJwks,
     *     HttpErrorCategory.SERVER_ERROR,
     *     cachedEtag,
     *     503
     * );
     * </pre>
     *
     * @param errorMessage human-readable error description
     * @param cause optional underlying exception
     * @param fallbackContent optional cached/fallback content
     * @param category error classification for retry decisions
     * @param cachedEtag optional ETag from cached content
     * @param httpStatus optional HTTP status code
     * @param <T> content type
     * @return Failure result with fallback
     */
    static <T> HttpResult<T> failureWithFallback(
            String errorMessage,
            @Nullable Throwable cause,
            @Nullable T fallbackContent,
            HttpErrorCategory category,
            @Nullable String cachedEtag,
            @Nullable Integer httpStatus) {
        return new Failure<>(errorMessage, cause, fallbackContent, category, cachedEtag, httpStatus);
    }

    // === Sealed Implementations ===

    /**
     * Successful HTTP operation with content.
     * Contains content loaded from server (HTTP 200) or validated from cache (HTTP 304).
     *
     * @param content the response content, never null
     * @param etag optional ETag header value for caching
     * @param httpStatus HTTP status code (typically 200 or 304)
     * @param <T> content type
     */
    record Success<T>(
    T content,
    @Nullable
    String etag,
    int httpStatus
    ) implements HttpResult<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Optional<T> getContent() {
            return Optional.of(content);
        }

        @Override
        public Optional<String> getETag() {
            return Optional.ofNullable(etag);
        }

        @Override
        public Optional<Integer> getHttpStatus() {
            return Optional.of(httpStatus);
        }

        @Override
        public Optional<HttpErrorCategory> getErrorCategory() {
            return Optional.empty();
        }

        @Override
        public <U> HttpResult<U> map(Function<T, U> mapper) {
            return new Success<>(mapper.apply(content), etag, httpStatus);
        }
    }

    /**
     * Failed HTTP operation with error details and optional fallback content.
     * Contains error information and may include cached content for degraded operation.
     *
     * @param errorMessage human-readable error description
     * @param cause underlying exception if available
     * @param fallbackContent optional cached content for degraded operation
     * @param category error classification for retry decisions
     * @param etag optional ETag from cached content
     * @param httpStatus optional HTTP status code
     * @param <T> content type
     */
    @Builder
    record Failure<T>(
    String errorMessage,
    @Nullable
    Throwable cause,
    @Nullable
    T fallbackContent,
    HttpErrorCategory category,
    @Nullable
    String etag,
    @Nullable
    Integer httpStatus
    ) implements HttpResult<T> {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Optional<T> getContent() {
            return Optional.ofNullable(fallbackContent);
        }

        @Override
        public Optional<String> getETag() {
            return Optional.ofNullable(etag);
        }

        @Override
        public Optional<Integer> getHttpStatus() {
            return Optional.ofNullable(httpStatus);
        }

        @Override
        public Optional<String> getErrorMessage() {
            return Optional.of(errorMessage);
        }

        @Override
        public Optional<Throwable> getCause() {
            return Optional.ofNullable(cause);
        }

        @Override
        public Optional<HttpErrorCategory> getErrorCategory() {
            return Optional.of(category);
        }

        @Override
        public <U> HttpResult<U> map(Function<T, U> mapper) {
            U mappedFallback = fallbackContent != null ? mapper.apply(fallbackContent) : null;
            return new Failure<>(errorMessage, cause, mappedFallback, category, etag, httpStatus);
        }
    }
}

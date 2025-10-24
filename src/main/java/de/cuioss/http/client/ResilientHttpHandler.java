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
package de.cuioss.http.client;

import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.HttpStatusFamily;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.http.client.retry.RetryContext;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HTTP handler with ETag caching and retry support.
 * <p>
 * Performs HTTP requests with conditional ETag headers ("If-None-Match") to support
 * HTTP caching. Uses {@link RetryStrategy} to retry transient failures.
 * Tracks whether content was loaded from cache (304 Not Modified) or server (200 OK).
 * <p>
 * Thread-safe using ReentrantLock.
 *
 * @param <T> the target type for content conversion
 * @author Oliver Wolff
 * @since 1.0
 */
public class ResilientHttpHandler<T> {

    private static final CuiLogger LOGGER = new CuiLogger(ResilientHttpHandler.class);
    private final HttpHandler httpHandler;
    private final RetryStrategy retryStrategy;
    private final HttpResponseConverter<T> contentConverter;
    private final ReentrantLock lock = new ReentrantLock();

    private HttpResult<T> cachedResult; // Guarded by lock, no volatile needed
    @Getter
    private volatile LoaderStatus loaderStatus = LoaderStatus.UNDEFINED; // Explicitly tracked status

    /**
     * Creates HTTP handler with ETag caching and retry support.
     * <p>
     * Use {@link RetryStrategy#none()} to disable retry behavior.
     *
     * @param httpHandler the HTTP handler for making requests, never null
     * @param retryStrategy the retry strategy for transient failures, never null (use RetryStrategy.none() to disable)
     * @param contentConverter the converter for HTTP content, never null
     * @throws NullPointerException if any parameter is null
     */
    public ResilientHttpHandler(@NonNull HttpHandler httpHandler, @NonNull RetryStrategy retryStrategy, @NonNull HttpResponseConverter<T> contentConverter) {
        this.httpHandler = httpHandler;
        this.retryStrategy = retryStrategy;
        this.contentConverter = contentConverter;
    }

    /**
     * Loads HTTP content with ETag caching and retry support.
     * <p>
     * Sends HTTP request with conditional ETag header if cached content exists.
     * Uses configured {@link RetryStrategy} to retry transient failures.
     * Updates {@link #loaderStatus} based on result.
     *
     * <h2>Result States</h2>
     * <ul>
     *   <li><strong>Success + 200</strong>: Content loaded from server</li>
     *   <li><strong>Success + 304</strong>: Content unchanged, using cached version</li>
     *   <li><strong>Failure + fallback</strong>: Error occurred, using cached data</li>
     *   <li><strong>Failure + no fallback</strong>: Error occurred with no cached content</li>
     * </ul>
     *
     * <h2>Retry Behavior</h2>
     * <ul>
     *   <li>Network errors: retried per {@link RetryStrategy}</li>
     *   <li>HTTP 5xx errors: retried per {@link RetryStrategy}</li>
     *   <li>HTTP 4xx errors: not retried</li>
     *   <li>HTTP 304 responses: not retried</li>
     * </ul>
     *
     * @return HttpResult containing content and state information, never null
     */
    public HttpResult<T> load() {
        lock.lock();
        try {
            // Set status to LOADING before starting the operation
            loaderStatus = LoaderStatus.LOADING;

            // Use RetryStrategy to handle transient failures
            RetryContext retryContext = new RetryContext("ETag-HTTP-Load:" + httpHandler.getUri().toString(), 1);

            // Execute async retry strategy and block for result (maintains existing synchronous API)
            HttpResult<T> result = retryStrategy.execute(this::fetchContentWithCache, retryContext).join();

            // Update status based on the result
            updateStatusFromResult(result);

            return result;
        } finally {
            lock.unlock();
        }
    }


    /**
     * Handles error results by returning cached content if available.
     *
     * @param category the error category to use for the result
     */
    private HttpResult<T> handleErrorResult(HttpErrorCategory category) {
        if (cachedResult != null && cachedResult.getContent().isPresent()) {
            return HttpResult.failureWithFallback(
                    "HTTP request failed, using cached content from " + httpHandler.getUrl(),
                    null,  // cause
                    cachedResult.getContent().orElse(null),
                    category,
                    cachedResult.getETag().orElse(null),
                    cachedResult.getHttpStatus().orElse(null)
            );
        } else {
            return HttpResult.failure(
                    "HTTP request failed with no cached content available from " + httpHandler.getUrl(),
                    null,  // cause
                    category
            );
        }
    }


    /**
     * Executes HTTP request with ETag validation support.
     *
     * @return HttpResult containing content and state information, never null
     */
    @SuppressWarnings("java:S2095")
    private HttpResult<T> fetchContentWithCache() {
        // Build request with conditional headers
        HttpRequest request = buildRequestWithConditionalHeaders();

        try {
            HttpClient client = httpHandler.createHttpClient();
            HttpResponse<?> response = client.send(request, contentConverter.getBodyHandler());

            return processHttpResponse(response);

        } catch (IOException e) {
            LOGGER.warn(e, HttpLogMessages.WARN.HTTP_FETCH_FAILED, httpHandler.getUrl());
            // Return error result for IOException - RetryStrategy will handle retry logic
            return handleErrorResult(HttpErrorCategory.NETWORK_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn(HttpLogMessages.WARN.HTTP_FETCH_INTERRUPTED, httpHandler.getUrl());
            // InterruptedException should not be retried
            return handleErrorResult(HttpErrorCategory.NETWORK_ERROR);
        }
    }

    /**
     * Builds an HTTP request with conditional headers (ETag support).
     *
     * @return configured HttpRequest with conditional headers if available
     */
    private HttpRequest buildRequestWithConditionalHeaders() {
        HttpRequest.Builder requestBuilder = httpHandler.requestBuilder();

        // Add If-None-Match header if we have a cached ETag
        if (cachedResult != null) {
            cachedResult.getETag().ifPresent(etag ->
                    requestBuilder.header("If-None-Match", etag));
        }

        return requestBuilder.build();
    }

    /**
     * Processes the HTTP response and returns appropriate result based on status code.
     *
     * @param response the HTTP response to process
     * @return HttpResult representing the processed response
     */
    private HttpResult<T> processHttpResponse(HttpResponse<?> response) {
        HttpStatusFamily statusFamily = HttpStatusFamily.fromStatusCode(response.statusCode());

        if (response.statusCode() == 304) {
            return handleNotModifiedResponse();
        } else if (statusFamily == HttpStatusFamily.SUCCESS) {
            return handleSuccessResponse(response);
        } else {
            return handleErrorResponse(response.statusCode(), statusFamily);
        }
    }

    /**
     * Handles HTTP 304 Not Modified responses.
     *
     * @return cached content result if available
     */
    @SuppressWarnings("java:S3655") // False positive - isPresent() checked on same line
    private HttpResult<T> handleNotModifiedResponse() {
        LOGGER.debug("HTTP content not modified (304) for %s", httpHandler.getUrl());
        if (cachedResult != null && cachedResult.getContent().isPresent()) {
            return HttpResult.success(
                    cachedResult.getContent().get(),
                    cachedResult.getETag().orElse(null),
                    304
            );
        } else {
            return HttpResult.failure(
                    "304 Not Modified but no cached content available",
                    null,
                    HttpErrorCategory.SERVER_ERROR
            );
        }
    }

    /**
     * Handles successful HTTP responses (2xx status codes).
     *
     * @param response the successful HTTP response
     * @return success result with converted content or error if conversion fails
     */
    private HttpResult<T> handleSuccessResponse(HttpResponse<?> response) {
        Object rawContent = response.body();
        String etag = response.headers().firstValue("ETag").orElse(null);

        LOGGER.debug("HTTP response received: %s SUCCESS for %s (etag: %s)",
                response.statusCode(), httpHandler.getUrl(), etag);

        // Convert raw content to target type
        Optional<T> contentOpt = contentConverter.convert(rawContent);

        if (contentOpt.isPresent()) {
            // Successful conversion - update cache with new result
            T content = contentOpt.get();
            HttpResult<T> result = HttpResult.success(content, etag, response.statusCode());
            this.cachedResult = result;
            return result;
        } else {
            // Content conversion failed - return error with no cache update
            LOGGER.warn(HttpLogMessages.WARN.CONTENT_CONVERSION_FAILED, httpHandler.getUrl());
            return HttpResult.failure(
                    "Content conversion failed for " + httpHandler.getUrl(),
                    null,
                    HttpErrorCategory.INVALID_CONTENT
            );
        }
    }

    /**
     * Handles error HTTP responses (4xx, 5xx status codes).
     *
     * @param statusCode the HTTP status code
     * @param statusFamily the HTTP status family
     * @return error result with appropriate category
     */
    private HttpResult<T> handleErrorResponse(int statusCode, HttpStatusFamily statusFamily) {
        LOGGER.warn(HttpLogMessages.WARN.HTTP_STATUS_WARNING, statusCode, statusFamily, httpHandler.getUrl());

        // For 4xx client errors, don't retry and return error with cache fallback if available
        if (statusFamily == HttpStatusFamily.CLIENT_ERROR) {
            return handleErrorResult(HttpErrorCategory.CLIENT_ERROR);
        }

        // For 5xx server errors, return error result with cache fallback if available
        // RetryStrategy will handle retry logic, but if retries are exhausted we want cached content
        return handleErrorResult(HttpErrorCategory.SERVER_ERROR);
    }

    /**
     * Updates the status based on the HttpResult result.
     * This method assumes the lock is already held.
     *
     * @param result the HttpResult to evaluate for status update
     */
    private void updateStatusFromResult(HttpResult<T> result) {
        if (result.isSuccess()) {
            loaderStatus = LoaderStatus.OK;
        } else {
            loaderStatus = LoaderStatus.ERROR;
        }
    }

}

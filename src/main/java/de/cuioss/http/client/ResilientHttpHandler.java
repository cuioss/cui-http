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

import de.cuioss.http.client.converter.HttpContentConverter;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResultObject;
import de.cuioss.http.client.retry.RetryContext;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.HttpStatusFamily;
import de.cuioss.uimodel.nameprovider.DisplayName;
import de.cuioss.uimodel.result.ResultDetail;
import de.cuioss.uimodel.result.ResultState;
import lombok.Getter;
import lombok.NonNull;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ETag-aware HTTP handler with stateful caching capabilities and built-in retry logic.
 * <p>
 * This component provides HTTP-based caching using ETags and "If-None-Match" headers,
 * with resilient HTTP operations through configurable retry strategies.
 * It tracks whether content was loaded from cache (304 Not Modified) or freshly fetched (200 OK).
 * <p>
 * Thread-safe implementation using ReentrantLock for virtual thread compatibility.
 * <h2>Retry Integration</h2>
 * The handler integrates with {@link RetryStrategy} to provide resilient HTTP operations,
 * solving permanent failure issues in well-known endpoint discovery and JWKS loading.
 *
 * @param <T> the target type for content conversion
 * @author Oliver Wolff
 * @since 1.0
 */
public class ResilientHttpHandler<T> {

    private static final CuiLogger LOGGER = new CuiLogger(ResilientHttpHandler.class);
    private final HttpHandler httpHandler;
    private final RetryStrategy retryStrategy;
    private final HttpContentConverter<T> contentConverter;
    private final ReentrantLock lock = new ReentrantLock();

    private HttpResultObject<T> cachedResult; // Guarded by lock, no volatile needed
    @Getter private volatile LoaderStatus loaderStatus = LoaderStatus.UNDEFINED; // Explicitly tracked status

    /**
     * Creates a new ETag-aware HTTP handler with unified provider for HTTP operations and retry strategy.
     * <p>
     * This constructor implements the HttpHandlerProvider pattern for unified dependency injection,
     * providing both HTTP handling capabilities and retry resilience in a single interface.
     *
     * @param provider the HTTP handler provider containing both HttpHandler and RetryStrategy
     * @throws IllegalArgumentException if provider is null
     */
    public ResilientHttpHandler(@NonNull HttpHandlerProvider provider, @NonNull HttpContentConverter<T> contentConverter) {
        this.httpHandler = provider.getHttpHandler();
        this.retryStrategy = provider.getRetryStrategy();
        this.contentConverter = contentConverter;
    }

    /**
     * Constructor accepting HttpHandler directly.
     * <p>
     * This constructor creates an ResilientHttpHandler with no retry capability.
     * For retry-capable HTTP operations, use {@link #ResilientHttpHandler(HttpHandlerProvider, HttpContentConverter)} instead.
     *
     * @param httpHandler the HTTP handler for making requests
     */
    public ResilientHttpHandler(@NonNull HttpHandler httpHandler, @NonNull HttpContentConverter<T> contentConverter) {
        this.httpHandler = httpHandler;
        this.retryStrategy = RetryStrategy.none();
        this.contentConverter = contentConverter;
    }

    /**
     * Loads HTTP content with resilient retry logic and ETag-based HTTP caching.
     * <p>
     * This method integrates {@link RetryStrategy} to provide resilient HTTP operations,
     * automatically retrying transient failures and preventing permanent failure states
     * that previously affected WellKnownResolver and JWKS loading.
     *
     * <h2>Result States</h2>
     * <ul>
     *   <li><strong>VALID + 200</strong>: Content freshly loaded from server (equivalent to LOADED_FROM_SERVER)</li>
     *   <li><strong>VALID + 304</strong>: Content unchanged, using cached version (equivalent to CACHE_ETAG)</li>
     *   <li><strong>VALID + no HTTP status</strong>: Content unchanged, using local cache (equivalent to CACHE_CONTENT)</li>
     *   <li><strong>WARNING + cached content</strong>: Error occurred but using cached data (equivalent to ERROR_WITH_CACHE)</li>
     *   <li><strong>ERROR + no content</strong>: Error occurred with no fallback (equivalent to ERROR_NO_CACHE)</li>
     * </ul>
     *
     * <h2>Retry Integration</h2>
     * The method uses the configured {@link RetryStrategy} to handle transient failures:
     * <ul>
     *   <li>Network timeouts and connection errors are retried with exponential backoff</li>
     *   <li>HTTP 5xx server errors are retried as they're often transient</li>
     *   <li>HTTP 4xx client errors are not retried as they're typically permanent</li>
     *   <li>Cache responses (304 Not Modified) are not subject to retry</li>
     * </ul>
     *
     * @return HttpResultObject containing content and detailed state information, never null
     */
    public HttpResultObject<T> load() {
        lock.lock();
        try {
            // Set status to LOADING before starting the operation
            loaderStatus = LoaderStatus.LOADING;

            // Use RetryStrategy to handle transient failures
            RetryContext retryContext = new RetryContext("ETag-HTTP-Load:" + httpHandler.getUri().toString(), 1);

            HttpResultObject<T> result = retryStrategy.execute(this::fetchJwksContentWithCache, retryContext);

            // Update status based on the result
            updateStatusFromResult(result);

            return result;
        } finally {
            lock.unlock();
        }
    }


    /**
     * Handles error results by returning cached content if available.
     */
    private HttpResultObject<T> handleErrorResult() {
        if (cachedResult != null && cachedResult.getResult() != null) {
            return new HttpResultObject<>(
                    cachedResult.getResult(),
                    ResultState.WARNING, // Using cached content but with error condition
                    new ResultDetail(
                            new DisplayName("HTTP request failed, using cached content from " + httpHandler.getUrl())),
                    HttpErrorCategory.NETWORK_ERROR,
                    cachedResult.getETag().orElse(null),
                    cachedResult.getHttpStatus().orElse(null)
            );
        } else {
            return HttpResultObject.error(
                    getEmptyFallback(), // Safe empty fallback
                    HttpErrorCategory.NETWORK_ERROR,
                    new ResultDetail(
                            new DisplayName("HTTP request failed with no cached content available from " + httpHandler.getUrl()))
            );
        }
    }

    /**
     * Handles 304 Not Modified response by returning cached content.
     */
    private HttpResultObject<T> handleNotModifiedResult() {
        LOGGER.debug("HTTP content not modified (304), using cached version");
        if (cachedResult != null) {
            return HttpResultObject.success(cachedResult.getResult(), cachedResult.getETag().orElse(null), 304);
        } else {
            return HttpResultObject.error(
                    getEmptyFallback(), // Safe empty fallback
                    HttpErrorCategory.NETWORK_ERROR,
                    new ResultDetail(
                            new DisplayName("304 Not Modified but no cached content available"))
            );
        }
    }


    /**
     * Executes HTTP request with ETag validation support and direct HttpResultObject return.
     * <p>
     * This method now returns HttpResultObject directly to support RetryStrategy.execute(),
     * implementing the HttpOperation<String> pattern for resilient HTTP operations.
     *
     * @return HttpResultObject containing content and state information, never null
     */
    @SuppressWarnings("java:S2095")
    private HttpResultObject<T> fetchJwksContentWithCache() {
        // Build request with conditional headers
        HttpRequest.Builder requestBuilder = httpHandler.requestBuilder();

        // Add If-None-Match header if we have a cached ETag
        if (cachedResult != null) {
            cachedResult.getETag().ifPresent(etag ->
                    requestBuilder.header("If-None-Match", etag));
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpClient client = httpHandler.createHttpClient();
            HttpResponse<?> response = client.send(request, contentConverter.getBodyHandler());

            HttpStatusFamily statusFamily = HttpStatusFamily.fromStatusCode(response.statusCode());

            if (response.statusCode() == 304) {
                // Not Modified - content hasn't changed, return cached content
                LOGGER.debug("Received 304 Not Modified from %s", httpHandler.getUrl());
                return handleNotModifiedResult();
            } else if (statusFamily == HttpStatusFamily.SUCCESS) {
                // 2xx Success - fresh content, update cache and return
                Object rawContent = response.body();
                String etag = response.headers().firstValue("ETag").orElse(null);

                LOGGER.debug("Received %s %s from %s with ETag: %s", response.statusCode(), statusFamily, httpHandler.getUrl(), etag);

                // Convert raw content to target type
                Optional<T> contentOpt = contentConverter.convert(rawContent);

                if (contentOpt.isPresent()) {
                    // Successful conversion - update cache with new result
                    T content = contentOpt.get();
                    HttpResultObject<T> result = HttpResultObject.success(content, etag, response.statusCode());
                    this.cachedResult = result;
                    return result;
                } else {
                    // Content conversion failed - return error with no cache update
                    LOGGER.warn(HttpLogMessages.WARN.CONTENT_CONVERSION_FAILED.format(httpHandler.getUrl()));
                    return HttpResultObject.error(
                            getEmptyFallback(), // Safe empty fallback
                            HttpErrorCategory.CLIENT_ERROR,
                            new ResultDetail(
                                    new DisplayName("Content conversion failed for %s".formatted(httpHandler.getUrl())))
                    );
                }
            } else {
                // HTTP error - this will trigger retry if it's a 5xx server error
                LOGGER.warn(HttpLogMessages.WARN.HTTP_STATUS_WARNING.format(response.statusCode(), statusFamily, httpHandler.getUrl()));

                // For 4xx client errors, don't retry and return error with cache fallback if available
                if (statusFamily == HttpStatusFamily.CLIENT_ERROR) {
                    return handleErrorResult();
                }

                // For 5xx server errors, return error result with cache fallback if available
                // RetryStrategy will handle retry logic, but if retries are exhausted we want cached content
                return handleErrorResult();
            }

        } catch (IOException e) {
            LOGGER.warn(e, HttpLogMessages.WARN.HTTP_FETCH_FAILED.format(httpHandler.getUrl()));
            // Return error result for IOException - RetryStrategy will handle retry logic
            return handleErrorResult();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn(HttpLogMessages.WARN.HTTP_FETCH_INTERRUPTED.format(httpHandler.getUrl()));
            // InterruptedException should not be retried
            return handleErrorResult();
        }
    }

    /**
     * Provides a safe empty fallback result for error cases.
     * Uses semantically correct empty value from content converter.
     * If no cached result available, uses converter's empty value.
     *
     * @return empty fallback result, never null
     */
    private T getEmptyFallback() {
        // Try to get cached result first
        if (cachedResult != null && cachedResult.getResult() != null) {
            return cachedResult.getResult();
        }
        // Use semantically correct empty value from converter
        // This ensures CUI ResultObject never gets null result
        return contentConverter.emptyValue();
    }

    /**
     * Updates the status based on the HttpResultObject result.
     * This method assumes the lock is already held.
     *
     * @param result the HttpResultObject to evaluate for status update
     */
    private void updateStatusFromResult(HttpResultObject<T> result) {
        if (result.isValid()) {
            loaderStatus = LoaderStatus.OK;
        } else {
            loaderStatus = LoaderStatus.ERROR;
        }
    }

}
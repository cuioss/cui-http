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
package de.cuioss.http.client.adapter;

import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;

import java.util.Optional;
import java.util.Set;

/**
 * Test dispatcher for HTTP adapter integration tests.
 * Supports all HTTP methods (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS).
 * <p>
 * Features:
 * <ul>
 *   <li>Configurable status codes and response content</li>
 *   <li>ETag support for caching scenarios</li>
 *   <li>Request tracking (body, headers, call count)</li>
 *   <li>304 Not Modified simulation</li>
 * </ul>
 *
 * @author Oliver Wolff
 */
public class TestApiDispatcher implements ModuleDispatcherElement {

    public static final String DEFAULT_BASE_PATH = "/api/data";

    @Getter
    @Setter
    private String basePath = DEFAULT_BASE_PATH;

    @Getter
    @Setter
    private int statusCode = 200;

    @Getter
    @Setter
    private String responseContent = "";

    @Getter
    @Setter
    private String etag = null;

    @Getter
    private int callCounter = 0;

    @Setter
    private String lastRequestBody = null;

    @Setter
    private String lastIfNoneMatch = null;

    @Getter
    @Setter
    private boolean successThenErrorMode = false;

    @Getter
    @Setter
    private String successContent = "";

    @Getter
    @Setter
    private String successEtag = null;

    @Getter
    @Setter
    private boolean errorThenSuccessMode = false;

    @Getter
    @Setter
    private String errorSuccessContent = "";

    @Getter
    @Setter
    private String errorSuccessEtag = null;

    @Override
    public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
        return handleRequest(request);
    }

    @Override
    public Optional<MockResponse> handlePost(@NonNull RecordedRequest request) {
        return handleRequestWithBody(request);
    }

    @Override
    public Optional<MockResponse> handlePut(@NonNull RecordedRequest request) {
        return handleRequestWithBody(request);
    }

    /**
     * Common handler for requests with body (POST, PUT).
     * Captures request body before delegating to handleRequest.
     *
     * @param request the recorded request
     * @return optional mock response
     */
    private Optional<MockResponse> handleRequestWithBody(@NonNull RecordedRequest request) {
        lastRequestBody = request.getBody().readUtf8();
        return handleRequest(request);
    }

    @Override
    public Optional<MockResponse> handleDelete(@NonNull RecordedRequest request) {
        return handleRequest(request);
    }

    @Override
    public Optional<MockResponse> handleHead(@NonNull RecordedRequest request) {
        return handleRequest(request);
    }

    private Optional<MockResponse> handleRequest(RecordedRequest request) {
        callCounter++;

        // Track If-None-Match header
        lastIfNoneMatch = request.getHeaders().get("If-None-Match");

        // Special mode: first call succeeds, subsequent calls fail
        if (successThenErrorMode) {
            if (callCounter == 1) {
                Headers.Builder headersBuilder = new Headers.Builder()
                        .add("Content-Type", "application/json");
                if (successEtag != null) {
                    headersBuilder.add("ETag", successEtag);
                }
                return Optional.of(new MockResponse(200, headersBuilder.build(), successContent));
            } else {
                return Optional.of(new MockResponse(503, new Headers.Builder()
                        .add("Content-Type", "application/json").build(), ""));
            }
        }

        // Special mode: first call fails, subsequent calls succeed
        if (errorThenSuccessMode) {
            if (callCounter == 1) {
                return Optional.of(new MockResponse(503, new Headers.Builder()
                        .add("Content-Type", "application/json").build(), ""));
            } else {
                Headers.Builder headersBuilder = new Headers.Builder()
                        .add("Content-Type", "application/json");
                if (errorSuccessEtag != null) {
                    headersBuilder.add("ETag", errorSuccessEtag);
                }
                return Optional.of(new MockResponse(200, headersBuilder.build(), errorSuccessContent));
            }
        }

        // Normal mode: use configured status/content
        Headers.Builder headersBuilder = new Headers.Builder()
                .add("Content-Type", "application/json");

        if (etag != null) {
            headersBuilder.add("ETag", etag);
        }

        return Optional.of(new MockResponse(statusCode, headersBuilder.build(), responseContent));
    }

    @Override
    public String getBaseUrl() {
        return basePath;
    }

    @Override
    public @NonNull Set<HttpMethodMapper> supportedMethods() {
        return Set.of(
                HttpMethodMapper.GET,
                HttpMethodMapper.POST,
                HttpMethodMapper.PUT,
                HttpMethodMapper.DELETE,
                HttpMethodMapper.HEAD
        );
    }

    /**
     * Configure for successful response with ETag.
     */
    public TestApiDispatcher withSuccessAndETag(String content, String etagValue) {
        this.statusCode = 200;
        this.responseContent = content;
        this.etag = etagValue;
        return this;
    }

    /**
     * Configure for 304 Not Modified response.
     */
    public TestApiDispatcher with304() {
        this.statusCode = 304;
        this.responseContent = "";
        // Keep existing etag
        return this;
    }

    /**
     * Configure for 204 No Content response.
     */
    public TestApiDispatcher withNoContent() {
        this.statusCode = 204;
        this.responseContent = "";
        this.etag = null;
        return this;
    }

    /**
     * Configure for server error (5xx).
     */
    public TestApiDispatcher withServerError() {
        this.statusCode = 503;
        this.responseContent = "";
        this.etag = null;
        return this;
    }

    /**
     * Configure for client error (4xx).
     */
    public TestApiDispatcher withClientError() {
        this.statusCode = 404;
        this.responseContent = "";
        this.etag = null;
        return this;
    }

    /**
     * Configure dispatcher to succeed on first call, then fail with server error on subsequent calls.
     * Useful for testing cache fallback scenarios.
     */
    public TestApiDispatcher withSuccessThenError(String initialContent, String etagValue) {
        this.successContent = initialContent;
        this.successEtag = etagValue;
        this.successThenErrorMode = true;
        this.errorThenSuccessMode = false;
        this.callCounter = 0;
        return this;
    }

    /**
     * Configure dispatcher to fail on first call, then succeed on subsequent calls.
     * Useful for testing retry scenarios.
     */
    public TestApiDispatcher withServerErrorThenSuccess(String recoveryContent, String etagValue) {
        this.errorSuccessContent = recoveryContent;
        this.errorSuccessEtag = etagValue;
        this.errorThenSuccessMode = true;
        this.successThenErrorMode = false;
        this.callCounter = 0;
        return this;
    }

    /**
     * Reset call counter and tracking data.
     */
    public TestApiDispatcher reset() {
        this.callCounter = 0;
        this.lastRequestBody = null;
        this.lastIfNoneMatch = null;
        this.successThenErrorMode = false;
        this.errorThenSuccessMode = false;
        return this;
    }

    /**
     * Get last If-None-Match header value.
     */
    public Optional<String> getLastIfNoneMatch() {
        return Optional.ofNullable(lastIfNoneMatch);
    }

    /**
     * Get last request body.
     */
    public Optional<String> getLastRequestBody() {
        return Optional.ofNullable(lastRequestBody);
    }
}

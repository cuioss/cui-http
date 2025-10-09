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
package de.cuioss.http.client.dispatcher;

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
 * Generic test dispatcher for HTTP tests that allows dynamic configuration
 * of responses with generator-based content. Can be used by multiple test classes
 * for flexible HTTP response scenarios.
 * <p>
 * Supports:
 * <ul>
 *   <li>Configurable status codes (2xx, 3xx, 4xx, 5xx)</li>
 *   <li>Dynamic response content (supports generator-based test data)</li>
 *   <li>ETag header support for caching scenarios</li>
 *   <li>Call counting for verification</li>
 *   <li>Fluent API for easy configuration</li>
 * </ul>
 *
 * @author Oliver Wolff
 */
public class TestContentDispatcher implements ModuleDispatcherElement {

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

    /**
     * Create dispatcher with default base path.
     */
    public TestContentDispatcher() {
        // Use default base path
    }

    /**
     * Create dispatcher with custom base path.
     */
    public TestContentDispatcher(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
        return handleRequest();
    }

    @Override
    public Optional<MockResponse> handleHead(@NonNull RecordedRequest request) {
        return handleRequest();
    }

    private Optional<MockResponse> handleRequest() {
        callCounter++;

        Headers.Builder headersBuilder = new Headers.Builder()
                .add("Content-Type", "text/plain");

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
        return Set.of(HttpMethodMapper.GET, HttpMethodMapper.HEAD);
    }

    /**
     * Configure for successful response with ETag.
     */
    public TestContentDispatcher withSuccess(String content, String etagValue) {
        this.statusCode = 200;
        this.responseContent = content;
        this.etag = etagValue;
        return this;
    }

    /**
     * Configure for 304 Not Modified response.
     */
    public TestContentDispatcher with304() {
        this.statusCode = 304;
        this.responseContent = "";
        return this;
    }

    /**
     * Configure for server error (5xx).
     */
    public TestContentDispatcher withServerError() {
        this.statusCode = 503;
        this.responseContent = "";
        this.etag = null;
        return this;
    }

    /**
     * Configure for client error (4xx).
     */
    public TestContentDispatcher withClientError() {
        this.statusCode = 404;
        this.responseContent = "";
        this.etag = null;
        return this;
    }

    /**
     * Reset to default successful response.
     */
    public TestContentDispatcher reset(String content) {
        this.statusCode = 200;
        this.responseContent = content;
        this.etag = null;
        this.callCounter = 0;
        return this;
    }
}

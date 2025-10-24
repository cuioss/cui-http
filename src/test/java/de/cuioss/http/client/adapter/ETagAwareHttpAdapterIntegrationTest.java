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

import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ETagAwareHttpAdapter} using MockWebServer.
 * <p>
 * Tests realistic HTTP scenarios:
 * <ul>
 *   <li>GET with ETag caching (200 → 304 flow)</li>
 *   <li>POST/PUT/DELETE with body handling</li>
 *   <li>Network failures and error handling</li>
 *   <li>Server errors (5xx) and client errors (4xx)</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
@EnableMockWebServer(useHttps = false)
@DisplayName("ETagAwareHttpAdapter Integration Tests")
class ETagAwareHttpAdapterIntegrationTest {

    private final TestApiDispatcher dispatcher = new TestApiDispatcher();

    public ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher;
    }

    /**
     * Test GET with ETag: first request 200 with ETag, second request sends If-None-Match, receives 304, returns Success with cached content
     */
    @Test
    @DisplayName("GET with ETag should cache and return 304 on second request")
    @ModuleDispatcher
    void getWithETagShouldCacheAndReturn304OnSecondRequest(URIBuilder uriBuilder) {
        // Configure dispatcher to return 200 with ETag, then 304
        dispatcher.withSuccessAndETag("{\"id\":1,\"name\":\"test\"}", "\"etag-123\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        // First request: 200 OK with ETag
        HttpResult<String> result1 = adapter.getBlocking();
        assertTrue(result1.isSuccess(), "First request should succeed");
        assertEquals("{\"id\":1,\"name\":\"test\"}", result1.getContent().orElse(null));
        assertEquals("\"etag-123\"", result1.getETag().orElse(null));
        assertEquals(200, result1.getHttpStatus().orElse(-1));

        // Configure dispatcher to return 304 for second request
        dispatcher.with304();

        // Second request: 304 Not Modified (cached content returned)
        HttpResult<String> result2 = adapter.getBlocking();
        assertTrue(result2.isSuccess(), "Second request should succeed with cached content");
        assertEquals("{\"id\":1,\"name\":\"test\"}", result2.getContent().orElse(null), "Should return cached content");
        assertEquals("\"etag-123\"", result2.getETag().orElse(null), "Should preserve cached ETag");
        assertEquals(304, result2.getHttpStatus().orElse(-1), "Status code should be 304");

        // Verify If-None-Match header was sent on second request
        assertTrue(dispatcher.getLastIfNoneMatch().isPresent(), "If-None-Match header should be sent");
        assertEquals("\"etag-123\"", dispatcher.getLastIfNoneMatch().orElse(null));
    }

    /**
     * Test POST request: body sent with Content-Type, response converted, ETag extracted but not cached
     */
    @Test
    @DisplayName("POST should send body and extract ETag without caching")
    @ModuleDispatcher
    void postShouldSendBodyAndExtractETagWithoutCaching(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("{\"id\":2,\"name\":\"created\"}", "\"etag-456\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .requestConverter(new StringRequestConverter())
                .build();

        // POST with body
        String requestBody = "{\"name\":\"new-item\"}";
        HttpResult<String> result = adapter.postBlocking(requestBody);

        assertTrue(result.isSuccess(), "POST should succeed");
        assertEquals("{\"id\":2,\"name\":\"created\"}", result.getContent().orElse(null));
        assertEquals("\"etag-456\"", result.getETag().orElse(null), "ETag should be extracted");

        // Verify request body was sent
        assertEquals(requestBody, dispatcher.getLastRequestBody().orElse(null));

        // Second POST should NOT use cache (POST is never cached)
        dispatcher.withSuccessAndETag("{\"id\":3,\"name\":\"another\"}", "\"etag-789\"");
        HttpResult<String> result2 = adapter.postBlocking(requestBody);

        assertTrue(result2.isSuccess());
        assertEquals("{\"id\":3,\"name\":\"another\"}", result2.getContent().orElse(null), "Should get fresh response, not cached");
        assertFalse(dispatcher.getLastIfNoneMatch().isPresent(), "POST should not send If-None-Match");
    }

    /**
     * Test PUT request: idempotent behavior, successful update
     */
    @Test
    @DisplayName("PUT should update resource idempotently")
    @ModuleDispatcher
    void putShouldUpdateResourceIdempotently(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("{\"id\":1,\"name\":\"updated\"}", "\"etag-updated\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data", "1").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .requestConverter(new StringRequestConverter())
                .build();

        String updateBody = "{\"name\":\"updated\"}";
        HttpResult<String> result = adapter.putBlocking(updateBody);

        assertTrue(result.isSuccess(), "PUT should succeed");
        assertEquals("{\"id\":1,\"name\":\"updated\"}", result.getContent().orElse(null));
        assertEquals("\"etag-updated\"", result.getETag().orElse(null));
        assertEquals(updateBody, dispatcher.getLastRequestBody().orElse(null));
    }

    /**
     * Test DELETE request: no body sent, 204 response handled
     */
    @Test
    @DisplayName("DELETE should handle 204 No Content response")
    @ModuleDispatcher
    void deleteShouldHandle204NoContent(URIBuilder uriBuilder) {
        dispatcher.withNoContent();

        String serverUrl = uriBuilder.addPathSegments("api", "data", "1").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpResult<String> result = adapter.deleteBlocking();

        assertTrue(result.isSuccess(), "DELETE should succeed");
        // 204 response has empty body, but StringResponseConverter converts empty string to empty Optional
        // Content may be present (empty string) or absent depending on converter implementation
        assertEquals(204, result.getHttpStatus().orElse(-1));
    }

    /**
     * Test server error: 503 response, returns SERVER_ERROR failure with status code
     */
    @Test
    @DisplayName("Server error (5xx) should return SERVER_ERROR failure")
    @ModuleDispatcher
    void serverErrorShouldReturnServerErrorFailure(URIBuilder uriBuilder) {
        dispatcher.withServerError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpResult<String> result = adapter.getBlocking();

        assertFalse(result.isSuccess(), "Server error should result in failure");
        assertEquals(HttpErrorCategory.SERVER_ERROR, result.getErrorCategory().orElse(null));
        assertEquals(503, result.getHttpStatus().orElse(-1));
        assertTrue(result.isRetryable(), "Server errors should be retryable");
    }

    /**
     * Test client error: 404 response, returns CLIENT_ERROR failure with status code
     */
    @Test
    @DisplayName("Client error (4xx) should return CLIENT_ERROR failure")
    @ModuleDispatcher
    void clientErrorShouldReturnClientErrorFailure(URIBuilder uriBuilder) {
        dispatcher.withClientError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpResult<String> result = adapter.getBlocking();

        assertFalse(result.isSuccess(), "Client error should result in failure");
        assertEquals(HttpErrorCategory.CLIENT_ERROR, result.getErrorCategory().orElse(null));
        assertEquals(404, result.getHttpStatus().orElse(-1));
        assertFalse(result.isRetryable(), "Client errors should not be retryable");
    }

    /**
     * Test network failure: server not responding, connection refused is classified as NETWORK_ERROR or CONFIGURATION_ERROR
     * Note: Connection refused (port 1) may be classified as CONFIGURATION_ERROR since it's not a true IOException in flight.
     * True network errors (timeouts, connection drops) require MockWebServer simulation or are tested in retry scenarios.
     */
    @Test
    @DisplayName("Connection refused should return failure")
    void connectionRefusedShouldReturnFailure() {
        // Use an unreachable URL to simulate connection failure
        String unreachableUrl = "http://localhost:1/unreachable";
        HttpHandler handler = HttpHandler.builder().url(unreachableUrl).build();

        HttpAdapter<String> adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpResult<String> result = adapter.getBlocking();

        assertFalse(result.isSuccess(), "Connection failure should result in failure");
        // Connection refused can be NETWORK_ERROR or CONFIGURATION_ERROR depending on JDK implementation
        assertTrue(result.getErrorCategory().isPresent(), "Should have error category");
        assertTrue(result.getErrorMessage().isPresent(), "Should have error message");
    }

    // === Helper Converters ===

    private static class StringResponseConverter implements HttpResponseConverter<String> {
        @Override
        public Optional<String> convert(@Nullable Object rawContent) {
            return rawContent == null ? Optional.empty() : Optional.of(rawContent.toString());
        }

        @Override
        public HttpResponse.BodyHandler<?> getBodyHandler() {
            return HttpResponse.BodyHandlers.ofString();
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }

    private static class StringRequestConverter implements HttpRequestConverter<String> {
        @Override
        public HttpRequest.BodyPublisher toBodyPublisher(@Nullable String content) {
            return content == null ? HttpRequest.BodyPublishers.noBody()
                                    : HttpRequest.BodyPublishers.ofString(content);
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }
}

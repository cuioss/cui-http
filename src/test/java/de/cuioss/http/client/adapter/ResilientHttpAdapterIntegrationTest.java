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

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ResilientHttpAdapter} using MockWebServer.
 * <p>
 * Tests retry behavior with realistic HTTP scenarios:
 * <ul>
 *   <li>Retry on network failures (IOException)</li>
 *   <li>Retry on server errors (5xx)</li>
 *   <li>No retry on client errors (4xx)</li>
 *   <li>Composition with ETagAwareHttpAdapter</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
@EnableMockWebServer(useHttps = false)
@DisplayName("ResilientHttpAdapter Integration Tests")
class ResilientHttpAdapterIntegrationTest {

    private final TestApiDispatcher dispatcher = new TestApiDispatcher();

    public ModuleDispatcherElement getModuleDispatcher() {
        return dispatcher;
    }

    /**
     * Test retry on server error: attempt 1 returns 503, attempt 2 returns 200
     */
    @Test
    @DisplayName("Retry on server error should succeed on second attempt")
    @ModuleDispatcher
    void retryShouldSucceedOnServerErrorSecondAttempt(URIBuilder uriBuilder) {
        // First call returns 503, second call returns 200
        dispatcher.withSuccessThenError("{\"status\":\"ok\"}", "\"etag-retry\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> baseAdapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        // Configure retry with fast delays for testing
        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .multiplier(1.5)
                .maxDelay(Duration.ofMillis(50))
                .jitter(0.0) // No jitter for predictable testing
                .idempotentOnly(false) // Allow retry on all methods
                .build();

        HttpAdapter<String> resilientAdapter = ResilientHttpAdapter.wrap(baseAdapter, config);

        // First request succeeds
        HttpResult<String> result1 = resilientAdapter.getBlocking();
        assertTrue(result1.isSuccess(), "First request should succeed");
        assertEquals("{\"status\":\"ok\"}", result1.getContent().orElse(null));

        // Now dispatcher is configured to fail
        // Second request will fail with 503 initially, then succeed on retry
        dispatcher.reset();
        dispatcher.withServerErrorThenSuccess("{\"status\":\"recovered\"}", "\"etag-recovery\"");

        HttpResult<String> result2 = resilientAdapter.getBlocking();
        assertTrue(result2.isSuccess(), "Should succeed after retry");
        assertEquals("{\"status\":\"recovered\"}", result2.getContent().orElse(null));
        assertTrue(dispatcher.getCallCounter() >= 2, "Should have retried at least once");
    }

    /**
     * Test no retry on client error: 404 returned immediately, no retry attempts
     */
    @Test
    @DisplayName("No retry on client error should fail immediately")
    @ModuleDispatcher
    void noRetryShouldFailImmediatelyOnClientError(URIBuilder uriBuilder) {
        dispatcher.withClientError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> baseAdapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(10))
                .build();

        HttpAdapter<String> resilientAdapter = ResilientHttpAdapter.wrap(baseAdapter, config);

        HttpResult<String> result = resilientAdapter.getBlocking();

        assertFalse(result.isSuccess(), "Client error should result in failure");
        assertEquals(HttpErrorCategory.CLIENT_ERROR, result.getErrorCategory().orElse(null));
        assertFalse(result.isRetryable(), "Client errors should not be retryable");
        assertEquals(1, dispatcher.getCallCounter(), "Should only attempt once (no retry)");
    }

    /**
     * Test composition: ResilientHttpAdapter wraps ETagAwareHttpAdapter, retry + caching work together
     */
    @Test
    @DisplayName("Composition should combine retry and ETag caching")
    @ModuleDispatcher
    void compositionShouldCombineRetryAndCaching(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("{\"data\":\"cached\"}", "\"etag-composed\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        // Stack: ResilientHttpAdapter -> ETagAwareHttpAdapter
        HttpAdapter<String> etagAdapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .etagCachingEnabled(true)
                .build();

        HttpAdapter<String> resilientAdapter = ResilientHttpAdapter.wrap(etagAdapter);

        // First request: 200 OK, cached
        HttpResult<String> result1 = resilientAdapter.getBlocking();
        assertTrue(result1.isSuccess());
        assertEquals("{\"data\":\"cached\"}", result1.getContent().orElse(null));
        assertEquals("\"etag-composed\"", result1.getETag().orElse(null));

        // Configure for 304 Not Modified
        dispatcher.with304();

        // Second request: 304 Not Modified, uses cache
        HttpResult<String> result2 = resilientAdapter.getBlocking();
        assertTrue(result2.isSuccess(), "304 response should be success (from cache)");
        assertEquals("{\"data\":\"cached\"}", result2.getContent().orElse(null), "Should return cached content");
        assertEquals("\"etag-composed\"", result2.getETag().orElse(null));
        assertEquals(304, result2.getHttpStatus().orElse(-1));

        // 304 is a success, not retried
        assertTrue(result2.isSuccess(), "304 should not trigger retry");
    }

    /**
     * Test 304 not retried: ETagAwareHttpAdapter returns Success for 304, ResilientHttpAdapter doesn't retry
     */
    @Test
    @DisplayName("304 Not Modified should not trigger retry")
    @ModuleDispatcher
    void notModifiedShouldNotTriggerRetry(URIBuilder uriBuilder) {
        // First request: 200 with ETag
        dispatcher.withSuccessAndETag("{\"data\":\"original\"}", "\"etag-304\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> etagAdapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        HttpAdapter<String> resilientAdapter = ResilientHttpAdapter.wrap(etagAdapter);

        // First request caches response
        HttpResult<String> result1 = resilientAdapter.getBlocking();
        assertTrue(result1.isSuccess());

        dispatcher.reset();
        dispatcher.with304();

        // Second request: 304 Not Modified
        HttpResult<String> result2 = resilientAdapter.getBlocking();

        assertTrue(result2.isSuccess(), "304 should be treated as success");
        assertEquals(304, result2.getHttpStatus().orElse(-1));
        assertEquals("{\"data\":\"original\"}", result2.getContent().orElse(null), "Should return cached content");
        assertEquals(1, dispatcher.getCallCounter(), "304 should not trigger retry (only 1 call)");
    }

    /**
     * Test idempotentOnly mode prevents POST retry
     */
    @Test
    @DisplayName("idempotentOnly=true should prevent POST retry")
    @ModuleDispatcher
    void idempotentOnlyShouldPreventPostRetry(URIBuilder uriBuilder) {
        dispatcher.withServerError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> baseAdapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        // Default config has idempotentOnly=true
        RetryConfig config = RetryConfig.defaults();
        HttpAdapter<String> resilientAdapter = ResilientHttpAdapter.wrap(baseAdapter, config);

        // POST with server error should NOT retry (non-idempotent)
        HttpResult<String> result = resilientAdapter.postBlocking(null);

        assertFalse(result.isSuccess());
        assertEquals(HttpErrorCategory.SERVER_ERROR, result.getErrorCategory().orElse(null));
        assertEquals(1, dispatcher.getCallCounter(), "POST should not retry with idempotentOnly=true");
    }

    /**
     * Test idempotentOnly=false allows POST retry
     */
    @Test
    @DisplayName("idempotentOnly=false should allow POST retry")
    @ModuleDispatcher
    void idempotentOnlyFalseShouldAllowPostRetry(URIBuilder uriBuilder) {
        dispatcher.withServerErrorThenSuccess("{\"created\":true}", null);

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).build();

        HttpAdapter<String> baseAdapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .idempotentOnly(false) // Allow POST retry
                .build();

        HttpAdapter<String> resilientAdapter = ResilientHttpAdapter.wrap(baseAdapter, config);

        // POST with server error SHOULD retry (idempotentOnly=false)
        HttpResult<String> result = resilientAdapter.postBlocking(null);

        assertTrue(result.isSuccess(), "Should succeed after retry");
        assertEquals("{\"created\":true}", result.getContent().orElse(null));
        assertTrue(dispatcher.getCallCounter() >= 2, "POST should retry with idempotentOnly=false");
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
}

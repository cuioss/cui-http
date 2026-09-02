/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
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

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
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
 *   <li>Cancellation propagation through the retry chain</li>
 *   <li>Handler lifecycle across the full adapter stack</li>
 * </ul>
 * <p>
 * TLS-dependent cases are <strong>not</strong> covered here — this fixture runs cleartext by choice,
 * not because TLS is unavailable. A {@code @EnableMockWebServer(useHttps = true)} server does accept
 * connections in this project, and {@code HttpHandlerHttpsIntegrationTest} drives a real handshake
 * against one. Retry behaviour is orthogonal to the transport, so adding TLS here would buy no
 * evidence this suite does not already have; the transport-level TLS evidence lives in that class.
 * The adapter-level TLS consequence that is <em>not</em> orthogonal — a handshake failure must not be
 * retried — is pinned deterministically in {@link ResilientHttpAdapterTest}, and the classification
 * it depends on in {@code HttpErrorCategoryTest}.
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
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

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
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

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
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

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
        assertEquals(Optional.of(304), result2.getHttpStatus());

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
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

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
        assertEquals(Optional.of(304), result2.getHttpStatus());
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
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

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
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

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

    /**
     * Cancelling the future returned by {@code get()} while the chain waits out its backoff must
     * stop the retry chain, not merely detach the caller from a chain that keeps hitting the server.
     */
    @Test
    @DisplayName("Cancelling mid-backoff issues no further server request")
    @ModuleDispatcher
    void cancellingMidBackoffShouldIssueNoFurtherRequest(URIBuilder uriBuilder) {
        dispatcher.withServerError();

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

        HttpAdapter<String> baseAdapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(new StringResponseConverter())
                .build();

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(300))
                .multiplier(1.0)
                .jitter(0.0)
                .build();

        CompletableFuture<HttpResult<String>> pending =
                ResilientHttpAdapter.wrap(baseAdapter, config).get();

        await().atMost(Duration.ofSeconds(5))
                .until(() -> dispatcher.getCallCounter() >= 1);
        pending.cancel(true);

        // Hold well past several backoff windows: the cancelled chain must never call again.
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertEquals(1, dispatcher.getCallCounter(),
                        "Cancellation must stop the retry chain from calling the server again"));

        handler.close();
    }

    /**
     * The full {@code HttpHandler -> ETagAwareHttpAdapter -> ResilientHttpAdapter} stack must work
     * with the handler managed as a resource, and leaving the block must release the client the
     * handler owns without disturbing the completed request.
     */
    @Test
    @DisplayName("Full adapter stack works with the handler closed in try-with-resources")
    @ModuleDispatcher
    void fullStackShouldWorkWithHandlerAsResource(URIBuilder uriBuilder) {
        dispatcher.withSuccessAndETag("{\"data\":\"stacked\"}", "\"etag-stacked\"");

        String serverUrl = uriBuilder.addPathSegments("api", "data").build().toString();
        HttpClient borrowedClient;
        HttpResult<String> result;

        try (HttpHandler handler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build()) {
            borrowedClient = handler.createHttpClient();

            HttpAdapter<String> stack = ResilientHttpAdapter.wrap(
                    ETagAwareHttpAdapter.<String>builder()
                            .httpHandler(handler)
                            .responseConverter(new StringResponseConverter())
                            .build());

            result = stack.getBlocking();
            assertTrue(result.isSuccess(), "The stacked request must succeed inside the block");
        }

        assertAll("The result survives the block; the borrowed client is released with the handler",
                () -> assertEquals("{\"data\":\"stacked\"}", result.getContent().orElse(null)),
                () -> assertEquals("\"etag-stacked\"", result.getETag().orElse(null)),
                () -> assertTrue(borrowedClient.isTerminated(),
                        "Closing the handler releases the client the adapters borrowed"));
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

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
import de.cuioss.http.client.converter.StringContentConverter;
import de.cuioss.http.client.dispatcher.TestContentDispatcher;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.http.client.retry.ExponentialBackoffRetryStrategy;
import de.cuioss.http.client.retry.RetryMetrics;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ResilientHttpHandler} using MockWebServer.
 * <p>
 * This test class focuses on HTTP integration scenarios:
 * <ul>
 *   <li>HTTP error handling (304, 4xx, 5xx) with and without cache fallback</li>
 *   <li>Successful response caching and ETag support</li>
 *   <li>Retry strategy integration with ExponentialBackoffRetryStrategy</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("ResilientHttpHandler Integration Tests")
class ResilientHttpHandlerIntegrationTest {

    private final TypedGenerator<String> strings = Generators.nonEmptyStrings();

    @Nested
    @DisplayName("HTTP Error Handling with MockWebServer")
    @EnableMockWebServer(useHttps = false)
    class HttpErrorHandlingTests {

        private final TestContentDispatcher dispatcher = new TestContentDispatcher();

        public ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher;
        }

        @Test
        @DisplayName("Should handle 304 Not Modified without cached content")
        @ModuleDispatcher
        void shouldHandle304WithoutCachedContent(URIBuilder uriBuilder) {
            dispatcher.with304();

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertFalse(result.isSuccess(), "304 without prior cache should fail");
            assertTrue(result.getErrorMessage().isPresent());
            assertTrue(result.getErrorMessage().get().contains("no cached content"),
                    "Error message should mention missing cache");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus());
        }

        @Test
        @DisplayName("Should handle server error (5xx) without cached content")
        @ModuleDispatcher
        void shouldHandleServerErrorWithoutCache(URIBuilder uriBuilder) {
            dispatcher.withServerError();

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertFalse(result.isSuccess(), "Server error should result in failure");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus(),
                    "Status should be ERROR after failure");
            assertTrue(result.getErrorMessage().isPresent());
            assertTrue(result.getErrorMessage().get().contains("no cached content"),
                    "Error message should mention no cache available");
            assertEquals(HttpErrorCategory.SERVER_ERROR, result.getErrorCategory().orElseThrow());
            assertTrue(result.isRetryable(), "Server errors should be retryable");
        }

        @Test
        @DisplayName("Should handle client error (4xx) without cached content")
        @ModuleDispatcher
        void shouldHandleClientErrorWithoutCache(URIBuilder uriBuilder) {
            dispatcher.withClientError();

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertFalse(result.isSuccess(), "Client error should result in failure");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus(),
                    "Status should be ERROR after client error");
            assertTrue(result.getErrorMessage().isPresent());
            assertTrue(result.getErrorMessage().get().contains("no cached content"));
            assertEquals(HttpErrorCategory.CLIENT_ERROR, result.getErrorCategory().orElseThrow());
            assertFalse(result.isRetryable(), "Client errors should not be retryable");
        }

        @Test
        @DisplayName("Should handle successful response and update cache")
        @ModuleDispatcher
        void shouldHandleSuccessAndUpdateCache(URIBuilder uriBuilder) {
            String responseContent = strings.next();
            String etagValue = strings.next();

            dispatcher.withSuccess(responseContent, etagValue);

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertTrue(result.isSuccess(), "Successful response should succeed");
            assertEquals(LoaderStatus.OK, handler.getLoaderStatus(),
                    "Status should be OK after success");
            assertTrue(result.getContent().isPresent());
            assertEquals(responseContent, result.getContent().get());
            assertTrue(result.getETag().isPresent());
            assertEquals(etagValue, result.getETag().get());
            assertEquals(200, result.getHttpStatus().orElseThrow());
        }

        @Test
        @DisplayName("Should fallback to cached content after server error")
        @ModuleDispatcher
        void shouldFallbackToCacheAfterServerError(URIBuilder uriBuilder) {
            String initialContent = strings.next();
            String initialEtag = strings.next();

            // First load succeeds and populates cache
            dispatcher.withSuccess(initialContent, initialEtag);

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            HttpResult<String> firstResult = handler.load();
            assertTrue(firstResult.isSuccess(), "Initial load should succeed");
            assertEquals(initialContent, firstResult.getContent().orElseThrow());

            // Second load fails with server error but returns cached content as fallback
            dispatcher.withServerError();

            HttpResult<String> secondResult = handler.load();

            assertFalse(secondResult.isSuccess(), "Server error should mark result as failure");
            assertTrue(secondResult.getContent().isPresent(), "Should have cached content as fallback");
            assertEquals(initialContent, secondResult.getContent().get(), "Should return cached content");
            assertEquals(HttpErrorCategory.SERVER_ERROR, secondResult.getErrorCategory().orElseThrow());
            assertTrue(secondResult.getErrorMessage().isPresent());
            assertTrue(secondResult.getErrorMessage().get().contains("using cached content"),
                    "Error message should indicate cached content fallback");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus());
        }

        @Test
        @DisplayName("Should fallback to cached content after client error")
        @ModuleDispatcher
        void shouldFallbackToCacheAfterClientError(URIBuilder uriBuilder) {
            String initialContent = strings.next();
            String initialEtag = strings.next();

            // First load succeeds and populates cache
            dispatcher.withSuccess(initialContent, initialEtag);

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            HttpResult<String> firstResult = handler.load();
            assertTrue(firstResult.isSuccess(), "Initial load should succeed");

            // Second load fails with client error but returns cached content as fallback
            dispatcher.withClientError();

            HttpResult<String> secondResult = handler.load();

            assertFalse(secondResult.isSuccess(), "Client error should mark result as failure");
            assertTrue(secondResult.getContent().isPresent(), "Should have cached content as fallback");
            assertEquals(initialContent, secondResult.getContent().get(), "Should return cached content");
            assertEquals(HttpErrorCategory.CLIENT_ERROR, secondResult.getErrorCategory().orElseThrow());
            assertTrue(secondResult.getErrorMessage().get().contains("using cached content"));
            assertFalse(secondResult.isRetryable(), "Client errors should not be retryable");
        }

        @Test
        @DisplayName("Should handle content conversion failure")
        @ModuleDispatcher
        void shouldHandleContentConversionFailure(URIBuilder uriBuilder) {
            String responseContent = strings.next();

            dispatcher.withSuccess(responseContent, null);

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();

            // Converter that always returns empty Optional to simulate conversion failure
            HttpContentConverter<String> failingConverter = new HttpContentConverter<>() {
                @Override
                public Optional<String> convert(Object rawContent) {
                    return Optional.empty();
                }

                @Override
                public HttpResponse.BodyHandler<?> getBodyHandler() {
                    return HttpResponse.BodyHandlers.ofString();
                }

                @Override
                public String emptyValue() {
                    return "";
                }
            };

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), failingConverter);

            HttpResult<String> result = handler.load();

            assertFalse(result.isSuccess(), "Content conversion failure should result in failure");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus());
            assertTrue(result.getErrorMessage().isPresent());
            assertTrue(result.getErrorMessage().get().contains("Content conversion failed"));
            assertEquals(HttpErrorCategory.INVALID_CONTENT, result.getErrorCategory().orElseThrow());
            assertFalse(result.getContent().isPresent(), "No content should be present after conversion failure");
        }
    }

    @Nested
    @DisplayName("Retry Strategy Integration")
    @EnableMockWebServer(useHttps = false)
    class RetryStrategyTests {

        private final TestContentDispatcher dispatcher = new TestContentDispatcher();

        public ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher;
        }

        @Test
        @DisplayName("Should complete without retry on successful response")
        @ModuleDispatcher
        void shouldCompleteWithoutRetryOnSuccess(URIBuilder uriBuilder) {
            String successData = strings.next();

            dispatcher.withSuccess(successData, null);

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();

            RetryStrategy retryStrategy = new ExponentialBackoffRetryStrategy.Builder()
                    .maxAttempts(3)
                    .initialDelay(Duration.ofMillis(100))
                    .build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, retryStrategy, StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertTrue(result.isSuccess(), "Should succeed without retry");
            assertEquals(successData, result.getContent().orElseThrow());
            assertEquals(LoaderStatus.OK, handler.getLoaderStatus());
        }

        @Test
        @DisplayName("Should not retry on client error (4xx)")
        @ModuleDispatcher
        void shouldNotRetryOnClientError(URIBuilder uriBuilder) {
            dispatcher.withClientError();

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();

            RetryStrategy retryStrategy = new ExponentialBackoffRetryStrategy.Builder()
                    .maxAttempts(3)
                    .initialDelay(Duration.ofMillis(100))
                    .build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, retryStrategy, StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertFalse(result.isSuccess(), "Client error should fail");
            assertEquals(HttpErrorCategory.CLIENT_ERROR, result.getErrorCategory().orElseThrow());
            assertFalse(result.isRetryable(), "Client errors are not retryable");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus());
        }

        @Test
        @DisplayName("Should respect custom backoff multiplier and max delay")
        @ModuleDispatcher
        void shouldRespectBackoffMultiplierAndMaxDelay(URIBuilder uriBuilder) {
            dispatcher.withServerError();

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();

            // Test that custom Builder parameters are accepted and used
            RetryStrategy retryStrategy = new ExponentialBackoffRetryStrategy.Builder()
                    .maxAttempts(3)
                    .initialDelay(Duration.ofMillis(10))
                    .backoffMultiplier(2.5)
                    .maxDelay(Duration.ofMillis(50))
                    .jitterFactor(0.0)
                    .build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, retryStrategy, StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertFalse(result.isSuccess(), "Should fail after retries");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus());
        }

        @Test
        @DisplayName("Should work with custom retry metrics")
        @ModuleDispatcher
        void shouldWorkWithCustomRetryMetrics(URIBuilder uriBuilder) {
            String successData = strings.next();
            dispatcher.withSuccess(successData, null);

            String url = uriBuilder.addPathSegments("api", "data").build().toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();

            RetryStrategy retryStrategy = new ExponentialBackoffRetryStrategy.Builder()
                    .maxAttempts(2)
                    .retryMetrics(RetryMetrics.noOp())
                    .build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, retryStrategy, StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertTrue(result.isSuccess());
            assertEquals(successData, result.getContent().orElseThrow());
        }
    }
}

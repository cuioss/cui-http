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
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link ResilientHttpHandler} covering HTTP error handling,
 * caching behavior, retry strategy integration, and thread safety.
 * <p>
 * This test uses MockWebServer to simulate HTTP scenarios and focuses on:
 * <ul>
 *   <li>Constructor validation and initialization</li>
 *   <li>Status tracking behavior</li>
 *   <li>HTTP error handling (4xx, 5xx) with and without cache fallback</li>
 *   <li>304 Not Modified response handling</li>
 *   <li>Successful response caching and ETag support</li>
 *   <li>Retry strategy integration with ExponentialBackoffRetryStrategy</li>
 *   <li>Thread safety of status updates</li>
 *   <li>Edge cases and boundary conditions</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
@DisplayName("ResilientHttpHandler Unit Tests")
class ResilientHttpHandlerTest {

    private static final String TEST_URL = "http://test.example.com/api/data";

    @Nested
    @DisplayName("Constructor and Initialization")
    class ConstructorTests {

        @Test
        @DisplayName("Should create handler with all required parameters")
        void shouldCreateHandlerWithRequiredParameters() {
            HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
            RetryStrategy retryStrategy = RetryStrategy.none();
            HttpContentConverter<String> converter = StringContentConverter.identity();

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, retryStrategy, converter);

            assertNotNull(handler, "Handler should be created successfully");
            assertEquals(LoaderStatus.UNDEFINED, handler.getLoaderStatus(),
                    "Initial status should be UNDEFINED");
        }

        @Test
        @DisplayName("Should create handler without retry strategy")
        void shouldCreateHandlerWithoutRetry() {
            HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
            HttpContentConverter<String> converter = StringContentConverter.identity();

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), converter);

            assertNotNull(handler, "Handler with no-retry strategy should be created");
            assertEquals(LoaderStatus.UNDEFINED, handler.getLoaderStatus(),
                    "Initial status should be UNDEFINED");
        }
    }

    @Nested
    @DisplayName("Status Tracking")
    class StatusTrackingTests {

        @Test
        @DisplayName("Should start with UNDEFINED status")
        void shouldStartWithUndefinedStatus() {
            ResilientHttpHandler<String> handler = createTestHandler();

            assertEquals(LoaderStatus.UNDEFINED, handler.getLoaderStatus(),
                    "New handler should have UNDEFINED status");
        }

        @Test
        @DisplayName("Should maintain status visibility across threads")
        void shouldMaintainStatusVisibility() throws InterruptedException {
            ResilientHttpHandler<String> handler = createTestHandler();

            // Initial status should be UNDEFINED
            LoaderStatus initialStatus = handler.getLoaderStatus();
            assertEquals(LoaderStatus.UNDEFINED, initialStatus,
                    "Initial status should be UNDEFINED");

            // Read status from different thread
            Thread statusReader = new Thread(() -> {
                LoaderStatus status = handler.getLoaderStatus();
                assertEquals(LoaderStatus.UNDEFINED, status,
                        "Status should be visible from different thread");
            });

            statusReader.start();
            statusReader.join();
        }
    }

    @Nested
    @DisplayName("Content Converter Behavior")
    class ContentConverterTests {

        @Test
        @DisplayName("Should use converter's empty value as fallback")
        void shouldUseConverterEmptyValueAsFallback() {
            HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
            HttpContentConverter<String> converter = StringContentConverter.identity();

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), converter);

            assertNotNull(handler, "Handler should be created with identity converter");
            // The empty value behavior is tested through integration tests
            // as it requires actual HTTP operations to trigger fallback paths
        }

        @Test
        @DisplayName("Should handle custom content converters")
        void shouldHandleCustomConverters() {
            HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();

            // Custom converter that always returns "CUSTOM_EMPTY"
            HttpContentConverter<String> customConverter = new HttpContentConverter<String>() {
                @Override
                public HttpResponse.BodyHandler<?> getBodyHandler() {
                    return HttpResponse.BodyHandlers.ofString();
                }

                @Override
                public Optional<String> convert(Object rawContent) {
                    return Optional.ofNullable((String) rawContent);
                }

                @NonNull
                @Override
                public String emptyValue() {
                    return "CUSTOM_EMPTY";
                }
            };

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), customConverter);

            assertNotNull(handler, "Handler should accept custom converter");
            // Empty value usage is verified through integration tests
        }

        @Test
        @DisplayName("Should work with identity string converter")
        void shouldWorkWithIdentityConverter() {
            HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
            HttpContentConverter<String> converter = StringContentConverter.identity();

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), converter);

            assertNotNull(handler, "Handler should work with identity converter");
            assertEquals(LoaderStatus.UNDEFINED, handler.getLoaderStatus(),
                    "Status should be UNDEFINED before any operations");
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent status reads")
        void shouldHandleConcurrentStatusReads() throws InterruptedException {
            ResilientHttpHandler<String> handler = createTestHandler();

            // Create multiple threads that read status concurrently
            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    LoaderStatus status = handler.getLoaderStatus();
                    assertNotNull(status, "Status should never be null");
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }
        }

        @Test
        @DisplayName("Should maintain status consistency under concurrent access")
        void shouldMaintainStatusConsistency() throws InterruptedException {
            ResilientHttpHandler<String> handler = createTestHandler();
            LoaderStatus initialStatus = handler.getLoaderStatus();

            // Multiple threads reading status should see consistent value
            LoaderStatus[] statuses = new LoaderStatus[5];
            Thread[] threads = new Thread[5];

            for (int i = 0; i < threads.length; i++) {
                final int index = i;
                threads[i] = new Thread(() -> statuses[index] = handler.getLoaderStatus());
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // All threads should see the same initial status
            for (LoaderStatus status : statuses) {
                assertEquals(initialStatus, status,
                        "All concurrent reads should see same status");
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle URLs with special characters")
        void shouldHandleSpecialCharactersInUrl() {
            String specialUrl = "http://test.example.com/api/v1/users?name=John%20Doe&id=123";
            HttpHandler httpHandler = HttpHandler.builder().url(specialUrl).build();

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            assertNotNull(handler, "Handler should handle URLs with special characters");
        }

        @Test
        @DisplayName("Should handle HTTPS URLs")
        void shouldHandleHttpsUrls() {
            String httpsUrl = "https://secure.example.com/api/data";
            HttpHandler httpHandler = HttpHandler.builder().url(httpsUrl).build();

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            assertNotNull(handler, "Handler should handle HTTPS URLs");
        }

        @Test
        @DisplayName("Should handle very long URLs")
        void shouldHandleLongUrls() {
            String longPath = "a".repeat(1000);
            String longUrl = "http://test.example.com/api/" + longPath;
            HttpHandler httpHandler = HttpHandler.builder().url(longUrl).build();

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            assertNotNull(handler, "Handler should handle long URLs");
        }
    }

    @Nested
    @DisplayName("HTTP Error Handling with MockWebServer")
    class HttpErrorHandlingTests {

        private MockWebServer server;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
        }

        @AfterEach
        void tearDown() throws IOException {
            server.shutdown();
        }

        @Test
        @DisplayName("Should handle 304 Not Modified without cached content")
        void shouldHandle304WithoutCachedContent() {
            server.enqueue(new MockResponse(304, okhttp3.Headers.of(), ""));

            String url = server.url("/api/data").toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            // 304 response with no cached content should fail
            assertFalse(result.isSuccess(), "304 without prior cache should fail");
            assertTrue(result.getErrorMessage().isPresent());
            assertTrue(result.getErrorMessage().get().contains("no cached content"),
                    "Error message should mention missing cache");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus());
        }

        @Test
        @DisplayName("Should handle server error (5xx) without cached content")
        void shouldHandleServerErrorWithoutCache() {
            server.enqueue(new MockResponse(503, okhttp3.Headers.of(), "Service Unavailable"));

            String url = server.url("/api/data").toString();
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
        void shouldHandleClientErrorWithoutCache() {
            server.enqueue(new MockResponse(404, okhttp3.Headers.of(), "Not Found"));

            String url = server.url("/api/data").toString();
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
        void shouldHandleSuccessAndUpdateCache() {
            server.enqueue(new MockResponse(200,
                    okhttp3.Headers.of("ETag", "etag-456"),
                    "response-content"));

            String url = server.url("/api/data").toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertTrue(result.isSuccess(), "Successful response should succeed");
            assertEquals(LoaderStatus.OK, handler.getLoaderStatus(),
                    "Status should be OK after success");
            assertTrue(result.getContent().isPresent());
            assertEquals("response-content", result.getContent().get());
            assertTrue(result.getETag().isPresent());
            assertEquals("etag-456", result.getETag().get());
            assertEquals(200, result.getHttpStatus().orElseThrow());
        }

        @Test
        @DisplayName("Should use cached content as fallback after error")
        void shouldUseCachedContentAsFallback() {
            // First request succeeds and populates cache
            server.enqueue(new MockResponse(200,
                    okhttp3.Headers.of("ETag", "etag-initial"),
                    "initial-content"));

            // Second request returns server error
            server.enqueue(new MockResponse(503, okhttp3.Headers.of(), "Service Unavailable"));

            String url = server.url("/api/data").toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), StringContentConverter.identity());

            // First load populates cache
            HttpResult<String> firstResult = handler.load();
            assertTrue(firstResult.isSuccess());
            assertEquals("initial-content", firstResult.getContent().orElseThrow());
            assertEquals(LoaderStatus.OK, handler.getLoaderStatus());

            // Second load encounters error, should use cached content as fallback
            HttpResult<String> secondResult = handler.load();
            assertFalse(secondResult.isSuccess(), "Server error should result in failure state");
            assertTrue(secondResult.getContent().isPresent(),
                    "Cached content should be available as fallback");
            assertEquals("initial-content", secondResult.getContent().get(),
                    "Should use cached content from first successful load");
            assertEquals("etag-initial", secondResult.getETag().orElseThrow(),
                    "Should preserve ETag from cached content");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus(),
                    "Status should be ERROR even with fallback content");
        }
    }

    @Nested
    @DisplayName("Retry Strategy Integration")
    class RetryStrategyTests {

        private MockWebServer server;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
        }

        @AfterEach
        void tearDown() throws IOException {
            server.shutdown();
        }

        @Test
        @DisplayName("Should complete without retry on successful response")
        void shouldCompleteWithoutRetryOnSuccess() {
            server.enqueue(new MockResponse(200, okhttp3.Headers.of(), "success-data"));

            String url = server.url("/api/data").toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();

            // Use retry strategy (but should not retry on success)
            RetryStrategy retryStrategy = new de.cuioss.http.client.retry.ExponentialBackoffRetryStrategy.Builder()
                    .maxAttempts(3)
                    .initialDelay(java.time.Duration.ofMillis(100))
                    .build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, retryStrategy, StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertTrue(result.isSuccess(), "Should succeed without retry");
            assertEquals("success-data", result.getContent().orElseThrow());
            assertEquals(LoaderStatus.OK, handler.getLoaderStatus());
        }

        @Test
        @DisplayName("Should not retry on client error (4xx)")
        void shouldNotRetryOnClientError() {
            server.enqueue(new MockResponse(400, okhttp3.Headers.of(), "Bad Request"));

            String url = server.url("/api/data").toString();
            HttpHandler httpHandler = HttpHandler.builder().url(url).build();

            RetryStrategy retryStrategy = new de.cuioss.http.client.retry.ExponentialBackoffRetryStrategy.Builder()
                    .maxAttempts(3)
                    .initialDelay(java.time.Duration.ofMillis(100))
                    .build();
            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, retryStrategy, StringContentConverter.identity());

            HttpResult<String> result = handler.load();

            assertFalse(result.isSuccess(), "Client error should fail");
            assertEquals(HttpErrorCategory.CLIENT_ERROR, result.getErrorCategory().orElseThrow());
            assertFalse(result.isRetryable(), "Client errors are not retryable");
            assertEquals(LoaderStatus.ERROR, handler.getLoaderStatus());
        }
    }

    // Helper method to create a basic test handler
    private static ResilientHttpHandler<String> createTestHandler() {
        HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
        return new ResilientHttpHandler<>(
                httpHandler, RetryStrategy.none(), StringContentConverter.identity());
    }
}

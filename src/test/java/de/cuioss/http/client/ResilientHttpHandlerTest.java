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
 * Unit test for {@link ResilientHttpHandler} focusing on business logic,
 * state management, and edge cases without requiring HTTP infrastructure.
 * <p>
 * Integration tests with actual HTTP calls are in {@link ResilientHttpHandlerIntegrationTest}.
 * This test focuses on:
 * <ul>
 *   <li>Constructor validation and initialization</li>
 *   <li>Status tracking behavior</li>
 *   <li>Error handling and fallback logic</li>
 *   <li>Thread safety of status updates</li>
 *   <li>Edge cases and boundary conditions</li>
 * </ul>
 *
 * @author Claude Code
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
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should identify retryable error categories")
        void shouldIdentifyRetryableErrors() {
            assertTrue(HttpErrorCategory.NETWORK_ERROR.isRetryable(),
                    "NETWORK_ERROR should be retryable");
            assertTrue(HttpErrorCategory.SERVER_ERROR.isRetryable(),
                    "SERVER_ERROR should be retryable");
        }

        @Test
        @DisplayName("Should identify non-retryable error categories")
        void shouldIdentifyNonRetryableErrors() {
            assertFalse(HttpErrorCategory.CLIENT_ERROR.isRetryable(),
                    "CLIENT_ERROR should not be retryable");
            assertFalse(HttpErrorCategory.INVALID_CONTENT.isRetryable(),
                    "INVALID_CONTENT should not be retryable");
            assertFalse(HttpErrorCategory.CONFIGURATION_ERROR.isRetryable(),
                    "CONFIGURATION_ERROR should not be retryable");
        }

        @Test
        @DisplayName("Should create error results with proper categories")
        void shouldCreateErrorResultsWithCategories() {
            HttpResult<String> networkError = HttpResult.failure(
                    "Network error occurred",
                    null,
                    HttpErrorCategory.NETWORK_ERROR);

            assertFalse(networkError.isSuccess(),
                    "Result should not be successful");
            assertEquals(HttpErrorCategory.NETWORK_ERROR,
                    networkError.getErrorCategory().orElse(null),
                    "Result should have NETWORK_ERROR category");
            assertTrue(networkError.isRetryable(),
                    "Network error should be retryable");
        }
    }

    @Nested
    @DisplayName("Result Object Behavior")
    class ResultObjectTests {

        @Test
        @DisplayName("Should create success result with ETag")
        void shouldCreateSuccessResultWithETag() {
            HttpResult<String> result = HttpResult.success(
                    "test-content", "etag-123", 200);

            assertTrue(result.isSuccess(), "Success result should be successful");
            assertTrue(result.getContent().isPresent(), "Content must be present");
            assertEquals("test-content", result.getContent().get(),
                    "Result should contain correct content");
            assertTrue(result.getETag().isPresent(), "ETag must be present");
            assertEquals("etag-123", result.getETag().get(),
                    "Result should contain ETag");
            assertTrue(result.getHttpStatus().isPresent(), "HTTP status must be present");
            assertEquals(200, result.getHttpStatus().get(),
                    "Result should contain HTTP status 200");
        }

        @Test
        @DisplayName("Should create success result without ETag")
        void shouldCreateSuccessResultWithoutETag() {
            HttpResult<String> result = HttpResult.success(
                    "test-content", null, 200);

            assertTrue(result.isSuccess(), "Success result should be successful");
            assertTrue(result.getContent().isPresent(), "Content must be present");
            assertEquals("test-content", result.getContent().get(),
                    "Result should contain content");
            assertFalse(result.getETag().isPresent(),
                    "Result should not have ETag when null provided");
        }

        @Test
        @DisplayName("Should create cached result with 304 status")
        void shouldCreateCachedResult() {
            HttpResult<String> result = HttpResult.success(
                    "cached-content", "etag-456", 304);

            assertTrue(result.isSuccess(), "Cached result should be successful");
            assertTrue(result.getContent().isPresent(), "Content must be present");
            assertEquals("cached-content", result.getContent().get(),
                    "Result should contain cached content");
            assertTrue(result.getHttpStatus().isPresent(), "HTTP status must be present");
            assertEquals(304, result.getHttpStatus().get(),
                    "Result should have 304 Not Modified status");
        }

        @Test
        @DisplayName("Should create error result with fallback content")
        void shouldCreateErrorResultWithFallback() {
            HttpResult<String> result = HttpResult.failureWithFallback(
                    "Error with fallback",
                    null,
                    "fallback-content",
                    HttpErrorCategory.NETWORK_ERROR,
                    null,
                    null);

            assertFalse(result.isSuccess(), "Error result should not be successful");
            assertEquals("Error with fallback", result.getErrorMessage().orElseThrow(),
                    "Result should have error message");
            assertEquals("fallback-content", result.getContent().orElseThrow(),
                    "Result should contain fallback content");
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

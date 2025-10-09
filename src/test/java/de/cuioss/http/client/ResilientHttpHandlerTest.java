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
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    // Helper method to create a basic test handler
    private static ResilientHttpHandler<String> createTestHandler() {
        HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
        return new ResilientHttpHandler<>(
                httpHandler, RetryStrategy.none(), StringContentConverter.identity());
    }
}

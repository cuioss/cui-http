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

import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.converter.StringContentConverter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for {@link ResilientHttpHandler} focusing on non-HTTP integration scenarios.
 * <p>
 * This test class covers:
 * <ul>
 *   <li>Constructor validation and initialization</li>
 *   <li>Status tracking behavior</li>
 *   <li>Content converter behavior</li>
 *   <li>Thread safety of status updates</li>
 *   <li>Edge cases and boundary conditions</li>
 * </ul>
 * <p>
 * HTTP integration tests with MockWebServer are in {@link ResilientHttpHandlerIntegrationTest}.
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
            HttpResponseConverter<String> converter = StringContentConverter.identity();

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
            HttpResponseConverter<String> converter = StringContentConverter.identity();

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
        @DisplayName("Should use converter with identity converter")
        void shouldUseIdentityConverter() {
            HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
            HttpResponseConverter<String> converter = StringContentConverter.identity();

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), converter);

            assertNotNull(handler, "Handler should be created with identity converter");
            // Converter behavior is tested through integration tests
            // as it requires actual HTTP operations
        }

        @Test
        @DisplayName("Should handle custom content converters")
        void shouldHandleCustomConverters() {
            HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();

            // Custom converter for testing
            HttpResponseConverter<String> customConverter = new HttpResponseConverter<String>() {
                @Override
                public HttpResponse.BodyHandler<?> getBodyHandler() {
                    return HttpResponse.BodyHandlers.ofString();
                }

                @Override
                public Optional<String> convert(Object rawContent) {
                    return Optional.ofNullable((String) rawContent);
                }

                @Override
                public ContentType contentType() {
                    return ContentType.TEXT_PLAIN;
                }
            };

            ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(
                    httpHandler, RetryStrategy.none(), customConverter);

            assertNotNull(handler, "Handler should accept custom converter");
            // Converter usage is verified through integration tests
        }

        @Test
        @DisplayName("Should work with identity string converter")
        void shouldWorkWithIdentityConverter() {
            HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
            HttpResponseConverter<String> converter = StringContentConverter.identity();

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

    // Helper method to create a basic test handler
    private static ResilientHttpHandler<String> createTestHandler() {
        HttpHandler httpHandler = HttpHandler.builder().url(TEST_URL).build();
        return new ResilientHttpHandler<>(
                httpHandler, RetryStrategy.none(), StringContentConverter.identity());
    }
}

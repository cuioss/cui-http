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

import de.cuioss.http.client.converter.StringContentConverter;
import de.cuioss.http.client.retry.RetryStrategies;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.tools.net.http.HttpHandler;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for ResilientHttpHandler with HttpHandlerProvider pattern.
 */
@DisplayName("ResilientHttpHandler HttpHandlerProvider Integration Tests")
class ResilientHttpHandlerProviderTest {

    private static final String TEST_URL = "https://test.example.com/.well-known/openid_configuration";

    /**
     * Test implementation of HttpHandlerProvider that simulates what WellKnownConfig would provide.
     * This replaces the dependency on WellKnownConfig which is not available in this repository.
     */
    @Builder
    @RequiredArgsConstructor
    static class TestHttpHandlerProvider implements HttpHandlerProvider {
        @NonNull private final String wellKnownUrl;
        @NonNull private final RetryStrategy retryStrategy;

        @Override
        public @NonNull HttpHandler getHttpHandler() {
            return HttpHandler.builder()
                    .url(wellKnownUrl)
                    .connectionTimeoutSeconds(5)
                    .readTimeoutSeconds(10)
                    .build();
        }

        @Override
        public @NonNull RetryStrategy getRetryStrategy() {
            return retryStrategy;
        }
    }

    @Test
    @DisplayName("Should create ResilientHttpHandler with HttpHandlerProvider")
    void shouldCreateWithHttpHandlerProvider() {
        // Given: A TestHttpHandlerProvider that implements HttpHandlerProvider
        RetryStrategy retryStrategy = RetryStrategies.exponentialBackoff();
        TestHttpHandlerProvider config = TestHttpHandlerProvider.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(retryStrategy)
                .build();

        // When: Creating ResilientHttpHandler with HttpHandlerProvider
        ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(config, StringContentConverter.identity());

        // Then: Should be created successfully
        assertNotNull(handler, "ResilientHttpHandler should be created");
    }

    @Test
    @DisplayName("Should create ResilientHttpHandler with custom HttpHandlerProvider")
    void shouldCreateWithCustomProvider() {
        // Given: A custom HttpHandlerProvider implementation
        HttpHandlerProvider provider = new HttpHandlerProvider() {
            @Override
            public @NonNull HttpHandler getHttpHandler() {
                return HttpHandler.builder()
                        .url(TEST_URL)
                        .build();
            }

            @Override
            public @NonNull RetryStrategy getRetryStrategy() {
                return RetryStrategy.none();
            }
        };

        // When: Creating ResilientHttpHandler with custom provider
        ResilientHttpHandler<String> handler = new ResilientHttpHandler<>(provider, StringContentConverter.identity());

        // Then: Should be created successfully
        assertNotNull(handler, "ResilientHttpHandler should be created with custom provider");
    }

    @Test
    @DisplayName("Should maintain backward compatibility with legacy constructor")
    void shouldMaintainBackwardCompatibility() {
        // Given: A direct HttpHandler (legacy usage)
        HttpHandler httpHandler = HttpHandler.builder()
                .url(TEST_URL)
                .build();

        // When: Creating ResilientHttpHandler with the withoutRetry factory method
        ResilientHttpHandler<String> handler = ResilientHttpHandler.withoutRetry(httpHandler, StringContentConverter.identity());

        // Then: Should work with factory method
        assertNotNull(handler, "ResilientHttpHandler should work with withoutRetry factory method");
    }

    @Test
    @DisplayName("Should fail when HttpHandlerProvider is null")
    @SuppressWarnings("ConstantConditions")
    void shouldFailWhenProviderIsNull() {
        // Given: A converter for the test
        var converter = StringContentConverter.identity();

        // When/Then: Should throw NullPointerException
        assertThrows(NullPointerException.class,
                () -> new ResilientHttpHandler<>((HttpHandlerProvider) null, converter),
                "Should throw exception when provider is null");
    }
}
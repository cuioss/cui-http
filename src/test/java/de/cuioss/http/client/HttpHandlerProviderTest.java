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

import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.tools.net.http.HttpHandler;
import lombok.Builder;
import lombok.NonNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the HttpHandlerProvider interface and its implementations.
 * Uses test implementations to replace the original WellKnownConfig and HttpJwksLoaderConfig
 * that were moved to a different project.
 */
class HttpHandlerProviderTest {

    private static final String TEST_URL = "https://test.example.com/.well-known/openid_configuration";

    /**
     * Test implementation to replace WellKnownConfig.
     * Simulates a configuration for well-known endpoints.
     */
    @Builder
    static class TestWellKnownConfig implements HttpHandlerProvider {
        @NonNull private final String wellKnownUrl;
        @Builder.Default private final RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

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

    /**
     * Test implementation to replace HttpJwksLoaderConfig.
     * Simulates a configuration for JWKS loading with both direct and well-known modes.
     */
    @Builder
    static class TestHttpJwksLoaderConfig implements HttpHandlerProvider {
        private final String wellKnownUrl;
        private final String jwksUrl;
        private final String issuerIdentifier;
        @Builder.Default private final RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        @Override
        public @NonNull HttpHandler getHttpHandler() {
            String url = jwksUrl != null ? jwksUrl : wellKnownUrl;
            return HttpHandler.builder()
                    .url(url)
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
    void wellKnownConfig_shouldImplementHttpHandlerProvider() {
        // Given: A TestWellKnownConfig with RetryStrategy
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        TestWellKnownConfig config = TestWellKnownConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(retryStrategy)
                .build();

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null");
        assertNotNull(config.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, config.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void wellKnownConfig_withoutRetryStrategy_shouldUseDefault() {
        // Given: A builder without explicit RetryStrategy
        TestWellKnownConfig config = TestWellKnownConfig.builder()
                .wellKnownUrl(TEST_URL)
                .build();

        // Then: Should provide default RetryStrategy (exponentialBackoff)
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null");
        assertNotNull(config.getRetryStrategy(), "Should provide default RetryStrategy");
        // The default is exponentialBackoff, so it should not be the no-op strategy
        assertNotSame(RetryStrategy.none(), config.getRetryStrategy(), "Default should not be no-op strategy");
    }

    @Test
    void wellKnownConfig_withNoOpRetryStrategy_shouldWork() {
        // Given: A TestWellKnownConfig with no-op retry strategy
        RetryStrategy noOpStrategy = RetryStrategy.none();

        TestWellKnownConfig config = TestWellKnownConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(noOpStrategy)
                .build();

        // Then: Should work correctly
        assertNotNull(config.getHttpHandler());
        assertSame(noOpStrategy, config.getRetryStrategy());
    }

    @Test
    void httpJwksLoaderConfig_directMode_shouldImplementHttpHandlerProvider() {
        // Given: TestHttpJwksLoaderConfig in direct HTTP mode
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        TestHttpJwksLoaderConfig config = TestHttpJwksLoaderConfig.builder()
                .jwksUrl(TEST_URL.replace("/.well-known/openid_configuration", "/jwks"))
                .issuerIdentifier("test-issuer")
                .retryStrategy(retryStrategy)
                .build();

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null in direct mode");
        assertNotNull(config.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, config.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void httpJwksLoaderConfig_wellKnownMode_shouldImplementHttpHandlerProvider() {
        // Given: TestHttpJwksLoaderConfig in well-known discovery mode
        RetryStrategy retryStrategy = RetryStrategy.exponentialBackoff();

        TestHttpJwksLoaderConfig config = TestHttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_URL)
                .retryStrategy(retryStrategy)
                .build();

        // Then: Should provide both HttpHandler and RetryStrategy
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null in well-known mode");
        assertNotNull(config.getRetryStrategy(), "RetryStrategy should not be null");
        assertSame(retryStrategy, config.getRetryStrategy(), "Should return the same RetryStrategy instance");
    }

    @Test
    void httpJwksLoaderConfig_withoutRetryStrategy_shouldUseDefault() {
        // Given: TestHttpJwksLoaderConfig without explicit RetryStrategy
        TestHttpJwksLoaderConfig config = TestHttpJwksLoaderConfig.builder()
                .jwksUrl(TEST_URL.replace("/.well-known/openid_configuration", "/jwks"))
                .issuerIdentifier("test-issuer")
                .build();

        // Then: Should provide default RetryStrategy
        assertNotNull(config.getHttpHandler(), "HttpHandler should not be null");
        assertNotNull(config.getRetryStrategy(), "Should provide default RetryStrategy");
    }
}
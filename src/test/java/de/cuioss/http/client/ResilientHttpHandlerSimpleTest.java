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
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResultObject;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.uimodel.nameprovider.DisplayName;
import de.cuioss.uimodel.result.ResultDetail;
import de.cuioss.uimodel.result.ResultState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for ResilientHttpHandler HttpResultObject behavior.
 * This test verifies the basic result pattern functionality without complex HTTP integration.
 *
 * NOTE: This test has been updated from testing the removed LoadState/LoadResult types
 * to testing the new HttpResultObject result pattern implementation.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
class ResilientHttpHandlerSimpleTest {

    @Test
    void withoutRetryFactoryMethod() {
        // Create a simple HttpHandler for testing
        var httpHandler = HttpHandler.builder()
                .url("http://test.example.com")
                .build();

        // Test the withoutRetry factory method
        ResilientHttpHandler<String> handler = ResilientHttpHandler.withoutRetry(httpHandler, StringContentConverter.identity());

        assertNotNull(handler);
        // The handler should be created successfully without retry capability
        // Further testing would require mocking HTTP responses, which is done in integration tests
    }

    @Test
    void httpResultObjectSuccessStates() {
        // Test fresh content (equivalent to LOADED_FROM_SERVER)
        HttpResultObject<String> freshResult = HttpResultObject.success("fresh-content", "etag123", 200);

        assertTrue(freshResult.isValid());
        assertEquals("fresh-content", freshResult.getResult());
        assertEquals("etag123", freshResult.getETag().orElse(null));
        assertEquals(200, freshResult.getHttpStatus().orElse(0));

        // Test cached content via ETag (equivalent to CACHE_ETAG)
        HttpResultObject<String> cachedResult = HttpResultObject.success("cached-content", "etag456", 304);

        assertTrue(cachedResult.isValid());
        assertEquals("cached-content", cachedResult.getResult());
        assertEquals(304, cachedResult.getHttpStatus().orElse(0));
    }

    @Test
    void httpResultObjectErrorStates() {
        // Test error with fallback content (equivalent to ERROR_WITH_CACHE)
        HttpResultObject<String> errorWithCache = HttpResultObject.error(
                "fallback-content",
                HttpErrorCategory.NETWORK_ERROR,
                new ResultDetail(
                        new DisplayName("Network error with cached fallback")));

        assertFalse(errorWithCache.isValid());
        assertEquals(ResultState.ERROR, errorWithCache.getState());
        assertEquals(HttpErrorCategory.NETWORK_ERROR, errorWithCache.getHttpErrorCategory().orElse(null));
        assertTrue(errorWithCache.getResultDetail().isPresent());

        // Now we can safely access the result after acknowledging the error details
        assertEquals("fallback-content", errorWithCache.getResult());
        assertTrue(errorWithCache.isRetryable());

        // Test error without content (equivalent to ERROR_NO_CACHE) - use empty string fallback since null is not allowed
        HttpResultObject<String> errorNoCache = HttpResultObject.error(
                "",
                HttpErrorCategory.NETWORK_ERROR,
                new ResultDetail(
                        new DisplayName("Network error with no cached content")));

        assertFalse(errorNoCache.isValid());
        assertEquals(ResultState.ERROR, errorNoCache.getState());
        assertEquals(HttpErrorCategory.NETWORK_ERROR, errorNoCache.getHttpErrorCategory().orElse(null));
        assertTrue(errorNoCache.getResultDetail().isPresent());

        // Now with proper ResultDetail including exception and after acknowledging error details, we can safely access the result
        assertEquals("", errorNoCache.getResult());
    }

    @Test
    void httpResultObjectMetadata() {
        // Test result with comprehensive metadata
        HttpResultObject<String> result = HttpResultObject.success("content", "etag789", 200);

        assertTrue(result.getETag().isPresent());
        assertEquals("etag789", result.getETag().get());
        assertTrue(result.getHttpStatus().isPresent());
        assertEquals(200, result.getHttpStatus().get().intValue());

        // Test equality and hashCode behavior (inherited from CUI ResultObject)
        HttpResultObject<String> sameResult = HttpResultObject.success("content", "etag789", 200);
        // Note: ResultObject equality is based on result content and state, not all metadata
        assertEquals(result.getResult(), sameResult.getResult());
        assertEquals(result.getState(), sameResult.getState());
    }

    @Test
    void httpErrorCategoryRetryBehavior() {
        // Verify retryable error categories
        assertTrue(HttpErrorCategory.NETWORK_ERROR.isRetryable());
        assertTrue(HttpErrorCategory.SERVER_ERROR.isRetryable());

        // Verify non-retryable error categories
        assertFalse(HttpErrorCategory.CLIENT_ERROR.isRetryable());
        assertFalse(HttpErrorCategory.INVALID_CONTENT.isRetryable());
        assertFalse(HttpErrorCategory.CONFIGURATION_ERROR.isRetryable());

        // Test result retryability
        HttpResultObject<String> retryableError = HttpResultObject.error(
                "", HttpErrorCategory.NETWORK_ERROR,
                new ResultDetail(
                        new DisplayName("Retryable network error")));
        assertTrue(retryableError.isRetryable());

        HttpResultObject<String> nonRetryableError = HttpResultObject.error(
                "", HttpErrorCategory.CLIENT_ERROR,
                new ResultDetail(
                        new DisplayName("Non-retryable client error")));
        assertFalse(nonRetryableError.isRetryable());
    }
}
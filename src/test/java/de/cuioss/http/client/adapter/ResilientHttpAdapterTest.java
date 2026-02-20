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

import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ResilientHttpAdapter}.
 */
class ResilientHttpAdapterTest {

    /**
     * Test retry on NETWORK_ERROR.
     */
    @Test
    void retryOnNetworkError() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails twice with NETWORK_ERROR, then succeeds
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 2,
                HttpResult.failure("Network timeout", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success after retry", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .multiplier(2.0)
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.getBlocking();

        // Should succeed on third attempt
        assertTrue(result.isSuccess());
        assertEquals("Success after retry", result.getContent().orElse(null));
        assertEquals(3, attemptCount.get());
    }

    /**
     * Test retry on SERVER_ERROR (5xx).
     */
    @Test
    void retryOnServerError() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails once with SERVER_ERROR, then succeeds
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Server error 503", null, HttpErrorCategory.SERVER_ERROR),
                HttpResult.success("Success after retry", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(10))
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.getBlocking();

        // Should succeed on second attempt
        assertTrue(result.isSuccess());
        assertEquals("Success after retry", result.getContent().orElse(null));
        assertEquals(2, attemptCount.get());
    }

    /**
     * Test no retry on CLIENT_ERROR (4xx).
     */
    @Test
    void noRetryOnClientError() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails with CLIENT_ERROR (should NOT retry)
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Not found 404", null, HttpErrorCategory.CLIENT_ERROR),
                HttpResult.success("Should not reach", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.getBlocking();

        // Should NOT retry CLIENT_ERROR
        assertFalse(result.isSuccess());
        assertEquals(HttpErrorCategory.CLIENT_ERROR, result.getErrorCategory().orElse(null));
        assertEquals(1, attemptCount.get());
    }

    /**
     * Test max attempts respected.
     */
    @Test
    void maxAttemptsRespected() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that always fails with NETWORK_ERROR
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 10,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                null); // Never succeeds

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.getBlocking();

        // Should try exactly 3 times
        assertFalse(result.isSuccess());
        assertEquals(3, attemptCount.get());
    }

    /**
     * Test idempotentOnly=true skips POST retry.
     */
    @Test
    void idempotentOnlySkipsPost() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails with NETWORK_ERROR
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Should not reach", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .idempotentOnly(true) // Default, POST should NOT be retried
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.postBlocking("request body");

        // POST should NOT be retried (non-idempotent)
        assertFalse(result.isSuccess());
        assertEquals(1, attemptCount.get());
    }

    /**
     * Test idempotentOnly=true skips PATCH retry.
     */
    @Test
    void idempotentOnlySkipsPatch() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails with NETWORK_ERROR
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Should not reach", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .idempotentOnly(true)
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.patchBlocking("request body");

        // PATCH should NOT be retried (non-idempotent)
        assertFalse(result.isSuccess());
        assertEquals(1, attemptCount.get());
    }

    /**
     * Test idempotentOnly=false retries POST.
     */
    @Test
    void idempotentOnlyFalseRetriesPost() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails once, then succeeds
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success after retry", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .idempotentOnly(false) // Explicitly enable POST retry
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.postBlocking("request body");

        // POST SHOULD be retried when idempotentOnly=false
        assertTrue(result.isSuccess());
        assertEquals(2, attemptCount.get());
    }

    /**
     * Test idempotentOnly=false retries PATCH.
     */
    @Test
    void idempotentOnlyFalseRetriesPatch() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails once, then succeeds
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success after retry", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .idempotentOnly(false)
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.patchBlocking("request body");

        // PATCH SHOULD be retried when idempotentOnly=false
        assertTrue(result.isSuccess());
        assertEquals(2, attemptCount.get());
    }

    /**
     * Test async delays are non-blocking.
     */
    @Test
    void asyncDelaysNonBlocking() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails once with NETWORK_ERROR
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(50)) // 50ms delay
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        long startTime = System.currentTimeMillis();
        HttpResult<String> result = resilient.getBlocking();
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Should succeed after delay
        assertTrue(result.isSuccess());
        // Should take at least 40ms due to delay (allowing for CI timing variations)
        assertTrue(elapsedTime >= 40, "Expected delay of at least 40ms, but took " + elapsedTime + "ms");
    }

    /**
     * Test success on first attempt (no retry).
     */
    @Test
    void successOnFirstAttempt() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that succeeds immediately
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 0,
                null,
                HttpResult.success("Success", null, 200));

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter);

        HttpResult<String> result = resilient.getBlocking();

        // Should succeed on first attempt
        assertTrue(result.isSuccess());
        assertEquals("Success", result.getContent().orElse(null));
        assertEquals(1, attemptCount.get());
    }

    /**
     * Test success on retry attempt.
     */
    @Test
    void successOnRetryAttempt() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails twice, succeeds on third
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 2,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success on retry", null, 200));

        RetryConfig config = RetryConfig.builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(10))
                .build();

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        HttpResult<String> result = resilient.getBlocking();

        // Should succeed on third attempt
        assertTrue(result.isSuccess());
        assertEquals("Success on retry", result.getContent().orElse(null));
        assertEquals(3, attemptCount.get());
    }

    /**
     * Test no retry on INVALID_CONTENT error.
     */
    @Test
    void noRetryOnInvalidContent() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails with INVALID_CONTENT (non-retryable)
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Invalid JSON", null, HttpErrorCategory.INVALID_CONTENT),
                null);

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter);

        HttpResult<String> result = resilient.getBlocking();

        // Should NOT retry INVALID_CONTENT
        assertFalse(result.isSuccess());
        assertEquals(HttpErrorCategory.INVALID_CONTENT, result.getErrorCategory().orElse(null));
        assertEquals(1, attemptCount.get());
    }

    /**
     * Test no retry on CONFIGURATION_ERROR.
     */
    @Test
    void noRetryOnConfigurationError() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Mock adapter that fails with CONFIGURATION_ERROR (non-retryable)
        HttpAdapter<String> mockAdapter = new MockAdapter<>(attemptCount, 1,
                HttpResult.failure("Invalid URL", null, HttpErrorCategory.CONFIGURATION_ERROR),
                null);

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter);

        HttpResult<String> result = resilient.getBlocking();

        // Should NOT retry CONFIGURATION_ERROR
        assertFalse(result.isSuccess());
        assertEquals(HttpErrorCategory.CONFIGURATION_ERROR, result.getErrorCategory().orElse(null));
        assertEquals(1, attemptCount.get());
    }

    /**
     * Test wrap factory method with defaults.
     */
    @Test
    void wrapFactoryWithDefaults() {
        HttpAdapter<String> mockAdapter = new MockAdapter<>(new AtomicInteger(0), 0,
                null, HttpResult.success("Success", null, 200));

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter);

        assertNotNull(resilient);
        assertInstanceOf(ResilientHttpAdapter.class, resilient);
    }

    /**
     * Test wrap factory method with custom config.
     */
    @Test
    void wrapFactoryWithCustomConfig() {
        RetryConfig config = RetryConfig.builder()
                .maxAttempts(2)
                .build();

        HttpAdapter<String> mockAdapter = new MockAdapter<>(new AtomicInteger(0), 0,
                null, HttpResult.success("Success", null, 200));

        HttpAdapter<String> resilient = ResilientHttpAdapter.wrap(mockAdapter, config);

        assertNotNull(resilient);
        assertInstanceOf(ResilientHttpAdapter.class, resilient);
    }

    /**
     * Test constructor validation (null delegate).
     */
    @Test
    void constructorNullDelegate() {
        RetryConfig config = RetryConfig.defaults();

        assertThrows(NullPointerException.class, () -> {
            new ResilientHttpAdapter<>(null, config);
        });
    }

    /**
     * Test constructor validation (null config).
     */
    @Test
    void constructorNullConfig() {
        HttpAdapter<String> mockAdapter = new MockAdapter<>(new AtomicInteger(0), 0,
                null, HttpResult.success("Success", null, 200));

        assertThrows(NullPointerException.class, () -> {
            new ResilientHttpAdapter<>(mockAdapter, null);
        });
    }

    /**
     * Test idempotent methods are retried by default (GET, PUT, DELETE, HEAD, OPTIONS).
     */
    @Test
    void idempotentMethodsRetriedByDefault() {
        // Test GET
        AtomicInteger getAttempts = new AtomicInteger(0);
        HttpAdapter<String> getMock = new MockAdapter<>(getAttempts, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success", null, 200));
        HttpAdapter<String> getResilient = ResilientHttpAdapter.wrap(getMock);
        assertTrue(getResilient.getBlocking().isSuccess());
        assertEquals(2, getAttempts.get());

        // Test PUT
        AtomicInteger putAttempts = new AtomicInteger(0);
        HttpAdapter<String> putMock = new MockAdapter<>(putAttempts, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success", null, 200));
        HttpAdapter<String> putResilient = ResilientHttpAdapter.wrap(putMock);
        assertTrue(putResilient.putBlocking("body").isSuccess());
        assertEquals(2, putAttempts.get());

        // Test DELETE
        AtomicInteger deleteAttempts = new AtomicInteger(0);
        HttpAdapter<String> deleteMock = new MockAdapter<>(deleteAttempts, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success", null, 200));
        HttpAdapter<String> deleteResilient = ResilientHttpAdapter.wrap(deleteMock);
        assertTrue(deleteResilient.deleteBlocking().isSuccess());
        assertEquals(2, deleteAttempts.get());

        // Test HEAD
        AtomicInteger headAttempts = new AtomicInteger(0);
        HttpAdapter<String> headMock = new MockAdapter<>(headAttempts, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success", null, 200));
        HttpAdapter<String> headResilient = ResilientHttpAdapter.wrap(headMock);
        assertTrue(headResilient.headBlocking().isSuccess());
        assertEquals(2, headAttempts.get());

        // Test OPTIONS
        AtomicInteger optionsAttempts = new AtomicInteger(0);
        HttpAdapter<String> optionsMock = new MockAdapter<>(optionsAttempts, 1,
                HttpResult.failure("Network error", null, HttpErrorCategory.NETWORK_ERROR),
                HttpResult.success("Success", null, 200));
        HttpAdapter<String> optionsResilient = ResilientHttpAdapter.wrap(optionsMock);
        assertTrue(optionsResilient.optionsBlocking().isSuccess());
        assertEquals(2, optionsAttempts.get());
    }

    /**
     * Mock adapter for testing.
     */
    private static class MockAdapter<T> implements HttpAdapter<T> {
        private final AtomicInteger attemptCount;
        private final int failuresBeforeSuccess;
        private final HttpResult<T> failureResult;
        private final HttpResult<T> successResult;

        MockAdapter(AtomicInteger attemptCount, int failuresBeforeSuccess,
                HttpResult<T> failureResult, HttpResult<T> successResult) {
            this.attemptCount = attemptCount;
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.failureResult = failureResult;
            this.successResult = successResult;
        }

        private CompletableFuture<HttpResult<T>> executeRequest() {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= failuresBeforeSuccess) {
                return CompletableFuture.completedFuture(failureResult);
            }
            return CompletableFuture.completedFuture(successResult);
        }

        @Override
        public CompletableFuture<HttpResult<T>> get(Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public CompletableFuture<HttpResult<T>> post(T requestBody, Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public CompletableFuture<HttpResult<T>> put(T requestBody, Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public CompletableFuture<HttpResult<T>> patch(T requestBody, Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public CompletableFuture<HttpResult<T>> delete(Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public CompletableFuture<HttpResult<T>> delete(T requestBody, Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public CompletableFuture<HttpResult<T>> head(Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public CompletableFuture<HttpResult<T>> options(Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public <R> CompletableFuture<HttpResult<T>> post(HttpRequestConverter<R> requestConverter, R requestBody, Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public <R> CompletableFuture<HttpResult<T>> put(HttpRequestConverter<R> requestConverter, R requestBody, Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public <R> CompletableFuture<HttpResult<T>> patch(HttpRequestConverter<R> requestConverter, R requestBody, Map<String, String> additionalHeaders) {
            return executeRequest();
        }

        @Override
        public <R> CompletableFuture<HttpResult<T>> delete(HttpRequestConverter<R> requestConverter, R requestBody, Map<String, String> additionalHeaders) {
            return executeRequest();
        }
    }
}

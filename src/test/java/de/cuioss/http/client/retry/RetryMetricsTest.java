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
package de.cuioss.http.client.retry;

import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResultObject;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.uimodel.result.ResultDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
class RetryMetricsTest {

    @Test
    @DisplayName("No-op implementation should handle all calls safely")
    void noOpImplementationShouldHandleAllCallsWithoutErrors() {
        RetryMetrics metrics = RetryMetrics.noOp();
        RetryContext context = RetryContext.initial("test-op");


        // All these calls should succeed without throwing exceptions
        assertDoesNotThrow(() -> {
            metrics.recordRetryStart(context);
            metrics.recordRetryAttempt(context, 1, Duration.ofMillis(100), false);
            metrics.recordRetryDelay(context, 2, Duration.ofMillis(500), Duration.ofMillis(510));
            metrics.recordRetryComplete(context, Duration.ofMillis(1000), false, 3);
        });
    }

    @Test
    @DisplayName("Should integrate with ExponentialBackoffRetryStrategy")
    void shouldIntegrateWithExponentialBackoffRetryStrategy() {
        MockRetryMetrics mockMetrics = new MockRetryMetrics();

        ExponentialBackoffRetryStrategy strategy = ExponentialBackoffRetryStrategy.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(10))
                .retryMetrics(mockMetrics)
                .build();

        RetryContext context = RetryContext.initial("test-integration");

        AtomicInteger attempts = new AtomicInteger(0);

        HttpResultObject<String> result = strategy.execute(() -> {
            attempts.incrementAndGet();
            return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                    new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("Connection failed"),
                            new ConnectException("Connection failed")));
        }, context);

        // Verify the result indicates failure
        assertFalse(result.isValid(), "Result should be invalid when all retry attempts fail");

        // Verify metrics were recorded
        assertEquals(1, mockMetrics.retryStartCount, "Should record one retry operation start");
        assertEquals(1, mockMetrics.retryCompleteCount, "Should record one retry operation completion");
        assertEquals(2, mockMetrics.retryAttemptCount, "Should record 2 failed attempts");
        assertEquals(1, mockMetrics.retryDelayCount, "Should record 1 delay between attempts");
        assertFalse(mockMetrics.lastSuccessful, "Final retry operation should be marked as failed");
        assertEquals(2, mockMetrics.lastTotalAttempts, "Should record total of 2 attempts made");
    }

    @Test
    @DisplayName("Should record successful retry metrics")
    void shouldRecordSuccessfulRetryMetrics() {
        MockRetryMetrics mockMetrics = new MockRetryMetrics();

        ExponentialBackoffRetryStrategy strategy = ExponentialBackoffRetryStrategy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .retryMetrics(mockMetrics)
                .build();

        RetryContext context = RetryContext.initial("test-success");

        AtomicInteger attempts = new AtomicInteger(0);

        HttpResultObject<String> result = strategy.execute(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 2) {
                return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                        new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("Connection failed"),
                                new ConnectException("Connection failed")));
            }
            return HttpResultObject.success("success", null, 200);
        }, context);

        assertTrue(result.isValid(), "Retry strategy should return valid result");
        assertEquals("success", result.getResult(), "Retry strategy should return successful result");

        // Verify metrics were recorded
        assertEquals(1, mockMetrics.retryStartCount, "Should record one retry operation start");
        assertEquals(1, mockMetrics.retryCompleteCount, "Should record one retry operation completion");
        assertEquals(2, mockMetrics.retryAttemptCount, "Should record 2 attempts (1 failed, 1 successful)");
        assertEquals(1, mockMetrics.retryDelayCount, "Should record 1 delay between attempts");
        assertTrue(mockMetrics.lastSuccessful, "Final retry operation should be marked as successful");
        assertEquals(2, mockMetrics.lastTotalAttempts, "Should record total of 2 attempts made");
    }

    /**
     * Mock implementation for testing retry metrics integration.
     */
    private static class MockRetryMetrics implements RetryMetrics {
        int retryStartCount = 0;
        int retryCompleteCount = 0;
        int retryAttemptCount = 0;
        int retryDelayCount = 0;
        boolean lastSuccessful = false;
        int lastTotalAttempts = 0;

        @Override
        public void recordRetryStart(RetryContext context) {
            retryStartCount++;
        }

        @Override
        public void recordRetryComplete(RetryContext context, Duration totalDuration, boolean successful, int totalAttempts) {
            retryCompleteCount++;
            lastSuccessful = successful;
            lastTotalAttempts = totalAttempts;
        }

        @Override
        public void recordRetryAttempt(RetryContext context, int attemptNumber, Duration attemptDuration,
                boolean successful) {
            retryAttemptCount++;
        }

        @Override
        public void recordRetryDelay(RetryContext context, int attemptNumber, Duration plannedDelay, Duration actualDelay) {
            retryDelayCount++;
        }
    }
}
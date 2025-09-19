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

import de.cuioss.http.client.HttpLogMessages;
import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.http.client.result.HttpResultObject;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.uimodel.result.ResultDetail;
import de.cuioss.uimodel.result.ResultState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
class ExponentialBackoffRetryStrategyTest {

    private ExponentialBackoffRetryStrategy strategy;
    private RetryContext context;

    @BeforeEach
    void setUp() {
        strategy = ExponentialBackoffRetryStrategy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .maxDelay(Duration.ofMillis(100))
                .backoffMultiplier(2.0)
                .jitterFactor(0.0) // Disable jitter for predictable testing
                .build();

        context = RetryContext.initial("test-operation");
    }

    @Nested
    @DisplayName("Successful operations")
    class SuccessfulOperations {

        @Test
        @DisplayName("Should return result on first attempt success")
        void shouldReturnResultOnFirstAttemptSuccess() {
            HttpOperation<String> operation = () -> HttpResultObject.success("success", null, 200);

            HttpResultObject<String> result = strategy.execute(operation, context);

            assertTrue(result.isValid(), "Result should be valid on first attempt success");
            assertEquals("success", result.getResult(), "Strategy should return operation result on first attempt success");
            assertEquals(200, result.getHttpStatus().orElse(0), "Should have success HTTP status");
        }

        @Test
        @DisplayName("Should return result after retries succeed")
        void shouldReturnResultAfterRetriesSucceed() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                            new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("Connection failed on attempt " + attempt),
                                    new ConnectException("Connection failed on attempt " + attempt)));
                }
                return HttpResultObject.success("success-on-attempt-" + attempt, null, 200);
            };

            HttpResultObject<String> result = strategy.execute(operation, context);

            assertTrue(result.isValid(), "Result should be valid after successful retry");
            assertEquals("success-on-attempt-3", result.getResult(), "Strategy should return result from successful retry attempt");
            assertEquals(ResultState.VALID, result.getState(), "Should indicate successful recovery");
            assertEquals(3, attempts.get(), "Strategy should have made exactly 3 attempts before success");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                    HttpLogMessages.INFO.RETRY_OPERATION_SUCCEEDED_AFTER_ATTEMPTS.resolveIdentifierString());
        }
    }

    @Nested
    @DisplayName("Retryable exceptions")
    class RetryableExceptions {

        @Test
        @DisplayName("Should retry ConnectException")
        void shouldRetryConnectException() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                        new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("Connection refused"),
                                new ConnectException("Connection refused")));
            };

            HttpResultObject<String> result = strategy.execute(operation, context);

            assertFalse(result.isValid(), "Result should be invalid when all retry attempts fail");
            assertEquals(ResultState.ERROR, result.getState());
            assertEquals(HttpErrorCategory.NETWORK_ERROR, result.getHttpErrorCategory().orElse(null));
            assertTrue(result.getResultDetail().isPresent(), "Error details should be present");
            assertEquals(3, attempts.get(), "Strategy should have exhausted all 3 retry attempts");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    HttpLogMessages.WARN.RETRY_MAX_ATTEMPTS_REACHED.resolveIdentifierString());
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    HttpLogMessages.WARN.RETRY_OPERATION_FAILED.resolveIdentifierString());
        }

        @Test
        @DisplayName("Should retry SocketTimeoutException")
        void shouldRetrySocketTimeoutException() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                        new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("Read timeout"),
                                new SocketTimeoutException("Read timeout")));
            };

            HttpResultObject<String> result = strategy.execute(operation, context);

            assertFalse(result.isValid(), "Result should be invalid when all retry attempts fail");
            assertEquals(ResultState.ERROR, result.getState());
            assertEquals(HttpErrorCategory.NETWORK_ERROR, result.getHttpErrorCategory().orElse(null));
            assertTrue(result.getResultDetail().isPresent(), "Error details should be present");
            assertEquals(3, attempts.get(), "Strategy should have exhausted all 3 retry attempts for timeout exception");
        }

        @Test
        @DisplayName("Should retry HttpConnectTimeoutException")
        void shouldRetryHttpConnectTimeoutException() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                        new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("HTTP timeout"),
                                new HttpConnectTimeoutException("HTTP timeout")));
            };

            HttpResultObject<String> result = strategy.execute(operation, context);

            assertFalse(result.isValid(), "Result should be invalid when all retry attempts fail");
            assertEquals(ResultState.ERROR, result.getState());
            assertEquals(HttpErrorCategory.NETWORK_ERROR, result.getHttpErrorCategory().orElse(null));
            assertTrue(result.getResultDetail().isPresent(), "Error details should be present");
            assertEquals(3, attempts.get(), "Strategy should have exhausted all 3 retry attempts for HTTP timeout");
        }

        @Test
        @DisplayName("Should retry general IOException")
        void shouldRetryGeneralIOException() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                        new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("General IO error"),
                                new IOException("General IO error")));
            };

            HttpResultObject<String> result = strategy.execute(operation, context);

            assertFalse(result.isValid(), "Result should be invalid when all retry attempts fail");
            assertEquals(ResultState.ERROR, result.getState());
            assertEquals(HttpErrorCategory.NETWORK_ERROR, result.getHttpErrorCategory().orElse(null));
            assertTrue(result.getResultDetail().isPresent(), "Error details should be present");
            assertEquals(3, attempts.get(), "Strategy should have exhausted all 3 retry attempts for IO exception");
        }
    }

    @Nested
    @DisplayName("Delay calculation")
    class DelayCalculation {

        @Test
        @DisplayName("Should respect maximum delay")
        void shouldRespectMaximumDelay() {
            ExponentialBackoffRetryStrategy longDelayStrategy = ExponentialBackoffRetryStrategy.builder()
                    .maxAttempts(5)
                    .initialDelay(Duration.ofMillis(1000))
                    .maxDelay(Duration.ofMillis(2000)) // Cap at 2 seconds
                    .backoffMultiplier(10.0) // Very aggressive multiplier
                    .jitterFactor(0.0)
                    .build();

            AtomicInteger attempts = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();

            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                        new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("Test exception"),
                                new ConnectException("Test exception")));
            };

            HttpResultObject<String> result = longDelayStrategy.execute(operation, context);

            assertFalse(result.isValid(), "Result should be invalid when max delay is exceeded");
            assertEquals(ResultState.ERROR, result.getState());
            assertTrue(result.getResultDetail().isPresent(), "Error details should be present");

            long totalTime = System.currentTimeMillis() - startTime;

            // With max delay of 2000ms and 4 retries (5 attempts total),
            // total time should be less than 4 * 2000 = 8000ms
            assertTrue(totalTime < 8000, "Total retry time should respect max delay constraint (< 8000ms), was: " + totalTime);
            assertEquals(5, attempts.get(), "Strategy should have made exactly 5 attempts as configured");
        }
    }

    @Nested
    @DisplayName("Configuration validation")
    class ConfigurationValidation {

        @Test
        @DisplayName("Should validate positive max attempts")
        void shouldValidatePositiveMaxAttempts() {
            var builder = ExponentialBackoffRetryStrategy.builder();
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.maxAttempts(0));

            assertEquals("maxAttempts must be positive, got: 0", exception.getMessage(), "Builder should validate maxAttempts parameter positivity");
        }

        @Test
        @DisplayName("Should validate non-negative initial delay")
        void shouldValidateNonNegativeInitialDelay() {
            var builder = ExponentialBackoffRetryStrategy.builder();
            var negativeDuration = Duration.ofMillis(-1);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.initialDelay(negativeDuration));

            assertEquals("initialDelay cannot be negative", exception.getMessage(), "Builder should validate initialDelay parameter non-negativity");
        }

        @Test
        @DisplayName("Should validate backoff multiplier >= 1.0")
        void shouldValidateBackoffMultiplier() {
            var builder = ExponentialBackoffRetryStrategy.builder();
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.backoffMultiplier(0.5));

            assertEquals("backoffMultiplier must be >= 1.0, got: 0.5", exception.getMessage(), "Builder should validate backoffMultiplier is >= 1.0");
        }

        @Test
        @DisplayName("Should validate jitter factor range")
        void shouldValidateJitterFactorRange() {
            var builder1 = ExponentialBackoffRetryStrategy.builder();
            IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class,
                    () -> builder1.jitterFactor(-0.1));

            assertEquals("jitterFactor must be between 0.0 and 1.0, got: -0.1", exception1.getMessage(), "Builder should validate jitterFactor lower bound");

            var builder2 = ExponentialBackoffRetryStrategy.builder();
            IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class,
                    () -> builder2.jitterFactor(1.1));

            assertEquals("jitterFactor must be between 0.0 and 1.0, got: 1.1", exception2.getMessage(), "Builder should validate jitterFactor upper bound");
        }

        @Test
        @DisplayName("Should validate required parameters are not null")
        void shouldValidateRequiredParametersNotNull() {
            var builder1 = ExponentialBackoffRetryStrategy.builder();
            assertThrows(NullPointerException.class,
                    () -> builder1.initialDelay(null));

            var builder2 = ExponentialBackoffRetryStrategy.builder();
            assertThrows(NullPointerException.class,
                    () -> builder2.maxDelay(null));
        }
    }


    @Nested
    @DisplayName("Thread interruption")
    class ThreadInterruption {

        @Test
        @DisplayName("Should handle thread interruption during retry delay")
        void shouldHandleThreadInterruptionDuringRetryDelay() {
            ExponentialBackoffRetryStrategy interruptableStrategy = ExponentialBackoffRetryStrategy.builder()
                    .maxAttempts(3)
                    .initialDelay(Duration.ofSeconds(10)) // Long delay to allow interruption
                    .build();

            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = createInterruptingOperation(attempts);

            HttpResultObject<String> result = interruptableStrategy.execute(operation, context);

            assertFalse(result.isValid(), "Result should be invalid when operation is interrupted");
            assertEquals(ResultState.ERROR, result.getState());
            assertEquals(HttpErrorCategory.NETWORK_ERROR, result.getHttpErrorCategory().orElse(null));
            assertTrue(result.getResultDetail().isPresent(), "Error details should be present");

            // Should preserve interrupt status
            assertTrue(Thread.interrupted(), "Thread interrupt status should be preserved after retry interruption");
        }

        private HttpOperation<String> createInterruptingOperation(AtomicInteger attempts) {
            return () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    // Interrupt the thread after the first failure
                    Thread.currentThread().interrupt();
                }
                return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                        new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("Test exception"),
                                new ConnectException("Test exception")));
            };
        }
    }

    @Nested
    @DisplayName("Static factory methods")
    class StaticFactoryMethods {

        @Test
        @DisplayName("Should create no-op strategy with none()")
        void shouldCreateNoOpStrategyWithNone() {
            RetryStrategy noRetryStrategy = RetryStrategy.none();
            AtomicInteger attempts = new AtomicInteger(0);

            HttpOperation<String> operation = () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    return HttpResultObject.error("", HttpErrorCategory.NETWORK_ERROR,
                            new ResultDetail(new de.cuioss.uimodel.nameprovider.DisplayName("Should not retry"),
                                    new ConnectException("Should not retry")));
                }
                return HttpResultObject.success("should-not-reach", null, 200);
            };

            HttpResultObject<String> result = noRetryStrategy.execute(operation, context);

            assertFalse(result.isValid(), "Result should be invalid when single attempt fails");
            assertEquals(ResultState.ERROR, result.getState());
            assertEquals(HttpErrorCategory.NETWORK_ERROR, result.getHttpErrorCategory().orElse(null));
            assertTrue(result.getResultDetail().isPresent(), "Error details should be present");
            assertEquals(1, attempts.get(), "No-op strategy should make exactly 1 attempt without retrying");
        }
    }
}
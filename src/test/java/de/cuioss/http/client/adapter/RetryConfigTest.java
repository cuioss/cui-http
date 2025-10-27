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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RetryConfig}.
 */
class RetryConfigTest {

    @Test
    void shouldCreateDefaultConfiguration() {
        // when
        RetryConfig config = RetryConfig.defaults();

        // then
        assertNotNull(config);
        assertEquals(5, config.maxAttempts());
        assertEquals(Duration.ofSeconds(1), config.initialDelay());
        assertEquals(2.0, config.multiplier());
        assertEquals(Duration.ofMinutes(1), config.maxDelay());
        assertEquals(0.1, config.jitter());
        assertTrue(config.idempotentOnly());
    }

    @Test
    void shouldCreateConfigurationUsingBuilder() {
        // when
        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(500))
                .multiplier(1.5)
                .maxDelay(Duration.ofSeconds(30))
                .jitter(0.2)
                .idempotentOnly(false)
                .build();

        // then
        assertEquals(3, config.maxAttempts());
        assertEquals(Duration.ofMillis(500), config.initialDelay());
        assertEquals(1.5, config.multiplier());
        assertEquals(Duration.ofSeconds(30), config.maxDelay());
        assertEquals(0.2, config.jitter());
        assertFalse(config.idempotentOnly());
    }

    @Test
    void shouldHaveDefaultBuilderValues() {
        // when
        RetryConfig config = RetryConfig.builder().build();

        // then - same as defaults()
        assertEquals(5, config.maxAttempts());
        assertEquals(Duration.ofSeconds(1), config.initialDelay());
        assertEquals(2.0, config.multiplier());
        assertEquals(Duration.ofMinutes(1), config.maxDelay());
        assertEquals(0.1, config.jitter());
        assertTrue(config.idempotentOnly());
    }

    @Test
    void shouldValidateMaxAttempts() {
        // when/then - zero not allowed
        var builder1 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder1.maxAttempts(0));

        // when/then - negative not allowed
        var builder2 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder2.maxAttempts(-1));

        // when/then - 1 is valid (minimum)
        assertDoesNotThrow(() -> RetryConfig.builder().maxAttempts(1).build());
    }

    @Test
    void shouldValidateInitialDelay() {
        // when/then - null not allowed
        var builder1 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder1.initialDelay(null));

        // when/then - zero not allowed
        var builder2 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder2.initialDelay(Duration.ZERO));

        // when/then - negative not allowed
        var builder3 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder3.initialDelay(Duration.ofSeconds(-1)));

        // when/then - positive is valid
        assertDoesNotThrow(() -> RetryConfig.builder().initialDelay(Duration.ofMillis(1)).build());
    }

    @Test
    void shouldValidateMultiplier() {
        // when/then - less than 1.0 not allowed
        var builder1 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder1.multiplier(0.9));

        // when/then - zero not allowed
        var builder2 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder2.multiplier(0.0));

        // when/then - negative not allowed
        var builder3 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder3.multiplier(-1.0));

        // when/then - 1.0 is valid (minimum, linear backoff)
        assertDoesNotThrow(() -> RetryConfig.builder().multiplier(1.0).build());

        // when/then - greater than 1.0 is valid
        assertDoesNotThrow(() -> RetryConfig.builder().multiplier(2.0).build());
    }

    @Test
    void shouldValidateMaxDelay() {
        // when/then - null not allowed
        var builder1 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder1.maxDelay(null));

        // when/then - zero not allowed
        var builder2 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder2.maxDelay(Duration.ZERO));

        // when/then - negative not allowed
        var builder3 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder3.maxDelay(Duration.ofSeconds(-1)));

        // when/then - positive is valid
        assertDoesNotThrow(() -> RetryConfig.builder().maxDelay(Duration.ofMillis(1)).build());
    }

    @Test
    void shouldValidateJitter() {
        // when/then - negative not allowed
        var builder1 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder1.jitter(-0.1));

        // when/then - greater than 1.0 not allowed
        var builder2 = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder2.jitter(1.1));

        // when/then - 0.0 is valid (no jitter)
        assertDoesNotThrow(() -> RetryConfig.builder().jitter(0.0).build());

        // when/then - 1.0 is valid (maximum jitter)
        assertDoesNotThrow(() -> RetryConfig.builder().jitter(1.0).build());

        // when/then - 0.5 is valid (middle)
        assertDoesNotThrow(() -> RetryConfig.builder().jitter(0.5).build());
    }

    @Test
    void shouldCalculateExponentialBackoff() {
        // given - config with no jitter for predictable testing
        RetryConfig config = RetryConfig.builder()
                .initialDelay(Duration.ofSeconds(1))
                .multiplier(2.0)
                .maxDelay(Duration.ofMinutes(10))
                .jitter(0.0)  // No jitter for exact calculation
                .build();

        // when/then - attempt 1: 1s * 2^0 = 1s
        Duration delay1 = config.calculateDelay(1);
        assertEquals(1000, delay1.toMillis());

        // when/then - attempt 2: 1s * 2^1 = 2s
        Duration delay2 = config.calculateDelay(2);
        assertEquals(2000, delay2.toMillis());

        // when/then - attempt 3: 1s * 2^2 = 4s
        Duration delay3 = config.calculateDelay(3);
        assertEquals(4000, delay3.toMillis());

        // when/then - attempt 4: 1s * 2^3 = 8s
        Duration delay4 = config.calculateDelay(4);
        assertEquals(8000, delay4.toMillis());

        // when/then - attempt 5: 1s * 2^4 = 16s
        Duration delay5 = config.calculateDelay(5);
        assertEquals(16000, delay5.toMillis());
    }

    @Test
    void shouldApplyMaxDelayCap() {
        // given - config with small max delay
        RetryConfig config = RetryConfig.builder()
                .initialDelay(Duration.ofSeconds(1))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(5))  // Cap at 5 seconds
                .jitter(0.0)  // No jitter
                .build();

        // when - calculate delay for high attempt number
        // Without cap: 1s * 2^9 = 512s
        Duration delay = config.calculateDelay(10);

        // then - should be capped at 5s
        assertEquals(5000, delay.toMillis());
    }

    @Test
    void shouldApplyJitterRandomization() {
        // given - config with 50% jitter
        RetryConfig config = RetryConfig.builder()
                .initialDelay(Duration.ofSeconds(1))
                .multiplier(1.0)  // No exponential growth
                .maxDelay(Duration.ofMinutes(1))
                .jitter(0.5)  // 50% jitter
                .build();

        // when - calculate delay multiple times (jitter is random)
        Duration delay1 = config.calculateDelay(1);
        Duration delay2 = config.calculateDelay(1);
        Duration delay3 = config.calculateDelay(1);

        // then - all should be in range [500ms, 1500ms] (1000ms ± 50%)
        assertTrue(delay1.toMillis() >= 500 && delay1.toMillis() <= 1500,
                "Delay should be within jitter range: " + delay1.toMillis());
        assertTrue(delay2.toMillis() >= 500 && delay2.toMillis() <= 1500,
                "Delay should be within jitter range: " + delay2.toMillis());
        assertTrue(delay3.toMillis() >= 500 && delay3.toMillis() <= 1500,
                "Delay should be within jitter range: " + delay3.toMillis());

        // Note: We can't assert they're different because random could theoretically
        // produce the same value, but the range check verifies jitter is working
    }

    @Test
    void shouldApplyJitterWithin10Percent() {
        // given - config with default 10% jitter
        RetryConfig config = RetryConfig.builder()
                .initialDelay(Duration.ofSeconds(1))
                .multiplier(1.0)
                .maxDelay(Duration.ofMinutes(1))
                .jitter(0.1)  // 10% jitter
                .build();

        // when - calculate delay multiple times
        for (int i = 0; i < 100; i++) {
            Duration delay = config.calculateDelay(1);

            // then - should be in range [900ms, 1100ms] (1000ms ± 10%)
            assertTrue(delay.toMillis() >= 900 && delay.toMillis() <= 1100,
                    "Delay should be within 10% jitter range: " + delay.toMillis());
        }
    }

    @Test
    void shouldCalculateDelayWithLinearBackoff() {
        // given - config with 1.0 multiplier (linear backoff)
        RetryConfig config = RetryConfig.builder()
                .initialDelay(Duration.ofSeconds(2))
                .multiplier(1.0)  // Linear: no exponential growth
                .maxDelay(Duration.ofMinutes(10))
                .jitter(0.0)
                .build();

        // when/then - all delays should be constant at 2s
        assertEquals(2000, config.calculateDelay(1).toMillis());
        assertEquals(2000, config.calculateDelay(2).toMillis());
        assertEquals(2000, config.calculateDelay(3).toMillis());
        assertEquals(2000, config.calculateDelay(10).toMillis());
    }

    @Test
    void shouldHandleZeroJitter() {
        // given - config with no jitter
        RetryConfig config = RetryConfig.builder()
                .jitter(0.0)
                .build();

        // when - calculate delay multiple times
        Duration delay1 = config.calculateDelay(1);
        Duration delay2 = config.calculateDelay(1);

        // then - should be exact same value (no randomization)
        assertEquals(delay1.toMillis(), delay2.toMillis());
    }

    @Test
    void shouldHandleFullJitter() {
        // given - config with maximum 100% jitter
        RetryConfig config = RetryConfig.builder()
                .initialDelay(Duration.ofSeconds(1))
                .multiplier(1.0)
                .maxDelay(Duration.ofMinutes(1))
                .jitter(1.0)  // 100% jitter: can be 0ms to 2000ms
                .build();

        // when - calculate delay multiple times
        for (int i = 0; i < 100; i++) {
            Duration delay = config.calculateDelay(1);

            // then - should be in range [0ms, 2000ms] (1000ms ± 100%)
            assertTrue(delay.toMillis() >= 0 && delay.toMillis() <= 2000,
                    "Delay should be within 100% jitter range: " + delay.toMillis());
        }
    }

    @Test
    void shouldSetIdempotentOnlyTrue() {
        // when
        RetryConfig config = RetryConfig.builder()
                .idempotentOnly(true)
                .build();

        // then
        assertTrue(config.idempotentOnly());
    }

    @Test
    void shouldSetIdempotentOnlyFalse() {
        // when
        RetryConfig config = RetryConfig.builder()
                .idempotentOnly(false)
                .build();

        // then
        assertFalse(config.idempotentOnly());
    }

    @Test
    void shouldBeThreadSafe() {
        // given - config with jitter (uses ThreadLocalRandom)
        RetryConfig config = RetryConfig.defaults();

        // when - calculate delays from multiple threads
        assertDoesNotThrow(() -> {
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    config.calculateDelay(1);
                }
            });
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    config.calculateDelay(1);
                }
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();
        });

        // then - no exceptions should be thrown (thread-safe)
    }

    @Test
    void shouldCalculateDelaySequenceWithDefaults() {
        // given - default configuration
        RetryConfig config = RetryConfig.builder()
                .jitter(0.0)  // Remove jitter for exact values
                .build();

        // when/then - verify default sequence
        // Attempt 1: 1s * 2^0 = 1s
        assertEquals(1000, config.calculateDelay(1).toMillis());

        // Attempt 2: 1s * 2^1 = 2s
        assertEquals(2000, config.calculateDelay(2).toMillis());

        // Attempt 3: 1s * 2^2 = 4s
        assertEquals(4000, config.calculateDelay(3).toMillis());

        // Attempt 4: 1s * 2^3 = 8s
        assertEquals(8000, config.calculateDelay(4).toMillis());

        // Attempt 5: 1s * 2^4 = 16s
        assertEquals(16000, config.calculateDelay(5).toMillis());

        // Attempt 6: 1s * 2^5 = 32s
        assertEquals(32000, config.calculateDelay(6).toMillis());

        // Attempt 7: 1s * 2^6 = 64s, but capped at maxDelay (60s)
        assertEquals(60000, config.calculateDelay(7).toMillis());
    }
}

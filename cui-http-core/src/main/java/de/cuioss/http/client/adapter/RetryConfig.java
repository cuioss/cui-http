/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuration for retry behavior with exponential backoff.
 * <p>
 * This record configures retry parameters for {@link ResilientHttpAdapter}. It uses industry
 * best practices for default values based on AWS SDK, Google Cloud SDK, RFC 8085, and Polly
 * (.NET resilience library) recommendations.
 *
 * <h2>Default Values (Industry Best Practices)</h2>
 * <ul>
 *   <li><b>maxAttempts: 5</b> - Total attempts including initial try (1 initial + 4 retries).
 *       Balances resilience vs. latency. Too few (&lt; 3) = poor resilience. Too many (&gt; 7) = excessive delays.</li>
 *   <li><b>initialDelay: 1 second</b> - Enough time for transient issues to clear (network hiccup, server restart).
 *       Sub-second often too fast for real transient issues.</li>
 *   <li><b>multiplier: 2.0</b> - Exponential backoff is proven most effective (RFC 8085).
 *       Linear backoff less effective. Higher multipliers (3.0+) cause excessive delays.</li>
 *   <li><b>maxDelay: 60 seconds</b> - Prevents runaway delays from exponential growth.
 *       After ~4 retries, delays would exceed 16s without cap.</li>
 *   <li><b>jitter: 0.1 (10%)</b> - Prevents thundering herd when many clients fail simultaneously.
 *       10% provides sufficient randomization without excessive variance.</li>
 *   <li><b>idempotentOnly: true</b> - Safe by default: only retries idempotent methods
 *       (GET/PUT/DELETE/HEAD/OPTIONS), preventing accidental duplicate operations from POST/PATCH retries.</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Default Configuration</h3>
 * <pre>{@code
 * RetryConfig config = RetryConfig.defaults();
 * // Uses: 5 attempts, 1s initial delay, 2.0 multiplier, 60s max, 10% jitter, idempotent only
 * }</pre>
 *
 * <h3>Custom Configuration</h3>
 * <pre>{@code
 * RetryConfig config = RetryConfig.builder()
 *     .maxAttempts(3)
 *     .initialDelay(Duration.ofMillis(500))
 *     .multiplier(1.5)
 *     .maxDelay(Duration.ofSeconds(30))
 *     .jitter(0.2)  // 20% jitter
 *     .idempotentOnly(true)
 *     .build();
 * }</pre>
 *
 * <h3>POST with Idempotency Keys</h3>
 * <p>
 * If your API uses idempotency keys (e.g., {@code Idempotency-Key} header) to prevent duplicate
 * operations, you must set {@code idempotentOnly(false)} to enable retry for POST:
 * <pre>{@code
 * RetryConfig config = RetryConfig.builder()
 *     .idempotentOnly(false)  // Required to retry POST with idempotency keys
 *     .build();
 *
 * HttpAdapter<Order> resilient = ResilientHttpAdapter.wrap(baseAdapter, config);
 *
 * // POST with idempotency key - safe to retry
 * Map<String, String> headers = Map.of("Idempotency-Key", UUID.randomUUID().toString());
 * HttpResult<Order> result = resilient.postBlocking(order, headers);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * This record is immutable and thread-safe. The {@link #calculateDelay(int)} method uses
 * {@link ThreadLocalRandom} for thread-local randomization without contention.
 *
 * @param maxAttempts   Total attempts including initial try (must be >= 1)
 * @param initialDelay  Starting delay after first failure (must be at least one millisecond)
 * @param multiplier    Each retry delay multiplied by this value (must be finite and >= 1.0)
 * @param maxDelay      Cap on delay regardless of exponential growth (must be at least one millisecond)
 * @param jitter        Randomization factor to prevent thundering herd (must be finite, 0.0 to 1.0)
 * @param idempotentOnly When true, only retry GET/PUT/DELETE/HEAD/OPTIONS; skip POST/PATCH
 *
 * @since 1.0
 */
public record RetryConfig(
int maxAttempts,
@NonNull
Duration initialDelay,
double multiplier,
@NonNull
Duration maxDelay,
double jitter,
boolean idempotentOnly
) {

    /**
     * Validates the retry invariants on every construction path.
     * <p>
     * The {@link Builder} setters validate each parameter eagerly for immediate feedback; this
     * compact constructor is the backstop that additionally covers the canonical-constructor path
     * (direct {@code new RetryConfig(...)} calls), so an invariant-violating instance can never
     * be created.
     *
     * @throws IllegalArgumentException if {@code maxAttempts < 1}, {@code initialDelay} is null or
     *         shorter than one millisecond, {@code maxDelay} is null or shorter than one
     *         millisecond, {@code jitter} is non-finite ({@code NaN} or infinite) or outside
     *         {@code [0.0, 1.0]}, or {@code multiplier} is non-finite or {@code < 1.0}. Non-finite
     *         values are rejected explicitly because every ordered comparison against {@code NaN}
     *         evaluates to {@code false}, so a range check alone would silently admit it.
     */
    public RetryConfig {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, but was: " + maxAttempts);
        }
        requireAtLeastOneMillisecond(initialDelay, "initialDelay");
        requireAtLeastOneMillisecond(maxDelay, "maxDelay");
        if (!Double.isFinite(jitter) || jitter < 0.0 || jitter > 1.0) {
            throw new IllegalArgumentException("jitter must be between 0.0 and 1.0, but was: " + jitter);
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0, but was: " + multiplier);
        }
    }

    /**
     * The shortest delay this configuration can express. {@link #calculateDelay(int)} works in whole
     * milliseconds, so a positive-but-sub-millisecond {@code initialDelay} truncates to zero and the
     * exponential backoff never leaves zero however many attempts it multiplies — a "retry delay"
     * that is in fact a busy loop. The floor is one millisecond rather than merely positive so that
     * a configuration cannot declare a delay the implementation silently cannot honour.
     */
    private static final Duration MIN_DELAY = Duration.ofMillis(1);

    /**
     * Rejects a delay that is null or shorter than {@link #MIN_DELAY}, naming the offending
     * parameter. Shared by the compact constructor and the corresponding {@link Builder} setters so
     * the canonical-constructor path enforces exactly the same floor the builder does.
     *
     * @param value the delay to check
     * @param name  the parameter name to quote in the failure message
     * @throws IllegalArgumentException if {@code value} is null or shorter than one millisecond
     */
    private static void requireAtLeastOneMillisecond(@Nullable Duration value, String name) {
        if (value == null || value.compareTo(MIN_DELAY) < 0) {
            throw new IllegalArgumentException(name + " must be at least 1 millisecond, but was: " + value);
        }
    }

    /**
     * Creates a builder with sensible defaults based on industry best practices.
     *
     * @return new builder with default values
     * @see Builder for default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns default configuration using industry best practices.
     * <p>
     * Equivalent to {@code builder().build()}.
     *
     * @return default retry configuration
     */
    public static RetryConfig defaults() {
        return builder().build();
    }

    /**
     * Calculates delay for given attempt using exponential backoff with jitter.
     * <p>
     * Formula: {@code initialDelay * (multiplier ^ (attempt - 1)) * (1 ± jitter)}
     * <p>
     * The jitter is applied by multiplying the delay by a random factor between
     * {@code (1 - jitter)} and {@code (1 + jitter)}. This randomization prevents
     * thundering herd problems when many clients fail simultaneously.
     *
     * <h3>Thread Safety</h3>
     * This method is safe to call concurrently from multiple threads. Uses
     * {@link ThreadLocalRandom} for thread-local randomization without contention.
     *
     * <h3>Example</h3>
     * With defaults (1s initial, 2.0 multiplier, 10% jitter):
     * <ul>
     *   <li>Attempt 1: 0.9s - 1.1s (1s ± 10%)</li>
     *   <li>Attempt 2: 1.8s - 2.2s (2s ± 10%)</li>
     *   <li>Attempt 3: 3.6s - 4.4s (4s ± 10%)</li>
     *   <li>Attempt 4: 7.2s - 8.8s (8s ± 10%)</li>
     *   <li>Attempt 5: 14.4s - 17.6s (16s ± 10%)</li>
     * </ul>
     *
     * @param attemptNumber current attempt number (1-based: 1 = first retry after initial failure)
     * @return calculated delay: the exponential backoff capped at {@code maxDelay}, then
     *         randomized by {@code ± jitter} (so a capped delay still varies around {@code maxDelay})
     */
    @SuppressWarnings("java:S2245") // Random is fine for jitter - not cryptographic use
    public Duration calculateDelay(int attemptNumber) {
        // Exponential backoff: initialDelay * (multiplier ^ (attempt - 1))
        double exponentialDelay = initialDelay.toMillis()
                * Math.pow(multiplier, (double) attemptNumber - 1);

        // Cap the base exponential delay at maxDelay BEFORE applying jitter, so that once the
        // exponential growth reaches the cap the retries still spread out (± jitter around the cap)
        // instead of all firing in lockstep at exactly maxDelay (the thundering herd jitter prevents).
        double cappedDelay = Math.min(exponentialDelay, maxDelay.toMillis());

        // Apply jitter: delay * (1 ± jitter)
        // Random value between -1.0 and 1.0
        double randomFactor = 2.0 * ThreadLocalRandom.current().nextDouble() - 1.0;
        double jitterMultiplier = 1.0 + (randomFactor * jitter);
        long delayMs = Math.round(cappedDelay * jitterMultiplier);

        return Duration.ofMillis(delayMs);
    }

    /**
     * Builder for {@link RetryConfig}.
     * <p>
     * Validation strategy: Parameters validated in setter methods for immediate feedback.
     * All setters throw {@link IllegalArgumentException} if validation fails.
     *
     * <h3>Default Values</h3>
     * <ul>
     *   <li>{@code maxAttempts}: 5 (1 initial + 4 retries)</li>
     *   <li>{@code initialDelay}: 1 second</li>
     *   <li>{@code multiplier}: 2.0 (exponential doubling)</li>
     *   <li>{@code maxDelay}: 1 minute (60 seconds)</li>
     *   <li>{@code jitter}: 0.1 (10% randomization)</li>
     *   <li>{@code idempotentOnly}: true (safe by default)</li>
     * </ul>
     */
    public static class Builder {
        private int maxAttempts = 5;
        private Duration initialDelay = Duration.ofSeconds(1);
        private double multiplier = 2.0;
        private Duration maxDelay = Duration.ofMinutes(1);
        private double jitter = 0.1;
        private boolean idempotentOnly = true;  // Safe by default

        /**
         * Sets the maximum number of attempts.
         *
         * @param maxAttempts total attempts including initial try (must be &gt;= 1)
         * @return this builder for chaining
         * @throws IllegalArgumentException if maxAttempts &lt; 1
         */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1, but was: " + maxAttempts);
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the initial delay after first failure.
         *
         * @param delay starting delay (must be at least one millisecond, non-null)
         * @return this builder for chaining
         * @throws IllegalArgumentException if delay is null or shorter than one millisecond
         */
        public Builder initialDelay(@NonNull Duration delay) {
            requireAtLeastOneMillisecond(delay, "initialDelay");
            this.initialDelay = delay;
            return this;
        }

        /**
         * Sets the backoff multiplier.
         *
         * @param multiplier each retry delay multiplied by this value (must be finite and &gt;= 1.0)
         * @return this builder for chaining
         * @throws IllegalArgumentException if multiplier is non-finite ({@code NaN} or infinite)
         *         or &lt; 1.0
         */
        public Builder multiplier(double multiplier) {
            if (!Double.isFinite(multiplier) || multiplier < 1.0) {
                throw new IllegalArgumentException("multiplier must be >= 1.0, but was: " + multiplier);
            }
            this.multiplier = multiplier;
            return this;
        }

        /**
         * Sets the maximum delay cap.
         *
         * <p>The cap is applied to the exponential backoff <em>before</em> jitter (see
         * {@link RetryConfig#calculateDelay(int)}), so that capped retries still spread out instead
         * of firing in lockstep. Consequently the final returned delay may exceed {@code maxDelay}
         * by up to the configured {@code jitter} factor; it bounds the exponential growth, not the
         * post-jitter value.</p>
         *
         * @param maxDelay cap on the exponential backoff before jitter (must be at least one
         *                 millisecond, non-null)
         * @return this builder for chaining
         * @throws IllegalArgumentException if maxDelay is null or shorter than one millisecond
         */
        public Builder maxDelay(@NonNull Duration maxDelay) {
            requireAtLeastOneMillisecond(maxDelay, "maxDelay");
            this.maxDelay = maxDelay;
            return this;
        }

        /**
         * Sets the jitter randomization factor.
         *
         * @param jitter randomization factor (must be finite and 0.0 to 1.0, where 0.1 = 10%)
         * @return this builder for chaining
         * @throws IllegalArgumentException if jitter is non-finite ({@code NaN} or infinite) or
         *         not in range [0.0, 1.0]
         */
        public Builder jitter(double jitter) {
            if (!Double.isFinite(jitter) || jitter < 0.0 || jitter > 1.0) {
                throw new IllegalArgumentException("jitter must be between 0.0 and 1.0, but was: " + jitter);
            }
            this.jitter = jitter;
            return this;
        }

        /**
         * Sets whether to only retry idempotent methods.
         * <p>
         * When true (default), only GET/PUT/DELETE/HEAD/OPTIONS are retried.
         * POST and PATCH are never retried to prevent accidental duplicate operations.
         * <p>
         * When false, all methods including POST/PATCH are retried. Use this only when:
         * <ul>
         *   <li>Your API uses idempotency keys (e.g., {@code Idempotency-Key} header)</li>
         *   <li>You accept the risk of duplicate operations</li>
         * </ul>
         *
         * @param idempotentOnly true to skip retry for POST/PATCH (safe default)
         * @return this builder for chaining
         */
        public Builder idempotentOnly(boolean idempotentOnly) {
            this.idempotentOnly = idempotentOnly;
            return this;
        }

        /**
         * Builds the retry configuration.
         *
         * @return new immutable retry configuration
         */
        public RetryConfig build() {
            return new RetryConfig(maxAttempts, initialDelay, multiplier, maxDelay, jitter, idempotentOnly);
        }
    }
}

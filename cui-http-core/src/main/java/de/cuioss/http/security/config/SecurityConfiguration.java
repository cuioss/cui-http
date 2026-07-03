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
package de.cuioss.http.security.config;

/**
 * Immutable record representing security configuration for HTTP validation.
 *
 * <p>This record encapsulates all security policies and settings that are enforced by the
 * validation stages and pipelines of this library. It provides a type-safe, immutable
 * configuration object that can be shared across multiple validation operations.</p>
 *
 * <p>Every setting in this record is consumed by at least one validation stage. Settings
 * that would require request-level context (parameter counts, header counts, allow/block
 * lists, cookie attribute requirements) are intentionally not part of this configuration:
 * the validation pipelines operate on single values and cannot enforce them. Enforce such
 * policies at the application layer, optionally using the reference constants in
 * {@link SecurityDefaults}.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Immutability</strong> - Configuration cannot be modified once created</li>
 *   <li><strong>Type Safety</strong> - Strongly typed configuration parameters</li>
 *   <li><strong>Honest Surface</strong> - Every setting is enforced by the validation pipeline</li>
 *   <li><strong>Composability</strong> - Easy to combine with builder patterns</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Create with builder
 * SecurityConfiguration config = SecurityConfiguration.builder()
 *     .maxPathLength(2048)
 *     .normalizeUnicode(true)
 *     .build();
 *
 * // Create restrictive configuration
 * SecurityConfiguration strict = SecurityConfiguration.strict();
 *
 * // Create permissive configuration (trusted environments only)
 * SecurityConfiguration lenient = SecurityConfiguration.lenient();
 * </pre>
 *
 * Implements: Task C1 from HTTP verification specification
 *
 * @param maxPathLength Maximum allowed URL path length in characters (positive)
 * @param allowDoubleEncoding Whether double URL encoding (e.g. {@code %252e}) is allowed
 * @param maxParameterNameLength Maximum allowed parameter name length in characters (positive)
 * @param maxParameterValueLength Maximum allowed parameter value length in characters (positive)
 * @param maxHeaderNameLength Maximum allowed header name length in characters (positive)
 * @param maxHeaderValueLength Maximum allowed header value length in characters (positive)
 * @param maxCookieNameLength Maximum allowed cookie name length in characters (positive)
 * @param maxCookieValueLength Maximum allowed cookie value length in characters (positive)
 * @param maxBodySize Maximum allowed body size in bytes (non-negative)
 * @param allowNullBytes Whether null bytes are allowed in content
 * @param allowControlCharacters Whether control characters are allowed in content
 * @param allowExtendedAscii Whether extended ASCII (128-255) and applicable Unicode characters are allowed
 * @param normalizeUnicode Whether Unicode normalization should be performed during decoding
 * @param caseSensitiveComparison Whether string comparisons are case-sensitive
 * @param failOnSuspiciousPatterns Whether validation fails on suspicious (non-attack) patterns
 *
 * @since 1.0
 * @see SecurityConfigurationBuilder
 */
// S107: The canonical constructor has many parameters by design - construction
// happens through SecurityConfigurationBuilder; the record only carries the data
@SuppressWarnings("java:S107")
public record SecurityConfiguration(
int maxPathLength,
boolean allowDoubleEncoding,
int maxParameterNameLength,
int maxParameterValueLength,
int maxHeaderNameLength,
int maxHeaderValueLength,
int maxCookieNameLength,
int maxCookieValueLength,
long maxBodySize,
boolean allowNullBytes,
boolean allowControlCharacters,
boolean allowExtendedAscii,
boolean normalizeUnicode,
boolean caseSensitiveComparison,
boolean failOnSuspiciousPatterns
) {

    /**
     * Validates configuration constraints.
     *
     * @throws IllegalArgumentException if any length limit is invalid
     */
    public SecurityConfiguration {
        if (maxPathLength <= 0) {
            throw new IllegalArgumentException("maxPathLength must be positive, got: " + maxPathLength);
        }
        if (maxParameterNameLength <= 0) {
            throw new IllegalArgumentException("maxParameterNameLength must be positive, got: " + maxParameterNameLength);
        }
        if (maxParameterValueLength <= 0) {
            throw new IllegalArgumentException("maxParameterValueLength must be positive, got: " + maxParameterValueLength);
        }
        if (maxHeaderNameLength <= 0) {
            throw new IllegalArgumentException("maxHeaderNameLength must be positive, got: " + maxHeaderNameLength);
        }
        if (maxHeaderValueLength <= 0) {
            throw new IllegalArgumentException("maxHeaderValueLength must be positive, got: " + maxHeaderValueLength);
        }
        if (maxCookieNameLength <= 0) {
            throw new IllegalArgumentException("maxCookieNameLength must be positive, got: " + maxCookieNameLength);
        }
        if (maxCookieValueLength <= 0) {
            throw new IllegalArgumentException("maxCookieValueLength must be positive, got: " + maxCookieValueLength);
        }
        if (maxBodySize < 0) {
            throw new IllegalArgumentException("maxBodySize must be non-negative, got: " + maxBodySize);
        }
    }

    /**
     * Creates a builder for constructing SecurityConfiguration instances.
     *
     * @return A new SecurityConfigurationBuilder with default values
     */
    public static SecurityConfigurationBuilder builder() {
        return new SecurityConfigurationBuilder();
    }

    /**
     * Creates a strict security configuration with tight restrictions.
     * This configuration prioritizes security over compatibility.
     *
     * <p>Delegates to {@link SecurityDefaults#STRICT_CONFIGURATION}, the single
     * source of truth for preset semantics.</p>
     *
     * @return A SecurityConfiguration with strict security policies
     */
    public static SecurityConfiguration strict() {
        return SecurityDefaults.STRICT_CONFIGURATION;
    }

    /**
     * Creates a lenient security configuration for maximum compatibility.
     * This configuration should only be used in trusted environments.
     *
     * <p>Delegates to {@link SecurityDefaults#LENIENT_CONFIGURATION}, the single
     * source of truth for preset semantics. Note that even the lenient preset
     * never permits null bytes; path traversal is always blocked by the
     * validation stages regardless of configuration.</p>
     *
     * @return A SecurityConfiguration with permissive policies
     */
    public static SecurityConfiguration lenient() {
        return SecurityDefaults.LENIENT_CONFIGURATION;
    }

    /**
     * Creates a security configuration with default balanced settings.
     *
     * <p>Delegates to {@link SecurityDefaults#DEFAULT_CONFIGURATION}, which is
     * identical to {@code builder().build()}.</p>
     *
     * @return A SecurityConfiguration with default security policies
     */
    public static SecurityConfiguration defaults() {
        return SecurityDefaults.DEFAULT_CONFIGURATION;
    }

    /**
     * Checks if this configuration is considered "strict" based on key security settings.
     *
     * @return true if this configuration uses strict security policies
     */
    public boolean isStrict() {
        return !allowDoubleEncoding &&
                !allowNullBytes &&
                !allowControlCharacters &&
                !allowExtendedAscii &&
                normalizeUnicode &&
                failOnSuspiciousPatterns;
    }

    /**
     * Checks if this configuration is considered "lenient" based on key security settings.
     *
     * @return true if this configuration uses lenient security policies
     */
    public boolean isLenient() {
        return allowDoubleEncoding &&
                !allowNullBytes &&
                allowControlCharacters &&
                allowExtendedAscii &&
                !normalizeUnicode &&
                !failOnSuspiciousPatterns;
    }
}

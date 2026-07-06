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

import java.util.Set;

/**
 * Immutable record representing security configuration for HTTP validation.
 *
 * <p>This record encapsulates all security policies and settings that are enforced by the
 * validation stages and pipelines of this library. It provides a type-safe, immutable
 * configuration object that can be shared across multiple validation operations.</p>
 *
 * <p>Every setting in this record is enforced. Single-value settings are consumed by the
 * validation stages/pipelines; request-level settings that need collection or attribute
 * context are enforced by dedicated validators: parameter/header/cookie <em>counts</em> by
 * {@code RequestCollectionValidator}, and cookie {@code Secure}/{@code HttpOnly} requirements
 * by {@code CookiePrefixValidationStage.validateCookie}.</p>
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
 * @param normalizeUnicode Whether Unicode normalization is applied during decoding. When enabled,
 *        input is canonicalized (normalize-and-continue: the canonical form flows to downstream
 *        stages) and rejected only when a compatibility/canonical fold introduces a structurally
 *        significant separator (e.g. fullwidth solidus {@code U+FF0F} &rarr; {@code /}); benign
 *        folds of legitimate international text are preserved, not rejected. Paths use NFKC,
 *        parameter values use the lossless NFC form.
 * @param caseSensitiveComparison Whether string comparisons are case-sensitive
 * @param failOnSuspiciousPatterns Whether validation fails on suspicious (non-attack) patterns
 * @param requireSecureCookies Whether cookies must carry the {@code Secure} attribute
 *        (enforced by {@code CookiePrefixValidationStage.validateCookie}). Opt-in, default
 *        {@code false}. Meaningful only for attribute-bearing (Set-Cookie) cookies, not for
 *        request {@code Cookie}-header {@code name=value} pairs.
 * @param requireHttpOnlyCookies Whether cookies must carry the {@code HttpOnly} attribute
 *        (enforced by {@code CookiePrefixValidationStage.validateCookie}). Opt-in, default
 *        {@code false}. Meaningful only for attribute-bearing (Set-Cookie) cookies.
 * @param maxParameterCount Maximum number of request parameters (positive; enforced by the
 *        collection-level {@code RequestCollectionValidator}, not a single-value pipeline)
 * @param maxHeaderCount Maximum number of request headers (positive; enforced by
 *        {@code RequestCollectionValidator})
 * @param maxCookieCount Maximum number of request cookies (positive; enforced by
 *        {@code RequestCollectionValidator})
 * @param allowedHeaderNames Case-insensitive allow-list of header names; empty means allow-all.
 *        Enforced by {@code AllowBlockListStage} in the header-name pipeline.
 * @param blockedHeaderNames Case-insensitive block-list of header names (takes precedence over
 *        the allow-list). Enforced by {@code AllowBlockListStage} in the header-name pipeline.
 * @param allowedContentTypes Case-insensitive allow-list of content types; empty means allow-all.
 *        Enforced by the content-type validator ({@code AllowBlockListStage}).
 * @param blockedContentTypes Case-insensitive block-list of content types (takes precedence over
 *        the allow-list). Enforced by the content-type validator ({@code AllowBlockListStage}).
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
boolean failOnSuspiciousPatterns,
boolean requireSecureCookies,
boolean requireHttpOnlyCookies,
int maxParameterCount,
int maxHeaderCount,
int maxCookieCount,
Set<String> allowedHeaderNames,
Set<String> blockedHeaderNames,
Set<String> allowedContentTypes,
Set<String> blockedContentTypes
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
        if (maxParameterCount <= 0) {
            throw new IllegalArgumentException("maxParameterCount must be positive, got: " + maxParameterCount);
        }
        if (maxHeaderCount <= 0) {
            throw new IllegalArgumentException("maxHeaderCount must be positive, got: " + maxHeaderCount);
        }
        if (maxCookieCount <= 0) {
            throw new IllegalArgumentException("maxCookieCount must be positive, got: " + maxCookieCount);
        }
        // Defensive, null-tolerant immutable copies of the allow/block lists.
        allowedHeaderNames = allowedHeaderNames == null ? Set.of() : Set.copyOf(allowedHeaderNames);
        blockedHeaderNames = blockedHeaderNames == null ? Set.of() : Set.copyOf(blockedHeaderNames);
        allowedContentTypes = allowedContentTypes == null ? Set.of() : Set.copyOf(allowedContentTypes);
        blockedContentTypes = blockedContentTypes == null ? Set.of() : Set.copyOf(blockedContentTypes);
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

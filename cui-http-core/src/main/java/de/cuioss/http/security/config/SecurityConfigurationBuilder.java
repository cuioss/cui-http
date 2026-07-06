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
 * Builder class for constructing {@link SecurityConfiguration} instances with fluent API.
 *
 * <p>Constructs SecurityConfiguration objects with defaults while allowing fine-grained control
 * over all security settings. Follows the standard builder pattern with method chaining.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Fluent API</strong> - All setter methods return the builder for chaining</li>
 *   <li><strong>Secure Defaults</strong> - Pre-configured with reasonable security defaults</li>
 *   <li><strong>Validation</strong> - Input validation on all parameters</li>
 *   <li><strong>Immutability</strong> - Produces immutable SecurityConfiguration instances</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Basic configuration with defaults
 * SecurityConfiguration config = SecurityConfiguration.builder().build();
 *
 * // Custom configuration
 * SecurityConfiguration custom = SecurityConfiguration.builder()
 *     .maxPathLength(2048)
 *     .maxParameterValueLength(1024)
 *     .normalizeUnicode(true)
 *     .build();
 *
 * // Chain encoding settings in one call
 * SecurityConfiguration strict = SecurityConfiguration.builder()
 *     .maxPathLength(1024)
 *     .encoding(false, false, false, true)
 *     .failOnSuspiciousPatterns(true)
 *     .build();
 * </pre>
 *
 * <h3>Default Values</h3>
 * <p>The builder is initialized with balanced default values that provide reasonable
 * security without being overly restrictive:</p>
 * <ul>
 *   <li>Path length: 4096 characters</li>
 *   <li>Parameter name/value length: 128 / 2048 characters</li>
 *   <li>Header name/value length: 128 / 2048 characters</li>
 *   <li>Cookie name/value length: 128 / 2048 characters</li>
 *   <li>Body size: 5MB</li>
 *   <li>Null bytes and control characters: blocked</li>
 * </ul>
 *
 * Implements: Task C2 from HTTP verification specification
 *
 * @since 1.0
 * @see SecurityConfiguration
 */
public class SecurityConfigurationBuilder {

    // Path Security defaults
    private int maxPathLength = 4096;
    private boolean allowDoubleEncoding = false;

    // Parameter Security defaults
    private int maxParameterNameLength = 128;
    private int maxParameterValueLength = 2048;

    // Header Security defaults
    private int maxHeaderNameLength = 128;
    private int maxHeaderValueLength = 2048;

    // Cookie Security defaults
    private int maxCookieNameLength = 128;
    private int maxCookieValueLength = 2048;

    // Body Security defaults
    private long maxBodySize = 5L * 1024 * 1024; // 5MB

    // Encoding Security defaults
    private boolean allowNullBytes = false;
    private boolean allowControlCharacters = false;
    private boolean allowExtendedAscii = true;
    private boolean normalizeUnicode = true;

    // General Policy defaults
    private boolean caseSensitiveComparison = false;
    private boolean failOnSuspiciousPatterns = false;

    // Cookie Security defaults (opt-in; enforced by CookiePrefixValidationStage.validateCookie)
    private boolean requireSecureCookies = false;
    private boolean requireHttpOnlyCookies = false;

    /**
     * Package-private constructor for internal use.
     */
    SecurityConfigurationBuilder() {
        // Initialize with default values already set above
    }

    // === Path Security Methods ===

    /**
     * Sets the maximum allowed path length.
     *
     * @param maxLength Maximum path length in characters (must be positive)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if maxLength is not positive
     */
    public SecurityConfigurationBuilder maxPathLength(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxPathLength must be positive, got: " + maxLength);
        }
        this.maxPathLength = maxLength;
        return this;
    }

    /**
     * Sets whether double URL encoding is allowed.
     *
     * @param allow true to allow double encoding, false to block it
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder allowDoubleEncoding(boolean allow) {
        this.allowDoubleEncoding = allow;
        return this;
    }

    // === Parameter Security Methods ===

    /**
     * Sets the maximum length for parameter names.
     *
     * @param maxLength Maximum name length (must be positive)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if maxLength is not positive
     */
    public SecurityConfigurationBuilder maxParameterNameLength(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxParameterNameLength must be positive, got: " + maxLength);
        }
        this.maxParameterNameLength = maxLength;
        return this;
    }

    /**
     * Sets the maximum length for parameter values.
     *
     * @param maxLength Maximum value length (must be positive)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if maxLength is not positive
     */
    public SecurityConfigurationBuilder maxParameterValueLength(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxParameterValueLength must be positive, got: " + maxLength);
        }
        this.maxParameterValueLength = maxLength;
        return this;
    }

    // === Header Security Methods ===

    /**
     * Sets the maximum length for header names.
     *
     * @param maxLength Maximum name length (must be positive)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if maxLength is not positive
     */
    public SecurityConfigurationBuilder maxHeaderNameLength(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxHeaderNameLength must be positive, got: " + maxLength);
        }
        this.maxHeaderNameLength = maxLength;
        return this;
    }

    /**
     * Sets the maximum length for header values.
     *
     * @param maxLength Maximum value length (must be positive)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if maxLength is not positive
     */
    public SecurityConfigurationBuilder maxHeaderValueLength(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxHeaderValueLength must be positive, got: " + maxLength);
        }
        this.maxHeaderValueLength = maxLength;
        return this;
    }

    // === Cookie Security Methods ===

    /**
     * Sets the maximum length for cookie names.
     *
     * @param maxLength Maximum name length (must be positive)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if maxLength is not positive
     */
    public SecurityConfigurationBuilder maxCookieNameLength(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxCookieNameLength must be positive, got: " + maxLength);
        }
        this.maxCookieNameLength = maxLength;
        return this;
    }

    /**
     * Sets the maximum length for cookie values.
     *
     * @param maxLength Maximum value length (must be positive)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if maxLength is not positive
     */
    public SecurityConfigurationBuilder maxCookieValueLength(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxCookieValueLength must be positive, got: " + maxLength);
        }
        this.maxCookieValueLength = maxLength;
        return this;
    }

    // === Body Security Methods ===

    /**
     * Sets the maximum body size in bytes.
     *
     * @param maxSize Maximum body size (must be non-negative)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if maxSize is negative
     */
    public SecurityConfigurationBuilder maxBodySize(long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxBodySize must be non-negative, got: " + maxSize);
        }
        this.maxBodySize = maxSize;
        return this;
    }

    // === Encoding Security Methods ===

    /**
     * Sets whether null bytes are allowed in content.
     *
     * @param allow true to allow null bytes, false to block them
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder allowNullBytes(boolean allow) {
        this.allowNullBytes = allow;
        return this;
    }

    /**
     * Sets whether control characters are allowed in content.
     *
     * @param allow true to allow control characters, false to block them
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder allowControlCharacters(boolean allow) {
        this.allowControlCharacters = allow;
        return this;
    }

    /**
     * Sets whether extended ASCII characters (128-255) are allowed in content.
     * For URL paths and parameters, this only affects characters 128-255.
     * For headers and body content, this also enables Unicode support.
     *
     * @param allow true to allow extended ASCII and applicable Unicode characters, false to block them
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder allowExtendedAscii(boolean allow) {
        this.allowExtendedAscii = allow;
        return this;
    }

    /**
     * Sets whether Unicode normalization is performed during decoding.
     *
     * <p>When enabled, input is canonicalized and the canonical form is passed to downstream
     * stages (normalize-and-continue). The stage rejects input only when a fold introduces a
     * structurally significant separator (such as a fullwidth solidus folding to {@code /});
     * benign compatibility folds of legitimate international text are preserved. When disabled,
     * input is left as-is.</p>
     *
     * @param normalize true to canonicalize Unicode, false to leave as-is
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder normalizeUnicode(boolean normalize) {
        this.normalizeUnicode = normalize;
        return this;
    }

    /**
     * Configures encoding security settings in one call.
     *
     * @param allowNulls Whether to allow null bytes
     * @param allowControls Whether to allow control characters
     * @param allowHighBit Whether to allow high-bit characters
     * @param normalizeUni Whether to normalize Unicode
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder encoding(boolean allowNulls, boolean allowControls,
            boolean allowHighBit, boolean normalizeUni) {
        return allowNullBytes(allowNulls)
                .allowControlCharacters(allowControls)
                .allowExtendedAscii(allowHighBit)
                .normalizeUnicode(normalizeUni);
    }

    // === General Policy Methods ===

    /**
     * Sets whether string comparisons should be case-sensitive.
     *
     * @param caseSensitive true for case-sensitive comparisons
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder caseSensitiveComparison(boolean caseSensitive) {
        this.caseSensitiveComparison = caseSensitive;
        return this;
    }

    /**
     * Sets whether to fail on detection of suspicious patterns.
     *
     * @param fail true to fail on suspicious patterns, false to log only
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder failOnSuspiciousPatterns(boolean fail) {
        this.failOnSuspiciousPatterns = fail;
        return this;
    }

    // === Cookie Security Methods ===

    /**
     * Sets whether cookies must carry the {@code Secure} attribute.
     *
     * <p>Enforced by {@code CookiePrefixValidationStage.validateCookie(Cookie)}. Opt-in
     * (default {@code false}); meaningful only for attribute-bearing (Set-Cookie) cookies,
     * not for request {@code Cookie}-header {@code name=value} pairs.</p>
     *
     * @param require true to require the Secure attribute on validated cookies
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder requireSecureCookies(boolean require) {
        this.requireSecureCookies = require;
        return this;
    }

    /**
     * Sets whether cookies must carry the {@code HttpOnly} attribute.
     *
     * <p>Enforced by {@code CookiePrefixValidationStage.validateCookie(Cookie)}. Opt-in
     * (default {@code false}); meaningful only for attribute-bearing (Set-Cookie) cookies.</p>
     *
     * @param require true to require the HttpOnly attribute on validated cookies
     * @return This builder for method chaining
     */
    public SecurityConfigurationBuilder requireHttpOnlyCookies(boolean require) {
        this.requireHttpOnlyCookies = require;
        return this;
    }

    /**
     * Builds the SecurityConfiguration with the current settings.
     *
     * @return A new immutable SecurityConfiguration instance
     * @throws IllegalArgumentException if any configuration values are invalid
     */
    public SecurityConfiguration build() {
        return new SecurityConfiguration(
                maxPathLength, allowDoubleEncoding,
                maxParameterNameLength, maxParameterValueLength,
                maxHeaderNameLength, maxHeaderValueLength,
                maxCookieNameLength, maxCookieValueLength,
                maxBodySize,
                allowNullBytes, allowControlCharacters, allowExtendedAscii, normalizeUnicode,
                caseSensitiveComparison, failOnSuspiciousPatterns,
                requireSecureCookies, requireHttpOnlyCookies
        );
    }
}

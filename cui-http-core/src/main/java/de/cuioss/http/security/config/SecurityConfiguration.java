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
 * @param failOnSuspiciousPatterns Whether validation fails when a URL path or parameter value
 *        starts with one of the {@link SecurityDefaults#PROTOCOL_HANDLER_SCHEMES}. That structural
 *        scheme check is this flag's entire remaining scope: the application-layer content
 *        judgement (sensitive filesystem paths, suspicious parameter names) is governed by
 *        {@code blockedPathPatterns} and {@code blockedParameterNames}, which are gated on their
 *        own non-emptiness and not on this flag. When {@code false} a scheme match is allowed
 *        through <em>silently</em> - {@code PatternMatchingStage} does not log or count it.
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
 * @param blockedPathPatterns Block-list of sensitive path literals; empty (the default) means
 *        block-none. Enforced by {@code PatternMatchingStage} for {@code URL_PATH} and
 *        {@code PARAMETER_VALUE} whenever the set is non-empty, matching a whole {@code /}-delimited
 *        path segment. Seed it from {@link SecurityDefaults#SENSITIVE_PATH_PATTERNS} to reproduce
 *        the {@link #paranoid()} detection on any base preset.
 * @param blockedParameterNames Block-list of parameter names; empty (the default) means block-none.
 *        Enforced by {@code PatternMatchingStage} for {@code PARAMETER_NAME} whenever the set is
 *        non-empty, matching by exact string equality. Seed it from
 *        {@link SecurityDefaults#SUSPICIOUS_PARAMETER_NAMES} to reproduce the {@link #paranoid()}
 *        detection on any base preset.
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
Set<String> blockedContentTypes,
Set<String> blockedPathPatterns,
Set<String> blockedParameterNames
) {

    /**
     * Validates configuration constraints.
     *
     * @throws IllegalArgumentException if any length limit is invalid
     */
    public SecurityConfiguration {
        validatePositive("maxPathLength", maxPathLength);
        validatePositive("maxParameterNameLength", maxParameterNameLength);
        validatePositive("maxParameterValueLength", maxParameterValueLength);
        validatePositive("maxHeaderNameLength", maxHeaderNameLength);
        validatePositive("maxHeaderValueLength", maxHeaderValueLength);
        validatePositive("maxCookieNameLength", maxCookieNameLength);
        validatePositive("maxCookieValueLength", maxCookieValueLength);
        validateNonNegative("maxBodySize", maxBodySize);
        validatePositive("maxParameterCount", maxParameterCount);
        validatePositive("maxHeaderCount", maxHeaderCount);
        validatePositive("maxCookieCount", maxCookieCount);
        // Defensive, null-tolerant immutable copies of the allow/block lists.
        allowedHeaderNames = allowedHeaderNames == null ? Set.of() : Set.copyOf(allowedHeaderNames);
        blockedHeaderNames = blockedHeaderNames == null ? Set.of() : Set.copyOf(blockedHeaderNames);
        allowedContentTypes = allowedContentTypes == null ? Set.of() : Set.copyOf(allowedContentTypes);
        blockedContentTypes = blockedContentTypes == null ? Set.of() : Set.copyOf(blockedContentTypes);
        blockedPathPatterns = blockedPathPatterns == null ? Set.of() : Set.copyOf(blockedPathPatterns);
        blockedParameterNames = blockedParameterNames == null ? Set.of() : Set.copyOf(blockedParameterNames);
    }

    /**
     * Rejects a limit that is not strictly positive.
     *
     * @param name  the record component name, used verbatim in the failure message
     * @param value the configured limit
     * @throws IllegalArgumentException if {@code value} is zero or negative
     */
    private static void validatePositive(String name, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got: " + value);
        }
    }

    /**
     * Rejects a limit that is negative.
     *
     * @param name  the record component name, used verbatim in the failure message
     * @param value the configured limit
     * @throws IllegalArgumentException if {@code value} is negative
     */
    private static void validateNonNegative(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative, got: " + value);
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
     * Creates a paranoid security configuration: {@link #strict()} plus the application-layer
     * content detection that no lower preset performs.
     *
     * <p>Delegates to {@link SecurityDefaults#PARANOID_CONFIGURATION}, the single source of truth
     * for preset semantics. It differs from {@code strict()} in exactly two settings - it seeds
     * {@code blockedPathPatterns} from {@link SecurityDefaults#SENSITIVE_PATH_PATTERNS} and
     * {@code blockedParameterNames} from {@link SecurityDefaults#SUSPICIOUS_PARAMETER_NAMES}.</p>
     *
     * <p><strong>False-positive profile.</strong> Those literals are filesystem paths and
     * configuration filenames, so this preset rejects a request whose path carries a matching
     * {@code /}-delimited segment (for example {@code /config/etc/settings}) and a request whose
     * parameter name is exactly {@code file}, {@code path}, {@code url} or one of their siblings -
     * all legitimate vocabulary in many REST APIs. Choose it where paths and parameters really do
     * reach a filesystem; otherwise seed a narrower list of your own via
     * {@link SecurityConfigurationBuilder#blockedPathPatterns(Set)} on another base preset.</p>
     *
     * @return A SecurityConfiguration with strict policies plus content block-lists
     */
    public static SecurityConfiguration paranoid() {
        return SecurityDefaults.PARANOID_CONFIGURATION;
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
     * Returns a copy of this configuration carrying different content block-lists.
     *
     * <p>Every other setting is taken from {@code this}, so a preset derived through this method
     * cannot drift away from its base on any setting other than the two named here. Both arguments
     * are defensively copied by the canonical constructor and may be {@code null}, which is read as
     * an empty (block-none) list.</p>
     *
     * <pre>
     * // paranoid() is strict() plus the application-layer content detection
     * SecurityConfiguration paranoid = SecurityConfiguration.strict()
     *         .withContentBlockLists(SecurityDefaults.SENSITIVE_PATH_PATTERNS,
     *                 SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES);
     * </pre>
     *
     * @param blockedPathPatterns Block-list of sensitive path literals; empty means block-none
     * @param blockedParameterNames Block-list of parameter names; empty means block-none
     * @return A new SecurityConfiguration identical to this one except for the two block-lists
     */
    public SecurityConfiguration withContentBlockLists(Set<String> blockedPathPatterns,
            Set<String> blockedParameterNames) {
        return new SecurityConfiguration(
                maxPathLength, allowDoubleEncoding,
                maxParameterNameLength, maxParameterValueLength,
                maxHeaderNameLength, maxHeaderValueLength,
                maxCookieNameLength, maxCookieValueLength,
                maxBodySize,
                allowNullBytes, allowControlCharacters, allowExtendedAscii, normalizeUnicode,
                caseSensitiveComparison, failOnSuspiciousPatterns,
                requireSecureCookies, requireHttpOnlyCookies,
                maxParameterCount, maxHeaderCount, maxCookieCount,
                allowedHeaderNames, blockedHeaderNames, allowedContentTypes, blockedContentTypes,
                blockedPathPatterns, blockedParameterNames);
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

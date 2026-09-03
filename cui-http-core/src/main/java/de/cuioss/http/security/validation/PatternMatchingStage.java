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
package de.cuioss.http.security.validation;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pattern matching validation stage for detecting malicious attack patterns.
 *
 * <p>This stage performs pattern-based security validation to detect
 * known attack signatures, injection attempts, and suspicious content patterns.
 * The stage analyzes input against multiple security pattern databases:</p>
 *
 * <ol>
 *   <li><strong>Path Traversal Patterns</strong> - Detects directory traversal attempts
 *       (unconditional)</li>
 *   <li><strong>Protocol Handler Schemes</strong> - Rejects a value that <em>starts with</em> a
 *       protocol handler such as {@code javascript:}; gated on {@code failOnSuspiciousPatterns}</li>
 *   <li><strong>Blocked Path Patterns</strong> - Rejects a value carrying a blocked
 *       {@code /}-delimited path segment; gated on {@code blockedPathPatterns} being non-empty</li>
 *   <li><strong>Blocked Parameter Names</strong> - Rejects a parameter name equal to a blocked
 *       name; gated on {@code blockedParameterNames} being non-empty</li>
 * </ol>
 *
 * <p>The last two are application-layer content judgements, so no preset below
 * {@link SecurityConfiguration#paranoid()} seeds their lists - see that preset for the default
 * seeding and its false-positive profile.</p>
 *
 * <h3>Supported Validation Types</h3>
 * <p>The pattern databases apply to {@link ValidationType#URL_PATH},
 * {@link ValidationType#PARAMETER_VALUE} and {@link ValidationType#PARAMETER_NAME} only. For every
 * other validation type - including {@link ValidationType#HEADER_NAME} and
 * {@link ValidationType#HEADER_VALUE} - this stage is an intentional pass-through: no pattern set
 * is selected, so the input is returned unchanged. Header components are therefore not covered by
 * this stage, and the header pipeline does not include it.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Signature-Based Detection</strong> - Uses known attack patterns from OWASP and CVE databases</li>
 *   <li><strong>Configurable Sensitivity</strong> - The scheme check is controlled by
 *       failOnSuspiciousPatterns; the two content block-lists are controlled by their own contents</li>
 *   <li><strong>Performance</strong> - Uses pre-compiled patterns</li>
 *   <li><strong>Context Aware</strong> - Different pattern sets applied based on validation type</li>
 * </ul>
 *
 * <h3>Security Validations</h3>
 * <ul>
 *   <li><strong>Path Traversal</strong> - ../,..\\, and encoded variants</li>
 *   <li><strong>Protocol Violations</strong> - Values starting with a protocol handler scheme</li>
 *   <li><strong>File Access</strong> - Attempts to access sensitive system files (opt-in via
 *       {@code blockedPathPatterns})</li>
 *   <li><strong>Parameter Pollution</strong> - Blocked parameter names (opt-in via
 *       {@code blockedParameterNames})</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Create pattern matching stage
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * PatternMatchingStage matcher = new PatternMatchingStage(config, ValidationType.URL_PATH);
 *
 * // Detect path traversal attack
 * try {
 *     matcher.validate("/api/users/../../../etc/passwd");
 *     // Throws UrlSecurityException with PATH_TRAVERSAL_DETECTED
 * } catch (UrlSecurityException e) {
 *     logger.warn("Path traversal blocked: {}", e.getDetail());
 * }
 *
 * // Path traversal detection
 * try {
 *     matcher.validate("../../../etc/passwd");
 * } catch (UrlSecurityException e) {
 *     logger.warn("Path traversal blocked: {}", e.getDetail());
 * }
 *
 * // Configurable sensitivity
 * SecurityConfiguration strict = SecurityConfiguration.strict(); // failOnSuspiciousPatterns=true
 * PatternMatchingStage strictMatcher = new PatternMatchingStage(strict, ValidationType.PARAMETER_VALUE);
 *
 * // Legitimate content that might trigger in strict mode
 * try {
 *     strictMatcher.validate("SELECT name FROM contacts WHERE id = 123");
 *     // May throw if configured to fail on suspicious patterns
 * } catch (UrlSecurityException e) {
 *     // Handle based on security policy
 * }
 * </pre>
 *
 * <h3>Performance Characteristics</h3>
 * <ul>
 *   <li>O(n*m) time complexity where n = input length, m = number of patterns</li>
 *   <li>Early termination on first pattern match</li>
 *   <li>Pattern order based on common attack frequency</li>
 *   <li>Case-insensitive matching for broader attack detection</li>
 * </ul>
 *
 * <h3>Configuration Dependencies</h3>
 * <ul>
 *   <li><strong>failOnSuspiciousPatterns</strong> - Controls whether a protocol-handler scheme
 *       match rejects the input, and nothing else. When {@code false} a match is allowed through
 *       <em>silently</em>: this stage does not log, count or otherwise report it.</li>
 *   <li><strong>blockedPathPatterns / blockedParameterNames</strong> - Each governs its own check
 *       and is enforced whenever the set is non-empty, independently of
 *       {@code failOnSuspiciousPatterns}.</li>
 *   <li><strong>caseSensitiveComparison</strong> - When {@code false} (the default) both the input
 *       and the pattern set are lowercased before comparison, so case-insensitive matching detects
 *       a superset of what case-sensitive matching detects. Enabling it can therefore only
 *       <em>reduce</em> detection, never increase it. The effect differs per database:
 *       <ul>
 *         <li>{@link SecurityDefaults#PROTOCOL_HANDLER_SCHEMES},
 *             {@link SecurityDefaults#SENSITIVE_PATH_PATTERNS} and
 *             {@link SecurityDefaults#SUSPICIOUS_PARAMETER_NAMES} are all-lowercase literals, so
 *             under {@code true} they cannot match a mixed-case input such as
 *             {@code JavaScript:alert(1)} or {@code /ETC/passwd} at all. The same holds for any
 *             caller-supplied block-list seeded from them.</li>
 *         <li>{@link SecurityDefaults#PATH_TRAVERSAL_PATTERNS} is deliberately mixed-case (it
 *             enumerates encoded spellings such as {@code ..%2F} and {@code %2E%2E/}), so under
 *             {@code true} it matches only the case permutations it literally enumerates.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>Traversal detection is duplicated across three sites</h3>
 * <p>Path-traversal detection is <strong>not</strong> owned by this stage alone. Three sites
 * participate, and they do not all raise the same failure type - so a caller that matches on a
 * specific {@link UrlSecurityFailureType} needs the whole set:</p>
 * <ul>
 *   <li><strong>{@code validation/PatternMatchingStage}</strong> (this class) - matches raw and
 *       encoded traversal literals via {@link SecurityDefaults#PATH_TRAVERSAL_PATTERNS}, the
 *       {@code ENCODED_TRAVERSAL_PATTERN} regex, and the dot-plus-separator regex. All three
 *       checks raise {@link UrlSecurityFailureType#PATH_TRAVERSAL_DETECTED}, always - they are
 *       not gated by {@code failOnSuspiciousPatterns}. This stage <em>never</em> raises
 *       {@code DIRECTORY_ESCAPE_ATTEMPT}.</li>
 *   <li><strong>{@code validation/NormalizationStage}</strong> - detects traversal in two layers
 *       around RFC 3986 dot-segment resolution. Both its intent check (before normalization) and
 *       its residual-segment check (after) raise
 *       {@link UrlSecurityFailureType#PATH_TRAVERSAL_DETECTED}, while its root-escape check
 *       raises {@link UrlSecurityFailureType#DIRECTORY_ESCAPE_ATTEMPT}. It is the <em>only</em>
 *       site that produces the latter.</li>
 *   <li><strong>{@code core/UrlSecurityFailureType#isPathTraversalAttack()}</strong> - the
 *       classification predicate that defines the traversal set as exactly
 *       {@code PATH_TRAVERSAL_DETECTED} and {@code DIRECTORY_ESCAPE_ATTEMPT}. Callers should
 *       branch on this predicate rather than on a single constant, precisely because the two
 *       producing sites above disagree on which constant they raise.</li>
 * </ul>
 * <p>In {@code pipeline/URLPathValidationPipeline} the duplication is deliberate and composed:
 * this stage runs <em>twice</em>, once before decoding/normalization to catch raw literals and
 * once after, to catch what decoding and dot-segment resolution reveal. Neither pass subsumes
 * the other.</p>
 * <p>
 * Implements: Task V3 from HTTP verification specification
 *
 * @param config         Security configuration controlling validation behavior.
 * @param validationType Type of validation being performed (URL_PATH, PARAMETER_NAME, etc.).
 * @see HttpSecurityValidator
 * @see SecurityConfiguration
 * @see SecurityDefaults
 * @see ValidationType
 * @since 1.0
 */
public record PatternMatchingStage(SecurityConfiguration config,
ValidationType validationType) implements HttpSecurityValidator {

    /**
     * Pre-compiled regex pattern for detecting encoded path traversal sequences.
     * Matches various URL-encoded representations of ../ and ..\ patterns including
     * double-encoded, UTF-8 overlong, and mixed encoding attempts.
     * ReDoS-safe: Uses only atomic patterns without nested or consecutive quantifiers.
     */
    @SuppressWarnings({"java:S5869", "java:S5867", "java:S5855"})
    private static final Pattern ENCODED_TRAVERSAL_PATTERN = Pattern.compile(
            """
                    %2e%2e(%2f|%5c|/|\\\\)|\
                    \\.%2e(%2f|%5c|/|\\\\)|%2e\\.(%2f|%5c|/|\\\\)|\
                    %252e%252e(%252f|%255c)|\
                    \\.\\.(%252f|%255c)|\
                    %c0%ae%c0%ae(%c0%af|%c1%9c|/|\\\\)|%c1%9c%c1%9c|\
                    %c0%ae%c0%ae%c0%af|%c0%ae%c0%af|%c1%9c|\
                    %2e%2e//|%2e%2e\\\\\\\\""",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pre-compiled regex pattern for detecting multiple dots followed by path separators.
     * ReDoS-safe: Uses specific atomic patterns without quantifiers that could cause backtracking.
     */
    @SuppressWarnings({"java:S5869", "java:S6035", "RegExpSingleCharAlternation"})
    private static final Pattern DOT_SEPARATOR_PATTERN = Pattern.compile(
            """
                    \\.\\.(/|\\\\)|\\.\\.%2f|\\.\\.%5c|\
                    \\.\\.\\.(/|\\\\)|\\.\\.\\.%2f|\\.\\.\\.%5c|\
                    \\.\\.\\.\\.(/|\\\\)|\\.\\.\\.\\.%2f|\\.\\.\\.\\.%5c|\
                    \\.\\.\\.\\.\\.(/|\\\\)|\\.\\.\\.\\.\\.%2f|\\.\\.\\.\\.\\.%5c""",
            Pattern.CASE_INSENSITIVE
    );


    // XSS script pattern removed - application layer responsibility.
    // Application layers have proper context for HTML/JS escaping and validation.

    /**
     * Pre-computed lowercase variants of the static pattern databases. These sets are
     * constants, so lowercasing them once here avoids re-lowercasing every pattern on
     * every validation call in case-insensitive mode (the common configuration). The
     * configurable block-lists have no such cache - they are caller-supplied, so they are
     * lowercased per check call instead; both are small.
     */
    private static final Set<String> PATH_TRAVERSAL_PATTERNS_LOWERCASE =
            toLowercaseSet(SecurityDefaults.PATH_TRAVERSAL_PATTERNS);
    private static final Set<String> PROTOCOL_HANDLER_SCHEMES_LOWERCASE =
            toLowercaseSet(SecurityDefaults.PROTOCOL_HANDLER_SCHEMES);

    private static Set<String> toLowercaseSet(Set<String> patterns) {
        return patterns.stream()
                .map(p -> p.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Validates input against attack pattern databases.
     *
     * <p>Processing stages:</p>
     * <ol>
     *   <li>Input validation - handles null/empty inputs</li>
     *   <li>Context-sensitive pattern selection - chooses appropriate patterns for validation type</li>
     *   <li>Pattern matching - tests against known attack signatures</li>
     *   <li>Policy enforcement - applies configured response to pattern matches</li>
     * </ol>
     *
     * <p>Pattern selection is driven by the validation type: only {@link ValidationType#URL_PATH},
     * {@link ValidationType#PARAMETER_VALUE} and {@link ValidationType#PARAMETER_NAME} select a
     * pattern set. For every other type - including {@link ValidationType#HEADER_NAME} and
     * {@link ValidationType#HEADER_VALUE} - this method is an intentional pass-through that returns
     * the input unchanged and never throws.</p>
     *
     * @param value The input string to validate against attack patterns
     * @return The original input wrapped in Optional if validation passes, or Optional.empty() if input was null
     * @throws UrlSecurityException if malicious patterns are detected:
     *                              <ul>
     *                                <li>PATH_TRAVERSAL_DETECTED - if path traversal patterns found</li>
     *                                <!-- XSS detection removed - application layer responsibility -->
     *                                <li>SUSPICIOUS_PATTERN_DETECTED - if the value starts with a
     *                                    protocol handler scheme and {@code failOnSuspiciousPatterns}
     *                                    is enabled, or if a {@code /}-delimited segment matches the
     *                                    non-empty {@code blockedPathPatterns} list</li>
     *                                <li>SUSPICIOUS_PARAMETER_NAME - if a parameter name equals an
     *                                    entry of the non-empty {@code blockedParameterNames} list</li>
     *                              </ul>
     */
    @Override
    @SuppressWarnings("java:S3516")
    public Optional<String> validate(@Nullable String value) throws UrlSecurityException {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isEmpty()) {
            return Optional.of(value);
        }

        // Prepare value for case-insensitive matching if needed.
        // Locale.ROOT avoids locale-specific folding (e.g. the Turkish dotless-i)
        // that could otherwise be exploited to bypass pattern matching.
        String testValue = config.caseSensitiveComparison() ? value : value.toLowerCase(Locale.ROOT);

        // Step 1: Check for path traversal patterns (applies to paths and parameters)
        if (validationType == ValidationType.URL_PATH ||
                validationType == ValidationType.PARAMETER_VALUE ||
                validationType == ValidationType.PARAMETER_NAME) {

            checkPathTraversalPatterns(value, testValue);
        }

        // XSS pattern checking removed - application layer responsibility.

        // Step 3: Check for protocol handler schemes and blocked path literals (paths and parameters)
        if (validationType == ValidationType.URL_PATH || validationType == ValidationType.PARAMETER_VALUE) {
            checkProtocolHandlerSchemes(value, testValue);
            checkBlockedPathPatterns(value, testValue);
        }

        // Step 4: Check for blocked parameter names (parameter names only)
        if (validationType == ValidationType.PARAMETER_NAME) {
            checkBlockedParameterNames(value, testValue);
        }

        // Validation passed - return original value
        // Note: Always returning input value is correct for validator contract
        return Optional.of(value);
    }

    /**
     * Checks input for path traversal attack patterns.
     *
     * <p><strong>Security Critical:</strong> Path traversal patterns are ALWAYS blocked
     * regardless of the failOnSuspiciousPatterns configuration, as they represent
     * direct security threats, not merely suspicious behavior.</p>
     *
     * @param originalValue The original input value
     * @param testValue     The value prepared for testing (case-normalized if needed)
     * @throws UrlSecurityException if path traversal patterns are detected
     */
    private void checkPathTraversalPatterns(String originalValue, String testValue) {
        // Check simple string patterns - ALWAYS fail on path traversal (security critical)
        Set<String> patterns = config.caseSensitiveComparison()
                ? SecurityDefaults.PATH_TRAVERSAL_PATTERNS : PATH_TRAVERSAL_PATTERNS_LOWERCASE;
        for (String pattern : patterns) {
            if (testValue.contains(pattern)) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
                        .validationType(validationType)
                        .originalInput(originalValue)
                        .detail("Path traversal pattern detected: " + pattern)
                        .build();
            }
        }

        // Check encoded patterns using regex - ALWAYS fail on path traversal (security critical)
        if (ENCODED_TRAVERSAL_PATTERN.matcher(originalValue).find()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
                    .validationType(validationType)
                    .originalInput(originalValue)
                    .detail("Encoded path traversal pattern detected via regex")
                    .build();
        }

        // Additional check: Look for any sequence of dots followed by path separators
        // This catches edge cases like multiple dots or mixed separators
        // ReDoS-safe: Using contains() with a compiled pattern instead of matches() with .*
        if (DOT_SEPARATOR_PATTERN.matcher(originalValue).find()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
                    .validationType(validationType)
                    .originalInput(originalValue)
                    .detail("Path traversal pattern detected: multiple dots with separators")
                    .build();
        }

    }

    // XSS pattern checking removed - application layer responsibility.
    // Application layers have proper context for HTML/JS escaping and validation.

    /**
     * Checks whether the input starts with a protocol handler scheme.
     *
     * <p>The match is anchored to the start of the <em>whole</em> tested value, not to each path
     * segment: a path component may never begin with a protocol handler, but a segment such as
     * {@code data:export} in {@code /v1/data:export} is ordinary REST vocabulary and must pass.</p>
     *
     * @param originalValue The original input value
     * @param testValue     The value prepared for testing (case-normalized if needed)
     * @throws UrlSecurityException if a scheme is found and policy requires failure
     */
    private void checkProtocolHandlerSchemes(String originalValue, String testValue) {
        if (!config.failOnSuspiciousPatterns()) {
            // Not configured to fail - the match is permitted silently (see ADR-0006).
            return;
        }
        Set<String> schemes = config.caseSensitiveComparison()
                ? SecurityDefaults.PROTOCOL_HANDLER_SCHEMES : PROTOCOL_HANDLER_SCHEMES_LOWERCASE;
        for (String scheme : schemes) {
            if (testValue.startsWith(scheme)) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED)
                        .validationType(validationType)
                        .originalInput(originalValue)
                        .detail("Protocol handler scheme detected: " + scheme)
                        .build();
            }
        }
    }

    /**
     * Checks the input against the configured path block-list.
     *
     * <p>Enforced whenever {@link SecurityConfiguration#blockedPathPatterns()} is non-empty -
     * mirroring {@code AllowBlockListStage}, whose block-list is likewise not gated by any boolean.
     * A pattern matches only when some {@code /}-delimited segment of the tested value
     * <em>equals</em> it with its leading and trailing {@code /} stripped, so {@code /etc/} rejects
     * {@code /config/etc/settings} but not {@code /api/sketches}.</p>
     *
     * @param originalValue The original input value
     * @param testValue     The value prepared for testing (case-normalized if needed)
     * @throws UrlSecurityException if a blocked path pattern is found
     */
    private void checkBlockedPathPatterns(String originalValue, String testValue) {
        Set<String> configured = config.blockedPathPatterns();
        if (configured.isEmpty()) {
            return;
        }
        Set<String> patterns = config.caseSensitiveComparison() ? configured : toLowercaseSet(configured);
        Set<String> segments = Arrays.stream(testValue.split("/")).collect(Collectors.toSet());
        for (String pattern : patterns) {
            String segment = stripSurroundingSlashes(pattern);
            if (!segment.isEmpty() && segments.contains(segment)) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED)
                        .validationType(validationType)
                        .originalInput(originalValue)
                        .detail("Suspicious path pattern detected: " + pattern)
                        .build();
            }
        }
    }

    /**
     * Strips one leading and one trailing {@code /} from a block-list pattern, so that the
     * path-shaped spelling {@code /etc/} and the bare spelling {@code etc} both denote the same
     * path segment.
     *
     * @param pattern The configured block-list pattern
     * @return The pattern reduced to the path segment it denotes
     */
    private static String stripSurroundingSlashes(String pattern) {
        int start = pattern.startsWith("/") ? 1 : 0;
        int end = pattern.length();
        if (end > start && pattern.charAt(end - 1) == '/') {
            end--;
        }
        return pattern.substring(start, end);
    }

    /**
     * Checks parameter names against the configured parameter-name block-list.
     *
     * <p>Enforced whenever {@link SecurityConfiguration#blockedParameterNames()} is non-empty.</p>
     *
     * @param originalValue The original input value
     * @param testValue     The value prepared for testing (case-normalized if needed)
     * @throws UrlSecurityException if a blocked parameter name is found
     */
    private void checkBlockedParameterNames(String originalValue, String testValue) {
        Set<String> configured = config.blockedParameterNames();
        if (configured.isEmpty()) {
            return;
        }
        Set<String> names = config.caseSensitiveComparison() ? configured : toLowercaseSet(configured);
        // Exact-match only: substring matching (contains) would reject legitimate names such
        // as "transcript", "profile" or "filepath" merely because they embed a blocked
        // token like "script", "file" or "path".
        if (names.contains(testValue)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.SUSPICIOUS_PARAMETER_NAME)
                    .validationType(validationType)
                    .originalInput(originalValue)
                    .detail("Suspicious parameter name detected: " + testValue)
                    .build();
        }
    }

}
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
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Path normalization validation stage with security checks.
 *
 * <p><strong>Scope:</strong> RFC 3986 dot-segment resolution and traversal detection are
 * applied only to path validation types ({@link ValidationType#isPath()}, i.e.
 * {@link ValidationType#URL_PATH}). For every non-path validation type (parameter, header
 * and cookie values, and body content) the input is passed through unchanged — it is opaque
 * application data rather than a filesystem path, so resolving {@code ..} segments would
 * silently rewrite caller data and could consume a traversal pattern before downstream
 * pattern matching inspects it. Traversal-style patterns are handled downstream by
 * {@code PatternMatchingStage} for the <em>parameter</em> types only
 * ({@link ValidationType#PARAMETER_NAME}, {@link ValidationType#PARAMETER_VALUE}). That redirect
 * does not hold for the header types: the header pipeline runs neither this stage nor
 * {@code PatternMatchingStage}, and relies on RFC 7230 character validation instead.</p>
 *
 * <p>For path types, this stage performs RFC 3986 Section 5.2.4 path normalization to resolve
 * relative path segments (. and ..) while detecting and preventing path traversal
 * attacks. The stage processes paths through multiple security layers:</p>
 *
 * <ol>
 *   <li><strong>Segment Parsing</strong> - Splits path into segments for processing</li>
 *   <li><strong>Normalization</strong> - Resolves . and .. segments according to RFC 3986</li>
 *   <li><strong>Security Validation</strong> - Detects remaining traversal attempts</li>
 *   <li><strong>Root Escape Detection</strong> - Prevents escaping application root</li>
 * </ol>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>RFC Compliance</strong> - Follows RFC 3986 path normalization rules</li>
 *   <li><strong>Security First</strong> - Detects attacks through normalization analysis</li>
 *   <li><strong>DoS Protection</strong> - Prevents excessive nesting and recursion attacks</li>
 *   <li><strong>Thread Safety</strong> - Safe for concurrent use across multiple threads</li>
 * </ul>
 *
 * <h3>Security Validations</h3>
 * <ul>
 *   <li><strong>Path Traversal</strong> - Detects ../ patterns that remain after normalization</li>
 *   <li><strong>Root Escape</strong> - Prevents paths from escaping the application root, including
 *       an absolute path whose .. walks past root</li>
 *   <li><strong>Component Delimiters</strong> - Rejects a decoded ? or # inside a path value</li>
 *   <li><strong>Excessive Nesting</strong> - Limits path depth to prevent resource exhaustion</li>
 *   <li><strong>Malicious Patterns</strong> - Identifies suspicious path construction</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Create normalization stage
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * NormalizationStage normalizer = new NormalizationStage(config, ValidationType.URL_PATH);
 *
 * // Normalize legitimate path
 * String normalized = normalizer.validate("/api/users/./123/../456")
 *         .orElseThrow(() -&gt; new IllegalArgumentException("input must not be null"));
 * // normalized is: "/api/users/456"
 *
 * // An absolute path whose .. segments walk past root is REJECTED, not clamped
 * try {
 *     normalizer.validate("/api/../../etc/passwd");
 *     // Throws UrlSecurityException with DIRECTORY_ESCAPE_ATTEMPT
 * } catch (UrlSecurityException e) {
 *     logger.warn("Root escape blocked: {}", e.getFailureType());
 * }
 *
 * // A decoded query or fragment delimiter is REJECTED — a path carrying one is not a path
 * try {
 *     normalizer.validate("/a?b");
 *     // Throws UrlSecurityException with INVALID_CHARACTER
 * } catch (UrlSecurityException e) {
 *     logger.warn("Component delimiter in path: {}", e.getFailureType());
 * }
 *
 * // Detect path traversal attack (LAYER 1, single-component traversal)
 * try {
 *     normalizer.validate("api/../admin");
 *     // Throws UrlSecurityException with PATH_TRAVERSAL_DETECTED
 * } catch (UrlSecurityException e) {
 *     logger.warn("Path traversal blocked: {}", e.getFailureType());
 * }
 *
 * // Detect root escape (relative path whose .. survives normalization)
 * try {
 *     normalizer.validate("../etc/passwd");
 *     // Throws UrlSecurityException with DIRECTORY_ESCAPE_ATTEMPT
 * } catch (UrlSecurityException e) {
 *     logger.warn("Root escape blocked: {}", e.getFailureType());
 * }
 *
 * // Detect excessive nesting attack (more than 100 resolved directory segments)
 * try {
 *     normalizer.validate("/" + "a/".repeat(101));
 *     // Throws UrlSecurityException with EXCESSIVE_NESTING
 * } catch (UrlSecurityException e) {
 *     logger.warn("DoS attack blocked: {}", e.getFailureType());
 * }
 * </pre>
 *
 * <h3>What this stage rejects</h3>
 *
 * <p><strong>Component delimiters.</strong> Before any normalization runs, a path value carrying a
 * {@code ?} or {@code #} is rejected with {@link UrlSecurityFailureType#INVALID_CHARACTER}. Both
 * open a different URI component, and {@code DecodingStage} can surface either from {@code %3F} or
 * {@code %23}, so a path that contains one is not a path and must not be resolved as one.</p>
 *
 * <p><strong>Traversal.</strong> {@code validate} runs three throwing checks:</p>
 * <ul>
 *   <li><strong>LAYER 1</strong> ({@code containsDirectoryTraversalIntent}, before
 *       normalization) raises {@link UrlSecurityFailureType#PATH_TRAVERSAL_DETECTED} for five
 *       pattern classes: a single-component traversal {@code seg/../seg} (via
 *       {@code SINGLE_COMPONENT_TRAVERSAL_PATTERN}); a percent-encoded traversal
 *       ({@code ..%} or {@code %2e%2e}, matched case-folded so all hex-case permutations are
 *       covered); three or more consecutive dots followed by a separator; a {@code ..} segment
 *       carrying a {@code ;} path-parameter suffix ({@code /..;/} or a leading {@code ..;},
 *       via {@code DOT_SEGMENT_WITH_PATH_PARAM_PATTERN}), which a Servlet-style container
 *       resolves to {@code ..} once it strips path parameters; and
 *       {@code ..\}-style backslash traversal that does not itself start with {@code ..\}.</li>
 *   <li><strong>LAYER 2</strong> ({@code containsInternalPathTraversal}, after normalization)
 *       raises {@link UrlSecurityFailureType#PATH_TRAVERSAL_DETECTED} for {@code ..} segments
 *       that survive normalization, and {@code escapesRoot} raises
 *       {@link UrlSecurityFailureType#DIRECTORY_ESCAPE_ATTEMPT} for a normalized path that
 *       escapes root.</li>
 *   <li><strong>Root-consumed {@code ..}</strong> ({@code processPathSegment}, during
 *       normalization) raises {@link UrlSecurityFailureType#DIRECTORY_ESCAPE_ATTEMPT} when an
 *       absolute path's {@code ..} reaches root with no parent left to consume.
 *       {@code /api/../../etc/passwd} is exactly this case.</li>
 * </ul>
 *
 * <p><strong>A segment that merely contains {@code ..} is not a dot-segment.</strong> Only the
 * complete segment {@code ..} navigates; {@code a/..c}, {@code file..txt}, {@code ..config} and
 * {@code path/sub..dir/file} are ordinary names and are returned verbatim.</p>
 *
 * <p><strong>Deliberately stricter than RFC 3986 Section 5.2.4.</strong> The RFC discards a
 * root-consumed {@code ..} so that resolution is total over syntactically valid absolute paths.
 * This stage rejects it instead: clamping would hand the caller a different, resolvable path than
 * the input named, which is the wrong default for a security-validation stage. The cost is a
 * false-positive class — a reverse proxy or templated client that emits a {@code ..} count
 * exceeding its nesting depth is now rejected. See ADR-0016.</p>
 *
 * <h3>Performance Characteristics</h3>
 * <ul>
 *   <li>O(n) time complexity where n is the number of path segments</li>
 *   <li>Single pass through path segments with early termination</li>
 *   <li>Minimal memory allocation - reuses StringBuilder</li>
 *   <li>DoS protection through segment counting</li>
 * </ul>
 *
 * <h3>RFC 3986 Compliance</h3>
 * <p>This implementation follows RFC 3986 Section 5.2.4 "Remove Dot Segments":</p>
 * <ul>
 *   <li>Single dot segments (.) are removed</li>
 *   <li>Double dot segments (..) remove the previous segment</li>
 *   <li>Trailing slashes are preserved</li>
 *   <li>Leading slashes are preserved</li>
 * </ul>
 * <p>with one deliberate divergence: a double dot segment with no previous segment to remove is
 * rejected rather than discarded (ADR-0016).</p>
 * <p>
 * Implements: Task V2 from HTTP verification specification
 *
 * @param config         Security configuration controlling validation behavior.
 * @param validationType Type of validation being performed (URL_PATH, PARAMETER_NAME, etc.).
 * @see HttpSecurityValidator
 * @see SecurityConfiguration
 * @see ValidationType
 * @since 1.0
 */
public record NormalizationStage(SecurityConfiguration config,
ValidationType validationType) implements HttpSecurityValidator {

    /**
     * Maximum number of path segments to prevent DoS attacks.
     * This limit prevents excessive processing time from deeply nested paths.
     */
    private static final int MAX_PATH_SEGMENTS = 1000;

    /**
     * Maximum directory depth to prevent excessive nesting attacks.
     * Based on common filesystem and application server limits.
     */
    private static final int MAX_DIRECTORY_DEPTH = 100;

    /**
     * Precompiled pattern to detect URLs with protocol schemes.
     * Matches RFC 3986 scheme format: scheme://authority/path
     * Used to prevent normalization of protocol portions in URLs.
     */
    private static final Pattern URL_WITH_PROTOCOL_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");

    /**
     * Pattern to detect suspicious single-component directory traversal.
     * Matches patterns like "valid/../segment" where a single path segment (not starting with ..)
     * precedes "../" and is followed by another path segment.
     * Updated to handle RFC 3986 allowed characters including dots, tildes, and sub-delimiters.
     */
    static final Pattern SINGLE_COMPONENT_TRAVERSAL_PATTERN = Pattern.compile("^(?!\\.\\./)[^/\\\\]+/\\.\\./[^/\\\\]+$");

    /**
     * Pattern to detect multiple consecutive dots with path separators.
     * Matches patterns like ".../" or "...\\" which could be traversal bypass attempts.
     * Does not match legitimate filenames like "file...txt".
     * Uses .find() with simple pattern to prevent ReDoS attacks.
     */
    static final Pattern MULTIPLE_DOTS_WITH_SEPARATOR_PATTERN = Pattern.compile("\\.{3,}[/\\\\]");

    /**
     * Pattern to detect a dot-segment carrying a path-parameter suffix.
     * Matches a {@code ..} segment — at the start of the input or immediately after a
     * forward slash or backslash — that is directly followed by the {@code ;} path-parameter
     * separator, as in {@code /api/..;/admin} or {@code ..;/etc/passwd}.
     * <p>
     * Any Servlet-style container strips path parameters before resolving the path, so
     * {@code ..;foo} is resolved to {@code ..} downstream: the segment therefore carries full
     * traversal intent even though a literal {@code ../} never appears in the raw input.
     * Anchoring the {@code ..} to a segment boundary keeps legitimate segments whose name
     * merely ends in dots (for example {@code b..;}) out of scope.
     * Uses .find() with a simple pattern to prevent ReDoS attacks.
     */
    static final Pattern DOT_SEGMENT_WITH_PATH_PARAM_PATTERN = Pattern.compile("(?:^|[/\\\\])\\.\\.;");

    /**
     * Pattern for splitting paths on forward slash or backslash separators.
     * Used for parsing path segments during traversal detection.
     */
    static final Pattern PATH_SEPARATOR_PATTERN = Pattern.compile("[/\\\\]");

    /**
     * Pattern to detect paths ending with "/..".
     * Matches paths that end with forward slash followed by double dot.
     */
    static final Pattern ENDS_WITH_SLASH_DOTDOT_PATTERN = Pattern.compile(".*/\\.\\.$");

    /**
     * Pattern to detect paths starting with "../".
     * Matches paths that begin with double dot followed by forward slash.
     */
    static final Pattern STARTS_WITH_DOTDOT_SLASH_PATTERN = Pattern.compile("^\\.\\./.*");

    /**
     * Pattern to detect paths starting with "..\\".
     * Matches paths that begin with double dot followed by backslash.
     */
    static final Pattern STARTS_WITH_DOTDOT_BACKSLASH_PATTERN = Pattern.compile("^\\.\\.\\\\..*");

    /**
     * Pattern to detect internal slash-dotdot patterns.
     * Matches "/" followed by ".." only when it's a directory traversal (followed by "/" or end of string).
     * This avoids false positives for filenames starting with ".." like "a/..c"
     * Uses .find() without unnecessary .* wrappers.
     */
    static final Pattern CONTAINS_SLASH_DOTDOT_PATTERN = Pattern.compile("/\\.\\.(?:/|$)");

    /**
     * Pattern to detect internal dotdot-backslash patterns.
     * Matches ".." followed by "\\" anywhere in the path.
     * Used in conjunction with STARTS_WITH_DOTDOT_BACKSLASH_PATTERN to exclude initial "..\\".
     * Uses .find() without unnecessary .* wrappers.
     */
    static final Pattern CONTAINS_DOTDOT_BACKSLASH_PATTERN = Pattern.compile("\\.\\.\\\\");


    /**
     * Validates and normalizes a path with security checks.
     *
     * <p>Processing stages:</p>
     * <ol>
     *   <li>Input validation - handles null/empty inputs</li>
     *   <li>Path segment parsing - splits on directory separators</li>
     *   <li>RFC 3986 normalization - resolves . and .. segments</li>
     *   <li>Security validation - detects remaining attack patterns</li>
     * </ol>
     *
     * @param value The input path to validate and normalize
     * @return The validated and normalized path wrapped in Optional, or Optional.empty() if input was null
     * @throws UrlSecurityException if any security violations are detected:
     *                              <ul>
     *                                <li>INVALID_CHARACTER - if the path carries a ? or # component delimiter</li>
     *                                <li>EXCESSIVE_NESTING - if path contains too many segments or depth</li>
     *                                <li>PATH_TRAVERSAL_DETECTED - if ../ patterns remain after normalization</li>
     *                                <li>DIRECTORY_ESCAPE_ATTEMPT - if the path escapes root, either by
     *                                    surviving normalization or by walking past root during it</li>
     *                              </ul>
     */
    @Override
    public Optional<String> validate(@Nullable String value) throws UrlSecurityException {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isEmpty()) {
            return Optional.of(value);
        }

        // RFC 3986 "remove dot segments" resolution is only meaningful for path components.
        // For non-path validation types (parameter/header/cookie values and bodies) a segment
        // like "a/b/../c" is opaque application data, not a filesystem path: rewriting it to
        // "a/c" would silently alter caller data and could consume a traversal pattern before
        // PatternMatchingStage inspects it. Non-path inputs therefore pass through unchanged.
        // Traversal-style patterns are detected downstream by PatternMatchingStage for the
        // parameter types only; the header pipeline runs neither this stage nor
        // PatternMatchingStage and relies on RFC 7230 character validation instead.
        if (!validationType.isPath()) {
            return Optional.of(value);
        }

        // Save original for comparison and error reporting
        @SuppressWarnings("UnnecessaryLocalVariable") // Used in exception handling below
        String original = value;

        // A path is one URI component: '?' opens the query and '#' opens the fragment, so neither
        // belongs inside a path value. DecodingStage can surface either from %3F / %23, and a path
        // that carries one is no longer a path -- normalizing it would resolve segments that the
        // recipient will read as a different component altogether.
        //
        // The check is scoped to values that ARE a path. A complete URI (scheme://authority/...)
        // legitimately carries its own query and fragment, and this stage leaves such input
        // unrewritten by design -- ADR-0016 states that scheme-bearing input is unaffected, so
        // applying the delimiter rule to it would both contradict that decision and change the
        // pre-existing complete-URI behaviour. A complete URI passes through here and is handled
        // by the scheme-preserving path below.
        boolean schemeBearing = URL_WITH_PROTOCOL_PATTERN.matcher(original).matches();
        int delimiterIndex = schemeBearing ? -1 : indexOfComponentDelimiter(original);
        if (delimiterIndex >= 0) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_CHARACTER)
                    .validationType(validationType)
                    .originalInput(original)
                    .detail("URI component delimiter '" + original.charAt(delimiterIndex)
                            + "' at position " + delimiterIndex + " in path")
                    .build();
        }

        // LAYER 1: Semantic Intent Validation - Detect directory traversal patterns BEFORE normalization
        // This follows OWASP/CISA best practices for defense in depth
        if (containsDirectoryTraversalIntent(original)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
                    .validationType(validationType)
                    .originalInput(original)
                    .detail("Directory traversal pattern detected in input")
                    .build();
        }

        // Normalize URI components (resolve . and .. in path segments)
        String normalized = normalizeUriComponent(value);

        // Check if path escapes root after normalization (check first for proper precedence)
        if (escapesRoot(normalized)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.DIRECTORY_ESCAPE_ATTEMPT)
                    .validationType(validationType)
                    .originalInput(original)
                    .sanitizedInput(normalized)
                    .detail("Path attempts to escape root directory")
                    .build();
        }

        // LAYER 2: Syntactic Validation - Check for remaining traversal patterns after normalization
        if (containsInternalPathTraversal(normalized)) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
                    .validationType(validationType)
                    .originalInput(original)
                    .sanitizedInput(normalized)
                    .detail("Path normalization revealed traversal attempt")
                    .build();
        }

        return Optional.of(normalized);
    }

    /**
     * Reports the offset of the first {@code ?} or {@code #} in a path value.
     *
     * @param value the path value to scan
     * @return the offset of the first URI component delimiter, or {@code -1} if there is none
     */
    private static int indexOfComponentDelimiter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '?' || c == '#') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Normalizes URI components according to RFC 3986 with DoS protection.
     *
     * <p>This method implements RFC 3986 Section 5.2.4 "Remove Dot Segments" algorithm
     * for path components, while preserving complete URIs with protocol schemes.
     * Includes additional security measures to prevent resource exhaustion attacks.</p>
     *
     * @param uriComponent The URI component to normalize (path segment or complete URI)
     * @return The normalized URI component
     * @throws UrlSecurityException if processing limits are exceeded
     */
    private String normalizeUriComponent(String uriComponent) {
        // Check if this is a complete URI with protocol - don't normalize protocol portion
        if (URL_WITH_PROTOCOL_PATTERN.matcher(uriComponent).matches()) {
            return uriComponent;
        }

        // RFC 3986 path segment normalization with recursion protection
        String[] segments = uriComponent.split("/", -1);
        List<String> outputSegments = new ArrayList<>();
        boolean isAbsolute = uriComponent.startsWith("/");

        // Validate segment count
        validateSegmentCount(segments.length, uriComponent);

        // Process each segment
        for (String segment : segments) {
            processPathSegment(segment, outputSegments, isAbsolute, uriComponent);
        }

        // Build and return normalized path
        return buildNormalizedPath(outputSegments, isAbsolute, uriComponent);
    }

    /**
     * Validates that the segment count does not exceed security limits.
     *
     * @param segmentCount Number of path segments
     * @param originalInput Original input for error reporting
     * @throws UrlSecurityException if segment count exceeds limits
     */
    private void validateSegmentCount(int segmentCount, String originalInput) {
        if (segmentCount > MAX_PATH_SEGMENTS) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.EXCESSIVE_NESTING)
                    .validationType(validationType)
                    .originalInput(originalInput)
                    .detail("Path contains too many segments: " + segmentCount + " (max: " + MAX_PATH_SEGMENTS + ")")
                    .build();
        }
    }

    /**
     * Processes a single path segment according to RFC 3986 normalization rules.
     *
     * @param segment Path segment to process
     * @param outputSegments Current output segments list
     * @param isAbsolute Whether this is an absolute path
     * @param originalInput Original input for error reporting
     * @throws UrlSecurityException if depth limits are exceeded
     */
    private void processPathSegment(String segment, List<String> outputSegments, boolean isAbsolute, String originalInput) {
        switch (segment) {
            case "." -> {
                // Current directory - skip (RFC 3986 Section 5.2.4)
            }
            case ".." -> {
                // Parent directory
                if (!outputSegments.isEmpty() && !"..".equals(outputSegments.getLast())) {
                    // Can resolve this .. by removing the previous segment
                    outputSegments.removeLast();
                } else if (isAbsolute) {
                    // RFC 3986 Section 5.2.4 discards a ".." that reaches the root of an absolute
                    // path, so resolution stays total. This stage deliberately runs stricter: the
                    // walk asked for a parent that does not exist, and clamping would hand the
                    // caller a different, resolvable path than the one the input named. Rejecting
                    // is the only outcome that does not silently rewrite an escape into a hit.
                    throw UrlSecurityException.builder()
                            .failureType(UrlSecurityFailureType.DIRECTORY_ESCAPE_ATTEMPT)
                            .validationType(validationType)
                            .originalInput(originalInput)
                            .detail("Absolute path walks past root with a '..' segment")
                            .build();
                } else {
                    // For a relative path the unresolved ".." is kept, so escapesRoot reports it
                    // once normalization finishes.
                    outputSegments.add("..");
                }
            }
            case "" -> {
                // Empty segment - only preserve for leading slash or trailing slash
                // Skip empty segments from double slashes in the middle
            }
            default -> {
                // Normal segment
                outputSegments.add(segment);
                validateDirectoryDepth(outputSegments.size(), originalInput);
            }
        }
    }

    /**
     * Validates that directory depth does not exceed security limits.
     *
     * @param currentDepth Current directory depth
     * @param originalInput Original input for error reporting
     * @throws UrlSecurityException if depth exceeds limits
     */
    private void validateDirectoryDepth(int currentDepth, String originalInput) {
        if (currentDepth > MAX_DIRECTORY_DEPTH) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.EXCESSIVE_NESTING)
                    .validationType(validationType)
                    .originalInput(originalInput)
                    .detail("Path depth " + currentDepth + " exceeds maximum " + MAX_DIRECTORY_DEPTH)
                    .build();
        }
    }

    /**
     * Builds the normalized path string from processed segments.
     *
     * @param outputSegments Processed path segments
     * @param isAbsolute Whether this is an absolute path
     * @param originalInput Original input for trailing slash preservation
     * @return Normalized path string
     */
    private String buildNormalizedPath(List<String> outputSegments, boolean isAbsolute, String originalInput) {
        StringBuilder result = new StringBuilder();

        // Add leading slash for absolute paths
        if (isAbsolute) {
            result.append("/");
        }

        // Add segments
        for (int i = 0; i < outputSegments.size(); i++) {
            if (i > 0) {
                result.append("/");
            }
            result.append(outputSegments.get(i));
        }

        // Preserve trailing slash if present and we have content, or for root path
        if (originalInput.endsWith("/") && !result.toString().endsWith("/") && (!outputSegments.isEmpty() || isAbsolute)) {
            result.append("/");
        }

        return result.toString();
    }

    /**
     * Detects directory traversal intent patterns in the original input before normalization.
     *
     * <p>This method implements semantic validation following OWASP/CISA best practices
     * for defense in depth. It identifies patterns that indicate malicious directory
     * navigation intent, such as "valid/../segment", regardless of normalization outcome.</p>
     *
     * <p>Based on research analysis of CVEs:
     * <a href="https://nvd.nist.gov/vuln/detail/CVE-2021-41773">CVE-2021-41773</a>,
     * <a href="https://nvd.nist.gov/vuln/detail/CVE-2021-42013">CVE-2021-42013</a>,
     * <a href="https://nvd.nist.gov/vuln/detail/CVE-2024-38819">CVE-2024-38819</a>
     * and industry best practices, patterns like "directory/../target" represent attack
     * fingerprints that should be rejected semantically before syntactic processing.</p>
     *
     * @param input The original input path to analyze for traversal intent
     * @return true if the input contains directory traversal patterns indicating malicious intent
     */
    private boolean containsDirectoryTraversalIntent(String input) {
        // Based on research, focus on specific attack patterns while allowing legitimate RFC 3986 navigation

        // Pattern 1: Suspicious single-component traversal patterns
        // This targets cases like "valid/../segment" where a single word precedes "../"
        // but allows legitimate multi-level paths like "/api/users/../admin"
        if (SINGLE_COMPONENT_TRAVERSAL_PATTERN.matcher(input).matches()) {
            return true;
        }

        // Pattern 2: Encoded traversal attempts (based on Apache CVE research)
        // Covers URL encoded variants like "..%2e/" or "%2e%2e/". Percent-encoding hex digits are
        // case-insensitive, so the check runs against a case-folded copy: that covers all four
        // permutations (%2e%2e, %2E%2E, %2e%2E, %2E%2e) rather than only the two homogeneous ones.
        String caseFolded = input.toLowerCase(Locale.ROOT);
        if (caseFolded.contains("..%") || caseFolded.contains("%2e%2e")) {
            return true;
        }

        // Pattern 3: Multiple consecutive dots with separators (traversal bypass attempts)
        // Covers ".../" but NOT "file...txt"
        if (MULTIPLE_DOTS_WITH_SEPARATOR_PATTERN.matcher(input).find()) {
            return true;
        }

        // Pattern 4: Dot-segment carrying a ";" path-parameter suffix ("/..;/" or leading "..;")
        // A Servlet-style container strips path parameters, so "..;" resolves to ".." downstream
        if (DOT_SEGMENT_WITH_PATH_PARAM_PATTERN.matcher(input).find()) {
            return true;
        }

        // Pattern 5: Windows-style backslash traversal (but not if it starts with ..)
        // Patterns starting with .. should be handled by escapesRoot check
        return CONTAINS_DOTDOT_BACKSLASH_PATTERN.matcher(input).find() &&
                !STARTS_WITH_DOTDOT_BACKSLASH_PATTERN.matcher(input).matches();
    }

    /**
     * Checks if the normalized path contains internal path traversal patterns.
     *
     * <p>After proper normalization, there should be no remaining .. segments
     * except at the beginning for relative paths (which is handled by escapesRoot).
     * This method checks for any remaining traversal patterns
     * that could indicate incomplete normalization or attacks.</p>
     *
     * @param path The normalized path to check
     * @return true if path contains internal traversal patterns
     */
    private boolean containsInternalPathTraversal(String path) {
        // After normalization, check for .. segments that aren't at the start
        if (CONTAINS_SLASH_DOTDOT_PATTERN.matcher(path).find()) {
            return true;
        }

        // For backslash patterns, exclude those starting with ..\\ (handled by escapesRoot)
        if (CONTAINS_DOTDOT_BACKSLASH_PATTERN.matcher(path).find() &&
                !STARTS_WITH_DOTDOT_BACKSLASH_PATTERN.matcher(path).matches()) {
            return true;
        }

        // Check for .. at end of path (without leading ../)
        if (ENDS_WITH_SLASH_DOTDOT_PATTERN.matcher(path).matches() &&
                !STARTS_WITH_DOTDOT_SLASH_PATTERN.matcher(path).matches()) {
            return true;
        }

        // Check for standalone .. that isn't at the beginning
        if ("..".equals(path)) {
            return true;
        }

        // Additional security: check for any .. that appears as a complete path segment
        // This catches cases where .. remains as directory navigation after normalization
        // but excludes .. that appears embedded within filenames (fixing false positives)
        if (path.contains("..")) {
            // Check if .. appears as a complete path segment (separated by slashes)
            String[] segments = PATH_SEPARATOR_PATTERN.split(path);
            for (String segment : segments) {
                if ("..".equals(segment)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if the normalized path attempts to escape the application root.
     *
     * <p>This check identifies paths that would navigate outside the intended
     * directory structure after normalization.</p>
     *
     * @param path The normalized path to check
     * @return true if path attempts to escape root
     */
    private boolean escapesRoot(String path) {
        // Check if normalized path tries to escape root
        return STARTS_WITH_DOTDOT_SLASH_PATTERN.matcher(path).matches() ||
                STARTS_WITH_DOTDOT_BACKSLASH_PATTERN.matcher(path).matches();
    }

}
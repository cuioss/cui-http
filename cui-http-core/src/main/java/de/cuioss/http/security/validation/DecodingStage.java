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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * HTTP protocol-layer decoding validation stage with security checks.
 *
 * <p>This stage performs URL decoding with security validation to detect and prevent
 * HTTP protocol-layer encoding attacks such as double encoding and overlong UTF-8 encoding.
 * <strong>Architectural Scope:</strong> Limited to HTTP/URL protocol encodings only.</p>
 *
 * <ol>
 *   <li><strong>Double Encoding Detection</strong> - Identifies %25XX patterns indicating double encoding</li>
 *   <li><strong>Overlong UTF-8 Detection</strong> - Blocks malformed UTF-8 encoding attacks</li>
 *   <li><strong>URL Decoding</strong> - Performs percent-decoding selected by validation type:
 *       {@code URL_PATH} uses RFC 3986 path semantics, in which {@code +} is an ordinary
 *       character and is preserved literally; every other type uses form
 *       ({@code application/x-www-form-urlencoded}) semantics, in which {@code +} decodes
 *       to a space</li>
 *   <li><strong>Unicode Normalization</strong> - Optionally canonicalizes Unicode (normalize and
 *       continue), rejecting only folds that introduce a structural separator</li>
 * </ol>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Immutability</strong> - All fields are final, stage instances are immutable</li>
 *   <li><strong>Thread Safety</strong> - Safe for concurrent use across multiple threads</li>
 *   <li><strong>Performance</strong> - Uses pre-compiled patterns</li>
 *   <li><strong>Security First</strong> - Detects attacks before potentially dangerous decoding</li>
 * </ul>
 *
 * <h3>Security Validations</h3>
 * <ul>
 *   <li><strong>Double Encoding</strong> - Detects %25XX patterns that could bypass filters</li>
 *   <li><strong>Overlong UTF-8</strong> - Blocks malformed UTF-8 encoding attacks</li>
 *   <li><strong>Invalid Encoding</strong> - Catches malformed percent-encoded sequences</li>
 *   <li><strong>Unicode Normalization Attacks</strong> - Canonicalizes input and rejects folds that
 *       introduce a structural separator (e.g. fullwidth solidus U+FF0F &rarr; {@code /})</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>
 * // Create decoding stage for URL paths
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * DecodingStage pathDecoder = new DecodingStage(config, ValidationType.URL_PATH);
 *
 * // Validate and decode input
 * try {
 *     String decoded = pathDecoder.validate("/api/users%2F123")
 *             .orElseThrow(() -&gt; new IllegalArgumentException("input must not be null"));
 *     // decoded is: "/api/users/123"
 * } catch (UrlSecurityException e) {
 *     // Handle security violation
 *     logger.warn("Encoding attack detected: {}", e.getFailureType());
 * }
 *
 * // Double encoding detection
 * try {
 *     pathDecoder.validate("/admin%252F../users"); // %25 = encoded %
 *     // Throws UrlSecurityException with DOUBLE_ENCODING failure type
 * } catch (UrlSecurityException e) {
 *     // Attack blocked before decoding
 * }
 * </pre>
 *
 * <h3>Performance Characteristics</h3>
 * <ul>
 *   <li>O(n) time complexity where n is input length</li>
 *   <li>Single pass through input for double encoding detection</li>
 *   <li>Minimal memory allocation - reuses pattern instances</li>
 *   <li>Early termination on security violations</li>
 * </ul>
 * <p>
 * Implements: Task V1 from HTTP verification specification
 *
 * @param config         Security configuration controlling validation behavior.
 * @param validationType Type of validation being performed (URL_PATH, PARAMETER_NAME, etc.).
 * @see HttpSecurityValidator
 * @see SecurityConfiguration
 * @see ValidationType
 * @since 1.0
 */
public record DecodingStage(SecurityConfiguration config,
ValidationType validationType) implements HttpSecurityValidator {

    /**
     * Pre-compiled pattern for detecting double encoding patterns.
     * Matches %25 followed by two hexadecimal digits, indicating a percent sign
     * that was encoded as %25 and then encoded again.
     */
    private static final Pattern DOUBLE_ENCODING_PATTERN = Pattern.compile("%25[0-9a-fA-F]{2}");

    /**
     * Pre-compiled pattern for detecting UTF-8 overlong encoding attacks.
     * Matches UTF-8 overlong encodings commonly used to bypass security filters.
     * Includes common overlong encodings for ASCII characters and path separators.
     */
    @SuppressWarnings({"java:S5785", "java:S5855"})
    private static final Pattern UTF8_OVERLONG_PATTERN = Pattern.compile(
            """
                    %c[0-1][0-9a-f]|\
                    %e0%[89][0-9a-f]%[89a-f]|\
                    %f0%80%[89][0-9a-f]%[89a-f]|\
                    %c0%[a-f][0-9a-f]|%c1%[0-9a-f]|\
                    %c0%ae|%c0%af|%c1%9c|%c1%81""",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Validates input through HTTP protocol-layer decoding with security checks.
     *
     * <p><strong>Architectural Boundary:</strong> This stage operates strictly at the HTTP protocol layer,
     * handling URL-specific encoding schemes. Application-layer encodings (HTML entities, JS escapes)
     * are handled by higher application layers where they have proper context.</p>
     *
     * <p>HTTP Protocol Processing stages:</p>
     * <ol>
     *   <li>Double encoding detection - fails fast if %25XX patterns found</li>
     *   <li>UTF-8 overlong encoding detection - blocks malformed UTF-8 attack patterns</li>
     *   <li>URL decoding - converts percent-encoded sequences to characters, with {@code +}
     *       preserved literally for {@code URL_PATH} (RFC 3986) and decoded to a space for the
     *       form-encoded types</li>
     *   <li>Decoded-character re-validation - rejects null bytes, combining marks, control
     *       characters and (for parameter names) decoded delimiters that percent-encoding
     *       hid from the earlier character-validation stage</li>
     *   <li>Unicode normalization - optionally canonicalizes and continues with the canonical form,
     *       rejecting only structural-separator folds</li>
     * </ol>
     *
     * @param value The input string to validate and decode
     * @return The validated and canonicalized string wrapped in Optional, or Optional.empty() if input was null
     * @throws UrlSecurityException if any security violations are detected:
     *                              <ul>
     *                                <li>DOUBLE_ENCODING - if double encoding patterns are found</li>
     *                                <li>INVALID_ENCODING - if URL decoding fails due to malformed input</li>
     *                                <li>NULL_BYTE_INJECTION - if the decoded output contains a null byte</li>
     *                                <li>CONTROL_CHARACTERS - if the decoded output contains a control
     *                                    character that this validation type forbids</li>
     *                                <li>INVALID_CHARACTER - if the decoded output contains a combining
     *                                    mark, or a delimiter inside a parameter name</li>
     *                                <li>UNICODE_NORMALIZATION_CHANGED - if normalization introduces a
     *                                    structurally significant separator character</li>
     *                              </ul>
     */
    @Override
    public Optional<String> validate(@Nullable String value) throws UrlSecurityException {
        if (value == null) {
            return Optional.empty();
        }

        // Step 1: Detect double encoding before decoding
        if (!config.allowDoubleEncoding() && DOUBLE_ENCODING_PATTERN.matcher(value).find()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.DOUBLE_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Double encoding pattern %25XX detected in input")
                    .build();
        }

        // Step 1.5: Detect UTF-8 overlong encoding attacks (always blocked - security critical)
        if (UTF8_OVERLONG_PATTERN.matcher(value).find()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("UTF-8 overlong encoding attack detected")
                    .build();
        }

        // Step 2: URL decode (HTTP protocol-layer appropriate), with the decoder selected by
        // validation type so a path is not silently rewritten by form semantics.
        String decoded;
        try {
            decoded = decodeForValidationType(value);
        } catch (IllegalArgumentException e) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("URL decoding failed: " + e.getMessage())
                    .cause(e)
                    .build();
        }

        // Step 2.5: Re-validate security-critical characters in the DECODED output.
        // Character validation runs before decoding, so a percent-encoded sequence
        // (e.g. %CC%80 for a combining grave accent, or %0D%0A for CRLF) is only
        // seen as valid percent-encoding. The decoded characters must be checked
        // again or encoding becomes a bypass for the character rules.
        validateDecodedCharacters(value, decoded);

        // Step 3: Unicode normalization - canonicalize-then-validate (OWASP model).
        // Normalize and CONTINUE with the canonical form downstream, rejecting only
        // when the fold introduces a structurally significant character (a separator
        // such as / \ . : ? # %) that was not present before -- i.e. the
        // homoglyph-separator attack (fullwidth solidus U+FF0F -> '/'). Benign
        // compatibility folds (fullwidth letters, ligatures, CJK compatibility
        // ideographs) are preserved rather than rejected, so legitimate international
        // content passes. Form is chosen by type: NFKC for URL paths (must fold
        // compatibility homoglyphs of separators), NFC for parameter values (lossless,
        // preserves legitimate international text).
        if (config.normalizeUnicode()) {
            String normalized = Normalizer.normalize(decoded, normalizationForm());
            if (introducesStructuralCharacter(decoded, normalized)) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.UNICODE_NORMALIZATION_CHANGED)
                        .validationType(validationType)
                        .originalInput(value)
                        .sanitizedInput(normalized)
                        .detail("Unicode normalization introduced a structurally significant character")
                        .build();
            }
            decoded = normalized;
        }

        return Optional.of(decoded);
    }

    /**
     * Percent-decodes the input using the semantics that belong to this validation type.
     *
     * <p>{@code URL_PATH} is decoded under RFC 3986 path semantics, where {@code +} is an
     * ordinary path character with no special meaning. {@link URLDecoder} only implements
     * {@code application/x-www-form-urlencoded} semantics, which map {@code +} to a space, so a
     * literal {@code +} is escaped to {@code %2B} before delegating — the decoder then returns it
     * unchanged. An already-encoded {@code %2B} is untouched by the escape and still decodes to
     * {@code +}, so both spellings converge on the same result. Every other validation type
     * carries form-encoded data, where mapping {@code +} to a space is the correct reading, and is
     * delegated directly.</p>
     *
     * <p>Delegating in both branches keeps the UTF-8 charset handling and the
     * {@link IllegalArgumentException}-on-malformed-input behaviour identical, so the
     * {@code INVALID_ENCODING} error path is unaffected by the choice of semantics.</p>
     *
     * @param value the still-encoded input
     * @return the decoded string
     * @throws IllegalArgumentException if the input contains a malformed percent-encoded sequence
     */
    private String decodeForValidationType(String value) {
        String toDecode = validationType == ValidationType.URL_PATH
                ? value.replace("+", "%2B")
                : value;
        return URLDecoder.decode(toDecode, StandardCharsets.UTF_8);
    }

    /**
     * Structurally significant characters: separators whose introduction changes how a
     * value parses (path/segment/scheme/query/fragment/encoding delimiters). The
     * dot-dot traversal sequence is covered transitively by counting {@code '.'}.
     */
    private static final String STRUCTURAL_CHARS = "/\\.:?#%";

    /**
     * Selects the Unicode normalization form for this validation type.
     *
     * <p><strong>NFKC</strong> (compatibility) for {@code URL_PATH}: fullwidth /
     * compatibility homoglyphs of path separators must fold to their canonical ASCII
     * form so the structural-fold check below and downstream path parsing see them.
     * <strong>NFC</strong> (canonical, lossless) for {@code PARAMETER_VALUE} and any
     * other type: preserves legitimate international content. {@code BODY} is not routed
     * through this stage today.</p>
     *
     * @return the normalization form to apply
     */
    private Normalizer.Form normalizationForm() {
        return switch (validationType) {
            case URL_PATH -> Normalizer.Form.NFKC;
            default -> Normalizer.Form.NFC;
        };
    }

    /**
     * Determines whether normalization <em>introduced</em> a structurally significant
     * character - i.e. added occurrences of a separator that were not present before the
     * fold. Per-character counts are compared so a fold that adds a new separator is
     * caught even when the same separator already appears in the input.
     *
     * @param before the decoded string prior to normalization
     * @param after the normalized string
     * @return {@code true} if any structural character occurs more often after folding
     */
    private static boolean introducesStructuralCharacter(String before, String after) {
        for (int i = 0; i < STRUCTURAL_CHARS.length(); i++) {
            char structural = STRUCTURAL_CHARS.charAt(i);
            if (countOccurrences(after, structural) > countOccurrences(before, structural)) {
                return true;
            }
        }
        return false;
    }

    private static int countOccurrences(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    /**
     * Validates security-critical characters in decoded output.
     *
     * <p>Checks applied to the decoded string:</p>
     * <ul>
     *   <li><strong>Null bytes</strong> - rejected unless explicitly allowed (defense in depth;
     *       encoded {@code %00} is normally already rejected before decoding)</li>
     *   <li><strong>Combining marks (all Unicode combining blocks)</strong> - always rejected,
     *       mirroring the raw-character rule; decoded combining marks can visually alter adjacent
     *       characters and enable homograph attacks. Classified by Unicode general category
     *       ({@link CharacterValidationConstants#isCombiningMark(int)}), not a fixed range</li>
     *   <li><strong>Decoded control characters</strong> (the whole C0/C1 class, i.e. every code
     *       point in the Unicode {@code Cc} category: {@code U+0000}-{@code U+001F},
     *       {@code U+007F}-{@code U+009F}) - always rejected for header names/values and cookie
     *       names/values (they travel inside HTTP headers, so a decoded control character is a
     *       response-splitting / header-injection vector) and for parameter <em>names</em>
     *       (structural). For URL paths, rejected unless control characters are explicitly
     *       allowed. Parameter <em>values</em> and bodies tolerate CR, LF and TAB because those
     *       are legitimate form data, but reject the remaining control characters unless
     *       explicitly allowed. The offending code point is reported in escaped {@code U+XXXX}
     *       form so no raw control character reaches a log.</li>
     *   <li><strong>Decoded parameter-name delimiters</strong> ({@code = &amp; ; space}) - rejected
     *       for parameter <em>names</em> only, since a decoded delimiter would split the name and
     *       enable parameter injection</li>
     * </ul>
     *
     * @param originalInput The original (still encoded) input for error reporting
     * @param decoded The decoded string to validate
     * @throws UrlSecurityException if a security-critical character is found
     */
    private void validateDecodedCharacters(String originalInput, String decoded) throws UrlSecurityException {
        // Iterate by Unicode code point (not char) so that supplementary-plane combining marks,
        // which arrive as surrogate pairs, are classified against their real code point rather
        // than an individual surrogate half.
        for (int i = 0; i < decoded.length(); ) {
            int cp = decoded.codePointAt(i);

            if (cp == '\0' && !config.allowNullBytes()) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.NULL_BYTE_INJECTION)
                        .validationType(validationType)
                        .originalInput(originalInput)
                        .detail("Decoded null byte at position " + i)
                        .build();
            }

            if (CharacterValidationConstants.isCombiningMark(cp)) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.INVALID_CHARACTER)
                        .validationType(validationType)
                        .originalInput(originalInput)
                        .detail("Decoded combining character (" + escaped(cp) + ") at position " + i)
                        .build();
            }

            // The null byte has its own dedicated flag (checked above) and is deliberately excluded
            // here, so allowNullBytes stays the single authority for it.
            if (cp != '\0' && Character.isISOControl(cp) && decodedControlCharacterForbidden(cp)) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.CONTROL_CHARACTERS)
                        .validationType(validationType)
                        .originalInput(originalInput)
                        .detail("Decoded control character (" + escaped(cp) + ") at position " + i)
                        .build();
            }

            // Parameter names are structural: a decoded delimiter (=, &, ;, space) would split
            // the name and enable parameter-injection. These are legitimate inside a parameter
            // VALUE (form data), so this rule is name-only.
            if (validationType == ValidationType.PARAMETER_NAME && isParameterNameDelimiter(cp)) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.INVALID_CHARACTER)
                        .validationType(validationType)
                        .originalInput(originalInput)
                        .detail("Decoded parameter-name delimiter '" + (char) cp + "' at position " + i)
                        .build();
            }

            i += Character.charCount(cp);
        }
    }

    /**
     * Characters that delimit parameters in a query string and therefore must not appear
     * inside a decoded parameter <em>name</em>.
     */
    private static boolean isParameterNameDelimiter(int ch) {
        return ch == '&' || ch == '=' || ch == ';' || ch == ' ';
    }

    /**
     * Decides whether a decoded control character is forbidden in this validation context.
     *
     * <p>Header and cookie names/values all travel inside HTTP headers, and a parameter name is
     * structural, so any decoded control character in those contexts is a response-splitting or
     * injection vector and is rejected unconditionally - {@code allowControlCharacters} does not
     * relax them. URL paths honour {@code allowControlCharacters}. Parameter values and bodies
     * carry form data, in which CR, LF and TAB are legitimate content; every other control
     * character is rejected there unless {@code allowControlCharacters} is set.</p>
     *
     * @param cp the decoded control code point under test
     * @return {@code true} if the code point must be rejected for this validation type
     */
    private boolean decodedControlCharacterForbidden(int cp) {
        return switch (validationType) {
            case HEADER_NAME, HEADER_VALUE, COOKIE_NAME, COOKIE_VALUE, PARAMETER_NAME -> true;
            case URL_PATH -> !config.allowControlCharacters();
            case PARAMETER_VALUE, BODY -> !isFormDataWhitespace(cp) && !config.allowControlCharacters();
        };
    }

    /**
     * CR, LF and TAB are legitimate content inside form-encoded parameter values and bodies -
     * a multi-line textarea submission carries them by design.
     */
    private static boolean isFormDataWhitespace(int cp) {
        return cp == '\r' || cp == '\n' || cp == '\t';
    }

    /**
     * Renders a code point in escaped {@code U+XXXX} form. The detail string is included in
     * {@link UrlSecurityException#getMessage()}, which callers log, so a control character must
     * never be rendered verbatim - a raw CR/LF would enable log forging.
     */
    private static String escaped(int cp) {
        return "U+%04X".formatted(cp);
    }

}
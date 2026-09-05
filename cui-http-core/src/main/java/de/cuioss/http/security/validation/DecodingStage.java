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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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
 *   <li><strong>Strict UTF-8 Decoding</strong> - Percent-decodes to bytes and decodes those bytes
 *       with a reporting UTF-8 decoder, so every malformed byte sequence - overlong forms,
 *       truncated sequences, surrogate halves - is rejected rather than replaced</li>
 *   <li><strong>URL Decoding</strong> - Performs percent-decoding selected by validation type:
 *       {@code URL_PATH} uses RFC 3986 path semantics, in which {@code +} is an ordinary
 *       character and is preserved literally; every other type uses form
 *       ({@code application/x-www-form-urlencoded}) semantics, in which {@code +} decodes
 *       to a space</li>
 *   <li><strong>Unicode Normalization</strong> - Always inspects the canonical form and rejects a
 *       fold that introduces a structural separator; {@code normalizeUnicode} decides only
 *       whether the canonical form or the input form is the value returned</li>
 * </ol>
 *
 * <h3>Raw and encoded spellings receive the same verdict</h3>
 * <p>Every gate above is unconditional, so a value cannot buy a softer verdict by being spelled
 * in encoded form. The two gates that were previously configuration-dependent - the double-encoding
 * pair and the Unicode structural-fold check - are the ones that made the encoded spelling of a
 * value outrank its raw spelling: a raw {@code /} is inspected by {@code CharacterValidationStage}
 * and the pattern stages, whereas under a relaxed configuration a {@code %252F} or a fullwidth
 * solidus carrying that same {@code /} was waved through. Both are now enforced for every
 * configuration.</p>
 *
 * <p>{@link SecurityConfiguration#allowDoubleEncoding()} is consequently no longer read by this
 * stage. It remains a supported configuration property with an unchanged public surface; it simply
 * no longer relaxes these gates. {@link SecurityConfiguration#normalizeUnicode()} remains live and
 * observable here, narrowed to the question of which form is returned.</p>
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
 *   <li><strong>Malformed UTF-8</strong> - Rejects every byte sequence a reporting UTF-8 decoder
 *       refuses, which subsumes the overlong encodings a fixed denylist could only enumerate</li>
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
     * Pre-compiled pattern for detecting a percent-encoding layer that survived decoding.
     * Matches a {@code %} followed by two hexadecimal digits in the DECODED output, which
     * means the input carried one more encoding layer than the decode consumed.
     *
     * <p>This is the decoded-form counterpart to {@link #DOUBLE_ENCODING_PATTERN}. The
     * wire-form regex only recognises the literal spelling {@code %25XX}, so an input such
     * as {@code %25%32%66} — whose {@code %25} is followed by {@code %3}, not two hex
     * digits — walks past it and decodes to the literal string {@code %2f}, a still-encoded
     * path separator. Checking the decoded output catches every spelling the wire-form
     * regex cannot express.</p>
     */
    private static final Pattern SURVIVING_ENCODING_PATTERN = Pattern.compile("%[0-9a-fA-F]{2}");

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
     *   <li>URL decoding - percent-decodes to bytes and decodes those bytes as strict UTF-8, so a
     *       malformed escape and a malformed byte sequence are both rejected rather than repaired.
     *       {@code +} is preserved literally for {@code URL_PATH} (RFC 3986) and decoded to a
     *       space for the form-encoded types</li>
     *   <li>Surviving-encoding detection - rejects a percent-encoding layer that outlived the
     *       decode (a {@code %} followed by two hex digits in the decoded output), catching the
     *       nested spellings the wire-form regex in stage 1 cannot express</li>
     *   <li>Decoded-character re-validation - rejects null bytes, combining marks, control
     *       characters and (for parameter names) decoded delimiters that percent-encoding
     *       hid from the earlier character-validation stage</li>
     *   <li>Unicode normalization - always folds the decoded value and inspects the result. The
     *       decoded-character rules of stage 4 are re-applied to the normalized form, so a fold
     *       that introduces a forbidden character cannot bypass them, and the fold is then
     *       rejected when it introduces a structural separator. Only the returned value depends
     *       on {@code normalizeUnicode}: the canonical form when it is enabled, the decoded input
     *       form when it is not</li>
     * </ol>
     *
     * @param value The input string to validate and decode
     * @return The validated and canonicalized string wrapped in Optional, or Optional.empty() if input was null
     * @throws UrlSecurityException if any security violations are detected:
     *                              <ul>
     *                                <li>DOUBLE_ENCODING - if a wire-form double-encoding pattern is
     *                                    found, or if a percent-encoding layer survives decoding</li>
     *                                <li>INVALID_ENCODING - if an escape is malformed, or if the
     *                                    percent-decoded bytes are not well-formed UTF-8</li>
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

        // Step 1: Detect double encoding before decoding. Unconditional: a value spelled with an
        // extra encoding layer smuggles a character past the wire-form character and pattern stages,
        // which only ever see the outer spelling. Gating this on configuration made the encoded
        // spelling of a value outrank the raw one -- the raw '/' is inspected by
        // CharacterValidationStage, while a %252F carrying the same '/' was waved through.
        if (DOUBLE_ENCODING_PATTERN.matcher(value).find()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.DOUBLE_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Double encoding pattern %25XX detected in input")
                    .build();
        }

        // Step 2: URL decode (HTTP protocol-layer appropriate), with the decoder selected by
        // validation type so a path is not silently rewritten by form semantics. The decode is
        // strict end to end: a malformed %XX escape and a malformed UTF-8 byte sequence are both
        // rejected rather than repaired, so no attacker-chosen byte stream is silently turned
        // into a replacement character that downstream stages then judge as benign.
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
        } catch (CharacterCodingException e) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .detail("Malformed UTF-8 byte sequence in percent-decoded input: " + e.getMessage())
                    .cause(e)
                    .build();
        }

        // Ordering note: the decoded-character validation below runs BEFORE the
        // surviving-encoding check that follows it, so no exception attaches an
        // unvalidated 'decoded' value to sanitizedInput. An input can trip both
        // properties at once ("%0D%0A%25%32%66" decodes to "\r\n%2f"), and every other
        // throw site in this class only exposes a value after it has cleared this check.
        // Step 2.5: Re-validate security-critical characters in the DECODED output.
        // Character validation runs before decoding, so a percent-encoded sequence
        // (e.g. %CC%80 for a combining grave accent, or %0D%0A for CRLF) is only
        // seen as valid percent-encoding. The decoded characters must be checked
        // again or encoding becomes a bypass for the character rules.
        validateDecodedCharacters(value, decoded);

        // Step 2.75: Reject a percent-encoding layer that survived the decode. The wire-form
        // gate in step 1 only recognises the literal spelling %25XX, so a nested spelling such
        // as %25%32%66 walks past it and decodes to the literal "%2f" -- a still-encoded path
        // separator that would reach NormalizationStage undecoded. Checking the DECODED output
        // catches every spelling the wire-form regex cannot express. Unconditional for the same
        // reason as step 1: the output of this stage is read as literal text by every stage after
        // it, so a surviving %2F would reach them as a still-encoded separator that the raw '/'
        // spelling of the same value would never have been able to hide behind.
        if (SURVIVING_ENCODING_PATTERN.matcher(decoded).find()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.DOUBLE_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .sanitizedInput(decoded)
                    .detail("Percent-encoding layer survived decoding in decoded output")
                    .build();
        }

        // Step 3: Unicode normalization - canonicalize-then-validate (OWASP model).
        // Normalize and CONTINUE rather than reject on any fold, failing only when the
        // fold introduces a structurally significant character (a separator such as
        // / \ . : ? # % ;) that was not present before -- i.e. the
        // homoglyph-separator attack (fullwidth solidus U+FF0F -> '/'). Benign
        // compatibility folds (fullwidth letters, ligatures, CJK compatibility
        // ideographs) are preserved rather than rejected, so legitimate international
        // content passes. Form is chosen by type: NFKC for URL paths (must fold
        // compatibility homoglyphs of separators), NFC for parameter values (lossless,
        // preserves legitimate international text).
        //
        // DETECTION IS UNCONDITIONAL; ONLY THE REWRITE IS CONFIGURABLE. The fold is always
        // computed and always inspected, because a homoglyph separator is a raw-versus-encoded
        // asymmetry rather than a compatibility preference: the raw '/' is inspected by every
        // wire-form stage, so a fullwidth solidus that folds to '/' must not be accepted merely
        // because canonicalization is switched off. What config.normalizeUnicode() controls is
        // the narrower question this stage genuinely has a choice about -- whether the canonical
        // form or the input form is the value handed downstream.
        String normalized = Normalizer.normalize(decoded, normalizationForm());
        if (!normalized.equals(decoded)) {
            // Re-run the decoded-character rules against the NORMALIZED form before the
            // structural-fold check. Step 2.5 only saw the pre-normalization string, so a
            // character rule could otherwise be bypassed by an input that folds INTO a
            // forbidden character (e.g. a compatibility form that normalizes to a control
            // character or to a parameter-name delimiter). Ordering it ahead of the
            // structural-fold check keeps the more specific character verdict authoritative.
            // The guard is a fast path, not a semantic gate: when the fold is a no-op the
            // re-check re-examines the exact string step 2.5 already cleared, and the
            // occurrence counts below are equal by construction.
            validateDecodedCharacters(value, normalized);
            if (introducesStructuralCharacter(decoded, normalized)) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.UNICODE_NORMALIZATION_CHANGED)
                        .validationType(validationType)
                        .originalInput(value)
                        .sanitizedInput(normalized)
                        .detail("Unicode normalization introduced a structurally significant character")
                        .build();
            }
        }

        String result = config.normalizeUnicode() ? normalized : decoded;

        // Step 4: Re-apply the surviving-encoding check to the value actually returned.
        // The fold can ASSEMBLE an escape that step 2.75 could not see: '%' followed by two
        // compatibility digits (U+FF12 U+FF26, reachable as the decoded text of
        // %25%EF%BC%92%EF%BC%A6) carries no ASCII hex, so the pattern misses it, and NFKC then
        // folds it to the live "%2F". The percent count is unchanged by that fold, so
        // introducesStructuralCharacter does not see it either. Checking the returned value is
        // the only place the assembled escape is observable.
        if (SURVIVING_ENCODING_PATTERN.matcher(result).find()) {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.DOUBLE_ENCODING)
                    .validationType(validationType)
                    .originalInput(value)
                    .sanitizedInput(result)
                    .detail("Percent-encoding layer survived decoding in returned output")
                    .build();
        }

        return Optional.of(result);
    }

    /**
     * Percent-decodes the input using the semantics that belong to this validation type, then
     * decodes the resulting bytes as strict UTF-8.
     *
     * <p>{@code URL_PATH} is decoded under RFC 3986 path semantics, where {@code +} is an
     * ordinary path character with no special meaning and is preserved literally. Every other
     * validation type carries form ({@code application/x-www-form-urlencoded}) data, where
     * mapping {@code +} to a space is the correct reading. An already-encoded {@code %2B} decodes
     * to {@code +} under both readings, so the two spellings converge on a path.</p>
     *
     * <p>The decode is deliberately hand-rolled rather than delegated to
     * {@code java.net.URLDecoder}: that decoder builds its result with a replacing charset
     * decode, so a malformed byte sequence becomes {@code U+FFFD} instead of an error, and an
     * attacker-chosen overlong or truncated sequence would reach the later stages disguised as
     * benign text. Decoding to a {@code byte[]} first and then running a
     * <em>reporting</em> {@link CharsetDecoder} over it makes every malformed sequence an error,
     * which subsumes the fixed overlong denylist this stage previously carried.</p>
     *
     * @param value the still-encoded input
     * @return the decoded string
     * @throws IllegalArgumentException  if the input contains a malformed percent-encoded escape
     * @throws CharacterCodingException if the percent-decoded bytes are not well-formed UTF-8
     */
    private String decodeForValidationType(String value) throws CharacterCodingException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(percentDecodeToBytes(value))).toString();
    }

    /**
     * Percent-decodes the input into the raw byte stream it encodes.
     *
     * <p>Characters that are not part of an escape are emitted as their own UTF-8 bytes, so the
     * result is one consistent byte stream that the strict decoder above can judge as a whole.
     * They are buffered as a run rather than converted one {@code char} at a time, which keeps a
     * surrogate pair intact instead of encoding each half separately.</p>
     *
     * @param value the still-encoded input
     * @return the decoded bytes
     * @throws IllegalArgumentException if an escape is truncated or carries a non-hex digit
     */
    private byte[] percentDecodeToBytes(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length());
        StringBuilder literalRun = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '%') {
                flushLiteralRun(literalRun, out);
                out.write(decodeEscape(value, i));
                i += 3;
            } else {
                boolean plusIsSpace = c == '+' && validationType != ValidationType.URL_PATH;
                literalRun.append(plusIsSpace ? ' ' : c);
                i++;
            }
        }
        flushLiteralRun(literalRun, out);
        return out.toByteArray();
    }

    private static void flushLiteralRun(StringBuilder literalRun, ByteArrayOutputStream out) {
        if (!literalRun.isEmpty()) {
            out.writeBytes(literalRun.toString().getBytes(StandardCharsets.UTF_8));
            literalRun.setLength(0);
        }
    }

    /**
     * Reads the single byte encoded by the {@code %XX} escape starting at {@code index}.
     *
     * @param value the still-encoded input
     * @param index the offset of the {@code %}
     * @return the decoded byte value in the range 0-255
     * @throws IllegalArgumentException if the escape is truncated or carries a non-hex digit
     */
    private static int decodeEscape(String value, int index) {
        if (index + 2 >= value.length()) {
            throw new IllegalArgumentException(
                    "Incomplete trailing escape (%) pattern at index " + index);
        }
        int high = asciiHexDigit(value.charAt(index + 1));
        int low = asciiHexDigit(value.charAt(index + 2));
        if (high < 0 || low < 0) {
            throw new IllegalArgumentException(
                    "Illegal hex characters in escape (%) pattern at index " + index + ": "
                            + escaped(value.charAt(index + 1)) + " " + escaped(value.charAt(index + 2)));
        }
        return (high << 4) | low;
    }

    /**
     * Reads one ASCII hex digit, or {@code -1} when the character is not one.
     *
     * <p>{@link Character#digit(char, int)} accepts every Unicode digit form, so it reads
     * U+FF12 FULLWIDTH DIGIT TWO as {@code 2}. An RFC 3986 escape is ASCII by definition, so
     * accepting a compatibility digit lets {@code %} followed by two fullwidth characters
     * decode to a real byte -- {@code %\uFF12\uFF26} would yield {@code '/'}, smuggling a path
     * separator past every wire-form check that looked for ASCII hex.</p>
     *
     * @param c the character to read
     * @return the digit value 0-15, or {@code -1} when {@code c} is not an ASCII hex digit
     */
    private static int asciiHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    /**
     * Structurally significant characters: separators whose introduction changes how a
     * value parses (path/segment/scheme/query/fragment/encoding delimiters, and the
     * {@code ';'} that separates parameters and cookie pairs). The dot-dot traversal
     * sequence is covered transitively by counting {@code '.'}.
     */
    private static final String STRUCTURAL_CHARS = "/\\.:?#%;";

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
        int i = 0;
        while (i < decoded.length()) {
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
     *
     * <h4>Why this rule is scoped to {@link ValidationType#PARAMETER_NAME} alone</h4>
     * <p>The scoping is a decision, not an omission. Only three validation types reach this
     * stage at all - {@code URL_PATH}, {@code PARAMETER_NAME} and {@code PARAMETER_VALUE} - and
     * of those only a parameter name is structural, so only there does a decoded delimiter
     * change how the value parses. A parameter <em>value</em> legitimately carries {@code &amp;},
     * {@code =}, {@code ;} and spaces as form content, and a path's own component delimiters
     * ({@code ?} and {@code #}) are rejected by {@code NormalizationStage}, which owns the
     * decoded-path rules.</p>
     *
     * <p>Widening the rule would not close a raw-versus-encoded asymmetry either, because there
     * is none to close <em>at this stage</em>: a raw input passes through
     * {@link #decodeForValidationType(String)} unchanged, so
     * {@link #validateDecodedCharacters(String, String)} reaches the identical verdict for both
     * spellings of the same value. The residual divergence - a raw {@code &amp;} in a parameter
     * name is admitted by {@code CharacterValidationStage} while its {@code %26} spelling is
     * rejected here - originates in that stage's wire-form character set
     * ({@code RFC3986_QUERY_CHARS} admits {@code &amp;}, {@code =} and {@code ;}), and closing it
     * means tightening that set rather than loosening this rule.</p>
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
     * <h3>Threat model: the CR/LF carve-out for PARAMETER_VALUE and BODY</h3>
     * <p><strong>Decoded CR and LF are deliberately allowed for
     * {@link ValidationType#PARAMETER_VALUE} and {@link ValidationType#BODY}</strong> - this is
     * the carve-out expressed by the {@code isFormDataWhitespace} branch above, not an
     * oversight. Form data legitimately carries line breaks (a multi-line {@code textarea}
     * submission is the ordinary case), so rejecting them would break correct applications.</p>
     *
     * <p>The consequence is a residual risk this stage does <em>not</em> close:
     * <strong>response-splitting safety for these two types depends on the application not
     * reflecting parameter values or body content into response headers.</strong> A value that
     * passes this stage may contain CR/LF; writing it unescaped into a {@code Set-Cookie},
     * {@code Location} or any other response header is a response-splitting vulnerability that
     * no configuration here prevents. The header types are unaffected - CR/LF is rejected
     * unconditionally for {@code HEADER_NAME}, {@code HEADER_VALUE}, {@code COOKIE_NAME},
     * {@code COOKIE_VALUE} and {@code PARAMETER_NAME}, and {@code allowControlCharacters} does
     * not relax them.</p>
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
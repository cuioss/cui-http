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
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CharacterValidationStageTest {

    private final SecurityConfiguration config = SecurityConfiguration.defaults();

    /**
     * ADR-0017: a gate shared by every preset is pinned under both {@code defaults()} and
     * {@code lenient()}, and must reach the same verdict under each. The character sets this
     * stage enforces are not configuration-dependent, so relaxing the configuration must not
     * relax them.
     */
    private static final SecurityConfiguration[] SHARED_GATE_PRESETS = {
            SecurityConfiguration.defaults(),
            SecurityConfiguration.lenient()
    };

    @Test
    void shouldAllowNullAndEmptyValues() throws Exception {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        assertEquals(Optional.empty(), stage.validate(null));
        var result = stage.validate("");
        assertTrue(result.isPresent());
        assertEquals("", result.get());
    }

    @Test
    void shouldAllowValidPathCharacters() throws Exception {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String validPath = "/api/users/123";
        var result = stage.validate(validPath);
        assertTrue(result.isPresent());
        assertEquals(validPath, result.get());

        String complexPath = "/path/with-special_chars.txt~test!$&'()*+,;=:@";
        var complexResult = stage.validate(complexPath);
        assertTrue(complexResult.isPresent());
        assertEquals(complexPath, complexResult.get());
    }

    @Test
    void shouldAllowValidQueryCharacters() throws Exception {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.PARAMETER_NAME);

        String validParam = "userName123";
        var result = stage.validate(validParam);
        assertTrue(result.isPresent());
        assertEquals(validParam, result.get());

        String complexParam = "param_name-with.special~chars!$'()*+,;";
        var complexResult = stage.validate(complexParam);
        assertTrue(complexResult.isPresent());
        assertEquals(complexParam, complexResult.get());
    }

    /**
     * RFC 3986 section 3.4 lists {@code /} and {@code ?} as legal query characters and {@code pchar}
     * admits {@code :} and {@code @}. Browsers send all three unencoded, so the previous query set -
     * which omitted them - rejected RFC-legal input.
     */
    @ParameterizedTest
    @ValueSource(strings = {"a/b", "a:b", "a@b", "https://example.com/cb?x=1"})
    void shouldAcceptRfc3986QueryCharactersInParameterValue(String value) throws Exception {
        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, ValidationType.PARAMETER_VALUE);

            var result = stage.validate(value);
            assertTrue(result.isPresent(), value + " should be accepted under " + preset);
            assertEquals(value, result.get(), value + " must pass through unchanged under " + preset);
        }
    }

    /**
     * RFC 6265 section 4.1.1 {@code cookie-octet} admits the whole base64 alphabet including
     * {@code +}, {@code /} and the {@code =} padding. The previous {@code RFC3986_UNRESERVED}
     * mapping rejected an ordinary padded base64 session cookie.
     */
    @Test
    void shouldAcceptPaddedBase64CookieValue() throws Exception {
        // A padded base64 session token exercising every character the old unreserved-only
        // mapping rejected: '+', '/' and the '=' padding.
        String base64Cookie = "c2Vzc2lvbi10b2tlbg+/ab+/cd==";

        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, ValidationType.COOKIE_VALUE);

            var result = stage.validate(base64Cookie);
            assertTrue(result.isPresent(), "A padded base64 cookie value should be accepted under " + preset);
            assertEquals(base64Cookie, result.get());
        }
    }

    /**
     * DQUOTE ({@code 0x22}) is not a {@code cookie-octet} member, so it is rejected wherever it
     * appears - there is no matched-quote-pair carve-out. The quoted spelling is rejected on its
     * very first character.
     */
    @Test
    void shouldRejectDoubleQuoteInCookieValueIncludingMatchedPair() {
        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, ValidationType.COOKIE_VALUE);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    stage.validate("\"abc\""), "A matched-pair quoted cookie value must be rejected under " + preset);

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
            assertEquals(ValidationType.COOKIE_VALUE, exception.getValidationType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("at position 0"),
                    "The opening quote is the rejected character: " + exception.getDetail().get());
        }
    }

    /**
     * RFC 6265 defines {@code cookie-name} as the RFC 7230/2616 {@code token} grammar, which
     * excludes {@code =} - unlike {@code cookie-octet}, which a cookie <em>value</em> uses and
     * which admits it as base64 padding. Before this rule both {@code COOKIE_NAME} and
     * {@code COOKIE_VALUE} shared {@code cookie-octet}, so a cookie-name suffix such as
     * {@code a=b} passed validation and, once serialized as {@code name=value}, changed which
     * text is read as the name and which as the value (CWE-20). Pinned under every preset since
     * the token/cookie-octet split is not itself configuration-dependent.
     */
    @Test
    void shouldRejectEqualsSignInCookieNameUnderEveryPreset() {
        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, ValidationType.COOKIE_NAME);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    stage.validate("a=b"), "'=' in a cookie name must be rejected under " + preset);

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
            assertEquals(ValidationType.COOKIE_NAME, exception.getValidationType());
        }
    }

    /**
     * Negative control for the rule above: {@code =} is ordinary {@code cookie-octet} content in
     * a cookie <em>value</em> (base64 padding), so the token-grammar tightening of
     * {@code COOKIE_NAME} must not collaterally narrow {@code COOKIE_VALUE}.
     */
    @Test
    void shouldStillAcceptEqualsSignInCookieValue() throws Exception {
        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, ValidationType.COOKIE_VALUE);

            var result = stage.validate("a=b");
            assertTrue(result.isPresent(), "'=' should remain accepted in a cookie value under " + preset);
            assertEquals("a=b", result.get());
        }
    }

    /**
     * The raw counterpart of the decoded rule asserted by
     * {@code DecodingStageTest.shouldRejectDecodedHashInParameterName}: {@code #} terminates the
     * query component, so it is rejected in a parameter <em>name</em> in both its raw spelling
     * (here, by the wire-form character set) and its {@code %23} spelling (there, after decoding).
     * Both verdicts are {@link UrlSecurityFailureType#INVALID_CHARACTER}.
     */
    @Test
    void shouldRejectRawHashInParameterName() {
        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, ValidationType.PARAMETER_NAME);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    stage.validate("na#me"), "A raw '#' in a parameter name must be rejected under " + preset);

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                    "The raw and decoded spellings must share a failure type under " + preset);
            assertEquals(ValidationType.PARAMETER_NAME, exception.getValidationType());
        }
    }

    /**
     * The C1 range (128-159) is rejected unconditionally, before the {@code allowExtendedAscii}
     * decision - so {@code lenient()}, which enables that flag, reaches the same verdict as
     * {@code defaults()} (ADR-0017). U+0085 (NEL) is the motivating case: several parsers treat it
     * as a line terminator, so admitting it into a header value via an "extended ASCII" opt-in
     * would smuggle a control character past the {@code ch <= 31} branch.
     */
    @Test
    void shouldRejectC1ControlCharactersInHeaderValueUnderEveryPreset() {
        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, ValidationType.HEADER_VALUE);

            // Built by code point rather than written literally: U+0085 is invisible in source.
            String withNel = "value" + (char) 0x85 + "next";

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    stage.validate(withNel), "U+0085 must be rejected under " + preset);

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                    "The C1 verdict must not depend on allowExtendedAscii");
            assertEquals(ValidationType.HEADER_VALUE, exception.getValidationType());
        }
    }

    /**
     * U+2028 LINE SEPARATOR sits above 255, so it is governed by {@code allowExtendedAscii}'s
     * second blast radius rather than by the unconditional C1 rule. Under {@code defaults()} the
     * flag is now {@code false}, which makes {@code HEADER_VALUE} ASCII-only and rejects it.
     */
    @Test
    void shouldRejectLineSeparatorInHeaderValueUnderDefaults() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.HEADER_VALUE);

        // Built by code point rather than written literally: U+2028 is invisible in source.
        String withLineSeparator = "value" + (char) 0x2028 + "next";

        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(withLineSeparator));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        assertEquals(ValidationType.HEADER_VALUE, exception.getValidationType());
    }

    /**
     * The other half of the flipped default: a header value carrying ordinary non-ASCII text is
     * rejected under {@code defaults()} and accepted only when the integrator opts in. This is the
     * documented breaking behaviour change, pinned so it cannot regress silently in either
     * direction.
     */
    @Test
    void shouldGateAllUnicodeAbove255InHeaderValueOnTheFlag() throws Exception {
        CharacterValidationStage byDefault = new CharacterValidationStage(config, ValidationType.HEADER_VALUE);
        assertThrows(UrlSecurityException.class, () -> byDefault.validate("café 你好"),
                "Non-ASCII header content is rejected under the fail-secure default");

        SecurityConfiguration optedIn = SecurityConfiguration.builder()
                .allowExtendedAscii(true)
                .build();
        CharacterValidationStage byOptIn = new CharacterValidationStage(optedIn, ValidationType.HEADER_VALUE);

        var result = byOptIn.validate("café 你好");
        assertTrue(result.isPresent(), "An explicit opt-in restores non-ASCII header content");
        assertEquals("café 你好", result.get());
    }

    /**
     * Every C0 control except the type-legal whitespace is rejected in a header or cookie name and
     * value regardless of {@code allowControlCharacters} - so {@code lenient()}, which enables that
     * flag, reaches the same verdict as {@code defaults()} (ADR-0017).
     *
     * <p>{@code HTTPHeaderValidationPipeline} composes no {@code DecodingStage}, and no pipeline
     * composes a {@code DecodingStage} with a cookie type either, so this stage is the sole
     * character guard for both: a VT admitted here would reach the application with no downstream
     * re-check.</p>
     *
     * <p>Header types report {@link UrlSecurityFailureType#INVALID_CHARACTER} (RFC 7230 treats a
     * rejected header character as simply invalid), while cookie types report the more specific
     * {@link UrlSecurityFailureType#CONTROL_CHARACTERS} - the same split already applied to every
     * other non-header validation type.</p>
     */
    @ParameterizedTest
    @EnumSource(value = ValidationType.class, names = {"HEADER_NAME", "HEADER_VALUE", "COOKIE_NAME", "COOKIE_VALUE"})
    void shouldRejectC0ControlCharactersInHeadersAndCookiesUnderEveryPreset(ValidationType type) {
        String withVerticalTab = "head" + (char) 0x0B + "er";
        boolean isHeaderType = type == ValidationType.HEADER_NAME || type == ValidationType.HEADER_VALUE;
        UrlSecurityFailureType expectedFailureType = isHeaderType
                ? UrlSecurityFailureType.INVALID_CHARACTER
                : UrlSecurityFailureType.CONTROL_CHARACTERS;

        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, type);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                            stage.validate(withVerticalTab),
                    "VT (0x0B) must be rejected in a " + type + " under " + preset);

            assertEquals(expectedFailureType, exception.getFailureType(),
                    "The C0 verdict must not depend on allowControlCharacters");
            assertEquals(type, exception.getValidationType());
        }
    }

    /**
     * Positive control for the rule above: the widened rejection runs AFTER the character-set
     * allowance, so HTAB - which {@code RFC7230_HEADER_CHARS} admits - is still accepted in a
     * header value. Without this the rule could pass by rejecting all C0 controls indiscriminately.
     */
    @Test
    void shouldStillAcceptHorizontalTabInHeaderValue() throws Exception {
        String withTab = "value\twith\ttabs";

        for (SecurityConfiguration preset : SHARED_GATE_PRESETS) {
            CharacterValidationStage stage = new CharacterValidationStage(preset, ValidationType.HEADER_VALUE);

            var result = stage.validate(withTab);
            assertTrue(result.isPresent(), "HTAB is header-legal and must survive under " + preset);
            assertEquals(withTab, result.get());
        }
    }

    @Test
    void shouldAllowValidHeaderCharacters() throws Exception {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.HEADER_NAME);

        String validHeader = "X-Custom-Header";
        var result = stage.validate(validHeader);
        assertTrue(result.isPresent());
        assertEquals(validHeader, result.get());

        String headerWithNumbers = "Header123";
        var numbersResult = stage.validate(headerWithNumbers);
        assertTrue(numbersResult.isPresent());
        assertEquals(headerWithNumbers, numbersResult.get());
    }

    @Test
    void shouldAllowSpaceInHeaderValues() throws Exception {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.HEADER_VALUE);

        String headerValue = "Mozilla/5.0 Chrome Safari";
        var result = stage.validate(headerValue);
        assertTrue(result.isPresent());
        assertEquals(headerValue, result.get());
    }

    @Test
    void shouldRejectNullByteInjection() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String maliciousPath = "/path/with\0null/byte";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(maliciousPath));

        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Null byte detected at position 10"));
    }

    @Test
    void shouldRejectEncodedNullByte() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String maliciousPath = "/path/with%00null/byte";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(maliciousPath));

        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Encoded null byte (%00) detected at position 10"));
    }

    @Test
    void shouldRejectInvalidCharacters() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String pathWithInvalidChar = "/path/with spaces/invalid";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(pathWithInvalidChar));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Invalid character ' ' (0x20) at position 10"));
    }

    @Test
    void shouldEscapeControlCharactersInDetailMessage() {
        // A raw CR reaching handleInvalidCharacter must be rendered as its escaped U+XXXX form,
        // never verbatim, so it cannot forge or inject log lines through the exception message.
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                () -> stage.validate("/api\rvalue"));

        assertTrue(exception.getDetail().isPresent());
        String detail = exception.getDetail().get();
        assertTrue(detail.contains("U+000D"), "control char must be hex-escaped: " + detail);
        assertFalse(detail.contains("\r"), "raw CR must not appear in the detail message");
    }

    @Test
    void shouldRejectInvalidEncodingFormat() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        // Incomplete percent encoding
        String incompleteEncoding = "/path/with%2";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(incompleteEncoding));

        assertEquals(UrlSecurityFailureType.INVALID_ENCODING, exception.getFailureType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Incomplete percent encoding at position 10"));
    }

    @Test
    void shouldRejectInvalidHexDigits() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String invalidHex = "/path/with%2G";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(invalidHex));

        assertEquals(UrlSecurityFailureType.INVALID_ENCODING, exception.getFailureType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Invalid hex digits in percent encoding at position 10"));
    }

    @Test
    void shouldAllowValidPercentEncoding() throws Exception {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String encodedPath = "/path/with%20encoded%2Fchars";
        var encodedResult = stage.validate(encodedPath);
        assertTrue(encodedResult.isPresent());
        assertEquals(encodedPath, encodedResult.get());

        String upperCaseHex = "/path/with%2A%2B";
        var upperResult = stage.validate(upperCaseHex);
        assertTrue(upperResult.isPresent());
        assertEquals(upperCaseHex, upperResult.get());

        String lowerCaseHex = "/path/with%2a%2b";
        var lowerResult = stage.validate(lowerCaseHex);
        assertTrue(lowerResult.isPresent());
        assertEquals(lowerCaseHex, lowerResult.get());
    }

    @Test
    void shouldNotAllowPercentEncodingInHeaders() throws Exception {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.HEADER_NAME);

        // Headers should allow % character, but percent sequences are not decoded
        // So %20 should be treated as literal characters, which is allowed
        String headerWithEncoding = "Header%20Name";

        var result = stage.validate(headerWithEncoding);
        assertTrue(result.isPresent());
        assertEquals(headerWithEncoding, result.get(),
                "A percent sequence in a header name is literal text and must pass through unchanged");

        // Test with a character that's actually not allowed in headers (control character)
        String headerWithControlChar = "Header\u0001Name";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(headerWithControlChar));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("Invalid character"));
    }

    @ParameterizedTest
    @EnumSource(ValidationType.class)
    void shouldHandleAllValidationTypes(ValidationType type) throws Exception {
        CharacterValidationStage stage = new CharacterValidationStage(config, type);

        // Basic alphanumeric is valid for every validation type
        var result = stage.validate("abc123");
        assertTrue(result.isPresent());
        assertEquals("abc123", result.get(),
                "Alphanumeric input must pass through unchanged for " + type);

        // Should reject null byte for all types
        assertThrows(UrlSecurityException.class, () -> stage.validate("test\0null"));
    }

    /**
     * The stage classifies by full code point ({@code codePointAt} plus {@code charCount}), not by
     * individual {@code char}. A supplementary-plane character therefore reaches
     * {@code isCharacterAllowed} as one code point above 255 and is rejected for a URL path, which
     * is ASCII-only per RFC 3986.
     * <p>
     * The detail assertions are what make this test sensitive to the iteration strategy: under
     * naive {@code charAt} iteration the reported character would be the {@code D835} high-surrogate
     * half rather than the {@code 1D400} code point, so this test fails if that regression is
     * introduced.
     */
    @Test
    void shouldRejectSupplementaryPlaneCharacterByCodePoint() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        // U+1D400 MATHEMATICAL BOLD CAPITAL A, encoded as the surrogate pair D835 DC00.
        String path = "/api/𝐀/next";

        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(path));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertTrue(exception.getDetail().isPresent());
        String detail = exception.getDetail().get();
        assertAll("The whole code point is reported, not a surrogate half",
                () -> assertTrue(detail.contains("0x1D400"),
                        "expected the full code point U+1D400 in: " + detail),
                () -> assertFalse(detail.contains("0xD835"),
                        "a surrogate half must not be reported as the offending character: " + detail));
    }

    /**
     * An unpaired surrogate is not a valid code point, but it is a reachable input: a caller can
     * hand the stage a {@code String} containing a high surrogate with no low surrogate following.
     * {@code codePointAt} yields the surrogate value itself, which is above 255 and so is rejected
     * for a URL path. This pins the behaviour as a clean rejection rather than an exception escaping
     * from the code-point machinery.
     */
    @Test
    void shouldRejectUnpairedSurrogate() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        // High surrogate with no low surrogate following it.
        String path = "/api/\uD800/next";

        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(path));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        assertEquals(ValidationType.URL_PATH, exception.getValidationType());
        assertTrue(exception.getDetail().isPresent());
        assertTrue(exception.getDetail().get().contains("0xD800"),
                "expected the unpaired surrogate to be reported: " + exception.getDetail().get());
    }

    @Test
    void shouldContinueAfterNullByteWhenAllowed() {
        SecurityConfiguration permissiveConfig = SecurityConfiguration.builder()
                .allowNullBytes(true)
                .build();
        CharacterValidationStage stage = new CharacterValidationStage(permissiveConfig, ValidationType.URL_PATH);

        // Null byte in middle: should skip it and continue processing remaining characters
        String input = "/valid\0/path";
        var result = assertDoesNotThrow(() -> stage.validate(input));
        assertTrue(result.isPresent());
        assertEquals(input, result.get());
    }

    @Test
    void shouldProcessMultipleNullBytesWhenAllowed() {
        SecurityConfiguration permissiveConfig = SecurityConfiguration.builder()
                .allowNullBytes(true)
                .build();
        CharacterValidationStage stage = new CharacterValidationStage(permissiveConfig, ValidationType.URL_PATH);

        // Multiple null bytes: should skip all and continue
        String input = "/a\0b\0c";
        var result = assertDoesNotThrow(() -> stage.validate(input));
        assertTrue(result.isPresent());
        assertEquals(input, result.get());
    }

    @Test
    void shouldRejectHighUnicodeCharacters() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);

        String unicodePath = "/path/with/unicode/字符";
        UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                stage.validate(unicodePath));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
        CharacterValidationStage stage1 = new CharacterValidationStage(config, ValidationType.URL_PATH);
        CharacterValidationStage stage2 = new CharacterValidationStage(config, ValidationType.URL_PATH);
        CharacterValidationStage stage3 = new CharacterValidationStage(config, ValidationType.PARAMETER_NAME);

        assertEquals(stage1, stage2);
        assertEquals(stage1.hashCode(), stage2.hashCode());
        assertNotEquals(stage1, stage3);
    }

    @Test
    void shouldHaveCorrectToString() {
        CharacterValidationStage stage = new CharacterValidationStage(config, ValidationType.URL_PATH);
        String toString = stage.toString();

        assertTrue(toString.contains("CharacterValidationStage"));
        assertTrue(toString.contains("URL_PATH"));
    }

    @Test
    void shouldRejectNonAsciiCharactersInHeaderNamesRegardlessOfConfig() {
        // RFC 7230: Header field names are tokens consisting of ASCII characters 33-126
        // This test reproduces the security issue where extended ASCII could be incorrectly allowed

        // Test with allowExtendedAscii = true (this should NOT affect header names)
        SecurityConfiguration configWithExtended = SecurityConfiguration.builder()
                .allowExtendedAscii(true)
                .build();

        CharacterValidationStage headerNameStage = new CharacterValidationStage(
                configWithExtended, ValidationType.HEADER_NAME);

        // Extended ASCII characters (128-255) should ALWAYS be rejected in header names
        String headerWithExtendedAscii = "X-Custom-Heäder"; // ä = U+00E4 (228)

        // This test will FAIL if the bug exists (extended ASCII incorrectly allowed)
        // and PASS if the bug is fixed (extended ASCII correctly rejected)
        assertThrows(UrlSecurityException.class,
                () -> headerNameStage.validate(headerWithExtendedAscii),
                "SECURITY BUG: Header names must reject extended ASCII even when allowExtendedAscii=true");

        // Test with Latin-1 supplement character
        String headerWithLatin1 = "Content-Typé"; // é = U+00E9 (233)
        assertThrows(UrlSecurityException.class,
                () -> headerNameStage.validate(headerWithLatin1),
                "Header names must reject Latin-1 supplement characters");

        // Test with high-bit character
        String headerWithHighBit = "X-Test\u00FF"; // ÿ = U+00FF (255)
        assertThrows(UrlSecurityException.class,
                () -> headerNameStage.validate(headerWithHighBit),
                "Header names must reject all non-ASCII characters");

        // Verify that regular ASCII header names still work
        String validHeader = "X-Custom-Header";
        var result = assertDoesNotThrow(() -> headerNameStage.validate(validHeader));
        assertTrue(result.isPresent());
        assertEquals(validHeader, result.get());
    }

    @Test
    void shouldHandleUnicodeCharactersConsistentlyWithAllowHighBitCharacters() {
        // Test with allowHighBitCharacters = true (default)
        SecurityConfiguration configWithHighBit = SecurityConfiguration.builder()
                .allowExtendedAscii(true)
                .build();

        CharacterValidationStage pathStage = new CharacterValidationStage(configWithHighBit, ValidationType.URL_PATH);
        CharacterValidationStage paramNameStage = new CharacterValidationStage(configWithHighBit, ValidationType.PARAMETER_NAME);
        CharacterValidationStage paramValueStage = new CharacterValidationStage(configWithHighBit, ValidationType.PARAMETER_VALUE);

        // Test extended ASCII (128-255) - should be allowed with allowHighBitCharacters=true
        String extendedAscii = "test\u00E9"; // é (U+00E9)
        assertDoesNotThrow(() -> pathStage.validate(extendedAscii),
                "Extended ASCII should be allowed in URL_PATH when allowHighBitCharacters=true");
        assertDoesNotThrow(() -> paramNameStage.validate(extendedAscii),
                "Extended ASCII should be allowed in PARAMETER_NAME when allowHighBitCharacters=true");
        assertDoesNotThrow(() -> paramValueStage.validate(extendedAscii),
                "Extended ASCII should be allowed in PARAMETER_VALUE when allowHighBitCharacters=true");

        // Test Unicode above 255 - currently rejected even with allowHighBitCharacters=true
        // This is the confusing behavior that needs clarification
        String unicode = "test中文"; // Chinese characters

        // Current behavior: Unicode > 255 is rejected for paths/parameters even with allowHighBitCharacters=true
        // This is confusing because the name "allowHighBitCharacters" implies it would allow these
        assertThrows(UrlSecurityException.class, () -> pathStage.validate(unicode),
                "Unicode > 255 is currently rejected in URL_PATH even with allowHighBitCharacters=true");
        assertThrows(UrlSecurityException.class, () -> paramNameStage.validate(unicode),
                "Unicode > 255 is currently rejected in PARAMETER_NAME even with allowHighBitCharacters=true");
        assertThrows(UrlSecurityException.class, () -> paramValueStage.validate(unicode),
                "Unicode > 255 is currently rejected in PARAMETER_VALUE even with allowHighBitCharacters=true");

        // Test with allowHighBitCharacters = false
        SecurityConfiguration configWithoutHighBit = SecurityConfiguration.builder()
                .allowExtendedAscii(false)
                .build();

        CharacterValidationStage restrictedStage = new CharacterValidationStage(configWithoutHighBit, ValidationType.URL_PATH);

        // Both extended ASCII and Unicode should be rejected
        assertThrows(UrlSecurityException.class, () -> restrictedStage.validate(extendedAscii),
                "Extended ASCII should be rejected when allowHighBitCharacters=false");
        assertThrows(UrlSecurityException.class, () -> restrictedStage.validate(unicode),
                "Unicode should be rejected when allowHighBitCharacters=false");
    }
}
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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.data.Cookie;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.cookie.*;
import de.cuioss.http.security.validation.CharacterValidationStage;
import de.cuioss.http.security.validation.CookiePrefixValidationStage;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for Cookie Chaos attacks based on PortSwigger research.
 *
 * <p>
 * This test class validates protection against cookie security vulnerabilities
 * documented in "Cookie Chaos: How to Bypass Host and Secure Cookie Prefixes"
 * (https://portswigger.net/research/cookie-chaos-how-to-bypass-host-and-secure-cookie-prefixes).
 * </p>
 *
 * <h3>Attack Vectors Tested</h3>
 * <ul>
 *   <li>Unicode whitespace injection in cookie names</li>
 *   <li>Zero-width character injection in cookie names</li>
 *   <li>Leading/trailing whitespace in cookie names</li>
 *   <li>Legacy parsing trigger detection ($Version=1)</li>
 *   <li>Cookie prefix validation (__Host-, __Secure-)</li>
 *   <li>Cookie attribute validation against prefix requirements</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>RFC 6265bis - Cookie Prefixes (__Host-, __Secure-)</li>
 *   <li>RFC 6265 - HTTP State Management Mechanism</li>
 *   <li>CWE-565: Reliance on Cookies without Validation and Integrity Checking</li>
 *   <li>CWE-614: Sensitive Cookie in HTTPS Session Without 'Secure' Attribute</li>
 * </ul>
 *
 * <h3>Implementation Notes</h3>
 * <p>
 * Tests #1 to #4 and #7 to #9 exercise the character-level validation of cookie names and
 * values through {@link CharacterValidationStage}. Tests #5 and #6 exercise the cookie prefix
 * semantics (for example, a {@code __Host-} cookie must not carry a Domain attribute) through
 * {@link CookiePrefixValidationStage#validateCookie(Cookie)}, which judges name, value and
 * attributes together and reports a violated prefix rule as
 * {@link UrlSecurityFailureType#COOKIE_PREFIX_VIOLATION}.
 * </p>
 *
 * @see <a href="https://portswigger.net/research/cookie-chaos-how-to-bypass-host-and-secure-cookie-prefixes">
 *      Cookie Chaos Research</a>
 * @see de.cuioss.http.security.data.Cookie
 * @see de.cuioss.http.security.validation.CharacterValidationStage
 * @see de.cuioss.http.security.validation.CookiePrefixValidationStage
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("Cookie Chaos: Cookie Security Bypass Attack Tests")
class CookieChaosAttackTest {

    /**
     * Draws for the attack-cookie test. The rarest name is one of six within one of four name
     * families, so 200 draws reach every family with certainty for practical purposes.
     */
    private static final int ATTACK_COOKIE_DRAWS = 200;

    /** The characters of the attack cookie names and values that are C0 controls. */
    private static final String C0_CONTROLS = "\t\r\n";

    /**
     * The printable or non-ASCII characters of the attack cookie values that the RFC 6265
     * {@code cookie-octet} set does not admit: space, semicolon, comma and the two bidi overrides
     * (U+202E, U+202D). The overrides are named by code point so the source carries no
     * bidirectional character.
     */
    private static final String NON_COOKIE_OCTET_CHARACTERS =
            " ;," + Character.toString(0x202E) + Character.toString(0x202D);

    private CharacterValidationStage cookieNameValidator;
    private CharacterValidationStage cookieValueValidator;
    private CookiePrefixValidationStage cookiePrefixValidator;
    private SecurityConfiguration config;

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.builder()
                .allowControlCharacters(false)
                .allowExtendedAscii(false)  // Cookie names must be ASCII-only per RFC
                .allowNullBytes(false)
                .build();
        cookieNameValidator = new CharacterValidationStage(config, ValidationType.COOKIE_NAME);
        cookieValueValidator = new CharacterValidationStage(config, ValidationType.COOKIE_VALUE);
        cookiePrefixValidator = new CookiePrefixValidationStage(config);
    }

    /**
     * Test #1: Unicode Whitespace Injection in Cookie Names
     *
     * <p>
     * Attack: Prefix cookie names with Unicode whitespace to bypass browser
     * cookie prefix validation. Browsers may accept these but servers may
     * strip whitespace, causing security validation to happen after normalization.
     * </p>
     *
     * <p>
     * Example: "\u2000__Host-session" looks different to browser but becomes
     * "__Host-session" after normalization, bypassing prefix requirements.
     * </p>
     *
     * <p>
     * Uses generator to produce combinations of 13 Unicode whitespace types
     * (multibyte and single-byte) combined with __Host- and __Secure- prefixed
     * cookie names in leading, trailing, and both positions.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameUnicodeWhitespaceGenerator.class, count = 50)
    @DisplayName("Attack #1: Unicode whitespace injection in cookie names must be rejected")
    void shouldRejectUnicodeWhitespaceInCookieName(String maliciousName) {
        assertCarriesCookiePrefix(maliciousName);

        var exception = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(maliciousName),
                "Unicode space in cookie name should be rejected: " + getDisplayableString(maliciousName));

        assertEquals(maliciousName, exception.getOriginalInput());
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "A non-ASCII whitespace character is outside the cookie-name token set");
    }

    /**
     * Test #2: Zero-Width Character Injection
     *
     * <p>
     * Attack: Inject zero-width characters into cookie names to bypass
     * string matching and hide malicious prefixes.
     * </p>
     *
     * <p>
     * Zero-width characters are invisible but can alter the semantic
     * meaning of cookie names and bypass security checks.
     * </p>
     *
     * <p>
     * Uses generator to produce 4 zero-width character types injected at
     * 6 different positions within cookie names.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameZeroWidthGenerator.class, count = 30)
    @DisplayName("Attack #2: Zero-width characters in cookie names must be rejected")
    void shouldRejectZeroWidthCharactersInCookieName(String name) {
        var exception = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(name),
                "Zero-width character should be rejected: " + getDisplayableString(name));

        assertEquals(name, exception.getOriginalInput());
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "A zero-width character is outside the cookie-name token set");
    }

    /**
     * Test #3: Leading and Trailing Whitespace
     *
     * <p>
     * Attack: Add leading/trailing whitespace to cookie names to bypass
     * browser validation. Servers may normalize by trimming, causing
     * security checks to happen after the fact.
     * </p>
     *
     * <p>
     * Uses generator to produce combinations of space/tab characters in
     * leading, trailing, both, and doubled positions. Each draw decorates with one kind of
     * whitespace only, and the kind decides the verdict: a tab is a C0 control and is reported as
     * {@code CONTROL_CHARACTERS}, a space is a printable character outside the token set and is
     * reported as {@code INVALID_CHARACTER}.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameAsciiWhitespaceGenerator.class, count = 40)
    @DisplayName("Attack #3: Leading/trailing ASCII whitespace must be rejected")
    void shouldRejectLeadingTrailingWhitespaceInCookieName(String invalidName) {
        assertCarriesCookiePrefix(invalidName);

        var exception = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(invalidName),
                "Whitespace in cookie name should be rejected: '" + invalidName + "'");

        UrlSecurityFailureType expected = invalidName.indexOf('\t') >= 0
                ? UrlSecurityFailureType.CONTROL_CHARACTERS
                : UrlSecurityFailureType.INVALID_CHARACTER;
        assertEquals(invalidName, exception.getOriginalInput());
        assertEquals(expected, exception.getFailureType(),
                () -> "Unexpected verdict for: " + getDisplayableString(invalidName));
    }

    /**
     * Test #4: Legacy Parsing Trigger Detection
     *
     * <p>
     * Attack: Use "$Version=1" prefix to trigger legacy cookie parsing in
     * Java servlet containers (Apache Tomcat, Jetty). Legacy parsers may not
     * enforce modern cookie prefix requirements.
     * </p>
     *
     * <p>
     * Note: The library validates before sending, so applications creating
     * cookies with this pattern should be blocked. Server-side parsing is
     * outside the library's scope.
     * </p>
     *
     * <p>
     * Uses generator to produce $Version=1 and $Version=2 patterns with
     * different separators (comma, semicolon, space) combined with cookie prefixes.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameLegacyParsingGenerator.class, count = 20)
    @DisplayName("Attack #4: Legacy parsing triggers must be rejected")
    void shouldRejectLegacyParsingTriggers(String legacyName) {
        var exception = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(legacyName),
                "Legacy parsing trigger should be rejected: " + legacyName);

        assertEquals(legacyName, exception.getOriginalInput());
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                "The '=' of $Version=1 is outside the cookie-name token set");
    }

    /**
     * Test #5: Cookie Prefix Validation - __Host- Requirements
     *
     * <p>
     * {@link CookiePrefixValidationStage#validateCookie(Cookie)} enforces the three
     * {@code __Host-} rules: the cookie must carry the Secure attribute, must not carry a Domain
     * attribute, and must carry {@code Path=/}. The prefix token is matched ASCII
     * case-insensitively, so a case variation cannot slip past the rules.
     * </p>
     *
     * @param cookieName the {@code __Host-} prefixed cookie name
     * @param attributes attributes that violate one of the {@code __Host-} rules
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            __Host-session | Domain=.example.com; Secure; Path=/
            __Host-session | Path=/
            __Host-session | Secure; Path=/admin
            __Host-session | Secure
            __host-session | Path=/
            __HOST-session | Domain=example.com; Secure; Path=/
            """)
    @DisplayName("Test #5: a __Host- cookie violating a prefix rule is rejected")
    void shouldRejectHostPrefixViolation(String cookieName, String attributes) {
        Cookie violating = new Cookie(cookieName, "value", attributes);

        var exception = assertThrows(UrlSecurityException.class,
                () -> cookiePrefixValidator.validateCookie(violating),
                () -> "A __Host- cookie with attributes '" + attributes + "' violates the prefix rules");

        assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
        assertEquals(cookieName, exception.getOriginalInput());
    }

    /**
     * Test #5: Cookie Prefix Validation - a conforming __Host- cookie
     *
     * <p>
     * A {@code __Host-} cookie that is Secure, carries no Domain attribute and has
     * {@code Path=/} satisfies every prefix rule and is accepted.
     * </p>
     */
    @Test
    @DisplayName("Test #5: a conforming __Host- cookie is accepted")
    void shouldAcceptConformingHostPrefixCookie() {
        Cookie conforming = new Cookie("__Host-session", "value", "Secure; Path=/");

        assertDoesNotThrow(() -> cookiePrefixValidator.validateCookie(conforming));
    }

    /**
     * Test #6: Cookie Prefix Validation - __Secure- Requirements
     *
     * <p>
     * {@link CookiePrefixValidationStage#validateCookie(Cookie)} enforces the single
     * {@code __Secure-} rule: the cookie must carry the Secure attribute, which ensures
     * HTTPS-only transmission. Domain and Path are unconstrained for this prefix.
     * </p>
     *
     * @param cookieName the {@code __Secure-} prefixed cookie name
     * @param attributes attributes that lack the Secure attribute
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            __Secure-session | Domain=example.com; Path=/
            __Secure-session | Path=/
            __secure-session | Domain=example.com; Path=/
            __SECURE-token   | HttpOnly
            """)
    @DisplayName("Test #6: a __Secure- cookie without the Secure attribute is rejected")
    void shouldRejectSecurePrefixViolation(String cookieName, String attributes) {
        Cookie violating = new Cookie(cookieName, "value", attributes);

        var exception = assertThrows(UrlSecurityException.class,
                () -> cookiePrefixValidator.validateCookie(violating),
                () -> "A __Secure- cookie with attributes '" + attributes + "' lacks the Secure attribute");

        assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
        assertEquals(cookieName, exception.getOriginalInput());
    }

    /**
     * Test #6: Cookie Prefix Validation - a conforming __Secure- cookie
     *
     * <p>
     * A {@code __Secure-} cookie that carries the Secure attribute is accepted, with or without
     * a Domain attribute.
     * </p>
     *
     * @param attributes attributes that include the Secure attribute
     */
    @ParameterizedTest
    @ValueSource(strings = {"Secure; Domain=example.com; Path=/", "Secure"})
    @DisplayName("Test #6: a conforming __Secure- cookie is accepted")
    void shouldAcceptConformingSecurePrefixCookie(String attributes) {
        Cookie conforming = new Cookie("__Secure-session", "value", attributes);

        assertDoesNotThrow(() -> cookiePrefixValidator.validateCookie(conforming));
    }

    /**
     * Test #7: Valid Cookie Names Should Pass
     *
     * <p>
     * Verify that legitimate cookie names without attack patterns are accepted.
     * Uses generator for valid cookie patterns to ensure no false positives.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = ValidCookieGenerator.class, count = 20)
    @DisplayName("Test #7: Valid cookies should be accepted")
    void shouldAcceptValidCookies(Cookie validCookie) {
        // ValidCookieGenerator always emits a non-blank name and value, so guarding these
        // acceptance checks would let a degenerate-cookie regression pass vacuously.
        assertTrue(validCookie.hasName(), "ValidCookieGenerator must emit a cookie carrying a name");
        assertTrue(validCookie.hasValue(), "ValidCookieGenerator must emit a cookie carrying a value");

        assertDoesNotThrow(() -> cookieNameValidator.validate(validCookie.name()),
                "Valid cookie name should be accepted: " + validCookie.name());
        assertDoesNotThrow(() -> cookieValueValidator.validate(validCookie.value()),
                "Valid cookie value should be accepted: " + validCookie.value());
    }

    /**
     * Test #8: Attack Cookie Generator Patterns
     *
     * <p>
     * Every name and every value {@link AttackCookieGenerator} emits has exactly one verdict
     * under character validation, and each draw asserts it. The verdict follows from the first
     * character the RFC 6265 grammar does not admit: a null byte is
     * {@code NULL_BYTE_INJECTION}, a tab, CR or LF is {@code CONTROL_CHARACTERS}, any other
     * character outside the grammar is {@code INVALID_CHARACTER}. A name or value made of
     * grammar characters only is accepted - character validation judges the grammar, not the
     * meaning, so a syntactically clean payload such as {@code javascript:alert(1)} passes it.
     * </p>
     *
     * @param attackCookie a cookie drawn from the attack generator
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = AttackCookieGenerator.class, count = ATTACK_COOKIE_DRAWS)
    @DisplayName("Test #8: every attack cookie name and value has its exact character verdict")
    void shouldGiveAttackCookieItsExactCharacterVerdict(Cookie attackCookie) {
        assertTrue(attackCookie.hasValue(), "AttackCookieGenerator must emit a cookie carrying a value");

        assertCharacterVerdict(cookieNameValidator, attackCookie.name(), expectedNameVerdict(attackCookie.name()));
        assertCharacterVerdict(cookieValueValidator, attackCookie.value(),
                expectedValueVerdict(attackCookie.value()));
    }

    /**
     * The verdict of the cookie-name character validation for an attack cookie name: the control
     * names carry a tab, CR or LF; the token names (pipe, apostrophe and the very long name)
     * consist of token characters only; every other attack name carries a printable separator.
     */
    private static Optional<UrlSecurityFailureType> expectedNameVerdict(String name) {
        if (containsAnyOf(name, C0_CONTROLS)) {
            return Optional.of(UrlSecurityFailureType.CONTROL_CHARACTERS);
        }
        boolean tokenOnly = "cookie|pipe".equals(name) || "cookie'apostrophe".equals(name)
                || name.startsWith("very_long_cookie_name_");
        return tokenOnly ? Optional.empty() : Optional.of(UrlSecurityFailureType.INVALID_CHARACTER);
    }

    /**
     * The verdict of the cookie-value character validation for an attack cookie value. Every
     * attack value that carries a control character carries it ahead of any other inadmissible
     * character, so the control verdict takes precedence.
     */
    private static Optional<UrlSecurityFailureType> expectedValueVerdict(String value) {
        if (value.indexOf('\0') >= 0) {
            return Optional.of(UrlSecurityFailureType.NULL_BYTE_INJECTION);
        }
        if (containsAnyOf(value, C0_CONTROLS)) {
            return Optional.of(UrlSecurityFailureType.CONTROL_CHARACTERS);
        }
        if (containsAnyOf(value, NON_COOKIE_OCTET_CHARACTERS)) {
            return Optional.of(UrlSecurityFailureType.INVALID_CHARACTER);
        }
        return Optional.empty();
    }

    private static boolean containsAnyOf(String input, String characters) {
        return input.chars().anyMatch(character -> characters.indexOf(character) >= 0);
    }

    /**
     * Asserts the one verdict the validator must reach for the input: rejection with the given
     * failure type, or acceptance of the unchanged input when no failure type is expected.
     *
     * @param validator the character validation stage to apply
     * @param input the cookie name or value to validate
     * @param expectedFailure the failure type the validator must report, or empty for acceptance
     */
    private void assertCharacterVerdict(CharacterValidationStage validator, String input,
            Optional<UrlSecurityFailureType> expectedFailure) {
        if (expectedFailure.isPresent()) {
            var exception = assertThrows(UrlSecurityException.class, () -> validator.validate(input),
                    () -> "Should be rejected: " + getDisplayableString(input));
            assertEquals(expectedFailure.get(), exception.getFailureType(),
                    () -> "Unexpected verdict for: " + getDisplayableString(input));
        } else {
            Optional<String> validated = assertDoesNotThrow(() -> validator.validate(input),
                    () -> "Should be accepted: " + getDisplayableString(input));
            assertEquals(Optional.of(input), validated,
                    () -> "Accepted input must be returned unchanged: " + getDisplayableString(input));
        }
    }

    /**
     * Test #9: Cookie Data Structure Accepts All Values
     *
     * <p>
     * The Cookie record is a data structure that accepts any values.
     * Validation must be performed separately using validators.
     * This test documents this design decision.
     * </p>
     */
    @Test
    @DisplayName("Test #9: Cookie record accepts all values (validation is separate)")
    void shouldDocumentCookieRecordAcceptsAllValues() {
        // Cookie record is a pure data holder - it accepts any values
        Cookie withUnicodeSpace = new Cookie("\u2000__Host-session", "value", "");
        Cookie withZeroWidth = new Cookie("__Host\u200B-session", "value", "");
        Cookie withWhitespace = new Cookie(" session ", "value", "");
        Cookie withLegacy = new Cookie("$Version=1,session", "value", "");

        // All are accepted by the record
        assertNotNull(withUnicodeSpace.name());
        assertNotNull(withZeroWidth.name());
        assertNotNull(withWhitespace.name());
        assertNotNull(withLegacy.name());

        // But validation should reject them
        String name1 = withUnicodeSpace.name();
        var ex1 = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(name1));
        assertNotNull(ex1);

        String name2 = withZeroWidth.name();
        var ex2 = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(name2));
        assertNotNull(ex2);

        String name3 = withWhitespace.name();
        var ex3 = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(name3));
        assertNotNull(ex3);

        String name4 = withLegacy.name();
        var ex4 = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(name4));
        assertNotNull(ex4);
    }

    /**
     * Asserts the prefix-bypass contract both whitespace generators declare: the name they
     * decorate is always a {@code __Host-} or {@code __Secure-} prefixed one, so every emitted
     * value exercises a prefix bypass rather than an ordinary cookie name. The decoration is
     * stripped first because the generators wrap the name in leading and trailing whitespace,
     * including non-breaking forms that {@link String#strip()} leaves in place.
     *
     * @param generatedName a name drawn from either whitespace generator
     */
    private void assertCarriesCookiePrefix(String generatedName) {
        String undecorated = stripDecoration(generatedName);
        assertTrue(CookieSecurityPrefixes.isPrefixed(undecorated),
                () -> "A prefix-bypass generator must decorate a name prefixed with one of "
                        + CookieSecurityPrefixes.ALL + ", but was: " + getDisplayableString(generatedName));
    }

    /**
     * Strips leading and trailing decoration characters with a linear scan. A regex of the shape
     * {@code ^[...]+|[...]+$} expresses the same intent but backtracks super-linearly on a run of
     * matching characters, so the scan is used instead.
     *
     * @param value the decorated name
     * @return {@code value} without its leading and trailing decoration
     */
    private static String stripDecoration(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isDecoration(value.charAt(start))) {
            start++;
        }
        while (end > start && isDecoration(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    /**
     * Matches the {@code [\s\p{Z}\p{Cc}]} class the generators decorate with. The explicit
     * separator-category checks are what cover the non-breaking forms (U+00A0, U+2007, U+202F)
     * that {@link Character#isWhitespace(char)} reports as false.
     *
     * @param character the character to classify
     * @return {@code true} when the character is whitespace, a separator, or a control character
     */
    private static boolean isDecoration(char character) {
        int type = Character.getType(character);
        return Character.isWhitespace(character)
                || type == Character.SPACE_SEPARATOR
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.CONTROL;
    }

    /**
     * Helper: Convert string with control characters to displayable format.
     *
     * @param input The string that may contain control/Unicode characters
     * @return A displayable representation showing special characters as Unicode escapes
     */
    private String getDisplayableString(String input) {
        if (input == null) {
            return "null";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Show control characters, extended ASCII, and zero-width chars as Unicode escapes
            if (Character.isISOControl(c) || c >= 0x80 && c <= 0x9F ||
                    c >= 0x2000 && c <= 0x200F || c == '\u00A0' || c == '\u0085' || c == '\uFEFF') {
                result.append("\\u%04X".formatted((int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}

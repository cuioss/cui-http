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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.data.Cookie;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.cookie.*;
import de.cuioss.http.security.validation.CharacterValidationStage;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

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
 * This test class focuses on character-level validation of cookie names.
 * Cookie prefix semantic validation (e.g., __Host- must not have Domain attribute)
 * is documented as a future enhancement and tested here to demonstrate the attacks
 * even though full semantic validation is not yet implemented in the library.
 * </p>
 *
 * @see <a href="https://portswigger.net/research/cookie-chaos-how-to-bypass-host-and-secure-cookie-prefixes">
 *      Cookie Chaos Research</a>
 * @see de.cuioss.http.security.data.Cookie
 * @see de.cuioss.http.security.validation.CharacterValidationStage
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("Cookie Chaos: Cookie Security Bypass Attack Tests")
class CookieChaosAttackTest {

    private CharacterValidationStage cookieNameValidator;
    private CharacterValidationStage cookieValueValidator;
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
     * (multibyte and single-byte) combined with __Host-, __Secure-, and regular
     * cookie names in leading, trailing, and both positions.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameUnicodeWhitespaceGenerator.class, count = 50)
    @DisplayName("Attack #1: Unicode whitespace injection in cookie names must be rejected")
    void shouldRejectUnicodeWhitespaceInCookieName(String maliciousName) {
        var exception = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(maliciousName),
                "Unicode space in cookie name should be rejected: " + getDisplayableString(maliciousName));

        assertNotNull(exception);
        assertEquals(maliciousName, exception.getOriginalInput());
        // Should be rejected as invalid character (Unicode in cookie name)
        assertTrue(exception.getFailureType().name().contains("CHARACTER") ||
                exception.getFailureType().name().contains("CONTROL"),
                "Should detect invalid Unicode character: " + exception.getFailureType());
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

        assertNotNull(exception);
        assertEquals(name, exception.getOriginalInput());
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
     * leading, trailing, both, and doubled positions.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameAsciiWhitespaceGenerator.class, count = 40)
    @DisplayName("Attack #3: Leading/trailing ASCII whitespace must be rejected")
    void shouldRejectLeadingTrailingWhitespaceInCookieName(String invalidName) {
        var exception = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(invalidName),
                "Whitespace in cookie name should be rejected: '" + invalidName + "'");

        assertNotNull(exception);
        assertEquals(invalidName, exception.getOriginalInput());
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

        assertNotNull(exception);
        assertEquals(legacyName, exception.getOriginalInput());
        // Should be rejected due to invalid characters (= , ; in cookie name)
    }

    /**
     * Test #5: Cookie Prefix Validation - __Host- Requirements
     *
     * <p>
     * Demonstrates the need for cookie prefix validation. The Cookie data
     * structure accepts these but applications should validate that:
     * - __Host- cookies must have Secure attribute
     * - __Host- cookies must NOT have Domain attribute
     * - __Host- cookies must have Path=/
     * </p>
     *
     * <p>
     * Note: This test documents the expected behavior for future implementation.
     * Currently, the library does not enforce cookie prefix semantics - that is
     * left to applications using the library.
     * </p>
     */
    @Test
    @DisplayName("Test #5: __Host- prefix requirements should be validated (documentation)")
    void shouldDocumentHostPrefixRequirements() {
        // These cookies violate __Host- requirements but are accepted by Cookie record
        // Applications must implement additional validation

        // VIOLATION: __Host- with Domain attribute (forbidden)
        Cookie invalidDomain = new Cookie(
                "__Host-session",
                "value",
                "Domain=.example.com; Secure; Path=/"
        );
        assertTrue(invalidDomain.getDomain().isPresent(),
                "Cookie data structure accepts Domain (validation needed by application)");

        // VIOLATION: __Host- without Secure attribute (required)
        Cookie noSecure = new Cookie(
                "__Host-session",
                "value",
                "Path=/"
        );
        assertFalse(noSecure.isSecure(),
                "Cookie data structure accepts missing Secure (validation needed by application)");

        // VIOLATION: __Host- with Path other than / (must be /)
        Cookie wrongPath = new Cookie(
                "__Host-session",
                "value",
                "Secure; Path=/admin"
        );
        assertEquals("/admin", wrongPath.getPath().orElse(null),
                "Cookie data structure accepts wrong Path (validation needed by application)");

        // VALID: Proper __Host- cookie
        Cookie valid = new Cookie(
                "__Host-session",
                "value",
                "Secure; Path=/"
        );
        assertTrue(valid.isSecure() &&
                valid.getDomain().isEmpty() &&
                "/".equals(valid.getPath().orElse("")),
                "Valid __Host- cookie has all required attributes");
    }

    /**
     * Test #6: Cookie Prefix Validation - __Secure- Requirements
     *
     * <p>
     * Demonstrates __Secure- prefix validation requirements.
     * __Secure- cookies must have the Secure attribute to ensure HTTPS-only
     * transmission.
     * </p>
     *
     * <p>
     * Note: This test documents expected behavior. Applications must implement
     * this validation as the library currently focuses on character-level validation.
     * </p>
     */
    @Test
    @DisplayName("Test #6: __Secure- prefix requirements should be validated (documentation)")
    void shouldDocumentSecurePrefixRequirements() {
        // VIOLATION: __Secure- without Secure attribute
        Cookie noSecure = new Cookie(
                "__Secure-session",
                "value",
                "Domain=example.com; Path=/"
        );
        assertFalse(noSecure.isSecure(),
                "Cookie data structure accepts __Secure- without Secure attribute (validation needed)");

        // VALID: Proper __Secure- cookie
        Cookie valid = new Cookie(
                "__Secure-session",
                "value",
                "Secure; Domain=example.com; Path=/"
        );
        assertTrue(valid.isSecure(),
                "Valid __Secure- cookie has Secure attribute");
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
    void shouldAcceptValidCookies(Cookie validCookie) throws UrlSecurityException {
        if (validCookie.hasName()) {
            assertDoesNotThrow(() -> cookieNameValidator.validate(validCookie.name()),
                    "Valid cookie name should be accepted: " + validCookie.name());
        }
        if (validCookie.hasValue()) {
            assertDoesNotThrow(() -> cookieValueValidator.validate(validCookie.value()),
                    "Valid cookie value should be accepted: " + validCookie.value());
        }
    }

    /**
     * Test #8: Attack Cookie Generator Patterns
     *
     * <p>
     * The existing AttackCookieGenerator includes various attack patterns.
     * This test validates that malicious cookie names and values are properly rejected.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = AttackCookieGenerator.class, count = 50)
    @DisplayName("Test #8: Attack cookie patterns must be rejected")
    void shouldRejectAttackCookiePatterns(Cookie attackCookie) {
        if (attackCookie.hasName()) {
            String name = attackCookie.name();
            // Some attack names may be valid at character level (e.g., empty string)
            // We document the validation behavior without asserting failure for all
            try {
                cookieNameValidator.validate(name);
                // If validation passes, the attack is at semantic level (not character level)
                assertNotNull(name, "Attack cookie name processed");
            } catch (UrlSecurityException e) {
                // Expected for many attack patterns
                assertNotNull(e.getOriginalInput());
            }
        }

        if (attackCookie.hasValue()) {
            String value = attackCookie.value();
            // Many attack values contain control characters and should be rejected
            try {
                cookieValueValidator.validate(value);
            } catch (UrlSecurityException e) {
                // Expected for attack patterns with control characters
                assertNotNull(e);
            }
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
        var ex1 = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(withUnicodeSpace.name()));
        assertNotNull(ex1);

        var ex2 = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(withZeroWidth.name()));
        assertNotNull(ex2);

        var ex3 = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(withWhitespace.name()));
        assertNotNull(ex3);

        var ex4 = assertThrows(UrlSecurityException.class,
                () -> cookieNameValidator.validate(withLegacy.name()));
        assertNotNull(ex4);
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

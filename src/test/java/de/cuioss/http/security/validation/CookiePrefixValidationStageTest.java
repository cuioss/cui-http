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
package de.cuioss.http.security.validation;

import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.data.Cookie;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.cookie.CookieNameAsciiWhitespaceGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for cookie prefix validation (RFC 6265bis).
 *
 * <p>Tests __Host- and __Secure- prefix requirements as specified in RFC 6265bis
 * and protection against Cookie Chaos attacks.</p>
 *
 * @see CookiePrefixValidationStage
 */
@DisplayName("Cookie Prefix Validation")
@EnableGeneratorController
class CookiePrefixValidationStageTest {

    private CookiePrefixValidationStage validator;

    @BeforeEach
    void setUp() {
        validator = new CookiePrefixValidationStage();
    }

    // Test data constants
    private static final String HOST_PREFIX = "__Host-";
    private static final String SECURE_PREFIX = "__Secure-";
    private static final String VALID_HOST_ATTRS = "Secure; Path=/";
    private static final String VALID_SECURE_ATTRS = "Secure";

    @Nested
    @DisplayName("__Host- Prefix Validation")
    class HostPrefixValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "Secure; Path=/",
                "Secure; Path=/; HttpOnly; SameSite=Strict",
                "Secure; Path=/; Max-Age=3600"
        })
        @DisplayName("Valid __Host- cookies should pass")
        void shouldAcceptValidHostCookies(String attributes) {
            Cookie valid = new Cookie(HOST_PREFIX + "session", "abc123", attributes);
            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @Test
        @DisplayName("__Host- without Secure should fail")
        void shouldRejectHostWithoutSecure() {
            Cookie invalid = new Cookie(HOST_PREFIX + "session", "abc123", "Path=/");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("__Host- prefix requires Secure attribute"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Domain=example.com", "Domain=.example.com"})
        @DisplayName("__Host- with Domain should fail")
        void shouldRejectHostWithDomain(String domainAttr) {
            Cookie invalid = new Cookie(HOST_PREFIX + "session", "abc123",
                    domainAttr + "; Secure; Path=/");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("Domain"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Secure", "Secure; Path=/admin", "Secure; Path=/api/v1"})
        @DisplayName("__Host- with missing or non-root Path should fail")
        void shouldRejectHostWithInvalidPath(String attributes) {
            Cookie invalid = new Cookie(HOST_PREFIX + "session", "abc123", attributes);

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("Path=/"));
        }
    }

    @Nested
    @DisplayName("__Secure- Prefix Validation")
    class SecurePrefixValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "Secure",
                "Secure; Domain=example.com",
                "Secure; Path=/api",
                "Secure; Domain=example.com; Path=/; HttpOnly; SameSite=Lax"
        })
        @DisplayName("Valid __Secure- cookies should pass")
        void shouldAcceptValidSecureCookies(String attributes) {
            Cookie valid = new Cookie(SECURE_PREFIX + "token", "xyz789", attributes);
            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @Test
        @DisplayName("__Secure- without Secure should fail")
        void shouldRejectSecureWithoutSecureAttribute() {
            Cookie invalid = new Cookie(SECURE_PREFIX + "token", "xyz789", "");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("__Secure- prefix requires Secure attribute"));
        }
    }

    @Nested
    @DisplayName("Regular Cookie Validation")
    class RegularCookieValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "Secure",
                "Domain=example.com",
                "Domain=example.com; Path=/; Secure; HttpOnly; SameSite=Strict"
        })
        @DisplayName("Regular cookies without security prefix should pass")
        void shouldAcceptRegularCookies(String attributes) {
            Cookie regular = new Cookie("session_id", "abc123", attributes);
            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }
    }

    @Nested
    @DisplayName("String-Based Name Validation")
    class StringNameValidation {

        @ParameterizedTest
        @ValueSource(strings = {"session_id", HOST_PREFIX + "session", SECURE_PREFIX + "token"})
        @DisplayName("Valid cookie names should pass")
        void shouldAcceptValidNames(String name) {
            assertDoesNotThrow(() -> validator.validate(name));
        }

        @ParameterizedTest
        @ValueSource(strings = {" session_id", "session_id ", " session_id ", "\tsession_id"})
        @DisplayName("Names with leading/trailing whitespace should fail")
        void shouldRejectNamesWithWhitespace(String invalidName) {
            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validate(invalidName));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("leading or trailing whitespace"));
        }

        @Test
        @DisplayName("Null name should return empty")
        void shouldReturnEmptyForNull() {
            var result = validator.validate(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Empty name should pass string validation")
        void shouldAcceptEmptyName() {
            // Note: Empty name validation at Cookie level
            assertDoesNotThrow(() -> validator.validate(""));
        }
    }

    @Nested
    @DisplayName("Cookie Chaos Attack Prevention")
    class CookieChaosAttackPrevention {

        @ParameterizedTest
        @TypeGeneratorSource(value = CookieNameAsciiWhitespaceGenerator.class, count = 20)
        @DisplayName("Should reject cookie names with ASCII whitespace injection")
        void shouldRejectCookieNamesWithWhitespace(String maliciousName) {
            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validate(maliciousName));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
        }

        @ParameterizedTest
        @ValueSource(strings = {" __Host-session", "\t__Secure-token", "__Host-session ", " session_id"})
        @DisplayName("Should reject prefix cookies with whitespace")
        void shouldRejectPrefixCookiesWithWhitespace(String invalidName) {
            Cookie invalid = new Cookie(invalidName, "value", VALID_HOST_ATTRS);

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        }
    }

    @Nested
    @DisplayName("Static Helper Methods")
    class HelperMethods {

        @Test
        @DisplayName("Should detect __Host- prefix")
        void shouldDetectHostPrefix() {
            assertTrue(CookiePrefixValidationStage.hasSecurityPrefix(HOST_PREFIX + "session"));
            assertTrue(CookiePrefixValidationStage.hasHostPrefix(HOST_PREFIX + "session"));
            assertFalse(CookiePrefixValidationStage.hasSecurePrefix(HOST_PREFIX + "session"));
        }

        @Test
        @DisplayName("Should detect __Secure- prefix")
        void shouldDetectSecurePrefix() {
            assertTrue(CookiePrefixValidationStage.hasSecurityPrefix(SECURE_PREFIX + "token"));
            assertTrue(CookiePrefixValidationStage.hasSecurePrefix(SECURE_PREFIX + "token"));
            assertFalse(CookiePrefixValidationStage.hasHostPrefix(SECURE_PREFIX + "token"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"session_id", "token", "auth"})
        @DisplayName("Should not detect prefix on regular names")
        void shouldNotDetectPrefixOnRegular(String name) {
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix(name));
            assertFalse(CookiePrefixValidationStage.hasHostPrefix(name));
            assertFalse(CookiePrefixValidationStage.hasSecurePrefix(name));
        }

        @Test
        @DisplayName("Should handle null safely")
        void shouldHandleNullSafely() {
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix(null));
            assertFalse(CookiePrefixValidationStage.hasHostPrefix(null));
            assertFalse(CookiePrefixValidationStage.hasSecurePrefix(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"__host-session", "__HOST-session", "__secure-token", "__SECURE-token"})
        @DisplayName("Should be case-sensitive")
        void shouldBeCaseSensitive(String wrongCase) {
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix(wrongCase));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should reject cookie with null name")
        void shouldRejectCookieWithNullName() {
            Cookie invalid = new Cookie(null, "value", "Secure");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        }

        @Test
        @DisplayName("Should reject cookie with empty name")
        void shouldRejectCookieWithEmptyName() {
            Cookie invalid = new Cookie("", "value", "Secure");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        }

        @ParameterizedTest
        @ValueSource(strings = {"session__Host-", "token__Secure-", "__H", "__Sec", "MyHost-cookie"})
        @DisplayName("Prefix at wrong position should be treated as regular cookie")
        void shouldTreatWrongPositionAsRegular(String name) {
            Cookie regular = new Cookie(name, "value", "");
            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }
    }
}

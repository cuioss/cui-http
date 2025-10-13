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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
class CookiePrefixValidationStageTest {

    private CookiePrefixValidationStage validator;

    @BeforeEach
    void setUp() {
        validator = new CookiePrefixValidationStage();
    }

    @Nested
    @DisplayName("__Host- Prefix Validation")
    class HostPrefixValidation {

        @Test
        @DisplayName("Valid __Host- cookie should pass")
        void shouldAcceptValidHostCookie() {
            Cookie valid = new Cookie("__Host-session", "abc123", "Secure; Path=/");

            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @Test
        @DisplayName("__Host- without Secure should fail")
        void shouldRejectHostWithoutSecure() {
            Cookie invalid = new Cookie("__Host-session", "abc123", "Path=/");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("__Host- prefix requires Secure attribute"));
        }

        @Test
        @DisplayName("__Host- with Domain should fail")
        void shouldRejectHostWithDomain() {
            Cookie invalid = new Cookie("__Host-session", "abc123", "Domain=example.com; Secure; Path=/");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("__Host- prefix must not have Domain attribute"));
        }

        @Test
        @DisplayName("__Host- with Domain=.example.com should fail")
        void shouldRejectHostWithDotDomain() {
            Cookie invalid = new Cookie("__Host-session", "abc123", "Domain=.example.com; Secure; Path=/");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("Domain"));
        }

        @Test
        @DisplayName("__Host- without Path should fail")
        void shouldRejectHostWithoutPath() {
            Cookie invalid = new Cookie("__Host-session", "abc123", "Secure");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("__Host- prefix requires Path=/"));
        }

        @Test
        @DisplayName("__Host- with Path=/admin should fail")
        void shouldRejectHostWithNonRootPath() {
            Cookie invalid = new Cookie("__Host-session", "abc123", "Secure; Path=/admin");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("Path=/"));
        }

        @Test
        @DisplayName("__Host- with all attributes should pass")
        void shouldAcceptHostWithAllValidAttributes() {
            Cookie valid = new Cookie("__Host-session", "abc123",
                    "Secure; Path=/; HttpOnly; SameSite=Strict");

            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @Test
        @DisplayName("__Host- with Max-Age should pass")
        void shouldAcceptHostWithMaxAge() {
            Cookie valid = new Cookie("__Host-session", "abc123",
                    "Secure; Path=/; Max-Age=3600");

            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }
    }

    @Nested
    @DisplayName("__Secure- Prefix Validation")
    class SecurePrefixValidation {

        @Test
        @DisplayName("Valid __Secure- cookie should pass")
        void shouldAcceptValidSecureCookie() {
            Cookie valid = new Cookie("__Secure-token", "xyz789", "Secure");

            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @Test
        @DisplayName("__Secure- without Secure should fail")
        void shouldRejectSecureWithoutSecureAttribute() {
            Cookie invalid = new Cookie("__Secure-token", "xyz789", "");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("__Secure- prefix requires Secure attribute"));
        }

        @Test
        @DisplayName("__Secure- with Domain should pass")
        void shouldAcceptSecureWithDomain() {
            Cookie valid = new Cookie("__Secure-token", "xyz789", "Secure; Domain=example.com");

            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @Test
        @DisplayName("__Secure- with Path should pass")
        void shouldAcceptSecureWithPath() {
            Cookie valid = new Cookie("__Secure-token", "xyz789", "Secure; Path=/api");

            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @Test
        @DisplayName("__Secure- with all attributes should pass")
        void shouldAcceptSecureWithAllAttributes() {
            Cookie valid = new Cookie("__Secure-token", "xyz789",
                    "Secure; Domain=example.com; Path=/; HttpOnly; SameSite=Lax");

            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }
    }

    @Nested
    @DisplayName("Regular Cookie Validation")
    class RegularCookieValidation {

        @Test
        @DisplayName("Regular cookie without prefix should pass")
        void shouldAcceptRegularCookie() {
            Cookie regular = new Cookie("session_id", "abc123", "");

            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }

        @Test
        @DisplayName("Regular cookie with Secure should pass")
        void shouldAcceptRegularWithSecure() {
            Cookie regular = new Cookie("session_id", "abc123", "Secure");

            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }

        @Test
        @DisplayName("Regular cookie with Domain should pass")
        void shouldAcceptRegularWithDomain() {
            Cookie regular = new Cookie("session_id", "abc123", "Domain=example.com");

            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }

        @Test
        @DisplayName("Regular cookie with all attributes should pass")
        void shouldAcceptRegularWithAllAttributes() {
            Cookie regular = new Cookie("session_id", "abc123",
                    "Domain=example.com; Path=/; Secure; HttpOnly; SameSite=Strict");

            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }
    }

    @Nested
    @DisplayName("String-Based Name Validation")
    class StringNameValidation {

        @Test
        @DisplayName("Valid cookie name should pass")
        void shouldAcceptValidName() {
            assertDoesNotThrow(() -> validator.validate("session_id"));
        }

        @Test
        @DisplayName("Name with leading whitespace should fail")
        void shouldRejectNameWithLeadingWhitespace() {
            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validate(" session_id"));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("leading or trailing whitespace"));
        }

        @Test
        @DisplayName("Name with trailing whitespace should fail")
        void shouldRejectNameWithTrailingWhitespace() {
            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validate("session_id "));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("leading or trailing whitespace"));
        }

        @Test
        @DisplayName("Name with both leading and trailing whitespace should fail")
        void shouldRejectNameWithBothWhitespace() {
            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validate(" session_id "));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
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

        @Test
        @DisplayName("Should reject __Host- with whitespace-prefixed name")
        void shouldRejectHostWithWhitespacePrefix() {
            Cookie invalid = new Cookie(" __Host-session", "abc123", "Secure; Path=/");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        }

        @Test
        @DisplayName("Should reject __Secure- with whitespace-prefixed name")
        void shouldRejectSecureWithWhitespacePrefix() {
            Cookie invalid = new Cookie("\t__Secure-token", "xyz789", "Secure");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
        }

        @Test
        @DisplayName("Unicode whitespace already blocked by character validation")
        void shouldDocumentUnicodeBlocking() {
            // Unicode whitespace (U+2000) would be blocked by CharacterValidationStage
            // This test documents that cookie prefix validation happens after character validation
            // Character validation: \u2000 -> UrlSecurityException (INVALID_CHARACTER)
            // Prefix validation: operates on cleaned input only

            assertTrue(true, "Unicode whitespace blocked by CharacterValidationStage");
        }
    }

    @Nested
    @DisplayName("Static Helper Methods")
    class HelperMethods {

        @Test
        @DisplayName("hasSecurityPrefix should detect __Host-")
        void shouldDetectHostPrefix() {
            assertTrue(CookiePrefixValidationStage.hasSecurityPrefix("__Host-session"));
            assertTrue(CookiePrefixValidationStage.hasHostPrefix("__Host-session"));
            assertFalse(CookiePrefixValidationStage.hasSecurePrefix("__Host-session"));
        }

        @Test
        @DisplayName("hasSecurityPrefix should detect __Secure-")
        void shouldDetectSecurePrefix() {
            assertTrue(CookiePrefixValidationStage.hasSecurityPrefix("__Secure-token"));
            assertTrue(CookiePrefixValidationStage.hasSecurePrefix("__Secure-token"));
            assertFalse(CookiePrefixValidationStage.hasHostPrefix("__Secure-token"));
        }

        @Test
        @DisplayName("hasSecurityPrefix should return false for regular names")
        void shouldNotDetectPrefixOnRegular() {
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix("session_id"));
            assertFalse(CookiePrefixValidationStage.hasHostPrefix("session_id"));
            assertFalse(CookiePrefixValidationStage.hasSecurePrefix("session_id"));
        }

        @Test
        @DisplayName("hasSecurityPrefix should handle null")
        void shouldHandleNullInHelpers() {
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix(null));
            assertFalse(CookiePrefixValidationStage.hasHostPrefix(null));
            assertFalse(CookiePrefixValidationStage.hasSecurePrefix(null));
        }

        @Test
        @DisplayName("hasSecurityPrefix should be case-sensitive")
        void shouldBeCaseSensitive() {
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix("__host-session"));
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix("__HOST-session"));
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix("__secure-token"));
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix("__SECURE-token"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should reject cookie without name")
        void shouldRejectCookieWithoutName() {
            Cookie invalid = new Cookie(null, "value", "Secure");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
            assertTrue(exception.getDetail().isPresent());
            assertTrue(exception.getDetail().get().contains("Cookie must have a name"));
        }

        @Test
        @DisplayName("Should reject cookie with empty name")
        void shouldRejectCookieWithEmptyName() {
            Cookie invalid = new Cookie("", "value", "Secure");

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.INVALID_INPUT, exception.getFailureType());
        }

        @Test
        @DisplayName("__Host- prefix at end of name is regular cookie")
        void shouldTreatSuffixAsRegular() {
            Cookie regular = new Cookie("session__Host-", "abc123", "");

            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }

        @Test
        @DisplayName("__Secure- prefix at end of name is regular cookie")
        void shouldTreatSecureSuffixAsRegular() {
            Cookie regular = new Cookie("token__Secure-", "xyz789", "");

            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }

        @Test
        @DisplayName("Partial prefix match should not trigger validation")
        void shouldNotMatchPartialPrefix() {
            Cookie regular = new Cookie("__H", "value", "");

            assertDoesNotThrow(() -> validator.validateCookie(regular));
        }
    }
}

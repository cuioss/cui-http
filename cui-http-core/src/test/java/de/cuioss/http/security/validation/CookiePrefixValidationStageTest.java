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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

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
    private static final String HTTP_PREFIX = "__Http-";
    private static final String HOST_HTTP_PREFIX = "__HostHttp-";
    private static final String VALID_HOST_ATTRS = "Secure; Path=/";

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

        @Test
        @DisplayName("A repeated Path resolves last-wins, so Path=/; Path=/admin is rejected")
        void shouldRejectHostWithRepeatedPathResolvingNonRoot() {
            // The user agent applies the LAST Path, so this cookie is scoped to /admin and is not
            // host-locked. Resolving first-wins let the gate read Path=/ and accept it outright -
            // the gate must read the value that actually takes effect, not the first one written.
            assertPrefixViolationUnderBothPresets(HOST_PREFIX + "a", "Secure; Path=/; Path=/admin",
                    "__Host- prefix requires Path=/ (found: /admin)");
        }

        @Test
        @DisplayName("A Domain key padded with whitespace is still a Domain (RFC 6265 section 5.2)")
        void shouldRejectHostWithWhitespacePaddedDomainKey() {
            // A user agent trims the attribute name, so "Domain =evil.com" sets a Domain and the
            // cookie is not host-locked. Treating the padded key as unparseable made the gate read
            // "no Domain" and accept it - a fail-open disagreement with the name enumeration, which
            // trimmed the same key and reported Domain.
            assertPrefixViolationUnderBothPresets(HOST_PREFIX + "x", "Secure; Path=/; Domain =evil.com",
                    "__Host- prefix must not have Domain attribute (found: evil.com)");
        }

        @Test
        @DisplayName("A repeated Domain resolves last-wins, so the effective Domain is reported")
        void shouldReportTheLastDomainOccurrence() {
            assertPrefixViolationUnderBothPresets(HOST_PREFIX + "a", "Secure; Path=/; Domain=; Domain=evil.com",
                    "__Host- prefix must not have Domain attribute (found: evil.com)");
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
    @DisplayName("__Http- and __HostHttp- Prefix Validation")
    class LaterDraftPrefixValidation {

        @ParameterizedTest
        @ValueSource(strings = {"Secure; HttpOnly", "Secure; HttpOnly; Domain=example.com; Path=/api"})
        @DisplayName("Valid __Http- cookies should pass")
        void shouldAcceptValidHttpCookies(String attributes) {
            Cookie valid = new Cookie(HTTP_PREFIX + "token", "xyz789", attributes);
            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Secure; HttpOnly; Path=/", "Secure; HttpOnly; Path=/; SameSite=Strict"})
        @DisplayName("Valid __HostHttp- cookies should pass")
        void shouldAcceptValidHostHttpCookies(String attributes) {
            Cookie valid = new Cookie(HOST_HTTP_PREFIX + "session", "abc123", attributes);
            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @Test
        @DisplayName("__Http- without HttpOnly should fail under both presets")
        void shouldRejectHttpWithoutHttpOnly() {
            assertPrefixViolationUnderBothPresets(HTTP_PREFIX + "token", "Secure",
                    "__Http- prefix requires HttpOnly attribute");
        }

        @Test
        @DisplayName("__Http- without Secure should fail under both presets")
        void shouldRejectHttpWithoutSecure() {
            assertPrefixViolationUnderBothPresets(HTTP_PREFIX + "token", "HttpOnly",
                    "__Http- prefix requires Secure attribute");
        }

        @Test
        @DisplayName("__HostHttp- without HttpOnly should fail under both presets")
        void shouldRejectHostHttpWithoutHttpOnly() {
            assertPrefixViolationUnderBothPresets(HOST_HTTP_PREFIX + "session", "Secure; Path=/",
                    "__HostHttp- prefix requires HttpOnly attribute");
        }

        @Test
        @DisplayName("__HostHttp- with Domain should fail under both presets")
        void shouldRejectHostHttpWithDomain() {
            assertPrefixViolationUnderBothPresets(HOST_HTTP_PREFIX + "session",
                    "Secure; HttpOnly; Domain=example.com; Path=/",
                    "__HostHttp- prefix must not have Domain attribute");
        }

        @Test
        @DisplayName("__HostHttp- without Path=/ should fail under both presets")
        void shouldRejectHostHttpWithoutRootPath() {
            assertPrefixViolationUnderBothPresets(HOST_HTTP_PREFIX + "session", "Secure; HttpOnly",
                    "__HostHttp- prefix requires Path=/");
        }
    }

    @Nested
    @DisplayName("Case-insensitive prefix matching (RFC 6265bis)")
    class CaseInsensitivePrefixMatching {

        @ParameterizedTest
        @ValueSource(strings = {"__host-session", "__HOST-session", "__HoSt-session"})
        @DisplayName("A case-varied __Host- name is subject to the full __Host- rules")
        void shouldApplyHostRulesRegardlessOfCase(String name) {
            assertAll("full __Host- rule set applies to " + name,
                    () -> assertPrefixViolationUnderBothPresets(name, "Path=/",
                            "__Host- prefix requires Secure attribute"),
                    () -> assertPrefixViolationUnderBothPresets(name, "Secure; Domain=example.com; Path=/",
                            "__Host- prefix must not have Domain attribute"),
                    () -> assertPrefixViolationUnderBothPresets(name, "Secure",
                            "__Host- prefix requires Path=/"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"__secure-token", "__SECURE-token", "__SeCuRe-token"})
        @DisplayName("A case-varied __Secure- name is subject to the __Secure- rule")
        void shouldApplySecureRuleRegardlessOfCase(String name) {
            assertPrefixViolationUnderBothPresets(name, "Domain=example.com",
                    "__Secure- prefix requires Secure attribute");
        }

        @ParameterizedTest
        @ValueSource(strings = {"__http-token", "__HTTP-token", "__HtTp-token"})
        @DisplayName("A case-varied __Http- name is subject to the __Http- rules")
        void shouldApplyHttpRulesRegardlessOfCase(String name) {
            assertAll("full __Http- rule set applies to " + name,
                    () -> assertPrefixViolationUnderBothPresets(name, "HttpOnly",
                            "__Http- prefix requires Secure attribute"),
                    () -> assertPrefixViolationUnderBothPresets(name, "Secure",
                            "__Http- prefix requires HttpOnly attribute"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"__hosthttp-session", "__HOSTHTTP-session", "__HostHTTP-session"})
        @DisplayName("A case-varied __HostHttp- name is subject to the full __HostHttp- rules")
        void shouldApplyHostHttpRulesRegardlessOfCase(String name) {
            assertAll("full __HostHttp- rule set applies to " + name,
                    () -> assertPrefixViolationUnderBothPresets(name, "HttpOnly; Path=/",
                            "__HostHttp- prefix requires Secure attribute"),
                    () -> assertPrefixViolationUnderBothPresets(name, "Secure; Path=/",
                            "__HostHttp- prefix requires HttpOnly attribute"),
                    () -> assertPrefixViolationUnderBothPresets(name, "Secure; HttpOnly; Domain=example.com; Path=/",
                            "__HostHttp- prefix must not have Domain attribute"),
                    () -> assertPrefixViolationUnderBothPresets(name, "Secure; HttpOnly",
                            "__HostHttp- prefix requires Path=/"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"__hOsT-session", "__sEcUrE-token", "__hTtP-token", "__hOsThTtP-session"})
        @DisplayName("A case-varied prefix that satisfies its rules passes")
        void shouldAcceptCompliantCaseVariedPrefixCookies(String name) {
            Cookie valid = new Cookie(name, "value", "Secure; HttpOnly; Path=/");
            assertDoesNotThrow(() -> validator.validateCookie(valid));
        }

        @ParameterizedTest
        @ValueSource(strings = {"__ſecure-token", "__hoſt-session"})
        @DisplayName("Non-ASCII case folding must not be read as a prefix")
        void shouldNotFoldNonAsciiIntoPrefix(String name) {
            // LATIN SMALL LETTER LONG S uppercases to 'S' under Unicode case folding, so a
            // Unicode-aware startsWith would read these as prefixed although no user agent does.
            assertFalse(CookiePrefixValidationStage.hasSecurityPrefix(name));

            // The cookie is still rejected, but as a non-ASCII cookie name rather than as a prefix
            // violation - the name grammar, not the prefix rules, is what stops it.
            Cookie cookie = new Cookie(name, "value", "");
            var exception = assertThrows(UrlSecurityException.class, () -> validator.validateCookie(cookie));
            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
            assertEquals(ValidationType.COOKIE_NAME, exception.getValidationType());
        }
    }

    /**
     * Pins a gate under both presets ADR-0017 requires: the same input must produce the same
     * failure type and the same canonical detail under {@code defaults()} and {@code lenient()}.
     * The asserted detail names the canonical prefix token, so it also proves the gate read the
     * canonical prefix rather than the casing the request happened to use.
     */
    private static void assertPrefixViolationUnderBothPresets(String name, String attributes, String expectedDetail) {
        assertAll("both presets reject '" + name + "' with attributes '" + attributes + "'",
                () -> assertPrefixViolation(SecurityConfiguration.defaults(), name, attributes, expectedDetail),
                () -> assertPrefixViolation(SecurityConfiguration.lenient(), name, attributes, expectedDetail));
    }

    private static void assertPrefixViolation(SecurityConfiguration config, String name, String attributes,
            String expectedDetail) {
        var stage = new CookiePrefixValidationStage(config);
        Cookie cookie = new Cookie(name, "value", attributes);

        var exception = assertThrows(UrlSecurityException.class, () -> stage.validateCookie(cookie));

        assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
        assertTrue(exception.getDetail().orElse("").contains(expectedDetail),
                "Expected detail to contain '" + expectedDetail + "' but was: " + exception.getDetail().orElse("none"));
    }

    @Nested
    @DisplayName("Cookie name and value character validation")
    class ComponentCharacterValidation {

        /**
         * Values outside the RFC 6265 {@code cookie-octet} set, each labelled by what it smuggles.
         * The non-printing code points are written as {@code (char)} literals rather than pasted in,
         * so the test data stays visible to a reader of this source.
         */
        static Stream<Arguments> valuesOutsideCookieOctet() {
            return Stream.of(
                    Arguments.of("semicolon", "bad;value"),
                    Arguments.of("comma", "bad,value"),
                    Arguments.of("space", "bad value"),
                    Arguments.of("double quote", "bad\"value"),
                    Arguments.of("backslash", "bad\\value"),
                    Arguments.of("carriage return", "bad\rvalue"),
                    Arguments.of("line feed", "bad\nvalue"),
                    Arguments.of("NBSP U+00A0", "bad" + (char) 0x00A0 + "value"),
                    Arguments.of("DEL U+007F", "bad" + (char) 0x007F + "value"));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("valuesOutsideCookieOctet")
        @DisplayName("A value outside cookie-octet is rejected under both presets")
        void shouldRejectValueOutsideCookieOctet(String smuggled, String value) {
            assertAll("both presets reject a value carrying a " + smuggled,
                    () -> assertComponentRejected(SecurityConfiguration.defaults(), "session", value),
                    () -> assertComponentRejected(SecurityConfiguration.lenient(), "session", value));
        }

        /** Whitespace-like code points above U+0020, which {@link String#trim()} does not strip. */
        static Stream<Arguments> namesWithNonAsciiWhitespace() {
            return Stream.of(
                    Arguments.of("NBSP U+00A0", "sess" + (char) 0x00A0 + "ion"),
                    Arguments.of("ZWSP U+200B", "sess" + (char) 0x200B + "ion"),
                    Arguments.of("IDEOGRAPHIC SPACE U+3000", "sess" + (char) 0x3000 + "ion"),
                    Arguments.of("NEL U+0085", "sess" + (char) 0x0085 + "ion"));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("namesWithNonAsciiWhitespace")
        @DisplayName("Non-ASCII whitespace in the name is rejected although String.trim leaves it")
        void shouldRejectNonAsciiWhitespaceInName(String codePointName, String name) {
            // String.trim only strips code points <= U+0020, so the pre-existing trim check cannot
            // see any of these - the character stage is what catches them.
            assertEquals(name, name.trim(), "Precondition: String.trim must leave " + codePointName + " in place");
            assertAll("both presets reject a name carrying " + codePointName,
                    () -> assertComponentRejected(SecurityConfiguration.defaults(), name, "value"),
                    () -> assertComponentRejected(SecurityConfiguration.lenient(), name, "value"));
        }

        @Test
        @DisplayName("An empty value stays legal - a bare name= pair is valid HTTP")
        void shouldAcceptEmptyValue() {
            assertDoesNotThrow(() -> validator.validateCookie(new Cookie("session", "", "")));
        }

        @Test
        @DisplayName("A rejected Domain value is escaped, never spliced raw into the detail")
        void shouldEscapeControlCharactersInReportedDomain() {
            String domainCarryingNul = "ex" + (char) 0x0000 + "ample.com";
            Cookie invalid = new Cookie(HOST_PREFIX + "session", "abc123",
                    "Secure; Path=/; Domain=" + domainCarryingNul);

            var exception = assertThrows(UrlSecurityException.class,
                    () -> validator.validateCookie(invalid));

            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            String detail = exception.getDetail().orElse("");
            assertTrue(detail.contains("U+0000"), "Detail should escape the control character: " + detail);
            assertTrue(detail.indexOf(0x0000) < 0, "Detail must not carry the raw control character");
        }

        private void assertComponentRejected(SecurityConfiguration config, String name, String value) {
            var stage = new CookiePrefixValidationStage(config);
            Cookie cookie = new Cookie(name, value, "");
            assertThrows(UrlSecurityException.class, () -> stage.validateCookie(cookie));
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
        @ValueSource(strings = {
                "__host-session", "__HOST-session", "__HoSt-session",
                "__secure-token", "__SECURE-token", "__SeCuRe-token",
                "__http-token", "__HTTP-token", "__HtTp-token",
                "__hosthttp-token", "__HOSTHTTP-token", "__HostHTTP-token"
        })
        @DisplayName("Should match security prefixes ASCII case-insensitively (RFC 6265bis)")
        void shouldMatchPrefixCaseInsensitively(String mixedCase) {
            assertTrue(CookiePrefixValidationStage.hasSecurityPrefix(mixedCase));
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

    @Nested
    @DisplayName("Cookie attribute requirements (F-11)")
    class AttributeRequirements {

        @Test
        @DisplayName("Default config does not require Secure or HttpOnly")
        void shouldNotEnforceByDefault() {
            // Default validator (no-arg) leaves both flags off - behavior unchanged.
            Cookie plain = new Cookie("session", "abc123", "");
            assertDoesNotThrow(() -> validator.validateCookie(plain));
        }

        @Test
        @DisplayName("requireSecureCookies rejects a cookie without Secure")
        void shouldRejectMissingSecureWhenRequired() {
            var secureValidator = new CookiePrefixValidationStage(
                    SecurityConfiguration.builder()
                            .requireSecureCookies(true)
                            .build());

            Cookie missingSecure = new Cookie("session", "abc123", "HttpOnly");
            var exception = assertThrows(UrlSecurityException.class,
                    () -> secureValidator.validateCookie(missingSecure));
            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().orElse("").contains("Secure"));

            // A cookie that carries Secure passes.
            Cookie withSecure = new Cookie("session", "abc123", "Secure");
            assertDoesNotThrow(() -> secureValidator.validateCookie(withSecure));
        }

        @Test
        @DisplayName("requireHttpOnlyCookies rejects a cookie without HttpOnly")
        void shouldRejectMissingHttpOnlyWhenRequired() {
            var httpOnlyValidator = new CookiePrefixValidationStage(
                    SecurityConfiguration.builder()
                            .requireHttpOnlyCookies(true)
                            .build());

            Cookie missingHttpOnly = new Cookie("session", "abc123", "Secure");
            var exception = assertThrows(UrlSecurityException.class,
                    () -> httpOnlyValidator.validateCookie(missingHttpOnly));
            assertEquals(UrlSecurityFailureType.COOKIE_PREFIX_VIOLATION, exception.getFailureType());
            assertTrue(exception.getDetail().orElse("").contains("HttpOnly"));

            // A cookie that carries HttpOnly passes.
            Cookie withHttpOnly = new Cookie("session", "abc123", "HttpOnly");
            assertDoesNotThrow(() -> httpOnlyValidator.validateCookie(withHttpOnly));
        }
    }
}

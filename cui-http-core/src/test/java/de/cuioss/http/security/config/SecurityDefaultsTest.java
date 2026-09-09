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
package de.cuioss.http.security.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link SecurityDefaults}
 */
class SecurityDefaultsTest {

    @Test
    void shouldHaveCorrectPathLengthConstants() {
        assertEquals(1024, SecurityDefaults.MAX_PATH_LENGTH_STRICT);
        assertEquals(4096, SecurityDefaults.MAX_PATH_LENGTH_DEFAULT);
        assertEquals(8192, SecurityDefaults.MAX_PATH_LENGTH_LENIENT);
    }

    @Test
    void shouldHaveCorrectParameterCountConstants() {
        assertEquals(20, SecurityDefaults.MAX_PARAMETER_COUNT_STRICT);
        assertEquals(100, SecurityDefaults.MAX_PARAMETER_COUNT_DEFAULT);
        assertEquals(500, SecurityDefaults.MAX_PARAMETER_COUNT_LENIENT);
    }

    @Test
    void shouldHaveCorrectParameterLengthConstants() {
        assertEquals(64, SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_STRICT);
        assertEquals(128, SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_DEFAULT);
        assertEquals(256, SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_LENIENT);

        assertEquals(1024, SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_STRICT);
        assertEquals(2048, SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_DEFAULT);
        assertEquals(8192, SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_LENIENT);
    }

    @Test
    void shouldHaveCorrectHeaderConstants() {
        assertEquals(20, SecurityDefaults.MAX_HEADER_COUNT_STRICT);
        assertEquals(50, SecurityDefaults.MAX_HEADER_COUNT_DEFAULT);
        assertEquals(100, SecurityDefaults.MAX_HEADER_COUNT_LENIENT);

        assertEquals(64, SecurityDefaults.MAX_HEADER_NAME_LENGTH_STRICT);
        assertEquals(128, SecurityDefaults.MAX_HEADER_NAME_LENGTH_DEFAULT);
        assertEquals(256, SecurityDefaults.MAX_HEADER_NAME_LENGTH_LENIENT);

        assertEquals(1024, SecurityDefaults.MAX_HEADER_VALUE_LENGTH_STRICT);
        assertEquals(2048, SecurityDefaults.MAX_HEADER_VALUE_LENGTH_DEFAULT);
        assertEquals(8192, SecurityDefaults.MAX_HEADER_VALUE_LENGTH_LENIENT);
    }

    @Test
    void shouldHaveCorrectCookieConstants() {
        assertEquals(10, SecurityDefaults.MAX_COOKIE_COUNT_STRICT);
        assertEquals(20, SecurityDefaults.MAX_COOKIE_COUNT_DEFAULT);
        assertEquals(50, SecurityDefaults.MAX_COOKIE_COUNT_LENIENT);

        assertEquals(64, SecurityDefaults.MAX_COOKIE_NAME_LENGTH_STRICT);
        assertEquals(128, SecurityDefaults.MAX_COOKIE_NAME_LENGTH_DEFAULT);
        assertEquals(256, SecurityDefaults.MAX_COOKIE_NAME_LENGTH_LENIENT);

        assertEquals(1024, SecurityDefaults.MAX_COOKIE_VALUE_LENGTH_STRICT);
        assertEquals(2048, SecurityDefaults.MAX_COOKIE_VALUE_LENGTH_DEFAULT);
        assertEquals(8192, SecurityDefaults.MAX_COOKIE_VALUE_LENGTH_LENIENT);
    }

    @Test
    void shouldHaveCorrectBodySizeConstants() {
        assertEquals(SecurityDefaults.MAX_BODY_SIZE_STRICT, 1024 * 1024);
        assertEquals(SecurityDefaults.MAX_BODY_SIZE_DEFAULT, 5 * 1024 * 1024);
        assertEquals(SecurityDefaults.MAX_BODY_SIZE_LENIENT, 10 * 1024 * 1024);
    }

    @Test
    void shouldHavePathTraversalPatterns() {
        assertNotNull(SecurityDefaults.PATH_TRAVERSAL_PATTERNS);
        assertFalse(SecurityDefaults.PATH_TRAVERSAL_PATTERNS.isEmpty());

        assertTrue(SecurityDefaults.PATH_TRAVERSAL_PATTERNS.contains("../"));
        assertTrue(SecurityDefaults.PATH_TRAVERSAL_PATTERNS.contains("..\\"));
        assertTrue(SecurityDefaults.PATH_TRAVERSAL_PATTERNS.contains("..%2F"));
        assertTrue(SecurityDefaults.PATH_TRAVERSAL_PATTERNS.contains("%2E%2E/"));
    }

    @Test
    void shouldHaveProtocolHandlerSchemes() {
        assertEquals(4, SecurityDefaults.PROTOCOL_HANDLER_SCHEMES.size());

        assertAll("protocol handler schemes",
                () -> assertTrue(SecurityDefaults.PROTOCOL_HANDLER_SCHEMES.contains("javascript:")),
                () -> assertTrue(SecurityDefaults.PROTOCOL_HANDLER_SCHEMES.contains("vbscript:")),
                () -> assertTrue(SecurityDefaults.PROTOCOL_HANDLER_SCHEMES.contains("data:")),
                () -> assertTrue(SecurityDefaults.PROTOCOL_HANDLER_SCHEMES.contains("file:")),
                () -> assertTrue(SecurityDefaults.PROTOCOL_HANDLER_SCHEMES.stream().allMatch(s -> s.endsWith(":")),
                        "every scheme literal must carry its ':' so start-anchored matching is exact"));
    }

    @Test
    void shouldHaveSensitivePathPatterns() {
        assertEquals(15, SecurityDefaults.SENSITIVE_PATH_PATTERNS.size());

        assertAll("sensitive path patterns",
                () -> assertTrue(SecurityDefaults.SENSITIVE_PATH_PATTERNS.contains("/etc/")),
                () -> assertTrue(SecurityDefaults.SENSITIVE_PATH_PATTERNS.contains("web.xml")),
                () -> assertTrue(SecurityDefaults.SENSITIVE_PATH_PATTERNS.contains(".env")),
                () -> assertTrue(SecurityDefaults.SENSITIVE_PATH_PATTERNS.contains("\\windows\\"),
                        "the Windows literals are reachable post-decode via %5C and must be carried"),
                () -> assertTrue(SecurityDefaults.SENSITIVE_PATH_PATTERNS.contains("\\users\\"),
                        "the backslash delimiters are load-bearing - the bare segment 'users' would "
                                + "reject every /users/... REST route"));
    }

    @Test
    void shouldHaveParanoidConfigurationDifferingFromStrictOnlyInBlockLists() {
        SecurityConfiguration paranoid = SecurityDefaults.PARANOID_CONFIGURATION;
        SecurityConfiguration strict = SecurityDefaults.STRICT_CONFIGURATION;

        assertAll("paranoid preset",
                () -> assertEquals(SecurityDefaults.SENSITIVE_PATH_PATTERNS, paranoid.blockedPathPatterns()),
                () -> assertEquals(SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES, paranoid.blockedParameterNames()),
                () -> assertTrue(strict.blockedPathPatterns().isEmpty()),
                () -> assertTrue(strict.blockedParameterNames().isEmpty()),
                () -> assertFalse(paranoid.caseSensitiveComparison(),
                        "ADR-0012: a security preset must never set caseSensitiveComparison to true"),
                () -> assertEquals(strict, withEmptyContentBlockLists(paranoid),
                        "paranoid() must equal strict() on every setting except the two block-lists"));
    }

    private static SecurityConfiguration withEmptyContentBlockLists(SecurityConfiguration config) {
        return new SecurityConfiguration(
                config.maxPathLength(), config.allowDoubleEncoding(),
                config.maxParameterNameLength(), config.maxParameterValueLength(),
                config.maxHeaderNameLength(), config.maxHeaderValueLength(),
                config.maxCookieNameLength(), config.maxCookieValueLength(),
                config.maxBodySize(),
                config.allowNullBytes(), config.allowControlCharacters(),
                config.allowExtendedAscii(), config.normalizeUnicode(),
                config.caseSensitiveComparison(), config.failOnSuspiciousPatterns(),
                config.requireSecureCookies(), config.requireHttpOnlyCookies(),
                config.maxParameterCount(), config.maxHeaderCount(), config.maxCookieCount(),
                config.allowedHeaderNames(), config.blockedHeaderNames(),
                config.allowedContentTypes(), config.blockedContentTypes(),
                Set.of(), Set.of());
    }

    @Test
    void shouldHaveSuspiciousParameterNames() {
        assertNotNull(SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES);
        assertFalse(SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES.isEmpty());

        // Only HTTP-layer appropriate suspicious parameter names
        assertTrue(SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES.contains("script"));
        assertTrue(SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES.contains("include"));
        assertTrue(SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES.contains("file"));
        assertTrue(SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES.contains("path"));
    }

    // XSS patterns removed - application layer responsibility.
    // Application layers have proper context for HTML/JS escaping and validation.

    /**
     * The eleven constants named here were unenforced published surface and were deleted outright,
     * with no deprecation window. Re-introducing any of them would silently re-grow the advisory
     * surface this plan removed, so the deletion is pinned by name rather than left to review.
     */
    @Test
    void shouldNotDeclareTheDeletedUnenforcedConstants() {
        Set<String> deleted = Set.of(
                "DANGEROUS_HEADER_NAMES", "DEBUG_HEADER_NAMES", "SUSPICIOUS_COOKIE_NAMES",
                "SAFE_CONTENT_TYPES", "DANGEROUS_CONTENT_TYPES", "UPLOAD_CONTENT_TYPES",
                "NULL_BYTE", "PROBLEMATIC_CONTROL_CHARS", "INJECTION_CHARACTERS",
                "DOUBLE_ENCODING_PATTERNS", "UNICODE_NORMALIZATION_FORMS");

        Set<String> declared = Arrays.stream(SecurityDefaults.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        Set<String> reintroduced = new HashSet<>(deleted);
        reintroduced.retainAll(declared);

        assertTrue(reintroduced.isEmpty(),
                "Deliberately deleted unenforced constants must not be re-introduced: " + reintroduced);
    }

    @Test
    void shouldHavePrebuiltConfigurations() {
        assertNotNull(SecurityDefaults.STRICT_CONFIGURATION);
        assertNotNull(SecurityDefaults.DEFAULT_CONFIGURATION);
        assertNotNull(SecurityDefaults.LENIENT_CONFIGURATION);

        assertTrue(SecurityDefaults.STRICT_CONFIGURATION.isStrict());
        assertFalse(SecurityDefaults.DEFAULT_CONFIGURATION.isStrict());
        assertFalse(SecurityDefaults.DEFAULT_CONFIGURATION.isLenient());
        assertTrue(SecurityDefaults.LENIENT_CONFIGURATION.isLenient());
    }

    @Test
    void shouldHaveConsistentStrictConfiguration() {
        SecurityConfiguration strict = SecurityDefaults.STRICT_CONFIGURATION;

        assertEquals(SecurityDefaults.MAX_PATH_LENGTH_STRICT, strict.maxPathLength());
        assertEquals(SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_STRICT, strict.maxParameterNameLength());
        assertEquals(SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_STRICT, strict.maxParameterValueLength());
        assertEquals(SecurityDefaults.MAX_HEADER_NAME_LENGTH_STRICT, strict.maxHeaderNameLength());
        assertEquals(SecurityDefaults.MAX_HEADER_VALUE_LENGTH_STRICT, strict.maxHeaderValueLength());
        assertEquals(SecurityDefaults.MAX_COOKIE_NAME_LENGTH_STRICT, strict.maxCookieNameLength());
        assertEquals(SecurityDefaults.MAX_COOKIE_VALUE_LENGTH_STRICT, strict.maxCookieValueLength());
        assertEquals(SecurityDefaults.MAX_BODY_SIZE_STRICT, strict.maxBodySize());

        assertFalse(strict.allowDoubleEncoding());
        assertFalse(strict.allowNullBytes());
        assertFalse(strict.allowControlCharacters());
        // STRICT_CONFIGURATION passes allowExtendedAscii POSITIONALLY to the record constructor.
        // Pinning it here - together with the LENIENT assertion below - catches a future
        // positional-argument slip, which no compiler check would surface between two booleans.
        assertFalse(strict.allowExtendedAscii());
        assertTrue(strict.normalizeUnicode());
    }

    @Test
    void shouldHaveConsistentDefaultConfiguration() {
        SecurityConfiguration defaults = SecurityDefaults.DEFAULT_CONFIGURATION;

        assertEquals(SecurityDefaults.MAX_PATH_LENGTH_DEFAULT, defaults.maxPathLength());
        assertEquals(SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_DEFAULT, defaults.maxParameterNameLength());
        assertEquals(SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_DEFAULT, defaults.maxParameterValueLength());
        assertEquals(SecurityDefaults.MAX_HEADER_NAME_LENGTH_DEFAULT, defaults.maxHeaderNameLength());
        assertEquals(SecurityDefaults.MAX_HEADER_VALUE_LENGTH_DEFAULT, defaults.maxHeaderValueLength());
        assertEquals(SecurityDefaults.MAX_BODY_SIZE_DEFAULT, defaults.maxBodySize());

        assertFalse(defaults.allowDoubleEncoding());
        assertFalse(defaults.allowNullBytes());
        assertFalse(defaults.allowControlCharacters());
        // The fail-secure default. DEFAULT_CONFIGURATION is builder().build(), so this is the
        // preset the builder flip moves - and the only one.
        assertFalse(defaults.allowExtendedAscii());

        // The default preset must be identical to plain builder defaults
        assertEquals(SecurityConfiguration.builder().build(), defaults);
    }

    @Test
    void shouldHaveConsistentLenientConfiguration() {
        SecurityConfiguration lenient = SecurityDefaults.LENIENT_CONFIGURATION;

        assertEquals(SecurityDefaults.MAX_PATH_LENGTH_LENIENT, lenient.maxPathLength());
        assertEquals(SecurityDefaults.MAX_PARAMETER_NAME_LENGTH_LENIENT, lenient.maxParameterNameLength());
        assertEquals(SecurityDefaults.MAX_PARAMETER_VALUE_LENGTH_LENIENT, lenient.maxParameterValueLength());
        assertEquals(SecurityDefaults.MAX_HEADER_NAME_LENGTH_LENIENT, lenient.maxHeaderNameLength());
        assertEquals(SecurityDefaults.MAX_HEADER_VALUE_LENGTH_LENIENT, lenient.maxHeaderValueLength());
        assertEquals(SecurityDefaults.MAX_BODY_SIZE_LENIENT, lenient.maxBodySize());

        // Both flags keep their lenient values, but neither disables a detection gate any more
        // (ADR-0017): the double-encoding gates and the Unicode structural-fold check are
        // unconditional. These assertions pin the recorded VALUES, not a relaxation.
        assertTrue(lenient.allowDoubleEncoding(),
                "Lenient preset records allowDoubleEncoding=true, which no longer relaxes the gate");
        assertFalse(lenient.normalizeUnicode(),
                "Lenient preset returns the un-normalised form; the fold is still computed and inspected");

        assertFalse(lenient.allowNullBytes()); // Never allowed, even in lenient mode
        assertTrue(lenient.allowControlCharacters());
        // LENIENT_CONFIGURATION passes allowExtendedAscii POSITIONALLY as true, and must keep
        // doing so after the builder default flipped to false - the two presets are only
        // distinguishable on this flag while this assertion and its STRICT counterpart hold.
        assertTrue(lenient.allowExtendedAscii());
    }

    @Test
    void shouldHaveImmutableSets() {
        assertThrows(UnsupportedOperationException.class, () ->
                SecurityDefaults.PATH_TRAVERSAL_PATTERNS.add("test"));

        assertThrows(UnsupportedOperationException.class, () ->
                SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES.add("test"));
    }

    @Test
    void shouldHaveNonEmptySets() {
        assertTrue(SecurityDefaults.PATH_TRAVERSAL_PATTERNS.size() > 5);
        assertTrue(SecurityDefaults.SENSITIVE_PATH_PATTERNS.size() > 5);
        assertTrue(SecurityDefaults.PROTOCOL_HANDLER_SCHEMES.size() > 3);
        assertTrue(SecurityDefaults.SUSPICIOUS_PARAMETER_NAMES.size() > 5);
        // XSS patterns removed - application layer responsibility
    }
}
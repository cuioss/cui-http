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
package de.cuioss.http.security.generators.cookie;

import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.data.Cookie;
import de.cuioss.http.security.validation.CookiePrefixValidationStage;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link CookieNameUnicodeWhitespaceGenerator}.
 *
 * <p>The defining property of this generator is that every value is a {@code __Host-} or
 * {@code __Secure-} prefixed cookie name decorated - in a leading, trailing or surrounding
 * position - with one of thirteen non-ASCII whitespace characters: U+2000 to U+200A, NO-BREAK
 * SPACE (U+00A0) and NEXT LINE (U+0085). None of them is stripped by the leading/trailing
 * whitespace check, so the cookie route,
 * {@link CookiePrefixValidationStage#validateCookie(Cookie)}, rejects every such name in
 * character validation as {@code INVALID_CHARACTER}.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("CookieNameUnicodeWhitespaceGenerator Contract Tests")
class CookieNameUnicodeWhitespaceGeneratorTest {

    private static final int AGGREGATE_DRAWS = 600;
    private static final Set<String> PREFIXED_NAMES =
            Set.of("__Host-session", "__Secure-token", "__Secure-session");
    private static final Set<Integer> UNICODE_WHITESPACE = Set.of(
            0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
            0x00A0, 0x0085);

    private static final HttpSecurityValidator COOKIE_ROUTE = name -> {
        new CookiePrefixValidationStage().validateCookie(new Cookie(name, "value", "Secure; Path=/"));
        return Optional.ofNullable(name);
    };

    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameUnicodeWhitespaceGenerator.class, count = 100)
    @DisplayName("Every value decorates a prefixed name with Unicode whitespace and is rejected as INVALID_CHARACTER")
    void shouldDecoratePrefixedNameWithUnicodeWhitespace(String generatedName) {
        int whitespace = whitespaceOf(generatedName);
        assertTrue(UNICODE_WHITESPACE.contains(whitespace),
                () -> "The decoration must be a documented Unicode whitespace character, but was U+%04X"
                        .formatted(whitespace));
        assertTrue(PREFIXED_NAMES.contains(undecorated(generatedName, whitespace)),
                () -> "The decorated name must be a prefixed cookie name. Value: <" + generatedName + ">");

        assertPipelineRejects(COOKIE_ROUTE, generatedName, UrlSecurityFailureType.INVALID_CHARACTER);
    }

    @Test
    @DisplayName("Should reach all thirteen whitespace characters, every placement and every name")
    void shouldReachEveryDocumentedPattern() {
        CookieNameUnicodeWhitespaceGenerator generator = new CookieNameUnicodeWhitespaceGenerator();
        Set<Integer> whitespaceCharacters = new HashSet<>();
        Set<String> placements = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            int whitespace = whitespaceOf(value);
            whitespaceCharacters.add(whitespace);
            placements.add(placementOf(value, whitespace));
            names.add(undecorated(value, whitespace));
        }

        assertAll("Documented patterns",
                () -> assertEquals(UNICODE_WHITESPACE, whitespaceCharacters,
                        "Every documented Unicode whitespace character must be reachable"),
                () -> assertEquals(Set.of("leading", "trailing", "surrounding"), placements,
                        "Every placement must be reachable"),
                () -> assertEquals(PREFIXED_NAMES, names, "Every prefixed name must be reachable"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new CookieNameUnicodeWhitespaceGenerator().getType(),
                "Generator should return String.class");
    }

    /** The decoration is the first character unless the value starts with the name itself. */
    private static int whitespaceOf(String value) {
        return value.charAt(value.charAt(0) != '_' ? 0 : value.length() - 1);
    }

    private static String undecorated(String value, int whitespace) {
        return value.replace(String.valueOf((char) whitespace), "");
    }

    private static String placementOf(String value, int whitespace) {
        boolean leading = value.charAt(0) == whitespace;
        boolean trailing = value.charAt(value.length() - 1) == whitespace;
        if (leading && trailing) {
            return "surrounding";
        }
        return leading ? "leading" : "trailing";
    }
}

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
 * Contract test for {@link CookieNameZeroWidthGenerator}.
 *
 * <p>The defining property of this generator is that every value is a cookie name carrying
 * exactly one zero-width character - U+200B, U+200C, U+200D or U+FEFF - at one of six documented
 * injection points. A zero-width character is outside the cookie-name token set, so the cookie
 * route, {@link CookiePrefixValidationStage#validateCookie(Cookie)}, rejects every such name as
 * {@code INVALID_CHARACTER}.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("CookieNameZeroWidthGenerator Contract Tests")
class CookieNameZeroWidthGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final Set<Integer> ZERO_WIDTH_CHARACTERS = Set.of(0x200B, 0x200C, 0x200D, 0xFEFF);

    /** The six injection points, each as the undecorated name and the index the character sits at. */
    private static final Set<String> INJECTION_POINTS = Set.of(
            "__Host-session@0", "__Host-session@6", "__Host-session@7",
            "__Secure-token@0", "__Secure-token@9", "session_id@7");

    private static final HttpSecurityValidator COOKIE_ROUTE = name -> {
        new CookiePrefixValidationStage().validateCookie(new Cookie(name, "value", "Secure; Path=/"));
        return Optional.ofNullable(name);
    };

    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameZeroWidthGenerator.class, count = 100)
    @DisplayName("Every value carries one zero-width character at a documented point and is rejected as INVALID_CHARACTER")
    void shouldInjectOneZeroWidthCharacter(String generatedName) {
        assertEquals(1, generatedName.chars().filter(ZERO_WIDTH_CHARACTERS::contains).count(),
                () -> "A value carries exactly one zero-width character. Value: <" + generatedName + ">");
        assertTrue(INJECTION_POINTS.contains(injectionPointOf(generatedName)),
                () -> "The zero-width character must sit at a documented injection point, but was: "
                        + injectionPointOf(generatedName));

        assertPipelineRejects(COOKIE_ROUTE, generatedName, UrlSecurityFailureType.INVALID_CHARACTER);
    }

    @Test
    @DisplayName("Should reach all four zero-width characters and all six injection points")
    void shouldReachEveryDocumentedPattern() {
        CookieNameZeroWidthGenerator generator = new CookieNameZeroWidthGenerator();
        Set<Integer> zeroWidthCharacters = new HashSet<>();
        Set<String> injectionPoints = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            zeroWidthCharacters.add((int) value.charAt(zeroWidthIndexOf(value)));
            injectionPoints.add(injectionPointOf(value));
        }

        assertAll("Documented patterns",
                () -> assertEquals(ZERO_WIDTH_CHARACTERS, zeroWidthCharacters,
                        "Every documented zero-width character must be reachable"),
                () -> assertEquals(INJECTION_POINTS, injectionPoints,
                        "Every documented injection point must be reachable"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new CookieNameZeroWidthGenerator().getType(),
                "Generator should return String.class");
    }

    private static int zeroWidthIndexOf(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (ZERO_WIDTH_CHARACTERS.contains((int) value.charAt(index))) {
                return index;
            }
        }
        return fail("Value carries no zero-width character. Value: <" + value + ">");
    }

    private static String injectionPointOf(String value) {
        int index = zeroWidthIndexOf(value);
        return value.substring(0, index) + value.substring(index + 1) + "@" + index;
    }
}

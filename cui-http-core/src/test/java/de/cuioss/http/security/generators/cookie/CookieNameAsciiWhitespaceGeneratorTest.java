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
 * Contract test for {@link CookieNameAsciiWhitespaceGenerator}.
 *
 * <p>The defining property of this generator is that every value is a {@code __Host-} or
 * {@code __Secure-} prefixed cookie name decorated with one kind of ASCII whitespace - spaces or
 * tabs, never both - in a leading, trailing, surrounding or embedded position. The cookie route,
 * {@link CookiePrefixValidationStage#validateCookie(Cookie)}, rejects every such name: a name
 * with leading or trailing whitespace fails the whitespace check as {@code INVALID_CHARACTER},
 * an embedded space is a printable character outside the cookie-name token set
 * ({@code INVALID_CHARACTER}), and an embedded tab is a C0 control
 * ({@code CONTROL_CHARACTERS}).</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("CookieNameAsciiWhitespaceGenerator Contract Tests")
class CookieNameAsciiWhitespaceGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final Set<String> PREFIXED_NAMES = Set.of("__Host-session", "__Secure-token", "__Host-token");

    private static final HttpSecurityValidator COOKIE_ROUTE = name -> {
        new CookiePrefixValidationStage().validateCookie(new Cookie(name, "value", "Secure; Path=/"));
        return Optional.ofNullable(name);
    };

    private enum Placement {
        LEADING, TRAILING, SURROUNDING, EMBEDDED
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameAsciiWhitespaceGenerator.class, count = 100)
    @DisplayName("Every value decorates a prefixed name with one whitespace kind and is rejected with its exact verdict")
    void shouldDecoratePrefixedNameWithOneWhitespaceKind(String generatedName) {
        assertTrue(PREFIXED_NAMES.contains(undecorated(generatedName)),
                () -> "The decorated name must be a prefixed cookie name. Value: <" + generatedName + ">");
        assertNotEquals(generatedName.indexOf(' ') >= 0, generatedName.indexOf('\t') >= 0,
                () -> "A value carries spaces or tabs, never both and never neither. Value: <" + generatedName + ">");

        UrlSecurityFailureType expected = placementOf(generatedName) == Placement.EMBEDDED
                && generatedName.indexOf('\t') >= 0
                ? UrlSecurityFailureType.CONTROL_CHARACTERS
                : UrlSecurityFailureType.INVALID_CHARACTER;
        assertPipelineRejects(COOKIE_ROUTE, generatedName, expected);
    }

    @Test
    @DisplayName("Should reach every placement with both whitespace kinds, every name and a whitespace run")
    void shouldReachEveryDocumentedPattern() {
        CookieNameAsciiWhitespaceGenerator generator = new CookieNameAsciiWhitespaceGenerator();
        Set<String> placements = new HashSet<>();
        Set<String> names = new HashSet<>();
        boolean whitespaceRun = false;

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            placements.add(placementOf(value) + (value.indexOf('\t') >= 0 ? "/tab" : "/space"));
            names.add(undecorated(value));
            whitespaceRun |= value.contains("  ") || value.contains("\t\t");
        }

        Set<String> documentedPlacements = new HashSet<>();
        for (Placement placement : Placement.values()) {
            documentedPlacements.add(placement + "/space");
            documentedPlacements.add(placement + "/tab");
        }
        boolean reachedWhitespaceRun = whitespaceRun;
        assertAll("Documented patterns",
                () -> assertEquals(documentedPlacements, placements,
                        "Every placement must be reachable with a space and with a tab"),
                () -> assertEquals(PREFIXED_NAMES, names, "Every prefixed name must be reachable"),
                () -> assertTrue(reachedWhitespaceRun, "A run of multiple whitespace characters must be reachable"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new CookieNameAsciiWhitespaceGenerator().getType(),
                "Generator should return String.class");
    }

    private static String undecorated(String value) {
        return value.replace(" ", "").replace("\t", "");
    }

    private static Placement placementOf(String value) {
        boolean leading = isAsciiWhitespace(value.charAt(0));
        boolean trailing = isAsciiWhitespace(value.charAt(value.length() - 1));
        if (leading && trailing) {
            return Placement.SURROUNDING;
        }
        if (leading) {
            return Placement.LEADING;
        }
        if (trailing) {
            return Placement.TRAILING;
        }
        return Placement.EMBEDDED;
    }

    private static boolean isAsciiWhitespace(char character) {
        return character == ' ' || character == '\t';
    }
}

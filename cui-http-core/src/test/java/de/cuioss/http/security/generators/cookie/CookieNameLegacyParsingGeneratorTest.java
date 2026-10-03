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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link CookieNameLegacyParsingGenerator}.
 *
 * <p>The defining property of this generator is that every value opens with a
 * {@code $Version=} attribute - the RFC 2109 legacy parsing trigger - followed by a separator and
 * a {@code __Host-} or {@code __Secure-} prefixed cookie name. The cookie route,
 * {@link CookiePrefixValidationStage#validateCookie(Cookie)}, rejects every such name as
 * {@code INVALID_CHARACTER}: the {@code =} of the version attribute is outside the cookie-name
 * token set, and it precedes every separator.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("CookieNameLegacyParsingGenerator Contract Tests")
class CookieNameLegacyParsingGeneratorTest {

    private static final int AGGREGATE_DRAWS = 200;
    private static final Pattern LEGACY_NAME =
            Pattern.compile("\\$Version=([12])(, |,|;)(__Host-session|__Secure-token)");

    private static final HttpSecurityValidator COOKIE_ROUTE = name -> {
        new CookiePrefixValidationStage().validateCookie(new Cookie(name, "value", "Secure; Path=/"));
        return Optional.ofNullable(name);
    };

    @ParameterizedTest
    @TypeGeneratorSource(value = CookieNameLegacyParsingGenerator.class, count = 60)
    @DisplayName("Every value is a $Version trigger ahead of a prefixed name and is rejected as INVALID_CHARACTER")
    void shouldGenerateLegacyParsingTrigger(String generatedName) {
        assertTrue(LEGACY_NAME.matcher(generatedName).matches(),
                () -> "A legacy parsing trigger is '$Version=<n><separator><prefixed name>'. Value: <"
                        + generatedName + ">");

        assertPipelineRejects(COOKIE_ROUTE, generatedName, UrlSecurityFailureType.INVALID_CHARACTER);
    }

    @Test
    @DisplayName("Should reach both versions, every separator and both prefixed names")
    void shouldReachEveryDocumentedPattern() {
        CookieNameLegacyParsingGenerator generator = new CookieNameLegacyParsingGenerator();
        Set<String> versions = new HashSet<>();
        Set<String> separators = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            Matcher matcher = LEGACY_NAME.matcher(value);
            assertTrue(matcher.matches(), () -> "Unexpected legacy parsing trigger. Value: <" + value + ">");
            versions.add(matcher.group(1));
            separators.add(matcher.group(2));
            names.add(matcher.group(3));
        }

        assertAll("Documented patterns",
                () -> assertEquals(Set.of("1", "2"), versions, "Both $Version values must be reachable"),
                () -> assertEquals(Set.of(",", ";", ", "), separators, "Every separator must be reachable"),
                () -> assertEquals(Set.of("__Host-session", "__Secure-token"), names,
                        "Both prefixed names must be reachable"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new CookieNameLegacyParsingGenerator().getType(),
                "Generator should return String.class");
    }
}

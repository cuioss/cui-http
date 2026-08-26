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
package de.cuioss.http.security.generators.header;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link InvalidHTTPHeaderNameGenerator}.
 *
 * <p>The defining property of this generator is that every emitted name is a well-formed
 * RFC 7230 field-name token spliced with exactly one injection separator, followed by the
 * literal payload {@code Injected}. That shape is what makes the value an injection attempt
 * rather than merely an odd string. The aggregate test asserts that all four documented
 * injection variants are reachable.</p>
 */
@EnableGeneratorController
@DisplayName("InvalidHTTPHeaderNameGenerator Contract Tests")
class InvalidHTTPHeaderNameGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final String INJECTED_PAYLOAD = "Injected";
    private static final Pattern RFC7230_TOKEN = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");
    private static final Set<String> INJECTION_SEPARATORS = Set.of("\r", "\n", "\r\n", "\0");

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidHTTPHeaderNameGenerator.class, count = 100)
    @DisplayName("Every generated name splices exactly one injection separator into a valid token")
    void shouldGenerateSplicedHeaderName(String generatedValue) {
        int injectionPoint = indexOfInjectionPoint(generatedValue);
        assertTrue(injectionPoint >= 0,
                () -> "Invalid header name must carry an injection separator. Value: <"
                        + generatedValue + ">");

        String separator = separatorAt(generatedValue, injectionPoint);
        String baseName = generatedValue.substring(0, injectionPoint);
        String payload = generatedValue.substring(injectionPoint + separator.length());

        assertAll("Spliced header name",
                () -> assertTrue(INJECTION_SEPARATORS.contains(separator),
                        () -> "Separator must be one of " + INJECTION_SEPARATORS + ". Value: <"
                                + generatedValue + ">"),
                () -> assertTrue(RFC7230_TOKEN.matcher(baseName).matches(),
                        () -> "Base name must match the RFC 7230 token production. Value: <"
                                + baseName + ">"),
                () -> assertEquals(INJECTED_PAYLOAD, payload,
                        "Everything after the separator must be the injected payload"));
    }

    @Test
    @DisplayName("Should reach all four documented injection variants")
    void shouldReachAllInjectionVariants() {
        InvalidHTTPHeaderNameGenerator generator = new InvalidHTTPHeaderNameGenerator();
        Set<String> separators = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            separators.add(separatorAt(value, indexOfInjectionPoint(value)));
        }

        assertEquals(INJECTION_SEPARATORS, separators,
                "Every documented injection variant must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new InvalidHTTPHeaderNameGenerator().getType(),
                "Generator should return String.class");
    }

    private static int indexOfInjectionPoint(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\r' || current == '\n' || current == '\0') {
                return i;
            }
        }
        return -1;
    }

    private static String separatorAt(String value, int injectionPoint) {
        assertTrue(injectionPoint >= 0,
                () -> "Value carries no injection separator. Value: <" + value + ">");
        if (value.startsWith("\r\n", injectionPoint)) {
            return "\r\n";
        }
        return value.substring(injectionPoint, injectionPoint + 1);
    }
}

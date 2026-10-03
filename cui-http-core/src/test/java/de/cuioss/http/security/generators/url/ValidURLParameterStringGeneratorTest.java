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
package de.cuioss.http.security.generators.url;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineAccepts;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract test for {@link ValidURLParameterStringGenerator}.
 *
 * <p>The defining property of this generator is that every value is a legitimate query parameter
 * value of one of eight shapes - it has the grammar of exactly one of them - and that the URL
 * parameter pipeline accepts it. The aggregate test asserts that all eight shapes are
 * reachable.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("ValidURLParameterStringGenerator Contract Tests")
class ValidURLParameterStringGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final String WORD = "(product|user|order|item|data|content|info|system)";

    private enum Shape {
        NUMERIC("\\d{1,4}"),
        ENCODED_TEXT(WORD + "%20" + WORD),
        SIMPLE_TEXT(WORD),
        BOOLEAN_LIKE("true|false|yes|no|on|off"),
        TIMESTAMP("\\d{10}"),
        ALPHANUMERIC_ID("[A-Za-z]{3,6}\\d{3}"),
        ENCODED_EMAIL("(john|jane|mike|sara)%40(example\\.com|test\\.org|demo\\.net)"),
        PHONE("\\d{3}-\\d{3}-\\d{4}");

        private final Pattern grammar;

        Shape(String grammar) {
            this.grammar = Pattern.compile(grammar);
        }
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = ValidURLParameterStringGenerator.class, count = 200)
    @DisplayName("Every value has the grammar of exactly one shape and is accepted by the parameter pipeline")
    void shouldGenerateLegitimateParameterValue(String generatedValue) {
        shapeOf(generatedValue);

        assertPipelineAccepts(
                new URLParameterValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach all eight parameter value shapes")
    void shouldReachAllShapes() {
        ValidURLParameterStringGenerator generator = new ValidURLParameterStringGenerator();
        Set<Shape> shapes = EnumSet.noneOf(Shape.class);

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            shapes.add(shapeOf(generator.next()));
        }

        assertEquals(EnumSet.allOf(Shape.class), shapes,
                "Every parameter value shape must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new ValidURLParameterStringGenerator().getType(),
                "Generator should return String.class");
    }

    private static Shape shapeOf(String value) {
        List<Shape> matching = EnumSet.allOf(Shape.class).stream()
                .filter(shape -> shape.grammar.matcher(value).matches())
                .toList();
        assertEquals(1, matching.size(),
                () -> "A value must have the grammar of exactly one shape, but matched " + matching
                        + ". Value: <" + value + ">");
        return matching.getFirst();
    }
}

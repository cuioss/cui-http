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
import de.cuioss.http.security.data.URLParameter;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ValidURLParameterGenerator}.
 *
 * <p>The defining property of this generator is that every emitted parameter is a legitimate
 * name/value pair: the name is a lower-case identifier, the value is alphanumeric, neither
 * carries an attack marker, and the value is accepted by the URL parameter validation pipeline.
 * The aggregate test asserts that the generator's name and value vocabularies are broad.</p>
 */
@EnableGeneratorController
@DisplayName("ValidURLParameterGenerator Contract Tests")
class ValidURLParameterGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final int MIN_DISTINCT_NAMES = 12;
    private static final int MIN_DISTINCT_VALUE_SHAPES = 6;

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern VALUE_PATTERN = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern NUMERIC_VALUE = Pattern.compile("\\d+");

    private static final Set<String> BOOLEAN_VALUES = Set.of("true", "false");
    private static final Set<String> SORT_VALUES = Set.of("asc", "desc");
    private static final Set<String> FORMAT_VALUES = Set.of("json", "xml", "csv", "html");
    private static final Set<String> LANGUAGE_VALUES = Set.of("en", "de", "fr", "es", "ja");
    private static final Set<String> STATUS_VALUES = Set.of("active", "inactive", "pending", "deleted");
    private static final Set<String> TEST_VALUES = Set.of("test", "example", "demo", "sample");

    @ParameterizedTest
    @TypeGeneratorSource(value = ValidURLParameterGenerator.class, count = 100)
    @DisplayName("Every generated parameter is legitimate and its value is accepted by the pipeline")
    void shouldGenerateLegitimateParameter(URLParameter generatedValue) {
        String name = generatedValue.name();
        String value = generatedValue.value();

        assertTrue(NAME_PATTERN.matcher(name).matches(),
                () -> "Parameter name must be a lower-case identifier. Value: <" + name + ">");
        assertTrue(VALUE_PATTERN.matcher(value).matches(),
                () -> "Parameter value must be alphanumeric. Value: <" + value + ">");
        assertCarriesNoMarker(name, "name");
        assertCarriesNoMarker(value, "value");

        assertPipelineAccepts(
                new URLParameterValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                value);
    }

    @Test
    @DisplayName("Should reach a broad name and value vocabulary")
    void shouldReachBroadVocabulary() {
        ValidURLParameterGenerator generator = new ValidURLParameterGenerator();
        Set<String> names = new HashSet<>();
        Set<String> valueShapes = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            URLParameter parameter = generator.next();
            names.add(parameter.name());
            valueShapes.add(classifyValueShape(parameter.value()));
        }

        assertAll("Vocabulary breadth",
                () -> assertTrue(names.size() >= MIN_DISTINCT_NAMES,
                        () -> "Expected at least " + MIN_DISTINCT_NAMES + " distinct names, got "
                                + names.size() + ": " + names),
                () -> assertTrue(valueShapes.size() >= MIN_DISTINCT_VALUE_SHAPES,
                        () -> "Expected at least " + MIN_DISTINCT_VALUE_SHAPES
                                + " distinct value shapes, got " + valueShapes.size() + ": " + valueShapes));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(URLParameter.class, new ValidURLParameterGenerator().getType(),
                "Generator should return URLParameter.class");
    }

    private static String classifyValueShape(String value) {
        if (NUMERIC_VALUE.matcher(value).matches()) {
            return "number";
        }
        if (BOOLEAN_VALUES.contains(value)) {
            return "boolean";
        }
        if (SORT_VALUES.contains(value)) {
            return "sort";
        }
        if (FORMAT_VALUES.contains(value)) {
            return "format";
        }
        if (LANGUAGE_VALUES.contains(value)) {
            return "language";
        }
        if (STATUS_VALUES.contains(value)) {
            return "status";
        }
        if (TEST_VALUES.contains(value)) {
            return "test";
        }
        return fail("Value <" + value + "> belongs to no documented value shape");
    }

    private static void assertCarriesNoMarker(String component, String description) {
        TRAVERSAL_MARKERS.forEach(marker -> assertFalse(component.contains(marker),
                () -> "Valid parameter " + description + " carries no traversal marker <" + marker
                        + ">. Value: <" + component + ">"));
        NULL_BYTE_MARKERS.forEach(marker -> assertFalse(component.contains(marker),
                () -> "Valid parameter " + description + " carries no null-byte marker <" + marker
                        + ">. Value: <" + component + ">"));
    }
}

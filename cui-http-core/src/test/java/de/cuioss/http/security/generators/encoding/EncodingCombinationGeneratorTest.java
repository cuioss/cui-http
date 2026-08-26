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
package de.cuioss.http.security.generators.encoding;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link EncodingCombinationGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value is a percent-encoded
 * traversal: it carries an encoded dot, and decoding it back to a fixed point yields one of the
 * documented traversal shapes. Because every value is a genuine traversal payload, the per-value
 * pipeline round-trip applies.</p>
 *
 * <p>The aggregate test asserts that all three encoding levels are reachable, that the mixed-case
 * variant is observable, and that all five decoded base-pattern shapes are reachable. The shapes
 * rather than the branches are asserted because the branches are not distinguishable from the
 * output: the Windows branch and the backslash arm of the simple branch emit the same value, as
 * do the deep and custom-depth branches.</p>
 */
@EnableGeneratorController
@DisplayName("EncodingCombinationGenerator Contract Tests")
class EncodingCombinationGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final int MAX_DECODE_ROUNDS = 8;

    /** The encoded dot as it appears at each of the three advertised encoding levels. */
    private static final List<String> ENCODED_DOT_FORMS =
            List.of("%2e", "%2E", "%252e", "%252E", "%25252e");
    private static final Pattern TRAVERSAL_SHAPE = Pattern.compile("^(\\.\\.[/\\\\])+$");

    private static final Pattern SINGLE_FORWARD = Pattern.compile("^\\.\\./$");
    private static final Pattern SINGLE_BACKSLASH = Pattern.compile("^\\.\\.\\\\$");
    private static final Pattern MIXED_SEPARATOR = Pattern.compile("^\\.\\./\\.\\.\\\\\\.\\./$");
    private static final Pattern DEEP_FORWARD = Pattern.compile("^(\\.\\./){2,}$");
    private static final Pattern DEEP_BACKSLASH = Pattern.compile("^(\\.\\.\\\\){2,}$");

    @ParameterizedTest
    @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 100)
    @DisplayName("Every generated value is a percent-encoded traversal the pipeline rejects")
    void shouldGenerateEncodedTraversal(String generatedValue) {
        assertTrue(ENCODED_DOT_FORMS.stream().anyMatch(generatedValue::contains),
                () -> "Value must carry an encoded dot from " + ENCODED_DOT_FORMS
                        + ". Value: <" + generatedValue + ">");

        String decoded = decodeToFixedPoint(generatedValue);
        assertTrue(TRAVERSAL_SHAPE.matcher(decoded).matches(),
                () -> "Fully decoded value must be a traversal. Value: <" + generatedValue
                        + ">, decoded: <" + decoded + ">");

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach all three encoding levels and the mixed-case variant")
    void shouldReachAllEncodingLevels() {
        EncodingCombinationGenerator generator = new EncodingCombinationGenerator();
        Set<String> levels = new HashSet<>();
        boolean mixedCaseObserved = false;

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            if (value.contains("%25252e")) {
                levels.add("level-3");
            } else if (value.contains("%252e") || value.contains("%252E")) {
                levels.add("level-2");
            } else if (value.contains("%2e") || value.contains("%2E")) {
                levels.add("level-1");
            }
            mixedCaseObserved |= value.contains("%2E");
        }

        boolean mixedCaseSeen = mixedCaseObserved;
        assertAll("Encoding levels",
                () -> assertEquals(Set.of("level-1", "level-2", "level-3"), levels,
                        "Every advertised encoding level must be reachable"),
                () -> assertTrue(mixedCaseSeen,
                        "The mixed-case encoding variant must be observable"));
    }

    @Test
    @DisplayName("Should reach all five decoded base-pattern shapes")
    void shouldReachAllBasePatternShapes() {
        EncodingCombinationGenerator generator = new EncodingCombinationGenerator();
        Set<String> shapes = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String decoded = decodeToFixedPoint(generator.next());
            if (SINGLE_FORWARD.matcher(decoded).matches()) {
                shapes.add("single-forward");
            }
            if (SINGLE_BACKSLASH.matcher(decoded).matches()) {
                shapes.add("single-backslash");
            }
            if (MIXED_SEPARATOR.matcher(decoded).matches()) {
                shapes.add("mixed-separator");
            }
            if (DEEP_FORWARD.matcher(decoded).matches()) {
                shapes.add("deep-forward");
            }
            if (DEEP_BACKSLASH.matcher(decoded).matches()) {
                shapes.add("deep-backslash");
            }
        }

        assertEquals(Set.of("single-forward", "single-backslash", "mixed-separator",
                        "deep-forward", "deep-backslash"), shapes,
                "Every decoded base-pattern shape must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new EncodingCombinationGenerator().getType(),
                "Generator should return String.class");
    }

    private static String decodeToFixedPoint(String value) {
        String current = value;
        for (int round = 0; round < MAX_DECODE_ROUNDS; round++) {
            String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8);
            if (decoded.equals(current)) {
                return decoded;
            }
            current = decoded;
        }
        return current;
    }
}

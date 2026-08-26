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
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link PathTraversalParameterGenerator}.
 *
 * <p>The defining property of this generator is that every emitted parameter value carries a
 * traversal marker and is therefore rejected by the URL parameter validation pipeline. The
 * aggregate test asserts that the literal traversal sequences of all eight documented attack
 * families are reachable; the Windows and UTF-8-overlong families each contribute two
 * sequences, because each of those branches alternates between two forms.</p>
 */
@EnableGeneratorController
@DisplayName("PathTraversalParameterGenerator Contract Tests")
class PathTraversalParameterGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    /** Every literal traversal sequence the eight documented attack families emit. */
    private static final List<String> FAMILY_SEQUENCES = List.of(
            "..%2F",                // basic encoded traversal
            "%2E%2E%2F",            // double encoded traversal (also the mixed uppercase arm)
            "%2e%2e%2f",            // mixed lowercase arm and deep traversal
            "..%5c",                // Windows style, partially encoded arm
            "%2e%2e%5c",            // Windows style, fully encoded arm
            "....%2f",              // quad-dot bypass
            "..%c0%af",             // UTF-8 overlong slash arm
            "%c0%ae%c0%ae%c0%af",   // UTF-8 overlong dots-and-slash arm
            "%252e%252e%252f");     // triple encoded traversal

    @ParameterizedTest
    @TypeGeneratorSource(value = PathTraversalParameterGenerator.class, count = 100)
    @DisplayName("Every generated parameter value carries a traversal marker and is rejected")
    void shouldGeneratePathTraversalParameterValue(String generatedValue) {
        assertContainsAny(generatedValue, TRAVERSAL_MARKERS, "Path traversal parameter value");

        assertPipelineRejects(
                new URLParameterValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach the traversal sequences of all eight documented attack families")
    void shouldReachAllAttackFamilySequences() {
        PathTraversalParameterGenerator generator = new PathTraversalParameterGenerator();
        Set<String> reached = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            FAMILY_SEQUENCES.stream().filter(value::contains).forEach(reached::add);
        }

        assertEquals(Set.copyOf(FAMILY_SEQUENCES), reached,
                "Every documented traversal sequence must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new PathTraversalParameterGenerator().getType(),
                "Generator should return String.class");
    }
}

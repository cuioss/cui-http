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
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for {@link PathTraversalURLGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value is a rooted URL path
 * carrying a traversal marker, and is therefore rejected by the URL path validation pipeline.
 * The aggregate test asserts that all six documented encoding families are reachable, each
 * identified by the literal separator sequence its branch emits.</p>
 */
@EnableGeneratorController
@DisplayName("PathTraversalURLGenerator Contract Tests")
class PathTraversalURLGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    /** One literal per documented encoding family, in the branch order of the generator. */
    private static final List<String> FAMILY_MARKERS = List.of(
            "%2E%2E/",          // basic uppercase-encoded traversal
            "%2E%2E%5C",        // Windows-style traversal
            "%252e%252e%252f",  // double-encoded traversal
            "../",              // mixed encoding, unencoded arm
            "%2e%2e/",          // lowercase-encoded traversal
            "%2E%2E%2F");       // multiple-depth traversal

    @ParameterizedTest
    @TypeGeneratorSource(value = PathTraversalURLGenerator.class, count = 100)
    @DisplayName("Every generated URL carries a traversal marker and is rejected by the pipeline")
    void shouldGeneratePathTraversalUrl(String generatedValue) {
        assertTrue(generatedValue.startsWith("/"),
                () -> "Path traversal URLs are rooted at '/'. Value: <" + generatedValue + ">");
        assertContainsAny(generatedValue, TRAVERSAL_MARKERS, "Path traversal URL");

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach all six documented encoding families")
    void shouldReachAllEncodingFamilies() {
        PathTraversalURLGenerator generator = new PathTraversalURLGenerator();
        Set<String> reached = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            FAMILY_MARKERS.stream().filter(value::contains).forEach(reached::add);
        }

        assertEquals(Set.copyOf(FAMILY_MARKERS), reached,
                "Every documented encoding family must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new PathTraversalURLGenerator().getType(),
                "Generator should return String.class");
    }
}

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
import de.cuioss.test.generator.junit.GeneratorSeed;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ValidURLGenerator}.
 *
 * <p>The generator's defining property is that every value is a URL <em>path</em> the URL path
 * pipeline accepts unchanged: it is rooted at {@code /}, carries no query string - a {@code ?}
 * is not a path character - and carries no traversal or null-byte marker.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("ValidURLGenerator Contract Tests")
class ValidURLGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    /** The generator must emit at least this many distinct paths to be worth calling a generator. */
    private static final int MINIMUM_DISTINCT_PATHS = 20;

    @ParameterizedTest
    @TypeGeneratorSource(value = ValidURLGenerator.class, count = 100)
    @DisplayName("Every generated value is a query-free path the path pipeline accepts")
    void shouldGeneratePathThePipelineAccepts(String generatedValue) {
        assertNotNull(generatedValue, "Generator must not produce null values");
        assertTrue(generatedValue.startsWith("/"),
                () -> "A valid path is rooted at '/'. Value: <" + generatedValue + ">");
        assertFalse(generatedValue.contains("?"),
                () -> "A valid path carries no query string. Value: <" + generatedValue + ">");

        assertContainsNone(generatedValue, TRAVERSAL_MARKERS, "Valid path (traversal)");
        assertContainsNone(generatedValue, NULL_BYTE_MARKERS, "Valid path (null-byte)");

        assertPipelineAccepts(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach at least twenty distinct paths")
    void shouldReachDistinctPaths() {
        ValidURLGenerator generator = new ValidURLGenerator();
        Set<String> paths = new HashSet<>();
        for (int draw = 0; draw < AGGREGATE_DRAWS; draw++) {
            paths.add(generator.next());
        }

        assertTrue(paths.size() >= MINIMUM_DISTINCT_PATHS,
                () -> "Expected at least " + MINIMUM_DISTINCT_PATHS + " distinct paths across "
                        + AGGREGATE_DRAWS + " draws but got " + paths.size());
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new ValidURLGenerator().getType(),
                "Generator should return String.class");
    }
}

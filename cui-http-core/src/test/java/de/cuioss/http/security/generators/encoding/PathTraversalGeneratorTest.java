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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.TRAVERSAL_MARKERS;
import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertContainsAny;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract test for {@link PathTraversalGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value carries a traversal
 * marker. The Unicode arms contribute the <em>literal escape text</em> forms of the marker
 * vocabulary rather than decoded characters, because
 * {@link PathTraversalGenerator} builds them from doubled backslashes.</p>
 *
 * <p>There is deliberately no per-value pipeline round-trip: those literal escape-text values
 * are ordinary printable text to a validator, so a uniform "the pipeline rejects it" assertion
 * would not hold.</p>
 *
 * <p>The generator's seven attack types deliberately share encodings — the mixed arm re-emits
 * the basic, encoded and Unicode forms — so the aggregate test asserts reachability of one
 * literal signature per attack type rather than attempting to attribute each value back to the
 * branch that produced it.</p>
 */
@EnableGeneratorController
@DisplayName("PathTraversalGenerator Contract Tests")
class PathTraversalGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    private static final List<String> BASIC_SIGNATURES = List.of("../", "..\\");
    private static final List<String> ENCODED_SIGNATURES = List.of("%2e%2e%2f", "%2e%2e%5c");
    private static final List<String> DOUBLE_ENCODED_SIGNATURES = List.of("%252e%252e");
    /** Escape text with an escaped separator, emitted only by {@code generateUnicodeTraversal}. */
    private static final List<String> UNICODE_LITERAL_SIGNATURES =
            List.of("\\u002e\\u002e\\u002f", "\\u002e\\u002e\\u005c");
    /** Escape text with a raw separator, emitted only by the mixed arm. */
    private static final List<String> MIXED_SIGNATURES =
            List.of("\\u002e\\u002e/", "\\u002e\\u002e\\");
    private static final List<String> NULL_BYTE_SIGNATURES = List.of("%00");
    private static final List<String> ADVANCED_SIGNATURES =
            List.of("....", "%c0%ae%c0%ae%c0%af", "\\ufe0e\\ufe0e\\u2044", "/var/www/", "C:\\inetpub\\wwwroot\\");

    @ParameterizedTest
    @TypeGeneratorSource(value = PathTraversalGenerator.class, count = 100)
    @DisplayName("Every generated value carries a traversal marker")
    void shouldGeneratePathTraversal(String generatedValue) {
        assertContainsAny(generatedValue, TRAVERSAL_MARKERS, "Path traversal value");
    }

    @Test
    @DisplayName("Should reach a signature of all seven documented attack types")
    void shouldReachAllAttackTypes() {
        PathTraversalGenerator generator = new PathTraversalGenerator();
        Set<String> attackTypes = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            recordIfMatched(attackTypes, "basic", value, BASIC_SIGNATURES);
            recordIfMatched(attackTypes, "encoded", value, ENCODED_SIGNATURES);
            recordIfMatched(attackTypes, "double-encoded", value, DOUBLE_ENCODED_SIGNATURES);
            recordIfMatched(attackTypes, "unicode-literal", value, UNICODE_LITERAL_SIGNATURES);
            recordIfMatched(attackTypes, "mixed", value, MIXED_SIGNATURES);
            recordIfMatched(attackTypes, "null-byte", value, NULL_BYTE_SIGNATURES);
            recordIfMatched(attackTypes, "advanced", value, ADVANCED_SIGNATURES);
        }

        assertEquals(Set.of("basic", "encoded", "double-encoded", "unicode-literal",
                        "mixed", "null-byte", "advanced"), attackTypes,
                "Every documented attack type must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new PathTraversalGenerator().getType(),
                "Generator should return String.class");
    }

    private static void recordIfMatched(Set<String> attackTypes, String attackType,
            String value, List<String> signatures) {
        if (signatures.stream().anyMatch(value::contains)) {
            attackTypes.add(attackType);
        }
    }
}

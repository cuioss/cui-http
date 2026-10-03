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
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract test for {@link PathTraversalParameterGenerator}.
 *
 * <p>The defining property of this generator is that every emitted parameter value carries a
 * traversal marker and is rejected by the URL parameter validation pipeline with the one failure
 * type its arm earns. The aggregate tests assert that the literal traversal sequences of all
 * eight documented attack families are reachable - the Windows and UTF-8-overlong families each
 * contribute two sequences, because each of those branches alternates between two forms - and
 * that all three verdict classes are.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
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

    /** The lead byte of a two-byte UTF-8 overlong encoding, emitted only by the overlong arm. */
    private static final String OVERLONG_LEAD_BYTE = "%c0";

    /** An encoded percent sign, emitted only by the triple-encoded arm. */
    private static final String ENCODED_PERCENT = "%25";

    @ParameterizedTest
    @TypeGeneratorSource(value = PathTraversalParameterGenerator.class, count = 100)
    @DisplayName("Every generated parameter value carries a traversal marker and is rejected with its arm's failure type")
    void shouldGeneratePathTraversalParameterValue(String generatedValue) {
        assertContainsAny(generatedValue, TRAVERSAL_MARKERS, "Path traversal parameter value");

        URLParameterValidationPipeline pipeline =
                new URLParameterValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter());
        UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(generatedValue),
                () -> "Pipeline must reject the generated value. Value: <" + preview(generatedValue) + ">");

        assertEquals(expectedFailureType(generatedValue), exception.getFailureType(),
                () -> "Unexpected verdict for: <" + preview(generatedValue) + ">");
        assertEquals(generatedValue, exception.getOriginalInput(),
                "Rejection must report the generated value as its original input");
    }

    @Test
    @DisplayName("Should reach an arm of each of the three failure types")
    void shouldReachEveryFailureType() {
        PathTraversalParameterGenerator generator = new PathTraversalParameterGenerator();
        Set<UrlSecurityFailureType> reached = EnumSet.noneOf(UrlSecurityFailureType.class);

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            reached.add(expectedFailureType(generator.next()));
        }

        assertEquals(EnumSet.of(UrlSecurityFailureType.INVALID_ENCODING, UrlSecurityFailureType.DOUBLE_ENCODING,
                UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED), reached,
                "Every verdict class must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    /**
     * Names the one failure type the parameter pipeline reports for the arm that emitted the
     * value. The parameter pipeline matches patterns only after decoding, so what the decoding
     * stage meets first decides: the UTF-8 overlong arm is refused as invalid encoding, the
     * triple-encoded arm as double encoding, and the six remaining arms decode cleanly to a
     * {@code ../} or {@code ..\} sequence that the pattern stage reports as path traversal.
     */
    private static UrlSecurityFailureType expectedFailureType(String generatedValue) {
        if (generatedValue.contains(OVERLONG_LEAD_BYTE)) {
            return UrlSecurityFailureType.INVALID_ENCODING;
        }
        if (generatedValue.contains(ENCODED_PERCENT)) {
            return UrlSecurityFailureType.DOUBLE_ENCODING;
        }
        return UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED;
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

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

import de.cuioss.http.security.data.URLParameter;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link AttackURLParameterGenerator}.
 *
 * <p>The defining property of this generator is that the parameter <em>value</em> carries an
 * attack payload on its own. The generator also emits a hostile parameter name, but that is an
 * independent attack surface: a value is not permitted to be benign filler that only becomes an
 * attack once a caller pairs it with such a name. The per-value assertion below therefore looks
 * at the value alone.</p>
 *
 * <p>This is a focused test of that invariant plus the two overlong size bands, not a full
 * contract suite over the generator's nine value families.</p>
 */
@EnableGeneratorController
@DisplayName("AttackURLParameterGenerator Contract Tests")
class AttackURLParameterGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    /**
     * Payload markers the shared vocabularies do not cover: the hostile protocol schemes and the
     * system paths the generator's protocol and file-path families emit.
     */
    private static final Set<String> PROTOCOL_AND_SYSTEM_PATH_MARKERS = Set.of(
            "javascript:", "file:", "data:", "/etc/", "windows/", "\\windows\\");

    /** The closed vocabulary of markers, at least one of which every emitted value must carry. */
    private static final Set<String> ATTACK_MARKERS = Stream.of(
            TRAVERSAL_MARKERS, NULL_BYTE_MARKERS, SHELL_METACHARACTERS,
            PROTOCOL_AND_SYSTEM_PATH_MARKERS)
            .flatMap(Set::stream)
            .collect(Collectors.toUnmodifiableSet());

    /** Length band of {@code generateLongStringAttack}: 50..200 padded by up to a further 150. */
    private static final int LONG_BAND_MIN = 50;
    private static final int LONG_BAND_MAX = 350;
    /** Length band of {@code generateVeryLongStringAttack}: 500..1000 padded by up to a further 500. */
    private static final int VERY_LONG_BAND_MIN = 500;
    private static final int VERY_LONG_BAND_MAX = 1500;

    @ParameterizedTest
    @TypeGeneratorSource(value = AttackURLParameterGenerator.class, count = 100)
    @DisplayName("Every generated value carries an attack marker independently of its name")
    void shouldGenerateAttackCarryingValue(URLParameter generatedParameter) {
        assertContainsAny(generatedParameter.value(), ATTACK_MARKERS, "Attack parameter value");
    }

    @Test
    @DisplayName("Should reach both overlong value bands, each still carrying an attack marker")
    void shouldReachBothOverlongBands() {
        AttackURLParameterGenerator generator = new AttackURLParameterGenerator();
        Set<String> reachedBands = new HashSet<>();

        for (int draw = 0; draw < AGGREGATE_DRAWS; draw++) {
            String value = generator.next().value();
            assertContainsAny(value, ATTACK_MARKERS, "Attack parameter value");
            if (isWithin(value, LONG_BAND_MIN, LONG_BAND_MAX)) {
                reachedBands.add("long");
            }
            if (isWithin(value, VERY_LONG_BAND_MIN, VERY_LONG_BAND_MAX)) {
                reachedBands.add("very-long");
            }
        }

        assertAll("Overlong value bands",
                () -> assertTrue(reachedBands.contains("long"),
                        "A value in the " + LONG_BAND_MIN + ".." + LONG_BAND_MAX
                                + " character band must be reachable within " + AGGREGATE_DRAWS + " draws"),
                () -> assertTrue(reachedBands.contains("very-long"),
                        "A value in the " + VERY_LONG_BAND_MIN + ".." + VERY_LONG_BAND_MAX
                                + " character band must be reachable within " + AGGREGATE_DRAWS + " draws"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(URLParameter.class, new AttackURLParameterGenerator().getType(),
                "Generator should return URLParameter.class");
    }

    private static boolean isWithin(String value, int minLength, int maxLength) {
        return value.length() >= minLength && value.length() <= maxLength;
    }
}

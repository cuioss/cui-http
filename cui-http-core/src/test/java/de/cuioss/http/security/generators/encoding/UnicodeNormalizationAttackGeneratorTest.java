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
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntPredicate;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link UnicodeNormalizationAttackGenerator}.
 *
 * <p>The defining property of this generator is that every value is a base attack pattern
 * respelled by one of nine documented techniques, each of which leaves a recognisable signature
 * in the value. Whatever the technique, the respelling puts a raw character outside the URL path
 * character set into the value - a non-ASCII character, or the {@code <} or {@code \} of the base
 * pattern ahead of it - so the URL path pipeline rejects every value in character validation as
 * {@code INVALID_CHARACTER}, before any normalization is computed.</p>
 *
 * <p>Technique attribution is first-match-wins over the ordered classifier below, because the
 * signatures nest: the mixed-script technique uses Cyrillic letters just as the homograph
 * technique does, and is told apart by the Greek letters it adds.</p>
 *
 * <p>The signature characters are named by their code points throughout, so that no combining,
 * zero-width or bidirectional character is embedded in this source file.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("UnicodeNormalizationAttackGenerator Contract Tests")
class UnicodeNormalizationAttackGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    private static final int FULL_STOP = 0x002E;
    private static final int LESS_THAN_SIGN = 0x003C;
    private static final int COMBINING_GRAVE_ACCENT = 0x0300;
    private static final int COMBINING_ACUTE_ACCENT = 0x0301;
    private static final int COMBINING_CIRCUMFLEX_ACCENT = 0x0302;
    private static final int ARABIC_FULL_STOP = 0x06D4;
    private static final int ZERO_WIDTH_SPACE = 0x200B;
    private static final int ONE_DOT_LEADER = 0x2024;
    private static final int RIGHT_TO_LEFT_OVERRIDE = 0x202E;
    private static final int DIVISION_SLASH = 0x2215;

    /** The decomposed technique: grave accent on the full stop, circumflex on the less-than sign. */
    private static final Set<String> DECOMPOSED_SIGNATURES = Set.of(
            fromCodePoints(FULL_STOP, COMBINING_GRAVE_ACCENT),
            fromCodePoints(LESS_THAN_SIGN, COMBINING_CIRCUMFLEX_ACCENT));

    /** The combining technique: acute accent on the full stop, grave accent on the less-than sign. */
    private static final Set<String> COMBINING_SIGNATURES = Set.of(
            fromCodePoints(FULL_STOP, COMBINING_ACUTE_ACCENT),
            fromCodePoints(LESS_THAN_SIGN, COMBINING_GRAVE_ACCENT));

    /**
     * The substitutes of the composed technique: FULLWIDTH FULL STOP, FRACTION SLASH, REVERSE
     * SOLIDUS OPERATOR, FULLWIDTH COLON and FULLWIDTH EQUALS SIGN.
     */
    private static final String COMPOSED_SUBSTITUTES = fromCodePoints(0xFF0E, 0x2044, 0x29F5, 0xFF1A, 0xFF1D);

    private enum Technique {
        DECOMPOSED, COMPOSED, COMPATIBILITY, COMBINING, HOMOGRAPH, OVERLONG_STYLE, MIXED_SCRIPT, ZERO_WIDTH,
        BIDIRECTIONAL
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UnicodeNormalizationAttackGenerator.class, count = 200)
    @DisplayName("Every value carries the signature of a documented technique and is rejected as INVALID_CHARACTER")
    void shouldGenerateNormalizationAttack(String generatedValue) {
        Technique technique = techniqueOf(generatedValue);
        if (technique == Technique.COMPATIBILITY) {
            assertNotEquals(generatedValue, Normalizer.normalize(generatedValue, Normalizer.Form.NFKC),
                    () -> "A compatibility attack must fold under NFKC. Value: <" + preview(generatedValue) + ">");
        }

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue, UrlSecurityFailureType.INVALID_CHARACTER);
    }

    @Test
    @DisplayName("Should reach all nine documented normalization techniques")
    void shouldReachAllTechniques() {
        UnicodeNormalizationAttackGenerator generator = new UnicodeNormalizationAttackGenerator();
        Set<Technique> techniques = EnumSet.noneOf(Technique.class);

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            techniques.add(techniqueOf(generator.next()));
        }

        assertEquals(EnumSet.allOf(Technique.class), techniques,
                "Every documented technique must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new UnicodeNormalizationAttackGenerator().getType(),
                "Generator should return String.class");
    }

    /**
     * Attributes a value to its technique. The base patterns all contain a full stop or a
     * less-than sign, which is what tells the decomposed technique from the combining one: they
     * put different accents on those two characters.
     */
    private static Technique techniqueOf(String value) {
        if (value.charAt(0) == RIGHT_TO_LEFT_OVERRIDE) {
            return Technique.BIDIRECTIONAL;
        }
        if (value.charAt(1) == ZERO_WIDTH_SPACE) {
            return Technique.ZERO_WIDTH;
        }
        if (value.chars().allMatch(UnicodeNormalizationAttackGeneratorTest::isFullwidthForm)) {
            return Technique.COMPATIBILITY;
        }
        if (containsAny(value, UnicodeNormalizationAttackGeneratorTest::isGreek)) {
            return Technique.MIXED_SCRIPT;
        }
        if (value.indexOf(ONE_DOT_LEADER) >= 0) {
            return Technique.OVERLONG_STYLE;
        }
        if (containsAny(value, character -> isCyrillic(character)
                || character == ARABIC_FULL_STOP || character == DIVISION_SLASH)) {
            return Technique.HOMOGRAPH;
        }
        if (DECOMPOSED_SIGNATURES.stream().anyMatch(value::contains)) {
            return Technique.DECOMPOSED;
        }
        if (COMBINING_SIGNATURES.stream().anyMatch(value::contains)) {
            return Technique.COMBINING;
        }
        if (containsAny(value, character -> COMPOSED_SUBSTITUTES.indexOf(character) >= 0)) {
            return Technique.COMPOSED;
        }
        return fail("Value carries the signature of no documented technique. Value: <" + preview(value) + ">");
    }

    private static boolean containsAny(String value, IntPredicate signature) {
        return value.chars().anyMatch(signature);
    }

    /** Halfwidth and Fullwidth Forms block. */
    private static boolean isFullwidthForm(int character) {
        return character >= 0xFF00 && character <= 0xFFEF;
    }

    /** Greek and Coptic block. */
    private static boolean isGreek(int character) {
        return character >= 0x0370 && character <= 0x03FF;
    }

    /** Cyrillic block. */
    private static boolean isCyrillic(int character) {
        return character >= 0x0400 && character <= 0x04FF;
    }
}

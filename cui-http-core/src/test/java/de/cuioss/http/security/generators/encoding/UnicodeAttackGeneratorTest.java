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

import java.util.HashSet;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertContainsAny;
import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link UnicodeAttackGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value carries one of the six
 * attack code points it documents <em>and</em> is an attack in its own right, so the URL path
 * pipeline rejects it without any hostile context a caller might add. The two traversal code
 * point sequences are complete payloads and are therefore reachable bare; the four invisible and
 * control code points are not, so the generator always embeds them in a traversal-shaped
 * carrier.</p>
 *
 * <p>The aggregate test asserts exactly that asymmetry: the bare form is reachable only for the
 * two traversal payloads, and every invisible code point is always accompanied by a traversal
 * marker.</p>
 *
 * <p>The attack code points are built from numeric code points rather than written as literals,
 * so that this source file stays plain ASCII and carries no invisible or direction-overriding
 * character of its own.</p>
 */
@EnableGeneratorController
@DisplayName("UnicodeAttackGenerator Contract Tests")
class UnicodeAttackGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    /** Unicode dots and slash, which the generator's escapes decode to. */
    private static final String DECODED_DOTS_AND_SLASH = fromCodePoints(0x002E, 0x002E, 0x002F);
    /** One-dot leaders and division slash, the homoglyph lookalike family. */
    private static final String LOOKALIKE_DOTS_AND_SLASH = fromCodePoints(0x2024, 0x2024, 0x2215);
    private static final String RIGHT_TO_LEFT_OVERRIDE = fromCodePoints(0x202E);
    private static final String ZERO_WIDTH_SPACE = fromCodePoints(0x200B);
    private static final String ZERO_WIDTH_NO_BREAK_SPACE = fromCodePoints(0xFEFF);
    private static final String NULL_CHARACTER = fromCodePoints(0x0000);

    /** The six attack code points the generator documents. */
    private static final Set<String> ATTACK_CODE_POINTS = Set.of(
            DECODED_DOTS_AND_SLASH,
            LOOKALIKE_DOTS_AND_SLASH,
            RIGHT_TO_LEFT_OVERRIDE,
            ZERO_WIDTH_SPACE,
            ZERO_WIDTH_NO_BREAK_SPACE,
            NULL_CHARACTER);

    /** The two code point sequences that are complete traversal payloads on their own. */
    private static final Set<String> BARE_ELIGIBLE_CODE_POINTS =
            Set.of(DECODED_DOTS_AND_SLASH, LOOKALIKE_DOTS_AND_SLASH);

    /** The four code points that carry no attack signal alone and must always be embedded. */
    private static final Set<String> INVISIBLE_CODE_POINTS = Set.of(
            RIGHT_TO_LEFT_OVERRIDE, ZERO_WIDTH_SPACE, ZERO_WIDTH_NO_BREAK_SPACE, NULL_CHARACTER);

    private static final Set<String> PATH_TARGETS =
            Set.of("etc/passwd", "etc/shadow", "windows/win.ini", "boot.ini");

    @ParameterizedTest
    @TypeGeneratorSource(value = UnicodeAttackGenerator.class, count = 100)
    @DisplayName("Every generated value carries an attack code point and is rejected by the pipeline")
    void shouldGenerateUnicodeAttack(String generatedValue) {
        assertContainsAny(generatedValue, ATTACK_CODE_POINTS, "Unicode attack value");

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Only the traversal payloads appear bare; invisible code points are always embedded")
    void shouldEmbedInvisibleCodePointsInTraversalCarrier() {
        UnicodeAttackGenerator generator = new UnicodeAttackGenerator();
        Set<String> bareForms = new HashSet<>();
        Set<String> compositeTargets = new HashSet<>();
        Set<String> reachedInvisible = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            if (ATTACK_CODE_POINTS.contains(value)) {
                bareForms.add(value);
            }
            PATH_TARGETS.stream().filter(value::endsWith).forEach(compositeTargets::add);
            for (String invisible : INVISIBLE_CODE_POINTS) {
                if (value.contains(invisible)) {
                    reachedInvisible.add(invisible);
                    assertTrue(carriesTraversalToSensitivePath(value),
                            () -> "An invisible code point must be embedded in a traversal carrier "
                                    + "ending in a sensitive path, but the value was <" + describe(value) + ">");
                }
            }
        }

        assertAll("Output forms",
                () -> assertEquals(BARE_ELIGIBLE_CODE_POINTS, bareForms,
                        "Only the two complete traversal payloads may be emitted bare"),
                () -> assertEquals(INVISIBLE_CODE_POINTS, reachedInvisible,
                        "Every invisible code point must be reachable inside a traversal carrier"),
                () -> assertFalse(compositeTargets.isEmpty(),
                        "The traversal-suffixed composite form must be reachable"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new UnicodeAttackGenerator().getType(),
                "Generator should return String.class");
    }

    /**
     * Determines whether the value is a traversal carrier: it carries a double dot and terminates
     * in one of the sensitive path targets the generator appends.
     */
    private static boolean carriesTraversalToSensitivePath(String value) {
        return value.contains("..") && PATH_TARGETS.stream().anyMatch(value::endsWith);
    }

    /**
     * Renders a value with its non-printable code points spelled out, so a failure message stays
     * readable when the offending characters are invisible.
     */
    private static String describe(String value) {
        StringBuilder builder = new StringBuilder(value.length() * 3);
        value.codePoints().forEach(codePoint -> {
            if (codePoint > 0x20 && codePoint < 0x7F) {
                builder.appendCodePoint(codePoint);
            } else {
                builder.append("U+%04X".formatted(codePoint));
            }
        });
        return builder.toString();
    }

    private static String fromCodePoints(int... codePoints) {
        StringBuilder builder = new StringBuilder(codePoints.length);
        for (int codePoint : codePoints) {
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }
}

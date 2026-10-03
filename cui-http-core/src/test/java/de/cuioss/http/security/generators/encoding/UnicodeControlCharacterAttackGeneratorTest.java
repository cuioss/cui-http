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

import java.util.EnumSet;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static de.cuioss.http.security.generators.GeneratorContractAssertions.preview;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contract test for {@link UnicodeControlCharacterAttackGenerator}.
 *
 * <p>The defining property of this generator is that every value is a base attack pattern with
 * control or format characters injected by one of twelve documented arms. An arm is recognisable
 * from the first character it injects - or, for the three arms that open with a fixed prefix,
 * from that prefix - and that same character decides the verdict of the URL path pipeline:</p>
 * <ul>
 *   <li>a raw or percent-encoded null byte is {@code NULL_BYTE_INJECTION} (the C0, URL-context
 *       and encoded-bypass arms),</li>
 *   <li>a raw line feed is {@code CONTROL_CHARACTERS} (the line-break arm),</li>
 *   <li>every other injected character lies outside the path character set without being a C0
 *       control and is {@code INVALID_CHARACTER}.</li>
 * </ul>
 *
 * <p>One base pattern masks the injection: a script tag opens with {@code <}, which is itself
 * outside the path character set and precedes the first injected character. Such a value is
 * {@code INVALID_CHARACTER} for every arm that injects after the first pattern character. The
 * arms that open with a prefix are unaffected, because their prefix precedes the pattern.</p>
 *
 * <p>The injected characters are named by their code points throughout, so that no control,
 * format or bidirectional character is embedded in this source file.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("UnicodeControlCharacterAttackGenerator Contract Tests")
class UnicodeControlCharacterAttackGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final String URL_CONTEXT_PREFIX = "http\0://";

    private static final int NULL = 0x0000;
    private static final int LINE_FEED = 0x000A;
    private static final int PADDING_CHARACTER = 0x0080;
    private static final int ZERO_WIDTH_SPACE = 0x200B;
    private static final int LINE_SEPARATOR = 0x2028;
    private static final int POP_DIRECTIONAL_FORMATTING = 0x202C;
    private static final int RIGHT_TO_LEFT_OVERRIDE = 0x202E;
    private static final int HIGH_SURROGATE = 0xD800;
    private static final int PRIVATE_USE_START = 0xE000;
    private static final int VARIATION_SELECTOR_1 = 0xFE00;

    private enum Arm {
        C0_CONTROL(UrlSecurityFailureType.NULL_BYTE_INJECTION, false),
        C1_CONTROL(UrlSecurityFailureType.INVALID_CHARACTER, false),
        FORMAT_CONTROL(UrlSecurityFailureType.INVALID_CHARACTER, false),
        BIDIRECTIONAL(UrlSecurityFailureType.INVALID_CHARACTER, true),
        ZERO_WIDTH(UrlSecurityFailureType.INVALID_CHARACTER, false),
        VARIATION_SELECTOR(UrlSecurityFailureType.INVALID_CHARACTER, false),
        PRIVATE_USE(UrlSecurityFailureType.INVALID_CHARACTER, false),
        SURROGATE(UrlSecurityFailureType.INVALID_CHARACTER, false),
        LINE_BREAK(UrlSecurityFailureType.CONTROL_CHARACTERS, false),
        URL_CONTEXT(UrlSecurityFailureType.NULL_BYTE_INJECTION, true),
        MIXED_SEQUENCE(UrlSecurityFailureType.INVALID_CHARACTER, true),
        ENCODED_BYPASS(UrlSecurityFailureType.NULL_BYTE_INJECTION, false);

        private final UrlSecurityFailureType verdict;
        private final boolean opensWithPrefix;

        Arm(UrlSecurityFailureType verdict, boolean opensWithPrefix) {
            this.verdict = verdict;
            this.opensWithPrefix = opensWithPrefix;
        }

        /** The verdict for a value of this arm, accounting for a masking script-tag pattern. */
        UrlSecurityFailureType verdictFor(String value) {
            if (!opensWithPrefix && value.charAt(0) == '<') {
                return UrlSecurityFailureType.INVALID_CHARACTER;
            }
            return verdict;
        }
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UnicodeControlCharacterAttackGenerator.class, count = 200)
    @DisplayName("Every value belongs to a documented arm and is rejected with that arm's exact verdict")
    void shouldGenerateControlCharacterInjection(String generatedValue) {
        Arm arm = armOf(generatedValue);

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue, arm.verdictFor(generatedValue));
    }

    @Test
    @DisplayName("Should reach all twelve documented attack arms")
    void shouldReachAllAttackArms() {
        UnicodeControlCharacterAttackGenerator generator = new UnicodeControlCharacterAttackGenerator();
        Set<Arm> arms = EnumSet.noneOf(Arm.class);

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            arms.add(armOf(generator.next()));
        }

        assertEquals(EnumSet.allOf(Arm.class), arms,
                "Every documented attack arm must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new UnicodeControlCharacterAttackGenerator().getType(),
                "Generator should return String.class");
    }

    /**
     * Attributes a value to its arm. The prefixed arms are read from their prefix; every other
     * arm injects directly after the first pattern character, so the second character names it.
     */
    private static Arm armOf(String value) {
        if (value.startsWith(URL_CONTEXT_PREFIX)) {
            return Arm.URL_CONTEXT;
        }
        if (value.charAt(0) == RIGHT_TO_LEFT_OVERRIDE) {
            return armBehindRightToLeftOverride(value);
        }
        int firstInjected = value.charAt(1);
        if (firstInjected == NULL) {
            return Arm.C0_CONTROL;
        }
        if (firstInjected == PADDING_CHARACTER) {
            return Arm.C1_CONTROL;
        }
        if (firstInjected == LINE_SEPARATOR) {
            return Arm.FORMAT_CONTROL;
        }
        if (firstInjected == ZERO_WIDTH_SPACE) {
            return Arm.ZERO_WIDTH;
        }
        if (firstInjected == VARIATION_SELECTOR_1) {
            return Arm.VARIATION_SELECTOR;
        }
        if (firstInjected == PRIVATE_USE_START) {
            return Arm.PRIVATE_USE;
        }
        if (firstInjected == HIGH_SURROGATE) {
            return Arm.SURROGATE;
        }
        if (firstInjected == LINE_FEED) {
            return Arm.LINE_BREAK;
        }
        if (firstInjected == '%') {
            return Arm.ENCODED_BYPASS;
        }
        return fail("Value belongs to no documented arm. Value: <" + preview(value) + ">");
    }

    /**
     * The bidirectional and the mixed-sequence arm both open with a right-to-left override; the
     * character injected behind the first pattern character tells them apart.
     */
    private static Arm armBehindRightToLeftOverride(String value) {
        int firstInjected = value.charAt(2);
        if (firstInjected == POP_DIRECTIONAL_FORMATTING) {
            return Arm.BIDIRECTIONAL;
        }
        if (firstInjected == NULL) {
            return Arm.MIXED_SEQUENCE;
        }
        return fail("Unknown arm behind a right-to-left override. Value: <" + preview(value) + ">");
    }
}

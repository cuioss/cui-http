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
package de.cuioss.http.security.generators.injection;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.generators.url.URLLengthLimitAttackGenerator;
import de.cuioss.http.security.generators.url.URLLengthLimitAttackGenerator.Surface;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline;
import de.cuioss.http.security.pipeline.URLParameterNameValidationPipeline;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.http.security.validation.CharacterValidationConstants;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntPredicate;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static de.cuioss.http.security.generators.GeneratorContractAssertions.preview;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link URLLengthLimitAttackGenerator}.
 *
 * <p>The defining property of this generator is length on one surface: every emitted value
 * exceeds the strict limit of the surface its generator was created for, and consists of
 * characters that surface admits, so that length is the only reason to reject it. Both halves
 * are asserted per value, together with the round-trip through the pipeline that owns the
 * surface's limit.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("URLLengthLimitAttackGenerator Contract Tests")
class URLLengthLimitAttackGeneratorTest {

    /**
     * Sized so that each of the {@link URLLengthLimitAttackGenerator#ARM_COUNT} arms of a surface
     * is missed with negligible probability.
     */
    private static final int AGGREGATE_DRAWS = 400;

    /** The limit tiers a surface's values can exceed. */
    private enum Tier {
        BEYOND_STRICT_ONLY, BEYOND_DEFAULT, BEYOND_LENIENT
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.class, count = 100)
    @DisplayName("Every path value is an overlong, rooted, path-legal value the strict path pipeline rejects")
    void shouldGenerateOverlongPath(String generatedValue) {
        assertTrue(generatedValue.startsWith("/"),
                () -> "A path length attack must be rooted at '/'. Value starts: <" + preview(generatedValue) + ">");
        assertOverlongLegalValue(Surface.URL_PATH, generatedValue);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.ForParameterName.class, count = 100)
    @DisplayName("Every parameter-name value is overlong, name-legal and rejected by the strict parameter-name pipeline")
    void shouldGenerateOverlongParameterName(String generatedValue) {
        assertOverlongLegalValue(Surface.PARAMETER_NAME, generatedValue);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.ForParameterValue.class, count = 100)
    @DisplayName("Every parameter-value value is overlong, value-legal and rejected by the strict parameter pipeline")
    void shouldGenerateOverlongParameterValue(String generatedValue) {
        assertOverlongLegalValue(Surface.PARAMETER_VALUE, generatedValue);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.ForHeaderName.class, count = 100)
    @DisplayName("Every header-name value is overlong, token-legal and rejected by the strict header-name pipeline")
    void shouldGenerateOverlongHeaderName(String generatedValue) {
        assertOverlongLegalValue(Surface.HEADER_NAME, generatedValue);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.ForHeaderValue.class, count = 100)
    @DisplayName("Every header-value value is overlong, value-legal and rejected by the strict header-value pipeline")
    void shouldGenerateOverlongHeaderValue(String generatedValue) {
        assertOverlongLegalValue(Surface.HEADER_VALUE, generatedValue);
    }

    @ParameterizedTest
    @EnumSource(Surface.class)
    @DisplayName("Every surface reaches a value beyond its strict, its default and its lenient limit")
    void shouldReachEveryLimitTier(Surface surface) {
        URLLengthLimitAttackGenerator generator = new URLLengthLimitAttackGenerator(surface);
        Set<Tier> reached = EnumSet.noneOf(Tier.class);

        for (int draw = 0; draw < AGGREGATE_DRAWS; draw++) {
            reached.add(tierOf(surface, generator.next()));
        }

        assertEquals(EnumSet.allOf(Tier.class), reached,
                "Every limit tier must be reachable within " + AGGREGATE_DRAWS + " draws for " + surface);
    }

    @ParameterizedTest
    @EnumSource(Surface.class)
    @DisplayName("Each surface generator reports the surface it was created for")
    void shouldReportSurface(Surface surface) {
        assertEquals(surface, new URLLengthLimitAttackGenerator(surface).getSurface());
    }

    @Test
    @DisplayName("The no-argument generators are bound to their documented surfaces")
    void shouldBindNoArgumentGeneratorsToSurfaces() {
        assertAll(
                () -> assertEquals(Surface.URL_PATH, new URLLengthLimitAttackGenerator().getSurface()),
                () -> assertEquals(Surface.PARAMETER_NAME,
                        new URLLengthLimitAttackGenerator.ForParameterName().getSurface()),
                () -> assertEquals(Surface.PARAMETER_VALUE,
                        new URLLengthLimitAttackGenerator.ForParameterValue().getSurface()),
                () -> assertEquals(Surface.HEADER_NAME, new URLLengthLimitAttackGenerator.ForHeaderName().getSurface()),
                () -> assertEquals(Surface.HEADER_VALUE,
                        new URLLengthLimitAttackGenerator.ForHeaderValue().getSurface()));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new URLLengthLimitAttackGenerator().getType(),
                "Generator should return String.class");
    }

    private static void assertOverlongLegalValue(Surface surface, String value) {
        assertNotNull(value, "Generator must not produce null values");
        assertTrue(value.length() > surface.strictLimit(),
                () -> "A length-limit attack on " + surface + " must exceed the strict limit of "
                        + surface.strictLimit() + " characters, but was " + value.length()
                        + ". Value starts: <" + preview(value) + ">");

        IntPredicate legalCharacters = CharacterValidationConstants.getCharacterSet(surface.validationType());
        assertTrue(value.chars().allMatch(legalCharacters),
                () -> "A length-limit attack on " + surface + " must consist of characters the surface admits, "
                        + "so that length is the only reason to reject it. Value starts: <" + preview(value) + ">");

        assertPipelineRejects(strictPipelineFor(surface), value);
    }

    private static Tier tierOf(Surface surface, String value) {
        if (value.length() > surface.lenientLimit()) {
            return Tier.BEYOND_LENIENT;
        }
        if (value.length() > surface.defaultLimit()) {
            return Tier.BEYOND_DEFAULT;
        }
        return Tier.BEYOND_STRICT_ONLY;
    }

    private static HttpSecurityValidator strictPipelineFor(Surface surface) {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxPathLength(Surface.URL_PATH.strictLimit())
                .maxParameterNameLength(Surface.PARAMETER_NAME.strictLimit())
                .maxParameterValueLength(Surface.PARAMETER_VALUE.strictLimit())
                .maxHeaderNameLength(Surface.HEADER_NAME.strictLimit())
                .maxHeaderValueLength(Surface.HEADER_VALUE.strictLimit())
                .build();
        SecurityEventCounter eventCounter = new SecurityEventCounter();
        return switch (surface) {
            case URL_PATH -> new URLPathValidationPipeline(config, eventCounter);
            case PARAMETER_NAME -> new URLParameterNameValidationPipeline(config, eventCounter);
            case PARAMETER_VALUE -> new URLParameterValidationPipeline(config, eventCounter);
            case HEADER_NAME -> new HTTPHeaderValidationPipeline(config, eventCounter, ValidationType.HEADER_NAME);
            case HEADER_VALUE -> new HTTPHeaderValidationPipeline(config, eventCounter, ValidationType.HEADER_VALUE);
        };
    }
}

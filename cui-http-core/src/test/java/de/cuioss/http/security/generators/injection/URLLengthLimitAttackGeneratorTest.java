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
import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.generators.url.URLLengthLimitAttackGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static de.cuioss.http.security.generators.GeneratorContractAssertions.preview;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link URLLengthLimitAttackGenerator}.
 *
 * <p>The defining property of this generator is length: every emitted value exceeds
 * {@link SecurityDefaults#MAX_PATH_LENGTH_STRICT}, which is the limit the generator advertises
 * itself as attacking. A value that stays under that limit is not a length-limit attack, so the
 * per-value test asserts the length directly rather than a token minimum.</p>
 *
 * <p>The pipeline round-trip is asserted only for the values {@link URLPathValidationPipeline}
 * actually governs — relative path and query values. Hostname-family values (which carry their
 * length in the authority component) and fragment-family values lie outside that pipeline's
 * remit, so they carry the length property alone.</p>
 */
@EnableGeneratorController
@DisplayName("URLLengthLimitAttackGenerator Contract Tests")
class URLLengthLimitAttackGeneratorTest {

    /**
     * Sized so that the rarest branch — the single traversal arm of the algorithmic-complexity
     * family, reached on one of eight sub-cases of one family — is missed with negligible
     * probability.
     */
    private static final int AGGREGATE_DRAWS = 2000;

    /**
     * The number of attack families {@code AttackTypeSelector} cycles through, sourced from the
     * generator itself so the count cannot desync when a family is added or removed.
     */
    private static final int ATTACK_FAMILY_COUNT = URLLengthLimitAttackGenerator.ATTACK_FAMILY_COUNT;

    /**
     * Observable shapes that are each reachable only through a distinct branch family, so that
     * reaching all of them is evidence the generator spans its documented taxonomy rather than
     * collapsing onto one shape.
     */
    private static final String HOSTNAME_SHAPE = "authority-rooted (https://)";
    private static final String FRAGMENT_SHAPE = "carries a fragment (#)";
    private static final String QUERY_SHAPE = "carries a query (?)";
    private static final String PURE_PATH_SHAPE = "pure path (no query, no fragment)";
    private static final String TRAVERSAL_SHAPE = "carries traversal segments (../)";
    private static final String BEYOND_LENIENT_SHAPE = "exceeds the lenient limit";

    private static final Set<String> ALL_SHAPES = Set.of(
            HOSTNAME_SHAPE, FRAGMENT_SHAPE, QUERY_SHAPE,
            PURE_PATH_SHAPE, TRAVERSAL_SHAPE, BEYOND_LENIENT_SHAPE);

    @ParameterizedTest
    @TypeGeneratorSource(value = URLLengthLimitAttackGenerator.class, count = 100)
    @DisplayName("Every generated value exceeds the strict path limit and is a rooted URL")
    void shouldGenerateOverlongRootedUrl(String generatedValue) {
        assertOverlongRootedUrl(generatedValue);

        if (isGovernedByPathPipeline(generatedValue)) {
            assertPipelineRejects(strictPathPipeline(), generatedValue);
        }
    }

    @Test
    @DisplayName("Should cycle through every attack family, each emitting overlong URLs")
    void shouldReachAllAttackFamilies() {
        URLLengthLimitAttackGenerator generator = new URLLengthLimitAttackGenerator();
        List<List<String>> valuesByFamily = new ArrayList<>();
        for (int family = 0; family < ATTACK_FAMILY_COUNT; family++) {
            valuesByFamily.add(new ArrayList<>());
        }

        for (int draw = 0; draw < AGGREGATE_DRAWS; draw++) {
            valuesByFamily.get(draw % ATTACK_FAMILY_COUNT).add(generator.next());
        }

        for (int family = 0; family < ATTACK_FAMILY_COUNT; family++) {
            List<String> values = valuesByFamily.get(family);
            assertFalse(values.isEmpty(), "Attack family " + family + " must be reached");
            values.forEach(this::assertOverlongRootedUrl);
        }
    }

    @Test
    @DisplayName("Should span every documented URL shape, including one beyond the lenient limit")
    void shouldSpanAllDocumentedShapes() {
        URLLengthLimitAttackGenerator generator = new URLLengthLimitAttackGenerator();
        Set<String> reached = new HashSet<>();

        for (int draw = 0; draw < AGGREGATE_DRAWS; draw++) {
            reached.addAll(shapesOf(generator.next()));
        }

        assertEquals(ALL_SHAPES, reached,
                "Every documented URL shape must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new URLLengthLimitAttackGenerator().getType(),
                "Generator should return String.class");
    }

    private void assertOverlongRootedUrl(String value) {
        assertNotNull(value, "Generator must not produce null values");
        assertTrue(value.length() > SecurityDefaults.MAX_PATH_LENGTH_STRICT,
                () -> "A length-limit attack must exceed the strict limit of "
                        + SecurityDefaults.MAX_PATH_LENGTH_STRICT + " characters, but was "
                        + value.length() + ". Value starts: <" + preview(value) + ">");
        assertTrue(isRooted(value),
                () -> "A URL length attack must be rooted at '/' or carry an http(s) scheme. Value starts: <"
                        + preview(value) + ">");
    }

    private Set<String> shapesOf(String value) {
        Set<String> shapes = new HashSet<>();
        if (value.startsWith("https://") || value.startsWith("http://")) { // NOSONAR - test URL patterns
            shapes.add(HOSTNAME_SHAPE);
        }
        if (value.indexOf('#') >= 0) {
            shapes.add(FRAGMENT_SHAPE);
        }
        if (value.indexOf('?') >= 0) {
            shapes.add(QUERY_SHAPE);
        }
        if (value.indexOf('?') < 0 && value.indexOf('#') < 0) {
            shapes.add(PURE_PATH_SHAPE);
        }
        if (value.contains("../")) {
            shapes.add(TRAVERSAL_SHAPE);
        }
        if (value.length() > SecurityDefaults.MAX_PATH_LENGTH_LENIENT) {
            shapes.add(BEYOND_LENIENT_SHAPE);
        }
        return shapes;
    }

    /**
     * Determines whether the URL path pipeline governs the value. Authority-rooted values carry
     * their length in the hostname and fragment-bearing values carry it after the {@code #}, so
     * neither is this pipeline's concern; everything else is a path or query the pipeline owns.
     */
    private boolean isGovernedByPathPipeline(String value) {
        return isRelative(value) && value.indexOf('#') < 0;
    }

    private boolean isRooted(String value) {
        return isRelative(value) || value.startsWith("http://") || value.startsWith("https://"); // NOSONAR - test URL patterns
    }

    private boolean isRelative(String value) {
        return value.startsWith("/");
    }

    private URLPathValidationPipeline strictPathPipeline() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .maxPathLength(SecurityDefaults.MAX_PATH_LENGTH_STRICT)
                .build();
        return new URLPathValidationPipeline(config, new SecurityEventCounter());
    }
}

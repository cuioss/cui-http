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
package de.cuioss.http.security.generators.header;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.generators.header.HttpHeaderInjectionAttackGenerator.Surface;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline;
import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
import de.cuioss.http.security.validation.CharacterValidationConstants;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link HttpHeaderInjectionAttackGenerator}.
 *
 * <p>The defining property of this generator is that every value is a benign leading token
 * followed by a line break and injected header lines, shaped for exactly one HTTP component. In
 * the header-value spelling the line break is a raw CR or LF, which the header-value pipeline
 * rejects as {@code INVALID_CHARACTER}. In the parameter-value spelling the line break is
 * percent-encoded and the value consists of query characters and escapes only, so it passes the
 * wire-form character validation and is rejected as {@code CONTROL_CHARACTERS} once it is decoded
 * - by a parameter pipeline that closes the line-break carve-out for parameter values.</p>
 *
 * <p>The payloads are fifteen fixed families,
 * {@link HttpHeaderInjectionAttackGenerator#ATTACK_FAMILIES}. The round-trip tests iterate every
 * listed payload, so no family member is left to chance; the reachability test asserts that
 * {@link HttpHeaderInjectionAttackGenerator#next()} draws from all fifteen on both surfaces.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("HttpHeaderInjectionAttackGenerator Contract Tests")
class HttpHeaderInjectionAttackGeneratorTest {

    private static final int AGGREGATE_DRAWS = 600;
    private static final Set<String> RAW_LINE_BREAKS = Set.of("\r", "\n");
    private static final Set<String> ENCODED_LINE_BREAKS = Set.of("%0d", "%0a");

    static Stream<String> headerValuePayloads() {
        return HttpHeaderInjectionAttackGenerator.ATTACK_FAMILIES.stream().flatMap(List::stream);
    }

    static Stream<String> parameterValuePayloads() {
        return headerValuePayloads().map(HttpHeaderInjectionAttackGenerator::asParameterValue);
    }

    @ParameterizedTest
    @MethodSource("headerValuePayloads")
    @DisplayName("Every header-value payload carries a raw line break and is rejected as INVALID_CHARACTER")
    void shouldListRawHeaderInjection(String headerValue) {
        assertContainsAny(headerValue, RAW_LINE_BREAKS, "Header-value injection payload");

        assertPipelineRejects(
                new HTTPHeaderValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter(),
                        ValidationType.HEADER_VALUE),
                headerValue, UrlSecurityFailureType.INVALID_CHARACTER);
    }

    @ParameterizedTest
    @MethodSource("parameterValuePayloads")
    @DisplayName("Every parameter-value payload carries only an encoded line break and is rejected as CONTROL_CHARACTERS")
    void shouldSpellEncodedHeaderInjection(String parameterValue) {
        assertContainsNone(parameterValue, RAW_LINE_BREAKS, "Parameter-value injection payload");
        assertContainsAny(parameterValue, ENCODED_LINE_BREAKS, "Parameter-value injection payload");
        assertTrue(parameterValue.chars().allMatch(
                        character -> character == '%' || CharacterValidationConstants.RFC3986_QUERY_CHARS.test(character)),
                () -> "Parameter-value payloads consist of query characters and percent-escapes only. Value: <"
                        + parameterValue + ">");

        assertPipelineRejects(
                new URLParameterValidationPipeline(
                        SecurityConfiguration.builder().allowLineBreaksInParameterValues(false).build(),
                        new SecurityEventCounter()),
                parameterValue, UrlSecurityFailureType.CONTROL_CHARACTERS);
    }

    @Test
    @DisplayName("Should list fifteen families whose payloads belong to one family each")
    void shouldListDisjointFamilies() {
        List<String> payloads = headerValuePayloads().toList();

        assertAll("Attack families",
                () -> assertEquals(15, HttpHeaderInjectionAttackGenerator.ATTACK_FAMILIES.size(),
                        "The class Javadoc documents fifteen attack families"),
                () -> assertEquals(HttpHeaderInjectionAttackGenerator.ATTACK_FAMILY_COUNT,
                        HttpHeaderInjectionAttackGenerator.ATTACK_FAMILIES.size(),
                        "The family count must describe the listed families"),
                () -> assertEquals(payloads.size(), new HashSet<>(payloads).size(),
                        "A payload listed by two families could not be attributed to either"));
    }

    @Test
    @DisplayName("Should reach all fifteen families on the header-value surface")
    void shouldReachAllFamiliesOnHeaderValueSurface() {
        HttpHeaderInjectionAttackGenerator generator = new HttpHeaderInjectionAttackGenerator();

        assertAll("Header-value surface",
                () -> assertEquals(Surface.HEADER_VALUE, generator.getSurface(),
                        "The no-argument constructor targets the header-value surface"),
                () -> assertEquals(allFamilies(), familiesDrawn(generator, UnaryOperator.identity()),
                        "Every family must be reachable within " + AGGREGATE_DRAWS + " draws"));
    }

    @Test
    @DisplayName("Should reach all fifteen families on the parameter-value surface")
    void shouldReachAllFamiliesOnParameterValueSurface() {
        HttpHeaderInjectionAttackGenerator generator = new HttpHeaderInjectionAttackGenerator.ForParameterValue();

        assertAll("Parameter-value surface",
                () -> assertEquals(Surface.PARAMETER_VALUE, generator.getSurface(),
                        "ForParameterValue targets the parameter-value surface"),
                () -> assertEquals(allFamilies(),
                        familiesDrawn(generator, HttpHeaderInjectionAttackGenerator::asParameterValue),
                        "Every family must be reachable within " + AGGREGATE_DRAWS + " draws"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new HttpHeaderInjectionAttackGenerator().getType(),
                "Generator should return String.class");
    }

    private static Set<Integer> allFamilies() {
        return IntStream.range(0, HttpHeaderInjectionAttackGenerator.ATTACK_FAMILIES.size()).boxed()
                .collect(Collectors.toSet());
    }

    /**
     * Draws from the generator and attributes every value to the family that lists it, comparing
     * in the spelling of the generator's surface.
     */
    private static Set<Integer> familiesDrawn(HttpHeaderInjectionAttackGenerator generator,
            UnaryOperator<String> spelling) {
        Set<Integer> families = new HashSet<>();
        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            families.add(familyOf(generator.next(), spelling));
        }
        return families;
    }

    private static int familyOf(String generatedValue, UnaryOperator<String> spelling) {
        List<List<String>> families = HttpHeaderInjectionAttackGenerator.ATTACK_FAMILIES;
        for (int family = 0; family < families.size(); family++) {
            if (families.get(family).stream().map(spelling).anyMatch(generatedValue::equals)) {
                return family;
            }
        }
        return fail("Value is listed by no attack family. Value: <" + generatedValue + ">");
    }
}

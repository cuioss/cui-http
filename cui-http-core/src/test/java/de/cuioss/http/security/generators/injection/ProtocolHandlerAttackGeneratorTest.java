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
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.GeneratorSeed;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.assertPipelineRejects;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ProtocolHandlerAttackGenerator}.
 *
 * <p>The defining property of this generator is that every value starts with one of the four
 * enforced protocol handler schemes - {@code javascript:}, {@code vbscript:}, {@code data:} or
 * {@code file:} - spelled raw, in another letter case or percent-encoded. The scheme is the only
 * rejectable property of a value, so the URL path pipeline under the {@code strict()} preset
 * rejects every value as {@code SUSPICIOUS_PATTERN_DETECTED}. The aggregate test asserts that all
 * eight documented attack categories are reachable.</p>
 *
 * <p>Category attribution is first-match-wins over the ordered classifier below: an encoded or
 * case-manipulated scheme, a nested scheme and a {@code file:} authority are each recognised
 * before the value is attributed to the plain category of its scheme.</p>
 */
@EnableGeneratorController
@GeneratorSeed(4711L)
@DisplayName("ProtocolHandlerAttackGenerator Contract Tests")
class ProtocolHandlerAttackGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final List<String> ENFORCED_SCHEMES = List.of("javascript:", "vbscript:", "data:", "file:");

    /** The longest percent-encoded scheme prefix that still ends before the scheme does. */
    private static final int SCHEME_REGION_LENGTH = 11;

    private enum Category {
        JAVASCRIPT, VBSCRIPT, DATA_URI, FILE, CASE_MANIPULATION, SCHEME_ENCODING, NESTED, FILE_WITH_AUTHORITY
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = ProtocolHandlerAttackGenerator.class, count = 200)
    @DisplayName("Every value starts with an enforced scheme and is rejected as SUSPICIOUS_PATTERN_DETECTED")
    void shouldGenerateProtocolHandlerAttack(String generatedValue) {
        assertTrue(ENFORCED_SCHEMES.stream().anyMatch(canonical(generatedValue)::startsWith),
                () -> "A protocol handler attack starts with an enforced scheme. Value: <" + generatedValue + ">");

        assertPipelineRejects(
                new URLPathValidationPipeline(SecurityConfiguration.strict(), new SecurityEventCounter()),
                generatedValue, UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED);
    }

    @Test
    @DisplayName("Should reach all eight documented attack categories")
    void shouldReachAllAttackCategories() {
        ProtocolHandlerAttackGenerator generator = new ProtocolHandlerAttackGenerator();
        Set<Category> categories = EnumSet.noneOf(Category.class);

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            categories.add(categoryOf(generator.next()));
        }

        assertAll("Attack categories",
                () -> assertEquals(ProtocolHandlerAttackGenerator.ATTACK_CATEGORY_COUNT, Category.values().length,
                        "The category count must describe the documented categories"),
                () -> assertEquals(EnumSet.allOf(Category.class), categories,
                        "Every documented category must be reachable within " + AGGREGATE_DRAWS + " draws"));
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new ProtocolHandlerAttackGenerator().getType(),
                "Generator should return String.class");
    }

    /** The value as the pattern stage compares it: percent-decoded and lower-cased. */
    private static String canonical(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
    }

    private static Category categoryOf(String value) {
        int firstEscape = value.indexOf('%');
        if (firstEscape >= 0 && firstEscape < SCHEME_REGION_LENGTH) {
            return Category.SCHEME_ENCODING;
        }
        String scheme = value.substring(0, value.indexOf(':') + 1);
        if (!scheme.equals(scheme.toLowerCase(Locale.ROOT))) {
            return Category.CASE_MANIPULATION;
        }
        String afterScheme = value.substring(scheme.length());
        if (ENFORCED_SCHEMES.stream().anyMatch(afterScheme::contains)) {
            return Category.NESTED;
        }
        if (value.startsWith("file://") && value.charAt("file://".length()) != '/') {
            return Category.FILE_WITH_AUTHORITY;
        }
        return switch (scheme) {
            case "javascript:" -> Category.JAVASCRIPT;
            case "vbscript:" -> Category.VBSCRIPT;
            case "data:" -> Category.DATA_URI;
            case "file:" -> Category.FILE;
            default -> fail("Value belongs to no documented category. Value: <" + value + ">");
        };
    }
}

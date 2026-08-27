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
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ValidURLPathGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value is a legitimate API
 * path: it is rooted at {@code /}, carries no attack marker, and is accepted by the URL path
 * validation pipeline. The aggregate test asserts that all seven documented path families are
 * reachable.</p>
 */
@EnableGeneratorController
@DisplayName("ValidURLPathGenerator Contract Tests")
class ValidURLPathGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    private static final Set<String> SYSTEM_PATHS = Set.of("/health", "/metrics", "/status", "/info");
    private static final String RESOURCES = "(users|orders|products|customers|documents)";
    // The three patterns below are compiled once from compile-time-constant string literals
    // (RESOURCES included), never from user input, and each alternation is a small flat set with no
    // nested or overlapping quantifiers — so the ReDoS warning static analysis raises here is a
    // false positive: there is no catastrophic-backtracking path and no attacker-controlled input.
    private static final Pattern PLAIN_API =
            Pattern.compile("^/api/" + RESOURCES + "(/\\d+/(search|profile|login|logout))?$");
    private static final Pattern VERSIONED_API =
            Pattern.compile("^/api/v[123]/" + RESOURCES + "(/\\d+)?$");
    // The child-resource alternation is disjoint from PLAIN_API's action alternation, so a
    // plain-API path can never satisfy the nested-resource family by coincidence.
    private static final Pattern NESTED_RESOURCE =
            Pattern.compile("^/api/" + RESOURCES + "/\\d+/(items|orders|notifications)$");
    private static final Pattern AUTH_PATH =
            Pattern.compile("^/api/auth/(search|profile|login|logout)$");
    private static final Pattern ADMIN_PATH =
            Pattern.compile("^/api/admin/(dashboard|settings|config)$");
    private static final Pattern REPORTING_PATH =
            Pattern.compile("^/api/(reports|stats|backup)/(daily|summary|status)$");

    @ParameterizedTest
    @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 100)
    @DisplayName("Every generated path is a legitimate API path the pipeline accepts")
    void shouldGenerateLegitimateApiPath(String generatedValue) {
        assertTrue(generatedValue.startsWith("/"),
                () -> "Valid URL paths are rooted at '/'. Value: <" + generatedValue + ">");
        assertCarriesNoMarker(generatedValue, TRAVERSAL_MARKERS, "path traversal");
        assertCarriesNoMarker(generatedValue, NULL_BYTE_MARKERS, "null byte");
        assertFalse(generatedValue.contains("<script"),
                () -> "Valid URL paths carry no script tag. Value: <" + generatedValue + ">");
        assertFalse(generatedValue.contains("javascript:"),
                () -> "Valid URL paths carry no javascript protocol. Value: <" + generatedValue + ">");

        assertPipelineAccepts(
                new URLPathValidationPipeline(SecurityConfiguration.defaults(), new SecurityEventCounter()),
                generatedValue);
    }

    @Test
    @DisplayName("Should reach all seven documented path families")
    void shouldReachAllPathFamilies() {
        ValidURLPathGenerator generator = new ValidURLPathGenerator();
        Set<String> families = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            if (SYSTEM_PATHS.contains(value)) {
                families.add("system");
            }
            if (VERSIONED_API.matcher(value).matches()) {
                families.add("versioned");
            }
            if (NESTED_RESOURCE.matcher(value).matches()) {
                families.add("nested");
            }
            if (AUTH_PATH.matcher(value).matches()) {
                families.add("auth");
            }
            if (ADMIN_PATH.matcher(value).matches()) {
                families.add("admin");
            }
            if (REPORTING_PATH.matcher(value).matches()) {
                families.add("reporting");
            }
            if (PLAIN_API.matcher(value).matches()) {
                families.add("api");
            }
        }

        assertEquals(Set.of("api", "versioned", "nested", "system", "auth", "admin", "reporting"), families,
                "Every documented path family must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new ValidURLPathGenerator().getType(),
                "Generator should return String.class");
    }

    private static void assertCarriesNoMarker(String value, Set<String> markers, String description) {
        markers.forEach(marker -> assertFalse(value.contains(marker),
                () -> "Valid URL paths carry no " + description + " marker <" + marker
                        + ">. Value: <" + value + ">"));
    }
}

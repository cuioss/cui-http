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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static de.cuioss.http.security.generators.GeneratorContractAssertions.containsControlCharacter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ValidHTTPHeaderNameGenerator}.
 *
 * <p>The defining property of this generator is that every emitted name is a well-formed
 * RFC 7230 field-name token and carries no control character. The aggregate test asserts that
 * all seven documented header families are reachable.</p>
 */
@EnableGeneratorController
@DisplayName("ValidHTTPHeaderNameGenerator Contract Tests")
class ValidHTTPHeaderNameGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;

    /** The RFC 7230 {@code token} production, which a field-name must match. */
    private static final Pattern RFC7230_TOKEN = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    private static final Set<String> STANDARD_HEADERS =
            Set.of("Authorization", "Content-Type", "User-Agent", "Host");
    private static final Set<String> ACCEPT_HEADERS =
            Set.of("Accept", "Accept-Language", "Accept-Encoding");
    private static final Set<String> CONTENT_HEADERS =
            Set.of("Content-Length", "Content-Encoding", "Cache-Control");
    private static final Set<String> COOKIE_HEADERS = Set.of("Cookie", "Set-Cookie");
    private static final Set<String> NAVIGATION_HEADERS = Set.of("Origin", "Referer", "Location");
    private static final Set<String> CONNECTION_HEADERS = Set.of("Connection", "Keep-Alive", "Upgrade");

    @ParameterizedTest
    @TypeGeneratorSource(value = ValidHTTPHeaderNameGenerator.class, count = 100)
    @DisplayName("Every generated name is an RFC 7230 token free of control characters")
    void shouldGenerateWellFormedHeaderName(String generatedValue) {
        assertTrue(RFC7230_TOKEN.matcher(generatedValue).matches(),
                () -> "Header name must match the RFC 7230 token production. Value: <"
                        + generatedValue + ">");
        assertFalse(containsControlCharacter(generatedValue),
                () -> "Header name must carry no control character. Value: <" + generatedValue + ">");
    }

    @Test
    @DisplayName("Should reach all seven documented header families")
    void shouldReachAllHeaderFamilies() {
        ValidHTTPHeaderNameGenerator generator = new ValidHTTPHeaderNameGenerator();
        Set<String> families = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            if (STANDARD_HEADERS.contains(value)) {
                families.add("standard");
            }
            if (ACCEPT_HEADERS.contains(value)) {
                families.add("accept");
            }
            if (CONTENT_HEADERS.contains(value)) {
                families.add("content");
            }
            if (COOKIE_HEADERS.contains(value)) {
                families.add("cookie");
            }
            if (NAVIGATION_HEADERS.contains(value)) {
                families.add("navigation");
            }
            if (CONNECTION_HEADERS.contains(value)) {
                families.add("connection");
            }
            if (value.startsWith("X-")) {
                families.add("custom");
            }
        }

        assertEquals(Set.of("standard", "accept", "content", "cookie", "navigation", "connection", "custom"),
                families,
                "Every documented header family must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new ValidHTTPHeaderNameGenerator().getType(),
                "Generator should return String.class");
    }
}

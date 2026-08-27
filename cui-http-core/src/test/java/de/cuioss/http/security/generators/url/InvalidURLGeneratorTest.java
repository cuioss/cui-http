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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for {@link InvalidURLGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value is either blank or
 * carries one of the ten documented malformation families. There is deliberately no per-value
 * pipeline round-trip: the generator emits values such as the well-formed
 * {@code http://example.com/path/} whose only declared defect is a trailing slash, so a uniform
 * "the pipeline rejects it" assertion would not hold.</p>
 *
 * <p>{@code createPathIssue} case 3 emits a merely trailing-slash URL, which is well-formed
 * rather than malformed. "Trailing slash" is enumerated below as a declared malformation family
 * so that this test states honestly what the generator emits today.</p>
 */
@EnableGeneratorController
@DisplayName("InvalidURLGenerator Contract Tests")
class InvalidURLGeneratorTest {

    /**
     * The literal-coverage assertion below needs the two rarest literals, each drawn with
     * probability 1/60. 2000 draws puts the chance of a spurious miss below one in a trillion,
     * where 400 draws would leave this test flaking roughly once in four hundred runs.
     */
    private static final int AGGREGATE_DRAWS = 2000;

    private static final String EMPTY_URL = "";
    private static final String WHITESPACE_URL = "   ";
    private static final String NOT_A_URL = "not-a-url-at-all";

    private static final Pattern SINGLE_SLASH_SCHEME = Pattern.compile("^[a-z]+:/[^/]");
    private static final Pattern TRAILING_DOT_HOST = Pattern.compile("://[^/]+\\./");
    private static final Pattern PORT_ISSUE = Pattern.compile("://[^/]*:(/|abc|-80|\\d{5,})");
    private static final List<String> INVALID_ESCAPES = List.of(
            "%encoding", "%2encoding", "%ZZencoding", "%GGencoding", "%invalidencoding", "%invalid%encoding");
    private static final List<String> UNENCODED_SPECIALS = List.of(" ", "[", "]", "{", "}", "|", "\\");
    private static final int MAX_REASONABLE_URL_LENGTH = 2048;

    @ParameterizedTest
    @TypeGeneratorSource(value = InvalidURLGenerator.class, count = 200)
    @DisplayName("Every generated URL is blank or carries a documented malformation")
    void shouldGenerateBlankOrMalformedUrl(String generatedValue) {
        assertTrue(generatedValue.isBlank() || carriesDocumentedMalformation(generatedValue),
                () -> "Value belongs to no documented malformation family. Value: <" + generatedValue + ">");
    }

    @Test
    @DisplayName("Should reach the empty, whitespace-only and non-URL literals")
    void shouldReachDegenerateLiterals() {
        InvalidURLGenerator generator = new InvalidURLGenerator();
        Set<String> literals = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            if (EMPTY_URL.equals(value) || WHITESPACE_URL.equals(value) || NOT_A_URL.equals(value)) {
                literals.add(value);
            }
        }

        assertEquals(Set.of(EMPTY_URL, WHITESPACE_URL, NOT_A_URL), literals,
                "Every degenerate literal must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new InvalidURLGenerator().getType(),
                "Generator should return String.class");
    }

    private static boolean carriesDocumentedMalformation(String value) {
        return hasSchemeIssue(value)
                || hasHostIssue(value)
                || hasPathSlashIssue(value)
                || value.contains("?")
                || value.contains("#")
                || PORT_ISSUE.matcher(value).find()
                || UNENCODED_SPECIALS.stream().anyMatch(value::contains)
                || INVALID_ESCAPES.stream().anyMatch(value::contains)
                || value.length() > MAX_REASONABLE_URL_LENGTH
                || NOT_A_URL.equals(value);
    }

    private static boolean hasSchemeIssue(String value) {
        return value.startsWith("htp://")
                || value.startsWith("://")
                || value.startsWith("http:///")
                || value.startsWith("javascript:")
                || value.startsWith("data:")
                || value.startsWith("ftp://")
                || value.startsWith("file://")
                || SINGLE_SLASH_SCHEME.matcher(value).find();
    }

    private static boolean hasHostIssue(String value) {
        return value.endsWith("://")
                || value.contains(":///")
                || value.contains("..")
                || value.contains("://.")
                || TRAILING_DOT_HOST.matcher(value).find();
    }

    private static boolean hasPathSlashIssue(String value) {
        if (value.endsWith("/")) {
            return true;
        }
        int schemeEnd = value.indexOf("://");
        String rest = schemeEnd < 0 ? value : value.substring(schemeEnd + 3);
        return rest.contains("//");
    }
}

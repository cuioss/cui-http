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
 * Contract test for {@link BoundaryFuzzingGenerator}.
 *
 * <p>The defining property of this generator is that every emitted value pushes at least one of
 * six documented boundaries: extreme length, extreme nesting depth, a null byte, a control
 * character, a path traversal, or a shell metacharacter. The aggregate test asserts that all six
 * branch families are reachable, each identified by the exact shape its branch emits.</p>
 *
 * <p>There is deliberately no per-value pipeline round-trip: the boundary families are not
 * uniformly rejected — a merely long path is not by itself an attack — so a uniform "the
 * pipeline rejects it" assertion would not hold.</p>
 */
@EnableGeneratorController
@DisplayName("BoundaryFuzzingGenerator Contract Tests")
class BoundaryFuzzingGeneratorTest {

    private static final int AGGREGATE_DRAWS = 400;
    private static final int EXTREME_LENGTH = 1000;
    private static final int EXTREME_NESTING_SLASHES = 50;

    /** U+001F, built numerically so that this source file carries no raw control character. */
    private static final String UNIT_SEPARATOR = Character.toString(0x001F);

    private static final Pattern LONG_PATH = Pattern.compile("^/(verylongpathsegment/)+$");
    private static final Pattern DEEP_NESTING = Pattern.compile("^(dir/)+$");
    private static final Pattern MIXED_TRAVERSAL = Pattern.compile("^(\\.\\./)+etc/passwd$");
    private static final Pattern MIXED_LONG_SEGMENTS = Pattern.compile("^/a(verylongpathsegment/)+$");
    private static final Pattern MIXED_NULL_TRAVERSAL = Pattern.compile("^/%00(\\.\\./)+$");
    private static final String MIXED_MULTIPLE_NULLS = "/\0/../\0/../file";

    private static final Set<String> NULL_BYTE_PATTERNS = Set.of(
            "/file\0.txt", "/admin%00.php", "../etc/passwd%00.jpg", "file.jsp%00.png", "%00../../etc/shadow");
    private static final Set<String> CONTROL_CHARACTER_PATTERNS = Set.of(
            "/file\r\n.txt", "/path\t\tfile", "/dir\b\bfile", "/test" + UNIT_SEPARATOR + "file");
    private static final Set<String> SPECIAL_CHARACTER_PATTERNS = Set.of(
            "/file|command", "/file;command", "/file`command`", "/file$variable", "/file>output");

    @ParameterizedTest
    @TypeGeneratorSource(value = BoundaryFuzzingGenerator.class, count = 200)
    @DisplayName("Every generated value pushes at least one documented boundary")
    void shouldGenerateBoundaryCase(String generatedValue) {
        assertFalse(generatedValue.isEmpty(), "Generator should produce non-empty boundary cases");
        assertTrue(pushesADocumentedBoundary(generatedValue),
                () -> "Value pushes no documented boundary. Value: <" + generatedValue + ">");
    }

    @Test
    @DisplayName("Should reach all six documented branch families")
    void shouldReachAllBranchFamilies() {
        BoundaryFuzzingGenerator generator = new BoundaryFuzzingGenerator();
        Set<String> families = new HashSet<>();

        for (int i = 0; i < AGGREGATE_DRAWS; i++) {
            String value = generator.next();
            if (LONG_PATH.matcher(value).matches()) {
                families.add("long-path");
            }
            if (DEEP_NESTING.matcher(value).matches()) {
                families.add("deep-nesting");
            }
            if (NULL_BYTE_PATTERNS.contains(value)) {
                families.add("null-bytes");
            }
            if (CONTROL_CHARACTER_PATTERNS.contains(value)) {
                families.add("control-characters");
            }
            if (isMixedBoundaryAttack(value)) {
                families.add("mixed");
            }
            if (SPECIAL_CHARACTER_PATTERNS.contains(value)) {
                families.add("special-characters");
            }
        }

        assertEquals(Set.of("long-path", "deep-nesting", "null-bytes", "control-characters",
                        "mixed", "special-characters"), families,
                "Every documented branch family must be reachable within " + AGGREGATE_DRAWS + " draws");
    }

    @Test
    @DisplayName("Should return correct type")
    void shouldReturnCorrectType() {
        assertEquals(String.class, new BoundaryFuzzingGenerator().getType(),
                "Generator should return String.class");
    }

    private static boolean pushesADocumentedBoundary(String value) {
        return value.length() >= EXTREME_LENGTH
                || value.chars().filter(character -> character == '/').count() >= EXTREME_NESTING_SLASHES
                || NULL_BYTE_MARKERS.stream().anyMatch(value::contains)
                || containsControlCharacter(value)
                || TRAVERSAL_MARKERS.stream().anyMatch(value::contains)
                || SHELL_METACHARACTERS.stream().anyMatch(value::contains);
    }

    private static boolean isMixedBoundaryAttack(String value) {
        return MIXED_TRAVERSAL.matcher(value).matches()
                || MIXED_LONG_SEGMENTS.matcher(value).matches()
                || MIXED_NULL_TRAVERSAL.matcher(value).matches()
                || MIXED_MULTIPLE_NULLS.equals(value);
    }
}

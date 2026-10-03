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
package de.cuioss.http.security.tests;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.encoding.UnicodeAttackGenerator;
import de.cuioss.http.security.generators.encoding.UnicodeNormalizationAttackGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T3: Test Unicode path traversal variants
 *
 * <p>
 * This test class implements Task T3 from the HTTP security validation plan,
 * focusing on testing Unicode-based path traversal attack patterns using the
 * UnicodeAttackGenerator and advanced cui-test-generator patterns.
 * </p>
 *
 * <h3>One verdict per family</h3>
 * <p>
 * {@link UnicodeAttackGenerator} decorates a traversal sequence with one kind of character, and
 * the kind decides the verdict because character validation runs ahead of pattern matching.
 * Every generator-driven method therefore selects one family by its structural fingerprint
 * ({@link PathWireForm}):
 * </p>
 * <ul>
 *   <li>an invisible or lookalike Unicode character (zero-width space, RLO, BOM, ...) -
 *       {@link UrlSecurityFailureType#INVALID_CHARACTER}</li>
 *   <li>a null character - {@link UrlSecurityFailureType#NULL_BYTE_INJECTION}</li>
 *   <li>no decoration at all, the plain traversal -
 *       {@link UrlSecurityFailureType#PATH_TRAVERSAL_DETECTED}</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>Unicode Security Considerations (TR39)</li>
 *   <li>OWASP Path Traversal Prevention</li>
 *   <li>RFC 3629 UTF-8 validation</li>
 *   <li>CVE-2019-11358, CVE-2020-5398 (Unicode variants)</li>
 * </ul>
 *
 * Implements: Task T3 from HTTP verification specification
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("T3: Unicode Path Traversal Attack Tests")
class UnicodePathTraversalAttackTest {

    private static final AttackFamilyGuard UNICODE_CHARACTER = new AttackFamilyGuard(
            "shouldRejectUnicodeDecoratedTraversalAsInvalidCharacter",
            PathWireForm.RAW_NON_PATH_CHARACTER::isFormOf);
    private static final AttackFamilyGuard NULL_CHARACTER = new AttackFamilyGuard(
            "shouldRejectNullCharacterTraversalAsNullByteInjection", PathWireForm.NULL_BYTE::isFormOf);
    private static final AttackFamilyGuard PLAIN_TRAVERSAL = new AttackFamilyGuard(
            "shouldRejectUndecoratedTraversalAsPathTraversal", PathWireForm.WIRE_CLEAN::isFormOf);

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @AfterAll
    static void shouldHaveAdmittedFilteredSamples() {
        AttackFamilyGuard.assertAllAdmittedSamples(UNICODE_CHARACTER, NULL_CHARACTER, PLAIN_TRAVERSAL);
    }

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(SecurityConfiguration.defaults(), eventCounter);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UnicodeAttackGenerator.class, count = 100)
    @DisplayName("Traversal decorated with a Unicode character is rejected as INVALID_CHARACTER")
    void shouldRejectUnicodeDecoratedTraversalAsInvalidCharacter(String unicodeAttackPattern) {
        if (!UNICODE_CHARACTER.admits(unicodeAttackPattern)) {
            return;
        }
        var exception = assertRejected(unicodeAttackPattern);

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                () -> "Unexpected verdict for: " + codePointsOf(unicodeAttackPattern));
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.INVALID_CHARACTER));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UnicodeAttackGenerator.class, count = 100)
    @DisplayName("Traversal carrying a null character is rejected as NULL_BYTE_INJECTION")
    void shouldRejectNullCharacterTraversalAsNullByteInjection(String unicodeAttackPattern) {
        if (!NULL_CHARACTER.admits(unicodeAttackPattern)) {
            return;
        }
        var exception = assertRejected(unicodeAttackPattern);

        assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType(),
                () -> "Unexpected verdict for: " + codePointsOf(unicodeAttackPattern));
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.NULL_BYTE_INJECTION));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = UnicodeAttackGenerator.class, count = 100)
    @DisplayName("Undecorated traversal is rejected as PATH_TRAVERSAL_DETECTED")
    void shouldRejectUndecoratedTraversalAsPathTraversal(String unicodeAttackPattern) {
        if (!PLAIN_TRAVERSAL.admits(unicodeAttackPattern)) {
            return;
        }
        var exception = assertRejected(unicodeAttackPattern);

        assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, exception.getFailureType(),
                () -> "Unexpected verdict for: " + codePointsOf(unicodeAttackPattern));
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED));
    }

    /**
     * Test Unicode normalization attack variants.
     *
     * <p>
     * Every pattern of {@link UnicodeNormalizationAttackGenerator} carries a raw non-ASCII
     * character - a combining mark, a fullwidth form, a homoglyph or a zero-width character -
     * which the path character set does not admit.
     * </p>
     */
    @ParameterizedTest
    @DisplayName("Unicode normalization attacks are rejected as INVALID_CHARACTER")
    @TypeGeneratorSource(value = UnicodeNormalizationAttackGenerator.class, count = 22)
    void shouldBlockUnicodeNormalizationAttacks(String attack) {
        var exception = assertRejected(attack);

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                () -> "Unexpected verdict for: " + codePointsOf(attack));
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.INVALID_CHARACTER));
    }

    private UrlSecurityException assertRejected(String attack) {
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(attack),
                () -> "Unicode attack should be rejected: " + codePointsOf(attack));
        assertEquals(attack, exception.getOriginalInput(), "Original input should be preserved in exception");
        return exception;
    }

    private static String codePointsOf(String payload) {
        return payload.codePoints().mapToObj("U+%04X"::formatted).toList().toString();
    }
}

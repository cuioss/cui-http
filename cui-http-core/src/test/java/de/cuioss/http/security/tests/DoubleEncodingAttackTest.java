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
import de.cuioss.http.security.generators.encoding.EncodingCombinationGenerator;
import de.cuioss.http.security.generators.encoding.PathTraversalGenerator;
import de.cuioss.http.security.generators.url.ValidURLPathGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T5: Test double encoding attacks
 *
 * <p>
 * This test class implements Task T5 from the HTTP security validation plan,
 * focusing on testing double and multiple encoding attacks that attempt to
 * bypass security controls through nested URL encoding patterns.
 * </p>
 *
 * <h3>One verdict per family</h3>
 * <p>
 * The two generators mix families that the path pipeline decides by different mechanisms, so
 * every generator-driven method selects one family by its structural fingerprint
 * ({@link PathWireForm}) and asserts that family's exact failure type:
 * </p>
 * <p>
 * The nested-percent and the double-encoded traversal family are drawn from
 * {@link PathTraversalGenerator}, the remaining ones from {@link EncodingCombinationGenerator}.
 * The triple-encoded family has one source, {@code EncodingCombinationGenerator}, and therefore
 * one test.
 * </p>
 * <ul>
 *   <li>a nested percent sign ({@code %%32%65}) is a malformed escape -
 *       {@link UrlSecurityFailureType#INVALID_ENCODING}</li>
 *   <li>a raw backslash is outside the path character set -
 *       {@link UrlSecurityFailureType#INVALID_CHARACTER}</li>
 *   <li>a double-encoded traversal sequence the pattern stage lists
 *       ({@code %252e%252e%252f}) is recognised before decoding -
 *       {@link UrlSecurityFailureType#PATH_TRAVERSAL_DETECTED}</li>
 *   <li>every other {@code %25XX} spelling, notably the triple-encoded ones, is caught by the
 *       decoding stage - {@link UrlSecurityFailureType#DOUBLE_ENCODING}</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>OWASP Top 10 - Injection Prevention</li>
 *   <li>RFC 3986 URI Encoding Standards</li>
 *   <li>CVE-2021-42013 (Apache double encoding bypass)</li>
 *   <li>CVE-2019-0230 (Apache Struts double encoding)</li>
 *   <li>CWE-20: Improper Input Validation</li>
 *   <li>CWE-22: Path Traversal</li>
 * </ul>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("T5: Double Encoding Attack Tests")
class DoubleEncodingAttackTest {

    private static final String ENCODED_PERCENT = "%25";

    private static final AttackFamilyGuard NESTED_PERCENT = new AttackFamilyGuard(
            "shouldRejectNestedPercentAsInvalidEncoding", PathWireForm.MALFORMED_ESCAPE::isFormOf);
    private static final AttackFamilyGuard LISTED_DOUBLE_ENCODED_TRAVERSAL = new AttackFamilyGuard(
            "shouldRejectListedDoubleEncodedTraversalAsPathTraversal",
            DoubleEncodingAttackTest::isWireCleanListedDoubleEncodedTraversal);
    private static final AttackFamilyGuard COMBINATION_RAW_BACKSLASH = new AttackFamilyGuard(
            "shouldRejectEncodingCombinationWithRawBackslashAsInvalidCharacter",
            PathWireForm.RAW_NON_PATH_CHARACTER::isFormOf);
    private static final AttackFamilyGuard COMBINATION_LISTED_TRAVERSAL = new AttackFamilyGuard(
            "shouldRejectListedEncodingCombinationAsPathTraversal",
            DoubleEncodingAttackTest::isWireCleanListedTraversal);
    private static final AttackFamilyGuard COMBINATION_UNLISTED_ENCODING = new AttackFamilyGuard(
            "shouldRejectUnlistedEncodingCombinationAsDoubleEncoding",
            DoubleEncodingAttackTest::isWireCleanUnlistedEncoding);

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    private static boolean isWireCleanListedTraversal(String payload) {
        return PathWireForm.WIRE_CLEAN.isFormOf(payload) && PathWireForm.carriesListedTraversalSpelling(payload);
    }

    private static boolean isWireCleanListedDoubleEncodedTraversal(String payload) {
        return isWireCleanListedTraversal(payload) && payload.contains(ENCODED_PERCENT);
    }

    private static boolean isWireCleanUnlistedEncoding(String payload) {
        return PathWireForm.WIRE_CLEAN.isFormOf(payload) && !PathWireForm.carriesListedTraversalSpelling(payload);
    }

    @AfterAll
    static void shouldHaveAdmittedFilteredSamples() {
        AttackFamilyGuard.assertAllAdmittedSamples(NESTED_PERCENT, LISTED_DOUBLE_ENCODED_TRAVERSAL,
                COMBINATION_RAW_BACKSLASH, COMBINATION_LISTED_TRAVERSAL, COMBINATION_UNLISTED_ENCODING);
    }

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(SecurityConfiguration.defaults(), eventCounter);
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = PathTraversalGenerator.class, count = 300)
    @DisplayName("A nested percent sign is rejected as INVALID_ENCODING")
    void shouldRejectNestedPercentAsInvalidEncoding(String doubleEncodingPattern) {
        if (!NESTED_PERCENT.admits(doubleEncodingPattern)) {
            return;
        }
        var exception = assertRejected(doubleEncodingPattern);

        assertEquals(UrlSecurityFailureType.INVALID_ENCODING, exception.getFailureType(),
                () -> "Unexpected verdict for: " + doubleEncodingPattern);
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.INVALID_ENCODING));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = PathTraversalGenerator.class, count = 150)
    @DisplayName("A listed double-encoded traversal sequence is rejected as PATH_TRAVERSAL_DETECTED")
    void shouldRejectListedDoubleEncodedTraversalAsPathTraversal(String doubleEncodingPattern) {
        if (!LISTED_DOUBLE_ENCODED_TRAVERSAL.admits(doubleEncodingPattern)) {
            return;
        }
        var exception = assertRejected(doubleEncodingPattern);

        assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, exception.getFailureType(),
                () -> "Unexpected verdict for: " + doubleEncodingPattern);
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 60)
    @DisplayName("An encoding combination with a raw backslash is rejected as INVALID_CHARACTER")
    void shouldRejectEncodingCombinationWithRawBackslashAsInvalidCharacter(String encodingAttackPattern) {
        if (!COMBINATION_RAW_BACKSLASH.admits(encodingAttackPattern)) {
            return;
        }
        var exception = assertRejected(encodingAttackPattern);

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                () -> "Unexpected verdict for: " + encodingAttackPattern);
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.INVALID_CHARACTER));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 60)
    @DisplayName("A listed encoded traversal combination is rejected as PATH_TRAVERSAL_DETECTED")
    void shouldRejectListedEncodingCombinationAsPathTraversal(String encodingAttackPattern) {
        if (!COMBINATION_LISTED_TRAVERSAL.admits(encodingAttackPattern)) {
            return;
        }
        var exception = assertRejected(encodingAttackPattern);

        assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, exception.getFailureType(),
                () -> "Unexpected verdict for: " + encodingAttackPattern);
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED));
    }

    @ParameterizedTest
    @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 100)
    @DisplayName("A triple-encoded combination is rejected as DOUBLE_ENCODING")
    void shouldRejectUnlistedEncodingCombinationAsDoubleEncoding(String encodingAttackPattern) {
        if (!COMBINATION_UNLISTED_ENCODING.admits(encodingAttackPattern)) {
            return;
        }
        var exception = assertRejected(encodingAttackPattern);

        assertEquals(UrlSecurityFailureType.DOUBLE_ENCODING, exception.getFailureType(),
                () -> "Unexpected verdict for: " + encodingAttackPattern);
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.DOUBLE_ENCODING));
    }

    /**
     * Test valid URL paths should pass validation.
     *
     * <p>
     * Uses ValidURLPathGenerator to ensure that legitimate URL paths
     * are not incorrectly blocked by double encoding detection.
     * </p>
     *
     * @param validPath A valid URL path
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 20)
    @DisplayName("Valid URL paths should pass validation")
    void shouldValidateValidPaths(String validPath) {
        Optional<String> validated = assertDoesNotThrow(() -> pipeline.validate(validPath),
                () -> "Valid path should be accepted: " + validPath);

        assertEquals(Optional.of(validPath), validated, "A valid path is returned unchanged");
        assertEquals(0, eventCounter.getTotalCount(),
                () -> "No security event should be recorded for valid path: " + validPath);
    }

    /**
     * Test the rejected edge cases of double encoding detection.
     *
     * <p>
     * An escape that is truncated is malformed encoding; a {@code %25} followed by two hex
     * digits is the wire form of a double-encoded character. A double-encoded traversal
     * sequence is recognised earlier still, by the pattern stage that runs before decoding,
     * and is therefore reported as path traversal.
     * </p>
     *
     * @param edgeCase the edge-case input
     * @param expected the one failure type the path pipeline reports for it
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            %                    | INVALID_ENCODING
            %2                   | INVALID_ENCODING
            %%                   | INVALID_ENCODING
            %25252e              | DOUBLE_ENCODING
            %2525%252e%252e%252f | PATH_TRAVERSAL_DETECTED
            """)
    @DisplayName("Malformed and double-encoded edge cases are rejected with their exact failure type")
    void shouldRejectEncodingEdgeCases(String edgeCase, UrlSecurityFailureType expected) {
        var exception = assertRejected(edgeCase);

        assertEquals(expected, exception.getFailureType(), () -> "Unexpected verdict for: " + edgeCase);
        assertEquals(1, eventCounter.getCount(expected), () -> "Exactly one " + expected + " event should be recorded");
    }

    /**
     * Test the accepted edge cases of double encoding detection.
     *
     * <p>
     * A single {@code %25} is an ordinary encoded percent sign: it decodes to a literal
     * {@code %} that is followed by no hex pair, so nothing is double-encoded.
     * </p>
     *
     * @param edgeCase the edge-case input
     * @param decoded the decoded form the path pipeline returns
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            %25              | %
            %252             | %2
            %252G            | %2G
            %25%25           | %%
            /normal%25path   | /normal%path
            /path%252        | /path%2
            /%25%25%25%25%25 | /%%%%%
            """)
    @DisplayName("An encoded percent sign that forms no second escape is accepted and decoded")
    void shouldAcceptEncodedPercentSign(String edgeCase, String decoded) {
        Optional<String> validated = assertDoesNotThrow(() -> pipeline.validate(edgeCase),
                () -> "Edge case should be accepted: " + edgeCase);

        assertEquals(Optional.of(decoded), validated, () -> "Unexpected decoded form of: " + edgeCase);
        assertEquals(0, eventCounter.getTotalCount(), "An accepted path records no security event");
    }

    private UrlSecurityException assertRejected(String attack) {
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(attack),
                () -> "Encoding attack should be rejected: " + attack);
        assertEquals(attack, exception.getOriginalInput(), "Original input should be preserved in exception");
        return exception;
    }
}

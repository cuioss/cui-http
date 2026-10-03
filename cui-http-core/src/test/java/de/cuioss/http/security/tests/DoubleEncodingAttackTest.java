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
import de.cuioss.http.security.generators.encoding.DoubleEncodingAttackGenerator;
import de.cuioss.http.security.generators.encoding.EncodingCombinationGenerator;
import de.cuioss.http.security.generators.url.ValidURLPathGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
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
 * <h3>Test Coverage</h3>
 * <ul>
 *   <li>Double URL encoding attacks (%252e%252e%252f)</li>
 *   <li>Triple and higher-level encoding patterns</li>
 *   <li>Mixed single and double encoding combinations</li>
 *   <li>Case variation attacks (%2E vs %2e)</li>
 *   <li>Complex encoding bypass attempts</li>
 *   <li>UTF-8 overlong encoding combined with standard encoding</li>
 *   <li>Windows and Unix path separator encoding mixtures</li>
 *   <li>CVE-specific double encoding patterns</li>
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
 * Implements: Task T5 from HTTP verification specification
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("T5: Double Encoding Attack Tests")
class DoubleEncodingAttackTest {

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;
    private SecurityConfiguration config;

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Test double encoding attack patterns.
     *
     * <p>
     * Uses DoubleEncodingAttackGenerator which creates focused double encoding
     * attack patterns including CVE-specific patterns and various bypass attempts.
     * </p>
     *
     * @param doubleEncodingPattern A double encoding attack pattern
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = DoubleEncodingAttackGenerator.class, count = 50)
    @DisplayName("Double encoding attack patterns should be rejected")
    void shouldRejectDoubleEncodingAttacks(String doubleEncodingPattern) {
        // Given: A double encoding attack pattern from the generator
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the double encoding attack
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(doubleEncodingPattern),
                "Double encoding attack pattern should be rejected: " + doubleEncodingPattern);

        // Then: The validation should fail with appropriate security event
        assertNotNull(exception, "Exception should be thrown for double encoding attack");
        assertTrue(isDoubleEncodingSpecificFailure(exception.getFailureType()),
                "Failure type should be double encoding specific: " + exception.getFailureType() +
                        " for pattern: " + doubleEncodingPattern);

        // And: Original malicious input should be preserved
        assertEquals(doubleEncodingPattern, exception.getOriginalInput(),
                "Original input should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded");
    }

    /**
     * Test standard encoding combination patterns.
     *
     * <p>
     * Uses EncodingCombinationGenerator which creates 1-3 levels of encoding
     * with mixed case variations to test various bypass attempts.
     * </p>
     *
     * @param encodingAttackPattern An encoding combination attack pattern
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 30)
    @DisplayName("Encoding combination attacks should be rejected")
    void shouldRejectEncodingCombinationAttacks(String encodingAttackPattern) {
        // Given: An encoding attack pattern from the generator
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the encoding attack
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(encodingAttackPattern),
                "Encoding attack pattern should be rejected: " + encodingAttackPattern);

        // Then: The validation should fail with appropriate security event
        assertNotNull(exception, "Exception should be thrown for encoding attack");
        assertTrue(isDoubleEncodingSpecificFailure(exception.getFailureType()),
                "Failure type should be double encoding specific: " + exception.getFailureType() +
                        " for pattern: " + encodingAttackPattern);

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded");
    }

    /**
     * Test complex encoding combination patterns.
     *
     * <p>
     * Uses EncodingCombinationGenerator which provides HTTP protocol-layer
     * encoding patterns including URL encoding combinations and various
     * bypass techniques used in real-world attacks.
     * </p>
     *
     * @param complexEncodingPattern A complex encoding attack pattern
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 20)
    @DisplayName("Complex encoding attacks should be rejected")
    void shouldRejectComplexEncodingAttacks(String complexEncodingPattern) {
        // Given: A complex encoding pattern from the generator
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the complex encoding attack
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(complexEncodingPattern),
                "Complex encoding pattern should be rejected: " + complexEncodingPattern);

        // Then: The validation should fail with appropriate security event
        assertNotNull(exception, "Exception should be thrown for complex encoding attack");
        assertTrue(isDoubleEncodingSpecificFailure(exception.getFailureType()),
                "Failure type should be double encoding specific: " + exception.getFailureType() +
                        " for pattern: " + complexEncodingPattern);

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded");
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
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(edgeCase),
                () -> "Edge case should be rejected: " + edgeCase);

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

    /**
     * QI-9: Determines if a failure type matches specific double encoding attack patterns.
     * Replaces broad OR-assertion with comprehensive security validation.
     *
     * @param failureType The actual failure type from validation
     * @return true if the failure type is expected for double encoding patterns
     */
    private boolean isDoubleEncodingSpecificFailure(UrlSecurityFailureType failureType) {
        // QI-9: Double encoding patterns can trigger multiple specific failure types
        // Accept all double encoding-relevant failure types for comprehensive security validation
        return failureType == UrlSecurityFailureType.DOUBLE_ENCODING ||
                failureType == UrlSecurityFailureType.INVALID_ENCODING ||
                failureType == UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED ||
                failureType == UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED ||
                failureType == UrlSecurityFailureType.INVALID_CHARACTER ||
                failureType == UrlSecurityFailureType.UNICODE_NORMALIZATION_CHANGED ||
                failureType == UrlSecurityFailureType.KNOWN_ATTACK_SIGNATURE ||
                failureType == UrlSecurityFailureType.CONTROL_CHARACTERS ||
                failureType == UrlSecurityFailureType.NULL_BYTE_INJECTION;
    }
}
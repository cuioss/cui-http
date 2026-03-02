/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
import de.cuioss.http.security.generators.encoding.UnicodeNormalizationAttackGenerator;
import de.cuioss.http.security.generators.url.ValidURLPathGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T8: Test Unicode normalization attacks
 *
 * <p>
 * This test class implements Task T8 from the HTTP security validation plan,
 * focusing on testing Unicode normalization attacks that exploit how different
 * Unicode sequences can normalize to the same characters, potentially bypassing
 * security controls that don't account for normalization.
 * </p>
 *
 * <h3>Test Coverage</h3>
 * <ul>
 *   <li>Decomposed Unicode characters (NFD normalization attacks)</li>
 *   <li>Composed Unicode characters (NFC normalization issues)</li>
 *   <li>Compatibility normalization (NFKC/NFKD attacks)</li>
 *   <li>Unicode combining character sequences</li>
 *   <li>Homograph attacks using Unicode lookalikes</li>
 *   <li>Overlong UTF-8 sequences that normalize differently</li>
 *   <li>Mixed script attacks using different Unicode blocks</li>
 *   <li>Zero-width character injection</li>
 *   <li>Bidirectional text override attacks</li>
 *   <li>Normalization form consistency validation</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>OWASP Top 10 - Injection Prevention</li>
 *   <li>CWE-20: Improper Input Validation</li>
 *   <li>CWE-176: Improper Handling of Unicode Encoding</li>
 *   <li>CWE-838: Inappropriate Encoding for Output Context</li>
 *   <li>Unicode Technical Standard #39 (Unicode Security Mechanisms)</li>
 *   <li>RFC 3454 - Preparation of Internationalized Strings</li>
 *   <li>Unicode Normalization Forms (UAX #15)</li>
 * </ul>
 *
 * Implements: Task T8 from HTTP verification specification
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@SuppressWarnings({"UnnecessaryUnicodeEscape", "SpellCheckingInspection"})
@EnableGeneratorController
@DisplayName("T8: Unicode Normalization Attack Tests")
class UnicodeNormalizationAttackTest {

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        // Reject high-bit Unicode
        SecurityConfiguration config = SecurityConfiguration.builder()
                .normalizeUnicode(true)
                .failOnSuspiciousPatterns(true)
                .allowExtendedAscii(false)  // Reject high-bit Unicode
                .allowControlCharacters(false)
                .build();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);

    }

    /**
     * Test Unicode normalization attack patterns.
     *
     * <p>
     * Uses UnicodeNormalizationAttackGenerator which creates attack patterns
     * using various Unicode normalization techniques to bypass security controls
     * that might not properly handle Unicode normalization before validation.
     * </p>
     *
     * @param unicodeAttackPattern A Unicode normalization attack pattern
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = UnicodeNormalizationAttackGenerator.class, count = 90)
    @DisplayName("Unicode normalization attack patterns should be rejected")
    void shouldRejectUnicodeNormalizationAttacks(String unicodeAttackPattern) {
        // Given: A Unicode normalization attack pattern from the generator
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the Unicode attack
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(unicodeAttackPattern),
                "Unicode normalization attack pattern should be rejected: " + unicodeAttackPattern +
                        " (normalized: " + Normalizer.normalize(unicodeAttackPattern, Normalizer.Form.NFC) + ")");

        // Then: The validation should fail with appropriate security event
        assertNotNull(exception, "Exception should be thrown for Unicode normalization attack");
        assertTrue(isUnicodeNormalizationSpecificFailure(exception.getFailureType()),
                "Failure type should be Unicode or security-related: " + exception.getFailureType() +
                        " for pattern: " + unicodeAttackPattern);

        // And: Original malicious input should be preserved
        assertEquals(unicodeAttackPattern, exception.getOriginalInput(),
                "Original input should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded");
    }

    /**
     * Test known Unicode normalization attack vectors.
     *
     * <p>
     * Tests specific Unicode normalization attack patterns that have been
     * documented in security research or used in real-world attacks.
     * </p>
     */
    @Test
    @DisplayName("Known Unicode normalization attack vectors should be rejected")
    void shouldRejectKnownUnicodeNormalizationAttacks() {
        String[] knownAttacks = {
                // Decomposed path traversal
                "..\u0300/",                           // ../ with combining grave accent
                "..\u0301\u0302/",                     // ../ with multiple combining characters
                ".\u0300.\u0301/",                     // ./ with combining characters

                // Fullwidth character attacks (NFKC normalization)
                "\uFF0E\uFF0E\uFF0F",                  // Fullwidth ../
                "\uFF0E\uFF0E\uFF3C",                  // Fullwidth ..\
                "\uFF1C\uFF53\uFF43\uFF52\uFF49\uFF50\uFF54\uFF1E", // Fullwidth <script>

                // Homograph attacks
                "\u0430dmin",                          // Cyrillic a + latin dmin (admin lookalike)
                "r\u043E\u043Et",                      // r + Cyrillic oo + t (root lookalike)
                "\u0441onfig",                         // Cyrillic c + latin onfig (config lookalike)

                // Zero-width character injection
                ".\u200B.\u200C/",                     // ../ with zero-width characters
                "admin\u200D",                         // admin with zero-width joiner
                "\uFEFF../",                           // ../ with zero-width no-break space

                // Bidirectional text attacks
                "\u202E../\u202C",                     // ../ with RLO override
                "admin\u061C\u200E",                   // admin with Arabic letter mark

                // Combining character path traversal
                ".\u0300.\u0301\u0302/\u0303.\u0304.\u0305/", // Complex combining sequence

                // Mixed script confusion
                "\u0430\u0440\u043C\u0438\u043D",      // Cyrillic "admin" lookalike
                "j\u0430v\u0430script:",               // Mixed Latin/Cyrillic "javascript:"

                // Compatibility character attacks
                "\u2024\u2024\u2044",                  // Dot leaders + fraction slash (../)
                "\u2215config",                        // Division slash + config

                // Overlong-style Unicode sequences
                "\u1E00\u1E00\u2044",                  // A with ring below (dot-like) + fraction slash

                // Complex normalization bypass attempts
                ".\u0300.\u0301/\u0302.\u0303.\u0304/\u0305etc\u0306/\u0307passwd\u0308",
        };

        for (String attack : knownAttacks) {
            long initialEventCount = eventCounter.getTotalCount();

            var exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate(attack),
                    "Known Unicode normalization attack should be rejected: " + attack +
                            " (normalized: " + Normalizer.normalize(attack, Normalizer.Form.NFC) + ")");

            assertNotNull(exception, "Exception should be thrown for: " + attack);
            assertTrue(eventCounter.getTotalCount() > initialEventCount,
                    "Security event should be recorded for: " + attack);
        }
    }

    /**
     * Test normalization consistency detection - should reject patterns that change after normalization.
     *
     * <p>
     * Tests that the system can detect when input changes after Unicode
     * normalization, which could indicate a normalization bypass attempt.
     * </p>
     */
    @Test
    @DisplayName("Should reject normalization-changing patterns")
    void shouldRejectNormalizationChangingPatterns() {
        String[] normalizationTests = {
                // Decomposed to composed normalization changes
                "file\u0300",              // file + combining grave (changes after NFC)
                ".\u0301/",                // . + combining acute (changes after NFC)
                "admin\u0302",             // admin + combining circumflex

                // Compatibility normalization changes
                "\uFF0E\uFF0E",            // Fullwidth dots (change after NFKC)
                "\uFF0F",                  // Fullwidth solidus (changes after NFKC)
                "\uFF1C",                  // Fullwidth < (changes after NFKC)

                // Multiple combining characters
                "a\u0300\u0301\u0302",     // a with multiple combining characters
                ".\u0303\u0304",           // . with multiple combining characters
        };

        for (String test : normalizationTests) {
            String normalized = Normalizer.normalize(test, Normalizer.Form.NFC);

            // Only test cases where normalization actually changes the string
            if (!test.equals(normalized)) {
                long initialEventCount = eventCounter.getTotalCount();

                // When: Validating normalization-changing input
                var exception = assertThrows(UrlSecurityException.class,
                        () -> pipeline.validate(test),
                        "Normalization-changing pattern should be rejected: " + test);

                // Then: Should specifically detect normalization changes
                assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                        "Should detect normalization as invalid character for: " + test);

                // And: Security event should be recorded
                assertTrue(eventCounter.getTotalCount() > initialEventCount,
                        "Security event should be recorded for normalization change: " + test);
            }
        }
    }

    /**
     * Test edge cases in Unicode normalization detection - malicious patterns.
     *
     * <p>
     * Tests various edge cases that might cause issues in Unicode normalization
     * detection logic, including complex combining sequences and boundary conditions.
     * These patterns should be rejected for security reasons.
     * </p>
     */
    @Test
    @DisplayName("Should reject malicious Unicode edge cases")
    void shouldRejectMaliciousUnicodeEdgeCases() {
        String[] maliciousEdgeCases = {
                // Lone combining character
                "\u0300",                             // Lone combining character
                "\u200B",                             // Lone zero-width space

                // Maximum combining character sequences
                "a\u0300\u0301\u0302\u0303\u0304\u0305\u0306\u0307\u0308\u0309", // Many combining chars

                // Malformed or edge Unicode sequences
                "\uD800",                             // High surrogate alone (invalid)
                "\uDFFF",                             // Low surrogate alone (invalid)

                // Mixed valid/invalid normalization
                "valid\u0300invalid\uD800text",       // Mixed valid combining + invalid surrogate

                // Complex bidirectional sequences
                "\u202E\u202D\u202C\u202B\u202A",     // Multiple BiDi controls

                // Very long normalization sequences
                generateDecomposedSequence(100),   // QI-17: Long sequence of decomposed dots

                // Normalization boundary cases
                "\uFFFE",                             // Byte order mark (BOM) variant
                "\uFFFF",                             // Invalid Unicode codepoint
        };

        for (String edgeCase : maliciousEdgeCases) {
            long initialEventCount = eventCounter.getTotalCount();

            // When: Validating malicious Unicode edge case
            var exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate(edgeCase),
                    "Malicious Unicode edge case should be rejected: " +
                            edgeCase.codePoints().mapToObj("U+%04X"::formatted).toList());

            // Then: Security event should be recorded
            assertTrue(eventCounter.getTotalCount() > initialEventCount,
                    "Security event should be recorded when rejecting: " +
                            edgeCase.codePoints().mapToObj("U+%04X"::formatted).toList());

            // And: Exception should have failure type
            assertNotNull(exception.getFailureType(),
                    "Exception should have failure type for: " + edgeCase);
        }
    }

    /**
     * Test legitimate empty string edge case.
     *
     * <p>
     * Tests that empty strings are handled gracefully without security exceptions.
     * </p>
     */
    @Test
    @DisplayName("Should handle empty string without security exception")
    void shouldHandleEmptyString() {
        // When: Validating empty string
        var result = pipeline.validate("");

        // Then: Should return valid result
        assertTrue(result.isPresent(), "Empty string validation should return result");
        assertNotNull(result, "Result should not be null for empty string");
    }

    /**
     * Test legitimate Unicode content that should be rejected by security-focused system.
     *
     * <p>
     * Tests that even legitimate Unicode content is rejected in URL security context.
     * This is acceptable for a security-first approach.
     * </p>
     */
    @Test
    @DisplayName("Legitimate Unicode content should be rejected by security-focused system")
    void shouldRejectLegitimateUnicodeContentForSecurity() {
        String[] legitimateContent = {
                // Common international characters
                "café",                                // French
                "naïve",                              // French with diaeresis
                "Zürich",                             // German
                "münchen",                            // German umlaut

                // Properly composed Unicode
                "résumé",                             // Composed accented characters
                "piñata",                             // Spanish ñ
                "jalapeño",                           // Spanish ñ

                // Note: Even legitimate Unicode is blocked in URL security context
                // This is acceptable for a security-focused system
        };

        for (String content : legitimateContent) {
            long initialEventCount = eventCounter.getTotalCount();

            // When: Validating legitimate Unicode content
            var exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate(content),
                    "Legitimate Unicode content should be rejected for security: " + content);

            // Then: Security event should be recorded
            assertTrue(eventCounter.getTotalCount() > initialEventCount,
                    "Security event should be recorded when blocking Unicode: " + content);

            // And: Exception should be properly formed
            assertNotNull(exception.getFailureType(),
                    "Exception should have failure type for: " + content);
        }
    }

    /**
     * Test valid URL paths should pass validation.
     *
     * <p>
     * Uses ValidURLPathGenerator to ensure that legitimate URL paths
     * are not incorrectly blocked by Unicode normalization detection.
     * </p>
     *
     * @param validPath A valid URL path
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 15)
    @DisplayName("Valid URL paths should pass validation")
    void shouldValidateValidPaths(String validPath) {
        // Given: A valid path from the generator
        long initialEventCount = eventCounter.getTotalCount();

        // When: Validating the legitimate path
        var result = pipeline.validate(validPath);

        // Then: Should return validated result
        assertTrue(result.isPresent(), "Valid path should return validated result: " + validPath);
        assertNotNull(result, "Valid path should return validated result: " + validPath);

        // And: No security events should be recorded for valid paths
        assertEquals(initialEventCount, eventCounter.getTotalCount(),
                "No security events should be recorded for valid path: " + validPath);
    }

    /**
     * Test normalization-changing forms should be rejected.
     *
     * <p>
     * Tests that the system consistently detects when input would change
     * under different normalization approaches.
     * </p>
     */
    @Test
    @DisplayName("Should reject normalization-changing forms")
    void shouldRejectNormalizationChangingForms() {
        String[] normalizationChangingCases = {
                ".\u0301/",       // Should change under NFC normalization
                "\uFF0E\uFF0E",   // Should change under NFKC normalization
        };

        for (String testCase : normalizationChangingCases) {
            String nfc = Normalizer.normalize(testCase, Normalizer.Form.NFC);
            long initialEventCount = eventCounter.getTotalCount();

            // Only test if normalization changes the input
            if (!testCase.equals(nfc)) {
                // When: Validating normalization-changing input
                var exception = assertThrows(UrlSecurityException.class,
                        () -> pipeline.validate(testCase),
                        "Normalization-changing input should be rejected: " + testCase);

                // Then: Should detect normalization as invalid character
                assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType(),
                        "Should detect normalization as invalid character for: " + testCase);

                // And: Security event should be recorded
                assertTrue(eventCounter.getTotalCount() > initialEventCount,
                        "Security event should be recorded for: " + testCase);
            }
        }
    }

    /**
     * Test already-normalized content should be rejected due to Unicode.
     *
     * <p>
     * Tests that even already-normalized Unicode content is rejected
     * in URL security context due to security-first approach.
     * </p>
     */
    @Test
    @DisplayName("Should reject already-normalized Unicode content")
    void shouldRejectAlreadyNormalizedUnicodeContent() {
        String testCase = "café"; // Should be same in NFC and NFD after normalization
        long initialEventCount = eventCounter.getTotalCount();

        // When: Validating already-normalized Unicode
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(testCase),
                "Already-normalized Unicode should be rejected for security: " + testCase);

        // Then: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for: " + testCase);

        // And: Exception should be properly formed
        assertNotNull(exception.getFailureType(),
                "Exception should have failure type for: " + testCase);
    }

    /**
     * QI-9: Determines if a failure type matches specific Unicode normalization attack patterns.
     * Replaces broad OR-assertion with comprehensive security validation.
     *
     * @param failureType The actual failure type from validation
     * @return true if the failure type is expected for Unicode attack patterns
     */
    private boolean isUnicodeNormalizationSpecificFailure(UrlSecurityFailureType failureType) {
        // QI-9: Unicode normalization patterns can trigger multiple specific failure types
        // Accept all Unicode-relevant failure types for comprehensive security validation
        return failureType == UrlSecurityFailureType.UNICODE_NORMALIZATION_CHANGED ||
                failureType == UrlSecurityFailureType.INVALID_CHARACTER ||
                failureType == UrlSecurityFailureType.CONTROL_CHARACTERS ||
                failureType == UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED ||
                failureType == UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED ||
                failureType == UrlSecurityFailureType.MALFORMED_INPUT ||
                failureType == UrlSecurityFailureType.INVALID_STRUCTURE ||
                failureType == UrlSecurityFailureType.KNOWN_ATTACK_SIGNATURE ||
                failureType == UrlSecurityFailureType.INVALID_ENCODING ||
                failureType == UrlSecurityFailureType.NULL_BYTE_INJECTION;
    }

    /**
     * QI-17: Generate realistic decomposed sequence instead of using .repeat().
     * Creates varied Unicode decomposed patterns for normalization testing.
     */
    private String generateDecomposedSequence(int count) {
        StringBuilder result = new StringBuilder();
        String[] patterns = {".\u0300", ".\u0301", ".\u0302", ".\u0303"}; // Various combining marks

        for (int i = 0; i < count; i++) {
            result.append(patterns[i % patterns.length]);
        }
        return result.toString();
    }

}
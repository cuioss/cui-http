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
import de.cuioss.http.security.generators.url.ValidURLPathGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6: Test HTTP protocol-layer mixed encoding attacks
 *
 * <p>
 * This test class implements Task T6 from the HTTP security validation plan,
 * focusing on testing mixed encoding attacks that combine different HTTP protocol-layer
 * encoding schemes to bypass security controls. <strong>Architectural Scope:</strong>
 * Limited to HTTP/URL protocol encodings only - application-layer encodings
 * (HTML entities, JavaScript escapes, Base64) are handled by higher layers.
 * </p>
 *
 * <h3>HTTP Protocol-Layer Test Coverage</h3>
 * <ul>
 *   <li>UTF-8 overlong encoding mixed with standard URL encoding</li>
 *   <li>Different URL encoding formats (% vs + for spaces, mixed case hex)</li>
 *   <li>Double URL encoding patterns (%25XX combinations)</li>
 *   <li>Mixed case hexadecimal encoding (%2f vs %2F)</li>
 *   <li>Unicode normalization combined with URL encoding</li>
 *   <li>Path separator encoding variations (/, %2F, %5C)</li>
 * </ul>
 *
 * <h3>REMOVED: Cross-Layer Encodings</h3>
 * <p>The following encodings were removed to maintain HTTP/application layer separation:</p>
 * <ul>
 *   <li>❌ HTML entity encoding - belongs in presentation layer</li>
 *   <li>❌ JavaScript escape sequences - belongs in code execution layer</li>
 *   <li>❌ Base64 encoding - belongs in application data layer</li>
 * </ul>
 *
 * <h3>Security Standards</h3>
 * <ul>
 *   <li>OWASP Top 10 - Injection Prevention</li>
 *   <li>CWE-20: Improper Input Validation</li>
 *   <li>CWE-116: Improper Encoding or Escaping of Output</li>
 *   <li>CWE-838: Inappropriate Encoding for Output Context</li>
 *   <li>RFC 3986 URI Encoding Standards</li>
 * </ul>
 *
 * Implements: Task T6 from HTTP verification specification
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@EnableGeneratorController
@DisplayName("T6: Mixed Encoding Attack Tests")
class MixedEncodingAttackTest {

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;
    private SecurityConfiguration config;

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.builder()
                .failOnSuspiciousPatterns(true)
                .allowExtendedAscii(false)  // Reject encoded high-bit chars
                .allowControlCharacters(false) // Reject encoded control chars
                .normalizeUnicode(true)        // Normalize Unicode encoding
                .caseSensitiveComparison(true) // Strict comparison
                .build();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Percent-encoded traversal in the spellings the pattern stage lists: single-encoded in any
     * hex case, with either separator, and the fully double-encoded form.
     *
     * @param attack a traversal spelled with listed percent-encoding
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "%2e%2e%2f",
            "%2E%2E%2F",
            "%2e%2E%2f",
            "%2e%2e%2f%2e%2e%2f%65%74%63",
            "%2E%2E%2F%65%74%63%2F%70%61%73%73%77%64",
            "%252E%252E%252F",
            "%2e%2e%5c",
            "%2e%2e%2f%2e%2e%5c"
    })
    @DisplayName("Listed percent-encoded traversal is rejected as PATH_TRAVERSAL_DETECTED")
    void shouldRejectEncodedTraversalAsPathTraversal(String attack) {
        var exception = assertRejected(attack);

        assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, exception.getFailureType(),
                () -> "Unexpected verdict for: " + attack);
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED));
    }

    /**
     * UTF-8 overlong dots, alone or mixed with standard encoding. An overlong sequence is not
     * well-formed UTF-8, so the decoding stage refuses it.
     *
     * @param attack a traversal spelled with an overlong UTF-8 sequence
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "%c0%ae%c0%ae%2f",
            "%c0%AE%c0%AE%2F",
            "%c0%ae%c0%ae%2f%c0%ae%c0%ae%2f",
            "%c0%ae%2e%2f",
            "%2e%c0%ae%2f",
            "%c0%ae%c0%ae%2F"
    })
    @DisplayName("UTF-8 overlong traversal is rejected as INVALID_ENCODING")
    void shouldRejectOverlongTraversalAsInvalidEncoding(String attack) {
        var exception = assertRejected(attack);

        assertEquals(UrlSecurityFailureType.INVALID_ENCODING, exception.getFailureType(),
                () -> "Unexpected verdict for: " + attack);
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.INVALID_ENCODING));
    }

    /**
     * Double encoding in a spelling the pattern stage does not list: a double-encoded dot pair
     * before a single-encoded separator, and the form in which every character of the encoded
     * sequence is itself encoded.
     *
     * @param attack a traversal spelled with unlisted double encoding
     */
    @ParameterizedTest
    @ValueSource(strings = {"%252e%252e%2f", "%25%32%65%25%32%65%25%32%66"})
    @DisplayName("Unlisted double-encoded traversal is rejected as DOUBLE_ENCODING")
    void shouldRejectUnlistedDoubleEncodedTraversalAsDoubleEncoding(String attack) {
        var exception = assertRejected(attack);

        assertEquals(UrlSecurityFailureType.DOUBLE_ENCODING, exception.getFailureType(),
                () -> "Unexpected verdict for: " + attack);
        assertEquals(1, eventCounter.getCount(UrlSecurityFailureType.DOUBLE_ENCODING));
    }

    private UrlSecurityException assertRejected(String attack) {
        var exception = assertThrows(UrlSecurityException.class, () -> pipeline.validate(attack),
                () -> "Mixed encoding attack pattern should be rejected: " + attack);
        assertEquals(attack, exception.getOriginalInput(), "Original input should be preserved in exception");
        return exception;
    }


    /**
     * Test that legitimate mixed format URLs pass validation.
     *
     * <p>
     * Ensures that legitimate uses of different encoding formats
     * (like + for spaces and % for reserved characters) don't trigger
     * false positives.
     * </p>
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 15)
    @DisplayName("Legitimate mixed format URLs should pass validation")
    void shouldHandleLegitimatesMixedFormatUrls(String path) {
        // Given: A legitimate URL path from ValidURLPathGenerator
        long initialEventCount = eventCounter.getTotalCount();

        // When: Validating the legitimate path
        var result = pipeline.validate(path);

        // Then: Legitimate path should be validated
        assertTrue(result.isPresent(), "Path validation should return result if successful: " + path);
        assertNotNull(result, "Legitimate path should be validated: " + path);

        // And: No security events should be recorded for legitimate paths
        assertEquals(initialEventCount, eventCounter.getTotalCount(),
                "No security events should be recorded for legitimate path: " + path);
    }

    /**
     * Test valid URL paths should pass validation.
     *
     * <p>
     * Uses ValidURLPathGenerator to ensure that legitimate URL paths
     * are not incorrectly blocked by mixed encoding detection.
     * </p>
     *
     * @param validPath A valid URL path
     */
    @ParameterizedTest
    @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 20)
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
}
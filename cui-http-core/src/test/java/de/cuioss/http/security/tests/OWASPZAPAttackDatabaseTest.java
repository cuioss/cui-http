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
import de.cuioss.http.security.database.AttackTestCase;
import de.cuioss.http.security.database.OWASPZAPAttackDatabase;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static de.cuioss.http.security.tests.AttackNameClaims.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for OWASP ZAP Active Scan Attack Database.
 *
 * <p><strong>COMPREHENSIVE ZAP ATTACK TESTING:</strong> This test class validates that all
 * OWASP ZAP active scan attack patterns are properly detected and rejected by the security
 * validation pipeline. Each test case represents an actual attack pattern used by ZAP
 * during security assessments.</p>
 *
 * <p>ZAP (Zed Attack Proxy) is one of the most widely used web application security testing
 * tools, maintained by OWASP. Its active scan rules are continuously updated based on
 * emerging threats and have been battle-tested against millions of web applications.</p>
 *
 * <h3>Test Coverage</h3>
 * <ul>
 *   <li><strong>Path Traversal</strong>: Plain, encoded and path-normalization variants</li>
 *   <li><strong>Local File Inclusion</strong>: Traversal toward a local file</li>
 *   <li><strong>HTTP Splitting</strong>: A double-encoded CR/LF pair followed by a header name</li>
 *   <li><strong>Null Byte Injection</strong>: An encoded null byte truncating a path</li>
 * </ul>
 *
 * <h3>What each test verifies</h3>
 * <p>{@link #shouldRejectZAPAttacksWithCorrectFailureTypes} verifies the pipeline verdict only.
 * {@link #shouldCarryTheFeatureItsNameClaims} verifies, on the payload itself, that each entry
 * carries the feature its constant name claims.</p>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@DisplayName("OWASP ZAP Active Scan Attack Database Tests")
class OWASPZAPAttackDatabaseTest {

    /** What the words of an {@link OWASPZAPAttackDatabase} constant name claim about its payload. */
    private static final AttackNameClaims NAME_CLAIMS = new AttackNameClaims(
            Map.ofEntries(
                    claim("TRAVERSAL", TRAVERSAL),
                    claim("PATH_TRAVERSAL", TRAVERSAL),
                    claim("BASIC", pattern("^(\\.\\./)+[^%]+$")),
                    claim("ENCODED_DOT", literal("%2e%2e/")),
                    claim("ENCODED_SLASH", literal("..%2f")),
                    claim("DOUBLE_DOTS", literal("....//")),
                    claim("BACKSLASH", literal("\\", "%5c")),
                    claim("ABSOLUTE", pattern("^/\\.\\./")),
                    claim("DOUBLE_SLASH", literal("//")),
                    claim("DOT_SEGMENT", literal("/./")),
                    claim("LFI", literal("/etc/passwd")),
                    claim("FILE_SEGMENT", literal("/file/")),
                    claim("RESOURCE_SEGMENT", literal("/resource/")),
                    claim("NULL_BYTE", literal("%00")),
                    claim("MIDSEGMENT", pattern("[^/]%00[^/.]")),
                    claim("TRUNCATION", pattern("%00\\.[a-z]+$")),
                    claim("MIDPATH", pattern("%00/.+")),
                    claim("HTTP_SPLITTING", pattern("(?i)%250d%250a[a-z-]+:")),
                    claim("ENCODED_CRLF", literal("%250d%250a")),
                    claim("OVERLONG_UTF8", literal("%c0%ae")),
                    claim("ENCODED_BACKSLASH", literal("%5c")),
                    claim("PATH_PARAMETER_BYPASS", pattern(";[a-z]+=[^/]*/\\.\\./")),
                    claim("FUZZING_LONG_PATH", pattern("(\\.\\./){50}"))),
            Set.of("ZAP", "TO"));

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Parameterized test that validates all OWASP ZAP attack patterns from the database.
     * Each test case includes comprehensive documentation and expected failure types.
     *
     * @param testCase AttackTestCase containing attack string, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(OWASPZAPAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should reject ZAP attacks with correct failure types")
    void shouldRejectZAPAttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: A ZAP active scan attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "ZAP attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
                attackString, testCase.attackDescription(), testCase.detectionRationale());
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(attackString),
                attackRejectionMessage);

        // Then: The validation should fail with the expected security failure type
        String failureTypeMessage = "Expected failure type %s for attack: %s%nRationale: %s".formatted(
                testCase.expectedFailureType(), attackString, testCase.detectionRationale());
        assertEquals(testCase.expectedFailureType(), exception.getFailureType(), failureTypeMessage);

        // And: Original malicious input should be preserved
        assertEquals(attackString, exception.getOriginalInput(),
                "Original attack string should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for attack: %s".formatted(testCase.getCompactSummary()));
    }

    /**
     * Structural claim of the database (ADR-0009): the payload of every entry carries the
     * feature its constant name claims. An entry whose payload is edited to drop that feature
     * fails here, whatever the pipeline verdict is.
     *
     * @param constantName the name of the database constant
     * @param payload the attack string of that constant
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("declaredEntries")
    @DisplayName("Each entry's payload carries the feature its name claims")
    void shouldCarryTheFeatureItsNameClaims(String constantName, String payload) {
        NAME_CLAIMS.assertCarriedBy(constantName, payload);
    }

    static Stream<Arguments> declaredEntries() {
        return AttackDatabaseEntries.declaredEntries(OWASPZAPAttackDatabase.class);
    }
}
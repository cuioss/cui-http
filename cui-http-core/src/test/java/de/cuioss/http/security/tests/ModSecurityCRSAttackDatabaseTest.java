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
import de.cuioss.http.security.database.ModSecurityCRSAttackDatabase;
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
 * Test class for ModSecurity Core Rule Set Attack Database.
 *
 * <p><strong>COMPREHENSIVE CRS TESTING:</strong> This test class validates that all
 * ModSecurity Core Rule Set (CRS) attack patterns are properly detected and rejected by
 * the security validation pipeline. Each test case represents an actual attack signature
 * from the industry-standard WAF ruleset.</p>
 *
 * <p>The ModSecurity Core Rule Set is the de facto standard for web application firewall
 * rules, used by millions of websites worldwide. It represents the collective knowledge
 * of web application security accumulated over two decades of WAF development.</p>
 *
 * <h3>CRS Rule Categories Tested</h3>
 * <p>Only the categories that have a URL-path expression: path traversal in its plain and
 * encoded spellings, null byte injection, excessive encoding layers, encoded control characters
 * and invalid path characters. See {@link ModSecurityCRSAttackDatabase} for the categories that
 * are deliberately absent.</p>
 *
 * <h3>What each test verifies</h3>
 * <p>{@link #shouldRejectCRSAttacksWithCorrectFailureTypes} verifies the pipeline verdict only.
 * {@link #shouldCarryTheFeatureItsNameClaims} verifies, on the payload itself, that each entry
 * carries the feature its constant name claims.</p>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@DisplayName("ModSecurity Core Rule Set Attack Database Tests")
class ModSecurityCRSAttackDatabaseTest {

    /**
     * What the words of a {@link ModSecurityCRSAttackDatabase} constant name claim about its
     * payload. The rule number is part of the entry's identifier.
     */
    private static final AttackNameClaims NAME_CLAIMS = new AttackNameClaims(
            Map.ofEntries(
                    claim("TRAVERSAL", TRAVERSAL),
                    claim("PATH_TRAVERSAL", TRAVERSAL),
                    claim("WINDOWS", literal("windows")),
                    claim("ENCODED", pattern("%[0-9a-fA-F]{2}")),
                    claim("DOUBLE_ENCODED", literal("%252e")),
                    claim("PROC", literal("/proc/")),
                    claim("CONFIG", literal("/config/")),
                    claim("NULL_BYTE", literal("%00")),
                    claim("CRLF_INJECTION", pattern("(?i)%0d%0a[a-z-]+:")),
                    claim("TRIPLE_ENCODING", literal("%25252e")),
                    claim("OVERLONG_UTF8", literal("%c0%ae")),
                    claim("ADMIN_PATH", pattern("^/admin")),
                    claim("DOUBLE_EXTENSION", pattern("\\.[a-z0-9]+%00\\.[a-z0-9]+$")),
                    claim("API", pattern("^/api/")),
                    claim("REDIRECT", pattern("^/redirect")),
                    claim("FILE_SEGMENT", literal("/file/")),
                    claim("INPUT_SEGMENT", literal("/input/")),
                    claim("DOUBLE_SLASH", literal("//")),
                    claim("PATH_SEMICOLON", pattern(";[a-z]+=")),
                    claim("DOT_SEGMENT", literal("/./")),
                    claim("DOTDOT", literal("..")),
                    claim("SESSION_FILE", literal("/sess_"))),
            Set.of("CRS", "TO", "ACCESS"));

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Parameterized test that validates all ModSecurity CRS attack patterns from the database.
     * Each test case includes comprehensive documentation and expected failure types.
     *
     * @param testCase AttackTestCase containing attack string, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(ModSecurityCRSAttackDatabase.ArgumentsProvider.class)
    @DisplayName("ModSecurity CRS patterns should be rejected with correct failure types")
    void shouldRejectCRSAttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: A ModSecurity CRS attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "CRS attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
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
        return AttackDatabaseEntries.declaredEntries(ModSecurityCRSAttackDatabase.class);
    }
}
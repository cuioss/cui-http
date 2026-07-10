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
import de.cuioss.http.security.database.AttackTestCase;
import de.cuioss.http.security.database.ProtocolHandlerAttackDatabase;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Protocol Handler Attack Database Tests using the structured attack database.
 *
 * <p><strong>CURATED PROTOCOL ATTACK DATABASE TESTING:</strong> This test class drives the
 * 24 hand-curated {@link AttackTestCase} records in {@link ProtocolHandlerAttackDatabase},
 * ensuring each documented protocol-handler attack (JavaScript injection, data URI
 * exploitation, file protocol access, custom schemes, protocol confusion, malformed
 * protocols, null-byte/control-character injection, and encoding bypass) is actually executed
 * against the validation pipeline and rejected with its declared failure type.</p>
 *
 * <p>Complements {@link ProtocolHandlerAttackTest}, which exercises the algorithmically
 * generated patterns. This class provides deterministic coverage of the curated corpus so the
 * per-record {@code expectedFailureType} claims are verified rather than dead test data.</p>
 *
 * @since 1.0
 */
@DisplayName("Protocol Handler Attack Database Tests")
class ProtocolHandlerAttackDatabaseTest {

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        // Suspicious-pattern detection must be enabled so that non-standard schemes
        // (javascript:, data:, custom protocols) are rejected as SUSPICIOUS_PATTERN_DETECTED,
        // matching the curated expectedFailureType values in the database.
        SecurityConfiguration config = SecurityConfiguration.builder()
                .failOnSuspiciousPatterns(true)
                .build();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Parameterized test that validates all protocol handler attack patterns from the database.
     * Each test case includes comprehensive documentation and an expected failure type.
     *
     * @param testCase AttackTestCase containing attack string, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(ProtocolHandlerAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Protocol handler attack patterns should be rejected with correct failure types")
    void shouldRejectProtocolHandlerAttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: A protocol handler attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious protocol pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "Protocol handler attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
                attackString, testCase.attackDescription(), testCase.detectionRationale());
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(attackString),
                attackRejectionMessage);

        // Then: The validation should fail with the expected security failure type
        String failureTypeMessage = "Expected failure type %s for protocol attack: %s%nRationale: %s".formatted(
                testCase.expectedFailureType(), attackString, testCase.detectionRationale());
        assertEquals(testCase.expectedFailureType(), exception.getFailureType(), failureTypeMessage);

        // And: Original malicious input should be preserved
        assertEquals(attackString, exception.getOriginalInput(),
                "Original protocol attack string should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for protocol attack: %s".formatted(testCase.getCompactSummary()));
    }
}

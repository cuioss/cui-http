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
import de.cuioss.http.security.config.SecurityDefaults;
import de.cuioss.http.security.database.AttackTestCase;
import de.cuioss.http.security.database.ProtocolHandlerAttackDatabase;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Protocol Handler Attack Database Tests using the structured attack database.
 *
 * <p><strong>CURATED PROTOCOL ATTACK DATABASE TESTING:</strong> This test class drives the
 * hand-curated {@link AttackTestCase} records in {@link ProtocolHandlerAttackDatabase},
 * ensuring each documented protocol-handler attack (javascript:, vbscript:, data: and file:
 * schemes, in raw, case-varied and percent-encoded spellings) is actually executed
 * against the validation pipeline and rejected with its declared failure type, and that the
 * scheme is the only property an entry can be rejected for.</p>
 *
 * <p>Complements {@link ProtocolHandlerAttackTest}, which exercises the algorithmically
 * generated patterns. This class provides deterministic coverage of the curated corpus so the
 * per-record {@code expectedFailureType} claims are verified rather than dead test data.</p>
 *
 * @since 1.0
 */
@DisplayName("Protocol Handler Attack Database Tests")
class ProtocolHandlerAttackDatabaseTest {

    /**
     * Substrings that would let a mechanism other than the scheme match reject a payload: a
     * traversal sequence, or a character outside the path character set.
     */
    private static final List<String> FORBIDDEN_CO_TRIGGERS = List.of("../", "/..", "<", ">", "?", "#", " ");

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

    /**
     * Structural claim of the database (ADR-0009): every entry is a protocol-handler attack and
     * nothing else. The payload, read the way a URL parser reads it, starts with one of the
     * enforced {@link SecurityDefaults#PROTOCOL_HANDLER_SCHEMES}, and it carries none of the
     * co-triggers that would let another mechanism reject it first - so the scheme match is the
     * only property the pipeline can reject the entry for. The claim is asserted on the payload
     * itself and holds whatever stage order a pipeline runs.
     *
     * @param constantName the name of the database constant
     * @param payload the attack string of that constant
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("databaseEntries")
    @DisplayName("Every entry carries an enforced scheme and no co-trigger")
    void shouldCarryAnEnforcedSchemeAndNoCoTrigger(String constantName, String payload) {
        String parserView = URLDecoder.decode(payload, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

        assertAll(constantName,
                () -> assertTrue(SecurityDefaults.PROTOCOL_HANDLER_SCHEMES.stream().anyMatch(parserView::startsWith),
                        () -> "Entry must start with one of " + SecurityDefaults.PROTOCOL_HANDLER_SCHEMES
                                + " but was: " + payload),
                () -> assertEquals(List.of(), FORBIDDEN_CO_TRIGGERS.stream().filter(payload::contains).toList(),
                        () -> "Entry must carry no co-trigger: " + payload),
                () -> assertTrue(payload.chars().allMatch(character -> character > 0x1F && character <= 0xFF),
                        () -> "Entry must carry no control character, null byte or code point above 255: " + payload));
    }

    static Stream<Arguments> databaseEntries() {
        return AttackDatabaseEntries.declaredEntries(ProtocolHandlerAttackDatabase.class);
    }
}

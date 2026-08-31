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
import de.cuioss.http.security.database.IPv6AttackDatabase;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IPv6 Attack Database Tests using structured attack database.
 *
 * <p><strong>COMPREHENSIVE IPv6 ATTACK DATABASE TESTING:</strong> This test class validates
 * IPv6 protocol attack patterns that exploit IPv6 address parsing vulnerabilities,
 * including IPv4-mapped bypass attacks, scope injection, and malformed address exploitation.</p>
 *
 * <p>Tests various IPv6 attack vectors that can bypass network filtering, exploit
 * dual-stack configurations, and abuse IPv6 address parsing inconsistencies across
 * different systems and libraries.</p>
 *
 * <h3>What each test in this class actually verifies</h3>
 *
 * <p>The two tests here verify different things, and the distinction matters because every entry in
 * the database declares {@code INVALID_CHARACTER}:</p>
 *
 * <ul>
 *   <li><strong>{@link #shouldRejectIPv6AttacksWithCorrectFailureTypes}</strong> verifies only that
 *       the pipeline rejects the payload with the declared failure type. Because the bracket
 *       characters {@code [} and {@code ]} are not members of the RFC 3986 path character set, every
 *       entry is rejected by character validation <em>before</em> any IPv6 address parsing happens.
 *       This test therefore does NOT distinguish an IPv4-mapped bypass from a zone-ID injection from
 *       a compression abuse - the pipeline reaches the same verdict by the same route for all of
 *       them.</li>
 *   <li><strong>{@link #shouldCarryTheStructuralFeatureItsNameClaims}</strong> supplies that missing
 *       distinction structurally, by asserting that each entry's payload actually contains the
 *       feature its constant name advertises: an IPv4-embedding prefix, a zone-identifier
 *       {@code %}, a {@code ::} compression sequence, a bracket, or a port suffix. It is a claim
 *       test over the database's own naming, not a pipeline-behaviour test.</li>
 * </ul>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@DisplayName("IPv6 Attack Database Tests")
class IPv6AttackDatabaseTest {

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Parameterized test that validates all IPv6 attack patterns from the database.
     * Each test case includes comprehensive documentation and expected failure types.
     *
     * @param testCase AttackTestCase containing IPv6 attack, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(IPv6AttackDatabase.ArgumentsProvider.class)
    @DisplayName("IPv6 attack patterns should be rejected with correct failure types")
    void shouldRejectIPv6AttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: An IPv6 attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious IPv6 pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "IPv6 attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
                attackString, testCase.attackDescription(), testCase.detectionRationale());
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(attackString),
                attackRejectionMessage);

        // Then: The validation should fail with the expected security failure type
        String failureTypeMessage = "Expected failure type %s for IPv6 attack: %s%nRationale: %s".formatted(
                testCase.expectedFailureType(), attackString, testCase.detectionRationale());
        assertEquals(testCase.expectedFailureType(), exception.getFailureType(), failureTypeMessage);

        // And: Original malicious input should be preserved
        assertEquals(attackString, exception.getOriginalInput(),
                "Original IPv6 attack string should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for IPv6 attack: %s".formatted(testCase.getCompactSummary()));
    }

    /**
     * Every declared {@code AttackTestCase} constant on the database, as (name, payload) pairs.
     * See {@link AttackDatabaseEntries} for why reflection is used here.
     */
    static Stream<Arguments> declaredEntries() {
        return AttackDatabaseEntries.declaredEntries(IPv6AttackDatabase.class);
    }

    /**
     * Asserts that each entry's payload carries the structural IPv6 feature its constant name
     * claims. This is what makes the names verifiable: the pipeline verdict cannot distinguish these
     * categories, because every payload is rejected at character validation for its brackets before
     * any IPv6 parsing occurs.
     *
     * <p>An entry whose payload is edited to drop the feature its name advertises fails here.</p>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("declaredEntries")
    @DisplayName("Each entry's payload carries the structural feature its name claims")
    void shouldCarryTheStructuralFeatureItsNameClaims(String name, String payload) {
        if (name.startsWith("IPV4_MAPPED")) {
            // RFC 4291 maps IPv4 as ::ffff:a.b.c.d; RFC 6052 embeds it after the well-known
            // prefix 64:ff9b::. Both are IPv4-embedding forms, so either satisfies the claim.
            assertTrue(payload.contains("::ffff:") || payload.contains("64:ff9b::"),
                    "%s claims an IPv4-mapped address but its payload carries neither the RFC 4291 "
                            + "'::ffff:' prefix nor the RFC 6052 '64:ff9b::' prefix: %s"
                            .formatted(name, payload));
        }
        if (name.startsWith("ZONE_")) {
            assertTrue(payload.contains("%"),
                    "%s claims a zone identifier but its payload carries no '%%' scope separator: %s"
                            .formatted(name, payload));
        }
        if (name.contains("COMPRESSION")) {
            assertTrue(payload.contains("::"),
                    "%s claims IPv6 zero compression but its payload carries no '::' sequence: %s"
                            .formatted(name, payload));
        }
        if (name.startsWith("BRACKET")) {
            assertTrue(payload.contains("[") || payload.contains("]"),
                    "%s claims a bracket manipulation but its payload carries no bracket: %s"
                            .formatted(name, payload));
        }
        if (name.contains("PORT")) {
            assertTrue(payload.contains("]:"),
                    "%s claims a port specification but its payload carries no ']:' port suffix: %s"
                            .formatted(name, payload));
        }
    }
}
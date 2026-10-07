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
import de.cuioss.http.security.database.SpringCVEAttackDatabase;
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
 * Replays the {@link SpringCVEAttackDatabase} (CVE-2020-5410) against the URL path pipeline.
 *
 * <h3>What each test verifies</h3>
 * <p>{@link #shouldRejectSpringCVEAttacksWithCorrectFailureTypes} verifies the pipeline verdict:
 * every entry is rejected with the failure type it declares, {@code PATH_TRAVERSAL_DETECTED},
 * raised by the wire-form pattern pass on the double-encoded parent reference.
 * {@link #shouldCarryTheFeatureItsNameClaims} establishes on the payload itself, independent of
 * the pipeline, that it carries the CVE's wire form and a traversal.</p>
 *
 * @since 1.0
 */
@DisplayName("Spring CVE Attack Database Tests")
class SpringCVEAttackDatabaseTest {

    /**
     * What the words of a {@link SpringCVEAttackDatabase} constant name claim about its payload.
     * CVE-2020-5410 is defined by a wire form - a double-encoded traversal closed by an encoded
     * fragment marker and followed by a profile segment - so the CVE identifier is a claim in its
     * own right.
     */
    private static final AttackNameClaims NAME_CLAIMS = new AttackNameClaims(
            Map.ofEntries(
                    claim("CVE_2020_5410", pattern("^/(?:\\.\\.%252F)+[^#?]*%23[^/]+/[^/]+$")),
                    claim("TRAVERSAL", TRAVERSAL),
                    claim("PASSWD", literal("etc%252Fpasswd")),
                    claim("DEPTH_4", pattern("^/(?:\\.\\.%252F){4}(?!\\.\\.)"))),
            Set.of("NUCLEI"));

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Validates every Spring CVE attack pattern from the database against the URL path pipeline.
     *
     * @param testCase AttackTestCase containing attack string, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(SpringCVEAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Spring CVE attack patterns should be rejected with correct failure types")
    void shouldRejectSpringCVEAttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: A Spring CVE attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "Spring CVE attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
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
        return AttackDatabaseEntries.declaredEntries(SpringCVEAttackDatabase.class);
    }
}

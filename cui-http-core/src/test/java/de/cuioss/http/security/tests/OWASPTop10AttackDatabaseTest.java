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
import de.cuioss.http.security.database.OWASPTop10AttackDatabase;
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
 * OWASP Top 10 Attack Database Tests using structured attack database.
 *
 * <p>The database holds the OWASP Top 10 2021 patterns that have a URL-path expression: path
 * traversal in its plain, encoded, double-encoded, overlong-UTF-8 and mixed-case spellings, null
 * byte injection, and traversal used against a vulnerable component or to step around a protected
 * path. Application-layer categories - SQL injection, cross-site scripting, command injection,
 * server-side request forgery - are deliberately absent; see {@link OWASPTop10AttackDatabase}.</p>
 *
 * <h3>What each test verifies</h3>
 * <p>{@link #shouldRejectOWASPTop10AttacksWithCorrectFailureTypes} verifies the pipeline verdict
 * only. {@link #shouldCarryTheFeatureItsNameClaims} verifies, on the payload itself, that each
 * entry carries the feature its constant name claims.</p>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@DisplayName("OWASP Top 10 Attack Database Tests")
class OWASPTop10AttackDatabaseTest {

    /** What the words of an {@link OWASPTop10AttackDatabase} constant name claim about its payload. */
    private static final AttackNameClaims NAME_CLAIMS = new AttackNameClaims(
            Map.ofEntries(
                    claim("CLASSIC", pattern("^[^%]+$")),
                    claim("TRAVERSAL", TRAVERSAL),
                    claim("PATH_TRAVERSAL", TRAVERSAL),
                    claim("UNIX", literal("../")),
                    claim("WINDOWS", literal("..\\")),
                    claim("URL_ENCODED", literal("%2e%2e%2f")),
                    claim("DOUBLE_ENCODED", literal("%252e%252e%252f")),
                    claim("UTF8_OVERLONG", literal("%c0%ae%c0%ae%c0%af")),
                    claim("NULL_BYTE", literal("%00")),
                    claim("STRUTS2", literal("/struts2")),
                    claim("AUTH_BYPASS", literal("/admin/../")),
                    claim("MIXED_ENCODING_BYPASS",
                            payload -> payload.contains("%2F") && payload.contains("%2f"))),
            Set.of("COMPONENT"));

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Parameterized test that validates all OWASP Top 10 attack patterns from the database.
     * Each test case includes comprehensive documentation and expected failure types.
     *
     * @param testCase AttackTestCase containing OWASP attack, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(OWASPTop10AttackDatabase.ArgumentsProvider.class)
    @DisplayName("OWASP Top 10 attack patterns should be rejected with correct failure types")
    void shouldRejectOWASPTop10AttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: An OWASP Top 10 attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious OWASP pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "OWASP Top 10 attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
                attackString, testCase.attackDescription(), testCase.detectionRationale());
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(attackString),
                attackRejectionMessage);

        // Then: The validation should fail with the expected security failure type
        String failureTypeMessage = "Expected failure type %s for OWASP attack: %s%nRationale: %s".formatted(
                testCase.expectedFailureType(), attackString, testCase.detectionRationale());
        assertEquals(testCase.expectedFailureType(), exception.getFailureType(), failureTypeMessage);

        // And: Original malicious input should be preserved
        assertEquals(attackString, exception.getOriginalInput(),
                "Original OWASP attack string should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for OWASP attack: %s".formatted(testCase.getCompactSummary()));
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
        return AttackDatabaseEntries.declaredEntries(OWASPTop10AttackDatabase.class);
    }
}
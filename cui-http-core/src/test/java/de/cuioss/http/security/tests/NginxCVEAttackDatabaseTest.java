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
import de.cuioss.http.security.database.NginxCVEAttackDatabase;
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
 * Nginx CVE Attack Database Tests using structured attack database.
 *
 * <p><strong>COMPREHENSIVE NGINX CVE DATABASE TESTING:</strong> This test class validates
 * Nginx CVE exploit patterns that target specific vulnerabilities in the Nginx web server
 * across different versions and module configurations.</p>
 *
 * <p>The entries are the URL-path expressions filed under Nginx and LiteSpeed CVEs and Nginx
 * misconfigurations: the space-in-URI trigger, injected request-line and header fragments, alias
 * off-by-slash traversal, variable-named segments and encoded traversal spellings. A CVE's impact
 * - buffer overflow, memory corruption - is not a property of a URL path and is not what these
 * tests exercise.</p>
 *
 * <h3>What each test verifies</h3>
 * <p>{@link #shouldRejectNginxCVEAttacksWithCorrectFailureTypes} verifies the pipeline verdict
 * only. {@link #shouldCarryTheFeatureItsNameClaims} verifies, on the payload itself, that each
 * entry carries the feature its constant name claims.</p>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@DisplayName("Nginx CVE Attack Database Tests")
class NginxCVEAttackDatabaseTest {

    /**
     * What the words of a {@link NginxCVEAttackDatabase} constant name claim about its payload. A
     * CVE number is part of the entry's identifier; the server product is a label.
     */
    private static final AttackNameClaims NAME_CLAIMS = new AttackNameClaims(
            Map.ofEntries(
                    claim("SPACE_URI", pattern("^/[^ ]+ /")),
                    claim("PASSWD", literal("/etc/passwd")),
                    claim("SHADOW", literal("/etc/shadow")),
                    claim("WINDOWS", literal("/windows/")),
                    claim("RANGE", literal("\r\nRange: bytes=")),
                    claim("RANGE_OVERFLOW", pattern("\r\nRange: bytes=\\d+-\\d{9,}")),
                    claim("H2", literal(" HTTP/2.0")),
                    claim("API", literal("/api/")),
                    claim("RESOLVER", literal("/resolver/")),
                    claim("DNS", literal("/dns/")),
                    claim("TRAVERSAL", TRAVERSAL),
                    claim("DIRECTORY_TRAVERSAL", TRAVERSAL),
                    claim("CRLF", literal("\r\n")),
                    claim("CRLF_INJECTION", pattern("\r\n[A-Za-z-]+: ")),
                    claim("AUTH_BYPASS", literal("\r\nX-Auth: bypass")),
                    claim("UPLOADS", literal("/uploads")),
                    claim("ALIAS", pattern("^/[a-z]+\\.\\./")),
                    claim("STATIC", literal("/static")),
                    claim("MEDIA", literal("/media")),
                    claim("VARIABLE", pattern("/\\$[a-z_]+/")),
                    claim("DOCUMENT_ROOT", literal("$document_root")),
                    claim("URI_INJECTION", literal("/$uri/")),
                    claim("CGI", literal("/cgi-bin/")),
                    claim("URL_ENCODED", literal("%2e%2e")),
                    claim("MIXED_ENCODING", literal("..%2f")),
                    claim("BACKSLASH", literal("\\"))),
            Set.of("CVE", "NGINX", "LITESPEED"));

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Parameterized test that validates all Nginx CVE attack patterns from the database.
     * Each test case includes comprehensive documentation and expected failure types.
     *
     * @param testCase AttackTestCase containing Nginx CVE attack, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(NginxCVEAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Nginx CVE attack patterns should be rejected with correct failure types")
    void shouldRejectNginxCVEAttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: A Nginx CVE attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious Nginx CVE pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "Nginx CVE attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
                attackString, testCase.attackDescription(), testCase.detectionRationale());
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(attackString),
                attackRejectionMessage);

        // Then: The validation should fail with the expected security failure type
        String failureTypeMessage = "Expected failure type %s for Nginx CVE attack: %s%nRationale: %s".formatted(
                testCase.expectedFailureType(), attackString, testCase.detectionRationale());
        assertEquals(testCase.expectedFailureType(), exception.getFailureType(), failureTypeMessage);

        // And: Original malicious input should be preserved
        assertEquals(attackString, exception.getOriginalInput(),
                "Original Nginx CVE attack string should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for Nginx CVE attack: %s".formatted(testCase.getCompactSummary()));
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
        return AttackDatabaseEntries.declaredEntries(NginxCVEAttackDatabase.class);
    }
}
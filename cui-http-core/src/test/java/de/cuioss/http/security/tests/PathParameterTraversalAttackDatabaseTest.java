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
import de.cuioss.http.security.database.PathParameterTraversalAttackDatabase;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Path-parameter traversal ({@code ..;} family) tests driven by the structured attack database.
 *
 * <p>Every entry of {@link PathParameterTraversalAttackDatabase} is a payload that a Servlet-style
 * container resolves to a parent-directory traversal once it strips the {@code ;}-introduced path
 * parameter, while the raw request line carries no literal {@code ../}. The pipeline must reject
 * each one with exactly {@link de.cuioss.http.security.core.UrlSecurityFailureType#PATH_TRAVERSAL_DETECTED}
 * — asserting merely that <em>some</em> exception was thrown would let an unrelated verdict
 * (a character rejection, a length rejection) pass as coverage of this family.</p>
 *
 * @since 1.0
 */
@DisplayName("Path Parameter Traversal Attack Database Tests")
class PathParameterTraversalAttackDatabaseTest {

    /**
     * A segment-anchored {@code ..;} in any spelling the pipeline decodes to it: the dots either
     * literal or percent-encoded, the semicolon either literal or percent-encoded. Hex digits are
     * matched case-insensitively because percent-encoding is case-insensitive.
     */
    private static final Pattern SEGMENT_ANCHORED_DOT_SEMICOLON = Pattern.compile(
            "(?:^|/)(?:\\.\\.|%2e%2e)(?:;|%3b)", Pattern.CASE_INSENSITIVE);

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Every database entry must be rejected by the URL path pipeline with the exact failure type
     * it declares, with the original input preserved and a security event recorded.
     *
     * @param testCase the attack case supplied by the database's ArgumentsProvider
     */
    @ParameterizedTest
    @ArgumentsSource(PathParameterTraversalAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Path-parameter dot-segment attacks should be rejected with PATH_TRAVERSAL_DETECTED")
    void shouldRejectPathParameterTraversalWithCorrectFailureTypes(AttackTestCase testCase) {
        // Arrange
        long initialEventCount = eventCounter.getTotalCount();
        String attackString = testCase.attackString();

        // Act
        String rejectionMessage = "Path-parameter traversal should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
                attackString, testCase.attackDescription(), testCase.detectionRationale());
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(attackString),
                rejectionMessage);

        // Assert: the specific typed failure, not merely that something was thrown
        assertEquals(testCase.expectedFailureType(), exception.getFailureType(),
                "Expected failure type %s for path-parameter traversal: %s%nRationale: %s".formatted(
                        testCase.expectedFailureType(), attackString, testCase.detectionRationale()));

        assertEquals(attackString, exception.getOriginalInput(),
                "Original attack string should be preserved in exception");

        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for path-parameter traversal: %s".formatted(
                        testCase.getCompactSummary()));
    }

    /**
     * Structural claim behind ADR-0009: every entry must literally carry a segment-anchored
     * {@code ..;} (or a percent-encoded spelling of it) in its raw form.
     *
     * <p>Without this check, an entry silently edited to drop its distinguishing {@code ;} would
     * still pass the verdict test above — it would simply be rejected on an ordinary {@code ../}
     * literal by a different pattern, and the database would quietly stop covering the family it
     * exists to cover. This test fails in that case instead.</p>
     *
     * @param testCase the attack case supplied by the database's ArgumentsProvider
     */
    @ParameterizedTest
    @ArgumentsSource(PathParameterTraversalAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Every entry must carry a segment-anchored '..;' in its raw form")
    void everyEntryCarriesSegmentAnchoredPathParameterDotSegment(AttackTestCase testCase) {
        String attackString = testCase.attackString();

        assertTrue(SEGMENT_ANCHORED_DOT_SEMICOLON.matcher(attackString).find(),
                ("Entry '%s' no longer carries a segment-anchored '..;' (literal or percent-encoded). "
                        + "It therefore does not exercise the path-parameter traversal mechanism this "
                        + "database exists to cover, and would pass the verdict test on an unrelated "
                        + "traversal literal.").formatted(attackString));

        assertFalse(attackString.contains("../"),
                ("Entry '%s' contains a literal '../', so it would already be rejected without the "
                        + "path-parameter mechanism being reached — remove it from this database.")
                        .formatted(attackString));
    }
}

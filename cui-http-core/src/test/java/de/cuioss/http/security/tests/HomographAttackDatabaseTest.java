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
import de.cuioss.http.security.database.HomographAttackDatabase;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.Character.UnicodeScript;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Homograph Attack Database Tests using structured attack database.
 *
 * <p><strong>COMPREHENSIVE UNICODE HOMOGRAPH DATABASE TESTING:</strong> This test class validates
 * Unicode homograph attack patterns that exploit visual character similarity across different
 * writing systems to bypass security filters while appearing legitimate to human users.</p>
 *
 * <p>Tests Unicode homographs from Cyrillic, Greek, Mathematical, Fullwidth, Armenian,
 * and Georgian scripts that are visually identical or nearly identical to Latin characters
 * but have different Unicode code points.</p>
 *
 * <h3>What each test in this class actually verifies</h3>
 *
 * <p>Every entry in the database declares {@code INVALID_CHARACTER}, so the two tests here verify
 * different things:</p>
 *
 * <ul>
 *   <li><strong>{@link #shouldRejectHomographAttacksWithCorrectFailureTypes}</strong> verifies only
 *       that the pipeline rejects the payload with the declared failure type. Every payload contains
 *       a non-ASCII code point, and RFC 3986 restricts URL paths to ASCII, so all of them are
 *       rejected by character validation on the first non-ASCII code point - before any script or
 *       confusable analysis. This test therefore does NOT distinguish a Cyrillic homograph from a
 *       Greek one from a fullwidth one.</li>
 *   <li><strong>{@link #shouldCarryTheScriptItsNameClaims}</strong> supplies that missing
 *       distinction structurally, by asserting each payload actually contains a code point in the
 *       script its constant name advertises. It is a claim test over the database's own naming, not
 *       a pipeline-behaviour test.</li>
 * </ul>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@DisplayName("Homograph Attack Database Tests")
class HomographAttackDatabaseTest {

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowExtendedAscii(true)  // Allow Unicode characters for homograph detection
                .failOnSuspiciousPatterns(true)  // Enable suspicious pattern detection
                .build();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Parameterized test that validates all Unicode homograph attack patterns from the database.
     * Each test case includes comprehensive documentation and expected failure types.
     *
     * @param testCase AttackTestCase containing homograph attack, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(HomographAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should reject homograph attacks with correct types")
    void shouldRejectHomographAttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: A Unicode homograph attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious homograph pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "Homograph attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
                attackString, testCase.attackDescription(), testCase.detectionRationale());
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(attackString),
                attackRejectionMessage);

        // Then: The validation should fail with the expected security failure type
        String failureTypeMessage = "Expected failure type %s for homograph attack: %s%nRationale: %s".formatted(
                testCase.expectedFailureType(), attackString, testCase.detectionRationale());
        assertEquals(testCase.expectedFailureType(), exception.getFailureType(), failureTypeMessage);

        // And: Original malicious input should be preserved
        assertEquals(attackString, exception.getOriginalInput(),
                "Original homograph attack string should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for homograph attack: %s".formatted(testCase.getCompactSummary()));
    }

    /** Unicode Mathematical Alphanumeric Symbols block. */
    private static final int MATH_ALPHANUMERIC_START = 0x1D400;
    private static final int MATH_ALPHANUMERIC_END = 0x1D7FF;

    /** Unicode Halfwidth and Fullwidth Forms block. */
    private static final int FULLWIDTH_FORMS_START = 0xFF00;
    private static final int FULLWIDTH_FORMS_END = 0xFFEF;

    /**
     * Scripts that carry no homograph claim of their own: {@code COMMON} covers punctuation and
     * digits, {@code LATIN} is the script being imitated, and {@code UNKNOWN} is not a script.
     */
    private static final Set<UnicodeScript> NON_CLAIMING_SCRIPTS =
            EnumSet.of(UnicodeScript.COMMON, UnicodeScript.LATIN, UnicodeScript.UNKNOWN);

    /**
     * Every declared {@code AttackTestCase} constant on the database, as (name, payload) pairs.
     * See {@link AttackDatabaseEntries} for why reflection is used here.
     */
    static Stream<Arguments> declaredEntries() {
        return AttackDatabaseEntries.declaredEntries(HomographAttackDatabase.class);
    }

    private static boolean containsScript(String payload, UnicodeScript script) {
        return payload.codePoints().anyMatch(cp -> UnicodeScript.of(cp) == script);
    }

    private static boolean containsCodePointInRange(String payload, int startInclusive, int endInclusive) {
        return payload.codePoints().anyMatch(cp -> cp >= startInclusive && cp <= endInclusive);
    }

    /**
     * Asserts that each entry's payload actually contains a code point in the script its constant
     * name claims. This is what makes the per-script names verifiable: the pipeline verdict is
     * {@code INVALID_CHARACTER} for every entry, reached on the first non-ASCII code point, so it
     * cannot distinguish Cyrillic from Greek from a mathematical or fullwidth variant.
     *
     * <p>An entry whose payload is edited to drop the script its name advertises - or to drop its
     * homograph character entirely - fails here.</p>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("declaredEntries")
    @DisplayName("Each entry's payload carries a code point in the script its name claims")
    void shouldCarryTheScriptItsNameClaims(String name, String payload) {
        // Baseline claim, binding on every entry: a homograph payload must carry at least one
        // non-ASCII code point. Without this, an entry could be silently reduced to plain ASCII
        // and still pass every other assertion.
        //
        // The baseline is deliberately "non-ASCII" rather than "non-Latin script": the
        // mathematical-bold and fullwidth families are Latin-script code points living in
        // compatibility blocks, so Character.UnicodeScript.of returns LATIN for them. Those two
        // families are therefore claimed by code-point block below, not by script.
        assertTrue(payload.codePoints().anyMatch(cp -> cp > 0x7F),
                "%s is a homograph entry but its payload is pure ASCII, so it substitutes nothing: %s"
                        .formatted(name, payload));

        Set<UnicodeScript> nonLatinScripts = payload.codePoints()
                .mapToObj(UnicodeScript::of)
                .filter(script -> !NON_CLAIMING_SCRIPTS.contains(script))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(UnicodeScript.class)));

        if (name.startsWith("CYRILLIC")) {
            assertTrue(containsScript(payload, UnicodeScript.CYRILLIC),
                    "%s claims a Cyrillic homograph but its payload carries no Cyrillic code point: %s"
                            .formatted(name, payload));
        }
        if (name.startsWith("GREEK")) {
            assertTrue(containsScript(payload, UnicodeScript.GREEK),
                    "%s claims a Greek homograph but its payload carries no Greek code point: %s"
                            .formatted(name, payload));
        }
        if (name.startsWith("MATHEMATICAL")) {
            assertTrue(containsCodePointInRange(payload, MATH_ALPHANUMERIC_START, MATH_ALPHANUMERIC_END),
                    "%s claims a mathematical homograph but its payload carries no code point in the "
                            + "Mathematical Alphanumeric Symbols block (U+1D400-U+1D7FF): %s"
                            .formatted(name, payload));
        }
        if (name.startsWith("FULLWIDTH")) {
            assertTrue(containsCodePointInRange(payload, FULLWIDTH_FORMS_START, FULLWIDTH_FORMS_END),
                    "%s claims a fullwidth homograph but its payload carries no code point in the "
                            + "Halfwidth and Fullwidth Forms block (U+FF00-U+FFEF): %s"
                            .formatted(name, payload));
        }
        if (name.startsWith("MIXED_SCRIPT")) {
            assertTrue(nonLatinScripts.size() >= 2,
                    "%s claims a mixed-script homograph but its payload draws on only %s: %s"
                            .formatted(name, nonLatinScripts, payload));
        }
    }
}
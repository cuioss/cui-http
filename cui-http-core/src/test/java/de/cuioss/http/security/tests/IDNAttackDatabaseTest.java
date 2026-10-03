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
import de.cuioss.http.security.database.IDNAttackDatabase;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.URLPathValidationPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.IDN;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static de.cuioss.http.security.tests.AttackNameClaims.*;
import static java.lang.Character.UnicodeScript.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * IDN (Internationalized Domain Name) Attack Database Tests using structured attack database.
 *
 * <p><strong>COMPREHENSIVE IDN ATTACK DATABASE TESTING:</strong> This test class validates
 * Internationalized Domain Name attack patterns that exploit IDN processing vulnerabilities,
 * including punycode encoding bypass, mixed script attacks, and homograph domain spoofing.</p>
 *
 * <p>Tests various IDN attack vectors that can bypass domain validation, create phishing
 * domains, and exploit Unicode normalization vulnerabilities in domain name processing.</p>
 *
 * <h3>Attack Categories Tested</h3>
 * <ul>
 *   <li><strong>Punycode Bypass</strong> - Malformed punycode to bypass filters</li>
 *   <li><strong>Mixed Script Domains</strong> - Combining character sets in domain names</li>
 *   <li><strong>Homograph Domains</strong> - Visually similar characters in domain spoofing</li>
 *   <li><strong>Unicode Normalization</strong> - Exploiting normalization differences</li>
 *   <li><strong>IDN Encoding Bypass</strong> - Various encoding bypass techniques</li>
 * </ul>
 *
 * <h3>What each test verifies</h3>
 * <p>{@link #shouldRejectIDNAttacksWithCorrectFailureTypes} verifies the pipeline verdict only.
 * Most entries carry a code point above 255 and are rejected by character validation before any
 * script, punycode or traversal property is looked at, so that verdict cannot tell a homograph
 * from a bidirectional override from a zero-width insertion.
 * {@link #shouldCarryTheFeatureItsNameClaims} supplies the distinction on the payload itself.</p>
 *
 * @author Claude Code Generator
 * @since 1.0
 */
@DisplayName("IDN Attack Database Tests")
class IDNAttackDatabaseTest {

    private static final int ONE_DOT_LEADER = 0x2024;
    private static final int HYPHENATION_POINT = 0x2027;
    private static final int LATIN_SMALL_E_WITH_ACUTE = 0x00E9;
    private static final int COMBINING_ACUTE_ACCENT = 0x0301;
    private static final int RIGHT_TO_LEFT_OVERRIDE = 0x202E;
    private static final int ZERO_WIDTH_SPACE = 0x200B;
    private static final int ZERO_WIDTH_NON_JOINER = 0x200C;
    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int ZERO_WIDTH_NO_BREAK_SPACE = 0xFEFF;
    private static final int SOFT_HYPHEN = 0x00AD;

    /**
     * What the words of an {@link IDNAttackDatabase} constant name claim about its payload. The
     * brand a payload imitates is a label: it names the target, not a property of the payload.
     */
    private static final AttackNameClaims NAME_CLAIMS = new AttackNameClaims(
            Map.ofEntries(
                    claim("CYRILLIC", hostLetter(letter -> scriptOf(letter) == CYRILLIC)),
                    claim("HOMOGRAPH", hostLetter(letter -> scriptOf(letter) != LATIN)),
                    claim("MIXED_SCRIPT", payload -> letterScriptsOf(host(payload)).size() > 1),
                    claim("PUNYCODE", IDNAttackDatabaseTest::carriesDecodablePunycodeLabel),
                    claim("RUSSIAN", decodedHostLetter(letter -> scriptOf(letter) == CYRILLIC)),
                    claim("CHINESE", decodedHostLetter(letter -> scriptOf(letter) == HAN)),
                    claim("ONE_DOT_LEADER", codePoint(ONE_DOT_LEADER)),
                    claim("HYPHENATION_POINT", codePoint(HYPHENATION_POINT)),
                    claim("COMPOSED_ACCENT", codePoint(LATIN_SMALL_E_WITH_ACUTE)),
                    claim("DECOMPOSED_ACCENT", codePoint(COMBINING_ACUTE_ACCENT)),
                    claim("RTL", codePoint(RIGHT_TO_LEFT_OVERRIDE)),
                    claim("RTL_OVERRIDE", codePoint(RIGHT_TO_LEFT_OVERRIDE)),
                    claim("RTL_MIDDLE", IDNAttackDatabaseTest::carriesOverrideInsideHost),
                    claim("ZERO_WIDTH", codePoint(ZERO_WIDTH_SPACE, ZERO_WIDTH_NON_JOINER,
                            ZERO_WIDTH_JOINER, ZERO_WIDTH_NO_BREAK_SPACE)),
                    claim("ZERO_WIDTH_SPACE", codePoint(ZERO_WIDTH_SPACE)),
                    claim("ZERO_WIDTH_NON_JOINER", codePoint(ZERO_WIDTH_NON_JOINER)),
                    claim("SOFT_HYPHEN", codePoint(SOFT_HYPHEN)),
                    claim("FULL_WIDTH", hostLetter(letter -> letter >= 0xFF01 && letter <= 0xFF5E)),
                    claim("MATHEMATICAL_BOLD", hostLetter(namedLike("MATHEMATICAL", "BOLD"))),
                    claim("MATHEMATICAL_ITALIC", hostLetter(namedLike("MATHEMATICAL", "ITALIC"))),
                    claim("PORT", pattern("^[a-z]+://[^/]+:\\d+/")),
                    claim("HTTPS", pattern("^https://")),
                    claim("TRAVERSAL", TRAVERSAL)),
            Set.of("APPLE", "GOOGLE", "MICROSOFT", "PAYPAL", "TWITTER", "INSTAGRAM", "CAFE", "ATTACK", "DOMAIN"));

    private URLPathValidationPipeline pipeline;
    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        SecurityConfiguration config = SecurityConfiguration.builder()
                .allowExtendedAscii(true)  // Allow Unicode/IDN characters
                .failOnSuspiciousPatterns(true)  // Enable suspicious pattern detection
                .build();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    /**
     * Parameterized test that validates all IDN attack patterns from the database.
     * Each test case includes comprehensive documentation and expected failure types.
     *
     * @param testCase AttackTestCase containing IDN attack, expected failure type, and documentation
     */
    @ParameterizedTest
    @ArgumentsSource(IDNAttackDatabase.ArgumentsProvider.class)
    @DisplayName("IDN attack patterns should be rejected with correct failure types")
    void shouldRejectIDNAttacksWithCorrectFailureTypes(AttackTestCase testCase) {
        // Given: An IDN attack test case with expected failure type
        long initialEventCount = eventCounter.getTotalCount();

        // When: Attempting to validate the malicious IDN pattern
        String attackString = testCase.attackString();
        String attackRejectionMessage = "IDN attack should be rejected: %s%nAttack Description: %s%nDetection Rationale: %s".formatted(
                attackString, testCase.attackDescription(), testCase.detectionRationale());
        var exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(attackString),
                attackRejectionMessage);

        // Then: The validation should fail with the expected security failure type
        String failureTypeMessage = "Expected failure type %s for IDN attack: %s%nRationale: %s".formatted(
                testCase.expectedFailureType(), attackString, testCase.detectionRationale());
        assertEquals(testCase.expectedFailureType(), exception.getFailureType(), failureTypeMessage);

        // And: Original malicious input should be preserved
        assertEquals(attackString, exception.getOriginalInput(),
                "Original IDN attack string should be preserved in exception");

        // And: Security event should be recorded
        assertTrue(eventCounter.getTotalCount() > initialEventCount,
                "Security event should be recorded for IDN attack: %s".formatted(testCase.getCompactSummary()));
    }

    /**
     * Structural claim of the database (ADR-0009): the payload of every entry carries the
     * feature its constant name claims - the script, the punycode label, the specific invisible
     * or lookalike code point. An entry whose payload is edited to drop that feature fails here,
     * whatever the pipeline verdict is.
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
        return AttackDatabaseEntries.declaredEntries(IDNAttackDatabase.class);
    }

    /** The authority's host: everything between the scheme separator and the path, without a port. */
    private static String host(String payload) {
        String afterScheme = payload.substring(payload.indexOf("://") + 3);
        String authority = afterScheme.substring(0, afterScheme.indexOf('/'));
        return authority.replaceFirst(":\\d+$", "");
    }

    private static Character.UnicodeScript scriptOf(int codePoint) {
        return Character.UnicodeScript.of(codePoint);
    }

    private static Set<Character.UnicodeScript> letterScriptsOf(String text) {
        return Set.copyOf(text.codePoints().filter(Character::isLetter)
                .mapToObj(IDNAttackDatabaseTest::scriptOf).toList());
    }

    private static Predicate<String> hostLetter(IntPredicate property) {
        return payload -> host(payload).codePoints().filter(Character::isLetter).anyMatch(property);
    }

    /** Applies {@code property} to the letters of the host after its punycode labels are decoded. */
    private static Predicate<String> decodedHostLetter(IntPredicate property) {
        return payload -> IDN.toUnicode(host(payload)).codePoints().filter(Character::isLetter).anyMatch(property);
    }

    private static IntPredicate namedLike(String... nameParts) {
        return codePoint -> {
            String name = Character.getName(codePoint);
            return name != null && Stream.of(nameParts).allMatch(name::contains);
        };
    }

    /** A punycode label is an {@code xn--} label that decodes to something other than itself. */
    private static boolean carriesDecodablePunycodeLabel(String payload) {
        String host = host(payload);
        return literal("xn--").test(host) && !IDN.toUnicode(host).equals(host);
    }

    private static boolean carriesOverrideInsideHost(String payload) {
        String host = host(payload);
        int position = host.indexOf(RIGHT_TO_LEFT_OVERRIDE);
        return position > 0 && position < host.length() - 1;
    }
}
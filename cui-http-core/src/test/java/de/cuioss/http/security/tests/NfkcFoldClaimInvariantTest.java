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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.Normalizer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable registry of every "this code point NFKC-folds to X" claim made in the security test
 * tree.
 *
 * <p><strong>Why this test exists.</strong> A comment asserting that a homoglyph folds to an ASCII
 * character is a claim about {@link Normalizer} behaviour that nothing checks. The claim has been
 * wrong in this tree more than once - U+2044 FRACTION SLASH and U+2215 DIVISION SLASH were each
 * glossed as folding to {@code '/'}, and neither does; both normalize to themselves. A homoglyph
 * that normalizes to itself silently turns a normalization attack into a value no normalizing
 * validator ever resolves to a traversal, so the payload stops testing what its comment says it
 * tests.</p>
 *
 * <p>This test turns each such comment into an assertion. Adding a fold claim anywhere in the
 * security test tree means adding a row here; a row whose code point does not actually fold to its
 * claimed target fails immediately, at the point the claim is made rather than in whatever distant
 * test the mistaken payload weakened.</p>
 *
 * <p>The {@code claimSource} column names where the claim is made, so a failing row points straight
 * at the comment to correct.</p>
 */
@DisplayName("NFKC fold-claim invariant")
class NfkcFoldClaimInvariantTest {

    /**
     * Positive rows: code points a comment in the security test tree claims fold to an ASCII
     * target under NFKC. Each row asserts BOTH that the fold produces the claimed target AND that
     * it actually changes the input - a "fold" to itself is the exact defect this registry guards.
     */
    static Stream<Arguments> claimedFolds() {
        return Stream.of(
                Arguments.of(0x2024, ".", "ONE DOT LEADER",
                        "UnicodeNormalizationAttackGenerator.createOverlongSequenceAttack; "
                                + "PathTraversalGenerator UNICODE_SIGNATURES"),
                Arguments.of(0xFF0E, ".", "FULLWIDTH FULL STOP",
                        "UnicodeNormalizationAttackTest 'Fullwidth ../' and 'Fullwidth dots'"),
                Arguments.of(0xFF0F, "/", "FULLWIDTH SOLIDUS",
                        "UnicodeNormalizationAttackTest 'Fullwidth solidus'; "
                                + "PathTraversalGenerator UNICODE_SIGNATURES; DecodingStage structural-fold check"),
                Arguments.of(0xFF3C, "\\", "FULLWIDTH REVERSE SOLIDUS",
                        "UnicodeNormalizationAttackTest 'Fullwidth ..\\'; "
                                + "PathTraversalGenerator UNICODE_SIGNATURES"),
                Arguments.of(0xFF1C, "<", "FULLWIDTH LESS-THAN SIGN",
                        "UnicodeNormalizationAttackTest 'Fullwidth <script>'"),
                Arguments.of(0xFF53, "s", "FULLWIDTH LATIN SMALL LETTER S",
                        "UnicodeNormalizationAttackTest 'Fullwidth <script>'"));
    }

    /**
     * Negative rows: code points that are visually confusable with an ASCII separator but which
     * NFKC does NOT fold - they normalize to themselves. These rows exist so the distinction stays
     * asserted rather than resting on a comment, because it is precisely the distinction that was
     * gotten wrong.
     */
    static Stream<Arguments> claimedNonFolds() {
        return Stream.of(
                Arguments.of(0x2044, "FRACTION SLASH",
                        "confusable with '/', but NFKC-invariant"),
                Arguments.of(0x2215, "DIVISION SLASH",
                        "confusable with '/', but NFKC-invariant"),
                Arguments.of(0x2216, "SET MINUS",
                        "confusable with '\\', but NFKC-invariant"));
    }

    @ParameterizedTest(name = "U+{0} {2} folds to {1}")
    @MethodSource("claimedFolds")
    @DisplayName("Each claimed fold actually folds to its claimed target under NFKC")
    void shouldFoldToClaimedTarget(int codePoint, String claimedTarget, String characterName,
            String claimSource) {
        String input = new String(Character.toChars(codePoint));

        String folded = Normalizer.normalize(input, Normalizer.Form.NFKC);

        assertAll("U+%04X %s, claimed by: %s".formatted(codePoint, characterName, claimSource),
                () -> assertEquals(claimedTarget, folded,
                        "U+%04X %s is claimed to NFKC-fold to '%s' but folds to '%s'"
                                .formatted(codePoint, characterName, claimedTarget, folded)),
                () -> assertNotEquals(input, folded,
                        "U+%04X %s is claimed to fold but normalizes to itself, so the payload that "
                                + "relies on it never resolves to the ASCII form it claims to encode"
                                .formatted(codePoint, characterName)));
    }

    @ParameterizedTest(name = "U+{0} {1} is NFKC-invariant")
    @MethodSource("claimedNonFolds")
    @DisplayName("Each confusable-but-non-folding code point normalizes to itself under NFKC")
    void shouldNotFold(int codePoint, String characterName, String rationale) {
        String input = new String(Character.toChars(codePoint));

        String folded = Normalizer.normalize(input, Normalizer.Form.NFKC);

        assertEquals(input, folded,
                "U+%04X %s (%s) is documented as NFKC-invariant, but it folded to '%s'. If Unicode "
                        + "changed, the comments describing it as non-folding must be revisited."
                        .formatted(codePoint, characterName, rationale, folded));
    }
}

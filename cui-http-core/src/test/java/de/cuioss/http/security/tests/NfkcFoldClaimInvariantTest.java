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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable registry of every "this code point folds to X under Unicode normalization" claim made
 * in the {@code cui-http-core} sources.
 *
 * <p><strong>Why this test exists.</strong> A comment asserting that a homoglyph folds to an ASCII
 * character is a claim about {@link Normalizer} behaviour that nothing checks. The claim has been
 * wrong in this tree more than once - U+2044 FRACTION SLASH and U+2215 DIVISION SLASH were each
 * glossed as folding to {@code '/'}, and neither does; both normalize to themselves. A homoglyph
 * that normalizes to itself silently turns a normalization attack into a value no normalizing
 * validator ever resolves to a traversal, so the payload stops testing what its comment says it
 * tests.</p>
 *
 * <p><strong>One structured source.</strong> {@link #CLAIMS} is the single table of claims. Every
 * assertion in this class is derived from it: the positive rows ({@link FoldClaim#folds()}) assert
 * that the input folds to its claimed result and actually changes, the negative rows assert that it
 * normalizes to itself.</p>
 *
 * <p><strong>Completeness is checked, not trusted.</strong>
 * {@link #shouldRegisterEveryFoldClaimMadeInSources()} scans {@code src/main/java} and
 * {@code src/test/java} for fold claims: a comment paragraph (or a single code line) that mentions
 * folding or a normalization form ({@link #CLAIM_CONTEXT}) together with a code point written as
 * {@code U+XXXX} or as a Java unicode escape ({@link #CODE_POINT}). Every code point found in such
 * a context must be covered by a row of {@link #CLAIMS} - as part of the row's input or of its
 * claimed result; a claim made in a comment or Javadoc without a row fails the build. The detection
 * is deliberately broad - registering a code point that is merely mentioned near a fold claim costs
 * one row, while a missed claim is the defect this registry exists to prevent.</p>
 *
 * <p>The {@code claimSource} column names where the claim is made, so a failing row points straight
 * at the comment to correct.</p>
 *
 * @see <a href="../../../../../../../../../doc/adr/0010-NFKC-fold_claims_centralized_in_one_executable_invariant_registry.adoc">ADR-0010</a>
 */
@DisplayName("NFKC fold-claim invariant")
class NfkcFoldClaimInvariantTest {

    /**
     * A claim that {@code input} normalizes, under {@code form}, to {@code claimedResult}; a
     * {@code null} result is the negative claim that the input normalizes to itself. The input is
     * usually one code point, or a base character followed by combining marks for a composition
     * claim.
     */
    record FoldClaim(String input, Normalizer.Form form, @Nullable String claimedResult, String description,
    String claimSource) {

        boolean folds() {
            return claimedResult != null;
        }

        /** Every code point the claim speaks about: those of its input and of its claimed result. */
        Set<Integer> coveredCodePoints() {
            Set<Integer> covered = new TreeSet<>();
            input.codePoints().forEach(covered::add);
            if (claimedResult != null) {
                claimedResult.codePoints().forEach(covered::add);
            }
            return covered;
        }

        @Override
        public String toString() {
            return "%s %s (%s)".formatted(spell(input), form, description);
        }
    }

    /** The single source of truth: every fold claim made in the module's sources. */
    static final List<FoldClaim> CLAIMS = List.of(
            new FoldClaim(chars(0x2024), Normalizer.Form.NFKC, ".", "ONE DOT LEADER",
                    "UnicodeNormalizationAttackGenerator.createOverlongSequenceAttack; "
                            + "PathTraversalGenerator UNICODE_SIGNATURES; UnicodeNormalizationAttackTest"),
            new FoldClaim(chars(0xFF0E), Normalizer.Form.NFKC, ".", "FULLWIDTH FULL STOP",
                    "UnicodeNormalizationAttackTest 'Fullwidth ../' and 'Fullwidth dots'; "
                            + "UnicodeNormalizationAttackGenerator"),
            new FoldClaim(chars(0xFF0F), Normalizer.Form.NFKC, "/", "FULLWIDTH SOLIDUS",
                    "UnicodeNormalizationAttackTest 'Fullwidth solidus'; PathTraversalGenerator "
                            + "UNICODE_SIGNATURES; DecodingStage structural-fold check; DecodingStageTest"),
            new FoldClaim(chars(0xFF3C), Normalizer.Form.NFKC, "\\", "FULLWIDTH REVERSE SOLIDUS",
                    "UnicodeNormalizationAttackTest 'Fullwidth ..\\'; "
                            + "PathTraversalGenerator UNICODE_SIGNATURES"),
            new FoldClaim(chars(0xFF1C), Normalizer.Form.NFKC, "<", "FULLWIDTH LESS-THAN SIGN",
                    "UnicodeNormalizationAttackTest 'Fullwidth <script>'"),
            new FoldClaim(chars(0xFF53), Normalizer.Form.NFKC, "s", "FULLWIDTH LATIN SMALL LETTER S",
                    "UnicodeNormalizationAttackTest 'Fullwidth <script>'"),
            new FoldClaim(chars(0xFF41), Normalizer.Form.NFKC, "a", "FULLWIDTH LATIN SMALL LETTER A",
                    "EdgeCaseValidURLsDatabase F-05 normalize-and-continue cases"),
            new FoldClaim(chars(0xFF12), Normalizer.Form.NFKC, "2", "FULLWIDTH DIGIT TWO",
                    "DecodingStage step 4 fold-assembled escape; DoubleEncodingPresetParityTest; DecodingStageTest"),
            new FoldClaim(chars(0xFF26), Normalizer.Form.NFKC, "F", "FULLWIDTH LATIN CAPITAL LETTER F",
                    "DecodingStage step 4 fold-assembled escape; DoubleEncodingPresetParityTest; DecodingStageTest"),
            new FoldClaim(chars(0x2162), Normalizer.Form.NFKC, "III", "ROMAN NUMERAL THREE",
                    "EdgeCaseValidURLsDatabase 'Percent-encoded Roman numeral three'"),
            new FoldClaim(chars(0x037E), Normalizer.Form.NFC, ";", "GREEK QUESTION MARK",
                    "DecodingStageTest NFC fold of %CD%BE"),
            new FoldClaim(chars(0x212B), Normalizer.Form.NFC, chars(0x00C5), "ANGSTROM SIGN",
                    "DecodingStageTest NFC-changes-the-decoded-form case"),
            new FoldClaim("e" + chars(0x0300), Normalizer.Form.NFC, chars(0x00E8),
                    "e + COMBINING GRAVE ACCENT composes",
                    "UnicodeNormalizationAttackTest normalization-changing patterns and forms"),
            new FoldClaim("n" + chars(0x0301), Normalizer.Form.NFC, chars(0x0144),
                    "n + COMBINING ACUTE ACCENT composes",
                    "UnicodeNormalizationAttackTest normalization-changing forms"),
            new FoldClaim("." + chars(0x0301), Normalizer.Form.NFKC, null,
                    ". + COMBINING ACUTE ACCENT has no precomposed form",
                    "UnicodeNormalizationAttackTest normalization-changing patterns and forms"),
            new FoldClaim(chars(0x2044), Normalizer.Form.NFKC, null, "FRACTION SLASH",
                    "confusable with '/', but NFKC-invariant; UnicodeNormalizationAttackTest"),
            new FoldClaim(chars(0x2215), Normalizer.Form.NFKC, null, "DIVISION SLASH",
                    "confusable with '/', but NFKC-invariant; UnicodeNormalizationAttackTest"),
            new FoldClaim(chars(0x2216), Normalizer.Form.NFKC, null, "SET MINUS",
                    "confusable with '\\', but NFKC-invariant; UnicodeNormalizationAttackGenerator"));

    /** Words that make a comment paragraph or code line a normalization / fold claim context. */
    static final Pattern CLAIM_CONTEXT = Pattern.compile("(?i)fold|\\bNFKC\\b|\\bNFC\\b|NFKC-|NFC-");

    /** A code point written as {@code U+XXXX} or as a Java unicode escape in source text. */
    static final Pattern CODE_POINT = Pattern.compile("U\\+([0-9A-Fa-f]{4,6})|\\\\u([0-9A-Fa-f]{4})");

    private static final String THIS_FILE = NfkcFoldClaimInvariantTest.class.getSimpleName() + ".java";

    static Stream<Arguments> claimedFolds() {
        return CLAIMS.stream().filter(FoldClaim::folds).map(Arguments::of);
    }

    static Stream<Arguments> claimedNonFolds() {
        return CLAIMS.stream().filter(claim -> !claim.folds()).map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("claimedFolds")
    @DisplayName("Each claimed fold actually folds to its claimed result")
    void shouldFoldToClaimedTarget(FoldClaim claim) {
        String folded = Normalizer.normalize(claim.input(), claim.form());

        assertAll("%s, claimed by: %s".formatted(claim, claim.claimSource()),
                () -> assertEquals(claim.claimedResult(), folded,
                        "%s is claimed to fold to %s but folds to %s"
                                .formatted(claim, spell(claim.claimedResult()), spell(folded))),
                () -> assertNotEquals(claim.input(), folded,
                        ("%s is claimed to fold but normalizes to itself, so the payload that "
                                + "relies on it never resolves to the form it claims to encode").formatted(claim)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("claimedNonFolds")
    @DisplayName("Each confusable-but-non-folding input normalizes to itself")
    void shouldNotFold(FoldClaim claim) {
        String folded = Normalizer.normalize(claim.input(), claim.form());

        assertEquals(claim.input(), folded,
                ("%s, claimed by %s, is documented as invariant, but it folded to %s. If Unicode "
                        + "changed, the comments describing it as non-folding must be revisited.")
                        .formatted(claim, claim.claimSource(), spell(folded)));
    }

    @Test
    @DisplayName("The claims table has exactly one row per input and form")
    void shouldRegisterEachClaimOnce() {
        Set<String> distinct = CLAIMS.stream().map(claim -> claim.input() + '|' + claim.form())
                .collect(Collectors.toSet());

        assertEquals(CLAIMS.size(), distinct.size(), "A claim is registered more than once in CLAIMS");
    }

    @Test
    @DisplayName("Every fold claim made in the module's sources is a row of the claims table")
    void shouldRegisterEveryFoldClaimMadeInSources() throws Exception {
        Set<Integer> registered = CLAIMS.stream().flatMap(claim -> claim.coveredCodePoints().stream())
                .collect(Collectors.toSet());
        Path moduleRoot = locateModuleRoot();
        List<String> unregistered = new ArrayList<>();
        int claimContexts = 0;

        for (Path sourceRoot : List.of(moduleRoot.resolve("src/main/java"), moduleRoot.resolve("src/test/java"))) {
            List<Path> sources;
            try (Stream<Path> walk = Files.walk(sourceRoot)) {
                sources = walk.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !THIS_FILE.equals(path.getFileName().toString()))
                        .sorted()
                        .toList();
            }
            for (Path source : sources) {
                for (ClaimContext context : claimContexts(Files.readAllLines(source))) {
                    claimContexts++;
                    for (int codePoint : codePointsIn(context.text())) {
                        if (!registered.contains(codePoint)) {
                            unregistered.add("U+%04X at %s:%d".formatted(codePoint,
                                    moduleRoot.relativize(source), context.line()));
                        }
                    }
                }
            }
        }

        assertTrue(claimContexts > 0, "The source scan found no fold-claim context at all - the scan is broken");
        assertEquals(List.of(), unregistered,
                "Fold claims made in sources without a row in NfkcFoldClaimInvariantTest.CLAIMS - add a row "
                        + "asserting the claim, or correct the comment");
    }

    /** A candidate claim: a comment paragraph or a single code line, with its first line number. */
    record ClaimContext(int line, String text) {
    }

    /**
     * Splits a source file into comment paragraphs (consecutive comment lines, broken at blank
     * comment lines, {@code <p>} and Javadoc block tags) and single code lines, keeping those that
     * read as a fold claim.
     */
    static List<ClaimContext> claimContexts(List<String> lines) {
        List<ClaimContext> contexts = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int paragraphStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            boolean commentLine = trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
            String content = trimmed.replaceFirst("^(/\\*\\*|/\\*|\\*/|//|\\*)", "").trim();
            if (commentLine && !content.isEmpty() && !"<p>".equals(content)) {
                if (content.startsWith("@") || content.startsWith("<p>")) {
                    addIfClaim(contexts, paragraphStart, paragraph);
                    paragraph.setLength(0);
                    paragraphStart = -1;
                }
                if (paragraphStart < 0) {
                    paragraphStart = i + 1;
                }
                paragraph.append(content).append(' ');
                continue;
            }
            addIfClaim(contexts, paragraphStart, paragraph);
            paragraph.setLength(0);
            paragraphStart = -1;
            if (!commentLine) {
                addIfClaim(contexts, i + 1, trimmed);
            }
        }
        addIfClaim(contexts, paragraphStart, paragraph);
        return contexts;
    }

    private static void addIfClaim(List<ClaimContext> contexts, int line, CharSequence text) {
        if (line > 0 && CLAIM_CONTEXT.matcher(text).find() && CODE_POINT.matcher(text).find()) {
            contexts.add(new ClaimContext(line, text.toString()));
        }
    }

    static SortedSet<Integer> codePointsIn(String text) {
        SortedSet<Integer> codePoints = new TreeSet<>();
        Matcher matcher = CODE_POINT.matcher(text);
        while (matcher.find()) {
            String hex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            codePoints.add(parseCodePoint(hex, text));
        }
        return codePoints;
    }

    /**
     * Parses one hex code point captured from a claim. A hex run too long for an {@code int}, or a
     * value outside the Unicode code-point range, fails the test naming the offending claim text
     * instead of escaping as a raw {@link NumberFormatException} or yielding a non-code-point.
     */
    private static int parseCodePoint(String hex, String claimText) {
        int codePoint;
        try {
            codePoint = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return fail("Code point U+%s in claim \"%s\" is not a parsable hex int: %s"
                    .formatted(hex, claimText, e.getMessage()), e);
        }
        assertTrue(Character.isValidCodePoint(codePoint),
                "Code point U+%s in claim \"%s\" is not a valid Unicode code point".formatted(hex, claimText));
        return codePoint;
    }

    private static String chars(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private static String spell(@Nullable String text) {
        if (text == null) {
            return "itself";
        }
        return text.codePoints().mapToObj(cp -> "U+%04X".formatted(cp)).collect(Collectors.joining(" "));
    }

    private static Path locateModuleRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/test/java/de/cuioss/http/security/tests"))) {
                return current;
            }
            Path module = current.resolve("cui-http-core");
            if (Files.isDirectory(module.resolve("src/test/java/de/cuioss/http/security/tests"))) {
                return module;
            }
            current = current.getParent();
        }
        return fail("Could not locate the cui-http-core module root from " + Path.of("").toAbsolutePath());
    }
}

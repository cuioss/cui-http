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

import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural-claim vocabulary of one attack database: which words of a constant name claim a
 * feature of the payload, and how that feature is recognized in the payload itself.
 *
 * <p>A constant name is read as underscore-separated words. Every word must be accounted for in
 * exactly one of three ways:</p>
 * <ul>
 *   <li>it belongs to a <strong>claim</strong> - a word or word sequence such as
 *       {@code NULL_BYTE} that is paired with a predicate over the payload, and the predicate
 *       must hold;</li>
 *   <li>it is a declared <strong>label</strong> - a word that says where the entry comes from or
 *       what it targets (a product, a rule set, a brand) and asserts nothing about the payload's
 *       shape;</li>
 *   <li>it is a number, which is part of an identifier such as a CVE or rule number.</li>
 * </ul>
 *
 * <p>The check fails closed in both directions: a word that is neither claim, label nor number
 * fails the entry, and so does a name made of labels alone, because nothing about its payload
 * would be verified. Adding an entry therefore forces a decision about what its name asserts.</p>
 *
 * <p>The predicates read the payload only and never consult a validation pipeline, so the claim
 * holds whichever stage a pipeline happens to reject the payload at.</p>
 */
final class AttackNameClaims {

    /**
     * A parent reference in any spelling the databases use: two dots, each raw, percent-encoded,
     * double- or triple-encoded, overlong-UTF-8-encoded or nested-encoded, followed by a
     * separator in a raw or encoded spelling.
     */
    static final Predicate<String> TRAVERSAL = pattern(
            "(?i)(?:\\.|%2e|%252e|%25252e|%c0%ae|%%32%65){2}"
                    + "(?:/|\\\\|%2f|%5c|%252f|%255c|%25252f|%c0%af)");

    private final Map<String, Predicate<String>> claims;
    private final Set<String> labels;

    /**
     * @param claims claim word sequence (underscore-separated, as it appears in a constant name)
     *               to the predicate its payload must satisfy
     * @param labels words that assert nothing about a payload
     */
    AttackNameClaims(Map<String, Predicate<String>> claims, Set<String> labels) {
        this.claims = Map.copyOf(claims);
        this.labels = Set.copyOf(labels);
    }

    /**
     * Asserts that {@code payload} carries every feature {@code constantName} claims, that the
     * name claims at least one, and that no word of the name is left unaccounted for.
     *
     * @param constantName the name of the database constant
     * @param payload the attack string of that constant
     */
    void assertCarriedBy(String constantName, String payload) {
        List<String> words = List.of(constantName.split("_"));
        boolean[] accounted = new boolean[words.size()];
        List<Executable> checks = new ArrayList<>();

        claims.forEach((claim, feature) -> {
            List<String> claimWords = List.of(claim.split("_"));
            int start = Collections.indexOfSubList(words, claimWords);
            if (start < 0) {
                return;
            }
            Arrays.fill(accounted, start, start + claimWords.size(), true);
            checks.add(() -> assertTrue(feature.test(payload),
                    () -> "%s claims %s, but its payload does not carry that feature: <%s>"
                            .formatted(constantName, claim, describe(payload))));
        });

        int verifiedClaims = checks.size();
        List<String> unaccounted = IntStream.range(0, words.size())
                .filter(index -> !accounted[index] && !isLabelOrNumber(words.get(index)))
                .mapToObj(words::get)
                .toList();

        checks.add(() -> assertTrue(verifiedClaims > 0,
                () -> "%s consists of labels only, so nothing about its payload is verified - "
                        .formatted(constantName) + "name the feature the payload carries"));
        checks.add(() -> assertEquals(List.of(), unaccounted,
                () -> "%s contains words that are neither a verified claim nor a declared label - "
                        .formatted(constantName) + "add a claim, declare a label or rename the entry"));
        assertAll(constantName, checks);
    }

    /**
     * Pairs a claim with its feature. Exists so that a lambda or method reference passed as
     * {@code feature} has an explicit target type inside {@code Map.ofEntries(...)}.
     *
     * @param words the claim word sequence, underscore-separated
     * @param feature the predicate the payload must satisfy
     * @return the map entry
     */
    static Map.Entry<String, Predicate<String>> claim(String words, Predicate<String> feature) {
        return Map.entry(words, feature);
    }

    /**
     * @param anyOf literals, compared ignoring ASCII letter case
     * @return a predicate that holds when the payload contains at least one of the literals
     */
    static Predicate<String> literal(String... anyOf) {
        List<String> lowerCased = Arrays.stream(anyOf).map(value -> value.toLowerCase(Locale.ROOT)).toList();
        return payload -> {
            String lowerCasedPayload = payload.toLowerCase(Locale.ROOT);
            return lowerCased.stream().anyMatch(lowerCasedPayload::contains);
        };
    }

    /**
     * @param regex a regular expression
     * @return a predicate that holds when the expression matches anywhere in the payload
     */
    static Predicate<String> pattern(String regex) {
        return Pattern.compile(regex).asPredicate();
    }

    /**
     * @param anyOf code points
     * @return a predicate that holds when the payload contains at least one of the code points
     */
    static Predicate<String> codePoint(int... anyOf) {
        return payload -> payload.codePoints()
                .anyMatch(candidate -> Arrays.stream(anyOf).anyMatch(expected -> expected == candidate));
    }

    private boolean isLabelOrNumber(String word) {
        return labels.contains(word) || word.chars().allMatch(Character::isDigit);
    }

    /**
     * Renders a payload with every code point outside printable ASCII spelled out, so a failure
     * message stays readable when the distinguishing character is invisible.
     */
    private static String describe(String payload) {
        StringBuilder builder = new StringBuilder(payload.length());
        payload.codePoints().forEach(codePoint -> {
            if (codePoint >= 0x20 && codePoint < 0x7F) {
                builder.appendCodePoint(codePoint);
            } else {
                builder.append("{U+%04X}".formatted(codePoint));
            }
        });
        return builder.toString();
    }
}

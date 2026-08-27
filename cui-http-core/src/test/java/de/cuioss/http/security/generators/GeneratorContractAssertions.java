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
package de.cuioss.http.security.generators;

import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.exceptions.UrlSecurityException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared vocabularies and assertions for the generator contract tests.
 *
 * <p>Every generator contract test in {@code de.cuioss.http.security.generators} asserts a
 * <em>defining property</em> of the values its generator emits — the property that makes the
 * value an instance of the attack (or of the legitimate input) the generator advertises.
 * Those properties are expressed against a small number of marker vocabularies, and this class
 * is the single place they are declared, so that the vocabularies are not copy-pasted across
 * the contract test population.</p>
 *
 * <h3>Traceability</h3>
 *
 * <p>Every marker declared here is emitted by a concrete branch of a generator in this tree;
 * the vocabularies are closed sets, not speculative catalogues. In particular
 * {@link #TRAVERSAL_MARKERS} carries the two <em>literal escape-text</em> forms
 * &#92;u002e and &#92;ufe0e: {@code PathTraversalGenerator.generateUnicodeTraversal()} and
 * {@code generateAdvancedTraversal()} case 4 append the six-character escape <em>text</em> to
 * their output, not the decoded character, so a contract assertion must look for the text.</p>
 *
 * <h3>Pipeline round-trip</h3>
 *
 * <p>{@link #assertPipelineAccepts(HttpSecurityValidator, String)} and
 * {@link #assertPipelineRejects(HttpSecurityValidator, String)} express the stronger contract
 * available to generators whose every branch is unambiguously legitimate (accepts) or
 * unambiguously an attack (rejects). Generators that mix the two — or that emit escape text
 * rather than decoded characters — assert marker properties only.</p>
 *
 * @since 1.0
 */
public final class GeneratorContractAssertions {

    /**
     * Substrings that mark a value as a path-traversal payload, as actually emitted by the
     * path-traversal generators in this tree.
     */
    public static final Set<String> TRAVERSAL_MARKERS = Set.of(
            "../",
            "..\\",
            "%2e%2e",
            "%2E%2E",
            "%252e%252e",
            "%252E%252E",
            "..%2f",
            "..%2F",
            "..%5c",
            "..%5C",
            "....",
            "%c0%ae",
            "%C0%AE",
            "..%c0%af",
            "..%00",
            "\\u002e",
            "\\ufe0e");

    /**
     * Substrings that mark a value as a null-byte injection payload: the raw null byte and its
     * percent-encoded form.
     */
    public static final Set<String> NULL_BYTE_MARKERS = Set.of("\0", "%00");

    /**
     * Substrings that mark a value as a CRLF injection payload: the raw carriage return and line
     * feed, plus their percent-encoded forms in both letter cases.
     */
    public static final Set<String> CRLF_MARKERS = Set.of("\r", "\n", "%0d", "%0a", "%0D", "%0A");

    /**
     * Shell metacharacters used by the boundary-fuzzing contract to recognise command-injection
     * payloads.
     */
    public static final Set<String> SHELL_METACHARACTERS = Set.of("|", ";", "`", "$", ">");

    private GeneratorContractAssertions() {
        // utility class
    }

    /**
     * Determines whether the value carries a control character, that is any character in the
     * range U+0000 to U+001F.
     *
     * @param value the value to inspect, must not be null
     * @return {@code true} when at least one character of {@code value} is a control character
     */
    public static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(c -> c <= 0x1F);
    }

    /**
     * Truncates a generated value for inclusion in a failure message, so an overlong attack
     * payload does not flood the test report.
     *
     * @param value the value to preview, must not be null
     * @return {@code value} unchanged when its length is at most 80, otherwise its first 80
     *         characters followed by {@code "..."}
     */
    public static String preview(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80) + "...";
    }

    /**
     * Asserts that the value contains at least one of the given markers.
     *
     * @param value the generated value under test, must not be null
     * @param markers the closed set of markers, at least one of which must occur in {@code value}
     * @param description names the contract being asserted; it is prefixed to the failure message
     */
    public static void assertContainsAny(String value, Set<String> markers, String description) {
        boolean found = markers.stream().anyMatch(value::contains);
        assertTrue(found, () -> description + " — value contains none of the expected markers. Value: <"
                + preview(value) + ">, expected any of: " + markers);
    }

    /**
     * Asserts that the value contains none of the given markers — the negated counterpart of
     * {@link #assertContainsAny(String, Set, String)}, for the legitimacy contract that a
     * generated value carries no marker from a closed attack vocabulary.
     *
     * @param value the generated value under test, must not be null
     * @param markers the closed set of markers, none of which may occur in {@code value}
     * @param description names the contract being asserted; it is prefixed to the failure message
     */
    public static void assertContainsNone(String value, Set<String> markers, String description) {
        markers.stream()
                .filter(value::contains)
                .findFirst()
                .ifPresent(marker -> fail(description + " — value contains marker <" + marker
                        + ">. Value: <" + preview(value) + ">"));
    }

    /**
     * Asserts that the pipeline accepts the value, that is it neither throws nor discards it.
     *
     * @param pipeline the validation pipeline under test
     * @param value the generated value that the pipeline must accept
     */
    public static void assertPipelineAccepts(HttpSecurityValidator pipeline, String value) {
        Optional<String> result = assertDoesNotThrow(() -> pipeline.validate(value),
                () -> "Pipeline must accept the generated value. Value: <" + preview(value) + ">");
        assertTrue(result.isPresent(),
                () -> "Pipeline must return a value for the accepted input. Value: <" + preview(value) + ">");
    }

    /**
     * Asserts that the pipeline rejects the value with a {@link UrlSecurityException} that reports
     * the value as its original input.
     *
     * @param pipeline the validation pipeline under test
     * @param value the generated value that the pipeline must reject
     */
    public static void assertPipelineRejects(HttpSecurityValidator pipeline, String value) {
        UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate(value),
                () -> "Pipeline must reject the generated value. Value: <" + preview(value) + ">");
        assertEquals(value, exception.getOriginalInput(),
                "Rejection must report the generated value as its original input");
    }
}

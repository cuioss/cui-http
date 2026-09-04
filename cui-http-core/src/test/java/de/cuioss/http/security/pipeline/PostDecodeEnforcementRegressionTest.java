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
package de.cuioss.http.security.pipeline;

import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-cutting, pipeline-level regression tests for the four post-decode and preset
 * enforcement gaps corrected by this change.
 *
 * <p>Every assertion here goes through a {@link PipelineFactory}-built URL path pipeline rather
 * than through an individual stage, so each test reproduces the gap from a caller's angle: it
 * fails against the pre-fix code and passes after. The four behaviours pinned are:</p>
 * <ol>
 *   <li>a percent-encoded control character is rejected after decoding, and the guard is governed
 *       by {@code allowControlCharacters} rather than hard-coded (SV-3 / SV-5);</li>
 *   <li>the paranoid preset detects a mixed-case sensitive path, because it no longer compares
 *       case-sensitively against an all-lowercase pattern set (SV-4);</li>
 *   <li>every case permutation of the encoded double dot is detected as traversal (SV-10);</li>
 *   <li>URL path decoding follows RFC 3986 and preserves a literal {@code +} instead of rewriting
 *       it to a space with form semantics (SV-24).</li>
 * </ol>
 */
@DisplayName("Post-decode and preset enforcement regressions")
class PostDecodeEnforcementRegressionTest {

    private SecurityEventCounter eventCounter;

    @BeforeEach
    void setUp() {
        eventCounter = new SecurityEventCounter();
    }

    private HttpSecurityValidator pipeline(SecurityConfiguration config) {
        return PipelineFactory.createUrlPathPipeline(config, eventCounter);
    }

    @Nested
    @DisplayName("(1) SV-3/SV-5: percent-encoded control characters are rejected after decoding")
    class PostDecodeControlCharacters {

        @Test
        @DisplayName("SV-3/SV-5: /a%1B%08b is rejected under the default configuration")
        void rejectsEncodedControlCharactersByDefault() {
            HttpSecurityValidator pipeline = pipeline(SecurityConfiguration.defaults());

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate("/a%1B%08b"));

            assertAll("encoded control characters survive character validation and must be caught after decoding",
                    () -> assertEquals(UrlSecurityFailureType.CONTROL_CHARACTERS, exception.getFailureType()),
                    () -> assertEquals(ValidationType.URL_PATH, exception.getValidationType()),
                    () -> assertEquals("/a%1B%08b", exception.getOriginalInput()));
        }

        @Test
        @DisplayName("SV-3/SV-5: the same input is accepted when allowControlCharacters(true) is configured")
        void acceptsEncodedControlCharactersWhenConfigured() {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowControlCharacters(true)
                    .build();
            String expected = "/a" + (char) 0x1B + (char) 0x08 + "b";

            Optional<String> result = pipeline(config).validate("/a%1B%08b");

            assertEquals(expected, result.orElseThrow(),
                    "The guard must be configuration-governed, not hard-coded");
        }
    }

    @Nested
    @DisplayName("(2) SV-4: the paranoid preset is no longer case-weakened")
    class ParanoidPresetCaseSensitivity {

        @ParameterizedTest
        @DisplayName("SV-4: paranoid() rejects a sensitive path in either case")
        @ValueSource(strings = {"/ETC/passwd", "/etc/passwd"})
        void paranoidRejectsSensitivePathRegardlessOfCase(String path) {
            HttpSecurityValidator pipeline = pipeline(SecurityConfiguration.paranoid());

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate(path),
                    "paranoid() must detect '" + path + "' - SENSITIVE_PATH_PATTERNS is all-lowercase, "
                            + "so case-sensitive comparison would let the mixed-case spelling through");

            assertEquals(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED, exception.getFailureType());
        }

        /**
         * A percent-encoded backslash is <em>not</em> stopped by character validation:
         * {@code CharacterValidationStage} judges the wire form, where {@code %5C} is a well-formed
         * escape, and {@code DecodingStage} then yields a literal backslash without re-checking
         * RFC 3986 membership. The Windows literals therefore have to survive the re-tiering and be
         * matched in their backslash-delimited spelling, or a Windows filesystem path reaches the
         * application unexamined.
         */
        @ParameterizedTest
        @DisplayName("paranoid() rejects a Windows filesystem path smuggled in as %5C")
        @ValueSource(strings = {
                "/api/%5cwindows%5csystem32%5cconfig",
                "/%5cWindows%5cwin.ini",
                "%5cusers%5cadmin"})
        void paranoidRejectsPercentEncodedBackslashPath(String path) {
            HttpSecurityValidator pipeline = pipeline(SecurityConfiguration.paranoid());

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate(path),
                    "paranoid() must detect '" + path + "' - %5C survives wire-form character "
                            + "validation and decodes to a literal backslash, so dropping the "
                            + "Windows literals would let the whole path through unexamined");

            assertEquals(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED, exception.getFailureType());
        }

        @ParameterizedTest
        @DisplayName("paranoid() still accepts a slash-delimited segment sharing a Windows literal's name")
        @ValueSource(strings = {"/users/42/profile", "/api/v1/users", "/windows/tint-preview"})
        void paranoidAcceptsSlashDelimitedNamesakes(String path) {
            HttpSecurityValidator pipeline = pipeline(SecurityConfiguration.paranoid());

            assertDoesNotThrow(() -> pipeline.validate(path),
                    "the Windows literals are backslash-delimited, so '" + path + "' - an ordinary "
                            + "REST route - must not be caught by them");
        }
    }

    @Nested
    @DisplayName("(3) SV-10: encoded double dots are detected in every hex-digit case")
    class EncodedDoubleDotCasePermutations {

        /**
         * The encoded double dot must be double-encoded to reach the fixed check through the
         * pipeline. {@code DecodingStage} runs before {@code NormalizationStage}, so a singly
         * encoded {@code /a%2e%2eb/x} arrives at the normalization stage already decoded to
         * {@code /a..b/x} - a legitimate filename, correctly accepted, and no test of this fix.
         * Permitting double encoding lets {@code %252e%252e} decode once to {@code %2e%2e}, which
         * is exactly the form {@code containsDirectoryTraversalIntent} inspects.
         *
         * <p>The trailing {@code b} is deliberate: it keeps the input clear of the
         * {@code PATH_TRAVERSAL_PATTERNS} entries that end in a separator, so the rejection comes
         * from the case-folded encoded-dot check under test rather than from the pre-decode
         * pattern stage. The two homogeneous spellings were detected before this change and serve
         * as positive controls; the two mixed-case spellings are the bypass being closed.</p>
         */
        @ParameterizedTest
        @DisplayName("SV-10: encoded double dot is traversal in any case permutation")
        @ValueSource(strings = {
                "/a/%252e%252eb/x",  // positive control - all lowercase
                "/a/%252E%252Eb/x",  // positive control - all uppercase
                "/a/%252E%252eb/x",  // the bypass - upper then lower
                "/a/%252e%252Eb/x"   // the bypass - lower then upper
        })
        void detectsEncodedDoubleDotInAnyCase(String path) {
            SecurityConfiguration config = SecurityConfiguration.builder()
                    .allowDoubleEncoding(true)
                    .build();
            HttpSecurityValidator pipeline = pipeline(config);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class,
                    () -> pipeline.validate(path),
                    "Percent-encoding hex digits are case-insensitive, so '" + path
                            + "' denotes the same traversal sequence");

            assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, exception.getFailureType());
        }
    }

    @Nested
    @DisplayName("(4) SV-24: URL path decoding preserves a literal plus")
    class PathPlusSemantics {

        @Test
        @DisplayName("SV-24: /a+b is accepted and returned verbatim")
        void preservesLiteralPlusInPath() {
            Optional<String> result =
                    pipeline(SecurityConfiguration.defaults()).validate("/a+b");

            assertEquals("/a+b", result.orElseThrow(),
                    "RFC 3986 treats + as an ordinary path character; form semantics do not apply to a path");
        }

        @Test
        @DisplayName("SV-24: encoded-plus control - /search/c%2B%2B is returned as /search/c++")
        void decodesEncodedPlusInPath() {
            Optional<String> result =
                    pipeline(SecurityConfiguration.defaults()).validate("/search/c%2B%2B");

            assertEquals("/search/c++", result.orElseThrow(),
                    "%2B still decodes to + regardless of the literal-plus handling");
        }
    }

    /**
     * Characterization test for a deliberate scope boundary, not an oversight.
     *
     * <p>{@code /%3Cscript%3E} decodes to {@code /<script>} and is accepted: this library validates
     * HTTP structure, and {@code <} and {@code >} carry no structural meaning in a URL path. Escaping
     * decoded content for an HTML or JavaScript context is an application-layer responsibility,
     * because only the application knows which sink the value flows into and therefore which
     * escaping applies. Rejecting it here would be a guess about a downstream context this layer
     * cannot see.</p>
     */
    @Test
    @DisplayName("scope boundary: /%3Cscript%3E is accepted - HTML escaping is an application-layer concern")
    void acceptsDecodedAngleBracketsAsApplicationLayerConcern() {
        Optional<String> result =
                pipeline(SecurityConfiguration.defaults()).validate("/%3Cscript%3E");

        assertEquals("/<script>", result.orElseThrow(),
                "Accepted by design - see this test's Javadoc for the scope boundary");
    }
}

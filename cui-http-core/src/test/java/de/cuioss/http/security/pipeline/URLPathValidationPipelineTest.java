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
import de.cuioss.http.security.core.UrlSecurityFailureType;
import de.cuioss.http.security.core.ValidationType;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.generators.encoding.EncodingCombinationGenerator;
import de.cuioss.http.security.generators.encoding.PathTraversalGenerator;
import de.cuioss.http.security.generators.encoding.UnicodeAttackGenerator;
import de.cuioss.http.security.generators.url.NullByteURLGenerator;
import de.cuioss.http.security.generators.url.ValidURLPathGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@EnableGeneratorController
class URLPathValidationPipelineTest {

    /** RFC 3986 unreserved characters and the path separator: no escape, no backslash, no Unicode. */
    private static final Pattern UNRESERVED_AND_SLASH = Pattern.compile("[A-Za-z0-9._~/-]*");

    /** The number of raw traversals {@code shouldRejectPathTraversal} asserts on. */
    private static final int TRAVERSAL_SAMPLES = 5;

    /**
     * The most draws {@code shouldRejectPathTraversal} takes, so a generator that stops emitting raw
     * traversals fails the test instead of hanging it. About one draw in ten qualifies, so the fifth
     * sample is expected within roughly fifty draws.
     */
    private static final int TRAVERSAL_DRAW_CAP = 2000;

    private SecurityConfiguration config;
    private SecurityEventCounter eventCounter;
    private URLPathValidationPipeline pipeline;

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLPathValidationPipeline(config, eventCounter);
    }

    @Nested
    class PipelineCreation {

        @Test
        void shouldCreatePipelineWithValidParameters() {
            assertEquals(ValidationType.URL_PATH, pipeline.getValidationType());
            assertEquals(6, pipeline.getStages().size());
            assertSame(eventCounter, pipeline.getEventCounter());
        }

        @Test
        void shouldRejectNullConfig() {
            assertThrows(NullPointerException.class, () ->
                    new URLPathValidationPipeline(null, eventCounter));
        }

        @Test
        void shouldRejectNullEventCounter() {
            assertThrows(NullPointerException.class, () ->
                    new URLPathValidationPipeline(config, null));
        }
    }

    @Nested
    class ValidInputHandling {

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidURLPathGenerator.class, count = 10)
        void shouldValidateValidPaths(String validPath) throws Exception {
            Optional<String> result = pipeline.validate(validPath);
            assertTrue(result.isPresent());
            // ValidURLPathGenerator emits plain canonical ASCII paths - no percent-encoding, no
            // dot segments, no non-ASCII - so no stage may rewrite them and the pipeline must
            // return the input verbatim.
            assertEquals(validPath, result.get(),
                    "A canonical valid path must be returned unchanged");
        }

        @Test
        void shouldAcceptPathExactlyAtMaxLength() throws Exception {
            String path = validPathOfLength(config.maxPathLength());
            assertEquals(config.maxPathLength(), path.length(),
                    "Test fixture must sit exactly on the boundary");

            Optional<String> result = pipeline.validate(path);

            assertTrue(result.isPresent(), "A path exactly at maxPathLength must be accepted");
            assertEquals(path, result.get(), "The boundary path must be returned unchanged");
        }

        @Test
        void shouldHandleNullInput() throws Exception {
            Optional<String> result = pipeline.validate(null);
            assertEquals(Optional.empty(), result);
        }

        @Test
        void shouldHandleEmptyInput() throws Exception {
            Optional<String> result = pipeline.validate("");
            assertTrue(result.isPresent());
            assertEquals("", result.get());
        }

        /**
         * Regression guard for ADR-0011: the "Unicode above 255 is rejected" rule of
         * {@code CharacterValidationStage} governs the wire form only, so a percent-encoded code
         * point above 255 is accepted at the default configuration. {@code %e5%98%8a%e5%98%8d}
         * decodes to U+560A U+560D, whose low bytes are LF and CR; the pipeline must return the
         * full code points and never a truncated line break.
         */
        @ParameterizedTest
        @CsvSource({"/api/%e5%98%8a%e5%98%8d, /api/\u560A\u560D", "/search/%e4%b8%ad, /search/\u4E2D"})
        void shouldAcceptDecodedCodePointAbove255(String encodedPath, String expected) {
            assertEquals(Optional.of(expected), pipeline.validate(encodedPath));
        }
    }

    @Nested
    class SecurityValidation {

        static Stream<String> nullByteUrls() {
            return NullByteURLGenerator.NULL_BYTE_URLS.stream();
        }

        @ParameterizedTest
        @MethodSource("nullByteUrls")
        void shouldRejectNullByteInjection(String maliciousPath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(maliciousPath));

            assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType());
            assertEquals(ValidationType.URL_PATH, exception.getValidationType());
            assertEquals(maliciousPath, exception.getOriginalInput());
        }

        /**
         * {@link PathTraversalGenerator} mixes families the pipeline decides by different
         * mechanisms, so this test draws until it holds five values of the one family whose
         * verdict is the traversal itself: raw dot-dot segments with forward slashes, made of
         * unreserved characters and slashes only. Nothing but the pattern stage can reject those.
         */
        @Test
        void shouldRejectPathTraversal() {
            PathTraversalGenerator generator = new PathTraversalGenerator();
            List<String> rawTraversals = Stream.generate(generator::next)
                    .limit(TRAVERSAL_DRAW_CAP)
                    .filter(value -> value.contains("../") && UNRESERVED_AND_SLASH.matcher(value).matches())
                    .limit(TRAVERSAL_SAMPLES)
                    .toList();

            assertEquals(TRAVERSAL_SAMPLES, rawTraversals.size(),
                    () -> "PathTraversalGenerator must still emit raw traversals made of unreserved characters "
                            + "and slashes; fewer than " + TRAVERSAL_SAMPLES + " in " + TRAVERSAL_DRAW_CAP + " draws");

            for (String traversalPath : rawTraversals) {
                UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                        pipeline.validate(traversalPath));

                assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, exception.getFailureType(),
                        () -> "Unexpected verdict for: " + traversalPath);
                assertEquals(ValidationType.URL_PATH, exception.getValidationType());
                assertEquals(traversalPath, exception.getOriginalInput());
            }
        }

        /**
         * Every value this generator emits is a traversal pattern wrapped in one to three
         * percent-encoding layers, optionally with a backslash separator. Which stage rejects it
         * depends on the sample, so the expected verdict is derived from the sample itself (see
         * {@link #expectedEncodingBypassVerdict(String)}) and asserted exactly.
         */
        @ParameterizedTest
        @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 5)
        void shouldRejectEncodingBypassAttacks(String encodedPath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(encodedPath));

            assertEquals(expectedEncodingBypassVerdict(encodedPath), exception.getFailureType(),
                    "Encoding-bypass attack %s produced unexpected failure type".formatted(encodedPath));
            assertEquals(ValidationType.URL_PATH, exception.getValidationType());
            assertEquals(encodedPath, exception.getOriginalInput());
        }

        /**
         * The Unicode attack generator mixes raw non-ASCII homoglyphs and invisible characters, raw
         * null bytes and plain traversal sequences, so the expected verdict is derived from the
         * sample itself (see {@link #expectedUnicodeAttackVerdict(String)}) and asserted exactly.
         */
        @ParameterizedTest
        @TypeGeneratorSource(value = UnicodeAttackGenerator.class, count = 5)
        void shouldRejectUnicodeAttacks(String unicodePath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(unicodePath));

            assertEquals(expectedUnicodeAttackVerdict(unicodePath), exception.getFailureType(),
                    "Unicode attack %s produced unexpected failure type".formatted(unicodePath));
            assertEquals(ValidationType.URL_PATH, exception.getValidationType());
            assertEquals(unicodePath, exception.getOriginalInput());
        }

        @Test
        void shouldRejectOversizedPath() {
            String oversizedPath = "/" + generatePathContent(config.maxPathLength());

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(oversizedPath));

            assertEquals(UrlSecurityFailureType.PATH_TOO_LONG, exception.getFailureType());
            assertEquals(ValidationType.URL_PATH, exception.getValidationType());
            assertEquals(oversizedPath, exception.getOriginalInput());
        }
    }

    @Nested
    class PipelineBehavior {

        @Test
        void shouldSequentiallyApplyStages() {
            String problematicPath = "/" + generateRepeatedPattern("invalid path with spaces", 1000);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(problematicPath));

            assertEquals(UrlSecurityFailureType.PATH_TOO_LONG, exception.getFailureType());
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = PathTraversalGenerator.class, count = 5)
        void shouldTrackSecurityEventsWhenRejectingAttacks(String attackPath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(attackPath));
            assertEquals(1, eventCounter.getTotalCount(), "One rejection records exactly one event");
            assertEquals(1, eventCounter.getCount(exception.getFailureType()),
                    "The recorded event carries the failure type of the rejection");
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = PathTraversalGenerator.class, count = 5)
        void shouldPreserveStageExceptionAsCause(String attackPath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(attackPath));

            assertInstanceOf(UrlSecurityException.class, exception.getCause(),
                    "Pipeline must preserve the originating stage exception as cause");
            UrlSecurityException stageException = (UrlSecurityException) exception.getCause();
            assertEquals(exception.getFailureType(), stageException.getFailureType(),
                    "Rewrapped exception must keep the stage's failure type");
        }

        @Test
        void shouldHaveCorrectEqualsAndHashCode() {
            URLPathValidationPipeline pipeline1 = new URLPathValidationPipeline(config, eventCounter);
            URLPathValidationPipeline pipeline2 = new URLPathValidationPipeline(config, eventCounter);

            assertEquals(pipeline1, pipeline2);
            assertEquals(pipeline1.hashCode(), pipeline2.hashCode());
        }

        @Test
        void shouldNotBeEqualWhenConfigurationDiffers() {
            URLPathValidationPipeline strict = new URLPathValidationPipeline(
                    SecurityConfiguration.strict(), eventCounter);
            URLPathValidationPipeline lenient = new URLPathValidationPipeline(
                    SecurityConfiguration.lenient(), eventCounter);

            assertNotEquals(strict, lenient,
                    "Pipelines with different security configurations must not compare equal");
        }

        @Test
        void shouldNotExposeConfigAccessor() {
            assertThrows(NoSuchMethodException.class,
                    () -> URLPathValidationPipeline.class.getMethod("getConfig"),
                    "The retained config must not become part of the exported public API");
        }

        @Test
        void shouldHaveCorrectToString() {
            String toString = pipeline.toString();

            assertAll("Rendered pipeline carries diagnostic content, not just an identity hash",
                    () -> assertTrue(toString.contains("URLPathValidationPipeline"),
                            "Rendering must name the concrete pipeline: " + toString),
                    () -> assertTrue(toString.contains(ValidationType.URL_PATH.name()),
                            "Rendering must state the validated component: " + toString),
                    () -> assertTrue(toString.contains("LengthValidationStage"),
                            "Rendering must list the composed stages: " + toString),
                    () -> assertTrue(toString.contains("CharacterValidationStage"),
                            "Rendering must list the composed stages: " + toString),
                    () -> assertTrue(toString.contains("DecodingStage"),
                            "Rendering must list the composed stages: " + toString),
                    () -> assertTrue(toString.contains("NormalizationStage"),
                            "Rendering must list the composed stages: " + toString),
                    () -> assertTrue(toString.contains("PatternMatchingStage"),
                            "Rendering must list the composed stages: " + toString));
        }

        @Test
        void shouldPreserveStageOrder() {
            var stages = pipeline.getStages();
            assertEquals(6, stages.size());

            assertTrue(stages.getFirst().getClass().getSimpleName().contains("Length"));
            assertTrue(stages.get(1).getClass().getSimpleName().contains("Character"));
            assertTrue(stages.get(2).getClass().getSimpleName().contains("Pattern"));
            assertTrue(stages.get(3).getClass().getSimpleName().contains("Decoding"));
            assertTrue(stages.get(4).getClass().getSimpleName().contains("Normalization"));
            assertTrue(stages.get(5).getClass().getSimpleName().contains("Pattern"));
        }
    }

    /**
     * Builds a valid URL path of exactly {@code totalLength} characters: a leading slash followed
     * by unreserved ASCII letters. Deliberately free of dot segments, percent-encoding and
     * separators beyond the leading slash, so length is the only property under test at the
     * boundary.
     */
    private String validPathOfLength(int totalLength) {
        StringBuilder path = new StringBuilder(totalLength);
        path.append('/');
        while (path.length() < totalLength) {
            path.append((char) ('a' + (path.length() % 26)));
        }
        return path.toString();
    }

    /**
     * QI-17: Generate realistic path content instead of using .repeat().
     * Creates varied path content for URL validation testing.
     */
    private String generatePathContent(int length) {
        StringBuilder result = new StringBuilder();
        String[] segments = {"api", "data", "user", "admin", "config", "test"};

        for (int i = 0; i < length; i++) {
            if (i % 20 == 0 && i > 0) {
                result.append("/").append(segments[i / 20 % segments.length]);
                i += segments[i / 20 % segments.length].length() + 1;
                if (i >= length) break;
            }
            result.append((char) ('a' + (i % 26)));
        }

        // Ensure exact length
        String generated = result.toString();
        return generated.length() > length ? generated.substring(0, length) : generated;
    }

    /**
     * QI-17: Generate realistic repeated patterns instead of using .repeat().
     */
    private String generateRepeatedPattern(String pattern, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(pattern);
            if (i % 10 == 9) {
                result.append(i % 10);
            }
        }
        return result.toString();
    }

    /**
     * Derives the verdict an {@code EncodingCombinationGenerator} sample must receive. The
     * generator never encodes a backslash, so a backslash variant reaches
     * {@code CharacterValidationStage} raw and is rejected there. A forward-slash variant with three
     * encoding layers carries {@code %2525}, which no traversal pattern matches, so
     * {@code DecodingStage} rejects it as double encoding. One or two layers match a traversal
     * pattern.
     */
    private static UrlSecurityFailureType expectedEncodingBypassVerdict(String encodedPath) {
        if (encodedPath.indexOf('\\') >= 0) {
            return UrlSecurityFailureType.INVALID_CHARACTER;
        }
        if (encodedPath.toLowerCase(Locale.ROOT).contains("%2525")) {
            return UrlSecurityFailureType.DOUBLE_ENCODING;
        }
        return UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED;
    }

    /**
     * Derives the verdict a {@code UnicodeAttackGenerator} sample must receive. Every sample is
     * raw, so {@code CharacterValidationStage} sees it first: a null byte is a null-byte injection,
     * any other non-ASCII code point is an invalid character, and a pure-ASCII sample is a plain
     * traversal that the pattern stage detects.
     */
    private static UrlSecurityFailureType expectedUnicodeAttackVerdict(String unicodePath) {
        if (unicodePath.indexOf('\0') >= 0) {
            return UrlSecurityFailureType.NULL_BYTE_INJECTION;
        }
        if (unicodePath.chars().anyMatch(ch -> ch > 127)) {
            return UrlSecurityFailureType.INVALID_CHARACTER;
        }
        return UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED;
    }
}

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
import de.cuioss.http.security.generators.encoding.UnicodeAttackGenerator;
import de.cuioss.http.security.generators.url.NullByteURLGenerator;
import de.cuioss.http.security.generators.url.PathTraversalURLGenerator;
import de.cuioss.http.security.generators.url.ValidURLPathGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnableGeneratorController
class URLPathValidationPipelineTest {

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
    }

    @Nested
    class SecurityValidation {

        @ParameterizedTest
        @TypeGeneratorSource(value = NullByteURLGenerator.class, count = 5)
        void shouldRejectNullByteInjection(String maliciousPath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(maliciousPath));

            assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType());
            assertEquals(ValidationType.URL_PATH, exception.getValidationType());
            assertEquals(maliciousPath, exception.getOriginalInput());
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = PathTraversalURLGenerator.class, count = 5)
        void shouldRejectPathTraversal(String traversalPath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(traversalPath));

            assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, exception.getFailureType());
            assertEquals(ValidationType.URL_PATH, exception.getValidationType());
            assertEquals(traversalPath, exception.getOriginalInput());
        }

        /**
         * Every value this generator emits is a traversal pattern wrapped in one to three
         * percent-encoding layers, optionally with a backslash separator. Which stage rejects it
         * therefore depends on the sample: a raw or singly-encoded backslash form is stopped by the
         * character set, a singly- or doubly-encoded forward-slash form matches a traversal pattern,
         * and a triply-encoded form is caught as double encoding. The verdict is asserted as
         * membership in that explicit set rather than a single value, because the generator
         * legitimately spans all three.
         */
        @ParameterizedTest
        @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 5)
        void shouldRejectEncodingBypassAttacks(String encodedPath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(encodedPath));

            EnumSet<UrlSecurityFailureType> expected = EnumSet.of(
                    UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
                    UrlSecurityFailureType.INVALID_CHARACTER,
                    UrlSecurityFailureType.DOUBLE_ENCODING);
            assertTrue(expected.contains(exception.getFailureType()),
                    "Encoding-bypass attack %s produced unexpected failure type %s, expected one of %s"
                            .formatted(encodedPath, exception.getFailureType(), expected));
            assertEquals(ValidationType.URL_PATH, exception.getValidationType());
            assertEquals(encodedPath, exception.getOriginalInput());
        }

        /**
         * The Unicode attack generator mixes non-ASCII homoglyphs, encoded null bytes and encoded
         * traversal sequences, so it spans three verdicts: a non-ASCII code point is rejected by the
         * character set, an encoded null byte as a null-byte injection, and an encoded traversal
         * sequence as traversal. Membership in that explicit set is asserted for the same reason.
         */
        @ParameterizedTest
        @TypeGeneratorSource(value = UnicodeAttackGenerator.class, count = 5)
        void shouldRejectUnicodeAttacks(String unicodePath) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(unicodePath));

            EnumSet<UrlSecurityFailureType> expected = EnumSet.of(
                    UrlSecurityFailureType.INVALID_CHARACTER,
                    UrlSecurityFailureType.NULL_BYTE_INJECTION,
                    UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED);
            assertTrue(expected.contains(exception.getFailureType()),
                    "Unicode attack %s produced unexpected failure type %s, expected one of %s"
                            .formatted(unicodePath, exception.getFailureType(), expected));
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
        @TypeGeneratorSource(value = PathTraversalURLGenerator.class, count = 5)
        void shouldTrackSecurityEventsWhenRejectingAttacks(String attackPath) {
            assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(attackPath));
            assertTrue(eventCounter.getTotalCount() > 0);
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = PathTraversalURLGenerator.class, count = 5)
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
            assertTrue(toString.contains("URLPathValidationPipeline"));
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
}
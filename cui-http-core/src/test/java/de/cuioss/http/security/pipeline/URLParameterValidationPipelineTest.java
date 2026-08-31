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
import de.cuioss.http.security.generators.url.NullByteInjectionParameterGenerator;
import de.cuioss.http.security.generators.url.PathTraversalParameterGenerator;
import de.cuioss.http.security.generators.url.ValidURLParameterStringGenerator;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.generator.junit.parameterized.TypeGeneratorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnableGeneratorController
class URLParameterValidationPipelineTest {

    /**
     * The verdicts {@code PathTraversalParameterGenerator} legitimately spans. A raw traversal
     * sequence matches a traversal pattern; a singly-encoded one is caught after decoding; a
     * multiply-encoded one trips the double-encoding gate; a truncated or malformed escape trips
     * encoding validation; and a Windows-style {@code %5c} variant decodes to a raw backslash,
     * which RFC 3986 does not permit in a query, so character validation rejects it before
     * traversal detection runs. The exact verdict depends on which spelling the generator emitted,
     * so membership in this explicit set is asserted rather than a single value.
     */
    private static final EnumSet<UrlSecurityFailureType> TRAVERSAL_PARAMETER_FAILURES = EnumSet.of(
            UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
            UrlSecurityFailureType.DOUBLE_ENCODING,
            UrlSecurityFailureType.INVALID_ENCODING,
            UrlSecurityFailureType.INVALID_CHARACTER);

    private SecurityConfiguration config;
    private SecurityEventCounter eventCounter;
    private URLParameterValidationPipeline pipeline;

    @BeforeEach
    void setUp() {
        config = SecurityConfiguration.defaults();
        eventCounter = new SecurityEventCounter();
        pipeline = new URLParameterValidationPipeline(config, eventCounter);
    }

    @Nested
    class PipelineCreation {

        @Test
        void shouldCreatePipelineWithValidParameters() {
            assertEquals(ValidationType.PARAMETER_VALUE, pipeline.getValidationType(), "Pipeline should have correct validation type");
            assertEquals(5, pipeline.getStages().size(), "Pipeline should have 5 validation stages");
            assertSame(eventCounter, pipeline.getEventCounter(), "Pipeline should use the provided event counter");
        }

        @Test
        void shouldRejectNullConfig() {
            assertThrows(NullPointerException.class, () ->
                    new URLParameterValidationPipeline(null, eventCounter));
        }

        @Test
        void shouldRejectNullEventCounter() {
            assertThrows(NullPointerException.class, () ->
                    new URLParameterValidationPipeline(config, null));
        }
    }

    @Nested
    class ValidInputHandling {

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidURLParameterStringGenerator.class, count = 10)
        void shouldValidateValidParameters(String validParam) throws Exception {
            Optional<String> result = pipeline.validate(validParam);
            assertTrue(result.isPresent());
            assertEquals(canonicalParameterForm(validParam), result.get(),
                    "Valid parameter should be returned in its decoded, NFC-canonical form");
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
            assertEquals("", result.get(), "Empty input should return empty string result");
        }
    }

    @Nested
    class SecurityValidation {

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidURLParameterStringGenerator.class, count = 5)
        void shouldValidateParameterVariations(String param) throws Exception {
            Optional<String> result = pipeline.validate(param);
            assertTrue(result.isPresent());
            assertEquals(canonicalParameterForm(param), result.get(),
                    "Parameter variation should be returned in its decoded, NFC-canonical form");
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = NullByteInjectionParameterGenerator.class, count = 5)
        void shouldRejectNullByteInjection(String maliciousParam) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(maliciousParam));

            assertEquals(UrlSecurityFailureType.NULL_BYTE_INJECTION, exception.getFailureType(), "Exception should indicate null byte injection failure");
            assertEquals(ValidationType.PARAMETER_VALUE, exception.getValidationType(), "Exception should indicate parameter value validation type");
            assertEquals(maliciousParam, exception.getOriginalInput(), "Exception should preserve original malicious input");
        }

        /**
         * The two spellings are rejected by <em>different</em> stages, and the assertions record
         * which. The percent-encoded values survive the query character set and are caught as
         * traversal once decoded. The unencoded values never reach traversal detection at all: a
         * literal {@code /} is not a member of the RFC 3986 query character set, so the character
         * stage rejects them first as an invalid character. Both are rejected, but only the encoded
         * pair exercises traversal detection.
         */
        @Test
        void shouldRejectSpecificPathTraversalValues() {
            String encoded1 = "..%2F..%2Fetc%2Fpasswd";
            String encoded2 = "%2E%2E%2F%2E%2E%2Fconfig";

            assertAll("Percent-encoded traversal is caught as traversal after decoding",
                    () -> assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
                            assertThrows(UrlSecurityException.class,
                                    () -> pipeline.validate(encoded1)).getFailureType(),
                            "Encoded traversal pattern: " + encoded1),
                    () -> assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
                            assertThrows(UrlSecurityException.class,
                                    () -> pipeline.validate(encoded2)).getFailureType(),
                            "Encoded traversal pattern: " + encoded2));

            String unencoded1 = "../../../etc/passwd";
            String unencoded2 = "../../config";

            assertAll("An unencoded slash is not a query character, so it is rejected earlier",
                    () -> assertEquals(UrlSecurityFailureType.INVALID_CHARACTER,
                            assertThrows(UrlSecurityException.class,
                                    () -> pipeline.validate(unencoded1)).getFailureType(),
                            "Unencoded traversal pattern: " + unencoded1),
                    () -> assertEquals(UrlSecurityFailureType.INVALID_CHARACTER,
                            assertThrows(UrlSecurityException.class,
                                    () -> pipeline.validate(unencoded2)).getFailureType(),
                            "Unencoded traversal pattern: " + unencoded2));
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = PathTraversalParameterGenerator.class, count = 5)
        void shouldRejectPathTraversalVariants(String maliciousParam) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(maliciousParam));

            assertTrue(TRAVERSAL_PARAMETER_FAILURES.contains(exception.getFailureType()),
                    "Traversal parameter %s produced unexpected failure type %s, expected one of %s"
                            .formatted(maliciousParam, exception.getFailureType(),
                                    TRAVERSAL_PARAMETER_FAILURES));
            assertEquals(ValidationType.PARAMETER_VALUE, exception.getValidationType());
            assertEquals(maliciousParam, exception.getOriginalInput());
        }

        /**
         * Spans three verdicts for the same reason as its URL-path counterpart: a backslash form is
         * stopped by the query character set, a singly- or doubly-encoded forward-slash form matches
         * a traversal pattern, and a triply-encoded form is caught as double encoding.
         */
        @ParameterizedTest
        @TypeGeneratorSource(value = EncodingCombinationGenerator.class, count = 5)
        void shouldRejectEncodingBypassAttacks(String encodedParam) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(encodedParam));

            EnumSet<UrlSecurityFailureType> expected = EnumSet.of(
                    UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED,
                    UrlSecurityFailureType.INVALID_CHARACTER,
                    UrlSecurityFailureType.DOUBLE_ENCODING);
            assertTrue(expected.contains(exception.getFailureType()),
                    "Encoding-bypass attack %s produced unexpected failure type %s, expected one of %s"
                            .formatted(encodedParam, exception.getFailureType(), expected));
            assertEquals(ValidationType.PARAMETER_VALUE, exception.getValidationType());
            assertEquals(encodedParam, exception.getOriginalInput());
        }

        @Test
        void shouldRejectOversizedParameter() {
            String oversizedParam = generateParameterValue(config.maxParameterValueLength() + 100);

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(oversizedParam));

            assertEquals(UrlSecurityFailureType.INPUT_TOO_LONG, exception.getFailureType());
            assertEquals(ValidationType.PARAMETER_VALUE, exception.getValidationType());
            assertEquals(oversizedParam, exception.getOriginalInput());
        }

        /**
         * Regression: a percent-encoded combining character (%CC%80 = U+0300) is valid
         * percent-encoding before decoding, but the decoded combining mark must be
         * rejected by the post-decode character re-validation.
         */
        @Test
        void shouldRejectEncodedCombiningCharacter() {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate("value%CC%80"));

            assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, exception.getFailureType());
            assertEquals(ValidationType.PARAMETER_VALUE, exception.getValidationType());
        }

        /**
         * F-05 re-partition: parameter values are canonicalized with the <em>lossless</em>
         * NFC form (not NFKC), so a percent-encoded fullwidth solidus (%EF%BC%8F = U+FF0F)
         * is preserved rather than folded into a path separator. This is deliberate:
         * parameter values are not parsed as paths, and NFC preserves legitimate
         * international content (the fullwidth-CJK-in-parameter false positive). Structural
         * folding to a separator is enforced for {@code URL_PATH} (NFKC), not for parameters.
         */
        @Test
        void shouldPreserveEncodedFullwidthContentInParameters() {
            assertTrue(config.normalizeUnicode(), "Default config normalizes (NFC) parameter values");

            // The fullwidth solidus does not fold under NFC, so no structural separator is
            // introduced and the value is accepted (normalize-and-continue).
            Optional<String> result = pipeline.validate("path%EF%BC%8F%EF%BC%8Fadmin");
            assertTrue(result.isPresent(), "Fullwidth content in a parameter value should be preserved, not rejected");
            assertEquals("path／／admin", result.get(),
                    "The fullwidth solidus must survive as U+FF0F, not fold to an ASCII '/'");
        }
    }

    @Nested
    class ParameterSpecificScenarios {

        @ParameterizedTest
        @TypeGeneratorSource(value = ValidURLParameterStringGenerator.class, count = 5)
        void shouldValidateParameterSpecificScenarios(String validParam) throws Exception {
            Optional<String> result = pipeline.validate(validParam);
            assertTrue(result.isPresent());
            assertEquals(canonicalParameterForm(validParam), result.get(),
                    "Valid parameter should be returned in its decoded, NFC-canonical form");
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = PathTraversalParameterGenerator.class, count = 3)
        void shouldRejectPathTraversalInParameters(String traversalParam) {
            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(traversalParam));

            assertTrue(TRAVERSAL_PARAMETER_FAILURES.contains(exception.getFailureType()),
                    "Traversal parameter %s produced unexpected failure type %s, expected one of %s"
                            .formatted(traversalParam, exception.getFailureType(),
                                    TRAVERSAL_PARAMETER_FAILURES));
            assertEquals(ValidationType.PARAMETER_VALUE, exception.getValidationType());
            assertEquals(traversalParam, exception.getOriginalInput());
        }
    }

    @Nested
    class PipelineBehavior {

        @Test
        void shouldSequentiallyApplyStages() {
            // QI-17: Replace excessive 12KB tab pattern with realistic boundary testing
            // Need to exceed default parameter value limit (2048 chars)
            // Generate exactly 2100 chars to guarantee exceeding the limit
            String problematicParam = "param=" + Generators.letterStrings(2100, 2100).next();

            UrlSecurityException exception = assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(problematicParam));

            assertEquals(UrlSecurityFailureType.INPUT_TOO_LONG, exception.getFailureType(), "Pipeline should reject input that exceeds length limits");
        }

        @ParameterizedTest
        @TypeGeneratorSource(value = PathTraversalParameterGenerator.class, count = 5)
        void shouldTrackSecurityEventsWhenRejectingAttacks(String attackParam) {
            assertThrows(UrlSecurityException.class, () ->
                    pipeline.validate(attackParam));
            assertTrue(eventCounter.getTotalCount() > 0, "Security events should be tracked when attacks are rejected");
        }

        @Test
        void shouldHaveCorrectEqualsAndHashCode() {
            URLParameterValidationPipeline pipeline1 = new URLParameterValidationPipeline(config, eventCounter);
            URLParameterValidationPipeline pipeline2 = new URLParameterValidationPipeline(config, eventCounter);

            assertEquals(pipeline1, pipeline2, "Pipelines with same configuration should be equal");
            assertEquals(pipeline1.hashCode(), pipeline2.hashCode(), "Equal pipelines should have same hash code");
        }

        @Test
        void shouldNotBeEqualWhenConfigurationDiffers() {
            URLParameterValidationPipeline strict = new URLParameterValidationPipeline(
                    SecurityConfiguration.strict(), eventCounter);
            URLParameterValidationPipeline lenient = new URLParameterValidationPipeline(
                    SecurityConfiguration.lenient(), eventCounter);

            assertNotEquals(strict, lenient,
                    "Pipelines with different security configurations must not compare equal");
        }

        @Test
        void shouldNotExposeConfigAccessor() {
            assertThrows(NoSuchMethodException.class,
                    () -> URLParameterValidationPipeline.class.getMethod("getConfig"),
                    "The retained config must not become part of the exported public API");
        }

        @Test
        void shouldHaveCorrectToString() {
            String toString = pipeline.toString();
            assertTrue(toString.contains("URLParameterValidationPipeline"), "toString should contain pipeline class name");
        }

        @Test
        void shouldPreserveStageOrder() {
            var stages = pipeline.getStages();
            assertEquals(5, stages.size(), "Pipeline should have exactly 5 stages in correct order");

            assertTrue(stages.getFirst().getClass().getSimpleName().contains("Length"), "First stage should be length validation");
            assertTrue(stages.get(1).getClass().getSimpleName().contains("Character"), "Second stage should be character validation");
            assertTrue(stages.get(2).getClass().getSimpleName().contains("Decoding"), "Third stage should be decoding validation");
            assertTrue(stages.get(3).getClass().getSimpleName().contains("Normalization"), "Fourth stage should be normalization validation");
            assertTrue(stages.get(4).getClass().getSimpleName().contains("Pattern"), "Fifth stage should be pattern validation");
        }
    }

    /**
     * The canonical form the parameter pipeline is documented to return for an accepted value:
     * percent-decoded (form semantics, so {@code +} maps to a space) and then NFC-normalized.
     * <p>
     * Unlike the URL-path pipeline, this pipeline does <em>not</em> return an accepted value
     * verbatim - {@code DecodingStage} decodes it, so {@code jane%40demo.net} comes back as
     * {@code jane@demo.net}. Asserting this canonical form is what makes the valid-input tests
     * check the returned value rather than merely its presence. NFC (not NFKC) is asserted because
     * parameter values are canonicalized losslessly; see
     * {@link #shouldPreserveEncodedFullwidthContentInParameters()}.
     */
    private static String canonicalParameterForm(String rawValue) {
        return Normalizer.normalize(
                URLDecoder.decode(rawValue, StandardCharsets.UTF_8), Normalizer.Form.NFC);
    }

    /**
     * QI-17: Generate realistic parameter values instead of using .repeat().
     * Creates varied parameter content for URL parameter validation testing.
     */
    private String generateParameterValue(int length) {
        StringBuilder result = new StringBuilder();
        String[] values = {"value", "param", "data", "user", "id", "name"};

        for (int i = 0; i < length; i++) {
            if (i % 30 == 0 && i > 0) {
                result.append("_").append(values[i / 30 % values.length]).append("_");
                i += values[i / 30 % values.length].length() + 2;
                if (i >= length) break;
            }
            result.append((char) ('a' + (i % 26)));
        }

        // Ensure exact length
        String generated = result.toString();
        return generated.length() > length ? generated.substring(0, length) : generated;
    }
}
/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.http.security.core;

import de.cuioss.http.security.exceptions.UrlSecurityException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link HttpSecurityValidator}
 */
class HttpSecurityValidatorTest {

    private static final String TEST_INPUT = "testInput";

    @Test
    void shouldBeFunctionalInterface() {
        // Verify it can be used as a lambda
        HttpSecurityValidator validator = input -> input != null ? Optional.of(input.toUpperCase()) : Optional.empty();

        Optional<String> result = validator.validate("hello");
        assertTrue(result.isPresent());
        assertEquals("HELLO", result.get(), "Lambda validator should transform input to uppercase");
        assertEquals(Optional.empty(), validator.validate(null), "Lambda validator should return empty Optional for null input");
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void shouldSupportMethodReference() {
        // Verify it can be used with method references
        HttpSecurityValidator validator = input -> Optional.of(input.toUpperCase());

        Optional<String> result = validator.validate("hello");
        assertTrue(result.isPresent());
        assertEquals("HELLO", result.get(), "Method reference validator should transform input to uppercase");
    }

    @Test
    void shouldComposeValidators() {
        HttpSecurityValidator first = input -> Optional.of(input + "_first");
        HttpSecurityValidator second = input -> Optional.of(input + "_second");

        HttpSecurityValidator composed = first.andThen(second);

        Optional<String> result = composed.validate("test");
        assertTrue(result.isPresent());
        assertEquals("test_first_second", result.get(), "Composed validator should apply transformations in sequence");
    }

    @Test
    void shouldComposeValidatorsWithCompose() {
        HttpSecurityValidator first = input -> Optional.of(input + "_first");
        HttpSecurityValidator second = input -> Optional.of(input + "_second");

        HttpSecurityValidator composed = second.compose(first);

        Optional<String> result = composed.validate("test");
        assertTrue(result.isPresent());
        assertEquals("test_first_second", result.get(), "Composed validator with compose() should apply transformations in correct order");
    }

    @Test
    void shouldPropagateExceptionsInComposition() {
        HttpSecurityValidator failing = input -> {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.INVALID_CHARACTER)
                    .validationType(ValidationType.URL_PATH)
                    .originalInput(input)
                    .build();
        };
        HttpSecurityValidator normal = input -> Optional.of(input + "_processed");

        HttpSecurityValidator composed = failing.andThen(normal);

        UrlSecurityException thrown = assertThrows(UrlSecurityException.class,
                () -> composed.validate("test"));
        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, thrown.getFailureType(), "Exception should preserve original failure type in composition");
    }

    @Test
    void shouldRequireNonNullInAndThen() {
        HttpSecurityValidator validator = Optional::ofNullable;

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> validator.andThen(null));
        assertTrue(thrown.getMessage().contains("after validator must not be null"), "Exception should indicate which validator parameter was null");
    }

    @Test
    void shouldRequireNonNullInCompose() {
        HttpSecurityValidator validator = Optional::ofNullable;

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> validator.compose(null));
        assertTrue(thrown.getMessage().contains("before validator must not be null"), "Exception should indicate which validator parameter was null in compose()");
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void shouldSupportConditionalValidation() {
        HttpSecurityValidator validator = input -> Optional.of(input.toUpperCase());
        HttpSecurityValidator conditionalValidator = validator.when(input -> input.startsWith("test"));

        Optional<String> result1 = conditionalValidator.validate("testValue");
        assertTrue(result1.isPresent());
        assertEquals("TESTVALUE", result1.get(), "Conditional validator should transform input when predicate matches");
        Optional<String> result2 = conditionalValidator.validate("otherValue");
        assertTrue(result2.isPresent());
        assertEquals("otherValue", result2.get(), "Conditional validator should leave input unchanged when predicate doesn't match");
    }

    @Test
    void shouldRequireNonNullPredicateInWhen() {
        HttpSecurityValidator validator = Optional::ofNullable;

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> validator.when(null));
        assertTrue(thrown.getMessage().contains("predicate must not be null"), "Exception should indicate that predicate parameter was null");
    }

    @Test
    void shouldProvideIdentityValidator() {
        HttpSecurityValidator identity = HttpSecurityValidator.identity();

        Optional<String> result1 = identity.validate(TEST_INPUT);
        assertTrue(result1.isPresent());
        assertEquals(TEST_INPUT, result1.get(), "Identity validator should return input unchanged");
        assertEquals(Optional.empty(), identity.validate(null), "Identity validator should return empty Optional for null input");
        Optional<String> result2 = identity.validate("");
        assertTrue(result2.isPresent());
        assertEquals("", result2.get(), "Identity validator should return empty string unchanged");
    }

    @Test
    void shouldProvideRejectValidator() {
        HttpSecurityValidator rejectValidator = HttpSecurityValidator.reject(
                UrlSecurityFailureType.INVALID_CHARACTER,
                ValidationType.URL_PATH
        );

        UrlSecurityException thrown = assertThrows(UrlSecurityException.class,
                () -> rejectValidator.validate("anything"));

        assertEquals(UrlSecurityFailureType.INVALID_CHARACTER, thrown.getFailureType(), "Reject validator should preserve specified failure type");
        assertEquals(ValidationType.URL_PATH, thrown.getValidationType(), "Reject validator should preserve specified validation type");
        assertEquals("anything", thrown.getOriginalInput(), "Reject validator should preserve original input in exception");
        assertTrue(thrown.getDetail().isPresent(), "Reject validator should provide detail information");
        assertTrue(thrown.getDetail().get().contains("unconditionally rejected"), "Reject validator detail should explain unconditional rejection");
    }

    @Test
    void shouldHandleNullInRejectValidator() {
        HttpSecurityValidator rejectValidator = HttpSecurityValidator.reject(
                UrlSecurityFailureType.INVALID_CHARACTER,
                ValidationType.URL_PATH
        );

        UrlSecurityException thrown = assertThrows(UrlSecurityException.class,
                () -> rejectValidator.validate(null));

        assertEquals("null", thrown.getOriginalInput(), "Reject validator should handle null input by converting to string");
    }

    @Test
    void shouldRequireNonNullParametersInReject() {
        assertThrows(NullPointerException.class, () ->
                HttpSecurityValidator.reject(null, ValidationType.URL_PATH));
        assertThrows(NullPointerException.class, () ->
                HttpSecurityValidator.reject(UrlSecurityFailureType.INVALID_CHARACTER, null));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void shouldSupportComplexComposition() {
        // Create a pipeline: trim -> lowercase -> reject if contains "bad"
        HttpSecurityValidator trimmer = input -> Optional.of(input.trim());
        HttpSecurityValidator lowercaser = input -> Optional.of(input.toLowerCase());
        HttpSecurityValidator badWordRejecter = input -> {
            if (input != null && input.contains("bad")) {
                throw UrlSecurityException.builder()
                        .failureType(UrlSecurityFailureType.SUSPICIOUS_PATTERN_DETECTED)
                        .validationType(ValidationType.URL_PATH)
                        .originalInput(input)
                        .build();
            }
            return Optional.of(input);
        };

        HttpSecurityValidator pipeline = trimmer.andThen(lowercaser).andThen(badWordRejecter);

        Optional<String> result = pipeline.validate("  GOOD  ");
        assertTrue(result.isPresent());
        assertEquals("good", result.get(), "Complex pipeline should process valid input correctly");

        UrlSecurityException thrown = assertThrows(UrlSecurityException.class,
                () -> pipeline.validate("  BAD  "));
        assertEquals("bad", thrown.getOriginalInput(), "Exception should contain processed input from pipeline stages");
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void shouldSupportConditionalPipeline() {
        HttpSecurityValidator processor = input -> Optional.of(input.toUpperCase());
        HttpSecurityValidator conditionalProcessor = processor.when(input ->
                input != null && input.startsWith("process:"));

        Optional<String> result1 = conditionalProcessor.validate("process:test");
        assertTrue(result1.isPresent());
        assertEquals("PROCESS:TEST", result1.get(), "Conditional pipeline should process matching input");
        Optional<String> result2 = conditionalProcessor.validate("skip:test");
        assertTrue(result2.isPresent());
        assertEquals("skip:test", result2.get(), "Conditional pipeline should skip non-matching input");
        assertEquals(Optional.empty(), conditionalProcessor.validate(null), "Conditional pipeline should handle null input correctly");
    }

    @Test
    void shouldWorkWithStreams() {
        HttpSecurityValidator validator = input -> input != null ? Optional.of(input.trim()) : Optional.empty();

        List<String> inputs = Arrays.asList(" hello ", " world ", null);
        List<String> outputs = inputs.stream()
                .map(input -> validator.validate(input).orElse(null)).toList();

        assertEquals(Arrays.asList("hello", "world", null), outputs, "Validator should work correctly with streams processing multiple inputs");
    }

    @Test
    void shouldPreserveExceptionDetails() {
        String testInput = "dangerous_input";
        String testDetail = "Specific violation details";

        HttpSecurityValidator validator = input -> {
            throw UrlSecurityException.builder()
                    .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
                    .validationType(ValidationType.URL_PATH)
                    .originalInput(input)
                    .detail(testDetail)
                    .build();
        };

        UrlSecurityException thrown = assertThrows(UrlSecurityException.class,
                () -> validator.validate(testInput));

        assertEquals(testInput, thrown.getOriginalInput(), "Exception should preserve original input");
        assertTrue(thrown.getDetail().isPresent(), "Exception should have detail information");
        assertEquals(testDetail, thrown.getDetail().get(), "Exception should preserve detailed failure information");
        assertEquals(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED, thrown.getFailureType(), "Exception should preserve specific failure type");
        assertEquals(ValidationType.URL_PATH, thrown.getValidationType(), "Exception should preserve validation type");
    }

    @Test
    void shouldSupportNestedComposition() {
        HttpSecurityValidator a = input -> Optional.of(input + "A");
        HttpSecurityValidator b = input -> Optional.of(input + "B");
        HttpSecurityValidator c = input -> Optional.of(input + "C");

        // Test ((a andThen b) andThen c)
        HttpSecurityValidator nested1 = a.andThen(b).andThen(c);
        Optional<String> result1 = nested1.validate("test");
        assertTrue(result1.isPresent());
        assertEquals("testABC", result1.get(), "Left-associative composition should produce correct result");

        // Test (a andThen (b andThen c))
        HttpSecurityValidator nested2 = a.andThen(b.andThen(c));
        Optional<String> result2 = nested2.validate("test");
        assertTrue(result2.isPresent());
        assertEquals("testABC", result2.get(), "Right-associative composition should produce correct result");

        // Both should produce the same result
        assertEquals(nested1.validate("test"), nested2.validate("test"), "Both composition styles should produce identical results");
    }

    @Test
    void shouldHandleEmptyAndSpecialStrings() {
        HttpSecurityValidator validator = HttpSecurityValidator.identity();

        Optional<String> result1 = validator.validate("");
        assertTrue(result1.isPresent());
        assertEquals("", result1.get(), "Identity validator should handle empty string correctly");
        Optional<String> result2 = validator.validate(" ");
        assertTrue(result2.isPresent());
        assertEquals(" ", result2.get(), "Identity validator should preserve space character");
        Optional<String> result3 = validator.validate("\n");
        assertTrue(result3.isPresent());
        assertEquals("\n", result3.get(), "Identity validator should preserve newline character");
        Optional<String> result4 = validator.validate("\t");
        assertTrue(result4.isPresent());
        assertEquals("\t", result4.get(), "Identity validator should preserve tab character");
        Optional<String> result5 = validator.validate("🚀");
        assertTrue(result5.isPresent());
        assertEquals("🚀", result5.get(), "Identity validator should handle Unicode characters correctly");
    }
}
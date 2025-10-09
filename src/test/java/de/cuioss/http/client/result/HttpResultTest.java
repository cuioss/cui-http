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
package de.cuioss.http.client.result;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link HttpResult} sealed interface with proper organization.
 */
@EnableGeneratorController
class HttpResultTest {

    private final TypedGenerator<String> strings = Generators.nonEmptyStrings();
    private final TypedGenerator<Integer> httpStatus = Generators.integers(100, 599);
    private final HttpResultSuccessGenerator successGen = new HttpResultSuccessGenerator();
    private final HttpResultFailureGenerator failureGen = new HttpResultFailureGenerator();

    @Nested
    class SuccessCreation {

        @Test
        void shouldCreateSuccessWithAllFields() {
            String content = strings.next();
            String etag = strings.next();
            int status = 200;

            HttpResult<String> result = HttpResult.success(content, etag, status);

            assertTrue(result.isSuccess());
            assertTrue(result.getContent().isPresent());
            assertEquals(content, result.getContent().get());
            assertTrue(result.getETag().isPresent());
            assertEquals(etag, result.getETag().get());
            assertTrue(result.getHttpStatus().isPresent());
            assertEquals(status, result.getHttpStatus().get());
        }

        @Test
        void shouldCreateSuccessWithNullETag() {
            String content = strings.next();

            HttpResult<String> result = HttpResult.success(content, null, 200);

            assertTrue(result.isSuccess());
            assertTrue(result.getContent().isPresent());
            assertEquals(content, result.getContent().get());
            assertFalse(result.getETag().isPresent());
        }

        @ParameterizedTest
        @CsvSource({"200", "201", "204", "304"})
        void shouldCreateSuccessWithVariousStatusCodes(int status) {
            HttpResult<String> result = HttpResult.success(strings.next(), null, status);

            assertTrue(result.isSuccess());
            assertTrue(result.getHttpStatus().isPresent());
            assertEquals(status, result.getHttpStatus().get());
        }
    }

    @Nested
    class FailureCreation {

        @Test
        void shouldCreateFailureWithoutFallback() {
            String errorMsg = strings.next();
            HttpErrorCategory category = HttpErrorCategory.CLIENT_ERROR;

            HttpResult<String> result = HttpResult.failure(errorMsg, null, category);

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().isPresent());
            assertEquals(errorMsg, result.getErrorMessage().get());
            assertTrue(result.getErrorCategory().isPresent());
            assertEquals(category, result.getErrorCategory().get());
            assertFalse(result.getContent().isPresent());
        }

        @Test
        void shouldCreateFailureWithFallback() {
            String errorMsg = strings.next();
            String fallback = strings.next();
            String etag = strings.next();

            HttpResult<String> result = HttpResult.failureWithFallback(
                    errorMsg, null, fallback, HttpErrorCategory.SERVER_ERROR, etag, 503);

            assertFalse(result.isSuccess());
            assertTrue(result.getContent().isPresent());
            assertEquals(fallback, result.getContent().get());
            assertTrue(result.getETag().isPresent());
            assertEquals(etag, result.getETag().get());
            assertTrue(result.getHttpStatus().isPresent());
            assertEquals(503, result.getHttpStatus().get());
        }

        @Test
        void shouldCreateFailureWithCause() {
            IOException cause = new IOException("Test");

            HttpResult<String> result = HttpResult.failure("Error", cause, HttpErrorCategory.NETWORK_ERROR);

            assertTrue(result.getCause().isPresent());
            assertSame(cause, result.getCause().get());
        }

        @Test
        void shouldBuildCompleteFailure() {
            IOException cause = new IOException("Test");
            String fallback = strings.next();
            String etag = strings.next();

            HttpResult<String> result = HttpResult.Failure.<String>builder()
                    .errorMessage("Error")
                    .cause(cause)
                    .fallbackContent(fallback)
                    .category(HttpErrorCategory.SERVER_ERROR)
                    .etag(etag)
                    .httpStatus(500)
                    .build();

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().isPresent());
            assertEquals("Error", result.getErrorMessage().get());
            assertTrue(result.getCause().isPresent());
            assertSame(cause, result.getCause().get());
            assertTrue(result.getContent().isPresent());
            assertEquals(fallback, result.getContent().get());
            assertTrue(result.getETag().isPresent());
            assertEquals(etag, result.getETag().get());
        }

        @Test
        void shouldBuildMinimalFailure() {
            HttpResult<String> result = HttpResult.Failure.<String>builder()
                    .errorMessage("Error")
                    .category(HttpErrorCategory.INVALID_CONTENT)
                    .build();

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().isPresent());
            assertFalse(result.getCause().isPresent());
            assertFalse(result.getContent().isPresent());
            assertFalse(result.getETag().isPresent());
            assertFalse(result.getHttpStatus().isPresent());
        }
    }

    @Nested
    class SuccessBehavior {

        @Test
        void shouldReturnTrueForIsSuccess() {
            HttpResult<String> result = successGen.next();

            assertTrue(result.isSuccess());
            assertInstanceOf(HttpResult.Success.class, result);
        }

        @Test
        void shouldNotBeRetryable() {
            HttpResult<String> result = successGen.next();

            assertFalse(result.isRetryable());
        }

        @Test
        void shouldHaveNoErrorInformation() {
            HttpResult<String> result = successGen.next();

            assertFalse(result.getErrorMessage().isPresent());
            assertFalse(result.getCause().isPresent());
            assertFalse(result.getErrorCategory().isPresent());
        }

        @Test
        void shouldAlwaysHaveContent() {
            for (int i = 0; i < 10; i++) {
                HttpResult<String> result = successGen.next();
                assertTrue(result.getContent().isPresent(), "Success must have content");
            }
        }

        @Test
        void shouldAlwaysHaveHttpStatus() {
            for (int i = 0; i < 10; i++) {
                HttpResult<String> result = successGen.next();
                assertTrue(result.getHttpStatus().isPresent(), "Success must have HTTP status");
            }
        }
    }

    @Nested
    class FailureBehavior {

        @Test
        void shouldReturnFalseForIsSuccess() {
            HttpResult<String> result = failureGen.next();

            assertFalse(result.isSuccess());
            assertInstanceOf(HttpResult.Failure.class, result);
        }

        @Test
        void shouldAlwaysHaveErrorMessage() {
            for (int i = 0; i < 10; i++) {
                HttpResult<String> result = failureGen.next();
                assertTrue(result.getErrorMessage().isPresent(), "Failure must have error message");
                assertFalse(result.getErrorMessage().get().isBlank(), "Error message must not be blank");
            }
        }

        @Test
        void shouldAlwaysHaveErrorCategory() {
            for (int i = 0; i < 10; i++) {
                HttpResult<String> result = failureGen.next();
                assertTrue(result.getErrorCategory().isPresent(), "Failure must have error category");
            }
        }

        @Test
        void shouldHaveNoSuccessInformation() {
            HttpResult<String> result = HttpResult.failure("Error", null, HttpErrorCategory.CLIENT_ERROR);

            assertFalse(result.getContent().isPresent());
        }
    }

    @Nested
    class RetryLogic {

        @ParameterizedTest
        @EnumSource(value = HttpErrorCategory.class, names = {"NETWORK_ERROR", "SERVER_ERROR"})
        void shouldBeRetryableForTransientErrors(HttpErrorCategory category) {
            HttpResult<String> result = HttpResult.failure("Error", null, category);

            assertTrue(result.isRetryable());
            assertTrue(category.isRetryable());
        }

        @ParameterizedTest
        @EnumSource(value = HttpErrorCategory.class, names = {"CLIENT_ERROR", "INVALID_CONTENT", "CONFIGURATION_ERROR"})
        void shouldNotBeRetryableForPermanentErrors(HttpErrorCategory category) {
            HttpResult<String> result = HttpResult.failure("Error", null, category);

            assertFalse(result.isRetryable());
            assertFalse(category.isRetryable());
        }

        @Test
        void successShouldNeverBeRetryable() {
            for (int i = 0; i < 10; i++) {
                HttpResult<String> result = successGen.next();
                assertFalse(result.isRetryable(), "Success must not be retryable");
            }
        }
    }

    @Nested
    class Transformation {

        @Test
        void successMapShouldTransformContent() {
            HttpResult<String> result = HttpResult.success("42", "etag", 200);

            HttpResult<Integer> mapped = result.map(Integer::parseInt);

            assertTrue(mapped.isSuccess());
            assertTrue(mapped.getContent().isPresent());
            assertEquals(42, mapped.getContent().get());
            assertTrue(mapped.getETag().isPresent());
            assertEquals("etag", mapped.getETag().get());
            assertTrue(mapped.getHttpStatus().isPresent());
            assertEquals(200, mapped.getHttpStatus().get());
        }

        @Test
        void failureMapWithoutFallbackShouldPreserveError() {
            IOException cause = new IOException("Test");
            HttpResult<String> result = HttpResult.failure("Error", cause, HttpErrorCategory.CLIENT_ERROR);

            HttpResult<Integer> mapped = result.map(Integer::parseInt);

            assertFalse(mapped.isSuccess());
            assertFalse(mapped.getContent().isPresent());
            assertTrue(mapped.getErrorMessage().isPresent());
            assertEquals("Error", mapped.getErrorMessage().get());
            assertTrue(mapped.getCause().isPresent());
            assertSame(cause, mapped.getCause().get());
            assertTrue(mapped.getErrorCategory().isPresent());
            assertEquals(HttpErrorCategory.CLIENT_ERROR, mapped.getErrorCategory().get());
        }

        @Test
        void failureMapWithFallbackShouldTransformFallback() {
            HttpResult<String> result = HttpResult.failureWithFallback(
                    "Error", null, "789", HttpErrorCategory.SERVER_ERROR, "etag", 500);

            HttpResult<Integer> mapped = result.map(Integer::parseInt);

            assertFalse(mapped.isSuccess());
            assertTrue(mapped.getContent().isPresent());
            assertEquals(789, mapped.getContent().get());
            assertTrue(mapped.getETag().isPresent());
            assertEquals("etag", mapped.getETag().get());
        }

        @Test
        void mapShouldPreserveAllMetadata() {
            for (int i = 0; i < 5; i++) {
                HttpResult<String> original = successGen.next();
                HttpResult<Integer> mapped = original.map(String::length);

                assertEquals(original.isSuccess(), mapped.isSuccess());
                assertEquals(original.getETag(), mapped.getETag());
                assertEquals(original.getHttpStatus(), mapped.getHttpStatus());
            }
        }
    }

    @Nested
    class PatternMatching {

        @Test
        void shouldMatchSuccessPattern() {
            String content = strings.next();
            String etag = strings.next();
            HttpResult<String> result = HttpResult.success(content, etag, 200);

            String matched = switch (result) {
                case HttpResult.Success<String>(var c, var e, var s) ->
                    "Success: %s, ETag: %s, Status: %d".formatted(c, e, s);
                case HttpResult.Failure<String> f ->
                    "Failure: " + f.errorMessage();
            };

            assertTrue(matched.startsWith("Success:"));
            assertTrue(matched.contains(content));
        }

        @Test
        void shouldMatchFailurePattern() {
            String errorMsg = strings.next();
            HttpResult<String> result = HttpResult.failure(errorMsg, null, HttpErrorCategory.CLIENT_ERROR);

            String matched = switch (result) {
                case HttpResult.Success<String>(var c, var e, var s) ->
                    "Success: " + c;
                case HttpResult.Failure<String> f ->
                    "Failure: " + f.errorMessage();
            };

            assertTrue(matched.startsWith("Failure:"));
            assertTrue(matched.contains(errorMsg));
        }

        @Test
        void shouldDeconstructSuccessRecordInPattern() {
            HttpResult<String> result = HttpResult.success("content", "etag", 200);

            if (result instanceof HttpResult.Success<String>(var content, var etag, var status)) {
                assertEquals("content", content);
                assertEquals("etag", etag);
                assertEquals(200, status);
            } else {
                fail("Should match Success pattern");
            }
        }

        @Test
        void shouldDeconstructFailureRecordInPattern() {
            HttpResult<String> result = HttpResult.failure("Error", null, HttpErrorCategory.CLIENT_ERROR);

            if (result instanceof HttpResult.Failure<String> failure) {
                assertEquals("Error", failure.errorMessage());
                assertEquals(HttpErrorCategory.CLIENT_ERROR, failure.category());
            } else {
                fail("Should match Failure pattern");
            }
        }
    }

    @Nested
    class Equality {

        @Test
        void successShouldEqualWithSameValues() {
            HttpResult<String> result1 = HttpResult.success("content", "etag", 200);
            HttpResult<String> result2 = HttpResult.success("content", "etag", 200);

            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        void successShouldNotEqualWithDifferentStatus() {
            HttpResult<String> result1 = HttpResult.success("content", "etag", 200);
            HttpResult<String> result2 = HttpResult.success("content", "etag", 304);

            assertNotEquals(result1, result2);
        }

        @Test
        void successShouldNotEqualWithDifferentContent() {
            HttpResult<String> result1 = HttpResult.success("content1", "etag", 200);
            HttpResult<String> result2 = HttpResult.success("content2", "etag", 200);

            assertNotEquals(result1, result2);
        }

        @Test
        void failureShouldEqualWithSameValues() {
            HttpResult<String> result1 = HttpResult.failure("Error", null, HttpErrorCategory.CLIENT_ERROR);
            HttpResult<String> result2 = HttpResult.failure("Error", null, HttpErrorCategory.CLIENT_ERROR);

            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        void failureShouldNotEqualWithDifferentCategory() {
            HttpResult<String> result1 = HttpResult.failure("Error", null, HttpErrorCategory.CLIENT_ERROR);
            HttpResult<String> result2 = HttpResult.failure("Error", null, HttpErrorCategory.SERVER_ERROR);

            assertNotEquals(result1, result2);
        }

        @Test
        void successShouldNotEqualFailure() {
            HttpResult<String> success = HttpResult.success("content", null, 200);
            HttpResult<String> failure = HttpResult.failure("Error", null, HttpErrorCategory.CLIENT_ERROR);

            assertNotEquals(success, failure);
        }
    }
}

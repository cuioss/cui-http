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
package de.cuioss.http.client.handler;

import de.cuioss.http.client.result.HttpErrorCategory;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpStatusFamily}.
 */
@EnableGeneratorController
class HttpStatusFamilyTest {

    @Nested
    @DisplayName("fromStatusCode Tests")
    class FromStatusCodeTests {

        @Test
        @DisplayName("Should return INFORMATIONAL for 1xx codes")
        void shouldReturnInformationalFor1xxCodes() {
            assertWholeRangeMapsTo(HttpStatusFamily.INFORMATIONAL, 100, 199);
        }

        @Test
        @DisplayName("Should return SUCCESS for 2xx codes")
        void shouldReturnSuccessFor2xxCodes() {
            assertWholeRangeMapsTo(HttpStatusFamily.SUCCESS, 200, 299);
        }

        @Test
        @DisplayName("Should return REDIRECTION for 3xx codes")
        void shouldReturnRedirectionFor3xxCodes() {
            assertWholeRangeMapsTo(HttpStatusFamily.REDIRECTION, 300, 399);
        }

        @Test
        @DisplayName("Should return CLIENT_ERROR for 4xx codes")
        void shouldReturnClientErrorFor4xxCodes() {
            assertWholeRangeMapsTo(HttpStatusFamily.CLIENT_ERROR, 400, 499);
        }

        @Test
        @DisplayName("Should return SERVER_ERROR for 5xx codes")
        void shouldReturnServerErrorFor5xxCodes() {
            assertWholeRangeMapsTo(HttpStatusFamily.SERVER_ERROR, 500, 599);
        }

        @Test
        @DisplayName("Should return UNKNOWN for invalid codes")
        void shouldReturnUnknownForInvalidCodes() {
            // The two edges adjoining the valid range stay pinned; the drawn values cover the two
            // open domains on either side of it, which five hand-picked literals only sampled.
            int belowRange = Generators.integers(Integer.MIN_VALUE, 99).next();
            int aboveRange = Generators.integers(600, Integer.MAX_VALUE).next();

            assertAll("every code outside 100..599 is UNKNOWN",
                    () -> assertEquals(HttpStatusFamily.UNKNOWN, HttpStatusFamily.fromStatusCode(99)),
                    () -> assertEquals(HttpStatusFamily.UNKNOWN, HttpStatusFamily.fromStatusCode(600)),
                    () -> assertEquals(HttpStatusFamily.UNKNOWN, HttpStatusFamily.fromStatusCode(belowRange),
                            () -> belowRange + " is below the valid range"),
                    () -> assertEquals(HttpStatusFamily.UNKNOWN, HttpStatusFamily.fromStatusCode(aboveRange),
                            () -> aboveRange + " is above the valid range"));
        }

        /**
         * Asserts the family for both edges of its range and for a code drawn from anywhere inside
         * it, so the mapping is stated for the range rather than for a few well-known codes.
         */
        private void assertWholeRangeMapsTo(HttpStatusFamily expected, int first, int last) {
            int drawn = Generators.integers(first, last).next();

            assertAll(expected + " covers " + first + ".." + last,
                    () -> assertEquals(expected, HttpStatusFamily.fromStatusCode(first), "the first code of the range"),
                    () -> assertEquals(expected, HttpStatusFamily.fromStatusCode(last), "the last code of the range"),
                    () -> assertEquals(expected, HttpStatusFamily.fromStatusCode(drawn),
                            () -> drawn + " lies inside the range"));
        }
    }

    @Nested
    @DisplayName("contains Tests")
    class ContainsTests {

        @Test
        @DisplayName("INFORMATIONAL should contain 1xx codes")
        void informationalShouldContain1xxCodes() {
            assertTrue(HttpStatusFamily.INFORMATIONAL.contains(100));
            assertTrue(HttpStatusFamily.INFORMATIONAL.contains(101));
            assertTrue(HttpStatusFamily.INFORMATIONAL.contains(199));
            assertFalse(HttpStatusFamily.INFORMATIONAL.contains(99));
            assertFalse(HttpStatusFamily.INFORMATIONAL.contains(200));
        }

        @Test
        @DisplayName("SUCCESS should contain 2xx codes")
        void successShouldContain2xxCodes() {
            assertTrue(HttpStatusFamily.SUCCESS.contains(200));
            assertTrue(HttpStatusFamily.SUCCESS.contains(201));
            assertTrue(HttpStatusFamily.SUCCESS.contains(299));
            assertFalse(HttpStatusFamily.SUCCESS.contains(199));
            assertFalse(HttpStatusFamily.SUCCESS.contains(300));
        }

        @Test
        @DisplayName("REDIRECTION should contain 3xx codes")
        void redirectionShouldContain3xxCodes() {
            assertTrue(HttpStatusFamily.REDIRECTION.contains(300));
            assertTrue(HttpStatusFamily.REDIRECTION.contains(301));
            assertTrue(HttpStatusFamily.REDIRECTION.contains(399));
            assertFalse(HttpStatusFamily.REDIRECTION.contains(299));
            assertFalse(HttpStatusFamily.REDIRECTION.contains(400));
        }

        @Test
        @DisplayName("CLIENT_ERROR should contain 4xx codes")
        void clientErrorShouldContain4xxCodes() {
            assertTrue(HttpStatusFamily.CLIENT_ERROR.contains(400));
            assertTrue(HttpStatusFamily.CLIENT_ERROR.contains(404));
            assertTrue(HttpStatusFamily.CLIENT_ERROR.contains(499));
            assertFalse(HttpStatusFamily.CLIENT_ERROR.contains(399));
            assertFalse(HttpStatusFamily.CLIENT_ERROR.contains(500));
        }

        @Test
        @DisplayName("SERVER_ERROR should contain 5xx codes")
        void serverErrorShouldContain5xxCodes() {
            assertTrue(HttpStatusFamily.SERVER_ERROR.contains(500));
            assertTrue(HttpStatusFamily.SERVER_ERROR.contains(503));
            assertTrue(HttpStatusFamily.SERVER_ERROR.contains(599));
            assertFalse(HttpStatusFamily.SERVER_ERROR.contains(499));
            assertFalse(HttpStatusFamily.SERVER_ERROR.contains(600));
        }

        @Test
        @DisplayName("UNKNOWN should contain invalid codes")
        void unknownShouldContainInvalidCodes() {
            assertTrue(HttpStatusFamily.UNKNOWN.contains(-1));
            assertTrue(HttpStatusFamily.UNKNOWN.contains(0));
            assertTrue(HttpStatusFamily.UNKNOWN.contains(99));
            assertTrue(HttpStatusFamily.UNKNOWN.contains(600));
            assertFalse(HttpStatusFamily.UNKNOWN.contains(100));
            assertFalse(HttpStatusFamily.UNKNOWN.contains(599));
        }
    }

    @Nested
    @DisplayName("Utility Method Tests")
    class UtilityMethodTests {

        @ParameterizedTest
        @ValueSource(ints = {200, 201, 204, 299})
        @DisplayName("isSuccess should return true for 2xx codes")
        void isSuccessShouldReturnTrueFor2xxCodes(int statusCode) {
            assertTrue(HttpStatusFamily.isSuccess(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {100, 199, 300, 400, 500})
        @DisplayName("isSuccess should return false for non-2xx codes")
        void isSuccessShouldReturnFalseForNon2xxCodes(int statusCode) {
            assertFalse(HttpStatusFamily.isSuccess(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 401, 404, 499})
        @DisplayName("isClientError should return true for 4xx codes")
        void isClientErrorShouldReturnTrueFor4xxCodes(int statusCode) {
            assertTrue(HttpStatusFamily.isClientError(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {100, 200, 300, 500})
        @DisplayName("isClientError should return false for non-4xx codes")
        void isClientErrorShouldReturnFalseForNon4xxCodes(int statusCode) {
            assertFalse(HttpStatusFamily.isClientError(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {500, 501, 503, 599})
        @DisplayName("isServerError should return true for 5xx codes")
        void isServerErrorShouldReturnTrueFor5xxCodes(int statusCode) {
            assertTrue(HttpStatusFamily.isServerError(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {100, 200, 300, 400})
        @DisplayName("isServerError should return false for non-5xx codes")
        void isServerErrorShouldReturnFalseForNon5xxCodes(int statusCode) {
            assertFalse(HttpStatusFamily.isServerError(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {300, 301, 302, 304, 399})
        @DisplayName("isRedirection should return true for 3xx codes")
        void isRedirectionShouldReturnTrueFor3xxCodes(int statusCode) {
            assertTrue(HttpStatusFamily.isRedirection(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {100, 200, 400, 500})
        @DisplayName("isRedirection should return false for non-3xx codes")
        void isRedirectionShouldReturnFalseForNon3xxCodes(int statusCode) {
            assertFalse(HttpStatusFamily.isRedirection(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {100, 101, 199})
        @DisplayName("isInformational should return true for 1xx codes")
        void isInformationalShouldReturnTrueFor1xxCodes(int statusCode) {
            assertTrue(HttpStatusFamily.isInformational(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {99, 200, 300, 400, 500})
        @DisplayName("isInformational should return false for non-1xx codes")
        void isInformationalShouldReturnFalseForNon1xxCodes(int statusCode) {
            assertFalse(HttpStatusFamily.isInformational(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {100, 200, 300, 400, 500, 599})
        @DisplayName("isValid should return true for valid codes")
        void isValidShouldReturnTrueForValidCodes(int statusCode) {
            assertTrue(HttpStatusFamily.isValid(statusCode));
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 0, 99, 600, 1000})
        @DisplayName("isValid should return false for invalid codes")
        void isValidShouldReturnFalseForInvalidCodes(int statusCode) {
            assertFalse(HttpStatusFamily.isValid(statusCode));
        }
    }

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString should format correctly for standard families")
        void toStringShouldFormatCorrectlyForStandardFamilies() {
            assertEquals("Informational (100-199)", HttpStatusFamily.INFORMATIONAL.toString());
            assertEquals("Success (200-299)", HttpStatusFamily.SUCCESS.toString());
            assertEquals("Redirection (300-399)", HttpStatusFamily.REDIRECTION.toString());
            assertEquals("Client Error (400-499)", HttpStatusFamily.CLIENT_ERROR.toString());
            assertEquals("Server Error (500-599)", HttpStatusFamily.SERVER_ERROR.toString());
        }

        @Test
        @DisplayName("toString should format correctly for UNKNOWN")
        void toStringShouldFormatCorrectlyForUnknown() {
            assertEquals("Unknown", HttpStatusFamily.UNKNOWN.toString());
        }
    }

    @Nested
    @DisplayName("toErrorCategory Tests")
    class ToErrorCategoryTests {

        @Test
        @DisplayName("CLIENT_ERROR should map to HttpErrorCategory.CLIENT_ERROR")
        void clientErrorShouldMapToClientErrorCategory() {
            assertEquals(HttpErrorCategory.CLIENT_ERROR, HttpStatusFamily.CLIENT_ERROR.toErrorCategory());
        }

        @Test
        @DisplayName("SERVER_ERROR should map to HttpErrorCategory.SERVER_ERROR")
        void serverErrorShouldMapToServerErrorCategory() {
            assertEquals(HttpErrorCategory.SERVER_ERROR, HttpStatusFamily.SERVER_ERROR.toErrorCategory());
        }

        @Test
        @DisplayName("REDIRECTION should map to HttpErrorCategory.INVALID_CONTENT")
        void redirectionShouldMapToInvalidContent() {
            assertEquals(HttpErrorCategory.INVALID_CONTENT, HttpStatusFamily.REDIRECTION.toErrorCategory());
        }

        @Test
        @DisplayName("INFORMATIONAL should map to HttpErrorCategory.INVALID_CONTENT")
        void informationalShouldMapToInvalidContent() {
            assertEquals(HttpErrorCategory.INVALID_CONTENT, HttpStatusFamily.INFORMATIONAL.toErrorCategory());
        }

        @Test
        @DisplayName("UNKNOWN should map to HttpErrorCategory.INVALID_CONTENT")
        void unknownShouldMapToInvalidContent() {
            assertEquals(HttpErrorCategory.INVALID_CONTENT, HttpStatusFamily.UNKNOWN.toErrorCategory());
        }

        @Test
        @DisplayName("SUCCESS should throw IllegalStateException")
        void successShouldThrowException() {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    HttpStatusFamily.SUCCESS::toErrorCategory
            );
            assertEquals("SUCCESS is not an error", exception.getMessage());
        }

        @Test
        @DisplayName("All non-SUCCESS families should map without exception")
        void allNonSuccessFamiliesShouldMapWithoutException() {
            assertDoesNotThrow(HttpStatusFamily.CLIENT_ERROR::toErrorCategory);
            assertDoesNotThrow(HttpStatusFamily.SERVER_ERROR::toErrorCategory);
            assertDoesNotThrow(HttpStatusFamily.REDIRECTION::toErrorCategory);
            assertDoesNotThrow(HttpStatusFamily.INFORMATIONAL::toErrorCategory);
            assertDoesNotThrow(HttpStatusFamily.UNKNOWN::toErrorCategory);
        }

        @Test
        @DisplayName("SERVER_ERROR category should be retryable")
        void serverErrorCategoryShouldBeRetryable() {
            HttpErrorCategory category = HttpStatusFamily.SERVER_ERROR.toErrorCategory();
            assertTrue(category.isRetryable(), "SERVER_ERROR should map to retryable category");
        }

        @Test
        @DisplayName("CLIENT_ERROR category should not be retryable")
        void clientErrorCategoryShouldNotBeRetryable() {
            HttpErrorCategory category = HttpStatusFamily.CLIENT_ERROR.toErrorCategory();
            assertFalse(category.isRetryable(), "CLIENT_ERROR should map to non-retryable category");
        }
    }
}
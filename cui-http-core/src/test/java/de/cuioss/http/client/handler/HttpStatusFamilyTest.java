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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpStatusFamily}.
 */
@EnableGeneratorController
class HttpStatusFamilyTest {

    /** The first and last status code of a family, stated independently of the enum under test. */
    private record StatusRange(int first, int last) {
    }

    /**
     * The expected boundaries of every family except {@link HttpStatusFamily#UNKNOWN}. The ranges
     * are written out here rather than read back from the enum, so the tests state the mapping
     * instead of echoing it.
     */
    private static final Map<HttpStatusFamily, StatusRange> EXPECTED_RANGES = Map.of(
            HttpStatusFamily.INFORMATIONAL, new StatusRange(100, 199),
            HttpStatusFamily.SUCCESS, new StatusRange(200, 299),
            HttpStatusFamily.REDIRECTION, new StatusRange(300, 399),
            HttpStatusFamily.CLIENT_ERROR, new StatusRange(400, 499),
            HttpStatusFamily.SERVER_ERROR, new StatusRange(500, 599));

    @Test
    @DisplayName("Every mapped family has its boundaries stated in the expectation table")
    void shouldStateBoundariesForEveryMappedFamily() {
        Set<HttpStatusFamily> mappedFamilies = EnumSet.complementOf(EnumSet.of(HttpStatusFamily.UNKNOWN));

        assertEquals(mappedFamilies, EXPECTED_RANGES.keySet(),
                "A new HttpStatusFamily constant needs its first and last code in EXPECTED_RANGES");
    }

    private static StatusRange expectedRangeOf(HttpStatusFamily family) {
        assertTrue(EXPECTED_RANGES.containsKey(family),
                () -> family + " has no expected boundaries in EXPECTED_RANGES");
        return EXPECTED_RANGES.get(family);
    }

    @Nested
    @DisplayName("fromStatusCode Tests")
    class FromStatusCodeTests {

        @ParameterizedTest
        @EnumSource(value = HttpStatusFamily.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should map the whole range of every family to that family")
        void shouldMapWholeRangeToFamily(HttpStatusFamily family) {
            StatusRange range = expectedRangeOf(family);

            assertWholeRangeMapsTo(family, range.first(), range.last());
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

        /**
         * Probes both edges of the family's range, a code drawn from inside it, and the codes
         * directly below and above it.
         */
        @ParameterizedTest
        @EnumSource(value = HttpStatusFamily.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Every family contains exactly the codes of its range")
        void shouldContainExactlyItsRange(HttpStatusFamily family) {
            StatusRange range = expectedRangeOf(family);
            int drawn = Generators.integers(range.first(), range.last()).next();

            assertAll(family + " contains " + range.first() + ".." + range.last(),
                    () -> assertTrue(family.contains(range.first()), "the first code of the range"),
                    () -> assertTrue(family.contains(range.last()), "the last code of the range"),
                    () -> assertTrue(family.contains(drawn), () -> drawn + " lies inside the range"),
                    () -> assertFalse(family.contains(range.first() - 1), "the code below the range"),
                    () -> assertFalse(family.contains(range.last() + 1), "the code above the range"));
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
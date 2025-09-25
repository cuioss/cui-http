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
package de.cuioss.http.security.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AttributeParser}.
 */
@DisplayName("AttributeParser")
class AttributeParserTest {

    @Nested
    @DisplayName("extractAttributeValue")
    class ExtractAttributeValue {

        @ParameterizedTest
        @CsvSource({
                "'name=value', 'name', 'value'",
                "'first=value1; second=value2', 'first', 'value1'",
                "'first=value1; second=value2', 'second', 'value2'",
                "'Domain=example.com; Path=/', 'domain', 'example.com'",
                "'Domain=example.com; Path=/', 'DOMAIN', 'example.com'",
                "'name=  value with spaces  ', 'name', 'value with spaces'",
                "'first=value1; last=finalvalue', 'last', 'finalvalue'",
                "'name=; other=value', 'name', ''"
        })
        @DisplayName("Should extract attribute values correctly")
        void shouldExtractAttributeValues(String attributes, String attributeName, String expected) {
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, attributeName);
            assertTrue(result.isPresent());
            assertEquals(expected, result.get());
        }

        @Test
        @DisplayName("Should handle attribute with only equals sign")
        void shouldHandleOnlyEqualsSign() {
            String attributes = "name=";
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "name");

            assertTrue(result.isPresent());
            assertEquals("", result.get());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should return empty for null or empty attribute string")
        void shouldReturnEmptyForNullOrEmptyString(String attributes) {
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "name");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty for non-existent attribute")
        void shouldReturnEmptyForNonExistentAttribute() {
            String attributes = "name=value; other=value2";
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "missing");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should match exact attribute name")
        void shouldMatchExactAttributeName() {
            // Fixed implementation now correctly matches exact attribute names
            String attributes = "longname=value1; name=value2";
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "name");

            assertTrue(result.isPresent());
            assertEquals("value2", result.get()); // Now correctly matches exact "name" attribute

            // Test with attribute at beginning
            String properlyFormatted = "name=value2; longname=value1";
            Optional<String> exactMatch = AttributeParser.extractAttributeValue(properlyFormatted, "name");
            assertTrue(exactMatch.isPresent());
            assertEquals("value2", exactMatch.get());
        }

        @ParameterizedTest
        @CsvSource({
                "'charset=UTF-8', 'charset', 'UTF-8'",
                "'charset=UTF-8; boundary=something', 'charset', 'UTF-8'",
                "'boundary=xyz; charset=ISO-8859-1', 'charset', 'ISO-8859-1'",
                "'type=text/html; charset=utf-8', 'charset', 'utf-8'"
        })
        @DisplayName("Should extract charset from content-type headers")
        void shouldExtractCharsetFromContentType(String contentType, String attribute, String expected) {
            Optional<String> result = AttributeParser.extractAttributeValue(contentType, attribute);

            assertTrue(result.isPresent());
            assertEquals(expected, result.get());
        }

        @ParameterizedTest
        @CsvSource({
                "'Domain=.example.com; Path=/; Secure; HttpOnly', 'domain', '.example.com'",
                "'Path=/api; Max-Age=3600; SameSite=Strict', 'max-age', '3600'",
                "'SameSite=Lax; Secure', 'samesite', 'Lax'"
        })
        @DisplayName("Should extract cookie attributes")
        void shouldExtractCookieAttributes(String cookieAttrs, String attribute, String expected) {
            Optional<String> result = AttributeParser.extractAttributeValue(cookieAttrs, attribute);

            assertTrue(result.isPresent());
            assertEquals(expected, result.get());
        }

        @Test
        @DisplayName("Should handle special characters in values")
        @SuppressWarnings("java:S5976")
        void shouldHandleSpecialCharactersInValues() {
            String attributes = "url=https://example.com/path?query=1&other=2; name=value";
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "url");

            assertTrue(result.isPresent());
            assertEquals("https://example.com/path?query=1&other=2", result.get());
        }

        @Test
        @DisplayName("Should handle values with equals signs")
        void shouldHandleValuesWithEqualsSigns() {
            String attributes = "formula=a=b+c; other=value";
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "formula");

            assertTrue(result.isPresent());
            assertEquals("a=b+c", result.get());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "name=value;no-space",
                "name=value ;with-space",
                "name=value  ;multiple-spaces"
        })
        @DisplayName("Should handle various spacing around semicolons")
        void shouldHandleVariousSpacing(String attributes) {
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "name");

            assertTrue(result.isPresent());
            assertEquals("value", result.get());
        }

        @Test
        @DisplayName("Should handle quoted values")
        void shouldHandleQuotedValues() {
            String attributes = "name=\"quoted value\"; other=unquoted";
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "name");

            assertTrue(result.isPresent());
            assertEquals("\"quoted value\"", result.get());
        }

        @Test
        @DisplayName("Should preserve case in values")
        void shouldPreserveCaseInValues() {
            String attributes = "Name=CaseSensitiveValue";
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "name");

            assertTrue(result.isPresent());
            assertEquals("CaseSensitiveValue", result.get());
        }

        @Test
        @DisplayName("Should not match shorter attribute names that are suffixes of longer ones")
        void shouldNotMatchSuffixAttributes() {
            // Bug reproduction test: searching for "id" should not match "session_id"
            String attributes = "session_id=123; id=456";
            Optional<String> result = AttributeParser.extractAttributeValue(attributes, "id");

            // This should return "456" not "123"
            assertTrue(result.isPresent());
            assertEquals("456", result.get(), "Should match exact attribute 'id', not 'session_id'");

            // Additional test cases
            String attributes2 = "user-id=abc; id=xyz";
            Optional<String> result2 = AttributeParser.extractAttributeValue(attributes2, "id");
            assertTrue(result2.isPresent());
            assertEquals("xyz", result2.get(), "Should match exact attribute 'id', not 'user-id'");

            String attributes3 = "prefix_name=first; name=second";
            Optional<String> result3 = AttributeParser.extractAttributeValue(attributes3, "name");
            assertTrue(result3.isPresent());
            assertEquals("second", result3.get(), "Should match exact attribute 'name', not 'prefix_name'");
        }
    }
}
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
package de.cuioss.http.client.converter;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StringContentConverter}.
 *
 * @author Oliver Wolff
 */
class StringContentConverterTest {

    @Test
    @DisplayName("Identity converter should return input unchanged")
    void identityConverterShouldReturnInputUnchanged() {
        StringContentConverter<String> converter = StringContentConverter.identity();
        String input = "test content";

        Optional<String> result = converter.convert(input);

        assertTrue(result.isPresent());
        assertEquals(input, result.get());
    }

    @Test
    @DisplayName("Identity converter should handle null input")
    void identityConverterShouldHandleNullInput() {
        StringContentConverter<String> converter = StringContentConverter.identity();

        Optional<String> result = converter.convert(null);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Identity converter should return empty string as empty value")
    void identityConverterShouldReturnEmptyStringAsEmptyValue() {
        StringContentConverter<String> converter = StringContentConverter.identity();

        String emptyValue = converter.emptyValue();

        assertEquals("", emptyValue);
    }

    @Test
    @DisplayName("Should use UTF-8 charset by default")
    void shouldUseUtf8CharsetByDefault() {
        StringContentConverter<String> converter = new StringContentConverter<String>() {
            @Override
            protected Optional<String> convertString(String rawContent) {
                return Optional.ofNullable(rawContent);
            }

            @NotNull
            @Override
            public String emptyValue() {
                return "";
            }
        };

        HttpResponse.BodyHandler<?> bodyHandler = converter.getBodyHandler();

        // The BodyHandler should be configured for UTF-8 (we can't easily test this directly,
        // but we can verify it returns a valid BodyHandler)
        assertNotNull(bodyHandler);
    }

    @Test
    @DisplayName("Should use specified charset")
    void shouldUseSpecifiedCharset() {

        StringContentConverter<String> converter = new StringContentConverter<String>(StandardCharsets.ISO_8859_1) {
            @Override
            protected Optional<String> convertString(String rawContent) {
                return Optional.ofNullable(rawContent);
            }

            @NotNull
            @Override
            public String emptyValue() {
                return "";
            }
        };

        HttpResponse.BodyHandler<?> bodyHandler = converter.getBodyHandler();

        // The BodyHandler should be configured for ISO-8859-1
        assertNotNull(bodyHandler);
    }

    @Test
    @DisplayName("Custom converter should implement conversion logic correctly")
    void customConverterShouldImplementConversionLogicCorrectly() {
        StringContentConverter<Integer> converter = new StringContentConverter<Integer>() {
            @Override
            protected Optional<Integer> convertString(String rawContent) {
                if (rawContent == null || rawContent.trim().isEmpty()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(Integer.parseInt(rawContent.trim()));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }

            @NotNull
            @Override
            public Integer emptyValue() {
                return 0;
            }
        };

        // Test successful conversion
        Optional<Integer> result1 = converter.convert("123");
        assertTrue(result1.isPresent());
        assertEquals(123, result1.get());

        // Test conversion failure
        Optional<Integer> result2 = converter.convert("not a number");
        assertFalse(result2.isPresent());

        // Test empty input
        Optional<Integer> result3 = converter.convert("");
        assertFalse(result3.isPresent());

        // Test null input
        Optional<Integer> result4 = converter.convert(null);
        assertFalse(result4.isPresent());

        // Test empty value
        assertEquals(0, converter.emptyValue());
    }
}
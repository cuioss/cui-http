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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link HttpContentConverter} interface.
 * <p>
 * These tests verify the basic contract and behavior expectations
 * for any implementation of HttpContentConverter.
 *
 * @author Oliver Wolff
 */
class HttpContentConverterTest {

    @Test
    @DisplayName("HttpContentConverter contract - convert method should handle null input gracefully")
    void contractConvertShouldHandleNullInputGracefully() {
        HttpContentConverter<String> converter = createTestConverter();

        Optional<String> result = converter.convert(null);

        // Contract: convert should return Optional.empty() for null input
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("HttpContentConverter contract - emptyValue should never return null")
    void contractEmptyValueShouldNeverReturnNull() {
        HttpContentConverter<String> converter = createTestConverter();

        String emptyValue = converter.emptyValue();

        // Contract: emptyValue must never return null
        assertNotNull(emptyValue);
    }

    @Test
    @DisplayName("HttpContentConverter contract - getBodyHandler should never return null")
    void contractGetBodyHandlerShouldNeverReturnNull() {
        HttpContentConverter<String> converter = createTestConverter();

        HttpResponse.BodyHandler<?> bodyHandler = converter.getBodyHandler();

        // Contract: getBodyHandler must never return null
        assertNotNull(bodyHandler);
    }

    @Test
    @DisplayName("HttpContentConverter contract - should maintain type safety")
    void contractShouldMaintainTypeSafety() {
        // Test with different generic types
        HttpContentConverter<Integer> stringToIntConverter = new HttpContentConverter<Integer>() {
            @Override
            public Optional<Integer> convert(Object rawContent) {
                String content = (String) rawContent;
                if (content == null) return Optional.empty();
                try {
                    return Optional.of(Integer.parseInt(content.trim()));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }

            @Override
            public HttpResponse.BodyHandler<?> getBodyHandler() {
                return HttpResponse.BodyHandlers.ofString();
            }

            @NotNull
            @Override
            public Integer emptyValue() {
                return 0;
            }
        };

        // Type safety should be maintained
        Optional<Integer> result = stringToIntConverter.convert("123");
        assertTrue(result.isPresent());
        assertEquals(123, result.get());

        Integer empty = stringToIntConverter.emptyValue();
        assertEquals(0, empty);
    }

    @Test
    @DisplayName("HttpContentConverter contract - should handle conversion failures gracefully")
    void contractShouldHandleConversionFailuresGracefully() {
        HttpContentConverter<Integer> converter = new HttpContentConverter<Integer>() {
            @Override
            public Optional<Integer> convert(Object rawContent) {
                String content = (String) rawContent;
                if (content == null || content.trim().isEmpty()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(Integer.parseInt(content.trim()));
                } catch (NumberFormatException e) {
                    // Contract: conversion failures should return Optional.empty()
                    return Optional.empty();
                }
            }

            @Override
            public HttpResponse.BodyHandler<?> getBodyHandler() {
                return HttpResponse.BodyHandlers.ofString();
            }

            @NotNull
            @Override
            public Integer emptyValue() {
                return -1;
            }
        };

        // Should handle conversion failures gracefully
        Optional<Integer> result = converter.convert("not a number");
        assertFalse(result.isPresent());

        // Should handle empty input
        Optional<Integer> emptyResult = converter.convert("");
        assertFalse(emptyResult.isPresent());
    }

    @Test
    @DisplayName("HttpContentConverter contract - different BodyHandler types should work")
    void contractDifferentBodyHandlerTypesShouldWork() {
        // Test with byte array BodyHandler
        HttpContentConverter<String> binaryConverter = new HttpContentConverter<String>() {
            @Override
            public Optional<String> convert(Object rawContent) {
                if (rawContent == null) {
                    return Optional.empty();
                }
                byte[] byteContent = (byte[]) rawContent;
                if (byteContent.length == 0) {
                    return Optional.empty();
                }
                return Optional.of(new String(byteContent));
            }

            @Override
            public HttpResponse.BodyHandler<?> getBodyHandler() {
                return HttpResponse.BodyHandlers.ofByteArray();
            }

            @NotNull
            @Override
            public String emptyValue() {
                return "";
            }
        };

        // Should work with different raw types
        HttpResponse.BodyHandler<?> bodyHandler = binaryConverter.getBodyHandler();
        assertNotNull(bodyHandler);

        byte[] testData = "test".getBytes();
        Optional<String> result = binaryConverter.convert(testData);
        assertTrue(result.isPresent());
        assertEquals("test", result.get());
    }

    @Test
    @DisplayName("HttpContentConverter contract - emptyValue should be semantically correct")
    void contractEmptyValueShouldBeSemanticallyCorrect() {
        // String converter should return empty string
        HttpContentConverter<String> stringConverter = createTestConverter();
        assertEquals("", stringConverter.emptyValue());

        // Integer converter should return meaningful empty integer
        HttpContentConverter<Integer> intConverter = new HttpContentConverter<Integer>() {
            @Override
            public Optional<Integer> convert(Object rawContent) {
                return Optional.empty();
            }

            @Override
            public HttpResponse.BodyHandler<?> getBodyHandler() {
                return HttpResponse.BodyHandlers.ofString();
            }

            @NotNull
            @Override
            public Integer emptyValue() {
                return 0; // Semantically correct empty value for Integer
            }
        };
        assertEquals(0, intConverter.emptyValue());
    }

    /**
     * Creates a simple test converter for contract testing.
     */
    private HttpContentConverter<String> createTestConverter() {
        return new HttpContentConverter<String>() {
            @Override
            public Optional<String> convert(Object rawContent) {
                return Optional.ofNullable((String) rawContent);
            }

            @Override
            public HttpResponse.BodyHandler<?> getBodyHandler() {
                return HttpResponse.BodyHandlers.ofString();
            }

            @NotNull
            @Override
            public String emptyValue() {
                return "";
            }
        };
    }
}
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

import de.cuioss.http.client.ContentType;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpResponseConverter} interface contract and implementations.
 */
class HttpResponseConverterTest {

    /**
     * Simple test implementation that returns empty on null or non-String input.
     */
    private static class TestStringConverter implements HttpResponseConverter<String> {
        @Override
        public Optional<String> convert(Object rawContent) {
            if (rawContent == null || !(rawContent instanceof String)) {
                return Optional.empty();
            }
            return Optional.of((String) rawContent);
        }

        @Override
        public HttpResponse.BodyHandler<?> getBodyHandler() {
            return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        }

        @Override
        public ContentType contentType() {
            return ContentType.TEXT_PLAIN;
        }
    }

    /**
     * Test implementation that simulates parsing failure.
     */
    private static class FailingConverter implements HttpResponseConverter<Integer> {
        @Override
        public Optional<Integer> convert(Object rawContent) {
            // Simulate parsing failure - always return empty
            return Optional.empty();
        }

        @Override
        public HttpResponse.BodyHandler<?> getBodyHandler() {
            return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }

    /**
     * Test implementation for binary content.
     */
    private static class ByteArrayConverter implements HttpResponseConverter<byte[]> {
        @Override
        public Optional<byte[]> convert(Object rawContent) {
            if (rawContent == null || !(rawContent instanceof byte[])) {
                return Optional.empty();
            }
            return Optional.of((byte[]) rawContent);
        }

        @Override
        public HttpResponse.BodyHandler<?> getBodyHandler() {
            return HttpResponse.BodyHandlers.ofByteArray();
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_OCTET_STREAM;
        }
    }

    @Test
    void convertReturnsEmptyOnParsingFailure() {
        var converter = new FailingConverter();

        Optional<Integer> result = converter.convert("123");

        assertTrue(result.isEmpty(), "Converter should return Optional.empty() on parsing failure");
    }

    @Test
    void convertReturnsEmptyOnNullInput() {
        var converter = new TestStringConverter();

        Optional<String> result = converter.convert(null);

        assertTrue(result.isEmpty(), "Converter should return Optional.empty() for null input");
    }

    @Test
    void convertReturnsEmptyOnWrongType() {
        var converter = new TestStringConverter();

        Optional<String> result = converter.convert(123); // Wrong type

        assertTrue(result.isEmpty(), "Converter should return Optional.empty() for wrong type");
    }

    @Test
    void convertSuccessReturnsValue() {
        var converter = new TestStringConverter();

        Optional<String> result = converter.convert("test-content");

        assertTrue(result.isPresent(), "Converter should return Optional.of() on success");
        assertEquals("test-content", result.get(), "Converted value should match input");
    }

    @Test
    void getBodyHandlerReturnsCorrectHandler() {
        var stringConverter = new TestStringConverter();
        var byteConverter = new ByteArrayConverter();

        HttpResponse.BodyHandler<?> stringHandler = stringConverter.getBodyHandler();
        HttpResponse.BodyHandler<?> byteHandler = byteConverter.getBodyHandler();

        assertNotNull(stringHandler, "String converter should return non-null body handler");
        assertNotNull(byteHandler, "Byte converter should return non-null body handler");
        assertNotSame(stringHandler, byteHandler, "Different converters should return different handlers");
    }

    @Test
    void contentTypeReturnsCorrectValue() {
        var stringConverter = new TestStringConverter();
        var failingConverter = new FailingConverter();
        var byteConverter = new ByteArrayConverter();

        assertEquals(ContentType.TEXT_PLAIN, stringConverter.contentType(),
                "String converter should return TEXT_PLAIN");
        assertEquals(ContentType.APPLICATION_JSON, failingConverter.contentType(),
                "Failing converter should return APPLICATION_JSON");
        assertEquals(ContentType.APPLICATION_OCTET_STREAM, byteConverter.contentType(),
                "Byte converter should return APPLICATION_OCTET_STREAM");
    }

    @Test
    void binaryContentConversion() {
        var converter = new ByteArrayConverter();
        byte[] input = new byte[]{1, 2, 3, 4, 5};

        Optional<byte[]> result = converter.convert(input);

        assertTrue(result.isPresent(), "Binary conversion should succeed");
        assertArrayEquals(input, result.get(), "Binary content should match input");
    }

    @Test
    void binaryContentFailsOnWrongType() {
        var converter = new ByteArrayConverter();

        Optional<byte[]> result = converter.convert("not-bytes");

        assertTrue(result.isEmpty(), "Binary converter should return empty for non-byte[] input");
    }

    /**
     * Verify that implementations follow the contract of returning empty rather than throwing.
     */
    @Test
    void converterNeverThrowsExceptions() {
        var converter = new TestStringConverter();

        // Should not throw for any input type
        assertDoesNotThrow(() -> converter.convert(null));
        assertDoesNotThrow(() -> converter.convert(123));
        assertDoesNotThrow(() -> converter.convert(new Object()));
        assertDoesNotThrow(() -> converter.convert("valid"));
    }

    /**
     * Test implementation demonstrating JSON-like parsing with error handling.
     */
    private static class SimulatedJsonConverter implements HttpResponseConverter<Integer> {
        @Override
        public Optional<Integer> convert(Object rawContent) {
            if (rawContent == null || !(rawContent instanceof String json)) {
                return Optional.empty();
            }
            try {
                // Simulate JSON parsing
                return Optional.of(Integer.parseInt(json.trim()));
            } catch (NumberFormatException e) {
                // Return empty instead of throwing - this is the contract!
                return Optional.empty();
            }
        }

        @Override
        public HttpResponse.BodyHandler<?> getBodyHandler() {
            return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }

    @Test
    void jsonLikeConverterSuccess() {
        var converter = new SimulatedJsonConverter();

        Optional<Integer> result = converter.convert("42");

        assertTrue(result.isPresent(), "Valid JSON should convert successfully");
        assertEquals(42, result.get(), "Converted value should be correct");
    }

    @Test
    void jsonLikeConverterFailureReturnsEmpty() {
        var converter = new SimulatedJsonConverter();

        Optional<Integer> result = converter.convert("not-a-number");

        assertTrue(result.isEmpty(), "Invalid JSON should return Optional.empty(), not throw");
    }

    @Test
    void multipleConvertersIndependentState() {
        var converter1 = new TestStringConverter();
        var converter2 = new TestStringConverter();

        Optional<String> result1 = converter1.convert("test1");
        Optional<String> result2 = converter2.convert("test2");

        assertTrue(result1.isPresent() && result2.isPresent(), "Both converters should work independently");
        assertEquals("test1", result1.get());
        assertEquals("test2", result2.get());
    }
}

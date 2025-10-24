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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpRequestConverter} interface contract.
 */
class HttpRequestConverterTest {

    /**
     * Test implementation that converts strings to body publishers.
     */
    private static class StringRequestConverter implements HttpRequestConverter<String> {
        private final boolean failOnSerialization;

        StringRequestConverter() {
            this(false);
        }

        StringRequestConverter(boolean failOnSerialization) {
            this.failOnSerialization = failOnSerialization;
        }

        @Override
        public HttpRequest.BodyPublisher toBodyPublisher(@Nullable String content) {
            if (content == null) {
                return HttpRequest.BodyPublishers.noBody();
            }
            if (failOnSerialization) {
                throw new IllegalArgumentException("Simulated serialization failure");
            }
            return HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8);
        }

        @Override
        public ContentType contentType() {
            return ContentType.TEXT_PLAIN;
        }
    }

    /**
     * Test implementation that always fails on serialization.
     */
    private static class FailingRequestConverter implements HttpRequestConverter<Object> {
        @Override
        public HttpRequest.BodyPublisher toBodyPublisher(@Nullable Object content) {
            if (content == null) {
                return HttpRequest.BodyPublishers.noBody();
            }
            throw new IllegalArgumentException("Serialization failed", /*~~(TODO: Use specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe)~~>*/new RuntimeException("Underlying cause"));
        }

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }
    }

    @Test
    void toBodyPublisherReturnsNoBodyForNull() {
        // GIVEN
        var converter = new StringRequestConverter();

        // WHEN
        HttpRequest.BodyPublisher result = converter.toBodyPublisher(null);

        // THEN
        assertNotNull(result, "BodyPublisher must never be null");
        // Note: We can't directly verify it's noBody(), but we can verify contentLength is 0
        assertEquals(0, result.contentLength(), "noBody() should have content length 0");
    }

    @Test
    void toBodyPublisherReturnsBodyPublisherForValidContent() {
        // GIVEN
        var converter = new StringRequestConverter();
        String content = "test content";

        // WHEN
        HttpRequest.BodyPublisher result = converter.toBodyPublisher(content);

        // THEN
        assertNotNull(result, "BodyPublisher must never be null");
        assertTrue(result.contentLength() > 0, "Content should have positive length");
    }

    @Test
    void toBodyPublisherThrowsIllegalArgumentExceptionOnSerializationFailure() {
        // GIVEN
        var converter = new FailingRequestConverter();
        Object content = new Object();

        // WHEN & THEN
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.toBodyPublisher(content),
                "Should throw IllegalArgumentException on serialization failure"
        );

        assertEquals("Serialization failed", exception.getMessage());
        assertNotNull(exception.getCause(), "Exception should have a cause");
    }

    @Test
    void toBodyPublisherDoesNotThrowForNullEvenWithFailingConverter() {
        // GIVEN
        var converter = new FailingRequestConverter();

        // WHEN
        HttpRequest.BodyPublisher result = converter.toBodyPublisher(null);

        // THEN
        assertNotNull(result, "Should return noBody() for null, not throw");
        assertEquals(0, result.contentLength(), "Should return noBody() with content length 0");
    }

    @Test
    void contentTypeReturnsNonNull() {
        // GIVEN
        var converter = new StringRequestConverter();

        // WHEN
        ContentType result = converter.contentType();

        // THEN
        assertNotNull(result, "contentType() must never return null");
        assertEquals(ContentType.TEXT_PLAIN, result);
    }

    @Test
    void contentTypeJsonConverter() {
        // GIVEN
        var converter = new FailingRequestConverter();

        // WHEN
        ContentType result = converter.contentType();

        // THEN
        assertEquals(ContentType.APPLICATION_JSON, result);
    }

    @Test
    void toBodyPublisherWithSimulatedFailure() {
        // GIVEN
        var converter = new StringRequestConverter(true);
        String content = "will fail";

        // WHEN & THEN
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.toBodyPublisher(content),
                "Should throw on serialization failure"
        );

        assertEquals("Simulated serialization failure", exception.getMessage());
    }

    @Test
    void toBodyPublisherEmptyStringIsNotNull() {
        // GIVEN
        var converter = new StringRequestConverter();
        String content = "";

        // WHEN
        HttpRequest.BodyPublisher result = converter.toBodyPublisher(content);

        // THEN
        assertNotNull(result, "Empty string should still create body publisher");
        // Empty string still has content length 0, but it's not noBody()
        assertEquals(0, result.contentLength(), "Empty string has content length 0");
    }
}

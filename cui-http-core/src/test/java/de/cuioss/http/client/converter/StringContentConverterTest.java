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
package de.cuioss.http.client.converter;

import de.cuioss.http.client.ContentType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StringContentConverter}.
 *
 * @author Oliver Wolff
 */
class StringContentConverterTest {

    /**
     * "Gruesse" spelled with U+00FC (u-umlaut) and U+00DF (sharp s). Both code points encode to a
     * single byte in ISO-8859-1 and to two bytes in UTF-8, so decoding with the wrong charset is
     * observable. Built from code points rather than written as a literal so the test does not
     * depend on the source-file encoding.
     */
    private static final String NON_ASCII_TEXT = new String(new char[]{'G', 'r', 0x00FC, 0x00DF, 'e'});

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
    @DisplayName("Identity converter should return TEXT_PLAIN as content type")
    void identityConverterShouldReturnTextPlainAsContentType() {
        StringContentConverter<String> converter = StringContentConverter.identity();

        ContentType contentType = converter.contentType();

        assertEquals(ContentType.TEXT_PLAIN, contentType);
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

            @Override
            public ContentType contentType() {
                return ContentType.TEXT_PLAIN;
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

        // Test content type
        assertEquals(ContentType.TEXT_PLAIN, converter.contentType());
    }

    @Test
    @DisplayName("Subclass must implement contentType() method")
    void subclassMustImplementContentTypeMethod() {
        // Test that contentType() returns correct value
        StringContentConverter<String> converter = new StringContentConverter<String>() {
            @Override
            protected Optional<String> convertString(String rawContent) {
                return Optional.ofNullable(rawContent);
            }

            @Override
            public ContentType contentType() {
                return ContentType.APPLICATION_JSON;
            }
        };

        assertEquals(ContentType.APPLICATION_JSON, converter.contentType());
    }

    @Nested
    @DisplayName("Charset precedence: response-declared wins, constructor charset is the fallback")
    class CharsetPrecedence {

        @Test
        @DisplayName("Response-declared ISO-8859-1 wins over a UTF-8 constructor charset")
        void responseDeclaredCharsetWinsOverConstructorCharset() throws Exception {
            StringContentConverter<String> converter = identityConverter(StandardCharsets.UTF_8);

            String decoded = decodeBody(converter, "text/plain; charset=ISO-8859-1",
                    NON_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

            assertEquals(NON_ASCII_TEXT, decoded);
        }

        @Test
        @DisplayName("Response-declared UTF-8 leaves the default converter's behaviour unchanged")
        void responseDeclaredUtf8LeavesDefaultBehaviourUnchanged() throws Exception {
            StringContentConverter<String> converter = identityConverter(null);

            String decoded = decodeBody(converter, ContentType.TEXT_PLAIN.toHeaderValue(),
                    NON_ASCII_TEXT.getBytes(StandardCharsets.UTF_8));

            assertEquals(NON_ASCII_TEXT, decoded);
        }

        @Test
        @DisplayName("A quoted charset parameter is honoured")
        void quotedCharsetParameterIsHonoured() throws Exception {
            StringContentConverter<String> converter = identityConverter(StandardCharsets.UTF_8);

            String decoded = decodeBody(converter, "text/plain; charset=\"ISO-8859-1\"",
                    NON_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

            assertEquals(NON_ASCII_TEXT, decoded);
        }

        @Test
        @DisplayName("The charset parameter name is matched case-insensitively")
        void charsetParameterNameIsMatchedCaseInsensitively() throws Exception {
            StringContentConverter<String> converter = identityConverter(StandardCharsets.UTF_8);

            String decoded = decodeBody(converter, "text/plain; Charset=ISO-8859-1",
                    NON_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

            assertEquals(NON_ASCII_TEXT, decoded);
        }

        @Test
        @DisplayName("A Content-Type without a charset parameter uses the constructor charset")
        void missingCharsetParameterUsesConstructorCharset() throws Exception {
            StringContentConverter<String> converter = identityConverter(StandardCharsets.ISO_8859_1);

            String decoded = decodeBody(converter, "text/plain",
                    NON_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

            assertEquals(NON_ASCII_TEXT, decoded);
        }

        @Test
        @DisplayName("An absent Content-Type header uses the constructor charset")
        void absentContentTypeHeaderUsesConstructorCharset() throws Exception {
            StringContentConverter<String> converter = identityConverter(StandardCharsets.ISO_8859_1);

            String decoded = decodeBody(converter, null,
                    NON_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

            assertEquals(NON_ASCII_TEXT, decoded);
        }

        @Test
        @DisplayName("An unsupported charset name falls back to the constructor charset without throwing")
        void unsupportedCharsetFallsBackWithoutThrowing() throws Exception {
            StringContentConverter<String> converter = identityConverter(StandardCharsets.ISO_8859_1);

            String decoded = decodeBody(converter, "text/plain; charset=X-NO-SUCH-CHARSET",
                    NON_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

            assertEquals(NON_ASCII_TEXT, decoded);
        }

        @Test
        @DisplayName("A malformed charset token falls back to the constructor charset without throwing")
        void malformedCharsetFallsBackWithoutThrowing() throws Exception {
            StringContentConverter<String> converter = identityConverter(StandardCharsets.ISO_8859_1);

            String decoded = decodeBody(converter, "text/plain; charset=\"\"",
                    NON_ASCII_TEXT.getBytes(StandardCharsets.ISO_8859_1));

            assertEquals(NON_ASCII_TEXT, decoded);
        }
    }

    /**
     * Creates an identity {@link StringContentConverter} bound to the supplied fallback charset.
     *
     * @param fallbackCharset the constructor charset, or {@code null} to exercise the no-arg
     *                        constructor's UTF-8 default
     * @return a converter that returns its input unchanged
     */
    private static StringContentConverter<String> identityConverter(@Nullable Charset fallbackCharset) {
        if (fallbackCharset == null) {
            return new StringContentConverter<String>() {
                @Override
                protected Optional<String> convertString(@Nullable String rawContent) {
                    return Optional.ofNullable(rawContent);
                }

                @Override
                public ContentType contentType() {
                    return ContentType.TEXT_PLAIN;
                }
            };
        }
        return new StringContentConverter<String>(fallbackCharset) {
            @Override
            protected Optional<String> convertString(@Nullable String rawContent) {
                return Optional.ofNullable(rawContent);
            }

            @Override
            public ContentType contentType() {
                return ContentType.TEXT_PLAIN;
            }
        };
    }

    /**
     * Drives the converter's {@link HttpResponse.BodyHandler} over a synthetic response so the
     * charset it actually decodes with is observable.
     *
     * @param converter   the converter under test
     * @param contentType the {@code Content-Type} header value, or {@code null} to omit the header
     * @param body        the raw response bytes
     * @return the decoded body
     * @throws Exception if the body subscriber does not complete
     */
    @SuppressWarnings("unchecked")
    private static String decodeBody(StringContentConverter<String> converter,
            @Nullable String contentType, byte[] body) throws Exception {
        HttpResponse.BodySubscriber<String> subscriber =
                (HttpResponse.BodySubscriber<String>) converter.getBodyHandler().apply(responseInfo(contentType));
        subscriber.onSubscribe(new NoOpSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(body)));
        subscriber.onComplete();
        return subscriber.getBody().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static HttpResponse.ResponseInfo responseInfo(@Nullable String contentType) {
        Map<String, List<String>> rawHeaders = contentType == null
                ? Map.of()
                : Map.of("Content-Type", List.of(contentType));
        return new TestResponseInfo(200, HttpHeaders.of(rawHeaders, (name, value) -> true),
                HttpClient.Version.HTTP_1_1);
    }

    private record TestResponseInfo(int statusCode, HttpHeaders headers, HttpClient.Version version)
            implements HttpResponse.ResponseInfo {
    }

    /**
     * Demand is irrelevant here — the test pushes the whole body in one {@code onNext}.
     */
    private static final class NoOpSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
            // no demand tracking needed: the test delivers the complete body synchronously
        }

        @Override
        public void cancel() {
            // never cancelled by the test
        }
    }
}

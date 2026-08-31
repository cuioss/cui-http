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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Optional;

/**
 * Base class for content converters that process String-based HTTP responses.
 * <p>
 * This converter is suitable for text-based content types such as JSON, XML, HTML, and plain text.
 * <p>
 * <strong>Charset precedence:</strong> the charset declared by the response wins. The response's
 * {@code Content-Type} header is inspected for a {@code charset} parameter, and the body is decoded
 * with that charset whenever it names a charset this JVM supports. The charset passed to the
 * constructor (UTF-8 by default) is the <em>fallback</em>, applied when the response declares no
 * {@code charset} parameter, or declares one that is malformed or unsupported. A bogus charset token
 * never fails the conversion — it falls back silently.
 * <p>
 * Subclasses need only implement the conversion logic and content type declaration.
 * The String raw type handling is managed internally.
 *
 * @param <T> the target type for content conversion
 * @author Oliver Wolff
 * @since 1.0
 */
public abstract class StringContentConverter<T> implements HttpResponseConverter<T> {

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String CHARSET_PARAMETER = "charset=";

    private final Charset charset;

    /**
     * Creates a String content converter with UTF-8 as the fallback charset.
     */
    protected StringContentConverter() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * Creates a String content converter with specified charset.
     *
     * @param charset the fallback charset, used when the response declares no usable charset
     */
    protected StringContentConverter(@NonNull Charset charset) {
        this.charset = charset;
    }

    // S1452: False positive - wildcard type required for flexible body handler API
    // JDK BodyHandler design requires type flexibility (String, byte[], Void, etc.)
    // Callers use the handler to read response bodies, not to access type-specific operations
    @SuppressWarnings("java:S1452")
    @Override
    public HttpResponse.BodyHandler<?> getBodyHandler() {
        HttpResponse.BodyHandler<String> handler =
                responseInfo -> HttpResponse.BodySubscribers.ofString(resolveCharset(responseInfo));
        return handler;
    }

    /**
     * Resolves the charset to decode the response body with.
     * <p>
     * The charset declared by the response's {@code Content-Type} header wins; the constructor
     * charset is the fallback for a response that declares no charset, or one that is malformed or
     * unsupported.
     *
     * @param responseInfo the response status line and headers, never {@code null}
     * @return the charset to decode with, never {@code null}
     */
    private Charset resolveCharset(HttpResponse.ResponseInfo responseInfo) {
        return responseInfo.headers().firstValue(CONTENT_TYPE_HEADER)
                .flatMap(StringContentConverter::parseDeclaredCharset)
                .orElse(charset);
    }

    /**
     * Extracts the {@code charset} parameter from a {@code Content-Type} header value.
     *
     * @param contentTypeHeader the raw header value, e.g. {@code "text/plain; charset=ISO-8859-1"}
     * @return the declared charset, or empty when none is declared or the declared token is
     * malformed or unsupported on this JVM
     */
    private static Optional<Charset> parseDeclaredCharset(String contentTypeHeader) {
        for (String parameter : contentTypeHeader.split(";")) {
            String trimmed = parameter.trim();
            if (trimmed.regionMatches(true, 0, CHARSET_PARAMETER, 0, CHARSET_PARAMETER.length())) {
                return toSupportedCharset(unquote(trimmed.substring(CHARSET_PARAMETER.length()).trim()));
            }
        }
        return Optional.empty();
    }

    /**
     * Strips a surrounding pair of double quotes from a header parameter value.
     *
     * @param value the raw parameter value
     * @return the value without its enclosing quotes, or the value unchanged when it is not quoted
     */
    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * Resolves a charset name to a {@link Charset} without throwing on a bogus name.
     *
     * @param charsetName the declared charset name
     * @return the resolved charset, or empty when the name is malformed or unsupported
     */
    private static Optional<Charset> toSupportedCharset(String charsetName) {
        try {
            return Optional.of(Charset.forName(charsetName));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<T> convert(@Nullable Object rawContent) {
        // Cast to String since our BodyHandler produces String content
        return convertString((String) rawContent);
    }

    /**
     * Converts String content to the target type.
     * This method is called by the public convert method after casting.
     *
     * @param rawContent the raw String content from HTTP response, may be {@code null}
     * @return Optional containing converted content, or empty if conversion fails
     */
    protected abstract Optional<T> convertString(@Nullable String rawContent);

    /**
     * Identity converter for String content (no conversion needed).
     * <p>
     * This is the most basic String converter that returns the input unchanged,
     * suitable for cases where the raw String response is the desired result.
     *
     * @return converter that returns the input String unchanged
     */
    public static StringContentConverter<String> identity() {
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
}
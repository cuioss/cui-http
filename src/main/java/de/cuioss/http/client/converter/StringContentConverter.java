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

import lombok.NonNull;

import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Base class for content converters that process String-based HTTP responses.
 * <p>
 * This converter is suitable for text-based content types such as JSON, XML, HTML, and plain text.
 * It uses HttpResponse.BodyHandlers.ofString() with configurable charset support.
 * <p>
 * Subclasses need only implement the conversion logic and empty value provision.
 * The String raw type handling is managed internally.
 *
 * @param <T> the target type for content conversion
 * @author Oliver Wolff
 * @since 1.0
 */
public abstract class StringContentConverter<T> implements HttpContentConverter<T> {

    private final Charset charset;

    /**
     * Creates a String content converter with UTF-8 charset.
     */
    protected StringContentConverter() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * Creates a String content converter with specified charset.
     *
     * @param charset the charset to use for String decoding
     */
    protected StringContentConverter(@NonNull Charset charset) {
        this.charset = charset;
    }

    @Override
    public HttpResponse.BodyHandler<?> getBodyHandler() {
        return HttpResponse.BodyHandlers.ofString(charset);
    }

    @Override
    public Optional<T> convert(Object rawContent) {
        // Cast to String since our BodyHandler produces String content
        return convertString((String) rawContent);
    }

    /**
     * Converts String content to the target type.
     * This method is called by the public convert method after casting.
     *
     * @param rawContent the raw String content from HTTP response
     * @return Optional containing converted content, or empty if conversion fails
     */
    protected abstract Optional<T> convertString(String rawContent);

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
            protected Optional<String> convertString(String rawContent) {
                return Optional.ofNullable(rawContent);
            }

            @Override
            @NonNull
            public String emptyValue() {
                return "";
            }
        };
    }
}
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
import java.util.Optional;

/**
 * Content converter for transforming HTTP response bodies into typed objects with proper BodyHandler support.
 * <p>
 * This interface provides both content conversion and appropriate BodyHandler selection,
 * allowing ResilientHttpHandler to leverage Java HTTP Client's type-safe body handling.
 * The raw response type is an implementation detail hidden from clients.
 * <p>
 * Implementations should handle conversion errors gracefully by returning Optional.empty()
 * when conversion fails or when there is no meaningful content to convert.
 *
 * @param <T> the target type for content conversion
 * @author Oliver Wolff
 * @since 1.0
 */
public interface HttpContentConverter<T> {

    /**
     * Converts raw HTTP response body to the target type.
     * <p>
     * Returns Optional.empty() when:
     * <ul>
     *   <li>Conversion fails due to malformed content</li>
     *   <li>Content is empty or null</li>
     *   <li>Content cannot be meaningfully converted</li>
     * </ul>
     *
     * @param rawContent the raw HTTP response body (may be null or empty)
     * @return Optional containing converted content, or empty if conversion fails
     */
    Optional<T> convert(Object rawContent);

    /**
     * Provides the appropriate BodyHandler for this converter.
     * <p>
     * This method enables proper leveraging of Java HTTP Client's type-safe body handling,
     * avoiding unnecessary intermediate conversions and preserving data integrity.
     * The raw type is handled internally by the implementation.
     * <p>
     * Examples:
     * <ul>
     *   <li>For JSON/XML: HttpResponse.BodyHandlers.ofString(charset)</li>
     *   <li>For binary data: HttpResponse.BodyHandlers.ofByteArray()</li>
     *   <li>For large content: HttpResponse.BodyHandlers.ofInputStream()</li>
     * </ul>
     *
     * @return the BodyHandler appropriate for this converter
     */
    @SuppressWarnings("java:S1452")
    HttpResponse.BodyHandler<?> getBodyHandler();

    /**
     * Provides a semantically correct empty value for this content type.
     * <p>
     * This method should return a meaningful "empty" representation that makes sense
     * for the target type T, rather than trying to convert meaningless input.
     * <p>
     * Examples:
     * <ul>
     *   <li>For JSON: empty JsonNode or empty object</li>
     *   <li>For String: empty string</li>
     *   <li>For Collections: empty collection</li>
     *   <li>For custom objects: default/empty instance</li>
     * </ul>
     *
     * @return semantically correct empty value for type T, never null
     */
    @NonNull
    T emptyValue();
}
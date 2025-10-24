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
 * <p>
 * <strong>DEPRECATION NOTICE:</strong> This interface is deprecated and will be removed in version 2.0.
 * It has been replaced by separate, more focused interfaces:
 * <ul>
 *     <li>{@link HttpResponseConverter} for HTTP response to typed object conversion</li>
 *     <li>{@link HttpRequestConverter} for typed object to HTTP request body conversion</li>
 * </ul>
 * <p>
 * <strong>Migration Guide:</strong>
 * <p>
 * <strong>Before (using HttpContentConverter):</strong>
 * <pre>{@code
 * public class UserConverter implements HttpContentConverter<User> {
 *     @Override
 *     public Optional<User> convert(Object rawContent) {
 *         return Optional.ofNullable(parseJson((String) rawContent, User.class));
 *     }
 *
 *     @Override
 *     public HttpResponse.BodyHandler<?> getBodyHandler() {
 *         return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
 *     }
 *
 *     @Override
 *     public User emptyValue() {
 *         return User.EMPTY;  // Not actually used by HttpResult<T>
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>After (using HttpResponseConverter):</strong>
 * <pre>{@code
 * public class UserResponseConverter implements HttpResponseConverter<User> {
 *     @Override
 *     public Optional<User> convert(Object rawContent) {
 *         return Optional.ofNullable(parseJson((String) rawContent, User.class));
 *     }
 *
 *     @Override
 *     public HttpResponse.BodyHandler<?> getBodyHandler() {
 *         return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
 *     }
 *
 *     @Override
 *     public ContentType contentType() {
 *         return ContentType.APPLICATION_JSON;
 *     }
 *
 *     // NO emptyValue() - HttpResult<T> uses Optional<T> directly
 * }
 * }</pre>
 * <p>
 * <strong>For bidirectional conversion (request + response):</strong>
 * <pre>{@code
 * public class UserConverter implements HttpResponseConverter<User>, HttpRequestConverter<User> {
 *     // Response conversion (same as before)
 *     @Override
 *     public Optional<User> convert(Object rawContent) {
 *         return Optional.ofNullable(parseJson((String) rawContent, User.class));
 *     }
 *
 *     @Override
 *     public HttpResponse.BodyHandler<?> getBodyHandler() {
 *         return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
 *     }
 *
 *     @Override
 *     public ContentType contentType() {
 *         return ContentType.APPLICATION_JSON;
 *     }
 *
 *     // Request conversion (NEW)
 *     @Override
 *     public HttpRequest.BodyPublisher toBodyPublisher(@Nullable User content) {
 *         if (content == null) {
 *             return HttpRequest.BodyPublishers.noBody();
 *         }
 *         String json = toJson(content);
 *         return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
 *     }
 * }
 *
 * // Usage with new HttpAdapter
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)  // Response conversion
 *     .requestConverter(userConverter)    // Request conversion (same instance!)
 *     .build();
 * }</pre>
 * <p>
 * <strong>Key Migration Benefits:</strong>
 * <ul>
 *     <li>No more unused {@code emptyValue()} method - {@code HttpResult<T>} uses {@code Optional<T>} directly</li>
 *     <li>Explicit {@code ContentType} declaration for proper header handling</li>
 *     <li>Separate interfaces allow implementing only what you need (response-only, request-only, or both)</li>
 *     <li>Better type safety with request body conversion</li>
 * </ul>
 *
 * @param <T> the target type for content conversion
 * @author Oliver Wolff
 * @since 1.0
 * @deprecated Use {@link HttpResponseConverter} for response conversion and {@link HttpRequestConverter} for request conversion.
 *             This interface will be removed in version 2.0.
 */
@Deprecated(since = "1.0", forRemoval = true)
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
     * <p>
     * <strong>DEPRECATED:</strong> This method is no longer used by the new {@link HttpResult} design,
     * which uses {@link Optional} to represent empty values instead of requiring a concrete empty instance.
     * The default implementation returns {@code null}, which is safe because the method is never called
     * by the new adapter implementations.
     * <p>
     * When migrating to {@link HttpResponseConverter}, simply remove this method - it is not needed.
     *
     * @return semantically correct empty value for type T, or null in default implementation
     * @deprecated Not used by HttpResult design. HttpResult uses Optional&lt;T&gt; directly.
     *             This method will be removed along with this interface in version 2.0.
     */
    @Deprecated(since = "1.0", forRemoval = true)
    default T emptyValue() {
        return null;  // Not used by new HttpResult<T> design
    }
}
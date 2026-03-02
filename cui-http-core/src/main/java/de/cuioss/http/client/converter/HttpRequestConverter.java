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

import java.net.http.HttpRequest;

/**
 * Converts typed objects to HTTP request bodies.
 * <p>
 * This interface handles the serialization of typed objects into HTTP request body publishers.
 * It defines contracts for null handling and error handling to ensure consistent behavior
 * across all implementations.
 * </p>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: JSON Request Converter</h3>
 * <pre>{@code
 * public class JsonRequestConverter<T> implements HttpRequestConverter<T> {
 *     private final ObjectMapper objectMapper = new ObjectMapper();
 *
 *     @Override
 *     public HttpRequest.BodyPublisher toBodyPublisher(@Nullable T content) {
 *         if (content == null) {
 *             return HttpRequest.BodyPublishers.noBody();
 *         }
 *         try {
 *             String json = objectMapper.writeValueAsString(content);
 *             return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
 *         } catch (JsonProcessingException e) {
 *             throw new IllegalArgumentException("Failed to serialize to JSON", e);
 *         }
 *     }
 *
 *     @Override
 *     public ContentType contentType() {
 *         return ContentType.APPLICATION_JSON;
 *     }
 * }
 * }</pre>
 *
 * <h3>Example 2: Combined Request/Response Converter</h3>
 * <pre>{@code
 * // For CRUD operations where request and response use the same type
 * public class UserConverter implements HttpRequestConverter<User>, HttpResponseConverter<User> {
 *     // Implement both interfaces for bidirectional conversion
 *     @Override
 *     public HttpRequest.BodyPublisher toBodyPublisher(@Nullable User user) {
 *         // Request serialization
 *     }
 *
 *     @Override
 *     public Optional<User> convert(Object rawContent) {
 *         // Response deserialization
 *     }
 *
 *     @Override
 *     public ContentType contentType() {
 *         return ContentType.APPLICATION_JSON;
 *     }
 * }
 * }</pre>
 *
 * @param <R> Request body type
 * @since 1.0
 */
public interface HttpRequestConverter<R> {

    /**
     * Converts typed object to HTTP request body publisher.
     * <p>
     * <strong>Null Handling Contract:</strong> If content is null, implementations
     * MUST return {@code HttpRequest.BodyPublishers.noBody()}. This is used for
     * requests that don't require a body (e.g., GET, DELETE).
     * </p>
     * <p>
     * <strong>Error Handling Contract:</strong>
     * </p>
     * <ul>
     *   <li>On null content: Return {@code HttpRequest.BodyPublishers.noBody()}</li>
     *   <li>On serialization failure: Throw {@code IllegalArgumentException} with cause -
     *       adapter will create {@code HttpResult.failure()} with {@code HttpErrorCategory.INVALID_CONTENT}</li>
     *   <li>Never return {@code noBody()} for serialization failures - throw instead</li>
     * </ul>
     *
     * @param content The content to serialize, may be null
     * @return BodyPublisher for the HTTP request. Never null.
     *         Returns {@code HttpRequest.BodyPublishers.noBody()} for null content only.
     * @throws IllegalArgumentException if serialization fails
     */
    HttpRequest.BodyPublisher toBodyPublisher(@Nullable R content);

    /**
     * Returns the content type for requests.
     * <p>
     * This is used to set the Content-Type header on HTTP requests.
     * </p>
     *
     * @return Content type (e.g., APPLICATION_JSON, TEXT_XML). Never null.
     */
    ContentType contentType();
}

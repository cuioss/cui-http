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

import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * Converts HTTP response bodies to typed objects.
 * <p>
 * This interface provides a contract for converting raw HTTP response content
 * into strongly-typed Java objects. Implementations handle deserialization from
 * various content types (JSON, XML, text, etc.) while following a consistent
 * error handling pattern.
 * </p>
 *
 * <h2>Error Handling Contract</h2>
 * <p>
 * Converters must follow a non-throwing error handling pattern:
 * </p>
 * <ul>
 *   <li><strong>Success:</strong> Return {@code Optional.of(parsedObject)} when conversion succeeds</li>
 *   <li><strong>Parsing Failure:</strong> Return {@code Optional.empty()} when conversion fails
 *       (adapter will create {@code HttpResult.failure()} with {@code HttpErrorCategory.INVALID_CONTENT})</li>
 *   <li><strong>Never throw exceptions:</strong> Always return {@code Optional.empty()} on failure</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>JSON Response Converter</h3>
 * <pre>{@code
 * public class JsonResponseConverter<T> implements HttpResponseConverter<T> {
 *     private final ObjectMapper mapper;
 *     private final Class<T> type;
 *
 *     @Override
 *     public Optional<T> convert(Object rawContent) {
 *         if (rawContent == null || !(rawContent instanceof String json)) {
 *             return Optional.empty();
 *         }
 *         try {
 *             return Optional.of(mapper.readValue(json, type));
 *         } catch (JsonProcessingException e) {
 *             LOGGER.debug("JSON parsing failed", e);
 *             return Optional.empty(); // Not throwing!
 *         }
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
 * }
 * }</pre>
 *
 * <h3>XML Response Converter</h3>
 * <pre>{@code
 * public class XmlResponseConverter<T> implements HttpResponseConverter<T> {
 *     private final JAXBContext context;
 *
 *     @Override
 *     public Optional<T> convert(Object rawContent) {
 *         if (rawContent == null || !(rawContent instanceof String xml)) {
 *             return Optional.empty();
 *         }
 *         try {
 *             Unmarshaller unmarshaller = context.createUnmarshaller();
 *             @SuppressWarnings("unchecked")
 *             T result = (T) unmarshaller.unmarshal(new StringReader(xml));
 *             return Optional.of(result);
 *         } catch (JAXBException e) {
 *             LOGGER.debug("XML parsing failed", e);
 *             return Optional.empty();
 *         }
 *     }
 *
 *     @Override
 *     public HttpResponse.BodyHandler<?> getBodyHandler() {
 *         return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
 *     }
 *
 *     @Override
 *     public ContentType contentType() {
 *         return ContentType.APPLICATION_XML;
 *     }
 * }
 * }</pre>
 *
 * @param <T> Response body type after conversion
 * @since 1.0
 */
public interface HttpResponseConverter<T> {

    /**
     * Converts HTTP response body to typed object.
     * <p>
     * <strong>Error Handling Contract:</strong>
     * </p>
     * <ul>
     *   <li>On success: Return {@code Optional.of(parsedObject)}</li>
     *   <li>On parsing failure: Return {@code Optional.empty()} - adapter will create
     *       {@code HttpResult.failure()} with {@code HttpErrorCategory.INVALID_CONTENT}</li>
     *   <li>Never throw exceptions - return {@code Optional.empty()} instead</li>
     * </ul>
     *
     * @param rawContent Raw response content from HTTP response (typically String or byte[])
     * @return Converted object, or {@code Optional.empty()} if conversion failed
     */
    Optional<T> convert(Object rawContent);

    /**
     * Returns body handler for HTTP response processing.
     * <p>
     * The body handler determines how the HTTP response body is read from the network.
     * Common handlers include:
     * </p>
     * <ul>
     *   <li>{@code BodyHandlers.ofString(charset)} - for text-based content (JSON, XML, HTML)</li>
     *   <li>{@code BodyHandlers.ofByteArray()} - for binary content (images, PDFs)</li>
     *   <li>{@code BodyHandlers.discarding()} - when only status code matters</li>
     * </ul>
     *
     * @return BodyHandler appropriate for this content type
     */
    HttpResponse.BodyHandler<?> getBodyHandler();

    /**
     * Returns the expected content type for responses.
     * <p>
     * This content type is used to set the {@code Accept} header in requests
     * and to validate that responses contain the expected content type.
     * </p>
     *
     * @return Content type (e.g., APPLICATION_JSON, TEXT_XML, APPLICATION_PDF)
     */
    ContentType contentType();
}

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
 * Built-in converter for Void responses (status code only).
 * <p>
 * Use when response body is ignored - only HTTP status matters.
 * This converter efficiently discards the response body without reading it
 * from the network, making it ideal for operations where only the status
 * code is significant.
 * </p>
 *
 * <h2>Common Use Cases</h2>
 * <ul>
 *   <li><strong>DELETE /resource/123:</strong> Returns 204 No Content - only need confirmation of deletion</li>
 *   <li><strong>HEAD /health:</strong> Returns 200 OK - only checking resource existence/health</li>
 *   <li><strong>POST /webhooks:</strong> Returns 200 OK - fire and forget operations</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: DELETE operation (status code only)</h3>
 * <pre>{@code
 * HttpAdapter<Void> adapter = ETagAwareHttpAdapter.<Void>builder()
 *     .httpHandler(handler)
 *     .responseConverter(VoidResponseConverter.INSTANCE)  // Built-in converter
 *     .build();
 *
 * // DELETE - only care about success/failure
 * HttpResult<Void> result = adapter.delete();
 * if (result.isSuccess()) {
 *     LOGGER.info("Resource deleted (status: {})", result.getHttpStatus().orElse(0));
 * }
 * }</pre>
 *
 * <h3>Example 2: HEAD operation (health check)</h3>
 * <pre>{@code
 * HttpAdapter<Void> adapter = ETagAwareHttpAdapter.<Void>builder()
 *     .httpHandler(handler)
 *     .responseConverter(VoidResponseConverter.INSTANCE)
 *     .build();
 *
 * // HEAD - only care about status
 * HttpResult<Void> healthCheck = adapter.head();
 * boolean isHealthy = healthCheck.isSuccess();
 * }</pre>
 *
 * <h3>Example 3: Using factory method (convenience)</h3>
 * <pre>{@code
 * // Shorthand for status-code-only operations
 * HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);
 *
 * // Fire-and-forget POST
 * HttpResult<Void> result = adapter.post(null);
 * }</pre>
 *
 * @since 1.0
 */
public final class VoidResponseConverter implements HttpResponseConverter<Void> {

    /**
     * Singleton instance - no need to create multiple instances.
     * <p>
     * Since this converter has no state and always behaves the same way,
     * use this shared instance rather than creating new ones.
     * </p>
     */
    public static final VoidResponseConverter INSTANCE = new VoidResponseConverter();

    /**
     * Private constructor to enforce singleton pattern.
     * Use {@link #INSTANCE} instead.
     */
    private VoidResponseConverter() {
        // Use INSTANCE
    }

    /**
     * Always returns {@code Optional.empty()} since the response body is discarded.
     * <p>
     * This converter is designed for operations where only the HTTP status code
     * matters. The body is never read from the network (see {@link #getBodyHandler()}),
     * so this method will always receive null or an empty value and return empty.
     * </p>
     *
     * @param rawContent Raw response content (always null or empty since body is discarded)
     * @return {@code Optional.empty()} - body is always discarded
     */
    @Override
    public Optional<Void> convert(Object rawContent) {
        return Optional.empty();  // Always empty - body is discarded
    }

    /**
     * Returns a body handler that efficiently discards the response body.
     * <p>
     * Uses {@link HttpResponse.BodyHandlers#discarding()} which doesn't read
     * the response body from the network at all, making this very efficient
     * for operations where only the status code matters.
     * </p>
     *
     * @return Discarding body handler - doesn't read body from network
     */
    // S1452: False positive - wildcard type required for flexible body handler API
    // JDK BodyHandler design requires type flexibility (String, byte[], Void, etc.)
    // Callers use the handler to read response bodies, not to access type-specific operations
    @SuppressWarnings("java:S1452")
    @Override
    public HttpResponse.BodyHandler<?> getBodyHandler() {
        return HttpResponse.BodyHandlers.discarding();  // Efficient - don't read body
    }

    /**
     * Returns {@code ContentType.APPLICATION_JSON} (value doesn't matter).
     * <p>
     * Since the body is discarded, the content type is irrelevant. We return
     * APPLICATION_JSON as a sensible default, but this value is never used
     * in practice because {@link #getBodyHandler()} discards the body entirely.
     * </p>
     *
     * @return {@code ContentType.APPLICATION_JSON} (arbitrary choice, body discarded anyway)
     */
    @Override
    public ContentType contentType() {
        return ContentType.APPLICATION_JSON;  // Doesn't matter, body discarded
    }
}

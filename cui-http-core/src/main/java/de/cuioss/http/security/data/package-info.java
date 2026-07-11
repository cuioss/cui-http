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
/**
 * Data models and records for HTTP components.
 *
 * <p>This package provides immutable data models representing various HTTP components
 * that can be validated. All data models are implemented as records for maximum
 * immutability and thread safety.</p>
 *
 * <h3>Data Models</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.data.URLParameter} - URL query parameter with key-value pair</li>
 *   <li>{@link de.cuioss.http.security.data.Cookie} - HTTP cookie with attributes</li>
 *   <li>{@link de.cuioss.http.security.data.HTTPBody} - HTTP request/response body with content type</li>
 *   <li>{@link de.cuioss.http.security.data.AttributeParser} - Utility for parsing attribute strings</li>
 * </ul>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Immutability</strong> - All data models are immutable after construction</li>
 *   <li><strong>Value Semantics</strong> - Records provide automatic equals, hashCode, and toString</li>
 *   <li><strong>Thread Safety</strong> - Safe for concurrent access without synchronization</li>
 *   <li><strong>Convenience Predicates</strong> - Built-in methods for content classification
 *       (e.g. {@code hasName()}, {@code hasValue()}, {@code isFlag()}, {@code isJson()})</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre><code>
 * // URL Parameter (name, value)
 * URLParameter param = new URLParameter("search", "user input");
 * if (param.isFlag()) {
 *     // Handle valueless flag parameter (e.g. "?debug")
 * }
 *
 * // Cookie (name, value, attributes) - attributes are a single attribute string
 * Cookie cookie = new Cookie("sessionId", "abc123", "HttpOnly; Secure");
 * if (cookie.isSecure()) {
 *     // Cookie carries the Secure attribute
 * }
 *
 * // HTTP Body - use the content-type factory methods (content, contentType, encoding)
 * HTTPBody body = HTTPBody.json(jsonContent);
 * if (body.isJson()) {
 *     // Content is application/json
 * }
 * </code></pre>
 *
 * <h3>Package Nullability</h3>
 * <p>This package follows strict nullability conventions using JSpecify annotations:</p>
 * <ul>
 *   <li>All parameters and return values are non-null by default</li>
 *   <li>Nullable parameters and return values are explicitly annotated with {@code @Nullable}</li>
 *   <li>Collection and map values may be null where semantically appropriate</li>
 * </ul>
 *
 * @since 1.0
 * @see de.cuioss.http.security.core.ValidationType
 * @see de.cuioss.http.security.pipeline
 */
@NullMarked
package de.cuioss.http.security.data;

import org.jspecify.annotations.NullMarked;
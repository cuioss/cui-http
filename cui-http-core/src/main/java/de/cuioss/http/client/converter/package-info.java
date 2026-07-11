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
 * HTTP request/response content converters.
 * <p>
 * This package defines the conversion contracts used by the HTTP adapters to serialize request
 * bodies and deserialize response bodies, plus a small set of built-in converters. It contains no
 * JSON library of its own - concrete JSON/XML converters are provided by callers by implementing
 * the interfaces below.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link de.cuioss.http.client.converter.HttpRequestConverter} - serializes a typed object
 *       into an HTTP request body publisher</li>
 *   <li>{@link de.cuioss.http.client.converter.HttpResponseConverter} - deserializes a raw HTTP
 *       response body into a typed object (non-throwing, {@code Optional}-based contract)</li>
 *   <li>{@link de.cuioss.http.client.converter.StringContentConverter} - abstract base for
 *       String-based (text/JSON/XML) response converters, with an {@code identity()} factory</li>
 *   <li>{@link de.cuioss.http.client.converter.VoidResponseConverter} - built-in converter that
 *       discards the response body for status-code-only operations</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@NullMarked
package de.cuioss.http.client.converter;

import org.jspecify.annotations.NullMarked;
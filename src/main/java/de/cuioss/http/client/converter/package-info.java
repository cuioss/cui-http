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
 * HTTP content converters for secure JSON processing.
 * <p>
 * This package contains converters that handle HTTP response content
 * transformation with security-focused JSON parsing capabilities.
 * <p>
 * Key components:
 * <ul>
 *   <li>Secure JSON content converters using DSL-JSON</li>
 * </ul>
 * <p>
 * The converters use DSL-JSON with configurable security limits to prevent
 * JSON-based attacks such as excessive memory consumption and deeply nested
 * structure attacks, while providing Jakarta JSON API compatibility through
 * bridge adapters.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
package de.cuioss.http.client.converter;
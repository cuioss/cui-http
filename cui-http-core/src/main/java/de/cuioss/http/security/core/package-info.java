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
 * Core interfaces and types for HTTP security validation.
 *
 * <p>This package provides the fundamental building blocks for HTTP security validation,
 * including the main validation interface, type definitions, and failure classifications.</p>
 *
 * <h3>Core Components</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.core.HttpSecurityValidator} - Main functional interface for validation</li>
 *   <li>{@link de.cuioss.http.security.core.ValidationType} - Enumeration of HTTP component types for validation</li>
 *   <li>{@link de.cuioss.http.security.core.UrlSecurityFailureType} - Classification of security failures</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * HttpSecurityValidator validator = new MyValidator();
 * try {
 *     String sanitized = validator.validate(userInput);
 *     // Process sanitized input
 * } catch (UrlSecurityException e) {
 *     // Handle security violation
 *     log.warn("Security violation: {}", e.getFailureType());
 * }
 * </code></pre>
 *
 * <h3>Package Nullability</h3>
 * <p>This package follows strict nullability conventions using JSpecify annotations:</p>
 * <ul>
 *   <li>All parameters and return values are non-null by default</li>
 *   <li>Nullable parameters and return values are explicitly annotated with {@code @Nullable}</li>
 * </ul>
 *
 * @since 1.0
 * @see de.cuioss.http.security.core.HttpSecurityValidator
 * @see de.cuioss.http.security.exceptions.UrlSecurityException
 */
@NullMarked
package de.cuioss.http.security.core;

import org.jspecify.annotations.NullMarked;
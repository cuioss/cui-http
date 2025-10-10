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
 * Exception handling for HTTP security validation failures.
 *
 * <p>This package provides exception types for representing security validation failures
 * with detailed context information. All exceptions follow the fail-fast principle and
 * provide rich information for debugging and monitoring.</p>
 *
 * <h3>Exception Types</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.exceptions.UrlSecurityException} - Main security validation exception</li>
 * </ul>
 *
 * <h3>Exception Features</h3>
 * <ul>
 *   <li><strong>Failure Type Classification</strong> - Detailed categorization via {@link de.cuioss.http.security.core.UrlSecurityFailureType}</li>
 *   <li><strong>Validation Context</strong> - Information about what was being validated via {@link de.cuioss.http.security.core.ValidationType}</li>
 *   <li><strong>Original Input</strong> - The input that caused the failure (for logging and debugging)</li>
 *   <li><strong>Sanitized Input</strong> - Partially processed input when available</li>
 *   <li><strong>Builder Pattern</strong> - Fluent construction with required and optional fields</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * try {
 *     String validated = validator.validate(userInput);
 *     // Process validated input
 * } catch (UrlSecurityException e) {
 *     // Rich exception context
 *     UrlSecurityFailureType failureType = e.getFailureType();
 *     ValidationType validationType = e.getValidationType();
 *     String originalInput = e.getOriginalInput();
 *     Optional&lt;String&gt; sanitized = e.getSanitizedInputOptional();
 *
 *     // Log security event
 *     log.warn("Security violation: {} in {} for input: {}",
 *         failureType, validationType, originalInput);
 *
 *     // Take appropriate action based on failure type
 *     switch (failureType) {
 *         case PATH_TRAVERSAL_DETECTED -&gt; blockRequest();
 *         case INVALID_CHARACTER -&gt; sanitizeAndRetry();
 *         case PATH_TOO_LONG -&gt; rejectWithError();
 *     }
 * }
 * </code></pre>
 *
 * <h3>Builder Pattern</h3>
 * <pre><code>
 * // Creating exceptions with builder
 * throw UrlSecurityException.builder()
 *     .failureType(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED)
 *     .validationType(ValidationType.URL_PATH)
 *     .originalInput(maliciousInput)
 *     .detail("Directory traversal sequence found at position 15")
 *     .build();
 * </code></pre>
 *
 * <h3>Package Nullability</h3>
 * <p>This package follows strict nullability conventions using JSpecify annotations:</p>
 * <ul>
 *   <li>All parameters and return values are non-null by default</li>
 *   <li>Nullable parameters and return values are explicitly annotated with {@code @Nullable}</li>
 *   <li>Optional fields use {@code Optional<T>} for safe access</li>
 * </ul>
 *
 * @since 1.0
 * @see de.cuioss.http.security.core.UrlSecurityFailureType
 * @see de.cuioss.http.security.core.ValidationType
 */
@NullMarked
package de.cuioss.http.security.exceptions;

import org.jspecify.annotations.NullMarked;
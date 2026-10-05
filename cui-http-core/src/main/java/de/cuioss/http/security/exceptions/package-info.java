/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
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
 *   <li><strong>Original Input</strong> - The rejected input, verbatim and attacker-controlled: never
 *       log it raw. {@code getMessage()} and {@code toString()} never reproduce it</li>
 *   <li><strong>Builder Pattern</strong> - Fluent construction with required and optional fields</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * try {
 *     Optional&lt;String&gt; validated = validator.validate(userInput);
 *     if (validated.isPresent()) {
 *         // Process validated input
 *     }
 * } catch (UrlSecurityException e) {
 *     // Rich exception context
 *     UrlSecurityFailureType failureType = e.getFailureType();
 *     ValidationType validationType = e.getValidationType();
 *
 *     // e.getMessage() is safe to log: it redacts the input to its length.
 *     // e.getOriginalInput() is attacker-controlled and must not be logged raw.
 *     String safeMessage = e.getMessage();
 *
 *     // A violation is never repaired and retried - reject or block the request
 *     switch (failureType) {
 *         case PATH_TRAVERSAL_DETECTED -&gt; blockRequest();
 *         default -&gt; rejectWithError();
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
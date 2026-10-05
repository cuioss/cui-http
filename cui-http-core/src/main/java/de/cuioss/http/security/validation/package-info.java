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
 * Individual validation stages for HTTP security checking.
 *
 * <p>This package contains the implementation of specific validation stages that can be
 * composed into validation pipelines. Each stage focuses on a specific aspect of security
 * validation and follows the fail-fast principle.</p>
 *
 * <h3>Validation Stages</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.validation.LengthValidationStage} - Input length limits per
 *       component type (first stage)</li>
 *   <li>{@link de.cuioss.http.security.validation.CharacterValidationStage} - Character set and encoding validation</li>
 *   <li>{@link de.cuioss.http.security.validation.DecodingStage} - URL percent-decoding with encoding
 *       attack detection (double encoding, UTF-8 overlong forms), followed by Unicode normalization
 *       (NFKC for URL paths, NFC for parameter values)</li>
 *   <li>{@link de.cuioss.http.security.validation.NormalizationStage} - RFC 3986 dot-segment
 *       resolution for URL paths, with traversal, root-escape and path-depth checks; a
 *       pass-through for every non-path type</li>
 *   <li>{@link de.cuioss.http.security.validation.PatternMatchingStage} - Attack pattern detection</li>
 *   <li>{@link de.cuioss.http.security.validation.AllowBlockListStage} - Case-insensitive allow and
 *       block lists for header names and content types</li>
 *   <li>{@link de.cuioss.http.security.validation.CookiePrefixValidationStage} - RFC 6265bis cookie
 *       prefix rules; standalone, invoked via
 *       {@link de.cuioss.http.security.validation.CookiePrefixValidationStage#validateCookie(de.cuioss.http.security.data.Cookie)}
 *       rather than as part of a pipeline</li>
 *   <li>{@link de.cuioss.http.security.validation.RequestCollectionValidator} - Request-level
 *       parameter, header and cookie count limits, which need the whole collection rather than a
 *       single value</li>
 *   <li>{@link de.cuioss.http.security.validation.CharacterValidationConstants} - RFC-compliant character sets</li>
 * </ul>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Immutability</strong> - All stages are immutable after construction</li>
 *   <li><strong>Thread Safety</strong> - Safe for concurrent use without synchronization</li>
 *   <li><strong>Performance</strong> - Target &lt;1ms validation times</li>
 *   <li><strong>Composability</strong> - Stages can be combined in different pipelines</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * // Individual stage usage
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * LengthValidationStage lengthStage = new LengthValidationStage(config, ValidationType.URL_PATH);
 * CharacterValidationStage charStage = new CharacterValidationStage(config, ValidationType.URL_PATH);
 *
 * // Length and character checks alone accept this traversal attempt: rejecting it is
 * // NormalizationStage's job, which is why the pipelines chain all stages in order
 * String input = "/api/../../../etc/passwd";
 * try {
 *     Optional&lt;String&gt; checked = lengthStage.validate(input);
 *     Optional&lt;String&gt; validated = checked.flatMap(charStage::validate);
 *     validated.ifPresent(value -&gt; {
 *         // Process validated input
 *     });
 * } catch (UrlSecurityException e) {
 *     // Handle security violation
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
 * @see de.cuioss.http.security.pipeline
 * @see de.cuioss.http.security.core.HttpSecurityValidator
 */
@NullMarked
package de.cuioss.http.security.validation;

import org.jspecify.annotations.NullMarked;
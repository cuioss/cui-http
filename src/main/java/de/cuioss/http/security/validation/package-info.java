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
 * Individual validation stages for HTTP security checking.
 *
 * <p>This package contains the implementation of specific validation stages that can be
 * composed into validation pipelines. Each stage focuses on a specific aspect of security
 * validation and follows the fail-fast principle.</p>
 *
 * <h3>Validation Stages</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.validation.LengthValidationStage} - Input length and depth validation (first stage)</li>
 *   <li>{@link de.cuioss.http.security.validation.CharacterValidationStage} - Character set and encoding validation</li>
 *   <li>{@link de.cuioss.http.security.validation.DecodingStage} - URL decoding and encoding attack detection</li>
 *   <li>{@link de.cuioss.http.security.validation.NormalizationStage} - Path normalization and canonicalization</li>
 *   <li>{@link de.cuioss.http.security.validation.PatternMatchingStage} - Attack pattern detection</li>
 *   <li>{@link de.cuioss.http.security.validation.CharacterValidationConstants} - RFC-compliant character sets</li>
 * </ul>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><strong>Immutability</strong> - All stages are immutable after construction</li>
 *   <li><strong>Thread Safety</strong> - Safe for concurrent use without synchronization</li>
 *   <li><strong>Performance</strong> - Optimized for &lt;1ms validation times</li>
 *   <li><strong>Composability</strong> - Stages can be combined in different pipelines</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * // Individual stage usage
 * LengthValidationStage lengthStage = new LengthValidationStage(config, ValidationType.URL_PATH);
 * CharacterValidationStage charStage = new CharacterValidationStage(config, ValidationType.URL_PATH);
 *
 * String input = "/api/../../../etc/passwd";
 * try {
 *     String checked = lengthStage.validate(input);
 *     String validated = charStage.validate(checked);
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
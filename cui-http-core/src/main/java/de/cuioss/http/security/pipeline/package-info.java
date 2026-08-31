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
 * Validation pipelines for different HTTP component types.
 *
 * <p>This package provides specialized validation pipelines that combine multiple validation
 * stages for specific HTTP components. Each pipeline detects and prevents security
 * vulnerabilities relevant to its target component type.</p>
 *
 * <h3>Available Pipelines</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.pipeline.URLPathValidationPipeline} - Path traversal and
 *       encoding attacks in a URL <em>path component</em></li>
 *   <li>{@link de.cuioss.http.security.pipeline.URLParameterValidationPipeline} - XSS and injection attacks via URL parameter values</li>
 *   <li>{@link de.cuioss.http.security.pipeline.URLParameterNameValidationPipeline} - Query parameter
 *       names (query keys), rejecting decoded delimiters and CR/LF that name-only rules forbid</li>
 *   <li>{@link de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline} - Header injection and CRLF attacks</li>
 *   <li>{@link de.cuioss.http.security.pipeline.ContentTypeValidationPipeline} - Allow/block-list
 *       enforcement for {@code Content-Type} values (list check only - no length or character validation)</li>
 *   <li>{@link de.cuioss.http.security.pipeline.PipelineFactory} - Factory for creating and configuring pipelines</li>
 * </ul>
 *
 * <h3>Pipeline Selection</h3>
 * <p>Choose the appropriate pipeline based on the HTTP component being validated:</p>
 * <ul>
 *   <li><strong>URL Paths</strong> - Use {@code URLPathValidationPipeline} for a URL <em>path
 *       component</em> such as {@code /api/users/123}. It is <strong>not</strong> a validator for a
 *       full absolute URL: {@code NormalizationStage} recognises a leading {@code scheme://} and
 *       returns such an input unchanged, so RFC 3986 dot-segment resolution - and the traversal
 *       detection that depends on it - is skipped. Extract the path component from an absolute URL
 *       and pass that.</li>
 *   <li><strong>Parameter values</strong> - Use {@code URLParameterValidationPipeline} for query
 *       parameter values and form data</li>
 *   <li><strong>Parameter names</strong> - Use {@code URLParameterNameValidationPipeline} for query
 *       keys, which applies the stricter name-only rules</li>
 *   <li><strong>Headers</strong> - Use {@code HTTPHeaderValidationPipeline} for HTTP headers</li>
 *   <li><strong>Content types</strong> - Use {@code ContentTypeValidationPipeline} for
 *       {@code Content-Type} allow/block-list enforcement</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * // Create configuration and event counter
 * SecurityConfiguration config = SecurityConfiguration.defaults();
 * SecurityEventCounter eventCounter = new SecurityEventCounter();
 *
 * // Create pipelines using factory
 * HttpSecurityValidator pathValidator = PipelineFactory.createUrlPathPipeline(config, eventCounter);
 * HttpSecurityValidator paramValidator = PipelineFactory.createUrlParameterPipeline(config, eventCounter);
 *
 * // Validate different HTTP components
 * try {
 *     Optional&lt;String&gt; safePath = pathValidator.validate("/api/users/123");
 *     Optional&lt;String&gt; safeParam = paramValidator.validate("search=test&amp;page=1");
 *     safePath.ifPresent(path -&gt; {
 *         // Process the validated path
 *     });
 *     safeParam.ifPresent(param -&gt; {
 *         // Process the validated parameter
 *     });
 * } catch (UrlSecurityException e) {
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
 * @see de.cuioss.http.security.validation
 * @see de.cuioss.http.security.config.SecurityConfiguration
 */
@NullMarked
package de.cuioss.http.security.pipeline;

import org.jspecify.annotations.NullMarked;
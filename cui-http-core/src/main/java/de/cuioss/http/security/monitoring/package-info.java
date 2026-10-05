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
 * Security event monitoring for HTTP validation.
 *
 * <p>This package provides event counting capabilities for security monitoring,
 * tracking attack pattern detection and validation failures for security information
 * and event management (SIEM) integration.</p>
 *
 * <h3>Monitoring Components</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.monitoring.SecurityEventCounter} - Thread-safe counting of security events by type</li>
 * </ul>
 *
 * <h3>Event Tracking</h3>
 * <p>The monitoring system tracks various types of security events:</p>
 * <ul>
 *   <li><strong>Attack Attempts</strong> - Path traversal, injection, encoding attacks</li>
 *   <li><strong>Validation Failures</strong> - Character set violations, length limit breaches</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * // Create event counter and hand it to the pipeline
 * SecurityEventCounter eventCounter = new SecurityEventCounter();
 * HttpSecurityValidator validator =
 *     PipelineFactory.createUrlPathPipeline(SecurityConfiguration.defaults(), eventCounter);
 *
 * try {
 *     String validated = validator.validate(input)
 *             .orElseThrow(() -&gt; new IllegalArgumentException("input must not be null"));
 *     // Process validated input
 * } catch (UrlSecurityException e) {
 *     // Reject the request. The pipeline has already counted this violation in
 *     // eventCounter - incrementing it here again would count it twice.
 * }
 *
 * // Query event statistics
 * long pathTraversalCount = eventCounter.getCount(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED);
 * long totalCount = eventCounter.getTotalCount();
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
 * @see de.cuioss.http.security.core.UrlSecurityFailureType
 * @see de.cuioss.http.security.exceptions.UrlSecurityException
 */
@NullMarked
package de.cuioss.http.security.monitoring;

import org.jspecify.annotations.NullMarked;
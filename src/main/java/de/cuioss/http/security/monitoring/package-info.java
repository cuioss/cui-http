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
 * Security event monitoring and logging for HTTP validation.
 *
 * <p>This package provides comprehensive monitoring capabilities for security events,
 * including attack pattern detection, event counting, and structured logging for
 * security information and event management (SIEM) integration.</p>
 *
 * <h3>Monitoring Components</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.monitoring.SecurityEventCounter} - Thread-safe counting of security events by type</li>
 *   <li>{@link de.cuioss.http.security.monitoring.URLSecurityLogMessages} - Structured log messages for security events</li>
 * </ul>
 *
 * <h3>Event Tracking</h3>
 * <p>The monitoring system tracks various types of security events:</p>
 * <ul>
 *   <li><strong>Attack Attempts</strong> - Path traversal, injection, encoding attacks</li>
 *   <li><strong>Validation Failures</strong> - Character set violations, length limit breaches</li>
 *   <li><strong>Configuration Changes</strong> - Security policy modifications</li>
 *   <li><strong>Performance Metrics</strong> - Validation timing and resource usage</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * // Create event counter
 * SecurityEventCounter eventCounter = new SecurityEventCounter();
 *
 * // Use in validation pipeline
 * try {
 *     String validated = validator.validate(input);
 * } catch (UrlSecurityException e) {
 *     // Increment counter for this failure type
 *     eventCounter.increment(e.getFailureType());
 *
 *     // Log structured security event
 *     CuiLogger logger = new CuiLogger(MyClass.class);
 *     logger.warn(URLSecurityLogMessages.WARN.PATH_TRAVERSAL_DETECTED, input);
 * }
 *
 * // Query event statistics
 * long pathTraversalCount = eventCounter.getCount(UrlSecurityFailureType.PATH_TRAVERSAL_DETECTED);
 * </code></pre>
 *
 * <h3>SIEM Integration</h3>
 * <p>The monitoring system is designed for enterprise security monitoring:</p>
 * <ul>
 *   <li>Structured log messages with consistent identifiers</li>
 *   <li>Configurable log levels for different event types</li>
 *   <li>Thread-safe counters for metrics collection</li>
 *   <li>Support for real-time alerting based on event patterns</li>
 * </ul>
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
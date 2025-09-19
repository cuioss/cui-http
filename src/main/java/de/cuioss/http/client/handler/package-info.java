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
 * Secure HTTP client utilities and SSL/TLS context management for enterprise applications.
 *
 * <p>This package provides comprehensive HTTP client handling utilities with security-first design,
 * offering simplified request execution, SSL/TLS context management, and HTTP status code classification.
 * All components are designed for enterprise use with proper error handling, security defaults, and
 * thread-safe operation.</p>
 *
 * <h3>Key Components</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.client.handler.HttpHandler} - Builder-based HTTP client with secure SSL defaults</li>
 *   <li>{@link de.cuioss.http.client.handler.HttpStatusFamily} - RFC 7231 compliant HTTP status code categorization</li>
 *   <li>{@link de.cuioss.http.client.handler.SecureSSLContextProvider} - TLS 1.2+ SSL context provider</li>
 * </ul>
 *
 * <h3>Best Practices</h3>
 * <ul>
 *   <li><strong>Always use HttpHandler.builder()</strong> - Provides secure defaults and validation</li>
 *   <li><strong>Set appropriate timeouts</strong> - Prevent resource exhaustion with connection/read timeouts</li>
 *   <li><strong>Use HttpStatusFamily for response handling</strong> - Type-safe status code classification</li>
 *   <li><strong>Let SSL context auto-creation work</strong> - Secure defaults unless custom context needed</li>
 *   <li><strong>Handle exceptions properly</strong> - IOException for network errors, InterruptedException for interruption</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * // Secure HTTPS client with auto-generated SSL context
 * HttpHandler handler = HttpHandler.builder()
 *     .uri("https://api.example.com/data")
 *     .connectionTimeoutSeconds(10)
 *     .readTimeoutSeconds(30)
 *     .build();
 *
 * // Execute request with proper error handling
 * try {
 *     HttpResponse&lt;String&gt; response = handler.executeGetRequest();
 *     if (HttpStatusFamily.isSuccess(response.statusCode())) {
 *         processData(response.body());
 *     } else if (HttpStatusFamily.isClientError(response.statusCode())) {
 *         handleClientError(response);
 *     }
 * } catch (IOException e) {
 *     log.error("Network error during HTTP request", e);
 * } catch (InterruptedException e) {
 *     Thread.currentThread().interrupt();
 *     log.warn("HTTP request interrupted");
 * }
 * </pre>
 *
 * <h3>Cross-References</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security} - HTTP security validation components</li>
 *   <li>{@link javax.net.ssl.SSLContext} - Java SSL context API</li>
 *   <li>{@link java.net.http.HttpClient} - Java HTTP client API</li>
 * </ul>
 *
 * <h3>Package Nullability</h3>
 * <p>This package follows strict nullability conventions using JSpecify annotations:</p>
 * <ul>
 *   <li>All parameters and return values are non-null by default</li>
 *   <li>Nullable parameters and return values are explicitly annotated with {@code @Nullable}</li>
 * </ul>
 *
 * @author CUI HTTP Security Team
 * @since 1.0
 */
@NullMarked
package de.cuioss.http.client.handler;

import org.jspecify.annotations.NullMarked;

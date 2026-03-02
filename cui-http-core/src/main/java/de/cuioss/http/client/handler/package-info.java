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
 * HTTP client utilities and SSL/TLS context management.
 *
 * <h3>Components</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.client.handler.HttpHandler} - HTTP client wrapper with builder API and SSL support</li>
 *   <li>{@link de.cuioss.http.client.handler.HttpStatusFamily} - HTTP status code classification per RFC 7231</li>
 *   <li>{@link de.cuioss.http.client.handler.SecureSSLContextProvider} - TLS 1.2+ SSL context provider</li>
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
 * @author Oliver Wolff
 * @since 1.0
 * @see javax.net.ssl.SSLContext
 * @see java.net.http.HttpClient
 */
@NullMarked
package de.cuioss.http.client.handler;

import org.jspecify.annotations.NullMarked;

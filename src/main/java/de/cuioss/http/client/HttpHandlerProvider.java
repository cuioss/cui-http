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
package de.cuioss.http.client;

import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.tools.net.http.HttpHandler;
import lombok.NonNull;

/**
 * Unified provider interface for HTTP operations requiring both HttpHandler and RetryStrategy.
 * <p>
 * This interface establishes a consistent pattern for configuring HTTP operations with retry
 * capabilities. Rather than passing HttpHandler and RetryStrategy as separate parameters,
 * implementations provide both dependencies through a single provider interface.
 * <h2>Design Benefits</h2>
 * <ul>
 *   <li><strong>Unified Constructor</strong>: {@code new ResilientHttpHandler(provider)}
 *       instead of {@code new ResilientHttpHandler(handler, strategy)}</li>
 *   <li><strong>Consistent Pattern</strong>: All HTTP configuration classes follow the same pattern</li>
 *   <li><strong>Reduced Breaking Changes</strong>: Internal provider evolution without API changes</li>
 *   <li><strong>Better Testability</strong>: Single interface to mock</li>
 *   <li><strong>Future-Proof</strong>: Interface can evolve for HTTP-specific configuration needs</li>
 * </ul>
 * <h2>Implementation Pattern</h2>
 * Configuration classes implement this interface by providing their HttpHandler and RetryStrategy:
 * <pre>
 * public class HttpJwksLoaderConfig implements HttpHandlerProvider {
 *     &#64;Override
 *     public HttpHandler getHttpHandler() { return httpHandler; }
 *
 *     &#64;Override
 *     public RetryStrategy getRetryStrategy() { return retryStrategy; }
 * }
 * </pre>
 * <h2>Consumer Pattern</h2>
 * HTTP handler implementations consume this interface for unified dependency injection:
 * <pre>
 * public class ResilientHttpHandler {
 *     public ResilientHttpHandler(HttpHandlerProvider provider) {
 *         this.httpHandler = provider.getHttpHandler();
 *         this.retryStrategy = provider.getRetryStrategy();
 *     }
 * }
 * </pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public interface HttpHandlerProvider {

    /**
     * Provides the HttpHandler for HTTP operations.
     * <p>
     * This HttpHandler is used by HTTP clients for making actual HTTP requests.
     * The implementation must ensure that the returned HttpHandler is properly
     * configured with appropriate timeouts, SSL settings, and other HTTP-specific parameters.
     *
     * @return the HttpHandler instance, never null
     */
    @NonNull
    HttpHandler getHttpHandler();

    /**
     * Provides the RetryStrategy for HTTP operations.
     * <p>
     * This RetryStrategy defines how failed HTTP operations should be retried,
     * including exponential backoff parameters, maximum attempts, and retry conditions.
     * The implementation must ensure that the RetryStrategy is configured appropriately
     * for the specific HTTP operation context.
     *
     * @return the RetryStrategy instance, never null
     */
    @NonNull
    RetryStrategy getRetryStrategy();
}
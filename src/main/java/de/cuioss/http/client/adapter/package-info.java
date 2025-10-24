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
 * Async-first HTTP adapter API for composable, type-safe HTTP operations.
 *
 * <h2>Architecture Overview</h2>
 *
 * <p>This package provides a modern, composable HTTP client architecture with three core components:
 *
 * <ul>
 *   <li><b>{@link de.cuioss.http.client.adapter.HttpAdapter HttpAdapter}</b> - Method-specific interface (get(), post(), put(), etc.)</li>
 *   <li><b>{@link de.cuioss.http.client.adapter.ETagAwareHttpAdapter ETagAwareHttpAdapter}</b> - Implementation with ETag caching for bandwidth optimization</li>
 *   <li><b>{@link de.cuioss.http.client.adapter.ResilientHttpAdapter ResilientHttpAdapter}</b> - Retry decorator with exponential backoff for transient failures</li>
 * </ul>
 *
 * <h2>Async-First Design Philosophy</h2>
 *
 * <p>All methods return {@code CompletableFuture<HttpResult<T>>} for non-blocking operation:
 *
 * <ul>
 *   <li><b>Primary methods</b> ({@code get()}, {@code post()}, etc.) - Async by default, return CompletableFuture</li>
 *   <li><b>Convenience methods</b> ({@code getBlocking()}, {@code postBlocking()}, etc.) - Blocking variants with suffix</li>
 * </ul>
 *
 * <p><b>Rationale:</b> Modern HTTP clients are inherently async. Most use cases benefit from non-blocking
 * operation (reduced thread blocking, better scalability, improved resource utilization). The naming convention
 * guides developers toward best practices - async-first with explicit opt-in for blocking.
 *
 * <h2>Quick Start Examples</h2>
 *
 * <h3>Example 1: Simple GET with ETag Caching (Async)</h3>
 * <pre>{@code
 * // Create handler
 * HttpHandler handler = HttpHandler.builder()
 *     .uri("https://api.example.com/users/123")
 *     .build();
 *
 * // Create adapter with ETag caching
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .build();
 *
 * // Async execution (recommended)
 * CompletableFuture<HttpResult<User>> future = adapter.get();
 * future.thenAccept(result -> {
 *     if (result.isSuccess()) {
 *         result.getContent().ifPresent(user ->
 *             System.out.println("User: " + user.getName()));
 *
 *         // Check if served from cache
 *         if (result.isNotModified()) {
 *             System.out.println("Cached (304 Not Modified)");
 *         }
 *     }
 * });
 * }</pre>
 *
 * <h3>Example 2: POST with Request Body (Blocking)</h3>
 * <pre>{@code
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .requestConverter(userConverter)  // For serializing request body
 *     .build();
 *
 * User newUser = new User("John Doe", "john@example.com");
 *
 * // Blocking convenience method for simple cases
 * HttpResult<User> result = adapter.postBlocking(newUser);
 * if (result.isSuccess()) {
 *     System.out.println("Created: " + result.getContent());
 * }
 * }</pre>
 *
 * <h3>Example 3: Retry with Exponential Backoff</h3>
 * <pre>{@code
 * // Base adapter with ETag caching
 * HttpAdapter<User> base = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .build();
 *
 * // Wrap with retry for transient failures
 * HttpAdapter<User> resilient = ResilientHttpAdapter.wrap(base);
 *
 * // Automatically retries network/server errors (5xx)
 * HttpResult<User> result = resilient.getBlocking();
 * }</pre>
 *
 * <h3>Example 4: DELETE Operation (Status Code Only)</h3>
 * <pre>{@code
 * // Use built-in Void converter for status-code-only operations
 * HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);
 *
 * HttpResult<Void> result = adapter.deleteBlocking();
 * if (result.isSuccess()) {
 *     System.out.println("Deleted successfully");
 * }
 * }</pre>
 *
 * <h3>Example 5: Token Refresh Without Cache Bloat</h3>
 * <pre>{@code
 * // Mobile app with frequent token refresh
 * // Problem: Default ALL filter creates new cache entry for each token
 * // Solution: Exclude Authorization header from cache key
 *
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization"))
 *     .build();
 *
 * // Token changes don't affect cache key - reuses existing cached entry
 * Map<String, String> headers1 = Map.of("Authorization", "Bearer old-token");
 * HttpResult<User> result1 = adapter.get(headers1).join();
 *
 * Map<String, String> headers2 = Map.of("Authorization", "Bearer new-token");
 * HttpResult<User> result2 = adapter.get(headers2).join();  // 304 Not Modified!
 * }</pre>
 *
 * <h2>ETag Caching Behavior</h2>
 *
 * <p><b>What is cached:</b>
 * <ul>
 *   <li>GET responses with 200 status AND ETag header</li>
 *   <li>Cache entries include content, ETag, and timestamp</li>
 * </ul>
 *
 * <p><b>What is NOT cached:</b>
 * <ul>
 *   <li>POST/PUT/DELETE/PATCH responses (never cached)</li>
 *   <li>HEAD/OPTIONS responses (never cached)</li>
 *   <li>Responses without ETag header</li>
 *   <li>Non-200 status codes</li>
 * </ul>
 *
 * <p><b>Cache key composition:</b> By default, cache key = URI + sorted headers.
 * Use {@link de.cuioss.http.client.adapter.CacheKeyHeaderFilter CacheKeyHeaderFilter} to customize:
 * <ul>
 *   <li>{@code CacheKeyHeaderFilter.ALL} - Include all headers (default, safest)</li>
 *   <li>{@code CacheKeyHeaderFilter.NONE} - URI only (single-user clients)</li>
 *   <li>{@code CacheKeyHeaderFilter.excluding("Authorization")} - Exclude specific headers (solves token refresh cache bloat)</li>
 * </ul>
 *
 * <h2>Retry Behavior</h2>
 *
 * <p>{@link de.cuioss.http.client.adapter.ResilientHttpAdapter ResilientHttpAdapter} retries:
 * <ul>
 *   <li><b>NETWORK_ERROR</b> - IOException, connection failures, timeouts</li>
 *   <li><b>SERVER_ERROR</b> - HTTP 5xx status codes (server issues)</li>
 * </ul>
 *
 * <p>Does NOT retry:
 * <ul>
 *   <li><b>CLIENT_ERROR</b> - HTTP 4xx (bad request, validation failures)</li>
 *   <li><b>INVALID_CONTENT</b> - Parsing failures (malformed JSON/XML)</li>
 *   <li><b>CONFIGURATION_ERROR</b> - SSL issues, invalid configuration</li>
 * </ul>
 *
 * <p><b>Idempotency safety:</b> By default, only idempotent methods (GET, PUT, DELETE, HEAD, OPTIONS)
 * are retried. POST and PATCH are NOT retried unless {@code idempotentOnly=false} (requires idempotency keys).
 *
 * <h2>Composition Pattern</h2>
 *
 * <p>Adapters compose using the decorator pattern:
 * <pre>{@code
 * HttpAdapter<User> final = ResilientHttpAdapter.wrap(
 *     ETagAwareHttpAdapter.<User>builder()
 *         .httpHandler(handler)
 *         .responseConverter(userConverter)
 *         .build()
 * );
 *
 * // Benefits from both:
 * // - ETag caching reduces bandwidth (304 Not Modified)
 * // - Retry handles transient failures (network errors, 5xx)
 * // - 304 responses are NOT retried (Success category)
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>All classes in this package are thread-safe for shared use:
 * <ul>
 *   <li><b>Builders:</b> NOT thread-safe (build once per adapter)</li>
 *   <li><b>Built adapters:</b> Immutable, safe for concurrent use</li>
 *   <li><b>HttpClient:</b> Created once in constructor, reused for all requests</li>
 *   <li><b>Cache:</b> ConcurrentHashMap with thread-safe eviction</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 *
 * <p>All methods return {@code HttpResult<T>} (sealed interface with Success/Failure):
 * <pre>{@code
 * HttpResult<User> result = adapter.getBlocking();
 *
 * // Pattern matching (Java 17+)
 * switch (result) {
 *     case HttpResult.Success<User>(var user, var etag, var status) -> {
 *         System.out.println("User: " + user);
 *     }
 *     case HttpResult.Failure<User> failure -> {
 *         System.err.println("Error: " + failure.errorMessage());
 *
 *         // Check if retryable
 *         if (failure.isRetryable()) {
 *             scheduleRetry();
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Security Integration</h2>
 *
 * <p>Adapters integrate with existing HTTP security validation:
 * <ul>
 *   <li><b>URL validation:</b> Already performed by {@link de.cuioss.http.client.handler.HttpHandler HttpHandler}</li>
 *   <li><b>Header validation:</b> Use {@code HTTPHeaderValidationPipeline} before passing headers to adapter</li>
 *   <li><b>Body validation:</b> Validate in converter's {@code convert()} method</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * // Validate headers before request
 * HTTPHeaderValidationPipeline headerValidator = new HTTPHeaderValidationPipeline();
 * headerValidator.validate(headerName).orElseThrow();  // Throws on injection attack
 *
 * // Then pass to adapter
 * Map<String, String> headers = Map.of("X-Custom", headerValue);
 * HttpResult<User> result = adapter.get(headers).join();
 * }</pre>
 *
 * @see de.cuioss.http.client.adapter.HttpAdapter
 * @see de.cuioss.http.client.adapter.ETagAwareHttpAdapter
 * @see de.cuioss.http.client.adapter.ResilientHttpAdapter
 * @see de.cuioss.http.client.adapter.CacheKeyHeaderFilter
 * @see de.cuioss.http.client.adapter.RetryConfig
 * @since 1.0
 */
package de.cuioss.http.client.adapter;

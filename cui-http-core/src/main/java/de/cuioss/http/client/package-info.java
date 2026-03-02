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
 * HTTP client utilities for type-safe requests and security-focused HTTP operations.
 *
 * <h2>Core Components</h2>
 *
 * <p>This package provides foundational HTTP client components:
 *
 * <ul>
 *   <li><b>{@link de.cuioss.http.client.ContentType ContentType}</b> - Type-safe MIME types with charset support</li>
 *   <li><b>{@link de.cuioss.http.client.HttpMethod HttpMethod}</b> - HTTP methods with safe/idempotent classification</li>
 *   <li><b>{@link de.cuioss.http.client.handler.HttpHandler HttpHandler}</b> - Secure HTTP request builder with validation</li>
 * </ul>
 *
 * <h2>ContentType Enum</h2>
 *
 * <p>Type-safe content types (MIME types) with automatic charset handling:
 *
 * <pre>{@code
 * // Text types include UTF-8 charset automatically
 * ContentType json = ContentType.APPLICATION_JSON;
 * String header = json.toHeaderValue();
 * // Returns: "application/json; charset=UTF-8"
 *
 * // Binary types have no charset
 * ContentType pdf = ContentType.APPLICATION_PDF;
 * String header = pdf.toHeaderValue();
 * // Returns: "application/pdf"
 * }</pre>
 *
 * <p>Supported content types:
 * <ul>
 *   <li><b>JSON/XML:</b> APPLICATION_JSON, APPLICATION_XML, TEXT_XML</li>
 *   <li><b>Text:</b> TEXT_PLAIN, TEXT_HTML, TEXT_CSV</li>
 *   <li><b>Forms:</b> APPLICATION_FORM_URLENCODED, MULTIPART_FORM_DATA</li>
 *   <li><b>Binary:</b> APPLICATION_OCTET_STREAM, APPLICATION_PDF, APPLICATION_ZIP</li>
 *   <li><b>Images:</b> IMAGE_PNG, IMAGE_JPEG, IMAGE_GIF, IMAGE_SVG</li>
 * </ul>
 *
 * <h2>HttpMethod Enum</h2>
 *
 * <p>HTTP methods with semantic properties for retry safety and validation:
 *
 * <pre>{@code
 * HttpMethod get = HttpMethod.GET;
 * boolean safe = get.isSafe();          // true (read-only)
 * boolean idempotent = get.isIdempotent();  // true (can retry)
 *
 * HttpMethod post = HttpMethod.POST;
 * boolean safe = post.isSafe();         // false (modifies state)
 * boolean idempotent = post.isIdempotent(); // false (unsafe to retry)
 * }</pre>
 *
 * <p>Method classification:
 * <ul>
 *   <li><b>Safe (read-only):</b> GET, HEAD, OPTIONS</li>
 *   <li><b>Idempotent (safe to retry):</b> GET, PUT, DELETE, HEAD, OPTIONS</li>
 *   <li><b>Non-idempotent (unsafe to retry):</b> POST, PATCH</li>
 * </ul>
 *
 * <h2>HttpHandler</h2>
 *
 * <p>Secure HTTP request builder with built-in security validation:
 *
 * <pre>{@code
 * // URL security validation is automatic
 * HttpHandler handler = HttpHandler.builder()
 *     .uri("https://api.example.com/users")
 *     .connectionTimeoutSeconds(5)
 *     .readTimeoutSeconds(10)
 *     .sslContextProvider(SecureSSLContextProvider.builder().build())
 *     .build();
 *
 * // Build requests with validated URL
 * HttpRequest request = handler.requestBuilder()
 *     .GET()
 *     .header("Authorization", "Bearer token")
 *     .build();
 * }</pre>
 *
 * <h2>Example: GET with JSON</h2>
 *
 * <pre>{@code
 * import de.cuioss.http.client.ContentType;
 * import de.cuioss.http.client.adapter.HttpAdapter;
 * import de.cuioss.http.client.adapter.ETagAwareHttpAdapter;
 * import de.cuioss.http.client.converter.HttpResponseConverter;
 * import de.cuioss.http.client.handler.HttpHandler;
 * import de.cuioss.http.client.result.HttpResult;
 *
 * // Configure handler
 * HttpHandler handler = HttpHandler.builder()
 *     .uri("https://api.example.com/users/123")
 *     .build();
 *
 * // Create JSON converter
 * HttpResponseConverter<User> converter = new JsonResponseConverter<>(User.class);
 *
 * // Build adapter
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(converter)
 *     .build();
 *
 * // Execute GET request
 * HttpResult<User> result = adapter.getBlocking();
 *
 * // Handle result
 * if (result.isSuccess()) {
 *     User user = result.getContent().orElseThrow();
 *     System.out.println("User: " + user.getName());
 * } else {
 *     System.err.println("Error: " + result.getErrorMessage());
 * }
 * }</pre>
 *
 * <h2>Example: POST with JSON</h2>
 *
 * <pre>{@code
 * // Converter that handles both request and response
 * class UserConverter extends StringContentConverter<User>
 *         implements HttpResponseConverter<User>, HttpRequestConverter<User> {
 *
 *     @Override
 *     protected Optional<User> convertString(String rawContent) {
 *         return Optional.ofNullable(parseJson(rawContent));
 *     }
 *
 *     @Override
 *     public HttpRequest.BodyPublisher toBodyPublisher(@Nullable User user) {
 *         if (user == null) return HttpRequest.BodyPublishers.noBody();
 *         String json = toJson(user);
 *         return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
 *     }
 *
 *     @Override
 *     public ContentType contentType() {
 *         return ContentType.APPLICATION_JSON;
 *     }
 * }
 *
 * UserConverter converter = new UserConverter();
 *
 * // Build adapter with request converter
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(converter)
 *     .requestConverter(converter)  // For serializing request body
 *     .build();
 *
 * // Create and send user
 * User newUser = new User("John Doe", "john@example.com");
 * HttpResult<User> result = adapter.postBlocking(newUser);
 *
 * if (result.isSuccess()) {
 *     User created = result.getContent().orElseThrow();
 *     System.out.println("Created user: " + created.getId());
 * }
 * }</pre>
 *
 * <h2>Example: Retry Configuration</h2>
 *
 * <pre>{@code
 * import de.cuioss.http.client.adapter.ResilientHttpAdapter;
 * import de.cuioss.http.client.adapter.RetryConfig;
 *
 * // Custom retry configuration
 * RetryConfig config = RetryConfig.builder()
 *     .maxAttempts(3)                       // Only 3 attempts
 *     .initialDelay(Duration.ofSeconds(2))  // Start with 2s delay
 *     .multiplier(2.0)                      // Exponential backoff
 *     .maxDelay(Duration.ofMinutes(1))      // Cap at 1 minute
 *     .jitter(0.1)                          // 10% jitter
 *     .idempotentOnly(true)                 // Only retry safe methods
 *     .build();
 *
 * // Wrap adapter with retry
 * HttpAdapter<User> resilient = ResilientHttpAdapter.wrap(baseAdapter, config);
 *
 * // Automatically retries on network/server errors
 * HttpResult<User> result = resilient.getBlocking();
 * }</pre>
 *
 * <h2>Example: Composition (Retry + Caching)</h2>
 *
 * <pre>{@code
 * // Base adapter with ETag caching
 * HttpAdapter<User> caching = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .build();
 *
 * // Wrap with retry for resilience
 * HttpAdapter<User> resilient = ResilientHttpAdapter.wrap(caching);
 *
 * // Benefits from both:
 * // - ETag caching reduces bandwidth (304 Not Modified)
 * // - Retry handles transient failures (network errors, 5xx)
 * HttpResult<User> result = resilient.getBlocking();
 * }</pre>
 *
 * <h2>Example: Async Execution Patterns</h2>
 *
 * <pre>{@code
 * // Async-first design - all methods return CompletableFuture
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .build();
 *
 * // Pattern 1: Chain async operations
 * CompletableFuture<Void> pipeline = adapter.get()
 *     .thenApply(result -> result.getContent().orElseThrow())
 *     .thenAccept(user -> System.out.println("User: " + user.getName()))
 *     .exceptionally(ex -> {
 *         System.err.println("Error: " + ex.getMessage());
 *         return null;
 *     });
 *
 * // Pattern 2: Combine multiple requests
 * CompletableFuture<User> user1 = adapter.get();
 * CompletableFuture<User> user2 = adapter.get();
 *
 * CompletableFuture.allOf(user1, user2)
 *     .thenRun(() -> System.out.println("Both loaded"))
 *     .join();
 *
 * // Pattern 3: Fallback on failure
 * CompletableFuture<User> withFallback = adapter.get()
 *     .thenApply(result -> result.getContent().orElseGet(() -> loadFromCache()));
 * }</pre>
 *
 * <h2>Example: Blocking Convenience Methods</h2>
 *
 * <pre>{@code
 * // For simple synchronous cases, use blocking methods
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .requestConverter(userConverter)
 *     .build();
 *
 * // GET (blocking)
 * HttpResult<User> getResult = adapter.getBlocking();
 *
 * // POST (blocking)
 * User newUser = new User("Jane", "jane@example.com");
 * HttpResult<User> postResult = adapter.postBlocking(newUser);
 *
 * // PUT (blocking)
 * HttpResult<User> putResult = adapter.putBlocking(updatedUser);
 *
 * // DELETE (blocking)
 * HttpResult<User> deleteResult = adapter.deleteBlocking();
 * }</pre>
 *
 * <h2>Example: Token Refresh Cache Bloat Solution</h2>
 *
 * <pre>{@code
 * import de.cuioss.http.client.adapter.CacheKeyHeaderFilter;
 *
 * // Problem: Default ALL filter creates new cache entry for each token refresh
 * // Solution: Exclude Authorization header from cache key
 *
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization"))
 *     .build();
 *
 * // Now:
 * // - Accept-Language IS included → separate cache per language ✓
 * // - Authorization NOT included → token refresh doesn't bloat cache ✓
 *
 * Map<String, String> headers1 = Map.of("Authorization", "Bearer old-token");
 * HttpResult<User> result1 = adapter.get(headers1).join();
 *
 * Map<String, String> headers2 = Map.of("Authorization", "Bearer new-token");
 * HttpResult<User> result2 = adapter.get(headers2).join();  // 304 Not Modified!
 * }</pre>
 *
 * <h2>Security Integration</h2>
 *
 * <p>All HTTP operations integrate with security validation pipelines:
 *
 * <pre>{@code
 * import de.cuioss.http.security.pipeline.HTTPHeaderValidationPipeline;
 * import de.cuioss.http.security.pipeline.URLParameterValidationPipeline;
 *
 * // Validate headers before request
 * HTTPHeaderValidationPipeline headerValidator = new HTTPHeaderValidationPipeline();
 * headerValidator.validate(headerName)
 *     .orElseThrow(() -> new UrlSecurityException("Header injection detected"));
 *
 * // Validate URL parameters
 * URLParameterValidationPipeline paramValidator = new URLParameterValidationPipeline();
 * paramValidator.validate(paramValue)
 *     .orElseThrow(() -> new UrlSecurityException("XSS attack detected"));
 *
 * // Then use validated values in adapter
 * Map<String, String> headers = Map.of("X-Custom", validatedHeaderValue);
 * HttpResult<User> result = adapter.get(headers).join();
 * }</pre>
 *
 * @see de.cuioss.http.client.ContentType
 * @see de.cuioss.http.client.HttpMethod
 * @see de.cuioss.http.client.handler.HttpHandler
 * @see de.cuioss.http.client.adapter
 * @see de.cuioss.http.client.converter
 * @see de.cuioss.http.client.result
 * @since 1.0
 */
package de.cuioss.http.client;

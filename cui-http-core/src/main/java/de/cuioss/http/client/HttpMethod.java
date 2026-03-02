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

/**
 * HTTP methods as defined by RFC 7231 and RFC 5789.
 * <p>
 * This enum provides classification of HTTP methods based on their semantic properties:
 * <ul>
 * <li><b>Safe methods</b>: Read-only operations with no side effects (GET, HEAD, OPTIONS)</li>
 * <li><b>Idempotent methods</b>: Multiple identical requests have the same effect as a single
 * request (GET, PUT, DELETE, HEAD, OPTIONS)</li>
 * </ul>
 * <p>
 * Public for logging, debugging, and advanced use cases. Most users interact with
 * methods via the type-safe {@code HttpAdapter} API:
 * <pre>
 * HttpAdapter&lt;User&gt; adapter = ...;
 *
 * // Type-safe method-specific API (recommended)
 * CompletableFuture&lt;HttpResult&lt;User&gt;&gt; result = adapter.get();
 *
 * // Rather than generic execute(HttpMethod) approach
 * </pre>
 *
 * <h2>Safe vs Idempotent</h2>
 * <p>
 * <b>Safe methods</b> are read-only and produce no side effects on the server:
 * <ul>
 * <li>GET: Retrieve resource</li>
 * <li>HEAD: Retrieve headers only</li>
 * <li>OPTIONS: Query supported methods</li>
 * </ul>
 * <p>
 * <b>Idempotent methods</b> can be safely retried because multiple identical requests
 * have the same effect as a single request:
 * <ul>
 * <li>GET, HEAD, OPTIONS: Safe methods are always idempotent</li>
 * <li>PUT: Replace resource (second PUT with same data has no additional effect)</li>
 * <li>DELETE: Remove resource (second DELETE on already-deleted resource has no additional effect)</li>
 * </ul>
 * <p>
 * <b>Non-idempotent methods</b> should NOT be automatically retried without explicit opt-in:
 * <ul>
 * <li>POST: Creates new resource on each request (duplicate submissions possible)</li>
 * <li>PATCH: May apply delta multiple times if not designed for idempotency</li>
 * </ul>
 *
 * <h2>Retry Safety</h2>
 * <p>
 * The {@link #isIdempotent()} property is used by retry mechanisms to determine
 * which requests can be safely retried on network failure:
 * <pre>
 * if (httpMethod.isIdempotent()) {
 *     // Safe to retry: GET, PUT, DELETE, HEAD, OPTIONS
 *     retryRequest();
 * } else {
 *     // Unsafe to retry without explicit opt-in: POST, PATCH
 *     requireManualRetry();
 * }
 * </pre>
 *
 * @author CUI-HTTP Development Team
 * @since 1.0
 */
public enum HttpMethod {

    /**
     * GET - Retrieve resource representation.
     * <p>
     * Safe: Yes (read-only, no side effects)<br>
     * Idempotent: Yes (multiple GETs return same resource)<br>
     * Cacheable: Yes
     * <p>
     * Use for: Retrieving data, querying resources
     */
    GET(true, true, "GET"),

    /**
     * POST - Create new resource or submit data for processing.
     * <p>
     * Safe: No (modifies server state)<br>
     * Idempotent: No (each POST may create a new resource)<br>
     * Cacheable: Only if explicit freshness information provided
     * <p>
     * Use for: Creating resources, submitting forms, non-idempotent operations
     * <p>
     * <b>Retry Warning:</b> POST should NOT be automatically retried without explicit
     * opt-in due to risk of duplicate resource creation.
     */
    POST(false, false, "POST"),

    /**
     * PUT - Replace entire resource with new representation.
     * <p>
     * Safe: No (modifies server state)<br>
     * Idempotent: Yes (multiple PUTs with same data produce same result)<br>
     * Cacheable: No
     * <p>
     * Use for: Updating entire resource, creating resource at known URI
     */
    PUT(false, true, "PUT"),

    /**
     * DELETE - Remove resource.
     * <p>
     * Safe: No (modifies server state)<br>
     * Idempotent: Yes (deleting already-deleted resource has same effect)<br>
     * Cacheable: No
     * <p>
     * Use for: Deleting resources
     */
    DELETE(false, true, "DELETE"),

    /**
     * PATCH - Apply partial modifications to resource.
     * <p>
     * Safe: No (modifies server state)<br>
     * Idempotent: No (depends on patch semantics, often non-idempotent)<br>
     * Cacheable: Only if explicit freshness information provided
     * <p>
     * Use for: Partial updates, applying deltas
     * <p>
     * <b>Retry Warning:</b> PATCH should NOT be automatically retried without explicit
     * opt-in unless the patch format guarantees idempotency.
     */
    PATCH(false, false, "PATCH"),

    /**
     * HEAD - Retrieve headers only (identical to GET but without response body).
     * <p>
     * Safe: Yes (read-only, no side effects)<br>
     * Idempotent: Yes (multiple HEADs have same effect)<br>
     * Cacheable: Yes
     * <p>
     * Use for: Checking resource existence, retrieving metadata (Content-Type,
     * Content-Length, ETag, Last-Modified)
     */
    HEAD(true, true, "HEAD"),

    /**
     * OPTIONS - Query supported HTTP methods and capabilities.
     * <p>
     * Safe: Yes (read-only, no side effects)<br>
     * Idempotent: Yes (multiple OPTIONS have same effect)<br>
     * Cacheable: No
     * <p>
     * Use for: Discovering allowed methods, CORS preflight requests
     */
    OPTIONS(true, true, "OPTIONS");

    private final boolean safe;
    private final boolean idempotent;
    private final String name;

    /**
     * Constructs HTTP method enum value.
     *
     * @param safe true if method is safe (read-only, no side effects)
     * @param idempotent true if multiple identical requests have same effect as single request
     * @param name HTTP method name (uppercase, e.g., "GET", "POST")
     */
    HttpMethod(boolean safe, boolean idempotent, String name) {
        this.safe = safe;
        this.idempotent = idempotent;
        this.name = name;
    }

    /**
     * Returns true if this method is safe (read-only with no side effects).
     * <p>
     * Safe methods: GET, HEAD, OPTIONS
     * <p>
     * Safe methods can be used freely without concern for modifying server state.
     * All safe methods are also idempotent.
     *
     * @return true if method is safe
     */
    public boolean isSafe() {
        return safe;
    }

    /**
     * Returns true if this method is idempotent (multiple identical requests have
     * the same effect as a single request).
     * <p>
     * Idempotent methods: GET, PUT, DELETE, HEAD, OPTIONS
     * <p>
     * Idempotent methods can be safely retried on network failures without risk
     * of unintended side effects. Non-idempotent methods (POST, PATCH) should NOT
     * be automatically retried without explicit opt-in.
     *
     * @return true if method is idempotent
     */
    public boolean isIdempotent() {
        return idempotent;
    }

    /**
     * Returns the HTTP method name as an uppercase string.
     * <p>
     * Examples: "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
     *
     * @return HTTP method name (uppercase)
     */
    public String methodName() {
        return name;
    }
}

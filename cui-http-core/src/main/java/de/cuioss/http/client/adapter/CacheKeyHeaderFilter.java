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
package de.cuioss.http.client.adapter;

import java.util.HashSet;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Strategy for determining which HTTP headers should be included in cache keys.
 *
 * <p>Headers included in cache keys create separate cache entries per header
 * combination. Headers excluded from cache keys allow cache sharing across
 * different header values.
 *
 * <h2>What this filter does not decide</h2>
 *
 * <p><strong>Isolation between principals is not this filter's responsibility, and cannot be
 * weakened by it.</strong> {@link ETagAwareHttpAdapter} binds every cache entry to the credential
 * material that produced it — unconditionally, and independently of the verdict returned here. No
 * configuration of this filter lets one principal read an entry another principal populated.
 *
 * <p>What the filter governs is which headers are reproduced <em>verbatim</em> in the key text, and
 * therefore how finely entries are split across header values that genuinely vary the response
 * (typically the {@code Accept-*} family). Credential values are never keyed verbatim in any case:
 * a credential-bearing header is reduced to its digest wherever it enters the key.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Keep the Credential Out of the Key Text</h3>
 * <pre>{@code
 * // Exclude Authorization from the verbatim header section, keep content-affecting headers
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization"))
 *     .build();
 *
 * // Now:
 * // - Accept-Language IS included → separate cache per language
 * // - Authorization NOT reproduced in the header section
 * // - Entries remain scoped to the presenting credential either way, so a refreshed token
 * //   still resolves to an entry of its own - exclusion is key hygiene, not cache sharing
 * }</pre>
 *
 * <h3>Example 2: Exclude All Trace Headers</h3>
 * <pre>{@code
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .cacheKeyHeaderFilter(
 *         CacheKeyHeaderFilter.excludingPrefix("X-")
 *             .and(CacheKeyHeaderFilter.excluding("Authorization"))
 *     )
 *     .build();
 * }</pre>
 *
 * <h3>Example 3: Whitelist Content Headers Only</h3>
 * <pre>{@code
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .cacheKeyHeaderFilter(CacheKeyHeaderFilter.including(
 *         "Accept-Language",
 *         "Accept-Encoding",
 *         "Accept-Charset"
 *     ))
 *     .build();
 * }</pre>
 *
 * @since 1.0
 */
@FunctionalInterface
public interface CacheKeyHeaderFilter {

    /**
     * Determines if the given header should be included in the cache key.
     *
     * @param headerName The HTTP header name (case-insensitive)
     * @return true if header should be included in cache key, false otherwise
     */
    boolean includeInCacheKey(String headerName);

    // ========== PRESET FILTERS ==========

    /**
     * Include all headers in cache key (default).
     *
     * <p><b>Use when:</b>
     * <ul>
     *   <li>Any header may vary the response and you would rather not enumerate which</li>
     *   <li>Defense-in-depth against server ETag bugs</li>
     * </ul>
     *
     * <p><b>Trade-off:</b> every distinct header combination is a distinct entry, so incidental
     * per-request headers (trace ids and the like) fragment the cache.
     */
    CacheKeyHeaderFilter ALL = header -> true;

    /**
     * Exclude all headers from cache key (URI only, plus the adapter's unconditional principal
     * binding).
     *
     * <p><b>Use when:</b>
     * <ul>
     *   <li>The response varies by URI alone</li>
     *   <li>You want the smallest possible number of entries per principal</li>
     * </ul>
     *
     * <p><b>Trade-off:</b> headers that genuinely vary the representation — {@code Accept-Language}
     * and the rest of the {@code Accept-*} family — collapse onto one entry, so a request may be
     * answered from a representation negotiated for different headers. This is a content-negotiation
     * trade-off, not an isolation one: entries stay scoped to the presenting credential regardless.
     */
    CacheKeyHeaderFilter NONE = header -> false;

    // ========== FACTORY METHODS ==========

    /**
     * Exclude specific headers from cache key, include all others.
     *
     * <p>Keeps incidental per-request headers — trace ids and the like — from fragmenting the cache,
     * while content-affecting headers such as {@code Accept-Language} continue to split entries.
     * Excluding a credential header additionally keeps it out of the verbatim header section; it
     * does not make entries shared, because the principal binding is applied either way.
     *
     * <p>Example:
     * <pre>{@code
     * // Exclude incidental per-request headers, include the rest
     * .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding(
     *     "Authorization", "X-Request-ID", "X-Trace-ID"
     * ))
     * }</pre>
     *
     * @param headerNames Case-insensitive header names to exclude
     * @return Filter that includes all headers except specified ones
     */
    @SuppressWarnings("java:S6485")
    static CacheKeyHeaderFilter excluding(String... headerNames) {
        // Use calculated capacity to avoid resizing (load factor 0.75)
        var excluded = new HashSet<String>(Math.max((int) (headerNames.length / 0.75f) + 1, 16));
        for (String name : headerNames) {
            excluded.add(name.toLowerCase(Locale.ROOT));
        }
        return header -> !excluded.contains(header.toLowerCase(Locale.ROOT));
    }

    /**
     * Include only specific headers in cache key, exclude all others.
     *
     * <p><b>Whitelist approach</b> for precise control over cache key composition.
     *
     * <p>Example:
     * <pre>{@code
     * // Include only content-affecting headers
     * .cacheKeyHeaderFilter(CacheKeyHeaderFilter.including(
     *     "Accept-Language", "Accept-Encoding"
     * ))
     * }</pre>
     *
     * @param headerNames Case-insensitive header names to include
     * @return Filter that includes only specified headers
     */
    @SuppressWarnings("java:S6485")
    static CacheKeyHeaderFilter including(String... headerNames) {
        // Use calculated capacity to avoid resizing (load factor 0.75)
        var included = new HashSet<String>(Math.max((int) (headerNames.length / 0.75f) + 1, 16));
        for (String name : headerNames) {
            included.add(name.toLowerCase(Locale.ROOT));
        }
        return header -> included.contains(header.toLowerCase(Locale.ROOT));
    }

    /**
     * Exclude headers matching a prefix (case-insensitive).
     *
     * <p>Example:
     * <pre>{@code
     * // Exclude all X- headers (trace IDs, custom headers)
     * .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excludingPrefix("X-"))
     * }</pre>
     *
     * @param prefix Case-insensitive prefix to match
     * @return Filter that excludes headers starting with prefix
     */
    static CacheKeyHeaderFilter excludingPrefix(String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return header -> !header.toLowerCase(Locale.ROOT).startsWith(lowerPrefix);
    }

    /**
     * Custom predicate-based filter for complex logic.
     *
     * <p>Example:
     * <pre>{@code
     * .cacheKeyHeaderFilter(CacheKeyHeaderFilter.matching(
     *     header -> !header.startsWith("X-") && !header.equals("Authorization")
     * ))
     * }</pre>
     *
     * @param predicate Custom header inclusion logic
     * @return Filter using the predicate
     */
    static CacheKeyHeaderFilter matching(Predicate<String> predicate) {
        return predicate::test;
    }

    // ========== COMPOSITION ==========

    /**
     * Combines this filter with another using logical AND.
     * Header is included only if both filters return true.
     *
     * <p>Example:
     * <pre>{@code
     * CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
     *     .excluding("Authorization")
     *     .and(CacheKeyHeaderFilter.excludingPrefix("X-"));
     * }</pre>
     *
     * @param other Another filter to combine with
     * @return Combined filter using logical AND
     */
    default CacheKeyHeaderFilter and(CacheKeyHeaderFilter other) {
        return header -> this.includeInCacheKey(header) && other.includeInCacheKey(header);
    }

    /**
     * Combines this filter with another using logical OR.
     * Header is included if either filter returns true.
     *
     * <p>Example:
     * <pre>{@code
     * CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
     *     .including("Accept-Language")
     *     .or(CacheKeyHeaderFilter.including("Accept-Encoding"));
     * }</pre>
     *
     * @param other Another filter to combine with
     * @return Combined filter using logical OR
     */
    default CacheKeyHeaderFilter or(CacheKeyHeaderFilter other) {
        return header -> this.includeInCacheKey(header) || other.includeInCacheKey(header);
    }

    /**
     * Negates this filter.
     *
     * <p>Example:
     * <pre>{@code
     * // Include all EXCEPT Accept-Language
     * CacheKeyHeaderFilter.including("Accept-Language").negate()
     * }</pre>
     *
     * @return Negated filter
     */
    default CacheKeyHeaderFilter negate() {
        return header -> !this.includeInCacheKey(header);
    }
}

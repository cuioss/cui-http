package de.cuioss.http.client.adapter;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Strategy for determining which HTTP headers should be included in cache keys.
 *
 * <p>Headers included in cache keys create separate cache entries per header
 * combination. Headers excluded from cache keys allow cache sharing across
 * different header values.
 *
 * <p>This functional interface allows fine-grained control beyond simple all-or-nothing
 * choices, solving the token refresh cache bloat problem while maintaining security.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Solve Token Refresh Cache Bloat</h3>
 * <pre>{@code
 * // Problem: ALL causes cache bloat on token refresh
 * // Solution: Exclude Authorization, keep content-affecting headers
 * HttpAdapter<User> adapter = ETagAwareHttpAdapter.<User>builder()
 *     .httpHandler(handler)
 *     .responseConverter(userConverter)
 *     .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization"))
 *     .build();
 *
 * // Now:
 * // - Accept-Language IS included → separate cache per language ✓
 * // - Authorization NOT included → token refresh doesn't bloat cache ✓
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
     * Include all headers in cache key (default, safest).
     *
     * <p><b>Use when:</b>
     * <ul>
     *   <li>Adapter is shared across multiple users</li>
     *   <li>Headers affect response content</li>
     *   <li>Defense-in-depth against server ETag bugs</li>
     * </ul>
     *
     * <p><b>Trade-off:</b> Token refresh creates cache bloat
     */
    CacheKeyHeaderFilter ALL = header -> true;

    /**
     * Exclude all headers from cache key (URI only).
     *
     * <p><b>Use ONLY when:</b>
     * <ul>
     *   <li>Single-user client (not shared)</li>
     *   <li>Server implements user-aware ETags</li>
     * </ul>
     *
     * <p><b>Risk:</b> Multi-user scenarios may cache wrong content
     */
    CacheKeyHeaderFilter NONE = header -> false;

    // ========== FACTORY METHODS ==========

    /**
     * Exclude specific headers from cache key, include all others.
     *
     * <p><b>Solves token refresh cache bloat</b> by excluding Authorization
     * while keeping content-affecting headers like Accept-Language.
     *
     * <p>Example:
     * <pre>{@code
     * // Exclude frequently-changing headers, include others
     * .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding(
     *     "Authorization", "X-Request-ID", "X-Trace-ID"
     * ))
     * }</pre>
     *
     * @param headerNames Case-insensitive header names to exclude
     * @return Filter that includes all headers except specified ones
     */
    static CacheKeyHeaderFilter excluding(String... headerNames) {
        Set<String> excluded = Set.of(headerNames).stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        return header -> !excluded.contains(header.toLowerCase());
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
    static CacheKeyHeaderFilter including(String... headerNames) {
        Set<String> included = Set.of(headerNames).stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        return header -> included.contains(header.toLowerCase());
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
        String lowerPrefix = prefix.toLowerCase();
        return header -> !header.toLowerCase().startsWith(lowerPrefix);
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

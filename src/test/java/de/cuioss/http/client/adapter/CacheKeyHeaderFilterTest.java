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
package de.cuioss.http.client.adapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CacheKeyHeaderFilter}.
 *
 * @author Generated
 */
class CacheKeyHeaderFilterTest {

    // ========== PRESET FILTERS TESTS ==========

    @Test
    void allIncludesAllHeaders() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.ALL;

        assertTrue(filter.includeInCacheKey("Authorization"));
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("X-Request-ID"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
        assertTrue(filter.includeInCacheKey(""));
    }

    @Test
    void noneExcludesAllHeaders() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.NONE;

        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("Accept-Language"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("Content-Type"));
        assertFalse(filter.includeInCacheKey(""));
    }

    // ========== EXCLUDING TESTS ==========

    @Test
    void excludingCreatesBlacklistFilter() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excluding("Authorization", "X-Request-ID");

        // Excluded headers
        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));

        // Included headers
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
        assertTrue(filter.includeInCacheKey("Accept-Encoding"));
    }

    @Test
    void excludingCaseInsensitive() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excluding("authorization");

        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("AUTHORIZATION"));
        assertFalse(filter.includeInCacheKey("authorization"));
        assertFalse(filter.includeInCacheKey("AuThOrIzAtIoN"));
    }

    @Test
    void excludingEmptyArray() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excluding();

        // No headers excluded, all included
        assertTrue(filter.includeInCacheKey("Authorization"));
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("X-Request-ID"));
    }

    // ========== INCLUDING TESTS ==========

    @Test
    void includingCreatesWhitelistFilter() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.including("Accept-Language", "Accept-Encoding");

        // Included headers
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Accept-Encoding"));

        // Excluded headers
        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("Content-Type"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
    }

    @Test
    void includingCaseInsensitive() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.including("accept-language");

        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("ACCEPT-LANGUAGE"));
        assertTrue(filter.includeInCacheKey("accept-language"));
        assertTrue(filter.includeInCacheKey("AcCePt-LaNgUaGe"));
    }

    @Test
    void includingEmptyArray() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.including();

        // No headers included, all excluded
        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("Accept-Language"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
    }

    // ========== EXCLUDING PREFIX TESTS ==========

    @Test
    void excludingPrefixFiltersHeadersByPrefix() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excludingPrefix("X-");

        // Excluded (X- prefix)
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("X-Trace-ID"));
        assertFalse(filter.includeInCacheKey("X-Custom-Header"));

        // Included (no X- prefix)
        assertTrue(filter.includeInCacheKey("Authorization"));
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
    }

    @Test
    void excludingPrefixCaseInsensitive() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excludingPrefix("x-");

        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("x-request-id"));
        assertFalse(filter.includeInCacheKey("X-TRACE-ID"));
    }

    @Test
    void excludingPrefixExactMatch() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excludingPrefix("Accept");

        // Excluded (starts with Accept)
        assertFalse(filter.includeInCacheKey("Accept"));
        assertFalse(filter.includeInCacheKey("Accept-Language"));
        assertFalse(filter.includeInCacheKey("Accept-Encoding"));

        // Included (doesn't start with Accept)
        assertTrue(filter.includeInCacheKey("Authorization"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
    }

    // ========== MATCHING TESTS ==========

    @Test
    void matchingCustomPredicate() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.matching(
                header -> !header.startsWith("X-") && !"Authorization".equals(header)
        );

        // Excluded by predicate
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("Authorization"));

        // Included by predicate
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
    }

    @Test
    void matchingComplexPredicate() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.matching(
                header -> header.startsWith("Accept-") || "Content-Type".equals(header)
        );

        // Included by predicate
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Accept-Encoding"));
        assertTrue(filter.includeInCacheKey("Content-Type"));

        // Excluded by predicate
        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
    }

    // ========== COMPOSITION: AND TESTS ==========

    @Test
    void andCombinesFiltersWithLogicalAnd() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
                .excluding("Authorization")
                .and(CacheKeyHeaderFilter.excludingPrefix("X-"));

        // Excluded by first filter (Authorization)
        assertFalse(filter.includeInCacheKey("Authorization"));

        // Excluded by second filter (X- prefix)
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("X-Trace-ID"));

        // Included by both filters
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
    }

    @Test
    void andRequiresBothTrue() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
                .including("Accept-Language", "Authorization")
                .and(CacheKeyHeaderFilter.excluding("Authorization"));

        // Authorization: included by first, excluded by second → false
        assertFalse(filter.includeInCacheKey("Authorization"));

        // Accept-Language: included by first, not excluded by second → true
        assertTrue(filter.includeInCacheKey("Accept-Language"));

        // Content-Type: excluded by first → false (regardless of second)
        assertFalse(filter.includeInCacheKey("Content-Type"));
    }

    // ========== COMPOSITION: OR TESTS ==========

    @Test
    void orCombinesFiltersWithLogicalOr() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
                .including("Accept-Language")
                .or(CacheKeyHeaderFilter.including("Authorization"));

        // Included by first filter
        assertTrue(filter.includeInCacheKey("Accept-Language"));

        // Included by second filter
        assertTrue(filter.includeInCacheKey("Authorization"));

        // Excluded by both filters
        assertFalse(filter.includeInCacheKey("Content-Type"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
    }

    @Test
    void orRequiresEitherTrue() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
                .excluding("Authorization")
                .or(CacheKeyHeaderFilter.excludingPrefix("X-"));

        // Excluded by first, included by second → true
        assertTrue(filter.includeInCacheKey("Authorization"));

        // Included by first, excluded by second → true
        assertTrue(filter.includeInCacheKey("X-Request-ID"));

        // Included by both → true
        assertTrue(filter.includeInCacheKey("Accept-Language"));
    }

    // ========== COMPOSITION: NEGATE TESTS ==========

    @Test
    void negateInvertsFilter() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
                .including("Accept-Language")
                .negate();

        // Was included, now excluded
        assertFalse(filter.includeInCacheKey("Accept-Language"));

        // Was excluded, now included
        assertTrue(filter.includeInCacheKey("Authorization"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
        assertTrue(filter.includeInCacheKey("X-Request-ID"));
    }

    @Test
    void negateDoubleNegation() {
        CacheKeyHeaderFilter original = CacheKeyHeaderFilter.excluding("Authorization");
        CacheKeyHeaderFilter doubleNegated = original.negate().negate();

        // Double negation should restore original behavior
        assertFalse(doubleNegated.includeInCacheKey("Authorization"));
        assertTrue(doubleNegated.includeInCacheKey("Accept-Language"));
    }

    // ========== COMPLEX COMPOSITION TESTS ==========

    @Test
    void complexCompositionAndOrNegate() {
        // Include Accept-* headers, exclude Authorization, but always include Content-Type
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
                .matching(header -> header.startsWith("Accept-"))
                .and(CacheKeyHeaderFilter.excluding("Authorization"))
                .or(CacheKeyHeaderFilter.including("Content-Type"));

        // Accept-* headers (included by first part)
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Accept-Encoding"));

        // Content-Type (included by OR clause)
        assertTrue(filter.includeInCacheKey("Content-Type"));

        // Authorization (excluded by first part, not included by OR)
        assertFalse(filter.includeInCacheKey("Authorization"));

        // Other headers (excluded by first part, not included by OR)
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
    }

    @Test
    void complexCompositionMultipleExclusions() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
                .excluding("Authorization")
                .and(CacheKeyHeaderFilter.excludingPrefix("X-"))
                .and(CacheKeyHeaderFilter.excluding("User-Agent"));

        // All exclusions respected
        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("User-Agent"));

        // Other headers included
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
    }

    // ========== REAL-WORLD SCENARIOS ==========

    @Test
    void realWorldScenarioTokenRefreshCacheBloatSolution() {
        // Exclude Authorization to prevent cache bloat on token refresh,
        // but keep content-affecting headers
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excluding("Authorization");

        // Authorization excluded (solves cache bloat)
        assertFalse(filter.includeInCacheKey("Authorization"));

        // Content-affecting headers included (maintains correctness)
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Accept-Encoding"));
        assertTrue(filter.includeInCacheKey("Accept-Charset"));
    }

    @Test
    void realWorldScenarioExcludeTraceHeaders() {
        // Exclude all trace/debug headers
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter
                .excluding("Authorization")
                .and(CacheKeyHeaderFilter.excludingPrefix("X-"));

        // Trace headers excluded
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("X-Trace-ID"));
        assertFalse(filter.includeInCacheKey("X-Correlation-ID"));

        // Authorization excluded
        assertFalse(filter.includeInCacheKey("Authorization"));

        // Content headers included
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
    }

    @Test
    void realWorldScenarioContentNegotiationOnly() {
        // Include only content negotiation headers
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.including(
                "Accept-Language",
                "Accept-Encoding",
                "Accept-Charset"
        );

        // Content negotiation headers included
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Accept-Encoding"));
        assertTrue(filter.includeInCacheKey("Accept-Charset"));

        // Everything else excluded
        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("Content-Type"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
    }
}

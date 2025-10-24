package de.cuioss.http.client.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CacheKeyHeaderFilter}.
 *
 * @author Generated
 */
class CacheKeyHeaderFilterTest {

    // ========== PRESET FILTERS TESTS ==========

    @Test
    void testAll_includesAllHeaders() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.ALL;

        assertTrue(filter.includeInCacheKey("Authorization"));
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("X-Request-ID"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
        assertTrue(filter.includeInCacheKey(""));
    }

    @Test
    void testNone_excludesAllHeaders() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.NONE;

        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("Accept-Language"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("Content-Type"));
        assertFalse(filter.includeInCacheKey(""));
    }

    // ========== EXCLUDING TESTS ==========

    @Test
    void testExcluding_createsBlacklistFilter() {
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
    void testExcluding_caseInsensitive() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excluding("authorization");

        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("AUTHORIZATION"));
        assertFalse(filter.includeInCacheKey("authorization"));
        assertFalse(filter.includeInCacheKey("AuThOrIzAtIoN"));
    }

    @Test
    void testExcluding_emptyArray() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excluding();

        // No headers excluded, all included
        assertTrue(filter.includeInCacheKey("Authorization"));
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("X-Request-ID"));
    }

    // ========== INCLUDING TESTS ==========

    @Test
    void testIncluding_createsWhitelistFilter() {
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
    void testIncluding_caseInsensitive() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.including("accept-language");

        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("ACCEPT-LANGUAGE"));
        assertTrue(filter.includeInCacheKey("accept-language"));
        assertTrue(filter.includeInCacheKey("AcCePt-LaNgUaGe"));
    }

    @Test
    void testIncluding_emptyArray() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.including();

        // No headers included, all excluded
        assertFalse(filter.includeInCacheKey("Authorization"));
        assertFalse(filter.includeInCacheKey("Accept-Language"));
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
    }

    // ========== EXCLUDING PREFIX TESTS ==========

    @Test
    void testExcludingPrefix_filtersHeadersByPrefix() {
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
    void testExcludingPrefix_caseInsensitive() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.excludingPrefix("x-");

        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("x-request-id"));
        assertFalse(filter.includeInCacheKey("X-TRACE-ID"));
    }

    @Test
    void testExcludingPrefix_exactMatch() {
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
    void testMatching_customPredicate() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.matching(
            header -> !header.startsWith("X-") && !header.equals("Authorization")
        );

        // Excluded by predicate
        assertFalse(filter.includeInCacheKey("X-Request-ID"));
        assertFalse(filter.includeInCacheKey("Authorization"));

        // Included by predicate
        assertTrue(filter.includeInCacheKey("Accept-Language"));
        assertTrue(filter.includeInCacheKey("Content-Type"));
    }

    @Test
    void testMatching_complexPredicate() {
        CacheKeyHeaderFilter filter = CacheKeyHeaderFilter.matching(
            header -> header.startsWith("Accept-") || header.equals("Content-Type")
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
    void testAnd_combinesFiltersWithLogicalAnd() {
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
    void testAnd_requiresBothTrue() {
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
    void testOr_combinesFiltersWithLogicalOr() {
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
    void testOr_requiresEitherTrue() {
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
    void testNegate_invertsFilter() {
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
    void testNegate_doubleNegation() {
        CacheKeyHeaderFilter original = CacheKeyHeaderFilter.excluding("Authorization");
        CacheKeyHeaderFilter doubleNegated = original.negate().negate();

        // Double negation should restore original behavior
        assertFalse(doubleNegated.includeInCacheKey("Authorization"));
        assertTrue(doubleNegated.includeInCacheKey("Accept-Language"));
    }

    // ========== COMPLEX COMPOSITION TESTS ==========

    @Test
    void testComplexComposition_andOrNegate() {
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
    void testComplexComposition_multipleExclusions() {
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
    void testRealWorldScenario_tokenRefreshCacheBloatSolution() {
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
    void testRealWorldScenario_excludeTraceHeaders() {
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
    void testRealWorldScenario_contentNegotiationOnly() {
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

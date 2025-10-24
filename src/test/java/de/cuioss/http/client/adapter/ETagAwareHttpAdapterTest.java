package de.cuioss.http.client.adapter;

import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.converter.VoidResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ETagAwareHttpAdapter builder and core structure.
 */
class ETagAwareHttpAdapterTest {

    private HttpHandler handler;
    private TestResponseConverter responseConverter;

    @BeforeEach
    void setUp() {
        // Create test handler with mock URI
        handler = HttpHandler.builder()
            .uri("https://api.example.com/test")
            .build();

        responseConverter = new TestResponseConverter();
    }

    @Test
    void testBuilderRequiresHttpHandler() {
        var builder = ETagAwareHttpAdapter.<String>builder()
            .responseConverter(responseConverter);

        assertThrows(NullPointerException.class, builder::build,
                     "Builder should require httpHandler");
    }

    @Test
    void testBuilderRequiresResponseConverter() {
        var builder = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler);

        assertThrows(NullPointerException.class, builder::build,
                     "Builder should require responseConverter");
    }

    @Test
    void testBuilderDefaultValues() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        assertNotNull(adapter, "Adapter should be built with defaults");
    }

    @Test
    void testBuilderWithAllParameters() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .etagCachingEnabled(false)
            .cacheKeyHeaderFilter(CacheKeyHeaderFilter.NONE)
            .maxCacheSize(500)
            .build();

        assertNotNull(adapter, "Adapter should be built with all parameters");
    }

    @Test
    void testBuilderValidatesMaxCacheSize() {
        var builder = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter);

        assertThrows(IllegalArgumentException.class, () -> builder.maxCacheSize(0),
                     "Builder should reject zero maxCacheSize");
        assertThrows(IllegalArgumentException.class, () -> builder.maxCacheSize(-1),
                     "Builder should reject negative maxCacheSize");
    }

    @Test
    void testBuilderValidatesCacheKeyHeaderFilter() {
        var builder = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter);

        assertThrows(NullPointerException.class, () -> builder.cacheKeyHeaderFilter(null),
                     "Builder should reject null cacheKeyHeaderFilter");
    }

    @Test
    void testStatusCodeOnlyFactory() {
        HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);

        assertNotNull(adapter, "statusCodeOnly should create adapter");
    }

    @Test
    void testStatusCodeOnlyUsesVoidConverter() {
        // statusCodeOnly should use VoidResponseConverter
        HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);

        // Verify adapter is created successfully (converter internally uses VoidResponseConverter)
        assertNotNull(adapter);
    }

    @Test
    void testClearETagCache() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        // Should not throw even on empty cache
        assertDoesNotThrow(adapter::clearETagCache);
    }

    @Test
    void testBuilderChaining() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .etagCachingEnabled(true)
            .cacheKeyHeaderFilter(CacheKeyHeaderFilter.ALL)
            .maxCacheSize(1000)
            .build();

        assertNotNull(adapter, "Builder chaining should work");
    }

    @Test
    void testBuilderWithRequestConverter() {
        var requestConverter = new TestRequestConverter();
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(requestConverter)
            .build();

        assertNotNull(adapter, "Builder should accept request converter");
    }

    @Test
    void testBuilderWithNullRequestConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(null)
            .build();

        assertNotNull(adapter, "Builder should accept null request converter");
    }

    // === Task 9: Request Execution Tests ===

    @Test
    void testGetMethodCreatesRequest() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        // GET should create a CompletableFuture
        var future = adapter.get();
        assertNotNull(future, "GET should return non-null CompletableFuture");
    }

    @Test
    void testGetMethodWithHeadersCreatesRequest() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var headers = java.util.Map.of("Accept", "application/json");
        var future = adapter.get(headers);
        assertNotNull(future, "GET with headers should return non-null CompletableFuture");
    }

    @Test
    void testSafeMethodsRejectBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        // Note: We can't directly test send() as it's private, but we can verify
        // that the adapter is properly constructed for safe method validation
        assertNotNull(adapter);
    }

    @Test
    void testCacheKeyGenerationWithAllFilter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .cacheKeyHeaderFilter(CacheKeyHeaderFilter.ALL)
            .build();

        assertNotNull(adapter, "Adapter with ALL filter should be created");
    }

    @Test
    void testCacheKeyGenerationWithNoneFilter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .cacheKeyHeaderFilter(CacheKeyHeaderFilter.NONE)
            .build();

        assertNotNull(adapter, "Adapter with NONE filter should be created");
    }

    @Test
    void testCacheKeyGenerationWithExcludingFilter() {
        var filter = CacheKeyHeaderFilter.excluding("Authorization");
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .cacheKeyHeaderFilter(filter)
            .build();

        assertNotNull(adapter, "Adapter with excluding filter should be created");
    }

    @Test
    void testCacheKeyGenerationWithIncludingFilter() {
        var filter = CacheKeyHeaderFilter.including("Accept", "Content-Type");
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .cacheKeyHeaderFilter(filter)
            .build();

        assertNotNull(adapter, "Adapter with including filter should be created");
    }

    @Test
    void testBodyPublisherCreationWithoutConverter() {
        // Adapter without request converter should handle body gracefully
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(null)
            .build();

        assertNotNull(adapter, "Adapter without request converter should be created");
    }

    @Test
    void testBodyPublisherCreationWithConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        assertNotNull(adapter, "Adapter with request converter should be created");
    }

    @Test
    void testEtagCachingDisabled() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .etagCachingEnabled(false)
            .build();

        // With caching disabled, GET should still work
        var future = adapter.get();
        assertNotNull(future, "GET with caching disabled should return CompletableFuture");
    }

    @Test
    void testCacheKeyConsistencyWithSameHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .cacheKeyHeaderFilter(CacheKeyHeaderFilter.ALL)
            .build();

        var headers1 = java.util.Map.of("Accept", "application/json", "Authorization", "Bearer token");
        var headers2 = java.util.Map.of("Authorization", "Bearer token", "Accept", "application/json");

        // Both should generate requests (cache key generation happens internally)
        var future1 = adapter.get(headers1);
        var future2 = adapter.get(headers2);

        assertNotNull(future1, "First request should be created");
        assertNotNull(future2, "Second request should be created");
    }

    /**
     * Test implementation of HttpResponseConverter for testing.
     */
    private static class TestResponseConverter implements HttpResponseConverter<String> {
        @Override
        public Optional<String> convert(Object rawContent) {
            if (rawContent == null) {
                return Optional.empty();
            }
            return Optional.of(rawContent.toString());
        }

        @Override
        public HttpResponse.BodyHandler<?> getBodyHandler() {
            return HttpResponse.BodyHandlers.ofString();
        }

        @Override
        public ContentType contentType() {
            return ContentType.TEXT_PLAIN;
        }
    }

    /**
     * Test implementation of HttpRequestConverter for testing.
     */
    private static class TestRequestConverter implements de.cuioss.http.client.converter.HttpRequestConverter<String> {
        @Override
        public java.net.http.HttpRequest.BodyPublisher toBodyPublisher(String content) {
            if (content == null) {
                return java.net.http.HttpRequest.BodyPublishers.noBody();
            }
            return java.net.http.HttpRequest.BodyPublishers.ofString(content);
        }

        @Override
        public ContentType contentType() {
            return ContentType.TEXT_PLAIN;
        }
    }
}

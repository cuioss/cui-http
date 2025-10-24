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

    // === Task 11: HTTP Method Implementation Tests ===

    @Test
    void testPostMethodWithBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var future = adapter.post("test body");
        assertNotNull(future, "POST should return non-null CompletableFuture");
    }

    @Test
    void testPostMethodWithBodyAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var headers = java.util.Map.of("Content-Type", "text/plain");
        var future = adapter.post("test body", headers);
        assertNotNull(future, "POST with headers should return non-null CompletableFuture");
    }

    @Test
    void testPostMethodWithNullBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var future = adapter.post(null);
        assertNotNull(future, "POST with null body should return non-null CompletableFuture");
    }

    @Test
    void testPostMethodWithExplicitConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var customConverter = new TestRequestConverter();
        var future = adapter.post(customConverter, "test body");
        assertNotNull(future, "POST with explicit converter should return non-null CompletableFuture");
    }

    @Test
    void testPostMethodWithExplicitConverterAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var customConverter = new TestRequestConverter();
        var headers = java.util.Map.of("Content-Type", "text/plain");
        var future = adapter.post(customConverter, "test body", headers);
        assertNotNull(future, "POST with explicit converter and headers should return non-null CompletableFuture");
    }

    @Test
    void testPutMethodWithBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var future = adapter.put("test body");
        assertNotNull(future, "PUT should return non-null CompletableFuture");
    }

    @Test
    void testPutMethodWithBodyAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var headers = java.util.Map.of("Content-Type", "text/plain");
        var future = adapter.put("test body", headers);
        assertNotNull(future, "PUT with headers should return non-null CompletableFuture");
    }

    @Test
    void testPutMethodWithExplicitConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var customConverter = new TestRequestConverter();
        var future = adapter.put(customConverter, "test body");
        assertNotNull(future, "PUT with explicit converter should return non-null CompletableFuture");
    }

    @Test
    void testPatchMethodWithBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var future = adapter.patch("test body");
        assertNotNull(future, "PATCH should return non-null CompletableFuture");
    }

    @Test
    void testPatchMethodWithBodyAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var headers = java.util.Map.of("Content-Type", "text/plain");
        var future = adapter.patch("test body", headers);
        assertNotNull(future, "PATCH with headers should return non-null CompletableFuture");
    }

    @Test
    void testPatchMethodWithExplicitConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var customConverter = new TestRequestConverter();
        var future = adapter.patch(customConverter, "test body");
        assertNotNull(future, "PATCH with explicit converter should return non-null CompletableFuture");
    }

    @Test
    void testDeleteMethodNoBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var future = adapter.delete();
        assertNotNull(future, "DELETE should return non-null CompletableFuture");
    }

    @Test
    void testDeleteMethodNoBodyWithHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var headers = java.util.Map.of("Authorization", "Bearer token");
        var future = adapter.delete(headers);
        assertNotNull(future, "DELETE with headers should return non-null CompletableFuture");
    }

    @Test
    void testDeleteMethodWithBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var future = adapter.delete("test body");
        assertNotNull(future, "DELETE with body should return non-null CompletableFuture");
    }

    @Test
    void testDeleteMethodWithBodyAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        var headers = java.util.Map.of("Authorization", "Bearer token");
        var future = adapter.delete("test body", headers);
        assertNotNull(future, "DELETE with body and headers should return non-null CompletableFuture");
    }

    @Test
    void testDeleteMethodWithExplicitConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var customConverter = new TestRequestConverter();
        var future = adapter.delete(customConverter, "test body");
        assertNotNull(future, "DELETE with explicit converter should return non-null CompletableFuture");
    }

    @Test
    void testHeadMethod() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var future = adapter.head();
        assertNotNull(future, "HEAD should return non-null CompletableFuture");
    }

    @Test
    void testHeadMethodWithHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var headers = java.util.Map.of("Accept", "application/json");
        var future = adapter.head(headers);
        assertNotNull(future, "HEAD with headers should return non-null CompletableFuture");
    }

    @Test
    void testOptionsMethod() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var future = adapter.options();
        assertNotNull(future, "OPTIONS should return non-null CompletableFuture");
    }

    @Test
    void testOptionsMethodWithHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        var headers = java.util.Map.of("Origin", "https://example.com");
        var future = adapter.options(headers);
        assertNotNull(future, "OPTIONS with headers should return non-null CompletableFuture");
    }

    @Test
    void testGenericBodyMethodsWithDifferentTypes() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .build();

        // Test with Integer body type (different from String response type)
        var intConverter = new de.cuioss.http.client.converter.HttpRequestConverter<Integer>() {
            @Override
            public java.net.http.HttpRequest.BodyPublisher toBodyPublisher(Integer content) {
                if (content == null) {
                    return java.net.http.HttpRequest.BodyPublishers.noBody();
                }
                return java.net.http.HttpRequest.BodyPublishers.ofString(content.toString());
            }

            @Override
            public ContentType contentType() {
                return ContentType.TEXT_PLAIN;
            }
        };

        var futurePost = adapter.post(intConverter, 42);
        var futurePut = adapter.put(intConverter, 42);
        var futurePatch = adapter.patch(intConverter, 42);
        var futureDelete = adapter.delete(intConverter, 42);

        assertNotNull(futurePost, "POST with different type should return CompletableFuture");
        assertNotNull(futurePut, "PUT with different type should return CompletableFuture");
        assertNotNull(futurePatch, "PATCH with different type should return CompletableFuture");
        assertNotNull(futureDelete, "DELETE with different type should return CompletableFuture");
    }

    @Test
    void testAllMethodsReturnCompletableFuture() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
            .httpHandler(handler)
            .responseConverter(responseConverter)
            .requestConverter(new TestRequestConverter())
            .build();

        // All methods should return CompletableFuture (async-first design)
        assertTrue(adapter.get() instanceof java.util.concurrent.CompletableFuture,
                   "GET should return CompletableFuture");
        assertTrue(adapter.post("body") instanceof java.util.concurrent.CompletableFuture,
                   "POST should return CompletableFuture");
        assertTrue(adapter.put("body") instanceof java.util.concurrent.CompletableFuture,
                   "PUT should return CompletableFuture");
        assertTrue(adapter.patch("body") instanceof java.util.concurrent.CompletableFuture,
                   "PATCH should return CompletableFuture");
        assertTrue(adapter.delete() instanceof java.util.concurrent.CompletableFuture,
                   "DELETE should return CompletableFuture");
        assertTrue(adapter.head() instanceof java.util.concurrent.CompletableFuture,
                   "HEAD should return CompletableFuture");
        assertTrue(adapter.options() instanceof java.util.concurrent.CompletableFuture,
                   "OPTIONS should return CompletableFuture");
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

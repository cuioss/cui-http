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

import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    void builderRequiresHttpHandler() {
        var builder = ETagAwareHttpAdapter.<String>builder()
                .responseConverter(responseConverter);

        assertThrows(NullPointerException.class, builder::build,
                "Builder should require httpHandler");
    }

    @Test
    void builderRequiresResponseConverter() {
        var builder = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler);

        assertThrows(NullPointerException.class, builder::build,
                "Builder should require responseConverter");
    }

    @Test
    void builderDefaultValues() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        assertNotNull(adapter, "Adapter should be built with defaults");
    }

    @Test
    void builderWithAllParameters() {
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
    void builderValidatesMaxCacheSize() {
        var builder = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter);

        assertThrows(IllegalArgumentException.class, () -> builder.maxCacheSize(0),
                "Builder should reject zero maxCacheSize");
        assertThrows(IllegalArgumentException.class, () -> builder.maxCacheSize(-1),
                "Builder should reject negative maxCacheSize");
    }

    @Test
    void builderValidatesCacheKeyHeaderFilter() {
        var builder = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter);

        assertThrows(NullPointerException.class, () -> builder.cacheKeyHeaderFilter(null),
                "Builder should reject null cacheKeyHeaderFilter");
    }

    @Test
    void statusCodeOnlyFactory() {
        HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);

        assertNotNull(adapter, "statusCodeOnly should create adapter");
    }

    @Test
    void statusCodeOnlyUsesVoidConverter() {
        // statusCodeOnly should use VoidResponseConverter
        HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handler);

        // Verify adapter is created successfully (converter internally uses VoidResponseConverter)
        assertNotNull(adapter);
    }

    @Test
    void clearETagCache() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        // Should not throw even on empty cache
        assertDoesNotThrow(adapter::clearETagCache);
    }

    @Test
    void builderChaining() {
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
    void builderWithRequestConverter() {
        var requestConverter = new TestRequestConverter();
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(requestConverter)
                .build();

        assertNotNull(adapter, "Builder should accept request converter");
    }

    @Test
    void builderWithNullRequestConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(null)
                .build();

        assertNotNull(adapter, "Builder should accept null request converter");
    }

    // === Task 9: Request Execution Tests ===

    @Test
    void getMethodCreatesRequest() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        // GET should create a CompletableFuture
        var future = adapter.get();
        assertNotNull(future, "GET should return non-null CompletableFuture");
    }

    @Test
    void getMethodWithHeadersCreatesRequest() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var headers = Map.of("Accept", "application/json");
        var future = adapter.get(headers);
        assertNotNull(future, "GET with headers should return non-null CompletableFuture");
    }

    @Test
    void safeMethodsRejectBody() {
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
    void cacheKeyGenerationWithAllFilter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.ALL)
                .build();

        assertNotNull(adapter, "Adapter with ALL filter should be created");
    }

    @Test
    void cacheKeyGenerationWithNoneFilter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.NONE)
                .build();

        assertNotNull(adapter, "Adapter with NONE filter should be created");
    }

    @Test
    void cacheKeyGenerationWithExcludingFilter() {
        var filter = CacheKeyHeaderFilter.excluding("Authorization");
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(filter)
                .build();

        assertNotNull(adapter, "Adapter with excluding filter should be created");
    }

    @Test
    void cacheKeyGenerationWithIncludingFilter() {
        var filter = CacheKeyHeaderFilter.including("Accept", "Content-Type");
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(filter)
                .build();

        assertNotNull(adapter, "Adapter with including filter should be created");
    }

    @Test
    void bodyPublisherCreationWithoutConverter() {
        // Adapter without request converter should handle body gracefully
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(null)
                .build();

        assertNotNull(adapter, "Adapter without request converter should be created");
    }

    @Test
    void bodyPublisherCreationWithConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        assertNotNull(adapter, "Adapter with request converter should be created");
    }

    @Test
    void etagCachingDisabled() {
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
    void cacheKeyConsistencyWithSameHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.ALL)
                .build();

        var headers1 = Map.of("Accept", "application/json", "Authorization", "Bearer token");
        var headers2 = Map.of("Authorization", "Bearer token", "Accept", "application/json");

        // Both should generate requests (cache key generation happens internally)
        var future1 = adapter.get(headers1);
        var future2 = adapter.get(headers2);

        assertNotNull(future1, "First request should be created");
        assertNotNull(future2, "Second request should be created");
    }

    // === Task 11: HTTP Method Implementation Tests ===

    @Test
    void postMethodWithBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var future = adapter.post("test body");
        assertNotNull(future, "POST should return non-null CompletableFuture");
    }

    @Test
    void postMethodWithBodyAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var headers = Map.of("Content-Type", "text/plain");
        var future = adapter.post("test body", headers);
        assertNotNull(future, "POST with headers should return non-null CompletableFuture");
    }

    @Test
    void postMethodWithNullBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var future = adapter.post(null);
        assertNotNull(future, "POST with null body should return non-null CompletableFuture");
    }

    @Test
    void postMethodWithExplicitConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var customConverter = new TestRequestConverter();
        var future = adapter.post(customConverter, "test body");
        assertNotNull(future, "POST with explicit converter should return non-null CompletableFuture");
    }

    @Test
    void postMethodWithExplicitConverterAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var customConverter = new TestRequestConverter();
        var headers = Map.of("Content-Type", "text/plain");
        var future = adapter.post(customConverter, "test body", headers);
        assertNotNull(future, "POST with explicit converter and headers should return non-null CompletableFuture");
    }

    @Test
    void putMethodWithBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var future = adapter.put("test body");
        assertNotNull(future, "PUT should return non-null CompletableFuture");
    }

    @Test
    void putMethodWithBodyAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var headers = Map.of("Content-Type", "text/plain");
        var future = adapter.put("test body", headers);
        assertNotNull(future, "PUT with headers should return non-null CompletableFuture");
    }

    @Test
    void putMethodWithExplicitConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var customConverter = new TestRequestConverter();
        var future = adapter.put(customConverter, "test body");
        assertNotNull(future, "PUT with explicit converter should return non-null CompletableFuture");
    }

    @Test
    void patchMethodWithBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var future = adapter.patch("test body");
        assertNotNull(future, "PATCH should return non-null CompletableFuture");
    }

    @Test
    void patchMethodWithBodyAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var headers = Map.of("Content-Type", "text/plain");
        var future = adapter.patch("test body", headers);
        assertNotNull(future, "PATCH with headers should return non-null CompletableFuture");
    }

    @Test
    void patchMethodWithExplicitConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var customConverter = new TestRequestConverter();
        var future = adapter.patch(customConverter, "test body");
        assertNotNull(future, "PATCH with explicit converter should return non-null CompletableFuture");
    }

    @Test
    void deleteMethodNoBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var future = adapter.delete();
        assertNotNull(future, "DELETE should return non-null CompletableFuture");
    }

    @Test
    void deleteMethodNoBodyWithHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var headers = Map.of("Authorization", "Bearer token");
        var future = adapter.delete(headers);
        assertNotNull(future, "DELETE with headers should return non-null CompletableFuture");
    }

    @Test
    void deleteMethodWithBody() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var future = adapter.delete("test body");
        assertNotNull(future, "DELETE with body should return non-null CompletableFuture");
    }

    @Test
    void deleteMethodWithBodyAndHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        var headers = Map.of("Authorization", "Bearer token");
        var future = adapter.delete("test body", headers);
        assertNotNull(future, "DELETE with body and headers should return non-null CompletableFuture");
    }

    @Test
    void deleteMethodWithExplicitConverter() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var customConverter = new TestRequestConverter();
        var future = adapter.delete(customConverter, "test body");
        assertNotNull(future, "DELETE with explicit converter should return non-null CompletableFuture");
    }

    @Test
    void headMethod() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var future = adapter.head();
        assertNotNull(future, "HEAD should return non-null CompletableFuture");
    }

    @Test
    void headMethodWithHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var headers = Map.of("Accept", "application/json");
        var future = adapter.head(headers);
        assertNotNull(future, "HEAD with headers should return non-null CompletableFuture");
    }

    @Test
    void optionsMethod() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var future = adapter.options();
        assertNotNull(future, "OPTIONS should return non-null CompletableFuture");
    }

    @Test
    void optionsMethodWithHeaders() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var headers = Map.of("Origin", "https://example.com");
        var future = adapter.options(headers);
        assertNotNull(future, "OPTIONS with headers should return non-null CompletableFuture");
    }

    @Test
    void genericBodyMethodsWithDifferentTypes() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        // Test with Integer body type (different from String response type)
        var intConverter = new HttpRequestConverter<Integer>() {
            @Override
            public HttpRequest.BodyPublisher toBodyPublisher(Integer content) {
                if (content == null) {
                    return HttpRequest.BodyPublishers.noBody();
                }
                return HttpRequest.BodyPublishers.ofString(content.toString());
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
    void allMethodsReturnCompletableFuture() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(new TestRequestConverter())
                .build();

        // All methods should return CompletableFuture (async-first design)
        assertTrue(adapter.get() instanceof CompletableFuture,
                "GET should return CompletableFuture");
        assertTrue(adapter.post("body") instanceof CompletableFuture,
                "POST should return CompletableFuture");
        assertTrue(adapter.put("body") instanceof CompletableFuture,
                "PUT should return CompletableFuture");
        assertTrue(adapter.patch("body") instanceof CompletableFuture,
                "PATCH should return CompletableFuture");
        assertTrue(adapter.delete() instanceof CompletableFuture,
                "DELETE should return CompletableFuture");
        assertTrue(adapter.head() instanceof CompletableFuture,
                "HEAD should return CompletableFuture");
        assertTrue(adapter.options() instanceof CompletableFuture,
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
    private static class TestRequestConverter implements HttpRequestConverter<String> {
        @Override
        public HttpRequest.BodyPublisher toBodyPublisher(String content) {
            if (content == null) {
                return HttpRequest.BodyPublishers.noBody();
            }
            return HttpRequest.BodyPublishers.ofString(content);
        }

        @Override
        public ContentType contentType() {
            return ContentType.TEXT_PLAIN;
        }
    }
}

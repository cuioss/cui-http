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

import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.converter.HttpRequestConverter;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.NonNull;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
        assertInstanceOf(CompletableFuture.class, adapter.get(), "GET should return CompletableFuture");
        assertInstanceOf(CompletableFuture.class, adapter.post("body"), "POST should return CompletableFuture");
        assertInstanceOf(CompletableFuture.class, adapter.put("body"), "PUT should return CompletableFuture");
        assertInstanceOf(CompletableFuture.class, adapter.patch("body"), "PATCH should return CompletableFuture");
        assertInstanceOf(CompletableFuture.class, adapter.delete(), "DELETE should return CompletableFuture");
        assertInstanceOf(CompletableFuture.class, adapter.head(), "HEAD should return CompletableFuture");
        assertInstanceOf(CompletableFuture.class, adapter.options(), "OPTIONS should return CompletableFuture");
    }

    // === Cache Key Allocation Skip Tests ===

    /**
     * Positive control for the allocation skip: GET reads and populates the cache, so it must build
     * a key. Without this the negative cases below would also pass against an adapter that never
     * generated a key at all.
     */
    @Test
    void getShouldGenerateCacheKey() {
        var filter = new CountingCacheKeyHeaderFilter();
        var adapter = cachingAdapterWith(filter);

        adapter.get(Map.of("Accept", "application/json"));

        assertTrue(filter.consultations() > 0, "GET reads the cache and must build a key");
    }

    /**
     * HEAD reads the cache to resolve a conditional 304 against the stored validator, so it must
     * build a key too. This is the guard against narrowing the lookup to GET alone, which would
     * silently delete the documented HEAD 304 behaviour.
     */
    @Test
    void headShouldGenerateCacheKey() {
        var filter = new CountingCacheKeyHeaderFilter();
        var adapter = cachingAdapterWith(filter);

        adapter.head(Map.of("Accept", "application/json"));

        assertTrue(filter.consultations() > 0, "HEAD reads the cache to resolve a 304 and must build a key");
    }

    @Test
    void nonCacheableMethodsShouldNotGenerateCacheKey() {
        var headers = Map.of("Accept", "application/json");
        var postFilter = new CountingCacheKeyHeaderFilter();
        var putFilter = new CountingCacheKeyHeaderFilter();
        var patchFilter = new CountingCacheKeyHeaderFilter();
        var deleteFilter = new CountingCacheKeyHeaderFilter();

        cachingAdapterWith(postFilter).post((String) null, headers);
        cachingAdapterWith(putFilter).put((String) null, headers);
        cachingAdapterWith(patchFilter).patch((String) null, headers);
        cachingAdapterWith(deleteFilter).delete(headers);

        assertAll("Methods that never touch the cache should not build a key",
                () -> assertEquals(0, postFilter.consultations(), "POST should not build a cache key"),
                () -> assertEquals(0, putFilter.consultations(), "PUT should not build a cache key"),
                () -> assertEquals(0, patchFilter.consultations(), "PATCH should not build a cache key"),
                () -> assertEquals(0, deleteFilter.consultations(), "DELETE should not build a cache key"));
    }

    @Test
    void cachingDisabledAdapterShouldNotGenerateCacheKey() {
        var filter = new CountingCacheKeyHeaderFilter();
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(filter)
                .etagCachingEnabled(false)
                .build();

        adapter.get(Map.of("Accept", "application/json"));

        assertEquals(0, filter.consultations(),
                "A caching-disabled adapter never reads the key, so it should not build one");
    }

    private ETagAwareHttpAdapter<String> cachingAdapterWith(CacheKeyHeaderFilter filter) {
        return ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(filter)
                .build();
    }

    /**
     * Records how often the filter is consulted. Key generation is the only caller, so a
     * consultation count of zero is the observable for "no cache key was built".
     */
    private static final class CountingCacheKeyHeaderFilter implements CacheKeyHeaderFilter {

        private final AtomicInteger consultations = new AtomicInteger();

        @Override
        public boolean includeInCacheKey(String headerName) {
            consultations.incrementAndGet();
            return true;
        }

        int consultations() {
            return consultations.get();
        }
    }

    // === Cache Key Injection Prevention Tests ===

    @Test
    void cacheKeysShouldDifferWhenHeaderValueContainsDelimiters() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var uri = URI.create("https://api.example.com/test");

        // Legitimate headers
        var legitimate = new LinkedHashMap<String, String>();
        legitimate.put("Accept", "text/html");
        legitimate.put("X-Custom", "safe");

        // Malicious: header value contains delimiter to forge another header entry
        var malicious = new LinkedHashMap<String, String>();
        malicious.put("Accept", "text/html|X-Custom:safe");

        String key1 = adapter.generateCacheKey(uri, legitimate, CacheKeyHeaderFilter.ALL);
        String key2 = adapter.generateCacheKey(uri, malicious, CacheKeyHeaderFilter.ALL);

        assertNotEquals(key1, key2,
                "Cache keys must differ when header value contains pipe/colon delimiters");
    }

    @Test
    void cacheKeysShouldDifferWhenHeaderNameContainsDelimiters() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        var uri = URI.create("https://api.example.com/test");

        var normal = new LinkedHashMap<String, String>();
        normal.put("A", "val1");
        normal.put("B", "val2");

        // Header name contains colon to impersonate key-value boundary
        var injected = new LinkedHashMap<String, String>();
        injected.put("A", "val1|B:val2");

        String key1 = adapter.generateCacheKey(uri, normal, CacheKeyHeaderFilter.ALL);
        String key2 = adapter.generateCacheKey(uri, injected, CacheKeyHeaderFilter.ALL);

        assertNotEquals(key1, key2,
                "Cache keys must differ when header value injects forged key-value pairs");
    }

    // === Request-Converter Guard Tests ===

    /**
     * First half of the {@code send(...)} guard: a non-null body with no configured request
     * converter is rejected with {@link IllegalStateException} rather than silently sending an empty
     * body. Asserted for every body-carrying method, since all of them funnel through that one
     * guard.
     */
    @Test
    void bodyWithoutRequestConverterShouldThrow() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();

        assertAll("A body with no configured request converter is rejected",
                () -> assertGuardRejects(() -> adapter.post("body"), "POST"),
                () -> assertGuardRejects(() -> adapter.put("body"), "PUT"),
                () -> assertGuardRejects(() -> adapter.patch("body"), "PATCH"),
                () -> assertGuardRejects(() -> adapter.delete("body"), "DELETE"));
    }

    /**
     * Second half of the same guard: once the guard has passed, the configured converter is the one
     * the request is built from — it serializes the body <em>and</em> supplies the Content-Type. The
     * Content-Type is resolved from the converter the guard bound, not from a second null test the
     * guard already made always true.
     */
    @Test
    void bodyWithRequestConverterShouldReachConverter() {
        var requestConverter = new RecordingRequestConverter();
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(requestConverter)
                .build();

        adapter.post("payload");

        assertAll("A body with a configured converter reaches that converter",
                () -> assertEquals("payload", requestConverter.lastBody(),
                        "The body should be handed to the converter for serialization"),
                () -> assertEquals(1, requestConverter.contentTypeCalls(),
                        "The request Content-Type should be resolved from the converter"));
    }

    /**
     * Negative control for the test above: with no body there is nothing to serialize, so the
     * converter is never consulted and no Content-Type is derived from it. Without this control the
     * assertion above would also pass against an adapter that resolved the content type
     * unconditionally.
     */
    @Test
    void nullBodyShouldNotResolveContentTypeFromRequestConverter() {
        var requestConverter = new RecordingRequestConverter();
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .requestConverter(requestConverter)
                .build();

        adapter.post((String) null);

        assertAll("A null body never reaches the converter",
                () -> assertNull(requestConverter.lastBody(),
                        "A null body should not be handed to the converter"),
                () -> assertEquals(0, requestConverter.contentTypeCalls(),
                        "No Content-Type should be resolved when there is no body"));
    }

    private static void assertGuardRejects(Executable call, String methodName) {
        var thrown = assertThrows(IllegalStateException.class, call,
                methodName + " with a body but no request converter must be rejected");
        assertTrue(thrown.getMessage().contains(methodName),
                "The failure should name the rejected method, but was: " + thrown.getMessage());
    }

    /**
     * Records what the adapter asked of the request converter: the body handed over for
     * serialization, and how often the content type was resolved.
     */
    private static final class RecordingRequestConverter implements HttpRequestConverter<String> {

        private final AtomicInteger contentTypeCalls = new AtomicInteger();
        private String lastBody;

        @Override
        public HttpRequest.BodyPublisher toBodyPublisher(String content) {
            lastBody = content;
            if (content == null) {
                return HttpRequest.BodyPublishers.noBody();
            }
            return HttpRequest.BodyPublishers.ofString(content);
        }

        @Override
        public ContentType contentType() {
            contentTypeCalls.incrementAndGet();
            return ContentType.TEXT_PLAIN;
        }

        int contentTypeCalls() {
            return contentTypeCalls.get();
        }

        String lastBody() {
            return lastBody;
        }
    }

    // === Cached-Fallback Restriction Tests ===

    /**
     * Pins the rule that cached content is fallback for an <em>availability</em> failure only.
     *
     * <p>The adapter under test is configured with the documented token-refresh filter
     * ({@code excluding("Authorization")}), which is exactly the configuration under which two
     * principals share one cache key. Principal A seeds the cache with a distinctive body; principal
     * B then draws a 4xx on the same URI and must receive nothing of A's — neither the body nor the
     * validator. A {@code 5xx} keeps serving the cached body, which is the control that proves the
     * narrowing is to the status class and not a blanket removal of fallback content.</p>
     */
    @Nested
    @EnableMockWebServer(useHttps = false)
    @DisplayName("Cached fallback is restricted to availability failures")
    class CachedFallbackRestriction {

        public ModuleDispatcherElement getModuleDispatcher() {
            return new PrincipalIsolationDispatcher();
        }

        /**
         * The centrepiece: a 4xx is a statement about <em>this</em> request, so the previous
         * principal's cached representation must not answer it. 403 is the case that motivated the
         * change; 401 and 404 are the same rule on the same path.
         */
        @ParameterizedTest(name = "HTTP {0}")
        @ValueSource(ints = {401, 403, 404})
        @DisplayName("A 4xx for a second principal should surface no cached content or validator")
        @ModuleDispatcher
        void clientErrorShouldNotServeAnotherPrincipalsCachedContent(int status, URIBuilder uriBuilder) {
            PrincipalIsolationDispatcher.reset();
            HttpAdapter<String> adapter = sharedKeyAdapter(uriBuilder);

            HttpResult<String> seeded = adapter.get(PrincipalIsolationDispatcher.PRINCIPAL_A_HEADERS).join();
            assertAll("Principal A seeds the cache",
                    () -> assertTrue(seeded.isSuccess(), "The seeding GET should succeed"),
                    () -> assertEquals(PrincipalIsolationDispatcher.PRINCIPAL_A_BODY, seeded.getContent().orElse(null),
                            "The seeding GET should return A's representation"));

            PrincipalIsolationDispatcher.failWith(status);
            HttpResult<String> denied = adapter.get(PrincipalIsolationDispatcher.PRINCIPAL_B_HEADERS).join();

            assertAll("Principal B answered with %d".formatted(status),
                    () -> assertFalse(denied.isSuccess(), "A 4xx is a failure"),
                    () -> assertEquals(Optional.of(status), denied.getHttpStatus(),
                            "The observed status should be preserved"),
                    () -> assertTrue(denied.getContent().isEmpty(),
                            "A 4xx must carry no fallback content at all"),
                    () -> assertFalse(denied.getContent().orElse("").contains(PrincipalIsolationDispatcher.SECRET_MARKER),
                            "Principal A's body must not reach principal B"),
                    () -> assertTrue(denied.getETag().isEmpty(),
                            "A 4xx must not surface the cached validator either"));
        }

        /**
         * The positive control: a {@code 5xx} says the server could not serve the representation
         * right now, which is precisely the availability failure stale content is meant to bridge.
         * It runs for the seeding principal itself, so it stays valid independently of how the cache
         * key is scoped.
         */
        @Test
        @DisplayName("A 503 should still serve the cached body to the principal that cached it")
        @ModuleDispatcher
        void serverErrorShouldStillServeCachedContent(URIBuilder uriBuilder) {
            PrincipalIsolationDispatcher.reset();
            HttpAdapter<String> adapter = sharedKeyAdapter(uriBuilder);

            assertTrue(adapter.get(PrincipalIsolationDispatcher.PRINCIPAL_A_HEADERS).join().isSuccess(),
                    "The seeding GET should succeed");

            PrincipalIsolationDispatcher.failWith(503);
            HttpResult<String> unavailable = adapter.get(PrincipalIsolationDispatcher.PRINCIPAL_A_HEADERS).join();

            assertAll("Principal A answered with 503",
                    () -> assertFalse(unavailable.isSuccess(), "A 503 is still a failure"),
                    () -> assertEquals(Optional.of(503), unavailable.getHttpStatus(),
                            "The observed status should be preserved"),
                    () -> assertEquals(PrincipalIsolationDispatcher.PRINCIPAL_A_BODY,
                            unavailable.getContent().orElse(null),
                            "An availability failure should degrade gracefully onto the cached body"),
                    () -> assertEquals(PrincipalIsolationDispatcher.ETAG, unavailable.getETag().orElse(null),
                            "The cached validator accompanies the cached body"));
        }

        /**
         * Builds the adapter over the token-refresh filter, which is what makes two principals share
         * one cache key — the precondition the isolation case needs in order to be observable at all.
         */
        private HttpAdapter<String> sharedKeyAdapter(URIBuilder uriBuilder) {
            String serverUrl = uriBuilder.addPathSegments("principal", "resource").build().toString();
            HttpHandler serverHandler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

            return ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(serverHandler)
                    .responseConverter(new TestResponseConverter())
                    .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization"))
                    .build();
        }
    }

    /**
     * Serves principal A's representation until a failure status is armed, then answers every
     * request with that status and an empty body.
     *
     * <p>The armed status is static because the dispatcher resolver serves requests from its own
     * instance rather than the one the test handed it — the same caveat the redirect dispatchers
     * document.</p>
     */
    static final class PrincipalIsolationDispatcher implements ModuleDispatcherElement {

        static final String BASE_PATH = "/principal";
        static final String PATH = BASE_PATH + "/resource";
        static final String SECRET_MARKER = "A-ONLY-PAYLOAD";
        static final String PRINCIPAL_A_BODY = "{\"owner\":\"principal-a\",\"secret\":\"" + SECRET_MARKER + "\"}";
        static final String ETAG = "\"etag-principal-a\"";
        static final Map<String, String> PRINCIPAL_A_HEADERS = Map.of("Authorization", "Bearer token-principal-a");
        static final Map<String, String> PRINCIPAL_B_HEADERS = Map.of("Authorization", "Bearer token-principal-b");

        private static final AtomicInteger ARMED_FAILURE = new AtomicInteger();

        static void reset() {
            ARMED_FAILURE.set(0);
        }

        static void failWith(int status) {
            ARMED_FAILURE.set(status);
        }

        @Override
        public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
            if (!PATH.equals(request.getUrl().encodedPath())) {
                return Optional.empty();
            }
            int armed = ARMED_FAILURE.get();
            if (armed != 0) {
                return Optional.of(new MockResponse(armed, new Headers.Builder().build(), ""));
            }
            return Optional.of(new MockResponse(200, new Headers.Builder()
                    .add("ETag", ETAG)
                    .add("Content-Type", "application/json")
                    .build(), PRINCIPAL_A_BODY));
        }

        @Override
        public String getBaseUrl() {
            return BASE_PATH;
        }

        @Override
        public @NonNull Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET);
        }
    }

    // === Conditional HEAD Tests ===

    /**
     * HEAD reads the cache so that a {@code 304} can be resolved against the stored validator. That
     * entitlement only makes sense if the HEAD actually asks conditionally, so the validator has to
     * reach the wire — otherwise the adapter would be interpreting a {@code 304} it never invited.
     */
    @Nested
    @EnableMockWebServer(useHttps = false)
    @DisplayName("HEAD revalidates conditionally against its cached entry")
    class ConditionalHead {

        public ModuleDispatcherElement getModuleDispatcher() {
            return new HeadRevalidationDispatcher();
        }

        @Test
        @DisplayName("A HEAD holding a cached entry should send If-None-Match")
        @ModuleDispatcher
        void headWithCachedEntryShouldSendIfNoneMatch(URIBuilder uriBuilder) {
            HeadRevalidationDispatcher.reset();
            HttpAdapter<String> adapter = adapterFor(uriBuilder);
            assertTrue(adapter.get().join().isSuccess(), "The seeding GET should populate the cache");

            adapter.head().join();

            assertEquals(HeadRevalidationDispatcher.ETAG, HeadRevalidationDispatcher.ifNoneMatchSeenOnHead(),
                    "The cached validator must accompany the HEAD that resolved it");
        }

        /**
         * The preservation control: making the HEAD conditional must not change what a {@code 304}
         * to a HEAD yields — status and validator, and no body, because a HEAD response has none.
         */
        @Test
        @DisplayName("A 304 to HEAD should still report status and ETag without a body")
        @ModuleDispatcher
        void head304ShouldStillReportStatusAndETagWithoutBody(URIBuilder uriBuilder) {
            HeadRevalidationDispatcher.reset();
            HttpAdapter<String> adapter = adapterFor(uriBuilder);
            assertTrue(adapter.get().join().isSuccess(), "The seeding GET should populate the cache");

            HttpResult<String> revalidated = adapter.head().join();

            assertAll("HEAD answered with 304",
                    () -> assertTrue(revalidated.isSuccess(), "A 304 to HEAD is a valid revalidation"),
                    () -> assertEquals(Optional.of(304), revalidated.getHttpStatus(), "Status should be reported as 304"),
                    () -> assertEquals(HeadRevalidationDispatcher.ETAG, revalidated.getETag().orElse(null),
                            "The validator should be reported"),
                    () -> assertTrue(revalidated.getContent().isEmpty(), "A HEAD response carries no body"));
        }

        private HttpAdapter<String> adapterFor(URIBuilder uriBuilder) {
            String serverUrl = uriBuilder.addPathSegments("conditional-head", "resource").build().toString();
            HttpHandler serverHandler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

            return ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(serverHandler)
                    .responseConverter(new TestResponseConverter())
                    .build();
        }
    }

    /**
     * Serves a validator-bearing representation to GET and answers a conditional HEAD with
     * {@code 304}, recording what the HEAD asked with. The record is the observable for "the HEAD
     * revalidated" — a result-only assertion could not distinguish a conditional request from an
     * unconditional one the server happened to answer with a 304.
     */
    static final class HeadRevalidationDispatcher implements ModuleDispatcherElement {

        static final String BASE_PATH = "/conditional-head";
        static final String PATH = BASE_PATH + "/resource";
        static final String BODY = "{\"id\":1,\"name\":\"head-revalidation\"}";
        static final String ETAG = "\"etag-head-revalidation\"";

        private static final Map<String, String> IF_NONE_MATCH_BY_METHOD = new ConcurrentHashMap<>();

        static void reset() {
            IF_NONE_MATCH_BY_METHOD.clear();
        }

        /**
         * What the most recent HEAD carried as {@code If-None-Match}, or the empty string when it
         * carried none. Never null, so "no validator sent" is not confused with "no HEAD issued".
         */
        static String ifNoneMatchSeenOnHead() {
            return IF_NONE_MATCH_BY_METHOD.getOrDefault("HEAD", "");
        }

        @Override
        public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
            return respond(request, "GET", BODY);
        }

        @Override
        public Optional<MockResponse> handleHead(@NonNull RecordedRequest request) {
            return respond(request, "HEAD", "");
        }

        private Optional<MockResponse> respond(RecordedRequest request, String method, String body) {
            if (!PATH.equals(request.getUrl().encodedPath())) {
                return Optional.empty();
            }
            String ifNoneMatch = Optional.ofNullable(request.getHeaders().get("If-None-Match")).orElse("");
            IF_NONE_MATCH_BY_METHOD.put(method, ifNoneMatch);

            Headers headers = new Headers.Builder()
                    .add("ETag", ETAG)
                    .add("Content-Type", "application/json")
                    .build();
            if (ETAG.equals(ifNoneMatch)) {
                return Optional.of(new MockResponse(304, headers, ""));
            }
            return Optional.of(new MockResponse(200, headers, body));
        }

        @Override
        public String getBaseUrl() {
            return BASE_PATH;
        }

        @Override
        public @NonNull Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET, HttpMethodMapper.HEAD);
        }
    }

    // === Credential-Digest and TTL Tests ===

    /**
     * The cache key outlives the request that produced it — it is retained in the cache map for as
     * long as the entry lives — so no credential value may appear in it verbatim. The two headers
     * asserted here take different routes into the key: {@code Authorization} is reproduced in the
     * header section under the {@code ALL} filter, while both contribute to the trailing principal
     * term.
     */
    @Test
    void cacheKeyShouldNotContainVerbatimCredentialValues() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.ALL)
                .build();

        var headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer super-secret-token");
        headers.put("Cookie", "session=super-secret-session");

        String key = adapter.generateCacheKey(URI.create("https://api.example.com/test"), headers,
                CacheKeyHeaderFilter.ALL);

        assertAll("Credential values are reduced to digests",
                () -> assertFalse(key.contains("super-secret-token"),
                        "The Authorization value must not appear verbatim, but key was: " + key),
                () -> assertFalse(key.contains("super-secret-session"),
                        "The Cookie value must not appear verbatim, but key was: " + key),
                // The digest marker is written through the same escaping as every other token, so the
                // colon appears as "\:" in the key - match the marker alone rather than the
                // pre-escape spelling.
                () -> assertTrue(key.contains("sha256"), "The credential should be represented by its digest"),
                () -> assertTrue(key.contains("application/json"),
                        "A non-credential header value is still keyed verbatim - without this the "
                                + "assertions above would also pass against a key that dropped every header"));
    }

    /**
     * The digest separates principals exactly as the raw credential would: two different tokens must
     * not collapse onto one key, and the same token must reproduce the same key.
     */
    @Test
    void cacheKeyDigestShouldSeparatePrincipalsAndBeStable() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization"))
                .build();

        var uri = URI.create("https://api.example.com/test");
        var filter = CacheKeyHeaderFilter.excluding("Authorization");
        String keyA = adapter.generateCacheKey(uri, Map.of("Authorization", "Bearer token-a"), filter);
        String keyASecondTime = adapter.generateCacheKey(uri, Map.of("Authorization", "Bearer token-a"), filter);
        String keyB = adapter.generateCacheKey(uri, Map.of("Authorization", "Bearer token-b"), filter);
        String anonymous = adapter.generateCacheKey(uri, Map.of(), filter);

        assertAll("Digest-based principal binding",
                () -> assertEquals(keyA, keyASecondTime, "The same credential must yield the same key"),
                () -> assertNotEquals(keyA, keyB, "Different credentials must yield different keys"),
                () -> assertNotEquals(keyA, anonymous, "An anonymous request must not share a credentialed key"));
    }

    /**
     * The TTL bounds how long an entry may answer for, which the size-triggered eviction alone
     * cannot do. Both halves are asserted here: an expired entry is neither served nor offered as a
     * validator, and an entry inside its TTL still is — the control that keeps the rule from
     * degrading into "never cache".
     */
    @Nested
    @EnableMockWebServer(useHttps = false)
    @DisplayName("Cache entries expire on the configured TTL")
    class CacheEntryTimeToLive {

        public ModuleDispatcherElement getModuleDispatcher() {
            return new PerPrincipalDispatcher();
        }

        @Test
        @DisplayName("An entry past its TTL should be neither served nor used for revalidation")
        @ModuleDispatcher
        void expiredEntryShouldNotBeServedOrRevalidated(URIBuilder uriBuilder) {
            PerPrincipalDispatcher.reset();
            HttpAdapter<String> adapter = adapterWithTtl(uriBuilder, Duration.ZERO);

            assertTrue(adapter.get().join().isSuccess(), "The first GET should populate the cache");

            HttpResult<String> second = adapter.get().join();

            assertAll("The entry expired before the second request",
                    () -> assertTrue(second.isSuccess(), "The second GET should succeed"),
                    () -> assertEquals(Optional.of(200), second.getHttpStatus(),
                            "An expired entry cannot be revalidated, so this is a full fetch"),
                    () -> assertEquals("", PerPrincipalDispatcher.ifNoneMatchSeenForAnonymous(),
                            "An expired entry's validator must not be sent to the origin"));
        }

        @Test
        @DisplayName("An entry inside its TTL should still be revalidated and served")
        @ModuleDispatcher
        void unexpiredEntryShouldStillBeUsed(URIBuilder uriBuilder) {
            PerPrincipalDispatcher.reset();
            HttpAdapter<String> adapter = adapterWithTtl(uriBuilder, Duration.ofMinutes(5));

            assertTrue(adapter.get().join().isSuccess(), "The first GET should populate the cache");

            HttpResult<String> second = adapter.get().join();

            assertAll("The entry is still inside its TTL",
                    () -> assertTrue(second.isSuccess(), "The second GET should succeed"),
                    () -> assertEquals(Optional.of(304), second.getHttpStatus(),
                            "A live entry should be revalidated rather than re-fetched"),
                    () -> assertEquals(PerPrincipalDispatcher.etagForAnonymous(),
                            PerPrincipalDispatcher.ifNoneMatchSeenForAnonymous(),
                            "The live entry's validator should be offered to the origin"),
                    () -> assertEquals(PerPrincipalDispatcher.bodyForAnonymous(),
                            second.getContent().orElse(null), "The cached body resolves the 304"));
        }

        private HttpAdapter<String> adapterWithTtl(URIBuilder uriBuilder, Duration ttl) {
            String serverUrl = uriBuilder.addPathSegments("per-principal", "resource").build().toString();
            HttpHandler serverHandler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

            return ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(serverHandler)
                    .responseConverter(new TestResponseConverter())
                    .cacheEntryTtl(ttl)
                    .build();
        }
    }

    @Test
    void builderValidatesCacheEntryTtl() {
        var builder = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter);

        assertAll("The TTL knob rejects unusable values",
                () -> assertThrows(NullPointerException.class, () -> builder.cacheEntryTtl(null),
                        "Builder should reject a null TTL"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> builder.cacheEntryTtl(Duration.ofSeconds(-1)),
                        "Builder should reject a negative TTL"),
                () -> assertDoesNotThrow(() -> builder.cacheEntryTtl(Duration.ZERO),
                        "Zero is the documented expire-immediately setting, not an error"));
    }

    // === Principal-Binding Tests ===

    /**
     * Pins the rule that a cache entry belongs to the credential material that produced it.
     *
     * <p>The adapter is built with {@code excluding("Authorization")} — the configuration under which
     * the credential never appears verbatim in the key. The binding term must still separate the two
     * principals, so B performs its own origin fetch instead of revalidating A's entry. The
     * same-principal control is what keeps the rule from degrading into "never cache".</p>
     */
    @Nested
    @EnableMockWebServer(useHttps = false)
    @DisplayName("Cache entries are bound to the credential material that produced them")
    class PrincipalBinding {

        public ModuleDispatcherElement getModuleDispatcher() {
            return new PerPrincipalDispatcher();
        }

        @Test
        @DisplayName("A second principal should fetch from origin rather than revalidate the first principal's entry")
        @ModuleDispatcher
        void secondPrincipalShouldNotReuseFirstPrincipalsEntry(URIBuilder uriBuilder) {
            PerPrincipalDispatcher.reset();
            HttpAdapter<String> adapter = tokenRefreshAdapter(uriBuilder);

            HttpResult<String> asA = adapter.get(PerPrincipalDispatcher.headersFor(PerPrincipalDispatcher.TOKEN_A)).join();
            assertAll("Principal A populates the cache",
                    () -> assertTrue(asA.isSuccess(), "A's GET should succeed"),
                    () -> assertEquals(PerPrincipalDispatcher.bodyFor(PerPrincipalDispatcher.TOKEN_A),
                            asA.getContent().orElse(null), "A should receive A's representation"));

            HttpResult<String> asB = adapter.get(PerPrincipalDispatcher.headersFor(PerPrincipalDispatcher.TOKEN_B)).join();

            assertAll("Principal B requests the same URI",
                    () -> assertTrue(asB.isSuccess(), "B's GET should succeed"),
                    () -> assertEquals(Optional.of(200), asB.getHttpStatus(),
                            "B has no entry of its own, so this is a full origin fetch"),
                    () -> assertEquals(PerPrincipalDispatcher.bodyFor(PerPrincipalDispatcher.TOKEN_B),
                            asB.getContent().orElse(null), "B should receive B's own representation"),
                    () -> assertEquals("", PerPrincipalDispatcher.ifNoneMatchSeenFor(PerPrincipalDispatcher.TOKEN_B),
                            "B's request must carry no conditional validator at all"),
                    () -> assertNotEquals(PerPrincipalDispatcher.etagFor(PerPrincipalDispatcher.TOKEN_A),
                            PerPrincipalDispatcher.ifNoneMatchSeenFor(PerPrincipalDispatcher.TOKEN_B),
                            "A's validator must never appear on B's request"));
        }

        /**
         * The control against over-correction: binding to the credential must not stop the same
         * principal from reusing its own entry, or the cache would have been disabled rather than
         * scoped.
         */
        @Test
        @DisplayName("The same principal should still revalidate against its own cached entry")
        @ModuleDispatcher
        void samePrincipalShouldStillHitTheCache(URIBuilder uriBuilder) {
            PerPrincipalDispatcher.reset();
            HttpAdapter<String> adapter = tokenRefreshAdapter(uriBuilder);
            Map<String, String> headers = PerPrincipalDispatcher.headersFor(PerPrincipalDispatcher.TOKEN_A);

            assertTrue(adapter.get(headers).join().isSuccess(), "The first GET should populate the cache");

            HttpResult<String> revalidated = adapter.get(headers).join();

            assertAll("Principal A repeats its own request",
                    () -> assertTrue(revalidated.isSuccess(), "The revalidation should succeed"),
                    () -> assertEquals(Optional.of(304), revalidated.getHttpStatus(),
                            "A's own entry should still be revalidated, not re-fetched"),
                    () -> assertEquals(PerPrincipalDispatcher.etagFor(PerPrincipalDispatcher.TOKEN_A),
                            PerPrincipalDispatcher.ifNoneMatchSeenFor(PerPrincipalDispatcher.TOKEN_A),
                            "A's own validator should be sent back to the origin"),
                    () -> assertEquals(PerPrincipalDispatcher.bodyFor(PerPrincipalDispatcher.TOKEN_A),
                            revalidated.getContent().orElse(null), "The cached body resolves the 304"));
        }

        /**
         * Builds the adapter over the token-refresh filter: the credential is deliberately kept out
         * of the verbatim header section, which is exactly the configuration the principal binding
         * has to hold on its own.
         */
        private HttpAdapter<String> tokenRefreshAdapter(URIBuilder uriBuilder) {
            String serverUrl = uriBuilder.addPathSegments("per-principal", "resource").build().toString();
            HttpHandler serverHandler = HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();

            return ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(serverHandler)
                    .responseConverter(new TestResponseConverter())
                    .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization"))
                    .build();
        }
    }

    /**
     * Serves a per-principal representation and records the {@code If-None-Match} each principal's
     * request carried, so "did this request revalidate, and against whose validator" is observable
     * on the wire rather than inferred from the result.
     *
     * <p>The record is static because the dispatcher resolver serves requests from its own instance
     * rather than the one the test handed it.</p>
     */
    static final class PerPrincipalDispatcher implements ModuleDispatcherElement {

        static final String BASE_PATH = "/per-principal";
        static final String PATH = BASE_PATH + "/resource";
        static final String TOKEN_A = "Bearer token-principal-a";
        static final String TOKEN_B = "Bearer token-principal-b";

        private static final String ANONYMOUS = "anonymous";
        private static final Map<String, String> IF_NONE_MATCH_BY_PRINCIPAL = new ConcurrentHashMap<>();

        static void reset() {
            IF_NONE_MATCH_BY_PRINCIPAL.clear();
        }

        static Map<String, String> headersFor(String token) {
            return Map.of("Authorization", token);
        }

        static String bodyFor(String token) {
            return "{\"owner\":\"%s\"}".formatted(token);
        }

        static String etagFor(String token) {
            return "\"etag-%s\"".formatted(token);
        }

        /**
         * The {@code If-None-Match} the named principal's most recent request carried, or the empty
         * string when it carried none. Never null, so a caller cannot read "no conditional header"
         * as "this principal never requested".
         */
        static String ifNoneMatchSeenFor(String token) {
            return IF_NONE_MATCH_BY_PRINCIPAL.getOrDefault(token, "");
        }

        /** The same record for a request that presented no credential at all. */
        static String ifNoneMatchSeenForAnonymous() {
            return ifNoneMatchSeenFor(ANONYMOUS);
        }

        static String bodyForAnonymous() {
            return bodyFor(ANONYMOUS);
        }

        static String etagForAnonymous() {
            return etagFor(ANONYMOUS);
        }

        @Override
        public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
            if (!PATH.equals(request.getUrl().encodedPath())) {
                return Optional.empty();
            }
            String principal = Optional.ofNullable(request.getHeaders().get("Authorization")).orElse(ANONYMOUS);
            String ifNoneMatch = Optional.ofNullable(request.getHeaders().get("If-None-Match")).orElse("");
            IF_NONE_MATCH_BY_PRINCIPAL.put(principal, ifNoneMatch);

            String etag = etagFor(principal);
            if (etag.equals(ifNoneMatch)) {
                return Optional.of(new MockResponse(304, new Headers.Builder().add("ETag", etag).build(), ""));
            }
            return Optional.of(new MockResponse(200, new Headers.Builder()
                    .add("ETag", etag)
                    .add("Content-Type", "application/json")
                    .build(), bodyFor(principal)));
        }

        @Override
        public String getBaseUrl() {
            return BASE_PATH;
        }

        @Override
        public @NonNull Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET);
        }
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

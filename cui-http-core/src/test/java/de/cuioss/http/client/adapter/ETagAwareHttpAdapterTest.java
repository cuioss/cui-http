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
import okio.ByteString;
import org.jspecify.annotations.Nullable;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ETagAwareHttpAdapter builder and core structure.
 */
class ETagAwareHttpAdapterTest {

    /**
     * URI used by the tests in this outer class. None of them issues a request: they exercise
     * builder validation and pure cache-key derivation, where the URI is input data rather than a
     * destination. Every test that actually sends a request lives in a nested class bound to
     * MockWebServer, so no test in this file contacts a real host.
     */
    private static final String NEVER_CONTACTED_URI = "https://api.example.com/test";

    private HttpHandler handler;
    private TestResponseConverter responseConverter;

    @BeforeEach
    void setUp() {
        handler = HttpHandler.builder()
                .uri(NEVER_CONTACTED_URI)
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

    /**
     * Each filter is asserted by what it does to the key, paired against the value it must NOT drop:
     * a filter that removed every header would otherwise satisfy each "does not contain" half on its
     * own.
     */
    @Test
    void cacheKeyFiltersShouldSelectExactlyTheHeadersTheyName() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();
        var uri = URI.create(NEVER_CONTACTED_URI);
        var headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("X-Custom", "custom-value");

        String all = adapter.generateCacheKey(uri, headers, CacheKeyHeaderFilter.ALL);
        String none = adapter.generateCacheKey(uri, headers, CacheKeyHeaderFilter.NONE);
        String including = adapter.generateCacheKey(uri, headers, CacheKeyHeaderFilter.including("Accept"));
        String excluding = adapter.generateCacheKey(uri, headers, CacheKeyHeaderFilter.excluding("Accept"));

        assertAll("Each filter keeps exactly the headers it names",
                () -> assertTrue(all.contains("application/json") && all.contains("custom-value"),
                        "ALL keeps both headers, but key was: " + all),
                () -> assertFalse(none.contains("application/json") || none.contains("custom-value"),
                        "NONE keeps neither header, but key was: " + none),
                () -> assertEquals(adapter.generateCacheKey(uri, Map.of(), CacheKeyHeaderFilter.NONE), none,
                        "NONE yields the same key regardless of which headers were sent"),
                () -> assertTrue(including.contains("application/json") && !including.contains("custom-value"),
                        "including(\"Accept\") keeps only Accept, but key was: " + including),
                () -> assertTrue(!excluding.contains("application/json") && excluding.contains("custom-value"),
                        "excluding(\"Accept\") drops only Accept, but key was: " + excluding));
    }

    /**
     * The key is derived from the header set, not from the order the caller happened to supply it in:
     * two maps carrying the same headers must resolve to one entry rather than two.
     */
    @Test
    void cacheKeyShouldBeIndependentOfHeaderOrder() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .build();
        var uri = URI.create(NEVER_CONTACTED_URI);

        var oneOrder = new LinkedHashMap<String, String>();
        oneOrder.put("Accept", "application/json");
        oneOrder.put("X-Custom", "custom-value");
        var otherOrder = new LinkedHashMap<String, String>();
        otherOrder.put("X-Custom", "custom-value");
        otherOrder.put("Accept", "application/json");

        assertEquals(adapter.generateCacheKey(uri, oneOrder, CacheKeyHeaderFilter.ALL),
                adapter.generateCacheKey(uri, otherOrder, CacheKeyHeaderFilter.ALL),
                "Header order must not fragment the cache");
    }

    // === Request Dispatch Tests (every request goes to MockWebServer) ===

    /**
     * Every test that actually issues a request lives here, bound to MockWebServer. The dispatcher
     * echoes the request method and the received body back, so each method's assertion can be exact —
     * the status, the body the server produced, and the validator it sent — rather than merely
     * "a future was returned".
     */
    @Nested
    @EnableMockWebServer(useHttps = false)
    @DisplayName("Requests dispatch against MockWebServer with exact results")
    class RequestDispatch {

        public ModuleDispatcherElement getModuleDispatcher() {
            return new EchoDispatcher();
        }

        @ParameterizedTest(name = "{0} reaches the origin and yields its response")
        @ValueSource(strings = {"GET", "HEAD", "OPTIONS", "DELETE"})
        @DisplayName("Body-less methods should yield the origin's status and body")
        @ModuleDispatcher
        void bodylessMethodShouldYieldOriginResponse(String method, URIBuilder uriBuilder) {
            HttpAdapter<String> adapter = adapterFor(uriBuilder, null);

            HttpResult<String> result = switch (method) {
                case "GET" -> adapter.get().join();
                case "HEAD" -> adapter.head().join();
                case "OPTIONS" -> adapter.options().join();
                default -> adapter.delete().join();
            };

            assertAll(method + " against the echo dispatcher",
                    () -> assertTrue(result.isSuccess(), method + " should succeed"),
                    () -> assertEquals(Optional.of(200), result.getHttpStatus(),
                            "The origin's status should be reported"),
                    () -> assertEquals(EchoDispatcher.ETAG, result.getETag().orElse(null),
                            "The origin's validator should be reported"),
                    // A HEAD response carries no body by protocol, so the echo never reaches the
                    // caller and the converter sees an empty payload.
                    () -> assertEquals("HEAD".equals(method) ? "" : EchoDispatcher.echoFor(method, ""),
                            result.getContent().orElse(null),
                            "The origin's body should be reported verbatim"));
        }

        @ParameterizedTest(name = "{0} sends its serialized body")
        @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
        @DisplayName("Body-carrying methods should send the converter's output to the origin")
        @ModuleDispatcher
        void bodyCarryingMethodShouldSendSerializedBody(String method, URIBuilder uriBuilder) {
            HttpAdapter<String> adapter = adapterFor(uriBuilder, new TestRequestConverter());

            HttpResult<String> result = switch (method) {
                case "POST" -> adapter.post("payload").join();
                case "PUT" -> adapter.put("payload").join();
                case "PATCH" -> adapter.patch("payload").join();
                default -> adapter.delete("payload").join();
            };

            assertAll(method + " with a body",
                    () -> assertTrue(result.isSuccess(), method + " should succeed"),
                    () -> assertEquals(Optional.of(200), result.getHttpStatus(),
                            "The origin's status should be reported"),
                    () -> assertEquals(EchoDispatcher.echoFor(method, "payload"), result.getContent().orElse(null),
                            "The origin should have received the serialized body"));
        }

        /**
         * The explicit-converter overloads serialize a body of a type unrelated to the response type.
         * Asserted through the echo so the converter's actual output is what reaches the origin.
         */
        @Test
        @DisplayName("An explicit converter should serialize a differently-typed body")
        @ModuleDispatcher
        void explicitConverterShouldSerializeDifferentlyTypedBody(URIBuilder uriBuilder) {
            HttpAdapter<String> adapter = adapterFor(uriBuilder, null);
            HttpRequestConverter<Integer> intConverter = new HttpRequestConverter<>() {
                @Override
                public HttpRequest.BodyPublisher toBodyPublisher(Integer content) {
                    return content == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(content.toString());
                }

                @Override
                public ContentType contentType() {
                    return ContentType.TEXT_PLAIN;
                }
            };

            HttpResult<String> result = adapter.post(intConverter, 42).join();

            assertEquals(EchoDispatcher.echoFor("POST", "42"), result.getContent().orElse(null),
                    "The Integer body should reach the origin as the converter serialized it");
        }

        /**
         * A caller-supplied header must reach the origin; the echo reports back what it saw, so the
         * assertion is on the received header rather than on the absence of an exception.
         */
        @Test
        @DisplayName("Caller-supplied headers should reach the origin")
        @ModuleDispatcher
        void callerHeadersShouldReachOrigin(URIBuilder uriBuilder) {
            HttpAdapter<String> adapter = adapterFor(uriBuilder, null);

            adapter.get(Map.of(EchoDispatcher.ECHOED_HEADER, "custom-value")).join();

            assertEquals("custom-value", EchoDispatcher.lastEchoedHeader(),
                    "The caller's header should have been sent to the origin");
        }

        /**
         * {@code statusCodeOnly} builds a Void adapter: it reports the status and holds no content,
         * which is exactly what distinguishes it from the String adapters above.
         */
        @Test
        @DisplayName("statusCodeOnly should report the status and carry no content")
        @ModuleDispatcher
        void statusCodeOnlyShouldReportStatusWithoutContent(URIBuilder uriBuilder) {
            HttpAdapter<Void> adapter = ETagAwareHttpAdapter.statusCodeOnly(handlerFor(uriBuilder));

            HttpResult<Void> result = adapter.get().join();

            assertAll("statusCodeOnly GET",
                    () -> assertTrue(result.isSuccess(), "The request should succeed"),
                    () -> assertEquals(Optional.of(200), result.getHttpStatus(), "The status should be reported"),
                    () -> assertTrue(result.getContent().isEmpty(), "A Void adapter holds no content"));
        }

        /**
         * {@code clearETagCache} must actually drop the entry: after clearing, the next GET carries no
         * validator, which is the observable the bare non-throwing assertion could not distinguish.
         */
        @Test
        @DisplayName("clearETagCache should drop the stored validator")
        @ModuleDispatcher
        void clearETagCacheShouldDropTheStoredValidator(URIBuilder uriBuilder) {
            EchoDispatcher.reset();
            var adapter = ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(handlerFor(uriBuilder))
                    .responseConverter(new TestResponseConverter())
                    .build();
            adapter.get().join();

            adapter.clearETagCache();
            adapter.get().join();

            assertEquals("", EchoDispatcher.lastIfNoneMatch(),
                    "After clearing, the next GET must not revalidate against the dropped entry");
        }

        /**
         * Control for the test above: without the clear, the second GET does revalidate — so the
         * empty validator asserted there is attributable to the clear, not to the cache never storing
         * anything.
         */
        @Test
        @DisplayName("Without clearing, the second GET revalidates against the stored validator")
        @ModuleDispatcher
        void secondGetShouldRevalidateWhenCacheIsNotCleared(URIBuilder uriBuilder) {
            EchoDispatcher.reset();
            var adapter = ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(handlerFor(uriBuilder))
                    .responseConverter(new TestResponseConverter())
                    .build();
            adapter.get().join();

            adapter.get().join();

            assertEquals(EchoDispatcher.ETAG, EchoDispatcher.lastIfNoneMatch(),
                    "The stored validator should accompany the second GET");
        }

        // === Cache Key Allocation Skip Tests ===

        /**
         * Positive control for the allocation skip: GET reads and populates the cache, so it must
         * build a key. Without this the negative cases below would also pass against an adapter that
         * never generated a key at all.
         */
        @Test
        @DisplayName("GET should build a cache key")
        @ModuleDispatcher
        void getShouldGenerateCacheKey(URIBuilder uriBuilder) {
            var filter = new CountingCacheKeyHeaderFilter();

            cachingAdapterWith(uriBuilder, filter).get(Map.of("Accept", "application/json")).join();

            assertTrue(filter.consultations() > 0, "GET reads the cache and must build a key");
        }

        /**
         * HEAD reads the cache to resolve a conditional 304 against the stored validator, so it must
         * build a key too. This is the guard against narrowing the lookup to GET alone, which would
         * silently delete the documented HEAD 304 behaviour.
         */
        @Test
        @DisplayName("HEAD should build a cache key")
        @ModuleDispatcher
        void headShouldGenerateCacheKey(URIBuilder uriBuilder) {
            var filter = new CountingCacheKeyHeaderFilter();

            cachingAdapterWith(uriBuilder, filter).head(Map.of("Accept", "application/json")).join();

            assertTrue(filter.consultations() > 0, "HEAD reads the cache to resolve a 304 and must build a key");
        }

        @Test
        @DisplayName("Methods that never touch the cache should build no key")
        @ModuleDispatcher
        void nonCacheableMethodsShouldNotGenerateCacheKey(URIBuilder uriBuilder) {
            var headers = Map.of("Accept", "application/json");
            var postFilter = new CountingCacheKeyHeaderFilter();
            var putFilter = new CountingCacheKeyHeaderFilter();
            var patchFilter = new CountingCacheKeyHeaderFilter();
            var deleteFilter = new CountingCacheKeyHeaderFilter();

            cachingAdapterWith(uriBuilder, postFilter).post((String) null, headers).join();
            cachingAdapterWith(uriBuilder, putFilter).put((String) null, headers).join();
            cachingAdapterWith(uriBuilder, patchFilter).patch((String) null, headers).join();
            cachingAdapterWith(uriBuilder, deleteFilter).delete(headers).join();

            assertAll("Methods that never touch the cache should not build a key",
                    () -> assertEquals(0, postFilter.consultations(), "POST should not build a cache key"),
                    () -> assertEquals(0, putFilter.consultations(), "PUT should not build a cache key"),
                    () -> assertEquals(0, patchFilter.consultations(), "PATCH should not build a cache key"),
                    () -> assertEquals(0, deleteFilter.consultations(), "DELETE should not build a cache key"));
        }

        @Test
        @DisplayName("A caching-disabled adapter should build no key")
        @ModuleDispatcher
        void cachingDisabledAdapterShouldNotGenerateCacheKey(URIBuilder uriBuilder) {
            var filter = new CountingCacheKeyHeaderFilter();
            var adapter = ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(handlerFor(uriBuilder))
                    .responseConverter(new TestResponseConverter())
                    .cacheKeyHeaderFilter(filter)
                    .etagCachingEnabled(false)
                    .build();

            adapter.get(Map.of("Accept", "application/json")).join();

            assertEquals(0, filter.consultations(),
                    "A caching-disabled adapter never reads the key, so it should not build one");
        }

        /**
         * Once the request-converter guard has passed, the configured converter is the one the
         * request is built from — it serializes the body <em>and</em> supplies the Content-Type.
         */
        @Test
        @DisplayName("A body with a configured converter should reach that converter")
        @ModuleDispatcher
        void bodyWithRequestConverterShouldReachConverter(URIBuilder uriBuilder) {
            var requestConverter = new RecordingRequestConverter();
            HttpAdapter<String> adapter = adapterFor(uriBuilder, requestConverter);

            adapter.post("payload").join();

            assertAll("A body with a configured converter reaches that converter",
                    () -> assertEquals("payload", requestConverter.lastBody(),
                            "The body should be handed to the converter for serialization"),
                    () -> assertEquals(1, requestConverter.contentTypeCalls(),
                            "The request Content-Type should be resolved from the converter"));
        }

        /**
         * Negative control for the test above: with no body there is nothing to serialize, so the
         * converter is never consulted and no Content-Type is derived from it.
         */
        @Test
        @DisplayName("A null body should not resolve a Content-Type from the converter")
        @ModuleDispatcher
        void nullBodyShouldNotResolveContentTypeFromRequestConverter(URIBuilder uriBuilder) {
            var requestConverter = new RecordingRequestConverter();
            HttpAdapter<String> adapter = adapterFor(uriBuilder, requestConverter);

            adapter.post((String) null).join();

            assertAll("A null body never reaches the converter",
                    () -> assertNull(requestConverter.lastBody(),
                            "A null body should not be handed to the converter"),
                    () -> assertEquals(0, requestConverter.contentTypeCalls(),
                            "No Content-Type should be resolved when there is no body"));
        }

        private ETagAwareHttpAdapter<String> cachingAdapterWith(URIBuilder uriBuilder, CacheKeyHeaderFilter filter) {
            return ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(handlerFor(uriBuilder))
                    .responseConverter(new TestResponseConverter())
                    .cacheKeyHeaderFilter(filter)
                    .build();
        }

        private HttpAdapter<String> adapterFor(URIBuilder uriBuilder,
                @Nullable HttpRequestConverter<String> requestConverter) {
            return ETagAwareHttpAdapter.<String>builder()
                    .httpHandler(handlerFor(uriBuilder))
                    .responseConverter(new TestResponseConverter())
                    .requestConverter(requestConverter)
                    .build();
        }

        private HttpHandler handlerFor(URIBuilder uriBuilder) {
            String serverUrl = uriBuilder.addPathSegments("echo", "resource").build().toString();
            return HttpHandler.builder().url(serverUrl).allowInsecureHttp(true).build();
        }
    }

    /**
     * Echoes the request method and the body it received, and records the caller-supplied header and
     * the {@code If-None-Match} each request carried. Echoing is what lets a dispatch assertion be
     * exact: the response body names which method the origin actually saw and what it sent.
     *
     * <p>The records are static because the dispatcher resolver serves requests from its own instance
     * rather than the one the test handed it — the same caveat the other dispatchers in this file
     * document.</p>
     */
    static final class EchoDispatcher implements ModuleDispatcherElement {

        static final String BASE_PATH = "/echo";
        static final String PATH = BASE_PATH + "/resource";
        static final String ETAG = "\"etag-echo\"";
        static final String ECHOED_HEADER = "X-Echo-Me";

        private static final Map<String, String> RECORDS = new ConcurrentHashMap<>();

        static void reset() {
            RECORDS.clear();
        }

        static String echoFor(String method, String body) {
            return "%s:%s".formatted(method, body);
        }

        /** The caller header the most recent request carried, or the empty string when it carried none. */
        static String lastEchoedHeader() {
            return RECORDS.getOrDefault("header", "");
        }

        /** The {@code If-None-Match} the most recent request carried, or the empty string when none. */
        static String lastIfNoneMatch() {
            return RECORDS.getOrDefault("if-none-match", "");
        }

        @Override
        public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
            return respond(request, "GET");
        }

        @Override
        public Optional<MockResponse> handleHead(@NonNull RecordedRequest request) {
            return respond(request, "HEAD");
        }

        @Override
        public Optional<MockResponse> handleOptions(@NonNull RecordedRequest request) {
            return respond(request, "OPTIONS");
        }

        @Override
        public Optional<MockResponse> handlePost(@NonNull RecordedRequest request) {
            return respond(request, "POST");
        }

        @Override
        public Optional<MockResponse> handlePut(@NonNull RecordedRequest request) {
            return respond(request, "PUT");
        }

        @Override
        public Optional<MockResponse> handlePatch(@NonNull RecordedRequest request) {
            return respond(request, "PATCH");
        }

        @Override
        public Optional<MockResponse> handleDelete(@NonNull RecordedRequest request) {
            return respond(request, "DELETE");
        }

        private Optional<MockResponse> respond(RecordedRequest request, String method) {
            if (!PATH.equals(request.getUrl().encodedPath())) {
                return Optional.empty();
            }
            RECORDS.put("header", Optional.ofNullable(request.getHeaders().get(ECHOED_HEADER)).orElse(""));
            RECORDS.put("if-none-match", Optional.ofNullable(request.getHeaders().get("If-None-Match")).orElse(""));

            // ByteString.toString() renders a debug form ("[text=42]"), so decode explicitly.
            String body = Optional.ofNullable(request.getBody()).map(ByteString::utf8).orElse("");
            Headers headers = new Headers.Builder()
                    .add("ETag", ETAG)
                    .add("Content-Type", "text/plain")
                    .build();
            return Optional.of(new MockResponse(200, headers, echoFor(method, body)));
        }

        @Override
        public String getBaseUrl() {
            return BASE_PATH;
        }

        @Override
        public @NonNull Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET, HttpMethodMapper.HEAD, HttpMethodMapper.OPTIONS,
                    HttpMethodMapper.POST, HttpMethodMapper.PUT, HttpMethodMapper.PATCH,
                    HttpMethodMapper.DELETE);
        }
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
     * The principal-binding term is derived by joining multiple credential headers with {@code &}
     * and {@code =} before digesting, exactly the delimiter shape {@code generateCacheKey}'s own
     * header section escapes for. A credential header value that itself contains {@code &name=value}
     * can therefore forge a second header: a caller sending only {@code Authorization: token&cookie=x}
     * must not bind to the same principal as one sending {@code Authorization: token} and
     * {@code Cookie: x} as two separate headers - two different credential presentations, and the
     * whole point of principal binding is that they must not share a cache entry.
     */
    @Test
    void principalBindingShouldNotCollideAcrossForgedDelimiters() {
        var adapter = ETagAwareHttpAdapter.<String>builder()
                .httpHandler(handler)
                .responseConverter(responseConverter)
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.excluding("Authorization", "Cookie"))
                .build();

        var uri = URI.create("https://api.example.com/test");
        var filter = CacheKeyHeaderFilter.excluding("Authorization", "Cookie");

        var forgedHeaders = new LinkedHashMap<String, String>();
        forgedHeaders.put("Authorization", "token&cookie=x");
        String forgedKey = adapter.generateCacheKey(uri, forgedHeaders, filter);

        var separateHeaders = new LinkedHashMap<String, String>();
        separateHeaders.put("Authorization", "token");
        separateHeaders.put("Cookie", "x");
        String separateKey = adapter.generateCacheKey(uri, separateHeaders, filter);

        assertNotEquals(forgedKey, separateKey,
                "A single Authorization header carrying '&cookie=x' must not bind to the same "
                        + "principal as separate Authorization/Cookie headers, but both keyed as: "
                        + forgedKey);
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

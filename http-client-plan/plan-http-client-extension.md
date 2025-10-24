# HTTP Client Extension: Complete Implementation Plan

**Issue Reference:** /Users/oliver/git/cui-http/http-client-plan/

---

## Instructions for Implementation Agent

**CRITICAL:** Implement tasks **ONE AT A TIME** in the order listed below.

After implementing each task:
1. ✅ Verify all acceptance criteria are met
2. ✅ Run all quality checks (tests, build, formatting)
3. ✅ Mark the task as done: `[x]`
4. ✅ Only proceed to next task when current task is 100% complete

**Do NOT skip ahead.** Each task builds on previous tasks.

---

## Overview

This implementation replaces the existing `ResilientHttpHandler` with a composable, async-first HTTP adapter architecture that supports all HTTP methods (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS), ETag caching, and retry logic with exponential backoff.

**Breaking Change:** This is a pre-1.0 project. The old `ResilientHttpHandler` implementation will be completely removed and replaced with the new architecture.

**Key Features:**
- Async-first design using `CompletableFuture`
- Method-specific API (`adapter.get()`, `adapter.post()`, etc.)
- Built-in ETag caching with 304 Not Modified handling
- Non-blocking retry with exponential backoff
- Separate request/response converters
- Fine-grained cache key control via `CacheKeyHeaderFilter`

---

## Tasks

### Task 1: Create ContentType Enum

**Goal:** Implement type-safe MIME type representation with charset support

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 630-720
- Package: `de.cuioss.http.client`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `ContentType` enum in `src/main/java/de/cuioss/http/client/ContentType.java`
- [x] Implement enum values: APPLICATION_JSON, APPLICATION_XML, TEXT_PLAIN, TEXT_HTML, TEXT_XML, TEXT_CSV, APPLICATION_FORM_URLENCODED, MULTIPART_FORM_DATA, APPLICATION_OCTET_STREAM, APPLICATION_PDF, APPLICATION_ZIP, IMAGE_PNG, IMAGE_JPEG, IMAGE_GIF, IMAGE_SVG
- [x] Add fields: `String mediaType`, `Charset defaultCharset`
- [x] Implement methods: `mediaType()`, `defaultCharset()`, `toHeaderValue()`
- [x] Add comprehensive Javadoc with examples
- [x] Implement unit tests in `ContentTypeTest` (minimum 80% coverage)
- [x] Test `toHeaderValue()` includes charset for text types
- [x] Test binary types have no charset
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- ContentType enum exists with all specified MIME types
- Each enum value has correct mediaType and defaultCharset
- toHeaderValue() returns "mediaType; charset=X" for text types
- toHeaderValue() returns "mediaType" only for binary types
- Test coverage ≥ 80%
- JavaDoc present on all public methods

---

### Task 2: Create HttpMethod Enum

**Goal:** Implement HTTP method classification with safe/idempotent properties

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 326-476
- Package: `de.cuioss.http.client`
- RFC 7231: HTTP method semantics

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `HttpMethod` enum in `src/main/java/de/cuioss/http/client/HttpMethod.java`
- [x] Implement enum values: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
- [x] Add fields: `boolean safe`, `boolean idempotent`, `String name`
- [x] Implement methods: `isSafe()`, `isIdempotent()`, `methodName()`
- [x] Set correct properties (GET: safe+idempotent, POST: unsafe+non-idempotent, PUT: unsafe+idempotent, etc.)
- [x] Add comprehensive Javadoc explaining safe vs idempotent
- [x] Mark enum as public (for logging/debugging visibility)
- [x] Implement unit tests in `HttpMethodTest` (minimum 80% coverage)
- [x] Test all methods have correct safe/idempotent properties
- [x] Test methodName() returns correct string
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- HttpMethod enum exists with all HTTP methods
- Each method has correct safe/idempotent classification per RFC 7231
- methodName() returns uppercase string (e.g., "GET", "POST")
- Test coverage ≥ 80%
- JavaDoc documents safe vs idempotent with examples

---

### Task 3: Create HttpResponseConverter Interface

**Goal:** Create interface for HTTP response to typed object conversion

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 1236-1291
- Package: `de.cuioss.http.client.converter`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `HttpResponseConverter<T>` interface in `src/main/java/de/cuioss/http/client/converter/HttpResponseConverter.java`
- [x] Add method: `Optional<T> convert(Object rawContent)`
- [x] Add method: `HttpResponse.BodyHandler<?> getBodyHandler()`
- [x] Add method: `ContentType contentType()`
- [x] Document error handling contract: return Optional.empty() on failure, never throw
- [x] Add comprehensive Javadoc with usage examples
- [x] Create unit tests in `HttpResponseConverterTest`
- [x] Test convert() returns Optional.empty() on parsing failure
- [x] Test getBodyHandler() returns correct handler
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- HttpResponseConverter interface exists with three methods
- JavaDoc documents error handling contract (Optional.empty() on failure)
- Interface is generic with type parameter T
- Test coverage ≥ 80%
- Examples show JSON/XML conversion patterns

---

### Task 4: Create HttpRequestConverter Interface

**Goal:** Create interface for typed object to HTTP request body conversion

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 1293-1343
- Package: `de.cuioss.http.client.converter`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `HttpRequestConverter<R>` interface in `src/main/java/de/cuioss/http/client/converter/HttpRequestConverter.java`
- [x] Add method: `HttpRequest.BodyPublisher toBodyPublisher(@Nullable R content)`
- [x] Add method: `ContentType contentType()`
- [x] Document null handling contract: return noBody() for null, throw IllegalArgumentException on serialization failure
- [x] Add comprehensive Javadoc with usage examples
- [x] Create unit tests in `HttpRequestConverterTest`
- [x] Test toBodyPublisher() returns noBody() for null input
- [x] Test toBodyPublisher() throws IllegalArgumentException on serialization failure
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- HttpRequestConverter interface exists with two methods
- JavaDoc documents null handling (noBody() for null)
- JavaDoc documents error handling (throw on serialization failure)
- Interface is generic with type parameter R
- Test coverage ≥ 80%

---

### Task 5: Create VoidResponseConverter

**Goal:** Implement built-in converter for status-code-only responses

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 1527-1594
- Package: `de.cuioss.http.client.converter`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `VoidResponseConverter` class in `src/main/java/de/cuioss/http/client/converter/VoidResponseConverter.java`
- [x] Implement `HttpResponseConverter<Void>` interface
- [x] Add singleton INSTANCE field
- [x] Implement convert() to return Optional.empty() always
- [x] Implement getBodyHandler() to return BodyHandlers.discarding()
- [x] Implement contentType() to return APPLICATION_JSON (doesn't matter, body discarded)
- [x] Make constructor private (use INSTANCE)
- [x] Add comprehensive Javadoc with DELETE/HEAD use cases
- [x] Create unit tests in `VoidResponseConverterTest`
- [x] Test convert() always returns Optional.empty()
- [x] Test getBodyHandler() returns discarding handler
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- VoidResponseConverter exists as singleton
- convert() always returns Optional.empty()
- getBodyHandler() uses efficient BodyHandlers.discarding()
- Test coverage ≥ 80%
- JavaDoc documents DELETE/HEAD use cases

---

### Task 6: Create CacheKeyHeaderFilter Interface

**Goal:** Implement functional interface for fine-grained cache key header control

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 12-325
- Package: `de.cuioss.http.client.adapter`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `CacheKeyHeaderFilter` interface in `src/main/java/de/cuioss/http/client/adapter/CacheKeyHeaderFilter.java`
- [x] Mark as @FunctionalInterface
- [x] Add method: `boolean includeInCacheKey(String headerName)`
- [x] Add constant: `CacheKeyHeaderFilter ALL = header -> true`
- [x] Add constant: `CacheKeyHeaderFilter NONE = header -> false`
- [x] Add static method: `excluding(String... headerNames)`
- [x] Add static method: `including(String... headerNames)`
- [x] Add static method: `excludingPrefix(String prefix)`
- [x] Add static method: `matching(Predicate<String> predicate)`
- [x] Add default method: `and(CacheKeyHeaderFilter other)`
- [x] Add default method: `or(CacheKeyHeaderFilter other)`
- [x] Add default method: `negate()`
- [x] Add comprehensive Javadoc with token refresh cache bloat examples
- [x] Create unit tests in `CacheKeyHeaderFilterTest` (minimum 80% coverage)
- [x] Test ALL includes all headers
- [x] Test NONE excludes all headers
- [x] Test excluding() creates blacklist filter
- [x] Test including() creates whitelist filter
- [x] Test excludingPrefix() filters by prefix
- [x] Test composition (and, or, negate)
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- CacheKeyHeaderFilter interface exists with all factory methods
- ALL and NONE presets work correctly
- excluding() solves token refresh cache bloat problem
- Composition methods (and/or/negate) work correctly
- Case-insensitive header name matching
- Test coverage ≥ 80%
- JavaDoc documents token refresh scenario with examples

---

### Task 7: Create HttpAdapter Interface

**Goal:** Implement method-specific adapter interface with async-first design

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 722-1174
- Package: `de.cuioss.http.client.adapter`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `HttpAdapter<T>` interface in `src/main/java/de/cuioss/http/client/adapter/HttpAdapter.java`
- [x] Add async methods: `get()`, `post()`, `put()`, `patch()`, `delete()`, `head()`, `options()`
- [x] All async methods return `CompletableFuture<HttpResult<T>>`
- [x] Add overloads for: no headers, with headers Map
- [x] Add body methods with same type T (requires configured request converter)
- [x] Add body methods with different type R (explicit converter parameter)
- [x] Add blocking convenience methods: `getBlocking()`, `postBlocking()`, etc.
- [x] Blocking methods use default implementation calling `asyncMethod().join()`
- [x] Add comprehensive Javadoc explaining async-first philosophy
- [x] Document why async methods don't have "Async" suffix
- [x] Create basic interface tests in `HttpAdapterTest`
- [x] Test blocking methods delegate to async methods
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- HttpAdapter interface exists with all HTTP methods
- All primary methods return CompletableFuture (async-first)
- Blocking convenience methods use `.join()`
- Overloads for headers Map and no-headers cases
- Generic body methods support different request/response types
- Test coverage ≥ 80%
- JavaDoc explains async-first design rationale

---

### Task 8: Implement ETagAwareHttpAdapter (Part 1: Core Structure)

**Goal:** Create base adapter implementation with builder and core fields

**References:**
- Specification: `http-client-plan/04-etag-aware-adapter.adoc` lines 1-668
- Current architecture: `http-client-plan/01-current-architecture.adoc`
- Package: `de.cuioss.http.client.adapter`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `ETagAwareHttpAdapter<T>` class implementing `HttpAdapter<T>`
- [x] Add final fields: `HttpHandler httpHandler`, `HttpClient httpClient`, `HttpResponseConverter<T> responseConverter`, `HttpRequestConverter<T> requestConverter`, `boolean etagCachingEnabled`, `CacheKeyHeaderFilter cacheKeyHeaderFilter`, `int maxCacheSize`
- [x] Create CacheEntry record: `record CacheEntry<T>(T content, String etag, long timestamp)`
- [x] Add cache field: `ConcurrentHashMap<String, CacheEntry<T>> cache`
- [x] Create HttpClient ONCE in constructor (store as final field for thread-safe reuse)
- [x] Create Builder class with fields matching adapter parameters
- [x] Implement builder methods with validation
- [x] Set builder defaults: etagCachingEnabled=true, cacheKeyHeaderFilter=ALL, maxCacheSize=1000
- [x] Implement build() method that constructs adapter
- [x] Add static factory: `statusCodeOnly(HttpHandler)` using VoidResponseConverter
- [x] Add method: `clearETagCache()`
- [x] Add comprehensive class-level Javadoc with examples
- [x] Create test class `ETagAwareHttpAdapterTest`
- [x] Test builder validation (null handler, null converter)
- [x] Test default builder values
- [x] Test statusCodeOnly() factory method
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- ETagAwareHttpAdapter class exists with all fields
- HttpClient created once in constructor (not per request)
- Builder pattern with sensible defaults
- CacheEntry record with timestamp
- ConcurrentHashMap for cache
- statusCodeOnly() factory returns HttpAdapter<Void>
- Test coverage ≥ 80% for builder and structure
- JavaDoc documents ETag caching behavior

---

### Task 9: Implement ETagAwareHttpAdapter (Part 2: Request Execution)

**Goal:** Implement core request execution with async CompletableFuture

**References:**
- Specification: `http-client-plan/04-etag-aware-adapter.adoc` lines 10-125
- Async architecture: `http-client-plan/02-proposed-architecture.adoc`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Implement private method: `send(HttpMethod method, @Nullable T body, Map<String, String> headers)`
- [x] Return type: `CompletableFuture<HttpResult<T>>`
- [x] Validate safe methods (GET/HEAD/OPTIONS) don't have bodies
- [x] Generate cache key using `generateCacheKey()` method
- [x] Retrieve cache entry BEFORE building request (hold local reference)
- [x] Build HttpRequest using `httpHandler.requestBuilder()`
- [x] Set HTTP method using `method.methodName()`
- [x] Add request body using `buildBodyPublisher()`
- [x] Add custom headers from Map
- [x] Add If-None-Match header if cache entry exists and method is GET
- [x] Execute async using `httpClient.sendAsync(request, responseConverter.getBodyHandler())`
- [x] Return CompletableFuture (no blocking!)
- [x] Implement helper: `generateCacheKey(URI uri, Map<String, String> headers, CacheKeyHeaderFilter filter)`
- [x] Sort headers alphabetically in cache key
- [x] Apply filter predicate to each header
- [x] Implement helper: `buildBodyPublisher(@Nullable T body)`
- [x] Return noBody() if body is null OR requestConverter is null
- [x] Call requestConverter.toBodyPublisher() if body present
- [x] Add unit tests for send() method structure
- [x] Test cache key generation with filters
- [x] Test If-None-Match only added for GET with cache entry
- [x] Test body publisher creation
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- send() method returns CompletableFuture (async)
- Cache entry retrieved before request (local reference held)
- If-None-Match added only for GET with cached entry
- Safe methods validated (no body allowed)
- Cache key generation uses filter predicate
- Headers sorted alphabetically in cache key
- Test coverage ≥ 80% for request execution

---

### Task 10: Implement ETagAwareHttpAdapter (Part 3: Response Handling and 304)

**Goal:** Implement async response handling with 304 Not Modified support

**References:**
- Specification: `http-client-plan/04-etag-aware-adapter.adoc` lines 10-152
- 304 handling pattern: lines 10-60

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] In send() method, add `.thenApply()` to CompletableFuture chain
- [x] Extract HTTP status code from response
- [x] Handle 304 Not Modified: check if statusCode == 304 AND cachedEntry != null
- [x] For 304: return `HttpResult.success(cachedEntry.content(), cachedEntry.etag(), 304)`
- [x] Extract ETag from response headers (all methods, not just GET)
- [x] Convert response body using `responseConverter.convert(response.body())`
- [x] Handle conversion failure: return Failure with INVALID_CONTENT
- [x] Cache successful GET responses: if method is GET AND statusCode is 200 AND etag present AND content present
- [x] Create CacheEntry with content, etag, timestamp
- [x] Call `putInCache(cacheKey, entry)`
- [x] For success: return `HttpResult.success(content.orElse(null), etag, statusCode)`
- [x] Add `.exceptionally()` to handle exceptions
- [x] Classify exceptions: IOException → NETWORK_ERROR, others → CONFIGURATION_ERROR
- [x] Return Failure with appropriate error category
- [x] Implement helper: `putInCache(String key, CacheEntry<T> entry)`
- [x] Add entry to cache
- [x] Call `checkAndEvict()`
- [x] Implement helper: `checkAndEvict()`
- [x] If cache.size() > maxCacheSize: remove oldest 10% by timestamp
- [x] Use weakly-consistent iterator (safe for concurrent modification)
- [x] Add unit tests for 304 handling
- [x] Test 304 returns Success with cached content
- [x] Test 304 preserves status code (not converted to 200)
- [x] Test ETag extraction from all HTTP methods
- [x] Test cache update on successful GET with ETag
- [x] Test cache eviction when size exceeded
- [x] Test exception handling (IOException, parsing failure)
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- 304 handling returns Success with cached content (structural guarantee)
- 304 status code preserved (not converted to 200)
- ETag extracted from all responses (GET, POST, PUT, DELETE)
- Only GET responses cached (with 200 status and ETag)
- Cache eviction removes oldest 10% when size > maxCacheSize
- Exceptions categorized correctly (NETWORK_ERROR, CONFIGURATION_ERROR)
- Test coverage ≥ 80% for response handling
- 100% coverage for 304 handling critical path

---

### Task 11: Implement ETagAwareHttpAdapter (Part 4: Method Implementations)

**Goal:** Implement all HttpAdapter interface methods delegating to send()

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 722-973
- Interface definition: HttpAdapter

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Implement `get(Map<String, String> headers)`: delegate to send(GET, null, headers)
- [x] Implement `post(@Nullable T body, Map<String, String> headers)`: delegate to send(POST, body, headers)
- [x] Implement `put(@Nullable T body, Map<String, String> headers)`: delegate to send(PUT, body, headers)
- [x] Implement `patch(@Nullable T body, Map<String, String> headers)`: delegate to send(PATCH, body, headers)
- [x] Implement `delete(Map<String, String> headers)`: delegate to send(DELETE, null, headers)
- [x] Implement `delete(@Nullable T body, Map<String, String> headers)`: delegate to send(DELETE, body, headers)
- [x] Implement `head(Map<String, String> headers)`: delegate to send(HEAD, null, headers)
- [x] Implement `options(Map<String, String> headers)`: delegate to send(OPTIONS, null, headers)
- [x] Implement generic body methods: `<R> post(HttpRequestConverter<R> converter, R body, Map headers)`
- [x] For generic methods: temporarily use converter, delegate to send(), restore original converter
- [x] Add unit tests for all HTTP method implementations
- [x] Test GET caches responses
- [x] Test POST/PUT/DELETE extract ETag but don't cache
- [x] Test HEAD doesn't cache
- [x] Test generic body methods with different types
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- All HttpAdapter methods implemented
- All methods return CompletableFuture (async)
- Only GET caches responses
- All methods extract ETag from response
- Generic body methods support different request/response types
- Test coverage ≥ 80% for all methods

---

### Task 12: Create RetryConfig Record

**Goal:** Implement configuration record for retry behavior with builder

**References:**
- Specification: `http-client-plan/05-resilient-adapter.adoc` lines 40-266
- Package: `de.cuioss.http.client.adapter`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `RetryConfig` record in `src/main/java/de/cuioss/http/client/adapter/RetryConfig.java`
- [x] Add record components: `int maxAttempts`, `Duration initialDelay`, `double multiplier`, `Duration maxDelay`, `double jitter`, `boolean idempotentOnly`
- [x] Create nested Builder class
- [x] Set builder defaults: maxAttempts=5, initialDelay=1s, multiplier=2.0, maxDelay=1min, jitter=0.1, idempotentOnly=true
- [x] Implement builder validation in setter methods
- [x] Validate maxAttempts >= 1
- [x] Validate initialDelay positive
- [x] Validate multiplier >= 1.0
- [x] Validate maxDelay positive
- [x] Validate jitter between 0.0 and 1.0
- [x] Add static factory: `defaults()` returning default configuration
- [x] Add static factory: `builder()` returning new Builder
- [x] Add method: `calculateDelay(int attemptNumber)` with exponential backoff formula
- [x] Use ThreadLocalRandom for jitter (thread-safe)
- [x] Apply jitter: delay * (1 ± jitter)
- [x] Cap delay at maxDelay
- [x] Add comprehensive Javadoc explaining defaults and rationale
- [x] Add @SuppressWarnings("java:S2245") for random in jitter
- [x] Create unit tests in `RetryConfigTest` (minimum 80% coverage)
- [x] Test builder validation (negative values, out of range)
- [x] Test default values
- [x] Test calculateDelay() exponential backoff
- [x] Test jitter randomization
- [x] Test maxDelay cap enforced
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- RetryConfig record exists with all fields
- Builder pattern with validation
- Sensible defaults: 5 attempts, 1s initial, 2.0 multiplier, 1min max, 10% jitter, idempotentOnly=true
- calculateDelay() implements exponential backoff with jitter
- Thread-safe delay calculation (ThreadLocalRandom)
- Test coverage ≥ 80%
- JavaDoc explains why these defaults (industry best practices)

---

### Task 13: Implement ResilientHttpAdapter

**Goal:** Create async retry decorator wrapping any HttpAdapter

**References:**
- Specification: `http-client-plan/05-resilient-adapter.adoc` lines 542-723
- Idempotency: lines 267-540
- Package: `de.cuioss.http.client.adapter`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create `ResilientHttpAdapter<T>` class implementing `HttpAdapter<T>`
- [x] Add final fields: `HttpAdapter<T> delegate`, `RetryConfig config`
- [x] Create constructor taking delegate and config
- [x] Add static factory: `wrap(HttpAdapter<T> delegate)` using defaults
- [x] Add static factory: `wrap(HttpAdapter<T> delegate, RetryConfig config)`
- [x] Implement all HttpAdapter methods delegating to `executeWithRetry()`
- [x] Implement private method: `executeWithRetry(Supplier<CompletableFuture<HttpResult<T>>> operation, HttpMethod method, int attempt)`
- [x] Return type: `CompletableFuture<HttpResult<T>>`
- [x] Call operation.get() to execute (already async, no supplyAsync needed)
- [x] Add `.thenCompose()` to handle result
- [x] If success: return completed future immediately
- [x] If idempotentOnly=true and method non-idempotent: skip retry
- [x] If non-retryable error: return failure immediately
- [x] If max attempts reached: return failure immediately
- [x] If retryable: calculate delay using config.calculateDelay()
- [x] Schedule retry using `CompletableFuture.delayedExecutor()` (non-blocking!)
- [x] Recursively call executeWithRetry() after delay
- [x] Use tail recursion via thenCompose (no stack overflow)
- [x] Add logging: DEBUG for attempts, WARN for retries, WARN for exhaustion
- [x] Create unit tests in `ResilientHttpAdapterTest` (minimum 80% coverage)
- [x] Test retry on NETWORK_ERROR
- [x] Test retry on SERVER_ERROR
- [x] Test no retry on CLIENT_ERROR
- [x] Test max attempts respected
- [x] Test idempotentOnly=true skips POST/PATCH
- [x] Test idempotentOnly=false retries all methods
- [x] Test async delays (no blocking)
- [x] Test success on first attempt
- [x] Test success on retry attempt
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- ResilientHttpAdapter wraps any HttpAdapter
- All operations return CompletableFuture (fully async)
- Retries NETWORK_ERROR and SERVER_ERROR only
- No retry for CLIENT_ERROR, INVALID_CONTENT, CONFIGURATION_ERROR
- Delays use non-blocking CompletableFuture.delayedExecutor
- idempotentOnly=true (default) skips POST/PATCH retry
- idempotentOnly=false retries all methods (requires explicit opt-in)
- Test coverage ≥ 80%
- 100% coverage for retry decision logic

---

### Task 14: Update HttpStatusFamily with toErrorCategory()

**Goal:** Add conversion method from status family to error category

**References:**
- Specification: `http-client-plan/01-current-architecture.adoc` lines 666-685
- Existing file: `src/main/java/de/cuioss/http/client/handler/HttpStatusFamily.java`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Read existing HttpStatusFamily enum
- [x] Add method: `toErrorCategory()` returning HttpErrorCategory
- [x] Map CLIENT_ERROR → HttpErrorCategory.CLIENT_ERROR
- [x] Map SERVER_ERROR → HttpErrorCategory.SERVER_ERROR
- [x] Map SUCCESS → throw IllegalStateException("SUCCESS is not an error")
- [x] Map REDIRECTION → HttpErrorCategory.INVALID_CONTENT (rare, most handled by HttpClient)
- [x] Map INFORMATIONAL, UNKNOWN → HttpErrorCategory.INVALID_CONTENT
- [x] Add comprehensive Javadoc explaining mapping
- [x] Update unit tests in HttpStatusFamilyTest
- [x] Test all mappings
- [x] Test SUCCESS throws IllegalStateException
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- toErrorCategory() method added to HttpStatusFamily
- All status families mapped correctly
- SUCCESS throws exception (not an error)
- Test coverage ≥ 80% for new method
- JavaDoc explains 3xx handling (most redirects followed automatically)

---

### Task 15: Update Module Exports

**Goal:** Export new packages in module-info.java

**References:**
- Specification: `http-client-plan/03-core-components.adoc` lines 1596-1614
- Existing file: `src/main/java/module-info.java`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Read existing module-info.java
- [x] Add export: `exports de.cuioss.http.client.adapter;`
- [x] Verify all other required exports exist
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- de.cuioss.http.client.adapter exported
- Module compiles successfully
- No module resolution errors

---

### Task 16: Update StringContentConverter

**Goal:** Update base converter to implement HttpResponseConverter

**References:**
- Specification: `http-client-plan/01-current-architecture.adoc` lines 242-304
- Existing file: `src/main/java/de/cuioss/http/client/converter/StringContentConverter.java`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Read existing StringContentConverter class
- [x] Change class to implement `HttpResponseConverter<T>` instead of HttpContentConverter
- [x] Add abstract method: `ContentType contentType()` (subclasses must implement)
- [x] Update existing convert() and getBodyHandler() methods (no changes to signatures)
- [x] Update unit tests in StringContentConverterTest
- [x] Test contentType() requires subclass implementation
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- StringContentConverter implements HttpResponseConverter
- contentType() is abstract (subclasses implement)
- Existing convert() and getBodyHandler() unchanged
- Test coverage maintained ≥ 80%

---

### Task 17: Remove HttpContentConverter (BREAKING CHANGE - Pre-1.0)

**Goal:** Remove legacy converter interface entirely (pre-1.0 breaking change)

**References:**
- Specification: `http-client-plan/01-current-architecture.adoc` lines 161-476
- Existing file: `src/main/java/de/cuioss/http/client/converter/HttpContentConverter.java`

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Remove HttpContentConverter.java file entirely
- [x] Update StringContentConverter to ONLY implement HttpResponseConverter
- [x] Remove StringContentConverter's emptyValue() method and dual-interface support
- [x] Find all usages of HttpContentConverter in codebase
- [x] Update all usages to use HttpResponseConverter and HttpRequestConverter
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- HttpContentConverter.java file deleted
- StringContentConverter implements ONLY HttpResponseConverter
- No @Deprecated annotations anywhere in the codebase
- All code uses new HttpResponseConverter/HttpRequestConverter interfaces
- Build passes with zero compilation errors

---

### Task 18: Integration Tests with MockWebServer

**Goal:** Create comprehensive integration tests for real HTTP scenarios

**References:**
- Specification: `http-client-plan/06-implementation-plan.adoc` lines 90-122
- Testing guidelines: section on integration tests

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Create integration test class: `ETagAwareHttpAdapterIntegrationTest`
- [x] Set up MockWebServer in @BeforeEach
- [x] Tear down MockWebServer in @AfterEach
- [x] Test GET with ETag: first request 200 with ETag, second request sends If-None-Match, receives 304, returns Success with cached content
- [x] Test POST request: body sent with Content-Type, response converted, ETag extracted but not cached
- [x] Test PUT request: idempotent behavior, successful update
- [x] Test DELETE request: no body sent, 204 response handled
- [x] Test network failure: server not responding, IOException caught, returns NETWORK_ERROR failure
- [x] Test server error: 503 response, returns SERVER_ERROR failure with status code
- [x] Test client error: 404 response, returns CLIENT_ERROR failure with status code
- [x] Create integration test class: `ResilientHttpAdapterIntegrationTest`
- [x] Test retry on network failure: attempt 1 fails with IOException, attempt 2 succeeds
- [x] Test retry on server error: attempt 1 returns 503, attempt 2 returns 200
- [x] Test no retry on client error: 404 returned immediately, no retry attempts
- [x] Test composition: ResilientHttpAdapter wraps ETagAwareHttpAdapter, retry + caching work together
- [x] Test 304 not retried: ETagAwareHttpAdapter returns Success for 304, ResilientHttpAdapter doesn't retry
- [x] Run all integration tests
- [x] Verify test coverage ≥ 80% overall, 100% for critical paths
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [x] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- Integration tests use MockWebServer for realistic HTTP
- All HTTP methods tested (GET, POST, PUT, DELETE)
- ETag caching flow tested (200 → 304)
- Network errors tested with retry
- Server errors tested with retry
- Client errors tested without retry
- Composition tested (ResilientHttpAdapter + ETagAwareHttpAdapter)
- Test coverage ≥ 80% overall
- 100% coverage for critical paths (ETag 304, retry decisions, cache eviction)

---

### Task 19: Documentation and Examples

**Goal:** Create comprehensive Javadoc and usage examples

**References:**
- Specification: `http-client-plan/07-usage-examples.adoc`
- `http-client-plan/06-implementation-plan.adoc` lines 145-160

**Checklist:**
- [x] Read and understand all references above
- [x] If unclear, ask user for clarification (DO NOT guess)
- [x] Review all new classes for Javadoc completeness
- [x] Ensure all public methods have @param, @return, @throws tags
- [x] Add class-level usage examples to all new classes
- [x] Add @since 1.0 tags to all new APIs
- [x] Create package-info.java for de.cuioss.http.client.adapter
- [x] Document async-first architecture
- [x] Create package-info.java for de.cuioss.http.client (update for new enums)
- [x] Add examples for GET with ETag
- [x] Add examples for POST with JSON
- [x] Add examples for retry configuration
- [x] Add examples for composition (retry + caching)
- [x] Add examples for async execution patterns
- [x] Add examples for blocking convenience methods
- [x] Document token refresh cache bloat solution (CacheKeyHeaderFilter.excluding)
- [x] Run `project-builder` agent to verify build passes
- [x] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- All public APIs have complete Javadoc
- Class-level examples for all new classes
- Package-info.java documents architecture
- Examples cover common use cases (GET, POST, retry, composition)
- Async patterns documented
- Token refresh cache bloat solution documented
- @since 1.0 tags present

---

### Task 20: Security Integration Documentation

**Goal:** Document security validation integration patterns

**References:**
- Specification: `http-client-plan/09-security-considerations.adoc`
- `http-client-plan/06-implementation-plan.adoc` lines 162-195

**Checklist:**
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Create security integration examples in Javadoc
- [ ] Document header validation using HTTPHeaderValidationPipeline
- [ ] Document body validation before POST/PUT
- [ ] Document Content-Type validation in converters
- [ ] Add example: ValidatingHttpAdapter decorator pattern
- [ ] Document when to validate (before request, not in adapter)
- [ ] Document URL validation (already integrated in HttpHandler)
- [ ] Add security best practices section to package-info.java
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- Security validation integration documented
- Header validation examples provided
- Body validation patterns documented
- ValidatingHttpAdapter example in Javadoc
- Security best practices section in package-info.java

---

### Task 21: Pre-Commit Quality Checks

**Goal:** Run all quality checks and fix any issues

**References:**
- Specification: `http-client-plan/06-implementation-plan.adoc` lines 197-226
- Project standards: `/Users/oliver/git/cui-http/CLAUDE.md`

**Checklist:**
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Run pre-commit checks: `./mvnw -Ppre-commit clean verify`
- [ ] Verify all tests pass
- [ ] Verify code formatting correct
- [ ] Verify static analysis passes
- [ ] Verify coverage ≥ 80% overall, 100% for critical paths
- [ ] Run coverage report: `./mvnw -Pcoverage clean verify`
- [ ] Check coverage report for any gaps
- [ ] Run dependency analysis: `./mvnw dependency:analyze`
- [ ] Fix any warnings or errors found
- [ ] Re-run checks until all pass
- [ ] Run `project-builder` agent for final verification
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- All pre-commit checks pass
- Test coverage ≥ 80% overall
- 100% coverage for critical paths (ETag 304 handling, retry decisions, error categorization, cache eviction)
- No compilation warnings
- No static analysis violations
- Dependency analysis clean
- All tests pass

---

### Task 22: Final Review and Cleanup

**Goal:** Review entire implementation for quality and completeness

**References:**
- Specification: All documents in `http-client-plan/`
- Project standards: `/Users/oliver/git/cui-http/CLAUDE.md`

**Checklist:**
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Review all new classes for CUI standards compliance
- [ ] Verify Lombok annotations used appropriately
- [ ] Verify @Nullable/@NonNull from JSpecify used
- [ ] Verify CuiLogger used (not slf4j or System.out)
- [ ] Verify no TODO or FIXME comments in production code
- [ ] Verify all deprecated methods documented with migration path
- [ ] Review test coverage report for any gaps
- [ ] Verify all public APIs have examples
- [ ] Verify no code duplication
- [ ] Verify thread safety documented where applicable
- [ ] Run final pre-commit check: `./mvnw -Ppre-commit clean verify`
- [ ] Run `project-builder` agent for final build
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- All code follows CUI standards
- No TODO/FIXME in production code
- All deprecated APIs documented
- Thread safety documented
- No code duplication
- All pre-commit checks pass
- Build successful

---

## Completion Criteria

All tasks above must be marked `[x]` before the issue is considered complete.

**Final verification:**
1. All acceptance criteria met for every task
2. All tests passing (unit + integration)
3. Code coverage ≥ 80% overall, 100% for critical paths
4. Documentation updated and complete
5. Build passes with no errors or warnings
6. All changes committed

**Critical Path Coverage (100% Required):**
- ETag cache lookup and 304 handling flow
- If-None-Match header injection logic
- Cache entry retrieval and reference holding
- Retry decision logic (error category + idempotency)
- Exponential backoff delay calculation
- Request/response converter error handling
- Cache eviction logic (timestamp-based)

---

**Plan created by:** issue-manager agent
**Date:** 2025-10-23
**Total tasks:** 22
**Estimated completion time:** 8-12 hours of focused implementation

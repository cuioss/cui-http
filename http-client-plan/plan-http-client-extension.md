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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Create `ETagAwareHttpAdapter<T>` class implementing `HttpAdapter<T>`
- [ ] Add final fields: `HttpHandler httpHandler`, `HttpClient httpClient`, `HttpResponseConverter<T> responseConverter`, `HttpRequestConverter<T> requestConverter`, `boolean etagCachingEnabled`, `CacheKeyHeaderFilter cacheKeyHeaderFilter`, `int maxCacheSize`
- [ ] Create CacheEntry record: `record CacheEntry<T>(T content, String etag, long timestamp)`
- [ ] Add cache field: `ConcurrentHashMap<String, CacheEntry<T>> cache`
- [ ] Create HttpClient ONCE in constructor (store as final field for thread-safe reuse)
- [ ] Create Builder class with fields matching adapter parameters
- [ ] Implement builder methods with validation
- [ ] Set builder defaults: etagCachingEnabled=true, cacheKeyHeaderFilter=ALL, maxCacheSize=1000
- [ ] Implement build() method that constructs adapter
- [ ] Add static factory: `statusCodeOnly(HttpHandler)` using VoidResponseConverter
- [ ] Add method: `clearETagCache()`
- [ ] Add comprehensive class-level Javadoc with examples
- [ ] Create test class `ETagAwareHttpAdapterTest`
- [ ] Test builder validation (null handler, null converter)
- [ ] Test default builder values
- [ ] Test statusCodeOnly() factory method
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Implement private method: `send(HttpMethod method, @Nullable T body, Map<String, String> headers)`
- [ ] Return type: `CompletableFuture<HttpResult<T>>`
- [ ] Validate safe methods (GET/HEAD/OPTIONS) don't have bodies
- [ ] Generate cache key using `generateCacheKey()` method
- [ ] Retrieve cache entry BEFORE building request (hold local reference)
- [ ] Build HttpRequest using `httpHandler.requestBuilder()`
- [ ] Set HTTP method using `method.methodName()`
- [ ] Add request body using `buildBodyPublisher()`
- [ ] Add custom headers from Map
- [ ] Add If-None-Match header if cache entry exists and method is GET
- [ ] Execute async using `httpClient.sendAsync(request, responseConverter.getBodyHandler())`
- [ ] Return CompletableFuture (no blocking!)
- [ ] Implement helper: `generateCacheKey(URI uri, Map<String, String> headers, CacheKeyHeaderFilter filter)`
- [ ] Sort headers alphabetically in cache key
- [ ] Apply filter predicate to each header
- [ ] Implement helper: `buildBodyPublisher(@Nullable T body)`
- [ ] Return noBody() if body is null OR requestConverter is null
- [ ] Call requestConverter.toBodyPublisher() if body present
- [ ] Add unit tests for send() method structure
- [ ] Test cache key generation with filters
- [ ] Test If-None-Match only added for GET with cache entry
- [ ] Test body publisher creation
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] In send() method, add `.thenApply()` to CompletableFuture chain
- [ ] Extract HTTP status code from response
- [ ] Handle 304 Not Modified: check if statusCode == 304 AND cachedEntry != null
- [ ] For 304: return `HttpResult.success(cachedEntry.content(), cachedEntry.etag(), 304)`
- [ ] Extract ETag from response headers (all methods, not just GET)
- [ ] Convert response body using `responseConverter.convert(response.body())`
- [ ] Handle conversion failure: return Failure with INVALID_CONTENT
- [ ] Cache successful GET responses: if method is GET AND statusCode is 200 AND etag present AND content present
- [ ] Create CacheEntry with content, etag, timestamp
- [ ] Call `putInCache(cacheKey, entry)`
- [ ] For success: return `HttpResult.success(content.orElse(null), etag, statusCode)`
- [ ] Add `.exceptionally()` to handle exceptions
- [ ] Classify exceptions: IOException → NETWORK_ERROR, others → CONFIGURATION_ERROR
- [ ] Return Failure with appropriate error category
- [ ] Implement helper: `putInCache(String key, CacheEntry<T> entry)`
- [ ] Add entry to cache
- [ ] Call `checkAndEvict()`
- [ ] Implement helper: `checkAndEvict()`
- [ ] If cache.size() > maxCacheSize: remove oldest 10% by timestamp
- [ ] Use weakly-consistent iterator (safe for concurrent modification)
- [ ] Add unit tests for 304 handling
- [ ] Test 304 returns Success with cached content
- [ ] Test 304 preserves status code (not converted to 200)
- [ ] Test ETag extraction from all HTTP methods
- [ ] Test cache update on successful GET with ETag
- [ ] Test cache eviction when size exceeded
- [ ] Test exception handling (IOException, parsing failure)
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Implement `get(Map<String, String> headers)`: delegate to send(GET, null, headers)
- [ ] Implement `post(@Nullable T body, Map<String, String> headers)`: delegate to send(POST, body, headers)
- [ ] Implement `put(@Nullable T body, Map<String, String> headers)`: delegate to send(PUT, body, headers)
- [ ] Implement `patch(@Nullable T body, Map<String, String> headers)`: delegate to send(PATCH, body, headers)
- [ ] Implement `delete(Map<String, String> headers)`: delegate to send(DELETE, null, headers)
- [ ] Implement `delete(@Nullable T body, Map<String, String> headers)`: delegate to send(DELETE, body, headers)
- [ ] Implement `head(Map<String, String> headers)`: delegate to send(HEAD, null, headers)
- [ ] Implement `options(Map<String, String> headers)`: delegate to send(OPTIONS, null, headers)
- [ ] Implement generic body methods: `<R> post(HttpRequestConverter<R> converter, R body, Map headers)`
- [ ] For generic methods: temporarily use converter, delegate to send(), restore original converter
- [ ] Add unit tests for all HTTP method implementations
- [ ] Test GET caches responses
- [ ] Test POST/PUT/DELETE extract ETag but don't cache
- [ ] Test HEAD doesn't cache
- [ ] Test generic body methods with different types
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Create `RetryConfig` record in `src/main/java/de/cuioss/http/client/adapter/RetryConfig.java`
- [ ] Add record components: `int maxAttempts`, `Duration initialDelay`, `double multiplier`, `Duration maxDelay`, `double jitter`, `boolean idempotentOnly`
- [ ] Create nested Builder class
- [ ] Set builder defaults: maxAttempts=5, initialDelay=1s, multiplier=2.0, maxDelay=1min, jitter=0.1, idempotentOnly=true
- [ ] Implement builder validation in setter methods
- [ ] Validate maxAttempts >= 1
- [ ] Validate initialDelay positive
- [ ] Validate multiplier >= 1.0
- [ ] Validate maxDelay positive
- [ ] Validate jitter between 0.0 and 1.0
- [ ] Add static factory: `defaults()` returning default configuration
- [ ] Add static factory: `builder()` returning new Builder
- [ ] Add method: `calculateDelay(int attemptNumber)` with exponential backoff formula
- [ ] Use ThreadLocalRandom for jitter (thread-safe)
- [ ] Apply jitter: delay * (1 ± jitter)
- [ ] Cap delay at maxDelay
- [ ] Add comprehensive Javadoc explaining defaults and rationale
- [ ] Add @SuppressWarnings("java:S2245") for random in jitter
- [ ] Create unit tests in `RetryConfigTest` (minimum 80% coverage)
- [ ] Test builder validation (negative values, out of range)
- [ ] Test default values
- [ ] Test calculateDelay() exponential backoff
- [ ] Test jitter randomization
- [ ] Test maxDelay cap enforced
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Create `ResilientHttpAdapter<T>` class implementing `HttpAdapter<T>`
- [ ] Add final fields: `HttpAdapter<T> delegate`, `RetryConfig config`
- [ ] Create constructor taking delegate and config
- [ ] Add static factory: `wrap(HttpAdapter<T> delegate)` using defaults
- [ ] Add static factory: `wrap(HttpAdapter<T> delegate, RetryConfig config)`
- [ ] Implement all HttpAdapter methods delegating to `executeWithRetry()`
- [ ] Implement private method: `executeWithRetry(Supplier<CompletableFuture<HttpResult<T>>> operation, HttpMethod method, int attempt)`
- [ ] Return type: `CompletableFuture<HttpResult<T>>`
- [ ] Call operation.get() to execute (already async, no supplyAsync needed)
- [ ] Add `.thenCompose()` to handle result
- [ ] If success: return completed future immediately
- [ ] If idempotentOnly=true and method non-idempotent: skip retry
- [ ] If non-retryable error: return failure immediately
- [ ] If max attempts reached: return failure immediately
- [ ] If retryable: calculate delay using config.calculateDelay()
- [ ] Schedule retry using `CompletableFuture.delayedExecutor()` (non-blocking!)
- [ ] Recursively call executeWithRetry() after delay
- [ ] Use tail recursion via thenCompose (no stack overflow)
- [ ] Add logging: DEBUG for attempts, WARN for retries, WARN for exhaustion
- [ ] Create unit tests in `ResilientHttpAdapterTest` (minimum 80% coverage)
- [ ] Test retry on NETWORK_ERROR
- [ ] Test retry on SERVER_ERROR
- [ ] Test no retry on CLIENT_ERROR
- [ ] Test max attempts respected
- [ ] Test idempotentOnly=true skips POST/PATCH
- [ ] Test idempotentOnly=false retries all methods
- [ ] Test async delays (no blocking)
- [ ] Test success on first attempt
- [ ] Test success on retry attempt
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Read existing HttpStatusFamily enum
- [ ] Add method: `toErrorCategory()` returning HttpErrorCategory
- [ ] Map CLIENT_ERROR → HttpErrorCategory.CLIENT_ERROR
- [ ] Map SERVER_ERROR → HttpErrorCategory.SERVER_ERROR
- [ ] Map SUCCESS → throw IllegalStateException("SUCCESS is not an error")
- [ ] Map REDIRECTION → HttpErrorCategory.INVALID_CONTENT (rare, most handled by HttpClient)
- [ ] Map INFORMATIONAL, UNKNOWN → HttpErrorCategory.INVALID_CONTENT
- [ ] Add comprehensive Javadoc explaining mapping
- [ ] Update unit tests in HttpStatusFamilyTest
- [ ] Test all mappings
- [ ] Test SUCCESS throws IllegalStateException
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Read existing module-info.java
- [ ] Add export: `exports de.cuioss.http.client.adapter;`
- [ ] Verify all other required exports exist
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Read existing StringContentConverter class
- [ ] Change class to implement `HttpResponseConverter<T>` instead of HttpContentConverter
- [ ] Add abstract method: `ContentType contentType()` (subclasses must implement)
- [ ] Update existing convert() and getBodyHandler() methods (no changes to signatures)
- [ ] Update unit tests in StringContentConverterTest
- [ ] Test contentType() requires subclass implementation
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- StringContentConverter implements HttpResponseConverter
- contentType() is abstract (subclasses implement)
- Existing convert() and getBodyHandler() unchanged
- Test coverage maintained ≥ 80%

---

### Task 17: Deprecate HttpContentConverter

**Goal:** Mark legacy converter interface as deprecated

**References:**
- Specification: `http-client-plan/01-current-architecture.adoc` lines 161-476
- Existing file: `src/main/java/de/cuioss/http/client/converter/HttpContentConverter.java`

**Checklist:**
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Read existing HttpContentConverter interface
- [ ] Add @Deprecated annotation with since="1.0", forRemoval=true
- [ ] Add default implementation for emptyValue() returning null
- [ ] Add Javadoc explaining deprecation and migration path
- [ ] Document replacement: HttpResponseConverter and HttpRequestConverter
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

**Acceptance Criteria:**
- HttpContentConverter marked @Deprecated
- emptyValue() has default implementation returning null
- Javadoc explains migration to new interfaces
- Existing code still compiles with deprecation warnings

---

### Task 18: Integration Tests with MockWebServer

**Goal:** Create comprehensive integration tests for real HTTP scenarios

**References:**
- Specification: `http-client-plan/06-implementation-plan.adoc` lines 90-122
- Testing guidelines: section on integration tests

**Checklist:**
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Create integration test class: `ETagAwareHttpAdapterIntegrationTest`
- [ ] Set up MockWebServer in @BeforeEach
- [ ] Tear down MockWebServer in @AfterEach
- [ ] Test GET with ETag: first request 200 with ETag, second request sends If-None-Match, receives 304, returns Success with cached content
- [ ] Test POST request: body sent with Content-Type, response converted, ETag extracted but not cached
- [ ] Test PUT request: idempotent behavior, successful update
- [ ] Test DELETE request: no body sent, 204 response handled
- [ ] Test network failure: server not responding, IOException caught, returns NETWORK_ERROR failure
- [ ] Test server error: 503 response, returns SERVER_ERROR failure with status code
- [ ] Test client error: 404 response, returns CLIENT_ERROR failure with status code
- [ ] Create integration test class: `ResilientHttpAdapterIntegrationTest`
- [ ] Test retry on network failure: attempt 1 fails with IOException, attempt 2 succeeds
- [ ] Test retry on server error: attempt 1 returns 503, attempt 2 returns 200
- [ ] Test no retry on client error: 404 returned immediately, no retry attempts
- [ ] Test composition: ResilientHttpAdapter wraps ETagAwareHttpAdapter, retry + caching work together
- [ ] Test 304 not retried: ETagAwareHttpAdapter returns Success for 304, ResilientHttpAdapter doesn't retry
- [ ] Run all integration tests
- [ ] Verify test coverage ≥ 80% overall, 100% for critical paths
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
- [ ] Commit changes using `commit-current-changes` agent

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
- [ ] Read and understand all references above
- [ ] If unclear, ask user for clarification (DO NOT guess)
- [ ] Review all new classes for Javadoc completeness
- [ ] Ensure all public methods have @param, @return, @throws tags
- [ ] Add class-level usage examples to all new classes
- [ ] Add @since 1.0 tags to all new APIs
- [ ] Create package-info.java for de.cuioss.http.client.adapter
- [ ] Document async-first architecture
- [ ] Create package-info.java for de.cuioss.http.client (update for new enums)
- [ ] Add examples for GET with ETag
- [ ] Add examples for POST with JSON
- [ ] Add examples for retry configuration
- [ ] Add examples for composition (retry + caching)
- [ ] Add examples for async execution patterns
- [ ] Add examples for blocking convenience methods
- [ ] Document token refresh cache bloat solution (CacheKeyHeaderFilter.excluding)
- [ ] Run `project-builder` agent to verify build passes
- [ ] Analyze build results - if issues found, fix and re-run
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

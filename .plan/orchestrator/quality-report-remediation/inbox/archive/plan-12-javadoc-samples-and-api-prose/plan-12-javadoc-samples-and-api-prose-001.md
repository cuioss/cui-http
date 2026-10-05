envelope_version=1
sender_type=plan
sender_id=plan-12-javadoc-samples-and-api-prose
epic=quality-report-remediation
kind=finding
created=2026-10-05T06:24:04Z

# PLAN-12 (Javadoc samples and API prose): sweep results and follow-ups

## Sweep counts (deliverable 7)

- Types checked: 50 (all public top-level types in `cui-http-core/src/main/java`).
- Examples added: 8 new type-level worked examples. Every surveyed type already had one, so no type had to be added to the write-set for a missing example. None was recorded as "no example warranted".
  - `HttpLogMessages`, `ForwardedLogMessages`: emitting one declared `LogRecord` through a `CuiLogger`.
  - `StringContentConverter`: a subclass implementing `convertString` and `contentType`, plugged into `ETagAwareHttpAdapter`.
  - `SecureSSLContextProvider`: `getOrCreateSecureSSLContext(null)`, then `HttpHandler.builder().sslContext(...).tlsVersions(provider)`.
  - `AbstractValidationPipeline`: use through `validate(...)`; the violation is counted in the shared `SecurityEventCounter`.
  - `ContentTypeValidationPipeline`: an allow-list example. Under a non-empty allow-list a missing Content-Type is rejected (`INVALID_INPUT`) and does not return `Optional.empty()`.
  - `URLParameterNameValidationPipeline`: validating the key, paired with `URLParameterValidationPipeline` for the value.
  - `AllowBlockListStage`: built with `AllowBlockListStage.forHeaderNames(config)`.
- Existing samples corrected because they did not compile (Javadoc only, committed with deliverable 7):
  - `security/data/Cookie.java`: `validator.validate(x, ValidationType...)` became `new CookiePrefixValidationStage(config).validateCookie(cookie)`.
  - `security/data/URLParameter.java`: the two-argument `validate` became `PipelineFactory.createParameterNamePipeline` / `createUrlParameterPipeline`, each called with one argument.
  - `security/core/UrlSecurityFailureType.java`: `.input(path)` became `.validationType(ValidationType.URL_PATH).originalInput(path)`.
  - `client/converter/VoidResponseConverter.java`: this file widened the write-set. It was found during the survey. `adapter.delete()`, `head()` and `post(null)` return `CompletableFuture`, so they cannot be assigned to `HttpResult<Void>`. They became `deleteBlocking()`, `headBlocking()` and `postBlocking(null)`.
- Every added or corrected sample compiles from a scratch file against `cui-http-core/target/classes` (with `cui-java-tools` and `jspecify` on the classpath). The outcomes the comments claim were checked by running the scratch file: accept and reject verdicts, the counted event, and the rendered `HTTP-106` / `HTTP-125` messages.

## Samples that need an API change to compile

None. No sample in deliverables 1 to 7 needed an API change.

Side note, not an API issue: the `HttpRequestConverter` and `HttpResponseConverter` class samples use Jackson or JAXB on purpose, as illustrations of third-party converters. They cannot compile against cui-http-core alone, and they were left unchanged.

## Samples dropped from scope because they were already correct at HEAD

- client: the retry, composition, async, blocking, cache-key and POST samples.
- adapter: examples 1, 3, 4 and 5, the error-handling switch, and URL handling.
- result: the factory-method sample.

## Follow-up: 23 test files still cite "HTTP verification specification"

Main sources are clean (0 hits). The phrase is still in these test files:

- generators/cookie/AttackCookieGenerator.java
- generators/cookie/ValidCookieGenerator.java
- generators/encoding/BoundaryFuzzingGenerator.java
- generators/encoding/EncodingCombinationGenerator.java
- generators/encoding/UnicodeAttackGenerator.java
- generators/encoding/UnicodeControlCharacterAttackGenerator.java
- generators/encoding/UnicodeNormalizationAttackGenerator.java
- generators/url/AttackURLParameterGenerator.java
- generators/url/InvalidURLGenerator.java
- generators/url/URLLengthLimitAttackGenerator.java
- generators/url/ValidURLGenerator.java
- generators/url/ValidURLParameterGenerator.java
- tests/DoubleEncodingAttackTest.java
- tests/EncodedPathTraversalAttackTest.java
- tests/HttpRequestSmugglingAttackTest.java
- tests/MixedEncodingAttackTest.java
- tests/NullBytePathTraversalAttackTest.java
- tests/PathTraversalAttackTest.java
- tests/ProtocolHandlerAttackTest.java
- tests/URLLengthLimitAttackTest.java
- tests/UnicodeControlCharacterAttackTest.java
- tests/UnicodeNormalizationAttackTest.java
- tests/UnicodePathTraversalAttackTest.java

All paths are under `cui-http-core/src/test/java/de/cuioss/http/security/`.

## Candidate follow-up: adapter request-body prose may overstate

The adapter prose on request bodies says "This prevents SQL injection, XSS scripts, path traversal…". That may claim more than the library actually checks. It is worth a separate review.

## ADR

No ADR was written in this plan, so no number was allocated.

# PLAN-08: HTTP Client Correctness — Cache Principal Isolation, Redirects, Retry, TLS and Result Invariants

epic: quality-report-remediation
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-08-etag-cache-principal-isolation.md` (filename retained across the merge
> below so the epic's `status.json` row needs no rename) and is queued in the epic `status.json`
> `plans[]` field. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no brief,
> so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

> ⛔ **REDISTRIBUTED 2026-09-15 at `d385aa3` (cleanup pass).** This spec MERGES the formerly
> separate `PLAN-08-etag-cache-principal-isolation.md` (its own prior content, preserved below)
> and `PLAN-09-redirect-retry-tls-result-correctness.md`. Both were already declared strictly
> sequential (PLAN-09 depended on PLAN-08) and both overlap at
> `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ResilientHttpAdapter.java` — the
> component-first, task-second grouping rule makes one plan the correct unit, not two PRs across
> one dependency edge. The absorbed spec's file is retired to a pointer at
> `plans/PLAN-09-redirect-retry-tls-result-correctness.md`; its queue row (`PLAN-09`) is
> transitioned to `parked` with this plan named as the merge destination. Deliverables 1-5 are the
> former PLAN-08 verbatim; deliverables 6-11 are the former PLAN-09 verbatim; deliverable 12
> MERGES the two plans' separate test-hardening deliverables (former PLAN-08 #6 and former
> PLAN-09 #7) into one, because both are "raise this test package to a real assertion standard"
> work over the same `de.cuioss.http.client` test tree — collapsing them is what keeps the merged
> count at 12 rather than 13. This is not a weak merge: the coupling was already load-bearing in
> both source specs' own Dependencies sections.

## Objective

Close the epic's highest-impact code defect and the client package's remaining protocol-
correctness cluster in one pass, since the second cluster's invariants (deliverable 10) are
tightened on exactly the `HttpResult` states deliverable 1 restricts. Under
`CacheKeyHeaderFilter.excluding("Authorization")` — the configuration the adapter's own Javadoc
recommends for token refresh — two principals share one cache entry, principal A's `ETag` is sent
as principal B's `If-None-Match`, and a 401/403 for B is answered with A's cached body as
"fallback content". Separately, a 301/302 redirect silently rewrites a `DELETE`, `PUT` or `PATCH`
into a bodyless `GET` and then reports the original method as successful; the retry classifier
treats the JDK's transient mid-handshake failure as a permanent configuration error;
`RedirectPolicy.forwardsCredentials` returns `true` for any cross-origin HTTPS target under a mode
named `FORWARD_TO_ALLOWLISTED` without ever reading the allow-list; `RetryConfig` accepts a
sub-millisecond initial delay and then hot-loops at 0 ms; the result records do not enforce their
own documented invariants and can throw `NullPointerException` on a supported construction; and
the cleartext `HttpHandler` constructor silently discards caller-supplied TLS settings while
misreporting `isVerifyHostname()`. In the same plan, stop `ETagAwareHttpAdapterTest` from firing
real outbound HTTPS requests at `api.example.com` and give the whole client test tree exact
assertions and negative-path coverage. Every change is driven by a regression test written and
seen to fail first.

## Deliverables

1. **Fallback content is restricted to availability failures (F-client-1, primary fix).** The
   gate in `handleHttpResponse` currently reads `method == GET && cachedEntry != null` and asks
   nothing about the error class or about which principal populated the entry. Serving a cached
   body on a 401/403/404 is an authorisation-boundary violation, not a resilience feature.
   Restrict the fallback to transport and 5xx availability failures, and record the change as a
   documented breaking change: a 4xx that previously returned stale content now surfaces as an
   error.
2. **Credential-bearing headers are part of the cache-entry identity (F-client-1, structural
   half).** Even with a filter that excludes `Authorization` from the *key*, an entry must not be
   served to, or conditionally revalidated on behalf of, a different principal. Bind each entry
   to the credential material that produced it, so `excluding("Authorization")` becomes a
   cache-key-bloat optimisation rather than a principal-mixing hazard.
3. **Cache-key hygiene and bounds (F-client-15).** `generateCacheKey` appends `name:value`
   verbatim for every filter-included header, so the default `ALL` filter embeds the literal
   `Authorization` value in a `ConcurrentHashMap` key. Hash the credential-bearing components,
   and add a time-based TTL alongside the existing size-triggered LRU so a fallback entry cannot
   be arbitrarily old.
4. **HEAD conditional requests (F-client-12).** `canReadCache` returns true for GET *and* HEAD so
   the adapter looks up an entry for a HEAD request, but `If-None-Match` is set only when
   `method == HttpMethod.GET`. Either make HEAD conditional or stop reading the cache for it —
   the documented HEAD-304 branch is currently reachable only through a header the same Javadoc
   tells callers not to send.
5. **Token-refresh guidance rewritten (F-client-1, documentation half in `.java` files).**
   `CacheKeyHeaderFilter`'s "fine-grained control … maintaining security" and "Solves token
   refresh cache bloat" Javadoc, the adapter's "Example: Token Refresh Without Cache Bloat"
   block, and the `adapter/package-info.java` sample all teach the configuration this plan is
   fixing. Rewrite all four to describe the safe pattern.
6. **Redirect method preservation (F-client-3).** `rebuildForHop` rewrites to `GET` whenever
   `!preserveMethodAndBody && (statusCode == 303 || !bodylessMethod)`, which covers `DELETE`,
   `PUT`, `PATCH` and `OPTIONS` on a 301/302. RFC 9110 sanctions the rewrite only for `POST`, and
   the JDK's own `RedirectFilter` preserves the method. Restrict the rewrite to `POST` (and 303),
   so a redirected `DELETE` is no longer reported as a successful `DELETE` after issuing a `GET`.
7. **Credential forwarding honours the allow-list (F-client-9).** `forwardsCredentials` never
   reads `allowedHosts`; only `HttpHandler`'s call order (`refuse()` before
   `forwardsCredentials()`) keeps this from being exploitable through the handler. Make the
   method itself consult the allow-list, so it is safe for any caller, not only the one whose
   call order happens to protect it.
8. **Retry classification of transient handshake failures (F-client-4).** The
   `SSLHandshakeException` carve-out classifies every handshake failure as
   `CONFIGURATION_ERROR` (non-retryable), while the adjacent Javadoc states that a connection
   reset mid-handshake is a genuinely transient condition that stays retryable — and the JDK
   reports exactly that case as `SSLHandshakeException`, not as a bare `SSLException`. Make the
   classification match its own stated rationale.
9. **`RetryConfig` rejects a delay it cannot honour (F-client-10).** The compact constructor's
   guard is `isNegative() || isZero()`, so `Duration.ofNanos(1..999_999)` is accepted;
   `calculateDelay` then truncates it to 0 ms and every retry fires immediately. Require an
   initial delay of at least one millisecond.
10. **Result-type invariants and `HttpErrorCategory` correctness (F-client-17, F-client-14).**
    `HttpResult.success(content, etag, httpStatus)` accepts `success(x, null, 500)` despite the
    documented "success is 200 or 304" contract, and `Failure.getErrorMessage()` calls
    `Optional.of(errorMessage)` with no null check, so a `failure(null, cause, category)` — which
    `failure()` itself accepts — throws `NullPointerException` on first read downstream. Enforce
    both invariants at construction. Separately, `fromException(InterruptedException)` falls
    through to `CONFIGURATION_ERROR` and the Javadoc sample routes interruption through it;
    classify interruption correctly and fix the sample.
11. **TLS scope honesty and the cleartext constructor (F-client-2, F-client-16, F-client-13).**
    `HostnameVerificationRelaxingTrustManager`'s Javadoc claims algorithm constraints and the
    revocation posture "still apply", but the `SSLEngine` overload forwards a `null` engine, which
    makes the JDK skip `AlgorithmChecker` and the stapled-OCSP consultation — correct the claim to
    state what is actually still enforced. The HTTP-URI constructor hard-codes `sslContext = null`,
    replaces any caller-supplied provider and forces `verifyHostname = true`, so
    `verifyHostname(false)` on an `http://` URI builds and then misreports; make the discard
    explicit rather than silent. Finally, `StringContentConverter.parseDeclaredCharset` fails to
    match `charset = X` (whitespace around `=`) and silently falls back to UTF-8; parse the
    parameter per RFC 9110 grammar.
12. **Client test suite hardening, isolation and negative-path coverage (F-client-7, F-client-8,
    F-client-18 — MERGED from both source plans' final deliverables).** 43 of 54
    `ETagAwareHttpAdapterTest` methods assert only `assertNotNull`, and the fixture targets the
    real host `https://api.example.com/test` with no MockWebServer, so 45 call sites fire real
    outbound requests (several on futures that are never awaited); move the suite onto
    MockWebServer and give every method an exact assertion — this is a build-reliability defect,
    not only test quality. Across the rest of the client test tree, no test covers a timeout, a
    malformed response, end-to-end 5xx exhaustion, or the TLS floor *over the wire* — the existing
    floor assertion reads a client-side flag on a handler whose handshake never happened, which the
    integration test class's own comment names as insufficient; add those tests. Introduce
    `cui-test-generator` `TypedGenerator` usage beyond the two `HttpResult` generators, which today
    have a single hand-rolled-loop consumer.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass; the repository's production and test code at that sha is byte-identical to the
reviewed commit `0bad295`. The verification corrected a systematic 14-15 line citation drift in
`CacheKeyHeaderFilter.java`, `RetryConfig.java` and several other stale citations; the corrected
numbers are used below. Re-checked at `ca74911` and again at `d385aa3` (this cleanup pass): `git
log 73afd22..d385aa3` touches no file under `de.cuioss.http.client` — every claim below is
unmoved since the reading that produced it. The only client-adjacent movement across that whole
range is `doc/adr/`, which grew by ADR-0019 through ADR-0022 and carries no claim below.

**From the former PLAN-08 (ETag cache / principal isolation):**

- OBSERVED: `prepareCacheContext` keys purely on URI plus filtered headers, so two principals
  share one key when `Authorization` is excluded — read at
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapter.java` §
  `prepareCacheContext` (lines 1139-1142).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3 (no file under de.cuioss.http.client moved since ca74911's re-check); carried forward unchanged from the redistributed PLAN-08.
- OBSERVED: `buildAndExecute` sends the cached `ETag` as `If-None-Match` regardless of the
  requesting principal's own `Authorization` value — read at the same file § `buildAndExecute`
  (lines 623-625).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-08.
- OBSERVED: the fallback gate is `method == GET && cachedEntry != null` and checks neither the
  error class nor the populating principal; a 401/403 for B returns `HttpResult.Failure` carrying
  A's `cachedEntry.content()` / `etag()` — read at the same file § `handleHttpResponse`
  (lines 986-996, gate at 987, payload at 992-994).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-08.
- OBSERVED: the recommending Javadoc exists at
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/CacheKeyHeaderFilter.java`:29-30
  ("fine-grained control … maintaining security") and :121-122 ("Solves token refresh cache
  bloat"), and at `ETagAwareHttpAdapter.java`:142-158 and :1229-1231; the sample is at
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java`:115-133.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-08.
- OBSERVED: `generateCacheKey` appends escaped-but-unhashed `name:value` for every included
  header (lines 731-754); `maxCacheSize` defaults to 1000 (line 1164); eviction is
  size-triggered LRU-by-timestamp only, with no TTL (lines 852-881) — read at
  `ETagAwareHttpAdapter.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-08.
- OBSERVED: `canReadCache` returns true for GET or HEAD (lines 1013-1015) while `If-None-Match`
  is set only for GET (line 623) — read at `ETagAwareHttpAdapter.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-08.
- OBSERVED (derived count, re-derived by the verification pass): 43 occurrences of `assertNotNull`
  across 54 `@Test` methods; the fixture host is `https://api.example.com/test` at lines 48-50;
  45 unmocked call sites; `safeMethodsRejectBody` (lines 208-219) asserts only
  `assertNotNull(adapter)` — read at
  `cui-http-core/src/test/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapterTest.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-08.
- OBSERVED: the verification pass corroborated 18 of 18 findings in `03-http-client.adoc`, 0
  contradicted, 0 unverifiable. This merged plan owns all 18 (4 formerly PLAN-08's — F-client-1,
  -7, -12, -15 — plus the 14 formerly PLAN-09's).
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at d385aa3, unchanged reason: no file under de.cuioss.http.client moved. Re-scoped by the merge: this plan now owns all 18, not 4 — the split ownership note is retired since there is no longer a PLAN-09 to own the other 14.
- HYPOTHESIS: deliverable 2 can be implemented without changing the public
  `CacheKeyHeaderFilter` API — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/CacheKeyHeaderFilter.java` § the
  public factory methods (verify-at-outline). If the credential binding requires a new public
  type or a filter-API change, that is a published-API change and needs an ADR.
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at d385aa3; unchanged in kind.
- Verify-first clause: before scoping deliverable 1, settle what `ResilientHttpAdapter` does with
  a `Failure` that no longer carries fallback content. Read
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ResilientHttpAdapter.java` §
  `decideNextStep` (lines 379-411). A refutation — the retry layer depends on fallback content
  being present on a 4xx — loops back and re-scopes deliverable 1.
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at d385aa3; unchanged in kind. ⛔ Now load-bearing for BOTH halves of this merged plan: `ResilientHttpAdapter.decideNextStep` is also deliverable 8's retry-classification site (former PLAN-09 claim), so the outline-time read settles one file for two deliverables at once.

**From the former PLAN-09 (redirects / retry / TLS / result invariants):**

- OBSERVED: `rebuildForHop` computes `preserveMethodAndBody = statusCode == 307 || 308` and
  `bodylessMethod = GET || HEAD`, then calls `builder.GET()` when
  `!preserveMethodAndBody && (statusCode == 303 || !bodylessMethod)` — read at
  `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java` § `rebuildForHop`
  (lines 783-804, the `GET()` call at 801).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: `forwardsCredentials` checks the scheme and then
  `isSameOrigin(from, to) || credentialForwarding == FORWARD_TO_ALLOWLISTED`, never reading
  `allowedHosts`; `HttpHandler` calls `refuse()` first at lines 696-702 — read at
  `cui-http-core/src/main/java/de/cuioss/http/client/handler/RedirectPolicy.java` §
  `forwardsCredentials` (lines 226-231).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: the TLS carve-out classifies any `SSLHandshakeException` as `CONFIGURATION_ERROR`
  (lines 150-154) while the adjacent Javadoc (lines 115-117) states the transient
  connection-reset-mid-handshake case stays retryable — read at
  `cui-http-core/src/main/java/de/cuioss/http/client/result/HttpErrorCategory.java` §
  `fromException` (lines 103-118, 150-156). The retryable decision that stops on
  `CONFIGURATION_ERROR` is at
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ResilientHttpAdapter.java` §
  `decideNextStep` (lines 403-411).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: the compact-constructor guard is `initialDelay.isNegative() || initialDelay.isZero()`
  at line 126, and `calculateDelay` multiplies `initialDelay.toMillis()` at lines 189-197 — read
  at `cui-http-core/src/main/java/de/cuioss/http/client/adapter/RetryConfig.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: `HttpResult.success(content, etag, httpStatus)` performs no status validation
  (lines 256-258); `Failure.getErrorMessage()` calls `Optional.of(errorMessage)` with no null
  check (lines 426-428); `failure()` accepts a null message (lines 278-283) — read at
  `cui-http-core/src/main/java/de/cuioss/http/client/result/HttpResult.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: `fromException`'s final line is
  `return unwrapped instanceof IOException ? NETWORK_ERROR : CONFIGURATION_ERROR;` (line 156) and
  the Javadoc sample catches `IOException | InterruptedException` and routes both through it
  (lines 131-139) — read at `HttpErrorCategory.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: the class Javadoc claims algorithm constraints and revocation posture still apply
  (lines 40-42) while the `SSLEngine` overload forwards `(SSLEngine) null` (lines 146-148) — read
  at
  `cui-http-core/src/main/java/de/cuioss/http/client/handler/HostnameVerificationRelaxingTrustManager.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: the HTTP-URI constructor hard-codes `sslContext = null` (line 392), replaces the
  provider (line 394) and forces `verifyHostname = true` (line 401) — read at `HttpHandler.java`
  (lines 388-414). The exact triple `http + allowInsecureHttp(true) + verifyHostname(false) +
  sslContext(ctx)` does NOT build (a mutual-exclusion check throws regardless of scheme); each
  pairwise combination does build and exhibits the silent discard independently.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: `CHARSET_PARAMETER` is the literal `"charset="` (line 52) matched by
  `regionMatches(true, 0, …)` (line 113), so `charset = ISO-8859-1` returns `Optional.empty()`
  and falls back to UTF-8 — read at
  `cui-http-core/src/main/java/de/cuioss/http/client/converter/StringContentConverter.java`
  (lines 52, 110-118).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: the TLS-floor assertion reads `handler.createHttpClient().sslParameters().getProtocols()`
  with no wire handshake, and the integration class's own comment names that shape insufficient —
  read at `cui-http-core/src/test/java/de/cuioss/http/client/handler/HttpHandlerTest.java` §
  `shouldPinEnabledTlsProtocolsOnHttpsClient` (lines 168-172) and
  `.../HttpHandlerHttpsIntegrationTest.java`:56.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: only three files in the client test tree use `TypedGenerator` — the two `HttpResult`
  generators and their sole consumer `HttpResultTest`, which drives them via hand-rolled loops
  rather than `@TypeGeneratorSource` — read at
  `cui-http-core/src/test/java/de/cuioss/http/client/result/HttpResultTest.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- OBSERVED: `L-12` in `01-security-validation.adoc` is superseded by `F-client-2` per that
  document's own consolidation table; this plan carries the single record.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged, carried forward from the redistributed PLAN-09.
- HYPOTHESIS: deliverable 6 changes behaviour integrators may depend on — a redirected `DELETE`
  that today silently succeeds as a `GET` will start behaving as a real `DELETE` against the
  redirect target. Confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/client/handler/HttpHandlerRedirectTest.java` § the
  301/302 method-rewrite tests (verify-at-outline). If a test pins the current rewrite as intended
  behaviour, the change needs an ADR.
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at d385aa3; unchanged in kind.
- Verify-first clause: before scoping deliverable 10, settle whether any production call site
  constructs `HttpResult.success` with a non-2xx/304 status. Read every `HttpResult.success(`
  call site under `cui-http-core/src/main/java`. A refutation — a legitimate internal caller
  depends on the loose contract — loops back and re-scopes deliverable 10 to a documentation fix.
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at d385aa3; unchanged in kind.

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapter.java` — `handleHttpResponse`, `buildAndExecute`, `prepareCacheContext`, `generateCacheKey`, `canReadCache`, `checkAndEvict`, `Builder`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/CacheKeyHeaderFilter.java` — the public factory methods and their Javadoc
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java` — the token-refresh sample at lines 115-133
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapterTest.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ResilientHttpAdapter.java` — `decideNextStep` (lines 379-411); this plan touches it directly for deliverable 8's retry classification, no longer a mere HYPOTHESIS since the merged plan owns both sides of that dependency
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java` — `rebuildForHop`, the HTTP-URI constructor, `requestBuilder`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/handler/RedirectPolicy.java` — `forwardsCredentials`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/handler/HostnameVerificationRelaxingTrustManager.java` — the class Javadoc and the `SSLEngine` overload
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/result/HttpErrorCategory.java` — `fromException` and its Javadoc sample
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/result/HttpResult.java` — `success`, `failure`, `Failure.getErrorMessage`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/RetryConfig.java` — the compact constructor and `calculateDelay`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/converter/StringContentConverter.java` — `parseDeclaredCharset`, `resolveCharset`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/HttpLogMessages.java` — the `HTTP-117` record comment (the doc-side claim is PLAN-13's)
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/handler/` — the whole handler test package
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/result/` — the whole result test package
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/converter/` — the converter test package
- HYPOTHESIS: `doc/adr/` — a new ADR, only if deliverable 1 lands as a breaking change, or deliverable 2 changes the published filter API (verify-at-outline)

⛔ This plan does **not** edit `doc/http-result-pattern.adoc`, `doc/LogMessages.adoc` or any other
file under `doc/`, except a new ADR recording its own decision. WS-06 PLAN-13 owns the prose set
and reconciles it to whatever this plan decides. Javadoc inside the `.java` files listed above IS
in scope. `F-client-5`'s and `F-client-11`'s prose halves are owned by WS-06 PLAN-13.
`F-client-6` (the non-compiling `package-info` samples) is owned by WS-06 PLAN-12, which also owns
the identical `F-documentation-2`.

## Dependencies and Sequencing

- Depends on: none. This is the head of WS-04 and may be emitted immediately — the dependency
  this merge existed to resolve (deliverable 10 tightening invariants on states deliverable 1
  restricts) is now internal task order, not a cross-plan sequencing constraint.
- Overlaps with: none remaining in the epic. `ResilientHttpAdapter.java` — the file the two
  merged source plans overlapped at — is now owned entirely by this one plan.
- Adjacent to: `de.cuioss.http.client.adapter`'s `ETagAwareHttpAdapter` and `CacheKeyHeaderFilter`
  (deliverables 1-5, 12) and `de.cuioss.http.client.handler` / `.result` / `.converter`
  (deliverables 6-11, 12) — both halves now sit in one plan by construction.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-08-etag-cache-principal-isolation.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction. ADR high-water
  as of this cleanup pass (`d385aa3`) is **0022** — re-check against `origin/main` regardless, per
  the rule above.
- Deliverable 1's regression test is the plan's centrepiece and must be written first: principal
  A populates the cache, principal B requests the same URI and receives a 403, and B's result
  must NOT carry A's body. Assert on the body content, not on the result type alone.
- No test this plan adds or rewrites may issue an outbound network request. Deliverable 12 exists
  precisely because the current `ETagAwareHttpAdapterTest` suite does, and the TLS-floor wire test
  it also covers uses an in-process TLS server, never a public host.
- Deliverables 1, 6, 9 and 10 are behaviour changes visible to integrators. Record each as such in
  the PR body and, if the project's release notes carry a migration section, there too.
- **Twelve deliverables is at the epic's raised ceiling (12), not over it.** The operator directed
  this redistribution explicitly: merge the two WS-04 plans rather than keep them as two
  dependency-linked PRs. The count is re-derived, not summed — deliverable 12 above already
  collapses what were two separate test-hardening deliverables in the source specs.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

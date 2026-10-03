# PLAN-08: Client Cache and Result Correctness

epic: quality-report-remediation
workstream: WS-03

## Objective

Close the cache-correctness hole that lets a 304 response to ANY method return a prior GET's cached
body as success — so a DELETE can appear to have succeeded and hand back a stale body — plus the
surrounding result-fidelity defects in `ETagAwareHttpAdapter`. The adapter's own fallback path
already gates on GET; the 304 success path is simply missing the same guard. Alongside it: a
typed-converter DELETE/PUT returning 204 No Content is reported as an `INVALID_CONTENT` failure even
though the operation succeeded; a conversion failure discards the HTTP status that was in hand; and
a 200 with no ETag leaves a stale entry in the cache so later failures serve outdated fallback.

## Deliverables

1. **Gate the 304 short-circuit by method**, with the three-way distinction the verification NOTE
   specifies: return cached content only for GET; return status plus ETag with NO body for HEAD
   (RFC 7232 permits a conformant 304 to a conditional HEAD, and a HEAD response carries no body);
   and treat 304 as a failure for POST/PUT/DELETE/PATCH, where RFC 7232 requires 412 instead. (CL-1)
2. **Exempt protocol-defined no-body statuses from the empty-content failure** — 204 and 205
   unconditionally. An empty 200 is NOT inherently a no-body status and stays governed by the
   existing converter-level `emptyContentIsValid()` opt-in. (CL-6)
3. **Preserve the HTTP status on a conversion failure** so callers can distinguish "200 but
   unparseable" from other invalid-content cases. (CL-13)
4. **Fix cache invalidation on an ETag-less 200** — a 200 with fresh content but no ETag currently
   leaves the old entry in place, so later failures serve outdated fallback and a stale
   `If-None-Match` keeps being sent. Also document the benign last-writer-wins race on the 304
   timestamp refresh, which the "fully thread-safe" claim does not mention. (CL-14)
5. **Reduce the unconditional cache-key allocation and the eviction cost** — the cache key string is
   built on every request including non-GET and caching-disabled adapters, and `checkAndEvict` does
   a full O(n log n) sort on each over-limit put. Acceptable at the default limit of 1000; address
   or document. (CL-19)
6. **Regression tests** for each, in particular the three-way 304 behaviour, which is the plan's
   load-bearing change.

Six deliverables — at the split guard. Proceeding unsplit: all six are in one class
(`ETagAwareHttpAdapter`) around one mechanism (the response-handling and cache paths); a split would
produce two plans that must serialize on the same file. Rationale recorded as an epic decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| CL-1 | HIGH | 304 short-circuit is not gated on GET — any method can return a prior GET's cached body as success |
| CL-6 | MEDIUM | Empty-body 2xx responses with a typed converter are reported as failure |
| CL-13 | LOW | Conversion-failure result discards HTTP status and ETag |
| CL-14 | LOW | Cache not invalidated on ETag-less 200; benign stale-overwrite race on 304 |
| CL-19 | INFO | Cache allocation and eviction cost |

## Claim Labels

- OBSERVED: these findings are stated at `source/http-client-review.adoc` § `CL-1`, `CL-6`, `CL-13`,
  `CL-14`, `CL-19`.
- OBSERVED: CL-1 carries a verification NOTE splitting it into two distinct cases and prescribing
  the three-way fix. The HEAD case is reachable against a FULLY CONFORMANT server — which is why the
  finding stays HIGH — while the POST/PUT/DELETE/PATCH case requires a non-conformant or hostile
  server. Read at `source/http-client-review.adoc` § `CL-1`.
- OBSERVED: CL-6 carries a verification NOTE scoping the fix to 204/205 only and recording that the
  practical impact is limited to TYPED converters — an identity `String` converter returns
  `Optional.of("")` and never hits the failure path. Read at `source/http-client-review.adoc` § `CL-6`.
- OBSERVED: the report records that the adapter's fallback path ALREADY gates on GET, so the
  correct guard exists in the same class. Read at `source/http-client-review.adoc` § `CL-1`.
- HYPOTHESIS: the 304 branch returns `HttpResult.success(cachedEntry.content(), …)` with only the
  timestamp refresh gated on `method == HttpMethod.GET` — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapter.java`
  § the 304 handling branch (verify-at-outline)
- HYPOTHESIS: `generateCacheKey` includes only URI and headers — not the method — and
  `prepareCacheContext` performs `cache.get(cacheKey)` for every method — confirm/refute at the same
  file § `generateCacheKey`, `prepareCacheContext` (verify-at-outline)
- HYPOTHESIS: the adapter sets `If-None-Match` itself only for GET, but caller-supplied request
  headers are applied for every method — the mechanism that makes the HEAD case reachable —
  confirm/refute at the same file § the request-building method (verify-at-outline)
- HYPOTHESIS: the empty-content failure branch tests
  `content.isEmpty() && isSuccess(statusCode) && !responseConverter.emptyContentIsValid()` with no
  status-code exemption — an asserted absence of the exemption — confirm/refute at the same file
  § the content-conversion branch (verify-at-outline)
- HYPOTHESIS: the cache-store branch requires `etag != null` before putting, so an ETag-less 200
  leaves the previous entry — confirm/refute at the same file § the cache-store branch
  (verify-at-outline)
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: deliverable 1 changes what the adapter returns for non-GET 304s from success
  to failure. Any consumer or test relying on the current permissive behaviour breaks. Check the
  test suite for such reliance before scoping; if found, it is evidence of the defect being
  depended upon, not a reason to leave it — but it must be surfaced, not silently rewritten.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapter.java` — the 304 branch, `generateCacheKey`, `prepareCacheContext`, the content-conversion branch, the cache-store branch, `checkAndEvict` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/result/HttpResult.java` — `failureWithFallback`, if deliverable 3 uses it (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/converter/` — `emptyContentIsValid`, if deliverable 2 touches the converter contract (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/client/adapter/` — adapter tests (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none within WS-03 — this is the workstream head.
- Overlaps with: PLAN-09 and PLAN-10 (all three edit the `adapter` package). Strictly sequential
  within WS-03.
- Adjacent to: `doc/http-result-pattern.adoc` (DOC-9, WS-05), which documents the `HttpResult`
  pattern this plan changes the failure semantics of. Do not edit it here; if deliverable 3 changes
  what a failure result carries, report it in the landing so PLAN-15 reconciles the doc.
- Adjacent to: `de.cuioss.http.security` (WS-01) — untouched; the client stack does not consume
  the validation pipelines.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-08-client-cache-and-result-correctness.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

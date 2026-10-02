# PLAN-09: Client Configuration and Retry Hardening

epic: quality-report-remediation
workstream: WS-03

## Objective

Make the client's configuration surfaces enforce the invariants they document, and stop the retry
classifier from treating a certificate-validation failure as a transient network error.
`RetryConfig` is a record with no compact constructor, so its public canonical constructor accepts a
negative initial delay and a jitter factor above 1.0 — both of which yield negative computed delays
and a hot retry loop — despite the record Javadoc stating the opposite contracts; all validation
lives only in the Builder. `SSLHandshakeException` is an `IOException`, so it is classified as
retryable `NETWORK_ERROR` and retried up to five times, wasting minutes and masking a configuration
error the enum's own documentation lists as non-retryable. Around these: builder reuse silently
ignores a new string URI, `host:port` strings are misparsed as carrying a scheme, and the blocking
convenience methods are not interrupt-responsive.

## Deliverables

1. **Add a compact constructor to `RetryConfig`** enforcing the invariants its Javadoc already
   states — `maxAttempts >= 1`, positive delays, jitter within 0.0–1.0 — so the public canonical
   constructor cannot produce a hot retry loop. (CL-7)
2. **Reclassify `SSLHandshakeException`** (and related non-transient TLS exceptions) as
   `CONFIGURATION_ERROR` rather than retryable `NETWORK_ERROR`, matching the enum's own documented
   placement of "SSL configuration issues". Keep `HttpTimeoutException`/`HttpConnectTimeoutException`
   retryable — those classifications are correct. (CL-9)
3. **Fix or document builder reuse** — `resolveUri()` mutates the builder field, so after one
   `build()` a subsequent string `uri(...)`/`url(...)` call on the same builder is ignored and the
   second handler points at the first URI. Nothing warns against builder reuse. (CL-2)
4. **Handle the `host:port` schemeless form** — `URL_SCHEME_PATTERN` matches `localhost` in
   `localhost:8080/api` as a scheme, so `https://` is not prepended and `build()` fails with
   "Unsupported URI scheme 'localhost'", contradicting the class doc's "schemeless string URLs
   default to HTTPS". This fails secure but is a correctness and documentation gap. (CL-3)
5. **Address `HttpClient` lifecycle and cancellation** — no deterministic resource release exists
   (each handler eagerly constructs an `HttpClient`, even when only `requestBuilder()` is used, and
   `HttpClient` is `AutoCloseable` on current JDKs), and `join()` ignores interrupts so
   `getBlocking()` over a default `ResilientHttpAdapter` can pin a thread for minutes
   uninterruptibly while cancelling the returned future cancels neither the in-flight call nor the
   scheduled retry chain. Fix or document each, including the undocumented unchecked
   `CompletionException`. (CL-11, CL-12)
6. **Regression tests**, in particular a test that the invalid `RetryConfig` shapes the report names
   are now rejected.

**Six deliverables — at the split guard.** Proceeding unsplit: deliverables 1, 2, 5 and 6 all
concern one behaviour (what the retry machinery does and how long it holds a thread) and 3–4 are two
adjacent `HttpHandler.Builder` corrections; both halves edit the `adapter` and `handler` packages
that a split could not separate. Rationale recorded as an epic decision.

⛔ **A seventh deliverable (a Sonar-findings sweep) was added to this spec on 2026-08-29 and then
REMOVED the same day. It never reached the running plan and was never this plan's work.** The
orchestrator wrote it at 18:26 while this plan's `request.md` had been written at 12:05, so
`phase-1-init` had already ingested the spec six hours earlier — the edit could not reach it. The
findings are now staged on PLAN-04. **Do not re-add them here, and do not read this plan's landing
as having closed any Sonar finding.**

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| CL-2 | MEDIUM | Builder reuse silently ignores a new string URI after the first `build()` |
| CL-3 | MEDIUM | `host:port` strings are misparsed as having a scheme |
| CL-7 | MEDIUM | `RetryConfig`'s public canonical constructor bypasses all validation |
| CL-9 | LOW | `SSLHandshakeException` is retried as a transient network error |
| CL-11 | LOW | No deterministic resource release for `HttpClient` |
| CL-12 | LOW | Blocking convenience methods are not interrupt-responsive; cancellation does not propagate |

## Claim Labels

- OBSERVED: these findings are stated at `source/http-client-review.adoc` § `CL-2`, `CL-3`, `CL-7`,
  `CL-9`, `CL-11`, `CL-12`.
- OBSERVED: the report records that CL-3 FAILS SECURE — it errors rather than silently connecting —
  so it is a correctness and documentation gap, not a security hole. Read at
  `source/http-client-review.adoc` § `CL-3`.
- OBSERVED: the report positively confirms the retry DESIGN is sound — non-blocking
  `delayedExecutor`, no stack growth, `CompletionException`/`ExecutionException` unwrapping with a
  cycle guard, jitter applied after capping. The defects here are the classifier and the constructor,
  not the design. Read at `source/http-client-review.adoc` § Verified correct / done well.
- HYPOTHESIS: `RetryConfig` is a record with no compact constructor and all validation lives in the
  Builder setters — an asserted absence plus a presence — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/RetryConfig.java`
  § the record header and the Builder (verify-at-outline)
- HYPOTHESIS: `HttpErrorCategory`'s classifier maps `IOException` subclasses to retryable
  `NETWORK_ERROR` with no `SSLHandshakeException` special case, while the enum's own `CONFIGURATION_ERROR`
  Javadoc lists "SSL configuration issues" — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/client/result/HttpErrorCategory.java`
  § the classification method and the `CONFIGURATION_ERROR` constant (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: RetryConfig still declares no compact constructor at f1ba539
- HYPOTHESIS: `HttpHandler`'s `resolveUri()` assigns to the builder's `uri` field — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java` § `resolveUri`
  (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: HttpErrorCategory contains no SSLHandshakeException handling; the asserted misclassification holds
- HYPOTHESIS: `URL_SCHEME_PATTERN` is `^[a-zA-Z][a-zA-Z0-9+.-]*:.*` — confirm/refute at the same
  file § `URL_SCHEME_PATTERN` (verify-at-outline)
- HYPOTHESIS: `HttpHandler` constructs its `HttpClient` eagerly and exposes no close/shutdown, and
  neither does `ETagAwareHttpAdapter` — an asserted absence — confirm/refute at the same file
  § the constructor and `createHttpClient`, and at `.../adapter/ETagAwareHttpAdapter.java`
  (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: URL_SCHEME_PATTERN still present and unchanged
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: deliverable 1 makes previously-constructible `RetryConfig` values throw.
  Deliverable 2 changes an SSL failure from retried to immediately-failed. Both are behaviour
  changes visible to consumers. Check the test suite and benchmark module for reliance before
  scoping; a benchmark or test constructing an invalid `RetryConfig` is a cross-module break that
  must be surfaced, and `cui-http-benchmarking` is WS-06's surface — escalate rather than edit it.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/RetryConfig.java` — the record header, the Builder setters (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/result/HttpErrorCategory.java` — the exception classification method (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java` — `resolveUri`, `URL_SCHEME_PATTERN`, `createHttpClient`, the builder, class Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/HttpAdapter.java` — the blocking convenience methods (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ResilientHttpAdapter.java` — the retry chain, cancellation propagation (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/client/handler/`, `cui-http-core/src/test/java/de/cuioss/http/client/adapter/`, and `cui-http-core/src/test/java/de/cuioss/http/client/result/HttpErrorCategoryTest.java` — handler, adapter, retry, and the CL-9 classification tests (verify-at-outline)
- ⛔ **NOT this plan's surface:** `client/result/HttpResultSuccessGenerator.java` and `client/result/HttpResultFailureGenerator.java`. PLAN-12 owns those two for TQ-23. This bullet previously declared the whole `client/` test tree, which made this plan falsely collide with PLAN-12 at the disjointness gate.

## Dependencies and Sequencing

- Depends on: PLAN-08 (WS-03 is strictly sequential; both edit the `adapter` package).
- Overlaps with: PLAN-08, PLAN-10.
- ✅ **Surface-disjoint from every sibling.** The deliverable-7 fold that briefly made this plan
  overlap PLAN-04, PLAN-07 and PLAN-12 has been removed; those collisions never existed in the
  running plan and do not exist in this spec.
- Adjacent to: `cui-http-benchmarking` (WS-06). If deliverable 1's compact constructor rejects a
  value the benchmark constructs, that is a cross-module compile break — surface it, do not fix it
  across the workstream boundary.
- Adjacent to: `doc/client-handlers-readme.adoc` (DOC-5, WS-05). Deliverables 3–5 change what
  `HttpHandler` documents about itself in Javadoc; the adoc is PLAN-14's. Report Javadoc changes in
  the landing so PLAN-14 can reconcile.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-09-client-config-and-retry-hardening.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

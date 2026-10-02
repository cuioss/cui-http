# PLAN-10: Client Converter and Javadoc Accuracy

epic: quality-report-remediation
workstream: WS-03

## Objective

Fix the client's charset and redirect behaviour and bring its Javadoc into agreement with the code.
`StringContentConverter` always decodes with its constructor charset, so a response declaring
`charset=ISO-8859-1` is mis-decoded and nothing documents that server-declared charsets are ignored.
`HttpStatusFamily`'s Javadoc says redirects are handled automatically by the JDK's `HttpClient`, but
both client construction paths omit `followRedirects` and the JDK default is `NEVER`, so every 3xx
surfaces as a non-retryable `INVALID_CONTENT`. `SecureSSLContextProvider`'s class-level summary
reads as if the class enforces the TLS floor by itself when the floor is only real once the context
is consumed through `HttpHandler`. Sequenced LAST in WS-03 so it documents what PLAN-08 and PLAN-09
actually landed.

## Deliverables

1. **Honour the response's declared charset in `StringContentConverter`**, or document that the
   constructor charset always wins. The no-arg `BodyHandlers.ofString()` honours the header's
   charset. (CL-5)
2. **Resolve the redirect contract** — either set an explicit redirect policy on both client
   construction paths, or correct the `HttpStatusFamily.toErrorCategory()` Javadoc and
   `adapter/package-info.java` to state that redirects are NOT followed and every 3xx surfaces to
   the adapter. ⛔ Setting a policy is a behaviour change and must be escalated, not decided
   silently. (CL-4)
3. **Correct `SecureSSLContextProvider`'s class-level Javadoc** so it does not read as enforcing the
   TLS floor by itself — the method-level docs are already accurate, and the floor is genuinely
   enforced downstream by `HttpHandler`'s `SSLParameters` pinning. Also resolve the dead, mildly
   contradictory public TLS-inspection API: `isSecureTlsVersion` and `FORBIDDEN_TLS_VERSIONS` have no
   main-code caller, and `isSecureTlsVersion("TLS")` answers `true` under a TLS 1.3 minimum although
   a generic-TLS context can negotiate 1.2. (CL-8, CL-10)
4. **Fix the `HttpHandler` exception-type and comment mismatches** — `build()` documents only
   `@throws IllegalArgumentException` while URI-to-URL conversion throws `IllegalStateException`;
   `pingHead()`/`pingGet()` docs say "returns the HTTP status code" but return an
   `HttpStatusFamily`; the internal comment claiming `URI.toURL()` is deprecated is wrong (the URL
   *constructors* are deprecated, `toURL()` is the recommended path); and the class example calls a
   `getSSLContext()` method that does not exist. (CL-15)
5. **Document the converter, responsibility, and API-completeness observations** — `VoidResponseConverter`
   sending `Accept: application/json` for status-only requests; `MULTIPART_FORM_DATA.toHeaderValue()`
   returning no boundary parameter, making it unusable as a real request Content-Type; the two
   competing health-check paths with no guidance on which to prefer; `ETagAwareHttpAdapter` doubling
   as the plain adapter despite its name; its ~15 verbatim re-implementations of interface default
   overloads; `createHttpClient()` not creating; and `HttpResult.Success.map` re-wrapping with the
   same ETag and status although the mapped value may no longer correspond to the ETagged
   representation. (CL-17, CL-18, CL-20)

Five deliverables — under the split guard.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| CL-4 | MEDIUM | Javadoc claims redirects are followed; they are not |
| CL-5 | MEDIUM | `StringContentConverter` ignores the response's declared charset |
| CL-8 | LOW (downgraded from MEDIUM in verification) | `SecureSSLContextProvider` class Javadoc overstates what the class alone enforces |
| CL-10 | LOW | Dead, mildly contradictory public TLS-inspection API |
| CL-15 | LOW | Exception-type and comment mismatches in `HttpHandler` |
| CL-17 | INFO | Content-type and converter quirks |
| CL-18 | INFO | Overlapping responsibilities and duplication |
| CL-20 | INFO | `HttpResult` API completeness |

**Not covered here:** CL-16 (non-compiling example in `security/monitoring/package-info.java`) is
the same defect as SV-15 and is owned by PLAN-04 in WS-01, per the report's own cross-reference.

## Claim Labels

- OBSERVED: these findings are stated at `source/http-client-review.adoc` § `CL-4`, `CL-5`, `CL-8`,
  `CL-10`, `CL-15`, and the CL-17/CL-18/CL-20 INFO entries.
- OBSERVED: CL-8 carries a verification NOTE downgrading it from MEDIUM to LOW — a Javadoc-precision
  issue, not a security hole, because the method-level Javadoc already documents the downstream
  enforcement and the actual enforcement is genuine. Read at `source/http-client-review.adoc` § `CL-8`.
- OBSERVED: CL-16 explicitly states it is tracked in full under SV-15 and is only noted in the
  client report because it surfaced there too. This is the report's own cross-reference, not an
  orchestrator inference. Read at `source/http-client-review.adoc` § `CL-16`.
- OBSERVED: CL-20 explicitly records that there is NO defect — `map` semantics are consistent and
  the docs never claim monad laws. The deliverable is documentation of the ETag re-wrap only. Read
  at `source/http-client-review.adoc` § `CL-20`.
- OBSERVED: the report positively confirms the TLS floor IS genuinely enforced on the wire through
  `HttpHandler`'s `SSLParameters` pinning on both HTTPS paths, that hostname verification is never
  disabled, and that no trust-all `X509TrustManager` exists (grep-verified). CL-8 must not be
  "fixed" in a way that contradicts this. Read at `source/http-client-review.adoc` § Verified correct.
- HYPOTHESIS: `StringContentConverter` calls `HttpResponse.BodyHandlers.ofString(charset)` with its
  constructor charset — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/client/converter/StringContentConverter.java`
  § the body-handler method (verify-at-outline)
- HYPOTHESIS: neither `HttpClient` construction path in `HttpHandler` calls `followRedirects(...)` —
  an asserted absence, verified as a presence — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java` § both client
  construction sites (verify-at-outline)
  - verdict: corroborated | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 6ee0c7c after PLAN-09 shipped the client package: StringContentConverter.java:66 still returns HttpResponse.BodyHandlers.ofString(charset) with the constructor charset
- HYPOTHESIS: `isSecureTlsVersion` and `FORBIDDEN_TLS_VERSIONS` have no main-code caller — an
  asserted absence — confirm/refute by grepping `cui-http-core/src/main/java/` for both symbols
  (verify-at-outline)
  - verdict: corroborated | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 6ee0c7c: no followRedirects call exists anywhere under client/ - the asserted absence still holds despite PLAN-09 reworking HttpHandler client construction
- HYPOTHESIS: `getOrCreateSecureSSLContext` returns a caller-supplied context unchanged — confirm/refute
  at `cui-http-core/src/main/java/de/cuioss/http/client/handler/SecureSSLContextProvider.java`
  § `getOrCreateSecureSSLContext` (verify-at-outline)
  - verdict: corroborated | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 6ee0c7c: isSecureTlsVersion is still referenced by exactly one file, its own declaration in SecureSSLContextProvider.java, so it remains dead in main code
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: ⛔ this plan is sequenced LAST in WS-03 because PLAN-08 and PLAN-09 change
  behaviour it documents. Deliverable 4's `build()` `@throws` list depends on what PLAN-09 landed
  for CL-2/CL-3, and deliverable 5's `HttpResult` observations depend on PLAN-08's CL-13 change.
  Read what those two plans landed before scoping.
- Verify-first clause: deliverable 2's "set an explicit redirect policy" route is a behaviour change
  to every request the library makes. It is a genuine fork — escalate to the operator rather than
  deciding it in-plan.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/converter/StringContentConverter.java`, `VoidResponseConverter.java`, `ContentType.java` — charset handling, converter Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpStatusFamily.java` — `toErrorCategory` Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java` — client construction, `build()` `@throws`, `pingHead`/`pingGet` docs, the `toURL()` comment, the class example (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/handler/SecureSSLContextProvider.java` — class Javadoc, `isSecureTlsVersion`, `FORBIDDEN_TLS_VERSIONS` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java`, `ETagAwareHttpAdapter.java`, `HttpAdapter.java` — redirect claim, responsibility documentation (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/result/HttpResult.java` — `map` Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/client/converter/` and `cui-http-core/src/test/java/de/cuioss/http/client/handler/` — converter and handler tests (verify-at-outline)
- ⛔ **No, not this plan's surface:** `cui-http-core/src/test/java/de/cuioss/http/client/adapter/ResilientHttpAdapterTest.java` — **PLAN-13 owns it** for the lower-bound timing assertion. This bullet previously declared the whole `client/` test tree, which made this plan collide with PLAN-13 — invisibly, because a directory declaration and a file declaration never match under the cross-check's exact-path comparison.
- CONFIRMED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapter.java` — the always-true expression (`java:S2589`), **moved here from PLAN-04 on 2026-08-30** because this plan already declares the file. ⛔ **Locate it by CONDITION, never by the scan's line 539** — PLAN-09 rewrote this file at `6ee0c7c`.

## Dependencies and Sequencing

- Depends on: PLAN-08 and PLAN-09 — both for the shared surface and because deliverables 4 and 5
  document what they landed.
- Overlaps with: PLAN-08, PLAN-09 — WS-03 is strictly sequential.
- Adjacent to: `cui-http-core/src/main/java/de/cuioss/http/security/monitoring/package-info.java` —
  CL-16 points there. ⛔ Do NOT edit it; PLAN-04 owns it.
- Adjacent to: `doc/client-handlers-readme.adoc` (DOC-5) and `doc/http-result-pattern.adoc` (DOC-9),
  both WS-05's. This plan changes Javadoc those documents mirror; report the changes in the landing.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-10-client-converter-and-javadoc-accuracy.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

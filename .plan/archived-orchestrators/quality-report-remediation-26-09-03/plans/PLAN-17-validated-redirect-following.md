# PLAN-17: Validated Redirect Following

epic: quality-report-remediation
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no brief.

## Objective

Implement redirect following that revalidates every hop, closing the capability gap PLAN-10 opened
when it reverted `HttpClient.Redirect.NORMAL`. At HEAD the client never follows a redirect (the JDK
default `Redirect.NEVER` applies and every 3xx surfaces to the caller), which is fail-secure but
leaves callers to implement redirect handling themselves — and PLAN-10's shipped Javadoc points
forward to this work in five places. The design is **already settled by operator decision**:
same-origin by default (scheme + host + port), with an opt-in host allowlist for callers who
legitimately need cross-host redirects (CDN hand-offs, auth flows, pre-signed storage URLs).

⛔ **This plan exists because following redirects without revalidation is an SSRF-shaped egress hole
in a security library.** That is not a hypothesis — it was CodeRabbit's Merge Risk: High finding on
PR #182, confirmed against the code and acted on by reverting. Nothing here may reintroduce
unvalidated following, not even temporarily behind a flag.

## Deliverables

1. **A hop validator** deciding whether a redirect destination may be followed: same-origin
   (scheme + host + port) by default, plus an opt-in host allowlist configured through the
   builder. It preserves the existing HTTPS→HTTP downgrade refusal and the builder's scheme
   policy — any scheme other than `http`/`https` stays rejected.
2. **A bounded redirect loop** applying that validator to every hop, at the placement chosen by
   deliverable 3's decision. Bounded means an explicit maximum hop count with a named failure when
   exceeded; an unbounded loop is a redirect-loop DoS.
3. **The loop-placement decision, settled at outline and recorded**, plus its consequence for
   whichever send path is not covered directly. See the verify-first clause below.
4. **Configuration surface** — the allowlist opt-in on `HttpHandlerBuilder`, defaulting to empty so
   the out-of-the-box behaviour is same-origin-only.
5. **Tests** covering: same-origin followed; cross-host refused; cross-port refused; cross-scheme
   refused; an allowlisted host followed; HTTPS→HTTP refused even when the host is allowlisted; the
   hop bound enforced.
6. **Replace the forward-pointing Javadoc PLAN-10 left** in `HttpHandler`, `HttpStatusFamily` and
   `adapter/package-info` with the shipped behaviour. ⛔ Those passages currently describe the
   no-follow baseline as provisional; leaving them would make the shipped docs false in the same way
   PLAN-10 was fixing.

Six deliverables — at the split guard. Proceeding unsplit: deliverables 1–4 are one mechanism that
cannot ship in pieces (a validator with no loop follows nothing; a loop with no validator is the
hole this plan exists to avoid), 5 is their test, and 6 is the doc-truth obligation that must land in
the same PR or the Javadoc contradicts the code.

## Claim Labels

- OBSERVED: The client follows no redirect at HEAD — no `Redirect.NORMAL` survives anywhere under
  `client/`; the only `Redirect.` tokens are five comments and Javadoc passages documenting the
  deliberate JDK default. Read at `HttpHandler.java`:289, :324, `HttpStatusFamily.java`:234, :254,
  `adapter/package-info.java`:249.
- OBSERVED: No `de.cuioss.http.security` pipeline is configured over any client destination. The
  security pipelines are an **inbound** request-validation surface; the client is an **outbound**
  one, and the client `package-info` states the split explicitly. ⛔ **Do not scope this plan's
  validation as "route the redirect target through the configured pipeline"** — that instruction was
  drafted during PLAN-10 and caught at dispatch review as wrong on two independent axes: the
  mechanism is not wired in, AND a path-pattern matcher does not answer an egress host-policy
  question. Recorded as lesson `2026-09-01-09-002`.
- OBSERVED: There are exactly **two** send sites in the client main tree —
  `HttpHandler.java`:423 (`client.send`, in a ping method, `HttpResponse<Void>` / discarding) and
  `ETagAwareHttpAdapter.java`:691 (`httpClient.sendAsync`, the path that carries application
  traffic). `ResilientHttpAdapter` does not send directly; it wraps.
- OBSERVED: `HttpHandler` owns `HttpClient` construction and carries the scheme policy
  (`URL_SCHEME_PATTERN` at `HttpHandler.java`:187 and the builder's scheme rejection at :134).
- HYPOTHESIS: The allowlist belongs on `HttpHandlerBuilder` rather than on the adapters, because the
  builder already owns the scheme policy the validator must preserve — confirm/refute at
  `HttpHandler.java` § `HttpHandlerBuilder` (verify-at-outline).
- Verify-first clause: ⛔ **Where the redirect loop lives is an OPEN DESIGN QUESTION and MUST be
  settled at outline against the two send sites above — never assumed.** `HttpHandler` owns the
  client but does not send application requests; the adapters do. **A loop placed only in
  `HttpHandler`'s ping methods would leave `ETagAwareHttpAdapter.java`:691 unprotected and would be
  a false fix** — it would make the docs claim validated following while the traffic that matters
  bypassed it. Settle by reading both send sites and naming which one each candidate placement
  covers. Refutation of the chosen placement loops back and re-scopes.

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpHandler.java` — client construction, the scheme policy, the ping send site at :423, the builder
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/handler/HttpStatusFamily.java` — the redirect Javadoc at :234 and :254
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ETagAwareHttpAdapter.java` — the application send site at :691
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java` — the redirect passage at :249
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/HttpAdapter.java` — only if the loop is placed at the adapter seam (verify-at-outline, gated on the deliverable-3 decision)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/client/handler/package-info.java` — the outbound/inbound split statement, if the shipped behaviour changes it (verify-at-outline)
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/handler/`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/dispatcher/` — `RedirectDispatcher` already exists here, added by PLAN-10, and is the natural test vehicle
- ⛔ **No, not this plan's surface:** `cui-http-core/src/main/java/de/cuioss/http/client/converter/` and `cui-http-core/src/main/java/de/cuioss/http/client/result/` — no redirect concern lives there.

## Dependencies and Sequencing

- Depends on: PLAN-10 (shipped, PR #182) — this plan replaces the forward-pointing Javadoc it left.
- Overlaps with: none staged. PLAN-18 is `security/` only and PLAN-19 is `doc/adr/` only, so all
  three are pairwise disjoint and may run concurrently.
- Adjacent to: `doc/client-handlers-readme.adoc` (DOC-5) and `doc/http-result-pattern.adoc` (DOC-9).
  ⛔ **Do NOT edit them** — WS-05 is closed and both already carry an unowned re-check debt from
  PLAN-08 and PLAN-10. Report every redirect-description change in the landing so the debt is
  recorded against a known state rather than growing silently.
- Adjacent to: `@EnableMockWebServer(useHttps = true)` is **broken project-wide** by a pre-existing
  `mockwebserver3`/okhttp mismatch (`Platform.configureTlsExtensions` no longer exists) — lesson
  `2026-08-29-12-001`, which has now blocked TLS coverage in two plans. ⛔ **The HTTPS→HTTP downgrade
  test in deliverable 5 will hit this.** Cover the intent at the validation seam and document the
  gap in the test class, as PLAN-10 did; do not spend the plan trying to fix the harness.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-17-validated-redirect-following.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

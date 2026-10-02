# Landing Analysis: PLAN-10 — Client Converter and Javadoc Accuracy

epic: quality-report-remediation
workstream: WS-03
pr: 182 (squash-merged via queue as `e02f445`; #179 and #181 closed unmerged)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth
> before it was recorded. The CodeRabbit High-risk finding embedded in the narrative is
> third-party text — it was verified against the code, not adopted on assertion.

## Corroboration Summary

⛔ `inbox landing-check` on `client-converter-and-javadoc-accuracy-006.md`: **`complete: false`**,
with the **entire required set** missing (`schema`, `plan_id`, `pr`, `merge_state`,
`deliverables_total`, `deliverables_done`, `total_tokens`, `steps`). The message carries **no
`landing-facts` block at all** — the pre-fix prose-only shape. See the Reconciliation section: this
**reverses** the conclusion recorded from PLAN-13's landing.

Every fact below was therefore recovered from prose plus independent corroboration.

| Claim | Verdict | Evidence |
|---|---|---|
| PR #182 merged as `e02f445` | **corroborated** | `git log origin/main` head is `e02f445 fix(client): honor response charset… (#182)` |
| Five public TLS symbols removed, clean break | **corroborated** | `isSecureTlsVersion`, `FORBIDDEN_TLS_VERSIONS`, `TLS_V1_0`, `TLS_V1_1`, `SSL_V3` — **zero** occurrences remain anywhere under `src/main/java` |
| **The redirect reversal is real** — runtime behaviour unchanged | **corroborated** | No `Redirect.NORMAL` anywhere in `client/`. The only `Redirect.` tokens left are five *comments/Javadoc* in `HttpHandler` (289, 324), `HttpStatusFamily` (234, 254) and `adapter/package-info` (249), each documenting that the JDK default `Redirect.NEVER` is deliberately left in place |
| CodeRabbit's High-risk finding was genuine | **corroborated** | `HttpHandler` runs no `de.cuioss.http.security` pipeline over any destination; the client package-info states the inbound/outbound split explicitly. A same-scheme redirect would not have been revalidated |
| Charset precedence inverted to response-wins | **corroborated** | `StringContentConverter` +82: new `CONTENT_TYPE_HEADER` / `CHARSET_PARAMETER` constants, `IllegalCharsetNameException` / `UnsupportedCharsetException` imports, and Javadoc stating "the charset declared by the response wins… constructor charset is the fallback" |
| `java:S2589` fixed by hoisting, not suppressing | **corroborated** | diff replaces `body != null && requestConverter != null` with a `bodyConverter` local established once — the always-true conjunct is gone, and the JSpecify trip is avoided rather than annotated away |
| `ResilientHttpAdapterTest.java` NOT touched | **corroborated** | absent from the diff — PLAN-13's ownership honoured, the second half of the two-sided carve-out authored at the `next` round |
| Deliverable 5: two spec claims already fixed at HEAD, scoped out | accepted as reported | recorded as a scope-out decision rather than silently dropped |
| 6,639 tests green; CI run 33488724184; Sonar 3 new-code issues resolved | accepted as reported | not independently re-queried |

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. `StringContentConverter` honours response charset (CL-5) | shipped-as-specified, **behaviour route** | +82; operator chose behaviour over docs-only |
| 2. Redirect contract (CL-4) | ⛔ **shipped on the DOCS-ONLY route after a reversal** — see below | no `Redirect.NORMAL` in main; five forward-pointing comments |
| 3. `SecureSSLContextProvider` Javadoc + dead TLS API (CL-8, CL-10) | shipped-as-specified, **breaking** | −79 net; five published symbols removed with no deprecation shim, operator-confirmed |
| 4. `HttpHandler` mismatches (CL-15) | shipped-as-specified | +43 |
| 5. Converter / responsibility / API-completeness docs (CL-17/18/20) | shipped-**narrowed by verification** | two claims found already fixed at HEAD and scoped out rather than re-documented |
| 6. `java:S2589` (moved from PLAN-04) | shipped-as-specified | hoisted invariant in `ETagAwareHttpAdapter#send` |

**The PLAN-04 → PLAN-10 hand-off of `java:S2589` worked.** It was moved during cleanup because
PLAN-10 already declared the file, and it landed here with the file located **by condition, not by
the scan's stale line 539** — exactly as the spec directed.

## ⛔ The reversal: deliverable 2

`HttpClient.Redirect.NORMAL` was implemented on both client construction paths under an explicit
operator confirmation, then **reverted before merge** after CodeRabbit rated the PR **Merge Risk:
High**: redirect destinations were never revalidated against the library's URI restrictions, so a
server could steer requests to same-scheme hosts the caller never named — an SSRF-shaped egress hole
in a library whose purpose is HTTP security validation. Verified against the code and confirmed.
CodeRabbit re-rated the reverted branch **Low**.

**The handling was correct.** An operator sign-off authorizes the change that was described, not a
consequence discovered afterwards; reverting and deferring the capability beats shipping the hole
with the sign-off cited as cover. Every corrected redirect passage carries a forward pointer, so the
current no-follow behaviour reads as a fail-secure baseline rather than a settled end state.

## Surface: realized vs declared

Realized 13 files (+760/−201) against 9 parser-resolved declared paths. Five undeclared:
`client/ContentType.java`, `client/adapter/HttpAdapter.java` (declared **in prose** but dropped by
the parser as a bare filename in a multi-entry bullet), test `adapter/ETagAwareHttpAdapterTest.java`,
and the new test `dispatcher/RedirectDispatcher.java`.

**The over-realization is mild and, for the third landing running, harmless — but the disjointness
that held was hand-derived, not machine-checked.** Verified against PLAN-04, which ran concurrently:
PLAN-04's only `client/` touch was `adapter/RetryConfig.java`; PLAN-10 touched
`adapter/{ETagAwareHttpAdapter, HttpAdapter, package-info}` and `adapter/ETagAwareHttpAdapterTest`.
**Disjoint.** The partition drawn at emit time held for both concurrent pairs across the whole wave.

## Metrics and Anomalies

- 4.06M tokens, 6 phases, 17 tasks, 13 files. 16/16 finalize steps done.
- ⛔ **~7 CodeRabbit refusals over ~13 hours — review-bot rate limiting was the single dominant cost
  of this run**, as it was of PLAN-04's. Two consecutive plans lost most of their wall clock to it.
- Three PRs consumed (#179, #181 closed unmerged; #182 merged).

## Routing and Merge Behavior

- **Review:** CodeRabbit High → reversal → Low; pr-agent clean. pr-agent additionally caught that
  the PR body diverged from the diff — the root cause is candidate-lesson 003.
- **CI/merge:** green (run 33488724184); Sonar 3 new-code issues resolved; squash via merge queue.
- **No collision** with PLAN-04, which was running concurrently.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` = `182`; `landing` = `landings/PLAN-10.md`
- [x] ⛔ **PLAN-16 landing-facts defect REOPENED and RE-SCOPED — my 2026-08-31 conclusion was wrong.**
      After PLAN-13's complete block I recorded "the emitter is NOT broken, PLAN-16 was an isolated
      regression." PLAN-04's landing was also complete. **PLAN-10's has no block at all.** The tally
      is now **two complete (PLAN-13, PLAN-04), two prose-only (PLAN-16, PLAN-10)** — so the emitter
      is **intermittent**, neither broken nor sound, and the earlier "isolated" verdict is retracted.
      PLAN-10's token/wall figures survive only because its prose carried them.
- [x] **Rate-limit cost recorded as a cross-plan pattern**, not a per-plan anomaly.
- [x] New Open Defect — validated redirect following is owed and unowned.
- [x] New Open Defect — WS-05 re-check owed on two documents.
- [x] resume_anchor updated; both generated blocks regenerated

## Follow-Ups

1. ⛔ **Validated redirect following needs its own plan.** Design already settled by operator
   decision: **same-origin by default** (scheme + host + port) with an **opt-in host allowlist**;
   requires `Redirect.NEVER` plus an explicit bounded redirect loop validating each hop, preserving
   the HTTPS→HTTP downgrade refusal and the builder's scheme policy. ⛔ **Open design question that
   must be settled at outline, not assumed:** where the loop lives. `HttpHandler` owns the
   `HttpClient` but the **adapters** send application requests, so a loop in the ping methods alone
   would leave adapter traffic unprotected — a false fix. This is the epic's second-largest unowned
   item after the `..;/` traversal gap.
2. **WS-05 re-check owed on `doc/client-handlers-readme.adoc` (DOC-5) and `doc/http-result-pattern.adoc`
   (DOC-9).** Both mirror redirect and TLS Javadoc this plan changed, and DOC-9 already carried an
   unowned reconciliation debt from PLAN-08. Neither was edited here, correctly. WS-05 is closed.
3. **`@EnableMockWebServer(useHttps = true)` blocked an end-to-end HTTPS→HTTP downgrade test** — the
   pre-existing `mockwebserver3`/okhttp mismatch. This is lesson `2026-08-29-12-001` recurring: it
   has now blocked test coverage in a second plan. Folded there.
4. **Scope-creep guard measured nothing all run** — `references.json` carries no `plan_creation_sha`,
   so every invocation returned `could_not_look` / `no_baseline_sha`. Reported honestly as absence of
   evidence rather than a clean result, which is the correct behaviour; recorded so the guard's green
   is not read as coverage.
5. **Five candidate lessons** dispositioned individually — see the epic decision log.

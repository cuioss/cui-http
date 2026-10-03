# Landing Analysis: PLAN-08 — HTTP Client Correctness (ETag Cache Principal Isolation, Redirects, Retry, TLS, Result Invariants)

epic: quality-report-remediation
workstream: WS-04
pr: #240

> Landing record for one shipped plan. Lives at `landings/PLAN-08.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

## Deliverable Fidelity vs Spec

Spec declared 12 numbered deliverables (the WS-04 merge of the former PLAN-08 + PLAN-09). Landing
reports **13** — ground truth: the 13th is ADR-0023 authored as its own explicit deliverable during
outline/execution, documenting the credential-binding decision the merged spec's deliverable 2
required but did not itself number as a separate ADR-writing task. Legitimate scope refinement, not
a contradiction — the PR body's own summary independently says "twelve corroborated
protocol-correctness defects" for the code fixes, consistent with 12 code deliverables + 1 doc
deliverable = 13.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Fallback content restricted to availability failures | shipped-as-specified | PR body: "cached-fallback-content is now served only on transport/5xx availability failures, never on 401/403/404" |
| 2. Credential-bearing headers as cache-entry identity | shipped-as-specified, **with an in-run security fix** | See Reconciliation note below — the original implementation had its own defect, found and fixed inside this same run |
| 3. Cache-key hygiene and bounds (hash + TTL) | shipped-as-specified | PR body: "hashed, credential-bearing key components... a time-based TTL was added alongside the existing size-triggered LRU" |
| 4. HEAD conditional requests | shipped-as-specified | PR body: "HEAD requests are now either fully conditional or excluded from `canReadCache`" |
| 5. Token-refresh guidance rewritten | shipped-as-specified | PR body: "the four Javadoc/sample sites that taught the unsafe `excluding(\"Authorization\")` pattern were rewritten" |
| 6. Redirect method preservation | shipped-as-specified, breaking | PR body: `DELETE`/`PUT`/`PATCH`/`OPTIONS` now preserve method across 301/302 redirects |
| 7. Credential forwarding honours allow-list | shipped-as-specified | PR body: `RedirectPolicy.forwardsCredentials` "now consults `allowedHosts` directly" |
| 8. Transient-handshake retry classification | shipped-as-specified | PR body: `SSLHandshakeException` "reclassified per its own documented rationale" |
| 9. RetryConfig zero-delay rejection | shipped-as-specified, breaking | PR body: "`initialDelay` must now be at least 1ms" |
| 10. Result-type invariants + HttpErrorCategory | shipped-as-specified, breaking | PR body: `HttpResult.success` enforces 200/304-only; `InterruptedException` classified correctly |
| 11. TLS scope honesty, cleartext constructor, charset parsing | shipped-as-specified, **plus an in-run CodeRabbit-caught gap** | PR body covers all three named fixes; see lesson `2026-09-16-15-007` for the caller-supplied provenance-flag gap CodeRabbit found in the cleartext-constructor fix itself |
| 12. Client test suite hardening | shipped-as-specified | `ETagAwareHttpAdapterTest` moved to MockWebServer; timeout/malformed/5xx-exhaustion/TLS-floor coverage added; `TypedGenerator` extended |
| 13. ADR-0023 (added during execution) | shipped, added-not-in-original-spec | `doc/adr/0023-A_cache_entry_is_bound_to_the_credential_material_that_produced_it.adoc` in realized footprint |

**Surface delta — 6 undeclared files, one of them a genuine spec-boundary violation.**
`git diff --name-only` against the plan's true merge base (`c680a3f`, i.e. after PLAN-16 landed)
lists 27 realized files against the spec's ~17 declared entries. Five of the six undeclared files
are unremarkable adjacent-scope additions (`client/package-info.java` at the top level rather than
just `adapter/package-info.java`; three new test files — `CacheKeyHeaderFilterTest.java`,
`ResilientHttpAdapterTest.java`, `RetryConfigTest.java` — inside the already-declared `adapter/`
test directory; and a new `test/.../client/dispatcher/RedirectDispatcher.java` supporting the
method-preservation tests). **The sixth pair is a real boundary violation**:
`doc/client-handlers-readme.adoc` and `doc/http-result-pattern.adoc` were both edited (6 lines
total, confirmed substantive via `git diff --stat`) — the spec's own Write-Boundary explicitly named
both as off-limits ("This plan does NOT edit `doc/http-result-pattern.adoc` or any other file under
`doc/`, except a new ADR"), and the PR's own "Explicit non-goals" section independently claims "does
not touch prose documentation under `doc/` other than the one new ADR" — a claim the PR's own diff
contradicts. No collision resulted (PLAN-13, the doc-owning plan, has not run), but **PLAN-13 must
re-read both files at its own outline rather than trusting the original quality-report's
description of them** — they have already been corrected once. Recorded as an Open Defect, not
folded into the routine under-declaration Watch, because it is a stated boundary the plan crossed
rather than an ordinary adjacent-file miss.

## Metrics and Anomalies

- Tokens: 12,972,739 total (landing-facts, corroborated against `metrics.md`'s identical figure).
- Duration: 7h32m wall (landing-facts `total_wall_seconds=27169`, corroborated against `metrics.md`);
  2h7m worked of the phases measured (n=4/6).
- Anomalies: **in-run security fix** — the finalize security-audit step found and fixed a real
  delimiter-forgery cache-key collision in the plan's own deliverable-2 implementation (see lesson
  `2026-09-16-15-001`); **CodeRabbit review round produced 8 fix tasks** landed and re-verified,
  including two more security/correctness-adjacent findings (builder provenance-flag gap, lesson
  `2026-09-16-15-007`; vacuous cross-principal test, lesson `2026-09-16-15-008`) and a documentation
  accuracy finding on the new ADR itself (lesson `2026-09-16-15-010`); **Sonar false-positive
  re-scan** — 3 issues re-reported byte-for-byte identical after a verified fix, correctly rejected
  as stale rather than driving a redundant loop-back (lesson `2026-09-16-15-002`); **adr-propose
  finalize step skipped out of manifest order** — see Routing note below.

## Routing and Merge Behavior

- Review: CodeRabbit produced 8 actionable fix tasks (TASK-25 through TASK-32 per the residue/lesson
  messages), all landed and re-verified before merge; `cuioss-review-bot` participated per the
  landing message's residue framing (inherited convention from prior landings — not independently
  re-verified here since `ci checks status` does not enumerate per-bot participation beyond pass/fail
  status). `ci checks status --pr-number 240`: `overall_status: success`, 25 checks.
- CI/merge: `ci pr view --pr-number 240` confirms `state: merged`,
  `merge_commit_sha: 2e9e8e0fa8b6dc7058205d3be973c55dafbe0e7f`, present on `origin/main`. No rebase
  conflicts reported. `cleanup_owed: false`.
- **Process fault, operator-resolved**: `adr-propose` was dispatched after `branch-cleanup` instead
  of before, per the manifest's declared order. Disclosed by the executing session; the operator
  independently confirmed skipping the step was acceptable, since ADR-0023 (the plan's sole
  architectural decision) was already authored as an explicit execution-time deliverable rather than
  deferred to that finalize step. A post-hoc Signal Gate re-check confirmed the step's own trigger
  condition (`compatibility: breaking`, non-empty decision log) WOULD have fired — this is recorded
  as a genuine process deviation, not one that happened to be harmless by luck.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-08 --status shipped`
- [x] row `pr` stamped → `#240`
- [x] row `landing` stamped → `landings/PLAN-08.md`
- [x] row `plan_marshall_plan_id` stamped → `etag-cache-principal-isolation`
- [x] epic.md Ordered Queue/START-HERE regenerated
- [x] Open Defect added: the `doc/client-handlers-readme.adoc` / `doc/http-result-pattern.adoc`
      boundary violation, with the explicit note for PLAN-13 to re-read both at its own outline
- [x] Watches: recurrence added to the surface-under-declaration watch (6 undeclared files, mixed:
      5 unremarkable + 1 boundary violation)
- [x] resume_anchor updated
- [x] 12 candidate-lesson + 1 landing inbox message drained: 10 new lessons promoted
      (`2026-09-16-15-001` through `-010`), 1 recurrence folded into `2026-09-15-19-003` (now a
      3-plan, 8-notation pattern), 1 discarded (informational, no corrective needed)
- [x] this landing message drained; `landing-check` reported `complete: true`

## Follow-Ups

- **`2026-09-15-19-003` (argparse router-vs-subcommand flag position) has now recurred in THREE
  consecutive plans across EIGHT `manage-*`/router notations, confirmed failing in both
  directions.** This has crossed from "worth a lesson" to "worth a mechanism" — flagging explicitly
  as a priority candidate for whatever surfaces from `/plan-orchestrator lessons`, and as a
  candidate for a structural fix (e.g. accepting the flag in either position on ambiguous surfaces,
  per the lesson's own corrective) rather than continued documentation-only remediation.
- **PLAN-13 must re-read `doc/client-handlers-readme.adoc` and `doc/http-result-pattern.adoc` at its
  own outline** — both were corrected by this plan outside its declared boundary; PLAN-13's original
  quality-report-derived premises about their content may already be stale.
- Three new security/correctness-adjacent lessons from this single plan (`2026-09-16-15-001`,
  `-007`, `-008`) — all caught in-run (security audit or CodeRabbit), none by the test suite or
  pre-existing gates. Worth a standing note that this plan's own deliverable-2 fix (principal
  isolation) needed its own security audit to catch a defect in the fix itself — a fix for a
  security defect is itself an unreviewed change, matching the theme of the epic's existing lesson
  `2026-09-06-14-002`.

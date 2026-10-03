# Landing Analysis: PLAN-16 — Benchmark and CI Hygiene

epic: quality-report-remediation
workstream: WS-06
pr: #170 — https://github.com/cuioss/cui-http/pull/170 (merged, `9f26433`)

> Landing record for one shipped plan. Written by the `analyze` verb after verifying claims
> against ground truth. A pasted claim is a lead, never a fact.

## Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #170 merged | corroborated | `ci pr view --pr-number 170` → `state: merged`, branch `feature/plan-16-benchmark-and-ci-hygiene` |
| merge commit `9f26433ae913…` | corroborated | matches `pr_view.merge_commit_sha` and `git log` HEAD |
| surface is workflows + benchmarking + poms + `CLAUDE.md` + `testing.adoc` + `marshal.json` | corroborated | `git show --name-only 9f26433` matches the landing's own "Surface touched" list exactly |
| ⛔ `files_changed: 13` | **contradicted** | `git show --stat 9f26433` → **12** files. The landing's OWN surface list also enumerates 12. Off by one against both git and itself. Immaterial to the outcome; recorded because an uncorroborated count in a landing is exactly what this step exists to catch. |
| `java:S1845` BLOCKER at `ForwardedHeaderResolver.java:646` | corroborated | line 646 declares `private static final ForwardedResult UNRESOLVABLE`; the record accessor `unresolvable()` is used at lines 323/501/508. Names differ only by case — a real S1845. |
| `java:S1905` unnecessary cast at `RetryConfig.java:163` | corroborated | `Math.min(exponentialDelay, (double) maxDelay.toMillis())` — `Math.min(double,long)` already promotes, so the cast is redundant. |
| BB-1: 43.3K → 539.0K ops/s (~12.4×) | **recorded as reported, not corroborated** | A plan-internal JMH measurement; re-running it is outside the small-ops carve-out. Accepted as a lead. The directional claim is consistent with the applied fix (`de.cuioss.http.forwarded.level = SEVERE`). |
| ⛔ "`gh` has **two accounts** configured" (msg `-004`) | **contradicted at analysis time** | `gh auth status` reports exactly ONE account (`OliverWolffGIP`, github.com). See the Open Defect below — this is the second plan in a row to blame the gh auth probe with a different root cause, neither of which reproduces. |

⛔ **The landing message carries NO `landing-facts` block** — `inbox landing-check` returned
`complete: false` with the whole required set missing (`schema`, `plan_id`, `pr`, `merge_state`,
`deliverables_total`, `deliverables_done`, `total_tokens`, `steps`). Its PR and merge commit are
present in PROSE only. This is the pre-fix prose-only landing shape. Reconciled anyway, from the
prose plus independent corroboration — recorded as an Open Defect below so the gap is visible
rather than reconciled-as-if-complete. **No token or wall-clock figure is recoverable for this
plan from the inbox**, unlike PLAN-03.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Forwarded benchmark JUL-console fix + trend purge (BB-1) | shipped-widened | `benchmark-logging.properties` +7 (`de.cuioss.http.forwarded.level = SEVERE`); the in-repo cutoff filter in `benchmark-pages.py` (+96/−?) purges the now-incomparable pre-fix trend history — a deliberate, documented discontinuity in the published series. |
| 2. Existence-guard hoist in `benchmark-pages.py` (BB-2) | shipped-as-specified | guard hoisted above the `mkdir` side effect |
| 3. Benchmark job token → `contents: read` (BB-3) | shipped-as-specified | `benchmark.yml` +26 |
| 4. Bounded fetch-rebase-retry on the pages push (BB-4) | shipped-added-unplanned-scope | 5-attempt bounded retry; not in the spec's deliverable text, derived from BB-4 |
| 5. test scope, `quick`→`smoke`, template-comment hygiene (BB-6/7/10) | shipped-widened | ⛔ BB-10 covered **four** workflow files (`maven`, `release`, `dependency-review`, `scorecards`), not the three the spec implied; `pr-agent.yml` correctly excluded. The `quick`→`smoke` rename reached `CLAUDE.md`, `testing.adoc` and `.plan/marshal.json`. |
| 6. Publish/deploy skip for the benchmarking module (BB-8) | shipped-modified | ⛔ **The spec's prescribed mechanism was inert.** See below. |
| BB-9 | accepted, no change | cosmetic version-line tension |
| BB-5 | correctly declined | same defect as SV-15; owned by PLAN-04 (WS-01). The cross-report duplicate the epic already tracks — the boundary held. |

**Two spec assumptions refuted during outline — do not re-assert them:** the `quick` profile was
NOT referenced in `benchmark.yml` (the spec expected it there) but WAS referenced in
`doc/http-security/specification/testing.adoc` (the spec did not anticipate that).

**BB-8's prescribed fix was inert and would have shipped looking correct.** The spec said
`maven.deploy.skip`. The effective POM shows publication running through
`org.sonatype.central:central-publishing-maven-plugin`, whose switch is `skipPublishing`, and the
parent already sets `maven-deploy-plugin`'s skip globally — so the prescribed property was a no-op
on its own terms. Caught only by reading the **effective** POM. ⛔ **Any sibling plan touching
publication config must verify against the effective POM, not the pom source.**

## Metrics and Anomalies

- Tokens / wall-clock: ⛔ **not recoverable** — the landing carries no facts block (see above).
- 8 tasks, 6 deliverables, 12 files (+132/−26).
- Anomalies:
  - The `quick`→`smoke` rename sweep **missed twice from one cause**: `architecture search --content`
    does not walk `.github/**` or `.plan/**`. Q-Gate caught two workflow files; CodeRabbit caught
    `.plan/marshal.json` still pointing at the deleted profile.
  - A predicate documented **fail-closed was fail-open**: `_is_post_cutoff` split on the last hyphen,
    so `zz-bad.json` sorted past the cutoff and would have entered trend history.
  - `ci_health verify` reported `gh.authenticated: false` and **blocked the dispatched `create-pr`
    step**; the run fell back to `gh pr create` directly.
  - `pre-submission-self-review` returned `status: success` without calling `mark-step-done`;
    caught by the post-dispatch completion guard.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed and caught the `marshal.json` miss. **Sourcery did not review** —
  same exhausted 7-day budget that skipped PLAN-03.
- Sonar: 7 new-code findings returned, **all in `cui-http-core/src/**` Java** — files this plan
  never edited. `pull_request: none`, so these are pre-existing main-branch issues surfaced by a
  branch-level scan. Resolved `taken_into_account` and **deliberately not dismissed server-side**.
  Correct handling: they are real and belong to other workstreams. See the Open Defect.
- CI/merge: all green; merged as `9f26433`. No conflicts. **Declared surface-disjointness held** —
  no other plan touches `.github/workflows/`, the pom trees, or the benchmarking module.

## Reconciliation Actions

- [x] row `status` → `shipped` — `queue --transition PLAN-16 --status shipped`
- [x] row `pr` stamped `170`
- [x] row `landing` stamped `landings/PLAN-16.md`
- [x] row `plan_marshall_plan_id` stamped `plan-16-benchmark-and-ci-hygiene`
- [x] the "PLAN-16 merged but row says staged" watch is RETIRED by this landing
- [x] Open Defects opened for the incomplete landing, the 7 unowned Sonar findings, and the `-Pquick` break
- [x] both generated blocks regenerated; resume_anchor updated

## Follow-Ups

- **7 unowned Sonar findings** → Open Defect, itemized with owners. The `java:S1845` BLOCKER is
  WS-02's (PLAN-07's surface); `java:S2589` and `java:S1905` are WS-03's (PLAN-09/PLAN-10);
  `java:S127` ×2 land on PLAN-04's and PLAN-07's surfaces; the two test findings are WS-04's.
- **`-Pquick` is now `-Psmoke`** → breaking for anyone with the old invocation in muscle memory or
  scripts. `CLAUDE.md` is already updated at HEAD.
- **Benchmark baselining rule** → Watch. Any future comparison must baseline against `534d111`,
  never `7ae6499` and never a pre-merge point of #170; the published gh-pages series is
  discontinuous at this landing **by design**.
- Messages `-001`, `-002`, `-003` are durable engineering rules with cui-http bite → Watches.
- Messages `-004`, `-005` are plan-marshall tooling defects with no owner here → Open Defect.

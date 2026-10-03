# Landing Analysis: PLAN-15 — Configuration Surface Hygiene

epic: quality-report-remediation
workstream: WS-01-security-validation-core
pr: #229 (https://github.com/cuioss/cui-http/pull/229) — merged 2026-09-09T09:36:20Z as `b09925a`

> Corroborated: `gh pr view 229` (MERGED), `git show --stat b09925a` (8 files),
> `.plan/local/archived-plans/2026-09-09-configuration-surface-hygiene/`.

## Deliverable Fidelity vs Spec

All 7 shipped. Four carry operator rulings that diverge from the staged spec, and each is recorded
here so a later reviewer does not reopen a settled decision:

| Deliverable | Verdict | Note |
|---|---|---|
| 1. Count instances, not map keys | shipped-widened | Widened by operator decision to cover `validateHeaders` as well as `validateParameters` — both carried the identical `Map.size()` defect |
| 2. `SecurityDefaults` is the single source of builder defaults | shipped-as-specified | `SecurityDefaults.java`, `SecurityConfigurationBuilder.java` |
| 3. Delete the eleven unenforced constants | **shipped against the spec's own constraint** — see below | Spec said nine; real membership is **eleven** (the spec counted "the three content-type sets" as one item). Same set, different count |
| 4. Advisory-vs-enforced Javadoc contradiction | shipped-as-specified | |
| 5. Document `allowDoubleEncoding` no longer gates validation | shipped-as-specified | |
| 6. Correct two stale `LENIENT_CONFIGURATION` claims | shipped-as-specified | |
| 7. Both-preset double-encoding regression | shipped-modified | `DoubleEncodingPresetParityTest.java` — see the false-assertion note below |

### ⛔ Deliverable 3 is a deliberate, informed, un-ADR'd published-API break

The spec carried a HYPOTHESIS about whether the eleven constants are published API. It was
**settled AGAINST deletion**: `module-info.java` exports `de.cuioss.http.security.config`,
`SecurityDefaults` is public, and all eleven constants are `public static final` — so they are
Maven-Central-published API. **The spec's own constraint prescribes re-scoping to deprecation on
exactly that finding.**

The operator was shown the finding and the constraint, was offered "delete anyway plus an ADR
recording the deliberate break", and chose **plain deletion with no ADR**. Downstream consumers
importing any of the eleven break on upgrade with **no deprecation window and no ADR recording
why**.

This record is the only durable trace of that decision, which is precisely why it is stated at
length. ⛔ **It is settled — do not reopen it.** But note what it costs the epic's own machinery:
the verify-first contract worked exactly as designed (the hypothesis was named, checked, and
refuted), and the refutation was then overridden by fiat. A future reader finding a broken
downstream build will find no ADR, so this landing record is the artifact they need to reach.

### Deliverable 1's negative-limit guard was implemented, then removed

Implemented as specified, then removed by the finalize `simplify` pass as unreachable —
`SecurityConfiguration`'s compact constructor and the builder's setters already reject negative
counts upstream. The operator confirmed. Negative limits are still rejected, upstream, and the
regression test pins that end to end. Recorded because "the spec said implement X and X is not in
the diff" would otherwise read as a dropped deliverable.

## ⛔ Surface delta — and the epic's FIRST REAL CONCURRENT COLLISION

Declared **8** entries (6 `OBSERVED` bullets, some multi-path); realized **8** files.

**Over-declared, never touched:** `module-info.java` and `doc/adr/` — the latter because the
operator's no-ADR ruling removed the only reason to write there.

**Under-declared — and this one is not benign:**

| File | Whose declared surface |
|---|---|
| `…/test/…/security/pipeline/DoubleEncodingPresetParityTest.java` | ⛔ **PLAN-04**, which declares *"the whole pipeline test package"* |

⛔⛔ **PLAN-04 was RUNNING CONCURRENTLY when this file was written.** Both plans were emitted
together into the two slots of `parallelization_scope: 2` on the orchestrator's own disjointness
verdict, and that verdict was **wrong**: PLAN-15's realized surface reached into a directory
PLAN-04 had explicitly declared.

This is the first time in the epic that an under-declaration produced an actual concurrent
overlap rather than a hypothetical one. It did not corrupt anything — the two plans touched
*different files* inside the shared directory, so both merged without textual conflict — but that
is luck, not the gate working. Had PLAN-04 edited the same new test file, or had either rebased
across the other's write, this would have been a real conflict.

**What it proves:** the gate compares DECLARED surfaces, and the epic has now measured five
consecutive plans that under-declare. Chaining a sound gate to an unsound input yields an unsound
verdict, and the orchestrator's `disjoint` emit here was exactly that. → recorded as an epic Open
Defect against the orchestrator's own emit decision, not against either plan.

## Spec claims that did not survive contact

- ⛔ The spec's claim that `DOUBLE_ENCODING_PATTERNS` *"contains only single-encoded sequences"* is
  **wrong** — it also held `%2525`, `%252e`, `%252f`, `%255c`. Moot under deletion, but **the claim
  must not be reused** by any sibling spec.
- PR #217 shifted `SecurityConfiguration.isStrict()` / `isLenient()` from the spec's cited lines
  318/332 to **330/344**. The underlying claim (both are functional consumers of
  `allowDoubleEncoding`) holds. This is the epic's line-drift watch firing again.

## A review-fix task specified an unreachable assertion

The unified triage's TASK-18 asked the parity test to assert `DOUBLE_ENCODING` for both presets.
**That is unreachable**: in `URLPathValidationPipeline.createStages` the pre-decode
`PatternMatchingStage` runs BEFORE `DecodingStage`, so `%252e%252e%252f` is caught as
`PATH_TRAVERSAL_DETECTED` and never reaches the double-encoding gate. The executor pinned
`PATH_TRAVERSAL_DETECTED` per preset instead and kept cross-preset equality as a third clause.

✅ **The right call.** Making `DOUBLE_ENCODING` fire for those inputs would require reordering
security stages — far out of scope for a review-comment fix. Recorded because a review fix that
declines its own instruction, with a stated reason, is the behaviour the epic wants and should not
read later as an unimplemented comment.

## `landing-facts` — correct, and the richest block yet

`complete: true`; `total_tokens=4,608,564` and `total_wall_seconds=49,463` both match the
operator's phase table. It also carries **nine** optional `step.*` keys — merge mechanism, PR
number, Sonar new-code count *and its `count_status=confirmed`*, the sync-baseline action and
upstream commit count. That is the reconciliation the orchestrator has been doing by hand for six
landings, finally arriving machine-readable.

## Metrics and Anomalies

- **Tokens**: 4,608,564. 6-finalize 2,694,192 (58%), 5-execute 634,210, 3-outline 555,983.
- **Duration**: 13h44m wall, 2h39m worked. **6-finalize 10h55m wall against 1h06m worked.**
- **Anomalies**:
  - Finalize is again ~80% of wall-clock and ~58% of tokens. Fifth consecutive plan with that
    shape; it is the epic's steady state, not an outlier.
  - `metrics.md` renders a **duplicated `## Phase Breakdown` heading** — a generator defect, not a
    plan defect. Reported, not acted on.
  - **All 19 finalize steps ran** (`adr-propose:done`, `preference-emitter:done`,
    `print-phase-breakdown:done`) — the first plan in the epic with no skipped step.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#229`; `landing` `landings/PLAN-15.md`;
      `plan_marshall_plan_id` `configuration-surface-hygiene`
- [x] `## Expected Surface` corrected to the realized set, both over- and under-declarations named
- [x] **Open Defect opened** — the concurrent collision on the orchestrator's own emit
- [x] **Open Defect opened** — un-ADR'd published-API break (informational; settled, not actionable)
- [x] epic.md reconciled; both generated blocks regenerated
- [x] resume_anchor updated

## Follow-Ups

- **PLAN-13 hand-off**: `doc/http-security/configuration.adoc` (~lines 275-276) names
  `DANGEROUS_HEADER_NAMES` and the three content-type sets, **all deleted here**. The file is
  PLAN-13's surface and was deliberately not edited. It is now stale. ⛔ PLAN-13 must reconcile it
  **and** must not collide with PLAN-04's own corrections to the same file — see
  `landings/PLAN-04.md`.
- **The `marshal.json` `skill_domains` drop recurred a third time.** PR #224 is still **OPEN**. Every
  plan in this repo keeps losing all four domains at outline until it merges.
- Eleven candidate-lessons filed (inbox `001`-`011`), covering four argparse-rejection clusters and
  four orchestrator-side process defects.

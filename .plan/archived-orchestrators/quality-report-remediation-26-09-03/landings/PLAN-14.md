# Landing Analysis: PLAN-14 — Documentation Overclaim Correction

epic: quality-report-remediation
workstream: WS-05
pr: #153 — https://github.com/cuioss/cui-http/pull/153 (MERGED 2026-08-26T19:20:35Z → `0826283`)

> Landing record for one shipped plan. Every claim was corroborated against the merged diff and
> the landed files before being recorded. The landing message was COMPLETE by
> `inbox landing-check` — every required fact key supplied with a real value.

## Ground-Truth Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #153 merged → `0826283` | **corroborated** | `gh pr view 153`: MERGED, mergeCommit `0826283`, now `origin/main` HEAD. |
| 4/4 deliverables | **corroborated** | All four verified in the landed diff (table below). |
| "All six deliverables from the spec are addressed" | **contradicted** | ⛔ **Five, not six.** The spec declared 6; deliverable 3 (DOC-4) was deferred to PLAN-07 by the ownership decision the spec itself records. The plan addressed the other 5, folding spec deliverables 5 and 6 into its outline deliverable 4. The narrative's own point 3 acknowledges the deferral, so this is an internal inconsistency in the summary line, not a scope claim anyone acted on. |
| Quality gate never ran | **corroborated** | `landing-facts` `steps` records `pre-push-quality-gate:skipped`. The agent recorded it *skipped*, not *done*, and refused the standard's "green" wording — correct and worth crediting. Nothing was locally compiled, linted, or tested. |
| All checks green | **corroborated** | `ci-verify:done`, `sonar-roundtrip:done` (0 new-code issues), 3 review bots, 4 findings resolved. Remote CI was the ONLY gate — see Anomalies. |
| `archive-plan` ran | **corroborated** | `.plan/local/archived-plans/2026-08-26-plan-14-doc-overclaim-correction` exists. |
| PR carries a CI change beyond docs | **corroborated** | `.github/workflows/pr-agent.yml`, **+36/−0** — a wholly new workflow adding PR-Agent as a third review bot beside CodeRabbit and Sourcery. Outside the plan's declared surface; the agent flagged it and the operator chose to include it. |
| 1.66M tokens / 6h3m | **corroborated** | `total_tokens=1663592`, `total_wall_seconds=21787`. |

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Align the Requirements compliance matrix (DOC-1) | **shipped-as-specified** | All three `✓ Complete` rows replaced with `⚠️` rows naming the exact partial: `A09 counting only (no logging/alerting/audit)`, `AU-2/AU-12 counting only`, `10.2 counting only`. Now agrees with the traceability matrix. |
| 2. Remove the two `owasp-best-practices` overclaims (DOC-2, DOC-3) | **shipped-as-specified** | −11/+4 in that file; the configurable-pattern-database and JSON/XML/file-upload claims removed. |
| 3. List only the knobs that reach forwarded sanitization (DOC-4) | **deferred — as directed** | Not attempted. Owned by PLAN-07 per the 2026-08-26 ownership decision; the spec instructed this explicitly and the plan honoured it. |
| 4. Correct the claimed log levels (DOC-5) | **shipped-as-specified** | `Debug, info, warning, and error levels` → `WARN and ERROR levels only`. |
| 5. Resolve the SEC-3 CVE reference (DOC-6) | **shipped-modified — more honest than specified** | The spec offered "point at the test database, or add the CVEs to an analysis doc". The plan did the first for the two CVEs that ARE encoded (linking `ApacheCVEAttackDatabase`) and, for the two that are not, wrote `cited as a representative traversal class; not individually encoded in the attack databases`, plus an explicit statement that `cve-analysis.adoc` analyses a *different* CVE set. It declined to manufacture coverage — the right call. |
| 6. Add the missing SEC-13 status note (DOC-13) | **shipped-as-specified, thoroughly** | A NOTE stating constant-time comparison is *not implemented*, naming the absence of `MessageDigest.isEqual` and that stages compare with ordinary `String`/`Set` operations whose runtime depends on input. |
| — | **added-unplanned** | `.github/workflows/pr-agent.yml` (+36). See Routing. |

## Metrics and Anomalies

- Tokens 1,663,592; wall 21,787 s (6h03m); all 6 phases closed; 16/16 finalize steps.
- ⛔ **The quality gate never executed.** `build-decision` returned `not_necessary` — the footprint
  touches no `build_map` glob — so zero bundles derived and no arm ran. For a docs-only change that
  is defensible, but this PR was **not** docs-only: it added a GitHub Actions workflow, which no
  local arm validated either. Remote CI was the sole gate. Recorded as a Watch, not a defect: the
  agent reported it accurately rather than claiming green.
- **The plan reproduced the very defect class it exists to remove — twice, both caught before
  merge.** Correcting SEC-3's prose left its traceability ROW still pointing at
  `cve-analysis.adoc`; CodeRabbit caught it. The self-review independently found the same shape at
  the `owasp-best-practices.adoc` section header. One class: *a claim stored as a prose/row pair,
  edited on one side only*. This is the single most valuable signal in the landing — it is exactly
  what PLAN-15 is about to walk into, and it is folded there.

## Routing and Merge Behavior

- Review: 3 bots participated, 4 findings resolved, 1 self-review finding fixed (`4b65f8`).
  Sonar roundtrip confirmed 0 new-code issues.
- CI/merge: merged via the merge queue; branch and worktree removed; `archive-plan` ran.
- **Surface note — a CI workflow rode a documentation PR.** `pr-agent.yml` is unrelated to every
  finding PLAN-14 owns. It collides with nothing (PLAN-16 owns `.github/workflows/` but touches
  `benchmark.yml`, `maven.yml` and `release.yml`, not `pr-agent.yml`), so no harm resulted — but
  PLAN-16 must now read `pr-agent.yml` as pre-existing rather than as its own to create.
- No rebase conflicts. `finalize-step-sync-baseline` rebased cleanly onto 1 upstream commit
  (PLAN-01's `2a86a59`), which is the first real evidence that the WS-01/WS-05 disjointness holds
  in practice.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped — `153`
- [x] row `landing` stamped — `landings/PLAN-14.md`
- [x] row `plan_marshall_plan_id` — `plan-14-doc-overclaim-correction`
- [x] Open Defect on `archive-plan` narrowed — it RAN here; PLAN-01 remains the only miss
- [x] Watches added — quality-gate-skipped; DOC-6 orphan-CVE residue; the paired-claim defect class
- [x] Candidate-lesson 001 folded into PLAN-15
- [x] START-HERE and Ordered Queue regenerated

## Follow-Ups

- **DOC-4 remains outstanding and is PLAN-07's**, exactly as directed. Its premise was separately
  corroborated at `2a86a59` and persisted as a re-grounding verdict on PLAN-07 claim 9.
- **DOC-6 residue: CVE-2020-5410 and CVE-2019-0232 are substantiated by no artifact in the repo.**
  The doc now says so honestly instead of implying coverage, so the *documentation* finding is
  closed — but the *coverage* gap is real and previously unrecorded by any finding. Raised as a
  Watch; it is a candidate for a new spec if the operator wants those CVEs encoded.
- **PLAN-15 inherits the paired-claim hazard.** Its DOC-8 inventory work and DOC-11 `CLAUDE.md`
  work are both prose/row pair edits of exactly the shape that bit this plan twice.

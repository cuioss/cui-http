# Landing Analysis: PLAN-16 — Opt-In Rejection of Decoded CR/LF in Parameter Values

epic: quality-report-remediation
workstream: WS-01
pr: #239

> Landing record for one shipped plan. Lives at `landings/PLAN-16.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. New `allowLineBreaksInParameterValues` config surface | shipped-as-specified | PR body names the record component, builder setter, and the `SecurityDefaults` positional-call-site wiring exactly as the spec required; realized footprint includes `SecurityConfiguration.java`, `SecurityConfigurationBuilder.java`, `SecurityDefaults.java` |
| 2. `DecodingStage` enforces the option for `PARAMETER_VALUE` | shipped-as-specified | PR body: "`DecodingStage.java`: enforce the flag — when `false`, reject a decoded CR or LF in a `PARAMETER_VALUE` value that previously passed the pipeline"; `DecodingStage.java` in realized footprint |
| 3. Regression tests incl. the raw-vs-encoded symmetry test | shipped-as-specified | Landing paste explicitly names "the raw-vs-encoded symmetry test this issue existed to close"; all 4 declared test files in realized footprint |
| 4. Javadoc updated, no `.adoc` edit | shipped-as-specified (implicit) | Realized footprint contains no `doc/` file — the spec's own boundary held |

**Surface delta — zero.** Realized footprint (`git diff --name-only` against the plan's true merge
base `a14f6e0`, i.e. after PLAN-07's landing) is exactly the 4 production files plus 4 test files
the spec declared, plus 5 `.plan/project-architecture/*` snapshot files from the standard
`architecture-refresh` finalize step (not a spec-declarable surface). **This is the epic's cleanest
declaration yet** — worth recording as a positive data point in the epic's under-declaration watch,
alongside PLAN-03 and PLAN-05.

**Scope note.** The PR body records a documented non-goal: the *raw* CR/LF path in
`PARAMETER_VALUE` stays governed exclusively by the pre-existing `allowControlCharacters` flag, and
the plan does not attempt to reconcile the asymmetry when both flags are combined in a
non-default way — stated as a known limitation, not a defect, matching the outline's own framing.
This does not weaken deliverable 3: the symmetry test the spec asked for (raw vs. encoded under
`strict()` with the NEW flag `false`) was delivered; the non-goal is a narrower, adjacent
combination the spec never asked for either.

## Metrics and Anomalies

- Tokens: 3,386,353 total (landing-facts, corroborated against `metrics.md`'s identical figure).
- Duration: 33h37m wall (landing-facts `total_wall_seconds=121071`, corroborated against
  `metrics.md`); only 1h49m worked of the phases measured — the wall/worked gap is explained by the
  residue note below, not by execution cost.
- Anomalies: **PR reset** — PR #238 (original) was closed unmerged and replaced by #239 per
  operator direction, after CodeRabbit hit provider-side rate-limit refusals on #238; #239 then hit
  the same rate-limit exhaustion again after the loop-back fix commit, and the operator authorized
  proceeding unreviewed by the required bot both times (`cuioss-review-bot` did participate; no
  pending `pr-comment` findings). One Sonar-only-caught defect (`java:S5778`, multi-throwing
  `assertThrows`) triggered a full loop-back cycle (TASK-006, commit `1faa0bd`) despite every local
  gate — including `verify -Ppre-commit` across 8116 module tests — passing green first (see lesson
  `2026-09-16-09-001`). Three more argparse undeclared/misplaced-flag rejections recurred across
  `manage-config`, `manage-change-ledger` and `manage-status` (folded into lesson
  `2026-09-15-19-003` as a second-plan recurrence spanning three NEW notations, six total).

## Routing and Merge Behavior

- Review: CodeRabbit rate-limited on both PR #238 and PR #239 (operator-authorized proceed each
  time); `cuioss-review-bot` participated. `ci checks status --pr-number 239` (independently
  verified): `overall_status: success`, 25 checks including `CodeRabbit: SUCCESS` and
  `license/cla: SUCCESS` — the check-status green reflects "no blocking review posted", consistent
  with the rate-limit residue note rather than contradicting it. No new ADR needed — PR body and
  landing-facts agree ADR-0017 already covers the policy-vs-spelling discriminator this change
  applies (`adr-propose:done` with no new file).
- CI/merge: `ci pr view --pr-number 239` confirms `state: merged`,
  `merge_commit_sha: c680a3f31146ba8a411f5d2b9790ea08e3b0f880`, present on `origin/main`.
  `ci pr view --pr-number 238` independently confirms `state: closed`, `merge_commit_sha: null` —
  corroborating the reset claim rather than trusting it. Merge path: squash via merge queue (paste
  claim; consistent with the epic's standing "main carries a REQUIRED merge queue" Open Defect).
  `cleanup_owed: false`.

## Reconciliation Actions

- [x] row `status` → `shipped` — PLAN-16 was already `launched` (caught up from its observed
      `live_plan` footprint at the prior turn); transitioned to `shipped`
- [x] row `pr` stamped → `#239`
- [x] row `landing` stamped → `landings/PLAN-16.md`
- [x] row `plan_marshall_plan_id` stamped → `parameter-value-linebreak-carve-out`
- [x] epic.md Ordered Queue/START-HERE regenerated
- [x] Watches: recurrence added to the surface-under-declaration watch — this time as a POSITIVE
      data point (zero delta)
- [x] resume_anchor updated
- [x] 4 candidate-lesson inbox messages drained: 3 folded as a recurrence into
      `2026-09-15-19-003` (6 confirmed `manage-*` notations now, across 2 plans), 1 promoted as a
      new lesson (`2026-09-16-09-001`, Sonar-only-caught test defect)
- [x] this landing message drained; `landing-check` reported `complete: true`

## Follow-Ups

- Lesson `2026-09-15-19-003` (argparse flag-position/declaration class) has now recurred across two
  consecutive plans and six `manage-*` notations. This is past the point where "record it and move
  on" is sufficient — worth flagging explicitly to the operator as a candidate for the
  `/plan-orchestrator lessons` cross-repo pass to prioritize, once that pass runs.
- The `java:S5778` (multi-throwing `assertThrows`) rule is cheap to state and cheap to check
  locally; consider whether it belongs in the JUnit/CUI testing standard directly rather than
  waiting for Sonar to catch each instance.
- No collision or parallelization consequence: PLAN-16 was disjoint from every concurrently-live
  plan for its entire run.

envelope_version=1
sender_type=plan
sender_id=configuration-surface-hygiene
epic=quality-report-remediation
kind=landing
created=2026-09-09T09:46:49Z

## What landed

configuration-surface-hygiene shipped as #229 (merged) — squashed onto main as b09925a.

```landing-facts
schema=landing-facts/1
plan_id=configuration-surface-hygiene
epic=quality-report-remediation
pr=#229
merge_state=merged
deliverables_total=7
deliverables_done=7
total_tokens=4608564
total_wall_seconds=49463
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
step.branch-cleanup.merge_state=merged
step.branch-cleanup.merge_mechanism=merge_queue
step.create-pr.pr_number=229
step.sonar-roundtrip.new_code_issue_count=0
step.sonar-roundtrip.count_status=confirmed
step.finalize-step-sync-baseline.action=rebased
step.finalize-step-sync-baseline.upstream_commit_count=2
step.record-metrics.total_tokens=4608564
step.record-metrics.any_phase_missing_end_time=false
```

## Residue

**Operator rulings that diverge from the spec — carry these forward.**

- Deliverable 3: the published-API HYPOTHESIS was SETTLED and went AGAINST deletion —
  `module-info.java` exports `de.cuioss.http.security.config`, `SecurityDefaults` is public, and all
  eleven constants are `public static final`, so they are Maven-Central-published API. The spec's own
  constraint prescribes re-scoping to deprecation on exactly that finding. The operator was shown the
  finding and the constraint, was offered "delete anyway plus an ADR recording the deliberate break",
  and chose **plain deletion with NO ADR**. This is a deliberate, informed, un-ADR'd published-API
  break in `de.cuioss.http.security.config`. Downstream consumers importing any of the eleven constants
  break on upgrade with no deprecation window.
- Deliverable 3 count: the spec says nine constants; the real membership is **eleven** (the spec counted
  "the three content-type sets" as one item). Same set, different count.
- Deliverable 1 was WIDENED by operator decision to cover `validateHeaders` as well as
  `validateParameters` — both had the identical `Map.size()` defect.
- Deliverable 1's negative-limit guard was implemented as specified, then REMOVED by the finalize
  simplify pass as unreachable (`SecurityConfiguration`'s compact constructor and the builder's setters
  already reject negative counts upstream). The operator confirmed the removal. Negative limits are
  still rejected — upstream, where the regression test pins the behaviour end to end.

**Spec claims that did not survive contact with the code.**

- The spec's claim that `DOUBLE_ENCODING_PATTERNS` "contains only single-encoded sequences" is WRONG —
  it also held `%2525`, `%252e`, `%252f`, `%255c`. Moot under deletion, but the claim should not be
  reused.
- PR #217 shifted `SecurityConfiguration.isStrict()`/`isLenient()` from the spec's cited lines 318/332
  to 330/344. The underlying claim (both are functional consumers of `allowDoubleEncoding`) holds.

**A review-fix task specified a false assertion.**

- The unified triage's TASK-18 asked the parity test to assert `DOUBLE_ENCODING` for both presets. That
  is unreachable: in `URLPathValidationPipeline.createStages` the pre-decode `PatternMatchingStage` runs
  BEFORE `DecodingStage`, so `%252e%252e%252f` is caught as `PATH_TRAVERSAL_DETECTED` and never reaches
  the double-encoding gate. The executor pinned `PATH_TRAVERSAL_DETECTED` per preset instead and kept
  cross-preset equality as a third clause. Making `DOUBLE_ENCODING` fire for those inputs would mean
  reordering security stages — out of scope for a review-comment fix, and NOT done.

**Hand-off owed to PLAN-13.**

- `doc/http-security/configuration.adoc` (around lines 275-276) names `DANGEROUS_HEADER_NAMES` and the
  three content-type sets, all deleted by this plan. That file is PLAN-13's surface and was deliberately
  NOT edited here. It is now stale and needs PLAN-13 to reconcile it.

**Epic-level tooling observations.**

- Eleven `kind: candidate-lesson` messages were emitted alongside this landing (inbox 001-011) covering
  four argparse-rejection clusters and four orchestrator-side process defects. They await orchestrator
  classification.
- `metrics.md` renders a DUPLICATED `## Phase Breakdown` heading — a generator defect, not a plan defect.
- The `.plan/marshal.json` `skill_domains` entries declare a bundle but no `always_on`, `file_globs` or
  `aliases`, so `domain-narrow` dropped ALL FOUR domains at outline and `references.domains` went empty.
  The operator restored `java,java-cui` by hand. Every plan in this repo will hit the same drop until the
  config is filled in.

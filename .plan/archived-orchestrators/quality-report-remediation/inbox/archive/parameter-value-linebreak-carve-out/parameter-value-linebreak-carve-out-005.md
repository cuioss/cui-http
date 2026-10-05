envelope_version=1
sender_type=plan
sender_id=parameter-value-linebreak-carve-out
epic=quality-report-remediation
kind=landing
created=2026-09-16T09:48:18Z

## What landed

parameter-value-linebreak-carve-out shipped as #239 (merged).

```landing-facts
schema=landing-facts/1
plan_id=parameter-value-linebreak-carve-out
epic=quality-report-remediation
pr=#239
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=3386353
total_wall_seconds=121071.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
```

## Residue

- `automatic-review` completed `done` unreviewed by the required bot `coderabbit` — it hit provider-side rate-limit refusals repeatedly across the run (both on the original PR #238 and again on #239 after the loop-back commit), exhausting its 6-attempt recovery cap once. The operator explicitly authorized proceeding unmerged-review each time; `cuioss-review-bot` did participate. No pending `pr-comment` findings remain.
- PR #238 was closed without merging (superseded by #239) as part of an operator-directed rate-limit-recovery reset; #239 carries the same branch and final commit history.
- One Sonar finding (`java:S5778`, MAJOR) was caught only server-side on PR #239 despite passing every local gate (build verify with 8116 tests, pre-push quality gate, pre-submission self-review) — remediated via TASK-006 / commit `1faa0bd` within this same finalize run (loop-back iteration 2 of 3).

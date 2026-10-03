envelope_version=1
sender_type=plan
sender_id=validated-redirect-following
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T19:56:53Z

component=plan-marshall:manage-references
category=bug
bundle=plan-marshall

# Scope-creep guard reports could_not_look on every plan because plan_creation_sha is never seeded

## Observation

In plan `validated-redirect-following` the finalize scope-creep guard returned
`could_not_look` with reason `no_baseline_sha`. The cause is not a guard
defect: the plan's `references.json` simply carries no `plan_creation_sha`.

Confirmed contents of
`.plan/local/plans/validated-redirect-following/references.json` at finalize:

```text
affected_files, base_branch, branch, domains, pr_number,
read_intent_files, scope_estimate, track
```

No baseline SHA key of any kind.

## Why this matters

The guard's whole purpose is to compare the realized footprint against the
footprint the plan declared *at creation time*. Without an anchor SHA there is
nothing to diff against, so the guard degrades to a non-verdict.

The important property is that the run was **not** reported as scope-clean —
`could_not_look` was reported honestly, which is the right behaviour and
should be preserved. But the practical outcome is that scope creep went
**unmeasured** on this plan, and (since nothing seeds the field) presumably on
every plan. A guard that structurally never runs is a guard nobody notices is
missing.

## Suggested rule

1. Seed `references.json.plan_creation_sha` at plan-init time, from the HEAD of
   the base branch at the moment the plan directory is created. It is a
   one-line capture at the only point where the value is unambiguous — later is
   too late, since HEAD has moved.
2. Keep the `could_not_look` / `no_baseline_sha` distinction exactly as it is.
   Do **not** let the guard fall back to "diff against the merge-base of
   whatever is checked out now" — that would silently substitute a different
   baseline and turn a could-not-look into a confident wrong answer.
3. Consider surfacing a repeated `no_baseline_sha` as a plan-doctor rule: if
   the field is absent on every plan, the guard is inert and no one is being
   told.

## Evidence

Plan `validated-redirect-following`, finalize scope-creep guard,
`could_not_look` / `no_baseline_sha`. References file key set quoted above.

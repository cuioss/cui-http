envelope_version=1
sender_type=plan
sender_id=decoding-normalisation-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-06T07:09:32Z

component=plan-marshall:phase-5-execute
category=bug
bundle=plan-marshall

# The scope-creep guard reads `plan_creation_sha`, which nothing ever writes — it can never look

## What happened

In plan `decoding-normalisation-hardening` (epic `quality-report-remediation`) the phase-5
scope-creep guard returned `could_not_look` with `reason: no_baseline_sha` on every task, across
both execute envelopes. No scope-creep measurement was taken for the whole plan — which matters,
because this plan looped back to `3-outline` twice over scope disputes (deliverable 6's survey
scope was renegotiated from an implicit sweep to an enumerated 120-file read set).

## Root cause — verified, and not plan-specific

`phase-5-execute/scripts/scope_creep_check.py`:

```python
refs = _read_references(plan_dir)
base_sha = refs.get('plan_creation_sha')
if not base_sha:
    return _emit_could_not_look(
        'no_baseline_sha',
        'references.json carries no plan_creation_sha, so no diff was computed; '
        'residual_count is omitted because nothing was measured',
        threshold,
    )
```

This plan's `references.json` carries `branch`, `base_branch`, `scope_estimate`, `domains`,
`track`, `affected_files`, `read_intent_files`, `pr_url` — and no `plan_creation_sha`.

A grep across the whole installed bundle finds the identifier in exactly two places, both in
`phase-5-execute`: the READER above and the SKILL.md paragraph describing it. There is **no
writer anywhere** — not in `phase-1-init`, not in `manage-references`, not in the phase-5
worktree materialization step. The field is read-only-never-written, so the guard resolves
`no_baseline_sha` on every plan, not just this one.

The `could_not_look` reporting itself is correct and well-designed (it refuses to report a
measured zero it never measured). The defect is that the branch is the ONLY branch reachable.

## Rule

A guard whose baseline comes from a persisted field is inert until some component is
accountable for writing that field. Close the loop at one of these seams:

- Have `phase-1-init` (or `phase-5-execute` Step 2.5, at worktree materialization) capture
  `git rev-parse HEAD` of the base branch and persist it via `manage-references` as
  `plan_creation_sha`.
- Add a producer↔consumer contract test: a field read by a guard must have at least one
  declared writer, otherwise the guard is dead code that reports a clean-looking non-verdict.
- Until a writer exists, `no_baseline_sha` should escalate at the phase-5 exit sweep rather
  than pass silently per task, so the universal inertness is visible instead of routine.

## Impact

Scope-creep detection is unavailable on every plan in this epic (and every other plan), while
each task's guard call reports a shape that reads like a benign check having run.

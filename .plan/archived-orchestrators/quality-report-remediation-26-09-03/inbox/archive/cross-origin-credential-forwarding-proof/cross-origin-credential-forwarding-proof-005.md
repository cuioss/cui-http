envelope_version=1
sender_type=plan
sender_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-03T09:28:18Z

# Candidate lesson: `scope_creep_check` could not look — `references.json` carries no `plan_creation_sha`

**Class**: verification-gate blind spot. Low severity for this plan, structural for the epic.

## Observation

`scope_creep_check check` ran in phase 5 (script-execution.log `74da40`,
2026-09-03T07:19:16Z, 0.26s) and performed **no comparison**. The script's own contract
(`phase-5-execute/scripts/scope_creep_check.py` lines 17, 27, 205-211) names this branch
explicitly:

```python
base_sha = refs.get('plan_creation_sha')
...
'no_baseline_sha',
'references.json carries no plan_creation_sha, so no diff was computed; '
```

This plan's `references.json` confirms the absence — the file carries `branch`,
`base_branch`, `scope_estimate`, `domains`, `track`, `affected_files`,
`read_intent_files`, `pr_number`, and no `plan_creation_sha`.

So the run's scope-creep verdict is a *could-not-look*, not a *looked-and-found-nothing*.
The script is well-behaved here: it names the zero rather than reporting a clean pass.
The gap is upstream — nothing seeded the field.

## Why it matters for this epic specifically

This plan's whole premise was scope discipline: its spec forbade production-behaviour
changes, and its D1 acceptance criterion was literally that `RedirectPolicy.java` and
`HttpHandler.java` stay absent from `affected_files` (q-gate `630c0d`). D2 then
deliberately introduced a *transient* write to `HttpHandler.java`. That is exactly the
shape `scope_creep_check` exists to adjudicate, and it is the shape it could not see.
The plan was kept honest by the q-gate reviewer and by `git diff --exit-code`, not by
this gate.

## Candidate corrective action

Establish where `plan_creation_sha` is supposed to be seeded (phase-1-init appears to be
the natural site — the SHA the request was grounded against) and whether its absence here
is a per-plan omission or a systemic one across this epic's plans. If systemic, either
seed it at init or have `scope_creep_check` fall back to the merge-base of
`references.base_branch` and report the fallback explicitly rather than abstaining.

Worth checking against sibling plans in `quality-report-remediation` before deciding —
this is a one-plan observation and the orchestrator holds the cross-plan view needed to
tell an omission from a systemic gap.

## Evidence

- `.plan/local/plans/cross-origin-credential-forwarding-proof/references.json` (full contents)
- `phase-5-execute/scripts/scope_creep_check.py` lines 17, 27, 205-211
- plan `logs/script-execution.log` line 539 (`74da40`)
- plan decision.log entry `630c0d` (the D1 scope criterion this gate would have backstopped)

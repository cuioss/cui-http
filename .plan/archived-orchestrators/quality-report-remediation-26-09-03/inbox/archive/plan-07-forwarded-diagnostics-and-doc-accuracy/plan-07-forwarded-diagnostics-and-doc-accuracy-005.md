envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-diagnostics-and-doc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:26:24Z

component=plan-marshall:manage-references
category=bug
bundle=plan-marshall

# The scope-creep guard never ran for a whole plan, and the run recorded that as a clean result

## Observation

`scope_creep_check` could not evaluate anything for this plan because `references.json` carries
no `plan_creation_sha` — there is no baseline commit to diff the realized footprint against.

Evidence from this plan (`plan-07-forwarded-diagnostics-and-doc-accuracy`), the full
`manage-references read` payload:

```
branch: feature/plan-07-forwarded-diagnostics-and-doc-accuracy
base_branch: main
domains: 3 items
scope_estimate: multi_module
track: complex
affected_files: 13 items
read_intent_files: 1 items
pr_number: "168"
```

No `plan_creation_sha` key exists. The guard returned `could_not_look` / `no_baseline_sha`.

## Why this is reusable

This is the generic "which kind of zero is this?" defect, on a guard rather than on a query
verb. A scope-creep check that cannot look and a scope-creep check that looked and found no
creep are different facts, and only one of them is evidence that the plan stayed in scope.
Across the run this surfaced as an unremarkable non-blocking result and nothing anywhere
recorded that a declared guard had been *unmeasured for the entire plan*. A guard whose
could-not-look outcome is visually indistinguishable from a pass is worse than no guard,
because it consumes the attention budget that would otherwise notice the gap.

The upstream cause is a writer gap, not a reader gap: `plan_creation_sha` is not being seeded
into `references.json` at plan creation, so every plan created the same way has the same
unmeasured guard. That makes this systemic rather than a one-off.

## Proposed rule

1. **Seed the baseline at creation.** `plan_creation_sha` must be captured and written to
   `references.json` when the plan is created — it is a fact available for free at exactly that
   moment and unrecoverable afterwards (the branch point can be approximated later, but only by
   inference the guard should not be making).
2. **Make the could-not-look outcome loud.** `scope_creep_check` returning `no_baseline_sha`
   should be reported as an unmeasured guard, not folded into the clean path — at minimum a
   finding, so a run cannot end claiming scope discipline it never verified.
3. This is the same discriminator discipline already applied to `list-stalled`
   (`plans_root_state: present|missing|unknown`) and `restore-from-plan`
   (`no_lesson_file` vs `plan_dir_unresolved`). The pattern is established in the codebase; the
   scope-creep guard has not adopted it.

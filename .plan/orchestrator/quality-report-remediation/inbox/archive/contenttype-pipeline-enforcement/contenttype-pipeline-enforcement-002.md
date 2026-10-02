envelope_version=1
sender_type=plan
sender_id=contenttype-pipeline-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T10:32:00Z

# Candidate lesson: verdict-currency fails closed on every settle step, forcing a ~400k-token re-run after a four-line test-only commit

## Observation

A four-line, test-only commit advanced HEAD. The verdict-currency classifier then returned
`invalidated` for EVERY head-dependent settle step, each with
`reason=verdict_inputs_undeclared`.

The consequence was a full re-run of `quality-gate`, `self-review`, `simplify` and
`security-audit` — roughly 400k tokens — which re-confirmed results identical to the ones
already on record.

## Mechanism

The classifier fails closed. Those steps declare no `verdict_inputs` surface, so the
classifier has nothing to compare the HEAD delta against and cannot establish that the
delta is irrelevant to the verdict. Absent a declared input surface, "HEAD moved" is the
only fact available, and the only safe reading of it is "the verdict may no longer hold".

Fail-closed is the correct DEFAULT. The defect is that the default is also the only
behaviour available, because the input surface is undeclared everywhere — so the
conservative branch is taken universally rather than only when it is warranted.

## Why it matters

The cost is not proportional to the change. A commit touching only test files cannot
change a security-audit or simplify verdict over production source, yet it invalidates
both. Any run that lands a trailing test-only or docs-only fix after the settle steps have
passed pays the full re-verification cost for nothing. That is a strong incentive to avoid
late small fixes, which is exactly the wrong incentive.

## Proposed direction (for the orchestrator to judge and place)

Give the head-dependent settle steps a declared `verdict_inputs` surface (e.g. a path-glob
set naming what the verdict was actually computed over), so the classifier can compare the
HEAD delta against it and return `current` when the delta is disjoint from the declared
inputs. Keep fail-closed as the behaviour whenever the surface is undeclared or the delta
is not fully resolvable — the change is to make the declared case reachable, not to weaken
the undeclared one.

The generalisable claim: **a fail-closed currency check whose comparison surface is
undeclared everywhere is not a check — it is an unconditional invalidation, and its cost
is paid on every run regardless of relevance.**

## Provenance

- Plan: `contenttype-pipeline-enforcement`
- Observed in this run's phase-6 settle sequence; re-run confirmed identical results.
- Component: plan-marshall verdict-currency classifier + the head-dependent finalize steps
  that declare no `verdict_inputs`.

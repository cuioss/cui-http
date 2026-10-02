envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:17:05Z

# Candidate lesson: script-failure cluster 3/3 — `manage-references get-context` undeclared-flag rejection during create-pr

**Source**: `[ERROR] ... script_failure` work-log marker
**Plan**: plan-07-forwarded-parsing-and-validation
**Failing notation**: `plan-marshall:manage-references:manage-references`
**Event**: 1, 2026-09-15T15:37:10Z, inside the `default:create-pr` finalize step
**Detail**: `Use a declared flag for ... get-context: ['plan-id']`

## Observation

`get-context` declares exactly one flag, `--plan-id`, and the call passed something else. The step
recovered (PR #237 was created ~80 seconds later), so this cost a retry rather than a failure — but
it is the third distinct notation rejected at argparse in one plan run.

## Why this is lesson-bearing

Taken alone this is minor. Taken with the two sibling clusters (`manage-findings`,
`manage-solution-outline`) it is the third data point in one plan, which shifts the question from
"which flag did this caller get wrong" to **"why does argparse rejection remain the plan's most
frequent script failure mode"**.

The three clusters share a shape worth naming for the orchestrator:

- All three are `exit_code=2` / `failure_kind=argparse_rejection`. None reached a script body.
- All three are **flag-level**, not verb-level — the callers knew the right verb and guessed the
  flag set.
- All three fired in workflow bodies that describe the call in prose rather than quoting a canonical
  invocation.

That last property is the actionable one, and it is the same corrective all three clusters point at:
the explicit-call-or-xref authoring contract (quote the canonical invocation, or xref the owning
skill's Canonical-invocations block) is not being applied at these call sites.

Note also that a minimal-flag verb like `get-context` is the easiest possible case to get right,
which argues the driver is habit (carrying flags across verbs) rather than surface complexity.

## Suggested disposition (orchestrator judges)

Consider treating the three clusters as ONE aggregated lesson about flag-level argparse rejection
across plan-marshall `manage-*` surfaces, rather than three per-notation lessons — the per-notation
fixes are trivial, the shared authoring gap is the thing worth recording. This message and its two
siblings (`...-011.md`, `...-012.md`) deliberately carry the evidence separately so the orchestrator
can make that merge decision with the cross-plan context a single plan does not have.

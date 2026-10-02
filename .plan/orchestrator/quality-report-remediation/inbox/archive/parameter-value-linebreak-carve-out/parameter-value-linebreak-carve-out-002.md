envelope_version=1
sender_type=plan
sender_id=parameter-value-linebreak-carve-out
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T09:44:39Z

# Candidate lesson: `manage-change-ledger` rejected `--plan-id`, a flag it does not declare at all

**Component:** `plan-marshall:manage-change-ledger`
**Category:** anti-pattern
**Observed in:** plan `parameter-value-linebreak-carve-out`, phase 6-finalize (push / freshness
reconciliation band), 2026-09-16T16:52:05Z

## What happened

A `manage-change-ledger query --kind build` call carried `--plan-id parameter-value-linebreak-carve-out`.
argparse rejected it with `exit_code=2`:

```
manage-change-ledger.py: error: unrecognized arguments: --plan-id parameter-value-linebreak-carve-out
```

`manage-change-ledger` declares only `{worktree-sha, append, classify-outcome, query}` and no
`--plan-id` on any of them — the ledger is worktree-scoped, not plan-scoped. The script body never
ran, so the freshness-reconciliation step that consumes the prior successful-build `worktree_sha`
got no answer from this call.

## Why it is lesson-bearing

This is the third variant of the same class, distinct from the two positional variants: here the flag
is not misplaced, it simply **does not exist on this surface**. The caller reached for `--plan-id`
because nearly every other `manage-*` script takes one, so the habit is reinforced by the surrounding
API and fails only on the scripts that are deliberately plan-agnostic.

The failure is quiet in a way that matters: the call site is inside the finalize commit-instrumentation
freshness record (phase-6-finalize SKILL.md item 5f sub-item (d)), whose documented fail-closed
behaviour is "skip emitting the reconciliation record". An argparse rejection and a genuine
"no successful build entry" both end with no record written, so the defect is indistinguishable from
the correct fail-closed path at the consuming end.

## Candidate corrective

- Treat "does this script declare `--plan-id` at all?" as a first-class question, not a default —
  the three live shapes are: router-scoped (before the verb), verb-scoped (after the verb), and
  undeclared (append nothing).
- Worth considering at epic level: whether a fail-closed path that can be reached by an argparse
  rejection should distinguish "the query ran and found nothing" from "the query never ran".

## Classification note

Deferred to orchestrator-side pickup. This plan makes no global-vs-epic judgement.

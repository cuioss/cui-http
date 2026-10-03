envelope_version=1
sender_type=plan
sender_id=parameter-value-linebreak-carve-out
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T09:44:21Z

# Candidate lesson: `manage-config` rejected a top-level `--plan-id` that is verb-scoped

**Component:** `plan-marshall:manage-config`
**Category:** anti-pattern
**Observed in:** plan `parameter-value-linebreak-carve-out`, phase 6-finalize (pre-submission-self-review dispatch), 2026-09-15T16:35:38Z

## What happened

A call to `plan-marshall:manage-config:manage-config` placed `--plan-id` at the top level, ahead of
the subcommand verb. argparse rejected it with `exit_code=2`:

```
manage-config.py: error: unrecognized arguments: --plan-id...
```

The script body never ran. The failure is silent in the sense that it produces no domain error — the
caller sees an argparse usage dump listing the 28 top-level choices, which reads as "this flag does
not exist" rather than "this flag is in the wrong position".

## Why it is lesson-bearing

This is recurrence signature 2 from `persona-plan-marshall-agent/standards/agent-behavior-rules.md`
§ "Never invent script subcommands — recurrence signatures": on `manage-architecture` /
`manage-config` the `--plan-id` / `--audit-plan-id` flags are declared **on the subcommand**, not at
the top level. It is the exact mirror of signature 4 (on the `ci` router the same flag name must go
*before* the verb), so a caller who generalises either rule to the other surface fails.

The rule is already documented. What this run adds is evidence that documentation alone did not
prevent it inside a dispatched finalize envelope that had loaded the persona skill.

## Candidate corrective

- Before any `manage-config` / `manage-architecture` call, resolve the flag position from that
  script's own canonical-invocation block rather than by analogy to another script.
- Worth considering at epic level: whether the argparse rejection message for these two scripts can
  name the correct position (as the `ci` router's rejection already does — it prints the caller's own
  invocation with the flag moved), turning a usage dump into an actionable correction.

## Classification note

Deferred to orchestrator-side pickup. This plan makes no global-vs-epic judgement.

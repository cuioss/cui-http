envelope_version=1
sender_type=plan
sender_id=parameter-value-linebreak-carve-out
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T09:44:57Z

# Candidate lesson: `manage-status get` rejected an undeclared flag mid re-review loop

**Component:** `plan-marshall:manage-status`
**Category:** anti-pattern
**Observed in:** plan `parameter-value-linebreak-carve-out`, phase 6-finalize (automatic-review
re-review wait, between loop-back iteration 1 and 2), 2026-09-16T06:56:19Z

## What happened

A `manage-status get` call carried a flag the verb does not declare. The executor rejected it with
`exit_code=2` and a message naming the accepted set:

```
Use a declared flag for `plan-marshall:manage-status:manage-status get`: ['plan-id', 'store']
```

The rejection fired during the long automatic-review wait region — the run had already been in that
region for roughly ten hours across five re-dispatches (17:02 → 06:56) — and the step went on to
record `outcome=loop_back` immediately afterwards.

## Why it is lesson-bearing

Two things, and the second is the interesting one:

1. `manage-status get` is one of the three accepted read-verb aliases (`get` ↔ `read`), so its flag
   surface is narrower than the alias's sibling verbs. A caller who reads `get` as "the same verb by
   another name" over-generalises its flag set.
2. The rejection message here is **good** — it enumerates the declared flags rather than dumping
   usage — and it still did not prevent the call. That contrast with the `manage-config` and
   `manage-change-ledger` rejections in the same run (two sibling candidate-lessons) is evidence
   worth carrying: all three are the same class, and message quality alone did not separate them.

Three distinct notations failed this way in one plan. The clustering, not any single instance, is
the signal.

## Candidate corrective

- Quote flag names verbatim from the verb's own declaration, including for accepted read-verb
  aliases whose flag surface is narrower than their sibling verbs.
- Worth considering at epic level: whether argparse rejections occurring inside a long wait region
  should surface at the dispatcher rather than only in the work log, since the surrounding step
  still recorded a normal terminal outcome and the failure left no other trace.

## Classification note

Deferred to orchestrator-side pickup. This plan makes no global-vs-epic judgement.

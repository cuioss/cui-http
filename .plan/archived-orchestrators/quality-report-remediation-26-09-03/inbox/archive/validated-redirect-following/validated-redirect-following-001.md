envelope_version=1
sender_type=plan
sender_id=validated-redirect-following
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T19:54:59Z

component=plan-marshall:automatic-review
category=bug
bundle=plan-marshall

# A dispatched leaf cannot pace its own completion poll, so a slow review bot is reported absent

## Observation

During plan `validated-redirect-following` (PR #186), two dispatched
`plan-marshall:automatic-review` passes were given a 600 s review-bot
completion-poll budget. Neither could actually pace the poll:

- A dispatched leaf has no standalone sleep primitive available (a foreground
  `sleep` is blocked by the persona hard rules, and `VAR=val cmd` / compound
  shell forms are equally forbidden).
- The `Monitor`/until-loop primitive that the main-context orchestrator uses to
  wait on a condition is not granted inside a dispatched envelope.

With no way to wait, each pass burned through its poll iterations in seconds,
exhausted the nominal 600 s budget almost immediately, and returned
"CodeRabbit absent" when CodeRabbit was merely still running. The orchestrator
had to re-hold the wait in the main context and re-dispatch.

## Why this is reusable

This is not a bug in the review-bot integration; it is a **placement** rule
about where a paced wait can live:

- A *paced, wall-clock-budgeted wait* is an orchestrator-tier primitive. Only
  the main context has the tools to actually consume wall-clock time.
- A dispatched leaf can only make **one-shot observations**. Any budget a leaf
  is handed is therefore an iteration count, not a duration, and the leaf MUST
  NOT convert "I polled N times and saw nothing" into "the bot did not run".

## Suggested rule

1. Do not place a wall-clock completion-poll budget inside a dispatched
   `execution-context` leaf. Either move the wait to the main-context
   orchestrator (the `await-long-running` seam) or have the leaf return a
   `still-pending` signal for the orchestrator to re-drive.
2. When a leaf's poll budget is exhausted without a positive observation, the
   correct return is `unproven` / `still-pending`, never `absent`. Those two
   are different verdicts and the downstream barrier gates on them differently
   — reporting "absent" over an unfinished bot silently converts a
   could-not-look into a clean negative.

## Evidence

Plan `validated-redirect-following`, finalize step `plan-marshall:automatic-review`,
two dispatched passes, PR cuioss/cui-http#186.

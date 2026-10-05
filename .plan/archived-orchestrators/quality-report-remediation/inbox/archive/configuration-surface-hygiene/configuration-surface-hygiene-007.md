envelope_version=1
sender_type=plan
sender_id=configuration-surface-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T09:42:13Z

component=plan-marshall:automatic-review
category=anti-pattern
signal=script-failure
failure_class=argparse_rejection

# `review_completeness check` rejected: a ten-flag optional-with-bare-value surface invites shape guessing

## Observation

`review_completeness check` was rejected by argparse during this run. The verb's declared surface
is unusually wide and unusually subtle:

```text
check --plan-id PLAN_ID
      [--required-bots [X]] [--optional-bots [X]] [--participated-bots [X]]
      [--in-progress-bots [X]] [--refused-bots [X]] [--stale-participation-bots [X]]
      [--declined-bots [X]] [--refused-causes [X]] [--refusal-size-caps [X]]
      [--unrecognised-refusal-bots [X]]
      [--not-triggered] [--triage-ran] [--measured-diff-size N]
```

Every `*-bots` flag is `nargs='?'` — **comma-separated, one argument, and legal bare** (a bare
flag reads as the empty list). That is three distinct call shapes per flag, across ten flags, and
none of them is inferable from the flag name. The rejection class is the same one that hit
`manage-references set-list` in this run: flag ARITY guessed rather than read.

## Why it matters

This surface is called from a workflow that is itself reasoning about bot participation, so the
caller is composing several of these lists at once. A single mis-shaped list rejects the whole
call, and the argparse message names the flag rather than the arity, so the natural next move is
to change the VALUE rather than the SHAPE.

## Corrective rule

Read `--help` for this verb before composing the call; do not carry a remembered shape from a
sibling flag. Each `*-bots` value is ONE quoted, comma-joined string
(`--participated-bots "coderabbit,cuioss-review-bot"`), or the flag supplied bare to mean the
empty list. Never space-separate and never repeat the flag.

## Candidate improvement (for the orchestrator to judge)

Two arity classes are in play across the marketplace's `manage-*`/tool surfaces — comma-joined
single-argument flags and genuinely repeatable flags (`--fact`, `--lesson-id`) — and nothing in a
flag's NAME distinguishes them. A documented convention (or a shared help-text suffix stating the
arity) would remove the guess at the source rather than relying on per-call `--help` discipline.

## Candidate scope

Global — the arity-guessing class spans at least two distinct scripts in this single run
(`manage-references set-list` and this one), which is recurrence evidence, not a one-off.

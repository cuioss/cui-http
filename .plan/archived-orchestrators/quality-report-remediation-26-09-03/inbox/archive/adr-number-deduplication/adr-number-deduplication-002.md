envelope_version=1
sender_type=plan
sender_id=adr-number-deduplication
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T13:43:59Z

# Candidate lesson: an argparse rejection must trigger `--help`, not a second guess

## Observation

One of this run's five script-failure clusters was **two successive rejections
of the same verb**:

- `plan-marshall:workflow-integration-git switch-and-pull` — rejected for a
  missing required `--base`
- retried, and rejected again for a missing required plan-id / project-dir
  selector

The verb name was correct throughout. Only the required-argument set was
unknown, and the retry after the first rejection was another guess rather than
a read of the surface. A single `--help` after rejection #1 would have shown
both missing flags at once and made rejection #2 unreachable.

The other two clusters have the same flag-level shape (a plausible flag name
that the declaration does not carry):

- `plan-marshall:manage-solution-outline get-deliverable` — an undeclared flag;
  the verb accepts only `deliverable-number` and `plan-id`
- `plan-marshall:tools-integration-ci pr reply --pr` — canonical `--pr-number`
  (an abbreviation of the declared flag, not a synonym for it)

## Why the existing prose does not cover this

The rule's "How to apply" paragraph reads:

> When in doubt, invoke the script with `--help` first …

That is a **pre-hoc** trigger, conditioned on the agent already feeling doubt.
It is silent on the **post-hoc** case, which is the one that produced a doubled
failure here: after an `exit_code: 2` rejection, doubt is no longer a
judgement call — it has been established as fact by the interpreter. Yet
nothing in the current wording says the next action must be a surface read.
An agent that "now knows what was wrong" (a missing `--base`) can supply that
one flag and re-guess the rest, entirely consistently with the rule as written.

## Proposed durable content

State the post-rejection trigger explicitly, alongside the existing pre-hoc one:

> **One rejection is the trigger, not a retry budget.** After ANY
> `exit_code: 2` / `wrong_parameters` rejection, the next action against that
> notation MUST be `python3 .plan/execute-script.py {notation} {subcmd} --help`.
> Do not repair the single flag the rejection named and re-issue: argparse
> reports the FIRST missing argument, not the complete set, so a
> rejection-driven repair converges one flag per round-trip while a `--help`
> converges in one. A second rejection of the same verb is itself the
> reportable defect.

Also worth noting for the flag-level clusters: the tell for both
`--pr` (vs `--pr-number`) and the `get-deliverable` flag is that the *shortened
or generalized* form was chosen. Declared flags in this marketplace are
consistently typed and long-form (`--pr-number`, `--lesson-id`,
`--deliverable-number`, `--plan-id`); a bare `--pr` or `--id` is a reliable
signal that the name was reconstructed rather than quoted. `argument-naming.md`
§ "typed-ID flags" already establishes the convention — what is missing is the
inverse reading, that an UNtyped short flag in a call is evidence the caller
invented it.

## Scope judgement (for the orchestrator)

Two small, separable prose additions to
`persona-plan-marshall-agent/standards/agent-behavior-rules.md`: the
post-rejection `--help` trigger, and the untyped-short-flag tell. Neither
introduces a new rule; both close a stated-condition gap in an existing one.
Consider whether `recipe-fix-argparse-rejection` already owns the
post-rejection procedure — if so, the addition may be a one-line pointer to
that recipe from the rule, rather than new prose in the rule.

## Evidence

Three of five script-failure clusters in plan `adr-number-deduplication`
(epic `quality-report-remediation`).

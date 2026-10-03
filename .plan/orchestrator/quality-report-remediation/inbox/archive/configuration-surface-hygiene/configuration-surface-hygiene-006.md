envelope_version=1
sender_type=plan
sender_id=configuration-surface-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T09:41:47Z

component=plan-marshall:manage-solution-outline
category=anti-pattern
signal=script-failure
failure_class=argparse_rejection
recurrence_in_run=5

# Five repeats of the same `manage-solution-outline` rejection: positionals passed where flags are declared

## Observation

`manage-solution-outline` was rejected **five times in one run** with the guidance message
"Use a declared flag for get-deliverable/read". Every one of that surface's verbs takes only
declared flags — there are no positional arguments after the verb:

```text
get-deliverable  --plan-id PLAN_ID --deliverable-number N
read             --plan-id PLAN_ID
```

The failure was passing the plan id (and/or deliverable number) as bare positionals after the
verb — the shape that reads naturally in workflow prose ("read deliverable 2 of plan X") and is
rejected by argparse.

## Why it matters

The five-fold repeat inside a single run is the signal, not the individual rejection. A repeated
identical failure means the corrective information was available (the script's own error message
names the fix) and was not consumed before the next attempt. This is the provenance-before-retry
discipline applied to argparse: re-issuing a paraphrased shape after a rejection is a blind
retry.

## Corrective rule

On the FIRST argparse rejection from any `manage-*` script, stop and run
`python3 .plan/execute-script.py {notation} {verb} --help` before the next attempt. Never
re-issue a second guess at the shape. For `manage-solution-outline` specifically: every verb is
flags-only; `--plan-id` is declared on the SUBCOMMAND (after the verb), and `get-deliverable`
additionally requires `--deliverable-number N`.

## Candidate scope

Global. The specific surface is one script, but the transferable rule — *one rejection, then
`--help`, never a second guess* — belongs alongside the recurrence-signature checklist in
`persona-plan-marshall-agent/standards/agent-behavior-rules.md`. The orchestrator should check
whether an existing lesson already carries the one-rejection-then-help rule and merge into it.

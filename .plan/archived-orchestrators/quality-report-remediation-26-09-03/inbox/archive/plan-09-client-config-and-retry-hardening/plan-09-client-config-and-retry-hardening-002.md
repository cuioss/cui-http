envelope_version=1
sender_type=plan
sender_id=plan-09-client-config-and-retry-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-30T10:26:35Z

component=plan-marshall:phase-6-finalize
category=improvement
bundle=plan-marshall

# A zero-finding loop-back iteration at an unchanged HEAD should not consume the loop-back ceiling

## Observation

The finalize loop-back ceiling admits a bounded number of iterations. In this run
all three admissible iterations were spent on zero-finding re-polls of
`automatic-review` against a single, unchanged HEAD: no findings were returned, no
commit was produced, and the tree was byte-identical at every iteration. When the
FIRST real review findings finally arrived, the budget was already exhausted and
the ceiling refused them.

The findings that were refused were substantive — one of them was the NaN
validation hole that the plan had itself shipped (see the sibling candidate). The
ceiling, whose purpose is to bound genuine fix-review cycles, ended up excluding
the only genuine fix-review cycle of the run.

## The generalizable defect

The ceiling counts *iterations*, but what it is meant to bound is *fix-review
cycles*. Those are not the same thing. An iteration that

- returned zero findings, AND
- produced no commit, AND
- ran against a HEAD identical to the previous iteration's

did no work the ceiling exists to bound. It is a re-poll, not a cycle. Counting it
means a stuck upstream signal (a bot that never triggered, a CI run that never
started, a provider outage) can silently consume the entire budget that real
findings need, and the failure surfaces only later, as a refusal of legitimate work.

## Suggested corrective rule

Make ceiling consumption evidence-based rather than count-based: an iteration
consumes the budget only when it produced findings or advanced HEAD. A no-op
iteration at an unchanged HEAD should be recorded (so a stall is still visible and
still bounded by its own separate no-progress guard) but should not decrement the
fix-review budget.

If a no-progress guard is wanted, it should be a distinct, cheaper limit whose
remedy is diagnostic (why is nothing arriving?) rather than terminal (refuse the
findings).

## Filing note

The corrective surface is the `plan-marshall:phase-6-finalize` bundle, which the
epic's host repository (`cui-http`) does not own; lifting needs
`--allow-foreign-store` or carrying to the plan-marshall repository.

envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:07:54Z

component=plan-marshall:phase-3-outline
category=anti-pattern
bundle=plan-marshall

# Outline deliverable named a guard-placement mechanism the target code does not use

Q-Gate validation of the PLAN-07 solution outline (phase `3-outline`) rejected deliverable 1 —
the bracketed-IPv6 exemption in forwarded-header host validation — because the deliverable
described the exemption as being applied through a guard mechanism that does not exist at the
cited site. The outline had been written from a plausible mental model of how such an exemption
"would naturally" be wired, not from the control flow actually present in the target method.

## Solution

A claim about HOW a change is wired (an early-return guard, a branch, a delegation, a
precondition check) is a claim about current code, exactly like a line number is. Before writing
it, open the target method and read the control flow at the line the deliverable will cite, then
describe the mechanism that is there. Do not infer the mechanism from the shape of the change.

## Impact

All deep-lane outlines. Q-Gate does catch this class, but each catch costs a full outline
re-dispatch round-trip; the cheap fix is a read at authoring time.

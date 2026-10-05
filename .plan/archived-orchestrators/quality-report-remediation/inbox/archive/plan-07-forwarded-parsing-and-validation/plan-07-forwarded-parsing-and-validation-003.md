envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:08:03Z

component=plan-marshall:phase-3-outline
category=anti-pattern
bundle=plan-marshall

# Outline deliverable enumerated the wrong set of Javadoc sites to update

Q-Gate validation of the PLAN-07 solution outline (phase `3-outline`) rejected deliverable 3
because its enumeration of the Javadoc sites requiring update did not match the sites actually
present in the target type. The deliverable listed the sites the author expected the behaviour
change to touch rather than the sites that document the changed behaviour.

## Solution

An enumeration inside a deliverable ("update the Javadoc on X, Y, Z") is a closure claim: it
tells the implementor that the listed set is complete. Before writing one, re-derive the set
from the declaring source — read the type and list every member whose documentation states the
behaviour being changed. Where the set may grow, point the deliverable at the derivation rule
("every method whose Javadoc states the ordering contract") instead of restating a hand-built
list that drifts.

## Impact

All outlines. An incomplete enumeration is worse than no enumeration: the implementor obeys it
as complete and leaves the unlisted sites stale.

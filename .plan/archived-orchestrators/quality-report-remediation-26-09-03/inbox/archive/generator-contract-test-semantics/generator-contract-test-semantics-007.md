envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:24:57Z

component=cui-http-test-generators
category=bug
source_plan=generator-contract-test-semantics
source_signal=pr-comment
source_finding=e76f0b
pr=164

# Shape predicate omitted the property that made it that shape

`shapesOf` labelled a value `PURE_PATH_SHAPE` whenever it carried no
query and no fragment. An absolute URL (`https://host/api`) satisfies
that, so a hostname-family output could tick the pure-path box and the
shape-coverage test could pass without ever reaching a relative
pure-path output. Fixed by adding `isRelative(value)` to the condition.

## Rule

A shape predicate must assert the DEFINING property, not merely the
absence of the other shapes' markers. "No query and no fragment" is a
negative characterisation that a superset satisfies; "relative AND no
query AND no fragment" is the shape. Check every classification
predicate against the scenario it exists to distinguish, and ask which
non-members it currently admits.

## Impact

Same family as the non-disjoint path-family predicates on this PR: the
coverage gate reports green over a partition it cannot actually
separate.

envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:24:52Z

component=cui-http-test-generators
category=anti-pattern
source_plan=generator-contract-test-semantics
source_signal=pr-comment
source_finding=29d726
pr=164

# Family count, dispatch switch, and test grouping were three unlinked views

Across `URLLengthLimitAttackGenerator` and `ValidURLPathGenerator` the
number of families, the dispatch switch that produces them, and the
test's expected family set were three separate declarations of the same
fact. A count or dispatch change could route values incorrectly while
the generic contract assertions still passed. The fix bound them:
`ValidURLPathGenerator.PATH_FAMILY_COUNT` now bounds the type generator
AND the contract test asserts the labelled family set size against it
(the attack-generator half was covered by `ATTACK_FAMILY_COUNT`).

## Rule

Derive selector bounds and expected family counts from the
generator-owned definition, while RETAINING explicit per-family labels
in the test. The derived count catches drift; the explicit labels prove
every defined family is actually reached. Neither alone is sufficient —
a derived count with no labels passes when one family is emitted N
times, and labels with a duplicated literal drift.

## Impact

This is the general shape for any "N variants" fixture: one owner of
the cardinality, one owner of the dispatch, and a test that asserts
both the size and the identity of the reached set.

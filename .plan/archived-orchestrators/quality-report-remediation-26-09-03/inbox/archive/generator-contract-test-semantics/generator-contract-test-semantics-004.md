envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:24:42Z

component=cui-http-test-generators
category=anti-pattern
source_plan=generator-contract-test-semantics
source_signal=pr-comment
source_finding=ff8e94
pr=164

# Attack-family count duplicated as a literal in generator and test

`URLLengthLimitAttackGenerator` constructed `AttackTypeSelector(13)`
while its test independently declared `private static final int
ATTACK_FAMILY_COUNT = 13`. The test's `draw % ATTACK_FAMILY_COUNT`
arithmetic silently stops mapping to selector arms the moment the
generator's family count changes, and nothing fails.

## Rule

A count that a test must mirror from production code is derived from
that code, never restated. Expose the count as a public constant on the
owning type (`ATTACK_FAMILY_COUNT`) and have both the selector
construction and the test reference it. This is the project's existing
path instruction: "Treat a hardcoded list that must mirror a set
defined elsewhere as a defect unless it is derived from that source at
build or run time."

## Impact

Duplicated cardinality constants are drift-silent: the test keeps
passing while asserting against a stale partition.

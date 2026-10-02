envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:24:47Z

component=cui-http-test-generators
category=bug
source_plan=generator-contract-test-semantics
source_signal=pr-comment
source_finding=79fcd9
pr=164

# Non-disjoint family predicates made a coverage assertion vacuous

`ValidURLPathGeneratorTest` classified each emitted sample by matching
it against per-family regexes, but `/api/users/1/profile` matched BOTH
`PLAIN_API` and `NESTED_RESOURCE`. One sample therefore incremented two
family counters, and the aggregate "every family was emitted"
assertion could pass while a generator family was never produced at
all.

## Rule

A coverage assertion built on classification requires the classifier's
buckets to be MUTUALLY EXCLUSIVE. Either make the route predicates
disjoint (remove the shared action from the overlapping family) or have
the generator emit an explicit exclusive family identifier the test
asserts on, instead of re-deriving the family by pattern matching.

## Impact

Overlapping classifiers turn a coverage gate into a tautology — the
failure mode it exists to catch is exactly the one it cannot see.

## Adjacent note (not a defect)

The bot's ast-grep ReDoS warnings (CWE-1333) on the same file were a
false positive: the patterns are compiled once from compile-time
string-literal fields, never from user input, and carry no
nested/overlapping quantifiers. A rationale comment was added rather
than a code change.

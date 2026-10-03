envelope_version=1
sender_type=plan
sender_id=character-set-and-control-characters
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T05:19:47Z

component=plan-marshall:phase-2-refine
category=anti-pattern

# A settled answer that names two artifacts with one grammar is unverified until the spec is checked for whether it separates them

A refinement question was phrased over two artifacts at once — "cookie name and value" —
and the operator settled it with a single grammar: "cookie types use RFC 6265 cookie-octet".
That answer was recorded as settled and every downstream gate treated it as ground truth.

RFC 6265 section 4.1.1 does not define the two the same way. It defines `cookie-name` as an
RFC 7230 `token` and only `cookie-value` as `cookie-octet`. The two grammars differ in a
security-relevant way: `cookie-octet` permits `=`, `token` does not. Applying the value
grammar to the name therefore admitted a cookie name such as `a=b`, which smuggles a second
`name=value` boundary past the prefix checks that are supposed to see one boundary.

## Why every gate missed it

The defect is invisible to gates that verify against the settled answer rather than against
the spec the settled answer claims to quote:

- The Q-Gate checked the implementation matched the settled decision. It did.
- Self-review looked for internal inconsistency. There was none — the grammar was applied
  uniformly, which is exactly the defect.
- The security audit reasoned about the guard as specified.
- The full test suite asserted the specified behaviour.

An external review bot (CodeRabbit), which re-derived the requirement from the RFC rather
than from the settled answer, caught it.

## Rule

When a settled question names TWO OR MORE artifacts in one phrase ("name and value",
"request and response", "header and trailer", "path and query") and the answer supplies ONE
grammar, constraint, or limit for all of them, that answer carries an unverified premise:
that the governing spec treats them jointly. Before recording it as settled, open the spec
clause and confirm the joint treatment. If the spec assigns them separate productions,
split the settled answer into one per artifact.

The failure mode is not "the operator was wrong". It is that a jointly-phrased answer
makes the difference between the artifacts unrepresentable, so no gate downstream of the
settling has anything to compare against.

## Signal that this recurs

The class is a conjunctive question answered with a single-value settlement. It is
detectable at settle time by a cheap textual property (the question names a coordination
of two nouns; the answer supplies one value), which is what makes it a candidate for a
refine-time check rather than only a review-time catch.

## Note for the orchestrator

The concrete instance is a cui-http cookie-validation defect, but the reusable rule is a
refinement-phase discipline. The component above is a proposal, not a classification.

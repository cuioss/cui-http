envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:08:12Z

component=pm-dev-java:javadoc
category=bug
bundle=pm-dev-java

# Javadoc asserted an ordering the method does not guarantee

The automated review bot (CodeRabbit) on PR #237 flagged the Javadoc of
`ContextPaths.containsUnsafePathConstruct` for stating an ordering claim about how the method
evaluates its checks that the implementation does not actually guarantee. The claim was written
alongside the implementation and described the order the author had in mind, not the order the
code commits to. It was fixed in-run via a loop-back fix task.

## Solution

Do not document an ordering, precedence, or short-circuit behaviour unless the implementation
guarantees it AND a caller can depend on it. For a predicate whose checks are internally
reorderable, document WHAT it returns, not the sequence in which it decides. When an ordering
genuinely is part of the contract, add a test that fails if the order changes — otherwise the
Javadoc is an unenforced claim that drifts on the first refactor.

## Impact

All Javadoc on predicate and validation methods. This slipped past authoring, the pre-submission
self-review, and the quality gate, and was caught only by the review bot — the in-repo gates do
not compare Javadoc behavioural claims against the method body.

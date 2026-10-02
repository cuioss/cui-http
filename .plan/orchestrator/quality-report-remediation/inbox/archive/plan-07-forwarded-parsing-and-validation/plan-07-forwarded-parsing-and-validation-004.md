envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:08:07Z

component=plan-marshall:phase-3-outline
category=anti-pattern
bundle=plan-marshall

# Outline deliverable carried an inverted line citation

Q-Gate validation of the PLAN-07 solution outline (phase `3-outline`) rejected deliverable 5
because its line citation was inverted — the referenced line numbers named the wrong side of the
construct being described, so an implementor following the citation would have edited the
opposite branch.

## Solution

A line citation is only trustworthy if it was read off the file at the moment of writing. Re-open
the file and confirm the cited line holds the construct the prose describes, including which
branch or which operand. Inverted citations are especially dangerous because the cited region is
real — the citation resolves, it just resolves to the wrong thing, so an implementor's
"citation checks out" glance passes.

## Impact

All outlines. Together with the sibling PLAN-07 Q-Gate findings (invented method name, wrong
mechanism, wrong Javadoc site set), this is one recurrence class: outline prose asserting facts
about current code that were never read off current code. Four of four PLAN-07 Q-Gate findings
were of this class.

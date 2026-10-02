envelope_version=1
sender_type=plan
sender_id=build-artifacts-and-ci-safety
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-06T09:05:58Z

component=plan-marshall:persona-security-expert
category=anti-pattern
title=A hardening change is itself an unreviewed change - re-review the fix, not just the hole it closed

# A hardening change is itself an unreviewed change — re-review the fix, not just the hole it closed

The run's first security fix tightened the benchmark workflow's egress
`allowed-endpoints`. That fix introduced a wildcard entry,
`*.blob.core.windows.net`, which CodeRabbit subsequently flagged as too broad.

The hole was real and the fix was directionally right. The defect is that the
remediation itself widened the trust boundary, and nothing in the run's own
process re-examined it — it took an external reviewer to catch a hole inside a
change whose entire purpose was closing holes.

## Why this recurs

A change labelled "security fix" carries an implicit presumption of safety that
an ordinary change does not. That presumption is the mechanism of the defect:

- The author's attention is on the *closed* hole, so the *new* surface the fix
  creates is never independently assessed.
- Allowlist and permission edits are exactly the shape most prone to it, because
  the natural way to make a denied thing work is to widen the predicate until it
  matches, and a wildcard always matches.
- A too-broad allow entry is silent. It never fails, so no test, gate, or CI
  signal will ever surface it.

## Rule

Treat a hardening change as a first-class change to the trust boundary and
review it on its own terms:

- After writing any allowlist / permission / scope edit, enumerate what the new
  entry admits **beyond** the specific thing it was added for. If the answer is
  "an entire vendor domain", it is too broad.
- Prefer enumeration over wildcards. Where the concrete set is knowable — as it
  was here, the same workflow already enumerates 20 concrete
  `productionresultssaNN` hosts — enumerate it. A wildcard is an admission that
  the set was not established, not a shorthand for it.
- Where a wildcard is genuinely unavoidable, record why the set could not be
  enumerated, so the next reader inherits the reasoning instead of the pattern.

The general form: **the class of defect a change exists to remove is the class it
is most likely to reintroduce**, because the author is operating in exactly that
space with the guard of unfamiliarity lowered.

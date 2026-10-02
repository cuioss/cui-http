envelope_version=1
sender_type=plan
sender_id=character-set-and-control-characters
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T05:19:52Z

component=plan-marshall:persona-code-reviewer
category=bug

# A guard written for one member of a type set and not extended to its sibling set — twice in one run, caught by two different gates

Two independent defects in the same finalize run had the identical shape: a guard was
written against one set of `ValidationType` values and was never extended to the sibling
set that the adjacent code already covered.

- The security audit found the C0 control-character guard covering the HEADER types but
  not the COOKIE types.
- CodeRabbit found the decoded-C1 check covering the RAW path but not the DECODED path.

In both cases the tell was the same and was one line away: the immediately adjacent guard
already used the broader predicate. The narrow guard was not a considered scoping decision;
it was a guard written while thinking about one case, sitting directly beneath a guard that
had already been broadened.

## Rule

When a guard's condition tests membership in an enumerated set (a `ValidationType` set, a
phase set, a kind enum, a path-variant pair like raw/decoded), compare its predicate against
the predicates of the guards immediately surrounding it in the same method or block. A guard
that is STRICTLY NARROWER than its neighbour is a defect candidate until the narrowing is
justified in a comment or a test name. "The adjacent guard one line above already uses the
broader predicate" is the highest-yield tell available, and it needs no cross-file analysis.

## Why this is worth mechanising

Two hits in one run, found by two different gates, is a recurrence signal rather than a
coincidence. The check is structural and local — it compares sibling predicates within one
block — so it is a plausible deterministic self-review candidate rather than something that
needs a reviewer's domain knowledge. Neither hit required knowing what C0 or C1 characters
are; both required only noticing that two adjacent guards disagreed about their scope.

## Non-goal

This is not "always use the broadest predicate". A genuinely narrower guard is legitimate.
The rule is that the asymmetry must be VISIBLE as a decision — justified in the code or
pinned by a test — rather than being the residue of writing one case at a time.

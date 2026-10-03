envelope_version=1
sender_type=plan
sender_id=doc-inventory-and-navigation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T07:28:58Z

# Candidate lesson: the read-only-fact-source declaration convention is unstated, so every deliverable relitigates it

## Observation

Four of this plan's eight Q-Gate findings are the SAME convention gap, raised
once per deliverable:

| Finding | Phase | Deliverable | Disposition |
|---------|-------|-------------|-------------|
| `bc21d4` | 3-outline | 4 and 6 | fixed (Approach point 6 added) |
| `73b37c` | 4-plan | 1 | accepted — "convention gap in the record only" |
| `a23ea3` | 4-plan | 2 | accepted — "same class as 73b37c" |
| `29585e` | 4-plan | 5 | accepted — "same class as 73b37c" |

The underlying tension is real and recurring: a documentation deliverable whose
success criterion is verified against `.java` sources must either (a) declare
those sources as `(read)` affected files — which flips the deliverable out of the
`documentation_only` bucket and pulls an unwarranted `module_testing` profile
behind a docs-only change — or (b) omit them, losing the `files_exist` presence
guarantee the outline elsewhere relies on.

The plan resolved it correctly (option b, with the deliverable's own verification
step asserting the fact source by name and treating an absent name as a refuted
premise). But it resolved it FOUR TIMES, and three of those were validator
findings triaged individually after the fact.

## Why this is epic-level

Half this plan's Q-Gate volume was one unstated convention. That is a cost the
next docs-with-code-facts plan in this epic pays again identically. The judgement
belongs in a standard, not in four accepted findings.

## Candidate rule

Codify the convention once: read-only `.java` fact sources cited by a
documentation deliverable's success criteria are NOT declared as affected files
(bucket avoidance), and in exchange each such deliverable's verification step
MUST assert its fact sources by name at execute time. Then either teach the
`scope_criterion_validator` the exemption, or make the outline template carry the
rationale so the validator sees a declared convention rather than an omission.

## Evidence

- Q-Gate findings `bc21d4` (3-outline), `73b37c` / `a23ea3` / `29585e` (4-plan)
- Resolution text on all three 4-plan findings is verbatim "convention gap in the
  record only" / "same class as 73b37c"
- The fix that landed: `solution_outline.md` Approach point 6

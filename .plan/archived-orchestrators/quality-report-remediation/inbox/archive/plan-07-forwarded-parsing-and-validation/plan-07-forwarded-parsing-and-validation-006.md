envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:14:55Z

# Candidate lesson: outline citation named the KEPT half of an adjacent pair as the half to delete

**Source**: Q-Gate finding `a46f93` (3-outline, type `triage`, severity `warning`, resolution `taken_into_account`)
**Plan**: plan-07-forwarded-parsing-and-validation
**Component under edit**: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java`

## Observation

Deliverable 5 instructed an implementer to delete "the stale clause in `resolveContextPath`'s
guard-ordering comment (current lines 719-724)". Re-anchored against HEAD, the stale clause was at
lines **715-717**, while **719-724** was the token-selection-before-guards rationale the same
deliverable explicitly required to be KEPT. The two ranges were inverted: an implementer following
the citation literally would have deleted the accurate half and preserved the stale one.

The prose quote in the deliverable disambiguated the intent, so the defect was recoverable by a
careful reader — but only by ignoring the one thing the plan told the reader to trust (a re-derived
line citation).

## Why this is lesson-bearing

The failure class is **adjacent-range citation inversion**: two consecutive paragraphs in one comment
block, one to delete and one to keep, cited by line range only. Nothing in the citation form makes
an inversion detectable at the citation site. The corrective that closed the finding is generalisable:

- Cite BOTH ranges when a deliverable acts on one member of an adjacent pair, and state the direction
  explicitly in one line ("715-717 is deleted, 719-724 survives").
- Quote a distinguishing fragment of the KEPT range, not only of the deleted one, so the two cannot
  be swapped silently.

## Suggested disposition (orchestrator judges)

Possible `plan-marshall:phase-3-outline` authoring rule, or an outline Q-Gate detector for
"deliverable deletes one of two adjacent ranges in the same block". Cross-plan relevance is likely:
the epic's other plans also re-anchor cited line ranges against HEAD.

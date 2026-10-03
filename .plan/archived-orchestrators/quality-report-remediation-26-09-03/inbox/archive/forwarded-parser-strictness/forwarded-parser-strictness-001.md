envelope_version=1
sender_type=plan
sender_id=forwarded-parser-strictness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T12:29:00Z

# Candidate lesson: auditing the diff misses what the diff left alone

## Signal provenance

`signal_qgate_pending_count = 6` — three of the six findings this plan produced after
implementation were places the plan did **not** change but should have.

## Observation

The plan tightened a family of related call sites. Three defects were found *after*
implementation, all of the same shape — a member of the family that the change skipped:

1. `resolvePort` was the one `resolve*` method not receiving the `forwarded` argument
   while every sibling `resolve*` method did.
2. `parseHostPort` never validated bracket **contents**, while `parseChainEntry` — its
   sibling parser over the same syntax — always had.
3. The user guide's field enumeration was not updated alongside the Java Javadoc that
   enumerates the same fields.

Two security audits and one pre-submission self-review passed over all three. Every one
of those passes verified that what **changed** was correct, and each was correct in that
verdict. None asked what the change left alone.

## Reusable rule (candidate)

When a change makes one member of a family of call sites stricter, the review unit is the
**whole family**, not the touched subset. Concretely, before signing off:

- Enumerate the family (siblings by name prefix, siblings by shared parser/validator,
  the doc surface that enumerates the same field set).
- For each member NOT in the diff, state why it does not need the same treatment.
- An unexplained absent member is a finding, not a non-event.

This is a review-scoping rule, not a code rule: the failure mode is that a diff-scoped
audit is structurally incapable of seeing it, however thorough the audit is on its own
terms.

## Suggested routing (orchestrator to judge)

- Kind: reusable review discipline; likely global rather than epic-local.
- Candidate component: `plan-marshall:persona-code-reviewer` (or the pre-submission
  self-review surface) — the audit-scoping step is where the rule has to bite.
- Category: `anti-pattern`.

## Plan context

- Plan: `forwarded-parser-strictness` (epic `quality-report-remediation`)
- Landed as PR #162, merged 7d43646.

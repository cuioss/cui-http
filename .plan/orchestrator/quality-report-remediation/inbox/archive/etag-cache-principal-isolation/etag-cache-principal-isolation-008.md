envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:54Z

# Candidate lesson: all-six-dimensions-100% confidence score was justified, not inflated — the evidence that made it defensible

Source signal: Q-Gate finding `8e8b01` (phase 2-refine, resolved `taken_into_account`).
Component: `plan-marshall:phase-2-refine`.

## What happened

The refine pass scored all six weighted confidence dimensions (correctness, completeness,
consistency, non-duplication, ambiguity, module_mapping) at 100, which the Q-Gate flags as
suspicious by default. The finding was reviewed and resolved `taken_into_account` rather than
`fixed`, on the strength of concrete evidence: the request already carried a prior read-only
code-verification pass with line-cited claims re-checked at `d385aa3` and confirmed unmoved at
HEAD; the refine pass then independently re-spot-checked four of the highest-impact claims against
current source; and every remaining open question was deferred as an explicit
verify-at-outline/verify-first clause with a pre-planned refutation-and-rescope path rather than
left ambiguous.

## Corrective rule

A perfect confidence score is defensible only when the justification names (a) the independent
verification that was actually performed, with revisions, and (b) what happens to each deferred
question if it turns out false. "All dimensions verified" without those two is the inflation the
Q-Gate exists to catch. The pattern above is a reusable template for a legitimate
`taken_into_account` disposition on this check.

## Generalisation for the epic

Low-severity / informational. Recorded mainly so the epic has a worked example of what a
LEGITIMATE all-100 justification looks like, to contrast against the inflated case the check is
aimed at.

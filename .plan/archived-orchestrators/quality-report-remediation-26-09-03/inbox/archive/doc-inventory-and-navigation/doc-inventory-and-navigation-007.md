envelope_version=1
sender_type=plan
sender_id=doc-inventory-and-navigation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T07:30:19Z

# Candidate observation (positive): the review_commitments reconcile guard caught a live regression

## Observation

Not a defect — a guard firing correctly in the wild, recorded so the epic can
count it as validated rather than merely present.

During the simplify re-run, a near-duplicate line was identified and trimmed. The
`review_commitments` reconcile then showed that the trimmed line was a FIXED
REVIEW COMMITMENT made earlier in this same run — i.e. text that existed
specifically because a reviewer had asked for it, and whose apparent redundancy
was the point. The trim was reverted.

Without the reconcile, the run would have silently deleted its own fix and shipped
a PR whose review threads claimed a change the merged tree no longer contained.

## Why this is worth transmitting

Two things:

1. **The guard has a confirmed catch.** The simplify pass and the review-response
   pass have genuinely conflicting objectives — one removes redundancy, the other
   often ADDS it deliberately — and the reconcile is the only thing that
   arbitrates. It worked, on a real conflict, in-run.
2. **The conflict is structural, not incidental.** Any plan that runs simplify
   AFTER responding to review comments in the same run is exposed to it. This run
   is evidence the exposure is real, not theoretical.

## Candidate rule

Keep the `review_commitments` reconcile mandatory whenever a simplify/dedup pass
runs after review responses in the same run, and treat a revert triggered by it
as a SUCCESS signal to be reported, not an anomaly to be suppressed. Consider
surfacing the catch count in the run's report so the guard's value stays visible
and it does not get optimised away as "never fires".

## Evidence

- simplify re-run trim, followed by `review_commitments` reconcile, followed by
  revert — all within this plan's finalize band.

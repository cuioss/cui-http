# PLAN-21: Fail-Closed Sweep for Guarded Assertions — SUPERSEDED

epic: quality-report-remediation
workstream: WS-04

> ⛔ **SUPERSEDED 2026-09-01, before launch. Merged into
> [`PLAN-20-test-evidence-integrity.md`](PLAN-20-test-evidence-integrity.md) at operator request.**
> This file is retained as the audit record of the merge — no spec file is ever deleted. Its queue row
> carries status `superseded`.

## Where the work went

Every deliverable of this spec is carried by PLAN-20, re-derived rather than concatenated:

| This spec's deliverable | Destination in PLAN-20 |
|---|---|
| 1. Triage the 12 candidate sites | deliverable 3 (triage + convert, collapsed) |
| 2. Convert every genuine instance to the `matched` form | deliverable 3 |
| 3. Prove each conversion by falsification | deliverable 4, **widened to span both halves** |
| 4. Re-run the sweep and record the residual | deliverable 5(b) (record reconciliation, collapsed) |

Every claim label, the 12-site candidate list, both confirmed classifications
(`UnicodeNormalizationAttackTest`:240 genuine, `NullByteURLGeneratorTest`:74 false positive), both
verify-first clauses and the full Expected Surface were carried across verbatim. **Nothing was
dropped in the merge**; the deliverable count fell from 4 to 0-of-its-own because the destination
collapsed overlapping steps rather than summing them.

## Why the merge is not weak

The two source plans are the same defect class — **a test suite reporting green over assertions that
never ran**. This spec's half is a *skipped* assertion (a guard prevents the body from running);
PLAN-20's original half is a *missing* one (the TLS path cannot be exercised, so a shipped security
control's end-to-end evidence does not exist). They share one validating technique — falsification —
which is what makes the merge genuine rather than two plans in a bag, and PLAN-20's deliverable 4
states that explicitly.

## Rationale for retaining this file

The scope-bloat guard is evaluated against the **re-derived** count, and PLAN-20 lands at five
deliverables — under the guard. Recording that here, beside the mapping table above, is what lets a
later reader confirm the merge collapsed overlaps rather than hiding a nine-deliverable plan behind a
count of five.

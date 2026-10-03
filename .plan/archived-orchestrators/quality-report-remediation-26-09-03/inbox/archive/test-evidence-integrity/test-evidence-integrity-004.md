envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T14:02:14Z

# Candidate lesson: tightening a guard changes its sample-size requirement — a fail-closed zero-admission check over an unadjusted sample is a dice roll, not a regression detector

## Signal class

Follow-on from Q-Gate finding `adea8c` / the family-exact guard remediation. Reproduced twice during the run.

## What happened

The six guards were tightened from broad text filters to **family-exact predicates**. That is the correct fix, and it had a measurable consequence: each guard's admission rate dropped to roughly **1 in 15** of generator output.

The generators were still configured with the pre-existing sample counts (25–30 per test). At that admission rate, the observed probability of a run admitting **zero** inputs was about **15–20% per guard per run**, and it was reproduced twice.

That matters because the natural next hardening step — make a guard **fail loudly when it admits nothing**, so a silently-empty test cannot pass green — turns a 15–20% dice roll into a 15–20% flaky-failure rate. A fail-closed guard over an unadjusted sample is not a regression detector; it is a coin.

## Why it is generalisable

**Guard tightness and sample size are coupled, and the coupling is easy to miss because the two live in different files.** Whenever a predicate is narrowed, the expected admission count per run falls proportionally, and every downstream property that depends on "at least one admission" silently loses power.

The rule:

> A fail-closed zero-admission check requires a sample large enough that a zero is evidence of a REGRESSION rather than evidence of SAMPLING. Narrowing a predicate without re-deriving the sample size converts a correctness guard into a flake.

Concretely, with per-draw admission probability `p`, the chance of zero admissions in `n` draws is `(1 - p)^n`. At `p = 1/15`, `n = 30` gives ≈13%; reaching a ≈1% false-failure budget needs `n ≈ 67`, and ≈0.1% needs `n ≈ 100`. The arithmetic is trivial — the failure is that nobody does it at the moment the predicate is narrowed.

## Proposed corrective actions

- **Make the coupling an explicit step of any guard-tightening change**: measure the post-tightening admission rate, then re-derive the sample count for the target false-failure budget before adding any zero-admission assertion.
- **Do not add a fail-closed zero-admission assertion until the sample has been resized.** Until then, prefer a *reported* zero (visible, non-blocking) over a *failing* zero — visibility without flake, which is the same visibility-is-not-validity boundary the sibling candidate lesson draws, applied in the other direction.
- **Record the admission rate and the chosen `n` next to the guard**, so a later tightening can see what the current `n` was sized for.

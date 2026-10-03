envelope_version=1
sender_type=plan
sender_id=plan-05-forwarded-trust-boundary
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T20:09:17Z

# Candidate lesson: a change whose purpose is to distinguish two states must enumerate every way the code can collapse them

## Signal

Source: `signal_automated_review_count` — a CodeRabbit finding on PR #155, remediated
in-run. The phase-6 security audit did not catch it.

## Observation

Deliverable FW-2 existed to close a fail-open: when the `Forwarded` header family and
the `X-Forwarded-*` header family disagree, the resolver must fail closed rather than
silently trusting the de-facto source.

The implementation made the resolver fail closed on disagreement — and then reopened the
same fail-open inside itself. `parseForwarded()` collapsed two distinct inputs into one
output: "header absent" and "header present but rejected by `sanitize()`" both produced
the same empty `Parsed` value. `reconcileSources` therefore could not tell them apart,
read the second as the first, and fell back to the de-facto source — which is precisely
the fail-open FW-2 was written to close.

## Root cause shape

The deliverable's whole premise was a two-state distinction (sources agree vs. sources
disagree). The underlying data has **three** states, not two:

1. absent
2. present and valid
3. present and invalid

State 3 was mapped onto state 1 by the parser's return type, so the distinguishing logic
downstream had no way to see it. The bug class the change existed to eliminate survived
inside the change's own implementation, one layer below where the author was looking.

A security audit that reviews the *distinguishing logic* will pass, because that logic is
correct — the defect is in what the logic is handed.

## Candidate directive

When a change's entire point is to distinguish two states, enumerate every path by which
the code can collapse them before declaring the change complete. In particular, check
whether any parser, accessor, or normalization step on the input path maps a rejected /
malformed value onto the same representation as an absent one. Absent, present-and-valid,
and present-and-invalid are three states; a return type that can only express two of them
will silently re-introduce the fail-open the change was meant to close.

## Evidence

- Deliverable FW-2 (fail closed when `Forwarded` and `X-Forwarded-*` disagree).
- `parseForwarded()` returning an empty `Parsed` for both the absent-header and
  `sanitize()`-rejected cases; `reconcileSources` falling back to the de-facto source.
- Found by CodeRabbit on PR #155, not by the phase-6 security audit — which is itself
  part of the signal: the audit's lens was the reconciliation logic, not the parser
  contract feeding it.

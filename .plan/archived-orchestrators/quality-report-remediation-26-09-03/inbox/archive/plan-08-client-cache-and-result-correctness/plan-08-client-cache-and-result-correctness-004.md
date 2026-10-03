envelope_version=1
sender_type=plan
sender_id=plan-08-client-cache-and-result-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T20:13:52Z

# Candidate lesson: falsification is what separates a real regression test from a vacuous one

## What happened

This run produced a defect whose test passed for the wrong reason: the unsafe-method 304 test
asserted `INVALID_CONTENT`, and the generic non-2xx error path also returns `INVALID_CONTENT`, so
the test was green while the branch it was written for was unreachable. A green test proved nothing.

After that, three fixes in this run were validated by **falsification** rather than by "the new test
passes":

1. Write the fix and its test.
2. Temporarily **restore the pre-fix form** of the production code.
3. Confirm the new test now **fails** — and that it fails for the stated reason, not incidentally.
4. Restore the fix; confirm green.

The recorded result for the `e87f76a` eviction fix is explicit: *"Verified by falsification
(23 passed / 1 failed against the pre-fix form)."* One test, and exactly one, flipped. That number
is the evidence: it shows the new test is sensitive to precisely the change being claimed, and that
no other test in the suite was silently depending on the old behaviour.

## Why it is a candidate lesson

The rule is cheap to state and was decisive here:

> A regression test that has never been observed to fail has not been shown to test anything.

Falsification catches the two failure modes that a passing test cannot distinguish itself from:

- **The vacuous test** — it asserts something already true, or reaches its assertion through a
  different code path (exactly the unsafe-method 304 case). Restoring the pre-fix form leaves it
  green, which is the tell.
- **The over-broad test** — it fails against the pre-fix form, but so do five others, meaning the
  change was wider than claimed. The pass/fail *count* is as informative as the fail itself.

It is also cheap: restore, run, restore back. In this run it cost one build per fix and was the only
technique that distinguished a real regression test from a vacuous one — including for the very
defect class the plan was remediating.

Worth considering as a standing obligation for any fix whose test is the sole evidence the defect is
gone: record the falsification result (pass/fail counts against the pre-fix form) alongside the fix,
so a later reader can tell a proven regression test from an assumed one.

## Evidence

- Plan: `plan-08-client-cache-and-result-correctness`, PR #166.
- Falsification recorded on the `572fcb` -> `e87f76a` fix: 23 passed / 1 failed against the pre-fix form.
- Counter-example that motivated it: finding `26ab48` / `788616` — a test that passed against
  unreachable code because the generic error path coincidentally returned the asserted category.

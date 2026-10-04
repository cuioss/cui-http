envelope_version=1
sender_type=plan
sender_id=plan-10-attack-test-mechanism-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-03T21:22:23Z

# Candidate lesson: verification-feedback rejects producer=ci-verify-policy (unknown_producer)

**Source signal**: ci-verify red-CI triage, loop-back iteration 1 (decision log bfc15f, WARNING).
**Component**: plan-marshall:plan-marshall (workflow/verification-feedback.md) vs plan-marshall:phase-6-finalize (ci-verify step).

## What happened

On red CI, the ci-verify step dispatched verification-feedback with `producer=ci-verify-policy`. verification-feedback returned `unknown_producer` because that producer is not in its accept-set, so no triage ran. The finalize dispatcher had to diagnose inline from the returned evidence (Sonar gate, text:S6389 at CookieChaosAttackTest.java:100), apply the fix, commit 484282b, resolve findings dff8dc/2a9f1b/421d8e/1d003d/87a89d, and record the loop_back by hand.

## Candidate rule

Producer/consumer contract drift: the producer token ci-verify emits must be in verification-feedback's accept-set (or ci-verify must emit an accepted token). A test that pins the ci-verify producer value against the verification-feedback accept-set would catch this.

## Classification hint

Marketplace defect (plan-marshall bundle), not project-scoped. Likely a global lesson / upstream bug report.

envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:36:02Z

# Candidate lesson: hard-coded 60s timeout on `gh auth status` misreports multi-account auth as "Not authenticated"

## What happened

The phase-6-finalize `create-pr` step failed once. The `ci` script's preflight ran
`gh auth status`, which took approximately 60.6 seconds against a hard-coded 60-second timeout.
The timeout fired and the step reported **"Not authenticated"** — while authentication was in fact
completely fine. A later retry succeeded and the PR was created normally.

## Root cause

Two conditions combine:

1. The environment has **two configured `gh` accounts**. `gh auth status` contacts each host in
   turn, and there is a fixed gap of roughly 60 seconds between the two probes, so total wall time
   lands just over 60s essentially every time.
2. The `ci` script's auth preflight uses a **hard-coded 60-second timeout**, which sits almost
   exactly on that duration. The result is a coin-flip, not a stable failure — which is why the
   retry succeeded and why this reads as flaky rather than broken.

The deeper defect is the **error mapping**, not the threshold. A timeout on the auth probe is
mapped to the semantic verdict "Not authenticated". Those are different facts: one says the
credential was checked and rejected, the other says the check did not finish. Reporting the second
as the first sends the operator to re-authenticate a working credential, and makes an infrastructure
timing problem look like a configuration problem.

## Candidate rules

1. A probe timeout must surface as its own outcome ("auth check did not complete within Ns"),
   never collapsed into the negative verdict of the thing being probed. This is the same
   *which-kind-of-zero* discipline the `manage-lessons` / `plan-orchestrator` store-resolution
   surfaces already enforce, applied to an external-CLI preflight.
2. The auth-preflight timeout should scale with the number of configured accounts, or at minimum
   not be pinned to a value that a normal two-account configuration reliably straddles.

## Suggested surface

`plan-marshall:tools-integration-ci` — the `ci` script's `gh auth status` preflight. The value is
hard-coded rather than resolved from config, so an operator hitting this has no knob to turn.

## Provenance

- Plan: plan-12-generator-implementation-correctness
- Observed once during phase-6-finalize `create-pr`; retry succeeded
- Measured probe duration: ~60.6s against a 60s limit

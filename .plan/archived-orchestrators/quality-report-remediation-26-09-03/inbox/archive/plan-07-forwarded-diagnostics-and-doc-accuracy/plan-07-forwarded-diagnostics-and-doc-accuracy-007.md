envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-diagnostics-and-doc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:27:13Z

component=plan-marshall:tools-integration-ci
category=bug
bundle=plan-marshall

# UNCORROBORATED: a subprocess timeout reported as an authentication failure, and a local failure reported as a remote CI verdict

## Evidence status — read this first

⚠️ **This candidate is NOT corroborated by this plan's own record.** It is forwarded because
the code path it names is cheap for the orchestrator to check directly, and because the failure
shape it describes is high-value if real. It should be verified against
`tools-integration-ci` source before being lifted into a lesson; do not treat the account below
as an observed fact of this run.

What I checked and did NOT find, in `plan-07-forwarded-diagnostics-and-doc-accuracy`:

- work.log (276 entries) — no auth-failure, `gh auth` or timeout marker anywhere.
- decision.log (115 entries) — no `ci_complete_precondition` record, no `ci_final_status`.
- script-execution.log (retained tail, 40 entries) — the `ci` calls it retains all succeeded
  (e.g. `19:17:32Z … ci --plan-id (1.29s)`).

The only `ci` failure this plan's record does carry is unrelated: `13:27:15Z`
`script_failure notation=plan-marshall:tools-integration-ci:ci exit_code=2
failure_kind=argparse_rejection` — `--plan-id` placed after the verb on the router. The
script-execution log is tail-retained, so an earlier occurrence may simply have aged out; that
is a possible explanation for the absence, not evidence for the claim.

## The reported observation (unverified)

`check_auth_cli` in `tools-integration-ci` is reported to collapse a subprocess **TIMEOUT**
(exit 124) into the message "Not authenticated. Run 'gh auth login' first." On the machine in
question a second, stale `gh` account made `gh auth status` take 60.65s against a 60s wrapper
timeout, so every `ci` / `github_ops` call failed with a message naming the wrong subsystem.

The reported knock-on: `ci_complete_precondition` then reported `ci_final_status: timeout`
alongside `wait_outcome: completed` and an empty `failing_checks[]` — i.e. a **local** auth/timeout
failure surfacing as a **remote CI verdict**, while CI was in fact green.

## Why it would be worth a lesson if confirmed

Two distinct error-attribution defects, both generic:

1. **Collapsing a timeout into a semantic failure.** "The probe did not answer in time" and
   "the probe answered and said you are logged out" are different facts with different remedies
   (`gh auth login` fixes only one of them). Any `except TimeoutExpired` that falls into the
   same branch as a non-zero exit destroys the distinction, and the resulting message actively
   misdirects — the operator is sent to re-authenticate a session that is fine.
2. **A local failure escaping as a remote verdict.** `ci_final_status: timeout` with
   `wait_outcome: completed` and empty `failing_checks[]` is internally incoherent: nothing
   timed out remotely, the wait completed, and no check failed. A precondition that cannot
   reach the provider must report *its own* inability, never synthesize a status for the remote
   system. Reporting green CI as a CI timeout is the same fail-open class as the scope-creep
   `could_not_look` result — a could-not-look rendered as a measurement.

Also worth noting: a >60s `gh auth status` caused by a stale second account is a plausible and
recurring environment condition, so the timeout branch is reachable in normal operation rather
than only under fault injection.

## Suggested verification

Read `check_auth_cli` in `tools-integration-ci` and confirm whether its `TimeoutExpired` handler
shares a branch with the non-zero-exit handler; and confirm whether `ci_complete_precondition`
can emit `ci_final_status` from a path that never reached the provider.

envelope_version=1
sender_type=plan
sender_id=post-decode-character-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T11:26:10Z

component=plan-marshall:persona-plan-marshall-agent
category=anti-pattern
bundle=plan-marshall

# An argparse rejection read as an environment/auth failure sends the operator after a credential problem that does not exist

## What happened

A dispatched leaf invoked the CI router with the router-scoped `--plan-id` placed
AFTER the subcommand verb:

```text
plan-marshall:tools-integration-ci:ci pr --plan-id {plan_id} ...
```

The `ci` router declares `--plan-id` as a top-level flag consumed BEFORE the verb, so
argparse rejected the call (`unrecognized arguments`, exit 2). The leaf did not read the
rejection. It classified the non-zero exit as a provider-authentication problem, returned
`status: loop_back` with `reason=unknown_participation_verdict`, and recommended that the
operator re-authenticate the CI provider.

The orchestrator disproved the diagnosis by re-running the same verb with the flag in the
position the parser requires; the call succeeded immediately. No credential was ever
involved.

## Why this generalizes

The failure is not about CI, and not about this one flag. It is a **diagnosis** failure with
a specific and recurring shape:

- A `manage-*` / tool script exits non-zero.
- The caller maps the non-zero exit to the most semantically plausible *domain* cause for
  that script (auth for a CI tool, network for a fetch tool, permissions for a file tool).
- The actual cause — a parser rejection that never reached the script body — is discarded
  unread.

The domain-plausible misdiagnosis is strictly worse than a bare "call failed", because it is
actionable and wrong: it produces a remediation instruction (re-authenticate, retry with
network, fix permissions) that an operator will follow, against a system that has nothing
wrong with it. A bare failure report would at least have surfaced the stderr.

The distinguishing evidence is always present and always cheap to read: **exit code 2 plus a
`usage:` line or an `unrecognized arguments` / `invalid choice` message on stderr is an
argparse rejection.** The script body did not run. No conclusion about the script's *domain*
(auth state, network state, provider health) is derivable from it, because nothing in that
domain was ever touched.

## Proposed rule

Before a caller may attribute a non-zero script exit to an environment, credential, or
provider cause, it must first rule out a parser rejection:

1. Read stderr. If it carries `usage:`, `unrecognized arguments`, `invalid choice`, or
   `the following arguments are required`, the call never reached the script body. The
   verdict is `invocation_error`, and the remedy is the invocation, never the environment.
2. A structured return (`loop_back`, `blocked`) that recommends an environment remediation
   must name the stderr evidence that supports it. An unquoted diagnosis is not a diagnosis.
3. A dispatched leaf that cannot distinguish the two returns the rejection verbatim and lets
   the orchestrator classify. Guessing a domain cause is the failure mode; returning the raw
   evidence is never one.

## Impact

Every dispatched leaf that shells out to a script and reports a verdict upward. The blast
radius is proportional to how confident the wrong diagnosis sounds: this one would have sent
an operator into a provider's credential settings for a flag-position typo.

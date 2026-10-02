envelope_version=1
sender_type=plan
sender_id=doc-inventory-and-navigation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T07:29:20Z

# Candidate lesson: five distinct script notations rejected at argparse in one run

## Observation

Seven `script_failure` markers across five DISTINCT script notations, every one
`exit_code=2 failure_kind=argparse_rejection`, all in this single plan run:

| Notation | Signature |
|----------|-----------|
| `plan-marshall:manage-execution-manifest:manage-execution-manifest` | unregistered verb — accepted set is `compose, lanes, read, reconcile, record-step, refire-report, step-params, validate, validate-loadable` |
| `plan-marshall:manage-status:manage-status` | unregistered verb — the router listed 25+ registered verbs back |
| `plan-marshall:workflow-integration-github:github_ops` | unregistered verb — accepted set is `branch, checks, issue, pr, repo` |
| `plan-marshall:workflow-integration-github:github_pr` | `unrecognized arguments: --plan-id doc-inventory-and-navigation` — the script declares NO `--plan-id` at any level; fired 3x in 1 second |
| `plan-marshall:automatic-review:review_completeness` | flag-level rejection on `check`; fired 3x across 3 separate finalize passes (22:14, 05:45, 06:19) |

Two recurrence signatures dominate, and both are already documented in
`persona-plan-marshall-agent` § "Never invent script subcommands":

1. **verb paraphrase** — a plausible-sounding verb read out of surrounding
   workflow prose rather than quoted from the script's registered set.
2. **`--plan-id` appended by rote** to a script whose parser never declares it.

## Why this is epic-level

`review_completeness check` failed identically on THREE separate finalize passes
hours apart. That is not a one-off slip; it is a workflow doc and a live argparse
surface that disagree, re-encountered every time the step re-fires. Likewise the
`github_pr --plan-id` rejection fired three times inside one second — a retry loop
around a deterministically-invalid invocation, which burns the retry budget
without any possibility of a different outcome.

## Candidate rule

Two separable asks for the epic:

- **Determinism**: an `argparse_rejection` (exit 2) is NOT retryable. Retrying it
  can never succeed. The dispatch/retry layer should classify `exit_code=2 /
  failure_kind=argparse_rejection` as terminal-for-this-invocation and surface it
  immediately instead of re-issuing the identical argv.
- **Drift**: where a workflow doc's canonical-invocation block and the script's
  live `--help` disagree, the doc is the defect. `review_completeness check` and
  the `github_pr` `--plan-id` position are two concrete instances worth
  reconciling at the source rather than at each call site.

## Evidence

Work-log `[ERROR] (plan-marshall:execute-script:2) script_failure` lines at
2026-08-26T21:41:58Z, 22:14:44Z, 2026-08-27T05:45:08Z, 06:19:31Z, 06:20:27Z,
07:10:39Z, 07:10:58Z (x3).

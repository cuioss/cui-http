envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:25:12Z

component=plan-marshall:manage-status
category=anti-pattern
source_plan=generator-contract-test-semantics
source_signal=script-failure
notation=plan-marshall:manage-status:manage-status
exit_code=2

# `metadata` invoked without its required `--field`

`manage-status metadata` was called with `--plan-id` only and rejected
with `error: invalid_invocation`, `reason: missing_required_flag`,
`rejected: --field`, `accepted: [field, plan-id]`. The `metadata` verb
carries a mode flag (`--get` / `--set`) plus `--field`, and the mode
flag alone reads like a complete invocation.

## Rule

For a verb whose canonical form is
`metadata --plan-id P --field F (--get | --set [--append] --value V)`,
`--field` is required on BOTH modes. Copy the whole canonical form from
`manage-status` Canonical invocations -> `metadata`; do not assemble it
flag-by-flag from the operation prose, where `--get`/`--set` are the
salient tokens and `--field` reads as optional context.

## Impact

Same argparse-rejection family as the sibling `manage-findings` and
`ci` failures on this run: four distinct notations rejected at parse
time in one plan. The common cause is invocation assembled from
narrative rather than copied from the canonical block.

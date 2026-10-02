envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:25:07Z

component=plan-marshall:manage-findings
category=anti-pattern
source_plan=generator-contract-test-semantics
source_signal=script-failure
notation=plan-marshall:manage-findings:manage-findings
exit_code=2

# Invented verb `qgate query` — the accepted set is add/clear/list/resolve/resolve-evidenced

`manage-findings qgate query ...` was invoked and rejected with
`error: invalid_invocation`, `reason: unknown_verb`, `rejected: query`,
`accepted: [add, clear, list, resolve, resolve-evidenced]`. The verb
`query` is a plausible-sounding paraphrase of `list` produced from
workflow narrative rather than from the declared argparse surface.

## Rule

This is the canonical verb-paraphrase recurrence signature named in
`persona-plan-marshall-agent` § "Never invent script subcommands". Quote
the subcommand verbatim from the skill's Canonical invocations block or
from `--help`; never extrapolate a synonym that fits the sentence.

## Impact

The rejection is exit 2, so the script body never runs — but a caller
that treats it as a soft miss and continues silently loses the query
result. The executor's `accepted:` list already names the fix; the
residual gap is that read-verb synonyms (`query`/`get`/`read`/`list`)
are chosen by narrative fit, so a did-you-mean hint mapping `query` to
`list` on this surface would close it mechanically.

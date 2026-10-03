envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:25:02Z

component=plan-marshall:automatic-review
category=anti-pattern
source_plan=generator-contract-test-semantics
source_signal=script-failure
notation=plan-marshall:automatic-review:review_completeness
exit_code=1

# --participated-bots given a bare bot_kind instead of a bot_kind:evidence_kind pair

`review_completeness check` was invoked with `--participated-bots
coderabbit`. The script refused with `error: malformed_bot_flag`: the
flag expects `bot_kind:evidence_kind` pairs, and a bare `bot_kind`
neither proves participation nor states a valid absence. The script's
own rejection text explains that silently dropping the token would
resolve the bot to absent — a blocking state — and manufacture a false
merge block, which is why it is a hard caller error rather than a
tolerated shorthand.

## Rule

A flag whose value is a COMPOUND token (`a:b`) never accepts the left
half alone. Before invoking, read the flag's value grammar from the
skill's Canonical invocations block — not from the surrounding prose,
which names bot kinds in isolation and reads like a legal value.

## Impact

This is the argparse-rejection recurrence class, in its value-grammar
variant rather than its verb/flag-name variant: the flag name was
correct and the value shape was not. Worth an explicit example in the
`review_completeness` invocation block showing a correct pair
(`coderabbit:inline-comment`) beside the rejected bare form.

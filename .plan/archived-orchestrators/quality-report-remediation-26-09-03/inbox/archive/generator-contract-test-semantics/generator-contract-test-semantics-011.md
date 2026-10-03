envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T15:25:17Z

component=plan-marshall:tools-integration-ci
category=anti-pattern
source_plan=generator-contract-test-semantics
source_signal=script-failure
notation=plan-marshall:tools-integration-ci:ci
occurrences=3
exit_code=2

# Three distinct `ci` rejections in one run, all about --plan-id position or presence

The `ci` surface was rejected three times in this plan, each a
different variant of the same confusion about where `--plan-id` lives:

1. `ci checks status --plan-id P` — `unrecognized arguments`. On this
   router `--plan-id` is a TOP-LEVEL flag consumed before the verb, so
   it must go `ci --plan-id P checks status`. The executor's note said
   exactly this.
2. `ci --plan-id P issue prepare-comment --issue N` — `unknown_flag:
   --issue`; the subcommand declares only `[plan-id, slot]`.
3. `ci issue prepare-comment` — `the following arguments are required:
   --plan-id`. Here `--plan-id` is declared ON the subcommand, so it
   must appear AFTER the verb.

Rejections 1 and 3 are mirror images: the same flag name, required in
opposite positions on two verbs of the same router.

## Rule

`--plan-id` position is per-verb, never per-router-wide. Consult the
verb's own canonical-invocation block and place the flag exactly where
that block shows it. `ci checks` takes it before the verb; `ci issue
prepare-comment` takes it after. Never append `--plan-id` by rote.

## Impact

Two of the five canonical argparse-rejection recurrence signatures
(router-scoped `--plan-id` placed after the verb, and verb-scoped
`--plan-id` omitted) fired on the SAME router within one plan, which is
strong evidence the router-vs-verb split is the surface's real hazard.
The `ci` SKILL.md would benefit from a single explicit table naming,
per verb, whether `--plan-id` is router-scoped or verb-scoped.

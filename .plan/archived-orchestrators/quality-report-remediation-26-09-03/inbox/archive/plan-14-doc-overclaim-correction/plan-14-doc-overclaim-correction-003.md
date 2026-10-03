envelope_version=1
sender_type=plan
sender_id=plan-14-doc-overclaim-correction
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T19:26:23Z

component=plan-marshall:phase-6-finalize
category=anti-pattern
bundle=plan-marshall

# The documented "never invent script subcommands" rule is not preventing recurrence at the orchestrator dispatch site

Three of the four distinct script-failure clusters in plan-14-doc-overclaim-correction
were exit-2 argparse rejections, all of them the orchestrator's own invocation errors
rather than script defects:

- `plan-marshall:tools-integration-ci:ci` — twice. First an invented `pr checks`
  verb; then, after correcting the verb, `--plan-id` placed AFTER the verb when the
  `ci` router consumes it as a top-level flag BEFORE the verb.
- `plan-marshall:automatic-review:review_completeness` — invoked with a guessed
  bundle prefix, `workflow-integration-github:`, instead of `automatic-review:`.
- `plan-marshall:workflow-integration-github:github_ops` — invoked on the assumption
  that `review_completeness` was one of its verbs.

Every one of these already has a named recurrence signature in
`persona-plan-marshall-agent/standards/agent-behavior-rules.md` § "Never invent
script subcommands", and a dedicated recipe exists
(`pm-plugin-development:recipe-fix-argparse-rejection`). The rule is documented,
loaded, and still did not fire. So the actionable finding is NOT "don't invent
verbs" — that is already written down. It is that a prose prohibition loaded at
agent-start does not survive contact with a workflow step that names a script
notation without naming its argument surface.

The failure shape is specific and repeatable: the agent knows the SCRIPT it needs
(the workflow body names it), and infers the VERB and FLAG POSITION from the
surrounding narrative. The narrative reads naturally and the guess reads plausibly,
so there is no felt uncertainty to trigger a `--help` check.

(Filed with a fourth live instance: the agent authoring this very lesson invoked
`manage-config get --key required_bots` and was rejected — `get` is not a registered
verb on that script. Same shape, same run.)

## Solution

Make the check unconditional rather than uncertainty-triggered:

1. **Before the FIRST call to any script notation in a given envelope**, run it with
   `--help` and read the verb list and flag positions. Once per notation per
   envelope, not once per call. This is a sub-second cost paid once; the rejection
   it prevents costs a failed call plus a retry plus a log entry.
2. **Never carry a verb over from a sibling script.** `review_completeness` being
   review-shaped does not make it a `github_ops` verb; script boundaries do not
   follow topic boundaries.
3. **Router scripts take `--plan-id` before the verb.** `ci` is a router: the flag is
   parsed by the top-level parser, so `ci --plan-id X pr` is correct and
   `ci pr --plan-id X` is an `unrecognized arguments` rejection. The opposite holds
   for non-router scripts that declare `--plan-id` on the subcommand. There is no
   universal rule, which is precisely why the `--help` walk is not optional.

Structurally: a workflow body that names a script notation should carry, or xref by
name, that script's canonical-invocation block for the verb it is telling the reader
to call — so the argument surface arrives with the instruction rather than having to
be recalled.

## Impact

Applies to every agent that dispatches `manage-*` / `tools-*` / `workflow-*` scripts
from a workflow body — phase dispatchers most of all, since they call the widest set
of unfamiliar notations. Each rejection is exit 2 with the script body never
executing, so the failure is loud and non-corrupting; the cost is wasted cycles and
log noise, not wrong results.

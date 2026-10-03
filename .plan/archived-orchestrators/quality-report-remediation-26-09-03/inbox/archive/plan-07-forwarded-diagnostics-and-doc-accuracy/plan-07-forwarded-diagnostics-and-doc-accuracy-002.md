envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-diagnostics-and-doc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:25:11Z

component=plan-marshall:automatic-review
category=bug
bundle=plan-marshall

# A gate script rejected at argparse four times while the gate it guards reported a verdict anyway

## Observation

`plan-marshall:automatic-review:review_completeness check` was rejected by argparse
(`exit_code=2`, `failure_kind=argparse_rejection`) on **four separate firings** of the
`automatic-review` step in one finalize run, and on every one of those firings the step went
on to record a completeness verdict.

Evidence from this plan (`plan-07-forwarded-diagnostics-and-doc-accuracy`), work.log:

| Time (UTC) | Rejection | Step outcome recorded immediately after |
|---|---|---|
| `12:48:23` | `review_completeness` exit 2 | `12:48:39` `automatic-review (outcome=done)` |
| `14:45:34` | `review_completeness` exit 2 | `14:46:14` `automatic-review (outcome=loop_back)` |
| `19:06:07` | `review_completeness` exit 2 | `19:07:32` `automatic-review (outcome=done)` |
| `19:15:02` | `review_completeness` exit 2 | `19:15:24` `automatic-review (outcome=done)` |

A second, unrelated instance of the same class in the same run:
`13:27:15` `script_failure notation=plan-marshall:tools-integration-ci:ci exit_code=2
failure_kind=argparse_rejection` (`--plan-id` placed after the verb on the `ci` router),
followed at `13:41:46` by `ci-verify (outcome=done)`.

## Why this is reusable

Two distinct defects, both generic:

**(a) The verdict was reached without the machinery that is supposed to produce it.** The
completeness quorum is exactly what `review_completeness check` computes. Four times it never
ran, and four times the step still emitted a participation verdict — the final one being a
`force-done with unproven review participation` reached by narrative reasoning in the decision
log rather than by the classifier. A gate that returns a verdict when its own classifier
failed to execute is reporting a conclusion it did not compute. The finalize workflow's own
exit-code convention already forbids this in words ("silent swallowing of `wrong_parameters`
rejections is the prohibited anti-pattern; 'log and continue' is equally forbidden") — the
prose rule existed and was not enforced by anything, four times, in one run.

**(b) The failure record truncates away the cause.** Every one of the four `script_failure`
work-log entries carries the argparse **usage block** and is then cut off with `...[truncated]`
*before* the `error:` line that names the actual rejection. The usage block is the least
informative part of an argparse rejection — it is identical for every rejection of that
subcommand — while the one line that says WHY was dropped. The cause of a recurrence that
fired four times is therefore not recoverable from the plan record at all. (Contrast the `ci`
rejection record, which survived intact and even carried its own remediation note — so the
truncation is a budget artefact of long usage blocks, not a policy.)

## Proposed rule

1. A non-zero exit from a gate's own classifier script MUST prevent that gate from recording a
   terminal outcome. If the classifier cannot run, the honest outcome is `failed`, not `done`
   with a hand-derived verdict. Prose-only prohibitions on swallowing argparse rejections have
   now demonstrably not held; this needs a structural check at the step boundary.
2. `script_failure` log records should truncate the **usage block**, not the `error:` line —
   or capture stderr tail-first. The diagnostic value is entirely in the last line.

envelope_version=1
sender_type=plan
sender_id=plan-10-attack-test-mechanism-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-03T21:22:50Z

# Candidate lesson: non-existent notation `github_pr review_completeness` used for the review-completeness check

**Source signal**: script failure (unknown notation) observed by the finalize orchestrator during the automatic-review rounds (reported by the dispatcher).
**Component**: plan-marshall:automatic-review (review_completeness script) and whichever doc or prompt produced the wrong notation.

## What happened

A call was attempted with the notation `github_pr review_completeness`, which does not exist. The correct invocation is `plan-marshall:automatic-review:review_completeness check`.

## Candidate rule

Invented-notation class: quote the executor notation verbatim from the owning skill's canonical-invocation block. If a doc or dispatch prompt still names `github_pr` for review completeness, it is stale and should be corrected to `plan-marshall:automatic-review:review_completeness check`.

## Classification hint

Marketplace doc/prompt drift (plan-marshall bundle); recurrence signature "never invent script subcommands".

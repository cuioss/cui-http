envelope_version=1
sender_type=plan
sender_id=plan-12-javadoc-samples-and-api-prose
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-05T07:35:21Z

component=plan-marshall:phase-6-finalize
category=bug

# ci_complete_precondition timed out waiting on the CodeRabbit review-bot commit status

## Source

- Plan: plan-12-javadoc-samples-and-api-prose (PR #260)
- Signal: observed by the orchestrator during finalize (not in this plan's findings store;
  provenance is the orchestrator's run observation)

## What happened

The CI arm of `ci_complete_precondition` timed out. The only check still incomplete was the
`CodeRabbit` commit status. That status is posted by a review bot to show review progress. It is
not a CI workflow check. The real CI workflows had already finished.

## Why it matters

CI completion and review-bot completion are separate gates, and the pipeline already tracks
review-bot completion separately (automatic-review, bot_completion). Counting a review bot's commit
status in the CI-complete set means a slow or rate-limited bot can time out the CI precondition.
On this run CodeRabbit's included-review allowance was exhausted ("0 remain after this review").
The failure is then reported as a CI problem, so the operator looks in the wrong place.

## Suggested correction

Leave review-bot commit statuses (CodeRabbit and any other configured review bot) out of the
ci_complete_precondition check set. Do this by matching the status context against the configured
review-bot registry. Leave those statuses to the review-bot completion path instead.

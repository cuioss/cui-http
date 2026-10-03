envelope_version=1
sender_type=plan
sender_id=forwarded-parser-strictness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T12:29:15Z

# Candidate lesson: a rate-limited review bot does not auto-retry, and its advertised reset ETA is not trustworthy

## Signal provenance

`signal_automated_review_count = 1`. The `automatic-review` step fired 5 times with the
history `done -> loop_back(6-finalize) -> done -> failed -> done`.

## Observation

A review bot refused to review because its rate-limit quota was exhausted, and:

- It advertised a reset ETA of ~29 minutes. It was still refusing 71 minutes later.
- After the quota did clear, it did **not** auto-retry the pending review. The review only
  happened on a second explicit re-trigger.
- `review_rate_window_await` is `false` in this project, so nothing in the pipeline waited
  for or re-triggered the bot automatically.

The consequence: the review gap would have been merged past unnoticed had the operator not
chosen to wait and re-trigger by hand. The step's own `outcome=done` at the end says
nothing about the intervening `failed` firing.

## Reusable rule (candidate)

Two independent facts, both process-level:

1. **A rate-limited bot's own reset ETA is an estimate, not a contract.** Do not schedule a
   re-trigger off it. Poll, or re-trigger explicitly and check the result.
2. **Quota clearing does not re-deliver a refused review.** A bot that declined for quota
   reasons has dropped the request; the review must be re-triggered explicitly. A pipeline
   that treats "the bot has quota again" as "the review will arrive" merges past a
   silently-absent review.

Where `review_rate_window_await` is `false`, both of the above land on the operator, and
nothing in the run surfaces the gap at merge time. That configuration choice is worth
making visible in the merge gate rather than leaving implicit.

## Suggested routing (orchestrator to judge)

- Candidate component: `plan-marshall:automatic-review`.
- Category: `improvement`.
- Weaker than the other candidates in this batch — it is process/config knowledge rather
  than a defect-plus-corrective-action rule, and much of it is bot-vendor-specific. The
  durable half is fact 2 (quota recovery does not re-deliver); fact 1 may be too
  vendor-specific to keep.

## Plan context

- Plan: `forwarded-parser-strictness` (epic `quality-report-remediation`)
- Reviewed HEAD 40e09fc; 3 bots reviewed, CodeRabbit reported 0 new findings after the
  second explicit re-trigger.

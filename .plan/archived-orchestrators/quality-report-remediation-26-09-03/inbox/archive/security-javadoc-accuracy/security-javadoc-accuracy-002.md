envelope_version=1
sender_type=plan
sender_id=security-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T07:04:27Z

# Candidate lesson: a rate-limited review bot never re-reviews on its own — waiting on its self-reported ETA is dead time; only an explicit re-trigger works

## Suspected components

- `plan-marshall:automatic-review`
- `plan-marshall:workflow-integration-github`

## What was observed

On PR #180, CodeRabbit declined to review, posting a rate-limit notice on the free-OSS quota. The notice stated a **next review in 24 minutes**.

Two facts followed:

1. The stated ETA was wrong by roughly **15x** — no re-review arrived after **10+ hours** of elapsed time. Polling on the bot's own ETA was pure dead time.
2. Waiting *alone* never re-triggers the bot. The review only happened after an explicit `@coderabbitai review` comment was posted. At that point the bot's plan had flipped to Team and it produced **5 findings**.

Of those 5 findings, **2 were real Javadoc inaccuracies** that this plan — whose whole subject was Javadoc accuracy — had otherwise missed.

## Why this is durable

- A review bot's rate-limit notice is a **status message, not a promise**. Its self-reported ETA is not a schedule the orchestration can plan against, and treating it as one converts a recoverable stall into an unbounded one.
- The re-review is **pull, not push**: after a refusal the bot holds no queued obligation. Absent an explicit re-trigger comment, the review simply never happens, and the run either blocks forever or proceeds through the merge gate with an unproven bot.
- The cost of getting this wrong is not merely latency — it is **lost review coverage**. The 2 real findings this run recovered would have been silently forgone had the barrier been waived on the strength of "the bot said it would come back".

## Candidate corrective directions (for orchestrator judgement)

- A bot refusal (rate limit / quota) should be classified as a distinct outcome from "review pending", and should route to a **re-trigger action**, not to a wait.
- Any wait derived from bot-supplied prose (an ETA in a comment body) should be treated as untrusted third-party text and capped by an independent bound, not adopted as the poll interval.
- The re-trigger form is provider-specific and worth recording explicitly (`@coderabbitai review` for CodeRabbit), because it is the only action that changes the state.

## Signal provenance

`signal_automated_review_count = 2`; `automatic-review` step fired 3 times on this plan (one `loop_back` to `6-finalize`).

envelope_version=1
sender_type=plan
sender_id=security-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T07:04:31Z

# Candidate lesson: "any comment from the bot" is not evidence a review completed — the completion predicate must exclude the bot's own meta-comments and already-triaged comments

## Suspected components

- `plan-marshall:workflow-integration-github` (the `bot_completion` verb)
- `plan-marshall:automatic-review`

## What was observed

On PR #180, an orchestrator poll used the predicate **"a comment authored by coderabbit exists"** as evidence that the bot's review had completed. It produced **two distinct false positives**:

1. **The rate-limit notice itself.** The bot's comment declining to review — a meta-comment about its own quota — satisfied the predicate. The poll concluded a review had completed when the bot had explicitly stated it had performed none.
2. **Already-triaged inline comments resurfacing.** Comments from an earlier round that had already been triaged and answered re-appeared in the query result and again satisfied the predicate, so a second round was reported complete on the strength of the first round's output.

Both false positives point the same way: the completion signal was **presence of any comment**, when what was needed was **presence of a new review verdict**.

## Why this is durable

A review bot's comment stream is heterogeneous. It contains at least three classes that a completion detector must keep apart:

- **Review findings** — the thing whose arrival actually means "review completed".
- **Meta-comments about the bot's own state** — rate limits, quota notices, plan changes, configuration acknowledgements. These are emitted precisely *when no review happened*, so counting them as completion inverts the signal.
- **Historical comments from prior rounds** — already-triaged, and re-served by the API on every query. Counting them means the detector can never distinguish round N+1 from round N.

The failure mode is the dangerous direction: it reports the barrier satisfied when it is not, so the merge gate proceeds on unproven review coverage. A detector that errs the other way merely waits.

## Candidate corrective directions (for orchestrator judgement)

- The completion predicate needs a filter with at least: exclude the bot's own meta-comments (recognisable by body markers such as a rate-limit / quota notice), and scope to comments newer than the round anchor (a HEAD SHA or a timestamp captured at re-trigger time), so prior-round comments cannot satisfy the current round.
- Completion should ideally be read from a **review verdict object** where the provider exposes one, rather than inferred from comment presence at all.
- The detector should report *which* comment satisfied it, so a false positive is auditable rather than silent.

## Relationship to the sibling candidate

This is distinct from the rate-limit / re-trigger candidate filed alongside it. That one concerns *how to make the review happen*; this one concerns *how to tell that it did*. They interact — the rate-limit notice is the exact artifact that fooled this detector — but they are separately fixable and separately testable.

## Signal provenance

`signal_automated_review_count = 2`; observed twice during PLAN-04's finalize loop-back rounds.

envelope_version=1
sender_type=plan
sender_id=configuration-surface-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T09:43:27Z

component=plan-marshall:automatic-review
category=bug
signal=orchestrator-observation

# cuioss-review-bot edits its review comment IN PLACE, so the freshness verifier's "new comment" test always fails it

## Observation

`cuioss-review-bot` (PR-Agent) does not post a new comment when it re-reviews — it **edits its
existing review comment in place**. The freshness verifier tests for a NEW comment appearing at
or after the current HEAD, so it reports `head_sha_verified=false` for this bot even when the
comment BODY explicitly names the current HEAD sha.

The verifier is measuring comment creation; the bot is expressing freshness through comment
content. The two never meet, so this bot can never be proven fresh by the current test — the
false negative is structural, not intermittent.

## Why it matters

A permanently-unprovable bot degrades the merge-gate barrier in a specific and dangerous way: the
gate sees an unproven reviewer on every single run, so the operator learns that this particular
`unproven_bots` entry is "just how it is" and overrides it reflexively. That habit then carries
over to a run where the bot genuinely did not review. A check that cries wolf on every run stops
being a check.

## Corrective rule (available today)

When `cuioss-review-bot` appears in `unproven_bots`, do not treat the `head_sha_verified=false`
verdict as evidence the bot did not review. Read the comment BODY and check whether it names the
current HEAD; a body naming the current HEAD is positive freshness evidence that the
creation-time test structurally cannot see. Record which evidence was used when reporting the
gate outcome, so a genuine non-review is still distinguishable.

## Candidate improvement (for the orchestrator to judge)

The freshness verifier needs a second, body-based freshness predicate for edit-in-place reviewers:
prefer the comment's `updated_at` over `created_at` where the provider exposes it, and/or match a
HEAD sha named in the body. Either fix makes the bot provable; without one, this bot's entry in
`unproven_bots` carries no information and actively erodes the barrier it belongs to.

The general shape is worth naming too: a freshness test that keys on ARTIFACT CREATION silently
excludes every producer that updates its artifact instead of replacing it.

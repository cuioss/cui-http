envelope_version=1
sender_type=plan
sender_id=build-artifacts-and-ci-safety
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-06T09:06:21Z

component=plan-marshall:automatic-review
category=bug
title=A timestamp bump is not evidence of re-review - anchor bot participation to comment content

# A timestamp bump is not evidence of re-review — anchor bot participation to comment content

The mechanical review-participation check credited `cuioss-review-bot` as
`participated` because its review object's `updated_at` had advanced. Content
inspection contradicted that verdict: the comment body still named the
**previous** head SHA, so the bot had not in fact re-reviewed the current one.

The run caught this — `automatic-review` ultimately recorded
`cuioss-review-bot STALE vs cdb21df (Guide unchanged since 00:46Z, still refs
20fa6b0)` — but only because a human-level content read overrode the mechanical
signal. The mechanical signal on its own was wrong, and it was wrong in the
**fail-open** direction.

## Why the mechanical signal is unsound

`updated_at` on a review object moves for reasons unrelated to the bot producing
a fresh verdict — a re-render, a resolution state change, an edit to an unrelated
thread, a provider-side touch. None of those establish that the bot examined the
current head. The field answers "did this object change", and the gate needs the
answer to "did this bot verdict the tree I am about to merge".

Reading the first as the second is the classic proxy-signal substitution: the
proxy is cheap and available, the real predicate is not, and the substitution is
invisible because the proxy is usually correlated with the truth.

## Rule

Participation must be established from **content anchored to the head under
review**, never from object metadata alone:

- Extract the head SHA (or equivalent commit anchor) the bot's own comment body
  cites, and compare it to the PR's current head. A comment citing an older head
  is `stale`, not `participated`, regardless of any timestamp.
- Where no anchor can be extracted, the correct verdict is `unproven` — a third
  value, not a fallback to `participated`. An unreadable signal is not a passing
  one.
- Never collapse `stale` or `unproven` into `participated`. The re-review barrier
  exists precisely to hold on those two states; a fail-open default disarms it
  silently and the merge proceeds with the gate reporting green.

The general form: when a gate's real predicate is expensive and a metadata field
is cheap, the cheap field will be substituted, and the substitution will fail
open. Name the two remaining states explicitly so the default cannot be the
permissive one.

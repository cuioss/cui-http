envelope_version=1
sender_type=plan
sender_id=decoding-normalisation-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-06T07:08:45Z

component=plan-marshall:workflow-integration-github
category=bug
bundle=plan-marshall

# Review-bot refusal cause is read off a persisted PR comment, so `quota` never expires

## What happened

Plan `decoding-normalisation-hardening` (epic `quality-report-remediation`, PR #209 → #210)
spent roughly six and a half hours of finalize wall-clock on a CodeRabbit rate limit that
had already lifted.

Decision-log trail (`.plan/local/plans/decoding-normalisation-hardening/logs/decision.log`):

- `22:37Z` — "CodeRabbit refused re-review of b307f6f with cause=quota (hourly included-review
  budget spent on the 9fc411d review). Operator directive: wait >=90min and retry, up to 10 waits.
  Starting wait 1/10."
- `23:33Z` — "still quota-refused ... after wait 1/10 and an explicit @coderabbitai review
  re-request. Starting wait 2/10."
- `01:16Z` — "unresponsive across waits 1/10 and 2/10 (~4h ... cause=quota throughout). Applying
  the operator's no-reaction rule: close PR #209 without merging, open a fresh PR on the same
  branch, wait 5 minutes, retry."
- `05:04Z` — "Confirmation pass over d41da0d still rate-limited at 07:00; posting a fresh
  @coderabbitai review now the hourly window has reset. Wait 4/10."

What actually unblocked it was step four: posting an explicit `@coderabbitai review` once the
hourly window had reset. The two ~90-minute waits and the whole close-PR-#209 / open-PR-#210
reissue bought nothing.

## Why the signal was wrong

`_github_pr._is_refusal_notice(body, bot_kind)` classifies a refusal purely from a comment
BODY — a registry `refusal_patterns` arm plus a structural shape arm — and
`_github_pr.refusal_cause(body, bot_kind)` then defaults every non-size refusal to `quota`.
Neither consults the comment's timestamp, the reviewed commit SHA, or any window bound.

The rate-limit text is a comment that stays on the PR forever. Every CodeRabbit `review_body`
on this PR carries "Included review availability: Your plan provides up to 1 included review
per hour; 0 remain after this review." So once a refusal notice is on the thread, every later
fetch re-reads it and re-reports `cause=quota`, whether or not the hour has elapsed. The signal
is a durable artifact being consumed as if it were a live state reading.

## Rule

A signal derived from a persisted external artifact (a PR comment, an issue body) MUST carry
the artifact's own recency evidence, and the consumer MUST bound it. Concretely:

- Attach the source comment's `created_at`/`edit_term` and the `reviewed_commit_sha` to each
  refusal record, and treat a refusal older than the bot's declared rate window as EXPIRED
  rather than live.
- Never let a backoff loop re-read the same notice as fresh evidence for the next wait — a
  wait must be justified by a refusal observed AFTER the previous retry, not by the notice
  that triggered the first one.
- For a `quota` cause specifically, the cheap remedy (re-request the review once the declared
  window has elapsed) should be attempted before a second long wait, and long before a PR
  reissue.

## Impact

Any plan whose finalize hits a review-bot quota refusal can burn unbounded wall-clock and, as
here, destroy and recreate a PR for no reason. The refusal classifier is shared by every plan
using `automatic-review`, so this is not project-specific.

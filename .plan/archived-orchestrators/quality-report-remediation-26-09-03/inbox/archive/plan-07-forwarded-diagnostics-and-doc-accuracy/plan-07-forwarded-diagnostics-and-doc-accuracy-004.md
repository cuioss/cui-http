envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-diagnostics-and-doc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:26:04Z

component=plan-marshall:automatic-review
category=anti-pattern
bundle=plan-marshall

# Waiting for a bot re-review that no event will ever trigger, then retrying a refusal that costs quota

## Observation

After a loop-back fix commit, neither required review bot re-reviewed the new HEAD on its own,
and the recovery attempts made things worse rather than better.

Evidence from this plan (`plan-07-forwarded-diagnostics-and-doc-accuracy`):

- **pr-agent never re-reviews on push.** decision.log `19:07:12Z`: "pr-agent (cuioss-review-bot)
  resolves `participated_stale`: its sole Guide comment (issue_comment, posted
  `2026-08-29T12:39:42Z`, never updated) predates the fix commit push (~13:24Z) and pr-agent has
  no auto-review-on-push — only an explicit `/review` trigger refreshes it." Waiting for it was
  never going to terminate.
- **CodeRabbit refuses to re-review already-reviewed commits.** Every explicit
  `@coderabbitai review` trigger this run posted came back as an auto-generated refusal:
  three `issue_comment` findings (`7ea148`, `1d58ba`, `7a75fb`) whose bodies read "Review rate
  limited. Note: CodeRabbit is an incremental review system and does not re-review already
  reviewed commits."
- **The wait was entered before noticing no event would fire.** decision.log `14:49:59Z`:
  "CodeRabbit rate-limit window (posted `13:24:50Z`, 14min) long expired at 14:49Z but no
  re-review fired — **no PR event since the rate-limited commit**. Posted an explicit
  '@coderabbitai review' re-trigger to make the required-bot re-review of HEAD 7f9b9d4
  reachable." The window was waited out for ~85 minutes for a re-review that could not have
  been scheduled at all.
- **Net cost:** loop-back iterations 1, 2 and 3 all spent on this; iteration 2 halted on an
  operator decision (`15:21:30Z` "wait for CodeRabbit quota reset and retry the required-bot
  re-review of HEAD 7f9b9d4 before merge"), and iteration 3 ended in a `force-done with
  unproven review participation` anyway. CI had been green the entire time.

## Why this is reusable

Two independent facts about bot re-review that a gate must model, not discover per run:

1. **Re-review is not event-driven for every bot.** A bot with no review-on-push has a
   *structurally unreachable* re-review absent an explicit trigger. Waiting on a rate-limit
   window is only meaningful when an event will fire once the window opens; with no pending
   event, an expired window changes nothing. "Window expired" and "re-review will now happen"
   are different propositions.
2. **A refusal is not a transient error, and retrying it is not free.** An incremental-review
   bot that declines already-reviewed commits will decline again. Each trigger consumes the
   quota being waited on, so retry-on-refusal actively lengthens the wait rather than shortening
   it — the failure mode is negative-progress, not merely wasted effort. (The step already
   distinguishes `declined` from `refused` and documents "the remedy is to accept the decline,
   not to re-trigger a bot that already declined"; that guidance was not what the run did.)

## Proposed rule

- Model **`triggers_rereview_on_push`** per bot in the registry. When it is false, never enter
  a wait for that bot — go straight to the explicit trigger, or to accepting the gap.
- Before entering (or continuing) a rate-window wait, require a *pending event* for the wait to
  resolve. An expired window with no pending event is not "wait longer", it is "no mechanism
  will produce this review".
- Treat an incremental-review decline as **terminal for that commit**. Do not re-trigger; cap
  explicit triggers at one per HEAD, and count each trigger against the quota model rather than
  treating triggers as free probes.
- When both required bots are structurally unreachable, surface the accept-the-gap decision
  immediately rather than after three loop-back iterations.

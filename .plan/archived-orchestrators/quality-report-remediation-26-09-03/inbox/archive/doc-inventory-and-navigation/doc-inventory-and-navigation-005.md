envelope_version=1
sender_type=plan
sender_id=doc-inventory-and-navigation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T07:29:40Z

# Candidate lesson: both review bots hit rate limits; the automated recovery path was disabled

## Observation

Two independent rate-limit events on PR #161, and the run's automated recovery
seam did not fire for either:

- **CodeRabbit** was rate-limited on its first review. Recovery required an
  explicit `@coderabbitai review` comment posted by hand; the review landed
  roughly 8 minutes later (finding `8ec061`, "Review finished", and the review
  body `e9c1ed` noting "Your plan provides up to 1 included review per hour;
  0 remain after this review").
- **Sourcery** returned a budget-exhaustion notice rather than review feedback:
  "you've used your own review budget of 250,000 diff characters for the last 7
  days ... You can request another review in 3 days" (finding `585e8b`). This is a
  MULTI-DAY window — no in-run recovery is possible at all.

`review_rate_window_await` was `false` for this run, so no automated wait or
recovery was attempted for either bot. The recovery that did happen was manual.

## Why this is epic-level

The rate-window await seam exists precisely for this, and it was off. The cost was
~8 minutes of dead time plus operator attention on a step that is supposed to be
unattended. And the two bots' windows differ by three orders of magnitude
(1 hour vs 7 days), which means "wait it out" is a sound strategy for one and
useless for the other — a distinction the current on/off flag cannot express.

## Candidate rule

Two asks:

- Default `review_rate_window_await` on where the bot's window is short enough to
  wait out (CodeRabbit's hourly window qualifies), so the `@coderabbitai review`
  nudge is issued by the pipeline rather than by the operator.
- Treat a rate-limit notice as a distinct outcome class from "reviewed cleanly".
  Sourcery's budget notice was correctly triaged here as `taken_into_account`
  ("not review feedback — nothing to fix"), but a bot that COULD NOT review must
  not be indistinguishable from a bot that reviewed and found nothing. Where the
  window exceeds the plan's lifetime, the honest outcome is `refused` / `unavailable`,
  not a clean pass.

## Evidence

- Findings `585e8b` (sourcery budget notice), `e9c1ed` (coderabbit review body,
  "0 remain after this review"), `8ec061` (coderabbit "Review finished"
  acknowledgement of the manual invocation)
- `review_rate_window_await: false` for this run

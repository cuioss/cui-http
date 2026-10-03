envelope_version=1
sender_type=plan
sender_id=semicolon-dot-segment-traversal
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T16:04:09Z

component=plan-marshall:phase-6-finalize
category=anti-pattern

# Never retrigger a quota-blocked review bot on a tight loop

## What happened

During `default:branch-cleanup` on plan `semicolon-dot-segment-traversal`, the
orchestrator armed a retrigger loop that posted `@coderabbitai review` on PR #183
roughly every 2 minutes for about 55 minutes, waiting for a review that could not
arrive. The finalize step spanned `12:27:48Z` (step start) to `16:01:00Z` (step
done) — the longest step of the run by a wide margin.

The outcome is recorded verbatim in the plan decision log:

```text
[2026-09-01T14:20:54Z] [WARNING] (plan-marshall:phase-6-finalize) ... Orchestrator
note: a retrigger loop posted ~27 spam comments and exhausted the CodeRabbit
chat-message hourly quota - do not retry a quota-blocked bot on a tight loop.
```

Net effect: ~27 junk comments on a public PR, the CodeRabbit **chat-message**
hourly quota burned in addition to the already-exhausted review quota (so the loop
made recovery strictly worse), ~1.5 h of wall clock, and PR #183 abandoned
unmerged in favour of replacement PR #185.

The same run shows the sibling bot reporting its own quota exhaustion explicitly
and being handled correctly — Sourcery's review-budget notice was filed as a
`pr-comment` finding and resolved `accepted` ("Not actionable review feedback ...
a fresh review can be requested later ... once the budget window resets"). The
defect is that CodeRabbit's equivalent state was *not* recognised, so it got a
retry loop instead of a wait-or-proceed decision.

## Why it matters

Retrying a rate-limited/quota-blocked bot cannot succeed by construction: the
block is time-windowed, not request-dependent. A tight loop therefore burns wall
clock, pollutes the PR conversation for every human reader, and can consume a
*second*, independent quota (chat messages) that would otherwise have been
available for the recovery path.

## Solution

- Recognise quota/rate-limit exhaustion as a **terminal state for this run**, not
  a transient one. Signatures: an explicit budget notice in the bot's body
  (Sourcery), or a green placeholder status with 0 reviews / 0 inline comments /
  0 check-runs (CodeRabbit).
- On that state, do not retrigger. Either wait for the stated reset window in one
  long sleep, or record the bot as unavailable-this-run and take the sanctioned
  merge-authorization path with an accurate `--granted-over`.
- If a retrigger is ever justified, bound it hard: at most one retrigger per bot
  per run, and never a fixed short interval. Each `@bot review` mention is a
  public comment and a quota-consuming chat message.
- Comment-posting retries must be counted and capped by the loop that issues them;
  ~27 identical comments should be structurally unreachable.

## Impact

Affects the review barrier in `phase-6-finalize` / `branch-cleanup` for every
review bot with a rate-limit or budget window (CodeRabbit, Sourcery, pr-agent).

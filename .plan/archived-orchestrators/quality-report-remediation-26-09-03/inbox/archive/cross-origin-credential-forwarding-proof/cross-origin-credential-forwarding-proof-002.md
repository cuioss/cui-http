envelope_version=1
sender_type=plan
sender_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-03T09:27:04Z

# Candidate lesson: Sourcery's per-account budget refusal matches no registered `refusal_patterns`

**Class**: producer/registry defect in `plan-marshall:automatic-review`. Reproduced on two PRs.
Immaterial to THIS run (Sourcery is an optional bot here) but a real mis-classification.

## Observation

Sourcery declined to review and posted, verbatim, on both PR #193 and PR #194:

> Sorry @OliverWolffGIP, you've used your own review budget of 250,000 diff characters
> for the last 7 days. You can request another review in 4 days and 2 hours by commenting
> `@sourcery-ai review`.

`automatic-review/standards/sourcery.md` lines 43-45 registers exactly two literals:

```yaml
refusal_patterns:
  - "your pull request is larger than the review limit of"
  - "reached your weekly rate limit of"
```

Neither matches. The text says "used your own review **budget** of" — it contains neither
"larger than the review limit of" nor "reached your weekly rate limit of".

The bot-agnostic structural arm (`_github_pr._is_rate_limit_notice`) also does not fire:
it requires BOTH a limit-exceeded marker AND a notice-shape marker. The exceeded-marker
regexes key on `exceeded|reached|hit` bound to a rate/usage-limit phrase; the body's verb
is "used" and its noun is "review budget", so no exceeded-marker matches. Failing the
first conjunct, the shape check is moot.

## Consequence observed

The comment was ingested as an ordinary `pr-comment` finding rather than counted in
`count_skipped_refusal` / named in `refused_bots[]`. Two findings were filed
(`ad9c45` for #193, `0fa768` for #194, both `kind: review_body`, `author: sourcery-ai`)
and both had to be dispositioned by hand as "no actionable content". Downstream,
`sourcery` was credited as `participated` in the merge-gate bot_states
(decision.log `cfd27a`: `sourcery:participated`) when it had in fact refused —
a hard-quota refusal mis-credited as a completed review.

Direction of the error matters: a refusal counted as participation is the UNSAFE
direction. It cannot be caught by waiting, because the account budget resets in days.

## Candidate corrective action

Register the observed literal (handle-free and number-free, matching the file's own
stated convention for the existing two entries) — something on the order of
`"review budget of"` — as a THIRD `refusal_patterns` entry. Also consider whether this
is a `cause=quota` refusal for `refusal_size_patterns` purposes: it is a rolling 7-day
account budget, not a per-PR size ceiling, so it must NOT be added to
`refusal_size_patterns` (that list resolves the bot to `refused_structural`).

## Evidence

- `automatic-review/standards/sourcery.md` lines 43-49
- `workflow-integration-github/scripts/_github_pr.py` lines 108-142
  (`_RATE_LIMIT_EXCEEDED_MARKERS`, `_RATE_LIMIT_NOTICE_SHAPE_MARKERS`)
- plan `artifacts/findings/pr-comment.jsonl`, both records (raw bodies preserved verbatim)
- plan decision.log entry `cfd27a`

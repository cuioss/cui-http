envelope_version=1
sender_type=plan
sender_id=plan-08-client-cache-and-result-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T20:15:51Z

module=cui-http
enrich_verb=insight
hint=Review-bot rate-limit and budget-exhaustion notices are transport failures, not review findings. Classify them at ingestion instead of storing them as pending pr-comment findings.

# Owed architecture hint: classify bot rate-limit notices at ingestion

## Target

- `--module`: `cui-http`
- enrich verb: `architecture enrich insight --module cui-http`

## Generalized hint (verbatim)

> Review-bot rate-limit and budget-exhaustion notices are transport failures, not review
> findings. Classify them at ingestion instead of storing them as pending `pr-comment`
> findings.

## Pattern that cleared the threshold

Disposition recurrence within this plan: `(cui-http, pr-comment, accepted)` occurred **2 times**,
clearing `preference_min_recurrence: 2`. Both instances were Sourcery budget-exhaustion notices
("you've used your own review budget of 250,000 diff characters for the last 7 days") — one filed
against the closed PR #157, one against PR #166.

Neither carried review content. Each was stored as a `pending` `pr-comment` finding, and
`pr-comment` is in the hardcoded ACTIONABLE blocking set, so both counted toward the pre-merge
blocking-finding gate until an operator dispositioned them by hand.

## Why it is worth generalizing

The cost was not the two dispositions. A bot's transport failure is currently indistinguishable,
at the findings layer, from a bot's review verdict:

- A Sourcery budget notice was classified `participated` on `review_body` evidence, so the
  completeness barrier counted a bot that had reviewed nothing as having participated.
- A CodeRabbit quota notice was read as a live countdown ("next review in 3 minutes") when its
  comment was 19 hours stale, and the run waited on a signal that could not arrive.

Both are the same shape: a notice ABOUT the review being unavailable, consumed as if it were the
review. Classifying at ingestion — a `refused` / `transport` class distinct from a finding — would
have removed the merge-gate noise and the false participation signal together.

The recurrence count understates this. Only the two dispositioned notices met the threshold; the
CodeRabbit instances were resolved through other paths and are not in the count.

envelope_version=1
sender_type=plan
sender_id=configuration-surface-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T09:43:07Z

component=plan-marshall:manage-locks
category=bug
signal=orchestrator-observation

# Rate-window recovery re-claimed a fresh 3600s window from a STALE notice, nearly burning an idle hour

## Observation

The review-bot rate-window recovery path read a rate-limit notice and claimed a fresh 3600-second
`coderabbit` window from it. The notice was **stale**: the real window it described had already
reopened. Honouring the claim would have parked the run for a full idle hour with the bot already
available.

The leaf had to release the claim on **wall-clock evidence** — comparing the notice's own
timestamp against now — to make any progress at all.

## Why it matters

The recovery path treats the notice as a live fact when it is a timestamped observation. Deriving
a fresh full-length window from an observation of unknown age is the defect: the window's
remaining time is `notice_window_end - now`, not `now + full_window`. Anchoring at `now` converts
every stale notice into a maximal wait, and the error compounds — a notice re-read later produces
an even later expiry.

The failure is also silent in the safe-looking direction: over-waiting never breaks anything, it
just costs an hour, so nothing surfaces it except an operator noticing the run is parked.

## Corrective rule

A rate-limit window claim must be anchored to the NOTICE's timestamp, never to the read time.
Before honouring any claimed window, check the notice's age against wall clock: a notice whose
described window has already elapsed carries no claim at all and must be discarded, not renewed.
When a claim and wall-clock evidence disagree, wall clock wins — release the claim.

## Candidate improvement (for the orchestrator to judge)

`manage-locks`' rate-window claim should either (a) require the notice timestamp as an input and
compute the residual window from it, refusing to claim from an undated notice, or (b) re-validate
an existing claim against wall clock on read and expire it automatically. Today the correction
depends on a leaf noticing the discrepancy and choosing to override, which is exactly the kind of
judgement a deterministic guard should not require.

envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:44Z

# Candidate lesson: `further_round_owed` bookkeeping finding stayed open across three clean rounds

Source signal: Q-Gate finding `63c2ce` (phase 6-finalize, `pre-submission-self-review`, resolved `fixed`).
Component: `pm-plugin-development:ext-self-review-plan-marshall` / `default:pre-submission-self-review`.

## What happened

The self-review step's round-1 verdict (1 finding, 1 class over a full-scope 28-file sweep) raised
a `further_round_owed` finding, because a returned finding always forces another round. The
further rounds duly ran — three of them (rounds 2, 3, 4), each independently re-verifying the full
plan surface and closing clean (`acceptance=accepted`, `may_close=yes`), most recently at HEAD
`289dbd3`. But the bookkeeping finding that RECORDED the obligation stayed pending until it was
manually resolved at finalize time, long after the obligation itself had been discharged.

## Corrective rule

A finding that records a *process obligation* (rather than a code defect) should be discharged by
the process step that satisfies it, not left for a human/agent sweep at the end. Concretely: when
`pre-submission-self-review` completes a subsequent round that closes clean, it should resolve the
`further_round_owed` finding it raised in the prior round as part of that round's own bookkeeping.

## Generalisation for the epic

Worth checking whether other self-raised obligation findings share the shape — raised by a step,
satisfied by the same step's later invocation, but never auto-closed. Every such finding inflates
the pending Q-Gate count and, in this run, was one of the five signals that made lessons-capture
fire at all.

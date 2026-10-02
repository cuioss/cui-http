envelope_version=1
sender_type=plan
sender_id=plan-16-benchmark-and-ci-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T16:06:48Z

# Candidate lesson (tooling defect): `pre-submission-self-review` leaf completed its review but returned without calling `mark-step-done`

## Observed signal

The dispatched `pre-submission-self-review` leaf performed its review and
returned `status: success`, but never recorded a `mark-step-done` outcome for
its step. The omission was caught by the post-dispatch completion guard
(`assert-step-recorded --require-terminal`), which reported the missing terminal
record.

## Why it is durable

This is the exact silent gap the post-dispatch guard exists to detect, and it
fired correctly — so the finding is not "the guard is missing" but "a specific
step's workflow body has a return path that skips its mandated side effect".
Without the guard the omission would have stayed invisible until the
`phase_steps_complete` handshake deadlocked the phase transition with no
per-step attribution. It recurs for anyone dispatching that step until the body
is fixed.

## Scope note (deliberately narrow)

Actionable content, reported as observed:

- The `pre-submission-self-review` workflow body has at least one completion
  path that returns without calling `mark-step-done`. Audit its branches and
  ensure every terminal return records an outcome (`done` / `skipped` /
  `loop_back` / `failed`) before returning.
- The guard's detection behaviour is correct and needs no change; it is the
  reason this was observable at all.

Not generalized into a rule about all dispatched steps — the contract that every
dispatched step must record its own outcome already exists; this is one body not
honouring it.

## Suggested classification (orchestrator judgement)

`bug`, owned by the `pre-submission-self-review` finalize-step component.

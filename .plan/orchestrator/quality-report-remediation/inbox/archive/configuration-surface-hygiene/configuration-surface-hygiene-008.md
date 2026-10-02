envelope_version=1
sender_type=plan
sender_id=configuration-surface-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T09:42:33Z

component=plan-marshall:phase-6-finalize
category=bug
signal=orchestrator-observation

# Dispatching `pre-submission-self-review` before its inline Step 1 wasted ~94k tokens on a step that was never owed

## Observation

The phase-6 dispatcher dispatched `default:pre-submission-self-review` **without first running
that step's inline Step 1 surfacer**. Step 1 produces the `candidates` set, and `candidates` is a
REQUIRED prompt-body field of the dispatched envelope — so the envelope refused immediately with
`contract_violation`, after a dispatch that cost roughly 94k tokens.

Running Step 1 afterwards showed the candidate count was **below the dispatch gate**. The step
therefore belonged inline in its entirety and no dispatch was ever owed. The cost was paid twice
over: once for the refused envelope, and once for discovering the dispatch should not have
happened.

## Why it matters

This is a sequencing defect with an expensive failure mode and no cheap signal. The refusal is
correct and fail-closed — the envelope did exactly the right thing — but it fires only AFTER the
dispatch has been paid for. The cost is proportional to the context the dispatch carried, so the
more work the run has done, the more the mistake costs.

The step's own contract already encodes the ordering (surface first, then gate on the count, then
dispatch only if the gate is exceeded). The dispatcher skipped straight to the last clause.

## Corrective rule

A dispatched step whose prompt body carries a REQUIRED field produced by an inline pre-step must
have that pre-step run to completion BEFORE the dispatch is composed. Concretely for
`pre-submission-self-review`: run Step 1's surfacer inline, read the candidate count, and dispatch
ONLY when that count exceeds the gate — otherwise complete the step inline and record it.

Generalised: never compose a dispatch whose required inputs have not yet been computed. When a
step doc says "surface, then gate, then dispatch", the gate is not advisory ordering — it is the
thing that decides whether a dispatch exists at all.

## Candidate improvement (for the orchestrator to judge)

The dispatcher could refuse to compose a dispatch with an absent required prompt-body field
locally, before spawning — turning a ~94k-token remote refusal into a zero-cost local one. That is
a component change to the dispatch loop, not a discipline the next agent can supply.

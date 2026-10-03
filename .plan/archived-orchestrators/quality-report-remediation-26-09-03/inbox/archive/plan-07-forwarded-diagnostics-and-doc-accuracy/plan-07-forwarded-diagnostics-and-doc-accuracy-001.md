envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-diagnostics-and-doc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:24:47Z

component=plan-marshall:phase-6-finalize
category=bug
bundle=plan-marshall

# Finalize dispatcher dispatched a step without the prompt field that step declares as required

## Observation

`phase-6-finalize` dispatched `default:pre-submission-self-review` with only the generic
5-field prompt body (`name`, `plan_id`, `skills[]`, `workflow`, `WORKTREE`). That workflow
declares `requires_prompt_fields: [candidates]` — the dispatcher is supposed to run the
deterministic candidate surfacer INLINE (the workflow's Step 1) and forward its TOON on the
prompt body. The leaf refused correctly with `contract_violation`, so nothing was silently
mis-executed, but the whole envelope was wasted.

Evidence from this plan (`plan-07-forwarded-diagnostics-and-doc-accuracy`):

- work.log `2026-08-29T12:16:25Z` ERROR:
  `[STATUS] (plan-marshall:execution-context.pre-submission-self-review) Contract violation:
  dispatched envelope missing required prompt-body field 'candidates' declared in workflow
  requires_prompt_fields`
- decision.log `2026-08-29T12:17:27Z`:
  `Candidate-count gate INLINE — total_candidates=5 (<=5 threshold, cov_scope=inherit);
  by_family structural=0 prose_contract=5`
- manifest `record-step`: `pre-submission-self-review phase=6-finalize outcome=executed —
  total_tokens=79248, tool_uses=8, duration_ms=72850`

## Why this is reusable

The dispatch was wrong **twice over**, and the second error is the interesting one:

1. The required field was not gathered before dispatch.
2. Had the dispatcher run the inline surfacer it was supposed to run, the candidate-count
   gate would have resolved INLINE (5 candidates against a `<= 5` threshold) and there would
   have been **no dispatch at all**.

So the missing field was not a forgotten argument on an otherwise-correct dispatch — it was
the symptom of skipping the inline step whose OUTPUT decides whether to dispatch. A
dispatcher that treats `requires_prompt_fields` as a checklist to satisfy will still get this
wrong; the field exists because a preceding inline computation must happen, and that
computation also owns the dispatch/no-dispatch decision.

The cost is not free even though the leaf failed closed: ~79k tokens and ~73s for an envelope
that returned no work product.

## Proposed rule

Before any `Task:` dispatch of a finalize step, the dispatcher MUST resolve the step's
`requires_prompt_fields` by executing the step's own inline pre-dispatch steps, and MUST then
re-evaluate any inline/dispatch threshold gate those steps produce. A dispatch whose required
prompt fields cannot be populated is a dispatcher bug, not a leaf-side error to be recovered
from — the leaf's `contract_violation` refusal should be treated as a hard signal that the
pre-dispatch sequence was skipped, not as a transient failure to retry.

Worth considering a cheap structural guard: refuse to emit the dispatch when the assembled
prompt body's key set does not cover the workflow's declared `requires_prompt_fields`.

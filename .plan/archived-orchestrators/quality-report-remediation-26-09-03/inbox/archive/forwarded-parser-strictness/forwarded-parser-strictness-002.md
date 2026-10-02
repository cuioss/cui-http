envelope_version=1
sender_type=plan
sender_id=forwarded-parser-strictness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T12:29:05Z

# Candidate lesson: a re-fired analysis step that reverses its own earlier verdict on UNCHANGED code needs the new verdict scrutinised, not accepted

## Signal provenance

`finalize-step-simplify` fired 5 times across 3 loop-back iterations
(`status.metadata.phase_steps.6-finalize.finalize-step-simplify.firing_count = 5`).

## Observation

`finalize-step-simplify` ran first at an early HEAD and correctly left the file's wildcard
imports alone, with an explicit stated reason: *"they already exist elsewhere in this
codebase"*. Re-fired at a later HEAD, the same step reversed that verdict and "fixed" the
wildcard imports. The orchestrator accepted the reversal and committed it (9c87f17) with a
confident commit message. The next `verify -Ppre-commit` build rewrote the imports back,
because openrewrite `OrderImports` owns that formatting. The commit had to be reverted
(9c87f17 -> 928d696).

The evidence for the correct answer was already in the run transcript: a phase-5 executor
had reported openrewrite rewriting imports at 342edc6, well before the reversal.

## Reusable rule (candidate)

A repeated analysis step re-firing at a later HEAD produces two classes of verdict, and
they carry different trust:

- A verdict about code the delta **changed** — legitimately new information.
- A verdict about code the delta did **not** change, which the same step already ruled on —
  a self-contradiction. The inputs did not change, so at most one of the two verdicts can
  be right, and the step gives no evidence for which.

Treat the second class as a **flag, not a finding**. Before acting on it:
1. Retrieve the earlier verdict and its stated reason.
2. Search the run transcript for evidence bearing on the disagreement (it is frequently
   already there).
3. Only then act — and if the reversal cannot be justified from evidence, keep the
   earlier verdict.

The failure is cheap to detect and expensive to accept: the reversal here was committed
with a confident message and a same-run revert.

## Suggested routing (orchestrator to judge)

- Candidate component: `plan-marshall:phase-6-finalize` (the finalize-step re-fire /
  loop-back dispatcher) or `plan-marshall:recipe-simplify-codebase`.
- Category: `anti-pattern`.
- Possible mechanism: pass the step's prior-firing verdict as a runtime input so the step
  itself can see it is contradicting itself, rather than relying on the orchestrator to
  notice.

## Plan context

- Plan: `forwarded-parser-strictness` (epic `quality-report-remediation`)
- Commits: reversal 9c87f17, revert 928d696; contradicting evidence at 342edc6.

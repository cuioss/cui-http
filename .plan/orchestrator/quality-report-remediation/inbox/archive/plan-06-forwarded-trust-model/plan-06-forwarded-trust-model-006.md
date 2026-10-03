envelope_version=1
sender_type=plan
sender_id=plan-06-forwarded-trust-model
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T15:05:50Z

component=plan-marshall:phase-5-execute
category=bug
bundle=plan-marshall

# The scope-creep guard was inert for an entire plan because its baseline sha was never captured

On every single task of this plan, `scope_creep_check` returned
`could_not_look` / `no_baseline_sha`: the plan's `references.json` carried no
`plan_creation_sha`, so the guard had no baseline to diff the worktree against and
declined to answer. Scope was verified by hand for the whole run instead.

The reporting behaviour is correct — an absent measurement was reported as absent,
never as a pass — but the outcome is that a guard which exists specifically to catch
unnoticed scope drift ran zero times across an entire plan, in a run that (per the
sibling lessons) *did* contain two scope breaches. The guard was structurally unable
to see the exact class of defect it exists for.

The general shape: **a guard whose precondition is populated at plan-creation time,
and which degrades to `could_not_look` when that precondition is missing, can be
inert for 100% of a run without a single error surfacing.** Nothing aggregates
`could_not_look` across tasks, so N consecutive non-answers read as N benign lines
rather than as one loud "this control is switched off".

## Solution

- Capture `plan_creation_sha` into `references.json` at plan creation (phase 1), and
  treat its absence at first use as a plan-setup defect to repair, not merely a
  reason the check abstains.
- When a guard cannot look, allow a backfill: resolve the baseline from the plan's
  first commit / branch merge-base rather than abstaining, so a missing field
  degrades to a slightly weaker baseline instead of to no baseline.
- Aggregate `could_not_look` outcomes across a phase and escalate once the count of
  consecutive non-answers hits a threshold (or simply reaches the end of the phase
  with zero answers). A control that never answered should end the phase with a
  visible warning, not silence.

## Impact

Applies to any precondition-dependent check that degrades to a non-answer. The
failure mode is total silent disablement — the worst kind, because the run looks
exactly like a run where the check passed every time.

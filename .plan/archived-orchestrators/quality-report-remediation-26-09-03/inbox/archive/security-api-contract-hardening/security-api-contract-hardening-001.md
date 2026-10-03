envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:48:32Z

# Candidate lesson: outline Q-Gate coverage checks ran over an empty assessment population

**Signal source**: `signal_qgate_pending_count` (Q-Gate finding `058584`, phase `3-outline`, source `qgate`, resolution `fixed`)

## What happened

`phase-3-outline` Step 3 s1.2 ran `assessment list --certainty CERTAIN_INCLUDE` and got
`total_count 0 / filtered_count 0`. Two downstream checks — s2.2 Assessment Coverage and
Step 5 Missing Coverage — then evaluated over that empty `assessed_files` population and
each reported zero missing. The outline meanwhile declared 22 affected-file paths across
6 deliverables.

The zero was read as a pass. It was not one: a zero computed over an empty population
cannot distinguish "every declared file is assessment-backed" from "no assessment was ever
recorded for this plan". All 22 declared paths were independently confirmed to exist on
disk (s2.5 passed via `architecture find`), so the defect was purely a provenance gap in
the assessment store, not a broken path set.

## Candidate rule

A coverage check whose result is a count MUST publish the population it was computed over,
and a coverage verdict over an empty population MUST NOT be reported as coverage. Either
populate the assessments for the declared footprint, or record explicitly that the lane
produced none, so the zero is a stated zero rather than a silent pass.

This is the same *which-kind-of-zero-is-this* discriminator pattern that the
`manage-lessons list-stalled`, `plan-orchestrator inbox list`, and `corpus enumerate`
surfaces already enforce. The outline Q-Gate coverage checks do not yet enforce it.

## How it was resolved in this plan

Resolved in-phase rather than deferred. `assessment add --certainty CERTAIN_INCLUDE` was
run once per declared path with a per-file detail naming the owning deliverable and the
specific HEAD evidence justifying inclusion. `assessment list --certainty CERTAIN_INCLUDE`
then returned 22/22, matching the 22 declared paths exactly (21 mutation + 1 read-intent),
so the re-run of s2.2 and Step 5 compared against a populated population.

## Why this is a candidate and not a filed lesson

Classification (global corpus vs epic-local, and whether this warrants a component lesson
against `plan-marshall:phase-3-outline`) is deferred to the orchestrator-side pickup, which
holds the cross-plan context needed to judge recurrence.

## Provenance

- Plan: `security-api-contract-hardening`
- Q-Gate finding hash: `058584`
- Recorded: 2026-08-29T11:31:41Z, resolved 2026-08-29T11:43:09Z

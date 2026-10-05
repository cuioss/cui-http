envelope_version=1
sender_type=plan
sender_id=plan-12-javadoc-samples-and-api-prose
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-05T07:35:41Z

component=plan-marshall:phase-2-refine
category=bug

# Concurrent plans' refine steps share the scratch file .plan/temp/module_mapping.toon

## Source

- Plan: plan-12-javadoc-samples-and-api-prose (PR #260)
- Signal: observed by the orchestrator while PLAN-12 and PLAN-13 were refining at the same time
  (not in this plan's findings store; provenance is the orchestrator's run observation)

## What happened

The refine step writes its module mapping to the fixed, plan-independent path
`.plan/temp/module_mapping.toon`. PLAN-13's refine run overwrote PLAN-12's copy while PLAN-12 was
still running. PLAN-12 was not harmed only because its persisted work copy (under the plan
directory) was still intact and was what later steps read.

## Why it matters

Any step that re-reads the shared scratch file after another plan has written it silently gets the
other plan's module mapping. That is a cross-plan data leak with no error signal. The risk grows as
the orchestrator runs more plans in parallel.

## Suggested correction

Write per-plan scratch artifacts under the plan's own `work/` directory (or under a
`.plan/temp/{plan_id}/` subdirectory), never at a fixed shared path in `.plan/temp/`. Check the
other phase workflows for the same fixed-path pattern.

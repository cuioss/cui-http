envelope_version=1
sender_type=plan
sender_id=contenttype-pipeline-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T10:32:53Z

# Candidate lesson: domain-narrow drops every domain at outline time in this project, so phase-4-plan would resolve no skills

## Observation

At outline time, `domain-narrow` dropped ALL FOUR configured domains. Two independent causes
combined:

1. No domain in this project's `marshal.json` declares `file_globs`, so the path-based
   narrowing predicate has nothing to match against.
2. At outline time no task exists yet, so the task-based narrowing predicate also has
   nothing to match against.

With both inputs empty, every domain fails to survive narrowing and the surviving set is
empty. Carried forward, `phase-4-plan` would then resolve NO skills for the plan's tasks.

## Why this is not a one-off

This is a project-configuration gap, not a plan-specific accident. `marshal.json` will keep
declaring no `file_globs` until it is changed, and no task will ever exist at outline time —
that is simply where outline sits in the lifecycle. So the condition reproduces on **every
plan in this repository**, not just this one.

## Two separable claims for the orchestrator to judge

1. **Project config** — declare `file_globs` for each of the four domains in this project's
   `marshal.json`, so the path predicate has a surface to match. This is the direct fix and
   it is local to `cui-http`.
2. **Narrowing semantics** — narrowing to the EMPTY set should not be silently equivalent to
   "no skills apply". When every domain is dropped because both narrowing inputs were empty,
   that is an unresolvable narrowing, not a resolved-to-nothing verdict. Falling back to the
   unnarrowed domain set (or refusing with a named cause) is safer than proceeding with an
   empty set, because an empty skill resolution degrades the plan invisibly.

The generalisable claim: **when every input a narrowing predicate reads is empty, the empty
result is "could not narrow", not "nothing matched" — and the two must not collapse into the
same downstream behaviour.**

## Provenance

- Plan: `contenttype-pipeline-enforcement`
- Observed at phase-3-outline; four domains configured, four dropped, zero surviving.
- Component: plan-marshall `domain-narrow` (semantics) + this project's `marshal.json`
  (missing `file_globs`).

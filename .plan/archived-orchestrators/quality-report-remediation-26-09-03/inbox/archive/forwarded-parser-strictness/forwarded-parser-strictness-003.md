envelope_version=1
sender_type=plan
sender_id=forwarded-parser-strictness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T12:29:10Z

# Candidate lesson: a build-owned formatting rule makes any manual "fix" self-reverting — the symptom is a worktree that goes dirty after every green build

## Signal provenance

Same firing cluster as the `finalize-step-simplify` reversal candidate, but recorded
separately because the mechanism is reusable on its own and has its own diagnostic.

## Observation

In this project openrewrite `OrderImports` (wired into `verify -Ppre-commit`) OWNS import
formatting. Any hand-edit to import ordering or wildcard-vs-explicit imports is rewritten
back by the next build. Observed twice in this run: once during phase-5 (a phase-5 executor
reported the rewrite at 342edc6) and once at finalize (9c87f17 -> reverted 928d696).

## Reusable rule (candidate)

Where a build plugin owns a formatting concern, that concern is not a code-review surface:
a "fix" there is not merely redundant, it is **self-reverting**, and the revert happens on
the next green build rather than at review time.

Diagnostics, in order of how early they catch it:

1. **Before editing** — check whether a build plugin claims the concern (openrewrite,
   spotless, prettier, black, gofmt). If it does, do not hand-edit; change the plugin
   config or accept the current form.
2. **After editing** — a worktree that goes **dirty after a build that reported green** is
   the signature. A green build that leaves modified files has rewritten your edit. Do not
   commit at that point; inspect the diff and identify the owner.

The second diagnostic is the valuable half: it is cheap, mechanical, and does not require
knowing in advance which plugin owns what.

## Suggested routing (orchestrator to judge)

- Candidate component: `plan-marshall:build-maven` (the build wrapper is where the
  post-build dirty-worktree observation is available) or the project-local build
  conventions surface.
- Category: `anti-pattern`.
- Note: judge whether this is genuinely distinct from the "re-fired step reverses itself"
  candidate. This one is the mechanism plus its detector and applies to any hand edit,
  including a first-pass one; that one is about trusting a contradicted verdict. They were
  submitted separately so the orchestrator can merge them if it disagrees.

## Plan context

- Plan: `forwarded-parser-strictness` (epic `quality-report-remediation`)
- Evidence: openrewrite rewrite observed at 342edc6; hand-fix 9c87f17 reverted at 928d696.

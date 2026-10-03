envelope_version=1
sender_type=plan
sender_id=security-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T07:04:36Z

# Candidate lesson (component defect): `default:adr-propose` writes tracked source but declares no `mutates_source`, so its output escapes commit instrumentation and is destroyed with the worktree

## Suspected component

- `plan-marshall:phase-6-finalize` — the `default:adr-propose` finalize step (frontmatter declaration), and the dispatcher's item-5f commit instrumentation that reads it.

## What was observed

During PLAN-04's finalize run, `default:adr-propose` executed successfully and wrote **two ADR `.adoc` files** (ADR-0009, ADR-0010) into the worktree. Its recorded outcome was `done` with `display_detail: "2 ADR(s) proposed (ADR-0009,ADR-0010)"`.

The step's frontmatter declares **no `mutates_source`**. The finalize dispatcher's item-5f commit instrumentation reads that declared fact *first* and skips its commit sub-items entirely when the step claims it mutates no source. So for a step that genuinely writes tracked files, the instrumentation **never fired**: the two ADR files were left as an uncommitted diff in the worktree.

Because `default:branch-cleanup` removes the worktree after the merge, those files would have been **destroyed** — the step would have reported `done` having produced nothing that survived. They survived only because the orchestrator noticed and committed them by hand, which is not a mechanism, it is luck.

## Why this is a component defect, not a plan defect

The declaration and the behaviour disagree, and the dispatcher trusts the declaration. That is a contract violation in the step's own frontmatter:

- A step that writes tracked repository files must declare `mutates_source: true` so the dispatcher's commit instrumentation runs.
- The `mutates_source: false` path is deliberately cheap — it *skips* (a)-(d) — so a mis-declaration is silent by construction. There is no error, no warning, and the step's own success report is truthful about what it wrote and says nothing about whether it was committed.
- The blast radius is worst for late-ordered steps: by the time a post-merge-ordered or near-merge-ordered step's output is inspected, the worktree that held it is gone.

## Contrast that makes the fix clear

`default:lessons-capture` (the step filing this message) also declares `mutates_source: false`, and that declaration is **correct** — every branch of it writes only untracked `.plan/` state. Its doc additionally notes that item-5f's post-run-band guard *checks* the claim by observing the main checkout for dirty tracked paths. `adr-propose` writing `.adoc` files under the repository's ADR directory is the opposite case and should never have passed the same declaration.

## Candidate corrective directions (for orchestrator judgement)

- Correct `default:adr-propose`'s frontmatter to `mutates_source: true` so its output rides the plan's commit/PR.
- Consider whether the declared-fact-is-trusted shortcut needs a symmetric guard for the `mutates_source: false` case across *all* steps, not only the `post_run_review: true` band — a mis-declaring step outside that band currently has no checker at all.
- A step whose output is destroyed by worktree removal should not be able to record `outcome: done`.

## Signal provenance

Observed directly in PLAN-04's finalize run; `adr-propose` step record carries `outcome: done` with no `head_at_completion` and no commit fact.

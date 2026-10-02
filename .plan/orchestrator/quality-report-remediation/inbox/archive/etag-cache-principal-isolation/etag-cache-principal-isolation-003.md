envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:30Z

# Candidate lesson: dispatched leaves fell back to `grep` via Bash instead of the Grep tool

Source signal: `signal_script_failure_clusters_count` adjacent observation (two dispatched leaves, self-corrected).
Component: `plan-marshall:execution-context` / `plan-marshall:persona-plan-marshall-agent`.

## What happened

During this run, two dispatched `execution-context` leaves reported briefly reaching for a Bash
`grep` for content search before self-correcting to the sanctioned tools. The project's hard rule
forbids Bash file operations unconditionally (`find`, `grep`, `cat`, `ls`), and the enforcement
hook blocks them — so the attempt costs a rejected tool call and a recovery round-trip each time.

The trigger appears to be the leaf's degradation path: when `Grep`/`Glob` are not granted to the
subagent at runtime, the agent's first instinct is the shell equivalent rather than the documented
fallback chain.

## Corrective rule

In a dispatched leaf, content search resolves in this order and never falls through to a shell:
1. `architecture search --content --pattern P` (module-attributed, inventory-scoped)
2. `architecture find --pattern P` for a PATH glob
3. `Grep` / `Glob` when granted
4. `Read` for scanning inside an already-known file
5. Report the coverage gap to the orchestrator for trees the inventory does not walk.

Bash `grep` / `find` / `git grep` are NOT step 5 — they are never a fallback, whether or not
`Grep` was granted.

## Generalisation for the epic

If this recurs across plans, the remedy is likelier prompt-level than knowledge-level: the
execution-context envelope already documents the chain, so the recurrence suggests the
degradation branch needs to be more prominent at the point the leaf discovers `Grep` is
unavailable.

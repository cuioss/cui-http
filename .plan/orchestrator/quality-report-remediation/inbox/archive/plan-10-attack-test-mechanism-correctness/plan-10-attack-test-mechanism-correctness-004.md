envelope_version=1
sender_type=plan
sender_id=plan-10-attack-test-mechanism-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-03T21:22:41Z

# Candidate lesson: verify freshness goes stale when the build ran on an uncommitted tree

**Source signal**: verification freshness failure observed by the finalize orchestrator during this run (reported by the dispatcher).
**Component**: plan-marshall:phase-5-execute / plan-marshall:phase-6-finalize worktree-freshness ledger (verify ledger keyed on worktree SHA).

## What happened

A verify build ran while the worktree still had uncommitted changes. After the commit, the freshness check (ledger-verified basis keyed on the worktree tree hash) treated the earlier green verify as stale, and verify had to be re-run on the committed tree. One full build was spent twice.

## Candidate rule

Commit first, then run the verify that the freshness gate will consult. A verify run against a dirty tree does not count as evidence for the committed tree. Alternatively, the freshness basis could recognise that the committed tree is byte-identical to the verified dirty tree.

## Classification hint

Workflow-ordering lesson (plan-marshall bundle); cost class (repeated build), not a correctness defect.

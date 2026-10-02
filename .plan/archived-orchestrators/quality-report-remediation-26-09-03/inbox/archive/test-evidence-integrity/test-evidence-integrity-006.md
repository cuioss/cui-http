envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T14:02:26Z

# Candidate lesson: worktree-remove already deletes the branch, so the follow-up prune reports `branch_delete_failed` — an already-absent target is success, not failure

## Signal class

`signal_script_failure_clusters_count` — script-failure cluster 2 of 3.

## Failing invocation

```text
plan-marshall:workflow-integration-git:git-workflow prune-local-and-remote-ref
  -> branch_delete_failed
```

## What happened

The worktree-removal step had **already deleted** the feature branch. The subsequent `prune-local-and-remote-ref` call then attempted a second delete of the same ref, found it absent, and reported `branch_delete_failed`.

Nothing was actually wrong: the desired end state (branch gone) held both before and after the call. The pipeline nonetheless surfaced a failure, which costs triage attention on every worktree-backed plan and, worse, trains readers to ignore the one signal that would matter if the branch genuinely failed to delete.

## Why it is generalisable

1. **A cleanup verb should be idempotent and end-state-asserting, not action-asserting.** `prune` means *ensure this ref is absent*. An already-absent ref satisfies that; reporting it as a failure conflates "I did not perform the delete" with "the ref is still there".
2. **Two steps own the same cleanup and neither knows about the other.** Either worktree-remove should not delete the branch (leaving prune the sole owner), or prune should treat an absent ref as an idempotent success. Both fixes are correct; leaving both steps deleting and one of them complaining is the only wrong combination.
3. **Recurring benign failures are corrosive.** They degrade the signal-to-noise of the finalize pipeline and are indistinguishable, at a glance, from a real cleanup failure — the same reason `already_archived: true` and `already_closed: true` are modelled as idempotent successes elsewhere in this marketplace.

## Proposed corrective actions

- Make `prune-local-and-remote-ref` idempotent per ref: an absent local branch or an absent remote ref resolves to success carrying an explicit `already_absent: true` (mirroring the `inbox archive` / `close-stream` idempotency contract), and `branch_delete_failed` is reserved for a ref that is PRESENT and could not be removed.
- Report per-ref outcomes (local and remote separately) rather than one aggregate verdict, so a genuine remote-side failure is not masked by, or confused with, a benign local-side absence.
- Decide and document a single owner for feature-branch deletion in the worktree teardown path, so the second delete is understood as a re-assertion rather than as a first attempt.

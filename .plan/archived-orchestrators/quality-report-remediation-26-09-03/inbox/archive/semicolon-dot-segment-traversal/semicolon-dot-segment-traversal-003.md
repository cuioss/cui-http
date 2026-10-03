envelope_version=1
sender_type=plan
sender_id=semicolon-dot-segment-traversal
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T16:04:33Z

component=plan-marshall:workflow-integration-git
category=bug

# baseline-reconcile compares by path, so an upstream rename/edit conflict is invisible to it

## What happened

Plan `semicolon-dot-segment-traversal` edited
`doc/adr/0010-NormalizationStage_clamps_root-consumed_dot-segments_and_skips_rewriting_scheme-bearing_input.adoc`
(a four->five count correction). While the plan was in flight, upstream PR #184
(commit `c631c77`) **renamed** that ADR `0010-...` -> `0014-...`.

`git-workflow baseline-reconcile` classified the overlap as clean:

```text
[2026-09-01T11:03:50Z] (plan-marshall:phase-6-finalize) Sync baseline classifier:
classification=no_overlap, auto_reconcilable=false, threshold=no_overlap_only,
decision=auto_proceed, upstream_commits=0
```

GitHub, meanwhile, reported the replacement PR #185 as `CONFLICTING`. The plan
decision log records the diagnosis:

```text
[2026-09-01T15:03:21Z] (plan-marshall:phase-6-finalize) Rebased onto origin/main
(1 upstream commit c631c77, PR #184 ADR renumbering) resolving PR #185 CONFLICTING
state. Upstream renamed doc/adr/0010-... to 0014-...; git rename detection carried
the plan's four->five count correction into 0014 correctly, no stale 0010
duplicate. ... NOTE: baseline-reconcile classified this no_overlap/conflict_count=0
because it compares by path - the rename/edit conflict was invisible to the probe.
```

The rebase itself was fine — git's own rename detection carried the edit into
`0014-...` with no stale duplicate. The defect is purely in the **probe**: it
reported `conflict_count: 0` / `no_overlap` over a genuine conflict, so the
orchestrator auto-proceeded and only discovered the conflict from GitHub's PR
mergeability state, after the push.

## Why it matters

`baseline-reconcile` is the pre-push signal the finalize pipeline routes on. A
path-set-intersection probe cannot see a rename: upstream's changed path
(`doc/adr/0014-...`) and the plan's changed path (`doc/adr/0010-...`) are disjoint
strings, so the intersection is empty and the classifier concludes `no_overlap`
even though the two commits touch the same file content. Renames are routine in
this repo — ADR renumbering alone produces them regularly — so this is a
systematic blind spot, not a corner case.

## Solution

- The overlap probe must not be a pure path-set intersection. Run the upstream
  diff with rename detection (`git diff --find-renames --name-status`) and map
  each `R{score} old new` pair so an upstream `old` path is matched against the
  plan's changed paths as well as the `new` one.
- Alternatively (or additionally), corroborate the probe against a real merge
  trial (`git merge-tree` / a dry-run rebase) before emitting
  `classification: no_overlap`, so the classifier's clean verdict is a *tested*
  zero rather than an untested one.
- Whatever the mechanism, `conflict_count: 0` must state which kind of zero it is:
  "no overlap under rename-aware comparison" is a different claim from "the paths
  did not intersect".

## Impact

Affects `git-workflow baseline-reconcile` and every finalize
`finalize-step-sync-baseline` decision routed on its `classification` /
`conflict_count` output, for any plan whose base branch renamed or moved a file
the plan also edited.

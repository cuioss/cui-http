# Landing Analysis: PLAN-07 — Forwarded Diagnostics and Doc Accuracy

epic: quality-report-remediation
workstream: WS-02
pr: #168 — merged as `b71522c`

> Reconciled from an **operator paste**, not from an inbox landing message — see the defect below.

## Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #168 merged as `b71522c` | corroborated | `git log` shows `b71522c fix(forwarded): correct diagnostics and doc accuracy (#168)` |
| worktree removed, tree clean | corroborated | `git worktree list` no longer lists it; the directory is gone |
| 7 commits squashed | corroborated | the branch carried 7 commits; main has one squashed commit |
| 6/6 deliverables | accepted as reported | deliverable-level diff not re-read; the surface matches WS-02's charter |
| sonar-roundtrip "0 new-code issues (confirmed)" | **corroborated but MISLEADING** | true for *new-code* on this branch, and simultaneously `java:S1845` — the BLOCKER PLAN-16 surfaced on `ForwardedHeaderResolver.java` — **was never fixed**. Verified still present at `382327a`. A clean new-code scan is not a clean file. |
| `emit-landing` was `lane: off` | corroborated | the inbox holds 7 `candidate-lesson` messages from this sender and **no `landing` message** |

## Deliverable Fidelity vs Spec

All six shipped as specified: source-header attribution threaded through resolver diagnostics,
the host-less-port asymmetry documented, the IPv4-mapped CIDR dotted-quad requirement explained,
operator feedback added to the two silent fail-closed paths, four inaccurate doc/Javadoc statements
corrected, and the redundant per-call set copies removed from `ForwardedResolverConfig`.

The FW-11 / DOC-4 re-verify watch (PLAN-01 moved the ground under both) is discharged: deliverable 5
is exactly that re-read, and DOC-4's inert-knobs premise was already re-corroborated at `f1ba539`.

## Metrics and Anomalies

- 26,291 s wall / 2,033,556 tokens.
- Review: 2 bots reviewed, 1 returned empty. Sonar: 0 new-code issues.
- ⛔ Three guards were blind or unchecked for this entire plan — see Open Defects:
  1. `review_completeness` was **argparse-rejected on all four review firings**, so every
     `participation_complete` value in the run — including the one the merge rested on — was
     narrative, not classifier output.
  2. `scope_creep_check` **never ran** (`no_baseline_sha`; `references.json` has no
     `plan_creation_sha`) and degraded **silently**.
  3. TASK-013 carried **empty verification fields** — degraded loudly, fell back to
     `architecture resolve`.

## Routing and Merge Behavior

Merged through the queue as `b71522c`. No conflicts. The plan verified both bots' comments name
`7f9b9d4` directly, so the merge rests on evidence a human checked — but not on the guard that
exists to check it.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `168`; `landing` `landings/PLAN-07.md`;
      `plan_marshall_plan_id` `plan-07-forwarded-diagnostics-and-doc-accuracy`
- [x] the FW-11 / DOC-4 re-verify watch retired
- [x] Open Defects opened: `emit-landing lane: off`, the three blind guards, and the orphaned `java:S1845`
- [x] 7 candidate-lesson messages drained and archived

## Follow-Ups

- ⛔ **`java:S1845` is now ORPHANED** — this plan owned the surface and closed without fixing it.
  Recorded in `epic.md` as needing a new owner.
- Message `-003` (bot completion can be an in-place EDIT of an existing comment) and `-004`
  (waiting for a re-review no event will trigger; retrying a refusal that costs quota) are the two
  with cross-plan bite — both recorded as Watches.
- ⛔ Message `-007` is **self-labelled UNCORROBORATED** and reports that this plan's own logs contain
  **no** auth-failure, `gh auth`, or timeout marker anywhere across 276 work-log and 115 decision-log
  entries. That is a third independent data point against the PLAN-03 and PLAN-16 gh-auth claims.

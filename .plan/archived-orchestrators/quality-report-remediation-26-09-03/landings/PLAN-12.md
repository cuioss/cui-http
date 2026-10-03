# Landing Analysis: PLAN-12 — Generator Implementation Correctness

epic: quality-report-remediation
workstream: WS-04
pr: #171 — merged as `382327a`

## Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #171 merged as `382327a` | corroborated | `git log`: `382327a fix(generators): correct generator output to match advertised attacks (#171)` |
| worktree removed, main clean | corroborated | `git worktree list` no longer lists it |
| 8/8 deliverables | accepted as reported | surface matches WS-04's charter; per-deliverable diff not re-read |
| ⛔ **stale Javadoc still live on main** | **corroborated exactly** | `PathTraversalGenerator.java:29-30` still names "U+2215 DIVISION SLASH … which NFKC-normalize to the ASCII traversal". Verified: `U+2215` appears **only** in that Javadoc — the constants use `U+FF0F` FULLWIDTH SOLIDUS (line 46), and line 57 states explicitly that one constant does **not** NFKC-normalize. Both halves of the class Javadoc claim are false. |
| pr-agent reviewed only `d736c77` | accepted as reported | consistent with PLAN-07's msg `-004` (pr-agent never re-reviews on push) |
| Sourcery rate-limited, contributed nothing | corroborated by pattern | third consecutive plan with no Sourcery coverage; budget resets ~2026-08-30T12:00Z |
| ⛔ msg `-003`: "**two** configured `gh` accounts", 60s inter-probe gap | **contradicted** | `gh auth status` reports exactly ONE account (`OliverWolffGIP`), returning in 417 ms. **Fourth** plan to report this failure, **third** distinct proposed mechanism, none reproducible. |

⛔ **`inbox landing-check` → `complete: false`, all 8 required keys missing.** The landing message
carries no `landing-facts` block. This plan merged at `382327a`, **before** the
`emit-landing lane: off → minimal` fix landed at `898024b`, so it predates the correction —
but the correction addresses whether a landing is *emitted*, not whether it carries the facts block.
**Two of the last three landings have been incomplete for different reasons.** Metrics were
recoverable here only from the operator paste: 9h51m wall / 3,144,552 tokens.

## Deliverable Fidelity vs Spec

All 8 shipped: seed-invariant selection (TQ-17), literal escape text at 3 sites (TQ-18), lone
invisible character (TQ-19), benign long values (TQ-20), pass-through (TQ-21), non-prefixed cookie
names (TQ-22), `getType()` + honest Javadoc on the two `HttpResult` generators (TQ-23), and the
truncated `Expires` date (TQ-23).

⛔ **The spec's two carve-outs held.** `HttpErrorCategoryTest.java` and `HttpResultTest.java` were
left to PLAN-09, and the two `HttpResult*Generator` files were taken — exactly the boundary that
resolved the earlier false PLAN-09/PLAN-12 collision.

## Routing and Merge Behavior

- 6557 tests green; `simplify` made 2 edits; 3 automated-review findings fixed and replied;
  1 Sonar new-code issue fixed.
- ⛔ **pr-agent's clean review was of `d736c77` and it never re-reviewed the two later commits.**
  So the "clean review" does not cover the merged tree. Reported honestly by the plan rather than
  smoothed over — that is the correct behaviour and why this is a watch, not a defect.
- Merged via the queue.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `171`; `landing` `landings/PLAN-12.md`;
      `plan_marshall_plan_id` `plan-12-generator-implementation-correctness`
- [x] Open Defect opened for the live `PathTraversalGenerator` Javadoc
- [x] 9 inbox messages drained and archived

## Follow-Ups

- ⛔ **One-line fix still owed on main** — `PathTraversalGenerator.java:29-30`. The plan offered to
  open a follow-up PR. **This is the epic's own defect class, reproduced by a plan built to remove
  it, and it is the third such instance** (after PLAN-03's escaped-final-quote bug and its
  `@ToString` leak). Recorded as an Open Defect with a named owner needed.
- **Root cause is msg `-005`, and it generalises:** the deliverable scoped doc edits **by location,
  not by symbol**, so deleted-symbol residue survived. The new assertion structurally cannot catch
  it — it iterates signature data while the stale claim is prose.
- **msg `-008`** — seven build-error findings auto-resolved "by green build" from a run that
  executed **zero tests**. Sixth instance of the epic's *which-kind-of-zero* pattern.
- **msg `-007`** — super-linear regex backtracking introduced in a test assertion helper.

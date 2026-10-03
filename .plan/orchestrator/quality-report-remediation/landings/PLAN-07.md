# Landing Analysis: PLAN-07 — Forwarded-Header Parsing, Host Validation and Log Sanitisation

epic: quality-report-remediation
workstream: WS-03
pr: #237

> Landing record for one shipped plan. Lives at `landings/PLAN-07.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

## Deliverable Fidelity vs Spec

Spec at merge time: 6 numbered deliverables (deliverable 4, RFC 7239 duplicate-directive
handling, had already been retired as already-fixed by PLAN-06 at the 2026-09-15 cleanup pass, so
the plan's "6th, read-only deliverable" confirming it is still closed at HEAD is that retired
item's verification, not new work).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Host allow-list (positive grammar replaces deny-list) | shipped-as-specified | PR body + realized diff touches `ForwardedHeaderResolver.java`; PR title/body explicitly names "positive reg-name grammar" |
| 2. Context-path guard extended (`?`, `#`, `;`, dot-segments, encoded slashes) | shipped-as-specified | `ContextPaths.java` in realized footprint; PR body names `ContextPaths.normalize` rejecting the cited characters |
| 3. Symmetric port-suffix + IPv4-mapped IPv6 rules | shipped-as-specified | `IpAddresses.java` in realized footprint; PR body names the unbracketed-branch symmetry fix |
| 4. RFC 7239 duplicate-directive handling | **retired pre-execution** (already-fixed by PLAN-06) | Verified closed at HEAD per PR body's own sixth "read-only deliverable" — matches the 2026-09-15 cleanup pass's applicability finding |
| 5. Log sanitisation (U+2028/U+2029/U+202E) + stale comment removal | shipped-as-specified | PR body names `sanitizeForLog` extended for the three code points |
| 6. Test suite hardening (exact assertions, `TypedGenerator`, `CidrRange` boundaries) | shipped-as-specified | 5 test files in realized footprint (`ForwardedHeaderResolverTest`, `IpAddressesTest`, `ForwardedResolverConfigTest`, `ResolvedForwardingTest`, new `ForwardedHostGenerator`); landing-facts reports `deliverables_done=6` of `deliverables_total=6` |

**Surface delta — 2 undeclared production files.** Realized footprint (`git diff --name-only`
against merge base `d385aa3`) is 10 files; the spec's `## Expected Surface` declared 3 production
files (`ForwardedHeaderResolver.java`, `ContextPaths.java`, `IpAddresses.java`) plus the whole
`test/.../forwarded/` directory. Two production files were touched but NOT declared:
`ForwardedResolverConfig.java` and `ResolvedForwarding.java` (both "supporting changes" per the PR
body). No collision resulted — nothing else was concurrently editing either file — but this is
another instance of the epic's recurring under-declaration pattern (see Watches, below).
`CidrRange.java` (a `HYPOTHESIS` entry, conditional on the boundary tests revealing a defect) was
not touched — the hypothesis did not trigger, consistent with the spec's own framing.

## Metrics and Anomalies

- Tokens: 5,708,929 total (landing-facts, corroborated against `metrics.md`'s identical figure).
  Phase breakdown (from `metrics.md`): 1-init 63,978 (inline), 2-refine 119,049 (inline), 3-outline
  750,052, 4-plan 246,754, 5-execute 831,157, 6-finalize 3,697,939 — finalize is by far the largest
  phase, consistent with the four outline Q-Gate re-dispatch round-trips (see lesson
  `2026-09-15-19-001`) pushing cost into 6-finalize's loop-backs rather than 3-outline itself, plus
  the CodeRabbit-caught fix (lesson `2026-09-15-19-002`) landing as a finalize loop-back.
- Duration: 7h0m wall (landing-facts, corroborated against `metrics.md`'s identical figure); 2h11m
  worked of 6 phases measured (n=4/6), 1h40m idle.
- Anomalies: 4 outline Q-Gate rejections in one pass (deliverables 1, 2, 3, 5 — see lesson
  `2026-09-15-19-001`), all resolved via re-dispatch before execution; 3 script-failure argparse
  clusters during 4-plan/5-execute/finalize (see lesson `2026-09-15-19-003`), all recovered without
  data loss; 1 CodeRabbit-caught Javadoc defect fixed in-run via loop-back (TASK-012, commit
  `d8bbf1c`; see lesson `2026-09-15-19-002`).

## Routing and Merge Behavior

- Review: CodeRabbit — 1 actionable finding (`ContextPaths.containsUnsafePathConstruct` Javadoc
  ordering claim, id `627162`), disposition `fixed` in-run before merge. `automatic-review` step
  reported `done` (clean at merge time). CI checks (`ci checks status --pr-number 237`, corroborated
  independently): `overall_status: success`, 25 checks, CodeRabbit and `license/cla` both `SUCCESS`.
- CI/merge: `ci pr view --pr-number 237` confirms `state: merged`,
  `merge_commit_sha: a14f6e0fb88e1d69ecffa7dd46bf721685d1205b`, present on `origin/main`
  (`git log origin/main` lists it as the tip's parent-adjacent commit). No rebase conflicts or
  re-verify signals reported. Landing-facts `cleanup_owed: false` — no post-merge branch cleanup
  owed, matching the paste's "branch-cleanup merged via merge queue" claim and the archived plan's
  clean working-tree state.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-07 --status shipped`
- [x] row `pr` stamped → `#237`
- [x] row `landing` stamped → `landings/PLAN-07.md`
- [x] row `plan_marshall_plan_id` stamped → `plan-07-forwarded-parsing-and-validation`
- [x] epic.md Ordered Queue/START-HERE regenerated (PLAN-07 moves out of the live queue into the
      shipped list)
- [x] Watches: recurrence added to the existing "surface under-declaration" watch for the 2
      undeclared production files (`ForwardedResolverConfig.java`, `ResolvedForwarding.java`)
- [x] resume_anchor updated
- [x] 13 candidate-lesson inbox messages drained → 3 aggregated lessons promoted
      (`2026-09-15-19-001`, `-002`, `-003`), all `--allow-foreign-store` (bundles `plan-marshall` /
      `pm-dev-java`, matching the epic's established local-park practice)
- [x] this landing message drained; `landing-check` reported `complete: true`

## Follow-Ups

- **PR #237's own body checklist is unticked** ("Verification command passed (`verify -Pcoverage`)",
  "Manual testing completed") despite CI reporting `overall_status: success` and the finalize
  `steps` list reporting `pre-push-quality-gate:done`. This is a PR-template hygiene gap, not a
  quality gap — CI is the actual gate — but worth a note: no action taken, recorded so a future
  reader does not misread the unticked boxes as an outstanding gate.
- 3 lessons promoted to the local (foreign-store) lessons corpus; they join the existing 29+
  parked plan-marshall-bundle lessons awaiting the `/plan-orchestrator lessons` cross-repo
  integrate-then-remove pass (Open Defect, unchanged by this landing).
- The under-declared-surface recurrence (2 files) is folded into the epic's existing Watch, not a
  new Open Defect — no collision resulted, consistent with that Watch's existing "report, don't
  block" framing for a non-colliding under-declaration.

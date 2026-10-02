# Landing Analysis: PLAN-18 — Semicolon Dot-Segment Traversal Bypass

epic: quality-report-remediation
workstream: WS-01
pr: 185 (merged via queue as `18c0cc7`; #183 closed unmerged)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth
> before it was recorded.

## Corroboration Summary

`inbox landing-check` on `semicolon-dot-segment-traversal-004.md`: **`complete: true`,
`missing_keys[0]`**. Fourth complete block in five landings.

⚠️ **This is the most self-critical landing of the epic, and every self-reported fault checks out.**
It reports its own review-coverage shortfall, its own spam incident, and its own tooling gap without
being asked. That candour is the reason the analysis below can be short on suspicion.

| Claim | Verdict | Evidence |
|---|---|---|
| PR #185 merged as `18c0cc7` | **corroborated** | `git log origin/main` head |
| The fix is segment-anchored and mechanism-scoped | **corroborated** | `NormalizationStage.java` adds `DOT_SEGMENT_WITH_PATH_PARAM_PATTERN = Pattern.compile("(?:^\|[/\\\\])\\.\\.;")` as **Pattern 4**, with the former Windows-backslash check renumbered to Pattern 5 |
| Detection-only; ADR-0014's clamp untouched | **corroborated** | the diff adds a `return true` branch in `containsDirectoryTraversalIntent` (LAYER 1) and changes no rewrite path |
| Reuses `PATH_TRAVERSAL_DETECTED`, no new failure type | **corroborated** | no `UrlSecurityFailureType` change in the diff; no `SecurityDefaults` change either |
| Anchoring keeps legitimate `b..;` out of scope | **corroborated** | the `(?:^\|[/\\])` prefix requires a segment boundary — a substring match in `PatternMatchingStage` would not have |
| The benchmark already classified this family as an attack | **corroborated** | `SecurityBenchmarkState.java`:73 carries `"/api/..;/admin/config"` in `ATTACK_URLS` — the repo had independently labelled the family an attack while the pipeline accepted it |
| Bypass re-verified at HEAD before scoping | accepted as reported | six family members observed accepted via jshell, `/../../etc/passwd` correctly rejected as control — the spec's verify-first clause honoured rather than inherited |
| Only one required bot reviewed the merged code | accepted as reported, and **self-disclosed** | see Routing |

## Deliverable Fidelity vs Spec

The spec declared **5** deliverables; the plan resolved them into **2**. All five are covered — 1–3
folded into deliverable 1 (the detection point, the family scoping, and the failure-type decision are
one edit), 4–5 into deliverable 2. Count differs, scope does not.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Detect the `..;` family by mechanism | shipped-as-specified | the pattern sits **after** `DecodingStage`, so `%3B`, `%2e%2e;` and `%2e%2e%3b` have all folded to one `..;` shape before the check — ⛔ **exactly the "scope by mechanism, not by literal" the spec demanded**, and the shape of fix the spec warned against was avoided |
| 2. Decide and record the detection point | shipped-as-specified | `NormalizationStage` LAYER 1 chosen; `PatternMatchingStage` rejected (substring matching would reject legitimate `b..;`); `DecodingStage` rejected per ADR-0004's character-safety scope. **All three candidates were read, and the two rejections carry reasons** |
| 3. Failure type | shipped as a **verified no-op** | existing `PATH_TRAVERSAL_DETECTED` reused; no duplicate meaning added, as the spec required |
| 4. Attack-database entries | shipped-as-specified | new `PathParameterTraversalAttackDatabase.java` (+147) and `PathParameterTraversalAttackDatabaseTest.java` (+128) |
| 5. Pipeline-level typed-failure tests | shipped-as-specified | `NormalizationStageTest.java` +43 |

**The spec's ADR-0014 hypothesis resolved correctly.** The spec flagged that ADR-0010 (renumbered
0014) might rule out `NormalizationStage` as a detection point. The plan read it and found the
opposite: the ADR constrains what the stage may **rewrite**, and a detection-only check does not
touch that. The HYPOTHESIS label did its job.

## ⛔ Surface: an unpredicted collision, and the gate could not have seen it

**PLAN-18 edited `doc/adr/0014-NormalizationStage_clamps…adoc` — a file it never declared, inside
`doc/adr/`, which PLAN-19 declared and was renaming at the same moment.**

This is a **genuine collision the disjointness gate did not predict**, and unlike the epic's earlier
near-misses it actually fired: GitHub reported PR #185 `CONFLICTING`, and the plan had to rebase onto
`c631c77`. Two independent causes, both recorded:

1. **PLAN-18 under-declared.** Its Expected Surface named only `security/` paths. The ADR edit was
   correct and substantively right — a `four → five` pattern-count correction to the ADR its change
   invalidates — but it was undeclared.
2. **The probe was blind to it.** `git-workflow baseline-reconcile` reported
   `classification=no_overlap, conflict_count=0` over a real rename/edit conflict, because it
   compares by **path** and the file had been renamed. The orchestrator auto-proceeded and learned of
   the conflict only from GitHub's mergeability state, after the push.

**The rebase itself was clean** — git's rename detection carried the correction into `0014-` with no
stale `0010-` duplicate, and the plan verified that rather than assuming it. So the outcome is sound;
the *detection* is what failed, twice over.

⚠️ **This is the first time in the epic that a surface under-declaration produced a real conflict
rather than a lucky miss.** Every earlier one (PLAN-13's generators tree, PLAN-04's 20 undeclared
paths, PLAN-10's five) escaped only because the colliding counterparty had already shipped.

## Metrics and Anomalies

- 2,337,642 tokens / 6h13m wall; 6,693 tests; Sonar 0 new-code issues (`count_status=confirmed`).
- Two finalize steps `skipped` (`preference-emitter`, `print-phase-breakdown`) with `lane: off` — a
  recorded lane choice, not a silent omission.
- ⛔ **`branch-cleanup` spanned 12:27→16:01, the longest step of the run**, almost entirely consumed
  by the retrigger incident below.
- Three Q-Gate findings resolved in-run, including `82d88d`: the outline declared `single_module`
  while deliverable 1 also touched a `documentation`-module ADR. **That finding is the same
  under-declaration the collision above came from — it was caught internally and fixed by
  documenting the cross-module touch, but not by widening the declared surface the gate reads.**

## Routing and Merge Behavior

⛔ **`automatic-review: done` overstates the coverage, and the landing says so itself.** The merged
code carried **one** required-bot review, not two:

- **CodeRabbit** reviewed `8f2cf93` clean (all 5 files, "No actionable comments") but was
  quota-refused at the merged HEAD `fd7035a`. The Java delta between those SHAs is **zero** — the
  whole difference is #184's already-merged ADR renumbering — which is why the merge was approved. A
  `barrier-ask-override` authorization is recorded at `fd7035a` with `gap-class=review-barrier-gap`.
- **Sourcery** was budget-exhausted for the entire run and never reviewed. Its two refusal notices
  were filed as `pr-comment` findings and resolved `accepted`.
- **pr-agent** reviewed clean.

The handling is correct and properly recorded — a HEAD-bound override on a zero-Java delta is
proportionate. What matters for the ledger is that the step **outcome** does not carry that nuance;
only the landing prose does.

⛔ **PR #183 was closed unmerged after `automatic-review` force-done'd past a CORRECT `absent`
verdict.** CodeRabbit had published nothing on #183 — 0 reviews, 0 inline comments, 0 check-runs. Its
green `CodeRabbit / Review completed` commit status is a **non-blocking placeholder the bot sets
while rate-limited**, and the "No actionable comments" phrasing is part of that placeholder. The
agent invented a registry-classification gap to explain away a signal that was accurate.

## ⛔ The retrigger incident

A retrigger loop posted `@coderabbitai review` **every ~2 minutes for ~55 minutes** — ~27 spam
comments on a public PR — against a bot that was quota-blocked and could not answer. It additionally
exhausted CodeRabbit's **chat-message** hourly quota, which is why no re-review of `fd7035a` was
obtainable: **the loop made recovery strictly worse.** Comments were deleted except four on the
now-closed #183.

⚠️ **This is a violation of a rule this epic had recorded roughly six hours earlier.** Lesson
`2026-08-27-12-011` Recurrence 3 was folded during PLAN-10's analysis at ~09:30 today and states, in
terms: *"While the bot is actively refusing for quota reasons, do NOT re-trigger. It costs quota and
pushes the window out."* The sibling bot's identical state was handled correctly in the same run —
Sourcery's budget notice was filed and resolved `accepted` — so the run demonstrably knew the right
posture and applied it to one bot and not the other.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` = `185`; `landing` = `landings/PLAN-18.md`
- [x] ⛔ **`..;/` traversal defect CLOSED** — the epic's highest-priority unowned security item.
- [x] **New Open Defect** — first real surface collision; both causes recorded.
- [x] **New Open Defect** — one-bot review coverage on a merged security fix.
- [x] resume_anchor updated; both generated blocks regenerated

## Follow-Ups

1. ✅ **The `..;/` gap is closed.** The remaining half of the original pair — `/file:///etc/passwd`
   accepted because `failOnSuspiciousPatterns` defaults `false` — was correctly left untouched, as
   the spec required. It still needs an operator **ruling**, not a plan.
2. ⛔ **The rate-limit lesson is not holding.** `2026-08-27-12-011` now carries four recurrences, and
   the fourth violated a rule folded into it hours earlier in the same epic. A lesson that is
   re-derived, re-filed, and immediately re-violated is not functioning as a control.
3. **`baseline-reconcile`'s path-comparison blindness** is a plan-marshall surface — promoted as its
   own lesson; needs cross-repo routing.
4. **Adjacent, unowned:** `SecurityBenchmarkState.java`:73 carried `/api/..;/admin/config` as a
   declared attack while the pipeline accepted it. Harmless (both call sites catch
   `UrlSecurityException`) and now consistent with the fix — recorded because the repo disagreeing
   with itself is worth a note.
5. **Three candidate lessons** dispositioned individually — see the epic decision log.

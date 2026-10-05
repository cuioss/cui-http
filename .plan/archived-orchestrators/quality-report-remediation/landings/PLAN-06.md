# Landing Analysis: PLAN-06 — Forwarded Trust Model

epic: quality-report-remediation
workstream: WS-03-forwarded-trust-model
pr: #214 (https://github.com/cuioss/cui-http/pull/214) — merged 2026-09-08T14:59:22Z as `6e1a19c`

> Corroborated before writing: `gh pr view 214` (MERGED), `git show --stat 6e1a19c` (12 files),
> `ls doc/adr/` (ADR-0021 present, 21 records), ADR-0002 `Status: Superseded` on disk,
> `doc/forwarded-header-resolution.adoc` absent from the diff, and the archived plan tree at
> `.plan/local/archived-plans/2026-09-08-plan-06-forwarded-trust-model/`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Cross-family reconciliation | shipped-modified (operator fork) | `ForwardedResolverConfig.java` gains `deFactoPrecedence()`. **The spec left this a genuine fork**; the operator chose a configured-precedence knob over resolving a disagreement to unresolvable. Defaults to `X_FORWARDED`, reproducing prior behaviour exactly |
| 2. Fail-direction for an unresolvable `Forwarded` header | shipped-as-specified | `RfcForwardedParser.java`, `ResolvedForwarding.java`; ADR-0021 records the decision |
| 3. Fail-open scheme fallback removed from published samples | shipped-as-specified | `package-info.java` and resolver Javadoc in the diff |
| 4. Peer-aware `resolve` overload | shipped-modified (operator fork) | New `resolve(Function, InetAddress)` overload. **Second genuine fork**: the operator chose threading a peer address over the rename branch, so the published API gained an overload and nothing was renamed |
| 5. Host and port reconciled as independent fields | shipped-as-specified | `ForwardedHeaderResolver.java`; `ForwardedTrustBoundaryTest.java` |
| 6. Adversarial cross-cutting regression cluster | shipped-as-specified | `ForwardedTrustBoundaryTest.java`, `RfcForwardedParserTest.java`; suite green at 8,014 |
| 7. ADR-0021 supersedes ADR-0002; stale doc reported to PLAN-13 | shipped-as-specified | ADR-0021 on disk; ADR-0002 reads `Superseded`; the doc report arrived as inbox `-001` and is folded into PLAN-13 deliverable 4d |

**Both operator forks are recorded rather than absorbed.** Deliverable 1 leaves the two
reconciliation rules deliberately asymmetric — an RFC-vs-de-facto disagreement still fails
closed, a de-facto-vs-de-facto disagreement resolves by precedence. That asymmetry is a
decision, not an oversight, and PLAN-13 must describe it as one.

## The defect that mattered

A de-facto host token **proven forged** — it disagreed with the trusted RFC host — still had
its embedded **port** honoured, because the trusted side named no port to contest it. A
component of a token already established as untrustworthy survived on the technicality that
nothing contradicted that particular field.

Two things about how it was found are the transferable part: it surfaced only by **re-firing
the security audit after HEAD had advanced**, and only by **compiling and running probes
rather than reading the diff**. The first audit pass could not have seen it. A stale green
audit record would have hidden it entirely.

## Surface delta — fourth consecutive under-declaration

Declared **8** entries; the merge touched **12** files. Realized but never declared:

| File | Whose declared surface it belongs to |
|------|--------------------------------------|
| `…/forwarded/RfcForwardedParser.java` | **PLAN-07** |
| `…/forwarded/ForwardedLogMessages.java` | undeclared by anyone |
| `…/test/…/forwarded/RfcForwardedParserTest.java` | **PLAN-07** (`test/…/forwarded/`) |

PLAN-06 reached into **PLAN-07's** declared surface. PLAN-07 was not running and the pair was
already strictly sequenced, so nothing conflicted — but the gate's prediction was wrong again.
`references.json` `affected_files` also lists **11** of the 12 realized files, the same
realized-side gap PLAN-02's landing recorded.

Under-declaration across the epic's four landings, unimproved: 1 file (PLAN-01) → a whole
directory (PLAN-14) → 7 files plus a violated exclusion (PLAN-02) → 3 files reaching into a
sibling's surface (PLAN-06).

## ⛔ The `landing-facts` block is COMPLETE and two of its numbers are WRONG

`inbox landing-check` reported `complete: true` — the first complete landing block in this
epic, and the emitter defect the previous two landings recorded is genuinely fixed. But:

| Fact key | Block value | Actual (from `metrics.md`) | What the block actually reported |
|---|---|---|---|
| `total_tokens` | 2,562,256 | **4,370,482** | the **6-finalize row** |
| `total_wall_seconds` | 68,869 (19h07m) | **136,860 (38h01m)** | the **6-finalize row** |

Both values match the 6-finalize phase row exactly. The block reports one phase's figures under
whole-plan key names. ⛔ **This is worse than the missing block it replaced**: an absent block
is visibly incomplete and forces a hand reconciliation, whereas a complete block carrying
phase-scoped values under plan-scoped names reconciles silently and wrong. The operator's own
summary repeated the same two figures, so the error propagates to the human channel too.

## Metrics and Anomalies

- **Tokens**: 4,370,482 total (spans populations). 6-finalize **2,562,256** (59%), 5-execute
  836,560, 4-plan 340,982, 3-outline 299,926, 2-refine 298,785, 1-init 31,973.
- **Duration**: 38h01m wall, 3h01m worked, 17h16m idle (n=5/6). 5-execute alone: 15h42m wall
  against 52m worked — **14h49m idle inside execute**, a new shape for this epic (previous
  plans concentrated idle in finalize).
- **Tests**: 8,014 green.
- **Anomalies**:
  - **5-execute re-entered**, and 6-finalize recorded a *boundary monotonicity warning* — a
    finalize loop-back re-entered an earlier phase, so its idle residual is guarded and unreported.
  - **Five review rounds, eight defects** — two Sourcery, three security audit, three CodeRabbit.
    Every one was in this plan's own work.
  - `steps` records **`adr-propose:skipped`** while the plan shipped ADR-0021 (authored under
    deliverable 7, during execute). ⛔ A consumer must not read `adr-propose:skipped` as "this
    plan wrote no ADR" — it is exactly the wrong inference for the collision class below.

## Routing and Merge Behavior

- **⛔ The ADR-0019 collision was caught, by the one check that could catch it.** This plan
  allocated 0019 against `origin/main` when 0018 was the highest there. PR #217 then landed its
  own 0019 **and** 0020 while this plan was in review. **The rebase was clean and the tests were
  green with both 0019 files present** — nothing structural sees it. Only the explicit pre-merge
  re-check did. Renumbered to **0021**; inbox message `-001` corrected in place. Second
  recurrence of the class (PLAN-01/PLAN-14 → 0016).
- **⛔ A review check was green without a review.** GitHub's CodeRabbit status check reported
  `SUCCESS` on a commit CodeRabbit had never reviewed. Gating a merge on that check alone is
  unsafe. → candidate-lesson `-004`, promoted.
- **A currency check credited the bot at a HEAD its own comment denied** — keyed on comment
  timestamp rather than reviewed commit. → folded onto lesson `2026-09-06-14-003`.
- **The scope-creep guard never ran**: `no_baseline_sha` on all 20 tasks, because
  `references.json` carries no `plan_creation_sha`. Scope was verified by hand. An absent
  measurement, not a clean one — third observation.
- **An executing envelope edited `doc/forwarded-header-resolution.adoc`** despite the spec's
  explicit prohibition. The edit was reverted and the file is untouched by the merged PR
  (confirmed: absent from `git show --stat 6e1a19c`). → candidate-lesson `-002`, promoted.
- CI green; merged through the platform merge queue; branch deleted; worktree removed.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#214`; `landing` `landings/PLAN-06.md`;
      `plan_marshall_plan_id` `plan-06-forwarded-trust-model`
- [x] epic.md reconciled; START-HERE and Ordered Queue regenerated
- [x] PLAN-06 `## Expected Surface` corrected to the realized 12-file set
- [x] **Open Defect retired** — the live ADR-0019 collision; resolved by this plan at 0021
- [x] **Open Defect escalated** — `doc/adr/README.md` now has FOUR divergences, not one
- [x] **Open Defect opened** — `landing-facts` reports finalize figures as plan totals
- [x] **Open Defect folded** — scope-creep guard inert (third observation)
- [x] Six candidate-lessons dispositioned: three promoted, three folded
- [x] resume_anchor updated

## Follow-Ups

- ⛔ **`doc/adr/README.md` has four divergences and is now the epic's most-recurrent defect.**
  21 `.adoc` files; the index carries 18 rows, still asserts "the highest allocated number is
  **18**", is missing rows for **0019, 0020 and 0021**, and its ADR-0002 row reads `Proposed`
  while the file reads `Superseded`. Four consecutive waves. → **PLAN-13 deliverable 4b**,
  whose mechanism-proposal remit now has a concrete design to evaluate: candidate-lesson
  `-003` argues the collision is structurally invisible to git because the contested resource
  is a number, not a path.
- **PLAN-07 is unblocked** — PLAN-06 was its strict predecessor. ⛔ But PLAN-06 already edited
  `RfcForwardedParser.java` and `RfcForwardedParserTest.java`, two files PLAN-07 declares.
  PLAN-07 must re-read both at outline rather than working from its staged premises.
- **PLAN-13 gains three more obligations** beyond the five stale passages already folded as
  deliverable 4d: the document now also predates the `deFactoPrecedence` knob, the peer-address
  `resolve` overload, and the structural port-statement rule — and it must describe the
  deliberate asymmetry between the two reconciliation rules as a decision.
- **`adr-propose:skipped` is not evidence that no ADR shipped.** Any future ADR-collision check
  must read `doc/adr/` rather than the step outcome.

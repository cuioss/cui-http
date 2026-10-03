# Landing Analysis: PLAN-19 — ADR Number Deduplication

epic: quality-report-remediation
workstream: WS-05
pr: 184 (squash-merged via queue as `c631c77`)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth
> before it was recorded.

## Corroboration Summary

`inbox landing-check` on `adr-number-deduplication-004.md`: **`complete: true`, `missing_keys[0]`**.
Third complete block in four landings.

| Claim | Verdict | Evidence |
|---|---|---|
| PR #184 merged as `c631c77` | **corroborated** | `git log origin/main` head |
| Fourteen ADRs, numbers 1–14, no repeats | **corroborated** | `ls doc/adr/` — `0001`…`0014`, each number once |
| ⛔ **The keep-the-earlier rule was applied correctly, and my own reading of it was wrong** | **corroborated after re-checking** | See below |
| One internal cross-reference existed and was repaired | **corroborated** | `0009-Attack-database…adoc`:61 now cites **ADR-0011**, the renumbered target |
| No dangling references to the four old paths | **corroborated** | grep over `doc/`, `README.adoc`, `CLAUDE.md` for the old filenames returns nothing |
| `README.md` records the constraint | **corroborated** | names the missing allocator, the four collisions as evidence, and why the disjointness gate is blind — and scopes the `manage-adr` claim to a **dated observation** rather than asserting behaviour this repo cannot enforce |
| ⛔ **All fourteen ADRs remain `Proposed`** | **corroborated — and it is broader than the landing says** | every one of the 14 files carries `Proposed`; see Follow-Ups |
| `README.adoc` was infeasible | accepted as reported | the `manage-adr` glob behaviour is external to this repo and was not independently re-run |

### The renumbering ordering — a check I nearly got wrong

The spec required keeping the **earlier-landed** file of each pair at its original number. At first
read this looked inverted for the 0004/0005 pair: PLAN-02's ADRs were moved to 0011/0012 while
PLAN-15's kept 0004/0005, and PLAN-02 is **PR #159** against PLAN-15's **PR #161**.

`git log --reverse` settles it: `94004bc` (#161) is an **older commit** than `d042750` (#159). PR
numbers are allocation order, not merge order, and #161 merged first. **PLAN-15's ADRs are the
earlier-landed pair, so keeping them at 0004/0005 is correct.**

⚠️ This is exactly what the spec's verify-first clause demanded — *"re-derive which file in each
pair landed first from git rather than from this spec's prose"* — and the plan did it. Had it
reasoned from PR numbers, as the obvious shortcut invites, it would have renumbered the wrong four.

| Number | Kept / moved | Origin |
|---|---|---|
| 0004, 0005 | kept | PLAN-15, PR #161 (`94004bc`) — earlier |
| 0011, 0012 | moved from 0004/0005 | PLAN-02, PR #159 (`d042750`) |
| 0009, 0010 | kept | PLAN-13, PR #178 — earlier |
| 0013, 0014 | moved from 0009/0010 | PLAN-04, PR #180 |

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Renumber one file per colliding pair to 0011–0014 | shipped-as-specified | four files renamed, headings updated, ordering derived from git |
| 2. Fix every cross-reference | shipped-as-specified, **and the sweep found what the spec asked it to look for** | one internal ref repaired; external absence confirmed over 564 inventoried files plus `.github/**`, `.claude/**` and the ignored set |
| 3. Record the allocation constraint | shipped-**re-scoped, correctly** | `README.md` not `README.adoc` — see below |
| 4. Confirm ADR statuses | shipped as a **verified no-op** | all 14 `Proposed`; no ruling was given, so none was invented |

**Deliverable 3's re-scope is the right call and the right process.** The spec's path was
infeasible: `manage-adr list`/`scan` glob `*.adoc` with no numeric-prefix filter, so a `README.adoc`
enumerates as a bogus fifteenth ADR numbered `0` and breaks the count criterion. ⛔ **The executor
stopped and escalated rather than substituting a path on its own** — the spec named `.adoc`, and
silently shipping `.md` would have been an unrecorded scope change. The operator chose `.md`.

**The spec's cross-reference claim was labelled a HYPOTHESIS and was partly falsified**, which is the
verify-first contract working as designed: the external absence held, but one internal reference
existed. A spec that had asserted the absence would have shipped a broken xref.

## Surface: realized vs declared

Declared: `doc/adr/`, `README.adoc`, `CLAUDE.md` (the latter two as HYPOTHESIS, gated on the sweep).
Realized: `doc/adr/` only — the sweep found nothing in `README.adoc` or `CLAUDE.md`, so the two
conditional entries correctly resolved to no edit. **This is the first landing in the epic whose
realized surface did not exceed its declaration.** The declaration was over-broad by exactly the two
entries it labelled conditional, which is the conservative direction and cost nothing: no live plan
declared either file.

**No collision** with the two plans running concurrently (`semicolon-dot-segment-traversal`,
`validated-redirect-following`) — neither touches `doc/adr/`.

## Metrics and Anomalies

- 3,049,695 tokens / 3h54m wall — **by far the cheapest plan of the epic** (PLAN-04 was 4.6M/21h15m,
  PLAN-10 4.06M). A doc-only plan with a tight declared surface.
- 16/16 finalize steps done; 6,671 tests; Sonar 0 new-code issues (`count_status=confirmed`);
  CodeRabbit's 4 comments all triaged and answered.
- **CodeRabbit's review was substantive and correctly acted on**: it flagged that the README asserted
  `manage-adr` behaviour this repo cannot enforce, and that the hand-maintained index duplicates the
  `.adoc` files with no drift check. Commit `abd5722` scoped the first to a dated observation and made
  the second explicit — a rescope rather than a rebuttal.
- **No rate-limit stall**, unlike PLAN-04 and PLAN-10. Three plans ran concurrently against one
  account-scoped quota and this one was unaffected.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` = `184`; `landing` = `landings/PLAN-19.md`
- [x] ⛔ **Duplicate-ADR defect CLOSED** — all four collisions resolved on main, cross-references
      repaired, constraint recorded. The **cui-http half** is done.
- [x] **A NEW defect replaces its upstream half** — the allocator and `adr-propose` misdeclaration
      remain unreachable, and the concurrency hazard is now known not to have been prevented.
- [x] **ADR-acceptance item RE-SCOPED and WIDENED** — see Follow-Ups.
- [x] resume_anchor updated; both generated blocks regenerated

## Follow-Ups

1. ⛔ **The ADR-acceptance item is bigger than the ledger recorded, and my own count was wrong.**
   The epic tracked "four ADRs awaiting acceptance". **All fourteen are `Proposed`** — no ADR in this
   repository has ever been accepted. The landing names ADR-0012 and ADR-0013 as the security
   positions; the epic ledger had been tracking PLAN-04's pair, which renumbered to **0013 and
   0014**. Both readings are defensible (0012, 0013 and 0014 all record security positions), but they
   are different sets, and the renumbering is what made them diverge. **Recorded as: at least three
   ADRs record security positions — 0012, 0013, 0014 — and the whole 14-record corpus is unaccepted.**
2. ⛔ **The concurrency hazard did not fire, and was not prevented — luck plus one deliberate
   choice.** Neither concurrent plan proposed an ADR, and this plan's own `standard` execution posture
   excludes `adr-propose`, so it could not allocate from the sequence it was deduplicating. **A plan
   running the `full` posture alongside another would reproduce the original collision**, against a
   `doc/adr/` that is now clean. The recorded constraint in `README.md` is documentation, not
   enforcement.
3. **The upstream half is untouched and now the only remaining piece**: no ADR-number allocator, and
   `default:adr-propose` still declares no `mutates_source` (lesson `2026-08-27-12-004`, two
   recurrences, no fix). Both live in the plan-marshall marketplace and are out of reach from this
   repository. Needs cross-repo routing, not a cui-http plan.
4. **Any future non-ADR document inside `doc/adr/` must not be `.adoc`** — the glob constraint that
   forced deliverable 3's re-scope applies to every successor.
5. **Three candidate lessons** dispositioned individually — see the epic decision log. All three
   concern argparse-rejection recurrence and target plan-marshall-marketplace surfaces.

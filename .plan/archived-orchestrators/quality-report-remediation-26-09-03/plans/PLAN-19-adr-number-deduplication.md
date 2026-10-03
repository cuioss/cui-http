# PLAN-19: ADR Number Deduplication

epic: quality-report-remediation
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no brief.

## Objective

Resolve the four duplicate ADR numbers standing on `main` and record the constraint that produced
them, so the ADR record is navigable and the next concurrent wave has something to read. `doc/adr/`
currently holds **two 0004s, two 0005s, two 0009s and two 0010s** — eight files across four numbers,
each pair authored by two plans that ran concurrently and allocated from the same unguarded sequence.

⛔ **Scope discipline.** This plan is the **cui-http half** of a two-part problem. The other half —
an allocator that prevents recurrence, and the `default:adr-propose` `mutates_source` misdeclaration
— lives in the plan-marshall marketplace, is not reachable from this repository, and is explicitly
**out of scope**. Deliverable 3 records the constraint; it does not fix it.

## Deliverables

1. **Renumber one file from each colliding pair** to the next free numbers, keeping the earlier-landed
   file at its original number in every pair. At HEAD the highest allocated number is 0010, so the
   four renamed files take **0011–0014**. Preserve each file's `= ADR-NNNN: {title}` heading so the
   heading and the filename agree — a renamed file whose heading still says `ADR-0009` reproduces the
   ambiguity inside the document.
2. **Fix every cross-reference to a renumbered ADR.** ⛔ **Sweep before assuming there are none:** at
   the time of staging, a grep found the four `0009`/`0010` files referenced **only by themselves**,
   but ADRs in this repo do cross-link (`xref:0001-….adoc[ADR-0001: …]` in ADR-0002 and ADR-0003), so
   the pattern exists and the 0004/0005 pairs were not swept. Sweep `doc/`, `README.adoc`,
   `CLAUDE.md` and the Java sources.
3. **Record the allocation constraint** where the next ADR author will read it — that ADR numbers are
   a shared sequential resource with no allocator, that two concurrent plans will collide, and that
   the surface-disjointness gate is **structurally blind** to it because a number is not a file path.
   Name the observed collisions as evidence.
4. **Decide the four `Proposed` ADRs' status** if, and only if, the operator has ruled on them by
   execution time. ⛔ **Otherwise leave every one at `Proposed` and say so in the landing** — two of
   them (PLAN-04's) record previously-undocumented *security positions*, so accepting them is a
   security decision, not bookkeeping, and it is not this plan's to make.

Four deliverables — under the split guard.

## Claim Labels

- OBSERVED: Eight files across four duplicated numbers exist at HEAD in `doc/adr/`:
  `0004-CharacterValidationStage_validates_the_wire_form…` and `0004-Documentation_inventories_point_at_package-level_source_trees…`;
  `0005-A_security_preset_must_never_set_caseSensitiveComparison_to_true` and `0005-Documentation_cross-references_use_named_anchors…`;
  `0009-Attack-database_entries_verified_structurally…` and `0009-HttpSecurityValidators_when_and_identity…`;
  `0010-NFKC-fold_claims_centralized…` and `0010-NormalizationStage_clamps_root-consumed_dot-segments…`.
- OBSERVED: The 0004/0005 pair came from PLAN-02 and PLAN-15 (recorded 2026-08-27); the 0009/0010
  pair from PLAN-13 (PR #178) and PLAN-04 (PR #180), landing one day apart on 2026-08-31 / 2026-09-01.
- OBSERVED: 0010 is the highest number allocated, so 0011–0014 are free.
- OBSERVED: ADRs in this repo cross-link by `xref:{filename}[ADR-NNNN: {title}]` — both the filename
  and the number appear, so a rename breaks the link and a number change breaks the label. Read at
  `0002-…adoc`:99–100 and `0003-…adoc`:89–90.
- HYPOTHESIS: No file outside `doc/adr/` references any of the eight by number — a grep over `doc/`
  and the Java sources at staging time returned only the ADR files themselves. ⛔ **This is an
  asserted ABSENCE and is labelled as one**: verify it rather than inherit it, and note the grep did
  not separately sweep `README.adoc` or `CLAUDE.md` (verify-at-outline).
- Verify-first clause: **Confirm all eight files and the four collisions still stand at HEAD before
  renumbering**, and re-derive which file in each pair landed first from git rather than from this
  spec's prose. The keep-the-earlier rule is only meaningful if the ordering is read from history.

## Expected Surface

- OBSERVED: `doc/adr/` — the eight colliding files; four are renamed and their headings updated
- HYPOTHESIS: `README.adoc` — only if the deliverable-2 sweep finds an ADR reference (verify-at-outline)
- HYPOTHESIS: `CLAUDE.md` — same condition (verify-at-outline)
- ⛔ **No, not this plan's surface:** `cui-http-core/src/main/java/` — no Java change is in scope. If the
  sweep finds an ADR number in a Javadoc comment, correcting that one reference is in scope; anything
  further is not.

## Dependencies and Sequencing

- Depends on: none. PLAN-04 and PLAN-13, which produced the 0009/0010 collision, have both shipped.
- Overlaps with: none staged. PLAN-17 is `client/` only and PLAN-18 is `security/` only, so all three
  are pairwise disjoint and may run concurrently.
- ⛔ **Adjacent — and this is the sequencing hazard that matters:** `default:adr-propose` writes tracked
  `.adoc` files while declaring no `mutates_source`, so its output escapes the finalize commit
  instrumentation and is destroyed with the worktree unless committed by hand. This is lesson
  `2026-08-27-12-004`, which has now recurred twice (ADRs 0004/0005/0006, then 0009/0010) and produced
  no fix. **If this plan's own finalize proposes an ADR, that ADR is at risk** — check the worktree for
  uncommitted `.adoc` files before `branch-cleanup` runs.
- ⛔ **Adjacent:** if any plan runs concurrently with this one and proposes an ADR, it will allocate
  from the same unguarded sequence and may collide with the 0011–0014 range this plan claims. Neither
  plan can detect it — the gate compares file paths, and the collision is on a number. **Prefer to run
  this plan when no other plan's finalize is expected to propose an ADR.**

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-19-adr-number-deduplication.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

# Architecture Decision Records

This directory holds the architecture decision records for cui-http. Each ADR is one file named
`NNNN-<title-slug>.adoc`, where `NNNN` is the record's number and the slug is derived from its title.
The number appears twice — in the filename and in the level-0 heading inside the file — and the two
must agree.

This index is Markdown rather than AsciiDoc on purpose. The `manage-adr` tooling is external to this
repository, so its enumeration behaviour is neither configured nor enforced here. As observed on
2026-09-01, `manage-adr list` and `manage-adr scan` enumerated `doc/adr/*.adoc` without filtering on the
numeric prefix, which reported a `README.adoc` placed here as an extra record numbered `0` and titled
`Unknown`. Keeping this file as `.md` sidesteps that enumeration entirely rather than relying on a
filter this repository cannot guarantee.

## Index

| # | Decision | Status |
|---|----------|--------|
| 1 | [Nearest-hop token selection is authoritative for forwarded header chains](0001-Nearest-hop_token_selection_is_authoritative_for_forwarded_header_chains.adoc) | Accepted |
| 2 | [Fail closed when X-Forwarded-For and RFC 7239 Forwarded disagree](0002-Fail_closed_when_X-Forwarded-For_and_RFC_7239_Forwarded_disagree.adoc) | Superseded by 21 |
| 3 | [Header accessor contract must expose every instance of a repeated header](0003-Header_accessor_contract_must_expose_every_instance_of_a_repeated_header.adoc) | Accepted |
| 4 | [Documentation inventories point at package-level source trees, not per-class enumerations](0004-Documentation_inventories_point_at_package-level_source_trees_not_per-class_enumerations.adoc) | Accepted |
| 5 | [Documentation cross-references use named anchors, not positional prose](0005-Documentation_cross-references_use_named_anchors_not_positional_prose.adoc) | Proposed |
| 6 | [PatternMatchingStage does not observe non-failing suspicious-pattern matches](0006-PatternMatchingStage_does_not_observe_non-failing_suspicious-pattern_matches.adoc) | Accepted |
| 7 | [TLS hostname-verification relaxation via a delegating trust manager, not a JVM-wide lever](0007-TLS_hostname-verification_relaxation_via_a_delegating_trust_manager_not_a_JVM-wide_lever.adoc) | Accepted |
| 8 | [Extend a Maven-Central-published provider surface additively, even under a plan-level breaking-compatibility setting](0008-Extend_a_Maven-Central-published_provider_surface_additively_even_under_a_plan-level_breaking-compatibility_setting.adoc) | Accepted |
| 9 | [Attack-database entries verified structurally, independent of pipeline short-circuit](0009-Attack-database_entries_verified_structurally_independent_of_pipeline_short-circuit.adoc) | Accepted |
| 10 | [NFKC-fold claims centralized in one executable invariant registry](0010-NFKC-fold_claims_centralized_in_one_executable_invariant_registry.adoc) | Accepted |
| 11 | [CharacterValidationStage validates the wire form; DecodingStage owns decoded-character safety](0011-CharacterValidationStage_validates_the_wire_form_DecodingStage_owns_decoded-character_safety.adoc) | Accepted |
| 12 | [A security preset must never set caseSensitiveComparison to true](0012-A_security_preset_must_never_set_caseSensitiveComparison_to_true.adoc) | Accepted |
| 13 | [HttpSecurityValidator's when() and identity() composition primitives are deliberately fail-open](0013-HttpSecurityValidators_when_and_identity_composition_primitives_are_deliberately_fail-open.adoc) | Accepted |
| 14 | [NormalizationStage clamps root-consumed dot-segments and skips rewriting scheme-bearing input](0014-NormalizationStage_clamps_root-consumed_dot-segments_and_skips_rewriting_scheme-bearing_input.adoc) | Superseded in part by 16 |
| 15 | [A pattern literal enforced by a named preset must be decidable from the HTTP component alone](0015-A_pattern_literal_enforced_by_a_named_preset_must_be_decidable_from_the_HTTP_component_alone.adoc) | Accepted |
| 16 | [Absolute-path '..' walking is rejected rather than clamped at root](0016-Absolute-path_dot-dot_walking_is_rejected_rather_than_clamped_at_root.adoc) | Accepted |
| 17 | [DecodingStage security gates must not create a raw-versus-encoded verdict asymmetry](0017-DecodingStage_security_gates_must_not_create_a_raw-versus-encoded_verdict_asymmetry.adoc) | Accepted |
| 18 | [CI harden-runner egress allowlists enumerate hosts; never wildcard, never runtime-derived](0018-CI_harden-runner_egress_allowlists_enumerate_hosts_never_wildcard_never_runtime-derived.adoc) | Proposed |
| 19 | [Cookie character sets are split by RFC role with no DQUOTE quote-pair carve-out](0019-Cookie_character_sets_are_split_by_RFC_role_with_no_DQUOTE_quote-pair_carve-out.adoc) | Accepted |
| 20 | [Header and cookie character gates ignore preset flags when no later stage can re-check them](0020-Header_and_cookie_character_gates_ignore_preset_flags_when_no_later_stage_can_re-check_them.adoc) | Accepted |
| 21 | [Unresolvable Forwarded header suppresses only the fields it carried](0021-Unresolvable_Forwarded_header_suppresses_only_the_fields_it_carried.adoc) | Accepted |
| 22 | [A missing Content-Type is rejected when a non-empty allow-list is configured](0022-A_missing_Content-Type_is_rejected_when_a_non-empty_allow-list_is_configured.adoc) | Accepted |
| 23 | [A cache entry is bound to the credential material that produced it](0023-A_cache_entry_is_bound_to_the_credential_material_that_produced_it.adoc) | Accepted |

The highest allocated number is **23**. This table is maintained by hand and is not derived from
`doc/adr/` at build time, so a rename, addition, or status change elsewhere can leave it stale;
treat the `.adoc` files as authoritative and update this table in the same change. The
[proposal below](#proposal-check-the-index-against-the-records-at-the-merge-gate) describes how that
obligation could be enforced instead of remembered.

## Allocating a number

ADR numbers are a shared sequential resource with no allocator. Nothing hands a number out, and nothing
records that one has been claimed until the file carrying it is merged. The only way to pick a number is
to read this directory and take one past the highest you see — which makes your choice a function of the
commit you happen to be standing on.

Two authors working concurrently therefore read the same highest number and each claim `highest + 1`.
Both are correct at their own branch point; both are wrong once the other lands. Neither can detect the
collision, because at the moment of allocation the other file does not exist on any branch either author
can see.

### The disjointness gate cannot see this

It is worth being precise about why no existing check catches it. The plan-level surface-disjointness
gate compares **file paths**, and the contested resource here is a **number**. Two plans that each write
`0011-{their-own-title}.adoc` occupy entirely disjoint paths — no path overlaps, so the gate reports the
two plans as safely independent — while both claim number 0011. The gate is not failing; it is
structurally blind to this class, because path disjointness and number disjointness are different
properties and only the first is being compared.

### Observed evidence

This is not hypothetical. It has happened three times, in three independent waves, producing six
colliding records:

- **0004 and 0005** collided on 2026-08-27 — commit `94004bc` (PR #161) against `d042750` (PR #159).
- **0009 and 0010** collided across 2026-08-31 and 2026-09-01 — commit `30edfa3` (PR #178) against
  `5aba533` (PR #180).
- **0016** collided across 2026-09-05 and 2026-09-06 — commit `7a0da52` (PR #210, the path-validation
  decision) against `73afd22` (PR #208, the CI egress decision). This wave is the sharpest illustration
  of the blindness described above: the two plans touched entirely different subsystems — one edits
  `cui-http-core/src/main`, the other edits `.github/workflows` — and neither declared the other's
  files, so nothing in either plan's surface suggested they could contend. The number was the only
  shared resource. It was resolved on 2026-09-07: `7a0da52`'s record kept `0016` (it landed first, and
  every in-repo `ADR-0016` reference already pointed at it), and `73afd22`'s moved to `0018`.

A third failure mode compounds each wave: the index above is maintained by hand, so the later-landed
plan can also fail to add its row. In the 0016 wave PR #210 updated this table and PR #208 did not,
leaving the index listing one `0016` and silent about the other for two days. The same drift later
accumulated without any collision at all: by the time this index was rebuilt it listed 18 rows
against 23 record files, and one row (0002) still read `Proposed` while its file read `Superseded`.

Every wave was resolved the same way: the earlier-landed file of each pair kept its original number,
and the later-landed file moved to a free number at the end of the sequence. Because each displaced
record took the next free number rather than leaving a gap, the numbering stays contiguous — at this
writing the directory holds 23 records numbered 0001 to 0023, with no gap and no duplicate. The cost of
a move is in the history instead: a renumbered record's earlier commits sit under its old filename, so
`git log --follow` is the way to read that history across the rename.

### What to do before you allocate

- **Re-read `doc/adr/` at the tip of `main`, not at your branch point.** A number chosen from a branch
  point is a number chosen from a stale view of the directory, and that staleness is precisely what
  produced every wave above.
- **Re-check the number again at the merge gate, against `origin/main`.** A number that was free when
  the record was written can be taken by a plan that merges first. ADR-0022's plan repeated the check
  against `origin/main` at the merge gate rather than trusting the authoring-time read, and its number
  landed without a collision; no collision has yet been caught by this control.
- **Prefer not to propose an ADR while another plan's finalize is expected to propose one.** Two
  finalizes in flight at once is exactly the condition that makes two reads return the same highest
  number.

None of these steps is a guarantee. They narrow the window; none closes it, because closing it would
require an allocator this directory does not have. This document records the constraint so the next
author meets it deliberately rather than discovering it after a merge. The proposal below describes a
check that would turn the merge-gate re-check from a habit into an enforced gate.

## Proposal: check the index against the records at the merge gate

*Status: proposal only. Nothing described in this section is implemented.*

Two of the failure modes recorded above — a duplicate number, and an index row that is missing or
disagrees with its file — are decidable from the repository alone. Neither needs judgement, so both
can be checked mechanically rather than left to the author's memory.

### Options

- **Generate the index.** A script derives the Index table from `doc/adr/*.adoc` (number, title,
  status, and the successor named in a superseded record's Status section) and writes it into this
  file. The table can then never disagree with the records, but every record change needs the
  generator to run, and a forgotten run still leaves the committed table stale.
- **Check the index (preferred).** A script reads the same three fields from every
  `doc/adr/*.adoc` file and from the Index table, and fails when they disagree. The table stays
  hand-written and reviewable in a diff; the check only refuses a change that leaves it inconsistent.
  This is the smaller change, and it catches the stale-generator case too.

### What the check verifies

1. Every `doc/adr/NNNN-*.adoc` number is unique, and the number in the filename equals the number in
   the level-0 heading.
2. The Index table has exactly one row per record file, and no row without a file.
3. Each row's title equals the record's title, and each row's Status cell equals the record's Status
   section — including the successor number for a superseded record and the "in part" qualifier
   where the record states one.
4. The high-water sentence names the highest record number.

### Where it runs

The check must run against the **merged** view, not the branch alone, because a collision only exists
once both records are present. It therefore belongs at the merge gate: the branch is evaluated as
merged onto the current `origin/main`. Two places fit:

- **A CI step** in the pull-request workflow (`.github/workflows/maven.yml`), running on
  `pull_request` and `merge_group` events. With a merge queue enabled, the `merge_group` run sees the
  branch merged onto the queue's base, which is exactly the view in which two plans' numbers collide.
  This is the preferred location, because it cannot be skipped locally.
- **A pre-commit verification** in the plan-marshall finalize flow, run after the branch has been
  synced with `origin/main`. This gives earlier feedback, but on its own it still evaluates a snapshot
  that another merge can overtake, so it complements the CI step rather than replacing it.

A failing check names the duplicate number, the missing row, or the disagreeing field, so the fix is a
one-line edit to this table or a renumbering of the later record.

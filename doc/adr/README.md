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
| 1 | [Nearest-hop token selection is authoritative for forwarded header chains](0001-Nearest-hop_token_selection_is_authoritative_for_forwarded_header_chains.adoc) | Proposed |
| 2 | [Fail closed when X-Forwarded-For and RFC 7239 Forwarded disagree](0002-Fail_closed_when_X-Forwarded-For_and_RFC_7239_Forwarded_disagree.adoc) | Proposed |
| 3 | [Header accessor contract must expose every instance of a repeated header](0003-Header_accessor_contract_must_expose_every_instance_of_a_repeated_header.adoc) | Proposed |
| 4 | [Documentation inventories point at package-level source trees, not per-class enumerations](0004-Documentation_inventories_point_at_package-level_source_trees_not_per-class_enumerations.adoc) | Proposed |
| 5 | [Documentation cross-references use named anchors, not positional prose](0005-Documentation_cross-references_use_named_anchors_not_positional_prose.adoc) | Proposed |
| 6 | [PatternMatchingStage does not observe non-failing suspicious-pattern matches](0006-PatternMatchingStage_does_not_observe_non-failing_suspicious-pattern_matches.adoc) | Proposed |
| 7 | [TLS hostname-verification relaxation via a delegating trust manager, not a JVM-wide lever](0007-TLS_hostname-verification_relaxation_via_a_delegating_trust_manager_not_a_JVM-wide_lever.adoc) | Proposed |
| 8 | [Extend a Maven-Central-published provider surface additively, even under a plan-level breaking-compatibility setting](0008-Extend_a_Maven-Central-published_provider_surface_additively_even_under_a_plan-level_breaking-compatibility_setting.adoc) | Proposed |
| 9 | [Attack-database entries verified structurally, independent of pipeline short-circuit](0009-Attack-database_entries_verified_structurally_independent_of_pipeline_short-circuit.adoc) | Proposed |
| 10 | [NFKC-fold claims centralized in one executable invariant registry](0010-NFKC-fold_claims_centralized_in_one_executable_invariant_registry.adoc) | Proposed |
| 11 | [CharacterValidationStage validates the wire form; DecodingStage owns decoded-character safety](0011-CharacterValidationStage_validates_the_wire_form_DecodingStage_owns_decoded-character_safety.adoc) | Proposed |
| 12 | [A security preset must never set caseSensitiveComparison to true](0012-A_security_preset_must_never_set_caseSensitiveComparison_to_true.adoc) | Proposed |
| 13 | [HttpSecurityValidator's when() and identity() composition primitives are deliberately fail-open](0013-HttpSecurityValidators_when_and_identity_composition_primitives_are_deliberately_fail-open.adoc) | Proposed |
| 14 | [NormalizationStage clamps root-consumed dot-segments and skips rewriting scheme-bearing input](0014-NormalizationStage_clamps_root-consumed_dot-segments_and_skips_rewriting_scheme-bearing_input.adoc) | Proposed |

The highest allocated number is **14**. This table is maintained by hand and is not derived from
`doc/adr/` at build time, so a rename, addition, or status change elsewhere can leave it stale;
treat the `.adoc` files as authoritative and update this table in the same change.

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

This is not hypothetical. It has happened twice, in two independent waves, producing four colliding
records:

- **0004 and 0005** collided on 2026-08-27 — commit `94004bc` (PR #161) against `d042750` (PR #159).
- **0009 and 0010** collided across 2026-08-31 and 2026-09-01 — commit `30edfa3` (PR #178) against
  `5aba533` (PR #180).

Both waves were resolved the same way: the earlier-landed file of each pair kept its original number,
and the later-landed file moved to a free number at the end of the sequence. That is why the index above
runs to 14 while the decisions it records were authored as far lower numbers, and why `git log --follow`
is the way to read their history across the rename.

### What to do before you allocate

- **Re-read `doc/adr/` at the tip of `main`, not at your branch point.** A number chosen from a branch
  point is a number chosen from a stale view of the directory, and that staleness is precisely what
  produced both waves above.
- **Prefer not to propose an ADR while another plan's finalize is expected to propose one.** Two
  finalizes in flight at once is exactly the condition that makes two reads return the same highest
  number.

Neither step is a guarantee. Both narrow the window; neither closes it, because closing it would require
an allocator this directory does not have. This document records the constraint so the next author meets
it deliberately rather than discovering it after a merge — it does not fix it, and it deliberately does
not propose a fix.

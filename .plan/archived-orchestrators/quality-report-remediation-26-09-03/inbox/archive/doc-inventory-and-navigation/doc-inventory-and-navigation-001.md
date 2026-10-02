envelope_version=1
sender_type=plan
sender_id=doc-inventory-and-navigation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T07:28:17Z

# Candidate lesson: the artifact written to codify overclaim-removal itself overclaimed

## Observation

This plan (PLAN-15, doc-inventory-and-navigation) existed to remove documentation
overclaims. During finalize, two ADRs were authored to CODIFY that removal:

- `doc/adr/0004-*.adoc`
- `doc/adr/0005-Documentation_cross-references_use_named_anchors_not_positional_prose.adoc`

Each ADR asserted, as a consequence, that link validation ALREADY RUNS in this
project's build. That is false, and verifiably so at authoring time:

- `pom.xml` configures no AsciiDoc processing at all.
- `.github/workflows/maven.yml` delegates to a reusable workflow that SKIPS
  documentation-only changes, so no doc gate runs on a docs-only PR.

CodeRabbit caught it on PR #161 (finding `945b2b`, threads 3869376618 /
3869376625). Corrected in `882cc7b`: both ADRs now claim only the property that
holds — an anchor makes a broken reference DECIDABLE BY A CHECKER, whereas
positional prose is not decidable at all — and ADR-0005 names "add a checker" as
the follow-on this decision ENABLES rather than a check it relies on.

## Why this is epic-level, not plan-level

This is the PLAN-14 paired-claim / overclaim hazard recurring ONE LEVEL UP: in
the artifact written specifically to prevent it. The plan's own gates were tuned
to catch overclaims in the documents under remediation; they did not look at the
new documents the remediation produced. A remediation artifact is not exempt from
the defect class it remediates, and nothing in the pipeline encoded that.

## Candidate rule

When a plan's purpose is to remove a defect class D from a document set S, every
NEW document the plan authors is part of S for gating purposes — including ADRs,
follow-up records, and PR bodies. Specifically for capability claims: an ADR
consequence stating "X is validated / checked / enforced" must be backed by a
named, resolvable mechanism (a plugin in the build file, a workflow step) or
restated as a property that holds independent of tooling.

## Evidence

- Finding `945b2b` (pr-comment, coderabbit, `doc/adr/0005-...adoc:36`, severity warning)
- Fix commit `882cc7b`
- Reviewed commit sha `a1c6395a9cbcf0b181db7aac7c7f93925f666d3c`

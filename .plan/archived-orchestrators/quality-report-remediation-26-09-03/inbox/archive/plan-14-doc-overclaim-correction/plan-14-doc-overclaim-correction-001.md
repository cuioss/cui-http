envelope_version=1
sender_type=plan
sender_id=plan-14-doc-overclaim-correction
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T19:25:39Z

component=pm-documents:ref-asciidoc
category=anti-pattern
bundle=pm-documents

# Correcting one side of a paired doc claim leaves the sibling side contradicting it

A documentation claim about "where X is implemented" is almost never stored in one
place. It is stored as a PAIR: a prose sentence in the body (`Implemented in: ...`,
`This document covers ...`) and a structured row elsewhere (a traceability table, a
requirements matrix, an index entry) that points at the same subject. The two are
written at different times by different edits and nothing mechanically binds them.

Editing exactly one half is therefore the default failure mode of any accuracy
campaign, and it is self-inflicted: a plan whose whole purpose is to remove
overclaims will introduce a fresh self-contradiction if it rewrites the body and
leaves the row, or narrows the row and leaves the body.

Two instances in a single run of plan-14-doc-overclaim-correction:

- Q-Gate finding `4b65f8` (6-finalize): the section-level `Implemented in:` line
  under-attributed parameter-name validation. The row-level attributions had been
  corrected; the section-level line above them had not, so the section header
  claimed a narrower implementation set than the rows beneath it.
- CodeRabbit PR finding `cbe21d` (pr-comment, fixed in-run): the SEC-3 traceability
  row still pointed at `cve-analysis.adoc` while the SEC-3 body — rewritten by this
  very plan — now stated that document covers a disjoint CVE set. The plan
  introduced exactly the class of self-contradiction it existed to remove.

Both were caught, but by a downstream reviewer rather than by the edit itself, and
`cbe21d` was caught only by an external review bot after the PR was open.

## Solution

Treat a claim edit as an edit to a claim SET, not to a line.

Before marking a documentation deliverable done, for every claim touched:

1. Search the repo for the subject of the claim (the requirement id, the file name,
   the class name) — not just the file being edited. `architecture search --content
   --pattern <subject>` returns the full attributed set.
2. For each other hit, decide explicitly: does it still hold under the corrected
   claim? Record the answer. "I did not look" and "I looked and it still holds" must
   not be indistinguishable at review time.
3. Pay particular attention to the two directions that are easy to miss:
   section-level summary lines that sit ABOVE the rows being corrected, and
   traceability/index rows that live in a DIFFERENT file from the prose.

Concretely, when a plan narrows what a document claims to cover, every pointer INTO
that document is a candidate stale reference and must be re-read against the new
scope.

## Impact

Applies to any documentation-correction, traceability, or requirements-mapping work
where prose and a structured index both assert the same fact. Highest risk on
accuracy/overclaim-correction plans specifically, because there the corrected claim
is narrower than the old one, so every surviving pointer silently overclaims.

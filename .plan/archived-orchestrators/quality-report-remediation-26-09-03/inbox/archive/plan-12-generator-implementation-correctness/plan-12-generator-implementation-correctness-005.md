envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:36:43Z

# Candidate lesson: deleted-symbol residue survives because the deliverable scoped doc edits by LOCATION, not by symbol

## What happened

Deliverable 1 deleted the private `AttackTypeSelector` inner class. A content sweep for the symbol
returned exactly two files, both already declared affected — so the *file-level* consumer set was
closed and the footprint looked complete.

The residue was inside a declared file. `injection/URLLengthLimitAttackGeneratorTest.java` lines
~63-66 carry a field Javadoc on `ATTACK_FAMILY_COUNT`: "The number of attack families
`{@code AttackTypeSelector}` cycles through". Deliverable 1's *Change per file* directed updating
"the `@DisplayName` and the class Javadoc" — neither of which is this field Javadoc. So a
`{@code}` reference to a deleted class would have survived the deliverable, and the stated intent
("no longer claims the generator cycles") would have been only partly met.

## Root cause

The footprint check and the change instruction operate at different granularities. The sweep found
the symbol at *file* granularity and pronounced the consumer set closed; the instruction then
scoped the edit to two *named locations* inside that file. A file can be correctly declared and
still be incompletely changed — and nothing reconciles the two, because "file is in the footprint"
reads as coverage.

Note the sweep itself was correct and complete. The gap is downstream of it.

## Caught by

phase-3-outline Q-Gate, finding `91d9bf`, `consumer_residue`. Resolved by extending deliverable 1's
*Change per file* to name the `ATTACK_FAMILY_COUNT` field Javadoc explicitly and restate it in
terms of `hashBasedSelection`. No footprint change was needed.

## Candidate rule

When a deliverable deletes or renames a symbol, the change instruction must be scoped by the
SYMBOL (every occurrence in each declared file), not by a list of locations someone happened to
notice. If the instruction names specific locations, it must also state the per-file occurrence
count the sweep found, so an instruction covering fewer sites than the sweep found is visibly
short rather than plausibly complete.

## Provenance

- Plan: plan-12-generator-implementation-correctness
- Q-Gate finding: `91d9bf` (phase 3-outline), resolution `taken_into_account`
- File: `cui-http-core/src/test/java/de/cuioss/http/security/generators/injection/URLLengthLimitAttackGeneratorTest.java`

envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:35:24Z

# Candidate lesson: a Javadoc-asserted normalization property that no test verified

## What happened

This plan existed to remove a defect class from the security test generators: generator code
whose documented intent and actual behaviour disagree. The plan's own output reproduced that
exact defect class.

`PathTraversalGenerator.LOOKALIKE_FORWARD_SEPARATOR` was set to `U+2215 DIVISION SLASH`, with a
Javadoc claiming it "NFKC-normalizes to `/`". It does not — `U+2215` NFKC-normalizes to itself.
The generator therefore emitted a signature that was visually confusable but not a normalization
attack, while its doc asserted it was one.

Three gates passed it: phase-3-outline, phase-5-execute, and the phase-6 pre-submission
self-review. It was caught by CodeRabbit on PR #171.

## Root cause

The Javadoc asserted a *property of a specific Unicode codepoint* — a fact that is machine-checkable
in one line — and no test checked it. A reviewer reading the constant sees a plausible name
(`DIVISION SLASH`, which looks like a slash) next to a plausible claim, and has no cheap way to
falsify either without leaving the file. The claim and the check lived in different places, so
the claim could drift without anything failing.

## Remedy applied in this run

`PathTraversalGeneratorTest` gained:

```
@DisplayName("Every Unicode signature NFKC-folds to the ASCII traversal it claims to encode")
```

which runs `Normalizer.normalize(signature, Normalizer.Form.NFKC)` over every `UNICODE_SIGNATURES`
entry and asserts the fold equals the ASCII traversal the class Javadoc claims for it. The constant
itself was changed to `U+FF0F FULLWIDTH SOLIDUS`, which genuinely does fold to `/`.

`U+2044 FRACTION SLASH` was kept, but explicitly re-documented as a homoglyph that does NOT
normalize — used only in the variation-selector branch, whose technique does not depend on
normalization. That distinction is now stated rather than assumed.

## Residual defect still present on `main` after the merge

The fix corrected the constant and its own Javadoc, but the CLASS-level Javadoc was not updated.
`cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/PathTraversalGenerator.java`
lines 29-30 still read:

```
 * - Unicode lookalike variants (U+2024 ONE DOT LEADER, U+2215 DIVISION SLASH,
 *   U+FF3C FULLWIDTH REVERSE SOLIDUS), which NFKC-normalize to the ASCII traversal
```

That sentence still asserts the false property, and still names a codepoint the class no longer
uses. The new assertion does not cover it: the assertion iterates `UNICODE_SIGNATURES`, which is
data, whereas the stale claim is prose. So the defect class recurred one layer up, inside the very
fix that closed it, and survived the merge.

## Candidate rule

When a doc comment asserts a checkable property of a constant — a Unicode normalization form, an
encoding round-trip, a regex/parse equivalence — the assertion belongs in a test that reads the
constant, not in prose beside it. And when such a claim is corrected, sweep every doc surface in
the file for the same claim: the correction site is rarely the only one.

## Provenance

- Plan: plan-12-generator-implementation-correctness
- PR: cuioss/cui-http#171 (merged)
- Caught by: CodeRabbit inline review, fixed in ed4943f
- Residual verified against `main` at the time of this message

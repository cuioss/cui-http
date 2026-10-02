envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=landing
created=2026-08-29T19:41:55Z

# Landing: PLAN-12 Generator Implementation Correctness

plan_id: plan-12-generator-implementation-correctness
pr: https://github.com/cuioss/cui-http/pull/171
pr_number: 171
merged_at: 2026-08-29T19:27:53Z
merge_commit_base: main
outcome: merged
deliverables: 8/8
diff: 19 files, +687/-181

## Findings covered

| ID | Disposition |
|----|-------------|
| TQ-17 | delivered — `URLLengthLimitAttackGenerator` seed-invariant `AttackTypeSelector` counter replaced by `hashBasedSelection`; the counter had survived PLAN-11 despite TQ-17 being absorbed there |
| TQ-18 | delivered — `PathTraversalGenerator` doubled-backslash literals at THREE sites, one (`generateMixedEncodingTraversal` case 2, line ~204) never named in the source report |
| TQ-19 | delivered — `UnicodeAttackGenerator` no longer emits a bare invisible code point |
| TQ-20 | delivered — `AttackURLParameterGenerator` long-string branches now pad a real payload |
| TQ-21 | delivered — `UnicodeNormalizationAttackGenerator` pass-through and mislabeled branches closed; equals-guard added to `createDecomposedNormalizationAttack` |
| TQ-22 | delivered — whitespace cookie generators no longer emit the off-contract non-prefixed name |
| TQ-23 | delivered — `HttpResult{Success,Failure}Generator` `getType()` + honest Javadoc; `ValidCookieGenerator` full RFC 1123 `Expires` |

## Scope corrections found during execution

- TQ-23's premise was WRONG as written: the comma concern is unfounded (`AttributeParser` splits on `;` only). The real defect was a truncated RFC 1123 date, and the far-future arm additionally carried the wrong weekday (`Fri, 31 Dec 2999` — that date is a Tuesday).
- Three PLAN-11 contract tests actively ASSERTED the defects this plan removed, so each fix had to rewrite its pinning test. `GeneratorContractAssertions.TRAVERSAL_MARKERS` documented the doubled-backslash behaviour as intended.
- `injection/URLLengthLimitAttackGeneratorTest.java` is a distinct file from the excluded `tests/URLLengthLimitAttackTest.java` and was correctly in scope.

## Post-review corrections (CodeRabbit, PR #171)

- `PathTraversalGenerator.LOOKALIKE_FORWARD_SEPARATOR` was `U+2215` DIVISION SLASH with Javadoc claiming it NFKC-normalizes to `/`. It does not. The Unicode traversal therefore folded to `..<U+2215>`, never `../` — the plan reproduced its own target defect class. Fixed to `U+FF0F`, plus a new assertion proving each signature folds to the traversal its doc claims.
- CodeRabbit's stated CAUSE was wrong (it claimed the `U+2024` dot leaders also lack the decomposition); independent verification kept the fix to the slash alone.
- `UnicodeAttackGenerator.PATH_TARGETS` registered and derived by the test instead of mirrored.
- `URLLengthLimitAttackGenerator.ATTACK_FAMILY_COUNT` derived from a registered `ATTACK_FAMILIES` list instead of a hand-maintained literal.
- Sonar `java:S8786` — super-linear strip regex in `CookieChaosAttackTest` replaced by a linear scan.

## OPEN — residual defect still on main

`cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/PathTraversalGenerator.java` lines 29-30: the CLASS-level Javadoc still reads "U+2024 ONE DOT LEADER, U+2215 DIVISION SLASH, U+FF3C FULLWIDTH REVERSE SOLIDUS), which NFKC-normalize to the ASCII traversal".

Both halves are false after this plan: the class no longer uses `U+2215`, and `U+2215` never NFKC-normalized. The new assertion cannot catch it — the assertion iterates signature DATA, the stale claim is PROSE. The defect class recurred one layer up inside the fix that closed it and survived the merge. Needs a follow-up.

## Coverage gaps recorded, not hidden

- `pr-agent` reviewed `d736c77` cleanly but never re-reviewed `ed4943f` or `d2598bb` (`participated_stale`). CodeRabbit did review the interim state.
- Sourcery was rate-limited throughout (250k diff-char budget, ~19h reset) and contributed no review.

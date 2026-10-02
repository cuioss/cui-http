envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:36:24Z

# Candidate lesson: an outline exhaustivity claim ("the only method that…") that no sweep verified

## What happened

Solution outline deliverable 5 stated that `createMixedScriptAttack` "is the only substitution
method in the class lacking the `if (result.equals(pattern))` fallback guard its four siblings all
carry", and its success criterion was "No substitution method in the class can return its input
unchanged".

The claim was false. `createDecomposedNormalizationAttack` (`UnicodeNormalizationAttackGenerator`,
lines ~150-173) is a substitution method with the same per-char `switch` shape, has
`default -> result.append(c)`, and carries no equals-guard either. Applying the outline as written
would have satisfied its own success criterion only by accident.

## Why the defect was invisible

`createDecomposedNormalizationAttack` happens not to pass its input through today, because all five
`generateBasePattern` shapes contain one of `.`, `/`, `<`, `>`, `=`. That is a property of the
current base-pattern set, not a structural guarantee — precisely the fragility the success
criterion existed to remove. A behavioural spot-check would have shown green; only reading every
sibling method showed the gap.

(`createCompatibilityNormalizationAttack` is genuinely safe: its `default` converts every other
char to fullwidth `a`. So there was exactly one residue, not a family.)

## Caught by

phase-3-outline Q-Gate, finding `b42111`, `scope_criterion_validator` / `under_coverage`.
Resolved operator-directed by ADDING the guard to `createDecomposedNormalizationAttack` rather than
by narrowing the criterion, so the criterion now holds structurally rather than accidentally. The
outline's Root Cause text was corrected in place — it no longer claims `createMixedScriptAttack` is
the only unguarded site.

## Candidate rule

An outline claim of the form "X is the only N that …" is an exhaustivity claim over a population,
and it is only as good as the sweep that produced it. Either enumerate the population explicitly
in the outline (name all N siblings and their status), or do not make the claim — write the
criterion over the population instead of over the one instance you found.

Related: a success criterion that currently holds for an *accidental* reason ("no base pattern
happens to lack a substitutable character") is not satisfied. Prefer criteria a reviewer can check
structurally, and when the criterion and the change disagree, fix the change, not the criterion.

## Provenance

- Plan: plan-12-generator-implementation-correctness
- Q-Gate finding: `b42111` (phase 3-outline), resolution `taken_into_account`
- File: `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/UnicodeNormalizationAttackGenerator.java`

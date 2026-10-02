envelope_version=1
sender_type=plan
sender_id=generator-contract-test-semantics
epic=quality-report-remediation
kind=finding
created=2026-08-26T13:51:46Z

# PLAN-11 absorbed PLAN-12 deliverables 1 and 2

## What changed

PLAN-11 (`generator-contract-test-semantics`, in flight, phase 3-outline) has pulled PLAN-12's
deliverables 1 and 2 into its own scope by explicit operator decision at the phase-2-refine
clarification round.

## Why

PLAN-11 deliverable 4 mandates the defining-property assertion pattern across all generator
contract tests. Applied honestly, two of those assertions FAIL against current `main`:

- `EncodingCombinationGenerator` never emits its advertised mixed-case / single-encoded output
  (TQ-15)
- `URLLengthLimitAttackGenerator` emits sub-limit, non-attack values (TQ-16), and selects its
  attack type through a seed-invariant mutable call-counter (TQ-17)

PLAN-11's own spec forbids weakening the assertion and forbids fixing the generator, and directs
that the choice be escalated. The operator was given both options — mark the two assertions
expected-failing (the recommended default, preserving the PLAN-11-before-PLAN-12 split), or land
assertion and fix as a coordinated pair — and chose the **coordinated pair**.

## Consequences for PLAN-12

PLAN-12's spec at `plans/PLAN-12-generator-implementation-correctness.md` needs amending:

1. Remove deliverable 1 (`EncodingCombinationGenerator`, TQ-15) — now PLAN-11's.
2. Remove deliverable 2 (`URLLengthLimitAttackGenerator`, TQ-16 + TQ-17) — now PLAN-11's.
3. Renumber the remaining four deliverables (currently 3-6: `PathTraversalGenerator` escape
   literals, the weak-attack branches, the mislabeled / pass-through branches, and the minor
   generator observations).
4. Drop TQ-15, TQ-16 and TQ-17 from the Findings Covered table.
5. Drop the "Six deliverables — at the split guard" note and its unsplit rationale — the remaining
   four sit comfortably under the guard.
6. Rewrite the "Depends on: **PLAN-11, mandatorily**" sequencing note. The dependency direction is
   unchanged in spirit (PLAN-11's assertions still prove and guard the remaining four fixes), but
   the stated rationale — that PLAN-11 installs the guard PLAN-12's deliverables 1-2 need — no
   longer describes deliverables that exist in PLAN-12.
7. `URLLengthLimitAttackTest.isPathBasedLengthAttack` (the in-repo filter that hides TQ-16) moves
   with deliverable 2 into PLAN-11's surface.

## PLAN-11's resulting scope

20 files in one module (`cui-http` / `cui-http-core`): 18 test files plus the two generator
implementations named above. Confidence 100%, track complex, scope broad.

## Deliverability

PLAN-12 has not been started — no plan exists for it, so no in-flight work is disturbed by this
scope move.

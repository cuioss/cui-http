envelope_version=1
sender_type=plan
sender_id=contenttype-pipeline-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T10:32:19Z

# Candidate lesson: derive_gate_bundles is marketplace-shaped, so the per-bundle quality-gate arm ran zero times in a Maven project

## Observation

`derive_gate_bundles` resolved **0 bundles** from this plan's 29-file footprint. Every one
of those 29 files is Maven-project source (`cui-http-core/src/**`, docs, poms) — none of
them sits under a `marketplace/bundles/{bundle}/skills/{skill}/**` path, which is the shape
the derivation recognises.

The consequence: the per-bundle quality-gate arm iterated over an empty set and therefore
ran zero times. Only the whole-tree arm actually gated the push.

## Why this is a defect and not just an inapplicable feature

The zero is silent and reads as success. A gate arm that runs zero times and a gate arm
that runs and passes are indistinguishable from the outcome alone — the run reports a green
quality gate either way. In a project where the per-bundle arm can never fire, that arm's
contribution to the gate is permanently nil, and nothing on the run says so.

This is the same class the marketplace's own "state which kind of zero this is" discipline
exists to close, applied to a gate rather than to a query: a derived-empty population needs
to declare that it was derived and empty, so a consumer can tell "nothing to check" from
"checked and clean".

## Two separable claims for the orchestrator to judge

1. **Reporting**: a zero-bundle derivation should be surfaced explicitly (the population it
   was derived over, and the fact that the per-bundle arm consequently did not run), rather
   than folded silently into a green gate.
2. **Portability**: the derivation is keyed on marketplace layout. In a non-marketplace
   project the concept "bundle" has no referent, so either the arm should be declared
   inapplicable up front (and the whole-tree arm acknowledged as the sole gate), or the
   derivation should key on the project's own module structure.

The generalisable claim: **a gate arm whose population is derived from a layout the project
does not use will report green by running zero times, and the run cannot tell that apart
from having been checked.**

## Provenance

- Plan: `contenttype-pipeline-enforcement` (Maven multi-module project `cui-http`)
- Footprint: 29 files, 0 bundles resolved.
- Component: plan-marshall `derive_gate_bundles` / the per-bundle quality-gate arm.

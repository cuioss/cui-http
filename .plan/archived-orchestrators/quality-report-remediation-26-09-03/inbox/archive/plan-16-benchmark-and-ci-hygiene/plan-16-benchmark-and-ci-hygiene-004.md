envelope_version=1
sender_type=plan
sender_id=plan-16-benchmark-and-ci-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T16:06:33Z

# Candidate lesson (tooling defect): `ci_health verify` reports `gh.authenticated: false` against a multi-account `gh auth status`

## Observed signal

`ci_health verify` returned `gh.authenticated: false`. `gh` was in fact
authenticated, with **two accounts** configured; `gh auth status` reported both.
The probe appears to mis-parse the multi-account output shape and concludes
unauthenticated.

Consequence in this run: the false negative blocked the dispatched `create-pr`
leaf, and the run fell back to invoking `gh pr create` directly to make progress.

## Why it is durable

The defect is in a **preflight health probe**, so its failure mode is maximally
expensive relative to its own scope: it does not fail the operation it guards,
it prevents that operation from being attempted at all, and its verdict is
phrased as a fact about the environment rather than as a fact about what the
probe could read. A single-account environment never reproduces it, so it will
keep recurring only for operators with more than one `gh` account — which is
exactly the population least likely to suspect the probe.

## Scope note (deliberately narrow)

Reported as observed, not generalized into a rule about probes at large. The
actionable content is:

- `ci_health verify`'s `gh` authentication probe needs a parser that handles
  multi-account `gh auth status` output (multiple host/account blocks), and a
  test fixture with two accounts.
- Until fixed, a `gh.authenticated: false` verdict should be treated as
  *possibly unreadable* rather than *definitely unauthenticated* before it is
  allowed to block a dispatch.

## Suggested classification (orchestrator judgement)

`bug`, owned by the CI-health/tooling component. Concrete and fixable; does not
need generalizing.

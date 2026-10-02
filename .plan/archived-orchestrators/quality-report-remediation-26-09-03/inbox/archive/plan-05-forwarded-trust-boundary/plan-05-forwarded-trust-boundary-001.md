envelope_version=1
sender_type=plan
sender_id=plan-05-forwarded-trust-boundary
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T20:08:41Z

# Candidate lesson: an automated dependency/parent bump must run the project's own quality gate

## Signal

Source: `signal_qgate_pending_count` / license-header churn observed across the plan's
own diff during phase-6 finalize.

## Observation

`cui-java-parent` 1.5.5 changed the license header template to
`<year>${project.inceptionYear}-present</year>`. Three bot-authored parent bumps
(PR #144, #145, #146) each changed exactly one line — the parent version — and none
of them ran the project's mandated quality gate (`verify -Ppre-commit`). The header
regeneration that their own bump made mandatory therefore never landed, and `main`
sat non-compliant with its own parent's template.

The debt was not paid by the bumps that created it. It was paid by PR #154, and the
first feature PR to actually run the gate — this plan's — inherited a 229-file diff
that was almost entirely license-header churn unrelated to its own change.

## Consequence (the part that makes this more than housekeeping)

The inherited 229-file diff exceeded CodeRabbit's 100-file review limit and Sourcery's
diff-character cap. Both bots declined to review. A HIGH-severity security change
(the forwarded-header trust boundary) consequently went unreviewed by both automated
reviewers until the branch was rebased to shed the churn.

So the cost of the skipped gate was not "a stale header". It was the silent loss of
automated security review on an unrelated, higher-risk PR.

## Candidate directive

An automated dependency/parent bump must run the project's own quality gate and commit
any generated-content churn its bump causes, in the bump's own PR. A bump that defers
generated-content regeneration displaces its cost onto the next unrelated PR, and that
displacement can disable review tooling that has per-PR size limits.

## Evidence

- PRs #144, #145, #146 — single-line parent bumps, no quality-gate run.
- PR #154 — the catch-up commit that regenerated the headers.
- This plan's PR (#155) — inherited the 229-file diff; CodeRabbit and Sourcery both
  declined to review until the branch was rebased.

## Scope note for the orchestrator

This is the widest-blast-radius candidate of the run: it concerns repository automation
policy rather than this plan's own subject matter, and it is likely to recur on every
future parent bump across cuioss repositories that share the parent POM.

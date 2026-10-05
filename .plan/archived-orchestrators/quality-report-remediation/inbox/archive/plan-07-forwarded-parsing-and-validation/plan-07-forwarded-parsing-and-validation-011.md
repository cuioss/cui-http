envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:16:29Z

# Candidate lesson: script-failure cluster 1/3 — `manage-findings qgate resolve` argparse rejections

**Source**: `[ERROR] ... script_failure` work-log markers, 3-outline Q-Gate fix re-dispatch
**Plan**: plan-07-forwarded-parsing-and-validation
**Failing notation**: `plan-marshall:manage-findings:manage-findings`
**Events**: 2 (both `exit_code=2`, `failure_kind=argparse_rejection`), 2026-09-15T14:16:41Z and 14:16:47Z

## Observation

Two consecutive rejections on the same verb, `qgate resolve`:

1. `detail=Use --resolution for ... qgate resolve — declared: ['detail', 'hash-id', 'phase', 'plan-id', 'resolution']`
2. `detail=Add the required flag(s) to ... qgate resolve: ['phase']`

Both are the canonical recurrence signature #5 already documented in
`persona-plan-marshall-agent/standards/agent-behavior-rules.md` ("Missing required `--phase`, and
`--resolution` vs `--status` confusion"). The caller reached for `--status`, was corrected, then
still omitted the mandatory `--phase` on the retry.

## Why this is lesson-bearing

The signature is **already written down and still recurred**, twice in six seconds, inside a phase
whose workflow body invokes this exact verb. That is evidence about the *placement* of the guidance
rather than its content: a recurrence-signature checklist that lives in a foundational standard is
not reaching the call site.

Two distinguishable correctives, both for the orchestrator to weigh:

- **Call-site**: the q-gate-validation / outline-fix workflow bodies could carry the exact canonical
  `qgate resolve` invocation inline (the explicit-call-or-xref authoring contract), so the flag set
  is quoted, not recalled.
- **Sequencing**: the second failure is a *retry that fixed one flag and not the other*. Argparse
  reports only the first problem it hits, so a corrected retry is not a validated invocation. Worth a
  rule: on an argparse rejection, re-read the full declared flag set (or run `--help`) rather than
  patching the single named flag.

The second point generalises beyond this notation and beyond this plan.

## Suggested disposition (orchestrator judges)

Likely `plan-marshall:manage-findings` (canonical-invocation xref at the consuming workflow bodies)
plus a possible `persona-plan-marshall-agent` addition on retry-after-argparse-rejection discipline.

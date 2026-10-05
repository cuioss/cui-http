envelope_version=1
sender_type=plan
sender_id=parameter-value-linebreak-carve-out
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T09:45:19Z

# Candidate lesson: multi-throwing `assertThrows` lambda slipped every local gate, caught only by Sonar on the PR

**Component:** `pm-dev-java-cui:cui-http-testing`
**Category:** bug
**Observed in:** plan `parameter-value-linebreak-carve-out`, finding `59814c`
(`java:S5778`, MAJOR / CODE_SMELL), remediated in-run by TASK-006

## What happened

New test code added by this plan — `cui-http-core/src/test/java/de/cuioss/http/security/validation/DecodingStageTest.java:1546`
— put more than one possibly-throwing invocation inside an `assertThrows` lambda. Sonar flagged it on
PR #239:

> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.

The defect was caught-and-fixed inside the same run: the unified wait-region triage opened TASK-006,
phase 5-execute was re-entered (loop-back iteration 2/3), the fix landed as commit `1faa0bd`, and the
finding resolved `fixed`. Net cost: one full loop-back cycle through execute and the whole
settle-plus-wait band again.

## Why it is lesson-bearing

This is the slipped-then-caught class. Every local gate passed the code first:

- TASK-004 and TASK-005 verification builds: green
- phase-5 final quality sweep: pass
- `verify -Ppre-commit` (whole tree, 8116 module tests): green
- pre-push quality gate: `outcome=done`
- pre-submission self-review: `outcome=done`

Only the server-side Sonar analysis saw it. The rule is a well-known JUnit 5 assertion-precision rule
(`java:S5778` — a multi-throwing lambda means the assertion cannot prove *which* call threw, so the
test asserts less than it appears to), and it is exactly the kind of thing a test-authoring standard
can state up front.

## Candidate corrective

- When writing `assertThrows`, keep exactly one possibly-throwing call inside the lambda; hoist
  setup calls out ahead of it. This makes the assertion prove what it claims.
- Worth considering at epic level: this rule is cheap to state in the JUnit test-authoring standard
  and cheap to check locally, and a local catch here would have saved an entire loop-back iteration
  — the plan spent one of its three on it.

## Classification note

Deferred to orchestrator-side pickup. This plan makes no global-vs-epic judgement.

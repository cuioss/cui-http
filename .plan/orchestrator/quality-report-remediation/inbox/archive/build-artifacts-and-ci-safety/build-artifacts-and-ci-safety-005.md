envelope_version=1
sender_type=plan
sender_id=build-artifacts-and-ci-safety
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-06T09:07:02Z

component=plan-marshall:manage-execution-manifest
category=improvement
title=Resolve a declared verification arm against a real canonical command at composition time, not at run time

# Resolve a declared verification arm against a real canonical command at composition time, not at run time

The plan's manifest declared `module-tests` and `test-compile` verification arms.
The project exposes **no canonical command for either, at any scope**, so neither
arm could run. The gate recorded them honestly — `pre-push-quality-gate` reported
`whole-tree quality-gate green, test-compile+module-tests UNGATED` — which is the
right behaviour and is not itself the defect.

The defect is upstream: an arm that can never run was declared as a verification
commitment, and that was only discovered at the moment the gate tried to execute
it, deep in finalize.

## Why the late discovery costs something

- The plan carried a **coverage claim it could not honour** from composition
  through execution. Anyone reading the manifest before the gate ran would have
  concluded those surfaces were gated.
- `UNGATED` is the correct record but a weak signal: it is one clause inside a
  step's `display_detail`, and it appears only after the work is done and the
  branch is ready to push. There is no decision left to change by then.
- The alternative failure — recording an un-runnable arm as **passed** — is one
  step away and would be undetectable. That the run avoided it is worth noting
  precisely because nothing structural prevented it.

## Rule

- At manifest composition time, resolve every declared verification arm against
  the project's actual canonical-command surface. An arm with no resolvable
  command is a composition-time error, not a run-time observation.
- When an arm genuinely cannot be provided, either drop it from the manifest or
  record it as explicitly waived **with its reason**, so the manifest never
  advertises coverage the project cannot deliver.
- Never let an un-runnable arm resolve to a passing outcome. `ungated` /
  `not_available` must be a distinct terminal value from `passed`, and it must be
  visible in the manifest itself rather than only in a step's rendered detail
  string.

The general form: a declared check that cannot execute is worse than no declared
check, because it converts an absence of coverage into an appearance of it.

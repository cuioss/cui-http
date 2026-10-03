envelope_version=1
sender_type=plan
sender_id=validated-redirect-following
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T19:57:18Z

component=plan-marshall:manage-architecture
category=improvement
bundle=plan-marshall

# A verification step whose canonical command does not exist records `skipped`, indistinguishable from "not needed"

## Observation

In plan `validated-redirect-following` the verification step
`verify:module-tests` recorded outcome `skipped`. The reason was not that
module tests were unnecessary — it is that the canonical simply does not exist
in this project. Reproduced directly at finalize time:

```text
$ architecture resolve --command verify:module-tests
status: error
error: architecture_error
message: Command not found
module: cui-http-parent
command: "verify:module-tests"
available[6]: clean, quality-gate, verify, install, compile, package
```

The project registers six canonicals; `verify:module-tests` is not among them.

## Why this is reusable

`skipped` is being used for two materially different things:

- **"This step did not apply to this run"** — a real, informed skip.
- **"This step could not be resolved at all"** — a could-not-look.

Collapsing them means a verification sweep can report a clean pass while one of
its steps never ran and nobody was told why. This is the same
zero-discrimination failure the rest of the toolchain has been careful about
elsewhere (`could_not_look` vs a genuine zero); the verification-step outcome
vocabulary has not yet had it applied.

Two things make it worse in practice: the outcome is recorded per run, so a
permanently-unresolvable canonical produces an indefinite run of clean-looking
`skipped` records; and nothing reconciles the manifest's declared verification
steps against the project's actually-registered canonicals, so the
misconfiguration has no other detection surface.

## Suggested rule

1. Distinguish `skipped` (informed, step did not apply) from a distinct
   `unresolvable` / `not_configured` outcome (the canonical does not resolve in
   this project). Record the resolver's own error and the `available[]` list on
   the latter, so the record says which kind of zero it is.
2. Validate the execution manifest's verification steps against
   `architecture resolve` at manifest-composition time, not at execution time.
   A step naming a canonical the project does not register is a manifest
   authoring error and should be reported once, up front, rather than as a
   silent per-run skip.
3. Either register the canonical in this project or drop the step from the
   manifest — leaving it as a permanent silent skip is the worst of the three.

## Evidence

Plan `validated-redirect-following`, verification step `verify:module-tests`,
outcome `skipped`. Resolver output quoted verbatim above.

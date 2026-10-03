envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:37:53Z

# Candidate lesson: seven build-error findings auto-resolved "by green build" from a run that executed zero tests

## What happened — the surface defect

A scoped test run was invoked as:

```
test -pl cui-http-core -am -Dtest=UnicodeAttackGeneratorTest+UnicodePathTraversalAttackTest+URLPathValidationPipelineTest+AllGeneratorsIntegrationTest -DfailIfNoSpecifiedTests=false
```

Surefire failed with:

```
No tests matching pattern "UnicodeAttackGeneratorTest+UnicodePathTraversalAttackTest+URLPathValidationPipelineTest+AllGeneratorsIntegrationTest" were executed!
(Set -Dsurefire.failIfNoSpecifiedTests=false to ignore this error.)
```

Two independent argument errors in one invocation:

1. `-Dtest` takes a **comma**-separated list. `+` is not a separator — the whole string was treated
   as one class name, matching nothing.
2. `-DfailIfNoSpecifiedTests=false` is the wrong property. Surefire 3.x wants
   `-Dsurefire.failIfNoSpecifiedTests=false`, and says so in the very error message. The unprefixed
   form is silently ignored, so the guard the caller thought they had was not in effect.

Both are the "plausible name that isn't the declared name" class — the argparse-rejection signature,
in Maven's property namespace.

## What happened — the more interesting defect

The failure produced **seven** `build-error` findings (`d874d8`, `4e8b47`, `3cb516`, `81bad2`,
`3efa48`, `250543`, `2794ea`). Six of them are not errors at all — they are Maven's epilogue lines
("re-run with -e", "[Help 1] …MojoFailureException", "mvn <args> -rf :cui-http"), captured as
individual findings of category `deprecation_warning`, severity `error`. One real failure became
seven findings, none categorised correctly.

All seven were then closed with:

```
auto-resolved by green build (analyses examined: compile; 0 test(s) executed; ...):
  ... test-compile -pl cui-http-core -am
```

The "green build" that resolved a **test-selection** failure was a `test-compile` run that executed
**zero tests**. It could not, even in principle, have exercised the condition that failed. Seven
findings were marked `fixed` on evidence that does not bear on them.

## Candidate rules

1. `-Dtest` list separator is `,`; the no-match escape hatch is `-Dsurefire.failIfNoSpecifiedTests`,
   not `-DfailIfNoSpecifiedTests`. Worth encoding in the maven build-wrapper rather than
   rediscovering.
2. Maven's error epilogue (`-e` / `-X` / `[Help N]` / `-rf :module` lines) should be recognised as
   trailer, not parsed into findings. Seven findings for one failure is noise that makes the
   findings store less trustworthy, not more complete.
3. **The load-bearing one:** auto-resolution by green build must check that the resolving build
   actually *entitles* the resolution. A build that ran 0 tests cannot resolve a test-execution
   finding; a compile-only phase cannot resolve anything downstream of compile. The resolution
   detail already records `0 test(s) executed` — the datum needed to refuse is present and simply
   not gated on. Otherwise the mechanism launders unverified findings into `fixed`.

## Provenance

- Plan: plan-12-generator-implementation-correctness
- Findings: `d874d8`, `4e8b47`, `3cb516`, `81bad2`, `3efa48`, `250543`, `2794ea` (all `build-error`,
  all resolution `fixed`)
- Note: `signal_script_failure_clusters_count` was reported as 0 for this run, so this cluster
  reached the candidate stream via the findings store rather than via the failure-cluster signal.

envelope_version=1
sender_type=plan
sender_id=validated-redirect-following
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T19:57:57Z

component=plan-marshall:build-maven
category=bug
bundle=plan-marshall

# Maven's failure epilogue is filed as ~30 separate build-error findings, all mislabelled deprecation_warning

## Observation

Plan `validated-redirect-following` finished with 61 findings in its store.
Their distribution:

```text
build-error   31
test-failure  14
pr-comment     6
sonar-issue    3
anti-pattern   2
```

The 31 `build-error` records and the 14 `test-failure` records represent
roughly **two** actual failures between them. The rest is parser noise, of two
distinct kinds.

### Kind 1 — Maven's failure epilogue parsed line-by-line as separate errors

Every line of Maven's standard post-failure epilogue became its own
`build-error` finding, and each was categorised `deprecation_warning`. Verbatim
titles from the store:

```text
Build error [deprecation_warning]: To see the full stack trace of the errors, re-run Maven with the -e switch.
Build error [deprecation_warning]: Re-run Maven using the -X switch to enable full debug logging.
Build error [deprecation_warning]: For more information about the errors and possible solutions, please read the following articles
Build error [deprecation_warning]: [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
Build error [deprecation_warning]: After correcting the problems, you can resume the build with the command
Build error [deprecation_warning]: mvn <args> -rf :cui-http
Build error [deprecation_warning]: See dump files (if any exist) [date].dump, [date]-jvmRun[N].dump and [date].dumpstream
Build error [deprecation_warning]: Failures:
```

None of these is an error. `mvn <args> -rf :cui-http` is a *resume hint*.
`[Help 1] http://cwiki...` is a URL. `Failures:` is a section header. And the
`deprecation_warning` category is wrong on every one of them — nothing here is
a deprecation. This exact block was captured three times over (once per failing
build in the run), which is where most of the 31 come from.

### Kind 2 — one test failure filed at three granularities

The same failure is recorded as three findings:

```text
Test failure: Tests run: 34, Failures: 1, ... <<< FAILURE!      (surefire summary line)
Test failure: de.cuioss...HttpHandlerRedirectTest.malformedLocationControlCh...  (FQN line)
Test failure in HttpHandlerRedirectTest.java:256: ...                            (file:line)
```

plus a run-level `Tests run: 6753, Failures: 1` roll-up and a `Failed to
execute goal ...surefire-plugin:3.5.6:test` wrapper — five records for one
broken test.

## Why this is reusable

This is a build-log-parser contract problem, not a project problem, and it has
a real cost: the findings store is the substrate the finalize gate counts. A
30:1 noise ratio makes `pending_findings_blocking_count` meaningless as a
human signal, buries the two records that actually mattered (the
`anti-pattern` security findings), and inflates every downstream report.

## Suggested rule

1. **Stop parsing at the epilogue.** Maven's post-`BUILD FAILURE` block
   (`To see the full stack trace`, `Re-run Maven using`, `[Help N]`,
   `After correcting the problems`, `mvn <args> -rf`, `See dump files`) is
   boilerplate; treat it as a terminator, not as content.
2. **Do not default an unclassified line to `deprecation_warning`.** A
   catch-all category that names a specific defect class is worse than an
   honest `unclassified` — every one of these 31 records asserts a deprecation
   that does not exist.
3. **Emit one finding per distinct failure, at one granularity.** Prefer the
   `file:line` form (it carries `file_path` + `line`, which the triage and
   evidence-gated resolve paths consume); fold the FQN line, the surefire
   summary and the plugin-goal wrapper into that record's detail rather than
   filing them as peers.
4. **Dedup across repeated builds within a run.** The same epilogue captured
   once per build attempt should reinforce one record, not allocate three.

## Evidence

Plan `validated-redirect-following` findings store, 61 records; type counts and
verbatim titles quoted above.

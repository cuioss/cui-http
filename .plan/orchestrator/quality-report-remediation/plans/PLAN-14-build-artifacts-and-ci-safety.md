# PLAN-14: Build Artifacts and CI Safety

epic: quality-report-remediation
workstream: WS-07

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-14-build-artifacts-and-ci-safety.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Stop the benchmark workflow from destroying the published benchmark site, and clean up the
published artifact and token scope around it. `benchmark-pages.py`'s `assemble` step prints a
warning and continues when the results directory is missing, still writes its metadata, and the
deploy step then runs `rm -rf $TARGET_DIR` followed by an unconditional commit — publishing a
near-empty tree over the live site. The verification pass established this is worse than the
report states: with no present modules the history-merge loop is skipped too, so the whole
published history and index are destroyed, not just the current run, and the `continue-on-error`
history fetch is a second, independent trigger for the same loss. Alongside it, the `generators`
classifier jar's single exclude cannot match javac's synthetic switch-map classes and the jar
carries the same `Automatic-Module-Name` as the main artifact; the benchmark module declares
Lombok that no source uses; two stage-isolation benchmarks never reach the code paths their
Javadoc claims; and `benchmark.yml` mints an App token scoped to this repository as well as
`cuioss.github.io`, readable by every later step in the job including the Maven and JMH runs.

## Deliverables

1. **The benchmark deploy cannot publish an empty tree (F-D-14).** Make `assemble` fail rather
   than warn when the results directory is absent, and gate the deploy step on a non-empty
   assembled tree. Close the second trigger too: the `continue-on-error: true` history fetch means
   a transient failure leaves `benchmark-history/` empty, the merge loop finds nothing to carry
   forward, and the same `rm -rf` destroys the history — so the fetch must either be mandatory or
   its failure must block the deploy.
2. **The `generators` classifier jar contains what it claims (F-D-15).** The single exclude
   `${...package.generators}/**/*Test.class` matches only filenames ending in the literal
   `Test.class`, so a synthetic `EnclosingClass$1.class` that javac emits for an enum switch is not
   excluded and ships in the jar. Broaden the exclude to cover nested and synthetic classes of
   excluded types. Separately, the parent POM applies
   `Automatic-Module-Name=${maven.jar.plugin.automatic.module.name}` to every `maven-jar-plugin`
   execution, and that property resolves to `de.cuioss.http` — the same name `module-info.java`
   declares — so the classifier jar and the main jar conflict on the module path. Give the
   classifier jar its own module name.
3. **Unused Lombok removed from the benchmark module (F-D-16).** The module declares Lombok both
   as a compile dependency and as an annotation-processor path, and no source under
   `cui-http-benchmarking/src` imports it.
4. **Benchmarks measure what their Javadoc says (F-D-17).** `ValidationStageBenchmark`'s
   `decodingStage` and `normalizationStage` methods are both fed `state.nextCleanUrl()`, and
   `SecurityBenchmarkState.CLEAN_URLS` contains no `%` and no dot-segments — so the percent-decoding
   path and the dot-segment-resolution path are never exercised despite the Javadoc claiming
   exactly those. `PipelineThroughputBenchmark.measureThroughput` and `urlPathCleanThroughput` have
   byte-identical bodies. Give each benchmark input that reaches its stated path, and either
   differentiate or merge the duplicate pair.
5. **The benchmark workflow's token is scoped to what it uses (F-D-18).** The
   `create-github-app-token` step requests `${{ github.event.repository.name }},cuioss.github.io`,
   but the token output is referenced exactly once — as the `token:` input for checking out
   `cuioss.github.io`. The source checkout uses no token and sets `persist-credentials: false`.
   Drop this repository from the scope.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that corroborated 5 of 5 findings in `06-build-and-infrastructure.adoc` with 0
contradicted and 0 unverifiable.

- OBSERVED: `assemble()` prints `Warning: …` and continues when
  `target/benchmark-results/gh-pages-ready` is absent, then still runs `mkdir`,
  `badges_dir.mkdir` and the `metadata.json` write — read at
  `cui-http-benchmarking/scripts/benchmark-pages.py` (lines 172-177, 201-223). The deploy step
  runs `rm -rf $TARGET_DIR; cp -r gh-pages/*; git add .; git commit` unconditionally — read at
  `.github/workflows/benchmark.yml` (lines 112-117).
- OBSERVED (blast radius, established by the verification pass and NOT stated in the report):
  because `present_modules` is empty in that state, the assemble step's history-merge loop is also
  skipped, so a previously-deployed `micro/history` tree is never carried into `gh-pages/` and is
  then removed by the `rm -rf` — destroying the whole benchmark history and index rather than one
  run's results. The `Fetch Previous History` step's `continue-on-error: true` at lines 65-73 is a
  second, disjoint trigger for the same loss, and fixing one does not fix the other.
- OBSERVED: the classifier execution's only exclude is
  `<exclude>${...package.generators}/**/*Test.class</exclude>` — read at `cui-http-core/pom.xml`
  (line 98) — which matches only names ending in the literal `Test.class`, so a javac-emitted
  `EnclosingClass$1.class` for an enum switch (such as the arrow switch over `ValidationType` in
  `SupportedValidationTypeGeneratorContractTest.shouldProvideReasonableDistribution`, lines
  110-116) is not excluded.
- OBSERVED: the parent POM `cui-java-parent-1.6.2` applies
  `Automatic-Module-Name=${maven.jar.plugin.automatic.module.name}` to every `maven-jar-plugin`
  execution via `pluginManagement` merge (lines 76-78), and that property resolves to
  `de.cuioss.http` at `cui-http-core/pom.xml`:15 — the identical name `module-info.java`:16
  declares. **Citation correction:** the report's parenthetical "core pom line 66" for this
  property is wrong; the property is at line 15, and line 66 is an unrelated `<dependency>` block.
- OBSERVED: `grep -rl lombok cui-http-benchmarking/src` returns zero files, while
  `cui-http-benchmarking/pom.xml` declares Lombok as a compile dependency (lines 90-93) and as a
  `maven-compiler-plugin` annotation-processor path (lines 109-112).
- OBSERVED: `ValidationStageBenchmark`'s `decodingStage` and `normalizationStage` methods (lines
  47-48, 59-60) are both fed `state.nextCleanUrl()`, and `SecurityBenchmarkState.CLEAN_URLS`
  (lines 56-67) contains no `%` and no dot-segments;
  `PipelineThroughputBenchmark.measureThroughput` (lines 41-43) and `urlPathCleanThroughput`
  (lines 47-49) have byte-identical bodies — read at those files.
- OBSERVED (verified strength, recorded so no plan "fixes" it): every `@Benchmark` method across
  the three classes returns its `Optional<String>` result, so JMH dead-code elimination is
  correctly avoided throughout. `@Threads(1)` on the two classes is overridden globally via
  `Options.threads()` per the `pom.xml` comment at lines 45-50.
- OBSERVED: the `create-github-app-token` step requests
  `repositories: ${{ github.event.repository.name }},cuioss.github.io` (lines 39-46) while
  `steps.app-token.outputs.token` is referenced exactly once in the whole file, at line 110, as the
  checkout token for `cuioss.github.io`; the source checkout at lines 48-52 uses no token and sets
  `persist-credentials: false` — read at `.github/workflows/benchmark.yml`.
- OBSERVED (blast radius, established by the verification pass): the token is available via
  step-output interpolation to every later step in the same job, including the `./mvnw install` and
  JMH benchmark-run steps, so a compromised action or transitive Maven/JMH dependency could read it
  and use it for authenticated write access to this repository — a scope the workflow never
  otherwise uses. The same `RELEASE_APP_ID` / `RELEASE_APP_PRIVATE_KEY` secrets back the release
  workflow, so the token likely carries non-trivial write permissions.
- Verify-first clause: before scoping deliverable 2's module-name change, settle whether any
  downstream consumer resolves the `generators` classifier jar on the module path under
  `de.cuioss.http`. Read `cui-http-core/src/main/java/module-info.java` and the classifier
  execution's configuration. A refutation — the jar is only ever used on the classpath — means the
  duplicate name is latent rather than active, and the fix is still correct but not urgent.

## Expected Surface

- OBSERVED: `cui-http-benchmarking/scripts/benchmark-pages.py` — `assemble`
- OBSERVED: `.github/workflows/benchmark.yml` — the token step (lines 39-46), the history fetch (lines 65-73), the deploy step (lines 112-117)
- OBSERVED: `cui-http-core/pom.xml` — the `maven-jar-plugin` `generators` execution (lines 91-98) and the `maven.jar.plugin.automatic.module.name` property (line 15)
- OBSERVED: `cui-http-benchmarking/pom.xml` — the Lombok dependency (lines 90-93) and annotation-processor path (lines 109-112)
- OBSERVED: `cui-http-benchmarking/src/main/java/de/cuioss/http/security/benchmark/standard/ValidationStageBenchmark.java`
- OBSERVED: `cui-http-benchmarking/src/main/java/de/cuioss/http/security/benchmark/standard/PipelineThroughputBenchmark.java`
- OBSERVED: `cui-http-benchmarking/src/main/java/de/cuioss/http/security/benchmark/SecurityBenchmarkState.java`
- HYPOTHESIS: `cui-http-core/src/main/java/module-info.java` — read to settle the verify-first clause; edited only if the module name must move (verify-at-outline)

⛔ This plan does **not** edit `cui-http-core/src/main/java` library code or
`cui-http-core/src/test/java`. `SupportedValidationTypeGeneratorContractTest` is named above only
as the example that produces the synthetic class; the fix is in the POM exclude, not in the test.

## Dependencies and Sequencing

- Depends on: none.
- Overlaps with: nothing. No other plan in the epic touches a POM, a workflow, or the benchmark
  module. This is the epic's only fully surface-disjoint plan and its safest concurrency partner.
- Adjacent to: `cui-http-core/src/test/java/de/cuioss/http/security/generators/`, whose compiled
  output the classifier jar packages. WS-05 PLAN-11 edits that tree; this plan changes only which
  compiled classes are packaged, so the two do not collide.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-14-build-artifacts-and-ci-safety.md"
```

## Execution Constraints

- **Emit this plan early despite its low severity.** `F-D-14` is the epic's only finding with an
  ongoing destructive risk: every scheduled benchmark run can destroy the published history, and
  the plan is fully surface-disjoint from every other plan, so nothing is gained by holding it.
- **TDD applies to deliverables 1 and 4.** For deliverable 1, add a test of `benchmark-pages.py`'s
  `assemble` that runs against an absent results directory and asserts a non-zero exit; see it fail
  first. For deliverable 4, assert that the benchmark inputs contain a `%` and a dot-segment before
  changing the state class.
- Deliverables 2, 3 and 5 are configuration changes with no unit-test surface. Verify deliverable 2
  by unpacking the built classifier jar and listing its entries — the check is that no `$`-suffixed
  synthetic class of an excluded type is present and that the manifest's `Automatic-Module-Name`
  differs from the main jar's.
- Deliverable 5 narrows a token scope. Confirm the benchmark workflow still deploys successfully
  after the change before the plan reports done.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

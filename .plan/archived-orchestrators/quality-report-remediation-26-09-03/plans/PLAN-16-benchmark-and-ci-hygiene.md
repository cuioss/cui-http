# PLAN-16: Benchmark and CI Hygiene

epic: quality-report-remediation
workstream: WS-06

## Objective

Make the forwarded attack-throughput benchmark measure the resolver instead of the console, and
tighten the CI and build configuration around it. `resolveAttackThroughput` cycles three attack sets,
and TWO OF EVERY THREE invocations deterministically emit two JUL WARN records each to a
`ConsoleHandler` — so on those invocations the measured hot path is synchronized console I/O plus
`SimpleFormatter` formatting, not header sanitization. Over five four-second throughput iterations
this also floods CI logs. The numbers that benchmark publishes are therefore not measurements of the
thing it names. Alongside it: the pages script's "results not found" guard can be defeated by its own
history-merge step in a way that would wipe the live dashboard, the benchmark job requests write
access no step uses, and the cross-repo pages push races any other repo deploying at the same time.

## Deliverables

1. **Fix the benchmark's logging contamination** — raise `de.cuioss.http.forwarded.level` to `SEVERE`
   or `OFF` in `benchmark-logging.properties`, or use attack inputs that fail WITHOUT logging. Note
   the security-pipeline benchmarks do not have this problem, because the `security` main packages
   contain no logging calls. (BB-1)
2. **Make the pages-script guard effective** — move the history-merge step AFTER the existence check.
   As written, if `--micro-results` does not exist but previous pages history does, step 1 creates
   `results_dir/history`, so the later `if not results_dir.is_dir()` check passes and a history-only
   tree is deployed; combined with the workflow's `rm -rf "$TARGET_DIR"` this would wipe the live
   dashboard. Low likelihood — a failing Maven step aborts the workflow first — but the guard does
   not do its job. (BB-2)
3. **Downgrade the benchmark job's token to `contents: read`** — it requests `contents: write` but no
   step writes to this repo with the default `GITHUB_TOKEN`: checkout uses `persist-credentials: false`
   and the pages deploy uses a GitHub App token against a DIFFERENT repo. The file's own header
   declares least-privilege intent. (BB-3)
4. **Serialize the cross-repo pages push** — add fetch-rebase-retry around the plain `git push`. The
   `concurrency` group serializes only this repo's benchmark runs, so any other cuioss repo deploying
   to `cuioss.github.io` simultaneously races it, causing an occasional failed deploy. The workflow
   comment already admits the gap. (BB-4)
5. **Clear the build and profile hygiene items** — declare `<scope>test</scope>` explicitly on
   `junit-jupiter-api` (verified safe today via the managed BOM scope, but it is the only test
   dependency relying on it while every sibling declares it, so an explicit scope removes the trap);
   reconcile the `quick` profile, which INCREASES per-iteration times while reducing iteration counts
   (net time still drops, but the name is surprising); and remove the leftover "Example: Copy this
   to your repo…" template comments from `maven.yml` and `release.yml`. (BB-6, BB-7, BB-10)
6. **Resolve the two items needing a decision rather than an edit** — ⛔ **BB-8 is an operator
   question, not a code fix:** `cui-http-benchmarking` will publish to Maven Central on release,
   because the publishing chain publishes every reactor module and the benchmarking pom sets
   `sonar.skip` but no publish skip, so the JMH jar (which depends on
   `de.cuioss.sheriff.oauth:benchmarking-common`) ships alongside `cui-http`. **Escalate this;
   do not decide it in-plan.** If the answer is "not intended", add a publish/deploy skip. BB-9 is
   the release skill's two-segment `X.Y` version assumption against a three-segment
   `current-version: 2.1.0` — cosmetic tension only, since the default rule still yields a sane
   `2.2` and the skill has an "ask if inconsistent" escape hatch; resolve or record as accepted. (BB-8, BB-9)

Six deliverables — at the split guard. Proceeding unsplit: this is the epic's only build-and-CI plan,
covering one module and one workflow directory that no other plan touches; splitting it would create
two plans competing for the same three files with no throughput gain, since WS-06 has no second
workstream to parallelize against. Rationale recorded as an epic decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| BB-1 | HIGH | The forwarded attack-throughput benchmark measures console logging, not resolver work |
| BB-2 | LOW | `benchmark-pages.py` "results not found" guard can be defeated by the history-merge step |
| BB-3 | LOW | `benchmark.yml` job is over-privileged |
| BB-4 | LOW | Cross-repo race on the `cuioss.github.io` push |
| BB-6 | INFO | `junit-jupiter-api` declared without an explicit test scope |
| BB-7 | INFO | The `quick` profile increases per-iteration times |
| BB-8 | INFO | `cui-http-benchmarking` will be published to Maven Central on release — **operator decision** |
| BB-9 | INFO | The release skill asserts a two-segment version line against a three-segment `current-version` |
| BB-10 | INFO | Leftover template comments in `maven.yml` and `release.yml` |

**Not covered here:** BB-5 (non-compiling examples in three `security/**` `package-info.java` files)
is the same defect as SV-15 and is owned by PLAN-04 in WS-01, per the report's own cross-reference.

## Claim Labels

- OBSERVED: these findings are stated at `source/build-and-benchmarking-review.adoc` § `BB-1`..`BB-4`
  and the `BB-6`..`BB-10` INFO bullets.
- OBSERVED: BB-1's two-in-three ratio is explained mechanically in the report — attack set 1 emits
  two warns (CR/LF outside `RFC7230_HEADER_CHARS` plus a protocol-relative context path), set 2 emits
  two (a backslash prefix plus an unparseable XFF entry), and set 3 emits ZERO because both its
  values are dropped by filters that do not log. Read at
  `source/build-and-benchmarking-review.adoc` § `BB-1`.
- OBSERVED: BB-6 is recorded as VERIFIED SAFE today — `cui-java-bom-1.5.4` manages
  `junit-jupiter-api` at test scope, so it does not leak into published compile deps. The finding is
  the inconsistency and the latent trap, not a live defect. Read at
  `source/build-and-benchmarking-review.adoc` § `BB-6`.
- OBSERVED: BB-2 is recorded as LOW LIKELIHOOD because a failing Maven step aborts the workflow
  first — the guard is ineffective as written, but the path to reaching it is narrow. Read at
  `source/build-and-benchmarking-review.adoc` § `BB-2`.
- OBSERVED: the report positively confirms the benchmark methodology is otherwise SOUND — every
  `@Benchmark` returns its computed value, state creation is `@Setup(Level.Trial)` so setup is not
  measured, the input-cycling overflow guard is correct, and `Scope.Thread` justifies the
  unsynchronized counters. ⛔ Deliverable 1 must not disturb any of this. Read at
  `source/build-and-benchmarking-review.adoc` § Verified correct / done well.
- OBSERVED: the report notes the `<jmh.include>` regex is a hardcoded allow-list currently matching
  both benchmark packages, and that a future benchmark package would be SILENTLY excluded until the
  POM regex is updated — it suggests a build-time consistency check. Read at the same section.
- HYPOTHESIS: `benchmark-logging.properties` sets `.level = WARNING` with a `ConsoleHandler` at
  `WARNING` — confirm/refute at
  `cui-http-benchmarking/src/main/resources/benchmark-logging.properties` (verify-at-outline)
- HYPOTHESIS: `benchmark-pages.py`'s `_copy_json_files` performs `dst_dir.mkdir(parents=True, …)`
  BEFORE the `if not results_dir.is_dir()` guard — confirm/refute at
  `cui-http-benchmarking/scripts/benchmark-pages.py` § `_copy_json_files` and the guard
  (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: benchmark-logging.properties still sets .level = WARNING with a ConsoleHandler at WARNING and SimpleFormatter
- HYPOTHESIS: `benchmark.yml` requests `contents: write` while its checkout sets
  `persist-credentials: false` and its pages deploy uses a GitHub App token — confirm/refute at
  `.github/workflows/benchmark.yml` § the `permissions` block, the checkout step, the deploy step
  (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: benchmark-pages.py still calls dst_dir.mkdir(parents=True) at line 46, before the results_dir.is_dir() guard
- HYPOTHESIS: the benchmarking pom sets `sonar.skip` but no publish or deploy skip — an asserted
  absence — confirm/refute at `cui-http-benchmarking/pom.xml` (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: benchmark.yml still requests job-level contents: write at line 29 while the workflow default is contents: read
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: cui-http-benchmarking/pom.xml still declares no publish or deploy skip; the asserted absence holds and BB-8 remains a live operator question
- Verify-first clause: ⛔ **BB-8 is a genuine fork with a product consequence** — whether the JMH
  benchmarking jar and its `de.cuioss.sheriff.oauth:benchmarking-common` dependency should appear on
  Maven Central. The report explicitly says "confirm this is intended". Escalate to the operator;
  do not add a publish skip on your own judgement, and do not leave it silently unaddressed either.
- Verify-first clause: deliverable 1 changes what the benchmark measures, so its published numbers
  become incomparable with the historical series in the gh-pages tree. Check whether the dashboard
  presents a continuous trend line and, if so, surface the discontinuity rather than silently
  breaking the series.

## Expected Surface

- HYPOTHESIS: `cui-http-benchmarking/src/main/resources/benchmark-logging.properties` — the level configuration (verify-at-outline)
- HYPOTHESIS: `cui-http-benchmarking/src/main/java/de/cuioss/http/forwarded/benchmark/ForwardedResolverBenchmark.java`, `ForwardedBenchmarkState.java` — the attack sets, if deliverable 1 takes the input route (verify-at-outline)
- HYPOTHESIS: `cui-http-benchmarking/scripts/benchmark-pages.py` — `_copy_json_files`, the existence guard (verify-at-outline)
- HYPOTHESIS: `.github/workflows/benchmark.yml` — the `permissions` block, the pages push step (verify-at-outline)
- HYPOTHESIS: `.github/workflows/maven.yml`, `.github/workflows/release.yml` — the leftover template comments (verify-at-outline)
- HYPOTHESIS: `cui-http-core/pom.xml` — the `junit-jupiter-api` scope (verify-at-outline)
- HYPOTHESIS: `cui-http-benchmarking/pom.xml` — the `quick` profile, the publish configuration, the `<jmh.include>` regex (verify-at-outline)
- HYPOTHESIS: `.claude/skills/release/SKILL.md` and `.github/project.yml` — the version-line assumption (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none. **Fully surface-disjoint from every other plan in this epic** — it is the only
  plan touching `.github/workflows/`, the pom trees, or the benchmarking module. If the operator
  ever raises `parallelization_scope` above 1, this is the safest plan to pair with anything.
- Overlaps with: none.
- Adjacent to: `de.cuioss.http.forwarded` (WS-02). The benchmark exercises the resolver, but BB-1's
  fix is to the LOGGING CONFIGURATION or the attack INPUTS — never to the resolver. ⛔ A resolver
  change is WS-02's; escalate rather than reaching across.
- ⛔ **`ForwardedBenchmarkState.java` was ALREADY compile-fixed by PLAN-05 (PR #155, `534d111`) —
  the WS-06 exclusion was lifted by explicit operator decision.** FW-1's accessor signature change
  (`Function<String,String>` → `Function<String,List<String>>`) necessarily broke the
  `cui-http-benchmarking` reactor compile, so the boundary could not hold. Scope was held to a
  mechanical fix; `ForwardedResolverBenchmark.java` needed no edit. **Read that file as
  already-current, not as yours to repair.** This is the first and so far only cross-workstream
  boundary crossing in this epic.
- **BB-1's measurement baseline has MOVED.** PLAN-05 self-reports one extra list traversal per
  header lookup, with scenario and fixtures unchanged. Any before/after benchmark comparison must
  be taken against `534d111`, never against `7ae6499`.
- Adjacent to: `ForwardedBenchmarkState` also constructs `ResolvedForwarding` directly, and PLAN-06
  deliverable 2 adds a validating compact constructor to that record. If PLAN-06 lands first, the
  benchmark may again fail to compile — check before scoping; fixing that construction is
  legitimately in scope here since the benchmark module is this plan's surface.
- ⛔ **`.github/workflows/pr-agent.yml` now EXISTS** — added by PLAN-14's PR #153 outside its
  declared surface, with operator approval. It is a fourth workflow file this plan's BB-10
  template-comment sweep should expect.
- Adjacent to: `cui-http-core/src/main/java/de/cuioss/http/security/**` — BB-5 points there. ⛔ Do NOT
  edit it; PLAN-04 owns it.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-16-benchmark-and-ci-hygiene.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

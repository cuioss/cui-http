# PLAN-10: Attack Test Mechanism Correctness and Generator Determinism, Reach and Contract Tests

epic: quality-report-remediation
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-10-attack-test-mechanism-correctness.md` (filename retained across the
> merge below so the epic's `status.json` row needs no rename) and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

> ⛔ **REDISTRIBUTED 2026-09-15 at `d385aa3` (cleanup pass).** This spec MERGES the formerly
> separate `PLAN-10-attack-test-mechanism-correctness.md` (its own prior content, preserved below)
> and `PLAN-11-generator-determinism-and-coverage.md`. Both were already declared strictly
> sequential ("Sequenced PLAN-10 first; PLAN-11 re-reads both at its own outline") and both
> overlap at `cui-http-core/src/test/java/de/cuioss/http/security/database/` plus two named
> generator files (`ProtocolHandlerAttackGenerator.java`, `HttpHeaderInjectionAttackGenerator.java`)
> — the component-first, task-second grouping rule (both plans work the whole
> `security/tests` + `security/generators` + `security/database` test tree, and neither touches
> production code) makes one plan the correct unit. The absorbed spec's file is retired to a
> pointer at `plans/PLAN-11-generator-determinism-and-coverage.md`; its queue row (`PLAN-11`) is
> transitioned to `parked` with this plan named as the merge destination. Deliverables 1-5 are the
> former PLAN-10's deliverables 1-5 verbatim; deliverables 6-12 are the former PLAN-11's
> deliverables 1-7 verbatim. Former PLAN-10 deliverable 6 ("Regression discipline for the whole
> suite") is NOT a thirteenth deliverable here — it never produced its own artifact, it is a
> blanket assertion-quality rule binding every deliverable above, so it is folded into Execution
> Constraints below where PLAN-10 already carried an equivalent rule
> ("Assert the exact `UrlSecurityFailureType`"). That fold is what keeps the merged count at 12
> rather than 13, and it is not a weak merge: the coupling was already load-bearing in both source
> specs' own Dependencies sections, and the fold loses no deliverable — it relocates a rule that
> was never itself a deliverable.

## Objective

Make the `security/tests` and `security/generators` integration and generator layers test what
their class names say, in one pass. Today the header injection, request smuggling and URL-length
classes feed URL-shaped payloads (`https://host/path?name=attack`) through
`URLPathValidationPipeline`, which runs `CharacterValidationStage` *before* `DecodingStage` against
a path character set containing neither `?` nor `#` nor space — so every one of those payloads is
rejected on the literal `?` before the CRLF, smuggling or length mechanism under test ever runs.
Seven further methods accept either outcome and cannot fail; eleven helpers assert "any of 6-10 of
the 25 failure types" while being named as exact assertions; and the protocol-handler database
exercises protocol-handler detection in 1 of its 24 entries because the other 23 contain `../`.
In the other direction, most attack "generators" wrap a fixed literal list and hand it to
`FixedValuesGenerator`, whose `next()` draws a non-reproducible random index with no
`@GeneratorSeed` anywhere in the tree — so a 15-entry list drawn N times skips entries on most
runs; and the "valid" generators stay inside the smallest accept set, so `ValidCookieGenerator`
emits only `[A-Za-z0-9_]` and a production false positive on an RFC-legal base64 cookie value is
invisible to the suite. Eleven generators have no contract test, five traversal generators
overlap, one test file sits under the wrong directory, and `AllGeneratorsIntegrationTest` exercises
10 of the 34 generators with tautological assertions. **This plan changes no production code.**
Every change is driven by a test that is first seen to fail — here that means seeing the
*corrected* test fail against the mechanism it now actually reaches, or the *widened* generator
fail against the pipeline it is now valid for.

## Deliverables

1. **Point each payload at the surface that receives it (F-E-1).** Header-injection payloads go to
   `HTTPHeaderValidationPipeline` with `HEADER_VALUE` / `HEADER_NAME`; query-shaped payloads go to
   the parameter pipelines; only true path payloads go to `URLPathValidationPipeline`. Each
   corrected test must be seen to fail on the mechanism (CRLF rejection, length limit, smuggling
   pattern) rather than on `INVALID_CHARACTER` for a `?`.
2. **Every test carries an expected verdict it can fail against (F-E-2).** Seven methods across
   `EncodedPathTraversalAttackTest`, `DoubleEncodingAttackTest`, `NullBytePathTraversalAttackTest`
   and `CookieChaosAttackTest` catch `UrlSecurityException` with no assertion or a tautological
   one, or aggregate into an `assertAll` that cannot fail. Give each an exact expected outcome.
   `CookieChaosAttackTest`'s Javadoc for two of them claims the prefix rules are "not yet
   implemented" while `CookiePrefixValidationStage.validateCookie` (now at line 325, reached
   through the `SecurityPrefix` enum at line 193 and `validatePrefix` at line 391 — PLAN-05 (#231)
   replaced the originally-cited `validateHostPrefix` / `validateSecurePrefix` methods with these)
   demonstrably exist — correct the Javadoc as part of the fix.
3. **Failure-type assertions become exact (F-E-3).** `isURLLengthLimitSpecificFailure` and its ten
   siblings accept a set of 6-10 enum constants — `URLLengthLimitAttackTest`'s helper accepts ten,
   including `INVALID_CHARACTER` with the comment "Repeated chars in length attacks". Assert the
   single failure type each case must produce. Note the enum has **25** constants, not the 24 the
   original report states.
4. **The protocol-handler database tests protocol handlers (F-E-4).** 23 of the 24 entries in
   `ProtocolHandlerAttackDatabase` (16 `PATH_TRAVERSAL_DETECTED`, 5 `INVALID_CHARACTER`, 1
   `NULL_BYTE_INJECTION`, 1 `CONTROL_CHARACTERS`, 1 `SUSPICIOUS_PATTERN_DETECTED`) are decided by
   a mechanism other than protocol-handler detection, and every branch of
   `ProtocolHandlerAttackGenerator.generatePath()` contains `../` or `/..`. Produce payloads whose
   only rejectable property is the protocol handler.
5. **Attack-database rationales name the real rejection cause (F-E-11, F-E-10).**
   `IDNAttackDatabase`'s `CYRILLIC_APPLE_HOMOGRAPH` rationale credits the traversal sequence, but
   `CharacterValidationStage.isCharacterAllowed` returns false for any codepoint above 255 on
   `URL_PATH`, so the Cyrillic character triggers `INVALID_CHARACTER` before `PatternMatchingStage`
   runs; `ApacheCVEAttackDatabase`'s `CVE_2020_1927_MOD_REWRITE` payload is rejected on a literal
   `?`, not on its `..%2f`. Correct the rationales. Also remove the surviving local NFKC-fold
   assertion in `UnicodeAttackGeneratorTest` that ADR-0010 says was to be deleted, and extend the
   ADR-0009 structural-claim tests beyond the 3 of 10 attack databases they cover today.
6. **Fixed lists are iterated, not sampled (F-E-5).** Convert every generator whose value space is
   a fixed literal list from a random draw to deterministic iteration over the whole list, so
   every entry is exercised on every run. Where a generator must stay random, pin the seed via
   `@GeneratorSeed` so a failure is reproducible.
7. **Valid generators widen to the full legal set (F-E-6).** `ValidCookieGenerator.generateAlphanumericValue`
   uses `letterStrings(...).toUpperCase()` and emits only `[A-Za-z0-9_]`, while
   `getCharacterSet(COOKIE_VALUE)` maps to `RFC3986_UNRESERVED` — so an RFC 6265 `cookie-octet`
   value is unreachable and the resulting production false positive is undetectable. Widen it, and
   fix the two generators that are not valid for any pipeline: `ValidURLGenerator` appends
   `?page=..&sort=..` query strings to values fed to a path pipeline, and
   `ValidHTTPHeaderValueGenerator` emits Fetch request-modes (`same-origin`, `cors`, `no-cors`) as
   `Origin` header values.
8. **Overlaps retired and misplaced consumers corrected (F-E-7).**
   `URLLengthLimitAttackGeneratorTest` lives under `generators/injection/` while its subject
   `URLLengthLimitAttackGenerator` lives under `generators/url/`; `PathTraversalParameterGenerator`
   documents itself as built for `URLParameterValidationPipeline` yet
   `EncodedPathTraversalAttackTest.shouldBlockUTF8OverlongPatterns` feeds it through
   `URLPathValidationPipeline`. Move the test, correct the consumer, and consolidate the five
   overlapping traversal generators.
9. **`SupportedValidationTypeGenerator` covers every supported type (F-E-13).** It emits only
   `URL_PATH`, `PARAMETER_VALUE`, `HEADER_NAME` and `HEADER_VALUE` via an `integers(1,4)` switch,
   omitting `PARAMETER_NAME`, which `PipelineFactory.createPipeline` explicitly supports.
10. **`AllGeneratorsIntegrationTest` becomes a real integration test (F-E-14, F-E-12, F-E-16).** It
    instantiates 10 of the 34 generator files and asserts tautologies —
    `anyMatch(s -> !s.isEmpty())` for "has Unicode attack", `length() > 100 || isEmpty()` for a
    length claim. Cover every generator and assert real properties. Remove
    `AttackCookieGenerator`'s empty-name arm, which no validator can reject because
    `CharacterValidationStage.validate` returns immediately for an empty string. Replace
    `URLLengthLimitAttackTest`'s `Math.abs(this.hashCode()) % bound` selection, which is not
    reproducible despite any seed claim, and drop the redundant `assertNotNull` that follows
    `assertTrue(result.isPresent())`.
11. **Contract tests for the eleven generators that have none (F-E-15).** The four `CookieName*`
    generators, `UnicodeControlCharacterAttackGenerator`, `UnicodeNormalizationAttackGenerator`,
    `HttpHeaderInjectionAttackGenerator`, `ValidHTTPHeaderValueGenerator`,
    `ProtocolHandlerAttackGenerator`, `NullByteInjectionParameterGenerator` and
    `ValidURLParameterStringGenerator` have no sibling `*Test.java`. Add one each, asserting family
    reachability and a pipeline round-trip — not substring presence, which is all
    `DoubleEncodingAttackGeneratorTest` does today.
12. **Generator usage in the suites that have none (F-A-13, F-D-19, F-client-18 test halves).** Six
    of nine stage tests, the whole forwarded package, and the client package outside
    `HttpResultTest` use no `TypedGenerator` at all. Introduce it where it adds reach.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that corroborated 16 of 16 findings in `04-test-framework.adoc` with 0
contradicted and 0 unverifiable, re-deriving each quoted count independently. Re-checked at
`ca74911` and again at `d385aa3` (this cleanup pass) — no file this plan cites moved between
`ca74911` and `d385aa3` (the diff over that range touches only CI workflow YAML, `pom.xml`'s
parent version and `.plan/marshal.json`, none of which is on this plan's declared surface).

**From the former PLAN-10 (attack test mechanism):**

- OBSERVED: `URLPathValidationPipeline.createStages` runs `CharacterValidationStage` before
  `DecodingStage` — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java`
  (lines 128-134 as of the `ca74911` re-read; originally cited 127-134). `RFC3986_PATH_CHARS`
  contains neither `?`, `#`, nor space — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationConstants.java`
  (lines 167-175).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged since the ca74911 re-read. Carried forward from the redistributed PLAN-10.
- OBSERVED: every branch of `HttpHeaderInjectionAttackGenerator.next()` appends `"?…=" + attack`
  to an absolute base URL — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/header/HttpHeaderInjectionAttackGenerator.java`
  (lines 77-120) — and `HttpHeaderInjectionAttackTest` feeds those through
  `URLPathValidationPipeline` — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/tests/HttpHeaderInjectionAttackTest.java`
  (lines 102-159).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; the cited artifact is byte-identical to the ca74911 reading. Carried forward from the redistributed PLAN-10.
- OBSERVED: all seven cited vacuous-test locations read verbatim as the report quotes them,
  including the aggregate-only `assertAll` — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/tests/` §
  `EncodedPathTraversalAttackTest`, `DoubleEncodingAttackTest`, `NullBytePathTraversalAttackTest`,
  `CookieChaosAttackTest` (lines 246-289).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-10.
- OBSERVED (re-scoped at the prior cleanup, `ca74911`): `CookiePrefixValidationStage.validateCookie`
  (line **325**) reaches prefix validation through a private `SecurityPrefix` enum (**193**) and
  one `validatePrefix` method (**391**), refuting the "not yet implemented" Javadoc on
  `CookieChaosAttackTest` tests #5 and #6 — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CookiePrefixValidationStage.java`.
  The original claim cited `validateHostPrefix` and `validateSecurePrefix` at lines 197/256/301;
  PLAN-05 (#231) deleted both methods and replaced them with the enum. The substance held, the
  symbols did not; deliverable 2 above already cites the corrected symbols.
  - verdict: contradicted | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Re-confirmed at d385aa3: still refers to the corrected symbols (validateCookie 325, validatePrefix 391); no further movement since ca74911.
- OBSERVED: `isURLLengthLimitSpecificFailure` accepts exactly ten enum constants —
  `INPUT_TOO_LONG`, `PATH_TOO_LONG`, `EXCESSIVE_NESTING`, `MALFORMED_INPUT`, `INVALID_STRUCTURE`,
  `SUSPICIOUS_PATTERN_DETECTED`, `PROTOCOL_VIOLATION`, `RFC_VIOLATION`, `INVALID_CHARACTER`,
  `PATH_TRAVERSAL_DETECTED` — with the `INVALID_CHARACTER` comment at line 665 — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/tests/URLLengthLimitAttackTest.java`
  (lines 654-667).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-10.
- OBSERVED (derived count, corrected): `UrlSecurityFailureType` declares **25** constants, not the
  24 that both the original report and its adversarial verification state — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/core/UrlSecurityFailureType.java`. This
  does not change any verdict; it corrects F-E-3's framing.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-10.
- OBSERVED (derived count, re-derived): `ProtocolHandlerAttackDatabase`'s 24 entries break down as
  16 `PATH_TRAVERSAL_DETECTED`, 5 `INVALID_CHARACTER`, 1 `NULL_BYTE_INJECTION`, 1
  `CONTROL_CHARACTERS`, 1 `SUSPICIOUS_PATTERN_DETECTED` — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/database/ProtocolHandlerAttackDatabase.java`
  (lines 59-234); every branch of `ProtocolHandlerAttackGenerator.generatePath()` contains `../`
  or `/..` — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/injection/ProtocolHandlerAttackGenerator.java`
  (lines 339-348).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-10.
- OBSERVED: `CharacterValidationStage.isCharacterAllowed` always returns false for codepoints
  above 255 on `URL_PATH` via `supportsUnicodeCharacters()` (line 482 as of the `ca74911` re-read,
  originally cited 371-380 — the cited window now holds the null-byte and C0 branches PLAN-02
  added) — so `IDNAttackDatabase`'s `CYRILLIC_APPLE_HOMOGRAPH` rationale at line 54 names the
  wrong cause; `ApacheCVEAttackDatabase`'s `CVE_2020_1927_MOD_REWRITE` payload contains a literal
  `?` rejected first — read at `.../database/ApacheCVEAttackDatabase.java` (lines 119-125).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged since the ca74911 anchor correction. Carried forward from the redistributed PLAN-10.
- OBSERVED: the local NFKC-fold assertion survives in
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/UnicodeAttackGeneratorTest.java`
  (lines 85-95) despite ADR-0010 line 31 stating it was to be removed; `doc/adr/0009-*.adoc` and
  `doc/adr/0010-*.adoc` both read `Status: Proposed` at line 15.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-10.
- HYPOTHESIS: several of this plan's corrected tests will fail against production code that WS-01
  and WS-02 are simultaneously changing — a header-injection payload correctly routed to
  `HTTPHeaderValidationPipeline` exercises the very C0-control rule PLAN-02 is rewriting.
  Confirm/refute by re-reading `HTTPHeaderValidationPipeline.createStages` and
  `CharacterValidationStage.isCharacterAllowed` at the HEAD this plan starts from (verify-at-outline).
  - verdict: unverifiable | checked_at: 2e9e8e0fa8b6dc7058205d3be973c55dafbe0e7f | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable via static inspection at 2e9e8e0, and the population has grown further: WS-01 through WS-04 are now ALL fully landed (PLAN-01 #210, PLAN-02 #217, PLAN-03 #222, PLAN-15 #229, PLAN-04 #227, PLAN-05 #231, PLAN-06 #214, PLAN-07 #237, PLAN-08 #240), plus the new WS-01 addendum PLAN-16 (#239, DecodingStage CR/LF option). PLAN-16 in particular touched DecodingStageTest.java, which sits inside this plan's own declared test/.../validation/ directory - re-read it explicitly at outline, not just the WS-01/WS-02 files this claim originally named. The outline-time re-read is now mandatory across a strictly larger set than when this claim was last written.
- Verify-first clause: before scoping deliverable 2, re-read every `CookieChaosAttackTest` method
  this plan is to fix at the then-current HEAD. PLAN-05 changed cookie prefix matching to
  case-insensitive and added value validation. A refutation — the method's premise is already
  gone — means the test is rewritten from scratch, not repaired.
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at d385aa3; unchanged in kind.

**From the former PLAN-11 (generator determinism and coverage):**

- OBSERVED: `FixedValuesGenerator.next()` is `values.get(RandomContext.random().nextInt(size))`
  and `RandomContext.initSeed()` calls `random.nextLong()` then `setSeed(...)`, giving a fresh
  non-deterministic seed absent `@GeneratorSeed` or the seed system property — read by
  disassembling `cui-test-generator-3.0.2.jar`. No `@GeneratorSeed` occurs anywhere in the tree.
  An example fixed list is the 15-literal `nullByteURLs` field — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/url/NullByteURLGenerator.java`
  (lines 63-79).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; the generator, database or test file this claim cites is byte-identical since ca74911. Carried forward from the redistributed PLAN-11.
- OBSERVED: `ValidCookieGenerator.generateAlphanumericValue` emits only `[A-Za-z0-9_]` — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/cookie/ValidCookieGenerator.java`
  (lines 228-231) — while `getCharacterSet(COOKIE_VALUE)` maps to `RFC3986_UNRESERVED`, which
  excludes `+`, `/` and `=`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-11.
- OBSERVED: `ValidURLGenerator` appends `?page=..&sort=..` and emits `/search?q=test&limit=10`
  directly — read at `.../generators/url/ValidURLGenerator.java` (lines 43-70);
  `ValidHTTPHeaderValueGenerator` emits `same-origin` / `cors` / `no-cors` as `Origin` values —
  read at `.../generators/header/ValidHTTPHeaderValueGenerator.java` (lines 245-252).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-11.
- OBSERVED: `URLLengthLimitAttackGeneratorTest.java` sits under `generators/injection/` while
  `URLLengthLimitAttackGenerator.java` sits under `generators/url/`, confirmed by direct file
  listing; `doc/test-framework-structure.adoc`:33 mis-describes `generators/injection` as holding
  length-limit attacks; `PathTraversalParameterGenerator` documents itself for
  `URLParameterValidationPipeline` at lines 26-27 while
  `EncodedPathTraversalAttackTest.shouldBlockUTF8OverlongPatterns` feeds it through
  `URLPathValidationPipeline` at lines 102-111.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-11.
- OBSERVED: `SupportedValidationTypeGenerator` emits four types via an `integers(1,4)` switch and
  omits `PARAMETER_NAME` — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/SupportedValidationTypeGenerator.java`
  (lines 36-42) — while `PipelineFactory.createPipeline` carries an explicit
  `case PARAMETER_NAME` at line 252.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-11.
- OBSERVED (derived count, re-derived at the prior cleanup, `ca74911`): **34** `*Generator.java`
  files under `cui-http-core/src/test`, not the 32 originally stated — read by `find`.
  `AllGeneratorsIntegrationTest` instantiates 10 of them; its `hasUnicodeAttack = …anyMatch(s ->
  !s.isEmpty())` is at line 158 and the `length() > 100 || isEmpty()` claim at line 159 — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/AllGeneratorsIntegrationTest.java`
  (lines 52-90, 158-159). ⛔ **Re-derive again at outline** — any plan's test authoring moves this
  count, and this cleanup pass did not re-run `find` since no generator file moved on this plan's
  declared surface between `ca74911` and `d385aa3` (confirmed via `git diff --stat`).
  - verdict: contradicted | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: The count was already re-scoped to 34 at ca74911 (from the original report's 32); re-confirmed unmoved at d385aa3 since no *Generator.java file changed in that range. Still mandatory to re-derive at outline per the field's own rule for derived counts.
- OBSERVED: `AttackCookieGenerator`'s empty-name arm is at line 62, and
  `CharacterValidationStage.validate` returns `Optional.of(value)` immediately for an empty string
  without running `validateCharacters` (lines 180-181), so an empty cookie name is trivially
  accepted — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/generators/cookie/AttackCookieGenerator.java`
  (lines 59-99) and `CharacterValidationStage.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-11.
- OBSERVED: `Math.abs(this.hashCode()) % bound` appears at lines 672-674 and again at line 757 —
  read at `cui-http-core/src/test/java/de/cuioss/http/security/tests/URLLengthLimitAttackTest.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-11.
- OBSERVED (derived, re-derived by file-existence check): all eleven named generators have no
  sibling `*Test.java`; `DoubleEncodingAttackGeneratorTest` (lines 31-48) asserts substring
  presence only, with no family-reachability or pipeline round-trip.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at d385aa3; unchanged. Carried forward from the redistributed PLAN-11.
- OBSERVED (re-scoped at the prior cleanup, `ca74911`): `EncodingCombinationGenerator.applyMixedCase`
  no longer uppercases only the literal `%2e` / `%2f` escapes. PLAN-04 (#227) rewrote it (now line
  118) to randomise the case of every hex digit of every `%XX` escape from the seeded source — its
  own in-code comment records that the previous two targeted replacements left every other escape,
  `%25` above all, lowercase. **The defect this claim originally scoped is already fixed.** No
  deliverable above rests on the original framing; this claim is retained as background context
  for the outline-time re-read of `EncodingCombinationGenerator.java` that deliverable 8's overlap
  note requires, and needs no further action from this cleanup pass.
  - verdict: contradicted | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Re-confirmed at d385aa3: EncodingCombinationGenerator.java has not moved since PLAN-04 (#227) landed; the fix stands and no deliverable rests on the retired framing.
- HYPOTHESIS: deliverable 6's conversion from sampling to iteration multiplies the executed test
  count and may push the suite past its acceptable runtime. Confirm/refute by reading the current
  `@TypeGeneratorSource(count = N)` values across `security/tests` and computing the new total
  before converting (verify-at-outline). If the total is unacceptable, the fix is a pinned seed
  plus a nightly exhaustive profile rather than unconditional iteration.
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at d385aa3: the executed-test-count multiplication cannot be measured without running the converted suite.
- Verify-first clause: before scoping deliverable 7, re-read
  `CharacterValidationConstants.getCharacterSet` at the then-current HEAD. WS-01 PLAN-02 widened
  the cookie character set to RFC 6265 `cookie-octet`; since it has landed, the false positive
  this deliverable exists to expose is already gone and the deliverable narrows to widening the
  generator so the *fix* is covered.
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable via static inspection alone at d385aa3, though PLAN-02 (#217) is confirmed landed — the outline-time re-read remains mandatory to confirm the narrowed scope.

## Expected Surface

- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/tests/` — the whole integration-test package, in particular `HttpHeaderInjectionAttackTest.java`, `HttpRequestSmugglingAttackTest.java`, `URLLengthLimitAttackTest.java`, `EncodedPathTraversalAttackTest.java`, `DoubleEncodingAttackTest.java`, `NullBytePathTraversalAttackTest.java`, `CookieChaosAttackTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/database/` — `ProtocolHandlerAttackDatabase.java`, `IDNAttackDatabase.java`, `ApacheCVEAttackDatabase.java` and the fixed-list databases the generators draw from
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/` — the whole generator tree (34 generator files and their contract tests, re-derive at outline)
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/injection/ProtocolHandlerAttackGenerator.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/header/HttpHeaderInjectionAttackGenerator.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/UnicodeAttackGeneratorTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/` — the stage tests, for deliverable 12's generator introduction
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/forwarded/` — for deliverable 12's generator introduction
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/client/` — for deliverable 12's generator introduction

⛔ This plan changes **no production code**. If a corrected test cannot be given a real expected
verdict, or a widened generator reveals a production false positive, because the production
behaviour is itself wrong, file an inbox message naming the class and the behaviour — that is a
WS-01..WS-04 deliverable, not this plan's. This plan also does not edit any file under `doc/`: the
ADR `Status: Proposed` items and the stale analysis-document counts are owned by WS-06 PLAN-13.

⛔ **WS-01 and WS-02 have fully landed since this spec was first staged** (PLAN-02 #217, PLAN-03
#222, PLAN-04 #227, PLAN-05 #231, PLAN-15 #229). Re-read every cited production symbol at outline:
the character sets, the C0/C1 rules, the cookie prefix implementation and the exception detail
rendering have all moved. `CookieChaosAttackTest.java`'s three stale passages are at **60-66,
240-244 and 300-303** (one line earlier than the original citations); all three are prose and
PLAN-05 did not edit the file.

## Dependencies and Sequencing

- Depends on: WS-01 PLAN-01, PLAN-02, PLAN-03 and WS-02 PLAN-04, PLAN-05, PLAN-15 — the entire
  security code surface (all landed). Every test this plan gives a real expected verdict, and
  every generator it widens, asserts or reaches behaviour those plans changed; running after them
  guarantees the re-reads land against final production code.
- Overlaps with: none remaining in the epic. `ProtocolHandlerAttackGenerator.java`,
  `HttpHeaderInjectionAttackGenerator.java` and `security/database/` — the files the two merged
  source plans overlapped at — are now owned entirely by this one plan.
- Adjacent to: `cui-http-core/src/test/java/de/cuioss/http/security/generators/encoding/EncodingCombinationGenerator.java`,
  which PLAN-04 touched for its own deliverable 6 and this plan does not edit (re-read only, per
  the retired claim above).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-10-attack-test-mechanism-correctness.md"
```

## Execution Constraints

- **TDD applies here in its test-repair and contract-test forms.** For each corrected test: make
  the correction, observe it FAIL for the mechanism-specific reason (not for the old
  `?`-rejection reason), then make it pass — either because the production code already handles
  the mechanism, or by filing an inbox message when it does not. For each new contract test
  (deliverable 11): write it so it FAILS against the generator's current reach, then widen the
  generator until it passes. A test that passes on the first run has not been shown to exercise
  the new mechanism or reach, and must be strengthened until it can fail.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction. ADR high-water
  as of this cleanup pass (`d385aa3`) is **0022**.
- **Regression discipline for the whole suite (folded from former PLAN-10 deliverable 6, now a
  blanket rule rather than its own deliverable).** No method this plan touches may end in a bare
  `assertNotNull`, a bare `assertTrue(result.isPresent())`, or an assertion that holds under both
  the accept and the reject branch. Assert the exact `UrlSecurityFailureType`; deliverable 3
  exists precisely to eliminate set-membership assertions, and no new one may be introduced
  anywhere else in this plan either.
- Record, in the PR body, the count of test methods that changed verdict as a result of
  deliverables 1-5 (the report's central claim is that these classes established nothing) and the
  before/after number of distinct generated values each converted generator reaches from
  deliverables 6-12 — both counts are the evidence these two halves of the plan worked.
- **Twelve deliverables is at the epic's raised ceiling (12), not over it.** The operator directed
  this redistribution explicitly: merge the two WS-05 plans rather than keep them as two
  dependency-linked PRs. The count is re-derived, not summed — former PLAN-10 deliverable 6 was
  folded into this section rather than kept as a numbered deliverable.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

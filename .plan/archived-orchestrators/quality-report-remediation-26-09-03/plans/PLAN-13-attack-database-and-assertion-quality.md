# PLAN-13: Attack Database and Assertion Quality

epic: quality-report-remediation
workstream: WS-04

## Objective

Make the attack databases test what their names claim, and close the assertion and coverage gaps in
the pipeline and stage tests. `ModSecurityCRSAttackDatabase` has an entry named CRLF_INJECTION whose
payload is a null byte — semantically duplicating the adjacent NULL_BYTE entry — so there is NO
CRLF-injection test in that database despite the name; an entry named for a semicolon and command
injection whose payload contains neither; and four further entries that reduce to a bare `%00` or a
plain `../`, so the smuggling, header-injection, RFI and protocol behaviours its class-level "CRS
Rule Categories Covered" list advertises are never distinguished from generic detection. Alongside
this: a wall-clock upper-bound assertion that is flaky by construction, and several tests that assert
weaker properties than their own siblings already demonstrate are achievable.

## Deliverables

1. **Fix the mislabeled and duplicated `ModSecurityCRSAttackDatabase` entries** — supply payloads
   that actually exercise the named category, or rename the entries to reflect what they test. The
   CRLF_INJECTION, PATH_SEMICOLON, CHUNKED_SMUGGLING, HEADER_INJECTION, SMUGGLING_PREFIX and
   PROTOCOL_HANDLER entries are all named. Reconcile the class-level category list with whichever
   route is taken. (TQ-2)
2. **Remove the flaky wall-clock assertion** in `EdgeCaseValidURLsDatabaseTest.shouldProcessEdgeCasesEfficiently`,
   which asserts a single validation completes in under 100 ms — first-call class loading, JIT, or a
   GC pause on a loaded CI runner can exceed that for the first parameterized case. Assert behaviour,
   not latency, or make it a non-failing benchmark. Also review the lower-bound timing assertion in
   `ResilientHttpAdapterTest`, which is far safer but still time-coupled. (TQ-3, TQ-10)
3. **Strengthen the weak attack assertions** — several tests assert only
   `assertThrows(UrlSecurityException.class, …)` where a typed `UrlSecurityFailureType` assertion is
   feasible AND is already used by an adjacent test in the same class, making them the weaker
   duplicates of their own siblings. (TQ-5)
4. **Assert the returned VALUE, not merely its presence, on valid input** — two of the three main
   pipeline test classes check only `isPresent()` plus `assertNotNull(result.get())`, so a pipeline
   that MANGLED a legitimate path would still pass. `HTTPHeaderValidationPipelineTest` already does
   `assertEquals` correctly and is the model. (TQ-6)
5. **Close the character-stage and database coverage gaps** — add a supplementary-plane and a
   lone-surrogate INPUT test for `CharacterValidationStage` (it tests BMP CJK rejection but never a
   code point above the BMP or a lone `\uD800`, and the only surrogate test lives in the length
   stage); add an "exactly at `maxPathLength` passes" test at the PIPELINE level (only the stage-level
   test has the at-limit case); and address the IPv6 and IDN/Homograph databases, whose 24 and 22
   entries all collapse to `INVALID_CHARACTER` on brackets or non-ASCII, so the IPv4-mapped-bypass,
   zone-ID-injection, compression-abuse and homograph-script distinctions they claim are never
   independently verified. (TQ-8, TQ-7)
6. **Document the CVE-database short-circuit honestly and drop the cosmetic wrappers** — many
   Apache/Nginx CVE cases are rejected by a disallowed character before the traversal or encoding
   logic they name is reached, so the named CVE bypass is often not the thing under test. The
   entries' own rationale strings already say so; make the class-level documentation say it too.
   Remove the `assertDoesNotThrow` wrappers around meaningful `assertEquals` calls. (TQ-12, TQ-11)

7. **Correct the false NFKC claim on the U+2044 test payload, and add the executable check that
   would have caught all three rounds of it** (folded 2026-08-29 — the residue of PR #175). At
   `cui-http-core/src/test/java/de/cuioss/http/security/tests/UnicodeNormalizationAttackTest.java:181`:

   ```java
   "\u2024\u2024\u2044",  // Dot leaders + fraction slash (../)
   ```

   ⛔ **The `(../)` claim is FALSE.** `NFKC("\u2024\u2024\u2044")` is `..\u2044`, not `../` —
   U+2044 FRACTION SLASH has no compatibility decomposition and normalizes to itself, exactly as
   U+2215 DIVISION SLASH does. Verified by direct normalization at `0b6dd71`.

   ⛔ **The PAYLOAD is fine and must NOT be changed** — it is rejection test data, and an
   unnormalizing lookalike is a legitimate thing to assert rejection of. **Only the comment is
   wrong.** Contrast the neighbour four lines down, which is already correct and is the model:

   ```java
   "\uFF0E\uFF0E\u2044",  // Fullwidth full stops (NFKC-fold to '.') + fraction slash
   ```

   It asserts the fold only for U+FF0E — which genuinely does fold — and merely NAMES the fraction
   slash without claiming anything about it.

   **Also add the executable invariant**, which is the durable half of this deliverable: for every
   codepoint any comment or Javadoc in the security test tree claims NFKC-folds, assert
   `NFKC(cp) != cp`. ⛔ **This is what makes the fix stick.** The same defect class has now escaped
   THREE times because each sweep was scoped to the wrong thing — PLAN-12 scoped by LOCATION
   (fixed the constant, missed the prose one line above), PR #175 scoped by CODEPOINT (searched
   `2215`, so could not see `2044`). The invariant that needs sweeping is **the CLAIM**, and only an
   executable assertion can hold it: #171's assertion iterates signature data and structurally
   cannot read prose.

⛔ **SEVEN deliverables — PAST the split guard, unsplit by explicit operator decision (2026-08-29).**
The original six carry this rationale: one coherent "make the tests assert what they claim" sweep
over a single test tree, where deliverables 1, 5 and 6 all touch the database classes while 3 and 4
touch the pipeline tests those databases feed; a split would separate a database's payload from the
assertion that consumes it. **Deliverable 7 fits that rationale rather than straining it** — it is
literally a "make the tests assert what they claim" correction in the same test tree, and its
executable invariant generalises deliverables 3 and 4's own theme. The operator directed the residue
into an upcoming plan rather than a running one.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| TQ-2 | MEDIUM | ModSecurity CRS database has mislabeled/duplicated entries; advertised rule categories are never exercised |
| TQ-3 | MEDIUM | A timing-based assertion is flaky |
| TQ-5 | LOW | Some attack tests assert only the exception type, not the failure type |
| TQ-6 | LOW | Two of the three main pipeline test classes assert only presence, not value, on valid input |
| TQ-7 | LOW | IPv6 and IDN/Homograph databases collapse to a single trivial rejection reason |
| TQ-8 | LOW | Character-stage edge-case gaps |
| TQ-10 | LOW | Lower-bound timing assertion in a retry test |
| TQ-11 | INFO | Cosmetic `assertDoesNotThrow` wrappers |
| TQ-12 | INFO | CVE databases largely test the character-validation short-circuit rather than the CVE mechanism |
| TQ-13 | INFO | **No action.** Payload correctness spot-checks PASSED — no incorrectly-encoded payloads were found. Named so its absence is not read as an oversight. |
| TQ-14 | INFO | **No action.** Every database is wired into an integration test (verified); `LegitimatePatternDatabase` is an abstract base, correctly without a direct test. |

**Not covered here:** TQ-9 (stale test-framework inventory documentation) is the same finding as
DOC-8 and is owned by PLAN-15 in WS-05, per the report's own cross-reference.

## Claim Labels

- OBSERVED: these findings are stated at `source/test-quality-review.adoc` § `TQ-2`, `TQ-3`, `TQ-5`,
  `TQ-6`, `TQ-7`, `TQ-8`, `TQ-10`, and the `TQ-11`..`TQ-14` INFO bullets.
- OBSERVED: TQ-9 explicitly cross-references DOC-8, and DOC-8 cross-references TQ-9 — the reports
  themselves identify these as one finding. Read at `source/test-quality-review.adoc` § `TQ-9` and
  `source/documentation-review.adoc` § `DOC-8`.
- OBSERVED: TQ-7 records that the databases' own rationale strings are HONEST about the collapse —
  the defect is that the databases test far less than their names and Javadoc advertise, not that
  they lie about it in the rationale. Read at `source/test-quality-review.adoc` § `TQ-7`.
- OBSERVED: TQ-12 gives one worked example (`CVE_2020_1927_MOD_REWRITE`, rejected because `?` is not
  in `RFC3986_PATH_CHARS` in a `URL_PATH` context) and states explicitly that the mechanism varies
  per payload while the point is general. Read at `source/test-quality-review.adoc` § `TQ-12`.
- OBSERVED: the report positively confirms the bulk of the suite is free of flakiness — no
  `Thread.sleep`, correct local MockWebServer usage, `invokeAll`+`Future.get()` concurrency tests
  with exact output-count assertions, no order-dependent coupling. TQ-3 and TQ-10 are the two
  exceptions. Read at `source/test-quality-review.adoc` § Verified correct / done well.
- HYPOTHESIS: `ModSecurityCRSAttackDatabase`'s `CRS_920100_CRLF_INJECTION` carries a null-byte
  payload and `CRS_932100_PATH_SEMICOLON` carries a plain traversal payload — confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/database/ModSecurityCRSAttackDatabase.java`
  § those constants (verify-at-outline)
- HYPOTHESIS: there is NO CRLF-injection test in that database — an asserted absence, verified as a
  presence — confirm/refute by reading every entry in the same file (verify-at-outline)
  - verdict: corroborated | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 6ee0c7c after PLAN-12 shipped the test tree: CRS_920100_CRLF_INJECTION at ModSecurityCRSAttackDatabase.java:104-105 still carries the null-byte payload /page%00attack, not a CRLF payload
- HYPOTHESIS: `EdgeCaseValidURLsDatabaseTest.shouldProcessEdgeCasesEfficiently` asserts
  `elapsedMs < 100` — confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/tests/EdgeCaseValidURLsDatabaseTest.java`
  § that method (verify-at-outline)
- HYPOTHESIS: all 24 `IPv6AttackDatabase` entries and all 22 `HomographAttackDatabase` entries expect
  `INVALID_CHARACTER` — a derived COUNT the orchestrator carries from the report, not one it verified
  — confirm/refute by counting entries in both database classes (verify-at-outline)
  - verdict: corroborated | checked_at: 6ee0c7c | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 6ee0c7c: EdgeCaseValidURLsDatabaseTest.java:133 still asserts elapsedMs < 100, a wall-clock bound
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: ⛔ deliverable 1's "supply payloads that actually exercise the named category"
  route will produce inputs the pipelines may NOT currently reject — a CRLF-injection payload in a
  URL_PATH context, for instance. If a new payload is not rejected, that is a PRODUCTION finding for
  WS-01, not a reason to rename the entry back. Escalate it.
- Verify-first clause: the attack-database expectations encode what the pipelines currently reject.
  If PLAN-01 or PLAN-02 has landed, those expectations may already be stale. Re-run the databases
  against HEAD before scoping, and treat a newly-failing expectation as a re-scope trigger.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/database/` — `ModSecurityCRSAttackDatabase`, `IPv6AttackDatabase`, `HomographAttackDatabase`, `ApacheCVEAttackDatabase`, `NginxCVEAttackDatabase` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/tests/EdgeCaseValidURLsDatabaseTest.java` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/` — `URLPathValidationPipelineTest`, `URLParameterValidationPipelineTest`, `HTTPHeaderValidationPipelineTest` (the last READ-ONLY, as the model) (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/validation/` — `CharacterValidationStageTest`, `DecodingStageTest` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/client/adapter/ResilientHttpAdapterTest.java` — the lower-bound timing assertion (verify-at-outline)

- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/tests/UnicodeNormalizationAttackTest.java` — line 181's comment ONLY, plus the new NFKC invariant assertion (deliverable 7, folded 2026-08-29) (verify-at-outline)
- ⛔ **No, not this plan's surface:** `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/HeaderAndContentTypeEnforcementRegressionTest.java` — **PLAN-04 owns it** for its one-line `java:S5778` fix and is RUNNING concurrently; this plan co-occupies the directory but never that file (see Dependencies).

## Dependencies and Sequencing

- Depends on: PLAN-12 (WS-04 is sequential; both edit the test tree). It does NOT depend on PLAN-11
  for correctness, but PLAN-11 heads the workstream.
- Overlaps with: PLAN-11, PLAN-12 — same test tree.
- Adjacent to: `doc/test-framework-structure.adoc` and `doc/http-security/specification/testing.adoc`.
  ⛔ Do NOT edit them — TQ-9/DOC-8 is PLAN-15's. But if this plan ADDS or RENAMES a test class, those
  inventories drift further; report every added or renamed class in the landing so PLAN-15 picks it up.
- ⛔ **No, not this plan's surface — PLAN-04 owns it and is RUNNING concurrently (2026-08-31):**
  `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/HeaderAndContentTypeEnforcementRegressionTest.java`.
  PLAN-04's deliverable 7 makes a single mechanical `java:S5778` fix at line 227 (hoisting `pipeline()`
  out of the `assertThrows` lambda). This plan co-occupies the `security/pipeline/` test directory but
  MUST NOT open that file — deliverable 3 (TQ-5) sweeps "several tests" in this tree, and that file is
  already the strong form it targets (it asserts `UrlSecurityFailureType` at line 230), so it is out of
  TQ-5's scope on the merits as well as by this carve-out. Report it in the landing if TQ-5 appears to
  need it; do not edit it.

- Adjacent to: WS-01's validation pipelines. A database expectation that fails after PLAN-01/PLAN-02
  land is a re-scope trigger, not a test to weaken.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-13-attack-database-and-assertion-quality.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

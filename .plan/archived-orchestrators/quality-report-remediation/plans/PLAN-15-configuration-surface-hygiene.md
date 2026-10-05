# PLAN-15: Configuration Surface Hygiene

epic: quality-report-remediation
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-15-configuration-surface-hygiene.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.
>
> **This spec was SPLIT OUT of PLAN-03 at the PLAN-01 landing.** Folding inbox message
> `decoding-normalisation-hardening-001` took PLAN-03 to eight deliverables, over the epic's
> scope-bloat guard. The exception/log half and this configuration half proved surface-disjoint,
> so the split was taken rather than a rationale recorded for proceeding unsplit. PLAN-03 keeps
> the exception and log surface; this spec owns the configuration surface.

## Objective

Remove the configuration surface that suggests enforcement which does not exist, and reconcile
what PLAN-01's landing left inert. `RequestCollectionValidator` advertises a hash-collision DoS
defence its own code cannot deliver and silently accepts a negative limit. The builder
hard-codes every default literal that `SecurityDefaults` independently declares and never
references, and a large block of `SecurityDefaults` constants is referenced by no production
class at all — one of them, `DOUBLE_ENCODING_PATTERNS`, does not even contain double-encoded
sequences. On top of that, PLAN-01 made both double-encoding gates unconditional, so
`allowDoubleEncoding` is now a published, documented configuration property that changes no
observable behaviour, and two `LENIENT_CONFIGURATION` Javadoc claims describing it went stale in
the same act. Every change is driven by a regression test written and seen to fail first.

## Deliverables

1. **`RequestCollectionValidator` counts what it claims and rejects a negative limit (F-A-10,
   L-7).** `validateParameters(Map<String,?>)` calls `parameters.size()`, so a
   `Map<String,String[]>` with one key and ten thousand values counts as one; `enforce()` checks
   only `actual > max` with no lower bound, so a negative configured count is silently accepted.
   Count parameter *instances*, and reject a negative limit at construction. Either deliver the
   advertised hash-collision defence or stop advertising it in the class Javadoc.
2. **Duplicated default literals reconciled (L-11, part of F-A-11).** The builder hard-codes
   `maxPathLength=4096`, `maxParameterNameLength=128`, `maxParameterValueLength=2048`,
   `maxHeaderNameLength=128`, `maxHeaderValueLength=2048`, `maxCookieNameLength=128`,
   `maxCookieValueLength=2048`, `maxBodySize=5MB` and `allowExtendedAscii` while `SecurityDefaults`
   independently declares identical `MAX_*_DEFAULT` constants the builder never references; only
   the `*_COUNT_DEFAULT` constants are actually used. Make one the source of truth.
3. **Dead constant surface removed or wired up (F-A-11).** `DANGEROUS_HEADER_NAMES`,
   `DEBUG_HEADER_NAMES`, `SUSPICIOUS_COOKIE_NAMES`, the three content-type sets, `NULL_BYTE`,
   `PROBLEMATIC_CONTROL_CHARS`, `INJECTION_CHARACTERS`, `DOUBLE_ENCODING_PATTERNS` and
   `UNICODE_NORMALIZATION_FORMS` are referenced by no production class.
   `DOUBLE_ENCODING_PATTERNS` in fact contains *single*-encoded dot-dot sequences, and
   `PROBLEMATIC_CONTROL_CHARS` omits 0x1A-0x1F. Delete what is dead; wire up what should not be.
4. **`SecurityDefaults`' internal contradiction resolved (F-A-12, code half).** Line 60 calls the
   count limits "advisory constants for application-layer enforcement" while lines 46-47 say
   `RequestCollectionValidator` enforces them. One of the two is wrong.
5. **`allowDoubleEncoding` is now inert — decide its fate (folded from PLAN-01, inbox
   `decoding-normalisation-hardening-001`).** PLAN-01's deliverable 5 made both double-encoding
   gates unconditional, so `DecodingStage` no longer reads `config.allowDoubleEncoding()`. It is
   therefore a published, documented, Maven-Central-shipped configuration property that changes no
   observable *validation* behaviour — but it is **not inert**: `isStrict()` and `isLenient()` still
   read it, so removing it changes what those two predicates report. Decide: retain with the
   demotion documented, deprecate, or schedule removal for the next major. Removal is
   a breaking change to a public surface governed by **ADR-0008** and would need its own ADR.
   **This is a genuine fork — surface it to the operator; do not decide it silently.**
6. **Correct the two stale `LENIENT_CONFIGURATION` Javadoc claims (folded from the same
   message).** `SecurityDefaults.java`'s lenient security callout still says
   `allowDoubleEncoding = true` "disables the double-encoding gate, so an input that hides an
   attack behind a second layer of percent-encoding (for example `%252e%252e%252f`) is no longer
   rejected on that basis" — that input IS now rejected under `lenient()`. It also says
   `normalizeUnicode = false` disables "the homoglyph/confusable detection that depends on
   normalization" — detection is now unconditional and only the *returned form* still depends on
   the flag. PLAN-01 had this file on its do-not-touch list, which is why these were filed rather
   than fixed.
7. **Regression tests, written first.** One failing test per deliverable, asserting exact values.
   Include: a one-key ten-thousand-value parameter map must be rejected; a negative configured
   count must throw at construction; `%252e%252e%252f` must be rejected under `lenient()` (the
   test that pins deliverable 6's corrected claim).

## Claim Labels

The findings below were checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that corroborated 44 of 44 findings in `01-security-validation.adoc` with 0
contradicted and 0 unverifiable. ⛔ **PLAN-01 has since landed at `7a0da52` and PLAN-02 lands
before this plan** — both touch this surface, so every line citation below is re-read at outline
rather than trusted.

- OBSERVED: `validateParameters` calls `parameters.size()` at line 86, and `enforce()` checks
  only `actual > max` with no lower bound at lines 143-153; the class Javadoc at lines 39-40
  frames the class as defending against "resource-exhaustion and hash-collision denial-of-service"
  — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/RequestCollectionValidator.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: parameters.size() at 86, enforce 143-153, Javadoc 39 all exact
- OBSERVED: the builder's field defaults are hard literals while `SecurityDefaults` independently
  declares identical `MAX_*_DEFAULT` constants the builder never references; only the
  `*_COUNT_DEFAULT` constants at lines 109-111 are used — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java`
  (lines 76-98, 109-111) and `.../SecurityDefaults.java` (lines 96-292).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: hard literals 76-98 and the three *_COUNT_DEFAULT refs 109-111
- OBSERVED: the dead constant blocks sit at `SecurityDefaults.java` lines 236-246, 278-281,
  295-314, 319-331 and 340-348, referenced by none of the roughly twenty production classes read;
  `DOUBLE_ENCODING_PATTERNS` contains `%2e%2e`, `%2f%2e%2e`, `%5c%2e%2e` — single-encoded, not
  double-encoded — and `PROBLEMATIC_CONTROL_CHARS` enumerates 0-8, 12, 14-25, omitting 9, 10, 13
  and 26-31.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: PROBLEMATIC_CONTROL_CHARS decoded: exactly 0-8,12,14-25, omitting 9,10,13,26-31; DOUBLE_ENCODING_PATTERNS holds the three single-encoded strings verbatim
- OBSERVED: `SecurityDefaults` line 60 describes the count limits as "advisory constants for
  application-layer enforcement" while lines 46-47 state the same limits ARE enforced by
  `RequestCollectionValidator`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: line 60 advisory vs lines 46-47 enforced, a direct internal contradiction
- OBSERVED (folded from PLAN-01, landed at `7a0da52`): `DecodingStage` no longer reads
  `config.allowDoubleEncoding()` — PLAN-01 made both the wire-form `DOUBLE_ENCODING_PATTERN` gate
  and the post-decode `SURVIVING_ENCODING_PATTERN` gate unconditional, and moved the Unicode
  structural-fold check out of `if (config.normalizeUnicode())` so the fold is always computed and
  always inspected, `normalizeUnicode` now deciding only which form is returned.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: DecodingStage class Javadoc line 66 states allowDoubleEncoding is no longer read by the stage
- ⛔ RE-SCOPED at the 2026-09-06 cleanup (**CONTRADICTED at `73afd22`**). The original claim
  carried PLAN-01's reported figure — "production references in exactly four files, of which only
  `DecodingStage` was a consumer" — and explicitly flagged it as un-re-derived. The cleanup re-ran
  the search, and **both halves were wrong**: `allowDoubleEncoding` appears in **five** files under
  `cui-http-core/src/main` (`package-info.java`, `SecurityDefaults.java`, `DecodingStage.java`,
  `SecurityConfiguration.java`, `SecurityConfigurationBuilder.java`), and
  `SecurityConfiguration.isStrict()` / `isLenient()` (lines 318 and 332) are **themselves
  functional consumers of the flag** — so it was never true that only `DecodingStage` read it.
  - verdict: contradicted | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: re-ran the search the claim itself flagged unreliable: allowDoubleEncoding appears in FIVE main files not four, and SecurityConfiguration.isStrict()/isLenient() at 318/332 are themselves consumers, so only-DecodingStage was never true. Re-scoped: the flag is demoted, not inert.
- OBSERVED (re-derived at `73afd22`): the flag is no longer read by any *validation* path —
  `DecodingStage`'s own class Javadoc states at line 66 that `allowDoubleEncoding()` "is
  consequently no longer read by this stage". ⛔ **This is the accurate framing, and it changes
  deliverable 5's fork**: the flag is not inert, it is *demoted* — it still determines what
  `isStrict()` and `isLenient()` report. Removing it therefore changes those two predicates'
  behaviour, which is a larger break than the original "retire an unread flag" framing implied.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: re-derived at 73afd22: no validation path reads the flag, but isStrict/isLenient still do - the accurate framing, and it enlarges deliverable 5's fork
- OBSERVED (quoted verbatim from inbox `decoding-normalisation-hardening-001`): the two stale
  `LENIENT_CONFIGURATION` Javadoc claims in deliverable 6. Confirm both strings still read that way
  at `SecurityDefaults.java` before editing.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: both stale LENIENT_CONFIGURATION strings present verbatim at 405-409; file unchanged by PLAN-01
- OBSERVED: `SecurityDefaults.SENSITIVE_PATH_PATTERNS`' Javadoc is **unaffected** by PLAN-01 — its
  decoded-backslash reachability argument still holds, because PLAN-01 deliberately did not add a
  decoded-backslash rejection precisely so as not to invalidate it. ⛔ Do not "fix" it.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: SENSITIVE_PATH_PATTERNS Javadoc confirmed unaffected, consistent with PLAN-13's independent finding
- HYPOTHESIS: deliverable 3 deletes code, and a constant a downstream consumer imports is a
  published-API break. Confirm/refute at `cui-http-core/src/main/java/module-info.java` and the
  `SecurityDefaults` export status (verify-at-outline). A refutation — the constants are exported
  public API — re-scopes deliverable 3 to deprecation rather than deletion.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: forward HYPOTHESIS on a published-API constant export
- Verify-first clause: before scoping deliverable 5, settle whether `isStrict()` / `isLenient()`
  are themselves public API and whether any test or downstream consumer asserts on them. Read
  `SecurityConfiguration.java` and every `isStrict(` / `isLenient(` call site. A refutation loops
  back and re-scopes deliverable 5 to the retain-as-inert branch only.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: procedural verify-first directive on isStrict/isLenient public-API status

## Expected Surface

⛔ **CORRECTED 2026-09-09 at landing (PR #229, `b09925a`): 8 declared entries, 8 realized files, but
NOT the same eight.** Shipped spec; correction serves the audit record.

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfiguration.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/RequestCollectionValidator.java` — declared
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/config/` — declared; `SecurityConfigurationBuilderTest`, `SecurityDefaultsTest`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/RequestCollectionValidatorTest.java` — declared
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/DoubleEncodingPresetParityTest.java` — ⛔ **UNDECLARED, and inside PLAN-04's declared "whole pipeline test package" — written while PLAN-04 was RUNNING**
- NOT TOUCHED: `cui-http-core/src/main/java/module-info.java` — over-declared
- NOT TOUCHED: `doc/adr/` — over-declared; the operator's no-ADR ruling removed the only reason to write there

⛔⛔ **The epic's first REAL concurrent collision.** Both plans were emitted together on the
orchestrator's own `disjoint` verdict, and that verdict was wrong. Nothing corrupted only because
the two touched different files inside the shared directory. See `landings/PLAN-15.md` and the epic
Open Defect — the fault is the orchestrator's emit, not either plan's execution.

## Dependencies and Sequencing

- Depends on: PLAN-02, which edits `SecurityConfigurationBuilder.java` — the same file deliverable
  2 rewrites — and which decides the `allowExtendedAscii` default this plan's deliverable 2 must
  then reference rather than re-litigate.
- Overlaps with: PLAN-02 at `SecurityConfigurationBuilder.java` — strictly sequential. Also
  overlaps PLAN-14 at `module-info.java`, but only on this plan's HYPOTHESIS entry; if PLAN-14 is
  still in flight when this plan is emitted, the gate will report it and the pair is sequenced.
- **File-disjoint from PLAN-03**, which is the point of the split: PLAN-03 owns
  `UrlSecurityException.java`, `AllowBlockListStage.java` and `test/.../exceptions/`; this plan
  owns the four config classes, `RequestCollectionValidator.java` and `test/.../config/`. The two
  MAY be paired concurrently once PLAN-02 has landed.
- Adjacent to: `DecodingStage.java`, which PLAN-01 already landed and which this plan READS to
  confirm the inert-flag claim but never edits.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-15-configuration-surface-hygiene.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction.
- ⛔ **Both-preset regression rule (carried from the PLAN-01 landing).** Deliverable 6's corrected
  Javadoc claim is only true if the behaviour is actually unconditional, so pin it with a
  regression that runs `%252e%252e%252f` under BOTH `defaults()` and `lenient()` and asserts the
  SAME failure type. **Check the operand, not the outcome** — a gate must read the canonical form,
  never the value selected for return, because the return selection is config-dependent. PLAN-01's
  round-1 review fix violated exactly this and reintroduced the asymmetry it existed to remove.
- Deliverable 3 deletes code. Removing a constant a downstream consumer imports is a published-API
  break; the deliverable-3 hypothesis exists to catch that before any deletion.
- Deliverable 5 contains a genuine fork. Surface it to the operator; do not decide it silently.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

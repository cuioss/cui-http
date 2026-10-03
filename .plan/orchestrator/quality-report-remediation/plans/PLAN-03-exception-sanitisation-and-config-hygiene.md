# PLAN-03: Exception Sanitisation and Log Safety

epic: quality-report-remediation
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-03-exception-sanitisation-and-config-hygiene.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.
>
> **This spec was SPLIT at the PLAN-01 landing.** It originally carried both the exception/log
> surface and the configuration surface. Folding inbox message
> `decoding-normalisation-hardening-001` took it to eight deliverables, over the epic's scope-bloat
> guard, and the two halves proved surface-disjoint. The configuration half moved to
> **PLAN-15-configuration-surface-hygiene**; this spec keeps the exception and log surface. The
> filename is deliberately unchanged so the queue row, the emitted command and every existing
> cross-reference keep resolving.

## Objective

Stop attacker-controlled input reaching logs unsanitised. `UrlSecurityException` sanitises only
`originalInput`; `detail` is appended to the message verbatim, and `AllowBlockListStage` splices
the raw value straight into `detail`, so CR/LF from a block-listed content-type forge log lines.
The sanitiser itself misses C1 controls and U+2028/U+2029 even on the path it does cover.
Independently, every exception message and `toString()` reproduces up to 200 characters of the
original input, which for the header-value pipeline — explicitly marketed for `Authorization`
values — means bearer tokens and session cookies land in logs, contradicting the library's own
logging guideline. Every change is driven by a regression test written and seen to fail first.

## Deliverables

1. **`detail` is sanitised like `originalInput` (F-B-5, L-4, F-A-5).** `buildMessage` appends
   `detail` with no sanitisation while routing `originalInput` through `truncateForLogging`.
   Sanitise `detail` on the same path — this single fix closes all three duplicate records — and
   stop `AllowBlockListStage` splicing the raw value into its detail string, matching what
   `CharacterValidationStage.handleInvalidCharacter` and `DecodingStage.escaped` already do.
2. **The sanitiser covers every line-forging character (F-B-5, second half).**
   `CONTROL_CHARS_PATTERN` is `[\x00-\x1F\x7F]`, which omits C1 controls (U+0085) and
   U+2028/U+2029 even on the `originalInput` path. Extend it.
3. **Exception messages stop reproducing credential material (F-B-7).** `buildMessage` always
   appends up to 200 characters of the input, and `toString()` repeats it a second time.
   `HTTPHeaderValidationPipeline` is documented for `Authorization` values, so a rejected bearer
   token is reproduced verbatim in any `e.getMessage()` log statement. Redact rather than merely
   truncate, or make the echo opt-in.
4. **Regression tests, written first.** One failing test per deliverable, asserting exact values.
   Include: a block-listed content-type carrying CR/LF must produce a message with no raw CR/LF;
   a U+0085 and a U+2028 in the input must be neutralised; a rejected `Authorization` value must
   not appear in `getMessage()` or in `toString()`.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that corroborated 44 of 44 findings in `01-security-validation.adoc` with 0
contradicted and 0 unverifiable. **PLAN-01 has since landed at `7a0da52`**; it did not touch
either file this spec owns, so these citations are unaffected by it.

- OBSERVED: `buildMessage` appends `detail` directly at lines 155-157 with no sanitisation, while
  `originalInput` is routed through `truncateForLogging` at lines 160-161; `CONTROL_CHARS_PATTERN`
  is `[\x00-\x1F\x7F]` at line 81 and excludes C1 controls and U+2028/U+2029 — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/exceptions/UrlSecurityException.java`
  (lines 81, 150-164).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: CONTROL_CHARS_PATTERN 81, detail-append 155-157, truncateForLogging 160-161 all exact; file untouched
- OBSERVED: both the block-list and allow-list rejection paths build `detail` as
  `"Value '" + value + "' is ..."` with the raw attacker value spliced in and no escaping — read
  at `cui-http-core/src/main/java/de/cuioss/http/security/validation/AllowBlockListStage.java`
  (lines 146, 155). `CharacterValidationStage.handleInvalidCharacter` renders control characters
  as `U+XXXX` before including them, which is the pattern to follow.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: AllowBlockListStage detail sites 146 and 155 exact
- OBSERVED: `truncateForLogging` (lines 172-185) replaces control characters with `?` and
  truncates at 200 characters but does not redact content; `buildMessage` (lines 159-164) and
  `toString()` (lines 188-198) each include it, so the input appears twice — read at
  `UrlSecurityException.java`. `HTTPHeaderValidationPipeline`'s Javadoc markets the pipeline for
  `Authorization` header values.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: truncateForLogging 172-185, buildMessage 159-164, toString 188-198 exact
- OBSERVED (landed evidence, not a prediction): PLAN-01's own finalize security audit found **two
  CRLF log-injection findings (CWE-117 / CWE-93) in code it had just written**, in this same
  package family. The defect class this plan closes is live and recurring, not historical —
  read at the PLAN-01 landing record `landings/PLAN-01.md` § Routing and Merge Behavior.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: landings/PLAN-01.md line 72 confirms the two CWE-117/CWE-93 findings verbatim
- HYPOTHESIS: deliverable 3's redaction changes the text of every `UrlSecurityException` message
  and will break tests that assert on message content. Confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/exceptions/UrlSecurityExceptionTest.java`
  (verify-at-outline).
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: forward HYPOTHESIS on redaction breaking message assertions
- Verify-first clause: PLAN-02 lands before this plan and edits `CharacterValidationStage`, whose
  `handleInvalidCharacter` escaping this plan adopts as its pattern. Re-read that method at the
  then-current HEAD before scoping deliverable 1; if PLAN-02 changed the escaping shape, follow the
  changed one rather than the shape recorded here.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: premise holds: PLAN-02 has not landed and handleInvalidCharacter escaping 250-264 is unchanged

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/exceptions/UrlSecurityException.java` — `CONTROL_CHARS_PATTERN`, `buildMessage`, `truncateForLogging`, `toString`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/AllowBlockListStage.java` — the two `detail` construction sites at lines 146 and 155
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/exceptions/` — the whole exception test package
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/AllowBlockListStageTest.java` — **added 2026-09-08 at landing**; realized but never declared. Inside PLAN-11's declared `test/.../validation/` surface (and PLAN-02's, now shipped)

✅ **Declared 3 entries, realized 4 — the epic's best declaration to date**, and the first reversal
of its under-declaration trend (PLAN-01 1 file → PLAN-14 a whole directory → PLAN-02 7 files plus a
violated exclusion → PLAN-06 3 files into a sibling's surface → **PLAN-03 one adjacent test**).
⛔ PLAN-11 must re-read `AllowBlockListStageTest.java` at outline.

⛔ This plan does **not** edit any file under `doc/`, and does **not** touch
`SecurityConfigurationBuilder.java`, `SecurityDefaults.java`, `SecurityConfiguration.java`,
`RequestCollectionValidator.java` or `module-info.java` — all of those moved to
**PLAN-15-configuration-surface-hygiene** in the split. Javadoc inside the two `.java` files above
IS in scope.

## Dependencies and Sequencing

- Depends on: PLAN-02, for the verify-first clause above only — PLAN-02 settles the escaping shape
  this plan follows. The two are **file-disjoint**: PLAN-02 owns `CharacterValidationStage.java`
  and `CharacterValidationConstants.java`, this plan owns `UrlSecurityException.java` and
  `AllowBlockListStage.java`, and their test directories do not overlap either
  (`test/.../validation/` vs `test/.../exceptions/`). They MAY therefore be paired concurrently if
  the operator accepts that this plan then re-reads the escaping shape from PLAN-02's branch
  rather than from a landed HEAD.
- Overlaps with: PLAN-04 and PLAN-15 at `AllowBlockListStage.java` (PLAN-04) — strictly sequential
  with PLAN-04, which is already sequenced after this plan.
- Adjacent to: `SecurityEventCounter` in `de.cuioss.http.security.monitoring`, which counts these
  exceptions and is not edited here.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-03-exception-sanitisation-and-config-hygiene.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass.
- Deliverable 1 closes three duplicate finding records (F-B-5 as primary, with L-4 and F-A-5 as
  the same defect seen from two other directions). Its regression test asserts on the *message
  text* — that no raw CR/LF survives into `getMessage()` — not on the exception type.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction.
- ⛔ **Both-preset regression rule (carried from the PLAN-01 landing).** Any gate or sanitiser this
  plan touches during review must be pinned by a regression that runs the SAME input under BOTH
  `defaults()` and `lenient()` and asserts the SAME outcome. PLAN-01's round-1 review fix read the
  value *selected for return*, whose selection is config-dependent, and thereby reintroduced the
  exact asymmetry that plan existed to remove; only a second review round caught it. **Check the
  operand, not the outcome** — read the canonical form, never the value chosen for return. A
  single-preset regression cannot observe an asymmetry.
- ⛔ **Re-check every review fix against the class invariant before committing it.** A fix written
  under review pressure against one reported symptom is exactly where a defect class silently
  returns. See ADR-0017 and the lessons corpus entry promoted from this epic.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

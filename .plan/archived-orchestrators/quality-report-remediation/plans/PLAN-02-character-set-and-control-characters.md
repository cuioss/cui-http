# PLAN-02: Character Sets and Control Characters

epic: quality-report-remediation
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-02-character-set-and-control-characters.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Settle what each `ValidationType`'s character set admits, in both directions. The sets are
simultaneously too narrow and too wide: the query set omits `/`, `:` and `@`, which RFC 3986
§3.4 permits and browsers send unencoded, while the identical characters sail through in their
percent-encoded spelling; the cookie set is RFC 3986 unreserved, so an RFC-legal base64 cookie
value containing `+`, `/` or `=` is rejected. In the other direction, `allowExtendedAscii`
defaults to `true` — contradicting the `CharacterValidationStage` Javadoc, which documents the
default as `false` — so C1 controls, U+2028/U+2029, U+202E and unpaired surrogates reach header
values under `defaults()`; `allowControlCharacters=true` under `lenient()` admits every C0
control except CR/LF into header names, header values and cookie names; decoded Unicode format
and invisible characters pass in URL paths; and a paranoid-preset parameter *value* equal to
`etc` is rejected because a path-segment block-list is applied to it. Every change is driven by a
regression test written and seen to fail first.

## Deliverables

1. **Query and cookie character sets match the RFCs they cite (F-A-9, L-8, F-B-4).**
   `RFC3986_QUERY_CHARS` adds only unreserved plus `?&=!$'()*+,;` — no `/`, `:` or `@`, both of
   which RFC 3986 §3.4 permits. `getCharacterSet(COOKIE_NAME/COOKIE_VALUE)` returns
   `RFC3986_UNRESERVED`, narrower than RFC 6265's `cookie-octet`. Widen both to the cited
   grammars, and close the asymmetry whereby `CharacterValidationStage`'s `%` special-case lets
   the percent-encoded spelling of a rejected character bypass the set entirely and never be
   re-checked after decoding.
2. **`allowExtendedAscii` default and the C1 gap (F-A-7, F-B-15, L-9).** The builder's field
   default is `true` while `CharacterValidationStage`'s Javadoc documents `false`; for the
   128-255 range `isCharacterAllowed` returns `allowExtendedAscii || allowedChars.test(ch)` with
   no ISO-control re-check, so U+0085 and friends pass under `defaults()` and only `strict()`
   rejects them. Decide the default, make code and Javadoc agree, and re-check the C1 range
   regardless of the flag. Separately, `DecodingStage.validateDecodedCharacters` rejects only
   combining marks and `Character.isISOControl` (category Cc), so Cf format characters (U+202E
   RLO, U+200B ZWSP, U+FEFF BOM) and non-ASCII Zs (U+00A0) pass in URL paths even though they
   enable the same spoofing class the combining-mark rule targets; reject them too.
3. **`lenient()` must not admit arbitrary C0 controls into headers (F-A-6).** For `ch <= 31`,
   CR/LF are rejected unconditionally only for header and cookie types; every other C0 control
   falls through to `return allowControlCharacters`, which `lenient()` sets to `true`.
   `HTTPHeaderValidationPipeline` composes only a length and a character stage — no
   `DecodingStage` — so this stage is the sole character guard for headers. Reject C0 controls in
   header names and values under every preset, contradicting neither RFC 7230 nor
   `DecodingStage`'s own "unconditional for header/cookie types" rule.
4. **Path-segment block-list stops being applied to parameter values (F-A-14).**
   `checkBlockedPathPatterns` is gated on `URL_PATH || PARAMETER_VALUE` and applies segment-based
   matching to both, so under `paranoid()` a parameter value equal to `etc`, `dev`, `sys`,
   `root`, `boot` or `proc` is rejected as a sensitive path. Apply path-segment semantics only to
   path types.
5. **Stage test-quality gaps (F-A-13).** `PatternMatchingStageTest.shouldRespectCaseSensitiveConfiguration`
   validates only the all-lowercase input `../etc/passwd` under both case-sensitive and
   case-insensitive configuration, so its assertion is identical either way and it cannot detect
   a regression in the case-sensitivity wiring. `DecodingStageTest.shouldBeImmutableAndThreadSafe`
   comments that immutability comes from Lombok `@Value` on a class that is a record, and asserts
   only that `equals`/`hashCode`/`toString` exist by reflection — trivially true. Give both real
   assertions.
6. **Regression tests, written first.** One failing test per deliverable, asserting the exact
   returned value or the exact `UrlSecurityFailureType`. Include the probe inputs the report and
   the verification pass name: a raw `/`, `:` and `@` in a query value; a padded base64 cookie
   value; U+0085 and U+2028 in a header value under `defaults()`; a VT (0x0B) in a header name
   under `lenient()`; a parameter value of exactly `etc` under `paranoid()`.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that corroborated 44 of 44 findings in `01-security-validation.adoc` with 0
contradicted and 0 unverifiable.

- OBSERVED: the `RFC3986_QUERY_CHARS` static initializer adds unreserved plus `?&=!$'()*+,;` and
  no `/`, `:` or `@` — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationConstants.java`
  (lines 177-185); `getCharacterSet` maps `COOKIE_NAME`/`COOKIE_VALUE` to `RFC3986_UNRESERVED` at
  line 267, and the unreserved set is built at lines 117-121.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: RFC3986_QUERY_CHARS init 177-185 and COOKIE mapping 267 exact; unreserved build is at 152-165 not 117-121
- OBSERVED: `CharacterValidationStage` special-cases `%` before the general set check (lines
  209-213), checking only the hex-digit validity of the two following characters and never the
  decoded meaning; `DecodingStage.validateDecodedCharacters` re-checks only NUL, combining marks,
  `isISOControl` and `PARAMETER_NAME` delimiters — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java`
  and `.../DecodingStage.java` (lines 376-394).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: % special-case still 209-213; validateDecodedCharacters moved 376-394 -> 546-597 after PLAN-01, substance holds
- OBSERVED: `allowExtendedAscii = true` is the builder's field default at line 97, while
  `CharacterValidationStage`'s Javadoc documents `(default: false)` at lines 123-130; the
  builder's own "Default Values" Javadoc section (lines 56-66) omits `allowExtendedAscii` and
  `normalizeUnicode` entirely — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java`
  and `CharacterValidationStage.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: allowExtendedAscii=true at 97 and Javadoc default:false at 123-130, both exact
- OBSERVED: the 128-255 branch of `isCharacterAllowed` returns
  `allowExtendedAscii || allowedChars.test(ch)` with no ISO-control re-check — read at
  `CharacterValidationStage.java` (lines 357-369).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: 128-255 branch at 357-369 verbatim
- OBSERVED: `Character.isISOControl` covers category Cc only, so Cf format characters and
  non-ASCII Zs pass the decoded-character loop — read at `DecodingStage.java` (lines 376-394).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Cf and non-ASCII Zs still bypass the decoded-char checks post-PLAN-01; cited lines moved to 546-597
- OBSERVED: for `ch <= 31`, CR/LF are rejected unconditionally only for header and cookie types
  (lines 341-343) and every other C0 control falls through to `return allowControlCharacters`
  (line 349) — read at `CharacterValidationStage.java` (lines 336-350).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: 336-350 verbatim: CR/LF unconditional only for header/cookie, fallthrough at 349
- OBSERVED: `HTTPHeaderValidationPipeline.createStages` composes `LengthValidationStage` and
  `CharacterValidationStage` (plus `AllowBlockListStage` for `HEADER_NAME`) and no
  `DecodingStage` — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/HTTPHeaderValidationPipeline.java`
  (lines 135-147).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: createStages 135-147 composes no DecodingStage
- OBSERVED: `checkBlockedPathPatterns` is gated on `validationType == URL_PATH || PARAMETER_VALUE`
  (lines 309-312) and splits on `/` for both (line 462); `stripSurroundingSlashes("/etc/")` yields
  `etc`, so a bare parameter value `etc` matches the single-segment set under `paranoid()` — read
  at `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java`
  (lines 309-312, 456-477) and `SecurityDefaults.SENSITIVE_PATH_PATTERNS`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: gate 309-312 and checkBlockedPathPatterns 456-477 exact
- OBSERVED: `PatternMatchingStageTest.shouldRespectCaseSensitiveConfiguration` (lines 300-311)
  validates only `../etc/passwd` under both configurations; `DecodingStageTest.shouldBeImmutableAndThreadSafe`
  (lines 425-433) carries a Lombok `@Value` comment on a record and asserts method existence by
  reflection — read at `cui-http-core/src/test/java/de/cuioss/http/security/validation/PatternMatchingStageTest.java`
  and `.../DecodingStageTest.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: PatternMatchingStageTest 300-311 unchanged; DecodingStageTest immutability test moved to ~508-533
- HYPOTHESIS: flipping `allowExtendedAscii` to `false` (deliverable 2) changes the default
  posture for every integrator and will reject non-ASCII input that is accepted today.
  Confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/validation/CharacterValidationStageTest.java`
  § the extended-ASCII test methods, and at
  `cui-http-core/src/test/java/de/cuioss/http/security/config/SecurityConfigurationBuilderTest.java`
  (verify-at-outline). If the permissive default is deliberate, the fix is to correct the Javadoc
  and re-check the C1 range rather than to flip the default — decide with the operator.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: forward HYPOTHESIS on flipping a default; requires the change to settle
- Verify-first clause: before scoping deliverable 1, settle whether widening
  `RFC3986_QUERY_CHARS` weakens any downstream check that currently relies on `/` being rejected
  in a parameter value. Read `PatternMatchingStage.checkBlockedPathPatterns` and every
  `PARAMETER_VALUE` consumer. A refutation loops back and re-scopes deliverable 1.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: procedural verify-first directive, no checkable current-state fact

## Expected Surface

⛔ **CORRECTED 2026-09-08 at landing (PR #217, `bdcb36e`) from 7 declared entries to the 19 files
the merge actually touched.** This spec is SHIPPED — the correction serves the audit record and the
epic's under-declaration measurement, not any future gate call. The original 7-entry declaration is
preserved verbatim in `landings/PLAN-02.md` § Surface delta.

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationConstants.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java` — declared
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/` — declared; `CharacterValidationConstantsTest`, `CharacterValidationStageTest`, `DecodingStageTest`, `PatternMatchingStageTest`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/config/SecurityConfigurationBuilderTest.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfiguration.java` — UNDECLARED; inside PLAN-15's surface
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/package-info.java` — UNDECLARED; in no spec's surface
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/data/Cookie.java` — UNDECLARED; inside PLAN-05's surface
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/config/SecurityConfigurationTest.java` — UNDECLARED; inside PLAN-15's surface
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/config/SecurityDefaultsTest.java` — UNDECLARED; inside PLAN-15's surface
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/data/CookieTest.java` — UNDECLARED; inside PLAN-05's surface
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/URLParameterValidationPipelineTest.java` — UNDECLARED; inside PLAN-04's surface
- OBSERVED: `doc/adr/` — UNDECLARED, and positively EXCLUDED by the original spec. Two ADRs shipped here: `0019-Cookie_character_sets_are_split_by_RFC_role_with_no_DQUOTE_quote-pair_carve-out.adoc` and `0020-Header_and_cookie_character_gates_ignore_preset_flags_when_no_later_stage_can_re-check_them.adoc`

⚠ **The realized set overlapped the DECLARED surfaces of PLAN-04, PLAN-05 and PLAN-15.** None was
running, so nothing conflicted — but the gate would have paired PLAN-02 with any of the three and
been wrong.

⛔ **The original spec asserted "this plan does not edit any file under `doc/`" and then shipped two
ADRs there.** `adr-propose` fires on every plan regardless of declaration; third occurrence in this
epic (PLAN-01, PLAN-14, PLAN-02).

⚠ `references.json` `affected_files` recorded only **14** of the 19, omitting `Cookie.java`,
`CookieTest.java`, `URLParameterValidationPipelineTest.java` and both ADRs. That field is what
`corpus cross-check` reads as a LIVE plan's surface, so a running plan's footprint was understated
at the source the gate trusts most for in-flight work.

## Dependencies and Sequencing

- Depends on: PLAN-01. That plan moves the decoded-character checks after normalisation, which
  changes the point at which this plan's character rules are applied.
- Overlaps with: PLAN-01 and PLAN-03 (same package, same test package) — strictly sequential.
  Also overlaps WS-02 PLAN-04, which composes `CharacterValidationStage` into the content-type
  pipeline; WS-02 is sequenced after this plan.
- Adjacent to: `de.cuioss.http.security.exceptions`, which PLAN-03 owns. This plan does not touch
  it, even though a character rejection produces an exception there.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-02-character-set-and-control-characters.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass.
- Assert the exact returned value or the exact `UrlSecurityFailureType`. Never `assertNotNull`,
  never "any of several failure types".
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction.
- ⛔ **Both-preset regression rule (carried from the PLAN-01 landing, `7a0da52`).** Any security
  gate this plan touches — including any gate touched by a fix applied during PR review — must be
  pinned by a regression that runs the SAME input under BOTH `defaults()` and `lenient()` and
  asserts the SAME failure type. **Check the operand, not the outcome**: a gate must read the
  canonical/normalised form, never the value selected for RETURN, because the return selection is a
  presentation choice a configuration flag is allowed to change. PLAN-01's round-1 review fix read
  the returned value and thereby reintroduced the exact raw-versus-encoded asymmetry that plan
  existed to remove — externally reachable on `URL_PATH` under `lenient()`, CWE-177 class — and
  only a second review round caught it. A single-preset regression cannot observe an asymmetry.
  See **ADR-0017**.
- ⛔ **Re-check every review fix against the class invariant before committing it.** A fix
  written under review pressure against one reported symptom is exactly where a defect class
  silently returns. Where this plan has an ADR, re-read the ADR's own claim against the patched
  code before committing — both of PLAN-01's round-1 defects were detectable as direct
  contradictions of ADR text already present in the same PR.
- Deliverable 2 contains a genuine fork (flip the default vs. correct the Javadoc). Surface it to
  the operator; do not decide it silently.
- Deliverables 1-4 change which inputs are accepted. Record each as a documented behaviour change
  in the PR body, in both directions — deliverable 1 accepts more, deliverables 2-3 accept less.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

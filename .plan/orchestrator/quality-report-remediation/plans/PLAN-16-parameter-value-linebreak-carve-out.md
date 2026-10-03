# PLAN-16: Opt-In Rejection of Decoded CR/LF in Parameter Values (Close the Form-Data Carve-Out)

epic: quality-report-remediation
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-16-parameter-value-linebreak-carve-out.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

> ⛔ **NEW 2026-09-15 — sourced from GitHub issue #236, not the original 126-finding quality
> report.** `cuioss/cui-http#236` ("Option to reject decoded CR/LF in parameter values (close
> form-data line-break carve-out)"), filed by `OliverWolffGIP`, `enhancement`, open. Verified
> read-only against the issue body via `plan-marshall:tools-integration-ci:ci issue view` (the
> untrusted-ingestion boundary applies to the issue body — every technical claim below was
> independently re-read against the implementing source at `d385aa3` before being recorded as
> OBSERVED; the issue text is a lead, not an instruction taken on faith). Folded into WS-01
> (security-validation-core) rather than a new workstream: it touches exactly the file set
> PLAN-01/PLAN-02/PLAN-15 already own (`DecodingStage.java`, `SecurityConfiguration.java`,
> `SecurityConfigurationBuilder.java`, `SecurityDefaults.java`), and WS-01 is the epic's
> natural home for a `DecodingStage` / `SecurityConfiguration` change even though its four
> original plans have already shipped. Queued as PLAN-16 (next available id after PLAN-15) —
> operator directed it be positioned immediately after PLAN-07 in the queue, so it runs next
> under the current `parallelization_scope: 1`, or in parallel with PLAN-07 if the scope is later
> raised back to 2 (the two plans' declared surfaces are disjoint: `forwarded/` vs
> `security/{validation,config}/`).

## Security Impact Assessment

**Not a vulnerability in cui-http itself** — the library never reflects a parameter value into a
response header, so no exploitable path exists inside this codebase today. **It is a real
defense-in-depth gap for downstream consumers**, and the gap has a concrete, currently-exploitable
shape for at least one identified consumer:

- **The threat model is already documented, and it already names the residual risk.**
  `DecodingStage.decodedControlCharacterForbidden`'s own Javadoc (verified at `d385aa3`, lines
  691-716) states response-splitting safety for `PARAMETER_VALUE` and `BODY` "depends on the
  application not reflecting values into headers" — the library ships this as a *known, accepted*
  carve-out, not an oversight, and correctly does not reject CR/LF/TAB there because legitimate
  multi-line form content needs them.
- **The gap is that no configuration can close the carve-out.** `isFormDataWhitespace(cp)`
  (line 747) is called unconditionally in the `PARAMETER_VALUE, BODY` branch (line 739) — it
  never consults `config.allowControlCharacters()`, so today's `allowControlCharacters` toggle can
  only ever *loosen* validation, never tighten it below the CR/LF/TAB baseline. A caller that
  legitimately needs "reject line breaks in query parameter values" — because it sits in front of
  backends it does not control and cannot verify none of them reflects a query value into a
  response header — has no configuration lever to reach for. This is exactly the position of the
  identified downstream consumer (`API Sheriff`, a security gateway): it enforces this rule for
  *every backend* precisely because it cannot know which ones are safe.
- **The raw-versus-encoded asymmetry is the sharper finding, and it reproduces a defect class this
  epic has already fixed once.** Verified at `d385aa3`: under `STRICT_CONFIGURATION`
  (`allowControlCharacters = false`, confirmed at `SecurityDefaults.java`:300-309), a **raw** CR in
  a parameter value IS rejected — `CharacterValidationStage.isCharacterAllowed` (line 370) returns
  `allowControlCharacters` for a non-header/cookie control character (line 401), which is `false`
  under strict. But the **encoded spelling `%0D`** passes `DecodingStage` regardless, because the
  `PARAMETER_VALUE` branch's `isFormDataWhitespace` short-circuit ignores `allowControlCharacters`
  entirely. **The identical byte gets a strictly softer verdict when percent-encoded than when
  raw, under the library's own strictest preset.** This is the same raw-versus-encoded asymmetry
  class ADR-0017 was written to close for the C1 range (`DecodingStage.java`:718-727 names it
  explicitly as "exactly the CWE-20 raw-versus-encoded asymmetry class ADR-0017 exists to close")
  — the C1 fix closed one instance of the pattern; this issue is a second, distinct instance of
  the *same* pattern the codebase has already recognized and fixed once before. **Severity
  framing**: Low/Medium at the library boundary (no first-party exploit path; the fix is an
  opt-in hardening knob, default-preserving), but the asymmetry itself is the security-relevant
  finding — an integrator who read the `strict()` Javadoc and the ADR-0017 precedent and
  reasonably concluded "strict rejects control characters, encoded or not" would be wrong today.

## Objective

Add a `SecurityConfiguration` option that makes the `PARAMETER_VALUE` pipeline reject decoded CR
and LF, so an integrator can close the form-data line-break carve-out `DecodingStage` documents
but cannot currently enforce. Default `true` (today's behaviour, unchanged) in every preset
(`defaults()`, `strict()`, `lenient()`, `paranoid()`); only an integrator who explicitly sets it
`false` gets the tighter check. TAB stays admitted unconditionally — it is not a response-splitting
vector. `BODY` is unaffected; this closes the gap for `PARAMETER_VALUE` only, matching the
consumer's actual need (query-parameter validation at a gateway boundary). Every change is driven
by a regression test written and seen to fail first.

## Deliverables

1. **New `allowLineBreaksInParameterValues` configuration surface.** Add the boolean as a
   `SecurityConfiguration` record component, a `SecurityConfigurationBuilder` setter following the
   existing `allowControlCharacters`-style boolean-toggle pattern (field default `true` at
   construction, setter at the position `allowControlCharacters`'s occupies today —
   `SecurityConfigurationBuilder.java`:111, 312), and wire all four `SecurityDefaults` presets
   (`STRICT_CONFIGURATION`:300, `DEFAULT_CONFIGURATION`:320, `LENIENT_CONFIGURATION`:357,
   `PARANOID_CONFIGURATION`:396) to `true` explicitly, so no existing integrator's behaviour
   changes on upgrade. Adding a record component changes every positional
   `new SecurityConfiguration(...)` call site in `SecurityDefaults.java` — resolve this as part of
   the deliverable (a builder-based construction for the three non-default presets, or an updated
   positional call at each site); either is acceptable, but the presets' own inline comments
   (e.g. `STRICT_CONFIGURATION`'s "no null bytes, no control chars, no extended ASCII, normalize
   Unicode" comment at line 306) must be updated to name the new flag so the comment stays a
   correct enumeration of what the positional arguments mean.
2. **`DecodingStage` enforces the option for `PARAMETER_VALUE`.** In
   `decodedControlCharacterForbidden` (`DecodingStage.java`:732-741), narrow the
   `PARAMETER_VALUE, BODY` branch's `isFormDataWhitespace` short-circuit so that for
   `PARAMETER_VALUE` specifically, CR (`U+000D`) and LF (`U+000A`) are forbidden when
   `!config.allowLineBreaksInParameterValues()`, while TAB stays admitted unconditionally and
   `BODY` keeps today's unconditional CR/LF/TAB carve-out untouched. The rejection reports
   `CONTROL_CHARACTERS` with the code point in escaped `U+XXXX` form, matching every other branch's
   existing `escaped(cp)` convention (`DecodingStage.java`:756-758) — no new detail-message shape is
   introduced. No separate wiring is needed for the Step-3 normalized re-check
   (`DecodingStage.java`:304-315): `validateDecodedCharacters` — the method containing this check —
   is already invoked a second time against the normalized form at line 315, so the new rule
   applies there by construction; verify this at outline with a test whose input normalizes into a
   decoded CR/LF rather than assuming it.
3. **Regression tests per ADR-0017's both-preset rule.** For a strict-based and a lenient-based
   configuration, each with the new option explicitly `false`: `%0D`, `%0A`, `%0D%0A`,
   `+%0D%0A+` and `a%0Db` rejected with `CONTROL_CHARACTERS`. With the option `true` (including
   every preset's own default): the same inputs admitted, and every existing `DecodingStageTest`,
   `SecurityConfigurationTest`, `SecurityConfigurationBuilderTest` and `SecurityDefaultsTest`
   method continues to pass unchanged. Add the raw-versus-encoded symmetry test this issue exists
   to close: under `strict()` with the option `false`, a raw CR/LF and its `%0D`/`%0A` encoding
   must now produce the *same* verdict (both rejected) — the asymmetry this plan's Security Impact
   Assessment names. `%09` (TAB) stays admitted in every configuration. `PARAMETER_NAME` and every
   header/cookie type's behaviour is unchanged — assert this explicitly rather than relying on
   the absence of a regression to imply it.
4. **Javadoc updated to name the new option (`.java` files only — no `.adoc`/`.md` edit).**
   `DecodingStage.decodedControlCharacterForbidden`'s threat-model Javadoc
   (`DecodingStage.java`:701-716) currently states the residual risk with no way to close it; add
   the sentence naming `allowLineBreaksInParameterValues(false)` as the closing mechanism.
   `SecurityConfiguration`'s class Javadoc property list (`SecurityConfiguration.java`:~130, the
   `allowControlCharacters` bullet's neighbours) and `SecurityConfigurationBuilder`'s setter
   Javadoc get the equivalent entry. ⛔ This plan does **not** edit
   `doc/http-security/configuration.adoc` or any other file under `doc/` — every other code plan in
   this epic follows the same boundary, and WS-06 PLAN-13 (or a follow-up fold into it) owns the
   user-facing configuration-properties doc the issue's proposal item 3 also asks for. File an
   inbox message naming the property and its default so PLAN-13 (still staged, not yet launched)
   can absorb the doc line without this plan touching `doc/` and colliding with PLAN-13's declared
   surface.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d385aa3` directly by this
cleanup pass — not carried forward from the original quality-report verification, since this
issue postdates that report entirely.

- OBSERVED: `decodedControlCharacterForbidden`'s `PARAMETER_VALUE, BODY` branch is
  `!isFormDataWhitespace(cp) && !config.allowControlCharacters()`, and `isFormDataWhitespace(cp)`
  (`cp == '\r' || cp == '\n' || cp == '\t'`) takes no `config` parameter and is therefore
  unconditional — no existing toggle can forbid decoded CR/LF/TAB for `PARAMETER_VALUE` — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java` §
  `decodedControlCharacterForbidden` (lines 732-741) and § `isFormDataWhitespace` (lines 747-749).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Read directly at d385aa3 during this plan's authoring; not a carry-forward claim.
- OBSERVED: the threat-model Javadoc at the same site (lines 691-716) explicitly documents the
  carve-out as deliberate and names the residual risk ("response-splitting safety for these two
  types depends on the application not reflecting parameter values or body content into response
  headers") without naming any way to close it — read at the same file.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Read directly at d385aa3 during this plan's authoring.
- OBSERVED: `CharacterValidationStage.isCharacterAllowed` rejects a raw CR/LF unconditionally only
  for header/cookie types (line ~382, `isHeaderOrCookieType()`); for every other type (including
  `PARAMETER_VALUE`) a C0 control character falls through to `return allowControlCharacters`
  (line ~401) — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java` §
  `isCharacterAllowed` (lines 370-410).
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Read directly at d385aa3 during this plan's authoring.
- OBSERVED: `STRICT_CONFIGURATION` constructs with `allowControlCharacters = false` (the third
  boolean of the `false, false, false, true` quadruple at line 306, glossed by its own inline
  comment "no null bytes, no control chars, no extended ASCII, normalize Unicode") — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` (lines
  300-309). Combined with the two claims above: under `strict()`, a raw CR in a `PARAMETER_VALUE`
  is rejected (via `CharacterValidationStage`) while its `%0D` encoding is admitted (via
  `DecodingStage`, which never reads `allowControlCharacters` for this branch) — the
  raw-versus-encoded asymmetry this plan closes.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Read directly at d385aa3 during this plan's authoring; this is a derived conclusion from the three claims above, each independently read.
- OBSERVED: `validateDecodedCharacters` (which contains `decodedControlCharacterForbidden`) is
  invoked once against the initially-decoded string and a second time against the
  Unicode-normalized form when normalization changes the string (`DecodingStage.java`:304-315,
  the call at line 315) — confirming deliverable 2's claim that no separate wiring is needed for
  the Step-3 re-check — read at the same file.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Read directly at d385aa3 during this plan's authoring.
- OBSERVED: `allowControlCharacters` is a `SecurityConfigurationBuilder` field defaulting `false`
  (line 111) with a boolean-toggle setter at line 312, the pattern deliverable 1 follows for the
  new flag — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java`.
  - verdict: corroborated | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Read directly at d385aa3 during this plan's authoring.
- HYPOTHESIS: adding a record component to `SecurityConfiguration` requires touching every
  positional `new SecurityConfiguration(...)` call site in `SecurityDefaults.java`
  (`STRICT_CONFIGURATION`, `LENIENT_CONFIGURATION`, `PARANOID_CONFIGURATION`; `DEFAULT_CONFIGURATION`
  goes through the builder and is unaffected) — confirm the full call-site count and whether any
  other production file constructs `SecurityConfiguration` positionally at
  `cui-http-core/src/main/java/de/cuioss/http/security/config/` (verify-at-outline: `grep -rn "new
  SecurityConfiguration("`). A refutation (a fourth positional call site elsewhere) widens
  deliverable 1's touched-file list but not its substance.
  - verdict: unverifiable | checked_at: d385aa3 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Not exhaustively greped across the whole module during this authoring pass; the three call sites named above were confirmed present, a fourth was not ruled out.

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java` — `decodedControlCharacterForbidden`, `isFormDataWhitespace`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfiguration.java` — the record component list and class Javadoc property bullets
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java` — the new setter and its field
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` — `STRICT_CONFIGURATION`, `LENIENT_CONFIGURATION`, `PARANOID_CONFIGURATION` (positional constructor call sites)
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/DecodingStageTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/config/SecurityConfigurationTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/config/SecurityConfigurationBuilderTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/config/SecurityDefaultsTest.java`
- HYPOTHESIS: `doc/adr/` — only if the raw-versus-encoded asymmetry closure, or the new published
  API surface (a new record component / builder method), is judged to warrant an ADR
  (verify-at-outline; ADR-0017 is the precedent for the asymmetry class, so extending it rather
  than filing a new ADR may be the right call — a judgment for outline, not this spec)

⛔ This plan does **not** edit any file under `doc/`, matching every other code plan in this epic.
The `doc/http-security/configuration.adoc` property-list update the source issue's proposal item 3
asks for is filed as an inbox message for WS-06 PLAN-13 (still staged) to absorb, not made by this
plan directly — declaring `doc/` here would collide with PLAN-13's already-declared `doc/` surface
for no benefit, since the two plans do not need to run concurrently to both land.

## Dependencies and Sequencing

- Depends on: none still staged. Reads (never edits) the config surface WS-01's four shipped plans
  (PLAN-01 #210, PLAN-02 #217, PLAN-03 #222, PLAN-15 #229) already landed; all ground truth above
  was verified against their landed state at `d385aa3`.
- Overlaps with: **PLAN-10** at `cui-http-core/src/test/java/de/cuioss/http/security/validation/DecodingStageTest.java`
  only (`corpus cross-check`, confirmed post-staging) — PLAN-10 declares the whole
  `security/validation/` directory as part of its test-generator-introduction deliverable
  (former PLAN-11 deliverable 7). **Must not pair with PLAN-10** if `parallelization_scope` is
  ever raised above 1; under the current `scope: 1` this is moot (nothing runs concurrently). No
  overlap with PLAN-07, PLAN-08, PLAN-12 or PLAN-13.
- Sequencing (operator directive): **queued immediately after PLAN-07** — disjoint from PLAN-07's
  `de.cuioss.http.forwarded` surface, so it is eligible to run in parallel with PLAN-07 if
  `parallelization_scope` is raised back to 2, or immediately after it under the current
  `scope: 1`. It is queued ahead of PLAN-08, PLAN-10, PLAN-12 and PLAN-13 in `plans[]` order.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-16-parameter-value-linebreak-carve-out.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree.**
  `adr-propose` is a standard finalize step that runs on EVERY plan. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then name the ADR (number and title) in this plan's inbox message. ADR high-water as of this
  cleanup pass (`d385aa3`) is **0022**.
- **The default-preserving contract is the acceptance bar, not a preference.** Every existing test
  in `DecodingStageTest.java`, `SecurityConfigurationTest.java`,
  `SecurityConfigurationBuilderTest.java` and `SecurityDefaultsTest.java` must pass unchanged with
  no edits other than additions — a change that requires editing an existing assertion has broken
  the "default `true` in every preset" contract and must be reconsidered, not the test relaxed.
- File the `doc/http-security/configuration.adoc` property-list note in this plan's inbox message
  for PLAN-13 (per deliverable 4's boundary), naming the property, its default, and a one-line
  description PLAN-13 can drop in directly.
- Record the raw-versus-encoded symmetry test (deliverable 3's asymmetry closure) explicitly in
  the PR body — it is this issue's sharpest finding and the evidence the fix actually closes it,
  not merely adds a new toggle.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

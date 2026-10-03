# PLAN-12: Javadoc Samples and API Prose

epic: quality-report-remediation
workstream: WS-06

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-12-javadoc-samples-and-api-prose.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Make every code sample in the Java sources compile against the real API and teach the real
contract. The "Security Integration" samples in the `client` and `client.adapter` package-info
files call no-arg constructors that do not exist, import `UrlSecurityException` from the wrong
package, and build their whole control flow on `Optional.empty()` meaning "attack detected" when
the validator's own Javadoc says it means "the input was null" — so a reader who copies them
believes headers are validated when nothing is. Around that sit a dozen further samples that do
not compile, feed whole query strings to the parameter-*value* pipeline, double-count monitoring
events, reference methods and classes that do not exist, or imply the library repairs input when
the security requirements forbid exactly that. Every fix is verified by compiling the sample.

## Deliverables

1. **The "Security Integration" samples compile and encode the real contract (F-documentation-2,
   F-client-6, part of F-B-12).** Both `package-info.java` samples call
   `new HTTPHeaderValidationPipeline()` and `new URLParameterValidationPipeline()` with no
   arguments; the real constructors take three and two arguments respectively. The
   `adapter/package-info.java` sample imports `de.cuioss.http.security.UrlSecurityException` while
   the class lives in `de.cuioss.http.security.exceptions` with a private, `@Builder`-only
   constructor. The `if (validated.isEmpty()) throw …` shape never fires on a real violation,
   because a violation throws. Rewrite both to the try/catch shape the contract actually implies.
2. **Every remaining non-compiling or wrong-pipeline sample (F-documentation-3, -16, -18,
   F-B-12).** `HTTPHeaderValidationPipeline`'s class Javadoc calls a two-argument constructor that
   does not exist. `URLParameterValidationPipeline`'s example passes `"user_id=123"` — a whole
   name=value pair — to the *value* pipeline, and `pipeline/package-info.java` passes
   `"search=test&page=1"`; both pass unrejected because `RFC3986_QUERY_CHARS` includes `=` and
   `&`, so a caller following them never gets the name-delimiter check that
   `URLParameterNameValidationPipeline` provides. `result/package-info.java`,
   `client/package-info.java` and `client/handler/package-info.java` reference `loadConfig`,
   `loadWithETag` and `JsonResponseConverter`, none of which exist, and the handler package's
   component list omits the public `RedirectPolicy` and `RedirectNotAllowedException`.
3. **Samples stop implying the library repairs input (F-documentation-25).**
   `UrlSecurityException`'s class Javadoc example sets `.sanitizedInput(…)` with a
   `"Removed script tags and special characters"` detail, and `exceptions/package-info.java` shows
   `case INVALID_CHARACTER -> sanitizeAndRetry();` — both contradicting
   `security-requirements.adoc`'s "must not attempt to fix invalid input" and
   `owasp-best-practices.adoc`'s "input is never stripped or repaired".
4. **The monitoring sample stops double-counting (F-documentation-17).**
   `monitoring/package-info.java` shows a manual `eventCounter.increment(e.getFailureType())` in a
   `catch (UrlSecurityException)`, but `AbstractValidationPipeline` already increments inside every
   factory-built pipeline's own catch before rethrowing, so a caller following the example counts
   every violation twice.
5. **The `validation` package-info describes the package it documents (F-documentation-13).** It
   attributes "input length and depth validation" to `LengthValidationStage`, but the depth logic
   (`EXCESSIVE_NESTING`, `MAX_PATH_SEGMENTS`, `MAX_DIRECTORY_DEPTH`) lives entirely in
   `NormalizationStage`; the stage list omits the public `AllowBlockListStage`,
   `CookiePrefixValidationStage` and `RequestCollectionValidator`. Similarly,
   `config/package-info.java` advertises configurable "Character Sets" and "Custom attack pattern
   definitions" while the builder exposes only boolean toggles and two literal block-list setters,
   the character sets themselves being compile-time `static final IntPredicate`s
   (F-documentation-26).
6. **The phantom specification reference and missing usage examples (F-documentation-20, -19).**
   Eighteen files carry `Implements: Task … from HTTP verification specification`; no such
   document exists anywhere in the repository. Remove or redirect the reference. Add the worked
   usage examples that `CLAUDE.md`'s "all public APIs must have Javadoc with usage examples" rule
   requires and that `SecureSSLContextProvider` and `StringContentConverter` lack.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that checked 28 findings in `05-documentation.adoc` and returned 27
corroborated and 1 unverifiable, with 0 contradicted.

- OBSERVED: both `package-info` samples call no-arg constructors while
  `HTTPHeaderValidationPipeline`'s only constructor is three-argument at line 121 and
  `URLParameterValidationPipeline`'s is two-argument at line 107; the wrong-package import is at
  `cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java`:284 and
  `UrlSecurityException`'s constructor is private at line 106 — read at
  `adapter/package-info.java` (lines 226-330), `client/package-info.java` (lines 296-317) and the
  three named classes. The report's original citations (import at 464, checklist at 583) were
  stale; the corrected locations are 284 and 434.
  - verdict: corroborated | checked_at: 2e9e8e0fa8b6dc7058205d3be973c55dafbe0e7f | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-read at 2e9e8e0: PLAN-08 (#240) touched adapter/package-info.java (rewrote the token-refresh example to the safe pattern, now Example 5 at ~lines 115-129) but did NOT fix the no-arg-constructor / wrong-package-import defect this claim cites - the wrong-package import (de.cuioss.http.security.UrlSecurityException) is now at line 290 (was 284) and the no-arg HTTPHeaderValidationPipeline()/URLParameterValidationPipeline() calls are now at lines 292/321/380/381. Substance fully holds; citations shifted ~6 lines. Re-derive exact lines at outline.
- OBSERVED: `HttpSecurityValidator`'s Javadoc states `validate()` returns `Optional.empty()` only
  for null input, so the samples' `.orElseThrow()` / `isEmpty()` branches never fire on a real
  attack — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/core/HttpSecurityValidator.java`
  (lines 109, 119).
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: the Javadoc site this claim cites is byte-identical across 73afd22..ca74911 - none of the three files PLAN-02/03/04 touched on this plan's surface moved this claim's lines.
- OBSERVED: `HTTPHeaderValidationPipeline`'s class Javadoc example at line 67 calls a two-argument
  constructor while the only constructor at line 121 takes three and throws for a non-header type
  — read at that file.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: the Javadoc site this claim cites is byte-identical across 73afd22..ca74911 - none of the three files PLAN-02/03/04 touched on this plan's surface moved this claim's lines.
- OBSERVED: `URLParameterValidationPipeline`'s example at line 61 calls
  `pipeline.validate("user_id=123")` and `pipeline/package-info.java`:70 calls
  `paramValidator.validate("search=test&page=1")`; `RFC3986_QUERY_CHARS` (built at
  `CharacterValidationConstants.java` lines 177-185) includes `=` and `&`, so both pass — read at
  those files. `URLParameterNameValidationPipeline`'s Javadoc (lines 31-38) states that only its
  own `DecodingStage` forbids the decoded delimiters.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: the Javadoc site this claim cites is byte-identical across 73afd22..ca74911 - none of the three files PLAN-02/03/04 touched on this plan's surface moved this claim's lines.
- OBSERVED: `loadConfig`, `loadWithETag` and `JsonResponseConverter` appear only inside
  `package-info` Javadoc comments and nowhere in main sources —
  `result/package-info.java`:30, :42, :65 and `client/package-info.java`:111;
  `client/handler/package-info.java`'s component list at lines 21-23 names only `HttpHandler`,
  `HttpStatusFamily` and `SecureSSLContextProvider` — read at those files.
  - verdict: corroborated | checked_at: 2e9e8e0fa8b6dc7058205d3be973c55dafbe0e7f | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 2e9e8e0: client/package-info.java was touched by PLAN-08 (#240) but this claim's cited JsonResponseConverter reference at line 111 is unmoved. result/package-info.java (loadConfig/loadWithETag) was not touched by any landing since ca74911. Substance and citations both hold.
- OBSERVED: `UrlSecurityException`'s example at lines 53-54 shows `.sanitizedInput(…)` with a
  "Removed script tags and special characters" detail, and `exceptions/package-info.java`:58 shows
  `case INVALID_CHARACTER -> sanitizeAndRetry();`; `doc/http-security/security-requirements.adoc`:105
  says "Must not attempt to \"fix\" invalid input" and
  `doc/http-security/owasp-best-practices.adoc`:103 says "input is never stripped or repaired" —
  read at all four.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-read at ca74911: UrlSecurityException's class-Javadoc example still shows .sanitizedInput("userscripttest1script") at line 53, unmoved despite PLAN-03 (#222) rewriting the class's redaction behaviour. ⛔ Note the claim gains force rather than losing it: PLAN-03 made messages emit (input: <redacted, length=N>) while getSanitizedInput() keeps returning raw as a documented opt-in, so a Javadoc example advertising sanitizedInput now sits beside a redaction contract it does not explain.
- OBSERVED: `monitoring/package-info.java`:47 shows the manual increment, while
  `AbstractValidationPipeline` already increments at lines 93-95 — read at both. The report cited
  lines 17-31; the actual call resolves to line 47.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: the Javadoc site this claim cites is byte-identical across 73afd22..ca74911 - none of the three files PLAN-02/03/04 touched on this plan's surface moved this claim's lines.
- OBSERVED: `validation/package-info.java`:25 attributes depth validation to
  `LengthValidationStage`, which contains no depth logic; `EXCESSIVE_NESTING`,
  `MAX_PATH_SEGMENTS` (line 182) and `MAX_DIRECTORY_DEPTH` (line 188) are thrown only from
  `NormalizationStage` (lines 395-400, 450-455); the stage list omits three public types present
  in the same package directory — read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: the Javadoc site this claim cites is byte-identical across 73afd22..ca74911 - none of the three files PLAN-02/03/04 touched on this plan's surface moved this claim's lines.
- OBSERVED: `config/package-info.java`:33 and :35 advertise "Character Sets" and "Custom attack
  pattern definitions" as configurable, while `SecurityConfigurationBuilder` exposes only boolean
  toggles (lines 279-320) plus `blockedPathPatterns` / `blockedParameterNames`, and the character
  sets are `static final IntPredicate`s in `CharacterValidationConstants` — read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-read at ca74911: config/package-info.java still advertises 'Character Sets' at line 33 and 'Pattern Configuration - Custom attack pattern definitions' at line 35, unmoved despite PLAN-02 (#217) touching the file. ⛔ But the referent moved underneath: PLAN-15 (#229) DELETED eleven unenforced constants from this package, so 'Custom attack pattern definitions' now advertises less than it did. Re-read the package contents, not just these two lines, at outline.
- OBSERVED (derived count, re-derived by the verification pass): exactly **18** files under
  `cui-http-core/src/main` carry the phrase "HTTP verification specification"; a search for that
  phrase or a `Task [A-Z][0-9]` pattern across `doc/`, `README.adoc` and `CLAUDE.md` returns zero
  matches — no such document exists.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: the Javadoc site this claim cites is byte-identical across 73afd22..ca74911 - none of the three files PLAN-02/03/04 touched on this plan's surface moved this claim's lines.
- OBSERVED: neither `SecureSSLContextProvider` nor `StringContentConverter` carries a `<pre>`
  block — only inline `{@code}` tokens — so neither has a worked usage example. This was
  spot-checked on the two classes the report's recommendation names; the full 72-class sweep is
  part of this plan's own deliverable 6.
  - verdict: corroborated | checked_at: 2e9e8e0fa8b6dc7058205d3be973c55dafbe0e7f | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at 2e9e8e0: StringContentConverter.java was touched by PLAN-08 (#240, charset-parsing fix) but still carries no <pre> block - only inline {@code} tokens. Claim substance holds unchanged despite the file being edited for an unrelated deliverable.
- Verify-first clause: before scoping any deliverable, re-read every sample at the then-current
  HEAD. WS-01 through WS-04 land first and change the APIs these samples demonstrate — PLAN-04
  in particular decides whether `PipelineFactory` still promises XSS detection, and PLAN-08
  rewrites the token-refresh guidance in `CacheKeyHeaderFilter` and `adapter/package-info.java`.
  A sample already corrected by an earlier plan is removed from this plan's scope rather than
  rewritten twice.
  - verdict: corroborated | checked_at: 2e9e8e0fa8b6dc7058205d3be973c55dafbe0e7f | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Confirmed at 2e9e8e0: PLAN-08 (#240) rewrote both CacheKeyHeaderFilter.java's Javadoc (the 'fine-grained control'/'maintaining security'/'Solves token' phrases are gone) and adapter/package-info.java's token-refresh sample (now Example 5, safe pattern). The premise held. No PLAN-12 deliverable targets this sample directly (it was only a verify-first caveat, never a numbered deliverable here), so nothing in this plan's scope needs removing - the clause is discharged with no scope change.

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/handler/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/result/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/monitoring/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/exceptions/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/HTTPHeaderValidationPipeline.java` — class Javadoc only
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterValidationPipeline.java` — class Javadoc only
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/exceptions/UrlSecurityException.java` — class Javadoc only
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/handler/SecureSSLContextProvider.java` — class Javadoc only
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/converter/StringContentConverter.java` — class Javadoc only
- OBSERVED: the 18 files under `cui-http-core/src/main` carrying "HTTP verification specification" — Javadoc only

⛔ This plan edits **Javadoc comments only**. It changes no executable statement, no signature and
no annotation. If a sample cannot be made to compile without an API change, that is a
WS-01..WS-04 deliverable — file an inbox message rather than changing the API here. This plan also
edits no `.adoc` or `.md` file; PLAN-13 owns those.

## Dependencies and Sequencing

- Depends on: WS-01, WS-02, WS-03 and WS-04 — every code workstream. Most of these samples
  demonstrate APIs those plans change, and rewriting a sample before its API settles guarantees a
  second rewrite.
- Overlaps with: every code plan at file granularity, because this plan edits Javadoc inside the
  same `.java` files. It must **not** run concurrently with any of them — which is the second,
  independent reason for its last-in-queue placement.
- Adjacent to: PLAN-13, which owns the `.adoc` and `.md` set. The two are surface-disjoint at file
  extension and MAY run concurrently with each other at the epic's scope of 2.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-12-javadoc-samples-and-api-prose.md"
```

## Execution Constraints

- **Every rewritten sample must be compiled before the plan reports it fixed.** Extract each
  sample into a scratch source file, compile it against the built `cui-http-core` classes, and
  keep the compiling form. A sample that was only read carefully is not verified — F-documentation-2
  exists because exactly that was done last time.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction.
- TDD in the ordinary sense does not apply to a comment-only change; the compile check above is its
  replacement, and it is mandatory.
- Deliverable 6's missing-usage-example sweep covers all public types, not only the two named
  classes. Report the count of types checked and the count of examples added.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

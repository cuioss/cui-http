# PLAN-13: AsciiDoc Specifications, Requirements, Traceability and ADRs

epic: quality-report-remediation
workstream: WS-06

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-13-asciidoc-specs-requirements-adrs.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Reconcile the AsciiDoc and Markdown set with the code, after every code workstream has landed.
The sharpest defects are documents that assert protections the library does not provide and are
then marked VERIFIED: `security-requirements.adoc` demands allow-list validation, output
encoding, access-control verification, ReDoS budgets and circuit breakers — none implemented —
and `compliance-traceability.adoc` marks OWASP A03, A04, A05 and ISO A.12.2 "✅ VERIFIED" against
exactly those requirements; SEC-14 claims defaults ship "maximum security checks active" and
"minimal allowed character sets" and is contradicted by a NOTE twenty lines later in its own
file. Around that sit stale test counts, a false log-coverage claim, CWE citations pointing at
tests that contain no such reference, a pipeline-selection matrix routing host and IPv6 concerns
to a pipeline that rejects `[` as an invalid character, two ADRs that contradict each other about
the Windows literals, and fifteen ADRs still marked `Status: Proposed` behind an index that
disagrees with itself about how many there are.

## Deliverables

1. **Requirement and traceability documents stop asserting what the library does not do
   (F-documentation-14, -15, -6).** Correct or explicitly mark NOT IMPLEMENTED: SEC-1's allow-list
   demand against a block-list `PatternMatchingStage`; SEC-2's output-encoding and access-control
   layers, which exist nowhere; SEC-9/SEC-10's ReDoS budgets and circuit breakers; SEC-14's
   "maximum security checks" claim against its own contradicting NOTE; HTTP-10's character-set
   restrictions against the actual `allowExtendedAscii` behaviour; HTTP-19's "maximum decoding
   iterations" and "maximum normalization passes" against a single `URLDecoder.decode` call. Then
   correct every `compliance-traceability.adoc` row that marks a containing standard VERIFIED on
   that basis, and fix its stale evidence — the CWE-178 and CWE-73 attributions cite test classes
   containing no CWE reference at all, and three line citations are each off by one.
2. **The pipeline-selection matrix routes to pipelines that can do the job (F-documentation-10,
   F-B-11, F-documentation-8).** `pipeline-architecture-standards.adoc` describes
   `URLPathValidationPipeline` as covering host-based attacks, host/domain exploits and IPv6
   parsing, with an example of `[::ffff:127.0.0.1]/../../../etc/passwd` — but `RFC3986_PATH_CHARS`
   never adds `[` or `]`, so that example dies as `INVALID_CHARACTER` before any host logic, and
   the package's own Javadoc says the pipeline is "not a validator for a full absolute URL".
   Similarly, `http1-vulnerabilities-analysis.adoc` credits `CharacterValidationStage` with
   detecting `%0d%0a` and embedded HTTP verbs, neither of which it does.
3. **Counts, coverage claims and inventories re-derived (F-documentation-4, -7, -21, -27,
   F-D-8/F-documentation-5, F-D-9, F-E-8, F-E-9).** `LogMessages.adoc`'s test-coverage note is
   false for seven WARN records beyond the one honest exception (HTTP-108, 110, 111, 112, 113,
   114, 120); `forwarded-header-resolution.adoc` still describes the catalogue as HTTP-120..125
   when HTTP-126 exists; the analysis documents claim 395 and 553 smuggling tests against an
   actual 1435 generated cases, and 213 and 50 cookie tests against an actual 164;
   `test-generators-readme.adoc` claims all databases implement `AttackDatabase` when three
   implement `LegitimatePatternDatabase` and one database is missing from the list;
   `security-readme.adoc` lists 3 of 5 pipelines and 5 of 8 stages; `README.adoc`'s documentation
   index does not reach `configuration.adoc`, `doc/adr/README.md` or
   `test-framework-structure.adoc` directly.
4. ~~**Resolve the duplicate ADR-0016.**~~ ✅ **RESOLVED OUT OF BAND — 2026-09-07, PR #213
   (`09818a0`). This deliverable is CLOSED; do not re-do it.** The operator directed the fix inline
   rather than waiting for this plan, which sits last in the queue. What landed, so this plan can
   verify rather than repeat:
   - `0016-CI_harden-runner_egress_allowlists_…adoc` → **`0018-…`** via `git mv`, level-0 heading
     renumbered to match. `0016-Absolute-path_dot-dot_walking_…adoc` kept `0016` (earlier-landed,
     and every in-repo `ADR-0016` reference already resolved to it — `NormalizationStage`, its
     test, ADR-0017's xref — so no reference moved).
   - `doc/adr/README.md` gained the missing row for **18**, and the high-water sentence moved to
     18. The compounding defect (PLAN-14 never adding its index row) is closed with it.
   - The **`runs to 14`** sentence in § Observed evidence was rewritten, so the 37-vs-73
     self-contradiction that deliverable 5 carries is ALREADY GONE. ⛔ Re-read the index at outline
     before asserting it: deliverable 5's claim about that sentence is stale as of `09818a0`.
   - ADR-0014's supersession was consolidated in the same PR: its Status is now `Superseded` (the
     `manage-adr` enum, kept as its own paragraph so the scanner's first-paragraph parse reads it)
     with the scope stated — the root-clamp half is superseded by ADR-0016, the
     scheme-bearing-input half still stands. **`doc/adr/` now holds 18 files at 18 distinct
     numbers**, and one ADR reads `Superseded`.
   - **Still open for THIS plan:** ADR-0018 remains `Proposed`, as do all the others deliverable 5
     covers. Nothing about the *status advance* was done here.
4b. **⛔ REOPENED 2026-09-08 — `doc/adr/README.md` is structurally unmaintained, not
   occasionally stale (folded from the PLAN-02 landing, PR #217 `bdcb36e`).** Deliverable 4's
   original instance was closed out of band by PR #213 on 2026-09-07. **It recurred within one
   plan.** PLAN-02 shipped **ADR-0019** and **ADR-0020** and updated the index for neither, so at
   `bdcb36e` the index runs to 17 rows and still asserts "the highest allocated number is **18**"
   while `doc/adr/` holds 20 records. This is the third consecutive wave (PLAN-01/PLAN-14 →
   0016; PLAN-02 → 0019/0020 unindexed), so the remit here is wider than a one-time repair:
   - **Re-derive the whole index from `doc/adr/*.adoc` at outline**, do not patch the rows you
     happen to know about. Every count, the high-water sentence, and every Status cell.
   - **Status cells are now genuinely mixed** — ADR-0014 reads `Superseded in part by 16`,
     ADR-0017 `Accepted`, ADR-0002 is `Superseded` on PLAN-06's branch, and the rest `Proposed`.
     Deliverable 5's status advance and this re-derivation must be done as ONE pass, not two.
   - **Propose a mechanism, or record why not.** Two hand-maintenance failures in four days is
     the evidence that "update the table in the same change" does not hold. A generated index,
     or a check that fails when `doc/adr/*.adoc` and the table disagree, is in scope for a
     proposal here even if implementing it is another plan's.

4c. **Reconcile the character-set prose to what PLAN-02 decided (carry-forward from its landing).**
   The decided state, authoritative as of `bdcb36e`: query = unreserved + `?&=!$'()*+,;` + `/ : @`;
   **cookie-name = RFC 7230 token** (NOT cookie-octet — that was the CodeRabbit-caught defect);
   cookie-value = RFC 6265 cookie-octet with DQUOTE rejected outright; C0/C1 unconditional for
   header and cookie types; `allowExtendedAscii` default **false**, gating 160-255 and all
   Unicode >255 for `HEADER_VALUE`/`BODY`. ADR-0019 and ADR-0020 record the reasoning. ⛔ Read
   the code, not this bullet, at outline — this is a lead carried across a landing.

4d. **Correct five stale passages in `doc/forwarded-header-resolution.adoc` (folded from PLAN-06's
   inbox finding `plan-06-forwarded-trust-model-001.md`, filed mid-flight 2026-09-07).** PLAN-06
   consulted but deliberately did not edit this document, which this plan owns. Line numbers are
   PLAN-06's, against the doc as it read it — **re-anchor at outline**, and note PLAN-06's own PR
   had not merged when this was folded, so verify each against landed code:
   - **Line 449-451 (highest severity) — the sample fails open to cleartext.**
     `fwd.scheme().orElse("http")` turns the resolver's fail-closed scheme-drop signal into a
     cleartext assumption, and line 451 derives the default port from it. PLAN-06 removed the
     identical sample from the resolver Javadoc and package docs; this copy survived. Show the
     absent case as an explicit caller decision, never a literal `"http"`.
   - **Lines 239-245 — the blanket unresolvable-header rule is obsolete.** ADR-0019 (PLAN-06's,
     see the collision note in deliverable 4b) replaces it: an unresolvable header suppresses only
     the fields whose directives the parser actually reached.
   - **Lines 175-186 — the precedence table's first-present-wins framing.** Scheme, host and port
     de-facto families are no longer ordered; they resolve independently and reconcile before the
     RFC 7239 comparison, with `ForwardedResolverConfig.deFactoPrecedence()` deciding a
     disagreement. Ordered precedence survives only for the context path. **State the deployment
     obligation**: the ingress must strip the family it does not itself write.
   - **Lines 183, 214-220 — host and port described as one comparison.** Now independent fields;
     a host disagreement drops only the host, a port disagreement only the port.
   - **Lines 36-51 — the security-precondition WARNING understates the API.** A second overload
     `resolve(Function<String,List<String>>, InetAddress)` enforces in code what the warning asks
     the deployment to guarantee by network placement. Present it as the preferred form, with
     network controls as defence in depth.

4e. **⛔ `doc/http-security/configuration.adoc` now carries TWO different obligations from two
   plans that ran CONCURRENTLY (folded 2026-09-09).** Read both before touching the file:
   - **PLAN-04 already corrected it at :42-44** (PR #227, `65dcb29`), as part of an
     operator-approved override of its own `doc/` exclusion. ⛔ **Do NOT re-derive or duplicate that
     correction.**
   - **PLAN-15 left it stale at ~:275-276** (PR #229, `b09925a`): it names `DANGEROUS_HEADER_NAMES`
     and the three content-type sets, **all eleven of which PLAN-15 deleted outright**. That half is
     yours to reconcile.

4f. **⛔ Three further `doc/` files were corrected by PLAN-04 — do NOT re-derive them.** All in PR
   #227, all because composing length + character stages into the content-type pipeline falsified a
   single-stage claim no deliverable owned:
   - `doc/http-security/specification/pipeline-architecture-standards.adoc` (:117) — ⛔ **the
     pipeline-selection matrix `CLAUDE.md` directs every agent to consult**
   - `doc/http-security/specification/specification.adoc` (:199, :203-204)
   - `doc/http-security/README.adoc` (:130-131)

   `F-documentation-15` (the `PipelineFactory` Javadoc retraction) remains yours — it does not cover
   the stage-composition change. `security-requirements.adoc` and `functional-requirements.adoc` were
   read but deliberately left untouched by PLAN-04.

4g. **The ADR index is now FIVE divergences behind, and the ADR body set has grown.** At `b09925a`:
   **22** `.adoc` files against 18 index rows, the high-water sentence still says 18, rows for
   **0019, 0020, 0021 and 0022** are missing, and ADR-0002's row reads `Proposed` while the file
   reads `Superseded`. ⛔ Re-derive the whole index from `doc/adr/*.adoc` — do not patch rows.
   ✅ One positive datum for the mechanism proposal: PLAN-04 allocated ADR-0022 cleanly by
   re-checking against `origin/main` **at the merge gate**, not merely at authoring time. That is the
   only control that has ever caught this class, and it is worth encoding rather than reinventing.

4h. **A published-API break shipped with NO ADR (PLAN-15) — decide whether the index should say so.**
   Eleven `public static final` constants were deleted from the exported
   `de.cuioss.http.security.config`. The operator was offered an ADR recording the deliberate break
   and declined it; the decision is **settled and must not be reopened**. But the ADR set is this
   plan's subject, and a reader looking for why a published constant vanished will find nothing.
   `landings/PLAN-15.md` is currently the only durable trace. Raising this as a documentation
   question is in scope; re-litigating the deletion is not.

4i. **⛔ Two `doc/` files were corrected by PLAN-08 OUTSIDE its own declared boundary (folded
   2026-09-16 from the PLAN-08 landing) — do NOT re-derive them from the original report, and note
   the boundary violation itself is this plan's to raise.** `doc/client-handlers-readme.adoc` and
   `doc/http-result-pattern.adoc` (PR #240, `2e9e8e0`) were both edited despite PLAN-08's own
   Write-Boundary explicitly excluding both, and despite PLAN-08's own PR body independently
   claiming no `doc/` file other than its new ADR was touched — a claim its own diff contradicts.
   The edits are small (6 lines total) and correct per the landed code; re-read both files at
   outline to confirm their current state before writing over them, and record in this plan's own
   PR that the correction already happened once, outside process. `doc/adr/0023-…adoc` is new
   (PLAN-08's ADR, not a correction to re-derive) and must be indexed per deliverable 5/4g below.

5. **ADR set reconciled (F-documentation-11, -12, F-A-12, F-E-10, F-D-20).** ADR-0006 says the
   Windows literals moved to `SENSITIVE_PATH_PATTERNS` as bare segments while ADR-0015 says they
   kept their backslash-delimited spelling; the code shows backslash-delimited, so ADR-0006 is
   wrong. The ADR index says both "the highest allocated number is 15" and "the index above runs
   to 14"; every one of the fifteen ADRs reads `Status: Proposed` despite describing implemented,
   tested behaviour; the index is not linked from `README.adoc`. ADR-0002 describes reconciliation
   as covering "scheme, host, and client IP", omitting port, which the resolver also reconciles.
   Advance the statuses, fix the contradictions, and link the index.
6. **Specification self-contradictions and remaining prose drift (F-documentation-9, -22, -24,
   -28, F-D-7 prose half, F-client-5, F-client-11 prose halves).** `specification.adoc` says three
   security profiles in one place and four in another, and claims body validators exist when
   `PipelineFactory` throws for `BODY`. `forwarded-header-resolution.adoc` claims the header-value
   pipeline collapses `//host` to `/host`, which it cannot — that pipeline has no
   `NormalizationStage` — and its host-rejection description omits `@`, `#` and `?`. The release
   skill misstates when the CI build check runs, because the branch filter it cites applies only
   to the `push:` trigger while the `pull_request:` trigger runs unconditionally.
   `http-result-pattern.adoc` contradicts itself two paragraphs apart about `AtomicReference`.
   `functional-requirements.adoc`'s HTTP-2 omits that `org.jspecify` is `requires transitive`.
   Two documents deviate from the project's own AsciiDoc standard by omitting `:sectnums:` /
   `:source-highlighter:` and by using `<<anchor,text>>` where `agents.md` prescribes `xref:`.
7. **The build-command fork (F-documentation-1) and the site link (F-documentation-23).**
   `CLAUDE.md`, `agents.md`, `README.adoc` and `testing.adoc` mandate
   `.plan/execute-script.py`, which is present on disk but untracked — `.gitignore` whitelists only
   six files under `.plan/`, so a fresh clone cannot run the mandated commands, and the documents
   forbid the working `./mvnw` fallback without saying what to do when the executor is absent.
   This is a genuine fork (commit the executor vs. document the fallback); surface it to the
   operator. Separately, settle `F-documentation-23` — the only finding in the whole report the
   verification pass could not decide statically.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that checked 28 findings in `05-documentation.adoc` and returned 27
corroborated and 1 unverifiable, with 0 contradicted, re-deriving the flagged counts
independently.

- OBSERVED: `security-requirements.adoc` SEC-1 (line 25) demands allow-listing while
  `PatternMatchingStage` is entirely block-list based; SEC-2 (lines 37-40) requires output-encoding
  and access-control layers that exist nowhere; SEC-9 (line 148) and SEC-10 (line 163) demand
  ReDoS budgets and circuit breakers, neither implemented; SEC-14 (lines 226-231) is contradicted
  by the NOTE at lines 234-250 admitting `defaults()` ships `failOnSuspiciousPatterns=false` and
  `allowExtendedAscii=true`. SEC-11/12/13 carry explicit NOT IMPLEMENTED notes; SEC-1/2/6/9/10/14/16
  carry none — read at that file and at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java`.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: `compliance-traceability.adoc` marks A03, A04, A05 and A.12.2 "✅ VERIFIED" against
  those requirements; its CWE-178 attribution (line 199) and CWE-73 attribution (line 330) cite
  `HomographAttackDatabaseTest` and `ProtocolHandlerAttackTest`, in which a `CWE` grep returns
  zero matches; three `PathTraversalAttackTest` line citations are each off by one (65 not 64, 210
  not 209, 218 not 217) — read at that file and those tests.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: `functional-requirements.adoc` HTTP-10 (lines 147, 150) restricts path segments and
  header values to sets narrower than `CharacterValidationStage` enforces (lines 359-380) under
  the actual `allowExtendedAscii = true` default (`SecurityConfigurationBuilder.java`:97);
  HTTP-19 (lines 282-283) claims decoding-iteration and normalisation-pass limits while
  `DecodingStage.decodeForValidationType` performs exactly one `URLDecoder.decode` call at line
  270; `specification.adoc`:142 nonetheless marks the area IMPLEMENTED — read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: `pipeline-architecture-standards.adoc` lines 14, 20, 22 and its line-38 example
  `[::ffff:127.0.0.1]/../../../etc/passwd`; `RFC3986_PATH_CHARS` (built at
  `CharacterValidationConstants.java` lines 167-175) never adds `[` or `]`;
  `pipeline/package-info.java` states the pipeline is "not a validator for a full absolute URL" —
  read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: `http1-vulnerabilities-analysis.adoc` lines 63 and 152 credit
  `CharacterValidationStage` with `%0d%0a` detection and embedded-verb detection;
  `CharacterValidationStage` sets `allowPercentEncoding=false` for header types at lines 166-169
  so `%` is a plain set member never decoded, `HTTPHeaderValidationPipeline.createStages` wires no
  `DecodingStage` (lines 135-147), and a verb grep over the stage returns nothing — read at those
  files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED (derived count, re-derived): `LogAsserts` calls across `cui-http-core/src/test/java`
  resolve only HTTP-106, 115, 116, 117 and 121-126; HTTP-108, 110, 111, 112, 113, 114 and 120 have
  zero matches — exactly **7** falsely-claimed records beyond the honestly-documented HTTP-107
  exception at `doc/LogMessages.adoc` lines 151-157.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED (derived counts, re-derived): `HttpRequestSmugglingAttackTest` has 8
  `@TypeGeneratorSource` methods totalling **1435** generated cases against the documents' claimed
  395 and 553; `CookieChaosAttackTest` sums to **164** against the claimed 213 and 50 — read at
  those test classes and at `doc/http-security/analysis/`.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: `forwarded-header-resolution.adoc`:166 states HTTP-120..125 while
  `ForwardedLogMessages` defines `identifier(126)` (`TRUSTED_PROXY_ENTRY_BLANK`) at lines 78-82,
  correctly listed in `LogMessages.adoc`:143-146; the same document at :372 omits `@`, `#` and `?`
  from the host-rejection description, and at :366-369 claims the header-value pipeline collapses
  `//host` to `/host` — refuted by `HTTPHeaderValidationPipeline.createStages` (lines 144-147) and
  by the same document's own lines 386-389 — read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: ADR-0006 (lines 154-156) says the Windows literals became bare segments while ADR-0015
  (lines 171-178) says they kept the backslash-delimited spelling; `SecurityDefaults.java`:166-168
  shows `"\windows\"`, `"\system32\"`, `"\users\"`, `"\program files\"` — backslash-delimited,
  confirming ADR-0015 — read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- ⛔ RE-SCOPED at the 2026-09-06 cleanup (was OBSERVED at `d242ba5`; **CONTRADICTED at `73afd22`**).
  The original claim read: "`doc/adr/README.md`:35 says the highest allocated number is 15 while
  :71 says the index runs to 14; all fifteen rows read `Status: Proposed`". PLAN-01's landing
  (`7a0da52`) edited `doc/adr/README.md` directly to add rows for its own ADR-0016 and ADR-0017,
  and PR #213 (`09818a0`) edited it again for the duplicate-0016 resolution. ⛔ **Both halves of
  the original claim are now FALSE and MUST NOT be carried into the fix.** At `09818a0` the index
  carries **18 rows**, the highest-allocated-number sentence says **18**, row 17 reads
  **`Accepted`** and row 14 reads **`Superseded in part by 16`**, so "all fifteen rows are
  Proposed" is false; and the **"runs to 14" sentence was rewritten by #213**, so the
  self-contradiction between the two sentences is **GONE** — it is no longer this plan's to fix.
  What REMAINS of this claim for deliverable 5: sixteen of the eighteen rows still read `Proposed`
  while describing implemented, tested behaviour, and the index is still not linked from
  `README.adoc`.
  - verdict: contradicted | checked_at: 2e9e8e0fa8b6dc7058205d3be973c55dafbe0e7f | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Re-measured at 2e9e8e0: doc/adr/ now holds 23 .adoc files at 23 distinct numbers (0001-0023, no duplicate) - PLAN-08 added ADR-0023. Index still carries 18 rows and still asserts 'highest allocated number is 18' while real high-water is 0023. Missing rows now: 0019, 0020, 0021, 0022, 0023 - SIX divergences, not five. Re-derive the whole index from doc/adr/*.adoc at outline; do not patch rows.
- OBSERVED (re-derived at `ca74911`): `doc/adr/` holds **22 `.adoc` files at 22 DISTINCT numbers**
  (0001-0022, no duplicate). Four ADRs landed since the last re-grounding: **0019** and **0020**
  (PLAN-02, #217), **0021** (PLAN-06, #214) and **0022** (PLAN-04, #227). `README.adoc`'s
  Documentation section (lines 135-159) still contains no link to `doc/adr/README.md`,
  `doc/http-security/configuration.adoc` or `doc/test-framework-structure.adoc` — that half is
  unaffected and is the half that survives. ⛔ Re-derive the count again at outline.
  - verdict: contradicted | checked_at: 2e9e8e0fa8b6dc7058205d3be973c55dafbe0e7f | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Re-derived at 2e9e8e0: doc/adr/ holds 23 .adoc files at 23 DISTINCT numbers (0001-0023, no duplicate). ADR-0023 (PLAN-08, #240) landed since the ca74911 count. README.adoc missing-links half is UNCHANGED and still holds. Re-derive the count again at outline - it moves on every adr-propose fire.
- OBSERVED: `specification.adoc`:124 names three profiles while :339-342 lists four and
  `SecurityDefaults.java`:456 defines `PARANOID_CONFIGURATION`; :297 claims body validators exist
  while `PipelineFactory.java`:253-254 throws for `BODY` — read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: `.claude/skills/release/SKILL.md`:81-83 states the branch-prefix filter governs
  whether the CI build check runs, but `maven.yml`:8 confines that filter to the `push:` trigger
  while `pull_request: branches: [main]` at lines 10-11 runs unconditionally — read at both.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: `test-generators-readme.adoc`:35 claims all databases implement `AttackDatabase`, but
  `EdgeCaseValidURLsDatabase`, `LegitimateSpecialCharactersDatabase` and
  `LegitimatePathPatternsDatabase` implement `LegitimatePatternDatabase`, and
  `PathParameterTraversalAttackDatabase` is absent from the enumerated list;
  `security-readme.adoc` lines 33-49 list 3 of 5 pipelines and lines 51-68 list 5 of 8 stages —
  read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- OBSERVED: `module-info.java`:20 declares `requires transitive org.jspecify` while
  `functional-requirements.adoc`:45 omits the word for that dependency alone;
  `configuration.adoc` lines 1-3 omit `:sectnums:` and `:source-highlighter:` and use
  `<<Seeding the lists>>`, and `compliance-traceability.adoc` lines 14-18 use five `<<anchor,text>>`
  references, against `agents.md`:114-121 — read at those files.
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911. The requirement, specification and traceability documents this claim cites were NOT touched by any landing between 73afd22 and ca74911 - the only doc/ files that moved are doc/adr/README.md and the four doc/http-security files PLAN-04 (#227) corrected under an approved override, plus README.adoc, CLAUDE.md and agents.md. ⛔ Those four PLAN-04 corrections are recorded in this spec as deliverable 4f and must NOT be re-derived.
- HYPOTHESIS: `F-documentation-23` — `about.adoc`:31 links to `index.html`, and
  `cui-http-core/src/site/asciidoc/` contains only `about.adoc`, so no source produces that target
  directly. Whether `maven-site-plugin` synthesises `index.html` from the POM description at
  site-generation time cannot be settled by static inspection. Confirm/refute by running the site
  build and listing the generated output directory (verify-at-outline). **This is the only finding
  in the entire 126-item report that the epic's ground-truth pass could not decide; it is
  therefore the epic's only candidate refutation.** The report itself records it as SUSPECTED, not
  CONFIRMED.
  - verdict: unverifiable | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at ca74911: the site build this clause needs has not been run, and the procedural verify-first directive remains partially discharged by this pass.
- Verify-first clause: before scoping deliverables 1-5, re-read every cited document AND the code
  it describes at the then-current HEAD. This plan runs last by design: WS-01 through WS-04 change
  the behaviour these documents describe, and several of this plan's corrections are "state what
  the code now does" rather than "state what the code did at `0bad295`". A correction written
  against the report's snapshot rather than against the landed code is a new defect.
  - verdict: unverifiable | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at ca74911: the site build this clause needs has not been run, and the procedural verify-first directive remains partially discharged by this pass.

## Expected Surface

- OBSERVED: `doc/` — the whole documentation tree, in particular `doc/http-security/security-requirements.adoc`, `doc/http-security/functional-requirements.adoc`, `doc/http-security/specification/compliance-traceability.adoc`, `doc/http-security/specification/specification.adoc`, `doc/http-security/specification/pipeline-architecture-standards.adoc`, `doc/http-security/specification/testing.adoc`, `doc/http-security/configuration.adoc`, `doc/http-security/analysis/`, `doc/http-security/owasp-best-practices.adoc`, `doc/LogMessages.adoc`, `doc/forwarded-header-resolution.adoc`, `doc/http-result-pattern.adoc`, `doc/security-readme.adoc`, `doc/test-generators-readme.adoc`, `doc/test-framework-structure.adoc`, `doc/client-handlers-readme.adoc`
- OBSERVED: `doc/adr/` — `README.md` and all **18** ADR files (18 distinct numbers as of `09818a0`; the 0016 duplicate was resolved by PR #213). ⛔ Re-count at outline: this figure moves whenever any plan's `adr-propose` fires
- OBSERVED: `README.adoc` — the Documentation index section at lines 135-159
- OBSERVED: `CLAUDE.md`, `agents.md` — the build-command mandate
- OBSERVED: `.claude/skills/release/SKILL.md` — lines 81-83
- HYPOTHESIS: `cui-http-core/src/site/asciidoc/about.adoc` — edited only if the site build shows `index.html` is not generated (verify-at-outline)
- HYPOTHESIS: `.gitignore` — edited only if deliverable 6's fork resolves toward committing the executor (verify-at-outline)

⛔ This plan edits **no `.java` file**, not even a Javadoc comment. PLAN-12 owns the Java prose.
Where a document is wrong because the code is wrong, the code fix belongs to WS-01..WS-04 and has
already landed by the time this plan runs; this plan only reconciles the prose to it.

## Dependencies and Sequencing

- Depends on: WS-01, WS-02, WS-03, WS-04 and WS-05 — every other workstream. Most of these
  documents describe behaviour those plans change.
- Overlaps with: nothing. `doc/**`, `README.adoc`, `CLAUDE.md`, `agents.md` and
  `.claude/skills/**` are touched by no other plan in the epic, which makes this the epic's best
  concurrency partner for a late code plan.
- Adjacent to: PLAN-12, which owns Javadoc in the `.java` files. Surface-disjoint by file
  extension; the two MAY run concurrently at the epic's scope of 2.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-13-asciidoc-specs-requirements-adrs.md"
```

## Execution Constraints

- **Every numeric claim this plan writes must be re-derived from the code or the test sources at
  the current HEAD**, never copied from the report or from the document being corrected. The
  report's own counts were partly stale at the time it was written (33 generators when there are
  32; 24 failure types when there are 25) and every code plan has since moved the numbers again.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction.
- Seven deliverables is over the epic's split guard, and the split-or-proceed rationale is recorded as
  an epic decision: the whole set is one file tree with a single reviewer audience, deliverables 1
  and 3 cross-reference each other's numbers, and splitting would produce two plans both editing
  `compliance-traceability.adoc` and `README.adoc`.
- Deliverable 7 contains a genuine fork (commit the build executor vs. document the `./mvnw`
  fallback). Surface it to the operator; do not decide it silently.
- `F-documentation-23` is the epic's only candidate refutation. Whichever way it resolves, record
  the outcome explicitly in the PR body — a refutation is as much a deliverable as a fix.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

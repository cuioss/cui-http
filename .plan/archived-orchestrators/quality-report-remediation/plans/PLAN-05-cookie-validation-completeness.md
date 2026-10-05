# PLAN-05: Cookie Validation Completeness

epic: quality-report-remediation
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-05-cookie-validation-completeness.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Make `validateCookie` validate the cookie. Today `CookiePrefixValidationStage.validateCookie`
checks the name for surrounding whitespace and applies the `__Host-` / `__Secure-` attribute
rules — and touches `cookie.value()` nowhere at all; `PipelineFactory` throws
`IllegalArgumentException` for `COOKIE_NAME` and `COOKIE_VALUE` with a "not yet implemented"
message, so no cookie-content pipeline exists anywhere in the library. The prefix match is
case-sensitive, which the class Javadoc defends as "per RFC" while RFC 6265bis and every major
browser match case-insensitively, and a test named `shouldBeCaseSensitive` pins the wrong
behaviour. `Cookie.hostPrefix` / `securePrefix` validate only the name suffix and then store the
value entirely unvalidated, while the class Javadoc's own example writes the result straight into
a `Set-Cookie` header — a response-splitting path. Attribute lookup is first-wins where RFC 6265
§5.3 is last-wins, so `__Host-a=1; Secure; Path=/; Path=/admin` passes a check a browser would
fail. Every change is driven by a regression test written and seen to fail first.

## Deliverables

1. **Cookie prefix matching becomes case-insensitive (F-A-3, L-5).** `HOST_PREFIX` and
   `SECURE_PREFIX` are compared with `String.startsWith`, so `__host-x`, `__HOST-x` and
   `__secure-x` are treated as ordinary cookie names and bypass every prefix rule; the class
   Javadoc claims case sensitivity is "per RFC", which RFC 6265bis contradicts. Match
   case-insensitively, correct the Javadoc, and rewrite the `shouldBeCaseSensitive` test that
   currently locks in the defect. Decide whether the later-draft `__Http-` / `__HostHttp-`
   prefixes are in scope and record the decision.
2. **Cookie name and value content are actually validated (L-5, F-B-6).** `validateCookie` never
   reads `cookie.value()`. `Cookie.hostPrefix(name, value)` validates only the name suffix and
   stores the value verbatim; `toCookieString()` concatenates `name=value=attributes` with no
   escaping, and the class Javadoc's example feeds that straight to
   `response.setHeader("Set-Cookie", …)`. Validate the value, and reject CR/LF outright. Also fix
   `hostPrefix(null, "v")`, which today produces the name `__Host-null` by string concatenation
   because `CharacterValidationStage.validate(null)` returns `Optional.empty()`.
3. **Attribute resolution follows RFC 6265 §5.3 last-wins (L-6, F-B-8).**
   `AttributeParser.extractAttributeValue` returns on the first matching token, so
   `Secure; Path=/; Path=/admin` resolves `Path` to `/` while a browser applies `/admin` — which
   means `validateHostPrefix` passes a cookie the browser would reject for violating the very
   contract the prefix encodes. Resolve last-wins.
4. **One attribute-splitting implementation with one key rule (F-B-9).** `Cookie` duplicates
   attribute splitting instead of delegating to `AttributeParser`, and the two disagree:
   `getAttributeNames` unconditionally trims each key, so `Domain =example.com` is listed, while
   `AttributeParser.extractAttributeValue` skips any key with trailing whitespace as "strict RFC
   compliance", so `getDomain()` returns empty for the same input. Delete the duplicate and settle
   on one key-normalisation rule.
5. **Decide the cookie-content pipeline (L-5, structural half).** `PipelineFactory.createPipeline`
   throws for `COOKIE_NAME` and `COOKIE_VALUE`. Either implement those pipelines or make the
   absence explicit in the factory Javadoc and in `CookiePrefixValidationStage`'s own
   documentation, so no reader believes `validateCookie` is a cookie validator. This is a genuine
   fork — surface it to the operator.
6. **Regression tests, written first.** One failing test per deliverable, asserting exact values.
   Include: `__host-session` must be subject to the `__Host-` rules; a cookie value containing
   CR/LF must be rejected; `hostPrefix(null, "v")` must throw rather than produce `__Host-null`;
   `__Host-a=1; Secure; Path=/; Path=/admin` must be rejected; `Domain =example.com` must produce
   the same answer from `getAttributeNames()` and `getDomain()`.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that corroborated 44 of 44 findings in `01-security-validation.adoc` with 0
contradicted and 0 unverifiable.

- OBSERVED: `validateCookie` calls `validate(cookieName)` plus the `__Host-` / `__Secure-`
  attribute rules and references `cookie.value()` nowhere in the file — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CookiePrefixValidationStage.java`
  (lines 196-247, the attribute rules at 236-244).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: validateCookie 196-247 with attribute branches 236-244; never reads cookie.value()
- OBSERVED: `PipelineFactory.createPipeline` throws `IllegalArgumentException` with a "Cookie
  validation pipelines are not yet implemented" message for `COOKIE_NAME` and `COOKIE_VALUE` —
  read at `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/PipelineFactory.java`
  (lines 255-258).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: PipelineFactory cookie throw 255-258 exact
- OBSERVED: `HOST_PREFIX` / `SECURE_PREFIX` (lines 147-150) are compared via `String.startsWith`
  at lines 236 and 242 — case-sensitive — while the class Javadoc at line 106 claims
  "Case-Sensitive — Cookie name prefixes are case-sensitive per RFC"; `__Http-` and
  `__HostHttp-` have no branch at all — read at `CookiePrefixValidationStage.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: HOST_PREFIX/SECURE_PREFIX 147/150, startsWith 236/242, Javadoc 106 all exact
- OBSERVED: `validate()`'s whitespace check at line 170 uses `cookieName.equals(cookieName.trim())`,
  and `String.trim()` strips only characters ≤ U+0020, so an ASCII trailing space is rejected
  while NBSP, ZWSP and U+3000 are not — read at `CookiePrefixValidationStage.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: whitespace check at 170 exact
- OBSERVED: the test `CookiePrefixValidationStageTest` pins the case-sensitive behaviour as
  correct — a `@ParameterizedTest` named `shouldBeCaseSensitive` asserting `hasSecurityPrefix`
  returns `false` for `__host-session` and `__HOST-session` — read at
  `cui-http-core/src/test/java/de/cuioss/http/security/validation/CookiePrefixValidationStageTest.java`
  (lines 269-274).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: shouldBeCaseSensitive spans 269-274 exact; @ValueSource carries four values not the two the prose names
- OBSERVED: `hostPrefix` (lines 152-156) and `securePrefix` (lines 200-204) call
  `COOKIE_NAME_VALIDATOR.validate(suffix)` — the suffix only — then construct the cookie with the
  value entirely unvalidated; `toCookieString` (lines 398-416) concatenates with no escaping; the
  class Javadoc's example at line 138 writes it directly to a `Set-Cookie` header — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/data/Cookie.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: hostPrefix 152-156, securePrefix 200-204, toCookieString 398-416, Javadoc example 138 exact
- OBSERVED: `hostPrefix(null, "v")` does not throw, because `CharacterValidationStage.validate(null)`
  returns `Optional.empty()`, and yields the name `__Host-null` through Java string concatenation
  — read at `Cookie.java` and `CharacterValidationStage.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: hostPrefix(null,v) confirmed not to throw; name built by concatenation
- OBSERVED: `AttributeParser.extractAttributeValue` returns on the first attribute-name match and
  never continues to a later duplicate — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/data/AttributeParser.java` (lines 114-149,
  the return at 136-143). `CookiePrefixValidationStage.validateHostPrefix` calls
  `cookie.getPath()` at lines 282-291 and therefore sees `Path=/` for
  `__Host-a=1; Secure; Path=/; Path=/admin`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: extractAttributeValue 114-149 with skip 123-133 and return 136-143; getPath call at 282
- OBSERVED: `Cookie.getAttributeNames` unconditionally trims each key at line 354, while
  `AttributeParser.extractAttributeValue` explicitly `continue`s past a key with trailing
  whitespace at lines 123-133 with a comment calling that "strict RFC compliance" — read at
  `Cookie.java` (lines 347-357) and `AttributeParser.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: getAttributeNames trims at 354, spans 347-357
- OBSERVED: `Cookie.getAttributeNames`' own Javadoc at lines 337-342 already acknowledges the
  "resolve first-match elsewhere" behaviour, so the divergence is documented at that one call site
  while the underlying RFC non-compliance is unchanged.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: first-match Javadoc paragraph spans 337-342 exact
- HYPOTHESIS: deliverable 3's last-wins change alters which cookies `validateHostPrefix` accepts
  and may break tests that construct multi-`Path` cookies. Confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/data/AttributeParserTest.java` and
  `.../CookieTest.java` (verify-at-outline).
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: forward HYPOTHESIS on last-wins breaking multi-Path tests
- Verify-first clause: before scoping deliverable 5, settle whether the absent cookie pipelines
  are promised by any requirement or specification document. Read
  `doc/http-security/specification/specification.adoc` and
  `doc/http-security/functional-requirements.adoc`. WS-06 PLAN-13 owns correcting those documents,
  so a promise found there is a lead for the fork in deliverable 5, not an obligation to
  implement.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: procedural verify-first directive on the cookie-pipeline fork

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/CookiePrefixValidationStage.java` — `validateCookie`, `validate`, `validateHostPrefix`, `validateSecurePrefix`, the prefix constants, the class Javadoc
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/data/Cookie.java` — `hostPrefix`, `securePrefix`, `toCookieString`, `getAttributeNames`, `hasAttributeFlag`, the class Javadoc
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/data/AttributeParser.java` — `extractAttributeValue`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/PipelineFactory.java` — the cookie branch and its Javadoc
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/CookiePrefixValidationStageTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/data/` — `CookieTest`, `AttributeParserTest`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/PipelineFactoryTest.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/data/HTTPBody.java` — **added 2026-09-09 at landing**; realized but never declared. Javadoc-only (documents that a repeated `charset` resolves last-wins, inherited from the shared `AttributeParser`) — a legitimate consequence of deliverable 3. Inside PLAN-04's declared surface, which had already shipped

⛔ **The landing message claimed "realized footprint is exactly the 10 declared files ... no
under-declaration". That claim is wrong by one** — `corpus surfaces` resolves this declaration to
**7 entries** covering 9 of the 10 realized files. The edit is harmless; the assertion is not,
because a stated zero invites the orchestrator to skip the check. ✅ Even so, one Javadoc-only file
out of ten is the epic's second-best declaration, after PLAN-03's one adjacent test.

⛔ This plan does **not** edit any file under `doc/`. Javadoc inside the `.java` files above IS in
scope. `F-E-2`'s cookie-test items and `F-E-12`'s `AttackCookieGenerator` branches live in the
test framework and are owned by WS-05 PLAN-10 and PLAN-11 — but note the adjacency below, because
this plan's behaviour change is what makes those tests fixable.

## Dependencies and Sequencing

- Depends on: PLAN-04. That plan edits `AllowBlockListStage`'s exception-detail construction,
  which `CookiePrefixValidationStage` shares, and settles the pattern this plan follows.
- Overlaps with: PLAN-04 (shared exception-detail construction) — strictly sequential.
- Adjacent to: `cui-http-core/src/test/java/de/cuioss/http/security/tests/CookieChaosAttackTest.java`,
  which WS-05 PLAN-10 owns. That class contains tests whose Javadoc says the prefix rules are "not
  yet implemented" while they are, and tests that catch `UrlSecurityException` with no assertion.
  This plan does not edit it; file an inbox message naming the specific methods this plan's
  behaviour change invalidates so PLAN-10 picks them up.


⛔ **RE-READ BEFORE OUTLINE (folded 2026-09-09 from the PLAN-04 landing).** PLAN-04 shipped first as
sequenced (PR #227, `65dcb29`) and **rewrote `AllowBlockListStage`'s entry canonicalisation** — the
class now runs configured entries through the same `canonicalise(value, mediaTypeOnly)` helper an
incoming value goes through, and rejects an entry canonicalising to an empty media type at
construction. This plan shares that class's exception-detail construction, so its staged premises
about `AllowBlockListStage` predate the rewrite and must be re-read against `main`, not trusted.

Also landed there and relevant here: `CharacterValidationStage` gained a paragraph, and
`AllowBlockListStage.renderForDetail`'s Javadoc changed. PLAN-03 (PR #222) separately rewrote the
same class's `detail` rendering through `renderForDetail`.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-05-cookie-validation-completeness.md"
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
- Deliverable 1 requires rewriting an existing passing test (`shouldBeCaseSensitive`) that pins the
  defect. Rewrite it to assert the corrected behaviour and see the rewritten test fail against the
  unmodified production code before fixing — a deleted test is not a regression test.
- Deliverable 5 contains a genuine fork (implement the cookie pipelines vs. document their
  absence). Surface it to the operator; do not decide it silently.
- Deliverables 1-4 change which cookies are accepted. Record each as a documented behaviour change
  in the PR body.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

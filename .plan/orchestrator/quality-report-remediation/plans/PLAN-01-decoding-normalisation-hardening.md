# PLAN-01: Decoding and Normalisation Hardening

epic: quality-report-remediation
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-01-decoding-normalisation-hardening.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Make the decode → normalise chain deliver the protections it advertises. Today the
double-encoding gate is a wire-form regex that recognises only the literal spelling `%25XX`, so
`%25%32%66` passes every preset and the pipeline returns `%2f`; the parameter-name delimiter
check runs before Unicode normalisation, so `a%CD%BEb` returns `a;b`; malformed UTF-8 is
silently replaced with U+FFFD and accepted despite a Javadoc promising `INVALID_ENCODING`; and a
segment-terminal `..` — with or without a decoded `?` or `#` glued to it — is accepted, with the
dot-dot still present in the value handed back to the caller. This plan carries both of the
epic's High findings on this surface, and the report's own remediation order puts it first.
Every change is driven by a regression test written and seen to fail first.

## Deliverables

1. **Decide double encoding on the decoded output, not on a wire-form regex (F-A-1, High).**
   `DOUBLE_ENCODING_PATTERN` is `%25[0-9a-fA-F]{2}`, which requires two literal hex digits
   directly after `%25`; in `%25%32%66` the following characters are `%` and `3`, so the gate
   never fires and the single decode pass yields the literal text `%2f`. Detect a surviving
   percent-encoding layer in the decoded result instead of pattern-matching the input.
2. **Run the decoded-character checks on the normalised string (F-A-2, High).**
   `validateDecodedCharacters` is invoked before the Unicode-normalisation block, so U+037E — a
   canonical NFC singleton that folds to `;` — is not a delimiter at check time and is a
   delimiter afterwards. Move the check after normalisation, or re-run it on the normalised
   value. `STRUCTURAL_CHARS` also omits `;`, so `introducesStructuralCharacter` does not catch it
   either; close both holes.
3. **Real UTF-8 decoding that reports malformed input (F-A-4, L-2).** `UTF8_OVERLONG_PATTERN`'s
   `%c[0-1][0-9a-f]` alternative needs a literal hex digit after `%c0`/`%c1`, so `%c0%80` and its
   family never match, and `java.net.URLDecoder.decode` substitutes U+FFFD without throwing.
   Replace the regex list with a decoder using `CodingErrorAction.REPORT` so malformed input
   raises `INVALID_ENCODING` as the Javadoc promises.
4. **Treat a segment starting with `..` as traversal intent (L-1, F-A-8, V-1, L-13).**
   `SINGLE_COMPONENT_TRAVERSAL_PATTERN` is anchored to the exact shape `seg/../seg` with no
   leading slash, so `a/../b` throws while `/a/../b` and `a/../b/c` are silently resolved;
   `processPathSegment` discards a terminal `..` unconditionally at root; `escapesRoot` can never
   fire for an absolute path; and a decoded `?` or `#` glued to a `..` (`..?`) misses every layer
   and survives verbatim in the returned value. Reject any segment beginning with `..`, and
   reject a decoded `?` or `#` in a path.
5. **Close the raw-vs-encoded asymmetries (L-10, F-B-16).** Under `lenient()` the double-encoding
   check is skipped entirely and `%2500` decodes to the three-character text `%00`, which a
   caller decoding once more reads as NUL. Separately, raw CR/LF in a parameter value is rejected
   while `%0D%0A` is accepted, because `decodedControlCharacterForbidden` carves out CR/LF/TAB
   for `PARAMETER_VALUE` and `BODY` via `isFormDataWhitespace`. Make the raw and the encoded
   spelling of the same decoded value produce the same verdict.
6. **Regression tests, written first.** One failing test per deliverable, asserting the exact
   returned value or the exact `UrlSecurityFailureType` — never `assertNotNull`, never "any of a
   set of failure types". Include the probe strings the report used: `%25%32%66` → must not
   return `%2f`; `%25%30%30` → must not return `%00`; `a%CD%BEb` → must not return `a;b`;
   `/a%EF%BC%9Bb`; `/a%C2%A0b`; `/a/b/..`; `/files/public/..%3F`; `%c0%80`.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass that corroborated 44 of 44 findings in `01-security-validation.adoc` with 0
contradicted and 0 unverifiable. The repository's production and test code at that sha is
byte-identical to the reviewed commit `0bad295`.

- OBSERVED: `DOUBLE_ENCODING_PATTERN` is `%25[0-9a-fA-F]{2}` and `decodeForValidationType`
  performs a single `URLDecoder.decode` call, so `%25%32%66` yields the literal text `%2f` —
  read at `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java`
  (pattern at line 117, gate at lines 178-185, decode at lines 266-271).
- OBSERVED: `validateDecodedCharacters(value, decoded)` is invoked at line 217, strictly before
  the Unicode-normalisation block at lines 229-241; `isParameterNameDelimiter` checks only the
  literal `&`, `=`, `;` and space; `STRUCTURAL_CHARS` is `"/\.:?#%"` at line 278 and omits `;` —
  read at `DecodingStage.java` (lines 217, 229-241, 278, 399-406).
- OBSERVED: `UTF8_OVERLONG_PATTERN`'s first alternative `%c[0-1][0-9a-f]` requires a literal hex
  digit after `%c0`/`%c1`, so `%c0%80` never matches, and `URLDecoder.decode` substitutes U+FFFD
  rather than throwing; the class Javadoc promises `INVALID_ENCODING` at lines 60-62 and 158-161
  — read at `DecodingStage.java` (lines 117-133, 188-195, 270).
- OBSERVED: `SINGLE_COMPONENT_TRAVERSAL_PATTERN` at line 203 is anchored to `seg/../seg` with no
  leading slash; `processPathSegment`'s `".."` case removes the previous segment unconditionally
  for an absolute path (lines 419-429) and drops empty segments (lines 430-433); `escapesRoot`
  fires only for a relative path starting with `../` or `..\` (lines 605-609) — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/NormalizationStage.java`.
- OBSERVED (V-1, traced statically): for `/files/public/..%3F` none of the five layer-1 patterns
  match `..?`; `validateDecodedCharacters` rejects only NUL, combining marks, `Character.isISOControl`
  characters and `PARAMETER_NAME` delimiters — `URL_PATH` has no delimiter rule — and
  `processPathSegment`'s switch special-cases only the exact string `".."`, so `..?` falls to the
  default branch and is appended verbatim at lines 434-436 — read at `NormalizationStage.java`
  (lines 415-439) and `DecodingStage.java` (lines 376-394).
- OBSERVED: `PatternMatchingStage.checkPathTraversalPatterns` matches only
  `SecurityDefaults.PATH_TRAVERSAL_PATTERNS` entries that all require a trailing separator, so a
  terminal `..` with no following slash never matches while `/a/b/../` does — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java` and
  `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java`.
- OBSERVED: ADR-0014 line 48 states verbatim that `NormalizationStage` implements RFC 3986
  §5.2.4 "exactly", and its scoping text at lines 61-63 says the clamp applies only to a `..`
  sequence long enough to walk past root — which `/a/b/..` refutes — read at `doc/adr/0014-*.adoc`.
- OBSERVED: with `allowDoubleEncoding=true` the gate at line 178 is skipped and `%2500` decodes
  to the three-character text `%00` — read at `DecodingStage.java` (lines 177-185, 266-271).
- OBSERVED: `decodedControlCharacterForbidden` carves out CR/LF/TAB for `PARAMETER_VALUE` and
  `BODY` via `isFormDataWhitespace`, while `CharacterValidationStage.isCharacterAllowed` rejects
  raw CR/LF for those types through the `allowedChars` / `allowControlCharacters` path — read at
  `DecodingStage.java` (lines 450-456) and
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java`
  (lines 341-343).
- OBSERVED: `NormalizationStage`'s class Javadoc (lines 114-147) already carries a "what this
  stage rejects, and what it silently clamps" section and a standalone-use caveat, so the
  stage-level documentation half of F-A-8 is partly already done; the pipeline-level and README-
  level documentation is not, and belongs to WS-06 PLAN-13.
- HYPOTHESIS: deliverable 4 rejects inputs the library currently accepts, including
  `/a/b/..` — a shape a servlet-style caller may legitimately send. Confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/security/validation/NormalizationStageTest.java` §
  the clamp test methods, and at
  `cui-http-core/src/test/java/de/cuioss/http/security/tests/PathTraversalAttackTest.java`
  (verify-at-outline). If existing tests pin the clamp as intended behaviour, the change needs an
  ADR superseding ADR-0014 rather than a plain fix.
- Verify-first clause: before scoping deliverable 3, settle whether any current caller depends on
  the U+FFFD replacement behaviour. Read every `DecodingStage` consumer under
  `cui-http-core/src/main/java`. A refutation loops back and re-scopes deliverable 3 to a
  configuration-gated strict mode.

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java` — `DOUBLE_ENCODING_PATTERN`, `UTF8_OVERLONG_PATTERN`, `validate`, `decodeForValidationType`, `validateDecodedCharacters`, `decodedControlCharacterForbidden`, `STRUCTURAL_CHARS`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/NormalizationStage.java` — `SINGLE_COMPONENT_TRAVERSAL_PATTERN`, `processPathSegment`, `containsDirectoryTraversalIntent`, `escapesRoot`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java` — `checkPathTraversalPatterns`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` — `PATH_TRAVERSAL_PATTERNS`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/DecodingStageTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/NormalizationStageTest.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/tests/` — the traversal and encoding attack tests that pin current behaviour
- HYPOTHESIS: `doc/adr/` — a new ADR superseding ADR-0014, only if the verify-first clause shows the clamp is pinned as intended (verify-at-outline)

⛔ This plan does **not** edit any `.adoc` under `doc/` except a new ADR recording its own
decision. ADR-0014's inaccurate scoping text, and every other prose consequence of this plan, are
owned by WS-06 PLAN-13. Javadoc inside the `.java` files above IS in scope.

## Dependencies and Sequencing

- Depends on: none. This is the head of WS-01 and of the epic's code work.
- Overlaps with: PLAN-02 and PLAN-03 (same package, same test package) — strictly sequential,
  PLAN-01 first. Also overlaps PLAN-10 and PLAN-11 in WS-05, whose tests assert the exact
  behaviour this plan changes; WS-05 is sequenced after WS-01 lands.
- Adjacent to: `de.cuioss.http.security.pipeline`, which composes these stages but is not edited
  here. PLAN-04 owns the content-type pipeline's composition.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-01-decoding-normalisation-hardening.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass. The report supplies concrete probe strings for every deliverable; use them as the
  first failing tests.
- Assert the exact returned value or the exact `UrlSecurityFailureType`. A test that accepts "any
  of several failure types" is the F-E-3 defect this epic is also fixing; do not add one.
- Deliverables 1-5 are behaviour changes visible to integrators: inputs the library accepts today
  will start throwing. Record each as a documented breaking change in the PR body.
- This plan is expected to break existing tests in `security/tests` and `security/database` that
  pin the current behaviour. Fix those tests as part of this plan when the fix is mechanical; when
  a test's whole premise changes, file an inbox message naming it so PLAN-10 picks it up.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

# PLAN-02: Post-Decode Character Enforcement

epic: quality-report-remediation
workstream: WS-01

## Objective

Close the percent-encoding bypass class and the strict-preset weakening. `CharacterValidationStage`
runs *before* `DecodingStage` and no pipeline re-runs it afterwards, so `/%3Cscript%3E` decodes to
`/<script>` and `/a%1B%08b` decodes to raw ESC and backspace — both returned to the caller as
"validated" despite characters that the un-encoded form would have rejected and despite
`allowControlCharacters=false`. Separately, `strict()` is the only preset that enables
suspicious-pattern detection and simultaneously turns on case-sensitive comparison, making its
all-lowercase pattern set bypassable with `/ETC/passwd` — so the strict preset detects strictly
*less* than a hand-built config. This plan makes character enforcement cover the decoded form or
narrows the stated contract to the encoded form only, and makes the strict preset actually strict.

## Deliverables

1. **Re-validate decoded output against the character allow-list**, or narrow
   `CharacterValidationStage`'s own Javadoc to state that character rules apply to the encoded form
   only. The report's Top Priority #4 names both routes as acceptable; a note in a review report is
   explicitly not one. (SV-3, SV-5)
2. **Fix the `strict()` preset** so enabling suspicious-pattern detection does not simultaneously
   weaken it — either make STRICT case-insensitive, or store the suspicious-pattern set so it
   matches case-insensitively regardless of the flag. (SV-4)
3. **Make `failOnSuspiciousPatterns(false)` honest.** It is documented as "log only" but nothing is
   logged and the event counter is not incremented, so suspicious matches are entirely invisible
   under the default. Either implement the logging/counting or correct the documentation to say
   matches are silently allowed; also remove the `PatternMatchingStage` Javadoc reference to the
   nonexistent `logSecurityViolations` config option. (SV-7)
4. **Complete the encoded-dot case check** in `containsDirectoryTraversalIntent`, which tests
   `%2e%2e` and `%2E%2E` but misses the mixed-case `%2E%2e` / `%2e%2E` forms. (SV-10)
5. **Decide and document the `+`-to-space asymmetry** for URL paths: `URLDecoder.decode` applies
   form semantics, so `/a+b` is silently rewritten to `/a b` — a space that is not a legal path
   character and, per SV-3, is never re-validated. Either use a path-appropriate percent-decoder for
   paths or document the rewrite. (SV-24)
6. **Regression tests** for each of the above, including the specific bypass strings the report names.

Six deliverables — at the split guard. Proceeding unsplit: deliverables 1, 3, 4 and 5 all edit
`DecodingStage`/`CharacterValidationStage` and deliverable 2's correctness is only observable through
the pattern stage that 3 also touches, so a split would produce two plans that cannot run
concurrently and must rebase on each other. Rationale recorded as an epic decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| SV-3 | MEDIUM | Decoded output is not re-validated against the character allow-list |
| SV-4 | MEDIUM | The `strict()` preset silently weakens pattern detection |
| SV-5 | MEDIUM | Percent-encoded control characters bypass `allowControlCharacters=false` |
| SV-7 | MEDIUM | `failOnSuspiciousPatterns(false)` is documented as "log only" but nothing is logged |
| SV-10 | LOW | `containsDirectoryTraversalIntent` encoded-dot checks are case-incomplete |
| SV-24 | INFO | `URLDecoder.decode` applies form semantics (`+` → space) to URL paths |

## Claim Labels

- OBSERVED: these findings are stated at `source/security-validation-review.adoc` § `SV-3`, `SV-4`,
  `SV-5`, `SV-7`, `SV-10`, `SV-24`.
- OBSERVED: the report records SV-24 as reported by two reviewers, and records that SV-3's status as
  defect-versus-accepted-boundary depends on the project's stance that XSS is an application-layer
  concern — read at `source/security-validation-review.adoc` § `SV-3`, `SV-24`.
- HYPOTHESIS: `CharacterValidationStage` runs before `DecodingStage` in the URL-path and parameter
  pipelines, and no pipeline re-runs it after decoding — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java`
  § `createStages` (verify-at-outline)
- HYPOTHESIS: `DecodingStage.validateDecodedCharacters` re-checks only null bytes, combining marks,
  CR/LF, and parameter-name delimiters — not the full allow-list — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java`
  § `validateDecodedCharacters` (verify-at-outline)
- HYPOTHESIS: `SecurityDefaults.strict()` sets `caseSensitiveComparison=true` and
  `failOnSuspiciousPatterns=true` together, and `PatternMatchingStage` lowercases the test value only
  when the flag is false while the pattern constants are all-lowercase — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` § `strict`
  and `.../validation/PatternMatchingStage.java` § the `testValue` assignment (verify-at-outline)
- HYPOTHESIS: `PatternMatchingStage` declares no logger and `SecurityEventCounter` is incremented
  only from `AbstractValidationPipeline`'s exception path — an asserted ABSENCE, verified exactly as
  a presence — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java`
  § class fields, and `.../pipeline/AbstractValidationPipeline.java` § `validate` (verify-at-outline)
- HYPOTHESIS: the derived claim that deliverables 1/3/4/5 share the `DecodingStage` /
  `CharacterValidationStage` surface — the basis of the proceed-unsplit rationale above — is the
  orchestrator's inference from the reports' file attributions, not a report statement.
  Confirm/refute by reading the actual imports and call sites at outline (verify-at-outline)
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: deliverable 1 offers two mutually exclusive resolutions (enforce on decoded
  output, or narrow the Javadoc). Enforcing changes what the library rejects and is potentially
  breaking for consumers; narrowing does not. This is a genuine fork — decide it at outline against
  the code and the project's XSS-scope stance, and record the choice.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java` — class Javadoc, allow-list enforcement (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java` — `validateDecodedCharacters`, the `URLDecoder.decode` call, `decodedLineBreakForbidden` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/NormalizationStage.java` — `containsDirectoryTraversalIntent` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java` — `testValue` case folding, the suspicious-pattern check methods, class Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` — `strict`, the suspicious-pattern constants (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java` — the `failOnSuspiciousPatterns` `@param` text (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java` — stage ordering, if a post-decode character stage is added (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/security/validation/` and `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/` — stage and pipeline tests for the bypass strings (verify-at-outline)
- ⛔ **NOT this plan's surface:** `.../security/generators/` and `.../security/database/` — WS-04 owns those.

## Dependencies and Sequencing

- Depends on: PLAN-01. Both touch `CharacterValidationStage` and `PipelineFactory`; PLAN-01 is the
  queue head and this plan rebases on it.
- Overlaps with: PLAN-01, PLAN-03, PLAN-04 — the whole of WS-01 is strictly sequential.
- Adjacent to: WS-04's attack databases and legitimate-pattern databases. If deliverable 1 enforces
  on decoded output, previously-accepted legitimate inputs may start being rejected — the
  legitimate-pattern databases assert a ZERO event count and would fail. Do not edit them here;
  report the impact in the landing so PLAN-13 re-verifies its expectations.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-02-post-decode-character-enforcement.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

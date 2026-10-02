envelope_version=1
sender_type=plan
sender_id=post-decode-character-enforcement
epic=quality-report-remediation
kind=landing
created=2026-08-27T11:30:16Z

# Landing: PLAN-02 Post-Decode Character Enforcement

- plan_id: post-decode-character-enforcement
- spec: `.plan/local/orchestrator/quality-report-remediation/plans/PLAN-02-post-decode-character-enforcement.md`
- workstream: WS-01
- outcome: MERGED
- pr: https://github.com/cuioss/cui-http/pull/159
- merge_commit: d042750
- merged_at: 2026-08-27T11:19:48Z
- base: main (squash via required merge queue)

## Findings covered

| ID | Severity | Disposition |
|----|----------|-------------|
| SV-3 | MEDIUM | Resolved by narrowing the documented contract, not by full re-validation — see below |
| SV-4 | MEDIUM | Fixed — `STRICT_CONFIGURATION` now sets `caseSensitiveComparison=false` |
| SV-5 | MEDIUM | Fixed — decoded-output check widened from CR/LF-only to the whole C0/C1 control class |
| SV-7 | MEDIUM | Documentation corrected; logging/counting deliberately NOT added |
| SV-10 | LOW | Fixed — encoded-dot check case-folded, covering `%2E%2e` / `%2e%2E` |
| SV-24 | INFO | Fixed — `URL_PATH` uses RFC 3986 decoding preserving a literal `+` |

## Fork resolutions (all four recorded, three promoted to ADRs)

1. **SV-3/SV-5 (operator-confirmed at the outline gate)**: the full-allow-list branch was REJECTED
   with evidence — `RFC3986_PATH_CHARS` excludes `%`, `[`, `]` and all code points > 255 for
   `URL_PATH`, so enforcing it post-decode would newly reject the library's own documented
   legitimate inputs (`/discount/25%25off`, `/array/items%5B0%5D`, CJK paths). Instead
   `CharacterValidationStage`'s documented scope was narrowed to the encoded wire form and
   `DecodingStage`'s decoded-output guarantee widened. **`/%3Cscript%3E` is deliberately ACCEPTED**
   (decoding to `/<script>`), consistent with the project's standing application-layer-XSS position.
   This INVERTS what the source report expected for that string; the boundary is pinned by a
   characterization test. Recorded as ADR-0004.
2. **SV-24**: `URL_PATH` gets a `+`-preserving decoder; parameter/body keep form semantics.
3. **SV-7**: documentation corrected rather than building `LogRecord` infrastructure for one WARN
   message (`PatternMatchingStage` is a record with no `SecurityEventCounter` access). CodeRabbit
   re-raised this on the PR and it was declined with the same rationale. Recorded as ADR-0006.
4. **SV-4**: strict preset flipped to case-insensitive. Recorded as ADR-0005.

## Impact for WS-04 / PLAN-13 (requested by this plan's spec)

The spec asked that any impact on the WS-04-owned legitimate-pattern databases be REPORTED here
rather than fixed in-plan. **No legitimate-database expectation was broken.** Because deliverable 1
resolved AWAY from full post-decode allow-list enforcement, the previously-accepted legitimate
percent-encoded paths continue to pass; the widened control-character check covers only C0/C1, which
no legitimate database entry contains. The full `cui-http-core` suite (5537 tests) is green, and no
file under `security/database/` or `security/generators/` was edited. PLAN-13 needs no re-verification
on this plan's account.

Two `security/tests/` classes were reviewed as in-scope and required no change:
`MixedEncodingAttackTest` (already accepts `CONTROL_CHARACTERS`) and `UnicodeControlCharacterAttackTest`
(its attacks use RAW control characters, rejected by `CharacterValidationStage` before decoding, so
this plan changed nothing for it — the predicate was deliberately NOT narrowed).

## Deliverables

- D1: four production fixes + three Javadoc corrections + `doc/http-security/configuration.adoc`
- D2: `PostDecodeEnforcementRegressionTest` — pipeline-level, one case per finding
- (added mid-finalize) TASK-004: narrowed an over-broad "in every pipeline" Javadoc claim that
  CodeRabbit correctly caught, against this plan's own D1 success criterion
- ADR-0004 / ADR-0005 / ADR-0006

## Caveat on the merge

The pre-merge review barrier read `participation_complete: false`. CodeRabbit DID review the final
HEAD `e07baf5` on request and signed off with no findings, verifying every narrowed claim against
`PipelineFactory` and each pipeline implementation — but delivered it as an issue comment rather
than a review submission, so the reviews API still anchors every review to the original `3e5433d`
and the automated signal could not see it. `pr-agent` never reviewed the two newest commits. An
operator-granted, HEAD-bound `pre-merge-consent` authorization over gap class `review-barrier-gap`
records this. CI green, Sonar clean (0 new-code issues), whole-tree quality gate green at the merged
HEAD.

## Note for the epic

A correction was issued mid-run: an earlier report that "CodeRabbit re-reviewed and had nothing
further" was WRONG — no re-review had been triggered (`re_review_on_loopback: false`), and the
"0 new comments" result meant "never re-reviewed", not "re-reviewed and clean". Seven candidate
lessons were queued to this epic's inbox; message 005 covers exactly that indistinguishable-zero
defect, and 004 covers an `adr-propose` fail-open that would have destroyed the three ADRs.

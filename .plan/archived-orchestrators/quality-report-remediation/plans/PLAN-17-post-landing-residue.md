# PLAN-17: Post-Landing Residue (decoded-parameter character check, prose drift, test residue)

epic: quality-report-remediation
workstream: WS-01

> Staged plan spec — one shippable unit of work. Sweeps up the Open Defects the PLAN-10, PLAN-12
> and PLAN-13 landings left unowned (see `epic.md` § Open Defects, entries dated 2026-10-04 and
> 2026-10-05). This spec is SELF-SUFFICIENT: the hand-off command is a one-line pointer and
> carries no brief. It is written so that it can be executed either by `/plan-marshall` or by a
> plain Claude Code session following `CLAUDE.md`.

## Objective

Close the residue of the quality-report remediation in one small PR. One item is a possible
security gap and is investigated test-first: a percent-encoded parameter value that decodes to a
code point above 255 (the PLAN-10 lead is `%e5%98%8a%e5%98%8d`, U+560A U+560D, whose low bytes
are LF/CR) may pass `URLParameterValidationPipeline` although the documented rule says Unicode
above 255 is always rejected for URL types. The rest is mechanical: comments and Javadoc that
contradict the code, and stale test-source residue. No new feature, no API change.

## Deliverables

1. **Decoded parameter values and the >255 rule (security, test-first).** Add a failing test to
   `URLParameterValidationPipelineTest` that feeds `%e5%98%8a%e5%98%8d` (and one plain CJK value
   such as `%e4%b8%ad`) through `URLParameterValidationPipeline` at the default configuration and
   asserts the outcome the documented rule requires. Then:
   - if the pipeline already rejects them, keep the tests as regression guards and record
     "refuted" in the PR body;
   - if it accepts them, make the post-decode value subject to the same character rule as the raw
     value (smallest change at `URLParameterValidationPipeline` § `createStages` or in
     `DecodingStage`), and apply the same check to `URLParameterNameValidationPipeline` and
     `URLPathValidationPipeline` if they share the gap (add the same test for each).
   ⛔ **Stop and ask the operator before merging a behaviour change** if the fix would reject
   percent-encoded UTF-8 in parameter values at the default configuration (for example `%c3%a4`
   for "ä"): that is a compatibility decision for a published library, not a bug fix, and needs an
   ADR (allocate the number against `origin/main`) plus a release-note entry.
2. **Scheme rejection on the URL path pipeline — document, do not implement.** No preset rejects
   `http:` / `ftp:` / `gopher:` / `ldap:` schemes on `URLPathValidationPipeline`. The pipeline
   validates a path component, not an absolute URL (CLAUDE.md, `pipeline/package-info.java`
   line 40), so scheme handling is the caller's job. State that explicitly in the class Javadoc of
   `URLPathValidationPipeline` (one or two sentences). No code change.
3. **Javadoc and comments that contradict the code.**
   - `CharacterValidationStage`: class Javadoc (~lines 131-138) and the >255 branch comment
     (~lines 431-436) say `HEADER_VALUE` / `BODY` are ASCII-only at the default. Re-read
     `isCharacterAllowed` (~line 428, `allowExtendedAscii || allowedChars.test(ch)`) and the
     `BODY` / `HEADER_VALUE` character sets in `CharacterValidationConstants`, then make the prose
     state what the code does (PLAN-13 reports 160-255 are admitted for `BODY` at the default).
   - `client/adapter/package-info.java` line 330: "This prevents SQL injection, XSS scripts, path
     traversal, and malicious Unicode." Narrow it to what `URLParameterValidationPipeline`
     actually detects (see `PatternMatchingStage`); the library does not detect SQL injection or
     XSS as such.
   - `forwarded/ForwardedHeaderResolver.java` (~line 166, "Present-but-invalid = drop (no
     fall-through)", and ~line 855, "contributes nothing") and `forwarded/RfcForwardedParser.java`
     (~line 72, "contributes nothing"): re-read the resolution logic and make each comment match
     it. The surrounding Javadoc (~lines 106, 140, 349) already says a present-but-invalid value
     *contests*; reconcile the outliers to that, or correct all of them if the code says otherwise.
   Javadoc and comments only in this deliverable — no executable change.
4. **Test-source residue.**
   - Remove the stale "Implements: Task Gn … from HTTP verification specification" lines from the
     23 test files that still carry them (`grep -rln "HTTP verification specification"
     cui-http-core/src/test` must return nothing afterwards).
   - `HttpHeaderInjectionAttackGenerator.java`: replace the raw NUL character in its Javadoc with
     an escaped description.
   - `IDNAttackDatabase.java`: replace raw non-ASCII / invisible characters in string literals with
     code-point construction (`Character.toString(0x…)` or `\uXXXX`) — Sonar rule `text:S6389`
     failed PLAN-10's first CI round on exactly this class.
   - `URLPathValidationPipelineTest`: replace the set-membership assertion PLAN-10 flagged with an
     assertion on the specific expected failure type.

## Claim Labels

- HYPOTHESIS: `URLParameterValidationPipeline` runs `CharacterValidationStage` before
  `DecodingStage` and nothing re-applies the character rule to the decoded value, so a
  percent-encoded code point above 255 is never checked against the ">255 always rejected for URL
  types" rule — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterValidationPipeline.java`
  § `createStages` and
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java`
  § `decodeForValidationType` (verify-at-outline, test-first per deliverable 1).
- OBSERVED: `URLParameterValidationPipeline.createStages` (lines 120-124) orders Length,
  Character, Decoding, Normalization, PatternMatching.
- OBSERVED: `CharacterValidationStage` lines 431-443 state Unicode above 255 is always rejected
  for URL paths and parameters, and return `allowExtendedAscii && supportsUnicodeCharacters()`.
- HYPOTHESIS: the `CharacterValidationStage` "ASCII-only at default" prose for `BODY` /
  `HEADER_VALUE` is wrong for 160-255 — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java`
  § `isCharacterAllowed` together with `CharacterValidationConstants` § `getCharacterSet`
  (verify-at-outline). Reported by PLAN-13, not re-checked by the orchestrator.
- OBSERVED: `client/adapter/package-info.java` line 330 reads "This prevents SQL injection, XSS
  scripts, path traversal, and malicious Unicode."
- OBSERVED (derived count, `grep -rln` at `1e61b78`): exactly **23** files under
  `cui-http-core/src/test` contain "HTTP verification specification"; `src/main` has 0.
- OBSERVED: `HttpHeaderInjectionAttackGenerator.java` contains a raw NUL byte (`grep -P '\x00'`);
  `IDNAttackDatabase.java` has 41 lines with non-ASCII characters.
- HYPOTHESIS: the `URLPathValidationPipelineTest` set-membership assertion is the one PLAN-10
  flagged as too weak — confirm at
  `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/URLPathValidationPipelineTest.java`
  by locating the assertion that accepts any member of a set of failure types (verify-at-outline).
- HYPOTHESIS: scheme rejection is out of scope by design for a path-component validator —
  confirm at `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/package-info.java`
  § package Javadoc line 40 (verify-at-outline).

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterValidationPipeline.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLParameterNameValidationPipeline.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/URLPathValidationPipeline.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/validation/CharacterValidationStage.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/adapter/package-info.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/RfcForwardedParser.java`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/generators/`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/tests/`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/database/IDNAttackDatabase.java`
- HYPOTHESIS: `doc/adr/` — only if deliverable 1 turns into a default-behaviour change (verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-10, PLAN-12, PLAN-13 (all shipped). Nothing else is open in the epic.
- Overlaps with: no live plan.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report-remediation/plans/PLAN-17-post-landing-residue.md"
```

Plain Claude Code alternative (the scope is small enough):

```text
claude "Implement the spec .plan/orchestrator/quality-report-remediation/plans/PLAN-17-post-landing-residue.md end to end, following CLAUDE.md"
```

## Execution Constraints

- Follow `CLAUDE.md`: feature branch, executor build commands (`./mvnw` fallback only without the
  executor), `verify -Ppre-commit` before every commit, PR to `main`, handle every review comment,
  no auto-merge without operator approval.
- Deliverable 1 is test-first: the new tests are committed and seen to fail (or seen to pass,
  which refutes the lead) before any production change.
- ⛔ Deliverables 2-4 change no executable statement in `src/main`. Only deliverable 1 may.
- ADR numbers, if one is needed, are allocated against `origin/main` (high-water is 0023 at
  `1e61b78`) and re-checked immediately before commit.
- The PR body states for deliverable 1 which outcome happened (refuted / fixed / escalated) and
  lists the files touched per deliverable.
- Release notes for the next version must mention that `PathTraversalURLGenerator` and
  `DoubleEncodingAttackGenerator` were removed from the `generators` artifact (PLAN-10). That is
  release work, not this plan's — do not edit release notes here.

## Write-Boundary

The implementing session touches only repository source and tests (and `doc/adr/` under the
deliverable 1 condition). It edits NO file under `.plan/orchestrator/`. Under `/plan-marshall` it
may file its own `inbox/` message; a plain Claude Code session reports through its PR only, and
the orchestrator reconciles from the PR.

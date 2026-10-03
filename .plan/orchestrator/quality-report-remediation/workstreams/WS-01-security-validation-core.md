# WS-01: Security Validation Core — Decoding, Normalisation, Character Rules

epic: quality-report-remediation

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-01-security-validation-core.md` and is tracked in the
> epic `status.json` `workstreams[]` field. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

This workstream owns the decode → normalise → character-check chain that every URL path and
parameter flows through, and it carries the epic's two High findings on that surface. The
review's own remediation order puts this first: the double-encoding gate, the UTF-8 malformed
handling, the decoded-delimiter ordering, the terminal `..` clamp and the character-set rules
are one interlocking mechanism, and fixing any of them in isolation risks re-opening another.
It closes when `DecodingStage`, `NormalizationStage` and `CharacterValidationStage` deliver the
protections their Javadoc and the ADRs claim, each proven by a regression test written before
the fix.

## Scope

- In scope: `de.cuioss.http.security.validation` (`DecodingStage`, `NormalizationStage`,
  `CharacterValidationStage`, `LengthValidationStage`, `PatternMatchingStage`),
  `de.cuioss.http.security.config` where a default or a constant governs those stages,
  `de.cuioss.http.security.exceptions` where a stage embeds unsanitised input in a message,
  and the unit tests for all of the above.
- Out of scope: `AllowBlockListStage` and `CookiePrefixValidationStage` and the pipelines that
  compose them (WS-02); the forwarded-header package (WS-03); the client packages (WS-04); the
  attack databases and generators under `src/test/java/.../security/{database,generators}`
  (WS-05); every `.adoc` under `doc/` and every `package-info.java` (WS-06).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-01-decoding-normalisation-hardening | staged | Decode/normalise correctness: the two High findings plus the traversal-clamp cluster |
| PLAN-02-character-set-and-control-characters | staged | What each `ValidationType`'s character set admits, and which control characters survive each preset |
| PLAN-03-exception-sanitisation-and-config-hygiene | staged | Unsanitised input reaching exception messages and logs |
| PLAN-15-configuration-surface-hygiene | staged | Request-collection limits, duplicated defaults, the dead constant surface, and the now-inert `allowDoubleEncoding` |

## Sequencing and Surface Notes

- **PLAN-01 shipped at `7a0da52` (PR #210).** See `landings/PLAN-01.md`.
- **PLAN-03 was split at that landing.** Folding the plan's inbox finding took it to eight
  deliverables, over the scope-bloat guard. The exception/log half and the configuration half are
  file-disjoint, so the split was taken rather than a proceed-unsplit rationale recorded. PLAN-03
  keeps `UrlSecurityException.java` + `AllowBlockListStage.java`; **PLAN-15** owns
  `SecurityConfiguration.java`, `SecurityConfigurationBuilder.java`, `SecurityDefaults.java`,
  `RequestCollectionValidator.java` and `module-info.java`.
- Order is PLAN-02 → {PLAN-03, PLAN-15}, and the PLAN-02-first order is load-bearing: it edits
  `SecurityConfigurationBuilder.java` (which PLAN-15 rewrites) and settles the
  `CharacterValidationStage` escaping shape PLAN-03 adopts as its pattern.
- **PLAN-03 and PLAN-15 are file-disjoint from each other** and MAY be paired concurrently once
  PLAN-02 has landed — the throughput gain the split bought.
- PLAN-02 and PLAN-03 are themselves file-disjoint (`validation/CharacterValidation*` +
  `test/validation/` vs `exceptions/UrlSecurityException` + `AllowBlockListStage` +
  `test/exceptions/`), so they MAY be paired if the operator accepts that PLAN-03 then reads the
  escaping shape from PLAN-02's branch rather than from a landed HEAD. PLAN-02 and PLAN-15 may
  NOT — both edit `SecurityConfigurationBuilder.java`.
- This workstream is surface-disjoint from WS-03, WS-04, WS-06 and WS-07, so exactly one plan
  from here may run alongside one plan from any of those.
- WS-02 overlaps: its content-type work adds a length and a character stage that reuse
  `LengthValidationStage` / `CharacterValidationStage`. WS-02 is sequenced after PLAN-02.
- WS-05 overlaps in the opposite direction: several of its test fixes assert the exact
  behaviour this workstream is about to change. WS-05 is sequenced after WS-01 lands.

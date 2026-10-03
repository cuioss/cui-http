# WS-02: Content-Type, Cookie and Request-Collection Surfaces

epic: quality-report-remediation

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-02-contenttype-cookie-collection.md` and is tracked in
> the epic `status.json` `workstreams[]` field. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

This workstream owns the three surfaces the review identifies as "false feeling of security":
a content-type pipeline that enforces nothing beyond list membership, a `validateCookie` that
validates only the name prefix and nothing about the cookie's content, and a
`RequestCollectionValidator` that cannot deliver the hash-collision defence it advertises. Each
is a trap rather than a bypass — the protection a reader believes is there is simply absent —
so the closing condition is that each surface either enforces what its documentation claims or
its documentation stops claiming it. It closes when no public entry point on these three
surfaces returns a "valid" verdict for input it never inspected.

## Scope

- In scope: `ContentTypeValidationPipeline`, `AllowBlockListStage`,
  `CookiePrefixValidationStage`, the `Cookie` and `HTTPBody` data classes, `AttributeParser`,
  `RequestCollectionValidator`, the dead configuration and constant surface that suggests
  enforcement which does not exist, and the unit tests for all of the above.
- Out of scope: `DecodingStage` / `NormalizationStage` / `CharacterValidationStage` internals
  (WS-01) — this workstream *composes* those stages into the content-type pipeline but does not
  change their behaviour; the forwarded-header package (WS-03); the client packages (WS-04);
  the attack databases and generators (WS-05); documentation files (WS-06).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-04-contenttype-pipeline-enforcement | staged | Give the content-type pipeline length and character stages; normalise list entries; define `null` semantics; retract the promised-but-absent detections |
| PLAN-05-cookie-validation-completeness | staged | Cookie prefix case-insensitivity, RFC 6265 last-wins attributes, and actual name/value validation |

## Sequencing and Surface Notes

- Depends on WS-01 PLAN-02 and PLAN-03 landing first: PLAN-04 adds `LengthValidationStage` and
  `CharacterValidationStage` to the content-type pipeline, PLAN-02 is the plan that settles what
  that character stage rejects, and PLAN-03 is the plan that stops the raw value reaching the
  exception `detail` that `AllowBlockListStage` builds.
- PLAN-04 and PLAN-05 both touch the exception-detail construction shared by
  `AllowBlockListStage` and `CookiePrefixValidationStage`, so they are sequential relative to
  each other — never a concurrent pair.
- Both plans are surface-disjoint from WS-03, WS-04 and WS-07, so one plan from here may run
  alongside one plan from any of those.
- WS-06's documentation plans depend on this workstream: several documentation findings are
  "the doc claims an enforcement that does not exist", and the correct fix direction is only
  known once this workstream has decided whether to implement the claim or retract it.
- `RequestCollectionValidator` and the dead configuration surface were originally scoped here
  as a third plan. They moved to WS-01 PLAN-03 because they live in
  `de.cuioss.http.security.validation` and `de.cuioss.http.security.config` — the same files
  WS-01 already owns — and a plan boundary that cuts across a file boundary is the one shape the
  disjointness gate cannot serialize.

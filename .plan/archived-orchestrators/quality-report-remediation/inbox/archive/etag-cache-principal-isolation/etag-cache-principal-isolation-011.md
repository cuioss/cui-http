envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:32:08Z

# Candidate lesson: hardcoded lists mirroring a set defined elsewhere — two independent occurrences in one PR

Source signal: `pr-comment` findings `d41b9c` and `fa64ad` (PR #240, coderabbitai, both resolved `fixed` in-run via TASK-30 / TASK-31).
Component: `cui-http-core` — `HttpHandlerRedirectTest`, `HttpErrorCategoryTest`.

## What happened

The same anti-pattern was flagged twice, in two unrelated test classes, in a single PR — against a
repository whose own path instructions already state the rule ("treat a hardcoded list that must
mirror a set defined elsewhere — build goals, enum constants, registered handlers, dispatch tables
— as a defect unless it is derived from that source at build or run time"):

- `HttpHandlerRedirectTest`: the 303 `@CsvSource` cases and the non-vacuity guard's exact
  `DELETE`/`PUT` list were static, while the 301/302 cases already derived from
  `RedirectDispatcher.supportedMethods()`. Partial adoption of the derivation left two un-derived
  sites that could silently drift from the dispatcher registry.
- `HttpErrorCategoryTest`: asserted the enum's exact 6-member cardinality and a per-name `valueOf`
  list — so adding a legitimate new category fails a test that checks nothing behavioural. (This
  plan ADDED `INTERRUPTED_ERROR`, i.e. the plan itself paid the cost the anti-pattern imposes.)

## Corrective rule

Derive from the source of truth at run time (`MethodSource` over the registry, `values()` over the
enum) and assert BEHAVIOUR plus non-emptiness — never cardinality and never a mirrored name list.
Partial derivation is a specific trap: once some cases in a class derive, the remaining static ones
read as intentional and survive review.

## Generalisation for the epic

Both occurrences were caught by the review bot citing the repo's own path instructions, not by any
in-run gate. If the rule is already written down and still recurs twice per PR, the gap is
enforcement, not knowledge — worth a deterministic surfacer (a test asserting
documented-set-equals-declared-set, or a self-review candidate class for "exact-count assertion
over an enum/registry").

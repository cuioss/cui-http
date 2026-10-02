envelope_version=1
sender_type=plan
sender_id=cross-origin-credential-forwarding-proof
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-03T09:27:57Z

# Candidate lesson: no `ext-self-review-{domain}` implementor exists for Java — the clean pass rested on no evidence

**Class**: extension-point coverage gap, plus a dispatch-contract defect on the
zero-candidate path.

## Observation A — the surfacer is domain-blind for Java

`default:pre-submission-self-review` ran over this plan's diff (two Java files:
`HttpHandlerHttpsIntegrationTest.java` and `HttpHandler.java`) and surfaced **zero**
candidates. decision.log `5130b0`:

```
Candidate-count gate INLINE — total_candidates=0 (<=5 threshold, cov_scope=inherit),
by_family structural=0 prose_contract=0
```

The step then reported the gap against itself, correctly and explicitly
(decision.log `0f4cb3`, WARNING):

```
Zero-observation clean pass — delta coverage: none of the 2 files in scope produced a
candidate, across all 2 content class(es) present — this round surfaced NO observation
of its own, so a clean verdict here rests on no evidence drawn from this delta
```

The only implementor of the `ext-self-review-{domain}` extension point in the loaded
skill population is `pm-plugin-development:ext-self-review-plan-marshall`, whose
candidate families (regexes, markdown sections, frontmatter description-vs-body, keep
markers, advertised-form help strings, argparse/CLI shapes, …) are plan-marshall-corpus
shaped. Over a Java diff it has nothing to match, so it classifies `other` and returns
empty. There is no `pm-dev-java:ext-self-review-java`; the sibling extension points for
that bundle do exist (`pm-dev-java:ext-triage-java`, `pm-dev-java:arch-gate-java`), so
the asymmetry is specific to self-review.

The step's own honesty is what makes this reportable rather than invisible: it did not
claim a checked negative, it stated a silence. The defect is that the step still
recorded `outcome=done` and the pipeline moved on, so downstream the run is
indistinguishable from one that was actually self-reviewed.

## Observation B — the zero-candidate case is not expressible in the dispatch prompt

At the same step, work.log `3ec489` (07:35:45Z) records:

```
[ERROR] [STATUS] (plan-marshall:execution-context.pre-submission-self-review)
        Missing required prompt field: candidates
```

The dispatched envelope refused its own prompt because `candidates` was required and the
surfacer had produced none. The zero-candidate outcome — the legitimate result of a
surfacer with no domain implementor — has no representation in the prompt contract, so
the correct empty result reads as a malformed dispatch. The step nonetheless completed
`done` 67 seconds later, meaning the error was absorbed rather than routed.

## Candidate corrective action

1. Decide whether a `pm-dev-java:ext-self-review-java` implementor should exist. Java has
   obvious deterministic candidate families (symmetric-pair methods, javadoc-vs-signature
   drift, `@Nullable`/`@NonNull` boundary asymmetry, flag-guard pairs, duplicated literals
   across test and production, assertion-count-vs-prose claims), so the extension point is
   not inapplicable to the domain — it is simply unimplemented.
2. Independently of (1), make the empty candidate set a FIRST-CLASS value in the
   pre-submission-self-review dispatch contract (an explicit empty list, or a distinct
   no-candidates branch that skips the dispatch). A required field with no legal empty
   representation forces every genuine zero to present as a contract violation.
3. Consider whether a zero-observation clean pass should record `outcome=skipped` with the
   coverage gap named, rather than `outcome=done` — `done` claims a review that the step's
   own delta-coverage line says did not happen.

## Evidence

- plan decision.log entries `5130b0`, `0f4cb3`
- plan work.log entries `fb6344`, `195d46`, `3ec489`, `a5ed4e` (07:34:19Z-07:36:52Z)
- plan `references.json` `affected_files` (the 2-file scope)
- loaded skill population: `ext-self-review-plan-marshall` is the sole implementor

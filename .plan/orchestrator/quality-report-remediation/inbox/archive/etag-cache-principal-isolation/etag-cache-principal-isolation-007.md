envelope_version=1
sender_type=plan
sender_id=etag-cache-principal-isolation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-16T15:31:49Z

# Candidate lesson: a documentation-set fix missed its in-source Javadoc twin — caught twice by the scope validator, at outline and again at plan

Source signal: Q-Gate findings `e1277e` (3-outline) and `b2dbbd` (4-plan), both resolved `fixed` by the same commit.
Component: `cui-http-core` — `HttpResult.java` class-level Javadoc sample / deliverables D10 + D13.

## What happened

Deliverable 13's success criterion was "neither published document still presents the error-category
set as five members". The outline's `affected_files` for D13 covered only the `.adoc` documents.
The identical five-arm exhaustive switch over `HttpErrorCategory` also existed as a Javadoc code
sample inside `HttpResult.java` (lines 90-116) — a file that WAS listed under D10, but whose
"Change per file" entry covered only the status contract and null-safety, not the Javadoc sample.

Once D10 added `INTERRUPTED_ERROR`, that sample would have been non-exhaustive and would not have
compiled if copied. The `scope_criterion_validator` flagged the gap as `under_coverage` at
3-outline and, because it was still unresolved at task-creation time, flagged the SAME gap again
at 4-plan against the task-level footprint. One commit (`8da51f8`) closed both.

## Corrective rule

When a success criterion is phrased over "documents", operationalise it as a content search over
the WHOLE repository, not over the doc tree: a documentation claim duplicated as an in-source
Javadoc/docstring sample is the same claim and the same defect. A file appearing in a sibling
deliverable's `affected_files` does NOT make it covered — coverage is per "Change per file"
instruction, not per file listing.

## Generalisation for the epic

The validator did its job twice, which is the good news; the bad news is that a gap detected at
3-outline survived into 4-plan unresolved and had to be re-detected. Worth considering whether an
unresolved `under_coverage` finding should block task creation for the affected deliverable rather
than being re-raised against the task-level footprint.

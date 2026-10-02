envelope_version=1
sender_type=plan
sender_id=client-converter-and-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T09:31:19Z

# Candidate lesson: a review step whose surfacer covers none of the changed content still records `outcome=done` — an honest empty result and a real clean result are the same observable

## Signal provenance

Reported by the `pre-submission-self-review` step itself during finalize. The step's surfacer
is a **plan-marshall-domain** implementor; the plan's changed content was **Java** (Javadoc
accuracy corrections plus client code).

## Observation

`pre-submission-self-review` ran and reported honestly that its surfacer has **no detectors
for Java content**, so it could not review the Javadoc changes for any of the defect classes
the step names (stale count-prose, description-vs-body drift, duplicate-claimable keys,
worked-example clause pairs, and the rest). The step did not fail, did not warn at a level
that gated anything, and returned a completed outcome.

That coverage gap was closed only because the orchestrator noticed and ran a **manual audit**
of the Javadoc changes. Had the orchestrator not done so, the run would have carried a
green `pre-submission-self-review` outcome over content the step never looked at — and the
plan's entire subject was Javadoc accuracy, i.e. exactly the content the step is meant to
catch errors in.

The structural problem: the step's output shape does not distinguish

- "I ran my detectors across the changed surface and found nothing", from
- "none of my detectors apply to any of the changed surface".

Both render as a completed step with no findings. The second is a **coverage gap**, and it is
strictly more dangerous than a finding, because a finding at least announces itself.

## Reusable rule (candidate)

**A domain-scoped analysis step must report its coverage, not just its findings.** Concretely:

1. The step should emit the **matched vs unmatched share of the changed surface** — how many
   changed paths its detectors could actually apply to, and which ones they could not. A
   changed-file set that is 100% unmatched is a distinct, reportable outcome.
2. Zero-coverage over a non-empty changed set should be a **loud** outcome (a finding, or an
   explicit `skipped` with the reason), never a silent `done`. This is the same discipline
   the codebase already applies to store-resolution zeros: *say which kind of zero this is*.
3. Where the surfacer is selected per domain, the dispatcher should notice that the plan's
   changed content is in a domain the selected surfacer does not cover, and either resolve a
   surfacer that does or record the gap explicitly.

Credit where due: the step **did** report the gap honestly in its own prose. The defect is
that the honest report had no machine-readable channel and no gating consequence — it relied
on a human reading it.

## Suggested routing (orchestrator to judge)

- Candidate component: `plan-marshall:phase-6-finalize` (the `pre-submission-self-review`
  step and its surfacer resolution) or
  `pm-plugin-development:ext-self-review-plan-marshall` (the domain-scoped implementor).
- Category: `improvement` (arguably `bug` — a green step over unreviewed content).
- Strength: high, and it generalizes to every `ext-*` extension point where an implementor is
  resolved per domain but the step's outcome is domain-blind.

## Plan context

- Plan: `client-converter-and-javadoc-accuracy` (epic `quality-report-remediation`)
- Gap closed by a manual orchestrator audit of the Java Javadoc changes; PR #182 merged as
  `e02f445`.

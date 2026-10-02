envelope_version=1
sender_type=plan
sender_id=post-decode-character-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T11:26:40Z

component=plan-marshall:phase-6-finalize
category=improvement
bundle=plan-marshall

# A universal-quantifier claim written by a plan is a checkable assertion about configuration, and self-review does not check it

## What happened

This plan added an allow-list to `CharacterValidationStage` and documented it in Javadoc with
a claim that the allow-list applies **"in every pipeline"**.

That is false. `CharacterValidationStage` precedes `DecodingStage` only in the URL path and
URL parameter pipelines; the header pipeline composes the stages differently, so the claim
over-generalizes to a configuration where it does not hold.

CodeRabbit caught it in PR review. The pre-submission self-review did not — and the plan's own
stated success criterion was documentation accuracy, so this is a slipped-then-caught defect
against the very property the plan existed to establish.

## Why this generalizes

A universal-quantifier claim about a *configurable composition* is not prose. It is an
assertion with a decidable truth value, checkable against the set of configurations that
exist in the repository:

> "in every pipeline" / "all validators" / "always runs before" / "any request" /
> "never reaches"

Each such phrase names a **population** and asserts a property over it. The population is
enumerable — the pipelines are declared in code, the stage orders are literal, the set is
small and static. The assertion is therefore mechanically falsifiable by enumerating the
population and checking the property against each member. Nothing about it requires review
judgement.

This is a good self-review surfacer candidate for exactly the reasons the existing ones
qualify: it is **deterministic** (a regex over quantifier vocabulary in the plan's own diff),
it is **scoped** (only text this plan introduced, bounded by the step's `--since-ref`
anchor), and it produces a **candidate, not a verdict** — the surfacer flags "you asserted a
universal here; enumerate the population and confirm", and the reviewer confirms or
qualifies.

The failure mode it catches is a specific and recurring authoring habit: a change is written
and verified against ONE configuration, and the documentation is then written from the
author's mental model of that configuration, generalized. The generalization is the defect,
and it is invisible to the tests, because the tests exercise the configuration the author was
thinking about.

## Proposed remedy

Add a surfacer to the pre-submission self-review's deterministic candidate set:

- **Trigger**: a universal or absolute quantifier (`every`, `all`, `always`, `never`, `any`,
  `no ...` ) appearing in documentation, comments, or Javadoc **added by this plan's diff**.
- **Emit**: one candidate per occurrence, quoting the sentence and naming the file/line.
- **Ask**: "name the population this quantifies over, and confirm the property holds for
  every member" — or qualify the claim to the subset where it holds.
- **Scope guard**: added text only, so a large existing corpus is not re-surfaced on every
  run; the anchor the step already receives as `--since-ref` bounds it.

The correct fix in this instance was a qualification, not a code change: the Javadoc now
names the two pipelines where the ordering holds. That is the typical resolution, and it is
cheap — the expensive part was that a review bot had to find it.

## Impact

Any plan that documents behaviour of a composable/configurable subsystem — which is most
plans touching a pipeline, a stage chain, a middleware stack, or a rule set. The defect
survives testing by construction.

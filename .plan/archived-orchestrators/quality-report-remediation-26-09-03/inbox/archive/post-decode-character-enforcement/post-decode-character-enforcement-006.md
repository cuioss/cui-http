envelope_version=1
sender_type=plan
sender_id=post-decode-character-enforcement
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-27T11:26:34Z

component=plan-marshall:phase-6-finalize
category=improvement
bundle=plan-marshall

# A correct fail-closed default over an UNDECLARED surface is a silent recurring cost, not a one-off

## What happened

The verdict-currency classifier returned `invalidated` for all **7** head-dependent finalize
steps after a docs-only commit. None of the 7 declares a `verdict_inputs` surface, and the
fail-closed default for an undeclared surface is "assume the commit could have invalidated
this verdict".

The classification is *correct*: with no declared input surface, the classifier genuinely
cannot know that a prose-only delta is irrelevant to a step's verdict, and guessing
"unaffected" would be exactly the fail-open this design avoids.

It is also expensive. A full settle-band plus wait-region re-fire was triggered **twice**,
both times for prose-only deltas that could not have changed any of the 7 verdicts.

## Why this generalizes

The pattern is broader than verdict currency: **a fail-closed default is the right behaviour
and the wrong steady state.** It is correct on every individual evaluation and wrong as a
long-run condition, because it converts a missing declaration into a recurring cost that is
paid silently, per run, forever, by whoever happens to be running the plan.

The specific property that makes it silent: the fail-closed branch is indistinguishable, from
the outside, from a genuine invalidation. Both render as "verdict invalidated, re-fire". A
run pays a full settle band and sees no signal saying *the re-fire was caused by an absent
declaration, not by a real change*. Nobody is ever prompted to fix it, because nothing ever
reports it as a gap.

Contrast with how the codebase handles the same tension elsewhere: `mark-step-done` writes
the record but attaches a `warning` field when head-dependence could not be derived, and the
skill body states the principle — an unresolvable derivation must not manufacture a refusal,
but must not pass silently either. That is the missing half here.

## Proposed remedy

1. **Report the reason, per step.** When the classifier invalidates on the fail-closed
   default rather than on an observed input-surface intersection, say so:
   `invalidated_reason: no_verdict_inputs_declared` alongside
   `invalidated_reason: inputs_touched`. This makes the recurring cost attributable in the
   run's own output instead of requiring someone to reason it out afterwards.
2. **Surface the population as a gap.** A finalize-step implementor declaring
   `head_dependent: true` with no `verdict_inputs` is a structurally detectable
   under-declaration — the same frontmatter-reading path the contract test already walks for
   `head_dependent`, `mutates_source`, and `records_facts`. It belongs in the doctor rule
   set, not in a per-run tax.
3. **Declare `verdict_inputs` on the 7 steps.** This is the designed remedy and it should
   happen; (1) and (2) are what stop the next 7 from repeating it.

The ordering matters: fixing only (3) closes today's instance and leaves the mechanism that
produced it intact.

## Impact

Every head-dependent finalize step, on every run whose HEAD advances — which is every run
that loops back, i.e. exactly the runs that are already the expensive ones.

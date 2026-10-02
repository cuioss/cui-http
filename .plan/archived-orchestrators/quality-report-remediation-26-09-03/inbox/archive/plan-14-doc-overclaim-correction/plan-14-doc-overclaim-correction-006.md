envelope_version=1
sender_type=plan
sender_id=plan-14-doc-overclaim-correction
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T19:27:27Z

component=plan-marshall:manage-config
category=improvement
bundle=plan-marshall

# The documentation skill domain declares no skills_by_profile.implementation, so doc plans resolve an empty implementation skill set

Q-Gate finding `bb0ea9` (4-plan, resolution `accepted`) in
plan-14-doc-overclaim-correction: the `documentation` module in cui-http's
`marshal.json` declares only a bundle and a triage extension —

```json
"documentation": {
  "bundle": "pm-documents",
  "workflow_skill_extensions": { "triage": "pm-documents:ext-triage-docs" }
}
```

— with no `skills_by_profile` block, while `skill_domains.active_profiles` includes
`implementation`. A documentation-domain task running the `implementation` profile
therefore resolves an empty skill set: no `ref-asciidoc`, no `ref-documentation`, no
`persona-documenter`. The task still runs; it just runs without the domain knowledge
that would tell it how AsciiDoc cross-references, includes, and traceability tables
are supposed to behave.

It was accepted rather than fixed in this plan because it was out of scope, and
because the resulting behaviour is degraded rather than broken — which is exactly
what makes it persist. An empty skill resolution produces no error and no warning; it
produces a task that proceeds on general knowledge.

This is not incidental to the quality-report-remediation epic. The epic is
documentation-heavy, so every remaining doc plan in it inherits the same gap, and the
finding classes those plans are chasing (stale cross-references, mis-scoped includes,
traceability drift) are precisely what the missing skills cover.

## Solution

Populate `skills_by_profile.implementation` for the `documentation` module in
`.plan/marshal.json`, minimally with the pm-documents authoring surface
(`pm-documents:ref-asciidoc`, `pm-documents:ref-documentation`) plus
`plan-marshall:persona-documenter`. Resolve the exact set via
`manage-config get-skills-by-profile` / `resolve-domain-skills` against the
`pm-documents` bundle rather than hand-listing it.

More generally, treat "a module registered for an active profile but declaring no
skills for it" as a configuration smell worth surfacing at steward/health-check time.
The distinction that matters is between *deliberately no skills* and *nobody filled
this in*, and an absent block cannot express the first.

## Impact

Applies to any project whose `marshal.json` registers a skill domain against a
subset of `active_profiles`. The symptom is silent under-resolution — tasks proceed
with less domain knowledge than the configuration implies they have — so it is found
by inspection, never by failure.

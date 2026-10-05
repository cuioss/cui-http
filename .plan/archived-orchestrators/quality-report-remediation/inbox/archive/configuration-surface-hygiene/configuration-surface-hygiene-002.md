envelope_version=1
sender_type=plan
sender_id=configuration-surface-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-09T09:40:29Z

component=pm-dev-java:javadoc
category=anti-pattern
signal=review
source=coderabbit
resolution=fixed

# Preset count restated as a literal in Javadoc drifts from the declaring source

## Observation

CodeRabbit flagged `SecurityDefaults`' Javadoc for restating the number of presets as a bare
literal in more than one place. The count is derivable from the declaring source (the preset
constants themselves), so each hand-written copy is an independent staleness site: adding a
preset silently falsifies every copy, and the doc reads as authoritative while being wrong.

Remediated in-run — resolution `fixed`.

## Why it matters

This is the count-prose-staleness class. A duplicated count is worse than an absent one because
a reader trusts it, and the drift is invisible at the site that carries it — nothing near the
literal references the set it counts. Duplication multiplies the number of edits a future change
must remember to make, and the reminder lives nowhere.

## Corrective rule

Do not restate a derivable cardinality as a literal in documentation. Either (a) point the prose
at the declaring source ("one per constant declared in `SecurityDefaults`") instead of naming a
number, or (b) if a number genuinely must appear, carry it at exactly ONE site and have every
other mention reference that site. When a count must be duplicated for readability, add a test
that asserts documented-count equals declared-count rather than relying on reviewer vigilance.

## Candidate scope

Global — the rule is the marketplace's existing count-prose-staleness discipline applied to
Javadoc. Worth checking against the existing corpus for a lesson already covering it.

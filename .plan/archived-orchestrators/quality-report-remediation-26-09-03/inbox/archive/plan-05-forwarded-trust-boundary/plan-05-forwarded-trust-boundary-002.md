envelope_version=1
sender_type=plan
sender_id=plan-05-forwarded-trust-boundary
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T20:08:57Z

# Candidate lesson: justifying a suppression removal by a type change must be checked against every site the suppression covered

## Signal

Source: `signal_qgate_pending_count` — 8 Sonar issues surfaced in phase 5, requiring a
loop-back to fix.

## Observation

The phase-3 outline required deleting a class-level `@SuppressWarnings("java:S4276")`.
The reasoning recorded in the outline was that S4276 only fires on a `Function<T,T>`
shape, and the public accessor's signature had changed to `Function<String,List<String>>`
— so the rule could no longer fire and the suppression was dead.

That reasoning was correct about the public entry point and wrong about the class. The
suppression was class-level, and the private helpers it also covered still took
`Function<String,String>` — exactly the `Function<T,T>` shape S4276 targets. Removing
the suppression surfaced 8 Sonar issues that a loop-back had to fix.

## Root cause shape

The outline reasoned about the surface that motivated the change (the public accessor)
and silently generalized that conclusion to the whole scope the annotation governed
(the class). The gap is between *the site that prompted the reasoning* and *the site
set the suppression actually covered* — a class-level annotation covers every member,
and a member-level justification does not transfer to it.

## Candidate directive

When justifying the removal of a suppression on the grounds that a type change made the
rule inapplicable, enumerate every site the suppression covered and verify the claim
against each one — not only the site that motivated the removal. The scope of the
justification must match the scope of the annotation: a class-level suppression needs a
class-wide justification.

## Evidence

- Phase-3 outline deliverable requiring removal of the class-level
  `@SuppressWarnings("java:S4276")`, with the `Function<String,List<String>>` rationale.
- Phase-5 loop-back fixing the 8 resulting Sonar S4276 issues on the private helpers,
  which retained `Function<String,String>` signatures.

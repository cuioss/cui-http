envelope_version=1
sender_type=plan
sender_id=attack-database-and-assertion-quality
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-31T17:23:19Z

# Candidate lesson: `.formatted()` binds to the trailing literal, not the concatenation

## Observation

New test code written by this plan used the shape:

```java
assertTrue(cond, "context prefix " + "detail %s and %s".formatted(a, b));
```

The `.` postfix binds tighter than `+`, so `.formatted()` applies only to the
trailing string literal. The concatenation still produces a well-formed message,
so nothing fails at compile time or at runtime — but when the format string is
split so that a `%s` ends up in the *leading* literal, that placeholder is never
substituted and the assertion message silently ships a raw `%s`.

## Why it is durable and non-obvious

- ONE root cause produced **5 SonarCloud `java:S3457` findings and 5 matching
  CodeQL findings across 3 files**. The fan-out is characteristic: the shape gets
  copied across sibling test methods, so the defect arrives in clusters.
- It was introduced by this plan and caught **only by external static analysis**
  (SonarCloud + CodeQL on the PR), not by compilation, not by the test run, and
  not by the plan's own gates. There is no local signal at all.
- The failure mode is *silent message corruption*, which is invisible precisely
  in the artifact people read when a test fails.

## Corrective rule

In any `String.format`/`.formatted` assertion message, the format call must own
the whole message. Either parenthesise the concatenation
(`("a " + "b %s").formatted(x)`) or, preferably, use a single format string with
no `+` at all. Treat `" + "` appearing anywhere to the left of `.formatted(` in
a message expression as the review trigger.

## Candidate scope

Java test-authoring convention; not project-specific. Plausibly belongs with the
JUnit/assertion authoring standards rather than as a cui-http-local lesson.

## Provenance

Plan `attack-database-and-assertion-quality`, PR #178 (merged as 30edfa3).
Signal source: `signal_automated_review_count` (SonarCloud + CodeQL PR findings,
remediated in-run).

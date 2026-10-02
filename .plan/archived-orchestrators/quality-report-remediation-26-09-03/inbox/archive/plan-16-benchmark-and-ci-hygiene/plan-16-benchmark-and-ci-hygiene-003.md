envelope_version=1
sender_type=plan
sender_id=plan-16-benchmark-and-ci-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T16:06:17Z

# Candidate lesson: verify a prescribed fix against the resolved effective configuration, not against the task text

## Observed signal

A task prescribed a concrete fix: set `maven.deploy.skip` to stop publication to
Maven Central. Applying it literally would have produced a change that looked
correct and changed nothing:

- The effective POM showed publication actually running through
  `central-publishing-maven-plugin`, whose own switch is `skipPublishing` —
  a different plugin with a different property.
- The parent POM already set the deploy-plugin skip globally, so the prescribed
  property was a no-op even on its own terms.

Reading the **effective** POM rather than the task text is what caught it.

## Why it is durable

The general failure is that a task specification names a *mechanism* as well as
an *outcome*, and the two can diverge. A prescribed mechanism is a hypothesis
about how the system works, written at planning time by someone reading the
declared configuration rather than the resolved one. Config systems with
inheritance and plugin layering (Maven parent POMs and effective POMs, CI
workflow inheritance, layered tool configs) routinely make the declared and the
resolved views disagree — and a fix applied to the declared view passes review,
passes the build, and silently does not achieve the outcome.

## Proposed durable directive

When a task prescribes a specific configuration key or flag as the fix:

- Treat the **outcome** as the requirement and the prescribed key as a
  *hypothesis to verify*.
- Before applying, resolve the effective configuration (`mvn help:effective-pom`
  or equivalent for the stack) and identify which component actually performs
  the behaviour being suppressed, and which switch that component reads.
- If the prescribed key is not the one the effective configuration routes
  through, implement the effective one and report the divergence back — the
  task text is now known-wrong and its source should be corrected too.
- A fix whose effect cannot be observed in the effective/resolved view is
  unverified, regardless of how plausible the key name is.

## Suggested classification (orchestrator judgement)

Likely `improvement` or `anti-pattern`, scoped to build/deploy configuration
tasks generally rather than to Maven specifically.

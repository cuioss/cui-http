envelope_version=1
sender_type=plan
sender_id=client-converter-and-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T09:30:30Z

# Candidate lesson: scoping a fix around a named mechanism requires proving the mechanism exists AND that it addresses the threat — plausibility is not existence

## Signal provenance

Caught by the operator at dispatch review, before the fix task was dispatched. No script
failure and no bot comment recorded it — this is a near-miss that only the operator's read
surfaced.

## Observation

After the redirect finding above was confirmed, the orchestrator scoped the remediation as:

> validate the redirect target through the currently configured `de.cuioss.http.security` pipeline

That instruction is wrong on **two independent axes**, and each alone would have wasted the
fix task:

1. **The mechanism does not exist.** `HttpHandler` configures no `de.cuioss.http.security`
   pipeline at all. The client package-info says so explicitly — the security pipelines are
   an *inbound* request-validation surface, and the client is an *outbound* one. There is no
   "currently configured pipeline" to route a redirect target through.
2. **Even if it existed, it would not stop the attack.** Those pipelines detect traversal and
   CVE patterns *within a path*. The redirect threat is an **egress host policy** question —
   "may this client talk to `evil.example.com` at all" — which no path-pattern matcher
   answers. A syntactically clean URL pointing at an attacker-controlled host passes every
   one of those stages.

The instruction was plausible because the component names were real and adjacent, and because
"use the project's own validation" is the shape a correct answer usually takes. Plausibility
is exactly what makes this class of error survive review.

## Reusable rule (candidate)

When scoping remediation work around a **named existing mechanism**, two checks are owed
before dispatch, and they are separate:

1. **Existence** — verify the named mechanism is actually wired into the component being
   fixed. Read the wiring, not the package name. A component in the same artifact, or in an
   adjacent package, is not evidence of configuration.
2. **Threat fit** — verify the mechanism's *detection class* matches the *threat class*.
   Name both explicitly: "the mechanism detects X; the threat is Y". A path-pattern matcher
   does not answer a host-policy question; an authn check does not answer an authz question;
   an input sanitizer does not answer a resource-exhaustion question.

Failing either check means the fix task ships work that cannot possibly close the finding,
and — worse — closes it on paper. The second check is the one usually skipped, because a
mechanism that genuinely exists feels like it has been verified.

## Suggested routing (orchestrator to judge)

- Candidate component: `plan-marshall:phase-6-finalize` (the triage/fix-task-allocation site
  where the instruction was authored) or `plan-marshall:persona-security-expert`.
- Category: `anti-pattern`.
- Strength: high, and generalizes well past this project — the two-axis check (existence,
  threat fit) is stateable as a dispatch precondition.

## Plan context

- Plan: `client-converter-and-javadoc-accuracy` (epic `quality-report-remediation`)
- Caught pre-dispatch; the redirect change was reverted instead, with validated redirect
  following deferred to a follow-up plan.

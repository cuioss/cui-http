envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:15:51Z

# Candidate lesson: deliverable asserted an exemption but placed the new guard where the exempt branch also lands

**Source**: Q-Gate finding `5f1bf9` (3-outline, type `triage`, severity `warning`, resolution `taken_into_account`)
**Plan**: plan-07-forwarded-parsing-and-validation
**Component under edit**: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java`

## Observation

Deliverable 1 said the new positive reg-name host predicate is "applied at the same call site inside
`parseHostPort` (the `host.isEmpty() || containsHostSeparator(host)` guard)", and separately that
"the bracketed-IPv6 branch is NOT routed through the new predicate".

At HEAD that guard is line 582, which is **post-branch-chain**: every branch reaches it, including
the bracketed one, where `host` is the retained literal `[2001:db8::1]` (line 572). A literal
substitution at 582 therefore rejects every bracketed host, breaking the existing
`rejectsBracketedNonLiteralHost` positive control and the deliverable's own success criterion
("Every host shape the landed test package accepts today is still accepted").

The deliverable **stated** the exemption but never specified the **mechanism** that produces it.

## Why this is lesson-bearing

The failure class is **an asserted exemption with no mechanism**. The two sentences are individually
true-sounding and jointly unimplementable, and the contradiction only surfaces when the cited line is
read in its control-flow context. A deliverable that says "X is exempt" at a shared call site is
incomplete unless it says *how* X avoids the site.

The corrective that closed the finding is the reusable shape: give one or more concrete, equivalent
implementable forms (here: (a) early-return the bracketed branch before the shared guard, noting that
branch keeps its own complete validation via `hasValidBracketTrailer` + `IpAddresses.parse`; or
(b) gate the shared guard on the value not starting with `[`), mark one preferred, and have the
later "NOT routed through" sentence cross-reference the mechanism rather than stand as a bare
assertion.

## Suggested disposition (orchestrator judges)

Candidate `plan-marshall:phase-3-outline` rule, or a Q-Gate detector: "a deliverable that adds a
guard at a call site AND claims a branch is exempt must name the mechanism". Likely cross-plan.

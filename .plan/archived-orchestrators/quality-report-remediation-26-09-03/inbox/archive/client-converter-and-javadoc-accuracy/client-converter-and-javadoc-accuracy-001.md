envelope_version=1
sender_type=plan
sender_id=client-converter-and-javadoc-accuracy
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-01T09:30:07Z

# Candidate lesson: an operator-confirmed behaviour decision is not review-proof — a security finding arriving after implementation must reopen the decision, not be argued against it

## Signal provenance

`signal_automated_review_count = 1`. CodeRabbit rated PR #182 **Merge Risk: HIGH** during the
finalize automated-review step, after the behaviour change had already been implemented,
verified, and pushed.

## Observation

The plan changed `HttpClient` redirect policy from `NEVER` to `HttpClient.Redirect.NORMAL`.
That change was **explicitly confirmed by the operator** at refine time as an intended
behaviour change — it had the strongest authorization signal the pipeline can produce.

The automated review then observed that redirect destinations are never revalidated against
the library's own URI policy: following a redirect means the client will issue a request to a
host the caller never named and no policy ever approved (an SSRF-shaped egress hole in a
*security* library). The finding was confirmed against the code, and the decision was
**reverted**; validated redirect following was deferred to a follow-up plan.

The failure mode is one of ordering, not of judgement. The operator confirmed a *behaviour*
(follow redirects) at a point where the *security consequence* of that behaviour had not been
analysed. By the time the analysis arrived, the decision carried the authority of an operator
sign-off — and the natural pull is to treat that sign-off as settling the question and to
defend the decision rather than reopen it.

## Reusable rule (candidate)

**An operator decision authorizes the change that was described to the operator, not every
consequence discovered later.** When a security finding lands against an already-confirmed,
already-implemented decision:

1. Confirm the finding against the code first (it was, here — the revalidation genuinely does
   not exist).
2. Treat the confirmed finding as **new information that invalidates the premise the operator
   confirmed under**, and reopen the decision. Do not weigh "the operator already approved
   this" as evidence the finding is out of scope.
3. Reverting plus deferring the capability to a follow-up plan is a legitimate resolution —
   shipping the hole with the operator sign-off cited as cover is not.

The stronger, cheaper form of the rule: **a behaviour change in a security-domain component
should have its threat consequence analysed BEFORE the confirmation is sought**, so the
operator is confirming the actual trade rather than a description of it. An egress-policy
change (redirects, proxies, hostname resolution, TLS trust) is exactly the class where the
described behaviour and the security consequence diverge.

## Suggested routing (orchestrator to judge)

- Candidate component: `plan-marshall:phase-2-refine` (where the confirmation is sought) or
  `plan-marshall:persona-security-expert` (where the "analyse before confirming" obligation
  would live for egress-policy changes).
- Category: `anti-pattern`.
- Strength: high. This is a defect-plus-corrective-action rule, and the cost avoided was a
  real SSRF-shaped hole in a security library.

## Plan context

- Plan: `client-converter-and-javadoc-accuracy` (epic `quality-report-remediation`)
- PR #182, merged to `main` as `e02f445` after the revert.

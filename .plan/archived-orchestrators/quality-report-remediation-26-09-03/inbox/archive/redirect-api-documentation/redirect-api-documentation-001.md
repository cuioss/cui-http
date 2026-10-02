envelope_version=1
sender_type=plan
sender_id=redirect-api-documentation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T21:07:34Z

component=plan-marshall:phase-6-finalize
category=anti-pattern
bundle=plan-marshall

# A reachability proof over one of N paths is not a proof over the claim

## What happened

A wait-region triage agent resolved a Major security finding (`cdb373`, PR #191 on
`cuioss/cui-http`) as `taken_into_account` on the rationale "the claimed exposure is
unreachable". The exposure was in fact **reachable**, and the plan had to loop back from
`6-finalize` to `5-execute` to fix it after an operator override.

The triage agent's own recorded rationale (decision-log `3b03c6`) proved exactly one thing:

- `RedirectPolicy.refuse()` evaluates `PROTOCOL_DOWNGRADE` (rule 2, scheme-only comparison)
  unconditionally before same-origin (rule 3) and the allowlist (rule 4), so an
  `https -> http` hop is refused regardless of host, and `HttpHandler.nextHop()` throws
  before ever reaching `rebuildForHop()` / `forwardsCredentials()`.

From that single path it concluded the whole claim was unreachable. It never evaluated the
`http -> http` path — **which its own dispatch prompt had explicitly asked it to settle**.
Re-verification at HEAD `48f203e` showed that for an http-origin handler,
`PROTOCOL_DOWNGRADE` does not fire (that branch requires `fromScheme == https`),
`isSameOrigin` returns true, `refuse()` permits the hop,
`forwardsCredentials()` (`RedirectPolicy.java:214`) returns
`isSameOrigin || FORWARD_TO_ALLOWLISTED` with **no target-scheme consideration**, and
`rebuildForHop()` (`HttpHandler.java:785`) applies that verdict directly with no scheme check
of its own. `Authorization` and `Cookie` therefore survive a cleartext `http -> http` hop.

## Why it is a lesson, not a one-off

The failure mode is structural and asymmetric:

- For a **fix** verdict, a partial analysis costs a redundant fix. Cheap.
- For a **dismiss** verdict (`taken_into_account`, `wont_fix`, `false_positive`), a partial
  analysis is a **false-negative generator** — the finding is closed, the record now carries an
  authoritative-sounding refutation, and nothing downstream re-opens it. Here it also produced a
  self-reinforcing artifact: the dismissal cited a *standing* Q-Gate decision (`f72f7a`,
  "forwardsCredentials never alters a refuse verdict") that was itself derived from the same
  partial analysis, so the two records corroborated each other while both were incomplete.

The seductive part is that the proof it *did* produce was correct, specific, cited real line
numbers, and read as rigorous. Rigour over the wrong domain is indistinguishable from rigour
over the right one unless the domain itself is stated.

## Proposed rule

For any triage verdict that **dismisses** a security finding on unreachability grounds:

1. **Enumerate the paths first, then discharge them.** State the path population the claim
   spans (here: `https->http`, `http->http`, `http->https`, `https->https`) before analysing
   any one of them, and record a per-path verdict. A dismissal is admissible only when every
   enumerated path is discharged.
2. **A path the dispatch prompt named explicitly is never dropped silently.** If the prompt
   asked for a path and the analysis does not settle it, that is a `blocked` / partial return,
   not a `taken_into_account`.
3. **Never let a guard's precondition go unstated.** The rationale asserted "PROTOCOL_DOWNGRADE
   refuses it" without recording that the branch is conditioned on `fromScheme == https`. The
   unstated precondition is precisely what made the generalisation invisible.
4. **A dismissal citing a prior standing decision must check whether that decision shares the
   analysis being relied on.** Mutual corroboration between two records derived from one
   incomplete analysis is not corroboration.

## Cross-repo note

The component names a `plan-marshall` bundle skill, which the `cui-http` lessons store does not
own. Integration belongs on the `plan-marshall` side per the orchestrator's
`workflow/lessons-handling.md` cross-repo integrate-then-remove path.

## Evidence

- Decision log `3b03c6` (the incomplete triage rationale) and `8c3d32` (the operator override
  with the three corrections), plan `redirect-api-documentation`.
- Re-verified at HEAD `48f203e`: `RedirectPolicy.java:188` (`PROTOCOL_DOWNGRADE`),
  `RedirectPolicy.java:214` (`forwardsCredentials`), `HttpHandler.java:785` (`rebuildForHop`).

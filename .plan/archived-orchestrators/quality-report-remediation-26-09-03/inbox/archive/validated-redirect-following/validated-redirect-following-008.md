envelope_version=1
sender_type=plan
sender_id=validated-redirect-following
epic=quality-report-remediation
kind=landing
created=2026-09-01T20:00:18Z

## What landed

validated-redirect-following shipped as PR #186 (merged via the required merge queue).

```landing-facts
schema=landing-facts/1
plan_id=validated-redirect-following
epic=quality-report-remediation
pr=186
merge_state=merged
deliverables_total=6
deliverables_done=6
total_tokens=4865380
total_wall_seconds=52440
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,finalize-step-security-audit:done,architecture-refresh:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:skipped,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:skipped,record-metrics:done,finalize-step-print-phase-breakdown:skipped,emit-landing:done,archive-plan:done
```

## Residue

Items the epic should track that no step recorded as a fact.

**The redirect loop-placement decision PLAN-17 deferred to outline was settled in `HttpHandler`.**
Both send sites route through it: `pingWithMethod` and `ETagAwareHttpAdapter.executeAsyncRequest`
(the adapter's own `httpClient` field was removed in favour of `httpHandler.sendAsync`).
`ResilientHttpAdapter` inherits coverage by delegation. The one uncovered path is a caller taking the
raw client from `createHttpClient()`, which keeps JDK `Redirect.NEVER` and therefore follows nothing —
fail-secure, asserted in tests and documented in Javadoc.

**One operator decision widened the declared design beyond PLAN-17's six deliverables.** Credential
stripping on cross-origin hops became strategy-configurable rather than unconditional: a public
`RedirectPolicy.CredentialForwarding` enum with `STRIP_ON_CROSS_ORIGIN` as the secure default and
`FORWARD_TO_ALLOWLISTED` as an explicit opt-in. The opt-in can only widen what the client carries,
never where it talks — it changes no `refuse` verdict, and same-origin hops forward headers unchanged
under every strategy.

**Scope widened to multi_module, as PLAN-17 anticipated it might not.** The WARN `HTTP-117` record
obliged a `doc/LogMessages.adoc` row, so the documentation module was touched after refine had asserted
it would not be. Operator-accepted at the outline review gate.

**The HTTPS to HTTP downgrade refusal is still not asserted end-to-end** — third consecutive plan
blocked by the project-wide broken `@EnableMockWebServer(useHttps = true)` (lesson 2026-08-29-12-001,
carried forward as inbox message -004 with a merge-into request). Covered at the `RedirectPolicy`
validation seam instead, and documented as a gap in `HttpHandlerRedirectTest`'s class Javadoc.

**The finalize review loop ran to its full 3/3 loop-back ceiling and terminated by operator choice,
not by convergence.** Every fix push drew a fresh round of bot findings. Eight review-driven defects
were found and fixed after the implementation was complete: unsanitized remote-controlled `Location`
in an exception message reaching log sinks; a mutable egress allowlist escaping through
`getAllowedHosts()`; undiscarded intermediate redirect response bodies leaking connections under
streaming handlers; incomplete RFC 9110 representation-metadata stripping on a body-dropping rewrite;
a test fixture duplicating the production followable-status set; an overclaiming test comment; a stale
`LogMessages.adoc` WARN range; and an unbounded target URI in the remaining refusal messages.

**A suppression was landed and then withdrawn on operator challenge.** Sonar `java:S4449` on
`sanitizeForMessage` was first answered with `// NOSONAR java:S4449` — placed above the declaration,
where Sonar honours nothing, and carrying a rationale that was itself wrong. The finding was a TRUE
positive: `resolveTarget` declared `location` `@Nullable` and passed it to a non-null parameter. The
shipped fix corrects the declaration and removes the suppression entirely. Recorded as inbox
message -003.

**Two coverage boundaries this run did not close.** `pre-submission-self-review`, `finalize-step-simplify`
and `finalize-step-security-audit` last ran at `35e5766`; the three later commits (`9f5cc32`, `bbace1d`,
`92b7e20`, `544a448`) were not re-swept by them, and their records keep the older SHA rather than a
re-stamped one. Separately, `92b7e20` and `544a448` carry no bot review at all — CodeRabbit reviewed
through `bbace1d`, and the operator chose to close out rather than run a further round.

**Three finalize steps carry `lane: off` in the manifest step-params yet remain in the composed step
list** — `adr-propose`, `finalize-step-preference-emitter`, `finalize-step-print-phase-breakdown`. All
three were recorded `skipped` on that marker. `adr-propose`'s decision-shape Signal Gate WOULD have
fired (compatibility present, decision log non-empty), and this plan did settle ADR-worthy decisions
(the loop placement, same-origin-by-default egress policy, the CredentialForwarding strategy), so an
ADR pass is worth running separately. The lane-off-but-still-listed shape may itself be a compose bug.

**Two measurement gaps.** The scope-creep guard returned `could_not_look` (`no_baseline_sha` — the plan
carries no `plan_creation_sha`), so scope creep is unmeasured rather than clean. `verify:module-tests`
recorded `skipped` because that canonical does not resolve in this project; `verify` covers it.

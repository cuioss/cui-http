# PLAN-06: Forwarded-Header Trust Model — Family Reconciliation and Fail-Direction

epic: quality-report-remediation
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-06-forwarded-trust-model.md` and is queued in the epic `status.json`
> `plans[]` field. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no brief,
> so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Fix the trust model of `ForwardedHeaderResolver` so that its resolution decisions match the
contract its Javadoc and `doc/forwarded-header-resolution.adoc` describe. Today the two de-facto
header families (`X-Forwarded-*` and the NiFi `X-Proxy*` family) are never compared against each
other, so whichever family the operator's ingress proxy does not write is fully attacker-
controlled; and any client can blank every resolved field — scheme, host, port and client IP —
by sending a single malformed `Forwarded` header, after which all three documented consumers
fall back to `http`. This plan carries two of the epic's five High findings. Every change is
driven by a regression test written and seen to fail first.

## Deliverables

1. **Cross-family reconciliation (F-D-1).** Reconcile the two de-facto families against each
   other before either is reconciled against RFC 7239 `Forwarded`, so a value present in only
   one family is no longer accepted on the strength of a fixed precedence order. Decide and
   record whether a disagreement between the two de-facto families resolves to unresolvable (the
   same rule the RFC-vs-de-facto comparison already uses) or to a configured precedence, and
   document the ingress requirement that the proxy must strip the families it does not write.
2. **Fail-direction for an unresolvable `Forwarded` header (F-D-2).** Stop a malformed
   `Forwarded` header from suppressing fields it never mentioned. `ForwardedResult.contributes()`
   currently reports the RFC source as present for *every* field whenever the header parsed
   unresolvably, which makes `reconcileSources` treat a legitimate proxy-set de-facto value as
   disagreeing with an empty RFC value and drop it. A parse failure must contribute only to the
   fields the header actually carried, or the whole RFC source must be discarded — not silently
   win a disagreement against a trusted value.
3. **Scheme fallback direction (F-D-2, consumer half).** The three `scheme().orElse("http")`
   call sites teach integrators to fail *open* on an attacker-triggerable empty value. Change
   the resolver's own sample in `ResolvedForwarding` and the `package-info` sample so that an
   unresolved scheme is an error the caller must handle, not a silent downgrade to cleartext.
4. **`trustedProxies` honesty (F-D-3).** The option name promises a check the class structurally
   cannot perform, because `resolve(Function<String,List<String>>)` is the only entry point and
   takes no peer address. Either add an optional peer-address input that genuinely gates whether
   any forwarded header is believed, or rename/redocument the option so it states what it does
   (select which hops in the chain are skipped). This is a genuine fork — surface it to the
   operator rather than deciding it silently.
5. **Host and port reconciled separately (F-D-4).** `reconcileSources("host", …)` compares a
   `HostPort` record by value equality, so a proxy that legitimately writes the port through
   `X-Forwarded-Port` and the host through `Forwarded host="h:p"` produces two unequal
   `HostPort` values and loses the host. Reconcile the host and the port as independent fields.
6. **Regression tests, written first.** One failing test per deliverable above, added before the
   production edit, asserting the exact resolved value (never `assertNotNull`). The suite must
   include the adversarial case for deliverable 2: a legitimate `X-Forwarded-Proto: https` plus a
   client-supplied garbage `Forwarded` header must still resolve `https`.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass; the repository's production and test code at that sha is byte-identical to
the reviewed commit `0bad295`, so the report's citations resolve exactly.

- OBSERVED: `firstPresent()` picks the first present name from a fixed two-element de-facto array
  with no cross-family comparison, and only the winning de-facto value is later reconciled
  against RFC 7239 — read at `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` § `firstPresent` (lines 191, 211, 333, 385).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: firstPresent calls at 191/211/333/385 exact
- OBSERVED: a sanitization or grammar failure in `parseForwarded()` produces `UNRESOLVABLE_RESULT`;
  `ForwardedResult.contributes()` then reports the RFC source as present for every field
  regardless of which directives the header carried, and `reconcileSources` drops scheme, host,
  port and client IP alike — read at the same file § `parseForwarded` (lines 505-516), §
  `ForwardedResult.contributes` (lines 693-695) and § `reconcileSources` (lines 558-572).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: UNRESOLVABLE_RESULT at 508/515; contributes 693-695; reconcileSources 558-573 (cited 558-572, off by the closing brace)
- OBSERVED: all three `scheme().orElse("http")` call sites exist verbatim — read at
  `doc/forwarded-header-resolution.adoc`:449, `cui-http-core/src/main/java/de/cuioss/http/forwarded/package-info.java`:84 and
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ResolvedForwarding.java`:44.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: all three orElse(http) sites exact at doc:449, package-info:84, ResolvedForwarding:44
- OBSERVED: `resolve(Function<String,List<String>>)` at line 171 is the only entry point and no
  peer or `InetAddress` parameter exists anywhere in the class; `trustedProxies` is consulted
  only inside `walkChain` / `isTrustedProxy` to decide which hops to skip. The class Javadoc
  (lines 51-63) states this as an intentional, mandatory deployment precondition — read at
  `ForwardedHeaderResolver.java` § `resolve` and § `isTrustedProxy` (line 431).
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: resolve at 171 and Javadoc 51-63 exact; the isTrustedProxy CALL is at 463, line 431 is a distinct trustedProxies-emptiness guard
- OBSERVED: `reconcileSources("host", …)` compares `Optional<HostPort>` by record equality at
  line 567, and `HostPort` bundles host and port into one record at line 701; `resolvePort`
  (lines 329-344) never cross-checks its result against the RFC host's embedded port — read at
  `ForwardedHeaderResolver.java`.
  - verdict: corroborated | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: reconcileSources host call 213, equals compare 567, HostPort 701, resolvePort 329-344
- OBSERVED (derived count): the verification pass corroborated 15 of 15 findings in
  `02-forwarded-header-resolution.adoc` with 0 contradicted and 0 unverifiable; this plan owns
  4 of them (F-D-1, F-D-2, F-D-3, F-D-4) and PLAN-07 owns the rest.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: meta-claim about a prior pass 15-of-15 count; re-deriving it is outside this pass
- HYPOTHESIS: deliverable 1's reconciliation change alters the value a deployed application
  resolves and is therefore a behavioural break for integrators — confirm/refute at
  `cui-http-core/src/test/java/de/cuioss/http/forwarded/ForwardedTrustBoundaryTest.java` §
  the de-facto-family test methods (verify-at-outline). If existing tests pin the current
  precedence as intended behaviour, the change needs an ADR and a migration note rather than a
  plain fix.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: forward HYPOTHESIS on integrator-visible precedence break
- Verify-first clause: before scoping deliverable 4, settle whether an optional peer-address
  parameter can be threaded through `resolve` without breaking the published API. Read
  `ForwardedHeaderResolver.java` § `resolve` and every call site the module exports. A refutation
  (the API cannot take it compatibly) loops back and re-scopes deliverable 4 to the
  rename-and-redocument branch only.
  - verdict: unverifiable | checked_at: 73afd22 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: procedural verify-first directive on threading a peer address

## Expected Surface

⛔ **CORRECTED 2026-09-08 at landing (PR #214, `6e1a19c`) from 8 declared entries to the 12 files
the merge touched.** Shipped spec — the correction serves the audit record and the epic's
under-declaration measurement, not a future gate call.

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedResolverConfig.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ResolvedForwarding.java` — declared
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/package-info.java` — declared
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/forwarded/ForwardedHeaderResolverTest.java` — declared
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/forwarded/ForwardedResolverConfigTest.java` — declared
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/forwarded/ForwardedTrustBoundaryTest.java` — declared
- OBSERVED: `doc/adr/` — declared; `0021-Unresolvable_Forwarded_header_suppresses_only_the_fields_it_carried.adoc` added and `0002-Fail_closed_when_X-Forwarded-For_and_RFC_7239_Forwarded_disagree.adoc` flipped to `Status: Superseded`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/RfcForwardedParser.java` — UNDECLARED; inside PLAN-07's surface
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedLogMessages.java` — UNDECLARED; in no spec's surface
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/forwarded/RfcForwardedParserTest.java` — UNDECLARED; inside PLAN-07's surface

⚠ **This plan reached into PLAN-07's declared surface.** PLAN-07 was not running and the pair was
already strictly sequenced, so nothing conflicted — but the gate's prediction was wrong.
⛔ **PLAN-07 must re-read `RfcForwardedParser.java` and `RfcForwardedParserTest.java` at outline**
rather than working from its staged premises.

⚠ `references.json` `affected_files` listed **11** of the 12 realized files — the same
realized-side gap PLAN-02's landing recorded.

## Dependencies and Sequencing

- Depends on: none. This is the head of WS-03 and may be emitted immediately.
- Overlaps with: PLAN-07 (same resolver class and the same test package) — strictly sequential,
  PLAN-06 first. No other plan in the epic touches `de.cuioss.http.forwarded`.
- Adjacent to: `de.cuioss.http.security.validation`'s `HTTPHeaderValidationPipeline`, which the
  resolver calls for context-path validation. This plan does not change that pipeline; WS-01
  owns it. If a deliverable here appears to need a pipeline change, file an inbox message
  instead of making it.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-06-forwarded-trust-model.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass. A production edit that lands without a preceding failing test is a defect in this
  plan's execution, not a shortcut.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction.
- Assert exact resolved values. `assertNotNull` is not an acceptable assertion for any test this
  plan adds — the report's F-D-19 finding is precisely that the existing forwarded suite does
  this, and PLAN-07 is cleaning it up.
- Deliverable 4 contains a genuine fork (add a peer-address input vs. rename the option). Surface
  it to the operator; do not decide it silently.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

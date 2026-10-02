# PLAN-05: Forwarded Trust Boundary

epic: quality-report-remediation
workstream: WS-02

## Objective

Close — or explicitly narrow the library's stated guarantee for — the three HIGH-severity trust-model
gaps in the forwarded-header resolver's integration surface. The chain-walk algorithm itself is
correct and fails closed; these findings are about how headers reach it. The recommended
`request::getHeader` accessor sees only the FIRST instance of a repeated header, so behind a proxy
that appends `X-Forwarded-For` as a separate header line the resolver walks a 100%
attacker-controlled chain. `X-Forwarded-For` unconditionally shadows RFC 7239 `Forwarded`, so a
deployment whose proxies manage only `Forwarded` honours an attacker-supplied XFF verbatim. And for
scheme, host and port the resolver takes the LEFTMOST token — the attacker-controlled end under
append-style proxies such as Apache `mod_proxy` — enabling host poisoning and scheme downgrade.

⛔ In all three cases the documented "must sit behind a trusted proxy" precondition is satisfied by
the attack: the request really did traverse the trusted proxy. The precondition is necessary but not
sufficient, and that is the finding.

## Deliverables

1. **Resolve the multi-valued-header gap** — either change the accessor contract to expose all
   values per header name, or document the multi-header requirement prominently in the resolver's
   own Javadoc AND in `doc/forwarded-header-resolution.adoc`, stating that callers must join every
   header instance. (FW-1)
2. **Resolve the header-family precedence gap** — either make the honoured header family
   configurable rather than XFF-always-wins, or state the real deployment requirement in both the
   Javadoc and the adoc: trusted proxies must strip-or-append EVERY header family the resolver
   honours, not just the one they emit. (FW-2)
3. **Resolve the leftmost-token selection for scheme, host and port** — either select the rightmost
   (proxy-appended) token under append semantics, or document that these fields require
   overwrite-style proxies. The RFC 7239 path has the same bias (first `proto`/`host` wins) and must
   be resolved consistently with the XFF path. (FW-3)
4. **Fix the context-path first-token drift** — the adoc claims comma-separated list headers resolve
   to their first token, but `firstToken` is never applied to the context path, so an append-style
   `X-Forwarded-Prefix: /app, /other` normalizes to the single bogus prefix `"/app, /other"` and is
   honoured verbatim under `trustAll`. Either apply first-token extraction to the prefix or correct
   the doc — and note this decision must agree with deliverable 3's leftmost-versus-rightmost
   outcome. (FW-6)
5. **Document the trusted-range composition requirement** — a broad range makes every hop trusted so
   the walk exhausts the chain and returns empty (fail-closed but counter-intuitive), and
   conversely any non-proxy machine inside a configured trusted range can spoof the client IP by
   prepending entries. Nothing currently warns that trusted ranges must contain ONLY proxies. (FW-19)
6. **Regression tests** covering each resolved gap, including the append-style proxy shapes the
   report names.

Six deliverables — at the split guard. Proceeding unsplit: deliverables 1–4 are one coherent trust
decision (which end of which header family is authoritative) that must be resolved consistently or
not at all — resolving them in separate plans risks two plans picking opposite conventions for the
XFF and RFC 7239 paths. Rationale recorded as an epic decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| FW-1 | HIGH | Multi-valued `X-Forwarded-For` is invisible to the accessor abstraction |
| FW-2 | HIGH | `X-Forwarded-For` unconditionally shadows RFC 7239 `Forwarded` |
| FW-3 | HIGH | Scheme/host/port use leftmost-token selection — the attacker-controlled end under append-style proxies |
| FW-6 | MEDIUM | Doc drift — the "first token" claim is false for the context path |
| FW-19 | INFO | Trusted ranges must contain only proxies; a broad range fails closed counter-intuitively |

## Claim Labels

- OBSERVED: these findings are stated at `source/forwarded-review.adoc` § `FW-1`, `FW-2`, `FW-3`,
  `FW-6`, and the FW-19 INFO bullet.
- OBSERVED: the report's own scope NOTE states that the XFF chain-walk algorithm is correct and
  fails closed, and that all three HIGH findings concern the integration surface rather than the
  algorithm — read at `source/forwarded-review.adoc` § the opening NOTE.
- OBSERVED: the epic-level index names FW-1/FW-2/FW-3 as Top Priority #3 and states the resolution
  may be either a code change or an amended Javadoc/adoc guarantee — but explicitly NOT a note in a
  review report. Read at `source/README.adoc` § Top Priorities.
- HYPOTHESIS: `ForwardedHeaderResolver` takes a `Function<String,String>` header lookup and its
  Javadoc recommends `request::getHeader` — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java`
  § the resolver constructor and class Javadoc (verify-at-outline)
- HYPOTHESIS: the resolver takes the XFF branch whenever XFF is present and only falls back to
  `forwarded.forValues()` otherwise — confirm/refute at the same file § the client-IP chain
  selection (verify-at-outline)
- HYPOTHESIS: `firstToken` is applied to scheme, host and port but never to the context path, and
  `RfcForwardedParser` keeps the first `proto`/`host` via a `x == null ? value : x` accumulator —
  confirm/refute at the same file § `firstToken`, `resolveContextPath`, and
  `.../RfcForwardedParser.java` § the directive accumulator (verify-at-outline)
- HYPOTHESIS: `ContextPaths.normalize` does not reject a comma or space in the prefix — an asserted
  absence, verified as a presence — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ContextPaths.java` § `normalize`
  (verify-at-outline)
- HYPOTHESIS: the derived claim that this plan carries three of the epic's eight HIGH findings is
  the orchestrator's own tally across the six reports — confirm/refute by counting HIGH headings
  across the source corpus (verify-at-outline)
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: ⛔ each of deliverables 1, 2, 3 and 5 offers TWO mutually exclusive
  resolutions — enforce in code, or narrow the stated guarantee. These are genuine security forks
  with materially different consequences for existing integrators: a code change may break
  deployments that currently work, while a narrowed guarantee leaves the attack open by design.
  Decide each at outline, record the choice, and if the choice is a narrowed guarantee, the narrowing
  MUST land in the resolver's own Javadoc AND `doc/forwarded-header-resolution.adoc` — not in a
  report, and not in a commit message.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` — the header lookup contract, client-IP chain selection, `firstToken`, scheme/host/port resolution, `resolveContextPath`, class Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/RfcForwardedParser.java` — the `proto`/`host` accumulator (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ContextPaths.java` — `normalize` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedResolverConfig.java` — if the header family becomes configurable (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/package-info.java` — trust-model documentation (verify-at-outline)
- HYPOTHESIS: `doc/forwarded-header-resolution.adoc` — the precondition statement, the first-token claim, the trusted-range guidance (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/forwarded/` — resolver tests (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none within WS-02 — this is the workstream head. It does NOT depend on WS-01, but see
  the adjacency note below.
- Overlaps with: PLAN-06 and PLAN-07 (all three edit `ForwardedHeaderResolver.java`). Strictly
  sequential within WS-02.
- Adjacent to: `de.cuioss.http.security` (WS-01). The resolver sanitizes through
  `createHeaderValuePipeline`, which PLAN-01 may change. This plan does not edit the security
  package; if PLAN-01 has already landed, re-read what HEADER_VALUE validation now does before
  scoping deliverable 5.
- Adjacent to: `doc/forwarded-header-resolution.adoc` is owned by WS-02 EXCLUSIVELY — WS-05 does not
  touch it. Editing it here is in scope and expected.
- Adjacent to: `cui-http-benchmarking/.../forwarded/benchmark/` (WS-06, BB-1). Do not edit the
  benchmark; if a resolver change alters what the benchmark measures, report it in the landing.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-05-forwarded-trust-boundary.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

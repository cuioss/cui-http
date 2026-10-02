# PLAN-06: Forwarded Parser Strictness and Validation

epic: quality-report-remediation
workstream: WS-02

## Objective

Make the two forwarded parsing paths equally strict and give the public `ResolvedForwarding` record
the invariants its Javadoc already asserts. The RFC 7239 `Forwarded` parser silently DROPS malformed
elements before they reach the chain walk, while the equivalent garbage in `X-Forwarded-For` ABORTS
resolution entirely — a parser-differential that contradicts the documented "an unverifiable hop
yields no client IP" guarantee. Separately, `ResolvedForwarding` is public and directly
constructible with no compact constructor, so CR/LF passes unescaped through both serializers, and
the record's documented invariants (scheme is http or https, port in 1..65535, contextPath has
exactly one leading slash) are enforced nowhere. A cluster of IP-literal and host-guard laxities
rounds out the plan.

## Deliverables

1. **Make the RFC 7239 path abort on malformed elements** rather than silently discarding an element
   with no `=` or an empty value, matching the XFF path's abort semantics and the documented
   guarantee. (FW-4)
2. **Add a validating compact constructor to `ResolvedForwarding`** that enforces the invariants its
   Javadoc asserts and rejects control characters, so a directly-constructed record cannot serialize
   CR/LF through `toXForwardedHeaders`/`toForwardedHeader`. (FW-5)
3. **Reject trailing garbage after a bracketed IPv6 literal** — `[::1]garbage` is currently accepted
   as `::1` because the remainder after `]` is never inspected, contradicting the package's
   otherwise strict-abort philosophy. (FW-7)
4. **Reject IPv4 octets with leading zeros** — `010.0.0.5` is parsed as decimal `10.0.0.5` (and so
   potentially matches a `10.0.0.0/8` trusted range) while `inet_aton`-based stacks read it as octal
   `8.0.0.5`. The regex can cheaply reject `0\d` octets and close the ambiguous-literal differential.
   (FW-8)
5. ⛔ **CLOSED EARLY by PLAN-05 (PR #155, `534d111`) — do NOT re-implement.** FW-9's host guard was
   tightened during PLAN-05's phase-6 security-audit sweep: `containsHostSeparator` now rejects `@`,
   `#` and `?` in addition to `/`, `\` and whitespace, with three regression tests. The trigger was
   `X-Forwarded-Host: real-host@attacker.example` composing to a userinfo-confusion absolute URL.
   **Verify at outline** that the unbracketed multi-colon half of FW-9 was also covered — PLAN-05's
   landing names only the delimiter half — and implement ONLY that remainder if it is still open.
6. **Make `parsePort` digit-only** — `Integer.parseInt("+443")` currently succeeds, so
   `X-Forwarded-Port: +443` resolves to 443. Harmless, but not RFC-conformant. (FW-10)
7. **Regression tests** for each, asserting the abort/reject outcome rather than merely the absence
   of a value.

Seven deliverables — over the split guard. Proceeding unsplit: deliverables 3–6 are four one-line
input-validation tightenings in two adjacent files (`IpAddresses`, `ForwardedHeaderResolver`) that
share a single test surface, and deliverables 1–2 are the strictness pair that gives the plan its
name. A split would produce a two-deliverable plan and a five-deliverable plan that both edit
`ForwardedHeaderResolver.java` and therefore cannot run concurrently — pure overhead. Rationale
recorded as an epic decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| FW-4 | MEDIUM | Malformed `Forwarded` elements are silently dropped, weakening the "unverifiable chain aborts" guarantee |
| FW-5 | MEDIUM | `ResolvedForwarding` has no canonical-constructor validation |
| FW-7 | LOW | Trailing garbage after a bracketed IPv6 literal is silently ignored |
| FW-8 | LOW | IPv4 octets with leading zeros are parsed as decimal |
| FW-9 | LOW | Host guard admits `@`, `?`, `#`, and unbracketed multi-colon values — ⛔ **delimiter half CLOSED EARLY by PLAN-05**; only the unbracketed multi-colon half may remain |
| FW-10 | LOW | `parsePort` accepts a signed literal |

## Claim Labels

- OBSERVED: these findings are stated at `source/forwarded-review.adoc` § `FW-4`, `FW-5`, `FW-7`,
  `FW-8`, `FW-9`, `FW-10`.
- OBSERVED: FW-5 carries a verification NOTE recording its precondition — the values the resolver
  itself produces are already sanitized, so the finding requires an integrator to construct the
  record DIRECTLY from unsanitized data, bypassing the resolver. The report calls this contrived but
  valid, because the record is public API with Javadoc-asserted invariants it does not enforce.
  Read at `source/forwarded-review.adoc` § `FW-5`.
- OBSERVED: FW-8's runtime behaviour was verified by the reviewer on the repository's JDK
  (OpenJDK 21) — `InetAddress.getByName("010.0.0.1")` returns `10.0.0.1`. Read at
  `source/forwarded-review.adoc` § `FW-8`. Re-verify on the current JDK before relying on it.
- OBSERVED: FW-9 is recorded as matching the adoc (no documentation drift) — the guard is simply
  weaker than the field's use in URL composition implies. Read at `source/forwarded-review.adoc` § `FW-9`.
- HYPOTHESIS: `RfcForwardedParser` returns early on `eq <= 0` and on an empty value, discarding the
  element — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/RfcForwardedParser.java`
  § the pair-parsing method (verify-at-outline)
- HYPOTHESIS: `walkChain` returns `Optional.empty()` on an unparseable hop — the contrasting XFF
  behaviour that makes FW-4 a differential — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` § `walkChain`
  (verify-at-outline)
- HYPOTHESIS: `ResolvedForwarding` declares no compact constructor and `quoteIfNeeded` escapes only
  `\` and `"` — an asserted absence plus a presence — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ResolvedForwarding.java`
  § the record header and `quoteIfNeeded` (verify-at-outline)
- HYPOTHESIS: the benchmark constructs `ResolvedForwarding` directly, which is the report's evidence
  that direct construction is a real usage pattern — confirm/refute at
  `cui-http-benchmarking/src/main/java/de/cuioss/http/forwarded/benchmark/ForwardedBenchmarkState.java`
  (verify-at-outline; read-only — WS-06 owns that file)
- HYPOTHESIS: `containsHostSeparator` rejects only `/`, `\`, and whitespace — confirm/refute at
  `ForwardedHeaderResolver.java` § `containsHostSeparator` (verify-at-outline)
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: deliverables 3, 4, 5 and 6 all make the resolver reject inputs it currently
  accepts. Each is a behaviour change that can break a working deployment. Check the existing test
  suite and the adoc's worked examples for reliance on the current laxity before scoping, and if
  reliance is found, escalate rather than tightening silently.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/RfcForwardedParser.java` — element parsing, the drop branches (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ResolvedForwarding.java` — the record header, `quoteIfNeeded`, `toXForwardedHeaders`, `toForwardedHeader` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/IpAddresses.java` — the IPv4 octet regex, the bracketed-IPv6 parse (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` — `containsHostSeparator`, `parsePort`, the bracketed-literal handling (verify-at-outline)
- HYPOTHESIS: `doc/forwarded-header-resolution.adoc` — the "unverifiable hop yields no client IP" claim, if deliverable 1 changes it (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/forwarded/` — parser, resolver, and IP-address tests (verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-05. Both edit `ForwardedHeaderResolver.java`; PLAN-05 heads WS-02.
- Overlaps with: PLAN-05, PLAN-07 — WS-02 is strictly sequential.
- Adjacent to: `cui-http-benchmarking/.../ForwardedBenchmarkState.java`, which constructs
  `ResolvedForwarding` directly. Deliverable 2 adds validation that this construction must satisfy.
  ⛔ Do NOT edit the benchmark — it is WS-06's surface. If the new compact constructor breaks the
  benchmark's construction, report it in the landing and escalate; a cross-module compile break is
  an epic-level sequencing fact, not a thing to fix across the workstream boundary.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-06-forwarded-parser-strictness.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

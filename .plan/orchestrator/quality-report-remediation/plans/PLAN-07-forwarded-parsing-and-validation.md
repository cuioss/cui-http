# PLAN-07: Forwarded-Header Parsing, Host Validation and Log Sanitisation

epic: quality-report-remediation
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-07-forwarded-parsing-and-validation.md` and is queued in the epic
> `status.json` `plans[]` field. The orchestrator EMITS the command below; it never launches the
> plan inline. This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and
> carries no brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and hand-off
> contract.

## Objective

Close the remaining correctness gaps in `de.cuioss.http.forwarded` once PLAN-06 has settled the
trust model above them: a six-character host deny-list that honours quotes, angle brackets,
semicolons, percent signs and homoglyph hosts; a context-path guard blind to `?`, `#`, `;`,
dot-segments and encoded slashes; an asymmetric port-suffix parser that rejects `[::1]:abc` but
accepts `1.2.3.4:abc`; IPv4-mapped IPv6 forms that slip past the leading-zero-octet rule; and a
log sanitiser that neutralises ISO controls but not U+2028/U+2029 or bidi overrides. It also
lifts the package's test suite off `assertNotNull` and onto exact assertions. Every change is
driven by a regression test written and seen to fail first.

> ⛔ **APPLICABILITY UPDATE 2026-09-15 at `d385aa3` (cleanup pass).** The original deliverable 4
> ("RFC 7239 duplicate directives are fatal") is retired below — it is already fixed. PLAN-06
> (#214) rewrote `RfcForwardedParser` to carry an explicit `Parsed(proto, host, forValues,
> malformed)` record whose class Javadoc states "A malformed pair stops the parse and is reported,
> not swallowed" (ADR-0021); `proto=https;broken` now reports `proto` with `malformed=true`. This
> is a positive account of what closed the defect, per the epic's applicability rule (an absent
> symbol alone never settles applicability; this is more than absence — the fix is present, named
> and ADR-backed). Deliverables are renumbered nowhere else in this spec; 4 is struck and the rest
> keep their original numbers so no cross-reference in Execution Constraints or elsewhere needs
> updating.

## Deliverables

1. **Host validation is an allow-list, not a six-character deny-list (F-D-5).** Replace
   `containsHostSeparator`'s deny-list (`/ \ @ # ? whitespace`) with a positive host grammar, so
   quotes, `<>`, `;`, `%`, `]` and non-ASCII homoglyph hosts are rejected rather than passed
   through into `HostPort.host`.
2. **Context-path guard covers the characters that change a path's meaning (F-D-6).** Extend
   `ContextPaths.normalize` beyond `isISOControl`, `//`-or-backslash and comma/whitespace to
   reject `?`, `#`, `;`, dot-segments and percent-encoded slashes.
3. **Symmetric port-suffix handling (F-D-10) and IPv4-mapped IPv6 rules (F-D-11).** Make the
   unbracketed branch of `IpAddresses.parseChainEntry` and
   `ForwardedHeaderResolver.parseHostPort` validate the port suffix the way the bracketed branch
   already does, and drop the value rather than silently discarding an invalid port. Apply the
   IPv4 literal rules (leading-zero octets, octet range) to the IPv4-mapped suffix of an IPv6
   literal, which today reaches `InetAddress.getByName` unchecked.
4. ~~**RFC 7239 duplicate directives are fatal (F-D-12).**~~ ✅ **ALREADY FIXED — retired at the
   2026-09-15 cleanup pass.** `Accumulator.apply` was reported as accumulating every `for`
   occurrence and silently overwriting `proto` / `host` on repeat. PLAN-06 (#214) rewrote
   `RfcForwardedParser` around a `Parsed(proto, host, forValues, malformed)` record; the class
   Javadoc now states "A malformed pair stops the parse and is reported, not swallowed"
   (ADR-0021), and `proto=https;broken` reports `proto` with `malformed=true`. Do not re-do this
   deliverable; verify it holds at outline and move on.
5. **Log sanitisation covers the separators that forge a log line (F-D-13), and the stale
   rationale comment goes (F-D-7, code half).** `sanitizeForLog` replaces only
   `Character.isISOControl` characters; U+2028, U+2029 and U+202E survive, and the default
   `HEADER_VALUE` character stage permits them. Extend the sanitiser, and delete the
   `resolveContextPath` comment claiming the header-value pipeline collapses `//host` to
   `/host` — `HTTPHeaderValidationPipeline.createStages` composes only a length and a character
   stage, with no `NormalizationStage` at all.
6. **Test suite hardening (F-D-9, F-D-19).** Add the missing `HTTP-120` control-character test
   for the resolver's WARN path on a raw `X-Forwarded-Prefix` / `X-ProxyContextPath` header;
   replace `IpAddressesTest.acceptsValidLiterals`' `assertNotNull` sweep with exact assertions;
   add `CidrRange` boundary cases (`/0`, `/-1`, `/33`, `/128`); and introduce `cui-test-generator`
   `TypedGenerator` usage, which the package uses nowhere today.

## Claim Labels

Every claim below was checked against the implementing source at HEAD `d242ba5` by a read-only
verification pass; the repository's production and test code at that sha is byte-identical to
the reviewed commit `0bad295`.

- OBSERVED: `containsHostSeparator` rejects exactly `/ \ @ # ?` and whitespace — a six-item
  deny-list — and everything else reaches `parseHostPort` unchanged — read at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` §
  `containsHostSeparator` (lines 306-314) and § `parseHostPort` (lines 259-295).
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-read at ca74911: containsHostSeparator (now lines 597-605) still rejects exactly / \\ @ # ? plus Character.isWhitespace - a six-item deny-list - and everything else still reaches parseHostPort. Substance intact through PLAN-06's rewrite; the method moved.
- OBSERVED: `ContextPaths.normalize` checks only `isISOControl`, protocol-relative-or-backslash,
  and comma/whitespace, so `/app/../admin`, `/app?x=1` and `/app#f` pass unaltered — read at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ContextPaths.java` § `normalize`
  (lines 44-57, 73-92).
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: ContextPaths.java and IpAddresses.java are BYTE-IDENTICAL across 73afd22..ca74911 - PLAN-06 (#214) touched neither. Every symbol and line this claim cites is unmoved.
- OBSERVED: the single-colon branch of `parseChainEntry` takes `substring(0, indexOf(':'))` and
  never validates the suffix, while the bracket branch calls `hasValidBracketTrailer` — read at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/IpAddresses.java` § `parseChainEntry`
  (lines 120-132).
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: ContextPaths.java and IpAddresses.java are BYTE-IDENTICAL across 73afd22..ca74911 - PLAN-06 (#214) touched neither. Every symbol and line this claim cites is unmoved.
- OBSERVED: `IPV6_LITERAL` is `[0-9A-Fa-f:.]+`, so an IPv4-mapped dotted-decimal suffix such as
  `::ffff:010.0.0.5` takes the `looksV6` branch, which delegates straight to
  `InetAddress.getByName` with no octet-range or leading-zero check — read at `IpAddresses.java`
  (line 43, lines 71-83).
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at ca74911: ContextPaths.java and IpAddresses.java are BYTE-IDENTICAL across 73afd22..ca74911 - PLAN-06 (#214) touched neither. Every symbol and line this claim cites is unmoved.
- OBSERVED: `Accumulator.apply` accumulates every `for` occurrence unconditionally and silently
  overwrites `proto` / `host` on repeat, with no branch raising malformed — read at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/RfcForwardedParser.java` §
  `Accumulator.apply` (lines 121-127); the contradicted Javadoc claim is at lines 45-52.
  - verdict: contradicted | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: REFUTED at ca74911 by PLAN-06 (#214). The claim says Accumulator.apply accumulates every 'for' unconditionally, silently overwrites proto/host on repeat, and has NO branch raising malformed. RfcForwardedParser now carries an explicit Parsed(proto, host, forValues, malformed) record and its class Javadoc states 'A malformed pair stops the parse and is reported, not swallowed' - proto=https;broken reports proto with malformed=true. This is exactly the behaviour ADR-0021 records. The defect this claim scopes no longer exists in the form described; re-scope the deliverable against the current parser at outline.
- OBSERVED: `sanitizeForLog` replaces a character only when `Character.isISOControl(c)` is true,
  leaving U+2028, U+2029 and U+202E untouched — read at `ForwardedHeaderResolver.java` §
  `sanitizeForLog` (lines 644-651).
  - verdict: corroborated | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-read at ca74911: sanitizeForLog still replaces a character only when Character.isISOControl(c) is true, so U+2028, U+2029 and U+202E remain untouched. ⛔ It moved from the cited 644-651 to 1193-1200 - PLAN-06's rewrite shifted it by ~550 lines. Substance corroborated, anchor stale.
- OBSERVED: `HTTPHeaderValidationPipeline.createStages` composes `LengthValidationStage` and
  `CharacterValidationStage` only — no `NormalizationStage` — which refutes the
  `resolveContextPath` comment at `ForwardedHeaderResolver.java`:392-394 — read at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/HTTPHeaderValidationPipeline.java`
  § `createStages` (lines 144-147).
  - verdict: contradicted | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Half-refuted at ca74911. The pipeline half HOLDS: HTTPHeaderValidationPipeline still composes LengthValidationStage and CharacterValidationStage only, with no NormalizationStage. But the refutation TARGET is gone: the resolveContextPath comment cited at ForwardedHeaderResolver.java:392-394 is not there - PLAN-06 moved resolveContextPath to line 707 and no normalization comment survives at either location. Re-scoped: re-locate the comment at outline before asserting the refutation, or drop that half.
- OBSERVED: no test drives the resolver's `HTTP-120` WARN path with a raw control character;
  `ResolvedForwardingTest.rejectsControlCharacters` (lines 79-86) tests the compact-constructor
  invariant on an already-built record instead — read at
  `cui-http-core/src/test/java/de/cuioss/http/forwarded/ResolvedForwardingTest.java`.
  - verdict: contradicted | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Citation stale at ca74911: PLAN-06 (#214) rewrote ForwardedHeaderResolverTest, ForwardedResolverConfigTest, ForwardedTrustBoundaryTest and RfcForwardedParserTest, and ADDED RfcForwardedParserTest content this plan declares. Every line number this claim cites in the forwarded test package predates that rewrite. The underlying observations about test-quality gaps may well survive; NONE of their anchors does. Re-scoped: re-derive every cited line at outline against the landed tests.
- OBSERVED: `IpAddressesTest.acceptsValidLiterals` is an `assertNotNull` sweep over 7 literals
  (lines 80-81); the nested `Cidr` class has exactly 3 tests (lines 105-131); the whole forwarded
  test package uses no `TypedGenerator` — read at
  `cui-http-core/src/test/java/de/cuioss/http/forwarded/IpAddressesTest.java` and
  `.../ForwardedResolverConfigTest.java` (lines 104-106).
  - verdict: contradicted | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Citation stale at ca74911: PLAN-06 (#214) rewrote ForwardedHeaderResolverTest, ForwardedResolverConfigTest, ForwardedTrustBoundaryTest and RfcForwardedParserTest, and ADDED RfcForwardedParserTest content this plan declares. Every line number this claim cites in the forwarded test package predates that rewrite. The underlying observations about test-quality gaps may well survive; NONE of their anchors does. Re-scoped: re-derive every cited line at outline against the landed tests.
- OBSERVED (citation corrections): three of the report's line numbers are stale against HEAD and
  the corrected values are used above — F-D-3's gate is at line 431 (not 435-437), F-D-8's
  `identifier(126)` field is at lines 78-82 (not 74-78), and F-D-19's malformed-spec citation is
  at lines 104-106 (not 90-96).
  - verdict: contradicted | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: yes | evidence: Citation stale at ca74911: PLAN-06 (#214) rewrote ForwardedHeaderResolverTest, ForwardedResolverConfigTest, ForwardedTrustBoundaryTest and RfcForwardedParserTest, and ADDED RfcForwardedParserTest content this plan declares. Every line number this claim cites in the forwarded test package predates that rewrite. The underlying observations about test-quality gaps may well survive; NONE of their anchors does. Re-scoped: re-derive every cited line at outline against the landed tests.
- Verify-first clause: deliverable 1 replaces a deny-list with a host grammar, which can reject
  hostnames a deployed integrator currently resolves successfully. Before scoping, read
  `cui-http-core/src/test/java/de/cuioss/http/forwarded/ForwardedHeaderResolverTest.java` § the
  host-parsing test methods and enumerate which currently-accepted shapes the grammar would
  newly reject. A refutation (the grammar breaks a legitimate deployed shape) loops back and
  re-scopes deliverable 1 to an extended deny-list.
  - verdict: unverifiable | checked_at: ca74911 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Still unverifiable at ca74911, and its population moved: PLAN-06 landed the trust-model rewrite this clause anticipated.

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` — `containsHostSeparator`, `parseHostPort`, `resolveContextPath`, `sanitizeForLog`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ContextPaths.java` — `normalize`
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/forwarded/IpAddresses.java` — `parseChainEntry`, `IPV6_LITERAL`, `parse`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/forwarded/` — the whole test package
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/CidrRange.java` — only if the boundary tests in deliverable 6 reveal a defect (verify-at-outline; the report verified this class's arithmetic as correct)

⛔ This plan does **not** edit any file under `doc/`. `F-D-7`'s prose half in
`doc/forwarded-header-resolution.adoc`:366-369, `F-D-8` (the `HTTP-120..125` catalogue range) and
`F-D-20` (host-rejection description, ADR-0002's omission of port, all three ADRs still
`Proposed`) are owned by WS-06 PLAN-13. This plan owns only their code and test halves.


⛔⛔ **RE-GROUNDED 2026-09-09 at `ca74911` — PLAN-06 (#214) landed underneath this spec and five of
its eleven claims are now contradicted.** Read this before outline; do not trust the staged text.

**What PLAN-06 changed on this plan's declared surface** (12 files, `6e1a19c`):

- `ForwardedHeaderResolver.java` — restructured. `sanitizeForLog` moved from ~644 to **1193**;
  `resolveContextPath` from ~392 to **707**. Every line anchor in this spec that cites this file is
  stale by hundreds of lines.
- `RfcForwardedParser.java` — ⛔ **rewritten, and it refutes claim 4 outright.** The parser now
  carries an explicit `Parsed(proto, host, forValues, malformed)` record and stops on a malformed
  pair rather than swallowing it (ADR-0021). The "no branch raising malformed" defect is gone.
  ⛔ **PLAN-06 edited this file WITHOUT declaring it** — it is inside this plan's declared surface.
- `RfcForwardedParserTest.java` and the three other forwarded tests — rewritten. Every cited line
  number in the test-quality claims (7, 8, 9) predates that rewrite.

**What did NOT change**: `ContextPaths.java` and `IpAddresses.java` are byte-identical, so claims
1, 2 and 3 carry forward unmoved. `containsHostSeparator` survives PLAN-06's rewrite with its
six-item deny-list intact (claim 0), and `sanitizeForLog` still uses `Character.isISOControl` alone
(claim 5) — only its address moved.

**New context this plan must absorb**, from `landings/PLAN-06.md`:

- `ForwardedResolverConfig` gained a `deFactoPrecedence()` knob; scheme, host and port de-facto
  families now resolve independently and reconcile before the RFC 7239 comparison.
- A second `resolve(Function, InetAddress)` overload gates belief on the socket peer.
- Host and port reconcile as **independent fields** — a host disagreement drops only the host.
- ADR-0002 is `Superseded` by **ADR-0021**. ADR high-water is **0022**.

## Dependencies and Sequencing

- Depends on: PLAN-06. That plan changes which header family wins and what an unresolvable state
  produces, and deliverable 3's host/port handling operates on the result of that decision.
- Overlaps with: PLAN-06 (same resolver class and test package) — strictly sequential.
- Adjacent to: `HTTPHeaderValidationPipeline`, which this plan READS to justify deleting a stale
  comment but never edits. WS-01 owns that file.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-07-forwarded-parsing-and-validation.md"
```

## Execution Constraints

- **TDD is mandatory for every code change in this plan.** For each deliverable: write the
  regression test, run it, see it fail for the stated reason, then make the production edit, then
  see it pass.
- ⛔ **ADR numbers are allocated against `origin/main`, never against the local worktree
  (carried from the PLAN-14 landing).** `adr-propose` is a standard finalize step that runs on
  EVERY plan, so any plan can write to `doc/adr/` whether or not its spec declares that surface —
  which is exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016 while running concurrently in
  separate worktrees, each scanning a tree in which 0016 was free. Before writing an ADR: fetch
  `origin/main`, take the highest number present THERE, and re-check immediately before commit.
  Then **name the ADR (number and title) in this plan's inbox message**, so the orchestrator can
  see the allocation even when the surface was never declared. The disjointness gate cannot catch
  this class — it reads declarations, and this write is undeclared by construction.
- Deliverable 6 is itself a test-quality deliverable; its new assertions must be exact. This plan
  must not add a single `assertNotNull`-only test method.

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and
edits NO file under `.plan/local/orchestrator/` other than its own `inbox/{sender}-{seq}`
message — the orchestrator owns every other ledger write — and reports its outcome through its
PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write
mechanism are stated in `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger
Write-Boundary.

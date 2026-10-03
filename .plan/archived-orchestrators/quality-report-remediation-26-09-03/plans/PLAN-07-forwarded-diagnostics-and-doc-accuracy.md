# PLAN-07: Forwarded Diagnostics and Documentation Accuracy

epic: quality-report-remediation
workstream: WS-02

## Objective

Make the forwarded package honest to the operator debugging it. Sanitization-rejection warnings are
logged with a fixed header-family name even when the value came from a different header, so an
operator chasing an HTTP-122 rejection is pointed at a header that may not be present in the
request. A port with no host is silently dropped from serialization. An IPv4-mapped CIDR spec —
valid IPv6 notation — is rejected at config time with a message about a 32-bit prefix range. Zone
IDs and blank trusted-proxy entries fail closed with no operator feedback. And four documentation
statements describe behaviour the code does not have. This plan closes the residual FW findings that
PLAN-05 and PLAN-06 do not.

## Deliverables

1. **Fix the misattributed sanitization warnings** — `sanitize(X_FORWARDED_PROTO, raw)` and its
   siblings are called with the fixed family name even when the value came from `X-ProxyScheme`,
   `X-ProxyHost`, `X-ProxyPort`, `X-ProxyContextPath`, or a `Forwarded` directive. Pass the actual
   source header name so the log names the header the operator must look at. (FW-11)

   ⛔ **WIDENED 2026-08-26 by PLAN-05's landing — this now also covers `HTTP-124`.** PLAN-05
   accepted-not-fixed its own instance of this defect (`e0b500`): the `HTTP-122` **and the new
   `HTTP-124`** log lines hardcode a canonical header-name constant (`X_FORWARDED_PROTO` /
   `X_FORWARDED_HOST`) even when the value came from an RFC 7239 `Forwarded` directive or an
   `X-Proxy*` fallback. PLAN-05 judged the correct fix — threading the matched header name through
   `firstPresent` / `schemeOf` / `hostPortOf` — wider than its own lean posture, and recommended a
   WS-02 follow-up. ⛔ **No new plan is needed: that follow-up is THIS deliverable.** It is the same
   defect class FW-11 already owns; only its blast radius grew, because PLAN-05 added `HTTP-124` to
   it. No security control depends on the logged name — this is audit-trail accuracy under incident
   investigation. Re-read `ForwardedLogMessages` and the `sanitize`/`firstPresent` call sites at
   `534d111`, not at `7ae6499`.
2. **Stop silently losing a host-less port** in `toForwardedHeader()`, or document the asymmetry as
   the contextPath asymmetry already is. (FW-12)
3. **Improve the IPv4-mapped CIDR config-time diagnostic** — `::ffff:10.0.0.0/104` fails because
   Java collapses the base to a 4-byte `Inet4Address`, making the max prefix 32, so the operator is
   told their IPv6 range has a prefix "out of range [0..32]". Fail-fast is correct; the message is
   not. Document that v4-mapped ranges must be written in dotted-quad form, or detect and say so. (FW-13)
4. **Give the silent fail-closed paths operator feedback** — an IPv6 zone ID (`fe80::1%eth0`) is
   unparseable and therefore aborts the whole client-IP resolution with only a generic HTTP-123
   warn, and a blank entry in `trustedProxies` is skipped silently, yielding an empty (fail-closed)
   trust set with no feedback at all. Document the zone-ID behaviour and warn on blank trust entries.
   (FW-14, FW-20)
5. **Correct the four documentation and Javadoc inaccuracies** — the adoc's "each entry is
   sanitized, then parsed" (the WHOLE header value is sanitized once, then entries are split and
   parsed without per-entry sanitization); `package-info.java`'s "untrusted values are ignored, only
   logged" (untrusted scheme/host/port are ignored WITHOUT logging, because the `!trustAll`
   early-returns precede sanitization); and the "resolve never throws" simplification in both the
   adoc and the resolver Javadoc (it throws NPE for a null `headerLookup`, and any RuntimeException
   from the caller-supplied lookup or a non-`UrlSecurityException` from the pipeline propagates).
   (FW-15, FW-16, FW-17)
6. **Remove the needless per-call allocation** in `ForwardedResolverConfig.allowedContextPaths()`
   and `trustedProxies()`, which re-copy into a new `LinkedHashSet` on every call although the
   fields are already unmodifiable — on a potentially hot path. (FW-18)

Six deliverables — at the split guard. Proceeding unsplit: this is a homogeneous
diagnostics-and-accuracy sweep over one package with no behavioural risk beyond deliverable 6's
allocation removal, and every deliverable shares the same two files. Rationale recorded as an epic
decision.

## Findings Covered

| ID | Severity | Statement |
|----|----------|-----------|
| FW-11 | LOW | Sanitization-rejection warnings misattribute the header name |
| FW-12 | LOW | `toForwardedHeader()` silently loses a port that has no host |
| FW-13 | LOW | IPv4-mapped CIDR specs are rejected at config time with a misleading message |
| FW-14 | INFO | IPv6 zone IDs are unparseable and abort client-IP resolution — fail-closed but undocumented |
| FW-15 | INFO | Doc "each entry is sanitized, then parsed" is inaccurate |
| FW-16 | INFO | `package-info` says untrusted values are "only logged"; scheme/host/port are ignored without logging |
| FW-17 | INFO | "`resolve` never throws" is a simplification |
| FW-18 | INFO | `ForwardedResolverConfig` accessors re-copy unmodifiable sets on every call |
| FW-20 | INFO | Blank entries in `trustedProxies` are skipped silently |
| DOC-4 | MEDIUM | **Inherited from PLAN-14 (WS-05) by ownership decision.** Two documented forwarded-sanitization config knobs are inert: `normalizeUnicode`, the header allow/block lists, and `allowDoubleEncoding` have no effect on forwarded-header sanitization, because the resolver sanitizes through the HEADER_VALUE pipeline which has no `DecodingStage` and gets `AllowBlockListStage` only for HEADER_NAME. List only the knobs that actually apply. ⛔ Re-verify this premise against the header pipeline's stage set at outline — PLAN-01 may have added a `DecodingStage`, which REFUTES the finding. |
| FW-21 | INFO | **No action.** The forwarded benchmarks were reviewed and found free of correctness defects. Named here so its absence is not read as an oversight; the benchmark's separate logging problem is BB-1, owned by PLAN-16. |

## Claim Labels

- OBSERVED: these findings are stated at `source/forwarded-review.adoc` § `FW-11`, `FW-12`, `FW-13`,
  and the FW-14..FW-21 INFO bullets.
- OBSERVED: FW-13's runtime behaviour was verified by the reviewer — Java collapses the
  `::ffff:10.0.0.0` base to a 4-byte `Inet4Address`, making `maxBits = 32`. Read at
  `source/forwarded-review.adoc` § `FW-13`. Re-verify on the current JDK.
- OBSERVED: FW-16 records that the adoc is MORE accurate than the `package-info` on this point, so
  the fix direction is to correct the Javadoc toward the adoc, not the reverse. Read at
  `source/forwarded-review.adoc` § the FW-16 bullet.
- OBSERVED: FW-21 explicitly states the benchmarks carry no correctness defects and cross-references
  BB-1 for the separate logging problem. This is the report's own cross-reference. Read at
  `source/forwarded-review.adoc` § the FW-21 bullet.
- HYPOTHESIS: the `sanitize` call sites pass a fixed family constant rather than the resolved source
  header name — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java`
  § the scheme, host, port and contextPath resolution methods (verify-at-outline)
- HYPOTHESIS: the `!config.trustAll()` early-returns for scheme/host/port precede any sanitization
  or logging call — the mechanism behind FW-16 — confirm/refute at the same file § those three
  resolution methods (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Five sanitize call sites still pass a fixed X_FORWARDED_* family constant, despite PLAN-05 and PLAN-06 rewriting much of the resolver
- HYPOTHESIS: `ForwardedResolverConfig.allowedContextPaths()` and `trustedProxies()` construct a new
  `LinkedHashSet` per call over already-unmodifiable fields — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedResolverConfig.java`
  § those two accessors (verify-at-outline)
- HYPOTHESIS: `IpAddresses`' literal regex excludes `%`, making a zone ID unparseable — confirm/refute
  at `cui-http-core/src/main/java/de/cuioss/http/forwarded/IpAddresses.java` § the address regex
  (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: ForwardedResolverConfig still constructs new LinkedHashSet instances per accessor call (7 occurrences)
- HYPOTHESIS: **(DOC-4, inherited)** `normalizeUnicode`, the header allow/block lists and
  `allowDoubleEncoding` are inert for forwarded-header sanitization, because the resolver sanitizes
  through the HEADER_VALUE pipeline which carries no `DecodingStage` and gets `AllowBlockListStage`
  only for HEADER_NAME — confirm/refute at
  `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/HTTPHeaderValidationPipeline.java`
  § `createStages` (verify-at-outline)
- HYPOTHESIS: the derived claim that these ten findings are exactly the FW residue not owned by
  PLAN-05 or PLAN-06 is the orchestrator's own partition of the FW range, not a report statement —
  confirm/refute by checking FW-1..FW-21 against the three WS-02 specs' Findings Covered tables
  (verify-at-outline)
  - verdict: corroborated | checked_at: f1ba539 | by: quality-report-remediation/cleanup | rescoped: n/a | evidence: Re-checked at f1ba539 after PLAN-06: HEADER_VALUE pipeline still carries no DecodingStage and AllowBlockListStage stays HEADER_NAME-only, so DOC-4's inert-knobs premise still holds
- Verify-first clause: all line numbers in the source reports are stated as of commit `7ae6499`.
  HEAD has advanced. Re-locate every symbol by NAME, never by line number, and if a named symbol is
  absent the premise is refuted — loop back and re-scope rather than proceeding.
- Verify-first clause: ⛔ deliverable 5 documents behaviour that PLAN-05 and PLAN-06 may have
  changed. In particular FW-15's "sanitized once, then split" description and FW-17's "never throws"
  simplification both depend on parsing behaviour PLAN-06 tightens. Read what those two plans landed
  before scoping.

## Expected Surface

- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java` — the `sanitize` call sites, the `trustAll` early-returns, resolver Javadoc (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ResolvedForwarding.java` — `toForwardedHeader` (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/CidrRange.java` — the prefix-range validation message (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedResolverConfig.java` — the two accessors, blank-entry handling (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/IpAddresses.java` — zone-ID documentation (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/package-info.java` — the untrusted-values claim (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedLogMessages.java` — if a new WARN is added for blank trust entries (verify-at-outline)
- HYPOTHESIS: `doc/forwarded-header-resolution.adoc` — the sanitization description, the never-throws claim (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/test/java/de/cuioss/http/forwarded/` — tests (verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-05 and PLAN-06 — both for the shared `ForwardedHeaderResolver.java` surface and
  because deliverable 5 documents what they landed.
- Overlaps with: PLAN-05, PLAN-06 — WS-02 is strictly sequential.
- Adjacent to: `doc/LogMessages.adoc`, which the report verifies matches `ForwardedLogMessages`
  EXACTLY — every ID, template string, and parameter description. ⛔ If deliverable 4 adds a new
  WARN record, that doc must be updated in the SAME change or its verified exactness breaks. This is
  the one place WS-02 legitimately reaches into a WS-05-scoped file; note it in the landing so
  PLAN-14/PLAN-15 do not re-derive it.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-07-forwarded-diagnostics-and-doc-accuracy.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

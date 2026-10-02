envelope_version=1
sender_type=plan
sender_id=forwarded-parser-strictness
epic=quality-report-remediation
kind=landing
created=2026-08-27T12:34:16Z

# Landing: PLAN-06 forwarded-parser-strictness

**Outcome**: merged. PR #162, squash commit `7d43646`, base `main`.

## Spec findings — all six closed

| ID | Disposition |
|----|-------------|
| FW-4 | Fixed. A malformed `forwarded-pair` now rejects the whole `Forwarded` header; new `HTTP-125` WARN. The pre-existing test asserting the laxity (`RfcForwardedParserTest.ignoresMalformedPairs`) was rewritten. |
| FW-5 | Fixed. `ResolvedForwarding` compact constructor enforces the Javadoc-asserted invariants and rejects control characters. |
| FW-7 | Fixed. Bracket trailing content rejected — and the duplicated check later collapsed into one shared `IpAddresses.hasValidBracketTrailer`. |
| FW-8 | Fixed. Leading-zero IPv4 octets rejected. |
| FW-9 | Only the unbracketed multi-colon half was open (PLAN-05 closed the delimiter half, as the spec predicted); fixed. |
| FW-10 | Fixed. `parsePort` is digit-only. |

## Four defects the spec did not name

1. **IPv4 octets were range-unbounded** (security audit). `999.1.1.1` matched the shape-only regex, so `InetAddress.getByName` treated it as a *hostname* and made a real DNS lookup — measured at 13 ms and 54 ms against 0 ms for a genuine literal, on attacker-controlled `X-Forwarded-For`. Contradicted the class's own "never DNS-resolving" guarantee. Pre-existing, not introduced here. Only the IPv4 arm is affected; the IPv6 arm rejects colon-bearing non-literals with no lookup.
2. **`resolvePort` ignored an unresolvable `Forwarded` header** (CodeRabbit). `resolveScheme`/`resolveHost` received `forwarded`; `resolvePort` did not — so a malformed header dropped the host but still honoured `X-Forwarded-Port`. A gap in D1's own documented guarantee.
3. **`parseHostPort` never validated bracket contents** (CodeRabbit). `[]` and `[not-an-ip]` passed as hosts.
4. **Sonar**: `java:S1845` (BLOCKER — `Parsed.MALFORMED` clashed case-only with the generated `malformed()` accessor) and `java:S6353`.

**These three share one shape**: the host-header path was laxer than the for-chain path. Worth carrying into any future work on this package.

## Cross-workstream facts

- **The WS-06 benchmark was NOT broken** by D2's compact constructor. The outline swept all 11 `new ResolvedForwarding(...)` sites; every one already satisfied the new invariants, `ForwardedBenchmarkState` included. The spec flagged this as a possible cross-module break; it did not materialise, and the file was never edited.
- **No ADR proposed.** `ADR-0002` already states the governing principle ("a header that is present but rejected by sanitization counts as present and unresolvable, not as absent"); this plan is three applications of it to a new failure mode, not a new decision.

## Quality signals at merge (`316f2e3`)

CI 13/13 · local `verify -Ppre-commit` green, 5583 tests · Sonar 0 new-code issues (confirmed) · 3 review bots participated, 0 outstanding · 0 of 31 findings pending.

## Caveats

- **Coverage was never measured.** `verify:coverage` does not resolve in this project's architecture (only `clean/quality-gate/verify/install/compile/package`), so it recorded `skipped` every phase. The plan's 80% bar is unverified.
- **The scope-creep guard measured nothing** — `references.json` carries no `plan_creation_sha`, so it returned `could_not_look`, not a clean zero.
- **Preference-emitter filed zero hints deliberately.** Two patterns cleared every documented gate but are chattiness artifacts: 15 "accepted build-error" findings are Maven `BUILD FAILURE` boilerplate mis-tagged by the log parser, and 5 "test-failure" findings are five fragments of one flaky test. Promoting them would teach the system to auto-accept genuine build errors. Routing rule (e) screens exactly this hazard but is scoped to `pr-comment` only.

## Process cost

3 of 3 loop-back iterations used. 6 phases, 15 tasks, 12 files, 21h8m wall / 2h23m worked.

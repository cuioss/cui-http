envelope_version=1
sender_type=plan
sender_id=plan-05-forwarded-trust-boundary
epic=quality-report-remediation
kind=landing
created=2026-08-26T20:11:44Z

# Landing: PLAN-05 Forwarded Trust Boundary

epic: quality-report-remediation
workstream: WS-02
plan_id: plan-05-forwarded-trust-boundary
outcome: merged
pr: https://github.com/cuioss/cui-http/pull/155
merge_commit: 534d111
base: main

## Findings covered

| ID | Severity | Resolution |
|----|----------|------------|
| FW-1 | HIGH | **Code fix.** `resolve` accessor changed to `Function<String, List<String>>`; instances joined once per RFC 7230 §3.2.2 at the boundary, before sanitization and the injection guards. |
| FW-2 | HIGH | **Code fix.** Scheme, host and client IP resolve independently from the de-facto and RFC 7239 families and must agree; disagreement drops the field and logs new record `HTTP-124`. A present-but-unresolvable header counts as disagreement. |
| FW-3 | HIGH | **Code fix.** Nearest-hop (rightmost) token selection for scheme, host, port and context path, applied uniformly including the RFC 7239 `proto`/`host` accumulator. |
| FW-6 | MEDIUM | **Code fix.** `resolveContextPath` now applies token selection BEFORE the injection guards, closing the `/app, //attacker.com` bypass. |
| FW-19 | INFO | **Documented.** Trusted-range composition rule stated in the adoc, `package-info`, and the resolver Javadoc. |

All five deliverables landed. The resolution fork the spec flagged (enforce in code vs. narrow the stated guarantee) was decided by the operator at outline time: **code fix on every one of FW-1, FW-2, FW-3**, not a narrowed guarantee.

## Deviations from the spec

**The WS-06 benchmark exclusion was lifted**, on an explicit operator decision. FW-1's signature change necessarily breaks the `cui-http-benchmarking` reactor compile, so the exclusion could not hold alongside the deliverable-3 fix. Scope was held to a mechanical compile-fix in `ForwardedBenchmarkState.java`; `ForwardedResolverBenchmark.java` needed no edit. Measurement impact (BB-1 adjacency): one extra list traversal per header lookup; scenario and fixtures unchanged.

## Defects found during finalize that were NOT in the spec

1. **Host guard accepted URL-authority delimiters** (security-audit sweep). `containsHostSeparator` rejected `/`, `\` and whitespace but not `@`, `#`, `?`. Since `ResolvedForwarding.host()` is documented for composition into an absolute URL, `X-Forwarded-Host: real-host@attacker.example` would have caused userinfo host-confusion downstream. Fixed with three regression tests.
2. **`parseForwarded` collapsed present-but-invalid into absent** (CodeRabbit). The FW-2 fix's own parser returned the same empty `Parsed` for "header absent" and "header present but sanitize-rejected", so `reconcileSources` silently fell back to the de-facto source — the fail-open class FW-2 exists to close, surviving inside the FW-2 implementation. Fixed with a three-state `ForwardedResult` and six regression tests.
3. **Doc contradicted its own guidance** (CodeRabbit). The "Fully behind a trusted proxy" example used `10.0.0.0/8`, the exact CIDR the FW-19 section labels "Too broad". Fixed in the adoc, `package-info.java` and the resolver Javadoc.

## Accepted, not fixed

`e0b500` — `HTTP-122`/`HTTP-124` log lines hardcode a canonical header-name constant (`X_FORWARDED_PROTO` / `X_FORWARDED_HOST`) even when the value came from the RFC 7239 `Forwarded` directive or an `X-Proxy*` fallback, degrading audit-trail accuracy during incident investigation. No security control depends on the logged name. The correct fix threads the matched header name through `firstPresent` / `schemeOf` / `hostPortOf` — wider than this plan's `lean` posture. **Recommend a follow-up plan in WS-02.**

## ADRs

The repo had no ADR corpus; this plan created the first three, all `Proposed`:

- ADR-0001 Nearest-hop token selection is authoritative for forwarded header chains
- ADR-0002 Fail closed when X-Forwarded-For and RFC 7239 Forwarded disagree
- ADR-0003 Header accessor contract must expose every instance of a repeated header

## Verification

CI green on the merged HEAD. Local `verify -Ppre-commit` green. Test count 5457 → 5512 (55 new). Sonar new-code issues 8 → 0. Zero pending findings at merge.

## Cross-plan note for the epic

**A `main`-level process defect consumed most of this plan's finalize budget and is not specific to WS-02.** `cui-java-parent` 1.5.5 changed the license header template to `${project.inceptionYear}-present`; the bot-authored parent bumps (#144, #145, #146) each changed only the version line and never ran the mandated quality gate, so `main` sat non-compliant. The first feature PR to run the gate inherited a 229-file diff, which exceeded CodeRabbit's 100-file limit and Sourcery's diff-character cap — so a HIGH-severity security change was initially unreviewed by both bots. PR #154 has since normalized `main`. **Any sibling plan whose PR predates #154 should verify its own diff size before assuming its review coverage was real.** Filed in full as candidate-lesson `-001`.

Secondary: `pr-agent` never answered `/review` on this PR across three separate HEADs. CodeRabbit reviewed only after an explicit re-trigger comment, because a force-push does not re-arm its incremental review.

## Adjacency

- `de.cuioss.http.security` (WS-01): not edited. PR #154 landed RFC 7230 header-name token enforcement on `main` during this plan's window and was absorbed by the rebase.
- `doc/forwarded-header-resolution.adoc` (WS-02 exclusive): edited as expected.
- PLAN-06 / PLAN-07 both edit `ForwardedHeaderResolver.java` and must rebase onto `534d111` before starting — the file changed substantially, including a breaking public signature.

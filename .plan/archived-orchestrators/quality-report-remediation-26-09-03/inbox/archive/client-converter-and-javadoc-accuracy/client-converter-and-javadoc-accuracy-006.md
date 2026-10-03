envelope_version=1
sender_type=plan
sender_id=client-converter-and-javadoc-accuracy
epic=quality-report-remediation
kind=landing
created=2026-09-01T09:33:52Z

# PLAN-10 landing — client converter and Javadoc accuracy

plan_id: client-converter-and-javadoc-accuracy
epic: quality-report-remediation
workstream: WS-03
pr: https://github.com/cuioss/cui-http/pull/182
merge_commit: e02f445929627680bb04a057db0382a55e127d1d
base: main
status: merged

## Deliverables as landed

| # | Finding(s) | Outcome |
|---|-----------|---------|
| 1 | CL-5 | **Landed as behaviour fix.** `StringContentConverter` honours the response-declared charset; constructor charset is the fallback only when the response declares none. Operator chose the behaviour route over docs-only. |
| 2 | CL-4 | **Landed on the DOCS-ONLY route — see the reversal below.** No redirect policy is configured, the JDK default `Redirect.NEVER` applies, and every 3xx surfaces to the caller. Runtime behaviour is unchanged from before the plan; only the (previously false) Javadoc claim is corrected. |
| 3 | CL-8, CL-10 | **Landed.** `SecureSSLContextProvider` class Javadoc no longer reads as enforcing the TLS floor alone. Five published public symbols removed with no deprecation shim (`isSecureTlsVersion`, `FORBIDDEN_TLS_VERSIONS`, `TLS_V1_0`, `TLS_V1_1`, `SSL_V3`) — operator-confirmed clean break. |
| 4 | CL-15 | **Landed.** `HttpHandler` `build()` `@throws` list, `pingHead()`/`pingGet()` return-type docs, the wrong `toURL()` deprecation comment, and the class example's nonexistent `getSSLContext()` call all corrected. |
| 5 | CL-17, CL-18, CL-20 | **Landed.** Converter, responsibility and API-completeness observations documented. Two of the spec's claims were found ALREADY FIXED at HEAD and were scoped out rather than re-documented: `VoidResponseConverter.contentType()`'s `Accept` rationale, and `createHttpClient()` not creating. |
| 6 | `java:S2589` (moved here from PLAN-04) | **Landed.** Always-true conjunct in `ETagAwareHttpAdapter#send` removed by hoisting the invariant into a non-null local, avoiding a JSpecify trip. |

## ⛔ Reversal the epic must know about

**Deliverable 2's behaviour route was implemented, then reverted before merge.**

The plan first took CL-4's "set an explicit redirect policy" route and shipped
`HttpClient.Redirect.NORMAL` on both `HttpClient` construction paths — operator-confirmed as a
deliberate behaviour change. CodeRabbit then rated the PR **Merge Risk: High**:

> The PR changes every request to automatically follow redirects, but redirected destinations are
> not revalidated against the library's URI restrictions. A server could redirect requests to
> unintended same-scheme hosts or ports reachable by the consuming process.

The finding was confirmed against the code: `HttpHandler` runs no `de.cuioss.http.security`
pipeline over a redirect destination (the client `package-info` states this explicitly), and the
JDK's `Redirect.NORMAL` enforces only an HTTPS→HTTP downgrade refusal. A same-scheme redirect to
an attacker-chosen host would have been followed silently — unacceptable in a library whose
purpose is security validation of HTTP components.

The operator reversed the decision. CodeRabbit re-assessed the reverted branch as
**Merge Risk: Low**.

## Follow-up work this plan created

**Validated redirect following is NOT implemented and is owed.** The agreed design, chosen by the
operator over both "accept the risk" and "same-origin only":

- **same-origin by default** (scheme + host + port), with an **opt-in host allowlist** so callers
  who legitimately need cross-host redirects (CDN hand-offs, auth flows, pre-signed storage URLs)
  can opt in explicitly.
- Requires `Redirect.NEVER` plus an explicit bounded redirect loop validating each hop before
  following it, preserving the HTTPS→HTTP downgrade refusal and the builder's scheme policy.
- **Open design question:** where the loop lives. `HttpHandler` owns the `HttpClient` but does not
  send application requests — the adapters do. Putting the loop only in `HttpHandler`'s ping
  methods would leave adapter traffic unprotected, which would be a false fix.

Every corrected redirect passage in the shipped code carries a forward pointer to this work, so
the current no-follow behaviour is documented as a fail-secure baseline rather than a permanent
end state. **This needs its own plan.**

## Adjacent-document impact (WS-05)

This plan changed redirect and TLS Javadoc that `doc/client-handlers-readme.adoc` (DOC-5) and
`doc/http-result-pattern.adoc` (DOC-9) mirror. Neither document was edited here. WS-05 should
re-check both against the merged state — in particular any redirect description, and any reference
to the five removed TLS symbols.

## Files landed

13 files, +760 / −201. Nine under `cui-http-core/src/main/java/de/cuioss/http/client/`
(`ContentType`, `adapter/ETagAwareHttpAdapter`, `adapter/HttpAdapter`, `adapter/package-info`,
`converter/StringContentConverter`, `handler/HttpHandler`, `handler/HttpStatusFamily`,
`handler/SecureSSLContextProvider`, `result/HttpResult`) and four tests, plus one new test
dispatcher (`dispatcher/RedirectDispatcher`).

`cui-http-core/src/test/java/de/cuioss/http/client/adapter/ResilientHttpAdapterTest.java` was NOT
touched — PLAN-13 owns it, as the spec required.

## Verification

Whole-tree `verify -Ppre-commit` green (compile, lint, 6639 tests); coverage profile green; CI green
(run 33488724184); Sonar 3 new-code issues, all triaged and resolved; all review-bot findings from
CodeRabbit and pr-agent resolved.

## Caveats worth carrying forward

- **Scope-creep guard never measured anything** for this plan: `references.json` carries no
  `plan_creation_sha`, so every invocation returned `could_not_look` / `no_baseline_sha`. That is
  absence of evidence, not a clean result.
- **`@EnableMockWebServer(useHttps = true)` is broken in this project** (a pre-existing
  `mockwebserver3` / okhttp mismatch — `Platform.configureTlsExtensions` no longer exists). This
  blocked an end-to-end HTTPS→HTTP downgrade test; the intent was covered at the validation seam
  instead and the gap documented in the test class. Any plan needing real TLS integration coverage
  hits this first.
- **`pre-submission-self-review` gave this plan no structural coverage of its Java changes.** The
  resolved surfacer is a plan-marshall-domain implementor whose detectors classify Java files as
  `other`. It reported the gap honestly rather than claiming a clean pass; the redirect-Javadoc
  cross-consistency audit was performed manually by the orchestrator instead.

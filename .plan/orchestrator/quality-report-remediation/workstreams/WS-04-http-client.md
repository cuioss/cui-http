# WS-04: HTTP Client — Cache, Redirects, Retry, TLS

epic: quality-report-remediation

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-04-http-client.md` and is tracked in the epic
> `status.json` `workstreams[]` field. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

This workstream owns `de.cuioss.http.client.*` and carries the epic's remaining High finding: a
cross-principal data leak in the ETag cache under exactly the cache-key configuration the
Javadoc recommends for token refresh. Around it sits a cluster of protocol-correctness defects
— 301/302 rewriting non-GET methods to GET while still reporting the original method as
successful, a retry classification that treats the JDK's transient handshake failure as
permanent, a redirect policy that forwards credentials to any cross-origin HTTPS target under a
mode whose name promises an allow-list. It closes when no client component reports success for
an operation it did not perform, and no cached response can cross a principal boundary.

## Scope

- In scope: `de.cuioss.http.client` and its `adapter`, `converter`, `handler` and `result`
  subpackages — the ETag caching adapter and cache-key filters, `RedirectPolicy` and the
  redirect follow loop, `RetryConfig` and the retry classifier, `HttpHandler` and the TLS
  context provider, `HttpResult` and `HttpErrorCategory`, `StringContentConverter`, the client
  log messages — and the client test suite.
- Out of scope: `de.cuioss.http.security.*` (WS-01, WS-02); `de.cuioss.http.forwarded` (WS-03);
  the security attack databases and generators (WS-05); the `package-info.java` prose samples,
  which are WS-06's — this workstream changes the client *API surface* and WS-06 rewrites the
  samples once that surface is settled.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-08-etag-cache-principal-isolation | staged | The High cross-principal leak, cache-key identity, cache bounds, and the HEAD conditional path |
| PLAN-09-redirect-retry-tls-result-correctness | staged | Redirect method rewriting and credential forwarding, retry classification, TLS relaxation scope, result-type invariants, converter charset parsing |

## Sequencing and Surface Notes

- PLAN-08 and PLAN-09 are surface-disjoint at file granularity — PLAN-08 owns the `adapter`
  subpackage and the cache-key filter, PLAN-09 owns `handler`, `result` and `converter` — so
  they MAY in principle run concurrently. They are nonetheless sequenced PLAN-08 first, because
  PLAN-08's fallback-content restriction changes which `HttpResult` states the adapter can
  produce and PLAN-09 tightens the invariants on exactly those states.
- This workstream is surface-disjoint from WS-01, WS-02, WS-03, WS-05 and WS-07.
- PLAN-08 is a **security fix with an integrator-visible behaviour change**: restricting
  fallback content to availability failures means a 4xx that previously returned stale content
  will now surface as an error. The plan records that as a documented breaking change and
  rewrites the token-refresh guidance in the same act.
- The report notes roughly forty `ETagAwareHttpAdapterTest` methods that fire real outbound
  HTTPS requests at `api.example.com`. PLAN-08 owns that test class and must neutralise the
  outbound calls as part of its work — it is a build-reliability defect, not only test quality.

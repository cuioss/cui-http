# WS-03: HTTP Client Stack

epic: quality-report-remediation

## Charter

The client stack's TLS posture is genuinely sound — the floor is pinned on the wire, hostname
verification is never disabled, and no trust-all fallback exists anywhere. The defects are
elsewhere: one cache-correctness hole that can make a DELETE appear to have succeeded, a public
record constructor that bypasses every documented invariant, a retry classifier that treats
certificate failures as transient, and a spread of Javadoc that describes behaviour the code does
not have. This workstream closes all 20 CL findings across `de.cuioss.http.client`.

## Scope

- In scope: `cui-http-core/src/main/java/de/cuioss/http/client/**` — the root package plus
  `handler`, `adapter`, `converter`, and `result`, including their `package-info.java` files.
- Out of scope: `de.cuioss.http.security.**` (WS-01) — CL-16 points at
  `security/monitoring/package-info.java` and is owned by PLAN-04, not here; `doc/client-handlers-readme.adoc`
  (WS-05 owns DOC-5); all tests (WS-04).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-08-client-cache-and-result-correctness | staged | The CL HIGH: 304 short-circuit ungated on GET, plus empty-body 2xx, discarded status, cache invalidation |
| PLAN-09-client-config-and-retry-hardening | staged | Unvalidated RetryConfig constructor, SSLHandshake misclassification, builder reuse, URI parsing, lifecycle |
| PLAN-10-client-converter-and-javadoc-accuracy | staged | Charset handling, redirect policy, and the client-side Javadoc/API accuracy sweep |

## Sequencing and Surface Notes

- PLAN-08 → PLAN-09 → PLAN-10. PLAN-08 and PLAN-09 both touch `ETagAwareHttpAdapter.java` and
  `adapter/`; sequential within the workstream.
- PLAN-10 is last for the same reason PLAN-04 is last in WS-01 — it documents what PLAN-08/09 change.
- **Cross-workstream:** CL-4 (redirect Javadoc) may be resolved by *setting* an explicit redirect
  policy rather than by editing Javadoc. That is a behaviour change to `HttpHandler`, so PLAN-10
  must escalate rather than decide it silently.
- **Adjacency:** `doc/client-handlers-readme.adoc` (DOC-5, WS-05) and `doc/http-result-pattern.adoc`
  (DOC-9, WS-05) describe this surface but are not edited here.

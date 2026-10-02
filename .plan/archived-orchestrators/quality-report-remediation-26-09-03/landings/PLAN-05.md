# Landing Analysis: PLAN-05 — Forwarded Trust Boundary

epic: quality-report-remediation
workstream: WS-02
pr: #155 — https://github.com/cuioss/cui-http/pull/155 (MERGED 2026-08-26T20:05:46Z → `534d111`)

> The epic's most consequential landing so far: three HIGH findings closed **in code**, one
> breaking public-API change, and the repo's first three ADRs. Every claim below was corroborated
> against the merged diff at `534d111`.

## Ground-Truth Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #155 merged → `534d111` | **corroborated** | MERGED 20:05:46Z; `origin/main` HEAD. |
| FW-1/FW-2/FW-3 resolved **in code**, not by narrowing the guarantee | **corroborated** | FW-1: `resolve(Function<String, List<String>>)` replaces `Function<String,String>`. FW-2: new `HTTP-124` record in `ForwardedLogMessages`. FW-3: `lastToken` replaces `firstToken` at every selection site including the RFC 7239 accumulator. ⛔ This settles the genuine fork the spec flagged, in the strictest direction available. |
| 5/5 deliverables | **corroborated** | Five outline deliverables covering all six spec deliverables (spec 3+4 merged into outline 1). |
| Tests 5457 → 5512 (+55), Sonar 8 → 0 | **corroborated as reported** | Plan self-report; CI green at merge, `sonar-roundtrip` confirmed. |
| `archive-plan` ran | **corroborated** | `.plan/local/archived-plans/2026-08-26-plan-05-forwarded-trust-boundary`. |
| Diff scale | **corroborated** | 18 files, +1534/−141 — tightly scoped, and a deliberate contrast with PR #154's 240-file churn. |
| Landing message completeness | **contradicted — `complete: false`** | ⛔ `inbox landing-check` reports **all 8 required keys missing**: the message carries no `landing-facts` block at all. Its prose is unusually thorough — richer than PLAN-14's, which WAS complete — but the machine-readable half is entirely absent. Recorded as an Open Defect. |

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Resolve the multi-valued-header gap (FW-1) | **shipped-as-specified — BREAKING** | Accessor contract changed to expose every instance; joined once per RFC 7230 §3.2.2 at the boundary, before sanitization and the injection guards. ADR-0003. |
| 2. Resolve the header-family precedence gap (FW-2) | **shipped-as-specified** | Scheme, host and client IP resolve independently from both families and must AGREE; disagreement drops the field and logs `HTTP-124`. A present-but-unresolvable header counts as disagreement. ADR-0002. |
| 3. Resolve leftmost-token selection (FW-3) | **shipped-as-specified** | Nearest-hop (rightmost) selection for scheme, host, port and context path, applied uniformly. ADR-0001. |
| 4. Fix the context-path first-token drift (FW-6) | **shipped-as-specified** | `resolveContextPath` applies token selection BEFORE the injection guards, closing the `/app, //attacker.com` bypass. |
| 5. Document trusted-range composition (FW-19) | **shipped-as-specified** | Stated in the adoc, `package-info`, and the resolver Javadoc. |
| 6. Regression tests | **shipped-as-specified** | Append-style proxy regression suite; +55 tests overall. |

## Metrics and Anomalies

- ⛔ **The review layers, not the plan, found three real defects.** All three are recorded as fixed:
  1. **Host guard accepted `@`, `#`, `?`** (security-audit sweep) — `X-Forwarded-Host: real-host@attacker.example`
     would have composed to a userinfo-confusion absolute URL. **This is FW-9**, which this epic
     assigned to PLAN-06, and PLAN-05 has now closed it early. Reconciled below.
  2. **`parseForwarded` collapsed present-but-invalid into absent** (CodeRabbit) — the fail-open
     class FW-2 exists to close, surviving *inside* the FW-2 fix. Fixed with a three-state
     `ForwardedResult`. The most instructive defect of the landing.
  3. **The doc example used the exact CIDR its own FW-19 section calls "too broad"** (CodeRabbit).
- **Repo's first three ADRs** (all Proposed), one per HIGH finding.
- Landing message carries no `landing-facts` block — see Open Defects.

## Routing and Merge Behavior

- ⛔ **Deliberate scope deviation — the WS-06 benchmark exclusion was LIFTED by operator decision.**
  FW-1's signature change necessarily breaks the `cui-http-benchmarking` reactor compile, so the
  exclusion could not hold alongside deliverable 3. Scope was held to a mechanical compile fix in
  `ForwardedBenchmarkState.java`; `ForwardedResolverBenchmark.java` needed no edit. **This is the
  first time a cross-workstream surface boundary in this epic actually had to be crossed**, and it
  was crossed knowingly and minimally. PLAN-16 must read that file as already-fixed.
- **BB-1 adjacency, self-reported:** one extra list traversal per header lookup; scenario and
  fixtures unchanged. PLAN-16's BB-1 baseline moves slightly.
- **Review-tooling caveats:** pr-agent never answered `/review` across three HEADs, and CodeRabbit
  only reviewed after an explicit re-trigger — a force-push does not re-arm its incremental review.
  Both are promoted as lessons.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` 155; `landing`; `plan_marshall_plan_id`
- [x] FW-9 moved out of PLAN-06 — closed early here
- [x] `e0b500` folded into PLAN-07 deliverable 1 (FW-11), widened to cover `HTTP-124`
- [x] PLAN-16 surface note — `ForwardedBenchmarkState.java` already compile-fixed
- [x] Open Defect — landing message has no `landing-facts` block
- [x] Open Defect — PR #154's review coverage was degraded by inherited churn
- [x] 4 candidate-lessons promoted (`2026-08-26-20-001..004`)

## Follow-Ups

- **`e0b500` needs NO new plan.** It is FW-11, which PLAN-07 already owns; PLAN-05 only widened its
  blast radius by adding `HTTP-124` to the same defect class. The landing's "recommend a follow-up
  plan in WS-02" is satisfied by the existing PLAN-07 deliverable, now amended.
- **The parent-bump finding is retrospective, and its live risk is already spent.** Both remaining
  early-branched plans (PLAN-08, PLAN-11) branched *from* `2a86a59`, so they carry no license-header
  churn — verified: 5 and 22 files respectively. The damage landed on PR #154 alone.

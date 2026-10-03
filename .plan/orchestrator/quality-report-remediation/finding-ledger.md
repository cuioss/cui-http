# Finding Ledger — every report finding, its ground-truth verdict, and its owning plan

epic: quality-report-remediation

> The epic's traceability record. One row per finding id in the review report, carrying the
> ground-truth verdict established at decompose time and the plan that owns the finding's
> deliverable. This document exists so the epic's closing condition — **every finding has a
> deliverable or a refutation** — is checkable rather than asserted.
>
> This is a NARRATIVE document. It is not derived from `status.json` and the `compact` stage never
> regenerates it. Update it by hand when a landing changes a row's disposition.

## Ground-truth method

Six read-only verification passes, one per report document, re-checked every finding's cited
`path:line` against the repository at HEAD `d242ba5` and returned a verdict from the closed
vocabulary `corroborated` / `contradicted` / `unverifiable`. Each pass read the surrounding code
on its merits rather than trusting the report, and re-derived every count the report quotes.

The verification rests on one structural fact, itself verified: commit `a32fa55` added
`doc/quality-report/` and commit `d242ba5` removed it, and **neither touched any production code,
test code, build file, or other documentation**. Every `path:line` citation in the report
therefore resolves exactly at HEAD, and the report's own adversarial verification (an independent
end-to-end re-check of all 125 original findings, which refuted none) still holds unmodified.

## Totals

| Verdict | Count | Consequence |
|---|---:|---|
| `corroborated` | 125 | Owned by a plan as a deliverable; labelled `OBSERVED` in that plan's spec |
| `unverifiable` | 1 | `F-documentation-23` — the epic's only candidate refutation; labelled `HYPOTHESIS` with a named confirm/refute artifact in PLAN-13 |
| `contradicted` | 0 | — |
| **Total** | **126** | |

Zero findings were refuted. That is the single most important output of the ingestion pass: the
epic produces 125 deliverables and exactly one open question, not a mix of real defects and
report noise.

## Corrections the verification pass made to the report

These are recorded here because a downstream plan that re-derives them from the report would get
them wrong.

| # | Correction | Affects |
|---|---|---|
| 1 | `UrlSecurityFailureType` declares **25** enum constants, not the 24 the report and its own adversarial verification both state | F-E-3's framing; PLAN-10 deliverable 3 |
| 2 | There are **32** `*Generator.java` files under `generators/**`, not 33 (the adversarial pass had already corrected this; independently reconfirmed) | F-E-9, F-E-14; PLAN-11 |
| 3 | `CacheKeyHeaderFilter.java` and `RetryConfig.java` citations run **14-15 lines short** throughout the report | F-client-1, F-client-10; PLAN-08, PLAN-09 |
| 4 | `HttpHandler.java:1140-1141` (cited for F-client-11's read-timeout claim) points at unrelated content; the correct location is `:475-479` | F-client-11; PLAN-09 |
| 5 | `cui-http-core/pom.xml:66` (cited for the `Automatic-Module-Name` property) is an unrelated `<dependency>` block; the property is at `:15` | F-D-15; PLAN-14 |
| 6 | Three forwarded citations are stale: F-D-3's gate is at `:431`, F-D-8's `identifier(126)` at `:78-82`, F-D-19's malformed-spec source at `:104-106` | PLAN-06, PLAN-07 |
| 7 | Three documentation citations are stale: F-documentation-2's import at `:284` and checklist at `:434`; F-documentation-17's increment call at `:47` | PLAN-12 |

## Findings the verification pass found WORSE than reported

| Finding | What the report says | What the verification established |
|---|---|---|
| `F-D-14` | A missing results directory leads to the published benchmark site being replaced by an empty tree | The history-merge loop is skipped in the same state, so the `rm -rf` destroys the **whole published history and index**, not one run. The `continue-on-error` history fetch is a **second, disjoint trigger** for the same loss, and fixing one does not fix the other. |
| `F-D-18` | An over-scoped App token is requested for a repository the workflow only reads | The token is readable by **every later step in the job**, including `./mvnw install` and the JMH benchmark runs, so a compromised action or transitive dependency could use it for authenticated **write** access to this repository. |

## The ledger

Verdict is `corroborated` for every row except the one marked. "Owner" is the plan whose
deliverable closes the finding. "Co-owner" appears only where a finding genuinely spans two file
surfaces the epic keeps disjoint — the code half and the prose half — and names the plan that
closes the other half.

### Lead reviewer stream (`L-`) — 13 findings

| ID | Severity | Verdict | Owner | Co-owner | Note |
|---|---|---|---|---|---|
| L-1 | Medium | corroborated | PLAN-01 | — | Same defect family as F-A-8 and V-1; all three kept, PLAN-01 closes them together |
| L-2 | Medium | corroborated | PLAN-01 | — | F-A-4 is the primary record |
| L-3 | Medium | corroborated | PLAN-04 | — | With F-B-1 and F-B-2, the content-type cluster |
| L-4 | Medium | corroborated | PLAN-03 | — | F-B-5 is the primary record of the L-4 / F-A-5 / F-B-5 trio |
| L-5 | Medium | corroborated | PLAN-05 | — | Adds that no cookie-content pipeline exists at all |
| L-6 | Low | corroborated | PLAN-05 | — | F-B-8 is the same code path |
| L-7 | Low | corroborated | PLAN-15 | — | F-A-10 is the same defect |
| L-8 | Medium | corroborated | PLAN-02 | — | F-B-4 is an exact duplicate; F-A-9 is adjacent |
| L-9 | Low | corroborated | PLAN-02 | — | Cf format and non-ASCII Zs characters, not covered by `isISOControl` |
| L-10 | Low | corroborated | PLAN-01 | — | LENIENT preset leaves an encoding layer in the returned value |
| L-11 | Low | corroborated | PLAN-15 | — | F-A-11 is the same surface |
| L-12 | Low | corroborated | PLAN-09 | — | Superseded by F-client-2; PLAN-09 carries the single record |
| L-13 | Info | corroborated | PLAN-01 | PLAN-13 | Code half in PLAN-01; ADR-0014's inaccurate scoping text in PLAN-13 |

### Added by adversarial verification (`V-`) — 1 finding

| ID | Severity | Verdict | Owner | Co-owner | Note |
|---|---|---|---|---|---|
| V-1 | Medium | corroborated | PLAN-01 | — | Traced statically: `..?` misses every layer and survives in the returned value |

### Validation stages and configuration (`F-A-`) — 14 findings

| ID | Severity | Verdict | Owner | Co-owner | Note |
|---|---|---|---|---|---|
| F-A-1 | **High** | corroborated | PLAN-01 | — | `%25%32%66` → `%2f` under every preset |
| F-A-2 | **High** | corroborated | PLAN-01 | — | `a%CD%BEb` → `a;b` from the parameter-name pipeline |
| F-A-3 | Medium | corroborated | PLAN-05 | — | A test named `shouldBeCaseSensitive` pins the defect |
| F-A-4 | Medium | corroborated | PLAN-01 | — | Primary record for the malformed-UTF-8 pair with L-2 |
| F-A-5 | Medium | corroborated | PLAN-03 | — | Duplicate of L-4 / F-B-5 |
| F-A-6 | Medium | corroborated | PLAN-02 | — | `lenient()` admits C0 controls other than CR/LF into headers |
| F-A-7 | Medium | corroborated | PLAN-02 | — | `allowExtendedAscii` default contradicts its own Javadoc |
| F-A-8 | Medium | corroborated | PLAN-01 | PLAN-13 | Stage Javadoc already documents much of this; the pipeline/README half is PLAN-13's |
| F-A-9 | Low | corroborated | PLAN-02 | — | Query and cookie sets narrower than the cited RFCs |
| F-A-10 | Low | corroborated | PLAN-15 | — | Duplicate of L-7 |
| F-A-11 | Low | corroborated | PLAN-15 | — | Duplicate of L-11; `DOUBLE_ENCODING_PATTERNS` holds *single*-encoded sequences |
| F-A-12 | Low | corroborated | PLAN-15 | PLAN-13 | `SecurityDefaults` Javadoc contradiction in PLAN-15; the ADR drift in PLAN-13 |
| F-A-13 | Medium | corroborated | PLAN-02 | — | Two stage tests that cannot detect a regression |
| F-A-14 | Info | corroborated | PLAN-02 | — | Paranoid block-list applied to parameter values with path semantics |

### Pipelines, data, exceptions, monitoring (`F-B-`) — 16 findings

| ID | Severity | Verdict | Owner | Co-owner | Note |
|---|---|---|---|---|---|
| F-B-1 | Medium | corroborated | PLAN-04 | — | List entries not canonicalised like the values they match |
| F-B-2 | Medium | corroborated | PLAN-04 | — | `null` Content-Type bypasses a configured allow-list |
| F-B-3 | Medium | corroborated | PLAN-04 | — | Factory Javadoc promises XSS and header-content detection no stage performs |
| F-B-4 | Medium | corroborated | PLAN-02 | — | Exact duplicate of L-8 |
| F-B-5 | Medium | corroborated | PLAN-03 | — | **Primary record** of the L-4 / F-A-5 / F-B-5 trio |
| F-B-6 | Medium | corroborated | PLAN-05 | — | Prefix helpers validate the suffix and store the value unvalidated |
| F-B-7 | Medium | corroborated | PLAN-03 | — | Exception messages reproduce up to 200 chars of credential material |
| F-B-8 | Low | corroborated | PLAN-05 | — | Same code path as L-6, different consequence |
| F-B-9 | Low | corroborated | PLAN-05 | — | Two attribute-splitting implementations with divergent key rules |
| F-B-10 | Low | corroborated | PLAN-04 | — | `isPlainText` uses equality where every sibling uses substring |
| F-B-11 | Low | corroborated | PLAN-13 | PLAN-15 | Doc half is F-documentation-10; the nine dead enum constants are PLAN-15's dead-surface deliverable |
| F-B-12 | Low | corroborated | PLAN-12 | — | Same defect as F-documentation-2, -3, -16 and F-client-6 |
| F-B-13 | Low | corroborated | PLAN-04 | — | `@ToString(callSuper=true)` renders an identity hash |
| F-B-14 | Low | corroborated | PLAN-04 | — | Pipeline-area test gaps, incl. the uncaught-`RuntimeException` contract |
| F-B-15 | Info | corroborated | PLAN-02 | — | Same mechanism as F-A-7 |
| F-B-16 | Info | corroborated | PLAN-01 | — | Raw CR/LF rejected, `%0D%0A` accepted |

### HTTP client (`F-client-`) — 18 findings

| ID | Severity | Verdict | Owner | Co-owner | Note |
|---|---|---|---|---|---|
| F-client-1 | **High** | corroborated | PLAN-08 | — | Traced end to end; the epic's highest-impact code defect |
| F-client-2 | Medium | corroborated | PLAN-09 | — | Supersedes L-12 |
| F-client-3 | Medium | corroborated | PLAN-09 | — | A redirected DELETE is reported as a successful DELETE after issuing a GET |
| F-client-4 | Medium | corroborated | PLAN-09 | — | The carve-out contradicts its own adjacent Javadoc rationale |
| F-client-5 | Medium | corroborated | PLAN-09 | PLAN-13 | Code comment in PLAN-09; `LogMessages.adoc` claim in PLAN-13 (F-documentation-4) |
| F-client-6 | Medium | corroborated | PLAN-12 | — | Same defect as F-documentation-2 |
| F-client-7 | Low | corroborated | PLAN-08 | — | 43 of 54 methods `assertNotNull`; 45 unmocked outbound call sites |
| F-client-8 | Low | corroborated | PLAN-09 | — | No timeout, malformed-response, 5xx-exhaustion or wire-level TLS-floor test |
| F-client-9 | Low | corroborated | PLAN-09 | — | Safe only because of the handler's call order, not its own logic |
| F-client-10 | Low | corroborated | PLAN-09 | — | Sub-millisecond initial delay accepted, then hot-loops at 0 ms |
| F-client-11 | Low | corroborated | PLAN-09 | PLAN-13 | Javadoc half in PLAN-09; `http-result-pattern.adoc` self-contradiction in PLAN-13 |
| F-client-12 | Low | corroborated | PLAN-08 | — | HEAD reads the cache but is never made conditional |
| F-client-13 | Low | corroborated | PLAN-09 | — | `charset = X` with whitespace silently falls back to UTF-8 |
| F-client-14 | Low | corroborated | PLAN-09 | — | `InterruptedException` → `CONFIGURATION_ERROR` |
| F-client-15 | Info | corroborated | PLAN-08 | — | Raw credential values in cache keys; no TTL |
| F-client-16 | Info | corroborated | PLAN-09 | — | Cleartext constructor silently discards TLS settings and misreports |
| F-client-17 | Low | corroborated | PLAN-09 | — | `failure(null, …)` throws NPE on first `getErrorMessage()` |
| F-client-18 | Info | corroborated | PLAN-09 | — | Three files in the whole client test tree use a `TypedGenerator` |

### Forwarded headers, build, CI (`F-D-`) — 20 findings

| ID | Severity | Verdict | Owner | Co-owner | Note |
|---|---|---|---|---|---|
| F-D-1 | **High** | corroborated | PLAN-06 | — | The family the proxy does not write is attacker-winnable |
| F-D-2 | **High** | corroborated | PLAN-06 | — | One bogus `Forwarded` header blanks scheme, host, port and client IP |
| F-D-3 | Medium | corroborated | PLAN-06 | — | Documented as a deployment precondition; the option name overpromises |
| F-D-4 | Medium | corroborated | PLAN-06 | — | Host and port compared as one record |
| F-D-5 | Medium | corroborated | PLAN-07 | — | Six-character host deny-list |
| F-D-6 | Low | corroborated | PLAN-07 | — | Context-path guard blind to `?`, `#`, `;`, dot-segments, encoded slashes |
| F-D-7 | Low | corroborated | PLAN-07 | PLAN-13 | Code comment in PLAN-07; `forwarded-header-resolution.adoc` prose in PLAN-13 |
| F-D-8 | Low | corroborated | PLAN-13 | — | Doc says HTTP-120..125; the code has 126 |
| F-D-9 | Low | corroborated | PLAN-07 | — | HTTP-120 has no test driving the resolver's WARN path |
| F-D-10 | Low | corroborated | PLAN-07 | — | Port-suffix parsing asymmetry between the bracketed and bare branches |
| F-D-11 | Info | corroborated | PLAN-07 | — | IPv4-mapped IPv6 forms bypass the leading-zero rule |
| F-D-12 | Low | corroborated | PLAN-07 | — | Duplicate RFC 7239 directives tolerated |
| F-D-13 | Low | corroborated | PLAN-07 | — | `sanitizeForLog` misses U+2028/U+2029 and bidi controls |
| F-D-14 | Medium | corroborated | PLAN-14 | — | **Worse than reported** — see the table above |
| F-D-15 | Low | corroborated | PLAN-14 | — | Classifier jar leaks synthetic classes and duplicates the module name |
| F-D-16 | Info | corroborated | PLAN-14 | — | Lombok declared, never used |
| F-D-17 | Info | corroborated | PLAN-14 | — | Stage benchmarks never reach the paths they claim to measure |
| F-D-18 | Low | corroborated | PLAN-14 | — | **Worse than reported** — see the table above |
| F-D-19 | Low | corroborated | PLAN-07 | — | Existence-only asserts; no generator usage; thin CIDR coverage |
| F-D-20 | Info | corroborated | PLAN-13 | — | Host-rejection description, ADR-0002's omission of port, `Status: Proposed` |

### Security test framework (`F-E-`) — 16 findings

| ID | Severity | Verdict | Owner | Co-owner | Note |
|---|---|---|---|---|---|
| F-E-1 | Medium | corroborated | PLAN-10 | — | Rejected on the literal `?` before the mechanism runs |
| F-E-2 | Medium | corroborated | PLAN-10 | — | Seven methods that cannot fail |
| F-E-3 | Medium | corroborated | PLAN-10 | — | "Any of 6-10 of 25" assertions labelled as exact |
| F-E-4 | Medium | corroborated | PLAN-10 | — | 23 of 24 database entries decided by another mechanism |
| F-E-5 | Medium | corroborated | PLAN-11 | — | Confirmed against the disassembled `cui-test-generator` jar |
| F-E-6 | Medium | corroborated | PLAN-11 | — | Valid generators too narrow to reveal a false positive |
| F-E-7 | Medium | corroborated | PLAN-11 | — | Overlapping generators, misplaced test, mis-routed consumer |
| F-E-8 | Medium | corroborated | PLAN-13 | — | Analysis-document counts; PLAN-13 re-derives them |
| F-E-9 | Medium | corroborated | PLAN-13 | — | Stale class inventories; a database listed that does not exist |
| F-E-10 | Low | corroborated | PLAN-10 | PLAN-13 | Surviving local fold assertion in PLAN-10; ADR statuses in PLAN-13 |
| F-E-11 | Low | corroborated | PLAN-10 | — | Rationales naming the wrong rejection cause |
| F-E-12 | Low | corroborated | PLAN-11 | — | Branches no validator can reject |
| F-E-13 | Low | corroborated | PLAN-11 | — | `PARAMETER_NAME` omitted from the type generator |
| F-E-14 | Low | corroborated | PLAN-11 | — | 10 of 32 generators, tautological assertions |
| F-E-15 | Low | corroborated | PLAN-11 | — | Eleven generators with no contract test |
| F-E-16 | Info | corroborated | PLAN-11 | — | `hashCode()`-based selection is not reproducible |

### Documentation (`F-documentation-`) — 28 findings

| ID | Severity | Verdict | Owner | Co-owner | Note |
|---|---|---|---|---|---|
| F-documentation-1 | Low | corroborated | PLAN-13 | — | **Genuine fork** — commit the executor vs. document the fallback |
| F-documentation-2 | Medium | corroborated | PLAN-12 | — | Same defect as F-client-6; contract genuinely inverted |
| F-documentation-3 | Low | corroborated | PLAN-12 | — | Constructor that does not exist |
| F-documentation-4 | Medium | corroborated | PLAN-13 | — | Re-derived: exactly 7 falsely-claimed WARN records |
| F-documentation-5 | Low | corroborated | PLAN-13 | — | Same as F-D-8 |
| F-documentation-6 | Low | corroborated | PLAN-13 | — | CWE citations resolve to tests containing no CWE reference |
| F-documentation-7 | Low | corroborated | PLAN-13 | — | Re-derived: 1435 vs 395/553; 164 vs 213/50 |
| F-documentation-8 | Medium | corroborated | PLAN-13 | — | Detections attributed to a stage that never decodes |
| F-documentation-9 | Low | corroborated | PLAN-13 | — | Three profiles vs four; body validators that throw |
| F-documentation-10 | Medium | corroborated | PLAN-13 | — | Same as F-B-11; the example dies on `[` |
| F-documentation-11 | Low | corroborated | PLAN-13 | — | ADR-0006 contradicts ADR-0015; the code confirms ADR-0015 |
| F-documentation-12 | Low | corroborated | PLAN-13 | — | Index self-contradicts; all 15 ADRs `Proposed`; index orphaned |
| F-documentation-13 | Low | corroborated | PLAN-12 | — | Depth validation misattributed; three public stages omitted |
| F-documentation-14 | Medium | corroborated | PLAN-13 | — | Character sets and resource limits the code does not implement |
| F-documentation-15 | Medium | corroborated | PLAN-13 | — | SEC-1/2/6/9/10/14/16 unqualified; traceability marks them VERIFIED |
| F-documentation-16 | Medium | corroborated | PLAN-12 | — | Whole query strings fed to the parameter-*value* pipeline |
| F-documentation-17 | Low | corroborated | PLAN-12 | — | Monitoring sample double-counts |
| F-documentation-18 | Low | corroborated | PLAN-12 | — | Three referenced APIs do not exist; handler inventory incomplete |
| F-documentation-19 | Low | corroborated | PLAN-12 | — | Public types with no usage example |
| F-documentation-20 | Low | corroborated | PLAN-12 | — | 18 files cite a specification that does not exist |
| F-documentation-21 | Low | corroborated | PLAN-13 | — | Interface claim wrong for three databases; two incomplete inventories |
| F-documentation-22 | Low | corroborated | PLAN-13 | — | Release skill misstates when the CI check runs |
| F-documentation-23 | Info | **unverifiable** | PLAN-13 | — | **The epic's only candidate refutation.** Needs a site build to settle; the report records it as SUSPECTED |
| F-documentation-24 | Info | corroborated | PLAN-13 | — | AsciiDoc-standard deviations |
| F-documentation-25 | Low | corroborated | PLAN-12 | — | Samples imply the library repairs input; SEC-6 forbids it |
| F-documentation-26 | Low | corroborated | PLAN-12 | — | Overclaimed configurable character sets and pattern definitions |
| F-documentation-27 | Low | corroborated | PLAN-13 | — | Three documents unreachable directly from the README index |
| F-documentation-28 | Info | corroborated | PLAN-13 | — | HTTP-2 omits `requires transitive` for `org.jspecify` |

## Amendments after the PLAN-01 landing (2026-09-06)

- **PLAN-01 shipped** at `7a0da52` (PR #210), closing its 10 findings. See `landings/PLAN-01.md`.
- **PLAN-03 was split into PLAN-03 (4 findings) + PLAN-15 (5 findings).** The five config-surface
  findings — L-7, L-11, F-A-10, F-A-11, F-A-12 — moved to PLAN-15. No finding changed owner in any
  other sense and none was dropped; the total is unchanged at 126.
- **No finding's verdict changed.** The landing corroborated the report on every point it touched.

## Per-plan finding counts

| Plan | Findings | Workstream |
|---|---:|---|
| PLAN-01 | 10 | WS-01 |
| PLAN-02 | 9 | WS-01 |
| PLAN-03 | 4 | WS-01 |
| PLAN-15 | 5 | WS-01 |
| PLAN-04 | 7 | WS-02 |
| PLAN-05 | 6 | WS-02 |
| PLAN-06 | 4 | WS-03 |
| PLAN-07 | 9 | WS-03 |
| PLAN-08 | 4 | WS-04 |
| PLAN-09 | 14 | WS-04 |
| PLAN-10 | 6 | WS-05 |
| PLAN-11 | 8 | WS-05 |
| PLAN-12 | 12 | WS-06 |
| PLAN-13 | 23 | WS-06 |
| PLAN-14 | 5 | WS-07 |
| **Total** | **126** | |

Every finding id in the report appears in exactly one Owner cell. The Co-owner column never
introduces a second owner for a finding — it names the plan closing the *other file surface* of a
finding whose code half and prose half the epic deliberately keeps in separate plans, so that no
two plans edit the same file.

## Verified strengths — do NOT "fix" these

The report's "Verified strengths" sections list roughly sixty specific checks that passed. They
are recorded here because a remediation plan that changes one of them is regressing a property
someone deliberately verified.

- No path-traversal bypass of the default configuration across roughly a hundred encoded,
  double-encoded, overlong, Unicode-folded, semicolon-parameter and backslash spellings.
- Header pipelines reject CR/LF under every preset.
- The TLS floor is pinned on the wire; there is no trust-all path anywhere.
- Redirects refuse a downgrade *before* consulting the allow-list, and strip credentials.
- The forwarded resolver's CIDR arithmetic, the no-DNS guarantee, nearest-hop selection,
  multi-instance joining (ADR-0003) and the three-state `Forwarded` model are all correct.
- Security events are counted exactly once; shared pipelines are thread-safe.
- Every JMH `@Benchmark` method returns its result, so dead-code elimination is correctly avoided.
- All GitHub Actions are pinned by SHA and workflow-level `permissions` are minimal — the
  `benchmark.yml` App-token scope (F-D-18) is the sole exception.
- `when()` and `identity()` are fail-open by design and documented as such in ADR-0013.

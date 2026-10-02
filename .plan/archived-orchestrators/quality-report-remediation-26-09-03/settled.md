# Settled Narrative — quality-report-remediation

Relocated from `epic.md` by the `cleanup` verb on 2026-08-31, operator-confirmed.
Every section here has a **closed subject**: resolved, retired, or superseded.
Bodies are verbatim; `epic.md` carries a pointer at each origin.

## Superseded: the PLAN-09 Sonar fold

- ⛔ **SUPERSEDED — do not act on this entry.** It recorded the 7 Sonar findings as folded into
  PLAN-09 on 2026-08-29. **That fold was reversed the same day**: it never reached the running plan,
  and the operator directed the findings to an upcoming plan instead. The live record is the
  ✅ RESOLVED entry below (all 7 staged on PLAN-04, later 6 after `java:S2589` moved to PLAN-10).
  Kept as the anti-rework record so the PLAN-09 route is not re-derived.

## Barrier lifted 2026-08-27: eight plans landed and drained

- ✅ **BARRIER LIFTED 2026-08-27 — all eight in-flight plans landed and drained.** R=0, inbox empty.
  **Wave 2 = PLAN-03, PLAN-07, PLAN-09, PLAN-12, PLAN-16 — exactly 5 against N=5**, all 10 pairs
  verified disjoint. The predicted PLAN-09/PLAN-12 overlap turned out to be a declaration
  imprecision (PLAN-12 needs two generator FILES in `client/result/`, PLAN-09 needs the sibling
  tests); both specs are narrowed with explicit not-this-plan's-surface notes.

## Retired: PLAN-16 merged-but-staged watch

- ✅ **RETIRED 2026-08-29 — PLAN-16's landing arrived and the row is reconciled.** The watch was
  correct to hold: the merge (`9f26433`) preceded the landing message by ~20 minutes, and the plan's
  finalize was still running. Marking it shipped from the merge alone would have fabricated a
  landing record. Now shipped with PR 170 and `landings/PLAN-16.md` stamped. **Standing rule this
  confirms: a merge commit is not a landing — wait for the message, or state the gap explicitly.**

## Resolved: the 7 Sonar findings found an owner

- ✅ **RESOLVED 2026-08-29 — all 7 Sonar findings are staged on PLAN-04 as deliverable 7.** Final
  disposition after two wrong turns, both recorded so neither is repeated:
  **(1)** the orchestrator first folded them into PLAN-09's spec while its ledger row said `staged`;
  the row was wrong — PLAN-09 had been running since 12:05 and the 18:26 spec edit landed six hours
  after `phase-1-init` ingested it, so the fold could never take effect.
  **(2)** after that was discovered, the orchestrator RE-SCOPED the fold onto PLAN-09 anyway, on a
  literal reading of "fold into the open plans" — adding deliverables to a plan already in
  `6-finalize` with commits pushed, which can only mean a loop-back. ⛔ **The operator corrected it:
  findings go to an upcoming plan, never a running one.** Deliverable 7 is now on PLAN-04, the next
  staged plan in queue order, with each of the 7 scoped to ONE mechanical fix and explicit
  do-not-widen boundaries. **`java:S1845`, the set's only BLOCKER, now has an owner again** — it was
  orphaned when PLAN-07 shipped `ForwardedHeaderResolver` without fixing it.
  ⛔ **PLAN-04 must NOT launch while PLAN-09 runs** — deliverable 7 opens `RetryConfig.java` and
  `ETagAwareHttpAdapter.java`, which PLAN-09 is actively editing. PLAN-09 lands first, then the two
  WS-03 line numbers are re-derived against the new HEAD.
  ⛔ **Do not read PLAN-09's landing as having closed any Sonar finding** — its spec's deliverable 7
  has been removed and never reached the running plan.

## Resolved: the cui-test-mockwebserver-junit5 chain

- ✅ **RESOLVED — the `cui-test-mockwebserver-junit5` consumption chain is COMPLETE through step 3.**
  PR #173 (`7807a00`) bumped `cui-java-parent` 1.5.9 → 1.5.10, and that parent's BOM carries
  `version.cui.test.mockwebserver=1.6` (verified in the resolved POM). So steps 2 and 3 — the
  `cuioss-parent-pom` bump and its release — are done, and cui-http now resolves 1.6 with no local
  override needed. ⛔ **Step 4 remains: the two deferred PATCH/OPTIONS test rows of the 304 matrix
  (finding `36b80c`) are still unwritten**, and they are `client/adapter` tests — PLAN-09's surface,
  and PLAN-09 is the one plan still open. *Re-check trigger:* now — this is the cheapest moment to
  land them, and the window closes when PLAN-09 does.

## Resolved: the gh auth probe failure

- ✅ **RESOLVED and HANDLED 2026-08-29 (operator): the `gh` auth probe failure is FIXED.** Four
  plans filed reports on it — PLAN-03, PLAN-16, PLAN-07 and PLAN-12 — and the orchestrator initially
  read the differing mechanisms (a 60.6 s host property, multi-account mis-parsing, a hard-coded 60 s
  timeout straddling a two-account probe gap) plus its own non-reproducing measurements (417 ms,
  one configured account) as four unreliable reports. ⛔ **That inference was wrong, and the
  correction is the durable part: the four plans were running CONCURRENTLY and all hit the SAME
  transient condition at the same time.** Four records of one event, not four theories about a
  phantom. The orchestrator's measurements were taken after the condition had passed, which is
  exactly what a concurrency-induced transient looks like — so a clean later measurement was never
  evidence against the reports. **Standing lesson for this epic's analysis: when N concurrent plans
  report the same failure with differing mechanisms, the null hypothesis is one shared event, not N
  bad reports — and a post-hoc measurement cannot refute a transient.** No further action; the
  underlying defect is fixed.

## Merged: PR #175 and its U+2044 residue

- ✅ **PR #175 MERGED (`0b6dd71`) — corroborated, and its residue is folded into PLAN-13 deliverable 7.**
  Verified independently across `898024b..0b6dd71`: the escalation from a one-line ask to 5 files was
  JUSTIFIED. `UnicodeAttackGenerator.LOOKALIKE_TRAVERSAL` was genuinely broken — NFKC of
  `U+2024 U+2024 U+2215` is `..` + U+2215, **not** `../`, so the generator emitted a non-traversal as
  a traversal attack in the published `generators` artifact. The `U+FF0F` fix is correct. Both
  deliberately-untouched U+2215 sites were checked and are correctly left (`createHomographAttack`
  asserts visual confusability, not normalization; the `U+2215 config` payload is valid rejection data).
  ⛔ **The U+2044 residue was NOT in #175 and remains on main** — `UnicodeNormalizationAttackTest.java:181`
  still claims `(../)` for a payload that NFKC-folds to `..` + U+2044. The payload is fine; only the
  comment is false. **Folded into PLAN-13 deliverable 7**, with the executable invariant attached.

## Resolved: validated redirect following (PLAN-17, PR #186)

- ✅ **RESOLVED 2026-09-01 by PLAN-17 (PR #186, `9446171`).** ⛔ **The open design question was
  settled the RIGHT way, not assumed**: the bounded revalidating loop lives in `HttpHandler` (`send`
  :554 / `while(true)` :559; `sendAsync` :592 via `sendAsyncHop` :606) **and `ETagAwareHttpAdapter`:688
  now calls `httpHandler.sendAsync` where it previously called `httpClient.sendAsync`, its own
  `httpClient` field removed** — the harder option, rather than the ping-methods-only false fix the
  spec warned against. `ResilientHttpAdapter` inherits by delegation; the raw `createHttpClient()`
  path keeps `Redirect.NEVER` and is fail-secure. Same-origin default with an opt-in allowlist, plus
  an operator-added `CredentialForwarding` strategy whose opt-in changes **no `refuse` verdict**.
  ⚠️ **The HTTPS→HTTP downgrade refusal is still not asserted end-to-end** — see the mockwebserver
  defect. Historical record follows: **VALIDATED REDIRECT FOLLOWING IS OWED AND UNOWNED — the largest
  capability gap this epic created.** PLAN-10 implemented `HttpClient.Redirect.NORMAL` under operator confirmation, then
  **reverted it** after CodeRabbit rated the PR Merge Risk **High**: redirect destinations are never
  revalidated against the library's URI restrictions, so a server could steer requests to same-scheme
  hosts the caller never named — an SSRF-shaped egress hole in a security library. **Verified against
  the code**: `HttpHandler` runs no `de.cuioss.http.security` pipeline over any destination, and the
  JDK's `Redirect.NORMAL` refuses only an HTTPS→HTTP downgrade. Runtime behaviour at HEAD is
  unchanged (`Redirect.NEVER`); only the previously-false Javadoc is corrected, and every corrected
  passage carries a forward pointer, so the current state reads as a fail-secure baseline.
  **Design already settled by operator decision:** same-origin by default (scheme + host + port) with
  an opt-in host allowlist, via `Redirect.NEVER` plus an explicit bounded redirect loop validating
  each hop and preserving the downgrade refusal and the builder's scheme policy.
  ⛔ **Open design question that must be settled AT OUTLINE, not assumed:** where the loop lives —
  `HttpHandler` owns the `HttpClient` but the **adapters** send application requests, so a loop in
  the ping methods alone would leave adapter traffic unprotected and be a false fix.
  *Owner: none — needs its own plan.* *Source: PLAN-10 landing, 2026-09-01.*

## Resolved: duplicate ADR numbers, cui-http half (PLAN-19, PR #184)

- ✅ **RESOLVED 2026-09-01 by PLAN-19 (PR #184, `c631c77`) — the cui-http half only.** `doc/adr/` now holds fourteen ADRs numbered 0001–0014 with no repeats; the one internal cross-reference (`0009-Attack-database…adoc`:61 → ADR-0011) is repaired and no dangling reference to an old filename survives. The keep-the-earlier-landed ordering was derived from **git commit order, not PR numbers** — `94004bc` (#161) predates `d042750` (#159), so PLAN-15's pair correctly kept 0004/0005. ⛔ **The upstream half is NOT resolved** — see the successor defect above. Historical record follows: **DUPLICATE ADR NUMBERS on main — and this WILL recur.** PLAN-02 and PLAN-15 each allocated
  ADR-0004 and ADR-0005 concurrently, so `doc/adr/` now holds two 0004s and two 0005s. Neither plan
  is individually at fault: there is **no shared ADR-number allocator**, so any two concurrent plans
  that propose ADRs collide. The barrier cadence does not fix this — a wave runs its plans
  concurrently, and the next wave has five candidates of which several may propose ADRs. No staged
  spec owns `doc/adr/` and WS-05 is closed, so this has no owner. ⛔ **Decide before the next wave
  is emitted**, or accept that it recurs. *Source: PLAN-02 landing, 2026-08-27.*
  ⛔ **RECURRED 2026-09-01, EXACTLY AS PREDICTED — four duplicate numbers now stand on main.**
  PLAN-13 (PR #178) landed `0009-Attack-database_entries_verified_structurally…` and
  `0010-NFKC-fold_claims_centralized…`; PLAN-04 (PR #180) landed
  `0009-HttpSecurityValidators_when_and_identity…` and `0010-NormalizationStage_clamps…`. So
  `doc/adr/` now holds two 0004s, two 0005s, two 0009s and two 0010s. The wave WAS emitted without
  the decision being made, and the prediction held: two concurrent plans both proposed ADRs and
  both allocated from the same unguarded sequence. Neither plan is at fault, and neither could have
  detected it — the collision is on a shared sequential resource that is not a file path, so the
  surface-disjointness gate is structurally blind to it. ⛔ **The next wave will do it again unless
  an allocator exists or ADR proposal is serialized.** *Recurrence source: PLAN-04 landing, 2026-09-01.*

# Epic: CUI-HTTP Quality Report Remediation

slug: quality-report-remediation

> Ledger document for one epic under `.plan/local/orchestrator/quality-report-remediation/`.
> The layout and authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. `status.json` is the
> machine authority; any statement here that conflicts with it is stale prose.

## Vision

A full seven-pass review of `cui-http` at commit `7ae6499` (2026-08-15) produced 117 findings
across six reports — 8 HIGH, 29 MEDIUM, 51 LOW, and 29 INFO — spanning the security-validation
pipelines, the forwarded-header trust model, the HTTP client stack, the test suite, the
documentation tree, and the build/benchmarking configuration. The report is a **diagnosis, not a
remediation**: nothing it describes is closed by the report's existence, and its own Top Priorities
section states explicitly that for the active security items prose is not a substitute for
enforcement.

This epic converts that diagnosis into shippable work. The dominant theme is the **gap between
advertised and actual behaviour** — components whose Javadoc, presets, or documentation promise more
validation than the code performs, which is precisely the "false feeling of security" failure mode.
Every finding is therefore resolved one of two honest ways: **close the gap in code**, or
**narrow the stated guarantee** so the contract matches the implementation. Silently leaving a
broad claim over a narrow implementation is not an accepted outcome for any finding in this epic.

Done at the epic level means: every one of the 117 findings has been either fixed, explicitly
narrowed in the library's own Javadoc/AsciiDoc, or recorded here with a rationale for deliberate
non-action — and no finding has been silently dropped.

## Source Corpus

The six review reports plus their index live under `source/` in this epic tree, moved verbatim out
of the git-controlled `doc/quality-report/` by PR #149. They are the epic's input, not a maintained
repository document. See `references.json` for the per-report finding ranges.

⛔ **The reports are a point-in-time diagnosis against commit `7ae6499`.** Every claim carried into a
plan spec from them is labelled `HYPOTHESIS` and carries a named confirm/refute artifact, because
the orchestrator read the *report*, not the *code*. HEAD has advanced since `7ae6499`; the consuming
phase re-grounds each premise against the implementing source before scoping on it.

## START HERE

### Annotations

<!-- ANNOTATION ZONE — hand-written, and deliberately OUTSIDE the generated markers.
     A regeneration replaces only what sits BETWEEN the markers, so everything written
     here survives it. -->

- Epic-wide — `parallelization_scope` is **5**, raised from 1 on 2026-08-26 after a computed
  pairwise surface analysis (see `## Decisions`). WS-01, WS-02, WS-03, WS-05 and WS-06 are mutually
  disjoint at EVERY plan position, so a 5-wide fill is sustainable for the whole epic life rather
  than only the first wave. WS-04 is the sole collider and is admitted whenever its current position
  permits. Disjointness — not this knob — decides eligibility; the knob only caps how many may be
  in flight, and a slot is left EMPTY rather than filled with a colliding plan.

## Ordered Queue

### Queue annotations

- ⚠️ **Row 2 (`PLAN-21`) is NOT emittable and renders here only because `superseded` is outside the
  generator's terminal set.** `no_terminal_in_live_queue` checks for `shipped`/`landed`, so a
  `superseded` row is rendered as live. PLAN-21 was merged into PLAN-20 on 2026-09-01 and its spec is
  a pointer stub; its `(no expected surface section)` cell is that stub, deliberate, and is the single
  spec behind the corpus's `indeterminate_count: 1`. ⛔ **Do not "fix" that surface** — the spec
  declares no work. **`next` must not emit row 2.** (Recorded as a renderer observation, not a defect
  of this epic; the generator is a plan-marshall surface.)

<!-- ANNOTATION ZONE — hand-written, and deliberately OUTSIDE the generated table markers. -->

- **PLAN-11 SHIPPED 2026-08-27** — PR #164 → `8a0aa4c`. TQ-1 closed: all 17 vacuous contract tests
  now carry defining-property assertions behind a shared helper, and TQ-4's un-failable method is
  deleted. Tests 5583 → 6307. 4.31M tokens / 46h30m, the epic's most expensive plan. Landing record:
  `landings/PLAN-11.md`.
- ✅ **The escalate-don't-improvise contract worked end to end — the clearest case in the epic.**
  PLAN-11's spec warned its honest assertions would FAIL against `main`, forbade both weakening them
  and fixing the generators in-plan, and required escalation. The plan escalated via the inbox, the
  operator chose the coordinated pair, PLAN-12 was amended, and the assertions landed green with no
  test ever in a known-failing state.
- **PLAN-06 SHIPPED 2026-08-27** — PR #162 → `7d43646`. All six FW parser-strictness findings, plus
  four the spec never named. Landing record: `landings/PLAN-06.md`. Notably it added **no ADR**
  (`adr-propose` correctly found ADR-0002 already covers the principle), so it did not worsen the
  duplicate-ADR problem.
- ⛔ **A claim in the report's "Verified correct / done well" section was FALSE — the first
  demonstrated case.** The report asserts *"No DNS resolution: `IpAddresses.parse` regex-guards to
  numeric literals … so hostnames can never be resolved into trusted addresses"*
  (`source/forwarded-review.adoc:148`). The old pattern `\d{1,3}(\.\d{1,3}){3}` guarded SHAPE but
  not RANGE: `999.999.999.999` matches it, is not a valid literal, and `InetAddress.getByName`
  therefore treats it as a hostname and performs a **real blocking DNS lookup** — attacker-triggerable
  through `X-Forwarded-For`, measured at 13–54 ms. Fixing the LOW finding FW-8 (range-bounding each
  octet) closed it. ⛔ **Consequence for the whole epic: the report's positive verifications are not
  more reliable than its findings.** PLAN-07's FW-14 zone-ID work and PLAN-16's
  benchmark-methodology claims both rest on such statements and must be re-verified, not assumed.
- **The amended PLAN-06 spec worked exactly as intended.** After PLAN-05 closed FW-9's delimiter half
  early, the spec was rewritten from *implement* to *verify the remainder*; deliverable 4 closed the
  multi-colon host half with no duplicate work.
- **PLAN-02 SHIPPED 2026-08-27** — PR #159 → `d042750`. WS-01's post-decode work: SV-4's strict
  preset genuinely fixed (`true, true` → `false, true`), SV-5 widened to the whole C0/C1 class,
  SV-24's `+` rewriting stopped. Landing record: `landings/PLAN-02.md`.
- ⛔ **SV-3 was closed by NARROWING the guarantee, not by enforcing — and that is now the library's
  recorded position.** `/%3Cscript%3E` is deliberately ACCEPTED and pinned by a characterization
  test; ADR-0004 (the `CharacterValidationStage` one) records it. This inverts what the source review
  expected, and it is **compliant**: the report's Top Priority #4 explicitly permitted either route.
  The consequence is that decoded output can still carry characters the encoded form would reject.
  ⛔ Any later plan tempted to "fix" this must read that ADR first — reversing it contradicts a
  recorded decision and breaks the test pinning it.
- **PLAN-13's watch is CLOSED.** PLAN-02 reported, as its spec required, that no legitimate-database
  expectation broke — precisely BECAUSE deliverable 1 resolved away from full post-decode
  enforcement. The widened check covers only C0/C1, which no legitimate entry contains.
- **SV-10's specified test inputs were unreachable, and the plan handled it correctly.** `/a%2E%2eb/x`
  cannot be rejected at pipeline level (`DecodingStage` precedes `NormalizationStage`, so it arrives
  as the legitimate `/a..b/x`). The executor proved this empirically — both positive controls failed
  too — and substituted double-encoded inputs. A spec defect refuted, demonstrated, and re-scoped
  rather than forced.
- ✅ **WS-05 CLOSED 2026-08-27** — both its plans shipped (PLAN-14 #153, PLAN-15 #161). The epic's
  first completed workstream. All 14 DOC findings are resolved, and the three ownership boundaries
  this epic set around WS-05 held under pressure: PLAN-15 never opened `security/**` (DOC-7's Javadoc
  half stayed PLAN-04's), never touched `doc/forwarded-header-resolution.adoc` (WS-02's), and edited
  no test class.
- **PLAN-15 SHIPPED 2026-08-27** — PR #161 → `94004bc`. All six deliverables, and it took the
  report's *preferred* route on DOC-8: it RETIRED the hand-maintained inventories in favour of
  package-level pointers rather than re-typing today's numbers. That choice is vindicated by its own
  finding — the counts had drifted further than the report knew (27 test classes not 26, 7 pipeline
  test classes not 4, and the "4 legitimate-pattern databases" over-counts because the fourth is the
  interface). Re-typed numbers would already be stale. Landing record: `landings/PLAN-15.md`.
- ⛔ **The PLAN-14 paired-claim hazard recurred in PLAN-15, one level up — exactly as folded, but not
  where it was aimed.** The fold warned about prose/row pairs. It recurred instead as *claim vs.
  build reality*: both new ADRs, written to codify removing documentation overclaims, themselves
  asserted that link validation already runs in this build. It does not. CodeRabbit caught it;
  corrected in `882cc7b`. The lesson generalises beyond the pair shape it was written for.
- **PLAN-05 SHIPPED 2026-08-26** — PR #155 → `534d111`. ⛔ **The epic's most consequential landing:
  all three FW HIGH findings closed IN CODE**, settling the genuine fork the spec flagged in the
  strictest direction available (enforce, not narrow the guarantee). Includes one BREAKING public-API
  change — `resolve(Function<String,String>)` → `resolve(Function<String,List<String>>)` — and the
  repo's first three ADRs, one per HIGH. Landing record: `landings/PLAN-05.md`.
- **Two findings moved on PLAN-05's landing.** FW-9's delimiter half was CLOSED EARLY by its
  security-audit sweep (PLAN-06 deliverable 5 now says verify-the-remainder, not implement); and
  `e0b500` — PLAN-05's accepted-not-fixed log-attribution defect — was folded into PLAN-07's FW-11
  deliverable, widened to cover the new `HTTP-124`. ⛔ **No new plan is needed for `e0b500`**, despite
  the landing recommending "a follow-up plan in WS-02": PLAN-07 already owned that defect class.
- ⛔ **The WS-06 boundary was crossed, knowingly.** FW-1's signature change broke the
  `cui-http-benchmarking` reactor compile, so PLAN-05 mechanically fixed `ForwardedBenchmarkState.java`
  by operator decision. First and so far only cross-workstream crossing in this epic. PLAN-16 must
  read that file as already-current, and BB-1's measurement baseline has moved to `534d111`.
- **PLAN-02 and PLAN-15 RUNNING as of 2026-08-26**, on operator confirmation. R = 5 of N = 5; slots
  are full and nothing may be emitted until a plan lands. Neither carries a stamped
  `plan_marshall_plan_id` yet: PLAN-02's directory exists but has no `request.md`, PLAN-15's does not
  exist. ⛔ Do NOT stamp either from the directory name — resolve through `request.md` `source_id` at
  landing, per the lookup rule below.
- ⛔ **OPERATING CADENCE: WAVE BARRIER, set by the operator 2026-08-27.** *"We drain all plans prior
  starting the next ones."* The epic no longer emits into a slot the moment a landing frees it.
  **All running plans must land AND their inbox messages must drain before the next wave is
  emitted**, and the wave is then emitted as a SET. `parallelization_scope` = 5 now caps the WAVE
  SIZE rather than a rolling in-flight count.
- ⛔ **PLAN-16's emit is RETRACTED under that cadence.** Clean retraction: `auto_emit` is false, so no
  `launched` transition was ever recorded and its row is still `staged` — nothing in `status.json`
  needed undoing. It rejoins the next wave.
- **The barrier retroactively settles the PLAN-16 starvation problem.** Under slot-filling it was
  displaced three times by queue order; under a barrier the wave is emitted as a set, so it can
  never be outranked by a lower-numbered plan again. The withdrawn N=6 recommendation stays
  withdrawn — for a different and now stronger reason.
- ⛔ **The barrier surfaces a collision that never bound under slot-filling.** The next wave's
  candidates are PLAN-03, PLAN-07, PLAN-09, PLAN-12, PLAN-16 — exactly 5 against N=5 — but
  **PLAN-09 and PLAN-12 overlap** on `cui-http-core/src/test/java/de/cuioss/http/client/result/`.
  Arrival time used to separate them; offered together, the disjointness gate must refuse one.
  PLAN-09 wins on queue order and PLAN-12 waits, UNLESS the overlap is resolved first by narrowing
  one spec's surface. Decide that before the wave is emitted, not during it.
- ✅ **PLAN-16 was emitted 2026-08-27 and immediately RETRACTED** (superseded by the barrier above). It is now the SOLE eligible
  candidate: every other staged plan is dependency-blocked behind a running one. The structural
  starvation resolved itself without raising N — WS-05 closing removed the last workstream that
  could keep unblocking a lower-numbered successor. The N=6 recommendation is therefore WITHDRAWN as
  moot for this round; it would still matter if several workstreams unblock simultaneously later.
- ⛔ **SUPERSEDED — the note that PLAN-16 had been held out THREE TIMES** (waves 1, 2 and 3). Each round it
  is eligible, dependency-free and verified disjoint from every running plan; each round a landing
  unblocks a lower-numbered plan that takes the slot on queue order. PLAN-05 shipping unblocked
  PLAN-06, which displaced it again. The pattern is structural, not accidental: WS-06 is a
  single-plan workstream sitting at queue position 16, so every other workstream's successor
  outranks it forever. It will never be reached by queue order while any other workstream is live.
  **Raising N to 6 is the only mechanism that clears it** — the disjointness evidence has supported
  6 since decomposition, and the sole argument against it (a sixth slot standing empty) has been
  falsified three times over.
- ⛔ **SUPERSEDED — the earlier note that PLAN-16 had been held out TWICE.** It is the only plan in the epic that has
  been eligible, dependency-free and verified disjoint from every running plan at every round since
  the first wave. Queue order keeps awarding its slot to a lower-numbered plan that a landing just
  unblocked. It is not blocked by anything real — only by N. Raising N to 6 would clear it in one
  round; otherwise it needs a round where no lower-numbered plan is unblocked.
- **PLAN-14 SHIPPED 2026-08-26** — PR #153 → `0826283`. Five of its six spec deliverables landed;
  the sixth (DOC-4) was deferred to PLAN-07 exactly as the spec directed. Landing was
  `complete: true`. Landing record: `landings/PLAN-14.md`. ⛔ Its summary line claiming "all six
  deliverables addressed" is inaccurate — five were, and its own narrative acknowledges the deferral.
- **Inbox drained 2026-08-26: 9 scanned, 9 archived, 0 invalid, 0 archive failures.** Queue is now
  EMPTY (not *finished* — PLAN-05, PLAN-08 and PLAN-11 are still running and may yet write).
  Dispositions: 2 reconciled, 2 folded, 5 promoted to the lessons corpus.
- **PLAN-11 absorbed PLAN-12's deliverables 1 and 2** (TQ-15, TQ-16, TQ-17) by operator decision at
  its phase-2-refine round, landing assertion and generator fix as a coordinated pair rather than
  marking two assertions expected-failing. PLAN-12 amended 6 → 4 deliverables; PLAN-11's findings
  table extended; epic coverage re-verified at 117/117 with no finding lost.
- **PLAN-01 SHIPPED 2026-08-26** — PR #154 → `2a86a59`. All five SV findings closed, including both
  security HIGHs. 5/5 spec deliverables (the pasted `6/6` is contradicted: the spec and the PR body
  both enumerate five). Landing record: `landings/PLAN-01.md`.
- **PRs discovered and stamped during the PLAN-01 landing analysis:** PLAN-05 → #155 (open),
  PLAN-14 → #153 (open), PLAN-11 → #156 (open). PLAN-08 has no PR yet — it is the only one of the
  four still working without one, which is unremarkable.
- **First wave RUNNING as of 2026-08-26 — PLAN-01, PLAN-05, PLAN-08, PLAN-11, PLAN-14.** Emitted
  after all 10 pairs verified disjoint and all five verified prep-ready (171 claims scanned, 0
  blocking, 0 stale). The operator confirmed at session that all five are running, and the rows were
  transitioned staged → launched → running on that confirmation.
- ⛔ **The `running` state is an operator ASSERTION, not an observed one.** Nothing in this ledger
  polls whether a plan is still alive — there is no liveness signal and no machine field behind this
  note. It records what the operator said, at the moment they said it. A resuming session MUST
  re-confirm with the operator rather than trusting this line, and the compaction stage must treat
  it as narrative and never regenerate it.
- **Slots are FULL: R = 5 of N = 5, so N − R = 0.** No plan may be emitted until one lands. This is
  the binding constraint now — not disjointness.
- ⛔ **SUPERSEDED 2026-08-26 — PLAN-16 did NOT take the freed slot.** The earlier annotation here
  promised PLAN-16 the first slot "ahead of any dependency-unblocked successor". That was the
  orchestrator's own preference and it is overridden by the queue-order walk the `next` selection
  prescribes: PLAN-01 shipping unblocked **PLAN-02**, which sits at queue position 2 against
  PLAN-16's position 16. Both were eligible (deps met, disjoint from all four running); the slot
  went to PLAN-02 on order. PLAN-16 remains eligible and is first in line for the next freed slot,
  now on the same queue-order footing as any other unblocked candidate. Retained rather than
  deleted so the reversal is visible.
- **R = 4 is CONFIRMED, not assumed.** All four running plans were verified alive against ground
  truth: PLAN-05 (worktree + PR #155), PLAN-08 (worktree + main dir), PLAN-11 (worktree at `3fee3b9`
  + PR #156), PLAN-14 (main dir + PR #153). The earlier suggestion that a fifth slot might already
  be free rested on the retracted PLAN-11 claim and is withdrawn — there is exactly ONE free slot,
  and PLAN-02 has it.
- **Plan-id join — do NOT derive it from the spec slug.** Two of the five launched plan directories
  dropped their `PLAN-NN-` prefix (`PLAN-01` → `header-and-content-type-enforcement`, `PLAN-11` →
  `generator-contract-test-semantics`) while the other three kept it. The `plan_marshall_plan_id`
  stamped on each row was resolved through the plan's `request.md` `source_id` pointer and confirmed
  with `inbox detect`; that pointer is the only reliable join.
- **Head wave — all six workstream heads are mutually disjoint.** PLAN-01, PLAN-05, PLAN-08,
  PLAN-11, PLAN-14 and PLAN-16 share no write surface, verified by the pairwise analysis. With
  `parallelization_scope` at 5, five of the six may be emitted at once. Six is the structural
  ceiling (each workstream is internally sequential, so six is the most that can ever be in flight)
  and was deliberately NOT chosen: the sixth slot is fillable only while WS-04 sits at PLAN-11.
- **Residual collisions — 7 pairs, all involving WS-04.** These are real and the gate must honour
  them; they are recorded so a future pairing decision does not re-derive them:
  - PLAN-13 vs PLAN-01, PLAN-02 — `security/validation/` test classes
  - PLAN-13 vs PLAN-08, PLAN-09, PLAN-10 — `client/adapter/ResilientHttpAdapterTest.java`
  - PLAN-12 vs PLAN-09, PLAN-10 — `client/result/` test classes
  So WS-04 pairs freely with WS-02, WS-05 and WS-06 at every position, and with WS-01/WS-03 only
  while it is at PLAN-11.
- PLAN-01 — Queue head. The two HIGH security findings (SV-1, SV-2) live here; it is sequenced
  first because SV-1 is Top Priority #2 in the report's own ranking.
- PLAN-02 — Sequenced after PLAN-01. Both touch `CharacterValidationStage`/`PipelineFactory`; the
  surfaces overlap, so these can never run concurrently regardless of the scope knob.
- PLAN-04 — Deliberately sequenced LAST within WS-01. It is a Javadoc-accuracy sweep over the same
  files PLAN-01..03 edit; running it first would document behaviour those plans are about to change.
- PLAN-05 — Carries three of the epic's eight HIGH findings (FW-1, FW-2, FW-3). Its resolution is
  explicitly allowed to be a narrowed guarantee rather than a code change; the report's Top
  Priority #3 names both routes as acceptable.
- PLAN-07 — Owns `doc/forwarded-header-resolution.adoc` outright, and now also **applies DOC-4**,
  transferred from PLAN-14 by the 2026-08-26 ownership decision. PLAN-14 no longer opens that file.
- PLAN-04 — Now also **removes DOC-7's "Implements: Task P5/C3" Javadoc references** in
  `PipelineFactory` and `SecurityDefaults`, transferred from PLAN-15 by the same decision. PLAN-15
  no longer opens the security tree.
- PLAN-11 — Must land before PLAN-12. PLAN-11 adds the semantic assertions; PLAN-12 fixes the
  generator implementations those assertions are what catch. Landing PLAN-12 first would fix the
  bugs with nothing in place to prevent their regression.
- PLAN-14/PLAN-15 — Both edit `doc/http-security/**`. Sequenced, never paired. After the two
  ownership transfers above, WS-05 writes ONLY inside `doc/` (minus the forwarded doc), `README.adoc`
  and `CLAUDE.md` — which is what makes it disjoint from every other workstream at every position.
- PLAN-16 — The only plan touching `.github/workflows/` and the benchmarking module; fully
  surface-disjoint from every other plan in the epic.

- ⛔ **Generated-Surface under-derivation — read before any disjointness verdict.** The Ordered
  Queue's Surface column is derived by the generator from each spec's `## Expected Surface`
  bullets, and it keeps only entries that resolve to a FILE. Directory-shaped entries (a path
  ending in `/`) are dropped. Four rows therefore understate their real surface, and the queue
  table alone must NOT be used as the disjointness input for them:
  - PLAN-11 renders 2 files but declares seven `generators/*/` directories — its true surface is
    the whole generator-test tree.
  - PLAN-12 renders only `cui-http-core/pom.xml` (which it declares READ-ONLY) and none of the
    generator implementations it actually edits. This row is the most misleading in the table.
  - PLAN-13 renders 2 files but declares the `database/`, `pipeline/` and `validation/` test
    directories.
  - PLAN-04 and PLAN-15 render a few bare `package-info.java` / `SecurityDefaults.java` names
    without their directory prefix, because the spec wrote them in prose-relative form.
  Read the spec's own `## Expected Surface` section before pairing any of these. This is a
  generator derivation limit, not a spec defect — the specs are correct and complete.

## Decisions

{Curated human-facing VIEW; the authoritative append-only record is `logs/decision.log`.}

- 2026-08-26 — **Source corpus relocated out of git control.** `doc/quality-report/` was a
  point-in-time diagnosis carried as a maintained repository document. Moved verbatim (7 files,
  each sha256-verified against its HEAD blob) to this epic's `source/` via PR #149. *Alternative
  considered:* leave it in `doc/` and reference it. *Rejected because* the report describes a
  commit that HEAD has moved past, so keeping it in the doc tree would itself become a
  documentation-drift finding of the kind this epic exists to close.
- 2026-08-26 — **`parallelization_scope = 1`, then raised to 5 the same day.** The operator first
  chose strictly sequential, then instructed "if we have a clear disjointness, raise n accordingly".
  A computed pairwise analysis over all 16 specs' `## Expected Surface` sections — not the
  under-derived queue table — established that five workstreams (WS-01, WS-02, WS-03, WS-05, WS-06)
  are mutually disjoint at every plan position, and that all six workstream HEADS are mutually
  disjoint. *Alternatives considered:* 4 (the largest disjoint head set BEFORE the fixes below) and
  6 (the structural ceiling). *Rejected 4* because two of its three blocking collisions were
  declaration artifacts, not real overlaps. *Rejected 6* because the sixth slot is fillable only
  while WS-04 sits at PLAN-11, so it would sit empty for most of the epic while raising peak
  merge-queue churn. Reaching 5 required three ledger changes, each logged separately: two
  ownership transfers (DOC-4 → PLAN-07, DOC-7's Javadoc part → PLAN-04) and one surface-declaration
  tightening (PLAN-01/02/03 named the whole `security/` test tree when they meant `pipeline/` and
  `validation/`).
- 2026-08-26 — **Six workstreams cut by SURFACE, not by severity.** *Alternative considered:* cut by
  severity (a HIGH workstream, a MEDIUM workstream, …). *Rejected because* the eight HIGH findings
  are spread across four different code surfaces (SV/FW/CL/TQ/BB); a severity cut would have made
  every workstream collide with every other, destroying the disjointness input that `next` consumes.
- 2026-08-26 — **Sixteen plans, deliberately large.** Operator asked for large plans. Four specs
  (PLAN-04, PLAN-07, PLAN-10, PLAN-16) exceed the ~6-deliverable split guard and proceed unsplit —
  each is a homogeneous sweep of documentation-accuracy or configuration edits over one surface
  where splitting would multiply PR overhead without reducing risk. Rationale recorded per spec.
- 2026-08-26 — **Every finding is assigned, including INFO.** The operator asked for the minors to
  be analyzed too. INFO findings that record a *verified-correct* observation with no action
  (TQ-13, TQ-14, FW-21) are named in their plan spec as no-action with the reason, rather than
  omitted — an omission is indistinguishable from an oversight.

## Open Defects

{Defects surfaced by landings or observations not yet owned by a staged plan.}

- ⛔ **`marshal.json`:103 IS WRONG ON `main`, AND MY OWN ADVICE PUT IT THERE.** `required_bots` takes
  a **`bot_kind`**, and **`pr-agent` was the correct value all along** — verified in
  `automatic-review/SKILL.md`, which gates on *"`{bot_kind}` is present in
  `required_bots ∪ optional_bots`"* and names `pr-agent` as that kind throughout. `cuioss-review-bot`
  is the **author login the registry resolves FROM the kind**. HEAD now reads
  `"coderabbit,cuioss-review-bot"` — the login in the kind slot — landed by `c7862d0` (#192).
  ⛔ **Revert it to `pr-agent`**: one line, undoing a regression this ledger recommended.
  ⚠️ **The real defect is a registry `participation_evidence` gap, not a config value**: CodeRabbit
  declares only `review_body`/`inline` but publishes its verdict as an **`issue_comment`**, which is
  additionally in its own `ignore_patterns` — so a genuinely reviewed PR classifies **`absent`**.
  Reproduced on #183, #189, #193, #194. `pm-findings.md` §2.7 is corrected in place.
  *Owner: none — the revert is cui-http's; the registry gap is plan-marshall's.*
- ⛔ **A REFUSAL RECOGNIZER THAT FAILS TOWARD CREDITING PARTICIPATION.** Sourcery's per-account budget
  notice says *"used"*; the recognizer keys on `exceeded|reached|hit` and its `refusal_patterns` lists
  *"reached your weekly rate limit of"*. **A hard refusal is therefore credited as a review.** Every
  other completion-evidence defect here fails toward reporting a real review as *absent* — safe. This
  one fails the other way, so **a merge gate satisfied by it has been satisfied by nothing**, and it
  retroactively weakens every Sourcery participation claim in this epic. `pm-findings.md` §10.11.
  *Owner: none — plan-marshall surface.*
- ✅ **Finding `22aa41` CLOSED 2026-09-03 by PLAN-23 (PR #194, `ff12f34`).** The E2E proof that
  `FORWARD_TO_ALLOWLISTED` forwards credentials cross-origin is restored and **falsified**:
  neutralising `HttpHandler.java`:789 moved the suite 7790/0 → **7788/2**, failing on exactly the
  credential pair. No second server was needed — `RedirectDispatcher`'s `PATH_ALLOWLISTED` route
  already crosses origin via the `127.0.0.1` alias. Zero production code changed.
  Historical record follows: **`marshal.json` NAMES THE WRONG REVIEWER LOGIN, AND THE MISMATCH BLOCKS.** `.plan/marshal.json`:103
  sets `required_bots: "coderabbit,pr-agent"`, but on this repository that reviewer posts as GitHub
  login **`cuioss-review-bot`**. The bot-completion producer matches no author with the configured name
  and resolves the bot to `absent` — **a BLOCKING state** — while it has in fact reviewed and reported
  no issues. **A pure name mismatch is reported as a missing review.** It cost PLAN-20 ~2h and several
  hundred thousand tokens chasing a review that already existed, and it was **fixed plan-locally
  ONLY** — every future plan in this repo hits it again. ⚠️ The repo-side fix is one config value via
  `manage-config`; the tooling side (a distinct `unresolvable_bot_identity` verdict naming the
  configured token AND the logins that did review) is a plan-marshall surface, recorded in
  `pm-findings.md` §2.7. *Owner: none — the config half is cui-http's and is trivially actionable.*
- **`manage-lessons consult` is inert for this project.** On PLAN-20 it surfaced **0 lessons with all
  23 paths `unmapped_paths`** — its `{bundle}:{skill}` derivation is marketplace-shaped and does not
  resolve Java source paths. ⛔ **That zero is a derivation miss, not evidence of no relevant lesson**;
  the three lessons the spec named were consulted by hand. Compounded by a second reader gap: a lesson
  filed with YAML frontmatter (PR #188, outside this epic) renders in `manage-lessons list` with empty
  component/category/title — present on disk, invisible to enumeration. Both recorded in
  `pm-findings.md` §10.6 and §10.7. *Owner: none — plan-marshall surface.*
- ⛔ **THE THIRD REMEDIATION IN THIS EPIC TO REPRODUCE ITS OWN TARGET DEFECT CLASS.** PLAN-20 existed to
  remove guards whose predicate is a broad text filter, and its remediation added six **admission
  counters** that proved only *the broad filter matched* — not that the input belonged to the named
  attack family. CodeRabbit rated it **Major**; fixed, falsified, confirmed on-thread. ⚠️ **The plan's
  own spec carried the sentence that would have caught it** (*"a matched flag makes the skip visible,
  but it does not make a wrong guard right"*) and nothing was checking the plan against its own rule.
  With PLAN-15's ADR overclaim and PLAN-13's vanishing assertions, this is a **pattern, not an
  anecdote**: a plan removing a defect class is the highest-risk place for that class to reappear.
  Recorded in `pm-findings.md` §10.1. *Owner: none — plan-marshall surface.*
- **`scope_creep_check` has now measured nothing on four consecutive plans** — `references.json` still
  carries no `plan_creation_sha`. Root cause known (`pm-findings.md` §5.2). Each plan correctly reports
  it as absence of evidence rather than a clean result. *Owner: none — plan-marshall surface.*

- ⛔ **AN ADR PASS IS OWED, AND THE STEP THAT WOULD HAVE DONE IT WAS COMPOSED IN THEN SKIPPED.**
  `adr-propose` carries `lane: off` in PLAN-17's manifest step-params **yet remained in the composed
  step list** (as did `finalize-step-preference-emitter` and `finalize-step-print-phase-breakdown`),
  so all three were recorded `skipped` on that marker. `adr-propose`'s decision-shape Signal Gate
  **would** have fired, and PLAN-17 settled genuinely ADR-worthy decisions: **the redirect loop
  placement, same-origin-by-default egress policy, and the `CredentialForwarding` strategy**. ⚠️ **The
  lane-off-but-still-listed shape may itself be a compose bug** — a step that is off should not be
  composed in and then skipped on a marker. *Owner: none — the ADR pass is a cui-http decision; the
  compose shape is a plan-marshall surface.*
- ⛔ **`@EnableMockWebServer(useHttps = true)` HAS NOW BLOCKED TLS COVERAGE FOR THREE CONSECUTIVE
  PLANS, and the third time it degraded a SHIPPED SECURITY GUARANTEE.** PLAN-17 exists to refuse
  unsafe redirect hops, and its **HTTPS→HTTP downgrade refusal could not be asserted end-to-end**. It
  is covered at the `RedirectPolicy` validation seam and documented as a gap in
  `HttpHandlerRedirectTest`'s class Javadoc — correct handling, and the third consecutive time the
  correct handling has been to route around this. **A seam-level assertion proves the policy object
  refuses the downgrade; it does not prove the wired client does.** Lesson `2026-08-29-12-001`, now at
  two recurrences. *Owner: none — a standing hole in the project's ability to test its own transport
  security.*
- **PLAN-17's merged tree was not fully re-analysed after its last four commits.**
  `pre-submission-self-review`, `finalize-step-simplify` and `finalize-step-security-audit` last ran
  at `35e5766` and their records keep that SHA; `92b7e20` and `544a448` carry **no bot review at
  all**. Nothing suggests a defect — the point is that the absence of findings over those commits is
  **unmeasured rather than clean**, and the plan disclosed it rather than letting the step outcomes
  imply coverage. ⚠️ Note this is the *second* consecutive plan to merge with a disclosed
  review-coverage boundary (PLAN-18 merged on a one-bot `barrier-ask-override`). *Owner: none.*
- ⛔ **The finalize review loop terminated by BUDGET, not by convergence.** PLAN-17's loop ran to its
  full 3/3 loop-back ceiling and stopped because the operator chose to; every fix push drew a fresh
  round of bot findings. **Eight defects were found after implementation was complete — two of them
  real**: undiscarded intermediate redirect bodies leaking connections under streaming handlers, and a
  mutable egress allowlist escaping through `getAllowedHosts()` (an egress policy callers could mutate
  is the policy failing open). Both verified fixed at HEAD. Recorded because "the loop stopped" and
  "the code converged" are different facts and only the first is established. Promoted as lesson
  `2026-09-01-20-002`. *Owner: none — plan-marshall surface.*

- ⛔ **THE FIRST REAL SURFACE COLLISION OF THE EPIC — two path-comparison mechanisms blind in the
  same direction.** PLAN-18 edited `doc/adr/0014-NormalizationStage_clamps…adoc` while PLAN-19 was
  concurrently **renaming** it (from `0010-…`). GitHub reported PR #185 `CONFLICTING` and the plan had
  to rebase onto `c631c77`. Two independent causes, and fixing either alone leaves the other:
  1. **PLAN-18 under-declared** — its `## Expected Surface` named only `security/` paths, so the
     orchestrator's disjointness gate had nothing to compare against PLAN-19's declared `doc/adr/`.
     The ADR edit itself was correct and substantively right (a `four → five` pattern-count
     correction to the ADR its own change invalidated) — it was simply undeclared.
  2. **`git-workflow baseline-reconcile` was blind to it** — it reported
     `classification=no_overlap, conflict_count=0` over a real rename/edit conflict, because it
     compares by **path**. The orchestrator auto-proceeded and learned of the conflict only from
     GitHub's mergeability state, after the push. Promoted as lesson `2026-09-01-16-001`.
  ⚠️ **Every earlier under-declaration in this epic escaped only because the counterparty had already
  shipped** (PLAN-13's generators tree, PLAN-04's ~20 undeclared paths, PLAN-10's five). This one had
  a live counterparty and fired. The rebase itself was clean — git's rename detection carried the
  correction into `0014-` with no stale duplicate, and the plan verified that rather than assuming it.
  *Owner: none — PLAN-18 is shipped; cause 2 is a plan-marshall surface needing cross-repo routing.*
- ⛔ **A MERGED SECURITY FIX CARRIED ONE REQUIRED-BOT REVIEW, NOT TWO — and `automatic-review: done`
  does not say so.** On PR #185, CodeRabbit reviewed `8f2cf93` clean but was quota-refused at the
  merged HEAD `fd7035a`; Sourcery was budget-exhausted for the whole run and never reviewed; pr-agent
  reviewed clean. The merge was approved because the Java delta between those SHAs is **zero** (the
  whole difference is #184's already-merged ADR renumbering), and a `barrier-ask-override` is recorded
  at `fd7035a` with `gap-class=review-barrier-gap`. **The handling is proportionate and properly
  recorded; the step OUTCOME is what overstates it** — only the landing prose carries the nuance.
  ⛔ Separately, PR #183 was closed unmerged after `automatic-review` **force-done'd past a CORRECT
  `absent` verdict**, justified by an invented registry-classification gap. CodeRabbit had published
  nothing on #183 (0 reviews, 0 inline comments, 0 check-runs) behind a green `Review completed`
  commit status that is a rate-limit placeholder. *Owner: none — plan-marshall surface.*
- ⛔ **THE RATE-LIMIT LESSON IS NOT FUNCTIONING AS A CONTROL.** Lesson `2026-08-27-12-011` now carries
  **four** recurrences, and the fourth violated a rule folded into it **roughly six hours earlier in
  this same epic**. During PLAN-18's `branch-cleanup` a retrigger loop posted `@coderabbitai review`
  every ~2 minutes for ~55 minutes against a quota-blocked bot: ~27 spam comments on a public PR,
  ~1.5h wall clock, and — the new mechanism — it exhausted CodeRabbit's **chat-message** hourly quota
  on top of the review quota, so **it removed the recovery path** and is the direct cause of the
  one-bot merge above. ⚠️ The run knew the correct posture and applied it **asymmetrically**: in the
  same run Sourcery's identical budget notice was filed as a `pr-comment` finding and resolved
  `accepted`. This is neither a corpus gap nor a loading gap. *Owner: none — plan-marshall surface.*
- **Adjacent, unowned:** `cui-http-benchmarking/.../SecurityBenchmarkState.java`:73 carried
  `/api/..;/admin/config` in its `ATTACK_URLS` array while the pipeline accepted that family — the
  repo disagreed with itself. Harmless (both benchmark call sites catch `UrlSecurityException`) and
  now consistent with PLAN-18's fix. Recorded, not actioned. *Owner: none.*

- ⛔ **NO ADR IN THIS REPOSITORY HAS EVER BEEN ACCEPTED — all FOURTEEN are `Proposed`.** Verified at
  HEAD `c631c77`: every one of `0001`…`0014` carries `Proposed`. This **widens and corrects** the
  earlier ledger entry, which tracked only "four ADRs awaiting acceptance". ⛔ **At least three record
  SECURITY positions**, so acceptance is a security decision rather than bookkeeping: **0012** (a
  security preset must never enable `caseSensitiveComparison`), **0013** (`when()`/`identity()` are
  deliberately fail-open) and **0014** (`NormalizationStage` clamps root-consumed dot-segments and
  skips rewriting scheme-bearing input). ⚠️ PLAN-19's landing names the security pair as 0012/0013
  while this ledger had been tracking PLAN-04's pair, which **renumbered to 0013/0014** — the two
  readings diverged because of the renumbering, and both are defensible, so all three are named here
  rather than picking one. *Owner: none — operator call.* *Source: PLAN-19 landing, 2026-09-01.*
- ⛔ **The ADR allocator and the `adr-propose` misdeclaration are now the ONLY remaining half, and
  both are out of reach from this repository.** PLAN-19 closed the cui-http side and recorded the
  constraint in `doc/adr/README.md`, but recording is documentation, not enforcement: there is still
  no ADR-number allocator, and `default:adr-propose` still declares no `mutates_source` (lesson
  `2026-08-27-12-004`, two recurrences, no fix). ⛔ **The concurrency hazard did not fire during
  PLAN-19 only through luck plus one deliberate choice** — neither concurrent plan proposed an ADR,
  and PLAN-19's own `standard` execution posture excludes `adr-propose`, so it could not allocate
  from the sequence it was deduplicating. **A plan running the `full` posture alongside another
  would reproduce the original collision against a now-clean `doc/adr/`.** Needs cross-repo routing
  to the plan-marshall marketplace, not a cui-http plan. *Owner: none.*
- **Any future non-ADR document inside `doc/adr/` must not be `.adoc`.** `manage-adr list`/`scan`
  glob `*.adoc` with no numeric-prefix filter and `parse_adr_file` assigns `number: 0` /
  `title: Unknown` to anything not matching `^(\d+)-`, so a `README.adoc` enumerates as a bogus
  extra record. This forced PLAN-19's deliverable 3 to ship as `README.md`. Recorded so the next
  author does not rediscover it as a broken count. *Owner: none — a standing constraint.*

> ↪ Relocated to `settled.md` § "Resolved: validated redirect following (PLAN-17, PR #186)" — PLAN-17 shipped the loop; the remaining downgrade-assertion gap is its own open defect.

- **WS-05 re-check owed on two documents, and WS-05 is closed.** PLAN-10 changed redirect and TLS
  Javadoc that `doc/client-handlers-readme.adoc` (DOC-5) and `doc/http-result-pattern.adoc` (DOC-9)
  mirror; neither was edited, correctly. Both need re-checking against the merged state — in
  particular any redirect description and any reference to the **five removed TLS symbols**
  (`isSecureTlsVersion`, `FORBIDDEN_TLS_VERSIONS`, `TLS_V1_0`, `TLS_V1_1`, `SSL_V3`, all now at zero
  occurrences in main). ⛔ **DOC-9 already carried an unowned reconciliation debt from PLAN-08**, so
  this is the second landing to add to the same file with no owner. *Owner: none.*
- **Review-bot rate limiting was the dominant cost of the epic's last two plans, not a per-plan
  anomaly.** PLAN-04 lost ~10 of its 21h15m wall clock to it; PLAN-10 took ~7 refusals over ~13
  hours. Folded into lesson `2026-08-27-12-011`, which now carries three recurrences and a corrected
  rule (do not re-trigger mid-window — it RESETS the window; re-trigger once after it elapses).
  Recorded here because the cost is now an epic-level planning fact. *Owner: none.*
- **The scope-creep guard measured nothing for PLAN-10.** `references.json` carried no
  `plan_creation_sha`, so every invocation returned `could_not_look` / `no_baseline_sha`. The plan
  reported this as **absence of evidence rather than a clean result**, which is the correct
  behaviour — recorded so no reader mistakes that step's green for coverage. *Owner: none —
  plan-marshall surface.*

- ⛔ **`default:adr-propose` writes tracked files while declaring no `mutates_source` — RECURRED
  five days after the lesson was filed.** Lesson `2026-08-27-12-004` recorded this exact defect on
  2026-08-27 (ADRs 0004/0005/0006). On 2026-09-01 PLAN-04 hit it again with ADRs 0009/0010: the
  dispatcher's item-5f commit instrumentation reads the declared fact first and skipped its commit
  sub-items, so both `.adoc` files sat uncommitted and would have been **destroyed** when
  `branch-cleanup` removed the worktree. They survived only because the plan committed them by hand
  — twice now, in two different plans. The step still records `outcome: done` with no
  `head_at_completion` and no commit fact. **A filed lesson that produced no fix.**
  *Owner: none — plan-marshall surface.* *Re-check trigger:* any future plan whose finalize proposes
  an ADR.
- **PLAN-04's declared surface understatement is CONFIRMED and now unfixable.** Recorded before
  PLAN-04 launched as "fix at the next cleanup"; the plan has shipped, so there is no spec left to
  correct. The measured outcome: the parser resolved **11** declared paths against a realized
  footprint of **28 files**. The four phantom declared paths
  (`security/pipeline/{monitoring,exceptions,validation,config}/package-info.java`) were never
  touched because they do not exist, while ~20 real paths were touched undeclared — including both
  cross-workstream deliverable-7 files. ⚠️ **The disjointness held anyway, but on hand analysis at
  emit time, not on the declaration**: PLAN-04's only `client/` touch was `RetryConfig.java`, which
  PLAN-10 does not own, and the two-sided PLAN-13 carve-out worked as designed. *Owner: none — kept
  as evidence in the gate's residual-error record.*
- **Two out-of-scope doc residues PLAN-04 observed and correctly did not touch — now unowned.**
  (1) `CLAUDE.md:41` repeats the same absolute-URL overclaim PLAN-04 just corrected in
  `pipeline/package-info.java` — verified present at HEAD. (2) Several `package-info` examples use
  SLF4J-style `{}` placeholders that the CuiLogger standard forbids. Neither file was in PLAN-04's
  declared footprint and WS-05 is closed. *Owner: none.* *Re-check trigger:* operator decision.

- ⛔ **TWO PRODUCTION SECURITY FINDINGS, corroborated at HEAD, UNOWNED.** Surfaced by PLAN-13's
  deliverable-1 payload probe and independently re-verified by the orchestrator at `30edfa3`.
  Both are accepted by the pipeline under `SecurityConfiguration.defaults()`:
  1. `/file:///etc/passwd` is accepted. The mechanism is exact and is **not** a missing pattern:
     `file:` and `/etc/` ARE both in `SecurityDefaults.SUSPICIOUS_PATH_PATTERNS` (lines 124–129),
     but `SecurityConfigurationBuilder.java:102` declares `failOnSuspiciousPatterns = false` and
     `DEFAULT_CONFIGURATION` is a bare `builder().build()` — so the whole gate is **inert by
     default**. ⚠️ **This may be intended design**: the field's own Javadoc reads "Whether
     validation fails on suspicious (**non-attack**) patterns". *Operator call needed before a
     spec is staged.*
  2. `/..;/..;/etc/passwd` is accepted — semicolon-suffixed dot-segment traversal. No pattern
     anywhere in `SecurityDefaults.java` contains `..;` (grep count 0); `PATH_TRAVERSAL_PATTERNS`
     enumerates `../`, `..\` and percent-encoded forms only, and `..;` is not an RFC 3986 dot
     segment so `NormalizationStage` does not resolve it either. ⛔ **This one has no
     by-design defence** — it is the known Tomcat/Spring `..;/` bypass class.
  ✅ **(2) CLOSED 2026-09-01 by PLAN-18 (PR #185, `18c0cc7`).** `NormalizationStage` LAYER 1 now
  carries a fifth, segment-anchored intent pattern `(?:^|[/\\])\.\.;`, placed after `DecodingStage`
  so every encoded spelling folds to one `..;` shape first — scoped by mechanism, not by literal.
  Detection-only, reusing `PATH_TRAVERSAL_DETECTED`. ⛔ **(1) REMAINS OPEN and is an operator
  RULING, not a plan** — `failOnSuspiciousPatterns` defaults `false`, and the field's own Javadoc
  ("suspicious (**non-attack**) patterns") argues that may be intended.
  *Owner: none.* *Re-check trigger:* operator decision on (1) only.
- **PLAN-13's realized surface EXCEEDED its declared `## Expected Surface`, and the gate could
  not see it.** Four undeclared paths were touched: `security/generators/encoding/PathTraversalGeneratorTest.java`,
  `security/generators/.../UnicodeNormalizationAttackGenerator.java`, and three files under
  `security/tests/` (`HomographAttackDatabaseTest`, `IPv6AttackDatabaseTest`, `NfkcFoldClaimInvariantTest`,
  `AttackDatabaseEntries`) — the spec named only two files under `security/tests/`, not the directory.
  **No collision resulted, and that is luck rather than design**: PLAN-11 and PLAN-12 own the
  generators tree and both shipped before PLAN-13 started, so the undeclared overlap had no live
  counterparty. Recorded as evidence for the gate's residual-error record per the
  parallelization-consequence rule. *Owner: none — PLAN-13 is shipped, so there is no spec left
  to correct.* *Re-check trigger:* the next landing — if its realized surface also exceeds its
  declaration, spec-authoring is the systemic gap, not this plan.
- **Test-class inventory drift from PLAN-13 has no owner — PLAN-15 already shipped.** PLAN-13's
  Dependencies section required added/renamed classes be reported for TQ-9/DOC-8. ADDED:
  `NfkcFoldClaimInvariantTest.java`, `AttackDatabaseEntries.java` (shared helper, not a test class).
  REMOVED methods: `EdgeCaseValidURLsDatabaseTest.shouldProcessEdgeCasesEfficiently`,
  `URLPathValidationPipelineTest.shouldRejectPathTraversalAttacks`,
  `PathTraversalGeneratorTest.shouldNfkcFoldUnicodeSignaturesToAsciiTraversal`. The report was
  filed correctly; its consumer is gone. *Owner: none.* *Re-check trigger:* operator decision —
  new spec, or accept the inventory drift.

- ⛔ **The masked failure mode is the serious half of that defect, and it is unaddressed.** Message
  `-010`: the `automatic-review` step reported `outcome=done` while three of its automated stages
  had failed and its finding production had fallen back to manual transcription. A run where the
  same degradation occurs and NO findings are filed is **indistinguishable at the step level from a
  clean review that found nothing**. PLAN-03 survived it only because an operator was present and
  filed the two CodeRabbit findings by hand. This is the same *which-kind-of-zero-is-this*
  discriminator the epic has now hit three times. *Owner: none — plan-marshall surface.*
- **D4's test couples to a private method name, and PLAN-03 asks the epic to rule on it.** Both
  routes the outline proposed assumed a body exceeding `Integer.MAX_VALUE` bytes (~700M chars,
  ~1.5 GB heap), infeasible in a unit test. The executor instead asserts the resolved limit via
  reflection on the private `getMaxLength()` — discriminating old from new exactly, but coupling a
  test to a private member. **This needs an operator standards call, not a code fix**, and it binds
  the rest of WS-01. *Re-check trigger:* PLAN-04 outline.
- ⛔ **REOPENED 2026-09-01 — "RESOLVED" WAS THE WRONG VERDICT.** On 2026-08-31 this was closed on
  the strength of Sourcery reviewing PR #178. **PR #185 (2026-09-01) shows the budget exhausted
  again**: PLAN-18's landing reports Sourcery budget-blocked for that entire run, with two refusal
  notices filed as `pr-comment` findings and resolved `accepted`. The 250,000-diff-char / 7-day budget
  **recovers and re-exhausts cyclically**, so "the budget recovered" was never the same statement as
  "the gap is closed" — a recovery is a window, not a fix.
  ⛔ **A methodological note, because it nearly repeated the epic's own lesson.** `ci pr reviews`
  lists `sourcery-ai` as a reviewer on #178, #185 AND #186, all `COMMENTED` — the reviewer list does
  not discriminate a real review from a budget-refusal notice, so **presence in it is not evidence a
  review happened**. That is exactly lesson `2026-09-01-09-002`. The discriminating evidence here is
  PLAN-18's own first-party report, not the reviewer list.
  ⚠️ Consequence for the epic's record: **"all review bots green" was never true for any PR in this
  epic**, and at least PR #185 merged with genuinely one required bot. *Owner: none — a standing
  budget constraint, not a defect to fix.* *Re-check trigger:* none — assume it will recur.
  Historical record follows.
- ✅ **RESOLVED 2026-08-31 by PLAN-13's landing.** Sourcery reviewed PR #178 (`ci pr reviews --pr-number 178` returns both `coderabbitai` and `sourcery-ai`), so the 7-day budget has recovered and the re-check trigger below — "first plan landing after 2026-08-30 noon" — fired positively. The historical statement stands for the PRs it named: **Sourcery reviewed neither PLAN-03 nor anything landing before ~2026-08-30T12:00Z.** Its 7-day
  250,000-diff-character budget is exhausted. CodeRabbit and pr-agent (both required) did review.
  Wave 2's four remaining emitted plans are all likely to land inside that window, so the same gap
  applies to them. Not a blocker — Sourcery is optional — but it means "all review bots green" is
  not what happened on any of these PRs. *Re-check trigger:* first plan landing after 2026-08-30 noon.

- ⛔ **PLAN-16's landing carried NO `landing-facts` block — its token and wall-clock figures are
  UNRECOVERABLE.** `inbox landing-check` returned `complete: false` with the ENTIRE required set
  missing (`schema`, `plan_id`, `pr`, `merge_state`, `deliverables_total`, `deliverables_done`,
  `total_tokens`, `steps`). The PR and merge commit appear in PROSE only. This is the pre-fix
  prose-only landing shape, and it is a REGRESSION against PLAN-03, which shipped a complete block
  hours earlier from the same epic. The landing was reconciled anyway — from prose plus independent
  corroboration — but PLAN-16 is now the only shipped plan in this epic with no recorded cost.
  ⛔ Its prose `files_changed: 13` also contradicts BOTH git (12 files at `9f26433`) and the
  landing's own "Surface touched" list (12 paths). Immaterial to the outcome; recorded because an
  uncorroborated count is exactly what the verify-first contract exists to catch.
  ⛔ **RETRACTED 2026-09-01 — the "isolated regression" verdict was WRONG, and the emitter is
  INTERMITTENT.** The 2026-08-31 re-check fired negative on PLAN-13's complete block and the
  orchestrator recorded "the emitter is NOT broken, PLAN-16 was an isolated regression". PLAN-04's
  landing was also complete, which appeared to confirm it. **PLAN-10's landing then carried NO
  `landing-facts` block at all** — `complete: false` with the entire required set of 8 keys missing,
  the same pre-fix prose-only shape as PLAN-16. The tally is now **two complete (PLAN-13 #178,
  PLAN-04 #180) against two prose-only (PLAN-16, PLAN-10 #182)**. The emitter is therefore neither
  broken nor sound but **intermittent**, and one sample was never enough to settle it — two
  consecutive successes read as a fix and were not. ⛔ Both PLAN-16's figures remain permanently
  unrecoverable; PLAN-10's survived only because its prose happened to carry them.
  *Re-check trigger:* every future landing — the discriminating question is what makes the block
  appear, not whether it does.
> ↪ Relocated to `settled.md` § "Superseded: the PLAN-09 Sonar fold" — subject closed.
- ⚠️ **BREAKING: `-Pquick` no longer exists — the benchmark profile is `-Psmoke`.** Renamed by
  PLAN-16 (BB-7); timings unchanged in substance, but any script, alias, or habit using `-Pquick`
  now fails. `CLAUDE.md` and `.plan/marshal.json` are updated at HEAD. *Re-check trigger:* none —
  stated so it is not rediscovered as a build failure.
- **`pre-submission-self-review` has a return path that skips `mark-step-done` (msg `-005`).**
  The leaf returned `status: success` without recording a terminal outcome; the post-dispatch guard
  (`assert-step-recorded --require-terminal`) caught it. The guard is working correctly — the defect
  is in the step's workflow body. *Owner: none — plan-marshall surface.*
- **`archive-plan` was skipped for PLAN-01 only — NARROWED, not repo-wide.** PLAN-14's landing shows
  it running correctly (`.plan/local/archived-plans/2026-08-26-plan-14-doc-overclaim-correction`), so
  the earlier "has never run for any plan" wording is superseded: PLAN-01 is the single miss.
  Consequence stands for PLAN-01 alone — its plan-store directory went with its worktree and its
  metrics are unrecoverable; only `deliverable-hashes.toon` survived under `.plan/temp/`.
  *Source: PLAN-01 landing analysis, narrowed by PLAN-14 landing, 2026-08-26.*
> ↪ Relocated to `settled.md` § "Barrier lifted 2026-08-27: eight plans landed and drained" — subject closed.
- ⛔ **PLAN-08's headline fix nearly shipped UNREACHABLE — and it is the counter-example to the
  bot-review pattern.** D6's cache-key optimization made `cachedEntry` always null for unsafe
  methods, making D2's method-gated 304 unreachable; the test passed via the generic error path **by
  coincidence**. **Five local gates cleared it; two review bots caught it.** Both this and the
  three-degraded-landings pattern now stand on the record; neither cancels the other. Second and
  sharper conclusion: **the outline had flagged the exact D6/D2 interaction as a risk before
  execution and it materialised anyway** — a predicted risk is not a mitigated one without a test
  that can falsify it.
- ⛔ **TQ-17 was absorbed by PLAN-11 and never delivered — caught at landing, returned to PLAN-12.**
  PLAN-11 took TQ-15/16/17 from PLAN-12 by operator decision, shipped TQ-15 and TQ-16, and left
  TQ-17 untouched: the seed-invariant `AttackTypeSelector` (`private int currentType`, advanced
  modulo `maxTypes`) survives verbatim at `8a0aa4c`, in a file PLAN-11 edited +118/−75. Its landing
  message does not mention TQ-17 at all. **This is the failure mode a scope transfer creates** — a
  finding moved out of one spec and not done in the other would have been silently lost, since
  PLAN-12's spec had already been amended to disown it. Returned as PLAN-12 deliverable 1; both
  specs record the transfer; coverage re-verified at 117/117. *Source: PLAN-11 landing, 2026-08-27.*
- ⛔ **Bot review is not a reliable gate in this epic — three of eight landings shipped degraded.**
  #154 (churn exceeded both bots' file limits), #159 (CodeRabbit reviewed as a comment, not a review
  submission, so the checker read `participated_stale`), #164 (CodeRabbit's hourly quota spent before
  the last two commits). Individually each is defensible; the pattern is that CI and Sonar are
  carrying the gate. PR-Agent's re-review trigger is additionally **broken for merge-queue plans** —
  it was made optional for PLAN-11 only, `marshal.json` untouched, so every remaining merge-queue
  plan hits the same block. A standing operator decision, not a per-plan one.
- ⛔ **Coverage is UNMEASURABLE in this project, and every plan's 80% claim is unverified.**
  `verify:coverage` does not resolve in this architecture and records `skipped` every phase, while
  CLAUDE.md mandates a minimum 80% with 100% on critical paths. Tests do run — PLAN-06 landed 5583
  green — but no coverage figure has been produced for any plan in this epic. No staged spec owns
  the build's coverage configuration; PLAN-16 owns the poms and is the natural home if it is fixed.
  *Source: PLAN-06 landing, 2026-08-27.*
> ↪ Relocated to `settled.md` § "Resolved: duplicate ADR numbers, cui-http half (PLAN-19, PR #184)" — the four collisions are gone from main; the upstream allocator half is its own open defect.

- ⛔ **`doc/http-result-pattern.adoc` reconciliation is owed and UNOWNED — a sequencing miss in this
  epic's own decomposition.** PLAN-08's conversion-failure results now carry status and ETag where
  both were empty. Its spec correctly said "do not edit it here; report it in the landing so PLAN-15
  reconciles the doc" — but **PLAN-15 shipped first** and WS-05 is closed. A doc plan was allowed to
  close before the code plan whose output it documents had landed. *Source: PLAN-08 landing.*
- ⛔ **Two residual doc items have NO owner — WS-05 is closed.** Both are outside the 117 report
  findings, surfaced by PLAN-15, and deliberately left rather than absorbed. Staging a spec for
  either is an operator decision:
  1. **`compliance-traceability.adoc` still carries per-section test-CASE counts** ("200+ test
     cases", "500+ test cases", "64+ patterns") and line-number references. Same staleness class as
     DOC-8. PLAN-15 scoped deliverable 1 to test-CLASS counts and declined to widen the diff —
     correct restraint, and the item is real.
  2. **No link checker exists in this repository.** `pom.xml` configures no AsciiDoc processing and
     the Maven workflow skips doc-only changes, so a broken xref is decidable but undetected.
     ADR-0005 names adding one as its follow-on. This is what let the ADR overclaim above survive
     authoring.
- **PR #154's bot review coverage was DEGRADED, and it shipped two security HIGHs.** Three
  bot-authored parent bumps (#144, #145, #146) each changed one line and skipped the quality gate,
  leaving `main` non-compliant with its own parent's license-header template. PR #154 paid that debt
  — 217 of its 240 files were pure header churn — and the inherited diff exceeded CodeRabbit's
  100-file limit and Sourcery's threshold. Verified: CodeRabbit reports `Review rate limited`
  (pass, 0s, no substantive review) and `Sourcery review` reports `skipping` on #154. **The live
  risk is spent** — PLAN-08 and PLAN-11 both branched FROM `2a86a59` and carry 5 and 22 files
  respectively, so no running plan inherits the churn. What remains is that SV-1 and SV-2, two HIGH
  security findings, merged without effective bot review. *Source: PLAN-05 landing, corroborated
  against PR #154's own check state, 2026-08-26.*
- **PLAN-05's landing message carried NO `landing-facts` block.** `inbox landing-check` reports all
  8 required keys missing — a pre-fix prose-only landing. Its prose was richer than PLAN-14's (which
  WAS complete), so the reconciliation succeeded regardless; the defect is that the machine-readable
  channel supplied nothing. Two of three landings drained so far were incomplete. *Source: inbox
  drain, 2026-08-26.*
- **PLAN-01's inbox landing message was INCOMPLETE.** `inbox landing-check` reports `complete: false`,
  missing `deliverables_total`, `deliverables_done` and `total_tokens`. The operator's paste supplied
  the deliverable figures and `.plan/temp/deliverable-hashes.toon` settled them at 6/6, so nothing is
  outstanding in practice — but the inbox channel alone would NOT have reconciled that landing fully.
  PLAN-14's landing, by contrast, was `complete: true`. *Source: inbox drain, 2026-08-26.*
- ⛔ **RETRACTED, 2026-08-26 — "three other plan directories are deleted" and "PLAN-11's state is
  unknown" were BOTH FALSE, and are withdrawn.** The orchestrator ran `ls .plan/local/plans/` in the
  MAIN checkout only and read absence there as deletion. **The plans store is NOT main-anchored —
  each worktree carries its own `.plan/local/plans/{plan}`.** Ground truth: PLAN-05 and PLAN-11 are
  alive in their worktrees, PLAN-14 is alive in the main checkout (its worktree was removed after
  its PR was raised — normal post-push cleanup), and PLAN-11 has an **open PR #156** plus a live
  worktree at `3fee3b9` with a clean status. No `plan_marshall_plan_id` is dangling except
  PLAN-01's, which is expected for a shipped plan. ⛔ **Do not re-derive either claim.** The lookup
  rule to use instead: a running plan's directory lives in ITS WORKTREE, not in main.
- **PLAN-01 wrote outside its declared Expected Surface** — four `doc/http-security/**` files, which
  is WS-05's tree. The edits are correct and consequential (they document the stage-set change), and
  they caused no conflict: PR #153 is file-disjoint and reports MERGEABLE/CLEAN. Recorded because
  the disjointness check did not predict it, not because harm resulted.
  *Source: PLAN-01 landing analysis, 2026-08-26.*

## Watches

- **Report-to-HEAD drift.** The reports describe commit `7ae6499`; HEAD is now `2a86a59`. Any plan
  whose outline pass refutes a premise must loop back and re-scope rather than proceed.
  *Re-check trigger:* every landing analysis — record refuted premises as epic decisions so later
  plans do not re-derive them.
- ⛔ **The quality gate did not run for PLAN-14, and the PR was not docs-only.** `build-decision`
  returned `not_necessary` (footprint touches no `build_map` glob), so zero bundles derived and no
  arm executed — nothing was locally compiled, linted, or tested. Defensible for prose, but the PR
  also added `.github/workflows/pr-agent.yml`, which no local arm validated either. Remote CI was the
  sole gate. The agent recorded it *skipped* rather than *done* and refused the standard's "green"
  wording, which is the correct behaviour and why this is a watch rather than a defect.
  *Re-check trigger:* any future doc-plan landing — confirm whether a non-doc file rode along.
- **DOC-6 residue: two CVEs are substantiated by nothing in the repo.** CVE-2020-5410 and
  CVE-2019-0232 are now honestly labelled "cited as a representative traversal class; not
  individually encoded in the attack databases", so the DOCUMENTATION finding is closed. The
  COVERAGE gap is real, was not covered by any of the 117 findings, and would need a new spec to
  close. *Re-check trigger:* operator decision on whether to encode them.
- **`pr-agent.yml` now exists — PLAN-16 must treat it as pre-existing.** PLAN-14's PR added a whole
  new review-bot workflow (+36 lines) outside its declared surface, with operator approval. It does
  not collide with PLAN-16's `benchmark.yml` / `maven.yml` / `release.yml`, but PLAN-16's BB-10
  "leftover template comments" sweep should not be surprised by a fourth workflow file.
  *Re-check trigger:* PLAN-16 outline.
- **Lookup rule, learned the hard way:** the plans store is per-worktree, not main-anchored. To
  find a RUNNING plan's directory, look in `.plan/local/worktrees/{plan}/.plan/local/plans/{plan}`,
  or use `git worktree list`. Checking only the main checkout reports a live plan as deleted.
  *Re-check trigger:* any future landing analysis that asks where a plan's artifacts are.
- ⛔ **PLAN-01 moved the ground under FW-11 and DOC-4 — both must RE-VERIFY, not re-derive.** The
  HEADER_VALUE stage set is now `Length → Character`; `NormalizationStage` and `PatternMatchingStage`
  were REMOVED from `HTTPHeaderValidationPipeline`, not merely re-documented. Both findings are
  owned by PLAN-07. **DOC-4 is NOT refuted** — no `DecodingStage` was added and `AllowBlockListStage`
  stays HEADER_NAME-only, so the inert-knobs premise still holds — but the stage list its wording
  describes has changed. *Re-check trigger:* PLAN-07 outline, against `2a86a59`.
- **PLAN-14 must not re-document what PLAN-01 already landed.** `configuration.adoc`,
  `specification.adoc` and `http1-vulnerabilities-analysis.adoc` now carry the pattern-stage scope
  note, the header-pipeline composition rationale, and the header-smuggling note. PLAN-14's four
  files are disjoint from these, so the instruction is simply: do not widen into them.
  *Re-check trigger:* PR #153 review.
- **`functional-requirements.adoc` is a requirements document and PLAN-01 edited a requirement.** The
  header-characters bullet was split into names (RFC 7230 tchar) and values (visible ASCII + tab).
  PLAN-15 owns DOC-10 and DOC-7 in the same file but different rows — no double-work, but PLAN-15
  must read the file at HEAD rather than from the report's quotation. *Re-check trigger:* PLAN-15 outline.
- **Cross-report shared findings.** Three findings are reported twice under different IDs
  (SV-15 = CL-16 = BB-5; TQ-9 = DOC-8; SV-6b = SV-12). Each is owned by exactly ONE plan.
  *Re-check trigger:* if a landing reports fixing a shared finding from the non-owning plan,
  reconcile the duplicate before the owning plan launches.
- **BB-8 needs an operator answer, not a code fix.** Whether `cui-http-benchmarking` should ship
  to Maven Central is a product decision the report flags for confirmation.
  *Re-check trigger:* PLAN-16 outline — escalate to the operator rather than deciding it in-plan.
- ⛔ **EXTERNAL DEPENDENCY, step 1 of 4 now DONE: `cui-test-mockwebserver-junit5` 1.6 is released.**
  Finding `36b80c` (PLAN-08's only deferral) parked the PATCH/OPTIONS rows of the 304 matrix because
  1.5.0 declares no PATCH/OPTIONS `HttpMethodMapper` constants and no `handlePatch`/`handleOptions`
  hooks, so `TestApiDispatcher` cannot serve those methods. Upstream PR #104 has landed: Maven
  Central carries `de.cuioss.test:cui-test-mockwebserver-junit5:1.6` as of 2026-08-29T10:32Z.
  ⛔ **This watch was logged to `logs/decision.log` on 2026-08-27 and recorded in
  `landings/PLAN-08.md`, but was never written into this section — it was invisible at resume until
  the operator raised it on 2026-08-29. Landing-analysis gap, not a re-derivation.**
  **The consumption chain the operator chose has three steps left, and two are in ANOTHER repository:**
  1. ✅ release `cui-test-mockwebserver-junit5` 1.6 — DONE (note: coordinate is `1.6`, not `1.6.0`).
  2. ⬜ bump `version.cui.test.mockwebserver` in `cuioss-parent-pom` — **outside this epic's write boundary.**
  3. ⬜ release that parent, then bump `cui-java-parent` here from `1.5.9`.
  4. ⬜ add the two deferred PATCH/OPTIONS test rows.
  Verified at `f1ba539`: `cui-java-parent`/`cui-java-bom` latest release is `1.5.9`, whose
  `version.cui.test.mockwebserver` is still `1.5.0`, and `cui-http-core/pom.xml` declares the
  dependency with NO local `<version>` — so cui-http resolves 1.5.0 today and step 4 is BLOCKED on
  steps 2–3 unless the operator accepts a local version override instead.
  ⛔ **Production enforcement is NOT affected** — the unsafe-method branch is a catch-all on
  `!canReadCache(method)`, so PATCH and OPTIONS already take the RFC 7232 failure path.
  Only test coverage waits; DELETE/PUT/POST cover the unsafe-method equivalence class meanwhile.
  *Re-check trigger:* operator decision on step 2's owner and on where step 4's rows land — no
  staged spec owns them today (PLAN-09 owns the adapter/retry surface but its spec does not carry
  this deliverable, and PLAN-08 is shipped).
- **Enumerate every Lombok generator on a type, not the one you thought of (from PLAN-03, msg `-008`).**
  D5's new `config` field was suppressed with `@Getter(AccessLevel.NONE)` after TWO outline-phase
  findings raised exactly that concern — and the SAME field then leaked through `@ToString` on the
  same five classes, caught by CodeRabbit on the PR. `@Getter(AccessLevel.NONE)` disables ONE
  generator; `@ToString`, `@EqualsAndHashCode`, `@Builder`, `@Data`, `@With` all keep consuming the
  field. Each suppression needs its own pinning test. ⛔ **PLAN-04 edits Javadoc on those same five
  pipeline classes** — read the annotations as they now stand at `e2c7ebd`, not as the report
  describes them. *Re-check trigger:* PLAN-04 outline.
- **Escaped-delimiter-at-the-boundary is a mandatory test in both directions (from PLAN-03, msg `-007`).**
  The SV-8 fix itself introduced a doc-vs-behaviour divergence: `unquote()` treated every trailing
  quote as a closing delimiter, so `name="abc\"` returned `abc\` while the method's Javadoc promised
  unbalanced values are returned unchanged. The parity of the escape run — not the presence of an
  escape character — decides. A suite exercising escapes only in a value's interior passes while the
  boundary is wrong. Fixed in `3313a97`. *Re-check trigger:* any WS-01/WS-04 work on a
  delimiter-stripping routine.
- **`scope_estimate` band names module counts but measures distinct path counts (from PLAN-03, msg `-005`).**
  PLAN-03 was labelled `multi_module` from an 11-file, 5-package spread resolving to ONE module
  (`cui-http/cui-http-core`); no second Maven module is touched anywhere. The plan explicitly took
  no position and referred the judgement here. ⛔ **This epic cannot fix it** — it is a plan-marshall
  band-vocabulary question. Recorded so the label is not read as evidence of cross-module scope on
  any cui-http plan. *Re-check trigger:* none; carry to a plan-marshall lessons pass.
- **Two review-cycle catches show the epic's own defect class being REINTRODUCED by its fixes.**
  PLAN-03 shipped three defects found only at review: the escaped-final-quote bug inside the SV-8
  fix, the `@ToString` leak of a field the operator had already corrected once, and Sonar's S127.
  All three are "code diverges from its own documented contract" — precisely what this epic exists
  to close. *Re-check trigger:* every WS-01 landing — if a fix introduces a fresh contract
  divergence, record it here rather than only in the PR.
> ↪ Relocated to `settled.md` § "Retired: PLAN-16 merged-but-staged watch" — subject closed.
- ⛔ **All 25 re-grounding verdicts are now STALE — every one was checked at `f1ba539`, and HEAD is
  `9f26433`, two epic-landing commits later.** Staleness is reported, never promoted, so every
  candidate still admits (`blocking_count: 0`). But the staleness is NOT uniform in consequence:
  **PLAN-04's four verdicts are the ones that matter**, because PLAN-03 (`e2c7ebd`) rewrote exactly
  PLAN-04's surface — the five pipeline classes' annotations, `SecurityEventCounter`'s Javadoc (four
  claims corrected, where the report named three), `AttributeParser`, `Cookie`, and
  `SecurityDefaults`. PLAN-04's claim 6 was ALREADY `contradicted`/`rescoped: yes` before this.
  ⛔ **PLAN-04 must re-read its targets at `9f26433`, not at `f1ba539` and not from the report's
  quotations.** *Re-check trigger:* PLAN-04 outline — this is the plan whose premises moved most.
- ⛔ **A completeness sweep verified only by `architecture search --content` is structurally blind
  to `.github/**` and `.plan/**` (from PLAN-16, msg `-001`).** The `quick`→`smoke` rename reported
  clean on a zero-hit search while stale references survived in two workflow files AND
  `.plan/marshal.json`. The inventory does not walk those trees, so the search COULD NOT have found
  them — a clean result was indistinguishable from a complete one. Caught only by a `Grep` fallback
  and by CodeRabbit. **Any rename / key-migration / deprecation sweep in this epic must add a raw
  `Grep` pass over the excluded trees before claiming completeness.** Same which-kind-of-zero
  discriminator the epic has now hit four times. *Re-check trigger:* any sweep-class deliverable.
- ⛔ **A predicate documented `fail-closed` was fail-OPEN for exactly the input class it promised to
  reject (from PLAN-16, msg `-002`).** `_is_post_cutoff` split on the last hyphen and compared the
  trailing segment, so `zz-bad.json` sorted past the cutoff and would have entered trend history.
  Both halves read as correct in isolation — the docstring states the contract, the split-and-compare
  looks reasonable — and nothing asserted the input WAS the shape the comparison assumed. **Validate
  the whole shape first (anchored match), then read fields from the validated value.** Directly
  relevant to this epic's validation-pipeline work. *Re-check trigger:* WS-01 and WS-04 predicate work.
- ⛔ **Verify a prescribed fix against the RESOLVED effective configuration, not the task text (from
  PLAN-16, msg `-003`).** BB-8's prescribed `maven.deploy.skip` was inert: publication runs through
  `central-publishing-maven-plugin` (`skipPublishing`), and the parent already set the deploy-plugin
  skip globally. The fix would have passed review and the build while changing nothing. A prescribed
  mechanism is a HYPOTHESIS about how the system works, written from the declared view. *Re-check
  trigger:* any plan touching pom, workflow, or layered-config surfaces — read the effective view.
- **Benchmark baselining: use `534d111`, never `7ae6499`, never a pre-merge point of #170 (from
  PLAN-16).** The published gh-pages trend series is **discontinuous at this landing by design** —
  `benchmark-pages.py` actively purges the pre-fix points as incomparable. The captured baseline
  artifact is at the archived plan's `work/baseline-534d1119.json`. BB-1's headline
  (43.3K → 539.0K ops/s, ~12.4x) is recorded **as reported**: re-running JMH is outside the
  orchestrator's carve-out, so it is a lead, not a corroborated fact. *Re-check trigger:* any future
  benchmark comparison in this repo.
- ⛔ **THE FOLD OF THE 7 SONAR FINDINGS NEVER REACHED THE RUNNING PLAN — deliverable 7 is spec-only,
  and the findings are still unowned in reality.** Timeline from disk, verified 2026-08-29:
  PLAN-09's `request.md` was written at **12:05**; the orchestrator edited
  `plans/PLAN-09-client-config-and-retry-hardening.md` to add deliverable 7 at **18:26** — six hours
  after `phase-1-init` had already ingested the spec as the request body. `grep -ci 'sonar|S1845'`
  on the running plan's request returns **0**. ⛔ **The spec and the running plan therefore
  DISAGREE**, and the spec is the one that is wrong about what is being built.
  **The operator's fold decision must be re-taken against this fact.** Three routes, none yet chosen:
  (1) let PLAN-09 finish its original six deliverables and handle the 7 findings separately — a new
  spec, or folds at their native owners; (2) inject deliverable 7 into the running plan, which is
  past phase 5 with commits pushed, so it means a loop-back that re-opens verification on five
  foreign files; (3) revert the spec edit and route to native owners as first recommended.
  ⛔ **Note `java:S1845` (BLOCKER, `ForwardedHeaderResolver.java:646`) is on PLAN-07's surface and
  PLAN-07 is STILL RUNNING** — it can still be caught there before that plan lands.
  *Re-check trigger:* operator decision; and PLAN-09's landing, which must NOT be read as having
  closed any Sonar finding.
- ⛔ **ORCHESTRATOR MISS, recorded against itself: three plans ran for six hours while the ledger
  called them `staged`.** `git worktree list` shows PLAN-07, PLAN-09 and PLAN-12 all live since
  12:02–12:06, all at `6-finalize`, with 6 / 3 / 6 commits pushed. Every `next`-verb computation in
  that window used **R=0 when the truth was R=3**, so the wave-2 emit, the re-emit, and the
  "sequence PLAN-04/07/12 behind PLAN-09" instruction were all computed against a false queue —
  and that last instruction was never actionable, because those plans were already executing.
  ⛔ **Cause: the queue was read and `git worktree list` was not** — the epic's own
  "plans store is per-worktree, not main-anchored" Watch names this exact failure and was not
  applied. **Standing correction: `next` and `analyze` MUST read `git worktree list` before trusting
  any `staged` row.** All three rows are now reconciled to `running` with their
  `plan_marshall_plan_id` stamped from ground truth. *Re-check trigger:* every `next` and `analyze`.
> ↪ Relocated to `settled.md` § "Resolved: the 7 Sonar findings found an owner" — subject closed.
- ⛔ **STANDING RULE, learned twice in one day: a finding is folded into a STAGED spec, never into a
  running plan.** A running plan has already ingested its request at `phase-1-init`; a later spec
  edit cannot reach it, and past phase 5 a new deliverable means a loop-back. Both failure modes
  occurred here. The precondition for any fold is therefore `git worktree list` plus the queue row —
  **not the queue row alone**, which is what made the first fold look legitimate.
> ↪ Relocated to `settled.md` § "Resolved: the cui-test-mockwebserver-junit5 chain" — subject closed.
- ⛔ **PLAN-07 shipped with `emit-landing` at `lane: off` — no landing message exists or ever will.**
  The epic received its 7 candidate-lesson messages and NO `kind: landing` message. PLAN-07 was
  reconciled from an operator paste plus ground truth instead. ⛔ **This is now the THIRD distinct
  landing-channel failure in one day**: PLAN-16 emitted a landing with no `landing-facts` block,
  PLAN-07 emitted no landing at all, and PLAN-12 (#171, `382327a`) has so far emitted nothing
  either. **A drained-empty inbox therefore does NOT mean every plan has reported** — the drain can
  only see what was written. *Re-check trigger:* every `analyze` — cross-check `git log` for merged
  plan PRs before concluding the queue is complete.
- ⛔ **PLAN-07's merge rested on a participation verdict that was never machine-checked.**
  `review_completeness` was **argparse-rejected on all four review firings**, so every
  `participation_complete` value in the run — including the one merged on — was narrative, not
  classifier output, and the failure records truncated the cause away. The plan verified both bots'
  comments named `7f9b9d4` by hand, so the merge is evidenced; the guard simply never ran. ⛔ A
  workflow whose own exit-code convention forbids swallowing argparse rejections swallowed four.
  *Owner: none — plan-marshall surface.*
- ⛔ **`scope_creep_check` never ran for the whole of PLAN-07, and the run recorded it as clean.**
  `no_baseline_sha` — `references.json` carries no `plan_creation_sha`. It degraded **silently**,
  unlike TASK-013's empty verification block, which degraded loudly and fell back to
  `architecture resolve`. Neither is a clean result. Fifth instance of the epic's recurring
  *which-kind-of-zero-is-this* pattern. *Owner: none — plan-marshall surface.*
- **Two review-bot mechanics worth carrying (PLAN-07 msgs `-003`, `-004`).** A bot's completion
  signal can be an **in-place EDIT of an existing comment**, not a new object — this produced both a
  false ABSENT and a near-false PRESENT in one run; an edit naming the current HEAD's commit range
  IS participation, a resolve/acknowledgement annotation is not. And **pr-agent never re-reviews on
  push** while **CodeRabbit refuses to re-review already-reviewed commits**, so waiting for a
  re-review that no event will trigger — then retrying the refusal — cost three loop-back
  iterations and quota. *Re-check trigger:* PLAN-09's finalize, and every later plan's.
- ⛔ **PLAN-12 left the epic's OWN defect class live on main — fix is IN FLIGHT (operator instructed
  the plan to open the one-line PR, 2026-08-29).** `PathTraversalGenerator.java:29-30` still reads
  "U+2024 ONE DOT LEADER, U+2215 DIVISION SLASH, U+FF3C … which NFKC-normalize to the ASCII
  traversal". **Corroborated exactly at `382327a`:** `U+2215` appears ONLY in that Javadoc — the
  constants use `U+FF0F` FULLWIDTH SOLIDUS (line 46) — and line 57 states explicitly that one
  constant does NOT NFKC-normalize. Both halves of the claim are false. CodeRabbit caught the
  original; nothing in the plan's own pipeline did. ⛔ **This is the THIRD time a plan built to close
  doc-vs-code divergences has introduced one** (after PLAN-03's escaped-final-quote bug and its
  `@ToString` leak). *Re-check trigger:* confirm the follow-up PR merged; if it did not, this
  reverts to unowned.
- ⛔ **ROOT CAUSE of the above, and it generalises (PLAN-12 msg `-005`): the deliverable scoped doc
  edits by LOCATION, not by SYMBOL.** The constant was fixed and asserted; the prose one line up was
  not, and **the new assertion structurally cannot catch it — it iterates signature data while the
  stale claim is prose.** Deleted-symbol residue survives any sweep scoped to locations.
  **Scope doc edits by symbol, and never let a data-driven assertion stand in for a prose check.**
  *Re-check trigger:* PLAN-04 — it is a documentation-accuracy sweep over a package PLAN-03 just
  rewrote, which is exactly this failure's setup.
- ⛔ **Seven build-error findings auto-resolved "by green build" from a run that executed ZERO tests
  (PLAN-12 msg `-008`).** A green with no tests is not evidence of anything. **Sixth** instance of
  this epic's recurring *which-kind-of-zero-is-this* pattern, and the most consequential of them.
  *Owner: none — plan-marshall surface.*
- ⛔ **Two of the last three landings were INCOMPLETE, for different reasons — the `emit-landing` fix
  does not close this.** PLAN-16: emitted a landing with no `landing-facts` block. PLAN-07:
  `lane: off`, no landing at all. PLAN-12: landing emitted, **still no facts block**
  (`complete: false`, all 8 required keys missing). PR #174 (`898024b`) set the lane to `minimal`,
  which governs whether a landing is EMITTED — not whether it carries the block. PLAN-12 merged at
  `382327a`, before that fix. ⛔ **Metrics for PLAN-12 and PLAN-16 exist only in operator pastes.**
  *Re-check trigger:* PLAN-09's landing — the first to finalize entirely after `898024b`. If it also
  lacks the facts block, the emitter is broken and the lane setting was never the problem.
> ↪ Relocated to `settled.md` § "Resolved: the gh auth probe failure" — subject closed.
> ↪ Relocated to `settled.md` § "Merged: PR #175 and its U+2044 residue" — subject closed.
- ⛔ **THE DEFECT CLASS ESCAPED THREE TIMES BECAUSE EACH SWEEP WAS SCOPED TO THE WRONG THING.**
  PLAN-12 scoped by **LOCATION** — fixed the constant, missed the prose one line above it.
  PR #175 scoped by **CODEPOINT** — searched `2215`, so `2044` was structurally invisible to it.
  ⛔ **The invariant that needs sweeping is the CLAIM: "X NFKC-folds to ASCII".** Only an EXECUTABLE
  assertion can hold it — #171's assertion iterates signature data and cannot read prose, which is
  exactly why it could not catch a false claim written one line above the constant it checked. The
  durable form is `assert NFKC(cp) != cp` for every codepoint a comment claims folds; it would have
  caught all three rounds. Now the second half of PLAN-13 deliverable 7.
  *Re-check trigger:* PLAN-04 — a doc-accuracy sweep over a package PLAN-03 just rewrote, i.e. the
  same setup; it must not be scoped by location or by token either.
- ⚠️ **`skip-bot-review` removed the one control that had actually worked on this defect class.**
  #175 landed with **no CodeRabbit review** (`review / review` skipped, as designed) — and CodeRabbit
  is what caught the original defect in #171 that nothing in PLAN-12's own pipeline found. The label
  is right for a scoped follow-up, but on a PR fixing a class the bots have a demonstrated record of
  catching it is not free. ⛔ **The U+2044 gap then survived that unreviewed PR.**
  *Re-check trigger:* any future `skip-bot-review` on a correctness fix rather than a config change.- ✅ **The landing-facts gap looks CLOSED — PLAN-09's landing is `complete: true`.** It is the first
  plan to finalize entirely after the `emit-landing` fix (`898024b`) and the first complete landing
  since PLAN-03: every required fact key supplied, `missing_keys[0]`. Its metrics are therefore
  recorded from the ledger rather than from an operator paste, unlike PLAN-12's and PLAN-16's.
  *Re-check trigger:* PLAN-04's landing — one complete landing is evidence, not proof.
- ⛔ **PLAN-09 shipped a bug INSIDE the guard its deliverable existed to add, and only the bot caught
  it.** CL-7's compact constructor used ordered comparisons — and **every ordered comparison against
  NaN is false** — so NaN passed all five guards, reached `calculateDelay()`, and reproduced the
  exact hot-retry loop D1 was written to prevent. The hole was at **three** seams, not the two the
  comment named. ⛔ **The outline, the Q-Gate AND the self-review all missed it**; CodeRabbit found
  it. Fixed in `482dd1d`; verified at `6ee0c7c` (`!Double.isFinite(...)` at `RetryConfig.java:132`
  and `:135`, mirrored in the builders at 272/310). **Durable rule: validate doubles with
  `Double.isFinite`, never with ordered bounds — a range check that reads correctly is still open at
  the NaN seam.** *Re-check trigger:* any remaining numeric-invariant work in WS-01 or WS-03.
- ⛔ **~3 hours and ALL THREE loop-back iterations were lost to a non-problem.** CodeRabbit's quota
  had reset hours earlier; its refusal comment was simply never updated, and nothing re-triggered the
  bot because HEAD had not moved. ⛔ **Waiting could not have fixed it** — an explicit
  `@coderabbitai review` produced a review in 8 minutes. This is the PLAN-07 msg `-003`/`-004` watch
  recurring with a measured cost: **a bot's stale comment is not a live verdict, and two of three
  bots here need explicit triggers.** Compounded by a second defect (PLAN-09 msg `-002`): **a
  zero-finding loop-back iteration at an unchanged HEAD consumed the loop-back ceiling** — nothing
  had changed, so nothing could be found, yet the iteration counted. *Owner: none — plan-marshall.*
- ⛔ **NEW unowned doc residue: `doc/client-handlers-readme.adoc` has no `close()`/`AutoCloseable`/
  cancellation coverage.** Created by PLAN-09 deliverable 5, which made `HttpHandler` `AutoCloseable`
  with interrupt-aware blocking. ⛔ **WS-05 is CLOSED and both its plans have shipped, so this has no
  owner** — it joins the existing unowned-doc-residue set, now four items. *Operator decision:* fold
  into a staged plan or accept the drift.

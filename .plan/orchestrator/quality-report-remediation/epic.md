# Epic: cui-http Quality Report Remediation

slug: quality-report-remediation

> Ledger document for one epic under `.plan/local/orchestrator/quality-report-remediation/`.
> The layout and authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. `status.json` is the
> machine authority; any statement here that conflicts with it is stale prose.

## Vision

A full independent quality review of `cui-http` — production code, tests, documentation, build
and CI — produced 126 findings (0 Critical, 5 High, 47 Medium, 59 Low, 15 Info; about 110
distinct defects once duplicates are counted once), each carrying `path:line` evidence and each
re-verified end to end by an adversarial second reviewer. This epic drives every one of those
findings to a **deliverable or a recorded refutation**. It is far too large for one plan: the
findings span seven surfaces that must not be edited concurrently — the security validation
core, the content-type/cookie/collection surfaces, the forwarded-header trust model, the HTTP
client, the security test framework, the documentation set, and the build/CI/benchmark
infrastructure.

Done at the epic level means: every finding id from the report appears in exactly one landed
plan, either as a fix that ships with a regression test written test-first, or as a refutation
recorded in the ledger with the ground-truth evidence that refutes it. No finding is silently
dropped, and no finding is closed on the report's own say-so — every claim is settled against
the implementing source.

## Source Corpus

The review report is the epic's input and lives **inside this tree**, deliberately outside
version control (it was removed from `doc/quality-report/` by PR #207):

```text
.plan/local/orchestrator/quality-report-remediation/quality-report/
├── README.adoc                        # scope, method, baseline metrics, severity scale, executive summary
├── 01-security-validation.adoc        # L-1..L-13, V-1, F-A-1..F-A-14, F-B-1..F-B-16   (44 items)
├── 02-forwarded-header-resolution.adoc# F-D-1..F-D-13, F-D-19, F-D-20                  (15 items)
├── 03-http-client.adoc                # F-client-1..F-client-18                        (18 items)
├── 04-test-framework.adoc             # F-E-1..F-E-16                                  (16 items)
├── 05-documentation.adoc              # F-documentation-1..F-documentation-28          (28 items)
├── 06-build-and-infrastructure.adoc   # F-D-14..F-D-18                                 (5 items)
└── 07-adversarial-verification.adoc   # independent re-verification of all 125 originals + V-1
```

The report is a snapshot of `main` at commit `0bad295`. Commit `a32fa55` added the report and
`d242ba5` removed it again; **neither touched any production code, test code, or other
documentation**, so every `path:line` citation in the report resolves exactly at HEAD. That is
the ground-truth anchor this epic's claim labels rest on.

## START HERE

### Annotations

<!-- ANNOTATION ZONE — hand-written, and deliberately OUTSIDE the generated markers.
     A regeneration replaces only what sits BETWEEN the markers, so everything written
     here survives it. This is what makes the block above genuinely regenerable: the
     per-row notes the generator cannot produce (why a row is parked, what a running
     plan is waiting on, an operator caveat on a queue entry) have a home that a
     verbatim paste does not destroy. -->

- **Both operator-confirmed-running notes are retired.** PLAN-01 and PLAN-14 have since shipped
  (#210 / #208), so the liveness question they recorded is settled by their landings. Nothing in
  the ledger observes whether a `running` plan is alive, which is why such a note is narrative and
  is written by hand rather than regenerated — the same applies to any future one.
- ⛔ **SUPERSEDED AGAIN 2026-09-15 — `parallelization_scope` is back to 2 (operator directed: run
  PLAN-16 in parallel with the confirmed-running PLAN-07).** The `scope: 1` note immediately below
  is itself now historical. **PLAN-07 is CONFIRMED RUNNING** (`launched`, and `corpus cross-check`
  now observes a real `live_plan` footprint for it — `references.json` carries
  `ContextPaths.java`, `ForwardedHeaderResolver.java`, `IpAddresses.java` plus four forwarded test
  files). **PLAN-16 is emitted to run alongside it** (R=1→2 once confirmed) — disjoint from
  PLAN-07's live footprint, no collision row in `corpus cross-check`. Once both are running, R=2=N
  and no further slot opens until one lands; PLAN-08 is next in emission order after that.
- ⛔ **SUPERSEDED 2026-09-15 — `parallelization_scope` changed 2 → 1 (operator directed: "stay with
  parallel N=1"), then back to 2 (see above).** The note below describing a PLAN-07+PLAN-08 joint
  emission is from an even earlier `scope: 2` regime and no longer applies. PLAN-08 (now the
  12-deliverable merged spec) stays `staged`, next in emission order after PLAN-16.
- **Nothing is running as of the 2026-09-09 evening drain (historical).** PLAN-05 shipped; PLAN-07
  and PLAN-08 were emitted together and awaiting operator-confirmed launch under the since-retired
  `scope: 2` regime — `auto_emit` is false, so both rows correctly read `staged`.
- ⛔ **PLAN-07's BRIEF IS UNDELIVERED, and a brief can only be delivered AT launch** — once a plan
  is running there is no channel to it and the inbox is orchestrator-inbound only. PLAN-07 owes a
  re-read of `ForwardedHeaderResolver.java`, `ContextPaths.java` and `IpAddresses.java` at outline
  (its declared surface after the 2026-09-15 cleanup dropped `RfcForwardedParser.java` — deliverable
  4 there is retired as already-fixed by PLAN-06).
- ✅ **NEW 2026-09-15 — PLAN-16 added (WS-01), sourced from GitHub issue #236 (not the original
  quality report), and positioned NEXT after PLAN-07 by operator directive.** **Emission order is
  PLAN-07, then PLAN-16**, ahead of PLAN-08/PLAN-10/PLAN-12/PLAN-13 in the table's array-order
  row position below — the array append put PLAN-16 last in the table, but the table's own
  banner already states array order is not emission order. PLAN-16 is disjoint from PLAN-07 (can
  run in parallel if `parallelization_scope` is later raised above 1) but **overlaps PLAN-10** at
  `DecodingStageTest.java` — the two must not be paired if scope is raised; moot under the current
  `scope: 1`. See `plans/PLAN-16-parameter-value-linebreak-carve-out.md` for the full Security
  Impact Assessment (raw-versus-encoded asymmetry, ADR-0017-class, library-boundary Low/Medium).
- ⛔ **The self-review warning in PLAN-05's brief was WRONG and must not be repeated.** See the
  amended Open Defect: an implementor does resolve for Java in this repo.
- ⛔ **Read the concurrent-collision defect before confirming that launch.** The previous pair was
  emitted on the same kind of `disjoint` verdict and still overlapped. The verdict bounds DECLARED
  surfaces only, and five of seven plans have under-declared.

## Ordered Queue

### Queue annotations

<!-- ANNOTATION ZONE — hand-written, and deliberately OUTSIDE the generated table markers.
     A regeneration replaces only the table BETWEEN the markers, so everything written here
     survives it. This is where the per-row narrative the generator cannot derive lives — a
     sequencing caveat, a disjointness note, why a row is parked — keyed by plan id. -->

**Emission order is NOT the table's row order.** The table renders in `plans[]` order, which is
workstream order. Actual emission order is set here, and is re-derived after every landing.

**Shipped (out of the live table; see their landing records):**

- **PLAN-01** — PR #210, `7a0da52`, `landings/PLAN-01.md`. Closed both High findings on the
  validation core. Landed with one under-declared file and three over-declared ones.
- **PLAN-14** — PR #208, `73afd22`, `landings/PLAN-14.md`. Wrote `doc/adr/` without declaring it
  and did not touch the one file it *did* declare — the collision the gate missed and the
  serialization it imposed for nothing.
- **PLAN-07** — PR #237, `a14f6e0`, `landings/PLAN-07.md` (2026-09-15). 6/6 deliverables, verified
  against real PR/CI/metrics state. 2 undeclared production files (ninth epic under-declaration
  occurrence, no collision). **PLAN-16 confirmed running** (live footprint observed matching its
  8-file declared surface) — transitioned `staged` → `launched` to catch the ledger up; the
  operator had launched it without an explicit confirmation message, same as PLAN-07 before it.
  **PLAN-08 emitted for the slot PLAN-07's landing freed** — disjoint from PLAN-16. PLAN-10
  skipped for this slot: confirmed live collision with PLAN-16 at `DecodingStageTest.java`.
- **PLAN-16** — PR #239, `c680a3f`, `landings/PLAN-16.md` (2026-09-16). 3/3 deliverables, zero
  surface delta (the epic's cleanest declaration, tenth watch occurrence, positive). PR #238
  (original) closed unmerged and replaced by #239 after CodeRabbit rate-limit exhaustion on both
  PRs — operator-authorized proceed unreviewed both times, confirmed via `ci pr view --pr-number
  238` (`state: closed`, `merge_commit_sha: null`). One Sonar-only-caught defect (`java:S5778`)
  fixed in-run. **PLAN-08 re-emitted** (it had never been confirmed launched — was still `staged`)
  — but only 1 of 2 slots fills this round: PLAN-08 collides with PLAN-10 (client test
  directories), PLAN-12 (`package-info.java` + `StringContentConverter.java`), and PLAN-13 (the
  `doc/adr/` HYPOTHESIS-group rule below), so none of the three can join it. Shortfall on slot 2.
- **PLAN-10** — PR #256, `f81a554`, `landings/PLAN-10.md` (2026-10-04 drain). 12/12
  deliverables, no production code. Twelfth under-declaration (4 undeclared files, incl. the
  `doc/…/testing.adoc` boundary crossing). 5 finalize rounds (2 over the ceiling), ~8.8M tokens.
  WS-05 fully closed. 9 candidate lessons promoted (2026-10-04-06-001..009).
- **PLAN-08** — PR #240, `2e9e8e0`, `landings/PLAN-08.md` (2026-09-16). 13/13 deliverables (12 code
  + ADR-0023 added during execution). The epic's WORST under-declaration since the gate was rebuilt
  (eleventh watch occurrence) — 6 undeclared files, including a real spec-boundary violation
  (`doc/client-handlers-readme.adoc`, `doc/http-result-pattern.adoc`, both explicitly excluded by
  the spec) now its own Open Defect for PLAN-13. In-run security fix (delimiter-forgery cache-key
  collision) plus 8 CodeRabbit fix tasks, all landed. WS-01, WS-03, WS-04 now all fully closed.
  **PLAN-10 and PLAN-12 emitted together** — the parser confirms them mutually disjoint now that
  every live code plan has shipped (PLAN-12's old "collides with every code plan" constraint no
  longer binds against a code plan that no longer exists in the queue). PLAN-13 held back purely by
  the 2-slot cap, not by any collision.

**Emitted, awaiting launch: PLAN-07 and PLAN-08.** Disjoint by the parser, in unrelated packages
(`http/forwarded` vs `http/client/adapter`); PLAN-07's PLAN-06 dependency is shipped and PLAN-08 is
the WS-04 head. **Shipped 2026-09-09: PLAN-05** (#231, `ca74911`) — closing two genuinely
exploitable `__Host-` fail-open holes, the epic's highest-severity pair since PLAN-01.

⛔ **PLAN-08 declares `doc/adr/`** and is therefore exposed to the ADR trap. High-water is **0022**,
and the only control that has ever caught this class is a re-check against `origin/main` **at the
merge gate** — a collision survives a clean rebase with green tests.

⛔⛔ **The disjointness gate produced a WRONG verdict on the previous pair, and the same risk
applies here.** PLAN-15 wrote into PLAN-04's declared test package while PLAN-04 was running; the
verdict was computed correctly from declarations that were wrong. **State this at every
`scope: 2` emit** — a `disjoint` verdict is a statement about what specs declared, and this corpus
under-declares five times in seven.

⛔⛔ **REDISTRIBUTED 2026-09-15 at `d385aa3` (cleanup pass, operator-directed).** PLAN-09 merged into
PLAN-08 (both WS-04, forced sequential, overlapped at `ResilientHttpAdapter.java`); PLAN-11 merged
into PLAN-10 (both WS-05, forced sequential, overlapped at `security/database/` and two generator
files). Both merges were component-first, task-second groupings the source specs' own Dependencies
sections already treated as load-bearing — not weak merges. **PLAN-09 and PLAN-11 rows are now
`parked`**, each pointing to its merge destination spec, which is retained as the retirement audit
record per the epic's duplication/redistribution apply-policy. PLAN-08 is now 12 deliverables
(former PLAN-08 #1-5 + former PLAN-09 #1-7, with the two plans' separate test-hardening
deliverables collapsed into one merged deliverable 12). PLAN-10 is now 12 deliverables (former
PLAN-10 #1-5 + former PLAN-11 #1-7, with former PLAN-10 #6 "regression discipline" folded into
Execution Constraints since it was never its own artifact). PLAN-07 deliverable 4 (RFC 7239
duplicate-directive handling) is retired as already-fixed by PLAN-06 — struck in place, not
renumbered. PLAN-12 and PLAN-13 were evaluated for the same treatment and left split: genuinely
surface-disjoint (Java Javadoc vs AsciiDoc) and already designed to run concurrently — merging them
would lose real parallelism for no coupling gain. **The "PLAN-08 → PLAN-09 sequenced" and "WS-05
(PLAN-10, PLAN-11)" sequencing notes below are SUPERSEDED by this merge — both are now internal
task order inside one plan, not inter-plan sequencing** — left in place as the historical record of
why the merge was the correct call, not as a live constraint.

⛔ **CLEANUP 2026-10-03 at `c10ffa9` (operator-directed, after `next` refused all three staged
plans).** **PLAN-09 and PLAN-11 rows are now `superseded`** (were `parked`): each was absorbed by a
named successor (PLAN-08 / PLAN-10), which is what `superseded` means and `parked` does not — the
"rows are now `parked`" sentence above is historical. PLAN-10, PLAN-12 and PLAN-13 re-grounded at
`c10ffa9`: 52 claims, 43 corroborated, 5 contradicted and re-scoped in place, 4 unverifiable.
Re-scopes — **PLAN-10** claim 12 / deliverable 7 (`COOKIE_VALUE` already maps to
`RFC6265_COOKIE_OCTET`; the false positive is fixed, only the generator widening remains) and
claim 16 (35 generator files, not 34); **PLAN-13** claim 2 / deliverable 2 (`allowExtendedAscii`
defaults to `false`; `DecodingStage` no longer calls `URLDecoder.decode`), claim 5 / deliverable 3
(6 falsely-claimed WARN records, HTTP-120 is now asserted) and claim 10 / deliverable 4g (23 ADR
files, rows 0019-0023 unindexed). **PLAN-12** Expected Surface corrected: the bare
`cui-http-core/src/main` entry resolved to a non-existent file at the gate and is replaced by the
15 files it stood for (29 declared, was 15).

⛔ **The disjointness gate is structurally unpassable for this epic on plugin 0.1.1842, and the
cleanup cannot fix it.** `corpus cross-check` compares each candidate against EVERY corpus spec
regardless of row status, so (a) PLAN-10/12/13 each carry `file_overlap_matches[]` rows against
SHIPPED specs only (PLAN-01..08/15/16) — plans that can no longer run concurrently — and (b) the
two superseded stubs (PLAN-09, PLAN-11), which legitimately declare no surface, keep
`candidate_comparison_determinate: false`. The three staged specs have NO overlap row against one
another, no live plan exists and no sibling epic is in the store. Emitting therefore needs an
explicit operator override recorded per emit, or a plugin fix that drops terminal rows from the
within-corpus candidate population.

⛔ **Mandatory re-reads before outline** — a shipped sibling changed each of these:
- **PLAN-05** → `AllowBlockListStage.java`. PLAN-04 rewrote its entry canonicalisation; PLAN-03
  rewrote its `detail` rendering. Both landed after PLAN-05 was staged.
- **PLAN-07** → `RfcForwardedParser.java` and `RfcForwardedParserTest.java` (PLAN-06, undeclared).
- **PLAN-11** → `EncodingCombinationGenerator.java` (PLAN-04, anticipated) and
  `AllowBlockListStageTest.java` (PLAN-03/PLAN-04).
- **PLAN-13** → `configuration.adoc` carries TWO obligations from two concurrent plans, and four
  other `doc/` files were already corrected by PLAN-04 and must not be re-derived.

✅ **ADR allocation is now a solved problem, and the solution is named.** PLAN-04 landed ADR-0022
clean by re-checking against `origin/main` **at the merge gate**, not only at authoring time. High-water
is **0022**. The index, however, is five divergences behind.

**Standing sequencing constraints (re-check each against the parser, never against this note):**

- **WS-01 order is PLAN-02 → {PLAN-03, PLAN-15}.** PLAN-02 edits `SecurityConfigurationBuilder.java`
  (which PLAN-15 rewrites) and settles the `CharacterValidationStage` escaping shape PLAN-03
  adopts. PLAN-03 and PLAN-15 are **file-disjoint from each other** — the gain the PLAN-03 split
  bought — and may pair once PLAN-02 lands.
- **PLAN-02 and PLAN-03 are also file-disjoint** and may pair, if the operator accepts that PLAN-03
  then reads the escaping shape from PLAN-02's branch rather than a landed HEAD.
- **PLAN-02 and PLAN-15 may NOT pair** — both edit `SecurityConfigurationBuilder.java`.
- **PLAN-06 → PLAN-07 strictly sequential** (same resolver class). **PLAN-08 → PLAN-09 sequenced**
  (PLAN-08 restricts the `HttpResult` states PLAN-09 tightens).
- **WS-02 (PLAN-04, PLAN-05) sits behind PLAN-02 and PLAN-03**; PLAN-04 → PLAN-05 sequential
  (shared exception-detail construction).
- **WS-05 (PLAN-10, PLAN-11) sits behind WS-01 and WS-02** — its tests assert behaviour those
  workstreams change. PLAN-11 additionally reaches into the stage, client and forwarded test
  suites, so it must not pair with a plan from WS-01, WS-03 or WS-04.
- **WS-06 (PLAN-12, PLAN-13) is last.** PLAN-12 edits Javadoc inside the same `.java` files every
  code plan edits, so it collides with all of them; PLAN-13 is disjoint from every code plan and is
  the epic's best late-stage pairing partner.

**The `doc/adr/` trap — read this before pairing anything.**

`doc/adr/` is declared as a conditional `HYPOTHESIS` by PLAN-06, PLAN-08, PLAN-09, PLAN-13 and
PLAN-15, and the gate scores it as a real overlap — so **no two of those five may run
concurrently**. That is why PLAN-06 and PLAN-08 cannot pair, and it is a genuine constraint, not
noise.

⛔ **But the gate does not bound the risk.** `adr-propose` is a standard finalize step that runs on
**every** plan, so any plan can write an ADR whether or not its spec declares one — which is
exactly how PLAN-01 and PLAN-14 both shipped an ADR-0016. Declaring `doc/adr/` in all thirteen
remaining specs would serialize the entire epic on one directory; that was considered and
rejected. Every spec instead carries an execution constraint to allocate ADR numbers against
`origin/main` at write time and to name the ADR in its inbox message. **Treat any concurrent pair
as capable of writing `doc/adr/`, regardless of what the gate says.**

## Decisions

{One entry per recorded decision — append-only, newest last. This section is a curated
human-facing VIEW; the authoritative append-only record is `logs/decision.log`, written via
`manage-logging --store orchestrator` (decision verb). Because entries carry rationale and
alternatives the log summary need not, this section is NARRATIVE — the compact stage preserves
it verbatim and never regenerates it.}

- 2026-09-05 — **Parallelization scope set to 2.** The operator chose 2 over the project
  default of 1. Rationale: the epic's surfaces partition cleanly enough that a documentation or
  build plan can run alongside a code plan. Constraint: the security-validation code plans
  overlap heavily in `de.cuioss.http.security.validation` and MUST stay sequential relative to
  each other regardless of the knob.
- 2026-09-05 — **The report was moved out of version control before decomposition.**
  `doc/quality-report/` was deleted by PR #207 (`skip-bot-review`, merged through the merge
  queue) and the files now live at `quality-report/` inside this epic tree. Alternative
  considered: keep it in `doc/` and delete it at the end of the epic. Rejected because the
  report is a snapshot pinned to `0bad295` — every finding it describes will be fixed or
  refuted, so leaving it in `doc/` guarantees a stale published artifact for the whole life of
  the epic. Accepted consequence: the corpus is no longer git-controlled, as the operator
  explicitly acknowledged.
- 2026-09-05 — **Ground truth is verified per finding at decompose time, not deferred wholesale
  to the plans.** Six read-only verification passes (one per report document) re-checked every
  finding's cited `path:line` against HEAD and returned a `corroborated` / `contradicted` /
  `unverifiable` verdict per finding. Alternative considered: label every finding `HYPOTHESIS`
  and let each plan's outline phase settle it. Rejected because the operator asked for ground
  truth at ingestion, and because a finding that is already refutable should never consume a
  plan slot. Findings that survive verification are labelled `OBSERVED` in their spec; the rest
  carry a `HYPOTHESIS` with a named confirm/refute artifact.
- 2026-09-05 — **Every code-change deliverable is TDD.** The operator's standing instruction:
  for any bug fix or behavioural change, the failing regression test is written and seen to
  fail before the production edit. Each plan spec carries this as an explicit deliverable
  constraint rather than relying on the executing plan to infer it.
- 2026-09-06 — **PLAN-03 was split into PLAN-03 + PLAN-15 at the PLAN-01 landing.** Folding inbox
  finding `decoding-normalisation-hardening-001` took PLAN-03 to eight deliverables, over the
  scope-bloat guard. Alternative considered: proceed unsplit with a recorded rationale, as
  PLAN-04, PLAN-09, PLAN-11 and PLAN-13 do. Rejected because those four proceed unsplit precisely
  *because* a split would produce two plans with an overlapping file surface — and here the
  opposite held: the exception/log half (`UrlSecurityException.java`, `AllowBlockListStage.java`,
  `test/.../exceptions/`) and the configuration half (the four config classes,
  `RequestCollectionValidator.java`, `test/.../config/`) share no file at all. The split therefore
  costs nothing and buys a concurrency pairing that did not exist before.
- 2026-09-06 — **Three plan-marshall lessons filed with `--allow-foreign-store`.** This repo's
  lessons store refuses a `plan-marshall` component because it does not own that bundle. The
  override was taken because the store *already holds three* such lessons filed the same way, so
  the established practice here is to park plan-marshall lessons locally and carry them to the
  bundle repo later via the `lessons` verb's cross-repo integrate-then-remove flow. Alternative
  considered: record them only as epic defects. Rejected — that loses the signal for every other
  repo. **Consequence to act on: six plan-marshall lessons now sit in a store that does not own
  them and are awaiting carry-over.**
- 2026-09-06 — **Candidate-lesson `-006` was folded, not promoted.** The lessons store refuses a
  `cui-http` component, and the rule's actual consumers are the five remaining validation specs.
  It was written into PLAN-02, PLAN-03, PLAN-04, PLAN-05 and PLAN-15 as a hard execution
  constraint instead. Filing it as a corpus lesson as well would have created a second home for
  one rule.
- 2026-09-06 — **The ADR-0016 collision changes how ADR surface is governed for the rest of the
  epic.** PLAN-01 and PLAN-14 both shipped an ADR numbered 0016. Alternative considered: declare
  `doc/adr/` in every remaining spec so the disjointness gate serializes on it. **Rejected** — that
  would make all twelve remaining plans mutually colliding on one directory and serialize the whole
  epic, a far larger cost than the defect. Chosen instead: a per-spec execution constraint
  requiring ADR numbers to be allocated against `origin/main` at write time (never the local
  worktree) and the ADR to be named in the plan's inbox message. The gate is explicitly **not**
  being asked to catch this class, because `adr-propose` runs on every plan and the write is
  undeclared by construction.
- 2026-09-15 — **Cleanup redistribution (A5): PLAN-09 merged into PLAN-08; PLAN-11 merged into
  PLAN-10.** Operator directive: re-ground all staged specs at current HEAD, remove stale claims,
  and reconsider composition toward larger plans (up to ~12 deliverables). Ground truth: HEAD moved
  `ca74911` → `d385aa3` since the prior cleanup, but the diff touches only CI workflow YAML,
  `pom.xml`'s parent version and `.plan/marshal.json` — nothing on any staged spec's declared
  surface, so all 87 previously-recorded verdicts still held; no claim needed re-corroboration, only
  a `checked_at` refresh. Rationale for the two merges: PLAN-08/PLAN-09 (WS-04) and PLAN-10/PLAN-11
  (WS-05) were each already declared strictly sequential by their own source specs and each pair
  overlapped at named files (`ResilientHttpAdapter.java`; `security/database/` plus two generator
  files) — component-first, task-second grouping makes one plan the correct unit rather than two
  dependency-linked PRs. Alternative considered: also merge PLAN-12 + PLAN-13 (WS-06) for symmetry.
  **Rejected** — that pair is genuinely surface-disjoint (Java Javadoc vs AsciiDoc/Markdown) and
  explicitly designed to run concurrently at the epic's `parallelization_scope: 2`; merging them
  would destroy real parallelism to satisfy a preference for uniform plan size, not to resolve a
  coupling. Each merged plan's two source specs' test-hardening deliverables were collapsed (not
  concatenated) where they covered the same activity over the same file tree, keeping both merged
  plans at exactly 12 deliverables rather than 13. Separately, PLAN-07 deliverable 4 (RFC 7239
  duplicate-directive handling) was retired as already-fixed by PLAN-06, on a positive account (an
  explicit `Parsed(...,malformed)` record and ADR-0021), not mere symbol absence. `corpus enumerate`,
  `corpus surfaces`, `corpus cross-check` and `corpus verdicts` were re-run after every edit and all
  reconcile cleanly (`blocking_count: 0`, `rows_without_spec_count: 0`, `specs_without_row_count: 0`).

## Open Defects

{Known defects surfaced by landings or observations that are not yet owned by a staged
plan. When a defect is folded into a plan spec, move it out of this list and note the
owning PLAN-NN.}

- **NEW 2026-10-04 (PLAN-10 landing, unverified lead) — low-byte CRLF homograph accepted.**
  PLAN-10 reports `URLParameterValidationPipeline` accepts `%e5%98%8a%e5%98%8d` (U+560A U+560D,
  whose low bytes are 0x0A / 0x0D) even with line breaks disallowed. Only exploitable where a
  downstream component truncates code points to bytes. Not owned by any staged plan (WS-01 is
  closed) — candidate for a new WS-01 plan after verification at `URLParameterValidationPipeline`
  § `createStages`.
- **NEW 2026-10-04 (PLAN-10 landing, unverified lead) — no scheme rejection on URL path.** No
  preset rejects `http:`/`https:`/`ftp:`/`gopher:`/`ldap:` or custom schemes on the URL path
  pipeline. Possibly by design (the pipeline validates a path component, not a URL); decide
  before staging.
- **NEW 2026-10-04 — PLAN-10 crossed its write-boundary into `doc/`.** It edited
  `doc/http-security/specification/testing.adoc`, which its spec reserved for PLAN-13. Owned by
  PLAN-13: re-read `testing.adoc` at outline before rewriting it.
- **NEW 2026-10-04 — breaking change to the published `generators` test artifact.** PLAN-10
  removed `PathTraversalURLGenerator` and `DoubleEncodingAttackGenerator`. Owned by PLAN-13 for
  `doc/test-generators-readme.adoc`; must also be called out in the next release notes.
- **NEW 2026-10-04 — PLAN-10 test-side residue.** Set-membership assertion in
  `URLPathValidationPipelineTest`, a raw NUL in the `HttpHeaderInjectionAttackGenerator` Javadoc,
  raw characters in `IDNAttackDatabase` (same class as lesson 2026-10-04-06-001). WS-05 is now
  closed; unowned.

- ⛔ **PLAN-08 (landed #240, 2026-09-16) edited two `doc/` files its own spec explicitly excluded.**
  `doc/client-handlers-readme.adoc` and `doc/http-result-pattern.adoc` were both touched (6 lines
  total, confirmed substantive via `git diff --stat`), despite the spec's Write-Boundary naming both
  as off-limits and the PR's own "Explicit non-goals" section independently claiming no `doc/` file
  other than the new ADR was touched — a claim the PR's own diff contradicts. No collision resulted
  (PLAN-13, the doc-owning plan, has not run), but **PLAN-13 must re-read both files at its own
  outline rather than trusting the original quality-report's description of them** — they have
  already been corrected once, underneath PLAN-13's still-staged premises. → owned by PLAN-13,
  recorded in `landings/PLAN-08.md`.
- ✅ **RESOLVED 2026-09-08 — the live ADR-0019 collision was caught and renumbered by PLAN-06
  itself.** Its ADR shipped as **ADR-0021** (`6e1a19c`), superseding ADR-0002, and inbox message
  `-001` was corrected in place. ⛔ **How it was caught is the durable part:** PLAN-06 allocated
  0019 against `origin/main` when 0018 was the highest there; PR #217 then landed its own 0019 AND
  0020 while PLAN-06 was in review. **The rebase was clean and the tests were green with both 0019
  files present** — nothing structural sees this. Only the explicit pre-merge re-check did. That
  re-check is now the sole control standing between this epic and a fourth collision, and it lives
  in prose, not in any gate. → analysis promoted as lesson `2026-09-08-15-005`; the mechanism
  question belongs to PLAN-13 deliverable 4b.

- **`main` carries a REQUIRED merge queue, and the configured squash merge is refused by it.**
  PLAN-05 hit *"an immediate merge would close the PR unmerged"* and routed through
  `pr merge-queue` instead. Repo configuration, not a plan defect — **every remaining plan should
  expect it** rather than treating the refusal as an error.

- **No step outcome is evidence about whether an ADR exists — there are now THREE distinct reasons
  a plan produces none.** PLAN-03: `adr-propose` `lane: off`. PLAN-06: recorded `skipped` yet an ADR
  shipped, written during execute. PLAN-05: composed out entirely by the `standard` posture.
  ⛔ Only `doc/adr/` answers the question. High-water remains **0022**.

- ⛔⛔ **THE GATE FAILED FOR REAL — a concurrent surface collision on the orchestrator's own emit
  (2026-09-09).** PLAN-15 and PLAN-04 were emitted together into the two slots of
  `parallelization_scope: 2` on a `disjoint` verdict this orchestrator produced. **PLAN-15 then wrote
  `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/DoubleEncodingPresetParityTest.java`
  — inside `test/.../security/pipeline/`, which PLAN-04's spec declares as "the whole pipeline test
  package" — while PLAN-04 was running.**

  Nothing corrupted: the two touched *different files* inside the shared directory, so both merged
  without textual conflict. ⛔ **That is luck, not the gate working.** Had PLAN-04 edited the same
  new file, or had either rebased across the other's write, this was a real conflict.

  **The gate is sound and its input is not.** Five consecutive plans have now under-declared
  (PLAN-01, PLAN-14, PLAN-02, PLAN-06, PLAN-15 — PLAN-03 and PLAN-04 excepted), and a sound gate over
  an unsound declaration yields an unsound verdict. The `disjoint` verdict was computed correctly
  from what the specs said; the specs were wrong. → **This defect is the orchestrator's, not either
  plan's.** No mitigation is available inside the current gate: it cannot see a surface a spec never
  declared. Until something reads realized footprints back into the declarations, every
  `parallelization_scope: 2` emit carries this residual risk, and it should be stated at every emit
  rather than assumed away.

- ⛔ **The `landing-facts` emitter now has THREE inconsistent states across six landings**, so
  neither verdict of `inbox landing-check` can be trusted on its own:

  | State | Plans |
  |---|---|
  | correct fenced block | PLAN-03, PLAN-15 (the latter with nine optional `step.*` keys — the richest yet) |
  | fenced block with **wrong values** (6-finalize figures under plan-scoped key names) | PLAN-06 |
  | **no block** — every fact present but as markdown bullets, or absent entirely | PLAN-02, PLAN-04 |

  `complete: true` establishes only that a block PARSED, not that it is right; `complete: false` does
  not mean the facts are missing. ⛔ Both verdicts still require a hand check against `metrics.md` —
  the opposite of what the check exists for. → no plan in this epic owns the emitter.

- **PLAN-15 shipped a deliberate, informed, un-ADR'd published-API break.** Eleven `public static
  final` constants were deleted from `de.cuioss.http.security.config`, a package `module-info.java`
  exports. The spec's HYPOTHESIS about published-API status was **settled AGAINST deletion**, and the
  spec's own constraint prescribes re-scoping to deprecation on exactly that finding. The operator was
  shown the finding and the constraint, was offered "delete anyway plus an ADR recording the break",
  and chose plain deletion with **no ADR**. Downstream consumers break on upgrade with no deprecation
  window. ⛔ **SETTLED — do not reopen.** Recorded here because `landings/PLAN-15.md` is the only
  durable trace a future reader chasing a broken downstream build will find. Note what it cost the
  epic's machinery: verify-first worked exactly as designed, and its refutation was overridden by fiat.

- ⛔⛔ **`doc/adr/README.md` now has FOUR divergences and is this epic's most-recurrent defect.**
  **Re-measured at `b09925a` on 2026-09-09: 22 `.adoc` files, index still 18 rows, still asserting
  "highest allocated number is 18" — now FIVE divergences.** ADR-0022 (PLAN-04) joined 0019/0020/0021
  as unindexed. ✅ **But the allocation discipline itself now works**: PLAN-04 verified 0022 against
  `origin/main` three times — before writing, before commit, and **at the merge gate** — and landed
  clean. The control that works is the merge-gate re-check; an authoring-time check alone has never
  caught a collision. Earlier measurement at `6e1a19c` on 2026-09-08: `doc/adr/` held **21** `.adoc` files; the index carries
  **18** rows, still asserts "the highest allocated number is **18**", is missing rows for **0019,
  0020 and 0021**, and its ADR-0002 row reads `Proposed` while the file on disk reads
  `Superseded`. Four consecutive waves (0016 → 0019/0020 → 0021 → the 0002 status flip), and PR
  #213 repaired this same surface by hand on 2026-09-07. **Hand maintenance has now failed four
  times in four days; "update the table in the same change" is refuted as a control.**
  → **PLAN-13 deliverable 4b (REOPENED, widened)**: re-derive the whole index rather than patch
  rows; do it as ONE pass with deliverable 5's status advance; and **propose a mechanism or record
  why not** — lesson `2026-09-08-15-005` supplies the analysis (the contested resource is a
  NUMBER, and git compares PATHS, so the collision is structurally invisible to every existing
  check).

- ⛔ **The `landing-facts` emitter is now WRONG rather than absent — a strictly worse failure.**
  PLAN-02's landing (2026-09-08) carried no block at all: `inbox landing-check` reported
  `complete: false` with all 8 required keys missing, which forced a hand reconciliation and was
  therefore VISIBLE. PLAN-06's landing reported **`complete: true`** — and two of its values are
  the **6-finalize phase row** published under whole-plan key names:

  | Key | Block | Actual (metrics.md) |
  |---|---|---|
  | `total_tokens` | 2,562,256 | **4,370,482** |
  | `total_wall_seconds` | 68,869 (19h07m) | **136,860 (38h01m)** |

  ⛔⛔ **And PLAN-03's block, drained the same day, is EXACT** (13,434,874 tokens / 12h34m, both
  matching `metrics.md`). So the emitter is **intermittently** wrong, not uniformly wrong —
  `complete: true` carries no assurance in either direction, and every drained landing's totals
  must be checked against `metrics.md` by hand. That is a worse property than a uniform bug, which
  could at least be corrected for. ⛔ A complete-but-wrong block **reconciles silently**, where a
  missing one cannot. The operator's
  own summary repeated both figures, so the error had already propagated to the human channel
  before the orchestrator caught it. Any consumer treating `complete: true` as "these numbers are
  the plan's" is wrong. → no plan in this epic owns the emitter.

- ⛔ **AMENDED 2026-09-09 — this defect was OVER-CLAIMED, and the over-claim was the orchestrator's.**
  PLAN-05 (#231) observed `pre-submission-self-review` **resolve an implementor**
  (`ext-self-review-plan-marshall`), run **full-surface over all 10 files**, and **find a real
  ADR-0017 defect** which was then fixed. The orchestrator had asserted the opposite in the brief it
  pasted into that plan's session, so a plan was briefed with a false warning and correctly reported
  it back.

  **What was actually observed, across five plans, was the OUTCOME `clean, no check matched` — never
  the absence of an implementor.** Those are different facts, and this epic collapsed them at the
  PLAN-01 landing and then re-asserted the collapsed version four times, most recently as "fourth
  corroboration" in the PLAN-04 addendum. Each corroboration was real; none of them established what
  the defect claimed.

  **What survives, and it is now a sharper question than the original claim.** The step was inert on
  earlier plans and substantive here, so:
  - ⛔ **The dimension is NOT structurally absent for Java in this repo.** Any plan told otherwise is
    being misinformed. Do not repeat the original wording.
  - **The open question is what differs.** `ext-self-review-plan-marshall` resolving on a `cui-http`
    Java footprint is itself worth understanding — it may be a fallback implementor rather than a
    domain one, in which case earlier plans' `no check matched` and this plan's full-surface run
    have a common explanation and the coverage question stands on different ground.
  - **Retained as a genuine finding:** on PLAN-01 CodeRabbit was the only structural review a
    security-critical change received, and it caught a reintroduced bypass on round 2. That happened
    and is unaffected by this amendment.
  - Original sourcing, unchanged: landing PLAN-01, inbox `-003`; lesson `2026-09-06-07-002`
    (whose `clean, no check matched` occurrences remain valid as outcome observations).

- **Scope-creep detection is inert on every plan in this epic.**
  `references.json.plan_creation_sha` has a reader in `phase-5-execute` and no writer anywhere in
  the bundle, so the guard resolves `no_baseline_sha` on every task and reports an absent
  measurement that reads like a clean one. PLAN-01 compensated with a hand check — which missed
  the one under-declared file the orchestrator's own landing analysis found. — source: landing
  PLAN-01, inbox `-004`; folded as a recurrence onto lesson `2026-09-04-12-003`. Second
  independent observation; not self-correcting.
  - **Third observation, and it names a second surface (PLAN-02, 2026-09-08).** PLAN-02's
    `references.json` `affected_files` recorded **14** files against a merge that touched **19**,
    omitting `Cookie.java`, `CookieTest.java`, `URLParameterValidationPipelineTest.java` and both
    ADRs. ⛔ This is worse than a bookkeeping gap: `affected_files` is exactly what
    `corpus cross-check` reads as a **live plan's** surface, so a running plan's collision
    footprint is understated at the one source the disjointness gate trusts most for in-flight
    work. The gate's declared-surface half and its realized-surface half are now both known
    lossy.
  - **Third observation (PLAN-06, 2026-09-08).** `no_baseline_sha` on all 20 tasks; scope verified
    by hand. Three plans in one epic, no mitigation applied between any of them. Folded onto lesson
    `2026-09-04-12-003` as its third occurrence.
  - ⛔ **And the realized side has now failed THREE different ways, not one (2026-09-08).** These
    are distinct faults, so a single fix addresses none of the others:

    | Plan | Mode | Effect |
    |---|---|---|
    | PLAN-02 | `affected_files` = 14 of 19 | silent under-count |
    | PLAN-06 | `affected_files` = 11 of 12 | silent under-count |
    | PLAN-03 | `realized_footprint` **null** (worktree removed before `capture-footprint`), then recovery attempts of **26** and **23** before a hand-set **4** | capture failed outright, and the recovery path is itself unsafe |

    PLAN-03's two wrong recoveries were wrong for a reason that recurs by construction in this
    epic: they diffed against a **moving `main`** (PLAN-06 landed mid-finalize), sweeping a
    sibling's work into this plan's footprint. ⛔ **In a parallel epic `main` advances during
    finalize by definition, so a footprint recovery must diff against the plan's own merge-base or
    merge commit — never against current `main`.** The hand-set 4 is correct; the orchestrator
    confirmed it independently from `git show --stat 7c7235b` before the report arrived.
- **Six plan-marshall lessons are parked in a store that does not own their bundle.**
  `2026-09-04-12-001/-002/-003` and `2026-09-06-07-001/-002/-003` all carry a `plan-marshall:*`
  component and live in cui-http's store. They need carrying to the plan-marshall bundle repo via
  `/plan-orchestrator lessons` (integrate-then-remove), or they will accumulate indefinitely. —
  source: this drain.
  - **Now 14, not 6, and the store is actively refusing them (2026-09-08).** The 2026-09-06-14-*
    set added five, and the PLAN-02 drain added `2026-09-08-06-001/-002/-003`. ⛔ `manage-lessons
    add` now REFUSES a `plan-marshall:*` component outright with `error: wrong_store` — "lessons
    store repo does not own bundle 'plan-marshall'" — so all three of the new ones had to be filed
    with `--allow-foreign-store`. The refusal is the store telling us the backlog is misfiled;
    overriding it is a stopgap, not a resolution. **The `/plan-orchestrator lessons` pass is now
    the blocking remedy, not a tidy-up.**
  - **Now 20 (2026-09-08, second drain).** The PLAN-03 and PLAN-06 drain promoted six more
    (`2026-09-08-15-001`…`-006`), every one needing `--allow-foreign-store`, and folded five
    recurrences onto existing parked lessons. The backlog is growing at roughly six per landing
    pair and the store refuses every new one on sight.

- ✅ **RESOLVED 2026-09-07 — the duplicate ADR-0016 is gone.** Fixed inline by operator direction
  rather than waiting for PLAN-13 (last in the queue): PR **#213** (`09818a0`) renamed the
  later-landed CI/egress record to **`0018-…`**, kept `0016` on PLAN-01's path-validation record
  (every in-repo `ADR-0016` reference already resolved to it, so none moved), added the index row
  PLAN-14 never wrote, and moved the high-water mark to 18. Consolidated in the same PR: ADR-0014's
  Status is now `Superseded` with its scope stated — the root-clamp half is superseded by ADR-0016,
  the scheme-bearing-input half stands. `verify -Ppre-commit` green. **PLAN-13 deliverable 4 is
  closed**; its spec records what landed, and its claims 9 and 10 are re-stamped `contradicted /
  rescoped: yes` at `09818a0`. ⛔ Two of PLAN-13's premises died with it — the `runs to 14`
  self-contradiction was rewritten by #213, and the "18 files / 17 numbers" count is now 18/18.
  The paragraph below is the record of the defect as it stood.

- ⛔ **Two ADRs on `main` share the number 0016.**
  `0016-Absolute-path_dot-dot_walking_is_rejected_rather_than_clamped_at_root.adoc` (PLAN-01,
  `7a0da52`) and `0016-CI_harden-runner_egress_allowlists_…adoc` (PLAN-14, `73afd22`). Allocated
  concurrently in separate worktrees; neither plan noticed. **Compounding:** `doc/adr/README.md`
  was updated by PLAN-01 and not by PLAN-14, so the index lists one 0016 and is silent about the
  other. → owned by **PLAN-13 deliverable 4**, which is last in the queue, so the duplicate sits on
  `main` for the rest of the epic. Every subsequent `adr-propose` will scan, see max=0017 and
  allocate 0018 — leaving the duplicate silently in place.
- **The epic's advertised `module-tests` and `test-compile` verification arms do not exist in this
  project.** Neither resolves as a canonical command at any scope; PLAN-14 recorded both UNGATED,
  not passed. Substantive coverage came from `verify -Ppre-commit` instead. Affects **every plan in
  the epic**. → lesson `2026-09-06-14-005`; no plan in this epic owns the manifest.
  - ⛔ **The MECHANISM is now named (PLAN-04, 2026-09-09): `build-maven` exposes no
    `resolve-test-scope` verb at all.** The arm is not mis-resolved — its resolver does not exist, so
    the module-tests divergence gate cannot run in this project under any configuration. Folded onto
    lesson `2026-09-06-14-005` as its second occurrence.
- **The benchmark egress allowlist has never executed.** PLAN-14 switched harden-runner from
  `audit` to `block` with 20 enumerated hosts, but the benchmark workflow fires only on merged PRs
  and tags, so the allowlist is unvalidated against a real run. The first post-merge benchmark run
  is the validation point. ⛔ **If it fails on a network call, add the host named in the
  harden-runner summary — do not revert to `audit`.**

- **The `skill_domains` `file_globs` gap is now PR #224 (OPEN, not merged).** Domain-narrowing
  emptied `references.domains` on both PLAN-02 and PLAN-03, and each restored it by hand. The
  operator's uncommitted working-tree fix has been lifted into #224, which is why the tree is clean
  again — ⛔ **the working copy no longer carries it, so do not look for it there.** Until #224
  merges, every plan in this epic keeps hitting the same zero-domain narrowing. `provisioned_version`
  is also stale (0.1.1619 vs **0.1.1627** as of 2026-09-09). → `/marshall-steward` territory.
  - ⛔ **PR #224 is STILL OPEN and the drop has now hit four consecutive plans** (PLAN-02, PLAN-03,
    PLAN-04, PLAN-15), each restoring `references.domains` by hand. PLAN-15 named the precise shape:
    the `skill_domains` entries **declare a bundle but no `always_on`, `file_globs` or `aliases`**, so
    `domain-narrow` drops ALL FOUR domains at outline. ⛔ Merging #224 is the cheapest open item in
    the epic and it blocks nothing — it is simply not being done.

- **PLAN-16 is a new plan sourced from an EXTERNAL GitHub issue, not the quality report — the
  epic's Vision statement is no longer a complete description of what this epic drives to
  closure.** `#236` was filed 2026-09-15, analyzed for security impact (a raw-versus-encoded
  CR/LF asymmetry in `DecodingStage` under `strict()`, the same defect CLASS ADR-0017 already
  closed once for the C1 range), and folded into WS-01 as PLAN-16 rather than a new workstream,
  since it touches exactly WS-01's already-shipped file set. **This establishes a precedent**: a
  future external issue with the same file-surface fit should fold into its matching workstream
  the same way; one with no fit becomes its own workstream. Not yet reflected in the `## Vision`
  section's finding-count language above — that section still describes only the 126-finding
  report's closure criterion.

- ✅ **RESOLVED 2026-09-07 — the restart-readiness `not_ready` signal is gone.** The deliberate
  `.plan/marshal.json` edit landed on `main` as PR #212 (`38a5a9c`, steward-maintained config), so
  the working tree is clean and every scored signal now reads `ready`. The paragraph below is kept
  as the record of why the dirty path was accepted for two landings; it no longer describes the
  current tree. `registry_parity` still reports `not_available` and is still excluded from the floor.

- **Restart-readiness is `not_ready` on one signal only, and it is operator-accepted.**
  `.plan/marshal.json` carries a deliberate `plan_without_asking: false → true` edit that has been
  preserved through two landings. The operator directed "just ignore" at the 2026-09-06 cleanup, so
  it is neither committed nor reverted. Every other scored signal is `ready`; `registry_parity`
  reports `not_available` and is excluded from the floor. ⛔ **A future session must not read this
  `not_ready` as a blocker or "fix" the working tree** — the dirty path is intentional state.

## Watches

{Mid-flight observations that need monitoring but no immediate action — signals to
re-check at the next landing or session. Retire a watch when it resolves or graduates
into a defect/plan.}

- **This watch FIRED again (PLAN-15, 2026-09-09).** PR #217 shifted
  `SecurityConfiguration.isStrict()`/`isLenient()` from the cited lines 318/332 to **330/344**; the
  underlying claim held, only the anchors moved. Two spec claims also failed outright on contact:
  `DOUBLE_ENCODING_PATTERNS` was asserted to contain "only single-encoded sequences" and in fact held
  `%2525`, `%252e`, `%252f`, `%255c` — ⛔ **that claim must not be reused by any sibling spec.**

- The report's own line references drift the moment a remediation plan edits a cited file.
  Later plans in the queue MUST re-anchor their citations against HEAD at outline rather than
  trusting the report's line numbers. — re-check at every landing that touches
  `cui-http-core/src/main/java/de/cuioss/http/security/`.
- **Surface under-declaration is a live authoring habit, not a theoretical residual.** PLAN-01
  created `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/PostDecodeEnforcementRegressionTest.java`
  — a file its `## Expected Surface` never declared — while over-declaring three entries it never
  touched. No collision resulted (PLAN-14's surface is disjoint, and PLAN-04 already declares that
  directory), so the gate was not wrong here; the declaration was. — re-check at every landing:
  compare the realized file set against the spec's declaration and record the delta in the landing
  record.

  ⛔ **Third occurrence, and this one WAS a collision the gate would have mispredicted (PLAN-02,
  2026-09-08).** PLAN-02 declared 7 entries and touched 19 files. Seven of the extra files sit
  inside the DECLARED surfaces of **PLAN-04, PLAN-05 and PLAN-15** (`SecurityConfiguration.java`
  and `test/.../config/` → PLAN-15; `Cookie.java` and `test/.../data/` → PLAN-05;
  `test/.../pipeline/` → PLAN-04). None was running, so nothing conflicted — but the gate would
  have paired PLAN-02 with any of the three and been wrong. It also wrote two ADRs under `doc/`
  after its spec positively asserted it would touch no file there. PLAN-02's `## Expected Surface`
  has been corrected in place to the realized set. **Fourth occurrence (PLAN-06, 2026-09-08):** declared 8, touched 12, and three of the extras
  reached into **PLAN-07's** declared surface (`RfcForwardedParser.java`, `RfcForwardedParserTest.java`)
  plus an undeclared `ForwardedLogMessages.java`. PLAN-07 was not running and the pair was already
  strictly sequenced, so nothing conflicted — but ⛔ **PLAN-07 must now re-read both files at outline**
  rather than trusting its staged premises. **The habit is not improving across landings:
  1 file (PLAN-01) → a whole directory (PLAN-14) → 7 files plus a violated exclusion (PLAN-02) →
  3 files reaching into a sibling's surface (PLAN-06).**

  ⛔ **Sixth and seventh occurrences (2026-09-09), and the seventh is the one that finally bit.**
  **PLAN-15**: 8 declared, 8 realized — but two declared entries (`module-info.java`, `doc/adr/`) were
  never touched while `DoubleEncodingPresetParityTest.java` landed **inside PLAN-04's declared
  directory, with PLAN-04 running.** That is the epic's first REAL concurrent collision, recorded as
  its own Open Defect above. **PLAN-04**: 9 declared, 22 realized — but its overrun is the honest
  kind: it hit the same `doc/` exclusion PLAN-02 violated silently, **surfaced it, got explicit
  operator approval, scoped the override to four named files, and handed off to PLAN-13.** The same
  boundary, handled two opposite ways one week apart — which is the clearest evidence yet that the
  problem is authoring discipline, not the boundary itself.

  ✅ **Eighth occurrence, second-best in the epic (PLAN-05, 2026-09-09): declared 7 entries covering
  9 of 10 realized files.** The one miss is `HTTPBody.java`, a Javadoc-only edit documenting a
  last-wins behaviour inherited from the shared `AttributeParser` — a legitimate consequence of its
  deliverable 3. ⛔ **But the plan CLAIMED zero under-declaration** ("realized footprint is exactly
  the 10 declared files"), and a stated zero is worse than a silent miss because it invites the
  orchestrator to skip the check. It was checked. Two of the last three plans now declare close to
  honestly, so the trend is real but the self-reporting is not yet trustworthy.

  ✅ **Fifth occurrence REVERSES it (PLAN-03, 2026-09-08): declared 3, realized 4** — the single
  extra being `AllowBlockListStageTest.java`, an obviously-adjacent test inside PLAN-11's declared
  directory. Three tightly-scoped entries landing four files is what an honest declaration looks
  like. ⛔ Nothing in the tooling changed to cause this, so it is evidence about **spec authoring**,
  not about the gate — one data point, not yet a trend. Keep the watch open.

  **Ninth occurrence (PLAN-07, 2026-09-15, landed #237): declared 3 production files plus the whole
  test directory, realized 5 production files.** Two undeclared: `ForwardedResolverConfig.java` and
  `ResolvedForwarding.java`, both "supporting changes" per the PR body. No collision resulted —
  nothing else was concurrently editing either file, and by landing time PLAN-16 (the only other
  live plan) touches an unrelated package. Consistent with the epic's dominant pattern: production
  "supporting changes" adjacent to the named deliverable files are the most common under-declared
  class, not a new failure shape. — recorded in `landings/PLAN-07.md`.

  ✅ **Tenth occurrence, the epic's CLEANEST declaration yet (PLAN-16, 2026-09-16, landed #239):
  declared 8 entries, realized exactly those 8 — zero under- or over-declaration.** The only
  additional realized files were 5 `.plan/project-architecture/*` snapshots from the standard
  `architecture-refresh` finalize step, not a spec-declarable surface. Third positive data point
  after PLAN-03 and PLAN-05 — three of the last four landings (PLAN-05, PLAN-07, PLAN-16, with
  PLAN-07 the sole miss at 2 files) show the trend continuing to improve, though PLAN-07's own miss
  in between keeps this "trend, not yet a guarantee" rather than closed. — recorded in
  `landings/PLAN-16.md`.

  ⛔ **Eleventh occurrence, the epic's WORST since the gate was rebuilt (PLAN-08, 2026-09-16, landed
  #240): declared ~17 entries, realized 27 — 6 undeclared, one of them a real boundary
  violation.** Five of the six are unremarkable adjacent-scope additions (a sibling package-info.java,
  three new test files inside an already-declared directory, one new supporting test file). The
  sixth pair — `doc/client-handlers-readme.adoc` and `doc/http-result-pattern.adoc` — is qualitatively
  different: the spec's OWN Write-Boundary named both files off-limits, and the plan edited them
  anyway. Recorded as its own Open Defect above rather than folded here, since a stated boundary the
  plan crossed is a different fact from an ordinary adjacent-file miss. The trend the tenth
  occurrence noted is broken by this one — not yet re-closed. — recorded in `landings/PLAN-08.md`.
- **WS-05 (now just PLAN-10, since PLAN-11 merged into it at the 2026-09-15 cleanup pass) needs
  re-baselining against the landed validation behaviour.** ⛔ **STALE as of this note**: WS-01
  (PLAN-01, PLAN-02, PLAN-03) and WS-02 (PLAN-04, PLAN-05, PLAN-15) have ALL now landed — the
  "three movers remain" framing below is superseded; every WS-01/WS-02 mover named has shipped.
  PLAN-10's own spec already carries the mandatory re-read-at-outline clause covering this. Kept as
  the historical record of what moved and when; the live obligation is simply "PLAN-10 re-reads
  the whole security-validation surface at its own outline", already stated there.
  PLAN-01 changed verdicts those plans' tests assert. Its deliverable 6 fixed the mechanical
  in-module breaks and reported no premise-changing break, but PLAN-02, PLAN-04, PLAN-05 and
  PLAN-15 each moved the same ground again. PLAN-02 landed hard (`bdcb36e`): `allowExtendedAscii`
  defaults false, C0/C1 are unconditional for header and cookie types, the query set widened by
  `/ : @`, cookie-name is an RFC 7230 token and cookie-value is cookie-octet, and the path-segment
  block-list is `URL_PATH`-only.
- **`doc/http-security/configuration.adoc` and `doc/forwarded-header-resolution.adoc` reference
  `allowDoubleEncoding` and were never inspected for the staleness PLAN-01 introduced.** PLAN-13's
  `doc/` declaration covers both. — re-check when PLAN-15 settles the flag's fate, since the
  correct prose depends on that decision.

- **Review is catching defects introduced by the plan's own fixes, two landings running.** PLAN-01:
  a review fix reintroduced the raw-versus-encoded asymmetry the plan existed to remove. PLAN-14: a
  hardening fix admitted any Azure Storage account via a wildcard. Both were found only because a
  further review round ran, and this epic ships without the pre-submission structural self-review.
  — re-check at every landing; a landing reporting only one review round is a gap, not efficiency.
- **The CodeRabbit quota-wait defect has cost this epic ~9.5 h of wall-clock across its first two
  plans** (≈6.5 h on PLAN-01, ≈3 h on PLAN-14), with no mitigation applied between them. — re-check
  at every landing; the cheap remedy (re-request once the declared window elapses, before a second
  long wait) worked both times. Lesson `2026-09-06-07-001`.
- **Two PLAN-14 review findings were accepted as out of scope and remain open work.** (1) The
  enumerated egress endpoint list has no drift check against GitHub's published host set — a new
  `productionresultssaNN` host blocks artifact upload and fails the benchmark deploy. (2) The
  required-artifact list is duplicated between `benchmark-pages.py` and `benchmark.yml`; unifying
  it needs the out-of-repo `benchmarking-common` producer. — re-check if the benchmark deploy
  fails; neither is owned by a plan in this epic.
- **The PLAN-14 deploy guard ships with no automated regression test**, by operator decision;
  verification was re-expressed as direct command invocation against a pre-change baseline. — no
  action; recorded so a later reader does not mistake it for an oversight.

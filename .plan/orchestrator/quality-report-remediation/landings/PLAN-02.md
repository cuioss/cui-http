# Landing Analysis: PLAN-02 — Character Sets and Control Characters

epic: quality-report-remediation
workstream: WS-01-security-validation-core
pr: #217 (https://github.com/cuioss/cui-http/pull/217) — merged 2026-09-08T05:15:41Z as `bdcb36e`

> Landing record for one shipped plan. Every claim below was corroborated against ground
> truth before it was written: `git show --stat bdcb36e`, `gh pr view 217/216`, the archived
> plan tree at `.plan/local/archived-plans/2026-09-08-character-set-and-control-characters/`,
> and the files on `main`. Where the landing narrative and ground truth diverge, the
> divergence is named rather than smoothed.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Query + cookie character sets match cited RFCs | shipped-modified | `CharacterValidationConstants.java` in the diff. `RFC3986_QUERY_CHARS` widened with `/ : @`; `RFC6265_COOKIE_OCTET` added. **Modified:** `COOKIE_NAME` ended on `RFC7230_TOKEN_CHARS`, not `cookie-octet` — a correction forced by review, see Routing below |
| 2. `allowExtendedAscii` default flip; C1/Cf/Zs gaps closed | shipped-as-specified | `SecurityConfigurationBuilder.java`, `CharacterValidationStage.java`, `DecodingStage.java` in the diff; default `true` → `false` (breaking, operator-decided) |
| 3. C0 controls rejected in headers + cookies under every preset | shipped-modified | `CharacterValidationStage.java`. **Modified:** first implementation covered header types only; cookie types added after the finalize security audit caught the gap |
| 4. Path-segment block-list scoped to `URL_PATH` only | shipped-as-specified | `PatternMatchingStage.java` `checkBlockedPathPatterns` in the diff |
| 5. Two hollow stage tests given real assertions | shipped-as-specified | `PatternMatchingStageTest.java`, `DecodingStageTest.java` in the diff; landing reports mutation-verified |
| — (unplanned) | added-unplanned | **ADR-0019** and **ADR-0020**, both under `doc/adr/` — a directory the spec explicitly excluded. See § Surface delta |

**Scope delta the plan reported and the orchestrator confirms as legitimate.** The spec
anticipated a decoded structural-delimiter rule across five characters; it collapsed to one
(`#` in `PARAMETER_NAME`). The plan's stated reason — `[`/`]` are legitimately
percent-encoded, `\` is already owned by `PatternMatchingStage`, `?`/`#` in paths by
`NormalizationStage` — is consistent with what PR #210 (PLAN-01) landed. This is a narrowing
found by investigation, not a dropped deliverable.

## Surface delta — the declaration was wrong in three separate ways

The spec declared **7** entries and carried an explicit exclusion: *"This plan does **not**
edit any file under `doc/`."* The merge touched **19** files. Three distinct defects, not one:

**1. Under-declared code surface (7 files).** Realized but never declared:

| File | Whose declared surface it belongs to |
|------|--------------------------------------|
| `…/security/config/SecurityConfiguration.java` | **PLAN-15** |
| `…/security/config/package-info.java` | undeclared by anyone |
| `…/security/data/Cookie.java` | **PLAN-05** |
| `…/test/…/security/config/SecurityConfigurationTest.java` | **PLAN-15** (`test/…/config/`) |
| `…/test/…/security/config/SecurityDefaultsTest.java` | **PLAN-15** (`test/…/config/`) |
| `…/test/…/security/data/CookieTest.java` | **PLAN-05** (`test/…/data/`) |
| `…/test/…/security/pipeline/URLParameterValidationPipelineTest.java` | **PLAN-04** (`test/…/pipeline/`) |

⚠ PLAN-02 therefore overlapped the declared surfaces of **PLAN-04, PLAN-05 and PLAN-15**.
None was running, so no conflict materialised — but the gate would have paired PLAN-02 with
any of them and been wrong. This is the third consecutive landing to under-declare.

**2. The `doc/` exclusion was violated.** The spec did not merely omit `doc/adr/` — it
positively asserted it would not touch `doc/`. Two ADRs shipped there. This is the
`adr-propose`-fires-on-every-plan hazard the epic's queue annotations already record, now
observed for the third time (PLAN-01, PLAN-14, PLAN-02).

**3. `references.json` `affected_files` under-counts the plan's own work.** It lists **14**
files against the diff's **19**, omitting `Cookie.java`, `CookieTest.java`,
`URLParameterValidationPipelineTest.java` and both ADRs. This matters beyond bookkeeping:
`affected_files` is what `corpus cross-check` reads as a *live plan's* surface, so a running
plan's collision footprint is understated at the source the gate trusts most.

PLAN-02's `## Expected Surface` has been corrected in place to the realized 19-file set, per
the same-act rule — the spec is shipped, so this is for the audit record and for the
under-declaration measurement, not for a future gate decision.

## Metrics and Anomalies

- **Tokens**: 5,276,918 total (spans populations). By phase: 6-finalize **3,228,633** (61%),
  3-outline 709,877, 5-execute 686,237, 4-plan 363,597, 2-refine 246,682, 1-init 41,892.
- **Duration**: 13h28m wall, of which **9h12m idle** (68%). 4h15m worked. Finalize alone:
  8h57m wall, 6h41m idle.
- **Tests**: 7,954 green, up from 7,925 (+29).
- **Anomalies**:
  - **5-execute was re-entered** (`close_count > 1`) — a loop-back, consistent with the
    defect found by the finalize security audit.
  - **Idle dominates the run.** 6h41m of finalize idle is the CodeRabbit wait loop; four
    90-minute quota waits plus one PR recreate. The epic has now lost ≈16h of wall-clock to
    this bot across three plans (≈6.5h PLAN-01, ≈3h PLAN-14, ≈6.7h PLAN-02) and the cost is
    *rising*, not falling.
  - Finalize consumed more tokens than every other phase combined.

## Routing and Merge Behavior

- **Review**: three defects were found **after** Q-Gate, pre-submission self-review and a
  7,948-test suite had all passed over two of them.
  1. *Finalize security audit* — the new unconditional C0 guard covered header types but not
     cookie types; `lenient()` admitted VT/FF/HTAB into a cookie name.
  2. *CodeRabbit* — `COOKIE_NAME` mapped to `cookie-octet`, which permits `=`, so a cookie
     name `a=b` smuggles a second `name=value` boundary past `Cookie.hostPrefix` /
     `securePrefix`. RFC 6265 §4.1.1 makes cookie-name an RFC 7230 token. **This inverted an
     answer settled at refine time.**
  3. *CodeRabbit* — `DecodingStage` deferred to `allowControlCharacters()` for `URL_PATH`
     while the character stage rejects C1 unconditionally, so `%C2%85` survived under
     `lenient()` where the raw byte never could. CWE-177 class; the subject of ADR-0017.

  All three are the same shape: **a guard written for one `ValidationType` set and not
  extended to its sibling, with the adjacent guard already using the broader predicate.**
- **CI/merge**: green; squash-merged via the platform merge queue. **Two PRs**: #216 was
  opened, reviewed, then **closed unmerged** (`gh pr view 216` → `CLOSED`, never merged);
  #217 opened on the same branch and merged. Any epic record keyed on #216 is wrong.
- **Review-bot refusal class change** — the operative discovery. CodeRabbit's refusal
  silently changed from time-bounded ("next review in 25/32 minutes") to unconditional
  ("does not re-review already reviewed commits"). Waiting only answers the first class; the
  second needed a fresh PR. This is why the merged PR is #217.
- **Operator judgement call, flagged and accepted**: CodeRabbit's review covers `0df4bb5`;
  merge HEAD `81352b7` adds only the two ADR files. The plan treated the mandate as satisfied
  rather than spending further waits on a docs-only delta. The orchestrator concurs — the
  delta is two new documents with no code path — and records it here so the decision is
  visible rather than implicit.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-02 --status shipped`
- [x] row `pr` stamped `#217` — `queue --set-row PLAN-02 --field pr`
- [x] row `landing` stamped — `queue --set-row PLAN-02 --field landing`
- [x] row `plan_marshall_plan_id` stamped `character-set-and-control-characters`
- [x] epic.md reconciled from status.json; START-HERE and Ordered Queue regenerated
- [x] PLAN-02 `## Expected Surface` corrected to the realized 19-file set
- [x] **Open Defect opened** — ⛔ live ADR-0019 collision with PLAN-06 (see Follow-Ups)
- [x] **Open Defect opened** — `doc/adr/README.md` again missing rows (0019, 0020)
- [x] **Open Defect opened** — landing message carried no `landing-facts` block
- [x] **Open Defect folded** — `references.json` `affected_files` under-counts (recurrence)
- [x] **Watch retired** — the WS-05 re-baselining watch gains PLAN-02 as a second mover
- [x] resume_anchor updated

## Follow-Ups

- ⛔ **A THIRD ADR-number collision is LIVE, not historical.** `main` carries
  `0019-Cookie_character_sets_are_split_by_RFC_role…adoc` (this plan).
  `origin/feature/plan-06-forwarded-trust-model` carries
  `0019-Unresolvable_Forwarded_header_suppresses_only_the_fields_it_carried.adoc`. Verified by
  `git ls-tree -r origin/feature/plan-06-forwarded-trust-model doc/adr/`. PLAN-06 is still
  running and **must renumber to 0021 before its PR merges** — 0019 and 0020 are taken. This
  is precisely the trap the epic's queue annotations flagged when PLAN-02 and PLAN-06 were
  emitted together, and it fired within one plan of the warning. → epic Open Defect.
- ⛔ **`doc/adr/README.md` was not updated for ADR-0019 or ADR-0020.** The index still reads
  "highest allocated number is **18**". PR #213 fixed exactly this gap for the previous wave
  four days ago and it recurred immediately — evidence the index is structurally unmaintained
  rather than occasionally forgotten. → epic Open Defect; **PLAN-13 deliverable 4 reopens**
  with a wider remit than the one it just closed.
- **The landing message carried no `landing-facts` block** — `inbox landing-check` reported
  `complete: false` with all 8 required keys missing. Every fact in this report was recovered
  by hand from git, the PR and the archived plan tree. → epic Open Defect.
- **PLAN-13 carry-forward, folded into its spec**: the decided character sets are query =
  unreserved + `?&=!$'()*+,;` + `/ : @`; cookie-name = RFC 7230 token; cookie-value = RFC 6265
  cookie-octet with DQUOTE rejected outright; C0/C1 unconditional for header and cookie types;
  `allowExtendedAscii` default false gating 160–255 (and all Unicode >255 for `HEADER_VALUE` /
  `BODY`).
- **Four candidate-lessons promoted** to the global corpus (`-001`…`-004`): the
  conjunctive-question settlement anti-pattern, the sibling-set guard asymmetry, the
  review-bot refusal-class change, and the non-resumable elapsed-time wait. All four carry a
  `plan-marshall:*` component, so all four join the existing parked-lessons defect.
- **The both-preset regression rule (ADR-0017) earned its place again** — both CodeRabbit
  defects were invisible under `defaults()` and observable only under `lenient()`.
- **Project-config gaps for a `/marshall-steward` pass** (reported, not corroborated by the
  orchestrator): `marshal.json` `skill_domains.java` has empty `file_globs` with
  `always_on: false`, which dropped all four domains to zero under `domain-narrow`; and
  `system.provisioned_version` is stale at 0.1.1619 against installed 0.1.1620.

# PLAN-22: Document the Redirect API, Minimize the ADR Corpus, Clear the Last Doc Overclaims

epic: quality-report-remediation
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no brief.
>
> ⚠️ **WS-05 was closed after PLAN-15.** This plan reopens it because PLAN-17 shipped a public
> security API into the client package after that closure, and no documentation followed it.

## Objective

Document `RedirectPolicy` — a shipped, security-relevant public API with **no user-facing
documentation at all** — and clear the two remaining verified doc overclaims in this repository.
`doc/client-handlers-readme.adoc` is the client stack's user-facing document and mentions redirects
**zero times**, while `RedirectPolicy` and `CredentialForwarding` appear in `doc/` only inside
`LogMessages.adoc`, incidentally, via the `HTTP-117` record.

⛔ **This is absent coverage of a security control, not stale prose.** A caller reading the client
documentation today cannot learn that redirects are followed at all, that the default is same-origin,
that an opt-in host allowlist exists, or that credential forwarding is strategy-configurable. The
epic spent a whole plan establishing that behaviour and left its only description in the Javadoc.

Alongside it, **review all fourteen ADRs and minimize the corpus where minimizing is defensible.**
The set has grown to 1,603 lines across 14 records with **none ever accepted** — every one is still
`Proposed` — while at least three genuinely-settled decisions from PLAN-17 have no record at all. A
corpus that is simultaneously **unpruned and incomplete** is not serving as a decision record.

## Deliverables

1. **Document the redirect API in `doc/client-handlers-readme.adoc`.** Cover: that redirects ARE
   followed (the document currently implies nothing on the subject); the **same-origin default**
   (scheme + host + port); the **opt-in host allowlist** and how a caller configures it; the bounded
   hop count and what happens when it is exceeded; the preserved **HTTPS→HTTP downgrade refusal**;
   and `CredentialForwarding` with `STRIP_ON_CROSS_ORIGIN` as the secure default.
   ⛔ **State the security posture, not just the API surface.** The reason the default is same-origin
   is that a redirect destination is a host the caller never named — that rationale is the load-bearing
   part for a reader deciding whether to opt in. ⛔ **Do not document the allowlist without documenting
   what opting in gives up.**
2. **Correct `CLAUDE.md`'s absolute-URL overclaim.** Line 41 reads
   *"`URLPathValidationPipeline`: All URL validation (paths, **full URLs**, directory traversal, CVE
   exploits)"*. PLAN-04 (PR #180) corrected this same claim in
   `security/pipeline/package-info.java` and explicitly reported `CLAUDE.md` as an out-of-scope
   residue. ⛔ **Read the corrected `package-info.java` wording first and align to it** — the point is
   that the two agree, not that `CLAUDE.md` gets some new phrasing of its own.
3. **Correct the four SLF4J-style logger examples.** Four `package-info.java` files show
   `log.warn("… {}", …)` while this codebase's `CuiLogger` uses `%s` — measured at HEAD: **15 `%s`
   uses in real logging code against 1 `{}`**. The examples teach the wrong form to every reader who
   copies them. Sites: `client/result/package-info.java`:58,
   `security/pipeline/package-info.java`:78, `security/core/package-info.java`:39,
   `security/exceptions/package-info.java`:52 (three placeholders on that one).

4. **Apply a value test to all fourteen ADRs and REMOVE the ones that fail it.** Removal means
   `git rm` — the file is deleted, not tombstoned. Git history is the archive, so a removed ADR stays
   recoverable and no `Superseded` stub is left behind.

   **The value test, applied to each record individually:**

   > **Does this ADR stop a future maintainer from reversing a deliberate choice they would otherwise
   > think was obviously right?**

   An ADR passes when its `Alternatives Considered` names a plausible option and says **why it was
   rejected in this codebase's terms**. It fails when it restates an industry default, records a
   consequence nobody would think to change, or documents a choice that has no plausible alternative.

   ⛔ **The orchestrator applied this test to all fourteen before staging, and it REFUTED its own
   earlier candidates** — see Claim Labels. **The expected outcome is that few or no ADRs are
   removed.** Do not treat "minimize" as a quota: a pass that removes nothing, with the test shown
   applied per record, is a valid and probably correct result.

   Three hard constraints:

   - **RENUMBER to close any gap, and update every reference in the same commit.** After a removal the
     survivors are renumbered to a contiguous `0001…000N`. ⛔ **The reference set is small, fully
     enumerable, and was swept at HEAD — treat this list as the checklist, and re-derive it rather
     than trusting it:**

     | Reference site | Count at HEAD | Form |
     |---|---|---|
     | ADR filenames | 14 | `NNNN-Title_with_underscores.adoc` — the number is *in the filename*, so renumbering means `git mv` |
     | `= ADR-NNNN:` level-0 headings | 14 | one per file; must agree with its own filename |
     | Inter-ADR `xref:` links | **8** | `xref:NNNN-Full_Filename.adoc[ADR-NNNN: Title]` — carries **both** the filename *and* the number, so **both halves need updating** |
     | `doc/adr/README.md` index rows | 14 | `\| N \| [Title](NNNN-Full_Filename.adoc) \| Proposed \|` — the ordinal column *and* the link |
     | Citations outside `doc/adr/` | **1** | `PathParameterTraversalAttackDatabaseTest.java`:101 — *"Structural claim behind ADR-0009"* |

     The 8 inter-ADR links are: 0001→{0002,0003}, 0002→{0001,0003}, 0003→{0001,0002}, 0005→0004,
     0009→0011.

     ⛔ **Prove it afterwards, do not assert it.** After renumbering, re-run the sweep
     (`grep -rnoE 'ADR[- ]?[0-9]{4}'` across `*.java`, `*.adoc`, `*.md`, excluding `.plan/`) and
     confirm every citation resolves to an existing file whose own heading matches. **A renumber
     verified only by reading the diff is not verified** — the failure mode is a link that still
     parses and now points at the wrong decision.

     ⚠️ **Why this is safe here, though PLAN-19 just fixed ADR numbering:** PLAN-19's defect was
     *duplicate* numbers — two files claiming `0004`, an ambiguity. A renumber that closes a gap
     creates no ambiguity, and the reference set above is 37 sites in 15 files, all mechanical.
     ⛔ Note that `.plan/` records (this spec, landing reports, the epic ledger) cite old ADR numbers
     **as historical fact** and are deliberately NOT updated — they record what was true when written.
   - ⛔ **Do not change any ADR's `Status`.** All fourteen are `Proposed`; **whether to accept them is
     an open operator ruling** this epic has recorded and not resolved. Removing a record for
     low value is a different act from accepting or rejecting its content — do the first, never the second.
   - **Update `doc/adr/README.md`** — its 14-row index is hand-maintained with no drift check, so any
     removal must be reflected there in the same commit.

Four deliverables — under the split guard.

## Claim Labels

- OBSERVED: `doc/client-handlers-readme.adoc` contains **zero** occurrences of "redirect"
  (case-insensitive) at HEAD `821cd34`.
- OBSERVED: `RedirectPolicy` and `CredentialForwarding` appear in exactly **one** file under `doc/` —
  `doc/LogMessages.adoc` — and only via the `HTTP-117` log record, not as API documentation.
- OBSERVED: `CLAUDE.md`:41 still reads *"All URL validation (paths, full URLs, directory traversal,
  CVE exploits)"*.
- OBSERVED: four `package-info.java` files carry `{}` placeholders in logger examples, at the lines
  named in deliverable 3. Real logging code at HEAD uses `%s` 15 times against 1 `{}`.
- OBSERVED: `SecureSSLContextProvider.createHostnameRelaxedSSLContext()` — which the readme documents
  at :113 — **still exists** at `SecureSSLContextProvider.java`:193. That part of the readme is
  accurate and needs no change.
- OBSERVED (refuted candidate, recorded so it is not re-derived): **the test-class inventory is NOT
  drifting.** `doc/http-security/specification/testing.adoc` carries a **package-level** inventory,
  and its `SecurityValidationTest` / `ComprehensiveSecurityTest` mentions are **illustrative code
  examples**, not entries. ⛔ **ADR-0004 decided this deliberately** — *"documentation that describes a
  set of code artifacts names the package and points at the tree; it does not enumerate the members …
  the source tree is the inventory, and it is authoritative by construction."* New test classes
  therefore create no drift. **Do not add per-class enumerations; that would violate a landed ADR.**
- OBSERVED (refuted candidate): `doc/http-result-pattern.adoc`'s PLAN-08 debt appears **already
  settled** — line 231 documents `failureWithFallback(message, cause, fallback, category, etag,
  status)` carrying both etag and status. Not in scope.
### The ADR review — the orchestrator's seed, and its own refutation

- OBSERVED: `doc/adr/` holds **14 ADRs, 1,603 lines**, all `Status: Proposed`. **Seven** inter-ADR
  `xref:` links exist, in two clusters: the forwarded trio (0001↔0002↔0003, six links) and 0005→0004.
- OBSERVED: **every one of the fourteen carries a populated `Alternatives Considered` section** naming
  at least one rejected option with codebase-specific reasoning. That is the value test's own criterion,
  and the corpus satisfies it uniformly.
- ⛔ OBSERVED (**a self-refutation, recorded so it is not repeated**): the orchestrator first nominated
  **three** removal/merge candidates from titles and line counts, then read their `Alternatives`
  sections and **withdrew all three**:
  * **0008** was called "an industry-standard semver convention". It is not. Its rejected alternative
    is that **a plan-level `compatibility: breaking` setting does NOT authorize breaking a
    Maven-Central-published signature** — a precedent about the planning system's *authority boundary*
    over an external contract. That is project-specific and non-obvious. **Keep.**
  * **0004 + 0005** were called mergeable doc conventions. Both carry three substantively rejected
    alternatives (regenerate from the tree / verify in CI / periodic sweep; and review / sweep /
    freeze structure). 0004 is additionally **load-bearing for this very plan's deliverable-3 scoping**.
    **Keep both**; 0005 is the corpus's weakest record and the only one still worth a second look.
  * **0006** was called "a consequence, not a decision". Its alternatives show a real architectural
    trade-off: counting would widen the stage's construction contract **for every caller**, and logging
    would require new `LogMessages` infrastructure for a package that has none. It exists to stop
    someone "fixing" a deliberate gap at disproportionate cost. **Keep.**
- OBSERVED: the remaining ten are each load-bearing against a plausible wrong turn — nearest-hop vs
  leftmost token (0001), fail-closed on family disagreement (0002), returning every repeated header
  rather than the first (0003), a delegating trust manager rather than the JVM-wide
  `disableHostnameVerification` lever or a trust-all manager (0007), structural verification rather
  than re-asserting a short-circuited pipeline verdict (0009), one fold registry rather than parallel
  per-class assertions (0010), the wire-form/decoded-form stage split (0011), refusing to turn
  `caseSensitiveComparison` into a dead public knob (0012), `when()` skipping rather than rejecting
  (0013), and RFC-correct clamping rather than a contract read as "rejects all traversal" (0014).
- OBSERVED (swept at HEAD `821cd34`): the **complete** ADR-reference set is **37 sites across 15
  files** — 14 filenames, 14 `= ADR-NNNN:` headings, 8 inter-ADR `xref:` links (each carrying both a
  filename and a number), 14 `README.md` index rows, and **exactly one** citation outside `doc/adr/`
  (`PathParameterTraversalAttackDatabaseTest.java`:101). ⛔ **Re-derive this set rather than trusting
  it** — a commit landing first changes it.
- HYPOTHESIS: **the correct outcome of this deliverable is to remove nothing, or at most ADR-0005** —
  confirm/refute by applying the value test to each record's own `Alternatives` section
  (verify-at-outline). ⛔ **Re-derive rather than inheriting this verdict**: the orchestrator's first
  pass was wrong, which is precisely why its second pass is a seed and not an instruction.
- Verify-first clause: ⛔ **A removal needs a positive account of what is lost and why that is
  acceptable** — not "low value" as a bare verdict. Name the alternative the ADR rejects, and show
  that either no maintainer would plausibly choose it or the reasoning survives elsewhere. **An ADR
  whose rejected alternative is genuinely tempting stays, however short it is.**
- OBSERVED (**the corpus is incomplete as well as possibly over-full**): PLAN-17 settled three
  ADR-worthy decisions — the redirect loop placement, same-origin-by-default egress policy, and the
  `CredentialForwarding` strategy — and **none has a record**, because `adr-propose` carried
  `lane: off`. ⚠️ **Writing them is NOT this plan's deliverable** (it is a separate owed pass recorded
  in the epic). Report the gap in the landing; do not absorb it.

- HYPOTHESIS: `doc/client-handlers-readme.adoc` needs no other correction beyond the redirect
  addition — its TLS floor, `verifyHostname(false)` and `SecureSSLContextProvider` sections were
  spot-checked as accurate but not read in full (verify-at-outline).
- Verify-first clause: ⛔ **Read `RedirectPolicy.java` and `HttpHandler`'s redirect loop as the source
  of truth, not PLAN-17's landing narrative or this spec.** Document what the code does. In
  particular confirm, rather than assume: the default hop bound, the exact same-origin comparison
  (scheme + host + port, and the case-sensitivity of each), and that `CredentialForwarding` changes
  **only what the client carries, never where it talks** — that last is the claim a security reader
  will lean on hardest.

## Expected Surface

- OBSERVED: `doc/client-handlers-readme.adoc` — the redirect section
- OBSERVED: `CLAUDE.md` — line 41
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/client/result/package-info.java` — :58
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/pipeline/package-info.java` — :78
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/core/package-info.java` — :39
- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/exceptions/package-info.java` — :52
- OBSERVED: `doc/adr/` — the fourteen ADR records reviewed; only those with an applied verdict are edited
- OBSERVED: `doc/adr/README.md` — the hand-maintained index; its ordinal column AND its links change on a renumber
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/tests/PathParameterTraversalAttackDatabaseTest.java` — :101, the **only** ADR citation outside `doc/adr/` (*"Structural claim behind ADR-0009"*). ⚠️ It is a **Javadoc comment only**; touching it changes no behaviour and does not breach the no-production-code rule, which this plan's surface bullet below scopes to `src/main/java`
- ⛔ **No, not this plan's surface:** `doc/http-security/specification/testing.adoc` and
  `doc/test-framework-structure.adoc` — the inventory is package-level by ADR-0004 and is NOT drifting.
- ⛔ **No, not this plan's surface:** `doc/http-result-pattern.adoc` — its PLAN-08 debt is settled.
- ⛔ **No, not this plan's surface:** any file under `cui-http-core/src/main/java/de/cuioss/http/client/` or
  `cui-http-core/src/main/java/de/cuioss/http/security/` **other than** the four `package-info.java`
  files named above — and in those, only the Javadoc examples. **This plan changes
  no behaviour.** A documentation correction that requires a code change has found a real defect:
  report it in the landing and re-scope rather than absorbing it.

## Dependencies and Sequencing

- Depends on: PLAN-17 (shipped, PR #186) — deliverable 1 documents what it shipped. PLAN-04 (shipped,
  PR #180) — deliverable 2 aligns `CLAUDE.md` to the correction it landed.
- Overlaps with: none. This is the epic's only live plan.
- ⚠️ **WS-05 is reopened by this plan.** Its charter is documentation truthfulness; PLAN-14, PLAN-15
  and PLAN-19 shipped under it and it was closed after PLAN-15. Reopening is deliberate: the debt
  accrued *after* the closure, from plans in other workstreams.
- ⛔ **Adjacent — three doc-truth rules this epic learned the hard way, all applicable here.**
  (a) A remediation artifact is part of the set it remediates: text this plan *adds* is subject to the
  same accuracy bar as the text it corrects. (b) **A universal quantifier is a checkable assertion** —
  if the redirect section says "every", "always" or "never", name the population and verify it; that
  exact class shipped a false Javadoc claim in this repo before. (c) **Naming a mechanism is necessary
  and not sufficient** — state its quantifier, e.g. the allowlist governs *registered hosts* and says
  nothing about the rest.
- ⛔ **Adjacent — the ADR number space has a live, unowned hazard.** There is still **no ADR-number
  allocator**, and `default:adr-propose` declares no `mutates_source` (two recurrences, no fix), so a
  concurrently-running plan that proposes an ADR can collide with this one and its output can be
  destroyed with the worktree. Both are plan-marshall surfaces recorded in `pm-findings.md`. **Prefer
  to run this plan when no other plan's finalize is expected to propose an ADR**, and if this plan's
  own finalize proposes one, check the worktree for uncommitted `.adoc` files before `branch-cleanup`.
- Adjacent to: `doc/LogMessages.adoc` — carries the `HTTP-117` record and is accurate. ⛔ Do not edit
  it; if the redirect documentation needs to reference a log record, xref it.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-22-redirect-api-documentation.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

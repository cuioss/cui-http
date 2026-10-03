# PLAN-18: Semicolon Dot-Segment Traversal Bypass

epic: quality-report-remediation
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no brief.

## Objective

Close the `..;/` path-traversal bypass. `/..;/..;/etc/passwd` is **accepted** by
`URLPathValidationPipeline` under `SecurityConfiguration.defaults()`. This is the known
Tomcat/Spring path-parameter traversal class: a servlet container strips the `;`-introduced path
parameter and resolves `..;` as `..`, so a payload this library passes as clean reaches the
container as a live traversal. Unlike the sibling `file:`/`/etc/` finding, this one has **no
by-design defence** — nothing in the codebase decided to allow it; no pattern covers it.

Surfaced by PLAN-13's deliverable-1 payload probe (PR #178) and independently re-verified by the
orchestrator at HEAD.

## Deliverables

1. **Detect `..;` as a traversal signature.** The fix must be scoped by *mechanism*, not by literal:
   the bypass is the container's path-parameter stripping, so `..;` is one instance of a family
   (`..;`, `..;foo=bar`, and percent-encoded forms of the `;`). ⛔ **Enumerate the family, then
   decide the detection point** — a single literal added to a pattern set is the shape of fix this
   epic has already had to redo.
2. **Decide and record WHERE it is detected** — `PatternMatchingStage` (a pattern-set addition),
   `NormalizationStage` (treating `..;` as a dot segment during RFC 3986 resolution), or
   `DecodingStage`. See the verify-first clause; the three have different failure types and
   different blast radii.
3. **A failure type that names what happened.** Reuse the existing traversal failure type if the
   detection point already emits one; do not add a type that duplicates an existing meaning.
4. **Attack-database entries** for the family, in the existing `security/database/` structure, so the
   bypass is regression-covered by the same machinery as every other attack class. ⛔ **PLAN-13
   established that a database entry must be structurally verified to exercise the mechanism it
   names** (ADR-0009, `0009-Attack-database_entries_verified_structurally…`) — an entry that is
   rejected by an earlier stage for an unrelated reason does not test this bypass.
5. **Tests at the pipeline level**, asserting the typed failure and not merely that something threw.

Five deliverables — under the split guard.

## Claim Labels

- OBSERVED: `/..;/..;/etc/passwd` is accepted under `SecurityConfiguration.defaults()`. Source:
  PLAN-13's deliverable-1 mandatory payload probe, PR #178.
- OBSERVED: **No pattern anywhere in `SecurityDefaults.java` contains `..;`** — verified by grep at
  HEAD, count 0. `PATH_TRAVERSAL_PATTERNS` at `SecurityDefaults.java`:103 enumerates `../`, `..\`,
  `..\/` and percent-encoded forms only.
- OBSERVED: `..;` is **not** an RFC 3986 dot segment, so `NormalizationStage`'s dot-segment
  resolution does not collapse it either. Both detection surfaces miss it for different reasons, and
  that is why it passes.
- OBSERVED: The `;` character is not itself rejected in a path — `CharacterValidationConstants.java`:173
  lists `;` among the RFC 3986 path sub-delims, so `;` is a legal path character and the fix cannot
  be "reject semicolons in paths" without breaking legitimate input.
- OBSERVED: Traversal detection is spread across `SecurityDefaults.java`,
  `SecurityConfigurationBuilder.java`, `PatternMatchingStage.java`, `NormalizationStage.java` and
  `UrlSecurityFailureType.java`. ⛔ PLAN-04's deliverable 5 added a Javadoc note that traversal
  detection is **duplicated across three sites with divergent failure types** — read that note before
  choosing a detection point, and do not add a fourth site.
- HYPOTHESIS: `ADR-0010` (`0010-NormalizationStage_clamps_root-consumed_dot-segments_and_skips_rewriting_scheme-bearing_input`,
  landed by PLAN-04, status `Proposed`) constrains what `NormalizationStage` may rewrite and may
  therefore rule out detection point (b) — confirm/refute by reading that ADR (verify-at-outline).
- Verify-first clause: ⛔ **The detection point is NOT chosen in this spec and must be settled at
  outline against the implementing source.** Read all three candidate stages and name, for each, what
  it would catch and what it would miss. ⛔ **Re-confirm the bypass still reproduces at HEAD before
  scoping** — PLAN-04 (PR #180) and PLAN-13 (PR #178) both landed in this tree after the probe, and
  this epic has already had one finding (`java:S5778`) reported gone while it was live, and another
  (the Turkish-locale claim) asserted while it was false. Reproduce it; do not inherit it.

## Expected Surface

- OBSERVED: `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityDefaults.java` — `PATH_TRAVERSAL_PATTERNS` at :103
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/PatternMatchingStage.java` — detection point (a) (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/NormalizationStage.java` — detection point (b), gated on the ADR-0010 check (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/validation/DecodingStage.java` — detection point (c) (verify-at-outline)
- HYPOTHESIS: `cui-http-core/src/main/java/de/cuioss/http/security/core/UrlSecurityFailureType.java` — only if deliverable 3 needs a new type (verify-at-outline)
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/database/`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/`
- OBSERVED: `cui-http-core/src/test/java/de/cuioss/http/security/validation/`
- ⛔ **No, not this plan's surface:** `cui-http-core/src/main/java/de/cuioss/http/security/config/SecurityConfigurationBuilder.java` — the sibling `failOnSuspiciousPatterns` finding lives there and is a **separate, undecided** question (it may be intended design: the field's Javadoc reads "suspicious (**non-attack**) patterns"). Do not fold it in.

## Dependencies and Sequencing

- Depends on: none. PLAN-01…04 (WS-01) have all shipped.
- Overlaps with: none staged. PLAN-17 is `client/` only and PLAN-19 is `doc/adr/` only, so all three
  are pairwise disjoint and may run concurrently.
- Adjacent to: the `/file:///etc/passwd` finding — `file:` and `/etc/` ARE in
  `SUSPICIOUS_PATH_PATTERNS` (`SecurityDefaults.java`:124–129) but
  `SecurityConfigurationBuilder.java`:102 sets `failOnSuspiciousPatterns = false` and
  `DEFAULT_CONFIGURATION` is a bare `builder().build()`, so that gate is inert by default. ⛔ **That
  is a separate operator decision and is explicitly OUT of this plan's scope.** Report it in the
  landing if the work touches the same reasoning; do not change the default.
- Adjacent to: `doc/http-security/specification/specification.adoc`, which the source reports verify
  against the pipeline stage orderings. If a detection point changes a documented stage order, flag it
  in the landing rather than editing it — WS-05 is closed.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/local/orchestrator/quality-report-remediation/plans/PLAN-18-semicolon-dot-segment-traversal.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/local/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

envelope_version=1
sender_type=plan
sender_id=character-set-and-control-characters
epic=quality-report-remediation
kind=landing
created=2026-09-08T05:22:10Z

# Landing: PLAN-02 Character Sets and Control Characters

**Outcome:** merged. PR #217 squash-merged into `main` via the platform merge queue at
2026-09-08T05:15:41Z. Base `main` now at `bdcb36e`.

**Note for the ledger:** this plan shipped under PR **#217**, not the first PR it opened. PR #216 was
opened, reviewed, and then closed UNMERGED — see "Review-bot handling" below. Any epic record keyed on
#216 should be re-pointed at #217.

## ADRs allocated

Both were allocated against `origin/main` and re-checked immediately before commit, per the
carried PLAN-14 guard. Neither surface was declared in this plan's spec, which is exactly why they
are named here.

- **ADR-0019** — Cookie character sets are split by RFC role with no DQUOTE quote-pair carve-out
- **ADR-0020** — Header and cookie character gates ignore preset flags when no later stage can
  re-check them

## Deliverables

All 5 shipped, 9 tasks, TDD throughout. Full `cui-http-core` suite green at 7954 tests (from 7925).

1. Query and cookie character sets match their cited RFCs. `RFC3986_QUERY_CHARS` widened with
   `/ : @`; new `RFC6265_COOKIE_OCTET` for cookie values.
2. `allowExtendedAscii` default flipped `true` -> `false` (operator decision, breaking); C1
   (128-159) rejected unconditionally; decoded Cf and non-ASCII Zs rejected.
3. All C0 controls rejected in header AND cookie names/values under every preset.
4. Path-segment block-list scoped to `URL_PATH` only.
5. Two hollow stage tests given real, mutation-verified assertions.

## Scope deltas against the spec

- The decoded structural-delimiter rule the spec anticipated collapsed to **one character** (`#` in
  `PARAMETER_NAME`). Filtering the five candidate delimiters against the library's own
  legitimate-input corpus and its asserted test contracts showed `[`/`]` are legitimately
  percent-encoded, `\` is already owned by `PatternMatchingStage`, and `?`/`#` in paths by
  `NormalizationStage`. The raw-vs-encoded asymmetry was largely already closed; D1's value is the
  character-set corrections themselves.
- `COOKIE_NAME` ended on `RFC7230_TOKEN_CHARS`, not `cookie-octet` — see below.
- Three describe-side files not in the spec's Expected Surface were touched
  (`config/package-info.java`, `SecurityConfiguration.java`, `CharacterValidationConstantsTest.java`),
  plus `Cookie.java` / `CookieTest.java` / `URLParameterValidationPipelineTest.java`, which pinned
  the old sets.

## Defects found after the plan's own gates passed

Three, each found by a different gate, all in this plan's own work:

1. **Finalize security audit** — the new unconditional C0 guard covered header types but not cookie
   types, so `lenient()` admitted VT/FF/HTAB into a cookie name.
2. **CodeRabbit** — `COOKIE_NAME` mapped to `cookie-octet`, which permits `=`, so a cookie name
   `a=b` could smuggle a second name=value boundary past `Cookie.hostPrefix`/`securePrefix`. RFC 6265
   §4.1.1 makes cookie-name an RFC 7230 token; only cookie-value is cookie-octet. This inverts an
   answer settled at outline time.
3. **CodeRabbit** — `DecodingStage` deferred to `allowControlCharacters()` for `URL_PATH` while the
   character stage rejects C1 unconditionally, so `%C2%85` survived under `lenient()` where the raw
   byte never could. CWE-177 class, the subject of ADR-0017.

Q-Gate, pre-submission self-review and a 7948-test suite all passed over (2) and (3).

## Review-bot handling

CodeRabbit's review was mandatory by operator policy. It cost four 90-minute quota waits and one PR
recreate. The operative discovery: its refusal silently changed class from a time-bounded quota
window ("next review in 25/32 minutes") to an unconditional "does not re-review already reviewed
commits". Waiting only helps the first class; the second needed a **fresh PR** (#216 closed unmerged,
#217 opened on the same branch) to give the incremental reviewer an unreviewed target. It then
reviewed and raised the two defects above.

Sourcery is on a hard quota until ~2026-09-14 and is optional, so it did not gate.

## Carry-forward for sibling plans

- **WS-06 PLAN-13** owns reconciling the requirement prose to the character sets this plan decided.
  The decided state is: query = unreserved + `?&=!$'()*+,;` + `/ : @`; cookie-name = RFC 7230 token;
  cookie-value = RFC 6265 cookie-octet with DQUOTE rejected outright; C0/C1 unconditional for header
  and cookie types; `allowExtendedAscii` default false gating 160-255 (and all Unicode >255 for
  `HEADER_VALUE`/`BODY`).
- **The both-preset regression rule (ADR-0017) earned its place again.** Both CodeRabbit defects were
  invisible under `defaults()` and only observable under `lenient()`. A single-preset regression
  cannot see this class.
- **A new sibling-set rule is worth carrying:** every one of the three defects above was a guard
  written for one `ValidationType` set and not extended to its sibling set, with the adjacent guard
  one line above already using the broader predicate.

## Project-config gaps observed (not fixed here)

- `marshal.json` `skill_domains.java` has empty `file_globs` and `always_on: false`, so
  `domain-narrow` dropped all four domains to zero and the orchestrator had to restore them by hand.
- `marshal.json` `system.provisioned_version` is stale (0.1.1619 vs installed 0.1.1620).

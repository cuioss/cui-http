envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:49:44Z

# Candidate lesson: one requirement stated three times at three specificities, vaguest wording in the field the implementer follows

**Signal source**: `signal_qgate_pending_count` — one defect carried by two records:
Q-Gate finding `f24645` (phase `3-outline`, source `qgate`) and user-review finding
`220afa` (phase `3-outline`, source `user_review`). Both resolved `taken_into_account` by
the same deliverable revision; transmitted as ONE candidate because they name one defect.

## What happened

Deliverable D6 stated its rejection condition three separate times, at three different
specificities:

1. Success Criteria — "document `@throws UrlSecurityException` with the rejection condition"
2. Overview diagram row — "non-unreserved suffix"
3. Change-per-file (the field the implementer actually follows) — the suffix "is validated
   against the permitted cookie-name character set"

Statement 3 is the vaguest of the three: it names no set and is circular as API
documentation ("valid characters are the valid characters"). The clarified request had been
more specific than any of them ("suffixes outside the RFC 3986 unreserved set"). So the
outline contradicted itself about how concrete the Javadoc had to be, and the least usable
wording sat in the one place the implementer reads.

Ground truth verified at HEAD: `Cookie.java:96-99` builds `COOKIE_NAME_VALIDATOR` as a
`CharacterValidationStage` over `ValidationType.COOKIE_NAME` with the default
`SecurityConfiguration`, and `CharacterValidationConstants.java:267` maps
`case COOKIE_NAME, COOKIE_VALUE -> RFC3986_UNRESERVED`, i.e. ALPHA / DIGIT / `-` / `.` /
`_` / `~`. The concrete set was knowable and cheap to cite the whole time.

## Candidate rule

When a normative condition appears in more than one place in a plan document, define it ONCE
canonically — with its ground-truth source cited — and restate it verbatim everywhere else.
Divergent restatements are not redundancy, they are a silent specificity gradient, and the
implementer follows whichever copy is nearest to the instruction field, which is empirically
the least precise one.

Corollary worth testing across the epic: the review that catches this must compare the
copies against EACH OTHER, not each copy against plausibility in isolation. Each of the three
wordings above reads as acceptable on its own.

## How it was resolved in this plan

D6 now defines one canonical rejection wording in its Root Cause, quoted as a normative
block: "the suffix contains any character outside the RFC 3986 unreserved set — that is,
outside ALPHA / DIGIT / - / . / _ / ~", with ground truth cited as
`CharacterValidationConstants.java:267` and `Cookie.java:96-99`. That identical wording is
reused in the Overview row, the Change-per-file `@throws` instruction (which explicitly
FORBIDS the set-less phrase "the permitted cookie-name character set"), the `CookieTest`
instruction (with concrete out-of-set examples: space, `/`, `%`), and the Success Criteria.

## Why this is a candidate and not a filed lesson

Classification and corpus placement are deferred to the orchestrator-side pickup.

## Provenance

- Plan: `security-api-contract-hardening`
- Finding hashes: `f24645` (qgate), `220afa` (user_review)
- File cited: `cui-http-core/src/main/java/de/cuioss/http/security/data/Cookie.java`
- Recorded 2026-08-29T11:32:14Z / 11:37:33Z, both resolved 2026-08-29T11:43:0xZ

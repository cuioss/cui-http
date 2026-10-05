envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:15:12Z

# Candidate lesson: outline named a symbol that does not exist anywhere in the repo

**Source**: Q-Gate finding `6bad6e` (3-outline, type `triage`, severity `warning`, resolution `taken_into_account`)
**Plan**: plan-07-forwarded-parsing-and-validation
**Component under edit**: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedResolverConfig.java`

## Observation

Deliverable 2 instructed work on "both allow-list entry points (`parseAllowedContextPaths` and
`Builder.allowedContextPaths`)". A literal content sweep for `parseAllowedContextPaths` returned
`count: 0` over 303 files scanned, none unreadable, not truncated, no elision — the symbol does not
exist anywhere in the repository. The real second entry point is
`ForwardedResolverConfig.parseAllowlist(String)` (line 199), whose normalize-and-drop loop is at
lines 204-209.

The substantive claim about behaviour was correct. Only the **name** was invented, so an implementer
searching for the cited symbol finds nothing and has to re-derive the entry point from scratch.

A secondary gap rode with it: the deliverable's test note covered only the `Builder` path, leaving
the public `parseAllowlist` path — whose observable behaviour the same change alters — untested.

## Why this is lesson-bearing

The failure class is **plausible-symbol synthesis in an outline**: a method name that reads exactly
like the codebase's conventions, is semantically correct about what the code does, and is not a real
identifier. It is invisible at the authoring site and only a literal content sweep catches it.

The cheap structural guard is already available and was what caught this: run
`architecture search --content --literal --pattern <symbol>` for every symbol an outline names, and
treat a zero-hit result under complete coverage as a hard authoring error, not a search miss.

A second, separable rule: when a deliverable names N entry points for one behaviour change, the test
note must cover all N, not the most familiar one.

## Suggested disposition (orchestrator judges)

Likely a `plan-marshall:phase-3-outline` authoring rule plus a Q-Gate detector
("every symbol an outline names must resolve under a complete-coverage content sweep"). Broad
cross-plan value — this is not specific to cui-http.

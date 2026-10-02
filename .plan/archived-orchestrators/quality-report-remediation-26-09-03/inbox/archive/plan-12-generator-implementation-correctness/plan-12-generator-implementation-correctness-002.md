envelope_version=1
sender_type=plan
sender_id=plan-12-generator-implementation-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T19:35:42Z

# Candidate lesson: a review bot's finding was valid but its stated CAUSE was wrong

## What happened

CodeRabbit correctly flagged the `U+2215 DIVISION SLASH` defect on PR #171 (see the sibling
candidate-lesson message for that defect). Its stated cause, however, was wrong: the comment
claimed the `U+2024 ONE DOT LEADER` entries ALSO lack the NFKC decomposition, i.e. that the whole
lookalike family was broken.

That is false. `U+2024` does NFKC-normalize to `.`, and `U+FF3C FULLWIDTH REVERSE SOLIDUS` does
normalize to the backslash. Only the forward-separator constant was wrong.

## Why it mattered

Acting on the bot's stated cause rather than on the verified fact would have rewritten three
constants and their signatures instead of one, expanding a one-character fix into a family-wide
rewrite of a generator whose contract tests other suites depend on. Independently checking each
claimed codepoint against `Normalizer.normalize(..., Form.NFKC)` kept the fix narrow to the single
genuinely-broken constant.

## Root cause

A review bot's *finding* and its *explanation* have different reliability. The finding is anchored
to a diff hunk it actually read; the explanation is generated reasoning about why, and can
over-generalise from one observed instance to a family. The two arrive in one comment and read as
one claim.

## Candidate rule

Treat a review bot's finding as a lead to verify and its stated cause as a hypothesis to test
separately. When the stated cause implicates more sites than the finding points at, verify each
additional site independently before widening the fix — the cheapest verification (here: one
`Normalizer.normalize` call per codepoint) is almost always cheaper than the unnecessary edit.

Corollary for the reply: answer the comment on what was actually verified, so the thread records
the correction to the bot's reasoning rather than silently accepting it.

## Provenance

- Plan: plan-12-generator-implementation-correctness
- PR: cuioss/cui-http#171 (merged), CodeRabbit inline review, fix in ed4943f

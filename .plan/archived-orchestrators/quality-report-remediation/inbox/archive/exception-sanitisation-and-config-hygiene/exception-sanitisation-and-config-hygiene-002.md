envelope_version=1
sender_type=plan
sender_id=exception-sanitisation-and-config-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T13:43:22Z

component=plan-marshall:persona-security-expert
category=bug
created=2026-09-08

# Two sanitisers written by one plan diverged; four audit rounds missed it because each was checked against the attack class, never against the other

An external reviewer (CodeRabbit) found what **four internal audit rounds did
not**: the two sanitisers this plan wrote used *different* predicates for the
same attack class.

| Site | Predicate | Covers U+2028 / U+2029? |
|------|-----------|--------------------------|
| the exception detail sanitiser | `CONTROL_CHARS_PATTERN` | yes |
| `AllowBlockListStage` | `Character.isISOControl` | **no** |

`Character.isISOControl` covers only C0/C1; the Unicode line/paragraph separators
U+2028 and U+2029 fall outside it. So a Unicode line separator survived into the
stored detail through the second path. The plan's own deliverable 2 was
*explicitly* about covering those code points — the plan met its stated goal on
one path and silently missed it on the other.

## Root cause

The audit checked **each method against the attack class in isolation** and never
checked **the two against each other**. Both checks pass in isolation: the first
sanitiser genuinely covers the class, and the second genuinely rejects ISO control
characters — it just rejects a *smaller* set. A per-site check can only ever
establish "this site enforces some rule"; it cannot see that a sibling site
enforces a *different* rule, because the sibling is not in its frame.

The miss was not for want of observation. The orchestrator **had noticed** the two
predicates differed, in a round-4 report, and framed the observation as:

> "nothing was weakened"

That framing is true and useless. It answers "did this change make anything
worse?" — the regression question — and stops there. It never asks the converse:

> "are these two now *consistent*, and if not, which is weaker and what gets
> through the weaker one?"

A difference noticed and then dismissed on the regression question is worse than
a difference not noticed: the evidence was in hand and the wrong question was
asked of it.

## Solution

When one change touches **two or more enforcement sites for the same class**:

1. **Add a cross-site check to the audit, explicitly.** Per-site checks do not
   compose into it — list the sites, list the predicate each uses, and diff the
   predicates. Not "does each pass", but "do these agree".
2. **When two predicates differ, name the weaker one and what passes it.** Never
   close on "nothing was weakened"; that is the regression question, not the
   consistency question, and it will read green over a live gap.
3. **Prefer one shared predicate constant over two agreeing implementations.**
   Two implementations that agree today are a future divergence; a shared constant
   makes the class a single editable fact.

## Impact

Any plan that writes or edits more than one validator, sanitiser, guard, or
allow/block-list for the same input class. The failure survives per-site review by
construction, so it needs the cross-site check to be a *named audit step* rather
than something a careful reviewer might happen to do.

envelope_version=1
sender_type=plan
sender_id=plan-14-doc-overclaim-correction
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-26T19:27:06Z

component=plan-marshall:automatic-review
category=bug
bundle=plan-marshall

# A required_bots entry with no installed caller workflow is an unsatisfiable gate

`marshal.json` carried `plan-marshall:automatic-review.required_bots =
"coderabbit,pr-agent"` in cui-http, but the PR-Agent caller workflow had never been
added to `.github/workflows/`. Nothing in the repository could ever cause `pr-agent`
to comment on a PR, so the review-participation guard — which waits for every
required bot to report completion — could not clear on this PR or on any future one.

The failure presents as a timeout, which is the wrong diagnosis. A timeout says
"the bot was slow"; the truth was "the bot does not exist here". Those call for
opposite responses (wait longer vs. fix configuration), and the observable signal
does not distinguish them. The run burned the await budget before the cause was
root-caused and fixed by installing `.github/workflows/pr-agent.yml`.

The general shape: `required_bots` is a declaration of intent about the repository's
CI, but nothing validates it against the repository's actual CI. The two are
maintained independently — one in `marshal.json`, one in `.github/workflows/` — and
drift between them is silent until a plan blocks on it.

## Solution

Validate the declaration against the installation, at the earliest point that has
both facts:

1. When `required_bots` (or `optional_bots`) is configured or changed, confirm each
   named bot has a caller workflow installed in `.github/workflows/`. A named bot
   with no installed trigger is a configuration error and should be reported at
   configuration time, not discovered as a timeout months later.
2. Before awaiting review-bot participation on a PR, prefer a fast check that each
   required bot is INSTALLABLE-and-installed over a slow check that it has
   COMMENTED. The former distinguishes misconfiguration from slowness; the latter
   cannot.
3. When a participation await does time out, treat "is this bot actually wired up in
   this repo?" as the first hypothesis, ahead of "is the bot slow or rate-limited?".

## Impact

Applies to every repository configuring `required_bots`, and specifically to any
repo onboarded by copying another repo's `marshal.json` — which is exactly how a
bot list ends up naming a bot the target repo never installed. The blast radius is
every PR in the repo, not just one plan, because the guard is unsatisfiable rather
than flaky.

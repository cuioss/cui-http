envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:07:58Z

component=plan-marshall:phase-3-outline
category=anti-pattern
bundle=plan-marshall

# Outline deliverable cited a method that does not exist

Q-Gate validation of the PLAN-07 solution outline (phase `3-outline`) rejected deliverable 2
because it cited a method on the target class that does not exist. The cited name was plausible
for the class's responsibility and consistent with its sibling method names, which is precisely
why it survived authoring: it read as correct to anyone who did not open the file.

## Solution

Every symbol named in a deliverable — class, method, field, constant — must be quoted verbatim
from the file, not reconstructed from the surrounding naming convention. Where the outline
author cannot open the file, the deliverable must say which symbol it expects to find rather
than asserting the symbol exists. A plausible-but-invented symbol name is the same failure class
as an invented script subcommand: it reads naturally in prose and fails at the first real call.

## Impact

All outlines. Symbol invention is self-concealing — the wrongness is invisible at the citation
site and only surfaces when an implementor tries to use it.

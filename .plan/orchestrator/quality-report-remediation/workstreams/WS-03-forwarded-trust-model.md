# WS-03: Forwarded-Header Trust Model and Parsing

epic: quality-report-remediation

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-03-forwarded-trust-model.md` and is tracked in the epic
> `status.json` `workstreams[]` field. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

This workstream owns `de.cuioss.http.forwarded` and carries two of the epic's five High
findings. The review's verdict on this package is split: the arithmetic is right — CIDR
matching, nearest-hop selection and the three-state `Forwarded` model were all verified correct
— but the *trust model above* it is not what the documentation describes. Two de-facto header
families are never reconciled against each other, a single malformed `Forwarded` header from
any client blanks every resolved field, and `trustedProxies` cannot gate whether headers are
believed because the socket peer is never an input. It closes when the resolver's trust
decisions match its documented contract, and where they cannot (the peer address is genuinely
outside the library's reach) the precondition is stated as a hard requirement rather than an
implied guarantee.

## Scope

- In scope: every class in `de.cuioss.http.forwarded` — the resolver, the RFC 7239 parser, the
  header-family handling, host/port reconciliation and validation, `CidrRange`,
  `ForwardedLogMessages`, `sanitizeForLog` — and the package's test suite.
- Out of scope: `de.cuioss.http.security.*` in every form (WS-01, WS-02); the client packages
  (WS-04); the security attack databases and generators (WS-05); `doc/LogMessages.adoc` and
  `doc/forwarded-header-resolution.adoc` prose, which are WS-06's — this workstream changes
  the log *code* and WS-06 reconciles the catalogue document to it.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-06-forwarded-trust-model | staged | Family reconciliation, the fail-toward-`http` scheme defect, and the `trustedProxies` gate |
| PLAN-07-forwarded-parsing-and-validation | staged | Host/port reconciliation, host validation, context-path guard, IPv4-mapped forms, duplicate parameters, log sanitisation |

## Sequencing and Surface Notes

- PLAN-06 must land before PLAN-07: PLAN-06 changes which header family wins and what an
  unresolvable state produces, and PLAN-07's host/port reconciliation fix operates on the
  result of that decision.
- Both plans edit the same resolver class, so they are **strictly sequential** — never a
  concurrent pair.
- This workstream is surface-disjoint from WS-01, WS-02, WS-04, WS-05 and WS-07, so one plan
  from here may run alongside one plan from any of those.
- PLAN-06 is a **behavioural break for integrators**: reconciling the families changes which
  value a deployed application resolves. The plan must decide and record whether the change is
  gated behind configuration or shipped as a documented breaking change, and it is the one
  plan in this epic most likely to need an ADR.

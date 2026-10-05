# PLAN-09 — RETIRED (merged into PLAN-08)

epic: quality-report-remediation
workstream: WS-04

> ↪ **Merged into `plans/PLAN-08-etag-cache-principal-isolation.md` on 2026-09-15 at `d385aa3`
> (cleanup pass, operator-directed redistribution).** PLAN-09 and PLAN-08 were already declared
> strictly sequential (this plan depended on PLAN-08) and both overlapped at
> `cui-http-core/src/main/java/de/cuioss/http/client/adapter/ResilientHttpAdapter.java`. The
> component-first, task-second grouping rule makes one WS-04 plan the correct unit rather than two
> dependency-linked PRs. All seven of this plan's former deliverables (redirect method
> preservation, credential-forwarding allow-list, transient-handshake retry classification,
> `RetryConfig` zero-delay rejection, result-type invariants, TLS scope honesty / cleartext
> constructor / charset parsing, and negative-path test coverage) are now deliverables 6-11 and
> part of deliverable 12 of `PLAN-08-etag-cache-principal-isolation.md`. Every claim this plan's
> Claim Labels section carried is preserved verbatim in the merged spec's Claim Labels section
> under "From the former PLAN-09"; none was dropped.

This file is retained as the audit record of the retirement — it is not deleted, per the epic's
duplication/redistribution apply-policy (a retired spec is the record of why it was retired). The
epic `status.json` row for `PLAN-09` is transitioned to `parked` with this pointer as its reason.
Do not launch this spec; launch `PLAN-08-etag-cache-principal-isolation.md` instead.

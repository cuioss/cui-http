envelope_version=1
sender_type=plan
sender_id=plan-06-forwarded-trust-model
epic=quality-report-remediation
kind=finding
created=2026-09-07T19:53:42Z

# Stale passages in `doc/forwarded-header-resolution.adoc` after the forwarded trust-model correction

**Addressee:** WS-06 PLAN-13 (owner of `doc/forwarded-header-resolution.adoc`).

**Sender:** `plan-06-forwarded-trust-model` — corrected the `ForwardedHeaderResolver`
trust model. This plan **consulted but did not edit** the reference document, which
WS-06 PLAN-13 owns.

**ADR allocated by this plan:** **ADR-0021 — "Unresolvable Forwarded header suppresses
only the fields it carried"**
(`doc/adr/0021-Unresolvable_Forwarded_header_suppresses_only_the_fields_it_carried.adoc`).
It supersedes **ADR-0002 — "Fail closed when X-Forwarded-For and RFC 7239 Forwarded
disagree"**, which is now `Status: Superseded`. ADR-0002's de-facto-versus-RFC-7239
disagreement rule itself is *retained*; only its treatment of an unresolvable header as
present for every field is replaced.

## Stale passages

Line numbers are against the document as this plan read it.

### 1. Line 449 — the sample fails open to cleartext (highest severity)

```java
String scheme = fwd.scheme().orElse("http");
int    port   = fwd.port().orElse(scheme.equals("https") ? 443 : 80);   // line 451
```

A dropped scheme is the resolver's fail-closed signal; `orElse("http")` converts it into
a cleartext assumption, so an attacker who can force a scheme drop downgrades the
consumer. This plan removed the identical sample from the resolver's own Javadoc and the
package documentation; this copy survives here. Line 451 inherits the defect, because it
derives the default port from the fail-open scheme.

Suggested direction: show the absent case as a decision the caller must make explicitly
(e.g. reject the request, or fall back to the *server's own* scheme), never to a literal
`"http"`.

### 2. Lines 239-245 — the blanket unresolvable-header rule

> "*A malformed `Forwarded` value rejects the whole header.* ... the header is
> present-but-unresolvable for *every* field it could have carried — scheme, host, port,
> and client IP alike"

No longer true, and this is the passage ADR-0021 replaces. An unresolvable header now
contributes nothing, so it suppresses only the fields whose directives the parser
actually reached: `Forwarded: proto=http;broken` drops the scheme and leaves an
`X-Forwarded-Host` value standing. A raw value that never sanitized yields no directives
at all and so suppresses nothing. A field the header *did* speak about still fails
closed.

### 3. Lines 175-186 — the precedence table's first-present-wins framing

> "Per field, the first present, non-blank value wins" with rows
> `X-Forwarded-Proto` → `X-ProxyScheme` → RFC 7239 `proto` (and the host/port analogues).

For scheme, host and port the two *de-facto* families are no longer ordered. They are
resolved independently and reconciled against each other **before** the RFC 7239
comparison; when they disagree the field is not dropped, the family named by the new
`ForwardedResolverConfig.deFactoPrecedence()` knob wins and the disagreement is logged.
Ordered first-present precedence survives only for the context path, whose two names are
not a de-facto family pair.

The knob carries a deployment obligation worth stating in this document: **the ingress
must strip the family it does not itself write.** The knob decides who wins a
disagreement; only stripping keeps a forged family from reaching the resolver.

### 4. Lines 183 and 214-220 — host and port described as one comparison

Line 183 describes the port fallback as "port carried by the resolved host", and the
"Conflicting sources = drop" paragraph enumerates "scheme, host, and client IP".

Host and port are now reconciled as **independent fields**: each source is still parsed
as one `host[:port]` token, but the comparison runs host against host and port against
port. A host disagreement drops only the host; a port disagreement drops only the port.
The previous unit comparison dropped a host both sources named whenever only one of them
also carried the port (`X-Forwarded-Host: h` + `X-Forwarded-Port: 8443` alongside
`Forwarded: host="h:8443"`).

### 5. Lines 36-51 — the security-precondition WARNING understates the available API

> "The resolver cannot make this guarantee for you, and by design does not accept the
> peer address as a parameter."

A second overload now exists: `resolve(Function<String,List<String>>, InetAddress)`. It
honors forwarded headers only when the socket peer is itself a configured trusted proxy,
returning `ResolvedForwarding.empty()` otherwise — so it enforces in code the property
this WARNING currently asks the deployment to guarantee by network placement alone. The
warning should present it as the stronger, preferred form, with the network controls
retained as defence in depth rather than as the only mechanism.

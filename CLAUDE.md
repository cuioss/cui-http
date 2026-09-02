# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CUI-HTTP is a security-focused HTTP utilities library providing secure validation pipelines, SSL/TLS context management, and HTTP client handlers. The library emphasizes security validation of HTTP components (paths, parameters, headers, bodies) with comprehensive attack pattern detection.

### Multi-Module Structure

```
cui-http/                         (root, packaging=pom, artifactId=cui-http-parent)
├── cui-http-core/                (library, artifactId=cui-http — preserves Maven Central coords)
│   └── src/
├── cui-http-benchmarking/        (JMH benchmarks, artifactId=cui-http-benchmarking)
│   ├── scripts/benchmark-pages.py
│   └── src/main/java/...
└── .github/workflows/benchmark.yml
```

### Build Commands

Never hard-code build tool commands (`./mvnw`, `mvn`) — invoke builds via the canonical executor commands below:

- Compile: `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "compile"`
- Quality gate: `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify -Ppre-commit"`
- Full verify: `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify"`
- Coverage: `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify -Pcoverage"`
- Tests (cui-http): `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "test -pl cui-http-core -am"` — only on cui-http
- Benchmark (cui-http-benchmarking): `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify -Pbenchmark,smoke -pl cui-http-benchmarking -am"` — only on cui-http-benchmarking (the `benchmark` profile flips `skip.benchmark=false`; `smoke` only retunes JMH timings — it cuts the iteration and warmup counts while lengthening each measurement window, so total wall time drops but the run is not measurement-grade — and must be combined with `benchmark` to actually run)

Use a 10-minute Bash timeout (600000ms) for build invocations. Analyze each build's TOON result: `status`, `errors[N]{file,line,message,category}`, `log_file`.

The authoritative Git workflow is defined in the "Git Workflow" section below.

## Architecture

### Core Components

1. **Security Validation Pipelines** (`de.cuioss.http.security.pipeline`)
   - `URLPathValidationPipeline`: URL *path component* validation (directory traversal, CVE exploits) — not a full absolute URL; extract the path and pass that
   - `URLParameterValidationPipeline`: Query parameter *value* validation
   - `URLParameterNameValidationPipeline`: Query parameter *name* validation (rejects delimiters that appear only after decoding)
   - `HTTPHeaderValidationPipeline`: Header injection attacks (header names and values)
   - `ContentTypeValidationPipeline`: Content-Type allow/block-list enforcement

2. **Validation Stages** (`de.cuioss.http.security.validation`)
   - `DecodingStage`: URL percent-decoding, UTF-8 overlong detection, and Unicode normalization (NFKC for URL paths, NFC for parameter values)
   - `NormalizationStage`: RFC 3986 dot-segment resolution (path normalization) — not Unicode normalization
   - `CharacterValidationStage`: Invalid character detection
   - `LengthValidationStage`: Length limits enforcement
   - `PatternMatchingStage`: Attack pattern detection
   - `AllowBlockListStage`: Case-insensitive header-name / content-type allow and block lists
   - `CookiePrefixValidationStage`: RFC 6265bis cookie prefix rules; standalone, invoked via `validateCookie(Cookie)` rather than as part of a factory-built pipeline
   - `RequestCollectionValidator`: Request-level limits that need collection or attribute context

3. **Client and Forwarded-Header Packages**
   - `de.cuioss.http.client`: Log messages and shared client types
   - `de.cuioss.http.client.adapter`: Async-first adapters with composable retry and ETag caching
   - `de.cuioss.http.client.converter`: Response-body converters
   - `de.cuioss.http.client.handler`: `HttpHandler`, `SecureSSLContextProvider`, `HttpStatusFamily`
   - `de.cuioss.http.client.result`: `HttpResult` sealed interface and `HttpErrorCategory`
   - `de.cuioss.http.forwarded`: Reverse-proxy / forwarded-header resolution

   See `README.adoc` and `/doc/client-handlers-readme.adoc` for the component documentation.

### Pipeline Selection Rules

The authoritative selection matrix — one section per concrete pipeline, with Purpose, Use When,
Current Implementations, and Attack Pattern Examples — lives in
`/doc/http-security/specification/pipeline-architecture-standards.adoc`. Consult it rather than
guessing which pipeline a given attack pattern belongs to.

## Testing Architecture

### Test Organization

- **Attack Databases** (`cui-http-core/src/test/java/de/cuioss/http/security/database`): Predefined attack patterns
- **Generators** (`cui-http-core/src/test/java/de/cuioss/http/security/generators`): Test data generators
- **Integration Tests** (`cui-http-core/src/test/java/de/cuioss/http/security/tests`): Attack database validation

### Test Generators (Available as separate artifact)

The project produces a `generators` artifact containing security testing utilities:
```xml
<dependency>
    <groupId>de.cuioss</groupId>
    <artifactId>cui-http</artifactId>
    <classifier>generators</classifier>
    <scope>test</scope>
</dependency>
```

## CUI Standards Integration

This project follows CUI standards documented in `agents.md`. Key requirements:

1. **Pre-commit checks are mandatory**: Always run the pre-commit checks (see Build Commands section) before commits
2. **Use CuiLogger**: Private static final LOGGER, never use slf4j or System.out
3. **JUnit 5 only**: No Mockito, PowerMock, or Hamcrest
4. **Minimum 80% test coverage**: Critical paths need 100%
5. **Use @Nullable/@NonNull**: From JSpecify for null-safety
6. **Lombok annotations**: @Builder, @Value, @UtilityClass where appropriate

## Security Validation Contract

All validators follow the "Optional return, throws on violation" pattern:

```java
public interface HttpSecurityValidator {
    Optional<String> validate(@Nullable String value) throws UrlSecurityException;
}
```

Validators are:
- Thread-safe
- Composable (can be chained)
- Fail-secure (throw UrlSecurityException on violations)

## Module Dependencies

- **cui-java-tools**: Core utilities and logging
- **JSpecify**: Null-safety annotations
- **Lombok**: Code generation
- **JUnit 5**: Testing framework (test scope)
- **cui-test-generator**: Test data generation (test scope)

## Important Files

- `/doc/http-security/specification/pipeline-architecture-standards.adoc`: Pipeline selection rules
- `/doc/test-generators-readme.adoc`: Generator implementation standards
- `/agents.md`: AI agent guidance and CUI development standards
- `/cui-http-core/src/main/java/module-info.java`: Module definition

## Development Notes

- Use `.plan/temp/` for ALL temporary files (covered by `Write(.plan/**)` permission - avoids permission prompts)

1. The project uses Java modules (JPMS) - main code in `module de.cuioss.http`
2. Tests run on classpath (not module path) to bypass JPMS restrictions
3. All public APIs must have Javadoc with usage examples
4. Security exceptions use builder pattern with detailed failure context
5. Attack patterns are organized by type (CVE, OWASP, protocol-specific)

## Git Workflow

All cuioss repositories have branch protection on `main`. Direct pushes to `main` are never allowed. Always use this workflow:

1. Create a feature branch: `git checkout -b <branch-name>`
2. Commit changes: `git add <files> && git commit -m "<message>"`
3. Push the branch: `git push -u origin <branch-name>`
4. Create a PR: `gh pr create --repo cuioss/cui-http --head <branch-name> --base main --title "<title>" --body "<body>"`
5. Wait for CI + Gemini review (waits until checks complete): `gh pr checks --watch`
6. **Handle Gemini review comments** — fetch with `gh api repos/cuioss/cui-http/pulls/<pr-number>/comments` and for each:
   - If clearly valid and fixable: fix it, commit, push, then reply explaining the fix and resolve the comment
   - If disagree or out of scope: reply explaining why, then resolve the comment
   - If uncertain (not 100% confident): **ask the user** before acting
   - Every comment MUST get a reply (reason for fix or reason for not fixing) and MUST be resolved
7. Do **NOT** enable auto-merge unless explicitly instructed. Wait for user approval.
8. Return to main: `git checkout main && git pull`

## Tool Usage

- Use proper tools (Edit, Read, Write) instead of shell commands (echo, cat)
- Never use Bash for file operations (find, grep, cat, ls) — use Glob, Read, Grep tools instead

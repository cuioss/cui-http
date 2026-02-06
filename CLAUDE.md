# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CUI-HTTP is a security-focused HTTP utilities library providing secure validation pipelines, SSL/TLS context management, and HTTP client handlers. The library emphasizes security validation of HTTP components (paths, parameters, headers, bodies) with comprehensive attack pattern detection.

## Build Commands

```bash
# Build and install locally
./mvnw clean install

# Run tests
./mvnw test

# Run specific test
./mvnw test -Dtest=ClassName#methodName

# Run pre-commit checks (MANDATORY before commits)
./mvnw -Ppre-commit clean verify

# Generate coverage report
./mvnw -Pcoverage clean verify

# Build with skipping tests
./mvnw clean install -DskipTests
```

## Git Workflow

This repository has branch protection on `main`. Direct pushes to `main` are never allowed. Always use this workflow:

1. Create a feature branch: `git checkout -b <branch-name>`
2. Commit changes: `git add <files> && git commit -m "<message>"`
3. Push the branch: `git push -u origin <branch-name>`
4. Create a PR: `gh pr create --head <branch-name> --base main --title "<title>" --body "<body>"`
5. Enable auto-merge: `gh pr merge --auto --squash --delete-branch`
6. Wait for merge (check every ~60s): `while gh pr view --json state -q '.state' | grep -q OPEN; do sleep 60; done`
7. Return to main: `git checkout main && git pull`

## Architecture

### Core Components

1. **Security Validation Pipelines** (`de.cuioss.http.security.pipeline`)
   - `URLPathValidationPipeline`: All URL validation (paths, full URLs, directory traversal, CVE exploits)
   - `HTTPHeaderValidationPipeline`: Header injection attacks
   - `URLParameterValidationPipeline`: Query parameter validation

2. **Validation Stages** (`de.cuioss.http.security.validation`)
   - `DecodingStage`: URL percent-encoding, UTF-8 overlong detection
   - `NormalizationStage`: Unicode normalization for paths
   - `CharacterValidationStage`: Invalid character detection
   - `LengthValidationStage`: Length limits enforcement
   - `PatternMatchingStage`: Attack pattern detection

3. **Client Handlers** (`de.cuioss.http.client.handler`)
   - `HttpHandler`: General HTTP request/response handling
   - `SecureSSLContextProvider`: SSL/TLS context management
   - `HttpStatusFamily`: HTTP status code classification

### Pipeline Selection Rules

**Use URLPathValidationPipeline for:**
- All URL validation (paths and full URLs)
- Attack patterns with or without protocols
- Directory traversal testing (`../../../etc/passwd`)
- CVE exploits for specific servers (Apache, IIS, Nginx)
- XSS or script injection in URLs
- Full URL parsing with domain validation

## Testing Architecture

### Test Organization

- **Attack Databases** (`src/test/java/de/cuioss/http/security/database`): Predefined attack patterns
- **Generators** (`src/test/java/de/cuioss/http/security/generators`): Test data generators
- **Integration Tests** (`src/test/java/de/cuioss/http/security/tests`): Attack database validation

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
- `/doc/http-security/specification/generator-contract.adoc`: Generator implementation standards
- `/agents.md`: AI agent guidance and CUI development standards
- `/src/main/java/module-info.java`: Module definition

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
5. Wait for CI + Gemini review (waits until checks complete): `gh pr checks --repo cuioss/cui-http <pr-number> --watch`
6. **Handle Gemini review comments** — fetch with `gh api repos/cuioss/cui-http/pulls/<pr-number>/comments` and for each:
   - If clearly valid and fixable: fix it, commit, push, then reply explaining the fix and resolve the comment
   - If disagree or out of scope: reply explaining why, then resolve the comment
   - If uncertain (not 100% confident): **ask the user** before acting
   - Every comment MUST get a reply (reason for fix or reason for not fixing) and MUST be resolved
7. Do **NOT** enable auto-merge unless explicitly instructed. Wait for user approval.
8. Return to main: `git checkout main && git pull`
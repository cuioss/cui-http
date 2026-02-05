# AGENTS.md

This file provides guidance to AI coding agents when working with the CUI-HTTP project.

## Project Overview

CUI-HTTP is a security-focused HTTP utilities library providing secure validation pipelines, SSL/TLS context management, and HTTP client handlers. The library emphasizes security validation of HTTP components (paths, parameters, headers, bodies) with comprehensive attack pattern detection.

- **Language**: Java 21 with JPMS module system (`module de.cuioss.http`)
- **Build System**: Maven 3.9.11 via wrapper (`./mvnw`)
- **Testing**: JUnit 5 (no Mockito, PowerMock, or Hamcrest)
- **Code Quality**: SonarCloud integration with mandatory pre-commit checks

## Dev Environment Tips

### Build and Run Commands

```bash
# Build and install locally
./mvnw clean install

# Run tests
./mvnw test

# Run specific test
./mvnw test -Dtest=ClassName#methodName

# MANDATORY: Run pre-commit checks before commits
./mvnw -Ppre-commit clean verify

# Generate coverage report
./mvnw -Pcoverage clean verify

# Build without tests
./mvnw clean install -DskipTests
```

### Architecture Quick Reference

**Security Validation Pipelines** (`de.cuioss.http.security.pipeline`):
- `URLPathValidationPipeline`: All URL validation (paths, full URLs, directory traversal, CVE exploits)
- `HTTPHeaderValidationPipeline`: Header injection attacks
- `URLParameterValidationPipeline`: Query parameter validation

**Validation Stages** (`de.cuioss.http.security.validation`):
- `DecodingStage`: URL percent-encoding, UTF-8 overlong detection
- `NormalizationStage`: Unicode normalization for paths
- `CharacterValidationStage`: Invalid character detection
- `LengthValidationStage`: Length limits enforcement
- `PatternMatchingStage`: Attack pattern detection

**Client Handlers** (`de.cuioss.http.client.handler`):
- `HttpHandler`: General HTTP request/response handling
- `SecureSSLContextProvider`: SSL/TLS context management
- `HttpStatusFamily`: HTTP status code classification

### Pipeline Selection Rules

Use `URLPathValidationPipeline` for:
- All URL validation (paths and full URLs)
- Attack patterns with or without protocols
- Directory traversal testing (`../../../etc/passwd`)
- CVE exploits for specific servers (Apache, IIS, Nginx)
- XSS or script injection in URLs
- Full URL parsing with domain validation

### Test Organization

- **Attack Databases** (`src/test/java/de/cuioss/http/security/database`): Predefined attack patterns
- **Generators** (`src/test/java/de/cuioss/http/security/generators`): Test data generators
- **Integration Tests** (`src/test/java/de/cuioss/http/security/tests`): Attack database validation

### Key Dependencies and Modules

- **cui-java-tools**: Core utilities and CuiLogger
- **JSpecify**: Null-safety annotations (`@Nullable`, `@NonNull`)
- **Lombok**: Code generation (`@Builder`, `@Value`, `@UtilityClass`)
- **JUnit 5**: Testing framework (test scope)
- **cui-test-generator**: Test data generation (test scope)

## Testing Instructions

### Test Execution

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=URLPathValidationPipelineTest

# Run specific test method
./mvnw test -Dtest=ClassName#methodName

# Run tests with coverage
./mvnw -Pcoverage clean verify
```

### Test Requirements

- **Minimum 80% line and branch coverage** (100% for critical paths)
- Use JUnit 5 (`@Test`, `@DisplayName`, `@Nested`)
- Follow AAA pattern (Arrange-Act-Assert)
- One logical assertion per test
- Tests must be independent
- Use cui-test-generator for test data generation
- Use `@EnableTestLogger` for testing log messages
- Use `assertLogMessagePresentContaining` for log validation
- **Forbidden**: Mockito, PowerMock, Hamcrest - use CUI alternatives

### Parameterized Tests

Annotation hierarchy (preferred order):
1. `@GeneratorsSource` - Most preferred for complex objects
2. `@CompositeTypeGeneratorSource` - For multiple related types
3. `@CsvSource` - Standard choice for simple data
4. `@ValueSource` - Single parameter variations
5. `@MethodSource` - Last resort only

## Coding Standards

### Core Requirements

- **Use CuiLogger**: Private static final LOGGER (never slf4j or System.out)
- **JSpecify Null-Safety**: Use `@Nullable` for nullable fields/parameters/returns; `@NonNull` for explicit non-null guarantees
- **Lombok Annotations**: `@Builder` for complex objects, `@Value` for immutable objects, `@UtilityClass` for utility classes
- **Return empty collections** instead of null
- **Use Optional** for nullable return values
- **Never catch generic Exception** - always use specific exception types
- **Make fields final** by default
- **Use enum** instead of constants for fixed sets

### Java Standards

- Use Java 21+ features (records, switch expressions, text blocks, pattern matching)
- Use sealed classes for restricted hierarchies
- Stream API for complex data transformations
- Method references over lambdas when possible
- Prefer composition over inheritance
- Mark classes final unless designed for inheritance
- Prefer immutable collections (`List.of()`, `Set.of()`)

### Security Validation Contract

All validators follow this pattern:

```java
public interface HttpSecurityValidator {
    Optional<String> validate(@Nullable String value) throws UrlSecurityException;
}
```

Validators are thread-safe, composable, and fail-secure (throw `UrlSecurityException` on violations).

## Documentation Standards

### Javadoc Requirements

- Every public and protected class/interface must be documented
- Document all public methods with parameters, returns, and exceptions
- Include `@since` tag with version information
- Document thread-safety considerations
- Include usage examples for complex classes and methods
- Every package must have `package-info.java`
- Use `{@link}` for references to classes, methods, and fields

### AsciiDoc Standards

- Use `.adoc` extension
- Include proper document header with TOC and section numbering
- Use `:source-highlighter: highlight.js` attribute
- Use `xref:` syntax for cross-references (not `<<>>`)
- Blank lines required before all lists
- Consistent heading hierarchy

## PR Instructions

### Before Committing

1. **Run pre-commit checks** (MANDATORY):
   ```bash
   ./mvnw -Ppre-commit clean verify
   ```
   - Fix ALL errors and warnings
   - Address code quality, formatting, and linting issues
   - Some recipes may add markers - fix them or suppress with justification
   - Never commit markers

2. **Update Documentation**:
   - Update Javadoc for public APIs
   - Update AsciiDoc documentation if necessary

3. **Test Coverage**:
   - New code requires appropriate test coverage
   - Existing tests must continue to pass

4. **Commit Message**: Follow Git Commit Standards

### Important Files

- `/doc/http-security/specification/pipeline-architecture-standards.adoc`: Pipeline selection rules
- `/doc/http-security/specification/generator-contract.adoc`: Generator implementation standards
- `/src/main/java/module-info.java`: Module definition

## Development Notes

- Use `.plan/temp/` for ALL temporary files (covered by `Write(.plan/**)` permission - avoids permission prompts)
- The project uses Java modules (JPMS) - main code in `module de.cuioss.http`
- Tests run on classpath (not module path) to bypass JPMS restrictions
- All public APIs must have Javadoc with usage examples
- Security exceptions use builder pattern with detailed failure context
- Attack patterns are organized by type (CVE, OWASP, protocol-specific)

## Test Generators Artifact

The project produces a separate `generators` artifact:

```xml
<dependency>
    <groupId>de.cuioss</groupId>
    <artifactId>cui-http</artifactId>
    <classifier>generators</classifier>
    <scope>test</scope>
</dependency>
```

This artifact contains security testing utilities for reuse in other projects.

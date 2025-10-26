# Command Configuration

## docs-verify-links

### Skipped Files

Files excluded from link verification:

- `target/**/*.adoc` (Build artifacts - auto-generated)
- `node_modules/**/*.adoc` (Dependency documentation)
- `.git/**/*.adoc` (Git metadata)

### Acceptable Warnings

Links approved by user as acceptable (even if broken/non-standard):

_(No acceptable warnings defined yet)_

### Last Verification

- Date: 2025-10-17
- Files verified: 19
- Links verified: 130+
- Format violations fixed: 130
- Broken link references removed: 5 (plan.adoc)
- Status: ✅ All links verified, format violations corrected, and broken references removed

## docs-technical-adoc-review

### Skipped Files

Files excluded from technical AsciiDoc review:

_(No files skipped)_

### Skipped Directories

Directories excluded entirely:

- `target/` - Build artifacts (auto-generated)
- `node_modules/` - Dependencies
- `.git/` - Git metadata

### Acceptable Warnings

Warnings approved as acceptable:

_(No acceptable warnings defined)_

### Last Execution

- Date: 2025-10-23
- Directories processed: 1 (http-client/)
- Files reviewed: 9
- Issues found: 19 (format compliance)
- Issues fixed: 18
- Issues remaining: 0
- Status: ✅ SUCCESS - All issues resolved
- Agents launched: 1

### Previous Execution (2025-10-21)

- Directories processed: 6
- Files reviewed: 20
- Issues found: 123 (71 + 47 + 5)
- Issues fixed: 3
- Issues remaining: 117 (all are broken anchor references to parent/sibling files)
- Status: ⚠️ PARTIAL - 4 directories clean, 2 directories with out-of-scope link issues
- Agents launched: 6 (all sequential)

### Lessons Learned

**From root directory review:**
- Discovery: README.adoc demonstrates excellent balance between descriptive technical language and neutral tone
- Framework: Distinguish between factual descriptive language (verifiable claims like "comprehensive attack pattern detection") and promotional language (subjective claims without evidence)
- Impact: More accurate tone analysis that preserves legitimate technical descriptions while removing actual marketing language

**From doc/ directory review:**
- Discovery: All documentation maintains excellent technical tone with no issues
- Observation: Path reference styles vary (some use `../doc/` prefix, others don't) but all links work correctly
- Impact: Functional correctness prioritized over style consistency when both approaches work

**From doc/http-security/analysis/ review:**
- Discovery: Descriptive language like "comprehensive" can be FACTUAL when describing verifiable scope
- Framework: When analyzing tone, distinguish between:
  1. Subjective quality claims ("powerful", "best-in-class") - ALWAYS remove
  2. Factual scope descriptors ("comprehensive test coverage") - KEEP when verifiable (e.g., 246 tests, 395+ tests)
- Impact: Fewer false positives on legitimate technical descriptions

**From doc/http-security/specification/ review:**
- Discovery: Link verification correctly identified broken anchor references, but root cause (missing anchors in parent files) requires cross-directory analysis
- Framework: When reviewing specification subdirectories, verify parent directory files contain expected anchors before removing references
- Impact: Maintains proper requirements traceability across directory boundaries

### Known Issues

**Broken Anchor References (117 total):**

1. **doc/http-security/ → subdirectories** (71 broken anchors)
   - Target files: specification/specification.adoc, specification/testing.adoc, analysis/owasp-best-practices.adoc, analysis/cve-analysis.adoc
   - Expected anchors missing: _core_components, _architecture, _validation_stages, _a032021_injection, etc.
   - Action required: Review specification/ and analysis/ files to add missing anchor IDs

2. **doc/http-security/specification/ → parent directory** (46 broken anchors)
   - Target file: ../functional-requirements.adoc
   - Expected anchors missing: HTTP-1 through HTTP-17 (requirement IDs)
   - Action required: Add anchor IDs to functional-requirements.adoc for all requirement numbers

**Note:** All broken links are cross-directory anchor references. The files themselves are excellent quality - this is a structural documentation issue requiring anchor standardization across all HTTP security documentation.

### Recommendations

1. **Anchor Standardization:** Implement consistent anchor naming conventions across all requirement documents (e.g., `[#HTTP-1]` format)
2. **Pre-commit Validation:** Add hooks to validate cross-document anchor references before commits
3. **Documentation Standards:** Document explicit anchor ID conventions in project standards
4. **Cross-Directory Reviews:** When reviewing subdirectories with parent/sibling references, include related files in scope or explicitly flag dependencies

## setup-project-permissions

### Last Execution

- **Date**: 2025-10-23
- **Action**: Complete rebuild of permission settings
- **Changes**: Removed 82 permissions, added 3 project-specific permissions
- **Security**: Fixed JSON syntax error (missing comma), removed dangerous permissions

### Removed Permissions

**Security Risks Removed:**
- `Read(//Users/oliver/**)` - Entire home directory access (CRITICAL SECURITY RISK)
- `Read(//tmp/**)` - System temp directory access
- `Bash(rm:*)` - Unrestricted file deletion

**Redundant Permissions Removed (already in global settings):**
- 30+ Bash commands (git, grep, find, cat, etc.)
- WebFetch domains (covered by global `domain:*`)
- CUI standards paths (covered by global `Read(//~/git/cui-llm-rules/**)`)
- 50+ other permissions already globally available

### Current Permissions

**Allow List (3 permissions - project-specific only):**
- `Edit(//~/git/cui-http/**)` - Edit project files
- `Read(//~/git/cui-http/**)` - Read project files
- `Write(//~/git/cui-http/**)` - Create new project files

**Deny List (4 permissions - security):**
- `Read(//tmp/**)` - Block system temp access
- `Read(//private/tmp/**)` - Block private temp access
- `Write(//tmp/**)` - Block temp file creation
- `Write(//private/tmp/**)`- Block private temp writes

**Ask List (1 permission - safety):**
- `Write(//.claude/settings.local.json)` - Require approval for settings changes

### User-Approved Permissions

_(No suspicious permissions approved - clean slate)_

### Architecture

**Global Settings** (`~/.claude/settings.json`):
- Common development tools (Bash commands, git, mvn, npm, etc.)
- CUI standards access (`Read(//~/git/cui-llm-rules/**)`)
- Universal web access (`WebFetch(domain:*)`)
- Claude tools (`SlashCommand`, agents, commands)

**Local Settings** (`./.claude/settings.local.json`):
- **ONLY** project-specific permissions (cui-http)
- Minimal footprint (3 allow, 4 deny, 1 ask)
- Clean separation of concerns

### Notes

**Fixed Issues:**
1. **JSON Syntax Error**: Line 72 missing comma - caused parsing failures
2. **Path Format**: Converted all `/Users/oliver/` to `~/` for portability
3. **Security**: Removed home directory wildcard access
4. **Redundancy**: Eliminated 79 permissions already covered globally
5. **Temp Files**: Explicitly denied system temp access (Maven should use `target/`)

**Rationale for Clean Rebuild:**
- Original file had 82 permissions (79 redundant, 3 dangerous)
- Invalid JSON syntax prevented reliable parsing
- Simpler to rebuild than to incrementally fix all issues
- New structure follows global/local separation principle

## ./mvnw -Ppre-commit clean install

### Last Execution Duration
- **Duration**: 60000ms (1 minute)
- **Last Updated**: 2025-10-23 (initial baseline)

### Acceptable Warnings
- `[WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources`
- `[WARNING] Parameter 'session' is deprecated`

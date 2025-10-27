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

- **Date**: 2025-10-26
- **Action**: Fixed degraded permission settings (settings had regressed since 2025-10-23)
- **Changes**: Removed 3 redundant permissions, added 3 essential project permissions, added security protection
- **Security**: Removed cross-project access, fixed absolute paths, added settings write protection

### Removed Permissions

**Redundant (already in global settings):**
- `Read(//Users/oliver/git/cui-llm-rules/claude/**)` - Covered by global `Read(//~/git/cui-llm-rules/**)`
- `Read(//Users/oliver/.claude/**)` - Covered by global `Read(//~/.claude/agents/**)` + `Read(//~/.claude/commands/**)`

**Security Risks:**
- `Read(//Users/oliver/git/OAuth-Sheriff/.claude/**)` - Cross-project access violation (cui-http shouldn't access OAuth-Sheriff's private settings)

**Issues with removed permissions:**
1. All used absolute paths `/Users/oliver/` instead of user-relative `~/`
2. Last one accessed different project's private .claude directory
3. All were redundant (already covered by global settings)

### Current Permissions

**Allow List (4 permissions):**
- `Bash(tee:*)` - Project-specific utility (tee command for output redirection)
- `Edit(//~/git/cui-http/**)` - Edit project files
- `Read(//~/git/cui-http/**)` - Read project files
- `Write(//~/git/cui-http/**)` - Create new project files

**Deny List (0 permissions):**
_(Empty - temp file security handled by global deny list)_

**Ask List (1 permission - security):**
- `Write(.claude/settings.local.json)` - Require approval for settings changes

### User-Approved Permissions

_(No suspicious permissions approved - clean slate)_

### Architecture

**Global Settings** (`~/.claude/settings.json`):
- Common development tools (141 Bash commands: git, mvn, npm, grep, find, etc.)
- CUI standards access (`Read(//~/git/cui-llm-rules/**)` - covers all CUI standards)
- Claude tools access (`Read(//~/.claude/agents/**)`, `Read(//~/.claude/commands/**)`)
- Universal web access (`WebFetch(domain:*)`)
- Slash commands (`/agents-*`, `/slash-*`, `/setup-project-permissions`)
- Security: 70 denied permissions (sudo, rm -rf, system directories, etc.)

**Local Settings** (`./.claude/settings.local.json`):
- **ONLY** project-specific permissions (cui-http)
- Minimal footprint (4 allow, 0 deny, 1 ask)
- Clean separation of concerns
- All paths use user-relative format (`~/`)

### Notes

**Why Settings Degraded:**
- Settings file was manually edited or reverted after 2025-10-23 fix
- Absolute paths returned (`/Users/oliver/` instead of `~/`)
- Cross-project access added (OAuth-Sheriff .claude access from cui-http)
- Essential project permissions still missing

**Fixed Issues:**
1. **Absolute Paths**: Converted all `/Users/oliver/` to `~/` for portability
2. **Cross-Project Access**: Removed OAuth-Sheriff .claude access (privacy/security violation)
3. **Redundancy**: Removed 2 permissions already covered globally
4. **Missing Permissions**: Added essential Edit/Read/Write for cui-http project
5. **Security Protection**: Added settings write protection to ask list

**Comparison to OAuth-Sheriff (reference):**
- OAuth-Sheriff has: Edit, Read, Write for project + specific .claude access
- OAuth-Sheriff also had absolute paths (should be fixed there too)
- Both projects should have identical structure (3-4 allow + 1 ask)

**Best Practices Reinforced:**
- **Global**: Common tools, CUI standards, shared .claude access, universal domains
- **Local**: THIS project's Edit/Read/Write ONLY, plus project-specific tools (like tee)
- **Security**: Settings write always in ask list, never in allow
- **Portability**: Always use `~/` instead of `/Users/username/`

## ./mvnw -Ppre-commit clean install

### Last Execution Duration
- **Duration**: 60000ms (1 minute)
- **Last Updated**: 2025-10-23 (initial baseline)

### Acceptable Warnings
- `[WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources`
- `[WARNING] Parameter 'session' is deprecated`

## handle-pull-request

### CI/Sonar Duration
- **Duration**: 300000ms (5 minutes)
- **Last Updated**: 2025-10-27 (initial baseline)

### Notes
- This duration represents the time to wait for CI and SonarCloud checks to complete
- Includes buffer time for queue delays

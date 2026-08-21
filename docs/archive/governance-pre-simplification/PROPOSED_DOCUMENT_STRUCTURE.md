# Proposed Document Structure

## Purpose

This is a proposal only. It does not move, rename, delete, rewrite, or otherwise change existing documentation. The current audit found multiple useful documents with overlapping authority and different lifecycle states. The target structure below separates current facts, contracts, operations, plans, instructions, generated output, and history.

## Proposed Hierarchy

```text
docs/
  governance/
    DOCUMENT_INVENTORY.md
    DOCUMENT_CONFLICT_REPORT.md
    PROPOSED_DOCUMENT_STRUCTURE.md
    AUTHORITY_MATRIX.md
    DOCUMENT_LIFECYCLE.md
  architecture/
    SYSTEM_OVERVIEW.md
    AUTHORIZATION_AND_STORE_SCOPE.md
    DATA_MODEL.md
    PRINTING_ARCHITECTURE.md
    PAD_DIRECT_ARCHITECTURE.md
    OFFLINE_ORDERING_ARCHITECTURE.md
  contracts/
    API.md
    EVENTS_AND_WEBSOCKET.md
    ERROR_CODES.md
    PRINT_JOB_CONTRACT.md
  plans/
    ROADMAP.md
    CURRENT_PILOT_SCOPE.md
    HISTORICAL/
  runbooks/
    cloud/
    windows-pilot/
    pad/
  testing/
    SMOKE_TESTS.md
    PILOT_CHECKLIST.md
  generated/
    SYSTEM_SNAPSHOT.md
    schema/
    api/
  history/
    PR_NOTES/
    DESIGN_RECORDS/
```

The existing `doc/`, `deployment/`, and `restaurant-pad-app/docs/` paths should be preserved during a transition. The hierarchy is a future target, not an instruction to perform a bulk move.

## Authority Matrix

| Topic | One proposed future authority | Executable verification source | Current documents that should become pointers/history |
| --- | --- | --- | --- |
| Repository workflow and agent rules | `doc/CODEX_WORKFLOW.md` or a short `docs/governance/DEVELOPMENT_WORKFLOW.md` | CI rules and repository configuration | Root `CODEX_WORKFLOW.md`, old prompts |
| Security/auth/store scope | `docs/architecture/AUTHORIZATION_AND_STORE_SCOPE.md` | Backend auth/context, capability registry, StoreAccessService, tests | Stale API auth sections, old MVP scope |
| Current system architecture | `docs/architecture/SYSTEM_OVERVIEW.md` | Current modules/configuration | `SystemDesign_Bilingual.md`, parts of `SYSTEM_DOCUMENTATION.md` |
| REST/API contract | `docs/contracts/API.md` | Controllers, DTOs, OpenAPI or contract tests | Existing `doc/API.md` after endpoint/auth reconciliation |
| WebSocket/events | `docs/contracts/EVENTS_AND_WEBSOCKET.md` | Event publishers/subscribers and integration tests | Flow/sequence diagrams as historical references |
| Database conceptual model | `docs/architecture/DATA_MODEL.md` | Entities/mappers | `doc/DatabaseDesign.md` as historical design |
| Database applied schema | Generated `docs/generated/schema/` | Flyway migrations and Flyway history table | Existing design tables |
| Printing and PAD_DIRECT | `docs/architecture/PRINTING_ARCHITECTURE.md` and `PAD_DIRECT_ARCHITECTURE.md` | Controllers, services, entities, Android implementation, tests | Pad phase documents after lifecycle labeling |
| Current pilot boundary | `docs/plans/CURRENT_PILOT_SCOPE.md` | Feature flags, profile, deployment commit/tag | Current scope copy after versioning |
| Cloud deployment | `docs/runbooks/cloud/` | Compose files, deploy scripts, profile configuration | Root deployment quick references as pointers |
| Android local/PAD_DIRECT operation | `docs/runbooks/pad/` | Android source, Gradle variant, installed APK | Existing Pad docs after separating POC/manual/semi-auto |
| Smoke and acceptance checks | `docs/testing/` | Test scripts and CI | Duplicated checklist copies |
| Historical decisions | `docs/history/` | Git history | PR notes embedded in `SYSTEM_DOCUMENTATION.md` |

## Lifecycle Metadata

Every future maintained document should declare a small metadata block near its title:

```yaml
status: current | historical | generated | draft
authority: canonical | supporting | non-authoritative
scope: cloud | pilot | android | frontend | backend | repository
last_verified_commit: <git commit>
last_verified_at: <YYYY-MM-DD>
source_paths:
  - <code/config/test path>
owner: <team or role>
```

Rules:

1. `current` means the claims were checked against the named source paths at the stated commit.
2. `historical` documents may preserve old decisions but must not be used as current implementation instructions.
3. `generated` documents are outputs and cannot override source code, migrations, or configuration.
4. `draft` documents must not be used as deployment or security authority.
5. A document may have only one canonical authority topic; it may link to another topic but should not redefine it.

## API and Schema Governance

The future API contract should be produced from or checked against:

- Controller request mappings.
- DTOs and validation annotations.
- Authentication/capability checks.
- Integration tests for status codes and response shapes.
- Event publisher/subscriber tests for WebSocket contracts.

The future database reference should distinguish:

- Conceptual model.
- Current migration chain.
- Actual applied migration state in each environment.

Migrations remain the executable schema authority. A generated schema document should include the migration version and commit used to create it.

## Generated Artifact Policy

Generated documentation and packaged artifacts should:

1. Identify their generator and source commit.
2. Avoid claiming to be the source of truth.
3. Be checked for drift in CI where practical.
4. Be excluded from manual edits unless the build process explicitly owns the file.
5. Clearly distinguish “what was packaged” from “what source currently specifies”.

This applies to bundled Android web assets, packaged Windows frontend distributions, diagram exports, build metadata, and any future generated API/schema snapshot.

## Transition Plan (Future Work Only)

1. Approve the authority matrix before moving any file.
2. Add lifecycle metadata and canonical links without changing technical content.
3. Reconcile the API/auth, deployment, Pad worker, and database conflicts identified in the conflict report.
4. Split `SYSTEM_DOCUMENTATION.md` into a generated snapshot and historical/operational documents; preserve a pointer for existing users.
5. Mark old MVP/design/PR prompt documents as historical.
6. Replace the duplicate Windows README with a canonical source plus a generated/package pointer only after packaging impact is verified.
7. Add CI checks for broken links, duplicate canonical claims, stale endpoint examples, profile names, and migration/API drift.
8. Move documents only in small, separately reviewed commits. Never combine documentation relocation with application behavior changes.

## Suggested CI Governance Checks

- Verify every root/documentation pointer resolves to an existing file.
- Detect duplicate Markdown content and duplicate canonical declarations.
- Compare documented endpoint paths with controller mappings or an OpenAPI export.
- Check documented profiles and Compose service names against configuration.
- Check examples for hardcoded production store IDs, secrets, private IPs, and owner passwords.
- Confirm the documented Flyway version is not ahead of the repository migration chain.
- Require current runbooks to name a verification commit/date.
- Warn when a document exceeds a practical working-context limit and should link to smaller topic documents.

## Explicit Non-Changes in This Audit

The following are intentionally not performed:

- No existing document was edited or normalized.
- No application code, migration, deployment script, generated asset, or Android source was changed.
- No document was moved, deleted, renamed, or merged.
- No server/database/device runtime was inspected or changed.


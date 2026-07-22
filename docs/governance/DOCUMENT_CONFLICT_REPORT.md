# Document Conflict Report

## Scope and Method

- **Audit commit:** `46d8a906fc270983fba66698e279095243780fd0`
- **Branch:** `codex/pilot-site-reliability-batch`
- **Date:** 2026-07-22
- **Mode:** Read-only comparison of tracked documents against repository source/configuration and against one another.
- **Runtime limitation:** No production server, database, Android device, deployed container, or live network behavior was inspected.

The “current most likely fact” below is stated only where the repository code or configuration supports it. It is not a claim about what is currently deployed.

## Severity

| Severity | Meaning |
| --- | --- |
| P0 | Could misstate security, authorization, financial/order correctness, or production safety. |
| P1 | Could cause an incorrect deployment, operational outage, or materially misleading developer behavior. |
| P2 | Governance, maintenance, duplication, or context-quality issue without an immediate runtime consequence. |

## Findings

### DCR-001 [P0] API authentication documentation is stale

- **File A:** `doc/API.md:8-17,398-405`
- **File B / code evidence:** `backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java:15,42-45`; `frontend/src/services/apiClient.ts:190-192`
- **Conflicting statements:** `doc/API.md` presents `X-User-Id` as the MVP authorization context and says it is the normal catalog requirement. Current request context requires a Bearer token unless a configured local fallback is enabled; the frontend API client adds Bearer auth and deletes `X-User-Id`.
- **Current most likely fact:** Cloud/current frontend business calls use Bearer authentication. `X-User-Id` is a compatibility fallback whose availability depends on configuration.
- **Confidence:** High
- **Recommended future authority:** A maintained API/security contract generated or checked against controllers and `RequestUserContextService`, with a separate local-development fallback section.
- **Code verification still required:** Yes. Verify the effective profile/configuration in each deployed environment before changing the contract wording.

### DCR-002 [P1] Roadmap current stage is behind the implemented repository

- **File A:** `doc/ROADMAP.md:5-7,74-106,146-171`
- **File B / code evidence:** `restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java`; `backend/src/main/java/com/restaurant/system/printing/controller/PadPrintingController.java:42-120`; `restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md:3-15`
- **Conflicting statements:** The roadmap says the project is before the Android Pad Shell and makes the Android shell/PAD_DIRECT a future milestone. The repository contains the Android worker and PAD_DIRECT pending/claim/start/payload/complete/fail/release APIs, and the semi-auto runbook documents foreground processing.
- **Current most likely fact:** The source tree has progressed beyond the roadmap's stated current stage.
- **Confidence:** High
- **Recommended future authority:** A versioned active roadmap plus `docs/CURRENT_PILOT_SCOPE.md` for the current pilot boundary.
- **Code verification still required:** Yes. Confirm which features are actually enabled in production; source presence does not prove deployment or feature-flag activation.

### DCR-003 [P1] Master plan phase boundary is not synchronized with implementation

- **File A:** `doc/RESTAURANT_POS_CLOUD_READY_MASTER_PLAN_AND_CODEX_SKILL.md:21-46,872-1100`
- **File B / code evidence:** `restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java`; current Pad Direct controllers/services; `restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md`
- **Conflicting statements:** The master plan explicitly places Android shell and PAD_DIRECT after cloud readiness, while later code and runbooks implement those capabilities.
- **Current most likely fact:** The document remains a policy/history baseline, not an accurate status report for the current branch.
- **Confidence:** High
- **Recommended future authority:** Split policy/guardrails, current architecture, and active roadmap into separate documents; retain the master plan as historical context.
- **Code verification still required:** Yes. Check the actual enabled printing mode, device registrations, and worker state in each environment.

### DCR-004 [P1] Pad app README claims the production worker is not implemented

- **File A:** `restaurant-pad-app/README_PAD_APP.md:5-15,47-53`
- **File B / code evidence:** `restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java`; `restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md:3-27`
- **Conflicting statements:** The README describes only a WebView/native TCP POC and says live print_jobs processing is not present. `MainActivity.java` contains pairing, pending jobs, manual printing, and foreground worker behavior.
- **Current most likely fact:** The README describes an earlier PR stage and is stale for the current source tree.
- **Confidence:** High
- **Recommended future authority:** `restaurant-pad-app/docs/ARCHITECTURE.md` for current structure and `PAD_DIRECT_SEMI_AUTO_RUNBOOK.md` for current operation, after a future update.
- **Code verification still required:** Yes. Verify the APK version and whether the deployed build includes the current worker code.

### DCR-005 [P1] Android architecture document stops at the TCP POC

- **File A:** `restaurant-pad-app/docs/ARCHITECTURE.md:3,85-103`
- **File B / code evidence:** `restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java`; `restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md:8-15`
- **Conflicting statements:** The architecture document says the production polling worker remains future work. Current code and the semi-auto runbook describe a foreground worker.
- **Current most likely fact:** The document is an early-stage design record, not a current architecture description.
- **Confidence:** High
- **Recommended future authority:** A current Pad architecture document linked to the exact Android package/classes and a separate operator runbook.
- **Code verification still required:** Yes. Confirm the installed app build and release/debug behavior.

### DCR-006 [P1] Manual/Pending docs and semi-auto runbook describe different worker states without phase labels

- **File A:** `restaurant-pad-app/docs/PAD_DIRECT_PENDING_JOBS.md:3-8,118-134` and `PAD_DIRECT_MANUAL_PRINT.md:3-4,55-69`
- **File B:** `restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md:3-15,21-37`
- **Conflicting statements:** The manual/Pending docs say there is no background polling or automatic printing; the semi-auto runbook says the app has an operator-controlled foreground worker. These can describe separate historical phases, but the documents do not consistently label them as historical or manual-only.
- **Current most likely fact:** The current source supports both a manual one-job flow and a foreground semi-auto flow; the semi-auto runbook is the current operational path.
- **Confidence:** High
- **Recommended future authority:** Semi-auto runbook for current operation; manual/Pending docs should be explicitly marked phase-specific historical/manual-only.
- **Code verification still required:** Yes. Verify the actual app version and `userAutoPrintEnabled` state during pilot operation.

### DCR-007 [P1] API contract omits current PAD_DIRECT endpoints

- **File A:** `doc/API.md:126-178`
- **File B / code evidence:** `backend/src/main/java/com/restaurant/system/printing/controller/PadPrintingController.java:54-120`; `backend/src/main/java/com/restaurant/system/printing/controller/OwnerPrintingController.java:204-210`
- **Conflicting statements:** `doc/API.md` lists pending, claim, payload, complete, fail, and release, but does not document `start-print` or the owner print-job acknowledgement endpoint that exist in the controllers.
- **Current most likely fact:** The executable controller mappings define the current endpoint surface; the document is incomplete.
- **Confidence:** High
- **Recommended future authority:** A checked API contract generated from controller mappings and DTOs, supplemented by security tests.
- **Code verification still required:** Yes. Confirm endpoint prefixes, request bodies, and response shapes against integration tests and deployed API version.

### DCR-008 [P1] Rollback instructions assume remote images while normal deployment uses local builds

- **File A:** `deployment/cloud/README_ROLLBACK.md:18-25`
- **File B / code evidence:** `README_GIT_DEPLOY_WORKFLOW.md:23-57`; `deployment/cloud/deploy.sh:94-103`
- **Conflicting statements:** The rollback guide tells operators to set known-good image tags and run `docker compose pull`. The normal workflow uses `restaurant-pos-*:local`, builds `backend` and `nginx` locally, and only pulls when `--pull-images` is explicitly requested.
- **Current most likely fact:** Default production-template deployment is local build; remote-image rollback is a separate mode and must be explicitly selected.
- **Confidence:** High
- **Recommended future authority:** One deployment mode matrix plus a rollback script/runbook that branches explicitly between local-build and remote-image modes.
- **Code verification still required:** Yes. Verify the actual server's `.env`, Compose project, image tags, and registry usage before executing rollback.

### DCR-009 [P2] Windows pilot README is duplicated byte-for-byte

- **File A:** `deployment/windows-pilot/README_WINDOWS_PILOT.md`
- **File B:** `deployment/windows-pilot/package/README_WINDOWS_PILOT.md`
- **Conflicting statements:** No textual conflict was found; `cmp` reports the files are identical. The conflict is governance ambiguity: both appear to be canonical.
- **Current most likely fact:** They are exact duplicates, so edits can diverge silently.
- **Confidence:** High
- **Recommended future authority:** The root Windows pilot README; package content should be generated or contain a pointer after explicit approval.
- **Code verification still required:** No for duplication; Yes before deleting or changing either copy because packaging scripts were not changed in this audit.

### DCR-010 [P1] SYSTEM_DOCUMENTATION claims a generated current snapshot but mixes history and plans

- **File A:** `SYSTEM_DOCUMENTATION.md:3-8,10-6897`
- **File B / evidence:** The same file's PR history, future plan sections, and current batch notes; `docs/CURRENT_PILOT_SCOPE.md`; `docs/PILOT_RELIABILITY_BATCH_RUNBOOK.md`
- **Conflicting statements:** The header presents the file as generated from the current codebase only, while the body contains historical PR narratives, future plans, operational instructions, and current-state summaries.
- **Current most likely fact:** It is a mixed reference and historical record, not a pure generated snapshot.
- **Confidence:** High
- **Recommended future authority:** Generated current snapshot, architecture documents, runbooks, and history should be separate artifacts.
- **Code verification still required:** No for the document's internal mixture; Yes for any individual runtime claim used operationally.

### DCR-011 [P1] Bilingual system design claims completeness it does not have

- **File A:** `doc/SystemDesign_Bilingual.md:4`
- **File B / code evidence:** `backend/src/main/java/com/restaurant/system/printing/controller/PadPrintingController.java`; `backend/src/main/java/com/restaurant/system/order/**`; `backend/src/main/resources/db/migration/V3__add_idempotent_order_submission.sql` through `V7__add_print_job_attention_acknowledgement.sql`
- **Conflicting statements:** The design says it is the source of truth for current code/runtime, but contains no substantive PAD_DIRECT, IndexedDB/outbox, or print-attention model coverage found in the current implementation.
- **Current most likely fact:** It is a historical/incomplete design overview.
- **Confidence:** High
- **Recommended future authority:** Split current architecture by domain and make migrations/entities/executable contracts authoritative for data behavior.
- **Code verification still required:** No for the gap; Yes before using any section as a current implementation contract.

### DCR-012 [P1] DatabaseDesign is not complete for the current migration chain

- **File A:** `doc/DatabaseDesign.md`
- **File B / code evidence:** `backend/src/main/resources/db/migration/V1__baseline_schema.sql` through `V7__add_print_job_attention_acknowledgement.sql`; current entities/repositories
- **Conflicting statements:** The document presents the MVP data model, while current migrations add menu revisions, idempotent submission/outbox fields, menu sort order, noodle defaults, routing snapshots, and print-job attention acknowledgement fields that are not represented as a complete current schema in the document.
- **Current most likely fact:** Applied schema is defined by the migration chain and current entity/mapper code; `DatabaseDesign.md` is historical design reference.
- **Confidence:** High
- **Recommended future authority:** Migration chain as executable authority, with a generated current schema reference and a separate conceptual data model.
- **Code verification still required:** Yes. Query the target database's Flyway history before making deployment/schema claims.

### DCR-013 [P1] AGENTS/MVP scope excludes capabilities now present in code

- **File A:** `AGENTS.md` scope and architecture rules, including the authentication/authorization and multi-store exclusions
- **File B / code evidence:** `backend/src/main/java/com/restaurant/system/common/auth/**`; store-scoped controllers/services; current frontend owner/admin and printing routes
- **Conflicting statements:** AGENTS describes authentication/authorization, multi-store support, and complex role permissions as out of MVP scope. Current code contains JWT/Bearer context, store access enforcement, capabilities, and multi-store routes.
- **Current most likely fact:** The codebase has moved beyond that MVP scope; the AGENTS scope text is historical unless explicitly limited to a legacy MVP.
- **Confidence:** High
- **Recommended future authority:** Current guardrails/security architecture document for present constraints; keep MVP scope as historical.
- **Code verification still required:** Yes. Confirm the intended supported roles and stores for the deployed pilot.

### DCR-014 [P2] Root README describes current features as future/later MVP work

- **File A:** `README.md:3-14,38`
- **File B / code evidence:** `frontend/src/features/owner-admin/**`; `frontend/src/features/feature-flags/featureConfig.ts:13`; current backend admin/printing controllers
- **Conflicting statements:** The README describes KDS and serving/inventory as MVP features while suggesting admin pages are later. Current frontend has broad admin/printing capabilities and explicitly disables KDS in the current printer-restaurant configuration.
- **Current most likely fact:** The README is a historical MVP overview, not a feature-flag/status reference.
- **Confidence:** Medium/High
- **Recommended future authority:** Current pilot scope plus feature configuration and deployment checklist.
- **Code verification still required:** Yes. Validate enabled features per profile/store.

### DCR-015 [P2] Pilot scope label is behind the current reliability batch

- **File A:** `docs/CURRENT_PILOT_SCOPE.md:3-5`
- **File B:** `docs/PILOT_RELIABILITY_BATCH_RUNBOOK.md`; current branch commits after the stated PR-PILOT-RELIABILITY-1 boundary
- **Conflicting statements:** The scope says it is after PR-PILOT-RELIABILITY-1, while the current branch includes later pilot reliability commits and a batch runbook.
- **Current most likely fact:** The pilot scope document's version marker is stale, even if much of its boundary remains useful.
- **Confidence:** High
- **Recommended future authority:** A versioned pilot scope document tied to a commit/tag and the active batch runbook.
- **Code verification still required:** Yes. Confirm which batch is actually deployed.

### DCR-016 [P1] API example hardcodes store 1

- **File A:** `doc/API.md:182`
- **File B / code/policy evidence:** `doc/CODEX_SKILL_RESTAURANT_POS_CLOUD_READY_MASTER_PLAN_AND_CODEX_SKILL.md` store-scoping guardrails; frontend store-context code; backend StoreAccessService/capability checks
- **Conflicting statements:** The API example uses `store_id=1` as a literal request example while current multi-store/cloud guardrails prohibit production paths from defaulting to store 1.
- **Current most likely fact:** Store IDs must come from authenticated/store context or an explicitly selected store, not a production default.
- **Confidence:** High
- **Recommended future authority:** Store-scoped API contract and auth/store-context documentation.
- **Code verification still required:** Yes. Search all current examples and test fixtures before changing operational docs.

### DCR-017 [P2] AGENTS references documentation paths that do not exist

- **File A:** `AGENTS.md` documentation rules
- **File B:** repository inventory: `doc/DatabaseDesign.md`, `doc/API.md`, `doc/MVP_Scope.md`; no tracked `docs/DatabaseDesign.md` or `docs/API_bilingual.md`
- **Conflicting statements:** AGENTS instructs contributors to read `docs/DatabaseDesign.md` and `docs/API_bilingual.md`, but those exact paths are absent; similarly named files are under `doc/`.
- **Current most likely fact:** The intended documents are probably the `doc/` files, but this cannot be assumed as a workflow contract without owner confirmation.
- **Confidence:** High for missing paths; Medium for intended replacements.
- **Recommended future authority:** Repository workflow index with validated links and explicit canonical paths.
- **Code verification still required:** No for path absence; Yes before updating instruction text or automation.

## Oversized or Mixed-Use Context Risks

These are not necessarily textual contradictions, but they make reliable working context difficult:

| File | Approx. lines | Risk |
| --- | ---: | --- |
| `SYSTEM_DOCUMENTATION.md` | 6,897 | Generated snapshot, history, plans, and runbooks are mixed. |
| `doc/RESTAURANT_POS_CLOUD_READY_MASTER_PLAN_AND_CODEX_SKILL.md` | 1,777 | Master plan, guardrails, architecture, and future roadmap are mixed. |
| `doc/PAD_APP_PR_PROMPTS.md` | 1,310 | Long historical prompt set that can be mistaken for current instructions. |
| `doc/PAD_APP_ARCHITECTURE.md` | 1,148 | Early-stage architecture and future target mixed. |
| `doc/DatabaseDesign.md` | 1,115 | Large conceptual MVP schema that is incomplete against migrations. |
| `doc/SystemDesign_Bilingual.md` | 1,012 | Claims current authority but omits major current domains. |
| `doc/ROADMAP.md` | 1,032 | Historical current-stage assumptions and future milestones coexist. |

## Generated Documentation Used as Authority Risk

- Bundled Android web assets, manifests, `build-info.json`, and packaged `frontend/dist` are generated outputs. They can prove what was packaged, but they do not define source behavior.
- `frontend/README.md` is a default scaffold README and should not be used as a frontend architecture source.
- Diagram PNGs under `doc/diagram/` are visual references only.
- `SYSTEM_DOCUMENTATION.md` says it is generated, but its narrative sections mean it should be treated as a mixed snapshot/history document until split.

## Items Requiring Runtime or Deployment Verification

The following cannot be proven by this repository-only audit:

1. Which Spring profile and feature flags are active on the production server.
2. Whether Flyway V1-V7 are applied to the target database.
3. Which Compose services/images/volumes are currently running.
4. Which Android APK version is installed and whether its worker preference is enabled.
5. Whether the server's current API behavior matches the checked-in controllers.
6. Whether generated bundled assets match the current frontend source.


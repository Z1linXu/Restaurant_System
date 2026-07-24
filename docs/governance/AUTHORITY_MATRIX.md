# Authority Matrix

## Purpose and limits

This is the Phase 2 authority matrix for reconciling documentation claims with
executable repository evidence. It is an audit artifact, not a runtime
configuration and not a replacement for any existing document. No claim below
proves that the corresponding code is deployed.

The terms used here are deliberately separate:

- Repository authority: the source code, tests, migrations, or configuration
  that define behavior in this checkout.
- Documentation candidate: a proposed future canonical document. The candidate
  may not exist yet.
- Deployed runtime authority: the actual production commit, container image,
  Spring profile, database history, environment, and installed APK observed in
  the target environment.
- Historical/supporting document: useful context that must not override
  executable evidence when it describes an older state.

## Matrix

| Domain | Canonical Documentation Candidate | Executable Repository Authority | Deployed Runtime Authority | Historical / Supporting Documents | Do not treat as current authority |
|---|---|---|---|---|---|
| Authentication and Bearer/X-User-Id fallback | A future security contract covering token precedence and profile rules | backend/src/main/java/com/restaurant/system/auth/filter/AuthTokenFilter.java, backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java, backend/src/main/java/com/restaurant/system/auth/service/TokenService.java, frontend/src/services/apiClient.ts, backend/src/main/resources/application-local.yml, application-cloud.yml, application-pilot.yml | Deployed image commit, active Spring profile, effective auth properties, request logs showing Bearer and rejected fallback | doc/API.md, SYSTEM_DOCUMENTATION.md Cloud Ready PR3, doc/CODEX_SKILL_RESTAURANT_POS_GUARDRAILS.md | doc/API.md where it describes X-User-Id as the normal API path |
| Authorization, capabilities, memberships, StoreAccessService | A future authorization and store-scope contract | AuthorizationService, StoreAccessService, RoleCapabilityRegistry, Capability, controller checks, authorization tests | Effective role/capability data, memberships, active image, denied/allowed requests in the target store | SYSTEM_DOCUMENTATION.md PR7-1/PR11C, guardrails | Any UI route list without backend capability and store-scope evidence |
| Order lifecycle and submitted-order add-only updates | A future order lifecycle/API contract | OrderController, IdempotentOrderSubmissionController, OrderServiceImpl, OrderUpdateBatch, repositories, order tests | Deployed image plus database order status/current_revision and API behavior | AGENTS.md, doc/MVP_Scope.md, SYSTEM_DOCUMENTATION.md order sections | Old MVP lifecycle text that omits idempotency or update batches |
| Offline menu cache, local drafts, Outbox, idempotent submit | A future offline ordering contract | frontend/src/offline/offlineDatabase.ts, frontend/src/offline/menuCache.ts, frontend/src/hooks/useMenuCatalog.ts, frontend/src/offline/localDrafts.ts, frontend/src/offline/orderOutbox.ts, frontend/src/services/orderOutboxProcessor.ts, backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java, V2/V3 migrations, tests | Installed frontend/asset build, IndexedDB schema at runtime, deployed API behavior, outbox records and logs | SYSTEM_DOCUMENTATION.md offline sections, restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md, docs/PILOT_RELIABILITY_BATCH_RUNBOOK.md | Any document that says the menu or submit path is online-only |
| Printing modes REAL/MOCK/PAD_DIRECT/DISABLED | A future printing mode and cloud boundary contract | backend/src/main/java/com/restaurant/system/printing/PrintingMode.java, FeatureFlagService, PrintDispatcherServiceImpl, ProductionSafetyConfig, application profiles, printing tests | Effective printing mode per store, active profile, cloud guard logs, Print Center records | doc/CODEX_SKILL_RESTAURANT_POS_GUARDRAILS.md printing rules, SYSTEM_DOCUMENTATION.md PR5/PR6/PAD_DIRECT sections | Historical docs that describe only REAL/MOCK or backend socket printing in cloud |
| PAD_DIRECT print-job state machine | A future PAD_DIRECT protocol contract | PrintJobStatus, PrintJob, PrintJobRepository.claimPadDirectJob, PadPrintingController, PadPrintJobServiceImpl, PrintJobAttempt, tests | print_jobs and print_job_attempts rows, device headers, API responses, deployed logs | restaurant-pad-app/docs/PAD_DIRECT_PENDING_JOBS.md, restaurant-pad-app/docs/PAD_DIRECT_MANUAL_PRINT.md, restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md | Older manual-print docs that omit PRINTING/start-print |
| Android Pad pairing, foreground worker, recovery | A future Android runtime/runbook contract | MainActivity, PadDirectWorkerPolicy, RestaurantPadDevice bridge, StoreDeviceController/Service, Android docs, APK build metadata | Installed APK version/signature, SharedPreferences/worker state, logcat, last_seen_at, active device rows | restaurant-pad-app/docs/ARCHITECTURE.md, restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md, restaurant-pad-app/docs/PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md | Android architecture/README documents predating pairing, recovery, or semi-auto worker |
| Multi-printer PAD_DIRECT routing | A future payload and assignment contract | PrintJob.printer_id, PrintJobServiceImpl.markPadDirectQueued, PadPrintJobPayloadResponse, PrinterConfig, assignment controller/service, Android payload consumer | Current assignment rows, payload response, physical endpoint reachability from each Pad, printed_by_device_id | restaurant-pad-app/docs/PAD_DIRECT_MULTI_PRINTER_ROUTING.md, backend/src/main/java/com/restaurant/system/printing/controller/OwnerPrintingController.java | A single local printer setting on the Pad as the routing authority |
| REST endpoint and DTO contract | A generated or manually maintained endpoint catalog tied to controllers/DTOs | Controller annotations, DTO classes, auth checks, service interfaces, API tests | Deployed OpenAPI/route behavior if available, HTTP status/body samples from the target environment | doc/API.md, MISSING_REFERENCE: docs/API_bilingual.md, frontend service files | doc/API.md when its routes or auth requirements differ from controller mappings |
| Flyway chain and schema increment | A future schema ledger generated from migration files plus runbook | backend/src/main/resources/db/migration/V*.sql, pom.xml Flyway config, JPA entities, migration tests/config | flyway_schema_history in the target DB, database version, backup/restore evidence | DatabaseDesign.md, SystemDesign_Bilingual.md, master plan | A schema dump or design document without target Flyway history |
| Feature flags and pilot scope | A future feature/pilot contract defining defaults and override precedence | frontend/src/features/feature-flags/featureConfig.ts, backend/src/main/java/com/restaurant/system/common/feature/FeatureConfigProperties.java, FeatureFlagService, route metadata, application profiles | Effective frontend build, active Spring profile/properties, feature API/UI behavior | docs/CURRENT_PILOT_SCOPE.md, SYSTEM_DOCUMENTATION.md PR7-2, restaurant-pad-app/docs/PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md | Roadmap/master-plan statements about planned or disabled features |
| Cloud Compose, deployment, backup, restore, rollback | A future deployment runbook with one Compose source and one rollback source | deployment/cloud/docker-compose.yml, deploy.sh, backup-db.sh, restore-db.sh, health-check.sh, Dockerfiles | Server Compose project, container IDs/images, mounts, env file, backup files, health results | README_CLOUD_DEPLOYMENT.md, README_ROLLBACK.md, FINAL_SMOKE_TEST_CHECKLIST.md, Windows pilot docs | Windows pilot instructions and historical deployment docs applied to cloud |
| Generated frontend/Android artifacts | A future build/release provenance contract | frontend build scripts, Vite config, Android asset manifest/build-info, Gradle configuration | Artifact checksum, source commit, CI/build log, installed APK package/version | restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md, asset manifest, build-info.json | Generated assets alone without matching source commit/build metadata |

## Reconciliation rules

1. If documentation and code disagree, report the disagreement; do not repair
   it in this phase.
2. If code and configuration agree but the target runtime has not been checked,
   the classification remains RUNTIME_UNKNOWN for deployment claims.
3. A migration file proves intended schema evolution in the repository, not
   successful application to a database.
4. A frontend or Android asset proves only what is inside that artifact. It
   does not prove which artifact is installed or served.
5. A route visible in the frontend does not grant authorization. Backend
   capability and StoreAccessService checks remain required.

## Current authority gaps

- There is no single current document that fully describes authentication,
  offline ordering, PAD_DIRECT, and cloud deployment together.
- Existing documents mix historical PR records, current behavior, and planned
  work. The Phase 1 conflict report remains the inventory of those conflicts.
- Runtime authority is unverified for the active production commit/profile,
  Flyway history, effective feature flags, printing mode, device state, and
  installed APK.

# Code-to-Documentation Traceability

## Scope and classification

This report reconciles material documentation claims to executable evidence in
the repository. It does not infer production deployment. A repository fact is
marked RUNTIME_UNKNOWN when the target environment must be inspected.

Classification values:

- MATCHED: the documented behavior and repository evidence agree.
- PARTIALLY_MATCHED: the document covers the behavior but omits or differs on
  material details.
- STALE_DOCUMENT: the repository has newer executable behavior.
- DOCUMENT_ONLY: the claim has no corresponding executable evidence found.
- CODE_ONLY: executable evidence exists but no adequate current documentation
  claim was found.
- RUNTIME_UNKNOWN: repository evidence exists, but deployed state is unverified.

## 1. Authentication and fallback behavior

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Authentication | Cloud/prod must use Bearer auth and disable X-User-Id fallback | SYSTEM_DOCUMENTATION.md, Cloud Ready PR3; doc/CODEX_SKILL_RESTAURANT_POS_GUARDRAILS.md, Production Safety Rules | backend/src/main/java/com/restaurant/system/auth/filter/AuthTokenFilter.java, doFilterInternal; backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java, getRequiredUser; application-cloud.yml and application-pilot.yml set app.auth.x-user-id-fallback-enabled=false; frontend/src/services/apiClient.ts, request headers | ProductionSafetyConfigTest; apiClient.test.ts | MATCHED for repository configuration; RUNTIME_UNKNOWN for deployment | High | Future security contract | Yes | Active profile, effective property, image commit, request with Bearer, request without Bearer/X-User-Id, logs/status |
| Authentication | X-User-Id remains a conditional local/compatibility fallback | doc/API.md, Authentication; SYSTEM_DOCUMENTATION.md local-development sections | RequestUserContextService.USER_ID_HEADER and fallback flag; application.yml enables fallback locally; apiClient deletes X-User-Id and sends Bearer | AuthorizationServiceTest; ProductionSafetyConfigTest | PARTIALLY_MATCHED | High | Future security contract | Yes for local/pilot mode only | Effective app.auth.x-user-id-fallback-enabled and representative request behavior |

## 2. Authorization, capabilities, memberships, and store scope

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Authorization | Access is authenticated user + capability + StoreAccessService scope | doc/CODEX_SKILL_RESTAURANT_POS_GUARDRAILS.md, Authorization; SYSTEM_DOCUMENTATION.md, Store Access sections | AuthorizationService.requireForStore; StoreAccessService.canAccessStore; RoleCapabilityRegistry; Capability; controller calls in printing/menu/order | AuthorizationServiceTest; StoreAccessServiceTest; StoreDeviceControllerTest | MATCHED | High | Future authorization/store-scope contract | Yes | Deployed role/membership rows and allow/deny requests for own and foreign stores |
| Authorization | FRONTDESK has menu and printing management but not staff/audit/platform management | SYSTEM_DOCUMENTATION.md, PR11C/security notes | RoleCapabilityRegistry FRONTDESK entries; OwnerPrintingController.requirePrintingAccess; menu controller capability checks; platform/audit controllers | AuthorizationServiceTest; StoreDeviceControllerTest | PARTIALLY_MATCHED | High | Future authorization/store-scope contract | Yes | Effective role capability data and HTTP 2xx/403 samples per endpoint |

## 3. Order lifecycle and add-only submitted updates

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Orders | Draft submission transitions to submitted/preparing and creates downstream work | AGENTS.md, Core Business Flow; SYSTEM_DOCUMENTATION.md order/printing sections | OrderController.submit; OrderServiceImpl.submitOrder; kitchen task, beverage, production, inventory creation; dispatchAfterCommit | OrderServiceImplTest | PARTIALLY_MATCHED | High | Future order lifecycle/API contract | Yes | Deployed response/status and order, task, outbox, print-job rows for one test order |
| Orders | Submitted orders accept add-only update batches with idempotency keys | SYSTEM_DOCUMENTATION.md, submitted-order/update sections | OrderServiceImpl.createOrderUpdate; OrderUpdateBatch; OrderUpdateBatchRepository.findByOrderIdAndIdempotencyKey; CreateOrderUpdateRequest; update dispatch calls | OrderServiceImplTest.submittedOrderLocksOldItemsAndUsesIdempotentUpdateBatchForNewItems; OrderServiceImplTest.updateDispatchesHotKitchenWhenBatchHasPrintableContent | MATCHED | High | Future order lifecycle/API contract | Yes | API request/response, order.current_revision, update batch, added item revision, print jobs |
| Orders | Optimistic/order conflict is distinct from menu drift | SYSTEM_DOCUMENTATION.md, offline/menu consistency sections | IdempotentOrderSubmissionServiceImpl.validateSnapshotsAndLogMenuDrift logs drift warnings; submit idempotency conflict compares payload hash; OrderServiceImpl locks order/context | IdempotentOrderSubmissionServiceImplTest; OrderSubmissionHashServiceImplTest | PARTIALLY_MATCHED | High | Future order lifecycle and offline contract | Yes | 409 response body/code for true conflict and successful old-menu snapshot submission |

## 4. Offline ordering, cache, drafts, Outbox, idempotency

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Offline menu | Menu cache is store/account scoped and cache-first with background revision check | restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md; SYSTEM_DOCUMENTATION.md offline ordering sections | offlineDatabase stores menuHeads/menuSnapshots; menuCache scope/hash/atomic replace; useMenuCatalog cache-first then fetchMenuRevision/fetchMenuCatalog | menuCache.test.ts; useMenuCatalog.test.ts; menuCacheNotice.test.ts | MATCHED | High | Future offline ordering contract | Yes | Installed asset build, IndexedDB schema, cache head/snapshot, revision response and UI source label |
| Offline drafts | Drafts and Outbox persist frozen item snapshots and survive retry states | SYSTEM_DOCUMENTATION.md offline ordering sections | localDrafts.ts; orderOutbox.ts; useDraftOrder.ts; OrderOutboxProvider; orderOutboxProcessor.ts | localDrafts.test.ts; orderOutbox.test.ts; orderLifecycle.test.ts; orderOutboxProcessor.test.ts | MATCHED for repository | High | Future offline ordering contract | Yes | IndexedDB records before/after restart, state transitions, localDraftId/clientOrderId |
| Idempotency | Same store/key replays the existing order; payload mismatch conflicts | SYSTEM_DOCUMENTATION.md idempotency sections; master plan | IdempotentOrderSubmitRequest; IdempotentOrderSubmissionServiceImpl.submit; order_submission_requests unique store/key; V3 migration | IdempotentOrderSubmissionServiceImplTest; OrderSubmissionHashServiceImplTest | MATCHED for repository; RUNTIME_UNKNOWN for applied schema | High | Future offline ordering contract | Yes | Flyway history, unique constraint, replay request response and database rows |
| Offline behavior | Menu compatibility drift is retryable/warning rather than automatically an order conflict | docs/PILOT_RELIABILITY_BATCH_RUNBOOK.md; SYSTEM_DOCUMENTATION.md cache consistency sections | orderOutbox.ts classifySubmissionFailure/recoverMenuCompatibilityOutboxRecord; backend validateSnapshotsAndLogMenuDrift | menuCacheNotice.test.ts; orderOutbox tests; idempotent submission tests | PARTIALLY_MATCHED | Medium | Future offline ordering contract | Yes | Actual 400 code/body from deployed API and UI state after old-menu submit |

## 5. Printing modes and cloud boundary

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Printing | REAL, MOCK, PAD_DIRECT, and DISABLED are supported modes | doc/CODEX_SKILL_RESTAURANT_POS_GUARDRAILS.md, Print modes; SYSTEM_DOCUMENTATION.md PR5 | PrintingMode enum; PrintDispatcherServiceImpl dispatch branches; application profiles | CloudPrintingGuardTest; PrintDispatcherServiceImplTest; PrintJobServiceImplTest | MATCHED for repository | High | Future printing mode/cloud boundary contract | Yes | Effective store printing mode and one job per mode |
| Printing | Cloud/prod must not open sockets to private LAN printers; PAD_DIRECT queues payload locally | SYSTEM_DOCUMENTATION.md Cloud Ready PR5; deployment/cloud README | ProductionSafetyConfig; CloudPrivatePrinterGuard/transport guard; PrintDispatcherServiceImpl PAD_DIRECT branch; PrintJobServiceImpl.markPadDirectQueued | CloudPrintingGuardTest; dispatcher tests | MATCHED for repository; RUNTIME_UNKNOWN for runtime | High | Future printing mode/cloud boundary contract | Yes | Active profile, mode, cloud guard log, backend socket telemetry, pending PAD_DIRECT job |

## 6. PAD_DIRECT state machine

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| PAD_DIRECT | Job states include PENDING, CLAIMED, PRINTING, PRINTED, FAILED, CANCELLED | restaurant-pad-app/docs/PAD_DIRECT_MANUAL_PRINT.md and PAD_DIRECT_SEMI_AUTO_RUNBOOK.md; SYSTEM_DOCUMENTATION.md PAD_DIRECT sections | PrintJobStatus; PrintJob; PadPrintingController; PadPrintJobServiceImpl; PrintJobRepository.claimPadDirectJob | PadPrintJobServiceImplTest; PrintJobResponseTest | MATCHED for repository | High | Future PAD_DIRECT protocol contract | Yes | Target DB enum/value rows, API status transitions, deployed logs |
| PAD_DIRECT | Claim is atomic and scoped to device/store; start-print marks PRINTING; complete/fail close the attempt | restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md | claimPadDirectJob atomic update; startPrint; getPayload; completeJob; failJob; PrintJobAttempt updates | PadPrintJobServiceImplTest; PrintDispatcherServiceImplTest | MATCHED for repository; RUNTIME_UNKNOWN for runtime | High | Future PAD_DIRECT protocol contract | Yes | Concurrent claim result, attempt row, token presence and device ID only, lease timestamps and final status |
| PAD_DIRECT | Retry count is recorded on failure but is not proof of automatic retry | Print Center/pilot docs and SYSTEM_DOCUMENTATION.md failure sections | PadPrintJobServiceImpl.failJob increments retry_count; repository pending query only returns PENDING and expired CLAIMED; no automatic FAILED requeue in this path | PadPrintJobServiceImplTest | PARTIALLY_MATCHED | Medium | Future PAD_DIRECT protocol contract | Yes | Job rows before/after fail, pending query result, worker logs |

## 7. Android pairing, worker recovery, multi-printer

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Android pairing | Device registration returns a token once and runtime calls use device headers | restaurant-pad-app/docs/PAD_DIRECT_PENDING_JOBS.md; PAD_DIRECT_MANUAL_PRINT.md | StoreDeviceController; StoreDeviceServiceImpl token hashing/auth; MainActivity bridge/prefs | StoreDeviceServiceImplTest; StoreDeviceControllerTest | MATCHED for repository | High | Future Android PAD runtime contract | Yes | Installed APK, device row, token receipt handling, heartbeat and auth logs |
| Android worker | Foreground semi-auto worker has user enable/disable, recovery backoff, lifecycle resume, and watchdog state | PAD_DIRECT_SEMI_AUTO_RUNBOOK.md; PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md | MainActivity worker fields/start/stop/tick/kick/watchdog/lifecycle methods; PadDirectWorkerPolicy | Android runtime tests are not present in repository; docs/manual validation only | PARTIALLY_MATCHED | High | Future Android PAD runtime contract | Yes | APK version, logcat state transitions, screen/lock/background test, lastPollAt and worker state |
| Routing | Each job payload supplies assigned printer endpoint and Android uses payload routing | PAD_DIRECT_MULTI_PRINTER_ROUTING.md | PrintJob.printer_id; PadPrintJobPayloadResponse.from; PrintJobServiceImpl payload generation; PrinterConfig | PadPrintJobServiceImplTest; PrintJobResponseTest | MATCHED for repository | High | Future PAD_DIRECT protocol contract | Yes | Payload fields, assignment rows, device-to-printer connectivity, printed device and printer evidence |

## 8. REST endpoints and DTO contracts

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| REST | Order idempotent submit is POST /api/v1/stores/{storeId}/orders/idempotent-submit | MISSING_REFERENCE: docs/API_bilingual.md; SYSTEM_DOCUMENTATION.md offline sections | IdempotentOrderSubmissionController @PostMapping; IdempotentOrderSubmitRequest | IdempotentOrderSubmissionServiceImplTest | PARTIALLY_MATCHED | High | Future REST/DTO catalog | Yes | Deployed route, auth status, response body, request DTO acceptance |
| REST | Printing admin and Pad device routes are controller-defined and capability-scoped | doc/API.md; SYSTEM_DOCUMENTATION.md printing sections | OwnerPrintingController; StoreDeviceController; PadPrintingController mappings and auth calls | StoreDeviceControllerTest; PadPrintJobServiceImplTest; PrintDispatcherServiceImplTest | PARTIALLY_MATCHED where docs lag | High | Future REST/DTO catalog | Yes | Deployed route inventory and representative 2xx/401/403/409 responses |
| REST | Payload carries assigned printer id/host/port/encoding/paper and ESC/POS base64 | PAD_DIRECT_MULTI_PRINTER_ROUTING.md | PadPrintJobPayloadResponse and from(job, printer) | PrintJobResponseTest; PadPrintJobServiceImplTest | MATCHED | High | Future PAD_DIRECT protocol contract | Yes | Actual JSON payload from target deployment with sensitive token redacted |

## 9. Flyway migration chain

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Schema | V1 is the baseline and V2-V7 add menu revision, idempotency/outbox, sort order, noodle default, routing snapshots, print attention acknowledgement | SYSTEM_DOCUMENTATION.md Cloud Ready PR2; master plan DB section | backend/src/main/resources/db/migration/V1__baseline_current_schema.sql; V2__add_versioned_menu_revision.sql; V3__add_idempotent_order_submission_and_dispatch_outbox.sql; V4__add_menu_item_sort_order.sql; V5__set_cold_chicken_noodle_default_type.sql; V6__add_order_item_routing_snapshots.sql; V7__add_print_job_attention_acknowledgement.sql; backend/src/main/resources/application-cloud.yml and application-pilot.yml use ddl-auto validate and Flyway | No migration-specific test class identified; ProductionSafetyConfigTest and RuntimeDataSeederPolicyTest cover related startup/seed policy | MATCHED as repository intent | High | Future schema ledger/runbook | Yes | SELECT from flyway_schema_history, schema version, migration checksums, backup timestamp |
| Schema | Existing non-empty cloud/pilot DB is baselined and follow-up migrations applied by startup | SYSTEM_DOCUMENTATION.md PR2; cloud deployment README | application-cloud.yml/pilot.yml Flyway properties; V1 baseline comments | No proof of target DB application in repository tests | PARTIALLY_MATCHED | High | Future schema ledger/runbook | Yes | Flyway history rows and startup logs from target environment |

## 10. Feature flags and pilot scope

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Features | Current defaults enable CORE_POS, PRINTING, ADMIN, ANALYTICS and disable KDS/PLATFORM/DEVELOPER_TOOLS | docs/CURRENT_PILOT_SCOPE.md; SYSTEM_DOCUMENTATION.md PR7-2 | frontend featureConfig.ts; backend FeatureConfigProperties; application profiles; App route gate | ProductionSafetyConfigTest; frontend feature tests if present | MATCHED for checked-in defaults | High | Future feature/pilot contract | Yes | Built frontend version, active backend profile/effective properties, route behavior |
| Features | KDS/Pickup UI is disabled without removing HOT_KITCHEN printing | SYSTEM_DOCUMENTATION.md PR7-2 | frontend routeFeatureMetadata/getRequiredFeatureForPath; App route gate; backend printing feature separate from KDS | No dedicated frontend route test identified; manual documentation only | MATCHED for repository | High | Future feature/pilot contract | Yes | Access /pickup and /kds paths; create and inspect HOT_KITCHEN print job |

## 11. Cloud deployment package

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Deployment | Compose project has db, backend, nginx and backend uses cloud profile | deployment/cloud/README_CLOUD_DEPLOYMENT.md, Architecture/Build and Start; SYSTEM_DOCUMENTATION.md deployment regression note | deployment/cloud/docker-compose.yml; backend/Dockerfile; deployment/cloud/deploy.sh | Local compose config/build checks if run; no proof of server state | MATCHED for repository | High | Future cloud deployment runbook | Yes | docker compose ps/config/images/mounts/env on target server |
| Deployment | Default deploy validates, builds local backend/nginx images, starts compose; pull is explicit | deployment/cloud/README_CLOUD_DEPLOYMENT.md; README_ROLLBACK.md | deploy.sh option parser, --validate, --pull-images, build backend nginx, up -d | Shell syntax/config checks | MATCHED for repository | High | Future cloud deployment runbook | Yes | Script version, command log, resulting container IDs and health |
| Deployment | Backup/restore scripts use externalized credentials and require explicit restore confirmation | deployment/cloud/README_ROLLBACK.md | backup-db.sh, restore-db.sh, .env.example, compose volume/data paths | Shell syntax checks | MATCHED for repository | High | Future cloud deployment runbook | Yes | Backup file, pg_dump/pg_restore logs, restore rehearsal evidence |

## 12. Generated artifacts and source provenance

| Domain | Claim | Document path and section | Executable source evidence | Tests | Classification | Confidence | Future canonical document | Runtime verification required | Exact runtime evidence needed |
|---|---|---|---|---|---|---|---|---|---|
| Generated assets | Bundled Android web assets include build version, generated timestamp, schema version, manifest and hashes | restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md | restaurant-pad-app/android/app/src/main/assets/web/build-info.json and asset-manifest.json; frontend build scripts | frontend/src/utils/appCacheVersion.test.ts; no source-commit proof in the asset metadata inspected | PARTIALLY_MATCHED | High | Future build/release provenance contract | Yes | APK embedded asset hashes, source commit/build log, served frontend build-info |
| Generated assets | The bundled asset build is the frontend actually used by a device | Android docs and local development docs | MainActivity asset loader plus current asset directory | No installed-device proof | RUNTIME_UNKNOWN | High | Future build/release provenance contract | Yes | Installed APK version, extracted assets, WebView URL/mode, matching source commit |

## Runtime evidence boundary

The repository supports a strong description of intended behavior, but it cannot
answer these without a target-environment read-only inspection:

- Which commit and branch are serving the cloud frontend/backend.
- Which Spring profile and effective environment variables are active.
- Whether V1-V7 exist in flyway_schema_history.
- Which store printing mode and assignments are live.
- Which Pad devices are active, paired, or polling.
- Which APK and bundled asset build are installed.
- Whether Compose has the expected service names, volumes, and images.

## Suggested evidence capture format

For each runtime check, record timestamp, environment identifier, commit/image
digest, command, redactions, raw result location, and operator. Redact JWTs,
device tokens, passwords, database URLs, private printer credentials, and
customer data. Do not use a successful local build as a substitute for a
production evidence record.

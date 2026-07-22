# Document Inventory

## Audit Scope

- **Repository:** `Restaurant_System`
- **Audit commit:** `46d8a906fc270983fba66698e279095243780fd0`
- **Branch:** `codex/pilot-site-reliability-batch`
- **Date:** 2026-07-22
- **Mode:** Read-only governance audit. No application code or existing documentation was changed.
- **Tracked Markdown / instruction files inventoried:** 47

This inventory records what each document appears to be for. A document's existence is not treated as proof that its statements match the current code or deployed runtime. The executable sources of truth remain source code, configuration, database migrations, and deployed environment state where applicable.

## Classification Legend

| Classification | Meaning |
| --- | --- |
| Architecture source of truth | Intended authoritative description of current system structure and boundaries. Current completeness must be verified against code. |
| API contract | Request/response, endpoint, authentication, or event contract. |
| Operational runbook | Steps for deployment, field operation, recovery, or verification. |
| Development instruction / skill | Rules for contributors, agents, or development workflow. |
| Active plan | Current scope, roadmap, or planned work. |
| Historical record | Earlier design, MVP, phase, or implementation record. |
| Generated reference | Generated, packaged, scaffolded, or code-derived reference material. It is not authoritative merely because it is generated. |
| Duplicate | Content duplicated elsewhere. |
| Unclear | Purpose or lifecycle is not explicit enough to establish authority. |

## Root Governance and Product Documents

| File | Classification | Apparent role / currentness | Governance note |
| --- | --- | --- | --- |
| `AGENTS.md` | Development instruction / skill | Repository-wide agent and MVP development rules. Several scope statements describe an earlier MVP. | Instruction source, but it references missing `docs/DatabaseDesign.md` and `docs/API_bilingual.md`; actual files are under `doc/`. Its auth, multi-store, and permissions exclusions conflict with current code. |
| `CODEX_WORKFLOW.md` | Development instruction / skill | Pointer to the canonical workflow. | Keep as a pointer only; authority is `doc/CODEX_WORKFLOW.md`. |
| `ROADMAP.md` | Active plan | Pointer to `doc/ROADMAP.md`. | Keep as a pointer only; the canonical roadmap itself is stale in several current-stage claims. |
| `NEXT_PHASES_PROMPT.md` | Historical record / unclear | Phase 0-4 execution prompt. | Treat as historical prompt, not an active plan or code contract. |
| `README.md` | Unclear | Short MVP overview and local commands. | Claims “admin later” while the repository has substantial owner/admin features; not a current architecture authority. |
| `README_GIT_DEPLOY_WORKFLOW.md` | Operational runbook | Git-based cloud deployment workflow using local Docker tags by default. | Useful current deployment reference; must stay aligned with `deployment/cloud/deploy.sh`. |
| `README_SERVER_DEPLOY.md` | Operational runbook | Server update and recovery quick reference. | Current service names mostly match Compose; contains destructive reset commands that must remain explicitly non-routine. |
| `SYSTEM_DOCUMENTATION.md` | Generated reference / historical record / unclear | Large codebase snapshot plus PR history, architecture notes, runbooks, and current batch notes. | 6,897 lines and mixed purposes. Header says generated from current code, but the file also contains narrative history and plans. It should not be the sole authority for every topic. |

## Deployment and Pilot Operations

| File | Classification | Apparent role / currentness | Governance note |
| --- | --- | --- | --- |
| `deployment/cloud/FINAL_SMOKE_TEST_CHECKLIST.md` | Operational runbook | Cloud go/no-go checklist. | Current-looking; verify commands and profile names against Compose and application profiles. |
| `deployment/cloud/README_CLOUD_DEPLOYMENT.md` | Operational runbook | Cloud architecture/template and deployment instructions. | Current Compose model is `db` + `backend` + `nginx`; includes historical deployment regression notes. |
| `deployment/cloud/README_PRODUCTION_BOOTSTRAP.md` | Operational runbook | Production bootstrap procedure. | Must be treated as a manual runbook; bootstrap credentials and actual deployed state are not proven by this file. |
| `deployment/cloud/README_ROLLBACK.md` | Operational runbook | Image/code/database rollback guidance. | Unconditional `docker compose pull` conflicts with the default local-image workflow unless remote-image mode is selected explicitly. |
| `deployment/windows-pilot/README_WINDOWS_PILOT.md` | Operational runbook | Windows pilot setup and operation. | Exact duplicate of the package copy below. |
| `deployment/windows-pilot/package/README_WINDOWS_PILOT.md` | Duplicate | Packaged Windows pilot copy. | Byte-for-byte duplicate of `deployment/windows-pilot/README_WINDOWS_PILOT.md`; lifecycle/authority is unclear. |
| `docs/CURRENT_PILOT_SCOPE.md` | Active plan / operational runbook | Current pilot boundary and acceptance scope. | Useful pilot authority, but its “after PR-PILOT-RELIABILITY-1” label is behind the current branch's later reliability batch. |
| `docs/PILOT_RELIABILITY_BATCH_RUNBOOK.md` | Operational runbook | Current five-commit pilot reliability batch procedure. | Strong candidate for the current pilot batch authority; runtime deployment still requires verification. |

## Architecture, API, Database, and Historical Design

| File | Classification | Apparent role / currentness | Governance note |
| --- | --- | --- | --- |
| `doc/API.md` | API contract | REST, auth, printing, order, and menu API reference. | Useful contract candidate, but auth text is stale and it omits current `start-print` and acknowledgement endpoints. It also contains a `store_id=1` example. |
| `doc/CODEX_SKILL_RESTAURANT_POS_GUARDRAILS.md` | Development instruction / skill | Guardrails for cloud, auth, printing, migration, and scope. | Current policy candidate; should be separated from implementation facts and historical plan text. |
| `doc/CODEX_WORKFLOW.md` | Development instruction / skill | Canonical agent workflow. | Current workflow candidate. |
| `doc/DatabaseDesign.md` | Historical record / architecture reference | Original MVP database design. | Does not describe the complete V1-V7 migration history and current fields; migrations/entities are stronger executable authorities. |
| `doc/Flow.md` | Historical record / architecture reference | Small flow diagram. | Useful historical overview; not a complete current runtime model. |
| `doc/MVP_Scope.md` | Historical record / active-plan precursor | MVP scope and constraints. | Earlier scope; conflicts with current auth, multi-store, printing, and admin implementation. |
| `doc/PAD_APP_ARCHITECTURE.md` | Historical record / active plan | Android Pad architecture through early PRs. | Explicitly describes a PR2-PR4/POC stage and is stale for the current foreground worker. |
| `doc/PAD_APP_PR_PROMPTS.md` | Historical record | PR2-PR8 implementation prompts. | Explicitly planning-only; not a current code contract. |
| `doc/RESTAURANT_POS_CLOUD_READY_MASTER_PLAN_AND_CODEX_SKILL.md` | Active plan / development instruction / architecture reference | Master cloud-readiness plan and guardrails. | Valuable policy history, but its “Android is post-cloud” boundary is behind the implemented PAD_DIRECT work. It combines plan, architecture, and skill content. |
| `doc/ROADMAP.md` | Active plan | Detailed product/PR roadmap. | Canonical roadmap pointer target, but its current-stage claims predate current Android/PAD_DIRECT implementation. |
| `doc/Restaurant_System_Development_Guide.md` | Historical record / development instruction | Early development guide. | Historical MVP guidance; not sufficient for current cloud/pilot behavior. |
| `doc/Restaurant_System_PRD_v1.md` | Historical record | Original product requirements. | Historical product baseline; does not define current reliability or PAD_DIRECT behavior. |
| `doc/SequenceD.md` | Historical record / architecture reference | Earlier sequence diagrams. | Historical design artifact. |
| `doc/SequenceD2.md` | Historical record / architecture reference | Earlier sequence diagrams. | Historical design artifact. |
| `doc/SystemDesign_Bilingual.md` | Architecture source of truth claim / historical record | Bilingual system design; explicitly claims “Source of truth: Current codebase and runtime structure”. | Incomplete relative to current PAD_DIRECT, offline outbox, and reliability features; should not be sole current authority. |
| `doc/uml.md` | Historical record / architecture reference | Earlier UML notes. | Historical design artifact. |

## Frontend and Android Documentation

| File | Classification | Apparent role / currentness | Governance note |
| --- | --- | --- | --- |
| `frontend/README.md` | Generated reference / unclear | Default Vite/React scaffold README. | Not a POS architecture or operational authority. |
| `restaurant-pad-app/README_PAD_APP.md` | Historical record / unclear | Early Pad app architecture and PR status. | Says the app does not run a production worker or live print_jobs flow, which is stale against current `MainActivity.java`. |
| `restaurant-pad-app/docs/API_BASE_CONFIG.md` | Operational runbook / API contract | Pad URL, API base, pairing, and local configuration. | Current setup reference; must be reconciled with the exact Android implementation and release/debug policy. |
| `restaurant-pad-app/docs/ARCHITECTURE.md` | Historical record / architecture reference | PR2-PR4 Pad skeleton/WebView/TCP POC architecture. | Explicitly says the production polling worker is future work; stale for current worker code. |
| `restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md` | Operational runbook / architecture reference | Bundled frontend assets, IndexedDB, and offline boundary. | Current-looking and useful; verify asset build/copy steps against Gradle and deployment scripts. |
| `restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md` | Operational runbook | Local preview, WebView, printer, pairing, and worker development. | Mixed POC and current sections; needs lifecycle metadata before serving as one authority. |
| `restaurant-pad-app/docs/PAD_DIRECT_MANUAL_PRINT.md` | Historical record / operational runbook | PR11D-4 one-job manual flow. | Correct as a manual-flow record, but its “no worker” statements conflict with current semi-auto operation if read as global current state. |
| `restaurant-pad-app/docs/PAD_DIRECT_MULTI_PRINTER_ROUTING.md` | Operational runbook / architecture reference | Payload-selected multi-printer routing. | Current pilot behavior candidate. |
| `restaurant-pad-app/docs/PAD_DIRECT_PENDING_JOBS.md` | Historical record / operational runbook | Read-only pending viewer and manual-only phase. | Phase-specific manual scope; not the complete current worker contract. |
| `restaurant-pad-app/docs/PAD_DIRECT_PRINTER_FAILURE_DIAGNOSTICS.md` | Operational runbook | Assigned-printer failure diagnostics. | Current pilot troubleshooting candidate. |
| `restaurant-pad-app/docs/PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md` | Operational runbook | Restaurant field checklist. | Current field validation candidate. |
| `restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md` | Operational runbook | Foreground semi-auto worker, recovery, and operator controls. | Strongest current PAD_DIRECT operator runbook found; still requires runtime/device verification. |
| `restaurant-pad-app/docs/PRINTER_PLUGIN_POC.md` | Historical record | Native TCP printer plugin POC. | Earlier POC; do not use as the complete production worker contract. |
| `restaurant-pad-app/docs/PRINTER_TESTING.md` | Operational runbook | Local native TCP connection/test-print instructions. | Current local hardware test reference, not automatic print behavior. |
| `restaurant-pad-app/docs/WEBVIEW_ROUTING.md` | Operational runbook / architecture reference | WebView route and URL behavior. | Current setup reference candidate. |

## Generated and Packaged Reference Artifacts

The Markdown inventory above is supplemented by tracked non-Markdown references that can look authoritative but are generated or packaged:

| Artifact family | Classification | Governance note |
| --- | --- | --- |
| `doc/diagram/*.png` | Generated reference | Diagram exports; useful visual references, not executable truth. |
| `restaurant-pad-app/android/app/src/main/assets/web/index.html`, `asset-manifest.json`, `build-info.json`, and hashed assets | Generated reference | Bundled frontend output. It can lag source `frontend/`; source/build configuration is authoritative. |
| `deployment/windows-pilot/package/frontend/dist/**` | Generated reference | Packaged frontend distribution; not the source frontend contract. |
| Android Gradle/build outputs if present locally | Generated reference | Must not be treated as source documentation or authority. |

## Scope and Authority Observations

1. The repository has many useful documents, but no single maintained authority matrix existed before this audit.
2. `SYSTEM_DOCUMENTATION.md` is too large for reliable daily working context and mixes generated snapshots, current facts, historical PR records, and plans.
3. The current executable authority for the database is the Flyway migration chain under `backend/src/main/resources/db/migration/` plus the entities/mappers; `doc/DatabaseDesign.md` is not complete enough to replace it.
4. The current executable authority for API behavior is the controllers, DTOs, security/configuration, and tests; `doc/API.md` is incomplete and partly stale.
5. The strongest current Pad operational document found is `restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md`, but the manual/Pending docs need explicit phase labels in a future documentation change.
6. No server, database, Android device, or deployed container was inspected in this read-only repository audit. Runtime claims remain to be verified separately.


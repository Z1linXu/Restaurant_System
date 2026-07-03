# ROADMAP.md

# Restaurant System Roadmap

Version: 1.0  
Project Stage: Cloud Readiness Before Android Pad Shell  
Primary Goal: Stabilize the existing restaurant POS system for cloud deployment, then build the Android Pad shell and expand toward multi-store usage.

---

# 1. Product Direction

This project is evolving from a local single-restaurant POS into a cloud-ready, multi-store restaurant operating platform.

The long-term direction is:

```text
Local Restaurant POS
        ↓
Cloud Ready Restaurant System
        ↓
First Restaurant Cloud Pilot
        ↓
Android Pad Shell
        ↓
Pad Direct Local Printing
        ↓
Multi-Store Owner Dashboard
        ↓
Multi-Restaurant Platform
```

The current priority is NOT adding more features quickly.

The current priority is:

1. Stability
2. Data safety
3. Production security
4. Cloud compatibility
5. Printing reliability
6. Store scope correctness
7. Operational visibility
8. Then Android App shell

---

# 2. Current Stage

The system already includes major business modules:

- Core POS ordering
- Frontdesk table board
- Takeout / pickup flow
- Kitchen Display System
- Pickup / serving shelf
- Print Center
- Durable print jobs
- Mock / Real / Disabled / Pad Direct print modes
- Owner Admin Console
- Menu management
- Staff management
- Audit logs
- Analytics summaries and reports
- Store workspace routing
- Owner multi-store dashboard foundation
- Authentication with JWT and refresh tokens
- Legacy `X-User-Id` fallback for local/dev compatibility

However, before moving to cloud deployment and Android Pad, the architecture must be hardened.

---

# 3. Explicit Non-Goals For The Current Stage

The following are intentionally NOT part of the current cloud-readiness stage:

## 3.1 Do Not Change Order Completion Semantics Yet

Do not change the current `completeOrder` behavior in this stage.

The current restaurant workflow still uses Finish / Complete as the operational close action.

This can be redesigned later when payment, settlement, pickup, refund, and accounting flows become more mature.

## 3.2 Do Not Implement Payment Yet

Do not implement:

- online payment provider integration
- card terminal integration
- payment settlement
- payment attempts
- payment reconciliation
- refund flow
- refund audit workflow

Current analytics are for owner visibility only: sales, cost, profit, margin, item performance, and store overview.

Formal accounting-grade payment/refund architecture can come later.

## 3.3 Do Not Start Android App Before Cloud Baseline

Do not build the Android Pad shell before the cloud API base, HTTPS, authentication, WebSocket, CORS, and printing-mode boundaries are stable.

Android should point to a stable cloud endpoint, not a moving local development target.

## 3.4 Do Not Expand SaaS Platform Features Yet

Do not implement:

- subscription billing
- public tenant self-onboarding
- platform marketplace
- multi-tenant billing plans
- advanced platform admin UI

The first goal is a stable deployable cloud system for real restaurant usage.

---

# 4. Execution Rules

Every development task must follow:

- `AGENTS.md`
- `CODEX_WORKFLOW.md`
- `SYSTEM_DOCUMENTATION.md`
- `doc/RESTAURANT_POS_CLOUD_READY_MASTER_PLAN_AND_CODEX_SKILL.md`
- `doc/CODEX_SKILL_RESTAURANT_POS_GUARDRAILS.md`

If these documents conflict, stop and ask before modifying code.

Each PR must be small, focused, and reviewable.

Never combine database migration, auth hardening, printing behavior, frontend refactor, and deployment work in one PR.

---

# 5. Milestone 1: Project Governance And Documentation Baseline

## Goal

Make sure Codex, future developers, and the project owner all follow the same direction.

## PR1: Documentation And Configuration Baseline

### Scope

- Add or update `CODEX_WORKFLOW.md`
- Add this `ROADMAP.md`
- Ensure cloud-readiness planning documents are committed under `doc/`
- Clarify current production direction in `SYSTEM_DOCUMENTATION.md`
- Recover or recreate missing `FINAL_PRE_CLOUD_SERVER_HARDENING_PLAN.md` if still needed

### Must Include

- Current stage: cloud readiness before Android
- Explicit non-goals:
  - no payment/refund
  - no Android shell yet
  - no completeOrder redesign yet
- PR order
- Cloud-readiness definition
- Deployment preparation scope

### Done When

- A new Codex session can understand the project direction by reading root documentation
- The roadmap is clear enough to prevent unrelated changes
- The guardrails are committed and not only local/untracked files

---

# 6. Milestone 2: Database Migration Foundation

## Goal

Stop relying on Hibernate `ddl-auto=update` for cloud/production schema evolution.

## PR2: Flyway Migration Baseline

### Scope

- Add Flyway dependency
- Add `backend/src/main/resources/db/migration/`
- Add `application-cloud.yml`
- Configure cloud profile with `ddl-auto=validate`
- Keep local development safe and convenient
- Decide carefully whether Windows pilot remains `update` temporarily or moves to `validate`
- Add migration documentation

### Important Rules

Do not ask Codex to invent the full schema from entities.

Preferred source of baseline:

```bash
pg_dump --schema-only
```

The first baseline should reflect the current real PostgreSQL schema.

### Do Not Do In PR2

- Do not add aggressive foreign keys
- Do not add large unique constraints
- Do not add many `NOT NULL` constraints
- Do not rename columns
- Do not drop tables
- Do not rewrite historical data
- Do not change order, printing, or auth behavior

### Done When

- Empty cloud database can be initialized through Flyway
- Existing database can be baselined safely
- Cloud profile validates schema instead of mutating it
- Local dev is not broken
- Documentation explains how to baseline old databases

---

# 7. Milestone 3: Production Safety Baseline

## Goal

Prevent development-only behavior from leaking into cloud deployment.

## PR3: Production Safety Guard

### Scope

- Add production/cloud startup safety checks
- Reject default JWT secret in cloud/prod
- Reject `X-User-Id` fallback in cloud/prod
- Reject dev role switcher in cloud/prod
- Reject unsafe seed overwrite in cloud/prod
- Make failure messages clear

### Required Behavior

Cloud/prod must fail fast if configured unsafely.

Examples:

```text
app.auth.x-user-id-fallback-enabled=true
app.dev-tools.role-switcher-enabled=true
app.auth.jwt-secret=dev-local-restaurant-pos-change-this-secret-please-2026
```

These should not be allowed in cloud/prod.

### Done When

- Local/dev still works
- Cloud/prod unsafe config refuses to start
- Safe cloud config starts
- Documentation lists required environment variables

---

# 8. Milestone 4: Runtime Seeder Production Safety

## Goal

Prevent startup seed logic from overwriting real restaurant data.

## PR4: RuntimeDataSeeder Safety

### Scope

- Review `RuntimeDataSeeder`
- Make local/dev seed behavior explicit
- Make cloud/prod seed behavior safe
- Prevent default password resets in cloud/prod
- Prevent menu/price/table/printer overwrite in cloud/prod
- Document first-owner initialization process

### Important Rule

Seeder may supplement safe missing metadata only when explicitly allowed.

Seeder must not silently reset owner-managed data.

### Done When

- Local/dev recovery accounts still work
- Cloud/prod does not reset passwords or overwrite restaurant data
- Fresh cloud initialization has a documented owner/admin creation path

---

# 9. Milestone 5: Cloud Printing Boundary

## Goal

Make printing safe for cloud deployment.

The cloud server must never directly connect to private LAN printers such as:

```text
192.168.x.x
10.x.x.x
172.16.x.x - 172.31.x.x
```

## PR5: Cloud Printing Guard

### Scope

- Harden `REAL` printing behavior under cloud/prod
- Detect private LAN printer IPs from cloud/prod
- Fail fast instead of socket timeout loops
- Mark print jobs as `FAILED` with clear error code
- Preserve local Windows/Mac pilot `REAL` LAN printing where appropriate
- Preserve `MOCK`, `PAD_DIRECT`, and `DISABLED`

### Required Behavior

Cloud/prod:

```text
REAL + private LAN printer IP
        ↓
print job FAILED
        ↓
clear error message
        ↓
order submission still succeeds
```

### Do Not Do

- Do not remove REAL mode
- Do not remove MOCK mode
- Do not remove PAD_DIRECT mode
- Do not make printing failure roll back orders
- Do not start Android implementation in this PR

### Done When

- Cloud cannot hang while trying to connect to store LAN printers
- Print job error is visible and actionable
- Local pilot remains usable

---

# 10. Milestone 6: Print Failure Visibility

## Goal

Make failed kitchen and receipt printing visible to operators.

## PR6: Print Failure Visibility Hardening

### Scope

- Improve Print Center failed job visibility
- Ensure failed/cancelled print jobs are easy to find
- Preserve rendered preview where available
- Ensure manual reprint still works
- Ensure frontdesk submit warning is clear when GRAB fails

### Required Behavior

If GRAB ticket fails:

```text
Order is still saved
Print job shows FAILED
Frontdesk sees warning
Owner/Admin can preview/reprint
```

### Do Not Do

- Do not implement a large automatic retry scheduler yet
- Do not change order lifecycle
- Do not implement payment/refund
- Do not make KDS required for printing

### Done When

- Failed print jobs are not silent
- Manual reprint is easy
- Operator can tell what failed and why

---

# 11. Milestone 7: Frontend API And Polling Cleanup

## Goal

Make the frontend cloud-safe and token-safe.

## PR7: Frontend API Client Consolidation

### Scope

- Audit frontend services
- Ensure production API calls go through shared API client
- Remove production use of `X-User-Id`
- Ensure Bearer token is attached consistently
- Ensure refresh-token flow is centralized
- Ensure request timeout handling is consistent
- Audit unnecessary polling
- Make sure KDS disabled means no KDS polling/subscriptions

### Search Audit

Run audits similar to:

```bash
rg "X-User-Id" frontend/src
rg "store_id=1" frontend/src
rg "fetch\\(" frontend/src
```

Not every occurrence is automatically wrong, but every production path must be reviewed.

### Done When

- Cloud frontend uses token-based auth consistently
- No production-critical screen relies on dev `X-User-Id`
- Long-running iPad/frontdesk pages handle expired token safely
- Background polling is controlled

---

# 12. Milestone 8: Store Scope Hardening

## Goal

Make sure multi-store access is safe before real multi-store usage.

## PR8: Store Scope Audit And Guard Completion

### Scope

- Audit all store-scoped backend APIs
- Ensure backend uses `StoreAccessService` or equivalent authority
- Do not trust URL store id alone
- Verify owner/admin/manager/frontdesk scope rules
- Verify printing, orders, menu, staff, audit, reports, table APIs

### Important Rule

Frontend route hiding is convenience only.

Backend authorization is the security boundary.

### Do Not Do

- Do not perform a large route rewrite
- Do not change order lifecycle
- Do not rewrite the whole permission model

### Done When

- Cross-store access attempts return 403
- Own-store access works
- Owner/admin multi-store access works according to membership rules
- Legacy local fallback remains controlled

---

# 13. Milestone 9: Deployment Architecture Preparation

## Goal

Prepare a repeatable cloud deployment package before buying or configuring the final server.

## PR9: Deployment Architecture Package

### Scope

Add deployment files such as:

```text
deployment/cloud/docker-compose.yml
deployment/cloud/nginx.conf
deployment/cloud/application-cloud.yml.example
deployment/cloud/.env.example
deployment/cloud/deploy.sh
deployment/cloud/backup.sh
deployment/cloud/restore.sh
deployment/cloud/README_CLOUD_DEPLOYMENT.md
```

### Should Cover

- Backend Spring Boot service
- Frontend static hosting
- PostgreSQL
- Nginx reverse proxy
- HTTPS readiness
- WebSocket proxying
- Environment variables
- Logs
- Backup
- Restore
- Health check
- Rollback

### Domain Plan

Recommended production domain layout:

```text
pos.yourdomain.com
```

or:

```text
app.yourdomain.com
api.yourdomain.com
```

Start simple unless there is a clear reason to split frontend and API domains.

### Done When

- A future server can be configured by following docs
- No real server credentials are committed
- `.env.example` documents required variables
- Backup and restore are documented before production data exists

---

# 14. Milestone 10: Observability And Operational Debuggability

## Goal

Make production problems diagnosable.

## PR10: Observability Baseline

### Scope

- Add consistent structured log context where practical
- Include identifiers in important logs:
  - store id
  - user id
  - order id
  - print job id
  - module code
  - request id if available
- Ensure print failures are logged clearly
- Ensure auth failures do not leak secrets
- Ensure audit writes remain best-effort

### Recommended Log Fields

```text
request_id
store_id
user_id
order_id
order_no
print_job_id
printer_id
module_code
error_code
```

### Do Not Do

- Do not add a large monitoring stack yet unless explicitly requested
- Do not send logs to third-party services yet
- Do not log passwords, tokens, raw refresh tokens, or card/payment data

### Done When

- Common production incidents can be traced from logs
- Print failures can be connected to order and printer
- Auth/session issues can be diagnosed safely

---

# 15. Milestone 11: Pre-Cloud Smoke Tests And Runbook

## Goal

Prove the system is ready to deploy before buying the server or moving real data.

## PR11: Integration Smoke Tests And Runbook

### Scope

- Add backend integration/smoke tests for critical flows
- Add manual QA checklist
- Add cloud dry-run checklist
- Add rollback guide
- Add first restaurant pilot checklist

### Critical Smoke Flows

1. Login owner/frontdesk
2. Load workspaces
3. Open store frontdesk
4. Create draft order
5. Add items/options
6. Submit order
7. Create print jobs
8. Handle failed/mock print status
9. Reprint GRAB/receipt
10. Finish order
11. Confirm order appears in history
12. Confirm analytics summary/rebuild does not break POS
13. Confirm cross-store access is blocked
14. Confirm KDS disabled does not create KDS polling

### Required Build Checks

Backend:

```bash
mvn clean compile
mvn test
```

Frontend:

```bash
npm run build
```

### Done When

- The system has a repeatable smoke test checklist
- Cloud profile dry-run is documented
- Rollback procedure is documented
- The first cloud deployment has a clear go/no-go checklist

---

# 16. Cloud Ready Definition

The project is considered Cloud Ready only when all of the following are true:

## Database

- Flyway is present
- Cloud profile uses `ddl-auto=validate`
- Existing DB baseline process is documented
- Backup and restore process is documented

## Security

- Cloud/prod rejects default JWT secret
- Cloud/prod rejects `X-User-Id` fallback
- Cloud/prod rejects dev role switcher
- Default development passwords are not production credentials

## Printing

- Cloud/prod does not directly connect to private LAN printers
- MOCK works for cloud dry-run
- PAD_DIRECT remains available for Android/local printing future
- Failed print jobs are visible
- Manual reprint works

## Frontend

- API calls use shared token-aware client
- Long-running pages handle token refresh
- Background polling is controlled
- Store workspace routes work

## Multi-Store

- Store scope is enforced by backend
- URL store id is not trusted
- Owner/manager/frontdesk access rules are tested

## Deployment

- Cloud deployment docs exist
- Environment variables are documented
- HTTPS/WebSocket proxy plan exists
- Backup/restore scripts exist or are documented
- Logs are usable for debugging

---

# 17. Milestone 12: Buy Server And Deploy Cloud Pilot

## Goal

Deploy the first cloud pilot after the cloud-ready checklist passes locally.

## Recommended Order

1. Buy cloud server
2. Buy domain
3. Point DNS to server
4. Install Docker / runtime dependencies
5. Configure PostgreSQL
6. Configure backend environment variables
7. Configure frontend hosting
8. Configure Nginx reverse proxy
9. Configure HTTPS
10. Run Flyway migrations
11. Start backend and frontend
12. Run smoke checklist
13. Test login
14. Test order flow in MOCK printing mode
15. Test Print Center visibility
16. Switch printing strategy only after cloud boundary is confirmed

## Domain Recommendation

A domain is strongly recommended.

For the first version, one subdomain is enough:

```text
pos.example.com
```

This keeps Android WebView, HTTPS, CORS, WebSocket, and auth simpler.

## Do Not Do During First Deploy

- Do not start Android App implementation during deployment
- Do not import multiple restaurants yet
- Do not enable REAL cloud-to-LAN printing
- Do not use reports for accounting validation yet
- Do not run with development JWT secret

---

# 18. Milestone 13: First Restaurant Cloud Pilot

## Goal

Run the existing web POS from the cloud for one real restaurant.

## Operating Mode

Recommended initial mode:

```text
Cloud backend + cloud frontend
Printing mode: MOCK or PAD_DIRECT preparation
Restaurant devices: browser/iPad access through HTTPS domain
```

If local printer is still required before Android Pad Direct is ready, use a controlled local bridge strategy rather than direct cloud-to-LAN socket printing.

## Pilot Success Criteria

- Owner can log in
- Frontdesk can place orders
- Table occupancy works
- Receipts/tickets produce visible print jobs
- Failed print jobs are obvious
- Manual reprint works
- Owner dashboard loads
- Menu management works
- Staff access works
- Audit logs work
- System can be backed up and restored
- Logs can explain production incidents

---

# 19. Milestone 14: Android Pad Shell

## Goal

Build the Android Pad shell only after cloud endpoint is stable.

## PR Scope

Create independent Android shell under `restaurant-pad-app/` or equivalent.

The Android app should:

- Use a stable HTTPS cloud URL
- Load the existing React frontend
- Support login/session persistence safely
- Support WebView route navigation
- Support WebSocket connection
- Support configurable API base URL only where appropriate
- Preserve existing web POS behavior

## Do Not Do First

- Do not rewrite POS UI in native Android
- Do not fork business logic into Android
- Do not bypass backend auth
- Do not hardcode local IPs
- Do not implement printer bridge before shell stability

## Done When

- Android Pad can log in
- Android Pad can open frontdesk/store routes
- Ordering flow works
- Token refresh works
- App can recover from network suspension
- WebView behavior is stable on real device

---

# 20. Milestone 15: Pad Direct Local Printing

## Goal

Allow Android Pad to print locally to store LAN printers while cloud backend remains the source of truth.

## Architecture

Cloud backend:

- Creates print jobs
- Renders receipt snapshot
- Produces ESC/POS payload when appropriate
- Leaves `PAD_DIRECT` jobs pending

Android Pad:

- Registers as store device
- Polls or receives pending jobs
- Claims job with lease
- Fetches payload
- Prints to local LAN printer
- Reports complete/fail/release

## Required Safety

- Backend stores only device token hash
- Raw device token is shown only once
- Claim must be atomic
- Expired claims can be reclaimed
- Failed jobs must be visible
- Manual reprint must still work

## Done When

- Pad can claim and print one job
- Duplicate printing is prevented by claim/lease
- Failed Pad print can be retried/released
- Print Center shows device/job status clearly

---

# 21. Milestone 16: Same-Table Concurrent Edit Guard

## Goal

Prevent two Pads/frontdesk devices from overwriting the same table order silently.

## Scope

- Add expected revision checks to order mutations
- Return `409 Conflict` for stale edits
- Frontend shows refresh-required message
- Preserve update batch idempotency

## Recommended Rule

```text
client sends expected_revision
backend compares with orders.current_revision
if mismatch → 409 Conflict
```

## Do Not Do

- Do not build complex CRDT collaboration
- Do not implement live editor cursors yet
- Do not change completeOrder lifecycle

## Done When

- Same-table stale edits no longer silently overwrite newer edits
- Staff can refresh and continue safely

---

# 22. Milestone 17: Owner / Platform / Developer Boundary Cleanup

## Goal

Separate restaurant-owner tools from developer/platform tools.

## Scope

- Owner Admin APIs stay owner-safe
- Platform APIs stay platform-only
- Developer tools stay local/dev-only
- Frontend navigation respects feature + role
- Backend remains source of truth

## Done When

- Owner cannot access developer diagnostics in cloud
- Manager cannot access platform-only tools
- Frontdesk cannot access owner admin tools
- Local developer can still use dev tools when explicitly enabled

---

# 23. Milestone 18: Multi-Store Expansion

## Goal

Support real multi-store usage after one-store cloud pilot is stable.

## Scope

- Owner Home becomes primary multi-store landing page
- Store switcher works across modules
- Store-scoped APIs are audited
- Analytics per store and organization are reliable
- Print settings are store-specific
- Staff roles are store-scoped

## Do Not Do Too Early

- Do not implement SaaS billing before real multi-store operations are stable
- Do not build advanced tenant onboarding before internal owner flows are stable

## Done When

- One owner can operate multiple stores safely
- Store data cannot leak across store boundaries
- Owner can compare stores
- Each store can have its own printer/menu/table/staff settings

---

# 24. Milestone 19: Future Financial Model

## Goal

Eventually move from owner visibility reporting to formal POS financial records.

This is not part of the current cloud-readiness stage.

## Future Scope

- Payments
- Payment attempts
- Cash/card/external terminal records
- Refunds
- Tax lines
- Discounts
- Tips/service charges
- Split bills persisted in backend
- Accounting-grade reports

## Trigger To Start

Start this only after:

- cloud pilot is stable
- Android Pad is stable
- printing is reliable
- owner actually needs formal settlement/payment tracking

---

# 25. Recommended PR Order Summary

Recommended execution order:

```text
PR1  Documentation baseline
PR2  Flyway migration baseline
PR3  Production safety guard
PR4  RuntimeDataSeeder production safety
PR5  Cloud printing guard
PR6  Print failure visibility
PR7  Frontend API client / polling cleanup
PR8  Store scope audit
PR9  Deployment architecture package
PR10 Observability baseline
PR11 Smoke tests and runbook

====== Cloud Ready ======

PR12 Buy server + deploy cloud pilot
PR13 First restaurant cloud pilot stabilization
PR14 Android Pad shell
PR15 Pad Direct local printing
PR16 Same-table concurrent edit guard
PR17 Owner / Platform / Developer boundary cleanup
PR18 Multi-store expansion
PR19 Future financial model
```

PR numbers may be renumbered by actual GitHub workflow, but the order should remain similar.

---

# 26. Decision Rules

When uncertain, choose the option that improves:

1. Data safety
2. Operational reliability
3. Debuggability
4. Backward compatibility
5. Cloud readiness

Do not choose the option that merely makes the current PR easier if it creates long-term architecture debt.

---

# 27. Final Reminder

The goal is not to build every feature immediately.

The goal is to make the system trustworthy enough that a real restaurant can depend on it during service.

A restaurant POS must prioritize:

- orders not disappearing
- tickets not silently failing
- staff not losing access
- store data not leaking
- backups being restorable
- bugs being diagnosable

Only after that should the project move aggressively into Android Pad, Pad Direct printing, and multi-store expansion.

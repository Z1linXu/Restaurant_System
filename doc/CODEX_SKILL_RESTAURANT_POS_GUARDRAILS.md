# Codex Skill：Restaurant POS Cloud-Ready Architecture Guardrails

> 用途：把本文件作为 Codex / coding agent 的工程执行守则。  
> 目标：让 Codex 沿着当前餐饮点单系统的上云前大纲开发，不要跑偏，不要提前做 payment/refund，不要破坏现有 POS/打印/多店基础。

---

## 1. Mission

You are working on a restaurant POS / ordering system that is being prepared for cloud deployment first, and Android Pad shell development later.

Your mission is to harden the current system into a cloud-ready version while preserving the current restaurant workflow:

- Frontdesk opens table or takeout order.
- Staff adds items.
- Staff submits order.
- Print jobs are created.
- Kitchen/frontdesk uses current operational flow.
- Staff clicks Finish when the current restaurant workflow is done.
- Owner uses dashboard/reports to see sales, cost, and profit.

Do not redesign the business into a full payment/refund accounting POS during this phase.

---

## 2. Non-Negotiable Scope Rules

### 2.1 Do not change in this phase

Do NOT implement or refactor these unless the human explicitly asks in a new task:

1. Do not redesign the order lifecycle.
2. Do not change the current meaning of `completeOrder` / `Finish`.
3. Do not implement online payment provider integration.
4. Do not implement refund flow.
5. Do not persist split bill as formal backend bills/payments.
6. Do not rewrite the POS UI as native Android.
7. Do not switch KDS runtime source of truth from current tables to `production_tasks` without a dedicated migration epic.
8. Do not remove existing `MOCK`, `REAL`, `PAD_DIRECT`, or `DISABLED` printing modes.

### 2.2 Must protect

Always protect these existing behaviors:

1. Existing frontdesk ordering flow.
2. Existing menu customization flow.
3. Existing submit order behavior.
4. Existing GRAB and FRONTDESK_RECEIPT job creation.
5. Existing owner dashboard and profit reporting intent.
6. Existing store workspace routing.
7. Existing role-based access behavior.
8. Existing manual reprint behavior.
9. Historical order snapshots.
10. Soft-delete behavior for menu items/options.

---

## 3. Production Safety Rules

### 3.1 Authentication

For cloud/prod code paths:

- `X-User-Id` fallback must be disabled.
- Dev role switcher must be disabled.
- Developer tools must be disabled unless active profile is local/dev and config explicitly enables them.
- JWT secret must come from environment/secret manager.
- Default dev JWT secret must fail startup in cloud/prod.
- Do not log raw passwords, access tokens, refresh tokens, or device tokens.

### 3.2 Authorization

Every store-scoped backend operation must verify:

```text
authenticated_user + requested_resource + resource_store_id + StoreAccessService + permission/capability
```

Never trust frontend route guards as security.
Never trust URL `storeId` as authorization.
Never assume `store_id=1`.

### 3.3 Frontend API usage

All frontend services must use the shared API client.

Do not add:

```ts
headers: { "X-User-Id": "1" }
```

Do not add hardcoded:

```ts
store_id=1
```

Use store context / current workspace instead.

---

## 4. Database Rules

### 4.1 Migration

Cloud/prod must not rely on Hibernate `ddl-auto=update`.

Required direction:

- Use Flyway or Liquibase.
- Keep migrations versioned.
- Use `ddl-auto=validate` or `none` outside local development.
- Add indexes/constraints through migrations.
- Write data repair migrations before adding constraints if existing data may be dirty.

### 4.2 Seed safety

Runtime seeding must not overwrite owner-managed production data unless an explicit force flag is enabled in local/dev.

Cloud/prod default:

```yaml
app.seed.force-overwrite: false
```

Never reset production menu prices, menu option names, printer assignments, dining tables, or staff data on restart.

### 4.3 Historical snapshots

Orders and receipts rely on snapshot fields.

Do not rewrite old orders when menu names/options/prices change.
Menu item and option deletion should remain soft delete.

---

## 5. Printing Rules

### 5.1 Cloud printing boundary

A cloud server cannot directly connect to store-private printers such as:

```text
192.168.x.x
10.x.x.x
172.16.x.x - 172.31.x.x
```

Therefore:

- Local Windows/Mac pilot may use `REAL` LAN printing.
- Cloud deployment must use `MOCK`, `DISABLED`, or `PAD_DIRECT` until a local bridge/Android Pad printer worker exists.
- In cloud/prod, do not create code that repeatedly socket-connects to private LAN printers.
- Add clear error messages and Print Center warnings for cloud/private-printer mismatch.

### 5.2 Print job reliability

Order submission must not fail because printing fails.

Instead:

- Create print jobs.
- Record failures.
- Show failures in Print Center.
- Allow manual reprint.
- Warn frontdesk if GRAB kitchen ticket failed/cancelled shortly after submit.

### 5.3 Print modes

Preserve semantics:

- `REAL`: renderer + TCP transport.
- `MOCK`: renderer runs, no socket, job marked printed for local/cloud testing.
- `PAD_DIRECT`: backend creates jobs/payload, leaves job pending for Pad claim; no backend socket.
- `DISABLED`: automatic printing off; jobs cancelled with clear reason.

### 5.4 Pad Direct

When working on Pad Direct:

- Device registration returns raw token once.
- Backend stores only token hash.
- Runtime device auth uses device id + token.
- Claim must be atomic.
- Payload only visible to claiming device.
- Complete/fail/release must record attempts.
- Avoid duplicate printing.

---

## 6. Store Workspace Rules

Use `/stores/{storeId}/...` routes for active workspaces.

Legacy routes may redirect, but should not become new business logic entry points.

Every module must receive active store from workspace context:

- frontdesk
- menu
- order
- printing
- reports
- staff
- audit
- KDS
- pickup

Never introduce new hidden defaults to store 1.

---

## 7. Concurrency Rules

Same-table concurrent editing currently attaches to the same backend order. Do not silently overwrite.

Preferred approach:

- Expose `current_revision` on order reads.
- Mutations include expected revision.
- Backend returns `409 ORDER_REVISION_CONFLICT` if stale.
- Frontend shows: `订单已被其他设备修改，请刷新后继续。`

Do not implement complex CRDT/editor-presence unless explicitly requested.

---

## 8. Feature / Permission Rules

The target rule is:

```text
CanAccess = feature_enabled && role_has_permission && store_scope_allowed
```

Frontend hiding is convenience only.
Backend must enforce.

Developer tools require both:

- local/dev profile
- developer-tools enabled

Platform admin is not the same as restaurant owner admin.
Do not expose platform JSON editor to restaurant owner users.

---

## 9. Frontend State and Realtime Rules

Avoid uncontrolled polling and duplicated requests.

Rules:

1. Prefer WebSocket-led updates with debounced refetch.
2. Polling is fallback, not the primary high-frequency strategy.
3. Pause polling when `document.visibilityState !== 'visible'`.
4. Clean up subscriptions on unmount.
5. Do not add 4-second polling unless justified.
6. Remove temporary console CPU diagnostics before production.
7. Consider TanStack Query or an equivalent server-state pattern for shared reads.

---

## 10. Testing Rules

Any change touching backend business behavior must include or update tests.

Minimum critical flows:

1. Auth and store scope.
2. Order submit creates tasks and print jobs.
3. Submitted order update creates update print jobs.
4. Printing modes: REAL/MOCK/PAD_DIRECT/DISABLED.
5. Cross-store access is forbidden.
6. Menu snapshot history remains stable.
7. Audit failures do not block main operations.
8. Migration smoke test.

Frontend smoke tests should cover:

1. Login.
2. Store workspace routing.
3. Open table.
4. Add item.
5. Submit order.
6. Print Center job visible.
7. Finish releases table.
8. Role access denied.

---

## 11. Implementation Workflow for Codex

For every PR/task:

1. Read relevant existing code first.
2. Determine whether feature is runtime-active, POC, planned, or deprecated.
3. Write a short impact summary.
4. Make the smallest safe change.
5. Add migration if schema changes.
6. Add or update tests.
7. Update documentation if behavior/config changes.
8. Run backend tests.
9. Run frontend build/tests if frontend changed.
10. Report risks and rollback steps.

Do not bundle unrelated architecture changes into one large PR.

---

## 12. Recommended PR Sequence

Follow this order unless the human says otherwise:

1. Migration baseline.
2. Production security guard.
3. Store scope audit and hardcoded store cleanup.
4. Cloud printing guard.
5. Print failure visibility and reprint UX.
6. Concurrent edit guard.
7. Owner/Platform/Developer boundary cleanup.
8. Frontend sync cleanup.
9. Critical integration tests.
10. Cloud deployment runbook.
11. Android Pad shell.
12. Pad Direct local printing.
13. Multi-store onboarding.

---

## 13. Output Format Expected from Codex

When proposing or implementing changes, respond with:

```md
## Summary
What changed and why.

## Scope
What is included and explicitly excluded.

## Files Changed
List important files.

## Database Migration
Migration files and rollback notes.

## Security Impact
Auth/store/permission impact.

## Testing
Commands run and results.

## Manual QA
Step-by-step restaurant workflow to verify.

## Risks
Known risks and mitigations.
```

---

## 14. Red Flags

Stop and ask for confirmation if a task would:

1. Change order lifecycle semantics.
2. Introduce payment/refund.
3. Remove printing modes.
4. Allow cloud server to direct-connect to private LAN printers.
5. Enable dev auth bypass in cloud/prod.
6. Add new `store_id=1` production code.
7. Hard delete menu/options/orders.
8. Switch KDS runtime source of truth to `production_tasks`.
9. Expose developer diagnostics to production users.
10. Skip migration for schema change.


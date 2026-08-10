# Known Issues Backlog

> Status: `ACTIVE_GOVERNANCE_BACKLOG`
>
> Last updated: 2026-08-10, America/Toronto
>
> This is the authority for current issue triage. Historical evidence remains
> in the Phase 3 reports and is not rewritten here.

## Priority definitions

| Priority | Meaning |
|---|---|
| P0 | Production is fully unavailable, data is being destroyed/corrupted, or there is a security incident. |
| P1 | A core on-site function is interrupted and cannot be safely recovered during service. |
| P2 | A business-rule defect or local functional exception that needs a workaround. |
| P3 | A UX, process, or governance improvement. |

## Active issues

### KI-011 - Production-like St-Denis Twin parity is not yet established

| Field | Value |
|---|---|
| issue_id | `KI-011` |
| priority | `P2` governance/product readiness |
| title | Staging must become a long-lived Production-like St-Denis Operational Twin |
| observed_behavior | Existing Staging has a verified synthetic St-Denis baseline and automated Owner/browser-equivalent evidence, but no sanitized Production configuration inventory or parity manifest. |
| expected_behavior | Staging reconstructs safe St-Denis operational configuration through shared application code and generic Store logic, with every parity domain classified `MATCH`, `EXPECTED_DIFFERENCE`, `BLOCKING_DIFFERENCE`, or `NOT_YET_VERIFIED`. |
| operational_impact | Production promotion and the former Chinatown-first route remain deferred until Twin parity and Owner field validation are complete. |
| current_workaround | None. Do not read Production or mutate Staging in this planning round. |
| evidence | [TWIN-001 St-Denis Twin Plan](agile/TWIN-001_ST_DENIS_STAGING_TWIN_PLAN.md). |
| status | `PLAN_READY_WAITING_FOR_OWNER_RUNTIME_READ_APPROVAL` |
| next_gate | `PRODUCTION_ST_DENIS_CONFIGURATION_READ_APPROVAL` |
| safety_boundary | No raw customer/order/payment data, credentials, secrets, production printer/device endpoints, `SELECT *`, or complete database dump. |
| last_updated | 2026-08-10 |

### KI-010 - Browser login rejected by proxy same-origin contract

| Field | Value |
|---|---|
| issue_id | `KI-010` |
| priority | `P1` for Staging acceptance; no Production incident observed |
| title | SSH-tunneled browser login returns CORS HTTP 403 before authentication |
| observed_behavior | Manual Chrome acceptance against `http://127.0.0.1:18080` reached `POST /api/v1/auth/login` and received `403 Invalid CORS request`; no principal, role, Organization, Store, or dashboard request was reached. The API-only acceptance client had no browser Origin and therefore passed. |
| expected_behavior | nginx preserves the browser-visible Host and explicit tunnel port so Spring recognizes the request as same-origin; login then proceeds through the existing generic authentication and Organization/Store authorization contracts. |
| operational_impact | Automated Phase-A API and browser-equivalent acceptance pass; the former manual closure is preserved as evidence and deferred by the Owner's TWIN-001 priority. |
| current_workaround | None required. Do not weaken CORS or add a Store/user-specific allowlist. |
| evidence | [STG-009 browser-login 403 repair evidence](runtime/STG-009_PHASE_A_BROWSER_LOGIN_403_REPAIR_EVIDENCE.md) and [browser-equivalent acceptance evidence](runtime/STG-009_PHASE_A_BROWSER_EQUIVALENT_ACCEPTANCE_EVIDENCE.md). |
| status | `REPAIR_DEPLOYED_BROWSER_EQUIVALENT_PASS_DEFERRED_BY_OWNER_TWIN_PRIORITY` |
| target_loop | `TWIN-001_ST_DENIS_STAGING_TWIN`; the browser-equivalent repair is retained as historical foundation and no new runtime repair is authorized by this issue entry. |
| acceptance_criteria | Proxy regression and repository checks pass; independent review accepts; fresh exact-SHA Staging deploy proves browser-equivalent login/session/redirect/Owner shell/Organization/source-Store/dashboard/refresh/logout without 401/403; the exposed synthetic credential is privately rotated; Owner manual browser evidence passes. |
| deployment_required | Historical Staging-only exact redeploy completed; no new deployment is authorized by this issue entry. |
| last_updated | 2026-08-10 |

### KI-009 - Non-web STG-005 one-shot retains WebSocket broker lifecycle

| Field | Value |
|---|---|
| issue_id | `KI-009` |
| priority | `P2` |
| title | Non-web STG-005 one-shot does not exit after validated command completion |
| observed_behavior | Exact Staging `2a6c30a...` reached `STG005_BOOTSTRAP|status=VALIDATED` before credential or data access, but the non-web profile also started `SimpleBrokerMessageHandler`; the JVM remained alive until the reviewed 600-second timeout. Compose `--rm` cleanup and the launcher finalizer then overlapped, preserving fail-closed blocked state. |
| expected_behavior | The dedicated non-web one-shot excludes long-lived WebSocket infrastructure while preserving the normal web runtime contract. A successful password-free plan exits inside its bounded window; unexpected cleanup failure remains fail-closed. |
| operational_impact | Historical STG-008 progress stopped before synthetic business writes; the reviewed pair was later recovered and the current synthetic baseline is complete. No replay is authorized by this issue entry. |
| current_workaround | Historical recovery and lifecycle repairs are retained as evidence. Do not replay the old one-shot or infer current runtime authority from its former exact SHA; the current route is the Owner-prioritized TWIN-001 governance plan. |
| evidence | [STG-008 one-shot lifecycle repair evidence](runtime/STG-008_ONE_SHOT_LIFECYCLE_REPAIR_EVIDENCE.md). |
| authoritative_rule | [AL-003S Staging acceptance runbook](../../deployment/cloud/README_AL003S_STAGING_ACCEPTANCE.md). |
| status | `HISTORICAL_REPAIR_RESOLVED_DEFERRED_BY_OWNER_ST_DENIS_TWIN_PRIORITY` |
| target_loop | `TWIN-001_ST_DENIS_STAGING_TWIN`; no STG-008 replay is authorized by this backlog entry. |
| acceptance_criteria | Focused non-web lifecycle and shell safety regressions plus independent review passed; PR #91 entered `main` and later exact runtime evidence is retained. No new rebind, recovery or PLAN is authorized by this issue entry. |
| deployment_required | Historical Staging-only exact redeploy completed; no new deployment is authorized by this backlog entry. |
| last_updated | 2026-08-09 |

### KI-008 - Non-web STG-005 plan cannot construct servlet request context

| Field | Value |
|---|---|
| issue_id | `KI-008` |
| priority | `P2` |
| title | Non-web STG-005 plan cannot construct servlet request context |
| observed_behavior | The approved password-free `bootstrap-plan` exits during Spring context initialization because `RequestUserContextService` requires `HttpServletRequest` while the guarded one-shot is non-web. |
| expected_behavior | The non-web guarded command constructs without inventing a request; any request-authentication attempt remains fail-closed. |
| operational_impact | Historical blocker resolved: exact `2a6c30a...` constructed and reached `VALIDATED` before credential/data access. The separate lifecycle blocker is tracked as `KI-009`. |
| current_workaround | None; do not reuse old evidence. The former STG-008 continuation is historical and deferred behind the Owner-prioritized TWIN-001 route. |
| evidence | [STG-008 non-web request-context repair evidence](runtime/STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md). |
| authoritative_rule | [AL-003S Staging acceptance runbook](../../deployment/cloud/README_AL003S_STAGING_ACCEPTANCE.md). |
| status | `RESOLVED_BY_EXACT_RUNTIME_VALIDATION` |
| target_loop | `TWIN-001_ST_DENIS_STAGING_TWIN`; no STG-008 replay is authorized by this backlog entry. |
| acceptance_criteria | Focused authorization/non-web safety regressions and independent review passed; PR #89 entered `main`; exact `2a6c30a...` passed rebind/deploy/readiness/recovery and emitted password-free `VALIDATED`. |
| deployment_required | Completed by the exact `2a6c30a...` Staging-only deploy; no Production deployment. |
| last_updated | 2026-08-09 |

### KI-002 - 走上海青被错误压缩为走青

| Field | Value |
|---|---|
| issue_id | `KI-002` |
| priority | `P2` |
| title | 走上海青被错误压缩为走青 |
| observed_behavior | A GRAB/display shorthand compresses the specific removal `走上海青` into the broader phrase `走青`. |
| expected_behavior | Print/display `走上海青`; do not apply the `走葱` + `走香菜` to `走青` compression to this distinct vegetable removal. |
| operational_impact | Kitchen staff can interpret the ticket as removing all greens rather than only Shanghai bok choy. |
| current_workaround | Staff manually clarify the ticket before production when it is observed. |
| evidence | Operator-reported field rule; code-level reproduction and regression test are pending. |
| authoritative_rule | [FRONTDESK_GRAB_ITEM_NAME_RULES.md](../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md), with this issue as the required correction. |
| status | `OPEN` |
| target_loop | Future display-rule bug loop, unassigned. |
| acceptance_criteria | The exact option renders as `走上海青`; only the jointly selected green-onion and cilantro removals compress to `走青`; automated renderer coverage is added. |
| deployment_required | Yes, backend receipt-renderer deployment. |
| last_updated | 2026-07-27 |

### KI-003 - 鸡丝凉面默认细面错误显示为鸡凉细

| Field | Value |
|---|---|
| issue_id | `KI-003` |
| priority | `P2` |
| title | 鸡丝凉面默认细面错误显示为鸡凉细 |
| observed_behavior | The default thin noodle selection is shown in the kitchen shorthand for chicken cold noodles. |
| expected_behavior | Default thin noodles render as `鸡凉`, without the extra `细` suffix. |
| operational_impact | Kitchen tickets contain redundant shorthand and weaken the agreed production naming convention. |
| current_workaround | Kitchen staff treat `鸡凉细` as the default chicken cold noodle. |
| evidence | Operator-reported field rule. Migration V5 establishes thin-noodle ordering, but it does not prove the desired renderer omission. |
| authoritative_rule | [FRONTDESK_GRAB_ITEM_NAME_RULES.md](../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md), with this issue as the required correction. |
| status | `OPEN` |
| target_loop | Future display-rule bug loop, unassigned. |
| acceptance_criteria | Default thin chicken cold noodle renders `鸡凉`; non-default noodle types retain their explicit suffixes; renderer tests cover both cases. |
| deployment_required | Yes, backend receipt-renderer deployment. |
| last_updated | 2026-07-27 |

### KI-004 - 鸡丝凉面韭页面型显示错误

| Field | Value |
|---|---|
| issue_id | `KI-004` |
| priority | `P2` |
| title | 鸡丝凉面韭页面型显示错误 |
| observed_behavior | Chicken cold noodles with leek-leaf noodles do not render using the agreed shorthand. |
| expected_behavior | Render `鸡凉韭`. |
| operational_impact | Kitchen staff must infer a non-default noodle type from an incorrect or unclear label. |
| current_workaround | Staff clarify the noodle type verbally when needed. |
| evidence | Operator-reported field rule; no code-level regression test has yet been recorded. |
| authoritative_rule | [FRONTDESK_GRAB_ITEM_NAME_RULES.md](../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md), with this issue as the required correction. |
| status | `OPEN` |
| target_loop | Future display-rule bug loop, unassigned. |
| acceptance_criteria | A chicken cold noodle with the stable leek-leaf option renders `鸡凉韭`; default thin still renders `鸡凉`; unrelated noodle SKU rules do not regress. |
| deployment_required | Yes, backend receipt-renderer deployment. |
| last_updated | 2026-07-27 |

### KI-005 - 数据库恢复演练未执行

| Field | Value |
|---|---|
| issue_id | `KI-005` |
| priority | `P2` |
| title | 数据库恢复演练未执行 |
| observed_behavior | A backup artifact was operator-confirmed as present and non-empty, but no independently approved restore rehearsal is recorded. |
| expected_behavior | A separately approved, non-production restore rehearsal has a documented result, timing, recovery-point/time expectations, and follow-up actions; backup integrity is independently verified. |
| operational_impact | Backup recoverability and recovery time remain unknown. |
| current_workaround | Preserve backups and treat restoration readiness as unverified. |
| evidence | [PHASE_3_COMPLETION_REPORT.md](runtime/PHASE_3_COMPLETION_REPORT.md) and [POST_DEPLOY_RUNTIME_EVIDENCE.md](runtime/POST_DEPLOY_RUNTIME_EVIDENCE.md). |
| authoritative_rule | [RUNTIME_VERIFICATION_CHECKLIST.md](RUNTIME_VERIFICATION_CHECKLIST.md). |
| status | `EVIDENCE_PENDING` |
| target_loop | Future owner-approved operations reliability loop. |
| acceptance_criteria | An owner-approved isolated restore rehearsal succeeds without Production data mutation; backup integrity, recovery boundaries and scope/limitations are recorded without secrets. |
| deployment_required | No application deployment; an owner-approved operations exercise is required. |
| last_updated | 2026-07-27 |

### KI-006 - 正式生产批准和发布记录尚未建立

| Field | Value |
|---|---|
| issue_id | `KI-006` |
| priority | `P3` |
| title | 正式生产批准和发布记录尚未建立 |
| observed_behavior | A reported runtime commit exists, but no independent formal approval/release record is retained. |
| expected_behavior | Each production deployment has an immutable RC identity, Owner approval, release/PR reference, exact source/artifact digests, deployed commit, migration statement, parity/acceptance results, and rollback reference. |
| operational_impact | Release provenance and incident response are slower and less auditable. |
| current_workaround | Use the Phase 3 evidence and owner confirmation as bounded historical context only. |
| evidence | [VERIFIED_RUNTIME_BASELINE.md](runtime/VERIFIED_RUNTIME_BASELINE.md); [PHASE_3_COMPLETION_REPORT.md](runtime/PHASE_3_COMPLETION_REPORT.md). |
| authoritative_rule | [AGILE_LOOP_OPERATING_MODEL.md](AGILE_LOOP_OPERATING_MODEL.md). |
| status | `PROCESS_PENDING` |
| target_loop | Governance/release-process improvement, unassigned. |
| acceptance_criteria | A lightweight owner-approved RC record freezes exact source/artifact identities after Twin/automated/Owner acceptance, proves same-artifact promotion, records compatibility-gated rollback/roll-forward and backup readiness, and contains no secrets or customer data. |
| deployment_required | No. |
| last_updated | 2026-07-27 |

### KI-007 - 单门店员工的 Android Device Pairing 流程过于复杂

| Field | Value |
|---|---|
| issue_id | `KI-007` |
| priority | `P3` |
| title | 单门店员工的 Android Device Pairing 流程过于复杂 |
| observed_behavior | A user logs in with a single-store account and must still understand and complete a separate Pad Direct pairing action. |
| expected_behavior | After explicit authorization, an employee with exactly one accessible Store may use a guided automatic registration flow bound only to that Store. |
| operational_impact | Pairing errors delay on-site printing setup and make store binding harder to understand. |
| current_workaround | Use the explicit Print Center/Android Control Panel pairing workflow and verify Store ID before use. |
| evidence | Current pairing architecture in `StoreDeviceController`, `StoreDeviceServiceImpl`, Web Print Center, and Android `RestaurantPadDevice` bridge. No approved auto-pair requirement exists yet. |
| authoritative_rule | [AL-001 technical plan](agile/AL-001_OWNER_STORE_ONBOARDING_CHINATOWN_TECHNICAL_PLAN.md). |
| status | `REQUIREMENTS_PENDING` |
| target_loop | Future single-store auto-pairing loop; **not** the first AL-001 implementation batch. |
| acceptance_criteria | A separately approved design proves explicit consent, single-store binding, no token exposure, and no cross-store registration path. |
| deployment_required | Yes, when implemented. |
| last_updated | 2026-07-27 |

## Closed / historical issues

### KI-001 - Orders stale JavaScript chunk / Android WebView blank page

| Field | Value |
|---|---|
| issue_id | `KI-001` |
| priority | Historical P1 |
| title | Orders stale JavaScript chunk / Android WebView blank page |
| observed_behavior | The Orders page previously requested an older JavaScript chunk, rendered a blank page, and could not be exited normally. Clearing Android WebView/App cache recovered the field incident. |
| expected_behavior | Orders loads after deployment without a stale-chunk blank page. |
| operational_impact | The page was unavailable on the affected Pad until cache recovery. |
| current_workaround | Historical recovery was clearing Android WebView/App cache. It is not an ongoing operational requirement. |
| evidence | Responsible owner confirms a code-level repair and field recovery. Historical records retain `INCIDENT_RECOVERED_ROOT_CAUSE_PENDING`; no automatic test/deployment proof is added by this backlog entry. |
| authoritative_rule | Historical Phase 3 reports and future regression coverage for the repaired path. |
| status | `CLOSED_OPERATOR_CONFIRMED` |
| target_loop | Not a current Agile Loop target. |
| acceptance_criteria | Closed by responsible-owner confirmation of code repair and field recovery; future evidence may add, but does not retroactively imply, machine verification. |
| deployment_required | Completed historically according to owner confirmation. |
| last_updated | 2026-07-27 |

`KI-001` is not marked `MACHINE_VERIFIED`. Do not reopen it merely because the
historical cache-clear recovery remains documented; reopen only with a new
reproduced or field-observed incident.

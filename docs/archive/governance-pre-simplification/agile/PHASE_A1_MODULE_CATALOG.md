# Phase A1 Module Catalog

Status: `PHASE_A1_MODULE_CATALOG = PASS`

Date: 2026-08-13

Fresh repository authority at A1 start:

```text
origin/main@1499e54f4617b4f0212f0144eeade8b995ed7c51
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging: no deployment required for A1
- Schema: no migration
- Runtime effect: repository documentation and static validation only

Machine-readable catalog:

```text
backend/src/main/resources/module/module-catalog.v1.json
```

Static validator:

```text
backend/src/test/java/com/restaurant/system/modules/ModuleCatalogContractTest.java
```

## A1 boundary decision

The Module Catalog is capability-based. It is not one frontend page per module
and not one backend package per module. A canonical module boundary may combine
frontend routes, backend APIs, Store configuration, environment capability,
authorization, hardware/runtime requirements and profile compatibility.

Owner required normal Lanzhou Store capabilities remain required even when the
canonical module key is different:

```text
ORDERING / POS
MENU
MENU_MANAGEMENT
TABLE_MANAGEMENT
PRINTING
GRAB_PRINTING
FRONTDESK_RECEIPT
ORDER_HISTORY
REPORTING
```

`GRAB_PRINTING` and `FRONTDESK_RECEIPT` are modeled as required capabilities of
the canonical `PRINTING` module, not separate top-level modules.

Owner decisions retained:

```text
KDS = OPTIONAL
KDS = DISABLED_BY_DEFAULT
KDS = NOT_ACTIVATION_BLOCKING
REPORTING_CORE = CORE_REQUIRED
```

## Canonical module keys

| Module key | Classification | Category | Active normal Store | Activation blocking | Current implementation authority |
|---|---:|---|---:|---:|---|
| `ORDERING_POS` | `CORE_MODULE` | `STORE_OPERATION` | Required | Yes | POS draft/submit/update and beverage workflow |
| `MENU` | `CORE_MODULE` | `STORE_OPERATION` | Required | Yes | menu catalog, revision, offline menu snapshot |
| `MENU_MANAGEMENT` | `CORE_MODULE` | `STORE_ADMINISTRATION` | Required | Yes | Owner Menu Management, Size, Pricing Rules, Combo Configuration |
| `TABLE_MANAGEMENT` | `CORE_MODULE` | `STORE_ADMINISTRATION` | Required | Yes | dining tables and table-board configuration |
| `PRINTING` | `CORE_MODULE` | `STORE_OPERATION` | Required | Yes | logical printers, print jobs, GRAB, Frontdesk Receipt, HOT Kitchen tickets |
| `ORDER_HISTORY` | `CORE_MODULE` | `STORE_OPERATION` | Required | Yes | frontdesk order history/detail/reprint context |
| `REPORTING_CORE` | `CORE_MODULE` | `REPORTING` | Required | Yes | sales/item reporting and operational summaries; currently behind legacy `ANALYTICS` flag |
| `STAFF_ACCESS` | `CORE_MODULE` | `ACCESS_CONTROL` | Required | Yes | auth, roles, Store/Organization memberships, Staff Admin |
| `STORE_ADMINISTRATION` | `CORE_MODULE` | `STORE_ADMINISTRATION` | Required | Yes | Store context, Owner/Admin surfaces, audit logs and operational settings |
| `KDS` | `OPTIONAL_MODULE` | `OPTIONAL_OPERATIONS` | Disabled by default | No | KDS screens, pickup/pass/noodle/hot kitchen APIs |
| `ANALYTICS_ADVANCED` | `OPTIONAL_MODULE` | `OPTIONAL_REPORTING` | Disabled by default | No | profit/multi-store/advanced analytics routes that remain split from required Reporting Core |

## Current capability audit

| Capability | A1 classification | Canonical location |
|---|---|---|
| Ordering | `CORE_MODULE` | `ORDERING_POS` |
| Dine-In | `MODULE_CAPABILITY` | `ORDERING_POS` + `TABLE_MANAGEMENT` |
| Takeout | `MODULE_CAPABILITY` | `ORDERING_POS` |
| Menu | `CORE_MODULE` | `MENU` |
| Menu Management | `CORE_MODULE` | `MENU_MANAGEMENT` |
| Table Management | `CORE_MODULE` | `TABLE_MANAGEMENT` |
| Frontdesk | `MODULE_CAPABILITY` | `ORDERING_POS` |
| Frontdesk Order History | `CORE_MODULE` | `ORDER_HISTORY` |
| GRAB | `MODULE_CAPABILITY` | `PRINTING` |
| Frontdesk Receipt | `MODULE_CAPABILITY` | `PRINTING` |
| Hot Kitchen | `MODULE_CAPABILITY` | `PRINTING` for tickets; `KDS` for optional screen workflow |
| KDS | `OPTIONAL_MODULE` | `KDS` |
| Beverage | `MODULE_CAPABILITY` | `ORDERING_POS` |
| Printing | `CORE_MODULE` | `PRINTING` |
| Devices / Pads | `HARDWARE_CAPABILITY` | `PRINTING` runtime hardware requirements; not a Store module assignment |
| Reports | `CORE_MODULE` | `REPORTING_CORE` |
| Analytics | `OPTIONAL_MODULE` | `ANALYTICS_ADVANCED` |
| Owner Admin | `ROLE_CAPABILITY` | `STORE_ADMINISTRATION`, `STAFF_ACCESS`, admin module surfaces |
| Organization | `SHARED_INFRASTRUCTURE` | `STORE_ADMINISTRATION` via Store/Organization context |
| Store | `CORE_MODULE` | `STORE_ADMINISTRATION` |
| Staff | `CORE_MODULE` | `STAFF_ACCESS` |
| Roles / Permissions | `ROLE_CAPABILITY` | `STAFF_ACCESS` |
| Realtime / WebSocket | `SHARED_INFRASTRUCTURE` | environment capability consumed by POS/history/KDS |
| Offline Menu | `MODULE_CAPABILITY` | `MENU` |
| Menu Revision | `MODULE_CAPABILITY` | `MENU` |
| Pricing Policy | `MODULE_CAPABILITY` | `MENU_MANAGEMENT` |
| Combo Configuration | `MODULE_CAPABILITY` | `MENU_MANAGEMENT` |
| Stations | `STORE_CONFIGURATION` | `MENU` and `PRINTING` relationships |
| Audit | `ROLE_CAPABILITY` | `STORE_ADMINISTRATION` |
| Platform Admin | `LEGACY_COUPLING` | shared infrastructure, not a normal Store module |
| Store Settings | `STORE_CONFIGURATION` | multiple modules; A3 defines Store module state |
| Operational Settings | `STORE_CONFIGURATION` | includes printing runtime-mode settings, which remain separate from Store module state |

## Feature flag classification

Current feature flags are not canonical Store module state.

| Current flag/source | A1 classification | A3 implication |
|---|---|---|
| `FeaturePackage.CORE_POS` / `app.features.core-pos` | `ENVIRONMENT_CAPABILITY` | Environment capability input; normal Store core module remains required. |
| `FeaturePackage.PRINTING` / `app.features.printing` | `ENVIRONMENT_CAPABILITY` | Environment printing availability, separate from Store `PRINTING`. |
| `stores.printing_enabled` | `LEGACY_FLAG` | Legacy Store-level printing switch; A3 defines compatibility precedence and retirement. |
| `stores.printing_mode` | `RUNTIME_MODE` | Store-scoped selected print mode; not the Store module boolean. |
| `app.printing.allowed-modes` / Staging allowed-mode ceiling | `RUNTIME_MODE` | Environment/runtime ceiling for allowed print modes, not Store module state. |
| `FeaturePackage.KDS` / `app.features.kds` | `ENVIRONMENT_CAPABILITY` | KDS runtime availability; KDS Store module remains optional/default-off. |
| `FeaturePackage.ADMIN` / `app.features.admin` | `ENVIRONMENT_CAPABILITY` | Admin runtime availability; authorization remains role/capability guarded. |
| `FeaturePackage.ANALYTICS` / `app.features.analytics` | `ENVIRONMENT_CAPABILITY` | Currently gates both required Reporting Core and optional Advanced Analytics; split later. |
| `FeaturePackage.PLATFORM` / `app.features.platform` | `ENVIRONMENT_CAPABILITY` | Platform Admin runtime availability; not a normal Store module. |
| `FeaturePackage.DEVELOPER_TOOLS` / `app.features.developer-tools` | `RUNTIME_MODE` | Developer-only runtime mode. |
| `frontend.featureConfig.ts` | `LEGACY_FLAG` | UI hiding only; not a security boundary. |
| `VITE_ENABLE_DEV_ROLE_SWITCHER` | `NOT_A_MODULE` | Developer-only. |
| `VITE_NETWORK_DIAGNOSTICS_ENABLED` | `ENVIRONMENT_CAPABILITY` | Diagnostics only. |

## Route / API / authorization map

The complete route/API/auth map is stored in the JSON catalog. Key findings:

- POS ordering uses `CORE_POS`, frontend role gates and backend capabilities
  such as `order:create`, `order:submit` and Store access.
- Menu Management uses `admin:menu_manage` and Store access.
- Printing uses `FeaturePackage.PRINTING`, Store printing settings,
  `admin:printing_manage`, Store access and Pad/device runtime authority.
- KDS is already gated by `FeaturePackage.KDS` and KDS-specific role
  capabilities. Because the default is false, KDS-off is already a supported
  route/API disabled state.
- Reports are currently all under `ANALYTICS`; Owner-required reporting is
  therefore represented as `REPORTING_CORE` with a legacy flag coupling to be
  split in later phases.
- Platform Admin remains a legacy coupling. A1 does not authorize the direct
  active Store creation route as future Owner provisioning.

## A1 validation

Static validation command:

```text
mvn -q -Dtest=ModuleCatalogContractTest test
```

The validator checks:

- module keys are unique and non-empty;
- module, capability and current-source classifications are from the approved
  A1 vocabulary;
- all Owner-required normal Lanzhou Store capabilities are represented;
- `GRAB_PRINTING` and `FRONTDESK_RECEIPT` map to `PRINTING`;
- `KDS` is optional, disabled by default and not activation blocking;
- `REPORTING_CORE` is core required;
- each module has category, core/optional, default state, dependency/conflict,
  route/API and authorization metadata;
- feature flag classification is present.

## Boundaries retained

- No Production deploy, restart, Flyway, config, credential, printer, Pad,
  menu, Store or business-data mutation.
- No Staging deploy, restart, Flyway, config, data mutation or credential
  change.
- No A4 Store Profile implementation, Phase B/C, Chinatown, Sainte-Catherine
  or Production release work.
- No shared-code Store ID/name branch is introduced.

Expected A1 completion state after tests, Agent 6, PR and merge:

```text
PHASE_A1_MODULE_CATALOG = PASS
```

Completion evidence:

```text
AGENT_6 = A1_ACCEPT
PR = #137
MERGE_SHA = 34169152c6d48ecf503b441fe7428416c399d0a9
```

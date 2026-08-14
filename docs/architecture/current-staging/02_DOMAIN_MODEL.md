# 02 Domain Model

## Purpose

This diagram records the current Store-scoped domain objects that matter for
Phase A architecture: Store identity, modules, profile templates, menu,
ordering, tables, staff access, and printing.

## Current runtime/source SHA

- Repository source SHA: `5f4504d23135655f63d564301f8e98f3218347b2`
- Deployed Staging SHA: `3440fddad7571409c66189e44976658921e5de1f`
- Staging Flyway: `V15`

## Scope

Current schema and code relationships only. Store Profile tables are templates;
they are not live Store clones.

## Mermaid diagram

```mermaid
erDiagram
    ORGANIZATIONS ||--o{ STORES : owns
    ORGANIZATIONS ||--o{ USERS : scopes
    STORES ||--o{ STORE_MODULES : configures
    STORES ||--o{ STORE_PRICING_POLICIES : prices
    STORES ||--o{ STORE_COMBO_COMPONENTS : offers
    STORES ||--o{ CATEGORIES : contains
    STORES ||--o{ MENU_ITEMS : sells
    STORES ||--o{ MENU_ITEM_OPTIONS : defines
    STORES ||--o{ DINING_TABLES : lays_out
    STORES ||--o{ STATIONS : routes_to
    STORES ||--o{ PRINTER_CONFIGS : owns
    STORES ||--o{ PRINTER_ASSIGNMENTS : maps
    STORES ||--o{ STORE_DEVICES : registers
    STORES ||--o{ ORDERS : receives
    STORES ||--o{ STORE_MEMBERSHIPS : grants

    USERS ||--o{ STORE_MEMBERSHIPS : has
    USERS ||--o{ ORDERS : creates

    CATEGORIES ||--o{ MENU_ITEMS : groups
    MENU_ITEMS ||--o{ MENU_ITEM_OPTIONS : allows
    MENU_ITEM_OPTIONS ||--o{ MENU_ITEM_OPTIONS : parent_child
    STATIONS ||--o{ MENU_ITEMS : production_route

    ORDERS ||--o{ ORDER_ITEMS : snapshots
    ORDER_ITEMS ||--o{ ORDER_ITEM_OPTIONS : snapshots
    ORDERS ||--o{ KITCHEN_TASKS : creates
    ORDERS ||--o{ PRINT_JOBS : produces
    PRINTER_CONFIGS ||--o{ PRINTER_ASSIGNMENTS : targets
    PRINTER_ASSIGNMENTS ||--o{ PRINT_JOBS : routes

    STORE_PROFILES ||--o{ STORE_PROFILE_VERSIONS : versions
    STORE_PROFILE_VERSIONS ||--o{ STORE_PROFILE_ARTIFACTS : contains

    ORGANIZATIONS {
      bigint id PK
      string code
      string name
    }
    STORES {
      bigint id PK
      bigint organization_id FK
      string code
      string name
      integer menu_revision
      boolean printing_enabled
      string printing_mode
    }
    STORE_MODULES {
      bigint store_id FK
      string module_key
      boolean enabled
      string source
      string configuration_status
    }
    STORE_PROFILES {
      bigint id PK
      string profile_code
      string status
    }
    STORE_PROFILE_VERSIONS {
      bigint id PK
      bigint profile_id FK
      string profile_version
      string schema_version
      string status
      char fingerprint_sha256
    }
    STORE_PROFILE_ARTIFACTS {
      bigint id PK
      string artifact_type
      char fingerprint_sha256
    }
    MENU_ITEMS {
      bigint id PK
      bigint store_id FK
      bigint category_id FK
      bigint station_id FK
      string name_en
      string name_zh
      decimal base_price
      boolean is_active
    }
    MENU_ITEM_OPTIONS {
      bigint id PK
      bigint store_id FK
      bigint menu_item_id FK
      bigint parent_option_id FK
      string option_group
      string option_code
      decimal price_delta
      boolean is_active
    }
    ORDERS {
      bigint id PK
      bigint store_id FK
      string status
      string order_type
      decimal total_amount
    }
    PRINT_JOBS {
      bigint id PK
      bigint store_id FK
      bigint order_id FK
      string module_code
      string execution_mode
      string status
    }
```

## Key invariants

- Live ordering uses submitted order snapshots; historical orders are not
  repriced when menu or pricing policies change.
- `store_pricing_policies` is the canonical Store-level Size/Combo pricing
  source.
- `store_combo_components` is the canonical Store-level Combo contents source.
- `menu_item_options` remains the per-item Size/Combo enablement and ordinary
  option graph.
- `store_modules` is the canonical Store module state, while runtime
  enforcement work is still expected in A6/A7.
- Store Profile records are DB-backed templates and must not contain source DB
  IDs, secrets, PII, physical printer endpoints, or device credentials.

## What omitted

- low-level audit log tables
- inventory/BOM relationships not central to current Phase A profile/module
  baseline
- raw authentication token storage details
- physical printer endpoint data

## Source files used

- `backend/src/main/resources/db/migration/V11__add_store_pricing_policies.sql`
- `backend/src/main/resources/db/migration/V12__add_store_combo_components.sql`
- `backend/src/main/resources/db/migration/V13__add_store_modules.sql`
- `backend/src/main/resources/db/migration/V14__add_store_profiles.sql`
- `backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql`
- `backend/src/main/java/com/restaurant/system/menu/service/impl/MenuServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/modules/StoreModuleServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java`
- `backend/src/main/java/com/restaurant/system/printing/entity/PrintJob.java`

## Last verified date

2026-08-14.

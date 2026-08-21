# Phase A8 Hardware Capability Contract Evidence

Status: `PHASE_A8_HARDWARE_CAPABILITY_CONTRACT = PASS`

Date: 2026-08-14

Fresh repository authority at A8 start:

```text
origin/main@01d199a484b5ece19cf16d002a2565b6c42751e3
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging: no deployment required for the A8 repository contract PR
- Schema: no Flyway migration
- Physical printer/device binding: `SEPARATE_OWNER_RUNTIME_GATE_PENDING`

## Layering contract

A8 separates these concerns:

| Layer | Canonical meaning | Current source |
|---|---|---|
| Product module | Store-level business capability on/off | `store_modules` |
| Hardware capability | Reviewed capability required by modules/profile | `hardware-capability-catalog.v1.json` |
| Environment capability | Runtime package/feature availability | `FeatureFlagService`, runtime policy |
| Store logical configuration | Store-owned logical printers and assignments | `printer_configs`, `printer_assignments` |
| Physical device binding | Pad/printer pairing and endpoints | `store_devices`, printer endpoint fields; not Store Profile content |
| Runtime mode | Selected print execution mode | `stores.printing_mode` |

`stores.printing_enabled` remains bounded compatibility only. It is not the
canonical Store module state.

## Hardware Capability Catalog

Machine-readable catalog:

```text
backend/src/main/resources/hardware/hardware-capability-catalog.v1.json
```

Canonical keys:

```text
TOUCH_CLIENT
PRINT_GRAB
PRINT_FRONTDESK_RECEIPT
PRINT_HOT_KITCHEN
PAD_DIRECT_PRINT_CLIENT
PAD_DEVICE
DEVICE_ENROLLMENT
KDS_DISPLAY_CLIENT
```

Deliberately absent until future reviewed scope:

```text
CASH_DRAWER
SCANNER
CUSTOMER_DISPLAY
CARD_TERMINAL
```

Legacy aliases are compatibility-only. The deployed
`ST_DENIS_CANONICAL_PROFILE/v1` still contains historical keys such as
`PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT` and `PAD_DEVICE_FOR_PAD_DIRECT`;
A8 resolves those aliases through the hardware catalog without modifying the
published profile content or fingerprint.

## Requirement/readiness contract

Readiness states:

```text
NOT_REQUIRED
UNCONFIGURED
CONFIGURED
VERIFIED
```

Dependency satisfaction:

- `NOT_REQUIRED`, `CONFIGURED`, and `VERIFIED` satisfy module dependency
  validation.
- `UNCONFIGURED` fails closed with `HARDWARE_CAPABILITY_MISSING`.

The Store Context module payload now exposes:

```text
hardware_capabilities
hardware_readiness
```

`hardware_capabilities` contains only dependency-satisfied canonical keys.
`hardware_readiness` records every observed capability state with source/layer
metadata.

## Logical vs physical printing

Logical printing requirements:

```text
PRINT_GRAB
PRINT_FRONTDESK_RECEIPT
PRINT_HOT_KITCHEN
```

These are satisfied by enabled Store-scoped logical assignments to enabled
logical printers. They do not require printer IPs or physical printer
credentials in Store Profiles.

Physical binding:

```text
PAD_DIRECT_PRINT_CLIENT
PAD_DEVICE
DEVICE_ENROLLMENT
```

`PAD_DIRECT_PRINT_CLIENT` is required only when the Store runtime mode is
`PAD_DIRECT`. In current Staging `MOCK`, it is `NOT_REQUIRED`, so Staging can
validate the Printing module without contacting or binding a physical printer.

## A2/A6/A7 integration

A8 updates the module dependency graph to require the canonical hardware
capabilities for Printing. The same `ModuleDependencyValidator` consumes the
A8 catalog, so unknown capabilities still fail closed and historical profile
aliases resolve deterministically.

Backend route/API gating now checks, in order:

```text
Store module persisted/enabled
→ module dependency configuration
→ environment capability
→ hardware capability
→ business action
```

Frontend module access now distinguishes:

```text
MODULE_DISABLED
MODULE_CONFIGURATION_INVALID
MODULE_ENVIRONMENT_CAPABILITY_MISSING
MODULE_HARDWARE_CAPABILITY_MISSING
```

## Staging proof retained

The current Staging state remains valid under A8:

```text
PRINTING module = ON
runtime mode = MOCK
logical printers = 4
assignments = 3
physical endpoint required = NO
PAD_DIRECT_PRINT_CLIENT = NOT_REQUIRED
```

No real printer endpoint, printer credential, Pad credential, token, raw env or
secret is placed in the repository contract or evidence.

## Validation

Focused backend tests:

```text
mvn -q -Dtest='HardwareCapabilityCatalogContractTest,ModuleCatalogContractTest,ModuleDependencyValidatorTest,StoreModuleCapabilityProviderImplTest,StoreModuleServiceImplTest,StoreModuleAccessEvaluatorTest,StoreProfileContractValidatorTest,PrintDispatcherServiceImplTest' test
PASS
```

Focused frontend test:

```text
npm run test -- storeModuleAccess.test.ts --run
PASS — 1 file, 6 tests
```

## Boundary

A8 does not implement:

- physical printer binding UI
- real printer discovery
- Store activation
- Phase B Pad enrollment wizard
- Chinatown/Sainte-Catherine hardware
- Production printer/device mutation
- Flyway migration

Next continuous phase under the active Owner authorization:

```text
PHASE_A9_LEGACY_COUPLING_REMOVAL
```

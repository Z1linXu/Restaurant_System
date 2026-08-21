# Phase A2 Module Dependency Graph

Status: `PHASE_A2_MODULE_DEPENDENCY_GRAPH = PASS`

Date: 2026-08-13

Fresh repository authority at A2 start:

```text
origin/main@34169152c6d48ecf503b441fe7428416c399d0a9
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging: no deployment required for A2
- Schema: no migration
- Runtime effect: repository/backend validator only; no public API behavior
  change

Canonical inputs:

```text
backend/src/main/resources/module/module-catalog.v1.json
backend/src/main/resources/module/module-dependency-graph.v1.json
```

Reusable validator:

```text
backend/src/main/java/com/restaurant/system/modules/ModuleDependencyValidator.java
```

Focused tests:

```text
backend/src/test/java/com/restaurant/system/modules/ModuleDependencyValidatorTest.java
```

## A2 contract

A2 uses the merged A1 catalog as the canonical module source and adds a
machine-readable dependency graph. The graph supports these relationship
types:

```text
REQUIRES
CONFLICTS_WITH
REQUIRES_ENVIRONMENT_CAPABILITY
REQUIRES_HARDWARE_CAPABILITY
```

Validation is fail-closed. Unknown modules, invalid graph entries, disabled
required modules, missing environment capability, missing hardware capability
and module conflicts all produce stable machine-readable issue codes.

## Required outcomes

| Scenario | Expected result |
|---|---|
| Default normal Store module configuration | `VALID` |
| `KDS = DISABLED` | `VALID` |
| Unknown module key | `FAIL_CLOSED / UNKNOWN_MODULE` |
| Required core/dependency disabled | `FAIL_CLOSED / CORE_MODULE_DISABLED + REQUIRED_MODULE_DISABLED` |
| Missing environment capability | `FAIL_CLOSED / ENVIRONMENT_CAPABILITY_MISSING` |
| Missing hardware capability | `FAIL_CLOSED / HARDWARE_CAPABILITY_MISSING` |
| Conflicting modules enabled | `FAIL_CLOSED / MODULE_CONFLICT` |
| Invalid dependency graph | `FAIL_CLOSED / INVALID_DEPENDENCY_GRAPH` |

The stable issue-code vocabulary is:

```text
UNKNOWN_MODULE
CORE_MODULE_DISABLED
REQUIRED_MODULE_DISABLED
MODULE_CONFLICT
ENVIRONMENT_CAPABILITY_MISSING
HARDWARE_CAPABILITY_MISSING
INVALID_DEPENDENCY_GRAPH
```

## Boundary decisions retained

- A2 does not create Store module persistence; A3 owns per-Store module state.
- A2 does not enforce runtime backend/frontend gating; A6/A7 own that later.
- A2 does not implement Store Profiles; A4 owns Profile persistence/versioning.
- A2 does not authorize schema migration, Staging deployment, Production
  deployment, Chinatown, Sainte-Catherine, Phase B or Phase C.

Completion evidence:

```text
PHASE_A2_MODULE_DEPENDENCY_GRAPH = PASS
AGENT_6 = A2_ACCEPT
PR = #138
MERGE_SHA = 1780c8934a502709844713d91c493b076e714983
```

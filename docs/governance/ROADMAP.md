# Productization Roadmap

This file defines Phase boundaries and high-level acceptance. It does not
authorize implementation or record current execution history. Current status
and authorization live only in [`CURRENT_STATE.yml`](CURRENT_STATE.yml).

## Transition rule

Technical acceptance may complete the current Phase or Package gate. Only the
Owner may open the next Phase or expand the authorized Package.

```text
technical PASS != next Phase authorization
```

## Phase A — Modular Productization

Goal:

```text
working St-Denis implementation
-> configurable shared platform for N Stores
```

Scope:

- dynamic standard Size and Store pricing policy;
- module catalog and dependency graph;
- Store-level module configuration and gating;
- versioned Store Profile contract and St-Denis canonical profile;
- Menu Management configurability closure;
- current-Staging architecture baseline;
- backend/frontend module gating;
- hardware capability separation;
- legacy coupling removal;
- regression and modular acceptance;
- Store-scoped Printing Display Rule configuration;
- Chain Master Menu/materialization design closure.

Acceptance summary:

- repository implementation and automated acceptance: `PASS`;
- Owner Phase A/A11 acceptance: `PASS`;
- Chain Master Menu design gate: `PASS`;
- Phase A is complete.

Historical implementation and acceptance evidence is preserved under
[`docs/archive/governance-pre-simplification/agile`](../archive/governance-pre-simplification/agile/).

## Phase B — Owner New Store Provisioning

Goal:

```text
Owner -> Create New Store -> Profile -> Modules -> Configure -> Validate
-> READY -> Activate
```

### Part 1 — Create, materialize and review

Boundary:

- Owner-authorized Organization-scoped provisioning;
- create a synthetic/non-active Store;
- materialize the versioned Chain Master Menu and Store Profile artifacts;
- preserve Store independence and idempotent replay;
- expose Owner UI/API for review;
- allow review/deactivation of local menu content;
- validate on exact-SHA Staging;
- stop before activation and final real-world binding.

Repository implementation is merged. Runtime acceptance remains incomplete.
The last repository-proven Staging deployment predates current `origin/main`;
see [`CURRENT_STATE.yml`](CURRENT_STATE.yml).

Part 1 acceptance requires:

- reviewed repository checks and required Agent 6 review;
- exact-SHA isolated Staging deploy with Flyway safety gates;
- automated Owner login/scope/catalog/provision/replay validation;
- no target Store or ledger write on failed preconditions;
- Owner manual Part 1 retest;
- a unique stop before Part 2 or real activation.

### Part 2 — Complete operational provisioning

Candidate scope:

- table/station configuration;
- staff/access provisioning;
- logical printing topology;
- automatic device enrollment/readiness;
- complete validation and recovery;
- READY and Owner-controlled activation.

Part 2 is `NOT AUTHORIZED`. Candidate designs in
[`drafts/`](drafts/) are supporting material only.

## Phase C — Real Multi-Store Proof

Goal:

```text
Chinatown first
-> Sainte-Catherine second
-> St-Denis + Chinatown + Sainte-Catherine multi-Store acceptance
```

Phase C must use the accepted Phase B workflow. It must not use manual SQL
shortcuts, Store-ID/name hardcodes, database clones, code forks or special
Store branches.

High-level acceptance:

- three independent Stores on the shared application;
- no cross-Store data or authorization leakage;
- Store-owned menu/profile/module/configuration state;
- required ordering, reporting, printing and device flows pass;
- physical bindings are explicit per Store;
- no unresolved P0/P1 issue;
- another comparable Store can be provisioned without shared business-code
  changes.

Phase C is `NOT AUTHORIZED`.

## Owner transition gates

Owner approval is required for:

- resuming any Phase or Package that `CURRENT_STATE.yml` explicitly marks as
  paused;
- accepting Part 1 and opening Part 2;
- real Store activation or real credential/master-data actions;
- opening Phase C;
- every Production release batch.

Draft plans, passing tests, merged code and Staging acceptance do not replace
these approvals.

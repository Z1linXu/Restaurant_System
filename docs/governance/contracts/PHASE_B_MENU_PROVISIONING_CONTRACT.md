# Phase B Menu Provisioning Contract

## Status

```text
PHASE_B_MENU_PROVISIONING_MODEL = DEFINED
PHASE_B_IMPLEMENTATION = WAITING_FOR_EXPLICIT_OWNER_APPROVAL
```

This is the Phase B entry contract, not an implementation.

Current note: the Owner has since granted Phase B Part 1 implementation
authority, and the repository implementation is recorded in
[PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](../../archive/governance-pre-simplification/agile/PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md).
The status above is retained as historical A11.5 design evidence.

Owner authorization clarification for Phase B Part 1: Store provisioning
authorization is authenticated principal plus `OWNER` authority plus active
Organization Owner membership plus correct Organization scope. A
`STG005_` username/login prefix is not a product authorization requirement;
that namespace remains only for explicit Staging synthetic bootstrap and
fixture identity contracts.

## Historical required Owner approval before code

Phase B implementation must not start until the Owner explicitly approves:

```text
BEGIN_PHASE_B_OWNER_STORE_PROVISIONING_IMPLEMENTATION
```

## Phase B v1 provisioning sequence

```text
1. Select target Organization.
2. Select published Master Menu version and verify fingerprint.
3. Select reviewed Store Profile version and verify fingerprint.
4. Validate Profile references to Master category/product/option/station keys.
5. Create target Store and Store-owned operational rows in one transaction.
6. Materialize menu, pricing, combo, module, station and initial A11 rule state.
7. Persist master identity mappings/provenance.
8. Run bounded Staging validation.
9. Stop for Owner acceptance.
```

## Required validation

- Organization exists and is active.
- Target Store code is unique in the intended scope.
- Master Menu version is published and immutable.
- Profile version is reviewed/ready and fingerprint-valid.
- Profile references resolve to the selected Master Menu version.
- Profile contains required post-A11 `PRINTING_DISPLAY_RULES` defaults unless a
  historical exemption is explicitly carried forward.
- No source Store IDs, live DB IDs, secrets, device credentials, printer
  endpoints, payment/customer data or runtime queue state appear in artifacts.
- Materialized Store catalog is self-contained and Store-scoped.

## API boundary

No Phase B provisioning API is implemented by A11.5. Future APIs must be
separate from legacy Platform Admin direct active Store creation, which remains
disabled until the Phase B provisioning path exists.

Current Part 1 implementation adds the canonical Owner API under
`/api/v1/owner/organizations/{organizationId}/phase-b/store-provisioning`;
see [PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](../../archive/governance-pre-simplification/agile/PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md).

Likely future API families:

- Master Menu read/list/version endpoints;
- provisioning dry-run endpoint;
- provisioning execute endpoint;
- provisioning evidence/status endpoint;
- Store-local override inspection endpoint.

## Runtime boundary

Phase B implementation must first validate on Staging. It must not create
Chinatown, Sainte-Catherine or any Production Store without a later explicit
Owner runtime gate.

## Stop state after this design

```text
PHASE_A11_5_CHAIN_MASTER_MENU_DESIGN_COMPLETE_WAITING_FOR_PHASE_B_OWNER_APPROVAL
```

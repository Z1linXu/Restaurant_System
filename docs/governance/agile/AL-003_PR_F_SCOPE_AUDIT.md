# AL-003 PR-F Public API Scope Audit

> Status: `AUDITED_WAITING_FOR_PR_D_PR_E_PR_F0`
>
> Audit base: `e6b41dd644c50b847d27947b5b0d27e1d4449c09`
>
> Runtime/API status: `NOT_IMPLEMENTED`

## Current fact

Current `main` has no `OwnerStoreMenuCloneController`, public validate/execute
endpoint, or complete Owner menu-clone orchestration service. V10, the request
DTO/response DTO, profile registry, reservation coordinator, and PR-C base
transaction are internal foundations only. PR-D, PR-E, and PR-F0 must enter
`origin/main` in order before PR-F implementation.

## Proposed routes from the approved technical plan

```http
POST /api/v1/owner/organizations/{organizationId}/stores/{targetStoreId}/menu-clone/validate
POST /api/v1/owner/organizations/{organizationId}/stores/{targetStoreId}/menu-clone
```

The second route is execute. PR-F must not invent an additional `/execute`
route. Until merged, these remain proposals and must not be documented as live
API.

## Contract

- Both routes accept `source_store_id` and exact `profile_code` through the
  existing request DTO.
- Profile code is strict, case-sensitive, and rejects leading/trailing
  whitespace. Registry lookup, fingerprinting, and reservation use the same
  original value.
- Execute requires a bounded nonblank `Idempotency-Key`; validate does not
  reserve or persist an idempotency key.
- Execute returns the existing sanitized response: request/scope/profile,
  revisions, status, replay flag, created counts, result code, and warnings.
- Public or durable responses never include category, station, item, or option
  ID maps.
- The validation response uses the technical plan's already-approved structured
  diagnostics. PR-F depends on PR-F0 implementing that frozen contract rather
  than returning permanently empty diagnostic collections.

## Authorization and preconditions

- Resolve the authenticated Owner and require active membership in the exact
  Organization. Platform Admin has no implicit bypass.
- Source and target must belong to that Organization. Cross-Organization
  failures return 403 without Store-existence disclosure.
- Only after Organization authorization may an absent target map to 404.
- Target must be inactive, have printing disabled with mode `DISABLED`, and
  have an empty menu.
- Validate is zero-write and uses the same logical planner as execute.
- Execute repeats authorization and validation, reserves the request, then
  executes under ascending source/target Store locks.

## Replay and failure

- Completed same-key/same-fingerprint requests replay the durable sanitized
  summary with `replayed=true` and create no rows or revision change.
- Same key with another fingerprint returns `IDEMPOTENCY_CONFLICT`.
- Processing returns `MENU_CLONE_IN_PROGRESS`.
- Failed is terminal for that key and returns
  `MENU_CLONE_RETRY_REQUIRES_VALIDATION`; retry requires a new validation and a
  new key. No automatic FAILED retry is allowed.
- V10 is canonical durable evidence. Supplementary audit failure cannot turn a
  committed clone into a failed clone.

## Error and test boundary

PR-F must preserve the technical plan's safe 400/403/404/409/422/500 mappings
without leaking raw exceptions or Store existence. Before implementation it
must also align Bean Validation failures with one documented error code; the
current generic validation handler does not establish the proposed
`MENU_CLONE_REQUEST_INVALID` code by itself.

Required tests include both MockMvc routes, Owner/cross-Organization/Admin
authorization, strict profile code, validate zero writes, execute/replay/
FAILED/concurrency/rollback/revision behavior, sanitized audit/error content,
no public ID maps, source invariance, and no Printing/Order/Payment/KDS side
effects. PostgreSQL V1-V10, focused/full tests, compile, diff, secret, and scope
checks remain mandatory.

## Exclusions and stop gate

PR-F does not own menu rules, migration, frontend UI, Android, deployment, or
real clone execution. The unresolved PR-F0 validate/execute parity repair and
frozen diagnostics implementation are an active Dependency Repair Gate; PR-F
implementation must not start before they are completed and merged.

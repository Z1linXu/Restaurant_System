# Staging Synthetic/Test Fixture Cleanup Path

This is a bounded reconciliation path for the current Owner-approved Staging
fixture cleanup window. It is not a general Store deletion feature and is not
available as a normal Store CRUD operation.

## Runtime boundary

The backend endpoint is:

```text
POST /api/v1/owner/organizations/{organizationId}/staging/fixture-cleanup
```

It requires the existing Platform feature, an authenticated Owner with an
active membership in the exact Organization, the explicit Phase B provisioning
runtime gate, an explicit Store ID list, and an `Idempotency-Key` for execute
requests. Production profiles, non-Staging runtimes and disabled Phase B
provisioning fail closed. The endpoint has no UI route and does not alter the
general Store CRUD contract.

The Staging package sets an immutable `APP_ENVIRONMENT=staging` container
marker in addition to `APP_PHASE_B_RUNTIME=staging`; the runtime gate requires
both. The additive Flyway `V26` ledger stores a request fingerprint and
sanitized completed result for execute idempotency. Reusing a key with a
different target set conflicts; replaying the same key returns the original
result.

The request supports `dry_run=true` (the default) and `dry_run=false`. Manual
test Stores must be passed in `approved_owner_manual_store_ids`; the current
audited allowlist is Store IDs `9` and `12`. Store `1` is always protected.

## Classification contract

The service does not use a Store name by itself:

- A10 fixtures require the `A10_VALIDATION_INACTIVE` status, legacy provenance,
  `BUSINESS` kind and the `A10_VALIDATION_STORE_` code namespace.
- Automated Phase B fixtures require `VALIDATION_FIXTURE`,
  `PHASE_B_OWNER_PROVISIONING` and the `PHASE_B_VALIDATION_STORE_` code
  namespace.
- Owner-manual fixtures require the same Phase B provenance plus an explicit
  approved Store ID from the current allowlist.
- Store 1 (`STG005_SRC_20260809_R01`) is a protected source/reference Store.
- Anything else is `REVIEW_UNSAFE_OR_UNKNOWN` and is rejected.

## Dependency graph and deletion policy

Before any write, the service locks existing target Store rows and checks:

- every `store_id` table is in the reviewed delete or preserve inventory;
- every direct FK to `stores` is covered by the reviewed graph;
- target Stores are not referenced as source Stores by synthetic bootstrap,
  restaurant templates or menu-clone requests;
- target staff has no foreign Organization or Owner membership;
- target inventory is not used by another Store menu or shared prep recipe.

The transactional delete order covers Store-local orders and snapshots,
printing jobs/rules, combo/menu/BOM/inventory rows, stations/tables,
devices/readiness, modules/pricing/mappings, memberships/credentials and the
Store root. Audit logs and onboarding/provisioning/readiness/activation
evidence are preserved. The Store FK on the historical owner provisioning
ledger is detached to `NULL` before root deletion; the ledger retains its
Store code/profile/master snapshots and is never deleted. Chain Master Menu,
Store Profile, Master identity and shared authority tables are not in the
delete set.

The serializable transaction, ordered Store row locks and V26 ledger make
execute replay idempotent: absent target Store rows are reported as
`ALREADY_CLEANED` and do not create or delete new data. A failed preflight,
serialization conflict or failed statement aborts the whole transaction.

## Operator usage

Use only against the isolated Staging release after exact-SHA deployment and
health/readiness validation. First submit a dry run for the explicitly audited
target list. Review that Store 1 is absent from the target list and that all
requested rows are `READY` with no rejected dependency. Only then submit the
same list with `dry_run=false` and a fresh idempotency key. Never call this path
against Production, real Store data, real credentials, Printers, Pads or
devices.

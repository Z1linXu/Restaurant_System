# AL-005 Dependency Repair: Printer Store Isolation

> **DRAFT / SUPPORTING / NOT AUTHORIZED.** See `docs/governance/CURRENT_STATE.yml`.

> Status: `IN_MAIN` via PR #66 at `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3`
>
> Base: `origin/main@8f58bcbfca253c1598b967f4d17c04c0be1cce5b` (PR #65 `IN_MAIN`)
>
> Runtime effect: none until merged and deployed

## Root cause

`OwnerPrintingController.updatePrinter()` authorizes the Store supplied in the
request body. `PrinterConfigServiceImpl.savePrinter()` then loaded an existing
printer by arbitrary ID and overwrote its `store_id` with that authorized Store
without checking the loaded row's Store. A caller with access to Store B could
therefore attempt to move a Store A printer into Store B by ID.

The automatic dispatch path also loaded an assignment's `printer_id` without
rechecking that the printer belonged to the dispatch Store. These are existing
Store-isolation defects and prerequisites for any reusable printing
provisioning module. They do not require a new product decision.

The PAD_DIRECT complete/fail paths also updated printer success/failure health
fields by bare `printer_id`. A dirty historical job reference could therefore
update another Store's printer health metadata even though the job/device Store
boundary itself was enforced.

## Bounded correction

- Reject null/unscoped printer writes in the service boundary.
- When updating an existing printer, require the persisted `store_id` to equal
  the requested `store_id` before copying any mutable fields.
- Leave the persisted entity untouched and perform no save on mismatch.
- Preserve existing same-Store create/update defaults and behavior.
- Reuse the existing Store-scoped printer validation in automatic dispatch so
  a dirty cross-Store assignment fails after the durable job/payload snapshot
  is created but before renderer or transport execution.
- Scope PAD_DIRECT printer-health updates by both `printer_id` and the durable
  job `store_id`; a missing or cross-Store printer skips only the health update
  and does not change the existing complete/fail job terminal-state contract.

## Explicit non-goals

- no new endpoint, DTO, role, capability, or migration;
- no change to `REAL`, `MOCK`, `PAD_DIRECT`, or `DISABLED` semantics;
- no printer assignment persistence, rendering, job-state contract, retry,
  reprint, TCP, Android Worker, or device-pairing change beyond the explicit
  automatic-dispatch Store ownership guard;
- no printer endpoint, credential, token, or Production/Staging data;
- no SSH, Docker, Flyway, print, deployment, or runtime mutation.

## Verification contract

- cross-Store existing-printer update is rejected;
- no repository save occurs on rejection;
- the loaded entity's Store and fields remain unchanged;
- same-Store update still succeeds;
- missing Store scope is rejected;
- automatic dispatch rejects a cross-Store assigned printer and does not render
  or call transport;
- PAD_DIRECT complete/fail never updates printer health through a bare
  cross-Store ID, while the job still reaches its existing terminal state;
- focused printing test, full backend test, compile, `git diff --check`, and
  scope/secret scan pass before review.

## Promotion boundary

PR #66 is independent of the #61-#65 mainline stack and entered `main` at
`f483a4640503c20f6eec1e2e9ae1d198bf23d1f3`. It remains a prerequisite for
any AL-005 executable printer provisioning adapter.
It does not make AL-005 implemented and does not authorize runtime printing.

## Stop state

`AL-005_PRINTER_STORE_ISOLATION_REPAIR_IN_MAIN`

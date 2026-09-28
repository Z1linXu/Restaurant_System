# Production V26 → V28 exact-artifact release — 2026-09-28

Owner explicitly authorized this one release using reviewed/manual deployment
primitives, without changing the V26-only promotion guards. Owner confirmed
Staging validation of `11996ef919d9b28ee5366c7e40a58a78c074b675` and excluded
later main, Uber PR #234 and V29. Result: **PASS**.

## Exact identities

- Previous application: `b4d0350c15cf777ff0ee53e9acb0d4e4127d9b99`.
- New application: `11996ef919d9b28ee5366c7e40a58a78c074b675`.
- Backend: `sha256:603f79e0272a3fe83fdd7ac9bb58b72dc2b1facb3ceef720bc636bf5b9c5469d`.
- Frontend: `sha256:d82650726a7faec62f1270a98bc7e878f0060392d5b6d62fa75ee03bf6e0728f`.
- Both existing immutable images matched the retained accepted Staging runtime
  evidence (`185d6b50326f9687965e587ad7cd5416354d78daf081e7cb6d45e3472ac84573`).
  No rebuild, pull, cherry-pick or replacement artifact.
- Clean source checkout at the target SHA contains migrations through V28 only.
- Production DB container before/after:
  `c2ab37fec6ac966e77a1d8ab7aa41d1da294b9ac57b1a21ce18904d04cfae91e`.
- Fixed bind mount preserved:
  `/home/ubuntu/Restaurant_System/deployment/cloud/data/postgres`.
- Flyway before: successful V1–V26. After: successful V1–V28, previous 26
  entries/checksums unchanged. V29 absent; Uber PR #234 NOT DEPLOYED to Production.

## Backup and execution

Existing `production-backup-rehearsal.sh --backup` used with a digest-bound
RC_PREPARED backup manifest and exact target tooling checkout. Backup completed
before application replacement, non-empty and `pg_restore --list` PASS:

- `/home/ubuntu/Restaurant_System/deployment/cloud/backups/restaurant-pos-predeploy-20260928T154151Z.dump`
- Size: 7,871,659 bytes.
- SHA-256: `9d84590ef9e4eb684f5a4da3bb13fc604a220e15595465e8d1992e8f4e124289`.
- This is verified dump/archive integrity, not a new full restore rehearsal.

Private evidence/configuration directory:
`/home/ubuntu/Restaurant_System/deployment/cloud/backups/v28-owner-release-1z0lrgt0`.
It contains backup result, before/after ledger and sanitized fingerprints,
report, read-smoke result and private resolved target/rollback Compose models.
Compose models contain secrets and remain owner-only; never copy into Git/chat.

Manual release reused the existing Production base Compose and V26 promotion
override to resolve the actual environment/mounts, verified them against running
containers, then froze a release-local model changing only app image IDs and
`FLYWAY_TARGET=28` (build definitions removed). Used the existing operations lock
and `compose up -d --no-deps --no-build --pull never backend nginx`.
No DB container recreation; no Flyway validation bypass/history edit; V27/V28
executed by normal application startup. No permanent promotion helper changes.

The Production control checkout is still `278851f0c4d932a149d598964abca765bb2b5083`;
it is not the application artifact identity. Future operations must use the
recorded exact runtime/config, not blindly run its old V26/base Compose settings.

## Verification and safety

- Backend/system health UP; frontend and Menu/Printing SPA routes HTTP200.
- Existing reviewed `production-v26-smoke.py --mode read`: 21 checks PASS;
  auth/workspace/Owner/context/dashboard/menu/history/staff/printing/printers/
  assignments/display rules/devices/reports, historical detail, DB-authoritative
  Organization scope, foreign Store denial and WebSocket bootstrap.
- Existing Store canonical LIVE. No synthetic Production order or physical print.
- Printer/assignment/device fingerprint unchanged (heartbeat timestamps excluded
  by existing contract); full Store and Store-module fingerprints unchanged.
- Cloud profile/config remained as before, with demo users/data/bootstrap and
  KDS/platform disabled as defined by the target cloud profile. No new feature
  activation, credential mutation, Add-on reconciliation or business-value choice.
- Readiness demonstrated by health plus authenticated API smoke; no separate
  unavailable readiness endpoint was invented.
- Rollback images retained:
  backend `sha256:41d8e2a2743b816fb94af8560f5e0954e1b06c3eb7968e5fa68d7f74d259e754`,
  frontend `sha256:7392658e65d9117bfa1ddebba9ccdbff4a5acf5549a821a34989a3c90860a9d1`.
- No rollback occurred. Compatibility of the old application with migrated V28
  is NOT newly proven; no automatic database restore or downgrade is authorized.
- Migration does not reconcile legacy Add-on business values. Previously
  unresolved names/codes remain unresolved; no manual adaptation SQL executed.

## Staging observation and resource hygiene

Staging was already running `101dbdc75c25904c340251134ee1a0a03f5ce4b7`, Flyway V29,
not the earlier accepted artifact. Health UP, frontend200/auth401. This release
used preserved accepted V28 images, not current Staging. No Staging mutation.
This runtime observation does not claim Uber Sandbox acceptance PASS.

Disk before 72% (~16GB free), after 70% (~18GB free). Build cache before
21.13GB /17.41GB reclaimable, unchanged after deployment. No build or intentional cache cleanup performed;
active/rollback artifacts protected. No system/volume prune or DB-volume action.

Release driver wall-clock: 53.52 seconds (backup/preflight/deploy/startup/smoke).
Sanitized report SHA-256:
`1de4734a1bf474e42e48bef399b422b06e96649fd08f1a9b93b1b07c7a8a35db`.
Total task also includes investigation and final evidence/PR synchronization;
see final Owner report for stage allocation. Docs-only closure does not redeploy.
STOP: release complete. No PAD_DIRECT/Reprint/走洋葱/affinity implementation;
Phase C NOT_STARTED. Production authorization consumed for this exact release.

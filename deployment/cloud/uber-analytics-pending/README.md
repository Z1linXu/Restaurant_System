# Bounded direct Production financial repair

Only the Owner-approved Saint-Denis 2026-11-03 fourteen-target case. Staging/Test
stay paused. The helper imports reviewed deployment primitives; it is not the
normal general-purpose deploy entry point. Run from the clean exact release.
Never print private JSON/environment files. Hold the existing Production lock.

Sequence: local focused/full tests and independent review; immutable commit/JAR
and static build; `prepare <sha>` (quiet/resource/config/runtime gates and fresh
custom backup); verify backup SHA256 and full restore on an isolated local PG;
build immutable images from the protected existing runtime bases using the
verified JAR/static artifacts; `preview <sha>` (read-only). Bind the local restore
preview SHA and backup SHA in `restore-verified.json`; bind tests/review/JAR SHA
in `validation.json`. Only then `deploy <sha>` rechecks all gates, changes only
backend/nginx images, verifies the additive V39.1, appends eight validated journal
records and checks all three Stores. Single-instance replacement may interrupt
HTTP. The six unresolved one-cent differences stay Pending.

Recovery: use the saved `rollback.private.json` and the exact original image IDs
with `docker compose -p cloud -f <private-release>/rollback.private.json up -d
--no-build --pull never --no-deps backend nginx`. Do not restart the DB or restore
Staging. V39.1 is additive and the old Flyway application accepts it as future
schema (verified locally). Never delete journal rows or overwrite v1 snapshots.

Financial compensation is independent: reuse `cli(root, 'revoke', preview_sha)`
from this exact helper/new image to append REVOKE for that committed batch.
Compensation works even if source charges/business status later change. The
helper attempts compensation before application rollback on release failure;
`compensation-failure.json` requires explicit follow-up, never a false rollback
success. All original data, APPLY/REVOKE audit events and backups are retained.
Full backup restore is an emergency separate approval, not normal rollback.

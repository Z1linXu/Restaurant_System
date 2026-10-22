# Five POS fixes: bounded Staging release

This one-shot release uses the existing isolated Staging Compose project and
the reviewed `staging-ingress/apply-once.py` runtime-model helpers. It is not a
second deployment service. The Owner authorized this package's Staging code,
V38 schema, fixture configuration and MOCK acceptance only. Production mutation
and merging `main` are outside this release.

The exact release must be a clean Git checkout at
`/srv/restaurant-pos/staging/releases/<full-sha>`. Push the dedicated reviewed
branch, fetch that exact commit into the Staging bare repository, and add its
release worktree. Do not update the shared Production control checkout.

Run `python3 deployment/cloud/pos-five-fixes/deploy-once.py <full-sha>` from that
release. The helper verifies the existing application image IDs, separate
database network/path/name/overrides, existing operations locks and print queue
before creating private evidence. It makes and checks a fresh custom-format
Staging database backup, then builds both SHA-labelled application images with
a disposable builder capped at 1 GiB and one CPU. It removes only that builder.
No shared image/cache/volume pruning is performed.

The helper preserves the actual Staging runtime environment, HTTPS forwarding,
network addresses, ingress template, mounts and limits. It replaces only
Staging backend/nginx, with `--no-deps --no-build --pull never`. Backend Flyway
must preserve V1–V37 and add exactly V38. Production and the Staging database
container fingerprints, Production migration ledger, old financial/order
option/task/print snapshots and health are checked before and after deployment.

Private evidence is under
`/srv/restaurant-pos/staging/pos-five-release-<full-sha>/` (mode 0700). Do not
publish its runtime Compose files, database dump or build logs. `result.json`
contains sanitized SHA/image/version/backup-hash results. Run the companion
`acceptance.py` only against the fixed public Staging URL, with existing
server-side mode-0600 test credentials; inspect `--help` for its bounded inputs.
Acceptance must identify `CHINATOWN` as a validation fixture and set only that
test Store to MOCK before creating any test order. No external order API or
physical print is part of this package.

If application replacement fails, the helper restores the saved application
models automatically. For a later application rollback, use the private
`rollback.private.json` with the existing Compose project and `--no-deps
--no-build --pull never backend nginx`, then verify health and fingerprints.
Keep the additive V38 tables/nullable references; do not roll Flyway backward.
Database restoration is a separate destructive recovery action requiring
explicit authorization and a stopped/verified Staging target. Never restore
the Staging dump into Production. Keep backups and evidence for the release.

The implementation and actual acceptance results are recorded in
[`docs/POS_FIVE_FIXES_STAGING_20261022.md`](../../../docs/POS_FIVE_FIXES_STAGING_20261022.md).

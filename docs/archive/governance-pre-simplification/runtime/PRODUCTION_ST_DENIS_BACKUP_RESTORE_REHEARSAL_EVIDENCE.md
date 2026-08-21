# Production St-Denis Backup and Restore Rehearsal Evidence

Date: 2026-08-11
Result: `PASS`

## Historical backup audit

The retained historical archive
`restaurant_pos_20260725_033648.dump` passed custom-format integrity inspection
but restored to a V1-through-V6 Flyway ledger. It was therefore correctly
rejected as the current V7 pre-deploy recovery point. The isolated audit had no
live Production effect.

## Fresh pre-deploy backup

| Field | Value |
|---|---|
| File | `restaurant-pos-predeploy-20260811T155951Z.dump` |
| UTC creation identity | `20260811T155951Z` |
| Size | `2329574` bytes |
| SHA-256 | `04c3ef44ccb5dcf1c95841014612c8be1874ad3e396bfd915ea64acfe77865d0` |
| Mode | `0600` |
| Integrity | custom-format `pg_restore --list` PASS |

The archive is in the fixed private Production backup root outside the
destructive Compose database volume path. Creation used the identified running
database container, an atomic temporary file, and a final rename.

## Isolated restore

The fresh archive restored successfully into a disposable PostgreSQL container
with `--network none`, tmpfs database state, resource limits, transactional
restore, and exit-on-error behavior. The restored Flyway ledger was exactly V1
through V7, all successful. No Production port, state root, database, container,
or Flyway history was touched by the rehearsal.

Database restore remains a destructive `TRUE OWNER GATE`; this evidence does
not authorize an automatic Production restore.

# Production Three-Reliability Backup and Restore Evidence

Date: 2026-08-12, America/Toronto
Result: `PASS`

## Fresh pre-deploy backup

| Field | Value |
|---|---|
| RC | `RC-THREE-RELIABILITY-20260812-3EC4D88` |
| File | `restaurant-pos-predeploy-20260812T044411Z.dump` |
| UTC creation identity | `20260812T044411Z` |
| Size | `2429400` bytes |
| SHA-256 | `67cb732e4131532941cc165373ac775b238b8fa68d401cb1a02ab3611cc15aed` |
| Integrity | custom-format `pg_restore --list` PASS |

The archive is in the fixed private Production backup root. The evidence
records only sanitized identity, size and digest; it does not include backup
contents or secrets.

## Isolated restore

The fresh archive restored into a disposable PostgreSQL container using
`--network none`, tmpfs database state, resource limits, transactional restore
and exit-on-error behavior. The restored Flyway ledger matched exact V1 through
V10 with failed rows `0`.

Database restore remains a destructive `TRUE OWNER GATE`. This evidence does
not authorize automatic Production restore, Flyway history edit, downgrade or
volume replacement.

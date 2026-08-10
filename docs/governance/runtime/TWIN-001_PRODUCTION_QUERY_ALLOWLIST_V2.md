# TWIN-001 Production Query Allowlist V2

> Gate: `TWIN-001_RECONSTRUCTION_MANIFEST_COMPLETION_READ_APPROVAL`
>
> Mode: `READ_ONLY`, Store `1`, Organization `1`

One explicit-column aggregate projection captured the manifest-v2 domains:
identity/Flyway; Store/Organization; categories, stations, items and full
option graph; tables; username/role/membership/station mapping; KDS display
configuration; logical printer topology/routing/templates; and safe device
topology. Every subquery has an exact Store predicate where applicable and a
bounded limit (16--512 rows). Organization membership/device queries also have
the exact Organization predicate.

The transaction used `BEGIN READ ONLY`, `statement_timeout=1500ms`,
`lock_timeout=100ms`, and `ON_ERROR_STOP`. It contains no `SELECT *`.

Excluded tables/columns include customers, orders and order history, payments,
`user_credentials`, password fields/hashes, refresh tokens, token hashes,
printer IP addresses/ports, error payloads, raw environment and unrelated
Store/Organization rows.

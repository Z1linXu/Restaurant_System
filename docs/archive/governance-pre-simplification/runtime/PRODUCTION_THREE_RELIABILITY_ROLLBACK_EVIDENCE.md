# Production Three-Reliability Rollback Evidence

Date: 2026-08-12, America/Toronto
Result: `APPLICATION_ROLLBACK_COMPATIBILITY=YES`

## Rollback target

The previous known-good Production application is the exact backend/frontend
image pair for application SHA
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab`:

- backend:
  `sha256:2db920f0929b775aae30271794e903c217f9ba99eb5e889f37ef0c2a4df309a9`
- frontend:
  `sha256:233cc07da7d41143bdc435a8850fb910af0c45490832e0edee57e95a27f4fa8f`

Fresh identity validation before promotion showed that exact pair was already
running on the current V10 Production database with frontend/API/WebSocket
health passing. Therefore application-only rollback to those retained images is
compatible with the current V10 database.

## Allowed rollback shape

If a severe P0/P1 application incident occurs, the approved rollback is:

`backend/frontend -> previous exact image IDs; database remains V10; health and
safe continuity checks`.

Not authorized: database rollback, Production restore, Flyway clean, Flyway
history edit, V10-to-V7 downgrade, volume replacement, printer/device
configuration change or credential action. Any DB restore remains a separate
Owner gate.

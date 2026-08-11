# Production St-Denis V7-to-V10 and Rollback Compatibility Evidence

Date: 2026-08-11
Result: `APPLICATION_ROLLBACK_COMPATIBILITY_GATE=YES`

## Exact forward migration rehearsal

A disposable internal Docker network and tmpfs PostgreSQL clone restored the
fresh Production-shaped V7 backup. Exact candidate backend image
`sha256:2db920f0929b775aae30271794e903c217f9ba99eb5e889f37ef0c2a4df309a9`
then applied only reviewed V8, V9, and V10. JPA/startup/health passed, the Flyway
ledger matched the frozen manifest, and a second startup reported no pending
migration.

## Previous application on V10

Previous Production backend image
`sha256:36daa6697ff7204d88e831315e356241721a956c8513551cf919937cce260792`
was started only against the isolated V10 clone. Health, synthetic isolated
login, `/auth/me`, menu read, and a synthetic isolated order write passed.
Printing was disabled and the network was internal-only, so no printer was
contacted. No Production credential or business record was copied into the
rehearsal.

The exact previous frontend image is
`sha256:781cb93ee4e821a827890f57de58a9f4286371bfc43aef9b4ad8a9507536eca7`.
It was the retained known-good frontend paired with that backend before
promotion and remains present on the Production host. It has no direct database
schema dependency; the V10 compatibility rehearsal therefore exercised the
previous backend, while retention verification covered both immutable images.

## Rollback decision

`ROLLBACK_COMPATIBILITY=YES`. For a severe application incident, the approved
strategy is application-only rollback to the retained previous backend/frontend
images while keeping the Production database at V10, followed by health and
basic smoke. Database downgrade, Flyway history edit, destructive restore, or
volume replacement is not authorized. Any database restore remains a new
Owner gate.

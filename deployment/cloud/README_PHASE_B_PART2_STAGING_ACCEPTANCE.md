# Phase B Part 2 Staging Acceptance

This runbook describes the reviewed, exact-SHA automated acceptance for the
Owner-authorized Phase B Part 2 package. It is subordinate to
`docs/governance/AUTHORITY.md`, `docs/governance/CURRENT_STATE.yml` and the
current Owner authorization. It is not a Production procedure.

The acceptance operates only on an existing inactive Part 1 synthetic Store in
the isolated Staging project. It never creates or activates Chinatown or
Sainte-Catherine, creates real staff credentials, binds a real Printer/Pad, or
mutates Production.

## Runtime boundary

Use the fixed Staging root `/srv/restaurant-pos/staging`, the detached release
at the approved full SHA, and the reviewed `.env.staging`. The runtime must
have Platform and Phase B provisioning enabled, with the bounded printing
pair `STAGING_PRINT_MODE=MOCK` and
`STAGING_PRINTING_FEATURE_ENABLED=true`. The allowlist remains exactly
`DISABLED,MOCK`, endpoint configuration remains disabled, and no endpoint/IP/
token is supplied to the application.

The normal sequence is:

1. exact-SHA release preparation and STG-004 preflight;
2. `staging-deploy.sh` validation/start using the same full SHA;
3. health, Flyway V23 and restart-safety evidence;
4. `staging-phase-b-part2-acceptance.sh --validate`;
5. Owner-approved `--execute-runtime --action phase-b-part2-acceptance`.

The runtime action consumes a private approval artifact and a private secret
file descriptor. It logs only sanitized result markers. Temporary request
bodies, access tokens, one-time synthetic credentials and device tokens stay in
the mode-0700 private workspace and are removed on exit. The synthetic Store
is intentionally left in `ACTIVE`/`MOCK` (`LIVE`) state for Owner manual
acceptance; any later cleanup is a separately reviewed Staging reconciliation.

## Automated matrix

The helper checks, in order:

- exact release SHA, environment/preflight/approval binding, health, Flyway V23
  and fixed Staging project identity;
- initial `NOT_READY`, duplicate printer-module failure rollback and failed
  request ledger;
- replay-safe tables/stations, staff/access memberships, logical printer roles,
  endpoint-free `MOCK`/`DISABLED` topology and no physical printer rows;
- one-time credential delivery, BCrypt persistence and secret-free replay;
- device authentication, heartbeat, broken proof, expired TTL, restored proof
  and Store/Organization isolation;
- missing prerequisite `NOT_READY`, full prerequisite `READY`, fingerprint
  conflict, activation lock/concurrency, idempotent replay and changed-request
  conflict;
- guarded `ACTIVE`/`ACTIVE` (`LIVE`) transition, menu/Profile/Master
  immutability, restart/drift revalidation and sanitized evidence;
- explicit Production and real-hardware untouched assertions.

The repository static check is
`deployment/cloud/tests/test_staging_phase_b_part2_acceptance.sh`. It validates
the shell syntax, required gates and forbidden-operation boundaries without
contacting a runtime.

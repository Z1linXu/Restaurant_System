# Staging Smoke Test Checklist

Use this checklist only after `staging-deploy.sh --validate` passes for the
approved full commit SHA. This is a bounded functional smoke test, not a load
or pressure test. Record the exact SHA, image IDs, operator, start/end time,
and results outside Git.

## A. Isolation preflight

- [ ] The release checkout is `/srv/restaurant-pos/staging/releases/<full-sha>`.
- [ ] `git rev-parse HEAD` matches the approved full SHA.
- [ ] `COMPOSE_PROJECT_NAME=restaurant-pos-staging`.
- [ ] `staging-deploy.sh --validate` passed.
- [ ] Only `127.0.0.1:18080` is published.
- [ ] PostgreSQL has no host port.
- [ ] Staging PostgreSQL data is `/srv/restaurant-pos/staging/state/postgres`.
- [ ] No path, volume, secret, account, cookie, device credential, or data was copied from production.
- [ ] Staging image tags include the exact SHA and are not `:local`.
- [ ] Resource caps and log rotation are configured.

## B. Empty database and Flyway

- [ ] Start from a confirmed empty Staging PostgreSQL data directory.
- [ ] Backend startup applies migrations V1 through the current repository migration.
- [ ] `flyway_schema_history` shows successful entries only.
- [ ] A second backend start validates without reapplying migrations or reporting checksum errors.
- [ ] JPA validation succeeds.
- [ ] No `Flyway clean`, schema-history edit, restore, or `docker compose down -v` was used.

## C. Synthetic identity and Store isolation

- [ ] Only synthetic Organization, Store, Owner, and Staff identities are used.
- [ ] No real password is recorded in commands, Git, logs, or test evidence.
- [ ] Login succeeds for the synthetic role under test.
- [ ] The Store workspace loads for the synthetic Store only.
- [ ] Cross-Store and cross-Organization access is denied.
- [ ] AL-002 onboarding tests use synthetic data and a generated test idempotency key.
- [ ] Same key/same request returns the same synthetic result.
- [ ] Same key/different request returns the documented conflict.
- [ ] Concurrent synthetic Store Code/idempotency behavior is recorded.

## D. POS and weak-network smoke

- [ ] Menu loads with synthetic data.
- [ ] Create a dine-in synthetic order.
- [ ] Create a takeout synthetic order.
- [ ] Add options, combo, notes, and quantity.
- [ ] Verify a persisted local draft restores after a browser refresh.
- [ ] Simulate a bounded network interruption using an approved Staging-only method.
- [ ] Verify queued/idempotent submission produces one server order after recovery.
- [ ] Finish the synthetic table order.
- [ ] Verify another synthetic workspace receives the expected table/order refresh.

## E. Printing boundary

- [ ] Every synthetic Store begins with actual Store `printing_mode=DISABLED`.
- [ ] No printer config includes a real endpoint.
- [ ] `APP_FEATURES_PRINTING=false` is present in the resolved backend configuration.
- [ ] `REAL`, `PAD_DIRECT`, and server-side `MOCK` execution are never selected.
- [ ] Print Job mock execution is recorded as deferred until a reviewed application-level allowlist exists.
- [ ] No physical printer, Android Pad pairing, claim, payload fetch, or native TCP printing is performed.

## F. Android boundary

- [ ] Android validation uses a dedicated test APK/profile or browser session.
- [ ] No production Device ID, Device Token, local storage, IndexedDB, or printer configuration is reused.
- [ ] Bundled/local-preview behavior is recorded separately from production evidence.
- [ ] Android tests do not interact with a real printer or production API.

## G. Restart and resource observation

- [ ] Restart only the Staging project after Owner approval.
- [ ] Verify backend and Nginx recover against the same Staging database.
- [ ] Verify no duplicate order or duplicate Print Job appears after the bounded restart scenario.
- [ ] Observe CPU, memory, disk, and Docker log growth during the smoke window.
- [ ] Stop immediately if shared-host resource headroom is unsafe.

## H. No-go conditions

- [ ] Do not proceed if a production project name, image, path, port, secret, account, or data source appears.
- [ ] Do not proceed if Flyway validation/checksum fails.
- [ ] Do not proceed if a real printer endpoint, `REAL`, or `PAD_DIRECT` appears.
- [ ] Do not proceed if the candidate SHA differs from the recorded SHA.
- [ ] Do not proceed if resource limits/log rotation are absent or host headroom is insufficient.
- [ ] Do not treat a Staging pass as production approval; record the exact candidate SHA and required Owner approval separately.

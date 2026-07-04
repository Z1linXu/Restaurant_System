# Final Cloud-Ready Smoke Test Checklist

Use this checklist before and after the first cloud pilot deployment. It is
copy-paste friendly by design.

## A. Pre-Deploy Local Checks

- [ ] `git status` is clean before creating the release artifact.
- [ ] Backend tests pass.
- [ ] Frontend build passes.
- [ ] Flyway baseline migration exists.
- [ ] Cloud safety guard configuration reviewed.
- [ ] `deployment/cloud` script syntax checks pass.
- [ ] No secrets are present in the repo.
- [ ] Cloud deployment docs reviewed by the operator.

## B. Cloud Environment Checks

- [ ] DNS points to the intended cloud host.
- [ ] HTTPS certificate is issued and installed.
- [ ] Firewall/security group allows only intended public ports.
- [ ] PostgreSQL is not publicly exposed.
- [ ] Required environment variables are set.
- [ ] `JWT_SECRET` is strong and production-only.
- [ ] Database backup path and retention are configured.
- [ ] `docker compose config` validates on the target machine.

## C. Backend Startup Checks

- [ ] `spring.profiles.active=cloud`.
- [ ] Flyway migrate/validate succeeds.
- [ ] JPA DDL mode is `validate`.
- [ ] X-User-Id fallback is disabled.
- [ ] Dev role switcher is disabled.
- [ ] RuntimeDataSeeder cloud-safe flags are active.
- [ ] Startup logs have no production safety failures.
- [ ] Startup logs have no unexpected seed/demo data creation.

## D. Frontend Checks

- [ ] Frontend loads over HTTPS.
- [ ] `/api` reverse proxy works.
- [ ] `/ws` reverse proxy is reachable or fails harmlessly.
- [ ] Login page loads.
- [ ] Access token refresh works without visible user disruption.
- [ ] Browser console has no mixed-content errors.
- [ ] Store workspace routes load after login.

## E. Bootstrap Checks

- [ ] Organization created.
- [ ] Store created.
- [ ] Owner user created.
- [ ] Owner credential created with BCrypt hash.
- [ ] Organization membership created.
- [ ] Store membership created.
- [ ] Owner can log in.
- [ ] Owner can enter store workspace.
- [ ] No local development default credential exists in the cloud database.
- [ ] Bootstrap notes recorded outside the repo.

## F. POS Smoke Test

- [ ] Open Frontdesk.
- [ ] Create dine-in order.
- [ ] Create takeout order.
- [ ] Add noodle item.
- [ ] Add combo item.
- [ ] Add fried item.
- [ ] Add chow mein item.
- [ ] Add combo fried egg.
- [ ] Submit order.
- [ ] Update order.
- [ ] Finish order.
- [ ] Order history shows the submitted order.
- [ ] Order detail opens without checkout/payment controls.

## G. Printing Smoke Test

- [ ] Print Center opens.
- [ ] Set printing mode to `MOCK` for first cloud smoke.
- [ ] GRAB job is created.
- [ ] FRONTDESK_RECEIPT job is created.
- [ ] HOT_KITCHEN job is created for fried item.
- [ ] HOT_KITCHEN job is created for chow mein.
- [ ] HOT_KITCHEN job is created for fried egg.
- [ ] HOT_KITCHEN job is not created for normal noodle without fried egg.
- [ ] Failed print job is visible in Print Center.
- [ ] Print Center reprint works.
- [ ] Order Center reprint works.
- [ ] Cloud direct private-printer attempt is blocked with
  `CLOUD_PRIVATE_PRINTER_BLOCKED`.
- [ ] `DISABLED` mode creates cancelled/disabled print status without breaking
  order submission.

## H. KDS Disabled Check

- [ ] `KDS=false`.
- [ ] `/kds/*` shows Feature Disabled.
- [ ] `/pickup` shows Feature Disabled.
- [ ] `/stores/{storeId}/kds/*` shows Feature Disabled.
- [ ] `/stores/{storeId}/pickup` shows Feature Disabled.
- [ ] Frontdesk/Admin pages do not start `/api/v1/kds/*` polling.
- [ ] Frontdesk/Admin pages do not start `/api/v1/kitchen-tasks/*` polling.
- [ ] HOT_KITCHEN printing still works.

## I. Admin Smoke

- [ ] Print Center opens.
- [ ] Menu Management opens.
- [ ] Dining Tables opens.
- [ ] Staff Management opens if enabled for the role.
- [ ] Audit Logs opens if enabled for the role.
- [ ] Reports pages load.
- [ ] Reports are marked validation/reference only, not accounting-grade.

## J. Backup / Restore

- [ ] `backup-db.sh` creates a timestamped dump.
- [ ] Restore is tested against staging or a temporary database.
- [ ] Backup file location is documented.
- [ ] Rollback runbook is reviewed.
- [ ] Database restore is not attempted directly on production without approval.

## K. Long-Running Pilot

- [ ] Frontdesk remains open for 1 hour.
- [ ] Order page remains open for 30 minutes.
- [ ] Print Center remains open for 30 minutes.
- [ ] No repeated login issue.
- [ ] No excessive polling.
- [ ] No browser memory/CPU runaway.
- [ ] Refresh recovers state.
- [ ] Print failure warnings remain visible and actionable.

## Do Not Go Live If

- [ ] Cloud profile is not used.
- [ ] JWT secret is a placeholder or known development value.
- [ ] X-User-Id fallback is enabled.
- [ ] Flyway is disabled.
- [ ] JPA DDL mode is `update`, `create`, or `create-drop`.
- [ ] No database backup exists.
- [ ] Owner bootstrap is unclear or untested.
- [ ] Printing failures are invisible to staff.
- [ ] Order submit is broken.
- [ ] HTTPS is broken.
- [ ] Print Center mode is accidental or unknown.

## Pilot Mode Acceptable Limitations

- [ ] No payment/refund integration.
- [ ] Reports are not accounting-grade.
- [ ] KDS display pages remain disabled.
- [ ] Android app is not required yet.
- [ ] PAD_DIRECT is future/local-bridge direction.
- [ ] Manual reprint is acceptable.
- [ ] No automatic retry scheduler yet.

## After Go-Live Monitoring

- [ ] Failed print jobs.
- [ ] Backend logs.
- [ ] Database backup success.
- [ ] Login failures.
- [ ] CPU/RAM/disk usage.
- [ ] Order count sanity.
- [ ] Print Center mode changes.
- [ ] Owner feedback.

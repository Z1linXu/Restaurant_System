# Pilot Reliability Batch Runbook

This runbook covers the five-commit field reliability batch on
`codex/pilot-site-reliability-batch`. It is a review and rollout checklist, not
an instruction to deploy directly from an unreviewed branch.

## Before Review

1. Confirm the branch is based on the intended `origin/main` and inspect each
   commit separately.
2. Create a PostgreSQL custom-format backup before applying Flyway V7:
   `pg_dump -Fc`.
3. Confirm `git diff --check`, frontend tests/build, backend tests/compile, and
   Android unit tests/debug assemble pass in the same environment.
4. Confirm the Android Pad APK is installed only on a test device first.

## Pilot Rollout Order

1. Deploy backend code and run the normal Flyway validation/migrate step. V7
   only adds nullable acknowledgement snapshot columns to `print_jobs`.
2. Deploy the frontend build. Verify the Print Center acknowledgement action,
   the local-order banner, and the menu-cache refresh notice.
3. Install the Android debug/release candidate and verify pairing, worker
   health, foreground recovery, and the existing manual/PAD_DIRECT flow.
4. Run one controlled order in `MOCK` or `PAD_DIRECT` before enabling the
   restaurant's normal print assignments.
5. Observe pending, claimed, printed, and failed jobs. Acknowledgement is an
   operator note only and does not mark a job printed.

## Acceptance Checks

- A short backend/network outage moves the Android worker into recovery without
  clearing the user's auto-processing preference.
- A high-risk TCP write/flush or uncertain completion error stops processing
  visibly and does not automatically reprint.
- `LOCAL_DRAFT`, `QUEUED`, and safe validation/conflict records have an explicit
  frontdesk action; `SUBMITTING` remains protected from duplicate submission.
- A menu refresh updates only future selections and leaves existing item and
  option snapshots unchanged.
- Print Center acknowledgement removes only the unchanged attention snapshot;
  a changed status, retry count, or error code is visible again.

## Rollback Order

1. Stop using the new frontend action or revert the frontend deployment if the
   UI is not usable. Existing print-job reprint behavior remains available.
2. Stop the Android auto worker on the pilot device if worker behavior is
   uncertain; use the existing manual/PAD_DIRECT controls and do not auto-retry
   failed or ambiguous physical prints.
3. Roll back backend/frontend application code only after checking whether V7
   has been applied. Do not use `git reset --hard`, delete database volumes, or
   reverse V7 destructively.
4. Keep the V7 columns in place during application rollback. The additive
   columns are harmless to older code; only re-enable the acknowledgement UI
   after the matching backend endpoint is deployed.
5. Restore the database only for a separately approved disaster-recovery event
   after testing the backup in a staging database.

## Explicit Boundaries

This batch does not modify payment/refund, `completeOrder`, KDS, Pickup,
Inventory, Platform Admin, Redis, database order lifecycle, or printer routing.
It does not implement an Android background daemon or automatic reprint.

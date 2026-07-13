# Production Bootstrap Runbook

This runbook explains how to initialize the first real cloud organization,
store, owner, menu, tables, and printing configuration after the cloud database
has been created.

This PR does not implement a bootstrap CLI or API. Treat every step below as an
operational checklist that must be reviewed and rehearsed before production.

## Why Bootstrap Exists

Cloud and pilot profiles intentionally disable unsafe runtime seed behavior:

- `app.seed.default-users-enabled=false`
- `app.seed.demo-data-enabled=false`
- `app.seed.membership-supplement-enabled=false`

That means a new cloud database will not automatically receive local development
accounts, demo menu data, demo tables, or demo printer assignments. This is
intentional. Production data must be explicitly created, reviewed, and owned by
the restaurant.

Do not re-enable demo seed flags to make production login work. That can pollute
real menu, table, printer, and membership data.

## Bootstrap Principles

- Back up first, even if the database is expected to be empty.
- Run bootstrap only during a planned initialization window.
- Rehearse every SQL or import step against staging or a temporary database.
- Generate the owner password with a secure password manager.
- Store only a BCrypt password hash in `user_credentials`.
- Never commit or log the plaintext owner password.
- Give the plaintext password to the owner only once through a secure channel.
- Record the operator, time, database name, environment, and script/checklist
  version used.
- After initialization, verify that no bootstrap-only switch remains enabled.
- Do not use `force-overwrite` or demo seed flags to repair production data.

## Data To Initialize

Minimum required data:

- `organizations`: real restaurant group / owner organization.
- `stores`: first real restaurant store.
- `roles`: safe metadata should already exist through cloud-safe seeding.
- `users`: first owner user.
- `user_credentials`: login identifier and BCrypt password hash.
- `organization_memberships`: owner membership for the organization.
- `store_memberships`: owner membership for the first store.
- Feature configuration: confirm Print Center and KDS mode decisions.
- Menu categories/items/options: import from validated pilot data or maintain
  through Owner Admin.
- Dining tables: import from validated pilot data or maintain through Owner
  Admin.
- Printer configs/assignments: configure intentionally after choosing print
  mode.
- Analytics: starts empty and fills as real orders are created.

## Recommended First Bootstrap Flow

1. Prepare a staging or temporary database.
2. Restore or migrate schema with Flyway.
3. Generate a strong owner password.
4. Generate a BCrypt hash for that password using a trusted local tool.
5. Fill a reviewed bootstrap SQL script from `bootstrap-template.sql.example`.
6. Run the bootstrap SQL against staging first.
7. Start the backend with the `cloud` profile against staging.
8. Log in as the owner and confirm `/owner/dashboard` and the first store
   workspace load.
9. Repeat against production only after staging verification.
10. Store the bootstrap audit note outside the repo.

## Owner Password Handling

- Generate a unique strong password.
- Generate a BCrypt hash offline.
- Put only the BCrypt hash into the database.
- Do not paste the plaintext password into SQL files, shell history, issue
  trackers, chat logs, or commit messages.
- Hand the plaintext password to the owner once.
- After future password-change support exists, require the owner to change it
  after first login.

## Menu, Tables, And Printing

For the first store, choose one initialization path:

- Import a reviewed dump from the Windows pilot or local rehearsal database.
- Manually maintain menu, options, tables, and printers in Owner Admin.
- Use a future reviewed import wizard or onboarding UI.

Do not use RuntimeDataSeeder demo menu/table/printer insertion in cloud.

Printing guidance:

- Use `MOCK` for dry-run validation.
- Use `DISABLED` if the restaurant is not ready to print.
- Use `PAD_DIRECT` or a local print bridge for real store printers.
- The cloud backend must not use `REAL` to directly connect to private LAN
  printers.
- Printer private addresses may be stored for future `PAD_DIRECT` use, but cloud
  backend direct transport should remain blocked by the cloud printing guard.

## Backup And Rollback Notes

Before bootstrap:

```bash
./backup-db.sh
```

If bootstrap goes wrong:

1. Stop traffic if the system is live.
2. Prefer restoring the pre-bootstrap backup.
3. Rehearse restore against staging or a temporary database.
4. Do not turn demo seed flags back on.
5. Do not use force overwrite to repair real restaurant data.

## Future Work

- One-time production bootstrap CLI or admin command.
- Platform onboarding UI for organizations and first stores.
- Owner password change and reset flow.
- Menu/table/printer import wizard.
- Audit log event for production bootstrap completion.

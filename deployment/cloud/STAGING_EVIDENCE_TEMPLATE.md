# Staging Evidence Template

Status: `STG-006_PREPARATION_ONLY_BLOCKED_ON_STG-003_STG-005`

Use this private template only after an Owner approves a future Staging
operation. The template records evidence; it does not authorize execution.

```text
UTC timestamp:
Owner approval reference:
Approved full Git SHA:
Staging project: restaurant-pos-staging
Staging root: /srv/restaurant-pos/staging
Printing mode: DISABLED

Validation result:
Container inventory result:
Disk thresholds approved by Owner:
Disk check result:
Backend image ID (shortened):
Frontend image ID (shortened):
Flyway evidence reference:
Loopback health evidence reference:

Backup metadata result:
Restore rehearsal status: REHEARSAL_NOT_EXECUTED_WAITING_FOR_OWNER_APPROVAL
Static rollback comparison result:
Runtime schema compatibility evidence:

Synthetic fixture status: BLOCKED_ON_STG-005_FIXTURE_CONTRACT
Open risks:
```

Do not include secrets, complete environment files, tokens, resolved Compose
output, customer data, raw print data, or real printer endpoint details.
Backup inventory records only a SHA-256 of each filename string plus filesystem
size and modification metadata. It does not record raw filenames or hash backup
contents.

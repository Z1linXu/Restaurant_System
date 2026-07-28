# STG-004 Same-Host Preflight Evidence Template

Status: `TEMPLATE_ONLY_NOT_EXECUTED`.

This template records a future Owner-approved, read-only same-host Staging
preflight. It is not evidence that a server action, deployment, container
build, Flyway migration, or test has occurred.

## Identity

| Field | Value |
|---|---|
| Evidence timestamp (UTC) | `<not recorded>` |
| Approved full SHA | `<not recorded>` |
| Staging project | `restaurant-pos-staging` |
| Staging root | `/srv/restaurant-pos/staging` |
| Explicit production project/root | `<owner-provided, redact private path in shared reports>` |
| Operator/approval reference | `<not recorded>` |

## Sanitized preflight summary

Paste only `CHECK|...` and `SUMMARY|...` lines. Do not paste `.env` values,
resolved Compose configuration, image IDs, database names/users, secrets,
container environment, printer endpoints, customer data, or tokens.

```text
<not executed>
```

## Result classification

| Check | Result | Evidence classification | Notes |
|---|---|---|---|
| Exact clean approved release | `<not recorded>` | `EVIDENCE_PENDING` | |
| Independent root/database path | `<not recorded>` | `EVIDENCE_PENDING` | |
| Loopback port `127.0.0.1:18080` | `<not recorded>` | `EVIDENCE_PENDING` | |
| Owner-supplied disk threshold | `<not recorded>` | `EVIDENCE_PENDING` | |
| CPU/memory metadata | `<not recorded>` | `EVIDENCE_PENDING` | |
| Printing disabled/no endpoint | `<not recorded>` | `EVIDENCE_PENDING` | |
| SHA-specific images | `<not recorded>` | `EVIDENCE_PENDING` | Pre-build absence is `PENDING_PREBUILD`, not a health result. |
| Existing Staging containers | `<not recorded>` | `NOT_APPLICABLE` | Expected absent before first approved start. |

## Approval gates

- [ ] Owner approved exact SHA and same-host resource window.
- [ ] Owner reviewed a `SUMMARY|PASS` preflight result.
- [ ] Owner separately approved any `--execute-start` action.
- [ ] Post-start health/Flyway evidence is captured in a new report.

## Non-actions confirmed

- No production deployment, production database access, or production config
  change is represented by this template.
- No Docker build/up/pull/start/stop/down command is represented by this
  template.
- No Flyway migrate/clean/repair, restore, backup, real printer, or PAD_DIRECT
  operation is represented by this template.

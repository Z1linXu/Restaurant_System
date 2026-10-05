# Production V37 release — 2026-10-05

Restaurant_System Production application release = COMPLETE.
Production domain: https://pos.lanzhounoodlesmtl.com.
Uber Production: NOT ACTIVATED.

Server deployment/automated acceptance passed. New-domain password login and
physical Production Pad URL migration remain manual observations pending; an
in-memory operational smoke token does **not** prove a password login. The
Owner has been asked to log in directly, without sending credentials to chat.

## Authority and immutable artifacts

Owner explicitly authorized backup, application release, real sequential Flyway
V29–V37, dedicated Production TLS, safe Production MOCK order and rollback on
failure. All three Uber capabilities and all real Uber bindings must remain off.
The dirty original Owner workspace was left untouched. Clean worktree used:
`Restaurant_System_production_20261005`; starting main `88a2d0bd12b4d82bc9e0cd02dc15cb578bffd3ef`.

Independent Agent 6 review found one bounded release blocker: daily Finish only
selected today's submissions. PR [#270](https://github.com/Z1linXu/Restaurant_System/pull/270)
fixed prior-day still-open dine-in selection, preserved locking/idempotency,
Store isolation, future-date exclusion and payment/submission fields. All20
focused tests passed, including8 real PostgreSQL integration tests. Agent6
accepted code and the deployment/rollback/HTTPS scripts after corrections.
Repository checks reported no required GitHub CI checks; no checks were bypassed.

| Component | Source | Immutable image |
|---|---|---|
| Production backend | `758dc5b2111db1b298b6104fac81c07f8aee0e12` | `sha256:8399cd393c532b4fd66fa7a50cfa5f30106b156eed0a3fb0fd840ec11db9133b` |
| Production frontend | `8c3a9e356f59cd025beff25f7fb1d5a20594e028` | `sha256:4bf301d4d547cc017be91c1e6d7c0d859cca345d4aed4db1696e32000e7048fc` |
| Previous Production backend | `11996ef919d9b28ee5366c7e40a58a78c074b675` | `sha256:603f79e0272a3fe83fdd7ac9bb58b72dc2b1facb3ceef720bc636bf5b9c5469d` |
| Previous Production frontend | `11996ef919d9b28ee5366c7e40a58a78c074b675` | `sha256:d82650726a7faec62f1270a98bc7e878f0060392d5b6d62fa75ee03bf6e0728f` |

New backend was built from exact merged source, then first deployed/health checked
in Staging. Runtime image/env/IP, Stage DB/frontend and old Production continuity
passed before Production promotion. No frontend rebuild was needed. Production
completed at **2026-10-05T05:24:38Z**. Current Staging uses the same backend and
its existing frontend; both HTTPS hostnames remain healthy.

## Backup and migrations

- Backup directory: `/home/ubuntu/Restaurant_System/deployment/cloud/backups/v37-owner-release-20261005T051347Z`.
- File: `production-v28.dump`; UTC timestamp `2026-10-05T05:13:47Z`.
- Size: **8,746,739 bytes**.
- SHA256: `e1be3818da6894eff484944cf452a8b36a229f5376cce33913719d7055b2b237`.
- Custom-format archive integrity checked with `pg_restore --list` in a
  network-disabled disposable container; original DB container unchanged.
- Normal backend startup executed all9 migrations:29,30,31,32,33,34,35,36,37.
  Each recorded success=true. No Flyway repair, skipped migration or history edit.
- Production resolved runtime uses `FLYWAY_TARGET=latest` and disables Uber.
- 32 retained-data fingerprints passed: Store/Organization, users/credentials,
  roles/memberships, tables, menu/items/options/BOM/addons/combo, printers and
  assignments, stable device identity/token hashes, existing modules, completed/
  cancelled order history and item snapshots, existing audit and report rows.
  New additive columns/rows and device heartbeat timestamps were excluded from
  old-row comparison. No Staging data was copied. No printer pairing was changed.

## HTTPS and smoke evidence

| Check | Result / limit |
|---|---|
| Frontend and API HTTPS | PASS; health200, all53 public assets match exact image bytes |
| HTTP canonical redirect | PASS308 to Production HTTPS |
| TLS | PASS trusted chain/hostname; dedicated certificate expires2027-01-03 |
| WSS | PASS101, STOMP CONNECTED,40 seconds and5 ping/pong exchanges |
| Origins | Production/Android200; foreign403; forged forwarding via old IP403 |
| Staging HTTPS | PASS; separate cert/block/relay retained |
| Authentication / Store switch API | PASS existing Owner identity, workspaces and all3 Store contexts; forbidden Store403 |
| Password login / new-origin browser session | NOT YET OBSERVED; login page renders correctly; user login requested |
| Frontdesk / dine-in / takeout dependencies | PASS tables/menu/history and actual pickup submit; browser new-origin session pending |
| Dashboard / Reports | PASS18 checks:3 stores × today/week/month × dashboard/reports |
| Sales trend/noodle/category data | PASS API fields present; all Uber revenue/count0; total equals in-store |
| KDS / pickup KDS board | Existing KDS module OFF in all3 stores,403 correctly retained; not claimed as an enabled board |
| Printing settings/devices/assignments | PASS all3 stores; existing configurations unchanged |
| Production physical Pad | NOT TESTED; native settings migration requested, no USB/physical confirmation this batch |

A single explicitly authorized Production order **#8359** in **Store2 (Chinatown)**
used existing MOCK mode and a menu item with exact observed stable size/noodle
options. Guard verified no inventory items in this Store. Submit passed;
1 kitchen task,1 production task,2 outbox events and2 MOCK PRINTED jobs were
observed. The order was then cancelled through the normal API, retained in
history and excluded from sales. No real paper, Store1 print dispatch or new
Uber order was used. This is software/queue evidence, not physical-print proof.

## Daily Finish and Uber state

- 23:30 Store local time; all3 resolve America/Toronto.
- All still-open submitted/preparing/ready IN_STORE dine-in tables are eligible,
  including older dates. Existing Finish domain performs the transition.
- Takeout, Uber/external, cancelled, completed, draft and future submissions are
  excluded. No fabricated payment or changed submitted timestamp. Durable ledger
  prevents duplicate batch execution. DST/catch-up/concurrency tests passed.
- No real wait until23:30 was required or claimed.
- Store1/St-Denis: UBER_EATS=false.
- Store3/St-Catherine: UBER_EATS=false.
- Store2/Chinatown: UBER_EATS=false.
- Uber bindings total=0, active=0. No Production Uber credentials, API calls,
  webhook Dashboard changes, menus or Order Manager changes.
- Future endpoint only:
  `https://pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook`.

## Operations and recovery

Actual canonical private Compose is
`/srv/restaurant-pos/production/current.compose.json`; do not run the historical
control checkout's generic deploy script over the shared HTTPS template. See
[current operations](../../deployment/cloud/README_PRODUCTION_V37_RELEASE.md).
Dedicated Production renewal timer and original Staging renewal timer are active.
Production `certbot renew --dry-run --cert-name pos.lanzhounoodlesmtl.com` passed.
Private release directory contains sanitized result files,
private runtime/config baselines, checksums, exact target and rollback models.

V37-compatible fallback image/model is retained and Compose-validated, not run.
It uses the preceding accepted Staging backend and current HTTPS/UBER-off config,
with the known temporary limitation that prior-day daily Finish correction is
absent. The original V28 images also remain; against V37 they have a known module
administration incompatibility with UBER_EATS. No full DB restore rehearsal or
physical rollback was claimed. No destructive database rollback was performed.

Disk after release:68%,19GiB free. Build cache16.07GB reviewed; no prune, deletion
or volume cleanup was needed. Existing images remain available for recovery.

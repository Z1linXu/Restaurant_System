# Production V10 → V26 Autonomous Release Evidence

Date: 2026-08-24 (America/Toronto)

Result: `PRODUCTION_V10_TO_V26_RELEASE_PASS`

Final status markers:

- `PRODUCTION_V10_TO_TARGET_MIGRATION_REHEARSAL = PASS`
- `PRODUCTION_SHAPED_READ_SMOKE = PASS`
- `PRODUCTION_SHAPED_WRITE_SMOKE = PASS`
- `ANDROID_PAD_COMPATIBILITY = PASS`
- `PRODUCTION_DIRECT_UPGRADE_READY = YES`
- `PRODUCTION_DEPLOYMENT = PASS`
- `PRODUCTION_POST_DEPLOY_SMOKE = PASS`

This evidence records the bounded Owner-authorized autonomous release from the
retained Production V10 runtime to the exact immutable application artifact
previously accepted on isolated Staging at V26. It does not close Phase B Part
2 Owner manual acceptance and does not authorize Phase C.

## Frozen release identity

| Field | Evidence |
| --- | --- |
| Frozen RC | `RC-PROD-V26-20260824-AE446-R7` |
| Frozen RC SHA-256 | `72ebaeacb8d21f33d95fcf2e9bccf337dcc10123e6408556fbb1254d2abdea8c` |
| Accepted application SHA | `ae446874e6a6bc7d2c19cdbc1ca92603ed53d6de` |
| Production application before | `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9` |
| Exact release-tooling main | `9e4d13ba8d210585518cbdb81bb14cf41be51c27` |
| Retained Production control checkout | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` |
| Target backend image | `sha256:d4e20d682b12fc7f0055eb7e259972af4bd118989cf54109272c3217c3ad8a77` |
| Target frontend image | `sha256:2071601ba5064e323dad1723cf210dac14b079fdabb717bc2e3b6ebcc6ccbfb9` |
| PostgreSQL image | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` |
| PostgreSQL container | `c2ab37fec6ac966e77a1d8ab7aa41d1da294b9ac57b1a21ce18904d04cfae91e`, unchanged |
| Flyway | V10 before; exact V26 after |
| Resolved Compose SHA-256 | `08d3958ff03c6c3370b4df356d835e5ad5e5f56046d78dea058116fc3f870616` |

The backend and frontend were promoted by immutable image ID with
`--no-build --pull never`. No application image was rebuilt for Production.

## Fresh backup and isolated restore

| Check | Result |
| --- | --- |
| Backup timestamp | `2026-08-24T08:36:01Z` |
| Private backup file | `restaurant-pos-predeploy-20260824T083601Z.dump` |
| Size | 3,749,102 bytes |
| SHA-256 | `026a954dbbfced7c8b0fce79935c5526dd38e5b7a7516cc34bd76ab3e58dcb92` |
| Custom-format integrity | PASS (`pg_restore --list`) |
| Independent tmpfs restore | PASS |
| Restored Flyway baseline | exact V10 |
| Production DB mutation during backup/restore | NONE |

The final R7 rehearsal used a run-owned internal Docker network, PostgreSQL
container, volume, backend and frontend. It published no test service to the
public network and used only the clone for synthetic writes.

## Automated release gates

| Gate | Result | Proof |
| --- | --- | --- |
| `PRODUCTION_BACKUP` | PASS | fresh private backup and digest above |
| `PRODUCTION_BACKUP_RESTORE` | PASS | isolated tmpfs exact-V10 restore |
| `V10_TO_TARGET_MIGRATION` | PASS | V11–V26, 16 migrations, exact ledger/checksums |
| `TARGET_APP_BOOT` | PASS | target backend/frontend plus same-image restart |
| `PRODUCTION_DATA_INTEGRITY` | PASS | business and printing fingerprints unchanged |
| `READ_SMOKE` | PASS | 21 authenticated checks, historical detail and WebSocket |
| `WRITE_SMOKE` | PASS | clone-only order/update/printing/inventory/replay flow |
| `ANDROID_PAD_COMPATIBILITY` | PASS | current app contract compatible; no APK update |
| `STORE_ORGANIZATION_ISOLATION` | PASS | canonical DB authority and unavailable Store rejection |
| `RECOVERY_PROOF` | PASS | failed restore preserved primary; validated V10 DB switch and old app smoke |
| `STAGING_ACCEPTED_ARTIFACT` | VERIFIED | exact accepted image IDs remained on Staging |
| `AGENT_6_RELEASE_REVIEW` | ACCEPT | tooling repairs and R7 runtime evidence reviewed |
| `PRODUCTION_PREFLIGHT` | PASS | frozen-RC exact-artifact preflight immediately before mutation |

`PRODUCTION_DIRECT_UPGRADE_READY = YES` before promotion.

## Migration and data-integrity result

- The clone migrated through all 16 additive migrations from V10 to V26 using
  normal backend Flyway startup. No migration was skipped, repaired or edited.
- The canonical Production-clone business fingerprint remained
  `c8f2ce3fa1335829ee975789037261c03e45b45cc904a545e2859550603c6c1b`.
- The printing-topology fingerprint remained
  `c3033a6a6f9ce0475d6998bbcad9f6968f4956e42f37541b8173ae47fec2ec6b`.
- The additive contract covered Organizations, Stores, memberships,
  users/staff, tables, menu/categories/items/options/combos, pricing, orders,
  printing configuration/assignments/rules/jobs, devices and migration-added
  authority relationships. It reported `violations=0`.
- The final Production migration reproduced both fingerprints exactly and
  retained the same PostgreSQL container and fixed data root.

## Production-shaped smoke and compatibility

The R7 read smoke passed health, authentication, Owner workspace, Store
context, Admin, Frontdesk, tables, menu/options/combo/pricing, historical
orders/details, staff/memberships, Printing Management/configuration,
assignments, display rules, devices, reports, WebSocket and authorization.

Clone-only write smoke created synthetic order `3641` and proved:

- four KitchenTasks, one update batch and four outbox events;
- normal options plus three Combo option snapshots;
- one MOCK printed job with no physical endpoint;
- submit/update/replay inventory transaction counts `2 → 3 → 3` against one
  isolated audited inventory/BOM fixture;
- enabled/disabled printing-role behavior, Store isolation and idempotent
  replay.

No synthetic row escaped the disposable clone.

`OLD_PRODUCTION_APP_ON_V26_SCHEMA = PASS` in the isolated rehearsal. The
stronger recovery proof nevertheless restored the fresh V10 backup into a
separate validated database, proved an invalid restore could not alter the V26
primary, switched database names, booted the retained V10 backend/frontend and
passed legacy authenticated read smoke without manual SQL.

`ANDROID_APP_UPDATE_REQUIRED = NO`. The installed Production Pad identity
remains `versionCode=2` / `0.2.0-offline-pr7`; WebView entry, auth/token,
device registration, PAD_DIRECT claim/start/complete/fail/release, printing
paths, request/response DTOs and required headers are unchanged. Heartbeat
persistence is additive and V26 adds no minimum-version guard. No APK or real
Pad was changed.

## Bounded repair loop

Every failed rehearsal remained fail-closed, retained distinct immutable
evidence and ended with zero run-owned containers, networks and volumes. No
failed attempt mutated Production.

| PR | Merge SHA | Repair |
| --- | --- | --- |
| #212 | `11955d96455845ff1a9b33a1580a195c38784560` | Added the same-artifact V10→V26 rehearsal, strict RC, evidence and recovery tooling. |
| #213 | `95386ee2f9804d8fd2addf48b21a4574abf74e59` | Restricted the Production control-checkout allowlist to known fixed runtime paths. |
| #214 | `763e1103fcbc04f1d0b25077deb61b544d4cf113` | Removed rehearsal host-port dependence and used the exact private address on the internal network. |
| #215 | `66e14d4a76f76f4226b40ae2c6ae0e20455e19c2` | Waited for stable PostgreSQL post-entrypoint readiness before restore. |
| #216 | `242c59163e5d0a92ed1c3c16ea8f31b8ca9e06f2` | Corrected smoke to the canonical `/api/v1/admin/platform/stations` route. |
| #217 | `ccfbcb7b8908d13a86d5047f0f7f530aaa7fa155` | Tested canonical database Organization authority instead of treating the token claim as a second authority source. |
| #218 | `0cce39559be043bd3669b7346860cdaaa0b6547b` | Added a guarded clone-only inventory/BOM fixture and exact submit/update/replay deduction assertions. |
| #219 | `9e4d13ba8d210585518cbdb81bb14cf41be51c27` | Passed recovery DB-name SQL over psql stdin, proved absence fail-closed, and removed negative-test false passes. |

Observed fail-closed causes were: obsolete host-port smoke, transient
PostgreSQL initialization readiness, one incorrectly bound Flyway-manifest
digest, a nonexistent stations route, an invalid Organization-claim test
assumption, absent Production inventory/BOM configuration and psql `-c`
variable-substitution semantics. Agent 6 blocked two iterations of the final
recovery test repair until tri-state absence and explicit negative assertions
were correct, then accepted it. Agent 6 separately accepted the complete R7
runtime evidence.

No application code or image changed in this repair chain. Staging therefore
required no rebuild or redeploy; the accepted application artifact and Flyway
V26 runtime remained unchanged.

## Promotion and post-deploy verification

The private promotion evidence records:

- private target authenticated read smoke: PASS;
- automatic database-restore authority closed before the public edge: PASS;
- public-edge authenticated read smoke: PASS;
- final marker:
  `PROMOTION|action=execute|rc_id=RC-PROD-V26-20260824-AE446-R7|source_sha=ae446874e6a6bc7d2c19cdbc1ca92603ed53d6de|flyway=V26-exact|db_container_unchanged=true|result=PASS`.

Immediate independent post-deploy verification confirmed:

- backend, frontend and PostgreSQL running; restart counts `0`;
- PostgreSQL healthy and container ID unchanged;
- exact 26-row Flyway ledger/checksums and no pending migration after restart;
- private backend health `UP`, public frontend and `/ws/info` HTTP 200;
- a second authenticated 21-check read smoke PASS;
- business and printing fingerprints unchanged; additive violations `0`;
- temporary recovery database names, rehearsal containers/network/volume and
  private probes all absent;
- Staging still runs the accepted backend/frontend image IDs and exact
  application SHA.

## Evidence and process hygiene

| Artifact | SHA-256 |
| --- | --- |
| Frozen R7 manifest | `72ebaeacb8d21f33d95fcf2e9bccf337dcc10123e6408556fbb1254d2abdea8c` |
| R7 rehearsal log | `d04f97efbcb3b22dcbfbed94d65c4169d57b3fe01411566119c151aac7d31cc5` |
| R7 promotion log | `75e9ae1248bee5258e017f5952941a01e01667ead066a666c11831ef3dce00f0` |
| Exact tooling bundle | `68a477398f6910e5b7dffced0240ae9b096590481a9bd81ca9af70b89ee8edd0` |

The private server artifacts are mode 0600. Evidence contains no password,
JWT, token, endpoint, customer/order payload or credential. Failed evidence
from the earlier attempts was not rewritten or spliced into R7.

Final process checks found zero active backup/rehearsal/promotion/recovery
helpers, zero run-owned Docker resources and no older non-interactive SSH
session from this run; only the current bounded audit connection existed and
ended with its command. R7 reported `children=0`. No unbounded command, prune,
tunnel or background process was used. The final fresh backup is retained under
the reviewed backup policy.

## Mutation boundary and remaining gates

Authorized Production mutations were limited to backend/frontend container
replacement/restart and normal additive Flyway V11–V26 migration. The
PostgreSQL container/state root and pre-existing canonical business/printing
fingerprints were preserved. Reviewed additive migration DML/backfills created
or initialized the Store-scoped pricing, modules, Combo configuration, printing
rules, lifecycle and other V11–V26 contract rows validated by the additive
invariants. No out-of-contract real operational Store/menu/staff edit,
credential change, Printer endpoint/binding or Pad/device binding occurred. No
physical print or APK deployment occurred. Staging mutation was `NONE`.

Real Printer/Pad field behavior was not exercised by this release; compatibility
was established by unchanged contracts and fingerprints. Phase B Part 2 Owner
manual acceptance remains pending. Phase C remains unauthorized.

Stop marker:
`PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE_PASS_WAITING_FOR_OWNER_MANUAL_ACCEPTANCE`.

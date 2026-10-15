# Phase 2 Combined Production source baseline

The machine-readable [manifest](phase2-baseline.json) records separately observed
backend `ce35278b3569a4eacfa29efc01cadbf0c409bec6` and frontend
`8c3a9e356f59cd025beff25f7fb1d5a20594e028`, image IDs, OCI revisions, source trees
and all 37 Flyway checksums. Latest read-only identity observation:
2026-10-15 17:30:03 UTC, unchanged restart counts (zero). The source of the runtime
observation is the [Phase 0 baseline](01_PRODUCTION_BASELINE_AUDIT.md), with sanitized
local recheck `/private/tmp/restaurant-phase0-20261015/phase2-runtime-provenance.json`.
No business data, secret or environment file was copied into this manifest.

Backend B descends from frontend F. Fresh main `06bac51` differs from B only in
documentation/current state and deploy-helper environment ordering. The backend,
frontend and Android source trees are identical at the relevant sources and main.
The Commercial line therefore starts from main without overlaying source files.
`python3 scripts/verify-commercial-baseline.py` verifies source identities and
applied migration immutability; later Commercial source trees intentionally diverge.

| Axis | Evidence / result |
| --- | --- |
| Source lineage | SOURCE_LINEAGE_CONFIRMED: Git trees, image OCI labels and V1–V37 checksums agree |
| Clean local build | Running from separate B/F archives; results and artifact hashes are recorded below after completion |
| Historical bit equality | PRODUCTION_BIT_REPRO_UNVERIFIED: floating Docker base tags, historical build parameters/toolchain and image SBOM not fully captured |
| Installed Android | APK_UNKNOWN / HARDWARE_QA_PENDING: source package `com.restaurant.pad`, versionCode 4 / `0.4.0-kitchen-field-fixes` do not prove fleet installation, signing, assets or paper results |

Build contracts: backend Dockerfile uses Maven 3.9 / Temurin 17 and a Temurin 17 JRE;
frontend uses Node 22 Alpine, committed npm lockfile, `VITE_APP_BUILD_VERSION` build
argument and nginx 1.27 Alpine. Local builds use Java 17 and Node 20.19.6 / npm 10.8.2,
and are new local artifacts, not claimed copies of the historical images. The API
and schema compatibility baseline is the jointly observed B/F/V37 combination; no
broader APK or multi-version compatibility promise has been verified.

Configuration contracts are `application-cloud.yml`, cloud Compose/nginx and
environment-specific runbooks. Only names/behavior are recorded: datasource
`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`, `JWT_SECRET`, `APP_ENVIRONMENT`,
Flyway target, auth fallback, seed flags, feature ceilings, allowed print modes,
endpoint-write permission and Uber flags/credentials. Values are private deployment
inputs. Observed production is cloud, auth fallback disabled, forwarded headers
NATIVE, Uber disabled; Staging differs and is not a Production-equivalence proof.

Historical bit equality and installed hardware unknowns are
NON_BLOCKING_LOCAL_DEVELOPMENT_GAP after clean source builds pass; they block release
and customer compatibility claims. Any unexplained source mismatch, checksum drift,
failed local build or unsafe test target is a BLOCKING_BASELINE_GAP and blocks Phase 3.
No failed test may be downgraded to an unknown. Current phase/gate stays solely in
[CURRENT_STATE](../governance/CURRENT_STATE.yml).

## Clean build result — 2026-10-15

`mvn -o clean verify` (Java 17.0.14, Maven 3.9.11): 951 tests, 0 failures, 0 errors, 71 skipped (optional local PostgreSQL suites, rerun at integration gate). JAR SHA256 `9f25b66109fa154ee4da9848edeb44fd76ccdcd9df7f165ec8701176e29253d6`.

`npm ci && npm test && npm run build`: 48 files / 267 tests PASS; TypeScript and Vite PASS. Dist aggregate SHA256 `2ffeed49a5e7fbd4994975896784a8315028f5f04908cf8b3d6803ab2bffe199` (sorted relative path + NUL + bytes). LOCAL_BUILD_VERIFIED.

The initial backend archive omitted the reviewed deployment fixture consumed by StagingPadDirectPolicyTest: one FileNotFound error. Retried after adding the deployment tree from the **same B commit**; all tests passed. No code was changed to make the baseline pass. Logs are in `/private/tmp/restaurant-commercial-baseline/`. Mockito uses the already installed Byte Buddy agent through JAVA_TOOL_OPTIONS because this macOS runtime denies self-attach.

# Phase A5 St-Denis Canonical Profile Staging Evidence

Status: `PHASE_A5_ST_DENIS_CANONICAL_PROFILE_COMPLETE_WAITING_FOR_PHASE_A6_OWNER_CONTINUATION`

Date: 2026-08-13

Fresh main and deployed Staging SHA:

```text
origin/main = 3440fddad7571409c66189e44976658921e5de1f
staging_deployed_sha = 3440fddad7571409c66189e44976658921e5de1f
staging_flyway = V15
```

Runtime boundaries retained:

```text
Production mutation = NONE
Production DB read = NONE
Staging reset = NONE
Flyway history edit = NONE
new migration after V15 = NONE
Store materialization = NONE
A6 / Phase B / Phase C / Chinatown / Sainte-Catherine = NOT_STARTED
```

Staging release evidence:

```text
release_env_rotation = PASS
env_sha256 = 23330c12681a21b135184b40787afd52636a413a0daefed2a1654650301e69d2
preflight = PASS
preflight_evidence = /srv/restaurant-pos/staging/evidence/phase-a5-jdbc-char-preflight-3440fddad7571409c66189e44976658921e5de1f.txt
preflight_sha256 = 0053f03d473bc1c1afabfede5014e758dc3791312d083b13ee448ee487337baf
staging_health = PASS
staging_health_http = 200
staging_ws_info_http = 200
```

Staging retained environment identity:

```text
STAGING_COMMIT_SHA = 3440fddad7571409c66189e44976658921e5de1f
VITE_APP_BUILD_VERSION = staging-3440fddad7571409c66189e44976658921e5de1f
STAGING_PRINT_MODE = MOCK
STAGING_PRINTING_FEATURE_ENABLED = true
STAGING_ALLOWED_PRINTING_MODES = DISABLED,MOCK
STAGING_PRINTER_ENDPOINT_CONFIGURATION_ENABLED = false
```

Flyway validation:

```text
flyway_success_count = 15
flyway_max_version = 15
flyway_all_success = true
V15__seed_st_denis_canonical_profile.sql = true
```

Profile validation:

```text
profile_code = ST_DENIS_CANONICAL_PROFILE
profile_version = v1
schema_version = STORE_PROFILE_CONTRACT_V1
status = READY
fingerprint_sha256 = af1a8f34cd156c1987b74ec1a9a22ddfd004859c617937b7d53f05e16e762602
artifact_count = 12
profile_json_prefix_valid = PASS
artifact_json_prefix_valid = 12
```

Artifact fingerprints:

```text
COMBO_CONFIGURATION / COMBO_CONFIGURATION / v1 = d724610d64eccddb37c580ec7dcc5b205105fd04038fd29ebdb3f1bae55fed48
DEVICE_CAPABILITY_REQUIREMENTS / DEVICE_CAPABILITY_REQUIREMENTS / v1 = 930805f55f8a8705bcd084c04b41b65c9a30fd3b2cc8524ab9a65ae9c0d3a7c6
FEATURE_DEFAULTS / FEATURE_DEFAULTS / v1 = 6a7f71cfd09341bcd2b0e249dce65481f487f423a3bc9b59bc0edf14835b2b21
HARDWARE_REQUIREMENTS / HARDWARE_REQUIREMENTS / v1 = b8dea2302a645aa0792380f6b94f8260be7d83346de1d5ab6c3c004f0bfe4ff2
LOGICAL_PRINTING_TOPOLOGY / PRINTING_TOPOLOGY / v1 = c571743199407c3ecf6ef7eca268667a665003a45b84f7213211240763f64515
MENU_TEMPLATE / MENU_TEMPLATE / v1 = 5624b1744554709122f194d93f989ef56277268f00c818f239e0e800e523c8ee
MODULE_DEFAULTS / MODULE_DEFAULTS / v1 = bb5400c492b7081c2b6bf5d00d4a3c18a2395e69524af10235196ade4a9e6638
OPERATIONAL_SETTINGS / OPERATIONAL_SETTINGS / v1 = 50d77a96b8f2dfe43e143adb051ac9137a2ef10000ede66443b4b3c586ef8335
PRICING_POLICY / PRICING_POLICY / v1 = d8f0b6bbdb287f99334cff5064b39290eff29f3ee7d5ae903fde5fb788ecd2d9
ROLE_ACCESS_DEFAULTS / ROLE_ACCESS_DEFAULTS / v1 = eb750bbd42e5037fbfb6513c4d9c431479fecff780023f2ad77bdef807e9b55d
STATION_TEMPLATE / STATION_TEMPLATE / v1 = dd502144614ad18446f5386a0566852983e9d0e29a42dd8a7eb5b7de1e14bde0
TABLE_TEMPLATE / TABLE_TEMPLATE / v1 = bd239fb0fcc370f334b5a6765ae1a144c13a4fd5f26dda04e8d2d4d4175ec240
```

Deterministic graph counts:

```text
categories = 6
items = 39
options = 380
parent_option_relationships = 11
tables = 13
stations = 5
logical_printers = 4
printer_assignments = 3
combo_components = 5
staff_templates = 4
device_slots = 7
```

Dependency repair PRs:

```text
PR #144 = a5d9c0fdced0c914cd456d4c7547a75617d4adab
PR #145 = 494497dfbf874bcf12da7eb3821a276f663959c5
PR #146 = 3c99cf1559bbaad2e4c367422bb5eb76877fb086
PR #147 = 3440fddad7571409c66189e44976658921e5de1f
```

Agent 6:

```text
PR #145 = ACCEPT
PR #146 = ACCEPT
PR #147 = ACCEPT
```

Production continuity:

```text
production_health_http = 200
production_containers = UP
production_db_read = NONE
production_mutation = NONE
```

Stop state:

```text
PHASE_A5_ST_DENIS_CANONICAL_PROFILE_COMPLETE_WAITING_FOR_PHASE_A6_OWNER_CONTINUATION
```

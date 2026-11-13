# Allô Print Center test repair — 2026-11-13

PRIMARY_REPAIR_GOAL: Restore Android PAD_DIRECT connection diagnostics and new Print Center sample ticket intent without changing real orders, pairing, lease/claim or duplicate-print safeguards.
EXPECTED_REPAIR_CLASS: MEDIUM

## Ground truth

- Started 2026-11-13T18:26:41Z. Fresh origin/main `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`.
- Clean Allô checkout at `d54fd08264c30d69443ca942a5157e05f9c531b1`; 2 main-only / 36 Allô-only commits. Protected other worktrees unchanged.
- Repair branch `codex/allo-printer-test-20261113`, Phase 6 / ALLO_ANDROID_APK_20261113.
- Contracts: `REQUIRED_CHANGE_REQUEST.md`, `SYSTEM_DOCUMENTATION.md` Android bridge and Print Center sections, `doc/API.md` Allô PAD intent/artifact policy.
- Owner authorized this bounded bug repair. A new immutable Production backend batch still requires approval per AGENTS.md §§8,15; historical b7893860 CORS approval is not a standing release authority.

## Root causes / minimum repair

Production logs at 13:21:41 and 13:22:24 Toronto show printer #3 test and FRONTDESK_RECEIPT assigned-module sample rejected at `markPadDirectQueued` with `RECEIPT_PAD_INTENT_REQUIRED`. Both jobs have no real order and never captured explicit PAD intent.

The connection button independently called the cloud endpoint, whose PAD_DIRECT branch intentionally refuses cloud-to-LAN probing. Android already exposes a socket-only native probe.

- Android UI reads the Store-authorized printer configuration and calls the existing native connection bridge in PAD_DIRECT; other modes preserve backend diagnostics. Cards show the local probe result scoped to Store and endpoint.
- New Allô diagnostic samples freeze a distinct server-owned `ALLO_PRINT_TEST_V1` intent and reuse existing artifact encoding/hash/current-mode checks. Diagnostic markers are rejected on real orders, mismatched Store/module or unknown sample sources. Historical missing/MOCK intent remains nonphysical.
- Actual printers #3/#4 use GBK, no code page, MEDIUM, 80mm. GBK cannot encode the Store display name's `ô`. New Allô PAD static diagnostics use stable Store code/Printer ID when those display names are not encodable; real receipt encoding/text remains unchanged. GBK tests use real GRAB and FRONTDESK renderers.
- No migration, new API, worker reset, pairing change, data clear, blind retry or physical test print.
- Product Simplicity: existing buttons; zero extra steps, states or repeated configuration.
- Small extension to existing artifact boundary is BLOCKING: real receipt context requires order/item snapshots and cannot safely represent an order-free sample. Deployment architecture/tooling improvements are NON_BLOCKING and deferred.

## Validation and release

- Focused backend: 126 tests PASS, zero failures/errors/skips (artifact/job/dispatcher/Pad/policy).
- Frontend: 87 files / 522 tests PASS; TypeScript PASS; diff check PASS.
- Agent 6: initial P2 GBK sample-name blocker closed; consolidated re-review PASS, no P0/P1/P2.
- Source review does not claim Android instrumentation execution; exact artifact build and isolated HTTP acceptance pending.
- Fresh Production backup: 87 tables restored/hash-matched, constraints/indexes valid, socket-only recovery cluster removed. Dump SHA256 `9adbbcefbe5a6b8cbe5bc25492464361ac9be9f539ada04c0433cdf60fbc403f`; not imported into QA.
- Production unchanged. Existing samples 44/45/46 remain PENDING with null execution mode/no artifact; no replay or mutation authorized.
- Standard governance scan meets an existing untracked dependency README broken link; run unchanged validator in the tracked exact-SHA archive to avoid scanning node_modules. Do not repair unrelated tooling.

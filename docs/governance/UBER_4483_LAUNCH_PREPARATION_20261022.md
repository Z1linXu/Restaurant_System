# 4483 Saint-Denis Production mapping acceptance — 2026-10-22

## Scope and runtime

The Owner explicitly authorized installing the existing Production OAuth Secret
and persisting verified Store1 menu mappings. This batch excludes Store3
Sainte-Catherine and Store2 Chinatown, Uber order/menu writes, real order import,
physical printing and activation. The original dirty Owner workspace was untouched.
Fresh implementation baseline: `8a3d41074b0aaad46ced6fc31ebc0e91fd662740`;
worktree branch: `codex/uber-4483-production-mapping`.

Both application environments retain source
`4b3abd1349e66a146c36ac310b6a8b48bb2ad572`, FlywayV38 and backend image
`sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5`.
Only the Production backend was recreated with that same image to load OAuth.
No application build, migration, APK installation or Staging deployment occurred.

## Actual OAuth and Uber reads

Production Client ID: `6GaiQpZTcfcuTa150wr4RyztC33PV7LS`.
Production Store UUID: `5a7dca5c-b7ec-57c8-a684-87d23e66e8d9`.

| Check | Actual result / UTC observation |
| --- | --- |
| Production OAuth, client_credentials | HTTP200, 17:54:15 |
| Requested / actual granted scopes | `eats.store eats.store.orders.read`, no extra scopes |
| GET `/v1/eats/stores` | HTTP200, 1 Store; target listed |
| GET `/v1/eats/stores/{uuid}` | HTTP200, 17:56:47 |
| Store identity | Nouilles de Lan Zhou (St Denis), 4483 Rue Saint-Denis, Montréal; active |
| GET `/v2/eats/stores/{uuid}/menus` | HTTP200, 17:54:15 |
| Production menu | 1 menu, 5 categories, 74 item records, 11 modifier groups |

The real OAuth Secret was installed through the existing hidden-TTY operator
into the existing private0600 Compose configuration. Runtime and canonical
configuration match. Primary Signing Key is separate and unchanged; Secondary
remains empty. Tokens remained in memory. No credential is included here.
The operator reused existing deployment locks, validated database backup,
same-image rollback and acceptance helpers; no second credential workflow exists.

The previous generic `PRODUCTION_OAUTH_NOT_200` discarded its response status
and body. The prior environment was confirmed to lack an OAuth Secret, and the
Owner reported the earlier Signing Key/Client Secret confusion. The exact past
HTTP/error code cannot be reconstructed. The correct credential now yields200;
this does not justify inventing a historical Uber error.

The original Stores parser looked only for `id`, making its initial target-listed
flag false despite a valid menu response. Uber returns `store_id`. A fresh
read correctly confirms the target; the bounded parser now accepts official
`store_id`, retains legacy `id`, and rejects conflicting identities. Original
proof is retained alongside the corrected read, rather than overwritten.

GTS Case60916651 granted the scopes at2026-10-22T04:41:45Z. The Owner sent the
UUID at11:53:07 EDT; GTS's17:30:35Z reply confirms manual reporting association.
Neither that email nor successful Store/Menu reads prove `order_webhook` or a
successful Get Order for a live order. No outbound support message was sent.

## Real menu comparison and actual persistence

Fresh Staging database semantics match all299 approved candidate rules. The
real Production menu and saved TEST baseline differ only in top-level entity
array ordering. Sorting menus/categories/items/modifier_groups by stable ID
makes the complete JSON structures identical. Titles, stable IDs, parent
contexts, quantity limits, availability, prices and menu structure have no
remaining differences. No fuzzy name expansion or external-ID guessing occurred.

| Fact | Database/API read-back |
| --- | ---: |
| Roots / modifier parent contexts externally verified | 41 / 258 |
| Verified transferable rules | 299 |
| Persisted Production Store1 rules | **299** |
| ITEM MAP / MODIFIER MAP / NO_OP | **41 / 238 / 20** |
| COMBO_ROOT, included in ITEM MAP | **8** |
| Missing / conflicting mapping rules | **0 / 0** |
| Canonical Add-on FK / Remove FK+eligibility rules checked | 49 / 14 |
| Actual OWNER audit records | **299** |

Before writing, the batch re-read Store1 catalog and eligible options, checked
canonical IDs and parent/group semantics, validated the exact binding and
worker=false, took a fresh dump and validated its pg_restore directory. It used
the existing authenticated Store1 `PUT /mappings` API, item roots first. Each
response and the final complete API list match the verified manifest. An
independent read-only SQL aggregate confirms the counts above. No Staging PK
was copied. Existing matching natural keys are skipped on retry; unknown or
conflicting existing rows stop without overwrite. No ADMIN impersonation.

One preflight retry occurred **before any PUT**: the guard initially compared
canonical Remove `cilantro` directly to the supported legacy option alias
`remove_cilantro`. The minimal correction applies the existing RemoveSemantics
prefix normalization while retaining exact FK/Store/eligibility checks.
Agent6 accepted both the batch and this correction. No rule or Master data was
changed to bypass that guard. The second attempt created299 rows, skipped0.

Protected fingerprints show all Master menu, Add-on/Remove applicability, combo,
module, binding, printer/device configuration and other-Store mappings unchanged.
No historical orders/snapshots were modified.

## Mapping Preview — explicit remaining business gap

At18:09:31Z the existing **read-only** Store1 `/mapping-preview` endpoint ran
300 real-menu-derived cases against the persisted database mappings:

- 41 root baselines, all258 modifier contexts and one root quantity2 case;
- all20 NO_OP contexts and8 combo roots;
- **275 fully passed; 25 have one warning each**;
- errors=0, RAW fallbacks=0, local item/option snapshot mismatches=0;
- implicit combo, egg/side, Add-on/Remove identity and quantities match exactly.

All25 warnings concern only **Noodles with Vegetables Combo**, root
`e72231fb-9029-4539-91d6-b6efbeeb3767`:
`REQUIRED_SOUP_BASE_MISSING`.
Its Uber menu has no Soup Base group. The mapped local `vegetable_noodle`
requires one of `soup_vegan / 素汤` or `soup_beef / 肉汤`. The other non-combo
vegetable root does have both choices, with rules explicitly scoped to itself.
Those rules cannot be copied across parent context or silently made defaults.

Owner must decide this one missing business fact: fixed vegan broth, fixed beef
broth, or customer selection required. Existing Mapping API has no implicit
broth field. A confirmed default would require a narrowly scoped explicit
capability; requiring selection would require separately authorized Uber menu
configuration. Neither was fabricated or applied during this batch. The old
printing fallback text is not evidence of a confirmed broth choice.

Therefore **mapping persistence/identity PASS**, but **full-menu Mapping Preview
FAIL** and activation remains NO. No existing warning was hidden. No further
confirmation is needed for the299 already approved identities.

Kitchen routing is checked from actual preview projections, active Store1
stations and the unchanged shared OrderService/HotKitchen eligibility rules.
WOK/fried items and fried-egg options qualify for HOT; cold combo-side tasks
remain COLD, and no HOT content means no HOT job. GRAB3/HOT4 assignments remain
enabled and PAD_DIRECT unchanged. This is semantic evidence, not newly executed
kitchen tasks or physical output. No FRONTDESK_RECEIPT job was created. The300-case offline routing projection
expects93 cases with HOT and207 without HOT;15 are direct-serve, and138 cold
combo-side tasks stay out of HOT, including48 with a fried-egg parent. These
counts are predictions from shared code and live configuration, not created jobs.

## Receiver, safety and retained private proof

Receiver=true, Worker=false; global cutover empty, per-Store cutovers `{}`.
Post-restart HMAC acceptance: valid200, invalid401, foreign environment400,
duplicate200 with exactly one new HELD_RECEIVE_ONLY event. New operator probe:
`aea34753-744a-4b32-b4db-6aad9598693b`; the two prior probes remain untouched.
The final database read contains5 HELD receipts: these3 known internal probes
and2 additional receipts whose sender provenance was not established in this
batch. **Official Uber delivery remains NOT_VERIFIED**; a receipt count alone
is not evidence of the sender or the role grant. No receipt was replayed.
Integration orders, local Uber orders and Uber print jobs remain0.
Seven existing POS read-smoke checks and Store-isolation checks passed.

Private server evidence, directory0700/files0600:
`/srv/restaurant-pos/production/uber-4483-final-20261022/`.

| Artifact | SHA256 / observation |
| --- | --- |
| Saved TEST menu | `104cd731d21806244778d7cf2b9c442f34d0e116564fc96e934d11711fb3cddc` |
| Actual Production menu | `3483e624905e0db685292da3dff4857c629f67e13ab998656ae6cd73c044798b` |
| Store1 candidate semantics | `e331993daa5748ee70e5ad32b31b0180c5b4b32effee9e001aec0d065d7e6271` |
| Verified manifest | `8760aa0c17eea762f2d945816b3f11e6aa223328784cb737d2cf62000603b810` |
| Mapping dump before write | `6b0c149ed55e23a26828fc6de196107346ffae349c44a420deeee8a6956c8328` |
| Mapping import proof | `mapping-import-20261022T180612Z/result.json`, persisted299 at18:07:18Z |
| Preview proof | `mapping-preview-20261022T180850Z/result.json`, 300 cases at18:09:31Z |

Private raw evidence and backups are excluded from Git. Do not restore the
whole database over live POS activity or replay old HELD events to undo a
mapping operation. A future correction must reconcile only the exact approved
mapping natural keys and preserve existing IDs/audits/history.

Focused Python tests: **81/81 PASS** (29 OAuth,21 menu verifier,31 existing
receiver/promotion/verification). Agent6 ACCEPT: parser correction and actual
import batch, no remaining P0/P1 code finding. The broth decision is an explicit
activation blocker. Application/device/schema contracts were unchanged, so
application rebuild/full regression/Staging deploy were not repeated.

Remaining activation evidence: resolve the one broth-input gap, confirm
`order_webhook` and official notification delivery with GTS, and complete a
controlled4483-only field window with fresh cutovers and selected Pad/printer
proof. Device13 has recent attributed native polling/claim/complete evidence;
this batch does not establish new HOT paper output. Worker stays disabled.

Post-mapping proof at18:11:24Z confirms Secret present/runtime-canonical match,
Primary unchanged, Secondary empty, TEST credentials separate, no secret value
in backend logs since the batch began, protected fingerprints unchanged and
both environments healthy. The independent database count is299/41/238/20/8.

Docker inspection after the credential-only recreation: disk73% (42G used,16G
free), Build Cache16.82GB with10.45GB reclaimable. No build or cleanup occurred.
The prior same-day observation was74%/16.82GB; it is not an exact immediate
pre-restart sample. Current Staging/Production image, database volumes and
rollback/evidence are protected. Cache is above the soft target and requires
a bounded age/eligibility review; it does not justify unrestricted pruning.

Merge-gate follow-up: Agent6 ACCEPT with no remaining P0/P1/P2 after clarifying
that broth resolution may require code and that the completed OAuth operator's
fixed evidence root must never be deleted/overwritten to force a rerun. The
Production credential/signing-key scan of the Git diff reports0 matches. Cache
review found old reclaimable records; no cleanup was necessary for this bounded
no-build batch, and no protected runtime/image/volume was removed.

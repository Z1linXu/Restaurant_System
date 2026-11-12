# Add-on source of truth repair

本文件记录实现与验收契约。16:09UTC固定23f3已部署Allô，随后实际受保护对账13组/94叶，重放零写；34项基础价格及历史快照不变。完整当前运行证据见 ALLO_PRODUCTION_DEPLOYMENT_REPORT.md。

`PRIMARY_REPAIR_GOAL`: Allô 模板 `ADD_ON`、Store Add-on 管理、前台读取、
新选择和订单使用同一 canonical 当前价格及中英文名称，同时保留历史快照。
`EXPECTED_REPAIR_CLASS`: MEDIUM。未新增 schema/Flyway；V1–V41 未修改。

## Current-value authority

| Business value | Authority |
| --- | --- |
| immutable Add-on identity/code | Organization definition and same-Store catalog FK |
| current Chinese/English names, price and Store availability | `store_addons` |
| item eligibility | linked leaf `addon_eligible` |
| template grouping, sorting and defaults | existing item-owned template metadata/leaves |
| French translation | existing leaf `name_fr`, preserved by detached projections and copies |
| submitted names/prices and rendered Jobs | existing frozen snapshots |

`StoreAddonOptionResolver` now projects the same canonical values for linked
legacy and template leaves. A template leaf's cached name/price or `is_active`
copy does not select another catalog value. Effective availability is catalog
active AND item eligibility; template group availability remains a separate
selection check. Explicit `CUSTOM`, `SPICY_LEVEL`, `SIZE`, `REMOVE` and Combo
semantics retain their independent boundary even when their codes match a catalog
code.

The generic `requiresAssignment` anti-forgery check retains its original exact
Combo/Remove exception list. A client `CUSTOM`, `SIZE`, `SPICY_LEVEL` or invented
group cannot exempt a missing/unlinked option from catalog assignment checks.
Order creation and direct draft submission grant an independent-template exception
only after `TemplateMenuService.isIndependentChoice` rechecks the persisted Option,
same-Store managed Item and current active group binding. The allowed independent
kinds are exactly `CUSTOM`/`SPICY_LEVEL`/`SIZE`; snapshot code/group must match the
server leaf, which must have neither Add-on nor Remove FK. A foreign/missing group,
inactive leaf or unmanaged Item cannot grant the exception.

`TemplateMenuService.getItem`, library reads and `validateSelection` consume the
current authority. Linked template writes normalize copied fields from the catalog
and cannot restore cached names/prices to the catalog. Removing a linked choice
also removes its item eligibility. French stays item-owned because the existing
bilingual catalog has no French field; this repair does not invent a translation
or erase an existing one.

Existing unlinked Add-on leaves cannot be silently converted or repriced by an
ordinary template save. Business-value edits or a supplied canonical ID require
controlled reconciliation (`ADDON_RECONCILE_REQUIRED`). The regular item and
library UI selects new Add-ons from the Store catalog, displays shared names and
prices read-only, and links to the existing Add-ons management section. French
remains editable on the leaf. Other group kinds remain editable as before.

The backend enforces this independently of the UI: ordinary item and library
saves reject any new `ADD_ON` without a same-Store canonical assignment
(`ADDON_ASSIGNMENT_REQUIRED`). An existing unlinked `CUSTOM`/other choice cannot
be converted to `ADD_ON` by changing request metadata, including when its code
matches a catalog entry. The bounded importer alone uses the internal
`saveImportedItem` service seam to create temporary legacy leaves before atomic
reconciliation; no HTTP controller exposes that seam.

Explicit new item eligibility binds to its existing active `ADD_ON` template
group. With no Add-on group it creates one minimal item-owned group automatically.
Disabled or ambiguous existing groups fail with
`ADDON_TEMPLATE_GROUP_UNAVAILABLE`; the operation does not activate another group
or add eligibility to other dishes. Repeating the same eligibility write does not
increment the menu revision.

## Controlled reconciliation API

The existing authenticated/Store-authorized endpoint remains:

`POST /api/v1/admin/menu/addons/reconcile`

Read-only preview:

```json
{"store_id":123,"dry_run":true}
```

Report fields are the existing `store_id`, `dry_run`, `linked_groups`,
`linked_options`, `conflicts`, plus `menu_revision` and SHA-256 `fingerprint`.
The fingerprint covers the exact Store, canonical values and relevant current
Add-on leaf identities, names, prices, availability, eligibility, parent and
template binding. No history or printing payload is read to choose a price.

Apply the exact preview:

```json
{"store_id":123,"dry_run":false,"expected_menu_revision":7,"expected_fingerprint":"<preview fingerprint>"}
```

Apply acquires the existing Store menu lock, recomputes the revision/fingerprint
and rejects a missing preview (`ADDON_RECONCILE_PREVIEW_REQUIRED`) or changed plan
(`ADDON_RECONCILE_CHANGED`). Any unresolved business/relationship conflict blocks
the entire checked apply (`ADDON_RECONCILE_CONFLICT`). Equal existing business
values may be linked; no source value wins by recency, frequency, minimum/maximum
or name similarity. A disabled Owner catalog conflicting with an active legacy
leaf is reported as `ADDON_CATALOG_AVAILABILITY_CONFLICT`, preserving both states.

Successful apply returns the resulting revision/fingerprint. Reapplying those
result guards links zero groups/options and performs zero catalog/leaf/fallback/
revision writes; the controller also omits a no-op reconciliation audit write.
An old pre-apply preview becomes stale after a write and must be refreshed.

The old internal transactional reconciliation overload remains for existing
provisioning/clone callers. The historical explicit `confirmed_prices` path is
still restricted by the existing verified Staging environment/database gate.
No automatic reconcile, importer or UI in this repair supplies price decisions;
Production/unknown environments cannot use that path. The Production release
must use a conflict-free ordinary preview/apply and must not use price decisions
to repair source conflicts.

## Import and startup behavior

`AlloMenuImporter` reconciles only after actually creating/updating menu items,
inside the existing import transaction, and aborts on unresolved conflicts.
An unchanged import does not materialize catalog entries or change menu revision.
Current catalog values remain authoritative when a later unchanged source is
replayed after an Owner edit. Import fingerprints omit the new read-only
`store_addon_id`/`addon_eligible` DTO fields, retaining the original V41 hash format
so a response-shape change cannot create a false source/Owner conflict at startup.
Only items written by that import receive refreshed applied hashes; preserved
Owner-edited ledger entries are not overwritten.

Existing deployed Allô legacy data requires one separately authorized, reviewed
and guarded reconciliation during its release. No startup migration or recurring
data backfill was added. This work did not access a server, real operational data,
historical Orders or PrintJobs.

## Regression proof

Local execution by the Add-on worker:

- PASS: frontend `TemplateOptionGroupsPanel`, `AddonCatalogPanel` and
  `ownerAddonService` tests, 3 files / 23 tests.
- PASS: `npx tsc -b --pretty false`.
- PASS: `git diff --check` at local review.
- PASS: latest focused Maven batch, 7 classes / 123 tests / 0 failures / 0 errors /
  0 skipped, 2026-11-12 02:52:23 America/Toronto. Local log:
  `/tmp/allo-addon-order-focused-maven.log`; Surefire results are in
  `backend/target/surefire-reports`. The worker received exclusive Maven access
  for this batch after the coordinator's combined regression run found the
  generic assignment-check regression. The original security test is unchanged.

The JPA test slice now registers a real `ObjectMapper` for the imported services.
Eligibility insertion explicitly writes `template_default=false`, ensuring the
same value in Flyway schemas and Hibernate-created test schemas. All previously
added French, eligibility and rollback assertions remain in place.

Backend focused classes prepared for the coordinated run:

- `StoreAddonServiceIntegrationTest`: checked preview/apply guards, fingerprint
  changes without revision changes, same-Store isolation, no-op replay, atomic
  conflicts, inactive Owner catalog preservation, visible template eligibility,
  disabled-group protection, existing rollback/concurrency/history tests, and
  detached French projection.
- `StoreAddonJpaIntegrationTest`: actual template/catalog/current-selection
  consistency after price/name edits, stale-draft rejection, stale template-save
  protection, French preservation, eligibility across catalog activation cycles,
  explicit-empty-template eligibility, independent custom/spicy/size values, and
  the synthetic Allô import/replay/Owner-price scenario (34 imported items,
  13 canonical Add-ons, 39 linked leaves on three dishes). Direct negative saves
  prove that an unlinked new paid Add-on/library definition and an existing
  CUSTOM-to-ADD_ON conversion roll back without option/group/revision changes.
- `OwnerMenuAddonControllerTest`: exact Store authorization and forwarding of
  preview guards while preserving the existing Production price-decision gate.
- `AlloImportValidationTest`: compatible import hashes when canonical assignment
  metadata is added to DTOs.
- `OrderServiceImplTest`: original missing/unlinked-option `CUSTOM`/`COMBO_FAKE`
  forgery regression, linked canonical stale-price/history checks, genuine
  same-code CUSTOM/SPICY_LEVEL/SIZE template create+submit with independent
  price/French snapshots, and foreign/missing template binding rejection during
  creation and direct draft submission.
- Existing `TemplateMenuServiceTest` and `AlloSelectionRulesTest`: retained group,
  cardinality, default, independent-option and refill contracts.

Integration follow-up belongs to the coordinator: run the combined affected
regression batch and order/Combo snapshot regressions, wire
`AddonCatalogPanel.onChanged` to the menu
refresh, update `SYSTEM_DOCUMENTATION.md` and the living API contract, independent
review, then any separately authorized environment acceptance/release. No runtime
or Production PASS is claimed here.

## Coordinator acceptance and actual environment state

Sealed app source e7dde0a6e27adf3be00baa9972fee06cdcc5482c includes the earlier
canonical repair unchanged. Combined backend1519total/1402pass/117skip/0failures,
frontend481/84pass, actual synthetic PostgreSQL and independent review passed.
Browser Owner changed n_extra_beef7.99→8.49 with synthetic names, saved/reloaded,
and B1 modal showed the same current values. A later narrowly guarded API
cleanup restored7.99/牛肉/Beef; old order snapshots remained8.49/7.99 as originally
submitted. This cleanup is not a UI-restore PASS. See browser/routing reports.

The09:10pre-deployment Production baseline had old code and0canonical rows. Configuration-only
pre-audit predicts13 groups/94 paid leaves, but lacks per-leaf zh/fr/FK details;
it does not certify conflict-free apply. Current-price/FR/ID/history comparisons,
checked dry-run/apply and zero-write replay remain activation steps, not executed
results. Other94 physical option leaves must remain unchanged.

## 09:10UTC config-only preflight

2026-11-12 的完整配置提取补齐了上一节旧 aggregate 缺少的逐叶中文、法语、
ID、parent、FK、eligibility 和组绑定。输入仅为私有文件
`production-addon-full-config-readonly.json`，SHA-256
`b1cc458c0ae62f639249daf4e3feb782afda0a2b2e7164334c7d2433993192ae`。
该 SHA 是文件证据标识，**不是 reconcile API fingerprint**。此检查没有访问
服务器、导入 QA、读取订单/用户/凭据或修改运行数据。

依据 sealed source `e7dde0a6e27adf3be00baa9972fee06cdcc5482c` 的实际
`StoreAddonService.rows/isAddon/plan/hasAmbiguousRelationships` 规则做静态检查：

- Store `1` / Organization `1` / menu revision `96`；34 items、188 options、
  31 groups、0 Store Add-ons、0 Organization definitions。
- 唯一符合 Add-on 语义的候选是 `paid_addons` 的 13 codes / 94 叶，分布于
  14 个菜品及其 14 个已启用 `ADD_ON` 组。8 个 noodle code 各 8 叶，
  5 个 rice code 各 6 叶。
- 每个 code 内 zh/en/fr 各只有一个精确值，三种名称均非空且不超过 255 字符；
  price 各只有一个数值，非负且最多两位小数。全部 94 叶 active=true。
  `n_extra_beef_bone=15.99`、`n_extra_broth=1.99`、
  `r_extra_crispy_chicken=8.99`；其余 10 codes 均为 7.99。
- 94 叶的 Add-on/Remove FK 及 `addon_eligible` 全为 null，parent 全为 null；
  无重复 item/code、重复 Option ID、孤立 item、跨 Store item 或缺失/错误/停用
  组绑定；关联菜品均启用模板管理。13 codes 格式合法，与其他组选项 code 无交集。
- 其余 94 叶分别为 32 spicy、24 noodle choices、4 duo choices 和 34 个
  inactive `COMBO/combo`。它们不符合 Add-on 语义；即使存在未提取的订单引用，
  这些 COMBO 叶也会被 `isAddon` 排除，不能成为第 14 个 canonical code。

**静态 plan 冲突数为 0；静态预期为 13 groups / 94 options。** 当前无 catalog
可与之产生 catalog-values/availability 冲突；逐 code 的中英文/金额和逐 item
关系检查也没有发现 business-values/relationship 冲突。按现有 apply 写入逻辑，
该快照将产生 13 个 Store catalog 和 13 个 Organization definition，原 94 叶
设置同店 FK、`addon_eligible=true`、更新 `updated_at`，重申既有 `ADD_ON` 并保持
active=true；zh/en/fr、金额、ID、
parent、默认、排序和组绑定均不被改写。其他 94 叶不在写入集合内。

这解决了旧 aggregate 无法验证中文/法语一致性和逐叶关系的证据缺口，
**不构成新后端实际 API dry-run PASS、锁内 fingerprint 校验 PASS、apply PASS
或历史/PrintJob 数据不变的运行证明**。文件提取后可能发生配置变化，实际
API 仍须重新返回 `conflicts=[]`、13/94、当前 revision 和 fingerprint，随后
通过受保护 apply、前后字段比较与零写重放验收；不能把文件 hash 当作 API guard。

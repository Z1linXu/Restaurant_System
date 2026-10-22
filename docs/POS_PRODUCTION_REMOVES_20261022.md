# POS 五项修复与三店共享 Remove 发布证据

PRIMARY_REPAIR_GOAL：发布五项修复与三店指定 18 个共享 Remove，原商品有效选项、
价格、启停、Parent、历史、设备和 Uber 行为保持。EXPECTED_REPAIR_CLASS：HIGH。
本文件只记录本次证据，不授予未来生产操作权限。

## 已验证基线与实施

- 开始：2026-10-22 04:32 UTC。fresh main `e6bd1f3ec13df3519f83881ef3fea29209f37fc6`。
- 以独立 `codex/pos-production-removes` worktree 整合 PR #282；合并基线
  `9a6fa04` 同时包含 main Uber 与原 PR 全部代码。保留原 Owner 脏工作区。
- Production 实测 Backend `7d573be03d66a9505e0c68f92fa17829247f56e3`，Frontend
  `8c3a9e356f59cd025beff25f7fb1d5a20594e028`，Flyway V37。
- 实际营业 Store：1 / 4483 R. Saint-Denis / `4483_R_SAINT_DENIS`；2 / CHINATOWN；
  3 / St. Catherine / `ST_CATHERINE`，Organization 1 / Lanzhou Noodles。
- 每店共享主数据 0、旧 Remove 110。三店已发布 MODIFIER_REMOVE 恰好 18 canonical
  对且相同，无草稿；无需新建规则版本。
- Backend 完整 `mvn verify`：1061 total、975 executed、86 conditional skip，0 failure/error。
  其中 real PostgreSQL StoreRemove 7、Baseline 4、printing lifecycle 13 均执行。
- Frontend：321 tests / 55 files、TypeScript/Vite build、受影响 ESLint PASS。
- 独立 Agent6 整批 ACCEPT，无剩余 P0/P1/P2。

## 数据演练与备份

使用只读 Production 330 条 Remove 输入，在独立本地 PG16 同 service dry-run/apply/replay：
每店 0→18 Master、53 links、原 raw/effective/价格/Parent 变化 0，fresh dry-run replay 0/0。
Store 2 原 inactive 406/420 保持 false。报告 `/tmp/pos-prod-remove-dryrun.json` 私密保存。

每店 57 retained legacy rows，对应 54 条诊断：19 missing code、22 outside baseline、
11 special parent、2 ambiguous business-value groups（胡萝卜/黄瓜）；不猜测、不删除/停用。

Production preflight backup：
`/srv/restaurant-pos/production/pos-removes-preflight-20261022T044150Z`，custom dump
11,020,499 bytes，SHA256 `1d6daf9c114746060aa7945bcb729064715ea8a5d75423c40cc47194f818f557`。
`pg_restore --list` 与完整 `--file=/dev/null` 读取成功；另保存三店 menu/options/pricing/rules
及当前私密 Compose。实际发布前部署 helper 还会生成新鲜备份。

保护历史 PRINTING IDs：Production 1176/3169/9226/20391；Staging 200/205/207。
只保留，不 release/requeue/ack/重打。Production 无实际测试营业订单或实体打印授权。

## Docker / Disk Hygiene（发布前）

磁盘 59G、已用 41G、可用 16G、73%；Build Cache 16.82GB / reclaimable 10.45GB。
168h 以上 291 records，严格 immutable/unshared/zero-use eligible 0；279 reclaimable
未满足保守资格，未清理。当前/回滚 app images、DB volume、配置、备份及证据全部保护。

正式 Staging / Production 部署与只读验收结果将在操作完成后补入本记录；当前不提前声称上线。

## 最终集成与并行 Uber 配置

发布前 fresh fetch 发现并整合 `5d2c563` / PR #284（receiver 运维 helper、6 个无网络
unit checks、文档；Backend/Frontend 源码与已测试版本完全相同）。保留其显式授权及配置。
PR #282 于 2026-10-22 04:51:20 UTC merge，最终 Release SHA：
`4b3abd1349e66a146c36ac310b6a8b48bb2ad572`。

04:51 UTC 独立只读复核：Production Backend 仍为 7d573be 同镜像，但 receiver 配置已
于 04:50:13 重建：receiver=true、signing key present、worker=false、KITCHEN_MIRROR。
仅 1 条 HELD_RECEIVE_ONLY 事件，binding、Uber/localUber order、Uber print job 全 0。
新发布复制这份实际 current.compose，不使用先前 receiver=false 的旧备份作为恢复模型。

最终小簇修复另跑 14 PostgreSQL/semantic tests，全部执行通过：generic reconcile 对
缺失 type/group 或 mixed-prefix group 保留 conflict，不触发 V38 CHECK 回滚整店。
Agent6 增量 ACCEPT；干净 exact-SHA server source 的治理校验 PASS。

发布前三店 Printing revision：Store1 v2 / id5；Store2 v6 / id10；Store3 v2 / id6。
三个已发布 18 项字典均原样保留；Store2 NO_SPICY 自定义内容保留。


## Staging 实际部署与验收脚本修正

Staging 于 04:56:59 UTC 部署 APP `4b3abd1` / Flyway V38；Backend image
`sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5`，
Frontend image `sha256:2b0749d39545a4eae784b29315be78aac0b5866dc069fb6c60372ad82ee4387b`。
全库备份 SHA256 `717dccc62f3b11f2280e95dfd7de49a46a34bba3d6c8ec4b4d1eeba2618db624`，
路径 `/srv/restaurant-pos/staging/pos-removes-4b3abd1349e66a146c36ac310b6a8b48bb2ad572`。
原 Production 及两 DB container/fingerprints 保持；历史快照 hash 不变。

Store18 首次补 3 个缺失 master（22→25）、0 legacy links、原 raw/effective 变化 0；
fresh dry-run/apply replay 0/0。原 apply/dry-run/replay 已另存 `.attempt1.json`。
首轮验收脚本错误比较整个 `combo_configuration`，其 `menu_revision` 随基线整理合法
递增，因此误报 AssertionError。DTO/服务确认该字段为共享菜单版本；原子事务报告
及其后 dry-run/无变更 replay 全 PASS。修正只忽略这一个 metadata 字段，仍完整比较
Store、groups/components/item overrides，新增 2 个针对检查 PASS；不修改应用代码，
不重建镜像。修正脚本通过独立 source SHA/clean tree/零 app-source-diff 校验后复用镜像。

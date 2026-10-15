# 01 — Production Baseline Audit

审计日期：2026-10-15（America/Toronto）。范围：READ-ONLY AUDIT + DOCUMENTATION + QUESTIONS。

源码基准：后端 **B = `ce35278b3569a4eacfa29efc01cadbf0c409bec6`**；线上前端 **F = `8c3a9e356f59cd025beff25f7fb1d5a20594e028`**；远端 main **M = `06bac51ddc0a829a9aa1106f07ef886572efbb65`**（本轮 `git ls-remote` 确认，未 fetch 或更新引用）。本轮证明 B/F 的 `frontend/`、`restaurant-pad-app/` 无 Git 内容差异，因此 B 中这两部分也可用于语义审查。源码链接固定到提交，不能用当前旧工作区的同名文件替代。

证据标签：`VERIFIED_IN_CODE` 表示读到固定提交中的实现；`VERIFIED_IN_PRODUCTION` 仅表示本轮明确读取的运行事实；`DOCUMENTED_ONLY` 表示仅有文档陈述；`PLANNED_ONLY` 表示计划/契约尚未证明完成；`UNKNOWN` 表示证据不足。代码被部署不等于所有行为均经过线上验证。风险优先级 P0=独立商家共享部署阻断，P1=上线前关键，P2=按首批范围处理，P3=维护改善；不代表要求立即修改现有 Production。


## 结论与边界

**当前应用基线已确认，是 B 后端 + F 前端 + Flyway V37 的组合版本。** 本轮通过已有 `restaurant-prod` SSH 别名、固定主机校验及现有身份读取。未读取私钥、`.env` 值、JWT、客户/员工/订单记录或业务数据库备份内容。网络沙箱第一次阻止连接，随后获工具只读执行许可；未绕过主机校验。没有重启、部署、pull/build、配对、打印、迁移、Git checkout/reset/fetch 或数据库写入。

观测窗口：2026-10-15 15:48:42–15:57:09 UTC（11:48:42–11:57:09 EDT）；随后只读补查 PostgreSQL catalog 的 RLS 计数。健康只证明 HTTP 可达，不证明交易/物理打印/全部设备兼容。审计期间餐厅继续营业。

## 运行版本与构建来源

| 对象 | 本轮读到的事实 | 状态/来源 |
| --- | --- | --- |
| Production backend | B；`restaurant-pos-backend:ce35278b3569a4eacfa29efc01cadbf0c409bec6` | VERIFIED_IN_PRODUCTION；运行容器 `.Image` → image ID / tag / OCI revision 标签一致；`/srv/restaurant-pos/production/release.json` 的 `source_sha` 与 backend-only `result.json` 一致 |
| Production frontend | F；`restaurant-pos-frontend:staging-8c3a9e356f59cd025beff25f7fb1d5a20594e028` | VERIFIED_IN_PRODUCTION；运行镜像 ID / tag / OCI revision；`v37-owner-release-20261005T051347Z/release-result.json` 的 frontend ID 相同。tag 中 staging 字样不能将其认作 Staging |
| Staging backend/frontend | 同 B/F 镜像 ID | Staging 运行快照本轮已核验；不使用 VERIFIED_IN_PRODUCTION 作为 Staging 验收标签；配置和数据仍独立 |
| 前后端同一提交 | 否；B 2026-10-06，F 2026-10-03 | VERIFIED_IN_CODE；B 的变更主要为 Add-on identity / Daily Close，B/F 的 frontend 与 Android 树无差异 |
| 镜像来源证明强度 | image ID、OCI revision、release result 可相互印证 | 没有验证签名、SBOM、独立可重现构建；不能把标签视为密码学源码证明 |
| RepoDigests | Docker 返回 backend/frontend 各自与下表 image ID 相同的 `repository@sha256:…` | VERIFIED_IN_PRODUCTION；未验证外部 registry manifest、签名或推送存在性，不混同 registry 分发凭证 |
| 数据库 | 两环境 V1–V37，37 条成功，脚本与 checksum 相同 | VERIFIED_IN_PRODUCTION；只读 SELECT；与 B 的 37 份 SQL 用 Flyway 行级 CRC32 规则重算全部一致 |
| 镜像 app 版本 | backend 的 OCI version 为 `22.04`，属于基础镜像信息 | 不能用该值代替应用 release SHA |

| 容器 | Image ID | StartedAt（UTC） | RestartCount | Health |
| --- | --- | --- | --- | --- |
| `cloud-backend-1` | `sha256:5b9583ee43ad2ceeb9b47a08a47397cb8802a19f1d89366a0b0b04889ebbaaad` | `2026-10-06T16:35:38.802710222Z` | 0 | NO_HEALTHCHECK |
| `cloud-nginx-1` | `sha256:4bf301d4d547cc017be91c1e6d7c0d859cca345d4aed4db1696e32000e7048fc` | `2026-10-05T05:24:37.795938414Z` | 0 | NO_HEALTHCHECK |
| `cloud-db-1` | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` | `2026-07-11T12:09:37.936437539Z` | 0 | healthy |
| `restaurant-pos-staging-backend-1` | `sha256:5b9583ee43ad2ceeb9b47a08a47397cb8802a19f1d89366a0b0b04889ebbaaad` | `2026-10-06T16:34:21.999336254Z` | 0 | NO_HEALTHCHECK |
| `restaurant-pos-staging-nginx-1` | `sha256:4bf301d4d547cc017be91c1e6d7c0d859cca345d4aed4db1696e32000e7048fc` | `2026-10-03T22:51:27.331008289Z` | 0 | NO_HEALTHCHECK |
| `restaurant-pos-staging-db-1` | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` | `2026-08-22T19:14:39.633030411Z` | 0 | healthy |

前后两次容器快照的 IDs、Image IDs、StartedAt 和 RestartCount 一致。backend 与 nginx 未配置 Docker healthcheck，两个 PostgreSQL healthy。`docker diff` 仅出现 JVM/Tomcat 临时目录，以及 Nginx cache/run/config 路径；未见 `/app` 或 `/usr/share/nginx/html` 的 overlay 变更。Nginx 配置属于运行配置，不是 frontend source commit 的一部分；挂载文件并不由 `docker diff` 完整覆盖。

## 域名、旧 IP 与真实入口

| 入口 | 本轮结果 | 能得出的结论 |
| --- | --- | --- |
| `http://170.106.13.34/` | 200，1537 bytes | 旧 HTTP IP 仍可返回 Production POS shell；是否继续开放须后续决策 |
| `http://pos.lanzhounoodlesmtl.com/` | 308 | 正式 HTTP 域名已转向 HTTPS；本轮没有把旧 HTTP 当成安全入口 |
| `https://pos.lanzhounoodlesmtl.com/` | 200，1537 bytes；health 200/UP | 正式 Production 入口可达 |
| `https://staging-pos.lanzhounoodlesmtl.com/` | 200，1537 bytes；health 200/UP | Staging 单独入口可达 |

三个成功 HTML 响应 SHA-256 均为 `81ad42d4cce490d53c5175d9847e5f70bda149115a32056e704c5f161990cbf4`。**相同 HTML 不能证明同一数据库**；本轮另读实际 Nginx upstream：Production 域名和旧 IP 默认站点指向 `cloud` 网络 backend；Staging 域名经 `172.18.0.1:18480` 的专用 ingress 转向 `127.0.0.1:18080` Staging 服务。两套 backend/db 容器不同。没有进行真实订单核验或 WebSocket 订阅。

两 backend 都为 `cloud` profile，`SERVER_FORWARD_HEADERS_STRATEGY=NATIVE`。Production `UBER_EATS_ENABLED=false`，Staging 为 true。未提供的环境项标为未读取，不推断其值；代码 cloud 默认关闭 X-User-Id fallback、demo/default-user/membership seed，见 [B:backend/src/main/resources/application-cloud.yml:48](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/application-cloud.yml#L48)。这不是完整 effective-property dump。

## Git、服务器修改与本地差异

| 层次 | 身份/差异 | 判断 |
| --- | --- | --- |
| 当前 Owner 工作区 | `main` / `ba169ed8b689ddef8dffe94deee82fea191cdcfb`，审计前 127 个 status 条目，含 staged/unstaged/untracked | 受保护的旧工作区；不是 Production，也不是 fresh main |
| remote main | M | 比 B 多两个提交；diff 仅 4 文件：部署 helper 比较 env 顺序修复、两份文档与 CURRENT_STATE；应用源码不变 |
| B 相对本地 HEAD | 1017 files changed，140075 insertions，4608 deletions | 不能从旧 HEAD 新建商业核心并假设已保留 Production 能力 |
| 服务器 control checkout | `/home/ubuntu/Restaurant_System`，`278851f0c4d932a149d598964abca765bb2b5083` | tracked status 无修改；untracked 运行锁、backups/data、bootstrap env 和旧配置 dump 存在，仅记类型，不读取内容 |
| 当前 production release dir | `/srv/restaurant-pos/production/addon-identity-<B>` | 运行 Compose/artifact 目录，不是 Git checkout；不能声称其源码 worktree clean |
| server 旧 staging bare repo | 不含 B object | 不能以它作为唯一提取来源；本机 Git object database 存在 B/F/M |
| 本轮源码提取 | 对已有 B/F 使用 `git archive` 到 `/private/tmp/restaurant-phase0-20261015/` | 没有从容器抽代码建立唯一源码、没有创建 branch/tag/repo、没有复制业务数据库 |

最新治理证据 [M:docs/governance/CURRENT_STATE.yml:30](https://github.com/Z1linXu/Restaurant_System/blob/06bac51ddc0a829a9aa1106f07ef886572efbb65/docs/governance/CURRENT_STATE.yml#L30) 与本轮 B/F/V37 匹配；其 Production mutation 已关闭。旧工作区的 V7/V8 handoff、9 月 V28/V30 和 HTTPS STOP 均为历史事实，不能覆盖本轮观测。最新 release evidence 是 [M:docs/governance/PRODUCTION_ADDON_IDENTITY_20261006.md:1](https://github.com/Z1linXu/Restaurant_System/blob/06bac51ddc0a829a9aa1106f07ef886572efbb65/docs/governance/PRODUCTION_ADDON_IDENTITY_20261006.md#L1)。

## Android 与兼容性

[B:restaurant-pad-app/android/app/build.gradle:13](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/build.gradle#L13) 声明 package `com.restaurant.pad`、versionCode 4 / `0.4.0-kitchen-field-fixes`。它是源码事实；本轮未连接 Android/ADB，也未读取设备注册业务表，**Production 各台安装 APK、签名、文件 SHA、bundled manifest/buildVersion、保存的 URL、设备配对和长期 Worker 状态全部 UNKNOWN**。历史 Staging 物理纸张确认见 M 的 evidence，不升级为本轮 Production 全设备兼容证明。前后端/Android 发布必须分别记录 SHA 和 contract version，不能仅记一个 repository SHA。

## Flyway 证据账本

命令边界：已有 DB 容器内 `psql -X`，会话 `default_transaction_read_only=on`、`statement_timeout=5000`、`lock_timeout=1000`；仅 `SELECT version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank`，另查 catalog。下面不含业务行。两份完整账本输出 SHA-256 同为 `c028fd56917e185eceafbda6da5957fd0f6738156059652235fcba5d18e521d1`（每行以 LF 终止）。

| Version | Script | Flyway checksum | success |
| --- | --- | --- | --- |
| 1 | `V1__baseline_current_schema.sql` | `431188510` | true |
| 2 | `V2__add_versioned_menu_revision.sql` | `-1546045661` | true |
| 3 | `V3__add_idempotent_order_submission_and_dispatch_outbox.sql` | `-1713808660` | true |
| 4 | `V4__add_menu_item_sort_order.sql` | `1636049775` | true |
| 5 | `V5__set_cold_chicken_noodle_default_type.sql` | `-1638580130` | true |
| 6 | `V6__add_order_item_routing_snapshots.sql` | `-1681894826` | true |
| 7 | `V7__add_print_job_attention_acknowledgement.sql` | `625683957` | true |
| 8 | `V8__add_owner_store_onboarding_requests.sql` | `1654406856` | true |
| 9 | `V9__add_staging_synthetic_bootstrap_requests.sql` | `1828097009` | true |
| 10 | `V10__add_owner_store_menu_clone_requests.sql` | `482267873` | true |
| 11 | `V11__add_store_pricing_policies.sql` | `-1257180446` | true |
| 12 | `V12__add_store_combo_components.sql` | `-1484328108` | true |
| 13 | `V13__add_store_modules.sql` | `1338109061` | true |
| 14 | `V14__add_store_profiles.sql` | `-965973533` | true |
| 15 | `V15__seed_st_denis_canonical_profile.sql` | `-800444250` | true |
| 16 | `V16__add_dynamic_menu_management_configuration.sql` | `473655687` | true |
| 17 | `V17__add_printing_display_rules.sql` | `-1840784048` | true |
| 18 | `V18__add_phase_b_master_menu_provisioning.sql` | `-1514098834` | true |
| 19 | `V19__seed_phase_b_master_menu_profile_v2.sql` | `182579909` | true |
| 20 | `V20__align_phase_b_provisioning_validation_status.sql` | `-1638534388` | true |
| 21 | `V21__repair_phase_b_master_menu_fingerprint.sql` | `2011567215` | true |
| 22 | `V22__repair_printing_display_rule_revision_lifecycle.sql` | `-699128137` | true |
| 23 | `V23__add_phase_b_part2_readiness_and_activation.sql` | `-1207640832` | true |
| 24 | `V24__harden_phase_b_part2_evidence_and_device_heartbeat.sql` | `755635947` | true |
| 25 | `V25__widen_phase_b_device_contract_version.sql` | `-1891760137` | true |
| 26 | `V26__add_staging_store_fixture_cleanup_ledger.sql` | `624267691` | true |
| 27 | `V27__add_item_combo_egg_default.sql` | `1012997460` | true |
| 28 | `V28__add_store_addon_catalog.sql` | `-235902998` | true |
| 29 | `V29__add_uber_eats_integration.sql` | `-1088042546` | true |
| 30 | `V30__printing_reliability_affinity.sql` | `119071053` | true |
| 31 | `V31__uber_kitchen_mirror.sql` | `-458680414` | true |
| 32 | `V32__uber_accepted_observation.sql` | `-834115206` | true |
| 33 | `V33__external_kitchen_fallback_snapshot.sql` | `1585227176` | true |
| 34 | `V34__add_store_daily_close.sql` | `1242746246` | true |
| 35 | `V35__add_uber_item_financial_snapshot.sql` | `-139453383` | true |
| 36 | `V36__add_external_order_note_snapshot.sql` | `1806435613` | true |
| 37 | `V37__add_uber_eats_store_module.sql` | `580592530` | true |

## 推荐的提取与 Git 隔离方案（仅设计）

优先方案：以 B 的应用源码为可追溯提取起点，同时固定 F 的 frontend artifact；纳入 M 中已审阅的部署工具修复，形成独立 release manifest，声明 backend/frontend/Android/schema/config 各自身份。**本轮不创建该分支或 Tag。** 未来经 Owner 答复后，可在隔离 checkout 中建立 `codex/commercialization-<phase>`，每个小 PR 保持 Lan Zhou profile 回归；现有餐厅仅接受明确批准的生产 release，不跟随商业试验分支自动部署。

| 选项 | 优点 | 代价与适用边界 |
| --- | --- | --- |
| 单 repository + core/modules/profiles + 独立 release manifests（建议候选） | 修复复用、历史可追踪；餐厅配置与产品代码分离 | 必须建立跨 profile 回归，禁止 main merge 自动改线上；release Tag 需记录组合版本 |
| 独立 commercial repo，保留 origin/commit lineage | 客户交付与品牌隔离更清晰 | 双向 backport、补丁漂移、依赖和授权管理成本更高；先检查历史中的敏感资料，不直接复制全库给客户 |
| 长期两条产品 branch | 初期隔离快 | 双重修复与 schema 漂移成本持续上升；不建议作为永久架构 |

未来 baseline Tag 建议为签名/受保护 annotated Tag 加版本清单；Tag 名称及 repo 由 Q20 决定。保留当前所有功能，先固化 Lan Zhou profile，再逐条替换调用方，旧订单和打印快照不可重算。

## 未知项及风险

UNKNOWN：全部 APK 安装实况、可重现/签名构建、registry provenance、完整 effective 配置、secret rotation/云账户权限、证书续期任务、offsite backup/加密/自动频率/RPO/RTO、恢复演练、WebSocket/WSS 端到端和物理打印。有限扫描发现 12 份 cloud backup dump，仅证实文件存在/大小；没有打开或复制 dump，不能推出备份完整性或恢复能力。backend-only release 私有目录另有 dump，本轮不将有限文件列表当全量备份清单。

原始临时观测用于复核，不作为长期唯一证据；本文件保留安全、最小化摘要。未来实施前需重新读 fresh runtime，不能把 2026-10-15 快照永久视为当前生产。

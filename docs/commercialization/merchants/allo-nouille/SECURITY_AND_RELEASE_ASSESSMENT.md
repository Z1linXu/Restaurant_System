# Allô 依赖安全与发布评估

日期：2026-11-12。Reviewer：`/root/independent_safety_review`。
核心源码：`c2425481ec25257660fa1a73d9ed0ec284eb5fe2`。

| 项目 | 结论 |
| --- | --- |
| 本次已核对依赖与发布配置 | **ACCEPT WITH LIMITATIONS**；未发现新增应用 P0/P1 |
| 前端 audit 修复后 | 全量 **7 moderate / 0 high / 0 critical**；`--omit=dev` **0** |
| Backend 全量 SCA / 容器 OS 扫描 | **未执行，未声称通过** |
| Production Release | **DEPLOYED / POST_SMOKE_PASS**；16:09UTC固定23f3批次已激活，工具受限UI保持NOT_RUN并经独立风险复核为非阻断 |
| 真实 Pad、Android Worker、打印机/纸张输出 | **NOT TESTED** |

本报告是证据评估，不是部署授权或 Gate 状态修改。Reviewer 只读源码、保存的扫描/
恢复/QA 产物及官方公告；只新增本报告。未运行 npm/Maven/audit、未连接目标服务器、
未执行恢复、部署、订单、价格或设备操作；没有修改其他人的实现和文档，也没有 commit。

## 1. 实际 npm 证据

证据目录为本机受限
`/Users/xuzilin/.codex/private/allo-release-20261112`。直接解析结果如下，未把 audit 的
非零退出或剩余提示改成零漏洞：

| 保存产物 | info | low | moderate | high | critical | total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `npm-audit.json`，修复前 | 0 | 1 | 11 | 8 | 1 | 21 |
| `npm-audit-after.json`，兼容修复后 | 0 | 0 | 7 | 0 | 0 | 7 |
| `npm-audit-production.json`，`--omit=dev` | 0 | 0 | 0 | 0 | 0 | 0 |

`npm-audit-fix.log` 记录 added 3 / changed 66 packages，剩余修复建议需要 `--force`
并标记 breaking change。本次未据此要求执行强制升级。当前 `package.json` 的既有
依赖范围保留，变更在 lock 中；主 Agent 已报告兼容更新后 Frontend 481 tests 与 build
通过。本 Reviewer 没有另跑测试或扫描。

| 产物 | SHA-256 |
| --- | --- |
| `npm-audit.json` | `23723ab6611438f7cafac4506bfadc1362eb4aa6a6c624da13a88f59e463d90b` |
| `npm-audit-after.json` | `046a93eb27d8dcc96703e69b947de3c9dee88eddf446d33d8eccb68b28e5034e` |
| `npm-audit-production.json` | `ef45e8443ec71be26004baf876cb6e9ee5b2d9748eddcbbeade7215e0138819a` |
| 当前 `frontend/package-lock.json` | `37666fa2abf39f20f314d9e2de147f58f4d0804711126cbd31429e2548098b94` |

### S-01：原 critical 已修复

修复前 critical 为 `websocket-driver` 的 GHSA-xv26-6w52-cph6。维护者公告说明
`<0.7.5` 受影响，`>=0.7.5` 已修复。当前 lock 和本机安装包均为 **0.7.5**，修复后
audit 不再列此项。这里依据版本修复关闭问题，没有把旧告警解释成不需要处理的
前端误报。[维护者公告](https://github.com/faye/websocket-driver-node/security/advisories/GHSA-xv26-6w52-cph6)。

### S-02：7 项 moderate 为非阻断开发依赖债务

七个 package 条目为 `typescript-eslint`、`@typescript-eslint/eslint-plugin`、
`parser`、`type-utils`、`typescript-estree`、`utils` 及其 `minimatch`，均沿同一
底层 GHSA-g3hv-75wg-m249 传播，不等于七个独立漏洞。当前 `typescript-eslint=8.72.2`、
嵌套 `minimatch=10.2.6` 在 lock 中均为 `dev: true`。维护者说明攻击需要可控制 glob
pattern 并触发相关优化处理；该版本仍受影响，修复版本为 11.0.1。
[维护者公告](https://github.com/isaacs/minimatch/security/advisories/GHSA-g3hv-75wg-m249)。

源码仅在 `frontend/eslint.config.js` 引入该 lint 链；应用源码没有相关 import。
Allô 的 frontend Dockerfile 将 `dist/` 复制到 Nginx，不复制 `node_modules` 或运行
ESLint。结合 production audit 为 0，本评估将其归为当前 Nginx 发布的**非阻断开发
工具风险**。这是部署路径推断，不是对构建机/CI 或全部 JS 可达性的零风险声明。
后续应在独立依赖维护中消除剩余提示；本次不盲目执行 breaking `--force` 修复。

## 2. Backend：仅核对已指定公告的适用条件

直接读取实际候选 JAR，确认包含：

- Spring Boot parent 3.3.3；`spring-core/web/webmvc=6.1.12`。
- `tomcat-embed-core=10.1.28`，未包含 `spring-webflux`。
- JAR SHA-256：`ec2627dbeeeb8cd11e8cd41ad60cf61d0ec97214dcc355d1c738c828d08a05a5`，
  与 `backend-full-summary.json` 和本机 QA 所用 artifact 一致。

### S-03：CVE-2024-38816 的窄范围静态适用性结论

Spring 官方公告将 6.1.0–6.1.12 列为受影响版本，6.1.13 为修复版本。触发条件同时
要求 functional `RouterFunctions` 静态资源处理与显式 `FileSystemResource`；公告还
说明 Tomcat/Jetty 会拒绝相应恶意请求。
[Spring 官方公告](https://spring.io/security/cve-2024-38816/)。

候选 **6.1.12 确实处于版本受影响区间**，未升级。当前 `backend/src/main` 没有
`RouterFunctions` / `FileSystemResource` 引用，使用注解 MVC Controller，实际 JAR
为嵌入式 Tomcat。因此本评估推断：**当前源码/打包方式不满足该公告的功能资源触发
条件，该具体 CVE 未形成新的发布阻断**。不能把此结论写成 Spring 已全部修复或
Backend SCA PASS。未运行 Maven 漏洞扫描、SBOM/OS 镜像扫描或针对其他公告的完整
可达性分析；旧框架依赖的后续维护仍需单独安排。

## 3. V42 与实际 Compose

当前核心源码的 `deployment/allo-nouilles/compose.yml` 已明确配置：

- `SPRING_FLYWAY_TARGET=42`、validate on migrate、baseline off、JPA validate。
- 原独立 `allo-test` profile、`db:5432/restaurant_allo_nouilles_test` / `allo_test`。
- `APP_ENVIRONMENT=production`、显式 Allô production printing opt-in、允许模式精确为
  `DISABLED,MOCK,PAD_DIRECT`；无 `REAL`，普通 seeders/两个 Test bootstrap/Uber worker 关闭。
- `APP_REALTIME_AUTHENTICATED_STOMP_ENABLED=true`；数据库不发布 host port，frontend
  只运行 Nginx；Store 运行数据必须继续为 `MOCK`，本次不配对真实设备或配置实体 endpoint。

当前 Compose SHA-256 为
`6672b3277ab87b39da37d6313d8694b238599de29fb41f3c6635754a7bc94177`。
前一份独立报告中“Compose 仍为 41，待调整”的描述在当前 commit 下已过时；此处纠正
源码事实，**不表示 Production 已迁移**。

从原始基线至核心 commit，migration 目录只有新增
`V42__add_category_bulk_pricing.sql`；V1–V41 未修改。V42 只添加默认 false 的 capability
column、preview/ledger tables 与 index，不自动启用 Store，不更新菜单价格、历史订单
或 PrintJob。SQL SHA-256：
`2da89cb47b68df72c82492afed3298997e09bebc6be93b4554c54f74315e3a55`。

已直接读取独立本机 QA 证据：相同 JAR 在新空库成功应用 **V1–V42 / 42 success / 0
failed**；真实 HTTP 协议测试与六单/12 个持久化 Renderer snapshots 为 PASS。
协议测试 sentinel 最终连接数 **0**，complete/fail 明确是 `NO_PRINT` 模拟回报，且恢复
MOCK/空 endpoint 并撤销合成设备。它们不是 Production 或实际打印机证明。

## 4. 备份与回滚边界

现有受限恢复证据可读：`production-recovery-manifest.json` 的 07:16 UTC dump 与本机
`PRODUCTION_RECOVERY_ONLY.dump` 实际 SHA-256 一致，大小 360648 bytes，digest 为
`86e07a66560059d716a2be40efbbe2c4c8e62f26d5dc9786a1335eb178351d70`。
07:20 UTC 的 `production-recovery-verification.json` 记录 85 表恢复后 hash 一致、
约束/索引有效、临时恢复集群删除、`not_used_for_QA=true`。Reviewer 只读取这些记录和
文件 digest，未重新恢复或查询 Production；此前备份不替代最终发布时当前源的保存。

**回滚必须为旧 backend/frontend + 旧 Compose + 对应旧 env 的完整配对。**
旧 binary 的启动 guard 只接受 `test`、`MOCK/DISABLED`、endpoint off；直接使用新
Compose 的 `production/PAD_DIRECT` 环境会在 Flyway 前失败。新 policy 变量直接写在
Compose `environment` 中，**只恢复 `runtime.env` 或只换旧 JAR 不足以回滚**。

可追溯的旧配对依据为 `production-runtime-before.json` 的 07:05 UTC 观察：

| 旧输入 | 精确身份 |
| --- | --- |
| 旧 Compose | `138d0291ad4e72a191c19328bcef10efb9b43677:deployment/allo-nouilles/compose.yml`；digest `2de5ecc5ce6c07c83674610bb63bedf731816001e61e6f5854e3015a3de6679f`，与记录中的 Production Compose 一致 |
| 旧 backend image | `sha256:613327357035df7e16cbf50206891d7853dca02afd79b4bfbcbf8b13bb81e1d7` |
| 旧 frontend image | `sha256:e3ae049a8fcaf77311dca3173b5f5761168334672a8571df2e8cb7520cb24cc0` |
| 旧应用身份 | `2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9-allo-ws-915e4ff4d8a6`；保留已鉴权 WSS 补丁 |

回滚保留数据库、V42 schema/ledger、营业数据与全部历史快照；不能删除 Flyway 记录、
执行 repair 或用旧 dump 覆盖新增营业数据。此次未执行旧 binary 对 V42 的实际启动或
真实回滚，不宣称 rollback rehearsal PASS；其 Flyway 对 future migration 的实际配置
与旧配对完整性应在最终 Release 方案中确认。受限 dump 用于独立恢复保障，不是本轮
正常应用回滚步骤，更不能恢复进合成 QA。

## 5. Release GO 的剩余条件

下列属于本轮已要求的 Release 证据收口，不能由依赖扫描或代码 PASS 代替：

1. 用户要求的实际浏览器验收完整闭合，并将结果绑定相同源码/构建产物；本评估仅已读
   HTTP/Renderer 结果，没有把截图文件存在当作浏览器全部通过。
2. 发布前保存当前 source backup、实际 Compose/env/config 和旧应用引用，确认上述旧
   artifact/env 配对可恢复；保存日志/备份，不执行旧 dump 覆盖。
3. 固定不可变 backend/frontend 镜像、JAR/dist digest 与核心源码，验证目标仅为新机
   `43.166.242.250` / `VM-0-6-ubuntu`、project `allo-nouilles`、独立 DB。不得部署到
   Lanzhou 或更改其共享入口、网络、凭据、价格与数据。
4. 精确发布前核对 Store 仍为 MOCK、真实设备/endpoint 为 0、旧 pending/processing
   工作数为 0；不得改写历史 Job/Outbox 来制造通过。
   V42 迁移后核对 ledger/checksum，并对订单、选项、历史小票/PrintJob 等保护项作前后
   对照。新 capability/reconcile 只能执行明确批准的 Allô 当前配置动作，不能调真实价格。
5. 部署后执行既定 health、登录/API/WSS 与隔离 smoke/观察，记录精确运行身份与结果。
   本报告未观察任何新 Production 部署，不填运行 PASS。

只有主 Agent 用实际证据关闭这些条件后，才能给出该不可变批次的 Release GO。
任何真实 Pad 配对、Android Worker、打印机 codepage/纸宽与实体输出仍为 **NOT TESTED**。

## Execution Time Breakdown

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | Estimated 7 min | 读取实际 audit、维护者/Spring 公告、JAR、Compose/V42、恢复证据与 QA 结果。 |
| Implementation | Not required | 仅新增本报告，无代码或配置修改。 |
| Tests | Not executed | 只读现有结果；未重跑 audit、测试、恢复或运行验收。 |
| Agent 6 / PR / Merge | N/A | 独立评估子任务，无 commit/PR/merge。 |
| Staging Deploy | N/A | 未执行。 |
| Staging Acceptance | N/A | 未执行。 |
| Evidence / Governance | Estimated 3 min | 编写与核对本报告；不改 Gate 或其他文档。 |
| Total | Estimated 10 min | 自 08:21 UTC 开始；具体 Release 和硬件结果留待主 Agent 证据。 |

## Coordinator final candidate reference

The dependency assessment above was performed for the c242548 implementation;
e7dde0a6e27adf3be00baa9972fee06cdcc5482c adds only the bounded Allô update-receipt
monetary repair, its tests and contract documentation. Dependency lockfiles and
frontend source are unchanged. The independent review separately checked this
new code and actual saved-snapshot amount regression; see INDEPENDENT_REVIEW.md
and UPDATE_RECEIPT_AMOUNT_REGRESSION.md. Final23f3 adds only the read-only transaction repair with unchanged dependencies. Backend1520total/1403pass/117skip/0errors; final fixed-batch review and actual Production smoke passed. See the deployment report for runtime evidence; remaining browser coverage limits and hardware NOT_TESTED are preserved.

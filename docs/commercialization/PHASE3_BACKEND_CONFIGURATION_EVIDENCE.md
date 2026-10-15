# Phase 3 Backend Configuration Evidence

日期：2026-10-15；隔离 `/private/tmp/restaurant-commercial-config`，分支
`codex/commercial-phase3-config`，起点 `5ec8abe`。Root 确认 G2 通过后才实施。
已交稳定 scaffold `66c947bdb2941b2fa42b24202a110d8ff024776b`；后续 exact commit 由 Git/Root 记录。
本报告是实现者证据，**AGENT6_REVIEW_PENDING / ROOT_INTEGRATION_PENDING**，不自授阶段 PASS。

## 实施与归属

- 新 `merchant` package：typed entity/repository/read DTO、Owner GET/PUT、严格 schema/refs、revision CAS、
  审计/menu invalidation、统一 `isLanzhouCompatible` / `safeSummary`。
- V39 新配置表与明确旧安装/精确 Profile provenance backfill；首提交后字节未变，SHA256
  `c7e48bfdb352d7db88c903440a8b379c4985bebf461fed106011db65373f8e0e`。
- V40 public empty Neutral preset；精确 Neutral ledger 的 Master Menu null 约束，旧 requests 仍要求齐全。
- 现有 Owner business create/default catalog 的 Neutral 分支，复用 coordinator/materializer；显式私有兰州
  和旧 phase-b catalog 不改默认授权语义。配置与 Store graph 同事务初始化。
- StoreContext/MenuCatalog `safe_summary`、content hash 接线；两个设计文件描述唯一来源与后续边界。
- 测试与仅构造依赖/新 public preset 断言的现有测试适配。未改 Root 的 Order/打印/rules/renderers/
  StoreCombo 产品实现、前端、Offline、Android、V1–V38 或真实环境。

## 验证

Java17 + `JAVA_TOOL_OPTIONS=-Dnet.bytebuddy.experimental=true`。PG 均为本地 loopback 合成；
`CommercialPostgres` 每次创建并最终删除自己的随机 `commercial_*` 数据库，不复用其他 worker 的 DB。

| 验证 | 结果 |
| --- | --- |
| 完整 `mvn -q -f backend/pom.xml test`，仅启 `COMMERCIAL_TEST_POSTGRES_URL/USER` | 1038 total：967 执行 PASS，71 其他 opt-in skipped；0 failure/error |
| 最终四组 Merchant unit / Bearer HTTP / migration PG / real creation PG | 27 tests PASS，0 skipped/failure/error；最终资源 pointer 和非空历史 fixture 另重跑通过 |
| 相关10组 focused（含现有 Profile/Owner/workspace/menu regression） | 合计48 tests PASS，0 skipped/failure/error |
| 新 schema/全部 mapped entities 的 Hibernate validate | 真 PG creation test 通过 |
| fresh V1→V40、V38→V40、Flyway validate / 再 migrate 零改动 | PASS |
| 历史非空 order/item/option/task/print frozen payload/base64/hash、menu/旧 Profile JSON+fingerprint | migration 与 metadata update 前后完整 row hashes / 内容一致 |
| `git diff --check` / V1–V38 未改 / V39 immutable hash | PASS |

完整默认套件曾发现共享 catalog 的 legacy/Business 默认需分开，已修为原 private phase-b catalog +
明确 Neutral business catalog，并完整重跑通过。首次非空历史 fixture 缺既有 sort_order 必填值导致
两项测试失败，已补合成 fixture 值、重跑 PG 通过；未为 fixture 修改产品 schema 或业务代码。

71 项旧 PG opt-in 未在本工作树全量启用，Root 负责整合线启完整 PG/frontend/native 回归；
不能把本树默认 suite 说成全 PG zero-skip。前端、打印、真实硬件/安装/纸张不在本树验收。

核心可复现命令：

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
JAVA_TOOL_OPTIONS=-Dnet.bytebuddy.experimental=true \
COMMERCIAL_TEST_POSTGRES_URL=jdbc:postgresql://127.0.0.1:55439/commercial_synthetic \
COMMERCIAL_TEST_POSTGRES_USER=commercial_test \
mvn -q -f backend/pom.xml -Dtest=MerchantConfigurationServiceTest,MerchantConfigurationHttpTest,MerchantConfigurationPostgresTest,NeutralBusinessCreationPostgresTest test
```

## 保留限制与交接

CAD/当前税政策/timezone 都是读取现有计算来源的元数据，不支持从此 API 变更金额或时间计算。
Profile switch、任意 resource graph/import、通用 Modifier/Combo/TTL、P5 station routing、发布/硬件证明不在范围。
Missing/unsupported config 是 Neutral，不能回滚成按名字/marker 动态猜兰州。V39/V40 采用 forward fix，
不降级/删除配置、旧 queue 或业务历史。

Root 接入最终 SYSTEM/API/Database 与独立 review；本树停止产品编辑后可切换独立 UI Agent6 范围。
时间（Estimated）：Implementation 约18分钟，Validation 约4分钟，设计/报告约5分钟，Total 约27分钟。

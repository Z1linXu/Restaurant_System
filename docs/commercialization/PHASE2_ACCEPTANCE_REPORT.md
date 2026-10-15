# Phase 2 Acceptance Evidence Snapshot

快照日期：2026-10-15。已整合代码观察点 `739b7085c5ed354f4a8704874f2e53c9de071363`；Offline候选 `3985eac` 尚待独立复审及最终集成。本文是日期化证据，不取代 [CURRENT_STATE](../governance/CURRENT_STATE.yml) 的唯一当前 Phase/Package/Gate；执行主索引为 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，任务结果为 [Backlog](COMMERCIALIZATION_BACKLOG.md)。

**本快照不能关闭 Phase 2 gate，也不允许依赖 G2 的 Phase 3 代码改造。** 原因是 Offline 两项 P1 仍待独立复审、修复候选未完成集成，且最终全量测试尚未重跑。各已通过模块可以保留其独立审查结论，不把作者局部PASS、旧批次或编译成功当作整体验收。Owner 已授权1→2→3，不需重新询问同一权限，但技术门槛不能跳过。

## G2-01…09 证据矩阵

| Gate | 本快照结果 | 可追溯证据 / 尚缺动作 |
| --- | --- | --- |
| G2-01 组合基线 | PASS_LOCAL_SOURCE；发行未知保留 | [Manifest](PHASE2_BASELINE_MANIFEST.md)、[JSON](phase2-baseline.json)、[Runbook](PHASE2_SOURCE_CONTROL_RUNBOOK.md)；B/F/main relevant trees/ancestry、V1–37checksum、source refs与干净build可解释；`c9f8d0f`补同B deployment fixture。历史bit一致/实装APK不在此PASS内 |
| G2-02 Auth/resource scope | LOCAL_REVIEW_PASS；最终整合待验 | [Security Report](PHASE2_SECURITY_TEST_REPORT.md)；ADMIN业务deny、有效Store role、actual path/body/parent scope、Staff/Audit/Analytics；`9c87944`原始grants、`739b708`锁后refresh/重授权已独立关闭。完整支持grant延期且未授权业务访问默认deny |
| G2-03 WS | LOCAL_REVIEW_PASS；最终整合待验 | 真实本地broker11 + queuedframe1、SockJS websocket/XHR、origin、token到期/用户/membership/module撤权与重连；frontend token读取/cleanup。真实入口代理/物理Pad仍未认证 |
| G2-04 私有Profile | LOCAL_REVIEW_PASS；最终整合待验 | V38 conservative owner推导、catalog/children/create/use/clone/replay检查；PG fresh/upgrade/ambiguous、Bearer Profile HTTP拒绝foreign/version/operator、JSON/fingerprint未改。无publish API；未绑定legacy owner仍deny |
| G2-05 Print/Device | LOCAL_REVIEW_PASS；物理QA待后续 | 实际Store/Org/token关系、foreign printer/七Pad操作负向、claim/lease/历史payload回归。全部MOCK/合成，无真实endpoint/任务/出纸；P7认证硬件另gate |
| G2-06 Offline/Web/Android | **REVIEW_BLOCKED / 候选待复审** | [安全报告](PHASE2_SECURITY_TEST_REPORT.md#offline-独立审查仍待关闭的-p1) 记录 A6-OFF-01 307手动pending泄密与 A6-OFF-02 token/snapshot错账户的独立复现；[Offline工作项](PHASE2_OFFLINE_ANDROID_SECURITY.md) 原始739快照仍是初版，候选分支追加修复/复审章。作者候选3985eac有局部tests，需另一Reviewer正式关闭、root整合后运行跨模块回归 |
| G2-07 BOM/库存 | LOCAL_REVIEW_PASS；最终整合待验 | 聚合需求、稳定顺序同店锁、MANDATORY订单事务、locked-refresh submit；PG竞争与ledger守恒/foreign零部分写/失败回滚、重复submit；完整restock/prep仍NOT_IMPLEMENTED |
| G2-08 回归/CI/环境 | PARTIAL；最终全量待验 | earlier root968全PG PASS；初集成1003有1fail，fixture关系修后Uber57与Auth focused67 PASS；frontend294/build PASS；最终代码上的backend全PG/frontend/native及必需checks待重跑。staticworkflow定义与本地等价命令已PASS，不声称remote CI成功 |
| G2-09 集成/证据/范围 | **PENDING，不关闭** | root与Auth独立审查PASS；Offline复审与最终alltest缺失。Draft PR #275/#276不等main merge。P0/P1、checksum/snapshot、docs/rollback/执行表须在最终exactSHA复核后由主线程关闭并同步唯一CURRENT_STATE |

`LOCAL_REVIEW_PASS` 是受限模块结论，不是整个Phase DONE。本文没有将 `NOT RUN`、skip、已知失败、候选修复或不明来源降级为PASS。

## P0 / P1 与后续限制

| 分类 | 处置 |
| --- | --- |
| 本批已识别P0 Auth/WS | 本地代码修复与独立审查通过；最终完整集成还需确认不回退。不据同品牌多店或独立DB替代负向测试 |
| 新P1 A6-OFF-01/02 | 保留原独立复现、候选修复3985eac与待复审标记；在关闭前阻断G2-06/09 |
| 本批已关闭P1 cluster | private Profile/device/BOM；dormant grant与OSIV锁后授权；scanner格式/实际fixture范围；详见安全报告的攻击链与tests |
| 预存P1 KDS完整角色流程 | 纯HOT_KITCHEN仍依赖通用Order详情权限；以P5厨房-onlyDTO/页面契约解决，本轮不扩厨房财务访问，不宣称全角色运营验收 |
| P4业务待定值 | 有限旧价TTL/服务端来源/版本契约、完整Modifier/Combo/税规则未实现；Q09决定保持，数值不猜，历史/outbox不改 |
| 发行/真实账号未知 | 已装APK/签名/asset、真实账号ADMIN/legacy依赖、physical/WebView/Keystore长期状态、认证型号/税语言/restore承诺待相应后续gate；不作为已认证营销承诺 |

## Combined Baseline 与 source gaps

| 轴 / 缺口 | 分级与允许范围 |
| --- | --- |
| SOURCE_LINEAGE_CONFIRMED | B后端/F前端/V37分别记录，main应用树关系已解释，旧Owner worktree受保护，商业线隔离。此证据证明来源关系，不能合成一个不存在的统一生产commit |
| LOCAL_BUILD_VERIFIED | 同源archive实际构建/测试，新JAR/dist hash见Manifest。PG optional skip如实记录，后续已有root968全PG结果但不是最终integratedalltest |
| PRODUCTION_BIT_REPRO_UNVERIFIED | `BASELINE_GAP / NON_BLOCKING_LOCAL_DEVELOPMENT_GAP`：floatingbase/历史toolchain/buildargs/SBOM未全核。阻断历史artifact逐字节复现与发行来源强保证，不阻止已有可追溯来源上的隔离安全开发 |
| APK_UNKNOWN / HARDWARE_QA_PENDING / PACKAGING_PENDING | `NON_BLOCKING_LOCAL_DEVELOPMENT_GAP`：实装/签名/asset与纸张未证；DB5代码与旧受跟踪DB4 assets的不一致由既有guard拒绝，未绕过打包/发布。P3前端稳定后可按已授权本地范围复用既有sync/verify做packaging smoke；真实安装/分发/出纸仍未授权 |
| 来源不明、checksum drift、失败build、危险test target | `BLOCKING_BASELINE_GAP`，不可降级成“未知”。本快照未发现此类新source gap；这不抹去Offline已知P1或最终集成未验 |

同一 canonical API origin 不得重新分配给另一商家/独立部署：目前以稳定origin作为本机environment identity，没有发明后端deployment-ID API。相同origin替换后端且数字ID全复用的检测能力未实现，须在改变此安装契约前另立明确identity/恢复方案。

## 测试与复现入口

完整计数、失败修复与命令见 [Security/Test Report](PHASE2_SECURITY_TEST_REPORT.md#测试账本不要合并不同阶段的计数)；原始source/boundary/Auth/Offline报告分别保留其实际范围。主线程最终重跑须在同一个结果SHA记录：

1. Java17 backend全量与六类optional PostgreSQL测试实际开启，全部目标为本地PG16.14、port55439、专用`commercial_*`合成库；保存0 failure/error/skip或逐项真实失败原因。
2. frontend全量tests、TypeScript/Vite，以及受变更影响的native源码/JVM tests；CI/staticguard/migrationchecksum/链接校验；明确lint为已有5 errors/6 warnings而非全库清洁。
3. 确认独立Auth/root/Offline review均覆盖最终修复集合；对新变化只重审受影响边界，不重复重建无变化的生产证据。
4. 复核历史order/task/print snapshot、DB4→5旧queue保留、重试key/hash、两Org负向、实际手动native入口和credential-associated offline auth。结果未知即不关闭对应gate。

## Phase 3 entry checklist

只有以下条件由主线程提供最终证据，才可依据已有Owner授权继续P3：G2-01…09全满足；本阶段P0与新P1全部关闭；Offline3985eac或其后继修复独立复审PASS并整合；最终exactSHA全量回归通过；API/SYSTEM/Backlog/HC-DG/风险/rollback同步；来源与hardwaresource未知的限制仍明确；Production/Staging没有mutation。不能因为文档已提交或Draft PR存在开P3。

P3仍仅配置/Profile骨架、Neutral fixture、兼容与typed reader，不能抢跑完整Modifier/Combo/price或PDF onboarding。Phase4未授权；本轮也未授权main merge、Production/Staging部署、真实数据/凭据/硬件操作。

## Rollback 与恢复

本地安全修复回退必须保留defaultdeny；必要时停模块/WS/native调度，不能恢复已复现路径。V38只作additive metadata，保留列与deny、采用forwardfix；不修改已应用V1–37。历史订单/厨房/打印job不重算；库存不重放流水；物理不确定打印不自动重试。

DB5保持兼容修复：旧未知环境records完整隔离，不凭当前URL/相同ID恢复，不clear应用数据，不直接降旧DB4 APK；旧native明文配对/打印安全记录不自动认领或宣称已全量加密。真实legacy恢复与credential撤销要有原环境/账户证明和独立授权。本轮本地合成库可由guardedhelper重建，不等于可在线restore。

## Product repair 与 blocking tooling

产品改动为auth/scope/WS、Profile、device/printing、库存与Offline隔离。工具只包括来源/checksum guard、新commercial默认/fixture扫描，以及治理validator从全磁盘rglob改为Git tracked+nonignored新Markdown，避免误扫npm/Gradle生成依赖的brokenREADME；仓库源码链接校验保留。这些是关闭当前验收所需的有界`BLOCKING`小修，不是部署、证据或发布生命周期框架。没有新增远程部署/清库/密钥系统。

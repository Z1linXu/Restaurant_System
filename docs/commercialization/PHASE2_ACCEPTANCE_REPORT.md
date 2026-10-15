# Phase 2 Acceptance Evidence Snapshot

快照日期：2026-10-15。最终集成代码观察点 `5ec8abeb7a67760000b29151e7f568a88a8f36ed`。本文为日期化验收证据，不取代 [CURRENT_STATE](../governance/CURRENT_STATE.yml) 的唯一当前 Phase/Package/Gate；执行主索引为 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，任务结果为 [Backlog](COMMERCIALIZATION_BACKLOG.md)。

**G2-01…09 隔离本地验收 PASS；本阶段未关闭 P0=0、新 P1=0。PHASE 2 COMPLETE — READY FOR PHASE 3 REVIEW。** BASE、root boundaries、Auth/WS、Offline 均经独立审查；最终后端全 optional PG 1013/0 failure/0 error/0 skip、frontend306/build、本地 DB5 assets/debug APK 与 guard 开启的 native24均通过。Owner已授权连续1→2→3，主线程同步唯一 CURRENT_STATE 后按既定边界继续，无需再次询问相同权限。此结论只允许可追溯来源上的隔离本地开发，不是发行、main merge、远端CI或真实环境认证。

## G2-01…09 证据矩阵

| Gate | 本快照结果 | 可追溯证据 / 明确限制 |
| --- | --- | --- |
| G2-01 组合基线 | **PASS_LOCAL**；发行未知保留 | [Manifest](PHASE2_BASELINE_MANIFEST.md)、[JSON](phase2-baseline.json)、[Runbook](PHASE2_SOURCE_CONTROL_RUNBOOK.md)；B/F/main relevant trees/ancestry、V1–37checksum、source refs与干净build可解释；`c9f8d0f`补同B deployment fixture。历史bit一致与实装APK不在此PASS内 |
| G2-02 Auth/resource scope | **PASS_LOCAL** | [Security Report](PHASE2_SECURITY_TEST_REPORT.md)；ADMIN业务deny、有效Store role、actual path/body/parent scope、Staff/Audit/Analytics；`9c87944`原始grants、`739b708`锁后refresh/重授权独立关闭；最终1013回归通过。完整支持grant延期，未授权业务访问默认deny |
| G2-03 WS | **PASS_LOCAL** | 真实本地broker11 + queuedframe1、SockJS websocket/XHR、origin、token到期/用户/membership/module撤权与重连；frontend最新token读取/cleanup，最终306回归通过。真实入口代理/物理Pad未认证 |
| G2-04 私有Profile | **PASS_LOCAL** | V38 conservative owner推导、catalog/children/create/use/clone/replay检查；PG fresh/upgrade/ambiguous、Bearer Profile HTTP拒绝foreign/version/operator、JSON/fingerprint未改，最终回归通过。无publish API；未绑定legacy owner仍deny |
| G2-05 Print/Device | **PASS_LOCAL**；物理QA后续 | 实际Store/Org/token关系、foreign printer/七Pad操作与list/ack/reprint Bearer负向、claim/lease/历史payload回归。全部MOCK/合成，无真实endpoint/任务/出纸；P7认证硬件另gate |
| G2-06 Offline/Web/Android | **PASS_LOCAL** | [安全报告](PHASE2_SECURITY_TEST_REPORT.md#offline-独立审查-p1-修复与关闭)保留A6-OFF-01手动pending 307泄密与A6-OFF-02错账户snapshot的原始复现；`3985eac`修复→`1609f0d`集成，独立`c824d21`复审→`c2fa49a`报告，两个P1 CLOSED。DB5/namespace/credential association/no-redirect/迟到响应通过；本地assets/debugAPK及guard开启native24通过，实机未证 |
| G2-07 BOM/库存 | **PASS_LOCAL** | 聚合需求、稳定顺序同店锁、MANDATORY订单事务、locked-refresh submit；PG竞争与ledger守恒/foreign零部分写/失败回滚、重复submit均通过最终全量；完整restock/prep仍NOT_IMPLEMENTED |
| G2-08 回归/CI/环境 | **PASS_LOCAL**；不声称remote CI | 最终`5ec8abe` Java17 clean verify **1013/0 failure/0 error/0 skip**、frontend53files306/build PASS；等价产品树`1609f0d`本地54assets/DB5/debugAPK PASS与native6suites24PASS，assets guard开启。baseline/scanner3tests/governance/diff PASS。旧失败轮次及修复原因在测试账本分别保留；lint残余5errors/6warnings属未改文件既有债务 |
| G2-09 集成/证据/范围 | **PASS_LOCAL** | BASE/root/Auth/Offline与最后`6e9a370`测试slice机械修复均独立review PASS；最终产品树/计数/HC-DG/rollback已入四份证据文档。本阶段未关闭P0=0/新P1=0。主线程负责CURRENT_STATE、SYSTEM及最终PR路由；Draft PR不等main merge，物理/历史bit未知不被降格为已认证 |

`PASS_LOCAL` 覆盖各行明确的本地合成范围。没有将 NOT RUN、skip、早期失败、作者候选测试、remote CI或物理未知写成PASS。完整历史账本保留初集成1003/1fail、后续1013/3errors、保留旧合成库1013/1fail，以及全新合成库最终1013全部通过的不同轮次，不相加或覆盖失败。

## P0 / P1 与后续限制

| 分类 | 处置 |
| --- | --- |
| 本阶段未关闭 P0 / 新 P1 | **0 / 0**；Auth/WS与私有Profile/device/BOM边界、本阶段新发现的dormant grant、OSIV锁后授权、Offline两P1均已修复并经独立审查；最终集成全量通过 |
| A6-OFF-01/02 | CLOSED；原始复现与修复/独立证据均保留，未靠删除旧记录/清queue规避反例 |
| 工具与fixture阻断项 | archive deployment fixture、scanner quotedkey/URL/实际fixture scope、治理validator排除generated依赖、menu测试slice依赖及合成membership关系均有界修复；未放宽产品scope。保留旧合成恢复记录的fullrun失败以全新隔离DB重跑解决，无产品改动 |
| 预存P1 KDS完整角色流程 | 纯HOT_KITCHEN仍依赖通用Order详情权限；以P5-KITCHEN-01厨房-onlyDTO/页面契约解决，本轮不扩厨房财务访问，不宣称全角色运营验收。本项不是本阶段新P1，也未标CLOSED |
| P4业务待定值 | 有限旧价TTL/服务端来源/版本契约、完整Modifier/Combo/税规则未实现；Q09决定保持，数值不猜，历史/outbox不改 |
| 发行/真实账号未知 | 实装APK/签名/asset、真实账号ADMIN/legacy依赖、physical/WebView/Keystore长期状态、认证型号/税语言/restore承诺待后续gate；不作为已认证营销承诺 |

## Combined Baseline 与 source gaps

| 轴 / 缺口 | 分级与允许范围 |
| --- | --- |
| SOURCE_LINEAGE_CONFIRMED | B后端/F前端/V37分别记录，main应用树关系已解释，旧Owner worktree受保护，商业线隔离。此证据证明来源关系，不能合成不存在的统一生产commit |
| LOCAL_BUILD_VERIFIED | 同源archive实际构建/测试，新基线JAR/dist hash见Manifest；最终商业源码`5ec8abe`完整Java17/allPG1013与frontend306/build通过。早期optional skip与失败真实保留，不代替最终结果 |
| PRODUCTION_BIT_REPRO_UNVERIFIED | `BASELINE_GAP / NON_BLOCKING_LOCAL_DEVELOPMENT_GAP`：floatingbase/历史toolchain/buildargs/SBOM未全核。阻断历史artifact逐字节复现与发行来源强保证，不阻止已有可追溯来源上的隔离安全开发 |
| LOCAL_DEBUG_PACKAGING_PASS | 新本地构建的54assets/DB5/guard/debugAPK通过；SHA-256=`fad05caed96762ce60bb3cd8cab9b0449219430bfb9d97c831f154a8db801899`。`1609f0d`与`5ec8abe`frontend及restaurant-pad-app trees逐一相同，非凭短SHA猜等价。未修改受跟踪旧assets或把guard绕过作为验收 |
| APK_UNKNOWN / HARDWARE_QA_PENDING | `NON_BLOCKING_LOCAL_DEVELOPMENT_GAP`：现装/发行签名/asset、安装升级/Keystore/WebView及真实出纸仍未证；本次local debug packaging不关闭这些未知。阻断相应发行/硬件兼容承诺，保留P7/P8验证 |
| 来源不明、checksum drift、失败build、危险test target | `BLOCKING_BASELINE_GAP`，不可降级成“未知”。最终本地验收未残留此类问题；早期已知失败分别修复/解释并全量重跑，不被忽略 |

同一 canonical API origin 不得重新分配给另一商家/独立部署：目前以稳定origin作为本机environment identity，没有发明后端deployment-ID API。相同origin替换后端且数字ID全复用的检测能力未实现，须在改变此安装契约前另立明确identity/恢复方案。

## 测试与复现入口

完整计数、失败修复、源码身份与命令见 [Security/Test Report](PHASE2_SECURITY_TEST_REPORT.md#测试账本不要合并不同阶段的计数)；原始source/boundary/Auth/Offline报告保留各自日期与范围，后续最终证据不改写旧报告当时的pending。

1. 最终Java17 backend全量与六类optional PostgreSQL测试实际开启，全部目标为本地PG16.14、port55439、专用`commercial_*`合成库；全新Uber/clone/rules目标避免旧合成记录污染，1013/0/0/0，日志`phase2-fresh-integrated-backend.log`，BUILD SUCCESS。
2. frontend53files/306tests、TypeScript/Vite，本地debugAPK/54assets/DB5与guard开启native6suites/24tests通过；baseline/staticguard/scanner3tests/governance/diff通过；lint保留已有5errors/6warnings，不声称全库清洁。
3. 独立BASE/root/Auth/Offline reviews覆盖最终修复集合，`6e9a370`仅补menu JPA slice未用user-onlymock，未弱化User真实授权测试。最后产品树没有未经review的业务修复。
4. 原回归保护历史order/task/print snapshot、DB4→5旧queue保留、稳定retry key/hash和双Org负向；实际手动native入口与credential-associated offlineauth有合成测试。此范围不扩张为真实安装/纸张或所有角色KDS验收。

## Phase 3 entry checklist

本观察点已满足G2-01…09本地门槛、阶段P0/新P1关闭、Offline独立复审及整合、最终exactSHA全量回归、API/SYSTEM/Backlog/HC-DG证据与rollback路由；来源/硬件未知保留，Production/Staging零mutation。主线程将结果同步唯一CURRENT_STATE后可依已有Owner授权进入P3；本文不另立当前阶段状态，也不以文档提交或Draft PR替代门槛。

P3仍仅typed MerchantConfiguration与validated Store-scoped读写、revision/cache metadata、private legacy/Neutral边界，复用StoreProfile、Canonical Add-on及已有菜单API。Neutral针对新空白Store，不复制菜单/桌台/人员/assignments；现Owner同品牌master materialization保留PRIVATE legacycompat。不能因通用Store创建入口已加授权而重新放宽该入口；新路径须显式且有界。不抢完整菜单/renderer/Modifier/Combo/price引擎或PDF onboarding，完整导入/向导留P6，Phase4未授权。此证据提交不实施P3。

## Rollback 与恢复

本地安全修复回退必须保留defaultdeny；必要时停模块/WS/native调度，不能恢复已复现路径。V38只作additive metadata，保留列与deny、采用forwardfix；不修改已应用V1–37。历史订单/厨房/打印job不重算；库存不重放流水；物理不确定打印不自动重试。

DB5保持兼容修复：旧未知环境records完整隔离，不凭当前URL/相同ID恢复，不clear应用数据，不直接降旧DB4 APK；旧native明文配对/打印安全记录不自动认领或宣称已全量加密。缺credential association的旧authsnapshot需在线验证，不删除draft/outbox。真实legacy恢复与credential撤销要有原环境/账户证明和独立授权。本轮本地合成库可由guardedhelper重建，不等于可在线restore。

## Product repair 与 blocking tooling

产品改动为auth/scope/WS、Profile、device/printing、库存与Offline隔离。工具只包括来源/checksum guard、新commercial默认/fixture扫描，以及治理validator从全磁盘rglob改为Git tracked+nonignored新Markdown，避免误扫npm/Gradle生成依赖的brokenREADME；仓库源码链接校验保留。这些是关闭当前验收所需的有界`BLOCKING`小修，不是部署、证据或发布生命周期框架。既有packaging流程只在临时本地工作树复用，没有新增远程部署/清库/密钥系统。

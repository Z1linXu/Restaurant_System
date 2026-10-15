# Commercialization Master Planbook

本文件是商业化后续 Codex 工作的唯一执行主索引，负责找到任务与依赖；不保存第二份“当前 Phase/Package/Gate/Stop”。唯一动态状态与授权边界在 [CURRENT_STATE.yml](../governance/CURRENT_STATE.yml)，优先级遵守 [AUTHORITY](../governance/AUTHORITY.md)。[Backlog](COMMERCIALIZATION_BACKLOG.md) 记录任务级结果及 HC/DG disposition，不能自行开 Phase。

Owner 已明确要求连续执行 Phase 1→2→3；这构成本次隔离本地实施的阶段授权，阶段间不用重复询问。技术 gate 未过不得跳阶段。主线程必须把本次授权同步到 CURRENT_STATE；旧的 Phase B stop 及 Phase 0 待决策文字保留历史身份，不继续当本轮待批准问题。本文件不授权 Phase 4，不授权任何 Production/Staging 变更或未授权远端操作。

## 阅读与事实入口

启动：AGENTS→AUTHORITY→CURRENT_STATE；再按 [Agile Loop](COMMERCIALIZATION_AGILE_LOOP.md) 核 fresh Git 和执行边界。任务事实读取 [SYSTEM](../../SYSTEM_DOCUMENTATION.md)、[API](../../doc/API.md)、[DatabaseDesign](../../doc/DatabaseDesign.md) 和存在的 [MVP Scope](../../doc/MVP_Scope.md)。旧用户路径 `docs/API_bilingual.md` 不存在；不能假设缺失 API。当前代码/现有 contracts 高于旧 MVP 描述。

| 索引 | 内容 |
| --- | --- |
| [OWNER_DECISIONS](OWNER_DECISIONS.md) | 20 项已确认选择、后果和范围 |
| [COMMERCIAL_ARCHITECTURE](COMMERCIAL_ARCHITECTURE.md) | Core/Modules/Profile、唯一配置来源、兼容与安全 |
| [COMMERCIALIZATION_BACKLOG](COMMERCIALIZATION_BACKLOG.md) | 每 PR 目标/路径/依赖/步骤/测试/证据/风险/rollback/DoD/并行，53 HC 与 22 DG 全映射 |
| [COMMERCIALIZATION_AGILE_LOOP](COMMERCIALIZATION_AGILE_LOOP.md) | 已授权本地持续执行、review/PR、真实 blockers 与停机规则 |
| [MERCHANT_ONBOARDING_DESIGN](MERCHANT_ONBOARDING_DESIGN.md) | 外部 Codex 配置交付，manifest/dry-run/恢复设计 |
| [COMMERCIAL_ACCEPTANCE_MATRIX](COMMERCIAL_ACCEPTANCE_MATRIX.md) | G2/G3 技术门槛、风险/测试证据与后续上市验收 |
| [P2 Security/Test Report](PHASE2_SECURITY_TEST_REPORT.md) / [P2 Acceptance Snapshot](PHASE2_ACCEPTANCE_REPORT.md) | 5ec8abe最终代码观察点的攻击链、分批失败/修复、独立review、G2本地PASS与发行限制；不取代CURRENT_STATE |
| [P3 Acceptance Snapshot](PHASE3_ACCEPTANCE_REPORT.md) / [P4 Entry Gates](PHASE4_ENTRY_GATES.md) | backend711638e观察点的有界实现、六任务/13项P3HC、真实自动证据与视觉NOT RUN；P4只列门槛、不授权实现 |
| [Phase 0 01](01_PRODUCTION_BASELINE_AUDIT.md) / [02](02_SYSTEM_ARCHITECTURE_AUDIT.md) / [03](03_HARDCODE_AND_CUSTOMIZATION_INVENTORY.md) / [04](04_COMMERCIAL_ARCHITECTURE_OPTIONS.md) / [05](05_DOCUMENTATION_GAP_REPORT.md) / [06](06_OWNER_DECISION_QUESTIONS.md) | 日期化证据和原 HC/DG 描述；不是动态状态/授权 |

## Phase 边界

| Phase | 交付边界 | Entry / Exit |
| --- | --- | --- |
| 1 | 本七文档，20 决策展开、架构、可执行 PR、追踪与验收 | 文档一致、链接/53+22 覆盖；主线程 review 后进入已授权 P2 |
| 2 | 组合基线、隔离商业线、Auth/WS、Private Profile、Print/Device、offline/Android 环境、库存安全、合成 fixture/CI | 以 G2-01…G2-09 证据全过为 exit；未知分级不能掩盖 P0 |
| 3 | Config contract/typed reader、private Lanzhou/Neutral 边界、最小 frontend/module 读取、双 Profile 兼容 | 只有 G2 全过才写依赖性改造；G3-01…G3-08 全过结束 |
| 4 | Universal Menu/Modifier/Combo/price/old-price policy | 需另授权；不混入 P3，不引入脚本/嵌套促销 |
| 5 | Kitchen/receipt presets/table/基础报表/可选 Uber 的通用配置 | 需 P4 稳定语义与另授权；保留旧纸张行为 |
| 6 | 配置导入 validator/dry-run/diff/idempotent executor、Codex playbook/prompt、临时支持 grant | 需 P3–5 contracts，另授权；PDF 在外部会话阅读 |
| 7 | 通用签名 Android、商家部署/备份恢复/secret 生命周期 | 需前述安全和工具，另授权；物理/恢复证据补齐 |
| 8 | 首批真实 Pilot，跨业态验证及地域/语言/税务验收 | 需完整交付链与每商家单独发布批准；不是 P3 完成的同义词 |

## 推荐执行依赖

P2-BASE-01 → P2-AUTH-02 → P2-WS-03；Auth contract/接口既定后，P2-PROFILE-04 / P2-PRINT-05 可按不同文件并行；P2-OFFLINE-06 可从 BASE 的独立分支同步实现，不改 Auth 公共函数签名，最终集成/验收必须等待 AUTH 通过。P2-INV-07 与 Auth 接口稳定后独立实现；P2-FIXTURE-08 可在 BASE 后先建设 fixture，负向断言等待各实现。所有这些 PR 和测试结果 → P2-GATE-09。

P2-GATE-09 → P3-CONFIG-01 → P3-PROFILE-02 → P3-READ-03 → P3-UI-04；P3-FIXTURE-05 在 CONFIG 稳定后可独立写合成验证，最终 P3-GATE-06 汇总集成证据。出现实际文件重叠时串行，不为形式并行修改同一 Auth、DTO、Schema、Order Snapshot 或打印状态机。

后续链：P3-GATE-06 → P4-MENU-01 → P4-MODIFIER-02 → P4-COMBO-03 → P4-PRICE-04 → P5-KITCHEN-01 / P5-TABLE-03 / P5-REPORT-05；Kitchen→P5-PRINT-02；P5-INTEGRATION-04 依赖 Kitchen/Price。P6-IMPORT-01 等待所有可导入实体契约；P6-PLAYBOOK-02 等待工具，P6-SUPPORT-03 独立于菜单但依赖安全契约。P7-ANDROID-01 / P7-DEPLOY-02 → P8-PILOT-01。

以上为任务依赖设计，所有后续阶段任务保持 `PLANNED`；不是阶段自动授权。任务详细 DoD 与证据在 Backlog 的相同 ID。

## P2 已执行证据路由 — 2026-10-15

最终代码观察点 `5ec8abeb7a67760000b29151e7f568a88a8f36ed`：BASE、root boundaries、Auth/WS、Offline及最后测试slice机械修复独立review均PASS；G2-01…09隔离本地证据满足，未关闭P0=0/新P1=0。最终backend Java17/all optional PG1013项0failure/error/skip、frontend53files306/build、同产品树1609f0d本地54assets/DB5/debugAPK及guard开启native6suites24均PASS。早期失败批次/fixture修复与全新合成DB重跑分别记账，未用旧968或focused67代替最终全量。

任务级结果见 [Backlog](COMMERCIALIZATION_BACKLOG.md#phase-2-执行快照--2026-10-15)；[Security/Test Report](PHASE2_SECURITY_TEST_REPORT.md) 记录source/PR/commit、攻击链与测试账本；[Acceptance Report](PHASE2_ACCEPTANCE_REPORT.md) 逐G2列本地PASS、sourcegap分级、rollback与P3边界。它们为日期化证据，唯一动态Phase/Package/Gate仍是CURRENT_STATE，由主线程同步后继续已授权P3；此文档不实施P3、不创建第二状态。历史bit/实装APK/硬件/remoteCI未被本地PASS覆盖，预存KDS完整角色流程继续P5处理。

## P3 已执行证据路由 — 2026-10-15

backend`711638e`已完成typedconfig/V39、Neutral/V40、safe_summary consumer与`57fe6ad`打印适配；最终Java17/全部六类PG1050/0failure/0error/0skip、真实loopback HTTP14/14、backend/printing独立review通过。旧142/136/27/57/1047等分批证据保留，不相加或冒充最终count。UI刷新delta已整合55198bc，Root npm ci后最终61files363/build PASS；另一独立Reviewer14files101/build/diff PASS，Provider stale/defaultdialog P1 CLOSED；P3本地54assets/DB5/debugAPK/native6suites24通过。本批新增P0=0/未关闭新P1=0，预存KDS角色P1继续P5，未称全角色已运营。

**原G3-04要求的768/1024实际截图/布局QA NOT RUN：浏览器工具被用户权限策略拒绝，未绕过。G3-08 BLOCKED，不能宣布Phase3DONE。** 基础代码与自动测试完成可如实记录，完整阶段结论保持`PHASE 3 PARTIAL/BLOCKED — REMAINING WORK LISTED`；不能把DOM/HTTP或重新命名localfoundation当原视觉gate的PASS。六任务执行表见 [Backlog](COMMERCIALIZATION_BACKLOG.md#phase-3-执行快照--2026-10-15)，逐Gate与13项P3HC见 [P3验收报告](PHASE3_ACCEPTANCE_REPORT.md)，有界实现见 [Architecture](COMMERCIAL_ARCHITECTURE.md#phase-3-有界实现观察--2026-10-15)。

配置/Profile技术合同由实现者维护并已与后端核对；此索引不另立schema/动态状态。Draft [PR #278](https://github.com/Z1linXu/Restaurant_System/pull/278)已创建为可审查交付，base为P2安全/离线branch而非main。后续只补已授权P3尚缺视觉证据；Root核finalhead/remoteCI并同步唯一CURRENT_STATE；不绕过浏览器权限策略，不进行runtime mutation。[Phase4门槛](PHASE4_ENTRY_GATES.md)仅路由既定任务/兼容债务，Phase4未授权。Canonical现SoT、Neutral完整snapshotadapter不代表全菜单/renderer通用；历史bit/实装/硬件未知继续保留。npm audit对照Phase2与P3漏洞完整对象相同，引入0、仍有既有13项（1critical/7high/4moderate/1low）；隔离本地non-blocking，外部release需适用性/patchreview，不宣称zero漏洞。

## Combined Production Baseline 的复现定义

P2-BASE-01 产出 `PHASE2_BASELINE_MANIFEST.md`、`PHASE2_SOURCE_CONTROL_RUNBOOK.md` 与安全校验工具，记录：B/F exact SHA 和各源码 tree IDs、Git ancestry/diff、image ID/OCI revision/已有 release evidence、Dockerfile/dependency lock/toolchain/build args、V1–V37 scripts/checksums、配置契约/来源（不含值）、APK/package/version/signing/hash/bundled asset 及 API/schema 兼容区间。APK 未读到就写 UNKNOWN；不能用源码 versionCode=4 推断安装事实。

干净构建分别从可追溯来源或证明 tree 等价的统一商业 source 构建，记录新本地产物 hash 和测试。保留 B/F/main source refs，不覆盖 Owner 旧 main `ba169ed` 与既有更改。不能把两个版本文件复制拼接后称原生产源码。本轮采用 local integration 与可审查的 stacked Draft PRs，不自动 merge origin/main。现 main 与 B 应用树关系可解释时可作为商业线来源，但必须声明“新商业构建”，不能将新的 hash 冒充历史镜像。

基线证据分四轴：`SOURCE_LINEAGE_CONFIRMED`、`LOCAL_BUILD_VERIFIED`、`PRODUCTION_BIT_REPRO_UNVERIFIED`、`APK_UNKNOWN/HARDWARE_QA_PENDING`。生产 bit-for-bit、签名/SBOM、安装 APK 未核验都登记 `BASELINE_GAP` 和影响范围，绝不写“完整生产已重现”。其中源码/产物关系无法解释、迁移 checksum 不一致、依赖环境/配置不足以做安全本地构建是 **BLOCKING_BASELINE_GAP**，阻断 G2 与 P3；已追踪 B/F/V37 并完成干净本地构建后，历史 bit 一致与物理 APK 不确定可列 **NON_BLOCKING_LOCAL_DEVELOPMENT_GAP**，只允许隔离本地安全开发，阻断相应对客兼容承诺/发布。G2 报告必须逐项说明此区分，不能把失败测试或来源不明降级成未知。

Hotfix：现餐厅独立 exact release → 最小 hotfix PR → 双 Profile/安全回归 → 审查后进入共享 Core；商业改动回餐厅需独立 release manifest、验收与批准。main merge 不自动更新现生产。

## 阶段门槛与停止

Phase 2 只有关键基线可追溯、商业线隔离、所有 P0 关闭、API/WS/Profile/Print/Device/offline/库存跨边界负向测试与关键回归 PASS、证据回写才可宣布 `PHASE 2 COMPLETE — READY FOR PHASE 3 REVIEW`。本次已有 Phase 3 授权，主线程核 G2 后继续；任何未关闭 P0 或 blocking baseline gap 只能 `PHASE 2 BLOCKED — REQUIRED ACTIONS LISTED`，不得跳入 P3。

Phase 3 需真实 Neutral 初始化、现店兼容、配置继承/版本/缓存、持续安全负向与历史 snapshots PASS；不能以编译通过结案。全过才可宣布 `PHASE 3 COMPLETE — READY FOR PHASE 4 REVIEW`，随后停止；否则 `PHASE 3 PARTIAL/BLOCKED — REMAINING WORK LISTED`。Phase 4 尚未授权。

遇到需线上数据/服务、不可解释来源、破坏 schema/history、架构与已定业务冲突或未授权远端动作，停止受影响任务并继续无依赖任务。所有 Production/Staging mutation 均禁止；旧 runbook 的 standing Staging 规则不覆盖本次明确限制。完整实机/税务/法语/恢复证据留给相应上市 gate，不能虚报已经认证。

## 完整性与未知项

Backlog 应保持 HC-001…HC-053 各一主处置、DG-01…DG-22 各一关闭/延期归属，且每个任务引用存在、无循环依赖。文档阶段验证 Markdown 本地链接、20 决策、53/22 coverage、`git diff --check`；不为文档改动跑应用构建或部署。

事实未知：安装 APK/签名/asset 来源；bit 可重现生产镜像；完整配置/云 secret 权限；认证硬件和长期 Worker；旧价具体期限；恢复/RPO/RTO/retention；目标商家税与语言义务。记录未知的责任任务/发布限制，不据此重问已决策问题或偷偷完成生产探测。

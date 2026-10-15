# Commercialization Agile Loop

执行入口为 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，任务及追踪见 [Backlog](COMMERCIALIZATION_BACKLOG.md)。本文件是商业化的受限执行规则，不另建当前 Phase/Package/Gate；唯一状态在 [CURRENT_STATE](../governance/CURRENT_STATE.yml)，永久规则在 [AUTHORITY](../governance/AUTHORITY.md)。

Owner 的本轮授权覆盖隔离本地 Phase 1→2→3 连续推进，不必每 PR/每阶段重复询问。Phase 2 的技术门槛必须全过才启动 Phase 3；Phase 4 未授权。所有 Production 和 Staging 变更均禁止，包含部署、重启、迁移、配对、数据、打印任务或真实纸张。旧 standing Staging 规则不适用本轮。

## 一个小 PR 的闭环

1. Ground Truth：fresh fetch、status/worktree、exact origin/main、受保护用户改动与来源 refs；读三治理及本任务 contract；核 CURRENT_STATE 已同步本轮授权。
2. 从 Backlog 领取依赖满足的一个 PR，记录 PRIMARY_REPAIR_GOAL、改动文件/模块和必要未知。不推测 API、不迁移旧名、不顺手改完整状态机。
3. 在独立 commercial worktree/branch 实施最小改动；只用合成账户、两个 Org、多 Store、MOCK/禁打印、本地临时 PostgreSQL；禁止拷生产 dump/env/token/真实 endpoint。
4. 运行 focused tests 与实际变动影响的回归；记录 exact SHA、命令、依赖/JDK/Node/SDK、环境、PASS/FAIL/BLOCKED/NOT RUN 和 counts。PostgreSQL opt-in 没跑就不能写 PASS，70 历史 unit 不充当本轮验证。
5. 自审 staged/unstaged diff、API/配置兼容、历史 snapshot、tenant负向、迁移和 rollback；更新 SYSTEM_DOCUMENTATION 与本任务 contract/API，文档索引由主线程统一协调，禁止多个 worker争写。
6. 材料变更做 AGENTS 要求的独立 Agent 6 review；相关问题合并修复后重审。无所需 review 能力就记 REVIEW_PENDING，不伪称通过。纯规划文档可记录风险比例例外，不能把架构实现 PR 归为文档例外。
7. 只 stage 已认领文件，提交可审查 commit 和 changed files/test/risk/migration/rollback。远端 PR/merge 只有明确权限时执行；无权限保留本地分支和 diff，不假报已合并。本轮使用 local integration 与 stacked Draft PRs，绝不自动 merge origin/main；未来若另获 merge 授权，合并后 fresh fetch、核 ancestry、刷新改动 authority。
8. 回写任务结果、证据路径、HC/DG disposition；CURRENT_STATE 仅由集成负责人写当下状态。继续下一个已授权、依赖满足任务。G2/G3 在独立 gate PR 复核，不能靠 backlog“勾选”绕过。

PR 的 DONE 至少是：范围内实现与对应自动/负向/回归测试通过、迁移/配置兼容与历史冻结验证、review要求完成、证据/文档/rollback齐备、无相关 P0。`LOCAL_COMMITTED`、`REVIEW_PENDING`、`BLOCKED`、`HARDWARE_QA_PENDING` 均不等于 merged/部署/商业 ready。

## 并行所有权

Auth/StoreAccess、共享 DTO/API/config model、Flyway 版本/SQL、Menu/Combo 关系、Order Snapshot、printing claim/lease/renderer 核心必须单一负责人串行。独立 module、test fixture、文档、Frontend branding 可以在共享 contract 固定后并行，每 worker有明确文件责任、独立分支/工作树，不能覆盖其他人改动。集成负责人统一迁移序号和跨模块回归。不会仅为规划中的“可并行”自动派发 Agent；实际委派服从本次用户授权与工具规则。

Phase 2 的 fixture/CI 可先于安全实现准备但不能先宣称安全过；Profile/Print 可复用稳定 Auth contract 在不同文件上实现；Offline 在 Auth contract/接口已定后可由 BASE 的独立分支并行实现，不改 Auth 公共函数签名，最终集成/acceptance 仍须等待 Auth 通过；出现重叠立即串行。Phase 3 config schema/reader先固定，UI与fixture再基于同版本实现。

## 技术失败与阻塞

普通编译、测试、链接、CI失败：root cause→最小修复→focused validation→必要重审，在已授权范围内继续，不增加无关框架。停止受影响任务的情况：需线上变更/真实凭据；来源关系不可解释；未授权远端操作；破坏已应用 migration/history；未定业务规则会改变金额/权限；持续失败且扩大实施不安全。可以继续不依赖该 blocker 的任务。

基线缺口按 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md) 的四轴与 blocking/nonblocking 分级。不能以缺实机而无限阻断纯本地隔离修复，也不能把“本地构建 PASS”写成“生产 APK/镜像认证”。真实硬件/生产确认和客户税/语言验收的限制必须随证据保留。

## 证据与回滚格式

每 PR 结果最少包含：任务 ID、来源与结果 commit、环境 LOCAL_SYNTHETIC、changed files、attack/repro（安全项）、命令/counts、API/DB契约、checksum/快照比较、review、剩余风险、精确 rollback。安全修复回退不得重启一个不安全商业环境；默认停止受影响能力并保持 fail closed。数据库新增字段优先前向修复/兼容旧应用；本地合成库可重建，业务库绝不以旧 dump 抹掉新订单。IndexedDB 升级失败保留原 records，打印未明确结果不能盲目 reprint。

支持性证据建议按阶段生成 `PHASE2_BASELINE_MANIFEST.md`、`PHASE2_SOURCE_CONTROL_RUNBOOK.md`、`PHASE2_SECURITY_TEST_REPORT.md`、`PHASE2_ACCEPTANCE_REPORT.md`、`PHASE3_CONFIGURATION_CONTRACT.md`、`PHASE3_PROFILE_DESIGN.md`、`PHASE3_ACCEPTANCE_REPORT.md`。这些为未来产物名称，当前未存在不表示已经完成；不建立另一个动态状态台账。

结束时以 G2/G3 实际结果输出 COMPLETE 或 PARTIAL/BLOCKED，列出未知和后续任务。Phase 3 后停止，不能自动进入通用菜单 Phase 4、商家部署或在线操作。

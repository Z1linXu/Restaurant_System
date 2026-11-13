# Allô Android 点单页提示简化 — 2026-11-13

PRIMARY_REPAIR_GOAL：按Owner要求移除点单页打印健康提示与常规缓存菜单提示，保留桌台页打印提示、缓存/刷新、现有订单及打印能力。
EXPECTED_REPAIR_CLASS：SMALL。

Fresh origin/main `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`；初始干净Allô HEAD `c55b816ed2b2dbbb931947a460c788e538e7903b`，main-only2 / Allô-only34。沿用PHASE_6 / ALLO_ANDROID_APK_20261113。Owner本轮明确授权UI简化、新APK验收与覆盖更新；无新服务器发布或数据清理授权。相关改动合并回同一修复PR313，避免拆出微小PR。

Product Simplicity Check：点单界面减少提示、按钮与展开/收起步骤；不增加用户操作、配置或生命周期。打印维护仍在既有桌台/Print Center完成。

PRODUCT REPAIR：OrderingPage移除PrintWorkerHealthBanner、完整的缓存来源/时间/展开刷新提示块，以及CHECKING/CURRENT/UPDATED常规状态；桌台页的两个打印提示入口保留。网络/认证/刷新失败、真实菜单版本变化提示仍在。缓存读取与自动刷新、草稿/outbox、菜单兼容校验、打印worker及安全停止状态均未更改。Allô更新1.0.2/code3，身份/签名/存储schema不变。

TOOLING / GOVERNANCE REPAIR：复用既有原生升级/按钮命中用例，增加桌台有打印控件、点单无打印控件和缓存信息条的实际DOM断言与截图；更新技术说明、CURRENT_STATE与本报告。无新框架、服务器或订单规则改动。

Validation：Frontend86 files/508项PASS，TypeScript/Vite/Gradle及Agent6源码和最终产物审查PASS。

- 实际原生验收6.065秒PASS：桌台页保留打印健康/检查按钮；点单页没有打印控件、缓存来源或“已是最新”信息。两张真实WebView截图已人工查看。
- 原TEST4本机草稿ID/A6数量1和7.99金额跨升级保持，三个操作按钮仍visible/enabled，保存可用，blocked business HTTP mutations=0。
- Owner Android16设备同签名adb-r更新1.0.2/code3成功；UID10318及dataDir不变。IndexedDB的3项数据文件和native preferences共4项SHA-256逐项一致；打印配对、设备Token和ERROR_STOPPED记录未重置，不自动恢复打印。
- 未对实体Pad运行instrumentation、未清数据/卸载、未提交或取消真实订单、未配对/打印。受限的Owner恢复副本只用于恢复保留，未用于QA；运行时只读复制不冒称原子快照。
- 旧Lanzhou资源51项哈希保持，旧App身份/签名/default server未改。

| Artifact | Value |
| --- | --- |
| Version / Code | 1.0.2 / 3 |
| Application ID | com.allonouilles.pos |
| Source SHA | 0b98ee271c26b56fb344868a489c4579a11e25fa |
| APK SHA256 | c522f6ffc6e9d9ba02de145aa49fde4239d5a4620cd53bef279f777c465267e4 |
| Bytes | 4,062,787 |
| Signing | DEBUG；有效；与旧包同一证书eefc3470dfeb1d118a69c2d0ba011c2f8a158e79bc23395caf0ea3ef8d35e106 |
| Asset Manifest SHA256 | ee7ac21aeba52d60c3d69c250db14fa0b2aca80a436a3e3eb22f3c7fe739af7e |
| Bundled Assets | 53项，集合及每项SHA与本次干净生成dist完全一致 |
| API | https://pos.allonouilles.com |
| PR | https://github.com/Z1linXu/Restaurant_System/pull/313；同一修复PR，未合并main |

APK：`/Users/xuzilin/.codex/worktrees/allo-production-improvements/Restaurant_System/dist/android/allo-nouilles-pos-v1.0.2-debug.apk`。同目录INSTALL.txt、build-info.json、按版本metadata/SHA已同步。构建一次精确SHA应用，证据变化未重建APK。

Production / Staging服务器mutation：NONE。Backend保持b7893860、Web Frontend保持b78ab21d，本轮只更新Android客户端。没有操作Lanzhou环境。
当前Gate：TECHNICAL完成；STOP_CLEAN_ORDERING_INSTALLED_NO_FURTHER_MUTATION。
Stop：`ALLO_ANDROID_1_0_2_INSTALLED_STOP_NO_SERVER_DATA_CLEAR_OR_PRINT`。

Execution Time Breakdown（分项Estimated，含并行）：

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 2 min | fresh authority、页面展示与worker独立性 |
| Implementation | 1 min | 删除提示UI、Allô版本、复用原生断言 |
| Tests | 2 min | 508项及真实table/ordering/草稿升级 |
| Agent 6 / PR / Merge | 2 min | 两次审查PASS；同PR313，未merge |
| Staging Deploy | N/A | 不需服务器发布 |
| Staging Acceptance | N/A | 既有独立模拟器本机草稿 |
| APK Build / Device Upgrade | 1 min | 同证书更新、UID/4项数据哈希校验 |
| Evidence / Governance | 2 min | 复用authority/link校验与交付metadata同步 |
| Total | ~10 min wall-clock rounded | Start16:33:35 UTC; final evidence 16:42:37 UTC |

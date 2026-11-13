# Allô Android 订单底部按钮修复 — 2026-11-13

PRIMARY_REPAIR_GOAL：恢复保存到本机、取消订单、提交/更新订单三个按钮的可达性，并保留覆盖升级前的本机草稿。
EXPECTED_REPAIR_CLASS：SMALL。

Fresh origin/main `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`，当前Allô基线 `7ed0832e6742e9e11da822edc743d0769c682d2f`，初始干净。main-only2 / Allô-only32提交。分支 `codex/allo-android-actions-20261113`，沿用PHASE_6 / ALLO_ANDROID_APK_20261113的现场缺陷修复。Owner当前反馈授权本轮APK修复、构建、验收与覆盖安装；没有新服务器发布授权，也不需要服务器变更。

## Root cause / 最小修复

`OrderSummaryPanel`外层同时存在viewport max-height和overflow-hidden；内部保留订单头、状态、菜品最小高度、整单备注、总额及不可收缩的操作区，内容超限时footer被裁剪。
旧APK在专用emulator-5580复现：滚动document到底后提交按钮top788.89、bottom836.89，visible=false（按真实elementFromPoint与viewport检测）。

产品修复仅移除外层overflow-hidden和两处max-height，保留列表独立滚动与safe-area padding。整页保持正常滚动，长内容下可以到达完整footer。没有重新设计页面、增加用户设置或全屏固定布局；没有改变任何按钮事件、订单生命周期、金额、备注、打印、认证或IndexedDB行为。

Allô版本升级为1.0.1 / code2，AppID、签名、deployment namespace及offline schema保持。旧Lanzhou flavor版本和生成资源不变。

## Validation

- Baseline reproduction：FAIL，旧APK真实按钮裁剪，符合用户现场反馈。
- Frontend：86 files / 508 tests PASS。
- 新增opt-in原生验收只允许ranchu/goldfish专用模拟器；正常登录后阻止所有业务HTTP mutation，仅建立本机TEST4草稿、加入A6、保存并检查三个按钮真实可见/可命中且enabled。不点击Submit/Cancel，不配对或打印。
- 模拟器本机保存draftId/item text/quantity基线；使用adb install -r升级后比较，证明草稿未丢失。Owner物理Pad不运行instrumentation、不清数据、不卸载；验收后仅覆盖更新同AppID。
- PASS：新APK构建、签名、53项资源哈希及精确source identity。
- PASS：实际WebView覆盖升级测试4.588秒；同一draftId保留A6、数量1、金额7.99；三个按钮通过viewport和elementFromPoint命中检查，保存回调执行后draftId仍相同，blocked business mutations=0。
- PASS：1280×800视口内，保存/取消top647.89、bottom695.89；提交top705.89、bottom753.89，均visible/enabled。截图已人工检查。
- PASS：Owner连接的Android16设备已同签名adb install -r更新至1.0.1/code2；UID10318与数据目录不变。升级前后IndexedDB的CURRENT、MANIFEST及WAL数据文件集合与SHA-256全部相同（3项，排除易变LOG/LOCK），没有清理本机数据。
- PASS：旧Lanzhou生成资源51项SHA保持，AppID/签名/default server配置未改；Allô APK无三个旧环境URL、真实员工密码或签名私钥。

验收用例前置条件已收敛：等待现有open-editable读取完成后再准备本机菜品，覆盖升级时明确进入/login完成正常登录；只增强既有instrumentation等待/失败诊断，未改变业务加载逻辑。
TOOLING_REASSESSMENT：回归准备的异步读取/入口假设不扩展为业务、测试或部署框架修复。复用现有原生测试路径完成本次布局与升级验收后停止。

初次生成目录含以前构建的未引用chunk，未交付该候选。将本轮拥有的generated Allô dist移至受限目录后，用同一exact-SHA原脚本干净重建一次；最终APK资源集合与本次dist完全一致。未修改构建框架、未改旧flavor、未删除Owner数据，证据变动未再触发应用重建。

PRODUCT REPAIR：`frontend/src/index.css`、`OrderSummaryPanel.tsx`（3处样式修复）及Allô版本元数据，直接阻塞主目标。
TOOLING / GOVERNANCE REPAIR：复用既有Android instrumentation增加本轮几何/本机草稿升级回归，更新SYSTEM_DOCUMENTATION、CURRENT_STATE及本报告；不新增部署或测试框架。

Production / Staging mutation：NONE。真实订单、配对、实体打印：NONE。
## 交付与审查

| 字段 | 结果 |
| --- | --- |
| Version / Code | 1.0.1 / 2 |
| AppID | com.allonouilles.pos |
| APK source | 40ddc684de685195cca5409104a6e7a4af9ff8ec |
| APK SHA256 | bfd01bcc4a0e712f3a1bc7faf30b0efb92bccf5e981523ca059a81b762bf2412 |
| Bytes | 5,002,351 |
| Build / Signing | DEBUG；有效；证书与1.0.0相同eefc3470dfeb1d118a69c2d0ba011c2f8a158e79bc23395caf0ea3ef8d35e106 |
| API | https://pos.allonouilles.com |
| Asset Manifest SHA256 | 332dba1cefdac7ef03b7f5df27a809a552c1f7b3f96217dd0305e5bd17761c9c |
| Agent 6 | 源码及最终产物/升级验收复核PASS |
| PR | https://github.com/Z1linXu/Restaurant_System/pull/313，base为既有Allô APK分支，未合并main |

交付APK：`/Users/xuzilin/.codex/worktrees/allo-production-improvements/Restaurant_System/dist/android/allo-nouilles-pos-v1.0.1-debug.apk`。
同目录的INSTALL.txt和build-info.json已指向新版，并保留按版本metadata与SHA文件。

```bash
adb -s DEVICE_SERIAL install -r "allo-nouilles-pos-v1.0.1-debug.apk"
```

Owner原Allô IndexedDB另外保留一份受限、只读的恢复副本；未用于QA。该副本在App运行时复制，不冒称为原子数据库备份，也未执行恢复或清除操作。没有对实体Pad运行测试instrumentation，没有提交/取消真实订单或配对/打印。

当前Gate：TECHNICAL，技术验收完成；STOP_ANDROID_BUTTON_FIX_DELIVERED_NO_SERVER_OR_DATA_MUTATION。
当前Stop：`ALLO_ANDROID_1_0_1_INSTALLED_STOP_NO_SERVER_DRAFT_CLEAR_OR_PRINT`。

## Execution Time Breakdown

分项为Estimated wall-clock投入，包含并行与验收重试。任务开始16:13:21 UTC，Total在最终证据提交前记录。

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 4 min Estimated | Git/authority、实际Pad视口、根因与旧APK复现 |
| Implementation | 2 min Estimated | 3处产品样式修复、Allô版本及聚焦原生回归 |
| Tests | 8 min Estimated | 508项、原生升级几何；包含fixture异步读取/入口假设修正重试 |
| Agent 6 / PR / Merge | 2 min Estimated | 源码和最终产物复核；PR313，未合并 |
| Staging Deploy | N/A | 无服务器发布 |
| Staging Acceptance | N/A | 独立模拟器本机草稿 |
| APK build / device upgrade | 1 min Estimated | 干净生成目录重建1次；同签名安装及数据校验 |
| Evidence / Governance | 3 min Estimated | 复用原validator进行当前authority及变更链接检查；全树旧依赖链接债务不扩展修复 |
| Total | ~20 min (wall-clock, rounded) | Start16:13:21 UTC; final evidence 16:32:22 UTC |

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
- 新APK构建、签名、逐文件资源哈希、升级与按钮几何验收：待完成后同步。

PRODUCT REPAIR：`frontend/src/index.css`、`OrderSummaryPanel.tsx`（3处样式修复）及Allô版本元数据，直接阻塞主目标。
TOOLING / GOVERNANCE REPAIR：复用既有Android instrumentation增加本轮几何/本机草稿升级回归，更新SYSTEM_DOCUMENTATION、CURRENT_STATE及本报告；不新增部署或测试框架。

Production / Staging mutation：NONE。真实订单、配对、实体打印：NONE。
当前Stop：ALLO_ANDROID_ACTIONS_REPAIR_ONLY_NO_SERVER_OR_DRAFT_CLEAR。

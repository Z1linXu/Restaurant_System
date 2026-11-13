# Allô Android 点单页提示简化 — 2026-11-13

PRIMARY_REPAIR_GOAL：按Owner要求移除点单页打印健康提示与常规缓存菜单提示，保留桌台页打印提示、缓存/刷新、现有订单及打印能力。
EXPECTED_REPAIR_CLASS：SMALL。

Fresh origin/main `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`；初始干净Allô HEAD `c55b816ed2b2dbbb931947a460c788e538e7903b`，main-only2 / Allô-only34。沿用PHASE_6 / ALLO_ANDROID_APK_20261113。Owner本轮明确授权UI简化、新APK验收与覆盖更新；无新服务器发布或数据清理授权。相关改动合并回同一修复PR313，避免拆出微小PR。

Product Simplicity Check：点单界面减少提示、按钮与展开/收起步骤；不增加用户操作、配置或生命周期。打印维护仍在既有桌台/Print Center完成。

PRODUCT REPAIR：OrderingPage移除PrintWorkerHealthBanner、完整的缓存来源/时间/展开刷新提示块，以及CHECKING/CURRENT/UPDATED常规状态；桌台页的两个打印提示入口保留。网络/认证/刷新失败、真实菜单版本变化提示仍在。缓存读取与自动刷新、草稿/outbox、菜单兼容校验、打印worker及安全停止状态均未更改。Allô更新1.0.2/code3，身份/签名/存储schema不变。

TOOLING / GOVERNANCE REPAIR：复用既有原生升级/按钮命中用例，增加桌台有打印控件、点单无打印控件和缓存信息条的实际DOM断言与截图；更新技术说明、CURRENT_STATE与本报告。无新框架、服务器或订单规则改动。

Validation：Frontend508项PASS；Agent6、exact-SHA APK、实际模拟器页面/草稿升级及Owner设备覆盖更新结果在完成后同步。

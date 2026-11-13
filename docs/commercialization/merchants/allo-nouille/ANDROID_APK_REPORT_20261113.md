# Allô Nouilles Android APK — 2026-11-13

PRIMARY_REPAIR_GOAL：交付独立可安装Allô Bundled Assets APK，复用现有POS/弱网/Native PAD_DIRECT，保持旧App身份/数据/配置。
EXPECTED_REPAIR_CLASS：HIGH（独立flavor、严格目的地、产物验证及实测必要CORS兼容）。

初始fresh origin/main80e888fcdad74e98326fcbeaa5df14bc0d9baed6，clean Allô HEAD75aa1c0e56d8bf44c9cc824303e753cedfdd31d8；main-only2个文档，Allô-only30个提交。
复用当前干净工作区并建codex/allo-android-apk-20261113，应用前端基于当前已上线b78ab21d。
Product Simplicity：首次启动直接bundled登录，无服务器手工输入；配对仍用既有Print Center，不增加provisioning流程。

实测2026-11-13 Bundled Origin https://restaurant-pad.local 对Allô /api/v1/auth/login OPTIONS返回403 Invalid CORS request；须按本轮明确兼容授权增加精确Allô Origin，不关闭校验。
未找到可用release signing配置，计划Debug交付；adb devices为空，硬件验收不伪称PASS。

测试：Frontend 86 files / 508 tests PASS；native 18 tests PASS；AlloCorsConfig 4 + authenticated STOMP 19 PASS；实际 emulator API35 冷启动与sandbox 2 PASS（含一次测试断言修复，Logo为图片不能用innerText判断）。使用专用fresh AVD。Allô UID10212，legacy UID10208，共存安装；legacy51assets SHA全部一致。

Agent6初审发现SockJS初始/info仍用bundled本地域名，已按最小transport修复统一cloud endpoint；KDS/Pickup沿用既有每次重连Bearer认证，未开放新server topic或更改鉴权。普通浏览器同源目标保持。新增3个bundled regression，完整508测试PASS。

兼容批次计划：只替换Allô backend JAR/image与APP_BUILD_SHA；root compose/nginx、frontend image/container、DB image/container/volume、V43不变，不执行SQL业务写入或Flyway新增迁移。镜像继承既有固定source base，无网络build。先保存runtime/env/config与旧backend image回滚基线；若回滚只重建旧backend，保留V43和当前业务数据；旧backend不能供新APK跨域登录，旧Web POS仍可用。禁止restore覆盖live库。

Fresh一致性snapshot备份 SHA256 `c0446e7455c4a3485c06128ce134c0b61135e30edc8c46ee9ff5d0936eed5c26`，87表在受限本机socket-only临时恢复副本校验行数/hash及constraints/indexes，PASS；临时副本已停止移除，未用于QA。

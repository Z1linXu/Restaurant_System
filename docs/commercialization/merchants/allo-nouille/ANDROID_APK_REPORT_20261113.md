# Allô Nouilles Android APK — 2026-11-13

PRIMARY_REPAIR_GOAL：交付独立可安装Allô Bundled Assets APK，复用现有POS/弱网/Native PAD_DIRECT，保持旧App身份/数据/配置。
EXPECTED_REPAIR_CLASS：HIGH（独立flavor、严格目的地、产物验证及实测必要CORS兼容）。

初始fresh origin/main80e888fcdad74e98326fcbeaa5df14bc0d9baed6，clean Allô HEAD75aa1c0e56d8bf44c9cc824303e753cedfdd31d8；main-only2个文档，Allô-only30个提交。
复用当前干净工作区并建codex/allo-android-apk-20261113，应用前端基于当前已上线b78ab21d。
Product Simplicity：首次启动直接bundled登录，无服务器手工输入；配对仍用既有Print Center，不增加provisioning流程。

实测2026-11-13 Bundled Origin https://restaurant-pad.local 对Allô /api/v1/auth/login OPTIONS返回403 Invalid CORS request；须按本轮明确兼容授权增加精确Allô Origin，不关闭校验。
未找到可用release signing配置，计划Debug交付；adb devices为空，硬件验收不伪称PASS。

测试：Frontend 86 files / 508 tests PASS；native 18 tests PASS；AlloCorsConfig 4 + authenticated STOMP 19 PASS；实际 emulator API35 冷启动与sandbox 2 PASS（含一次测试断言修复，Logo为图片不能用innerText判断）。使用专用fresh AVD。Allô UID10212，legacy UID10208，共存安装；legacy51assets SHA全部一致。

Agent6初审发现SockJS初始/info仍用bundled本地域名，已按最小transport修复统一cloud endpoint；KDS/Pickup复用既有Frontdesk每次重连Bearer路径，未开放新server topic或更改鉴权。普通浏览器同源目标保持。新增3个bundled regression，完整508测试PASS。

兼容批次计划：只替换Allô backend JAR/image与APP_BUILD_SHA；root compose/nginx、frontend image/container、DB image/container/volume、V43不变，不执行SQL业务写入或Flyway新增迁移。镜像继承既有固定source base，无网络build。先保存runtime/env/config与旧backend image回滚基线；若回滚只重建旧backend，保留V43和当前业务数据；旧backend不能供新APK跨域登录，旧Web POS仍可用。禁止restore覆盖live库。

Fresh一致性snapshot备份 SHA256 `c0446e7455c4a3485c06128ce134c0b61135e30edc8c46ee9ff5d0936eed5c26`，87表在受限本机socket-only临时恢复副本校验行数/hash及constraints/indexes，PASS；临时副本已停止移除，未用于QA。

## 最终交付

| 字段 | 结果 |
| --- | --- |
| ANDROID_BUILD | PASS |
| APK_CREATED | YES |
| APPLICATION_ID | com.allonouilles.pos |
| App Name | Allô Nouilles POS |
| Version Name / Code | 1.0.0 / 1 |
| Build Type / APK_SIGNING | DEBUG；现有 Android Debug key，非正式 Release 签名 |
| API_TARGET | https://pos.allonouilles.com |
| WebSocket | wss://pos.allonouilles.com |
| Asset Origin | https://restaurant-pad.local |
| BUNDLED_ASSETS | PASS，132 files，逐个 SHA-256 验证 |
| PAD_DIRECT_INTEGRATION | HARDWARE_NOT_TESTED；桥/worker策略单测及未配对状态通过 |
| LANZHOU_APP_UNCHANGED | VERIFIED：身份、label、签名配置、默认服务器行为及51个旧资源不变 |
| 最低 Android | minSdk 26 / Android 8.0 声明；本轮实际测试 API35 / Android 15 |
| 大小 | 4,707,328 bytes（约4.49 MiB） |
| APK source SHA | b7893860815fb87ef936730213e32170626ab7f2 |
| APK SHA256 | 57d86226023ab284569214de0c6cc2c957e83f2718bdbf5668fc69c072414fea |
| 证书 SHA256 | eefc3470dfeb1d118a69c2d0ba011c2f8a158e79bc23395caf0ea3ef8d35e106 |
| Asset Manifest SHA256 | 19a82035cf5d40410b542ebb7228801fc7afa8b07ad83635984031b136848606 |
| PR | https://github.com/Z1linXu/Restaurant_System/pull/312；base为既有Allô分支，无main合并 |

APK_PATH：`/Users/xuzilin/.codex/worktrees/allo-production-improvements/Restaurant_System/dist/android/allo-nouilles-pos-v1.0.0-debug.apk`。
同目录提供 `build-info.json`、`.apk.sha256`、`INSTALL.txt`。APK、签名私钥及生成资源不进入Git；公开报告保存可复核元数据。

安装前打开Pad的USB调试，在APK所在目录执行：

```bash
adb devices
adb -s DEVICE_SERIAL install -r "allo-nouilles-pos-v1.0.0-debug.apk"
```

启动 **Allô Nouilles POS** 后使用Allô员工账号登录。此App与 `com.restaurant.pad` 共存，不需要卸载旧App。普通升级保留本地草稿/outbox，不执行清除数据。当前为Debug现场测试包；正式Release需另配受控签名凭据。实体Pad配对、LAN TCP打印和纸张效果需后续明确授权的现场验收，本轮未执行。

## 验收与变更边界

- PASS：Frontend 508 tests / 86 files，TypeScript/Vite，Android Gradle，18项原生TCP deadline/worker/proof/endpoint单测，23项CORS/STOMP后端单测。
- PASS：专用fresh emulator-5580安装两个App，Allô UID10212、legacy UID10208；包名/label/version/签名、独立dataDir、禁止HTTP、首次无device token、空登录表单与加载Logo通过。
- PASS：实际WebView通过Allô正常登录，进入授权Workspace、Frontdesk并观察到真实SockJS/STOMP `CONNECTED`，菜单卡片A1及4.99金额已加载；Print Center显示当前MOCK及未配对。未点击提交、配对、打印或模式切换。
- PASS：独立本机QA订单14覆盖A1+C4、商品备注、整单备注、稳定幂等重放无重复订单/票、仅备注更新及重放、完成订单；subtotal21.98、tax3.29、total25.27。此写入仅在独立synthetic QA，保留原有合成设备fixture，无实体端点。
- PASS：线上精确bundled Origin与现有Web Origin返回200；HTTP/异端口/伪造后缀Origin返回403。实际 `/ws/info` 成功；WSS已认证本店订阅通过，匿名、无效Token、跨店、通配、客户端SEND五个负例拒绝。
- PASS：线上7个业务表完整V43列哈希、11个配置表、菜单34项金额、Flyway ledger均不变；仍1个既有active订单、0 pending jobs/outbox、0 devices/endpoints、MOCK、routing=false。
- NOT RUN：实体Pad、真实设备配对、LAN TCP及实体小票；KDS/Pickup未开放的服务端topic不在本轮运行验收范围，未新增任何服务端授权。
- 已关闭的验收脚本问题：Logo图片不属于innerText；页面过渡期会出现旧DOM，已改为实际菜单卡片、已加载打印模式及真实CONNECTED条件；PAD_DIRECT标识在翻译label末尾；同源OPTIONS可合法省略ACAO。修正测试后复验通过，无产品绕过。
- 非阻塞：Allô CSP阻止外部Google Fonts，使用既有系统字体fallback。未为字体放宽联网白名单。

最终验收仅加强androidTest断言和截图等待，不改变已交付APK或线上JAR；严格复用上述已验证产物，未为证据/文档变化重新构建应用。

## Allô最小兼容发布

Agent6源码复审及exact batch复核PASS；上线时间 `2026-11-13T15:53:52.135168+00:00`。

| 对象 | 精确结果 |
| --- | --- |
| Backend SHA | b7893860815fb87ef936730213e32170626ab7f2 |
| Backend JAR SHA256 | 885d9ae0f5dbaff70f263ac0e957bba6e5000dfdcd28391c0948ac8fab6885e0 |
| Archive SHA256 | 8f8023f2512c5f893455ad8ed898282f4eaae25e9ff3143a442ad2be31e0089f |
| Backend Image | sha256:28df2d2751b8ac007856bcf805fe6c495d56982ad31634ce99a1371c424624c1 |
| Backend Container | 90519fe2796cd8ed6b2bf4f2b8ca534654f67cd0bedd61fba72e349cb464c676 |
| Frontend SHA | b78ab21d9d4b97bf2b6526ccc489503f486b866e，未发布新Web前端 |
| Frontend Container | 58086eaa3421ddb9eb0fe0e16fcbae316935c36e917e4507abfc6ab9e875850f，ID/image/start time不变 |
| DB Container | cbd0c6cf504d3b746f880879db3f0beb9da3c3ce212e35c8d2b0a21061820d37，ID/image/start time不变 |
| Migration Scope | NONE，V43不变，未新增或重跑迁移 |

只改变runtime.env的BACKEND_IMAGE和APP_BUILD_SHA，只重建backend；compose、Nginx、JWT/DB Secret、DB volume、配置和营业数据未改变。启动时短暂502，38秒后健康；最终HTTPS/API/WSS正常。回滚基线在 `/srv/allo-nouilles/releases/b7893860815fb87ef936730213e32170626ab7f2/rollback-baseline`，保存旧backend image和runtime/env/config；恢复时仅回滚backend，保留V43与所有当前数据，不恢复旧快照覆盖营业订单。

Staging mutation：NONE（共享Staging未授权；使用既有本机独立QA）。Allô Production mutation：仅已授权最小CORS兼容backend release。Lanzhou Production、旧APK/设备数据：未操作。

Docker / Disk：发布前后均16%，可用约48GB；Build Cache 1.278GB→1.445GB，未清理/未prune。当前运行镜像、DB volume及旧backend回滚镜像均保留；三个容器健康。

## PRODUCT REPAIR 与 TOOLING / GOVERNANCE REPAIR

| 类别 | 文件/责任 | 必要性及耗时 |
| --- | --- | --- |
| PRODUCT REPAIR | Android manifest/flavor资源、MainActivity、FrontendAssetPathHandler、MerchantEndpointPolicy；RestaurantBrand/vite-env；SockJS三入口/helper；TemplateTestCorsConfig | 独立身份、品牌、固定HTTPS及真实跨源API/实时连接所必需；Implementation约11分钟Estimated |
| TOOLING / GOVERNANCE REPAIR | 既有build-bundled-apk/sync脚本的flavor参数和资产校验、对应回归及androidTest；构建文档、SYSTEM_DOCUMENTATION、API、CURRENT_STATE及本报告 | 复用现有构建/备份/受限恢复/部署路径，只做本交付阻塞的资源隔离、验证与证据；没有建立新部署框架。验收与证据时间见下表 |

Gate：TECHNICAL，当前技术验收完成；下一动作STOP_DELIVER_APK_NO_FURTHER_RUNTIME_MUTATION。
Stop Marker：`ALLO_ANDROID_APK_DELIVERED_STOP_NO_LANZHOU_DEVICE_PAIRING_OR_PHYSICAL_PRINT`。不产生后续Production或新Phase授权。

## Execution Time Breakdown

任务开始：2026-11-13 15:26:25 UTC。各阶段含并行，分项为Estimated wall-clock投入；Total在最终交付时记录。

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 4 min Estimated | fresh Git、SDK/签名、Native架构、实际CORS403 |
| Implementation | 11 min Estimated | 独立flavor、品牌/网络隔离、SockJS兼容；无订单业务重写 |
| Tests | 7 min Estimated | 前后端/native、fresh AVD、实际WebView及QA订单；包括断言/fixture/API标识修正重试 |
| Agent 6 / PR / Merge | 3 min Estimated | 合并问题簇复审、exact batch复核、PR312；Merge未授权未执行 |
| Staging Deploy | N/A | 无共享Staging mutation |
| Staging Acceptance | N/A | 使用独立loopback QA，结果计入Tests |
| Allô compatibility deploy / recovery | 6 min Estimated | fresh备份及87表恢复、传输、镜像复用、backend启动与smoke；与其他工作并行 |
| Evidence / Governance | 4 min Estimated | 元数据/截图、状态同步和校验；复用APK/JAR |
| Total | ~35 min (wall-clock, rounded) | Start 15:26:25 UTC; final evidence at 16:01:06 UTC; parallel stages are not additive |

Governance validation: current authority semantics and changed-document links PASS. The unchanged full-tree scanner FAILS on ignored node_modules links; a Git export additionally encounters a historical private screenshot link absent from tracked source. Both are NON_BLOCKING_EXISTING_DEBT; no scanner/framework change was made.

Final Agent 6 close: PASS. The owned fresh QA emulator was stopped after acceptance; no existing AVD or physical device was stopped or cleared. Final acceptance test file SHA256: `dd20e924c82d3a7ac4500b6d769751acf9a028de0026660ca11e6a3bf5da6dc5`.

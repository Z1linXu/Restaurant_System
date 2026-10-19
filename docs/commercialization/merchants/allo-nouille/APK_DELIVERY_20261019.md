# Allô Nouille Demo APK — 2026-10-19

文件：Allo-Nouille-Demo.apk（1,565,150 bytes，约1.6MB）。Android 8.0及以上。
应用名：Allô Nouille Demo；独立包名 `com.restaurant.pad.allo.demo`，不会覆盖 `com.restaurant.pad` 的原有应用/登录/订单缓存。
这是 debug 测试包，不是生产发布。APK内置当前已验证前端，菜单来自 Mac 的独立 Allô Demo 数据库；未内置菜单数据库、账户密码或设备凭据。首次读取菜单需要连接后端，已有离线缓存能力不等于离线安装即有菜单。

## 安装和连接

1. Android 开启 USB 调试，用 USB 连接这台 Mac，并在 Android 上自行接受这台电脑的调试授权。
2. Mac Terminal 检查设备（应显示 device，不应是 unauthorized）：

```sh
"/Users/xuzilin/Library/Android/sdk/platform-tools/adb" devices -l
```

3. 安装 APK，并建立到 Mac Demo 的USB预览通道：

```sh
"/Users/xuzilin/Library/Android/sdk/platform-tools/adb" install -r "/Users/xuzilin/Downloads/Allo-Nouille-Demo-20261019/Allo-Nouille-Demo.apk"
"/Users/xuzilin/Library/Android/sdk/platform-tools/adb" reverse tcp:51026 tcp:51026
"/Users/xuzilin/Library/Android/sdk/platform-tools/adb" reverse tcp:8081 tcp:8081
```

如果连接多台设备，在 adb 后加 `-s 设备序列号` 选择测试设备；不要安装或修改运营中的平板。此交付未自动安装任何设备。重新连接USB后可能需要重新执行 reverse。

4. 打开 **Allô Nouille Demo**，长按页面进入本地控制面板，设置：
   - Local Preview Web App URL：`http://localhost:51026`（推荐，与Mac当前同版前端及实时连接一致）
   - Bundled Assets API Base URL：留空（预览模式使用51026现有代理）
   - 保存并重新加载。
5. 登录账号 `allo.owner`。密码从 Mac 私有文件安全复制，不写入此说明：

```sh
pbcopy < "/Users/xuzilin/Library/Application Support/RestaurantSystemCommercialDemo/allo-20261018-d42urcne/owner-password.txt"
```

6. 选择 Allô Nouille，可操作菜单、套餐、C5双拼和MOCK打印预览。不要配对真实Pad打印设备、填写真实打印机地址或使用 Local Printer Test；本轮仅MOCK。

Mac后端需保持运行，健康检查：http://localhost:8081/api/v1/system/health 。当前前后端只监听Mac loopback，USB reverse保留该隔离，不开放LAN端口。此处Android localhost能使用的前提是已建立USB reverse；未转发时它只代表Android自己。

APK同时内置完整前端。可将Web App URL留空、API Base URL设为 `http://localhost:8081` 使用内置页的REST功能，但现有后端未允许内置页Origin的WebSocket，故该方式的实时更新不受支持，不作为本次推荐验收路径。推荐51026预览路径不会修改后端Origin规则。两种模式属于不同离线Origin，不应通过清缓存强行搬移已有草稿/Outbox。

## 校验与限制

SHA256：`38a1fe8d7de1087385a3e9e1e294113279aae71240c8dc39743c602865534405`，可用同目录SHA256.txt校验。

24项Android测试PASS；APK v2签名验证PASS；54个内置资源逐一SHA256/size核对并与当前Demo dist一致；离线schema DB5。Backend Health UP，`https://restaurant-pad.local` 登录CORS预检200。当前无连接的Android设备，因此真机安装、WebView登录/下单与物理设备QA尚未执行。没有改动/重启现有服务、数据库或运营APK；没有把开发APK认定为生产验收。

## 构建来源与复现

Native源码：`b2f5c4e2ed8f1593700d072df46a1fd851cdf5d1`；前端应用源码：`eb07448c1d0959aad15e5872738b7fcb7ed723cd`，后续head仅文档变更。

在私有 `runtime/apk-20261019` 复制当前 `restaurant-pad-app`（排除build/.gradle/local.properties）与已验证 `frontend-dist-ce05c14b`，提供同源 `frontend/src/offline/offlineDatabase.ts`。只在私有副本：

- app/build.gradle 的 debug 添加 `applicationIdSuffix ".allo.demo"`、`versionNameSuffix "-allo-demo"`。
- src/main/AndroidManifest.xml 的label改为 `Allô Nouille Demo`。
- local.properties 指向本机既有SDK。Java/native源、受跟踪bundled资产及原包签名配置均未修改；debug cleartext沿用现有debug Manifest。

使用现有 `scripts/copy-frontend-dist.sh` 生成/校验bundle metadata，`BUNDLED_BUILD_VERSION=allo-demo-eb07448-20261019`；Java21/Node20执行 `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`，BUILD SUCCESSFUL（10s）。复用现有已验证前端，不因文档重新编译应用。

私有构建日志与证据：`/Users/xuzilin/Library/Application Support/RestaurantSystemCommercialDemo/allo-20261018-d42urcne/apk-20261019/build.log`、`apk-evidence.json`。开发证书SHA256：`eefc3470dfeb1d118a69c2d0ba011c2f8a158e79bc23395caf0ea3ef8d35e106`。APK为本机交付文件，不入Git。

本轮Owner追加授权只覆盖本地APK打包；正式G3 PARTIAL、P4–P7授权、main合并、Staging/Production与实体打印边界不变。独立Agent6已审查私有override、源差异、资源与安装隔离，PASS，无打包P0/P1/P2阻塞；所发现Bundled WebSocket限制已明确记录并使用既有51026预览路径，未静默放宽后端Origin。

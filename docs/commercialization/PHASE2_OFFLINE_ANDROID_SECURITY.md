# P2-OFFLINE-06 — Offline / Android 环境隔离验收

日期：2026-10-15。范围：仅隔离本地源码和合成测试。基线 `92871dfb515b5fc53c2eeb1a117c02093c9443e9`，分支 `codex/commercial-phase2-offline`。

`PRIMARY_REPAIR_GOAL`：离线记录、会话凭据和设备凭据只用于原环境与当前已验证身份/门店，同时完整保留来源不明的旧记录。`EXPECTED_REPAIR_CLASS=HIGH`。

状态：本地产品代码/自动测试通过；独立 Agent 6 审查与集成待主线程执行。APK packaging、实机 WebView/Keystore 和物理打印仍待相应验收。没有 Production/Staging/真实 Pad/真实打印机操作。

## 缺陷与修复

| 入口 | 原缺陷/误用场景 | 当前最小防线 |
| --- | --- | --- |
| `menuCache` / `localDrafts` / `orderOutbox` | 同 bundled origin 切到另一 API，数字 account/org/store/order IDs 重复时命中原数据 | schema v2 key/record 含 canonical API origin、account、Organization、Store；snapshot revision/context/client identity 继续追加在原层级后 |
| `workspaceSnapshot` | 固定 `auth:current` 和 account/store key 可恢复另一服务器身份 | origin namespace；账户一致性、Store Context 与 workspace Organization 一致性、原 24h freshness / 401/403 不 fallback 保留 |
| `OrderOutboxProvider` / processor | 按 account 回放所有环境和门店；手动入口绕开 provider | 实际 Store Context 验证成功才注册 active scope；切店卸载/授权失败即暂停；自动列表和直接 process 都核完整 scope，异步 IDB 返回后及发送前再核 |
| `apiClient` / AuthProvider | 固定 token key；登出等待网络期间继续工作；迟到 refresh 可恢复已登出的凭据 | token 按 origin namespace；登出同步清当前 scoped token、invalidate session generation、abort 旧 HTTP、发 `restaurant-auth-expired`；refresh/login/result 核 origin/epoch；API 拒绝 foreign absolute URL，禁止携密重定向 |
| Android URL / credentials / Worker | URL 单独覆盖，旧 device token 可附到新 URL | 原子 target+origin-bound credential snapshot；切目标/重配对先停止 worker、invalidate generation；poll/job/native busy/不确定出纸时拒绝替换，不清安全上下文 |
| Android bridge | `addJavascriptInterface` 没有调用 frame 来源边界 | 仅 AndroidX `WebViewCompat.addWebMessageListener` 的明确 origin allowlist；每条消息核 `sourceOrigin`、`isMainFrame`、当前 WebView/Activity；无 unsafe fallback |
| 新设备 secret | 新 token 原样写 SharedPreferences | AndroidKeyStore AES/GCM，origin 作为 authenticated associated data；encrypted envelope 以 origin 的 SHA-256 key 分开保存；失效/损坏不 fallback 或清数据 |
| local printer-test settings | 切服可沿用原商家的手工测试 endpoint | 这些本地设置也按 origin 保存；目标更换时输入保存于原目标 namespace，新目标只读自己的设置；不改正式 printer routing/状态机 |

代码入口：`frontend/src/offline/environmentScope.ts`、现有 offline helpers / hooks / services；`StoreContextProvider`；`MainActivity`；`RuntimeEnvironmentPolicy`、`DeviceApiConnection`、`CredentialCipher`、`KeystoreDeviceCredentials`。

Store scope 只控制本机数据和调度，不是后端授权。ADMIN 是否有业务权限仍由本轮 Auth 修复强制执行；AuthProvider 的 `isOwner` 同步改为仅 OWNER。

## DB4 → DB5 与旧数据

- IndexedDB 版本 4 → 5，不删/重建 object store，不按当前 URL 重写旧 key，不回填虚构 environmentId。
- v1 菜单、workspace/auth snapshot、draft 和 outbox 原样留在原 store；新版 normal readers 不把它们当作当前环境可信状态。
- 旧 `LOCAL_DRAFT`、`QUEUED`、`SUBMITTING`、`FAILED_RETRYABLE`、`CONFLICT` 不自动过期删除/发送。cleanup 仅处理当前 origin 的明确 v2 draft。
- 新 key 前缀为 `environment:${encodeURIComponent(canonicalApiOrigin)}`。新 draft/outbox 保持原 `clientOrderId` / `idempotency_key`；升级不重估金额、不改变 frozenPayload/hash、不复制成新订单。
- 页面显示“旧版本机订单已安全保留，尚未恢复。请联系支持核对原服务器后恢复，请勿清理应用数据。”不展示未知来源订单内容给当前商家。
- 无来源的旧记录**没有自动恢复/认领入口**。支持恢复先核原服务器及原账户/Org/Store，有来源证据才可制定受控恢复；不能仅因数字 IDs 相同或当前登录成功就认领。不删除旧记录来消除提示。
- 旧 unscoped localStorage token、native plaintext pairing preferences 和旧 printer-test preferences 不自动认领/读取/发送，也不自动删除。旧设备状态有明确保留/需重配提示。旧 native 凭据仍是历史 app-private 明文，后续经过来源确认/撤销计划后才可迁移清理；不能声称旧秘密已被全量加密。
- 旧 native 未终结/不确定打印安全记录仍保留且阻止换服/重配对，不能借环境切换清除风险。

回滚：使用保留 DB5、origin namespace 和隔离逻辑的兼容修复包。禁止清 IndexedDB、改 frozen payload/hash 或直接降回 DB4 APK/前端。DB4 opener 对 DB5 得到 `VersionError`，防止旧 outbox reader 自动回放；这不证明旧 native worker 安全，旧 APK 整体降级仍不受支持。真实数据恢复/旧凭据撤销不在本地任务授权中。

## 环境契约与兼容性

- 本轮只使用已有配置推导 canonical API origin，没有发明 backend deployment-ID API。HTTP(S) scheme/host/default port 规范化；拒绝 userinfo、query/hash 和 API base path；bundled origin 本身不能作为 backend identity。
- Native remote Web 模式以所配置 Web origin 为 API identity；bundled 模式以 API base origin 为准。不会从当前导航页面猜另一后端。注入 bundled fetch/WS config 也读取同一规范目标。
- **限制**：相同 canonical API origin 被替换为另一独立部署、且数字身份全部复用时无法凭现有契约识别。不同客户/环境须使用稳定且不重新分配的 API origins；改动该约束前必须实施明确 deployment identity/恢复契约。此项不能被两个不同 origin 的测试覆盖。
- 新前端仅调用异步 `RestaurantPadMessages`。旧无校验 `RestaurantPadDevice` 只用于发现不兼容并提示更新，绝不作为 fallback 调用。消息缺失、失败、错误格式或 timeout 不会代签/触发有证明要求的 API。
- `getAccessToken` / `getRefreshToken` / 现有 auth API public 签名不变。`restaurant-auth-expired` 同步通知离线 worker 和 security 工作项的 realtime cleanup；原 `restaurant-auth-updated` 刷新事件保留。
- 未修改 backend、Flyway、Auth 后端、WS 服务、Profile、Order/Printing 状态机、价引擎或现有菜单兼容恢复语义。Q09 服务端有限旧价政策由主线程独立处理。

## 实测

所有新增数据、账号、token、HTTP server 均为本地合成。命令从仓库根目录执行，除所注明者外。

| 检查 | 结果 |
| --- | --- |
| `npm --prefix frontend test -- --reporter=dot` | PASS：51 files，287 tests，0 failures；含全量现有回归 |
| `npm --prefix frontend run build` | PASS：TypeScript build + Vite |
| 改动核心 offline/auth/API/bridge/UI paths 的 ESLint | PASS：0 errors / warnings；`useDraftOrder` 原 hook dependency warning 不在此清洁核心集合 |
| `npm --prefix frontend run lint` | 原有问题仍存在：5 errors / 6 warnings，错误均在未改文件。主线程独立基线为 6 errors / 6 warnings；本次触及的 PrintWorkerHealthBanner effect 已收敛，不扩修其余 lint |
| `restaurant-pad-app/android/gradlew -p restaurant-pad-app/android testDebugUnitTest -x verifyBundledFrontend --no-daemon` | PASS：Android SDK 35 编译 MainActivity/AndroidX/Keystore source；5 suites，22 tests，0 failures/errors/skips |
| 常规 native preBuild / `verifyBundledFrontend` | PACKAGING_PENDING：现有受跟踪 manifest/asset 来自 DB4，源码 DB5，既有 guard 正确拒绝不一致。未绕过 guard 产出/发布 APK；单元测试明确仅排除该资产预构建检查 |
| `git diff --check` | PASS |

关键新测试：

1. `environmentIsolation.test.ts` 实际创建 v4 IndexedDB，写原 schema/key/旧 pending states，升级 v5、执行 cleanup/登出 snapshot 清理后比较原记录完整 JSON，验证无认领、无丢失、无回放及 DB4 downgrade 拒绝。
2. 同 `restaurant-pad.local`、两个 API origin 使用相同 account/org/store/client/revision IDs，菜单/draft/outbox/workspace/auth cache 互不可见；切回 A 保留相同 retry payload/hash/identity。
3. Processor 的 foreign environment/account/Org/Store 直接调用零 storage lookup/零网络；IDB lookup 挂起期间登出/切店不会提交，返回原店以原 payload 继续。
4. 实际 workspace service 的 HTTP 401/403 不调用 cached fallback；logout 同步清身份、迟到 refresh 不复活/写入新环境；logout listener 改目标也不能带旧 refresh token 到新服务器。
5. Bridge correlation、timeout、失败/损坏 proof 零 fetch；成功 proof await 后附明确 header，原 idempotency body 不变；旧 JavascriptInterface 不调用。
6. Native policy 验证 sourceOrigin / main-frame、scheme/port、活跃/不确定任务禁切换、local printer-test 设置隔离；JCE AES/GCM 错 origin/错 key/损坏密文拒绝。
7. `DeviceApiConnectionTest` 使用两个 loopback HTTP server：A 凭据发 B 在建立连接前拒绝；A 的 307 不转发到 B，B 零连接。

## 尚未完成的发行/硬件证明

- AGENT6_REVIEW_PENDING：须在集成后独立审查；本报告不是自己的独立安全审查。
- PACKAGING_PENDING：主线程在 Phase 3 前端稳定后，用既有 sync/verify 脚本于隔离 worktree 重建 assets、核对 SHA/manifest/DB5，再做本地 packaging smoke。本工作项不手改 generated assets。
- HARDWARE_QA_PENDING：未安装/重签/分发 APK，未在真实 WebView 测 frame 来源、AndroidKeyStore 持久化/失效/升级、重启恢复、网络切换或物理打印。JVM 测试证明加密算法/来源策略与实际 HTTP factory，不冒充 Android provider/实机成功。
- 既有物理安装 APK、签名/asset hash 不明；旧真实配对来源、待确认打印结果与真实 legacy queue 恢复需要后续授权证据。
- 现有 dependency audit 提示不是本项升级目标；没有运行无范围的 `npm audit fix`。

## 主线程文档接线

为避免共享文档冲突，本工作项将以下片段交主线程更新 `SYSTEM_DOCUMENTATION.md` 与 `doc/API.md`：DB5/schema-v2 origin isolation、legacy retention/recovery notice、DB4 downgrade 限制、active Store-only replay、origin-scoped auth/同步 logout、异步可信 Android bridge、新 secret 的 Keystore origin binding，以及上述 packaging/hardware pending。Backend HTTP API request/response 未变；新增 `api_origin` 是本地 native pairing message 字段，不是新的 backend API。

## 独立 Agent 6 审查 — 129f66d（2026-10-15）

审查者：独立 security reviewer；本次未修改 Offline/Android 产品实现。
审查差异：`92871df..129f66dcdf4abd39f9b5311636ef6abd9a2d24d7`，53 个文件。
结论：**BLOCKED — 下面两项 G2-06 安全/身份隔离问题须修复后复审**。
原实现测试 PASS 和本节审查结论分别陈述，不以测试数量替代缺陷关闭。

### A6-OFF-01 [P1] 手动 pending 刷新仍可随重定向泄露设备凭据

- 位置：`MainActivity.java:1309–1340`，尤其 `1333` 的原始 `url.openConnection()` 与
  `1338–1339` 的 `X-Device-Id` / `X-Device-Token`。
- 可达入口：本地控制面板 `Refresh Pending Print Jobs` 按钮（`MainActivity.java:528–533`）。
  自动 Worker 已走安全工厂，手动刷新没有复用 `DeviceApiConnection.open`，也未禁用 follow-redirect。
- 场景：API A 的 pending endpoint 返回指向另一 HTTP Origin B 的 307；该 raw connection 默认跟随
  redirect，自定义设备 header 没有被移除，B 获得 A 的 device token。它绕过本轮新增的 origin-pair /
  no-redirect 防线；`DeviceApiConnectionTest` 只覆盖调用新工厂的路径，不能证明这个按钮安全。
- 复现：审查时在 `/private/tmp/OfflineRedirectProbe.java` 建两个合成 loopback HttpServer，按该
  方法同样的原始 HttpURLConnection 构造方式附 synthetic headers；JDK17 执行输出：
  `raw_refresh_status=200 redirected_device_token=synthetic-review-token`。
  这证明该调用形态在本地 JVM 的缺陷；真实 Android HTTP provider 仍是硬件验证范围，未冒充实机证据。
- 最小修复：手动刷新也必须取得不可变的目标/凭据快照并复用安全工厂，禁止自动 redirect；同时给迟到
  response 的 callback 加当前环境/配对 generation 检查。不要恢复 plaintext fallback。
- 必补测试：覆盖手动刷新实际使用的连接构造路径，A→B 307 时 B 零连接/零设备 header；另验证刷新期间
  切目标或重配对后，旧 response 不进入新环境的可操作队列。

### A6-OFF-02 [P1] 当前离线身份快照没有与当前账户凭据绑定

- 位置：`workspaceSnapshot.ts:19` 的 environment-only current key，`134–159` 的保存/读取；
  `AuthProvider.tsx:49–67` 的离线恢复入口；`authService.ts:32–34` 先持久化 token、返回后才由
  `AuthProvider.tsx:42–46` 异步保存身份快照。
- 场景：同一 API Origin 先有账户 A 的有效 snapshot；B 登录的 token 已写 localStorage，而 B 的异步
  snapshot 写入失败或进程在写入前中止。下次离线启动仅检查“当前 Origin 存在任意 token”，然后读取
  `auth:current:environment:...`；reader 只比较 record 内 accountId 与 record.user.id，未比较当前凭据。
  因此 B 的 token 可触发 A 的离线身份、权限和 A 的本机 Workspace/订单缓存恢复。namespace 含 Origin
  不足以闭合此处 account 边界。pending clear 因新 generation 失效时也可能保留 A 的记录。
- 复现：只读转译当前 `environmentScope` / `offlineDatabase` / `workspaceSnapshot` 到临时目录，实际
  fake-indexeddb 保存 A(user 7) snapshot，设置同 Origin 的 B(user 8) credential 后调用当前 reader，输出：
  `current_token_user=8 restored_offline_snapshot_user=7`。未修改产品文件或真实数据库。
- 最小修复：snapshot 必须与本次已验证账户及当前持久化 credential association/fingerprint 一致；缺失或
  不匹配时拒绝离线身份恢复。可以用 token account claim 作为本机缓存拒绝依据，或保存当前 credential
  fingerprint；不能把客户端解码当后端授权。无需删除或认领任何 draft/outbox。
- 必补测试：实际 AuthProvider/reader 组合模拟 A snapshot + B credential 的崩溃/失败窗口，离线不能恢复
  A；同时验证正常同账户 refresh 后仍可恢复，以及 logout/late snapshot 不重新激活旧账户。

### 已检查的其余边界与测试

- DB4→DB5 没有删 store、清库或重写来源不明旧 key；schema-v1 记录退出 normal readers / expiry cleanup，
  仍原样保留。未发现静默删除旧 pending records 的路径。
- 新 draft/menu/outbox key 与正常 reader 均包含 canonical environment/account/Org/Store；processor 的
  初始、IDB 返回后、发送前检查有效 scope，旧请求结果写回原 key，保持 stable idempotency identity。
- `apiClient` 拒绝 foreign absolute URL、fetch redirect:error，refresh/login 使用 session generation；
  同步 logout 停处理器/WS并 abort 已登记 fetch。此 Web 路径与 A6-OFF-01 的漏接 native 路径区分。
- Android 消息入口检查 exact sourceOrigin、main-frame、当前 WebView/Activity；没有 legacy
  JavascriptInterface fallback。新设备凭据的 AES/GCM AAD 和 envelope namespace 绑定 canonical Origin，
  解密失败保持字节、不回退 plaintext。
- runtime/配对替换在 worker/job/native busy 或结果不确定时拒绝；无实机证明，WebView、Keystore 生命周期、
  APK packaging 和纸张仍 pending。
- 审查独立运行：`npm --prefix frontend test -- src/offline/environmentIsolation.test.ts src/services/apiClient.environment.test.ts src/services/orderOutboxProcessor.test.ts src/services/storeWorkspaceService.test.ts`，
  **4 files / 21 tests PASS**。这组测试未覆盖上述两个反例，因此未因其通过而授予安全 PASS。

审查时间（Estimated）：只读调查约 4 分钟；Implementation N/A（0 产品变更）；Validation 约 1 分钟；
报告约 1 分钟；Total 约 6 分钟。无 PR/merge/deploy/运行环境操作。修复产品代码及独立复审由 Root 协调。

## Agent 6 finding cluster 的限定修复 — 2026-10-15

以上独立审查的原始 BLOCKED 证据完整保留。此节是实现者的修复/测试记录，**不替代独立复审**。

- `A6-OFF-01`：`refreshPendingPrintJobs` 改用 `ManualPendingJobsRequest` 的不可变 origin / credential / Store / generation 快照；其实际 `open()` 只调用 `DeviceApiConnection`，禁止自动 redirect。手动 read 在途期间纳入换服/重配对和实际 job/poll 的 busy 门禁；返回 callback 核对同一 request、generation、当前 origin/device，再允许生成可操作任务卡。没有改变 claim/lease/不确定出纸状态机。
- `A6-OFF-02`：当前 auth snapshot 新增已验证响应对应的持久化 access+refresh credential association（环境与两份 token 的 SHA-256 fingerprint，token 本身不写入 IndexedDB）。reader 必须匹配当前 token pair；缺失 fingerprint、凭据不符、logout 或读取期间身份改变均 fail closed。旧 snapshot/queue/token 不因不匹配而删除；旧 v2 auth snapshot 需要一次正常在线验证才能建立 association。
- Snapshot save 在 digest、DB open 和写入前核凭据/session；reader 给 AuthProvider 一个只在当前进程有效的同步复核函数，防止 reader resolve 后凭据变化仍应用旧身份。晚到 `/auth/me` 的成功响应不能在 token 已替换后被采用；晚到 refresh 的成功或 401/403 结果均不能覆盖/清除另一组已持久化凭据。
- 同一 auth 边界中处理了 bundled 首次启动 API 未配置：捕获 environment guard，停止 loading、保持未登录并显示简短服务器配置提示；既有原生设置入口不变，不用 `restaurant-pad.local` 代替后端。
- Credential fingerprint 依赖 WebCrypto SHA-256；不支持安全摘要时离线身份恢复不可用，不能回退到“有任意 token 即恢复”。后端仍是授权边界，fingerprint 仅作本机缓存关联/拒绝依据。

修复新增测试：

1. 真实 AuthProvider + fake IndexedDB：A snapshot 留盘，B tokens 已持久化且 B snapshot 写入被真实 transaction failure 拒绝；离线冷启动 user/permissions 均不恢复 A，原 A 记录完整保留。
2. 真实 `/auth/me` 401 → refresh → 新凭据 `/auth/me` → snapshot → 同账户离线重启成功；旧 fingerprint 在新凭据未经验证时拒绝恢复。
3. 迟到 snapshot save、旧 `/auth/me`、同 Origin 的迟到 refresh 均不能绑定/覆盖 B；缺少 bundled API 的真实 Provider 初始化没有未处理 rejection 或永久 loading。
4. `ManualPendingJobsRequestTest` 直接运行手动 pending read 实际使用的连接入口，以两台 loopback server 验证 307 时 B 零连接、A token 不转发；并覆盖 worker/job/native/manual busy 和环境/配对/generation 变化后拒绝旧结果。

本次集中验证：

| 检查 | 本地结果 |
| --- | --- |
| Offline/Auth focused | 5 files / 33 tests PASS |
| 完整 frontend | 52 files / 299 tests PASS |
| TypeScript + Vite build | PASS |
| 此 cluster 改动 TS/TSX 的 focused ESLint | PASS |
| Android SDK source compile / JVM unit | 6 suites / 24 tests PASS，0 failures/errors/skips；仍仅为 `-x verifyBundledFrontend` 的源码单测 |
| `git diff --check` | PASS |

仍为 `AGENT6_REREVIEW_PENDING`、`PACKAGING_PENDING`、`HARDWARE_QA_PENDING`。没有生成/安装资产或 APK，没有 Production/Staging/真实设备操作。回滚必须同时保留此 credential association 和 manual-read 安全连接，不能退回已复现漏洞的旧 DB5 实现。

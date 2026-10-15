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

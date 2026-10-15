# Phase 3 UI boundaries — local implementation

基线：`5ec8abeb7a67760000b29151e7f568a88a8f36ed`。工作分支：
`codex/commercial-phase3-ui`。本报告记录本地前端实现与证据，不是部署、
真实门店激活、原生后台 worker 或实体打印验收。独立复审由主线程集中安排。

## 展示配置

- Store Context 与 Menu Catalog 增加可选 typed `safe_summary`：schema 1、
  config revision、brand、locale、CAD、timezone、tax policy reference、profile
  code、NEUTRAL / PROVISIONAL_COMPATIBILITY。HTTP 路径、请求体与后端授权不变。
- 一个小型 presentation helper 派生品牌、locale、CAD 和 timezone。POS 导航、
  堂食标题、侧栏及 Owner shell 使用当前 Store 的品牌；用户默认门店名不覆盖
  当前选中 Store。原有硬编码 `蘭` 标记改用摘要，中性品牌为 Restaurant System。
- 缺失摘要、未来 schema / currency、未知兼容模式、非法 revision / locale /
  timezone 全部退回中性展示，不保留不受支持摘要中的品牌。只支持 CAD；locale
  影响格式，不做币种转换。旧摘要缺失时继续 en-CA/CAD，时区沿用浏览器默认。
- POS 卡片、行金额、订单合计与定制选项使用同一 CAD formatter；所触 Owner
  dashboard 的现有金额标签和时钟复用它。未扩写全站 formatter、翻译目录或
  i18n 编辑器；其他旧 Reports / 管理面板格式仍在原契约下。
- schema、revision、profile、compatibility、readiness 和 tax-policy reference
  不作为经营页面状态或表单字段展示。现有模块拒绝页收敛为简短业务提示，
  不展示内部 issue codes、legacy flags 或依赖诊断。没有新增 Owner 配置表单。

## Scope 与缓存

Store Context 在 snapshot 写入前核对响应 Store ID；Menu revision 与 catalog
在菜单 cache 写入前核对 Store ID，并沿用已有 Organization 校验。
`mapStoreContext` 对新选中 Store 与旧响应不匹配时立即丢开旧展示值，显示中性
loading 状态。摘要随现有 scoped workspace/menu snapshot 保存，未新增缓存、
存储版本或跨店 summary 注册表。Menu Catalog 的 content hash / ETag 更新由
后端 writer 负责；本前端继续使用已有 revision/ETag 更新路径，不另发配置请求。
未修改旧离线记录、草稿、outbox、credential 或恢复语义。

## 模块关闭行为

| Web 入口 | 行为 |
| --- | --- |
| POS / 堂食 | PRINTING 关闭不挂载 health banner，不读取打印 options，不启动提交后 coordinator，不 kick worker，不显示或执行手动重打 |
| 订单记录 | ORDER_HISTORY 仍可读订单和详情；PRINTING 关闭不读取 print options/jobs、不显示重打/打印记录区域，不建立 follow polling |
| Uber Inbox | UBER_EATS 本身的请求仍由原路由与 badge 守卫控制；PRINTING 单独关闭后隐藏并拒绝厨房重打，Uber 接单流程保留 |
| KDS / Pickup | 保留原 Store 模块路由守卫；关闭时业务页面不挂载，已挂载页面卸载并清理订阅和轮询 |
| Owner dashboard | ANALYTICS_ADVANCED 关闭不读 dashboard 或用于该面板的 workspace selector；迟到结果忽略；重新启用后正常加载 |
| Core Reports | REPORTING_CORE 独立于 ANALYTICS_ADVANCED，后者关闭不阻塞 Core 报表读取 |
| Printing Settings | TopNav 与原管理路由统一使用 management guard；模块启用但缺少硬件仍能进入设置，不能要求配置完成才开放配置入口 |

关闭 Printing 会取消现有 Web coordinator/follow、移除状态轮询，失效旧 options
及手动 action 的 scope。正常 submit/outbox completion 仍完成订单回调；关闭
时不补发 kick，重开不把已处理完成回调当作新打印事件。

手动重打 helper 增加可选 UI-scope predicate，旧调用方式兼容。关闭后不打开
迟到确认、不发送确认后的新 POST、不 kick 或跟随迟到结果。已提交的服务端
任务仍按原状态机处理；未知结果继续保留原 intent/idempotency key，不能借模块
关闭取消或重写实体出纸结果。本项不改变 PrintJob、claim/lease 或打印状态机。

**零调用断言仅覆盖 Web 页面/组件发起的上述副作用。** Android headless auto
worker 可以独立于 Web 启动；本项不证明其禁用后零轮询，不停止或重写原生
生命周期，也不发明 bridge stop API。后端 disabled-Printing device API 拒绝与
原 claim/lease 保护不变。原生后台轮询和物理出纸契约需单独证据。

## 初始提交 10acf3a 本地验证

| 验证 | 结果 |
| --- | --- |
| 全量 `npm --prefix frontend test` | PASS，59 files / 350 tests |
| focused 边界与相关回归测试 | PASS，13 files / 95 tests |
| `npm --prefix frontend run build` | PASS，TypeScript + Vite |
| 新增/改动文件 focused ESLint | PASS，40 files / 0 errors / warnings；仅排除下述两个已确认旧 finding 文件 |
| 全量 lint | 仍为原有 5 errors / 6 warnings；未扩修无关 lint。所触 OrderLineItemRow 的原 effect finding 另用基线源码 stdin 重现；OrdersPage 原 loadToday dependency warning 保留 |
| `git diff --check` | PASS |

真实组件/调用链测试包括：

1. schema / currency / locale / timezone 不支持时中性 fallback，以及切 Store
   时不借用旧品牌；foreign Context/catalog 在任何 snapshot/cache 写入前拒绝。
2. 实际 App 的七种 disabled KDS/Uber 路由零业务 reads / subscriptions；已启用
   Ramen/Uber 路由先建立读取和订阅，再关闭后证明取消订阅及停止轮询。
3. 真实 DineIn / Ordering / Orders 组件关闭 Printing 后零打印读取/原生触发，
   正常提交与 outbox 完成仍生效；运行中关闭、旧 handler、迟到 options、迟到
   submit/reprint 结果不会重新启动打印副作用。
4. 手动 helper 关闭后零 POST/kick；已提交结果返回而不 kick；迟到 confirmation
   不弹窗、不重发，未知 intent key 保留。
5. Advanced 关闭零 dashboard/selector reads，迟到结果忽略、重启后重新加载；
   Core Report hook 在 Advanced 关闭时仍执行自己的两期报表请求。
6. 缺少硬件时实际 management guard 允许工具挂载、runtime guard 阻止工具，
   TopNav 仍提供 Printing 入口且个人信息优先当前 Store。

所有账号、门店、摘要、网络和 bridge 结果均为本地合成 fixture。未调用
Production/Staging、真实设备/打印机或真实 Uber。初始提交未新增依赖、未改 Android
源码、未手工改 bundled assets、未安装/发布 APK。后端摘要 writer 的集成验收、
独立 Agent 6 与最终资产重建由主线程完成；物理 iPad/WebView 排版、Keystore、
后台 worker 和实体打印不在本地 React 测试证明范围。

## Execution Time Breakdown

本项本地 UI 实施（估算，后续主线程集成时间另计）：调查/规划 3 分钟，实施
6 分钟，测试与针对性修复 4 分钟，文档/自查 2 分钟；总计约 15 分钟。独立
Agent 6 / PR / merge 未执行；Staging deploy/acceptance 不在本项授权范围。

## 集中修复：Provider 刷新与默认确认弹窗

StoreContextProvider 原来仅在 mount / 切 Store / online 读取，菜单刷新得到的新
summary 不能更新作为展示单一来源的 Store Context。现在复用现有 GET Context
及 snapshot 路径，在可见且在线时响应 focus、visibilitychange、online 和 60 秒
周期。正常可达网络下，配置修改在 focus 或下一个周期与 Menu Catalog 收敛；
无额外配置事件、无第二份 summary 全局状态。网络/后端不可达不承诺 60 秒收敛。

- 同一个 Provider 同时仅有一个请求。重叠前台事件去重；隐藏或离线不发周期
  请求。卸载清理 timer、listeners 及 AbortController。
- 响应沿用环境 session generation、请求 signal、Store ID 校验，取消后的迟到
  响应不显示结果，也不开始新的 snapshot 写入/恢复；已接受的同 scope 缓存
  事务保留原契约，不删除旧记录。账户/环境/Store 切换立即屏蔽旧
  展示数据，迟到响应不能恢复旧品牌或模块。
- 已验证的 environment/account/organization/store registration 在同 scope 的
  定时刷新、焦点刷新和 restricted mode 变化时保持稳定；只有真正 scope 变化、
  请求授权/成员失败或卸载才释放，避免每分钟无谓 clear/re-register/outbox kick。
- 默认手动重打确认使用调用方可选 AbortSignal。POS、Orders、Uber、Printing
  Settings 的原 scope cleanup 触发 abort；登出复用现有 auth-expired 事件。打开
  的真实默认 dialog 会 close/remove 并 resolve(false)，新上下文不保留旧任务/Pad
  信息。scope 取消保留未知 intent key，旧确认按钮不能再次 POST/kick；主动用户
  取消保留原语义。已提交服务端 Job、claim/lease、实体结果均不变。
- 旧 helper/service 调用方式继续兼容，可选 predicate/signal 仅收敛 Web 生命周期。
  新增 `jsdom@26.1.0`（lockfile 精确版本，dev-only）执行真实默认 DOM 分支；
  只 shim jsdom 未实现的 dialog showModal/close 顶层方法，不把 shim 当实机证明。

修复后验证：全量 **61 files / 363 tests PASS**，TypeScript/Vite build PASS；
delta focused **7 files / 59 tests PASS**；14 个 delta TS 文件 ESLint clean，排除
原 OrdersPage hook warning；全量 lint 仍为原有 5 errors / 6 warnings；diff-check PASS。

`StoreContext.refresh.test.tsx` 使用实际 Provider、workspace service、Context
mapper 与菜单 hook，证明品牌/菜单/config revision 同步、模块关闭、稳定离线
registration、hidden/offline/dedup、跨 Store/account/environment 的 abort 和迟到
缓存拒绝。`OrdersPage.confirmation.test.tsx` 使用实际 Orders→默认手动 helper→
DOM dialog，覆盖打开后关闭模块/切店/logout 的移除与 intent 保留，以及实际
Provider 通过 focus 得知 Printing 关闭后移除已打开的旧确认；无新 POST/kick。

Focused 复现（仓库根目录）：

```sh
npm --prefix frontend test -- --run \
  src/features/orders/OrdersPage.confirmation.test.tsx \
  src/features/store/StoreContext.refresh.test.tsx \
  src/services/storeWorkspaceService.test.ts \
  src/services/manualReprintService.test.ts \
  src/features/uber-eats/UberInboxPage.test.tsx \
  src/features/dinein/DineInPage.modules.test.tsx \
  src/features/orders/OrdersPage.modules.test.tsx
```

本修复耗时估算：调查 2 分钟、实施 6 分钟、测试修复 3 分钟、文档自查 1 分钟，
总计约 12 分钟；独立 Agent 6 / PR / merge 由主线程安排，Staging deploy/acceptance
未执行。仍无 backend/native/generated-assets 或真实运行环境修改。

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

## 本地验证

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
Production/Staging、真实设备/打印机或真实 Uber。未新增依赖、未改 Android
源码、未手工改 bundled assets、未安装/发布 APK。后端摘要 writer 的集成验收、
独立 Agent 6 与最终资产重建由主线程完成；物理 iPad/WebView 排版、Keystore、
后台 worker 和实体打印不在本地 React 测试证明范围。

## Execution Time Breakdown

本项本地 UI 实施（估算，后续主线程集成时间另计）：调查/规划 3 分钟，实施
6 分钟，测试与针对性修复 4 分钟，文档/自查 2 分钟；总计约 15 分钟。独立
Agent 6 / PR / merge 未执行；Staging deploy/acceptance 不在本项授权范围。

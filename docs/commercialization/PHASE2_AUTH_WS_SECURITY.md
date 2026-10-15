# Phase 2 Auth / Store Scope / WebSocket 安全实施证据

范围：P2-AUTH-02、P2-WS-03；HC-002/003/004/005/036/038。
基线：隔离工作树 `/private/tmp/restaurant-commercial-security`，分支
`codex/commercial-phase2-security`，来源 `92871dfb515b5fc53c2eeb1a117c02093c9443e9`。
本文不改变当前 Phase、部署或数据变更授权。未连接或修改 Production/Staging，未触发打印。

PRIMARY_REPAIR_GOAL：关闭无授权跨 Organization/Store 的 HTTP 和实时业务访问，保留合法本店行为。
EXPECTED_REPAIR_CLASS：HIGH。支持授权完整工作流归 P6-SUPPORT-03；本轮不新增其 UI、grant 表或 Migration。

## 产品修复与攻击路径

| 原链 / 误用 | 修复 | 验证 |
| --- | --- | --- |
| ADMIN 在 StoreAccess 直接 true / findAll，并被 isOwner 当作 Owner | ADMIN 商家 capabilities 为空、无隐式 Store/Organization 权限；即使普通 membership 填 OWNER 也不等同有效支持 grant | StoreAccess、HTTP、真实 broker ADMIN 负向 |
| User 全局 OWNER + 目标店 FRONTDESK membership 可提升权限 | 有效 Store preset 不超过全局角色和 membership 两者；不可比较的冲突 preset 拒绝；Organization 继承只认全局 OWNER + exact active Org OWNER membership | mixed-role HTTP、报表、OwnerOverview、StoreAccess |
| membership 停用后恢复 users.store_id fallback | 任意状态 membership 存在即关闭 legacy fallback；从未有 membership 的非 ADMIN 本店账户保留兼容 | revoked/default/legacy 正负向 |
| Platform PUT 仅验证 body.store_id，按外店 path id 取实体再覆盖归属；POST 可携带 id 走更新 | 原资源、body Store 和 parent/child 引用均检查；通用 API 不允许已有业务实体跨店/跨 item 重新归属；Service 再检查稳定归属 | station/table/category/item/KDS 的 PUT 与 POST-id、外店 category/station/parent-option、user 负向；本店菜单/工位正向 |
| item-options 没有 Store 授权 | 通过 menu_item_id 取实际 Store，验证原 option/parent item，保留该旧 API 的 ADMIN_STORE_CONFIG 门槛 | 外店 option/parent 拒绝，本店 Owner 写入通过；原 Add-on/Combo 写保护保留 |
| 平台 metadata lists / overview 包含全库对象；共享 role 可被商家改写 | 仅返回有效管理 scope；Owner 仅改 exact active Org；组织创建与共享 role 改写拒绝；不关闭合法本店菜单管理 | 双 Org HTTP lists、role/admin 负向 |
| Staff Owner /stores 返回全库；更新只核旧店；可操作默认店的 ADMIN 账户 | 列表按有效管理角色；新旧 Store 都授权且同 Organization；平台账户排除；账户现有全部有效 Store 都须在 actor scope；角色/搬店同步 membership | staff list、foreign target、ADMIN deactivate/reset 拒绝；合法 Owner 同 Org 搬店/降权后旧会员失效 |
| Staff mutation 与搬店并发可使用过期原归属 | User mutation 复用事务内 pessimistic row lock，generic save 同时禁止重新归属 | 新 repository query；HTTP 负向及全量回归；真实 PG 集成由主线程统一执行 |
| Audit 无 store 参数走全库查询；Analytics ADMIN global rebuild | audit 必须提供授权 Store，Service 拒绝 null；global rebuild 始终拒绝，显式 Store rebuild 保留 | HTTP audit、Analytics scope 负向 |
| aggregate 入口只认全局能力和可见 Store，忽略该店降权 | Analytics / OwnerOverview 再按每店 ADMIN_STORE_CONFIG 过滤；该能力为当前 API 契约，不新增虚构 ANALYTICS_VIEW | mixed Owner/Frontdesk aggregate API 与 OwnerOverview 零财务查询 |

Store/Organization 实体、表名及字段未改。门店管理权限和业务运行 lifecycle 仍分开：Store scope
不因门店尚未 Live 或 optional hardware 未配置而整体拒绝管理；运行 API 继续由既有 module/lifecycle
边界判断。inactive Organization 不产生商家 Store 授权。

## HTTP 授权契约（供主线程回写 doc/API.md 与 SYSTEM_DOCUMENTATION.md）

- 主认证为 `Authorization: Bearer ...`。HTTP 与 STOMP 共用 `BearerUserResolver`，签名/到期验证后每次重新加载 active User 和当前 Role。
- `X-User-Id` 仍仅受现有显式本地 fallback 开关控制；本轮测试关闭它，未作为商业认证。
- `StoreAccessService.forStore(actor, storeId)` 返回有效 Store role principal；`requireForStore/order/item/task` 复用它。返回值 storeId 是实际授权门店，roleCode 是有效 preset，身份 userId 不变；roleId 保留账户原值，不能据其推导有效权限。
- `hasCapabilityForStore(actor, Store, Capability...)` 用于聚合读取的逐店过滤；普通 `canAccessStore` 不替代 capability 检查。
- `ADMIN` 是平台身份，商家业务权限默认拒绝；商家不得创建 ADMIN，也不得借普通 Store membership 授予平台支持访问。
- legacy 默认店兼容仅限从未配置 membership 的普通身份；撤销记录不会重新激活默认店。
- `/admin/platform` 保留本店菜单/工位/桌台/人员管理；原资源归属必须匹配 body，child 引用必须属于实际 parent/Store。POST 内附 id 同样受检查。
- `/admin/audit-logs` 要求显式 Store；不传或外店返回 403。`/admin/analytics/rebuild` 不传 Store 返回 403。共享 Role preset 写 API 返回 403；组织新建仍需独立平台授权路径。

## 实时协议

HTTP WebSocket / SockJS transport 可以匿名建立；它不授予业务访问。Spring 的 Origin 检查保留同源，
额外允许 `app.realtime.allowed-origins` 中的 exact HTTP(S) Origin。默认仅增加已存在 Android bundled
Origin `https://restaurant-pad.local`，不使用 wildcard。开发反向代理若改变 Host，需显式配置实际开发
Origin；不能使用 `*`，配置含 path/query/fragment/userinfo 或非 HTTP(S) 会启动失败。

浏览器/SockJS 不支持 transport 自定义 Authorization header，因此不把 STOMP connectHeaders
冒充 HTTP header，也不把 access token 放 query URL。CONNECT 必须含唯一的 STOMP
`Authorization: Bearer ...`，完成身份验证前 SUBSCRIBE 和任何业务访问被拒绝。参考
[Spring 官方 Token Authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html)。

- SUBSCRIBE 只允许既有七个 `/topic/stores/{positiveId}/...` 精确 topic，各自验证 Store 有效角色、screen capability 和 module；unknown、通配、foreign Store、角色不匹配拒绝。
- 客户端 SEND/ACK/NACK 等业务命令拒绝；只保留认证后的 SUBSCRIBE/UNSUBSCRIBE 和无业务访问的 DISCONNECT cleanup。heartbeat 不传业务数据。
- 每个 outbound MESSAGE 在入队前与 executor 实际 delivery 前重新验证 token、User、Store membership、capability 和 module；到期、撤权、停用后丢弃并关闭 transport。session registry 在 disconnect 清理，不记录凭据。
- `StompRealtimeEventPublisher` topic 与 after-commit 行为不变。
- 前端统一 helper 在每次 beforeConnect 读取最新 `getAccessToken()`；登出关闭连接，token 更新先关闭旧身份再重连；不改 apiClient/Offline 的公共接口。
- SockJS endpoint 使用已配置的 HTTP API origin 派生 `/ws`，支持 Android `/info`、XHR 与 websocket；不能直接传 ws/wss base。保留现有 reconnect/polling fallback。
- Hot Kitchen 改订阅自己的 `kds/hot-kitchen`，并改用已经存在且同 DTO 的 `/api/v1/kds/hot-kitchen` 任务读取 API。

## 测试边界与证据

合成 Fixture 为 Org 100 的 Store 10/11 与无关 Org 200 的 Store 20；不使用任何真实用户、订单、打印机、
设备 Token 或生产数据库。MockMvc 使用真实 Bearer filter / StoreAccess / Authorization / resource guard，
只替换 persistence/business executor。broker 测试启动独立本地随机端口 Tomcat + Spring simple broker，
实际连接 native WS、SockJS websocket 和强制 SockJS XHR，发布合成 refresh；没有连接线上频道。

验证命令（JDK17，进程级加载本机 cached ByteBuddy agent；不改 pom）：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v17) JAVA_TOOL_OPTIONS=-javaagent:/Users/xuzilin/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar mvn -o -q -f backend/pom.xml test
npm --prefix frontend run build
npm --prefix frontend test
git diff --check
```

最终结果由本文件末尾记录。71 项 opt-in PostgreSQL 测试未在本分支启用，主线程会在合成 PG16
环境执行集成验收；本分支的 PASS 不声称生产、硬件或真实客户账号兼容已经验证。

初轮回归修复的是安全语义变更后的旧断言和 Fixture：旧测试肯定跨店搬移、只 stub active-exists
查询或缺 Org active 数据；现测试改为明确拒绝搬店/零写入并完整提供 membership role。原 Add-on/Combo
保护的 BusinessException 顺序保持，未削弱既有校验。

## 仍未声称完成的内容

- **预存 P1 / P5-KDS**：Hot Kitchen hook 取得任务后仍调用 `GET /orders/{id}` 补全 `BackendOrderResponse`。
  当前 `HOT_KITCHEN` preset 没有 `ORDER_VIEW_DETAIL`，该通用 API 还受 ORDERING_POS gate；原 hook 甚至先读
  NOODLE_VIEW endpoint，本轮已最小换成已有 Hot-specific 任务 API。现有 KDS `/pass`、`/history` DTO
  不是完整 OrderResponse，且权限分别为 PASS / HOT-or-PASS；没有发现可直接替换且同语义的安全 order-detail
  API。因此不增加厨房财务读取权限、不猜新 API；P5 应以 kitchen-only DTO/页面契约关闭后半段依赖。
  纯 HOT_KITCHEN 页面端到端运营验收仍未完成；本店 Owner 的既有读取权限未扩大或删除。
- 当前真实餐厅账号是否依赖 ADMIN 或失效 membership，未读取生产业务行，仍 UNKNOWN。隔离代码正向 Fixture
  不替代未来真实发布的账号盘点/Owner 验收。
- 完整支持 grant、授予/到期/撤销 UI 和审计工作流仍在 P6；本轮保持无授权业务访问为零。
- Browser/Android 物理设备、生产入口代理与允许 Origin 的实机 QA 尚未执行。服务器本地协议兼容不等同实机认证。

## Migration / Rollback / 集成

无 migration；不修改 Profile、Order、Printing 或 Offline 文件。UserRepository 仅新增事务锁查询。
Root 集成时须保留 Offline worker 对 AuthProvider `isOwner` 仅 OWNER 的变更，以及按 API origin 保存 token 的实现。
API / SYSTEM 由 Root 统一回写以上当前契约，避免本工作树与其他 worker 同改文档。

回滚只能选择经审查的安全实现或关闭 realtime 后用既有 REST 刷新；不能恢复 ADMIN 业务通行、wildcard
Origin、匿名订阅或遗失的 membership 兼容漏洞。Agent 6 和 Phase 2 综合验收由主线程统一执行。

## 本工作树最终验收

- Backend 完整默认 suite：984 tests，913 executed PASS，71 opt-in PG skipped，0 failures / 0 errors；日志 `/private/tmp/security-backend-verified.log`。
- 其中真实 broker 11 项、双 Org HTTP 19 项、StoreAccess 6 项、OwnerOverview 4 项全部通过。
- Frontend：production build PASS；49 test files / 274 tests PASS；新旧 realtime 10 项通过。
- `git diff --check`：PASS。最终本地提交待 Root cherry-pick、Agent 6 统一 review；本报告不声称已 merge 或部署。

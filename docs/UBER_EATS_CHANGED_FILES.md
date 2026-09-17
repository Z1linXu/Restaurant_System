# Uber Eats v1 Changed Files
本清单仅列本轮独立 worktree 的变更。

## Backend

| 文件 | 作用 |
|---|---|
| [backend/src/main/java/com/restaurant/system/auth/filter/AuthTokenFilter.java](../backend/src/main/java/com/restaurant/system/auth/filter/AuthTokenFilter.java) | 仅精确webhook POST绕过浏览器bearer解析。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/client/UberEatsApiException.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/client/UberEatsApiException.java) | 仅保留安全远端错误码。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/client/UberEatsOAuthClient.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/client/UberEatsOAuthClient.java) | client_credentials、token cache/expiry、固定host和安全超时。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/client/UberEatsOrderClient.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/client/UberEatsOrderClient.java) | 真实Get/Accept/Deny路径及严格204 ACK。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java) | 默认关闭、环境与backend secret校验。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsWorkerConfig.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsWorkerConfig.java) | 独立Uber调度器与现有默认调度器隔离。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsExceptionHandler.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsExceptionHandler.java) | 集成异常HTTP映射且不返回敏感body。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsInboxController.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsInboxController.java) | StoreAccess保护的inbox/decision/config API。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsWebhookController.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsWebhookController.java) | 限制raw request字节长度并快速返回ACK。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/dto/UberOrderSnapshot.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/dto/UberOrderSnapshot.java) | 订单制作字段最小化快照。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsEvent.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsEvent.java) | 持久event/hash/retry实体。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsMenuMapping.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsMenuMapping.java) | 稳定item/modifier/parent映射实体。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsOrder.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsOrder.java) | 远端状态、决策checkpoint、冻结请求、本地关联。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsStoreMapping.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsStoreMapping.java) | 环境+Uber门店到本地Store/Organization绑定。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/mapping/UberEatsMenuMappingService.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/mapping/UberEatsMenuMappingService.java) | 使用现有catalog构造中文snapshots与组合选项，未知项阻断。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsEventRepository.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsEventRepository.java) | 事件去重、claim和正式通知存在检查。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsMenuMappingRepository.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsMenuMappingRepository.java) | Store绑定下显式映射查询。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsOrderRepository.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsOrderRepository.java) | 订单去重、锁、inbox优先级与恢复查询。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsStoreMappingRepository.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsStoreMappingRepository.java) | 按环境查询双向Store绑定。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsConfigurationService.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsConfigurationService.java) | 平台绑定、Owner菜单规则、自动remap与审计。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsException.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsException.java) | 安全业务状态码。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsInboxEvents.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsInboxEvents.java) | 提交后发门店WebSocket刷新提示。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderImportService.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderImportService.java) | 事务外远端调用与不确定结果对账。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderNormalizer.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderNormalizer.java) | 白名单标准化、嵌套modifier与不支持字段阻断。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderTransactions.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderTransactions.java) | 短事务状态机、锁后刷新、现有领域原子提交。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsWebhookService.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsWebhookService.java) | raw HMAC、durable去重、取消/改单即时guard。 |
| [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsWorker.java](../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsWorker.java) | 定时消费与恢复入口。 |
| [backend/src/main/java/com/restaurant/system/order/entity/Order.java](../backend/src/main/java/com/restaurant/system/order/entity/Order.java) | 新增nullable外部来源及订单标识。 |
| [backend/src/main/java/com/restaurant/system/printing/renderer/ExternalOrderReceiptHeader.java](../backend/src/main/java/com/restaurant/system/printing/renderer/ExternalOrderReceiptHeader.java) | 通用外部订单抬头与身份存在判断。 |
| [backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java](../backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java) | 复用原票据逻辑，显示通用外部来源并避免虚构桌号。 |
| [backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java](../backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java) | 原GRAB流程加通用外部来源，保留全部厨房formatter。 |
| [backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java](../backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java) | 原HOT路由和渲染加通用来源。 |

## Frontend

| 文件 | 作用 |
|---|---|
| [frontend/src/App.tsx](../frontend/src/App.tsx) | 注册Frontdesk与Owner映射路由。 |
| [frontend/src/features/frontdesk/components/FrontdeskTopNav.tsx](../frontend/src/features/frontdesk/components/FrontdeskTopNav.tsx) | 接入Uber订单数量badge。 |
| [frontend/src/features/owner-admin/OwnerAdminShell.tsx](../frontend/src/features/owner-admin/OwnerAdminShell.tsx) | Owner/Admin映射入口。 |
| [frontend/src/features/uber-eats/UberInboxBadge.tsx](../frontend/src/features/uber-eats/UberInboxBadge.tsx) | 前台可点击的待处理数量。 |
| [frontend/src/features/uber-eats/UberInboxPage.tsx](../frontend/src/features/uber-eats/UberInboxPage.tsx) | 可触摸订单卡片、modifiers、接拒单与复核状态。 |
| [frontend/src/features/uber-eats/UberMappingPage.tsx](../frontend/src/features/uber-eats/UberMappingPage.tsx) | Owner/Admin稳定菜单映射配置。 |
| [frontend/src/features/uber-eats/useUberInbox.ts](../frontend/src/features/uber-eats/useUberInbox.ts) | 轮询和现有WebSocket提示刷新。 |
| [frontend/src/services/uberEatsService.ts](../frontend/src/services/uberEatsService.ts) | 无Uber凭据的store-scoped HTTP客户端。 |

## Flyway

| 文件 | 作用 |
|---|---|
| [backend/src/main/resources/db/migration/V29__add_uber_eats_integration.sql](../backend/src/main/resources/db/migration/V29__add_uber_eats_integration.sql) | 新增四张表、外部关联、唯一约束及查询索引。 |

## Tests

| 文件 | 作用 |
|---|---|
| [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsOAuthClientTest.java](../backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsOAuthClientTest.java) | HTTP mock校验OAuth/cache/错误与endpoint/ACK。 |
| [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java](../backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java) | 真实PG/Flyway/订单库存打印、并发与恢复集成测试。 |
| [frontend/src/features/uber-eats/UberInboxPage.test.tsx](../frontend/src/features/uber-eats/UberInboxPage.test.tsx) | 接拒单、doubletap、未知映射与错误交互测试。 |
| [frontend/src/features/uber-eats/useUberInbox.test.tsx](../frontend/src/features/uber-eats/useUberInbox.test.tsx) | refresh/remount/realtime与跨Store状态隔离测试。 |

## Documentation

| 文件 | 作用 |
|---|---|
| [SYSTEM_DOCUMENTATION.md](../SYSTEM_DOCUMENTATION.md) | 记录本轮技术行为与集成文档入口。 |
| [doc/API.md](../doc/API.md) | 记录webhook与所有store-scoped接口合同。 |
| [docs/UBER_EATS_ACCEPTANCE.md](../docs/UBER_EATS_ACCEPTANCE.md) | A–Z证据与外部阻塞报告。 |
| [docs/UBER_EATS_CHANGED_FILES.md](../docs/UBER_EATS_CHANGED_FILES.md) | 逐文件变更说明。 |
| [docs/UBER_EATS_INTEGRATION.md](../docs/UBER_EATS_INTEGRATION.md) | 官方API、数据流、配置、安全、限制与恢复runbook。 |
| [docs/governance/BACKLOG.md](../docs/governance/BACKLOG.md) | 登记真实Sandbox与现场验收待办。 |
| [docs/governance/CURRENT_STATE.yml](../docs/governance/CURRENT_STATE.yml) | 更新本轮package/gate/stop，不改变部署事实。 |

## Deployment/config examples

| 文件 | 作用 |
|---|---|
| [.gitignore](../.gitignore) | 允许提交空凭据示例文件。 |
| [backend/.env.uber-eats.example](../backend/.env.uber-eats.example) | 无实际凭据的backend配置示例。 |
| [deployment/cloud/.env.staging.example](../deployment/cloud/.env.staging.example) | 默认关闭的backend-only环境变量示例。 |
| [deployment/cloud/docker-compose.staging.yml](../deployment/cloud/docker-compose.staging.yml) | 透传可选凭据并固定sandbox环境。 |

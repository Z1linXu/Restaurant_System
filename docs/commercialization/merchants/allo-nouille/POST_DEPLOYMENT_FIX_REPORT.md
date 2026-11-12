# Allô 六项改进实现与发布状态 — 2026-11-12

**IMPLEMENTATION_TESTED；NOT_DEPLOYED；PENDING_BROWSER_GATE；RELEASE_GO = false。**

候选源码：`e7dde0a6e27adf3be00baa9972fee06cdcc5482c`。六项功能已实现并通过下列自动化、
隔离数据库及已完成的浏览器验收。浏览器在 Finish 原生确认框处阻塞，剩余浏览器验收
尚未完成，因此不能将实现通过或镜像预置视为发布完成。

本次目标仅为 `43.166.242.250` / `https://pos.allonouilles.com`。Lanzhou Production
不在范围内。

## 六项实现

| 功能 | 已实现行为 | 验证与范围 |
| --- | --- | --- |
| 1. 受控 PAD_DIRECT | 显式 Allô opt-in、冻结执行模式来源、区分 MOCK/PAD artifact；复用原 Outbox、设备亲和、claim/lease/token 和队列。REAL 后端 TCP 仍禁止。 | 单测与独立代码评审 PASS；线上仍 MOCK，硬件 NOT TESTED。详见下文 PAD_RUNTIME。 |
| 2. 菜单编号显示 | 从稳定 `AN_` SKU / 订单 SKU 快照显示 A1–E3 共 32 个编号；名称编辑与语言切换不改变编号，不重复前缀，两个免费续餐项保持无编号。 | 菜单截图与现有分类/价格核对 PASS；浏览器 C6 改名、单品改价及恢复后编号仍为 C6；无需重写历史快照。 |
| 3. GRAB / FRONTDESK_RECEIPT | 冻结商品编号、语言、选项、路由与金额；既有 Outbox/source key 产生厨房/前台票。新 Allô 加单冻结整单金额差额并标注整单舍入，旧票据原样保留。 | 五组十二份原始快照及新十份加单金额快照 PASS；Print Center 浏览器交互待恢复，实体未测试。 |
| 4. 分类厨房打印 | 在既有 receipt settings 冻结 GRAB 分类排除；DRINK 不进厨房，Dessert 默认包含且可独立关闭/开启；前台收据保留所有售出项目。 | 浏览器 Dessert 关闭保存、刷新读回、开启恢复 PASS；完整 renderer 对照 PASS，每单一张 BACK_KITCHEN GRAB。 |
| 5. Canonical Add-on | `store_addons` 提供当前名称、价格与可售状态；模板叶保留分组、排序、默认及 item eligibility，受控 reconcile 使用 revision/fingerprint 并支持无写入重放。历史订单与打印快照不重写。 | 浏览器共享 Add-on 改名、价格改为 8.49 后 POS 同步 PASS；合成订单与回归通过。未对 Production 执行 reconcile 或改价。 |
| 6. 分类批量基础调价 | V42 增加默认关闭的 Store capability、preview 与幂等 ledger；应用时校验 revision/fingerprint，在原 Store 锁和事务内更新、审计。免费续餐、明确固定优惠、选项价格及历史记录不变。 | 隔离 PostgreSQL V42 PASS；浏览器对 B/C 两类 14 项 +1、再 -1 并恢复 PASS；角色、并发、重放和回滚回归通过。 |

实现细节分别见 [菜单与分类核对](MENU_CODE_AND_CATEGORY_AUDIT.md)、
[Add-on 权威来源](ADDON_SOURCE_OF_TRUTH_REPORT.md)、
[分类批量调价](CATEGORY_PRICING_IMPLEMENTATION.md) 与
[独立评审](INDEPENDENT_REVIEW.md)。

## 已完成验证

| 验证 | 结果 | 证据含义 |
| --- | --- | --- |
| 合并后 Backend 回归 | PASS：1,519 total，1,402 passed，117 skipped；191 个 XML，0 failures / errors。 | 明确保留 117 skipped，不将其计作通过。 |
| Frontend 回归 | PASS：84 files / 481 tests。 | 与 Backend 计数独立。 |
| TypeScript / Vite | PASS。 | 类型检查及生产构建通过，不代表线上已切换。 |
| 隔离 PostgreSQL / Flyway | PASS：V42。 | 使用新建合成 QA 数据库；Production 仍为 V41。 |
| 完整 renderer 验收 | PASS：5 组场景、6 张合成订单、12 份 GRAB/FRONTDESK 快照。 | 真实渲染与保存快照证据，不能替代真实 Pad 或物理出纸。 |
| 加单金额回归 | PASS：115 项 focused 回归与新构建实际 HTTP/保存票据。 | 正反向一分钱分摊、连续零价追加、幂等及历史哈希不变；[金额证据](UPDATE_RECEIPT_AMOUNT_REGRESSION.md)。 |
| 独立代码评审 | PASS（e7dde0a）。 | 代码及金额门槛通过，浏览器发布门槛仍未完成。 |
| Hosted CI | NOT RUN / 此 checkout 无 `.github` workflow。 | 不将本机测试称作远端 CI。 |

完整渲染样本见 [打印验收](PRINTING_ACCEPTANCE_5_ORDERS.md)；路由结果见
[打印路由报告](PRINTING_ROUTING_TEST_REPORT.md)。所有测试订单和浏览器写入均位于隔离
合成 QA，与 Production 恢复数据分离。

## 已发现并修复的加单金额问题

实际 UI Order8 的整单金额36.17正确，但旧初单29.30与加单6.88相加为36.18。
没有因其为旧逻辑而豁免：e7dde0a 仅为新 Allô 加单冻结整单取分差额，本例应为
5.98+0.89=6.87。实际新订单9/10验证5.74+4.58=10.32、19.53+19.54=39.07，
合法零价续面增加0.00；重试不重复发票。原 Orders1–8 / Jobs1–18 / Outbox 哈希不变，
旧 Job18 仍保留原文6.88。c242548 预置候选被新版本取代，不得切换旧候选。

## 浏览器验收：部分完成，发布门槛待恢复

已通过真实 UI 操作：Owner/menu 进入，C6 改名与单品改价后恢复，Dessert 开关保存与
刷新读回后恢复，B/C 两类 14 项批量 +1/-1 后恢复，共享 Add-on 新名称及 8.49 价格在
POS 显示，以及堂食提交和已提交订单追加 Update。

随后 Finish 的 `window.confirm` 原生对话框阻塞了 CUA。`getJsDialog`、focus、close、
navigation 均未能恢复；新标签页可以读取页面，但点击仍无响应。**Finish 确认及后续
剩余浏览器模块为 NOT_RUN，不宣称整个浏览器验收通过。** 已异步请求用户协助解除
阻塞；恢复后需要继续未完成项，不能用 API、自动化测试或代码评审代替。

## Production 与恢复证据

| 项目 | 当前事实 |
| --- | --- |
| 候选镜像 | `e7dde0a` 对应最终新镜像已 STAGED 到 `43.166.242.250`；未切换运行。 |
| 实际线上源码 | 仍为 `2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9` + WSS patch `915e4ff4d8a6`。 |
| 实际线上状态 | V41、MOCK；本报告不宣称 V42 或新 PAD policy 已在线生效。 |
| Production 写入 | 未修改配置、价格、订单或 PrintJobs；未创建 Production 测试订单、重印或物理打印。 |
| 新备份 | 2026-11-12 08:31:59 UTC；SHA-256 `55dd0ddf0e8a4e4f6a7f1ed22cd298d6e9501fc7d850de485f784da9f5ca0008`。 |
| 恢复验证 | 08:33:27 UTC：85 张表、精确内容 hash 与有效约束核对 PASS。恢复集群仅使用 Unix socket，与合成 QA 分离，验证后已移除。 |
| 发布状态 | NOT_DEPLOYED / PENDING_BROWSER_GATE；当前无 release GO。 |

实际 Pad pairing、Android Worker、机型/codepage、纸宽与实体出纸均为 **NOT TESTED**。

## PAD_RUNTIME

`allo-test` 缺省仍保持 `test`、`MOCK/DISABLED`、禁止 endpoint 的安全边界。
以下是候选运行时契约，尚未应用到当前线上。Allô 独立 Production 必须显式同时配置：

```dotenv
SPRING_PROFILES_ACTIVE=allo-test
APP_ENVIRONMENT=production
APP_DELIVERY_ALLO_PRODUCTION_PRINTING_ENABLED=true
APP_DELIVERY_ALLO_BOOTSTRAP_ENABLED=false
APP_PRINTING_ALLOWED_MODES=DISABLED,MOCK,PAD_DIRECT
APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED=true
```

原数据库身份保留：`jdbc:postgresql://db:5432/restaurant_allo_nouilles_test`、
user `allo_test`。原已有 `allo-test` + 精确 loopback
`jdbc:postgresql://127.0.0.1:25442/restaurant_allo_nouilles_test` 契约保留，但该端口
已有本机服务，本次不连接、不停止、不复用。此次新建合成 QA 仅允许 profile set
`allo-test,allo-qa` + `jdbc:postgresql://127.0.0.1:26512/restaurant_allo_nouilles_test`，
不能以 `allo-qa` 借用 Production 数据库；其他 host/port/DB 均不接受。
本机 QA 使用相同 Production policy 条件是为了合成验证，**不证明真实
Production 运行身份、设备在线或物理出纸**。

Flyway 前继续拒绝独立 Flyway URL/user、Allô/通用 Template 混合 profiles、外部平台 worker。
Production opt-in 还要求 `app.seed` 的 `runtime-enabled`、`force-overwrite`、
`safe-metadata-enabled`、`default-users-enabled`、`demo-data-enabled`、
`membership-supplement-enabled`、`production-bootstrap-enabled` 全部为 false，
两个 Test bootstrap 均关闭。`REAL` 不能出现在允许模式中；没有开启后端 TCP 路径。

Store mode 与 endpoint 写入另外受 `ALLO_NOUILLES_TEST` 精确 Store code 限定；其他
template Store 不能使用 PAD。候选发布计划仅开放 runtime policy；Store 保持 `MOCK`，
避免没有配对 Pad 时把普通浏览器操作转换成必须携带原生 HMAC 的 PAD 操作。

模板厨房路由沿原 Outbox/source key/PrintJob 队列工作，保存原
`PrintOriginContext.deviceFor(...)` 来源设备。只有受控 Allô 的 PAD 模式使用既有
`markPadDirectQueued`，没有新增 transport。旧 source-key Job 不重开、不重新附加内容；
MOCK 故障注入不会在 PAD 路径执行。

新增模板 `FrozenIntent` 与三语 `ReceiptPresentation` 均冻结提交时的
`execution_mode`。进入 PAD 的事件必须明确为 `PAD_DIRECT`；历史缺失或 MOCK 意图
只保留 inert MOCK 语义，Store 后续改成 PAD 不赋予历史 Outbox 物理执行权限。
不批量重写既有 Outbox/Jobs 来补来源。

三语凭条的新 PAD Job 从冻结文本，按实际 printer encoding/codepage/font/paper
生成 ESC/POS，并记录 `receipt_artifact.transport=PAD_DIRECT_ESCPOS_V1` 及 text/bytes
SHA-256。严格 encoder 拒绝不能编码的字符，不以替换字符假装成功。
原 `MOCK_ONLY_NOT_PHYSICAL_PROOF` bytes 及历史 Job 保持原样并禁止转成 PAD。
模板与三语 PAD 原件重印均要求源 Job 本身为 PAD、显式冻结 PAD 意图和有效 PAD
artifact，复制原文本/bytes/context；设备配置不兼容时
拒绝重新编码。已有手动重印幂等与状态保护保持。

PAD `pending/claim/start-print/payload` 同时在 controller/service 复核当前 Store/runtime
仍为 PAD。关闭模式后不能继续取新任务或 payload；已拥有的在途 `complete/fail`
仍可认证回报，即使 Printing module 已关闭。Store/Organization、device token、
claim token/lease、PRINTING 不重领和原生 HMAC 边界保留。

## PAD focused 验证补充

以下 227 项是实现期间的 focused 子集，不与上述合并后回归重复累加。

- `git diff --check`：PASS。
- Maven：主 Agent 释放共享 `target` 后，本实现 Agent 独占执行 16 个 focused 类；
  **227 tests、0 failures、0 errors、0 skipped，exit 0**。执行后已释放 Maven 锁。
  日志：`/tmp/allo-pad-provenance-focused-20261112.log`。
- 针对测试：`AlloProductionPrintingPolicyTest`、`TemplateTestRuntimeSafetyTest`、
  `TemplatePrintingBoundaryTest`、`ReceiptContextServiceTest`、`ReceiptArtifactTest`、
  `PrintJobServiceImplTest`、`PadPrintJobServiceImplTest`、`PadPrintingControllerTest`、
  `PrinterConfigServiceImplTest`、`PrintDispatcherServiceImplTest`、`AlloCorsConfigTest`、
  `CategoryKitchenRoutingTest`、`MenuCodeFormatterTest`、`AlloReceiptPresentationTest`、
  `StorePresentationServiceTest`、`StagingPadDirectPolicyTest`。
- 未创建真实订单/设备、未发送物理打印 bytes；服务器备份与候选镜像预置不代表业务发布。
- 实际 Pad pairing、Android Worker、机型/codepage/纸宽与实体出纸：NOT TESTED。

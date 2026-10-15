# Owner Decisions — 商业化产品约束

决策日期：2026-10-15。来源为 Owner 对 [Phase 0 二十问](06_OWNER_DECISION_QUESTIONS.md) 的最终选择：`1A,2A,3A,4A,5B,6A,7A,8B,9A,10A,11A,12A,13A,14A,15A,16A,17A,18A,19A,20A`。下表是已确认决定，不再将原报告的“推荐/待回答”当成开放问题。执行入口为 [Master Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，动态 Phase/Package/Gate 只属于 [CURRENT_STATE](../governance/CURRENT_STATE.yml)。

| 决策 | 完整决定 | 约束与后果 | 实施/验收归属 |
| --- | --- | --- | --- |
| Q01=A | 首批付费客户为面馆、中式快餐，之后扩展菜系。 | 共享语义仍须能表示非面食；使用中性 fixture 防止继续绑定现餐厅。首发不承诺覆盖所有全服务业态。 | P3-FIXTURE-05；P4-MENU-01；P8-PILOT-01 |
| Q02=A | 每个独立商家独立应用和 PostgreSQL 数据库；同一商家的门店可共用该运行单元。 | 分开配置、Secrets、备份、升级与回滚。独立库不能代替应用内授权；两组织测试仍为必需。暂不建共享 SaaS 控制面。 | P2-BASE-01；P2-AUTH-02；P7-DEPLOY-02 |
| Q03=A | 一个独立经营客户/数据拥有者对应一个 Organization，其自营门店属于该 Organization。 | 无关联加盟商不得因品牌相同共享私有数据；品牌模板共享需显式授权。组织身份不是餐厅名称或客户端传入的 ID。 | P2-AUTH-02；P2-PROFILE-04；P3-CONFIG-01 |
| Q04=A | 商家不获得平台 ADMIN；支持人员必须经商家授权、限时间、限资源/操作范围并留审计。 | 平台运维身份不等于业务数据通行权。Phase 2 先堵住无授权访问；未实现完整支持 grant 生命周期时业务访问默认拒绝，不以临时开 ADMIN 代替。 | P2-AUTH-02；P6-SUPPORT-03 |
| Q05=B | 通用 Modifier 分组可配置必选/可选、单选/多选和 min/max；按经确认需求支持数量及免费额度。 | 保留现组选项为 Profile preset；不用任意脚本、嵌套条件引擎。数量/免费额度的具体计价规则要在 P4 明确契约并测试，不猜商家菜单含义。 | P4-MENU-01；P4-MODIFIER-02 |
| Q06=A | Canonical Add-on 的 Org identity code 统一；Store 拥有名称、价格、active；单菜只拥有 eligibility。 | 继续复用 resolver/FK，不能重新在每菜和打印配置中维护第二套名称/价格。历史名称/金额冻结在订单快照。 | P3-READ-03；P4-MODIFIER-02；P2-GATE-09 |
| Q07=A | 套餐使用通用选择组并显式关联真实菜品，保留现有蛋/小菜 Profile 兼容。 | 旧负 ID/code payload 必须可回放；不能靠中文菜名或 SKU 猜关联。首发不承诺复杂嵌套套餐/促销。 | P3-PROFILE-02；P4-COMBO-03 |
| Q08=B | 门店默认规格价格政策允许显式单菜规格/价格覆盖。 | Store default 与 item override 有唯一读取优先级；不能把三规格差价宣称为任意 variant 模型。历史订单不重估；不提前引入渠道/时段促销。 | P3-CONFIG-01；P4-PRICE-04 |
| Q09=A | 离线旧价只在服务端限定的时间/version 政策内接受；越界、不可售项或冲突要求确认，重试身份保持稳定。 | 有效期数值尚未指定。Phase 2 不自行改变成交政策；Phase 4 在有版本/来源证明后实施，未知来源不接受任意客户端价。已冻结重试 payload 不自动改写，不清队列。 | P2-OFFLINE-06；P4-PRICE-04 |
| Q10=A | 每 Store 可选择 printer、KDS 或 hybrid，工位与输出角色配置化。 | 只承诺通过测试的组合。配置管理入口不依赖硬件已就绪；禁用模块必须同时约束 backend、workers、订阅和 frontend。 | P3-UI-04；P5-KITCHEN-01 |
| Q11=A | 使用中性版式和少量稳定打印 presets；商家可配置抬头、语言、别名、排序和份数。 | 沿用 published revision 与 job snapshot；不建自由脚本/拖拽模板设计器。别名不能创造可售商品。 | P3-PROFILE-02；P5-PRINT-02 |
| Q12=A | 首发支持整桌，以及可选现有 A/B 拆桌。 | 不承诺任意座位、拼桌或 split bill。保留活跃订单原桌号/后缀；无堂食门店可不建桌。 | P3-PROFILE-02；P5-TABLE-03 |
| Q13=A | 首发地域为 Quebec、币种 CAD；税、时区和营业日边界作为明确配置并验证。 | 现有 14.975% 常量不是对所有交易的税务结论。商家确认税务政策、税包含/不含、舍入、免税与生效日后才做上市验收；历史金额/日期不批量转换。 | P3-CONFIG-01；P4-PRICE-04；P8-PILOT-01 |
| Q14=A | 统一产品品牌，Store 拥有餐厅品牌资料；中英基础语言能力并保留语言扩展入口。 | 不承诺完整白标或已完成第三语言。如果目标商家适用法语要求，上市合规验收须识别、关闭相应缺口，不能用中英选择当成豁免。 | P3-UI-04；P5-PRINT-02；P8-PILOT-01 |
| Q15=A | 一个统一签名的通用 Android App，通过安全绑定选择商家和环境。 | APK、签名、bundled assets、Web/API/DB contract 分开记版本；切服要隔离 token、Worker、draft/outbox，不复制每商家业务源码。 | P2-OFFLINE-06；P7-ANDROID-01 |
| Q16=A | 首发认证有限的 ESC/POS TCP 型号组合，以 Android 原生打印；iPad 可作为浏览器点餐端。 | 9100/GBK/80mm 是协议配置默认，不等于任意设备支持。实机字库、宽度、切纸和结果不确定的防重验收逐型号完成。 | P2-PRINT-05；P5-PRINT-02；P7-ANDROID-01 |
| Q17=A | Uber Eats 为可选模块；Grab 外卖平台需求另立项。 | `GRAB` 是厨房打印模块。新商家的 Uber 授权、secret、mapping、生产激活分别受控；当前 Production off 不因商业规划改变。 | P3-UI-04；P5-INTEGRATION-04 |
| Q18=A | 运营协助建立独立 Org/首位 Owner，一次创建空白店或选择获授权 preset。 | 不做首发完整自助注册；复用现有 transactional materialization 和一步 Create→LIVE。不能复制真实员工/订单/设备/token/LAN endpoint；必需业务确认由运营处理，内部 readiness 不变成新增产品步骤。 | P3-PROFILE-02；P6-IMPORT-01；P6-PLAYBOOK-02 |
| Q19=A | 首发 POS、厨房、打印、基础报表；库存只承诺已验证基础能力，支付暂不集成。 | 现有 BOM 危险写入必须在 Phase 2 修复或严格关闭；restock/prep 文档不代表 API 已实现。完整库存/采购/支付在额外授权前延期。 | P2-INV-07；P5-REPORT-05；P8-PILOT-01 |
| Q20=A | 共享 Core/Modules/Profile 代码体系，各餐厅分别受控发布、验收和回滚。 | 不建立永久客户 fork；现兰州生产不跟随商业分支自动更新。Hotfix 通过独立 backport PR 和双 Profile 回归回灌共享 Core。 | P2-BASE-01；P7-DEPLOY-02 |

仍需采集的事实包括：安装 APK/签名/hash/asset manifest、认证打印机型号、实际客户税务/语言要求、旧价有效期数值、支持预算与客户 RPO/RTO/维护窗口、备份 retention/offsite 和恢复实测。它们不是再次询问以上二十项选择。局部任务只能在不依赖未知值时继续；硬件/商业发布承诺等待相关证据。

产品定位：菜单 PDF 直接在 Codex 会话中阅读，Codex 是外部工程助手。POS 不开发 PDF 上传、OCR/LLM API、AI 聊天或运行时 Agent；每商家交付的是配置和独立环境，不是新生成的 Spring/React 源码。

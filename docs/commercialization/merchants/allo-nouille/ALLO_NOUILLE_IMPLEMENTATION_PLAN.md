# Allô Nouille 商家定制实施计划

> 历史规划快照：2026-10-18 Owner Q1–Q9 与已授权本地试点的实际行为，以 [IMPLEMENTATION_RECORD](IMPLEMENTATION_RECORD.md) 为准；本文件原有待批准/未决项不再阻断已明确授权范围。

2026-10-18。DRAFT / SUPPORTING / NOT AUTHORIZED FOR IMPLEMENTATION。本轮交付仅是静态审查与规划；不改变任何既定产品决策或当前Phase/Gate。当前动态授权只看[CURRENT_STATE](../../../governance/CURRENT_STATE.yml)。

## 交付索引与结论

| 文档 | 用途 |
| --- | --- |
| [MENU_PDF_ANALYSIS](MENU_PDF_ANALYSIS.md) | 4页来源、32商品三语/价格、15加购、两套餐及未知项 |
| [CURRENT_CAPABILITY_GAP](CURRENT_CAPABILITY_GAP.md) | 实际分支/PR状态、E01–E18代码/测试证据、16项差距 |
| [MERCHANT_CONFIGURATION_DESIGN](MERCHANT_CONFIGURATION_DESIGN.md) | Neutral初始化、现私有Profile框架、Store-owned配置和数据关系 |
| [MENU_OPTION_COMBO_MAPPING](MENU_OPTION_COMBO_MAPPING.md) | SKU/option/Add-on/offer/portion映射、A1/A2份量与数量 |
| [UI_AND_PRINTING_DESIGN](UI_AND_PRINTING_DESIGN.md) | 点餐推荐与替代、语言、厨房路由与未来MOCK票示意 |
| [OWNER_CLARIFICATION_QUESTIONS](OWNER_CLARIFICATION_QUESTIONS.md) | Q1–Q9业务决定；已知规则不重复询问 |

审查对象为商业worktree `codex/commercial-phase3-foundation@cb6ff2e91409c3445d5a5e765e68f93599838638`；fresh origin/main为 `06bac51ddc0a829a9aa1106f07ef886572efbb65`。PR #275–#278只读核实仍为OPEN Draft，未合并。Git事实与源PDF见上述证据，不把静态可读测试当本轮PASS。

**不能仅关闭Size、配置Add-on/现Combo就完成Allô。** 新品无SIZE、固定单点价、零价加购、单品资格、Store隔离、Neutral空白与打印快照底座已经存在；缺少per-offer固定套餐价、通用选择/数量约束、准确portion和全组件履约、三语标签、可信旧价政策及通用配置导入。

推荐同一Commercial Core，Allô使用独立app+DB和精确Org/Store，从Neutral创建，再受控应用PRIVATE `ALLO_NOUILLE_PROFILE`。本轮不建Store/Profile，不修改当前Demo，不读取生产经营数据，不启动新商家环境。

## 与商业主计划对齐

依赖[Master Planbook](../../COMMERCIALIZATION_MASTER_PLANBOOK.md)、[Backlog](../../COMMERCIALIZATION_BACKLOG.md)、[P4 Entry Gates](../../PHASE4_ENTRY_GATES.md)、[P3 Acceptance](../../PHASE3_ACCEPTANCE_REPORT.md)。G3正式验收仍暂停/未闭合；既有视觉NOT RUN不能由本规划关闭。P4/P5/P6均为未来DEPENDENCY，没有实施授权。

以下**六个拟议feature PR**仅估计Allô需求直接触及的通用增量，不是六个PR包办商业化Phase4–6全部任务的承诺。现Backlog任务的其他DoD、P5-TABLE-03/P5-REPORT-05等P6前置仍有效；不需要的Allô功能不主动扩写，但也不能忽略既定Phase exit。若要批准更窄的交付Package，应由Owner明确其边界，不能本轮改写依赖或宣布某Phase完成。金额/日结、报表/桌台等现Backlog工作量可能另需既定PR，不能藏在商家配置里。

| 包 | 对齐既有任务 | 状态 |
| --- | --- | --- |
| AN-PR1 | P4-MENU-01 / P4-MODIFIER-02的必要选择约束 | PLANNED / BLOCKED：G3及P4授权、Q1/Q2/Q3 |
| AN-PR2 | P4-COMBO-03，offer/组件/份量契约 | PLANNED / DEPENDENCY：PR1、Q4 |
| AN-PR3 | P4-PRICE-04，固定总价与旧价/金额 | PLANNED / DEPENDENCY：PR1/2、Q8/Q9 |
| AN-PR4 | P4菜单契约兼容、P5票据语言输入 | PLANNED / DEPENDENCY：菜单契约稳定、Q7 |
| AN-PR5 | P5-KITCHEN-01 / P5-PRINT-02 | PLANNED / DEPENDENCY：P4稳定、PR2/3/4、Q3–Q7 |
| AN-PR6 | P6-IMPORT-01的私有配置应用 | PLANNED / DEPENDENCY：PR1–5及既定P6前置 |

## AN-PR1 — 通用选择约束与Optional Size补缺

| 字段 | 计划 |
| --- | --- |
| Goal | 在保留兰州Size的同时表达Allô无碗型、面型选择、free额度与C5选2 |
| Scope | 复用当前无SIZE路径；只补已配Size可清空、generic required/min/max/default/quantity及重复规则，不重建整套Menu CRUD |
| Existing APIs/Models Reused | MenuItem/options、OwnerMenuOptionController、OwnerMenuPricingPolicyController、StoreAddonService及其item eligibility、Menu catalog、现Modal |
| Required Changes | 通用组/assignment可缺省及兼容默认；明确no-default；同服务端契约校验C5计数/free quantity；新字段纳入revision。面型code是数据，不按Allô SKU分支 |
| Files/Modules | backend menu entity/DTO/service/controller与相应测试；StorePricingPolicyServiceImpl；order校验；frontend useMenuCatalog/useOrderSessions、ItemCustomizationModal、offline payload adapter；doc/API |
| Dependencies | G3 closure、明确P4授权、P4-MENU-01基础；Q1默认/交互、Q2额度、Q3重复政策；Migration编号待真实PR分配 |
| Test Plan | 复用OwnerMenuOption/StorePricingPolicy/Idempotent与Modal测试；无SIZE创建/编辑/提交/重开cart；数量边界、伪造组、C5少选/多选/重复、旧payload回放 |
| Acceptance Criteria | Allô无碗型但全链可提交；没有设置通用默认时不偷偷选第一项；C5与免费量服务端有效；原兰州Size/默认行为保持 |
| Regression Risks | legacy option_group/default解释、Size清空对草稿影响、重复/计数语义及离线旧字段 |
| Rollback Strategy | 先以兼容reader/feature配置关闭新规则；已持久化字段保留，前向修复；不删旧option、不清outbox、不改既有订单 |
| Definition of Done | 聚焦单测/HTTP/PG与必要前端回归通过、同Store负向验证、payload兼容和API文档、Agent6；不是只把SIZE UI隐藏 |

## AN-PR2 — 通用Combo offer、资格与准确组件份量

| 字段 | 计划 |
| --- | --- |
| Goal | 同Store独立表达Noodle/Rice两offer、A1–A9选择及单条春卷组件 |
| Scope | 扩展现StoreComboGroup/Component，新增必要offer关联/份量语义；不开发嵌套促销、脚本、无限套餐平台 |
| Existing APIs/Models Reused | OwnerMenuPricingPolicyController的combo-configuration、StoreComboConfigurationServiceImpl、linked_menu_item_id、combo_allowed、Store Menu Items及scope guard |
| Required Changes | per-offer main IDs和group assignments；durable组件ID；component quantity与frozen结构；受控component-only MenuItem/规格、通用可售visibility；旧负ID/hash显式adapter。同Store active引用，B4/C5服务端排除 |
| Files/Modules | menu/combo entity/DTO/service、MenuItem/catalog投影；frontend comboSelection/useMenuCatalog/Modal；order component DTO/snapshot与offline兼容；additive migration及doc/API |
| Dependencies | PR1稳定selection契约；Q4单条春卷制作关联；价格由PR3实现、完整任务由PR5实现，不能单凭本PR宣称套餐可营业 |
| Test Plan | 复用StoreComboConfigurationServiceImplTest；63种面+前菜结构、5种饭套餐结构、B4/C5拒绝、cross-Store真实引用拒绝；A2两条与受控一条不可混同；旧dynamic/legacy IDs回放 |
| Acceptance Criteria | 两套餐组互不串用；Rice包含一碗3颗汤+一条春卷；Noodle选A2仍两条；C5蛋白不引用整份饭；前台不可单点component-only项 |
| Regression Risks | 原Combo默认/5内置项、FNV/负ID离线记录、父子关系、隐藏组件误售 |
| Rollback Strategy | 新offer保持未启用直到PR3/5集成；保留旧mode adapter；新数据/快照不可用旧程序错误解释，采用兼容reader或前向修复，不删记录 |
| Definition of Done | schema/DTO/IDs/快照合同清楚、CRUD/catalog/负向/兼容通过、Agent6和文档完成；明确price/fulfillment仍DEPENDENCY |

## AN-PR3 — 固定套餐报价、确定性金额与可信旧价

| 字段 | 计划 |
| --- | --- |
| Goal | 单点、固定19.99套餐、Add-on、税/总额及离线成交具有同一权威计算 |
| Scope | 扩展通用price policy，不创建Allô engine；保留兰州delta兼容。仅纳入已明确税务与旧价规则，未确认部分阻止真实上线 |
| Existing APIs/Models Reused | store_pricing_policy、base_price、Store Add-on当前价、TaxCalculator、Order金额快照/幂等提交、menu revision、DB5 outbox及当前日结/报表合同 |
| Required Changes | per-offer fixed total=1999 minor units；included组件增量0，付费Add-on一次；后端BigDecimal、前端minor-unit或固定十进制库，传输格式兼容；受信schema/revision/expiry、无篡改旧价策略。新增tax policy版本/生效引用，不能通过现metadata PUT任意覆盖 |
| Files/Modules | menu/pricing、common/pricing/TaxCalculator、order quote/submit/snapshot、frontend tax/price formatting/useDraftOrder/useOrderSessions、offline replay；受影响report/dailyclose的既定任务与API合同 |
| Dependencies | PR1/2；Q8税处理、Q9旧价期限；现P4-PRICE-04价格/币种/时区及HC-006/010/011/012/015合同，不以本菜单需求缩掉原DoD |
| Test Plan | 15.99→19.99、16.99→19.99、17.99→19.99三类；1999×3=5997等精确分值；0/7.99/8.99/1.99/15.99加购；组件不另收费；边界舍入/税inclusive或exclusive按确认policy；伪造旧价/过期/停售/重放/同key异hash；订单/receipt/report金额守恒 |
| Acceptance Criteria | 前后端确定性一致；组件不能使套餐叠加A1/A2单点价；有效报价来源与版本可验证；超期给重新确认流程；已接受请求稳定重试不重价/换key |
| Regression Risks | 原14.975% HALF_UP规则、历史金额与日结、旧草稿容忍路径、退回旧版本误算新offer |
| Rollback Strategy | 新offer发布受控；保留现policy adapter和历史tax/price snapshot；必要时暂停新offer成交并前向修复。保留已接收订单/outbox，不回写历史金额、不把DB5降回DB4 |
| Definition of Done | 对应P4价格门槛、聚焦精度/版本/幂等/金额回归及Agent6通过；税与TTL有Owner决定；未证实税法适用不能写PASS |

## AN-PR4 — 菜单与快照的最小三语扩展

| 字段 | 计划 |
| --- | --- |
| Goal | 保留FR/EN/ZH菜单，独立选择显示语言，历史小票稳定 |
| Scope | 建议nullable name_fr及必要快照字段；不将完整法语后台国际化或Translation平台设为前置 |
| Existing APIs/Models Reused | 当前name_zh/name_en、Store locale、Menu/Addon/Combo投影、Order/PrintJob snapshot、Display Rule配置读取 |
| Required Changes | 对涉及名称实体/DTO增加可空法语；明确fallback与员工UI/菜单/票语言的边界；标签变化进入revision/hash；不重算旧job，不移除中英文 |
| Files/Modules | menu/option/addon/combo名称模型及additive migration、catalog、frontend名称选择/Modal、order/task snapshot字段；打印消费由PR5集成；doc/API |
| Dependencies | 基础Menu/PR1/2公共DTO稳定；Q7原文审校/默认语言。翻译整理可先行，schema/DTO修改串行合入，P5打印实现不提前越Gate |
| Test Plan | 32商品+15加购三语逐字段对照；缺译回退、Unicode重音、旧zh/en客户端忽略新字段；切语言不串Store；缓存修订；旧snapshot/job字节内容保持 |
| Acceptance Criteria | 原文保留，疑点未被默改；无Allô brand常量/跨Profile回退；前台菜单切语言不改变员工全局UI；历史票不跟当前译名变化 |
| Regression Risks | Catalog大小/hash、旧离线payload、打印字符集/宽度；实际设备字形仍待后续授权验证 |
| Rollback Strategy | 可关闭新法语显示，保留新增字段和冻结内容；旧reader忽略可空字段；不删已有三语数据或改历史打印 |
| Definition of Done | 标签SoT与fallback文档、必要backend/frontend兼容测试及Agent6通过；不宣称实体打印机已验证法语/中文 |

## AN-PR5 — 通用组件履约、路由与MOCK打印

| 字段 | 计划 |
| --- | --- |
| Goal | 每个套餐/加购按准确制作量进入自己的工位，小票与订单结构一致 |
| Scope | 复用KitchenTask/print infra，扩展通用fulfillment、Quick Add条件和角色/语言display配置；初始验收仅MOCK，不重做打印系统 |
| Existing APIs/Models Reused | OrderServiceImpl任务事务、单品station、现KDS/event after-commit、Printing Assignment/Display Rules、GRAB/FRONTDESK_RECEIPT/HOT_KITCHEN、PrintJob/claim/lease/冻结reprint |
| Required Changes | 遍历真实component结构；parent qty×component qty且BOM不重复；direct serve不再仅由固定category决定；移出通用路径的legacy COLD/WOK/GRAB白名单假设；保留显式兰州adapter。语言/品牌由Store配置和冻结标签决定，Quick Add不能跳过必选或制作 |
| Files/Modules | order/task/InventoryConsumption既有边界、kitchen/station/KDS、printing semantic/renderer/assignment/displayrules、frontend orderingCustomizationRules/打印设置/Preview；Menu fulfillment配置与合同 |
| Dependencies | P4稳定并明确P5授权；PR2/3/4集成；Q3–Q7实际份量/工位/语言。库存recipe不明时不能宣称真实扣料已验收 |
| Test Plan | 全组件task/角色payload/BOM需求计数；q=1/3的A1与A2对比；主菜/前菜跨station，同套关系清楚；C5蛋白不重复整盘；饮料/甜品Quick Add与需制作分别测试；NeutralProfilePrintingTest、兰州GRAB/HOT/前台黄金样例、故障/重印/去重回归 |
| Acceptance Criteria | Rice q3只出3条春卷/3碗9颗汤；Noodle A2 q3出6条；没有静默漏任务或双扣；receipt价等于订单；所有输出MOCK/DISABLED，无真实endpoint/dispatch |
| Regression Risks | 厨房备餐/ready推进、旧BOM、原station省略/排序/简写、Pad payload及不确定打印结果 |
| Rollback Strategy | 保留旧兰州adapter；新路由/offer启用前完整MOCK验收。故障暂停新offer/路由并前向修复，不回滚已成交订单、不盲重印或重建任务 |
| Definition of Done | 对应P5 task/printing合同、聚焦后端/前端/PG/MOCK与Agent6通过；明确物理设备/纸张及真实KDS流程未运行，不把MOCK当硬件证明 |

## AN-PR6 — 复用私有Profile的受控配置应用

| 字段 | 计划 |
| --- | --- |
| Goal | 将已验证商家配置安全应用到新Neutral Store，不建立第二Profile或菜单SoT |
| Scope | P6 manifest validator/dryrun/diff/idempotent apply必要增量；配置由外部会话整理，不内建PDF/AI，不允许任意脚本/SQL |
| Existing APIs/Models Reused | 现Owner Neutral BUSINESS创建流程、StoreProfile/version/artifact/fingerprint、StoreProfileAccessService、StoreAccessService、Store-owned Menu/Addon/Combo API背后的服务、事务/锁/请求协调器 |
| Required Changes | 以精确target scope+code解析引用、schema/capability兼容、revision CAS、同key同hash重放/异hash拒绝、整图事务；只为apply补必要操作记录/有界扩展。不能把原create ledger伪改为Allô来源，不能通过现四字段metadata PUT安装 |
| Files/Modules | owner/profile、owner/provisioning协调设施、merchant配置应用边界、menu/addon/combo各域服务、审计/revision、受控API/CLI及API/runbook；实际接口名待评审，当前没有可执行import命令 |
| Dependencies | PR1–5及现Backlog P6-IMPORT-01前置，含P5-TABLE-03/P5-REPORT-05等可导入实体合同；另获P6授权、真实target确认、配置问题关闭。不能为Allô单独绕过Phase gate |
| Test Plan | 复用NeutralBusinessCreationPostgresTest、StoreProfileIsolationTest/PG、MerchantConfigurationHttpTest；同scope apply/retry/CAS冲突/失败整图回滚；真实foreignOrg/Profile/version/artifact/child refs拒绝；目录不能泄漏；无真实凭据/订单/endpoint入包 |
| Acceptance Criteria | Neutral仍空白；Allô包PRIVATE、exact ownerOrg，受权后目标Store拥有独立数据；重复应用不复制菜单；dryrun不写业务状态；手工override冲突不静默覆盖 |
| Regression Risks | 原provision replay/provenance、Profile fingerprint、跨Org权限、半图/重复数据、code重名或旧导入覆盖新人工修改 |
| Rollback Strategy | dryrun先行；apply原子失败回滚自身事务，已成功后用受审前向diff/上一合法内容的新revision修复；不删已引用菜单、不恢复dump覆盖订单 |
| Definition of Done | Profile/Store隔离、CAS/幂等/事务/审计和dryrun/apply测试、Agent6及操作文档通过；当前API能力边界清楚，无额外Owner工程步骤 |

## 配置、并行与发布任务

AN-CFG是配置/数据包工作：依据32商品、15加购、明确资格集合及Owner决定整理私有制品。它不需要“再开发Menu CRUD”的feature PR；可以用同一文档/配置review随实际实现提交。现在只存在映射文档，不能先调用当前API写半套Allô菜单。所有必需Core能力及授权具备后才物化配置。

依赖主链为 `G3 → P4授权 → PR1 → PR2 → PR3 → P5授权/PR4集成 → PR5 → P6前置与授权 → PR6 → AN-CFG apply → AN-DEMO/AN-ACCEPT`。PR4的非运行翻译准备可早做；其Menu contract和阶段归属仍受主链约束。每个PR必须在其父依赖稳定后按exact SHA验证；不能六个PR同时修改公共Order或Catalog DTO。

| 可并行范围（未来获授权后） | 文件/责任边界 | 集成条件 |
| --- | --- | --- |
| AN-CFG原文校对与PR1/2实现 | 配置/翻译作者只改merchant包；Core作者改menu规则/组件 | 未知字段显式UNRESOLVED，不生成可apply制品 |
| PR4法语标签与PR3定价 | PR4拥有标签投影/翻译；PR3拥有金额/报价 | Menu entity/DTO、migration编号、Order snapshot公共文件由一次集成串行协调；若不能分离就串行 |
| PR5测试样例/打印布局准备与PR3 | 仅静态MOCK样例/独立renderer测试准备 | 实际task/OrderService和P5运行实现等待P4 gate；不能靠规划并行提前实施P5 |
| PR6设计/validator测试数据准备与PR5 | 仅稳定schema的静态包、权限/冲突用例 | 实际executor等待P5全部导入契约和P6授权，不能提前部署 |

这里描述未来工作分配，不代表本轮开启并行代码或创建PR。保持少量有业务边界的PR，发现相关缺口先合并同一修复簇，不拆成大量按钮/脚本PR。若范围超出六个Allô增量，先记录依赖与影响，不能借估算授权扩大开发。

AN-DEMO / AN-ACCEPT是未来验收工作包，不额外造feature框架：独立新数据库、独立app配置与浏览器origin、合成账号/订单、MOCK/DISABLED、无Uber真实店铺或真实设备。复用现有安全启动/验收路径；不覆盖当前5173/51025 Demo、既有本地DB或进程，不执行Docker清理。凭据仅保存在受限本地运行目录，不入Git、不复制进规划文档。

## 最终验收矩阵（全部PLANNED / NOT RUN）

| Scenario | 未来步骤与必须断言 | 证据 / 前置 |
| --- | --- | --- |
| A 兰州回归 | 原Size、Combo、Canonical Add-on、面型/厨房简写、GRAB/HOT/前台票均保持；旧订单重印/旧outbox可读 | 现单测/PG与golden样例复用，不用Allô数据改旧Profile；PR1–5 |
| B Neutral空白 | 正常API新建Neutral后菜单/option/Combo为零，无兰州/Allô数据；独立Store配置可进入 | NeutralBusinessCreationPostgresTest与未来真实HTTP，创建事务/provenance不伪改 |
| C 普通面 | B1=15.99、细/粗0增量、无Size；加料金额/购物车编辑/离线恢复/提交/任务/票一致 | PR1/3/4/5；必须覆盖Backend直接请求，不能只看UI |
| D 面套餐 | B1+A1=19.99且有主菜+前菜任务；穷举7主面×9前菜=63种结构/金额；B4请求拒绝；A2完整2条 | PR2/3/5；task与receipt/PrintJob冻结，组件不另计单点价 |
| E 饭套餐 | C1+soup1碗3颗+spring1条=19.99；5种主饭资格；C6同价、C5拒绝；q=3为3盘/3碗9颗/3条 | PR2/3/5；不得误用A2整份、不得生成6条；对照BOM而非只改显示 |
| F C5双拼 | 四选二，少/多选拒绝；按Owner决定测试重复；snapshot中两个蛋白/数量，厨房不生成两份饭盘 | Q3、PR1/5；不以名字包含判断 |
| G 多商家隔离 | 用实际存在的foreignOrg/Store/menu/order/offer/portion/Profile/version/artifact身份逐层越权；列表不泄漏；active membership/Owner撤权及锁后再检；同code跨Store互不串改 | 各PR现guard复用，不能只用不存在ID测404；Private根和所有child路径 |
| H 弱网恢复 | 同scope draft/outbox恢复，稳定key重试只一单；超时/冲突/旧价有效/过期/停售/新menu revision；翻译/份量/规则更新不重写旧订单/job；旧payload有兼容adapter | Q9/PR3及全部snapshot变更；DB5/account/Org/Store/origin不变，不清队列掩盖问题 |
| I 金额与税 | Add-on0/7.99/8.99等、多数量/边界舍入、included子项0增量、订单/前台票/报表守恒；按Q8经确认policy测含税/未税和分类 | PR3；当前代码税率不等于Allô合法适用，未确认不得PASS |
| J 浏览器与MOCK运行 | 登录/切Store/配置/Menu/Frontdesk/受控submit/Print Center/Preview/module开关；HTTP请求带实际localhost Origin/Host验证CSRF/CORS；768/1024观察无关键错误 | 授权独立Demo；不扩大origin白名单或禁用安全策略。浏览器受限时记录NOT RUN并交用户地址，不绕策略 |

测试优先复用E01–E18所列现有测试、聚焦新增约束，必要PG事务/隔离/真实HTTP/MOCK；Owner后续可在授权环境亲验UI。静态规划的场景和样例不是本轮测试执行记录。

## 最低风险实施与回滚路径

1. Owner审核本计划并回答影响首批契约的Q1/Q2/Q3；Q4–Q9按各PR前置关闭。本轮到此停止。
2. 单独恢复并关闭Phase3既定正式验收，保留真实NOT RUN/BLOCKED；G3通过本身不打开P4。不要为运行Allô先合并#275–#278；其review/合并须另按原授权与依赖链处理。
3. Owner明确授权后，按商业Backlog完成P4相关PR/验收，再进入经授权的P5/P6；每阶段只在其Package内实施。Agent6集中审查安全/金额/打印/离线边界，所有新增migrations只向前、编号由集成时确定。
4. 能力就绪后才以现Neutral API新建受授权独立目标、通过私有包dryrun/diff/apply写Store-owned配置；自动完成内部协调，不让店员执行schema/hash/provision步骤。
5. 独立本地合成Demo执行A–J，先证明金额/组件数量/跨店拒绝/稳定重试/历史快照，再做纸面及未来硬件验证。保持当前Demo/兰州/Neutral不变。
6. 后续部署准备提交exact SHA、制品、migration、备份/恢复与验收证据；Staging与Production按各自明确授权，真实Allô Store激活、打印设备和Production发布均不能从本计划推导授权。

应用rollback优先兼容feature配置/旧mode adapter和前向修复，不删除新增列、不改已应用migration、不清draft/outbox、不改已成交order/print payload。schema/payload不兼容时禁止盲降旧binary；先用兼容reader验证。配置恢复是发布旧合法内容为新revision，并保留手工override/引用关系和审计，不能dump覆盖后来经营数据。

本轮只修改本目录七份规划与必要文档索引；没有业务代码、Migration、数据库、服务、设备、PR或部署变更。现有未提交Demo文档及CURRENT_STATE保持原状；SYSTEM只添加规划入口。正式Phase3验收仍暂停，本计划不关闭G3、不进入P4。

## 本轮文档验证记录

| 检查 | 结果及边界 |
| --- | --- |
| PDF核对 | 4页原生提取+逐页渲染检查；32商品、15加购、2套餐，价格/份量/三语来源记录于PDF分析 |
| 文档完整性 | PASS：七份指定文档、六拟议PR各11字段、Scenario A–H及补充I/J均存在；相对本地链接210项有效 |
| Governance / diff | PASS：`python3 scripts/validate-governance.py`及`git diff --check`；CURRENT_STATE与任务起点逐字节相同，无tracked业务/Migration差异 |
| Agent6独立静态审查 | PASS / 无阻断问题；现能力/缺口、Phase前置、Private/Neutral provenance、19.99/份量/task/BOM、DB5/旧payload/history边界与真实代码合同一致；只读，无HTTP/SQL/秘密读取或文件修改 |
| 应用/运行验收 | NOT RUN：无build、应用测试、HTTP/SQL、浏览器验证、订单或打印执行；本记录不把未来A–J标为PASS，不证明Allô已可上线 |
| 变更范围 | 新建本目录7份MD；SYSTEM_DOCUMENTATION和Master Planbook只加规划入口；未stage/commit/push/创建PR/merge，未改变现Demo进程或经营数据 |

**ALLO NOUILLE PLAN READY — AWAITING OWNER APPROVAL**

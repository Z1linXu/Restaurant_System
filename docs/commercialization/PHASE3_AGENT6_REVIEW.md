# Phase 3 Independent Agent 6 Review

日期：2026-10-15。Reviewer未实施本报告所审产品代码；本阶段仅维护规划/证据文档并只读审查产品。本文是分批审查证据，动态Phase/Package/Gate只在[CURRENT_STATE](../governance/CURRENT_STATE.yml)。模块PASS不等整体G3关闭；最终结果由[P3验收报告](PHASE3_ACCEPTANCE_REPORT.md)汇总。

## Root打印批次 — PASS

审查范围：`f94c2f5..de1032c`中的`57fe6ad` printing/rules/renderer/semantic与Order新task标签适配，以及`de1032c`显式Lanzhou Uber fixture。`e565be0`的MerchantConfiguration接口仅核其打印依赖调用边界；尚未完成的Neutral/V40、config消费者、UI与最终PG/history不在本批审查完成声明内，后续稳定SHA再集中审查。

**结论：PASS；本批未发现新的阻断问题。** 已确认的边界如下。

| 审查重点 | 具体结果 |
| --- | --- |
| Neutral缺配置/字典 | [PrintingDisplayRuleServiceImpl](../../backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceImpl.java)的settings/default/draft/preview/activeContext经有效persisted Store配置选择；missing/invalid配置空Neutral字典。生产调用未绕用保留给legacy fixture的四参数context或默认factory；同Store已发布revision继续经过rule_set归属核对 |
| SKU/category/配料碰撞与own alias | [Neutral renderer测试](../../backend/src/test/java/com/restaurant/system/printing/renderer/NeutralProfilePrintingTest.java)覆盖beef_chow_mein/FRIED_NOODLE/tea_egg/remove等碰撞，完整customer标签保留，不调用兰州缩写/egg推断；显式Store item alias仍可用，不因Neutral清除用户当前规则 |
| 新task完整标签 | [OrderServiceImpl](../../backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java)仅在Neutral新task使用既有完整option/quantity/notes snapshot构造，跳过旧菜系primary/secondary缩写；actual create→submit测试保full label。Legacy开关true继续原路径 |
| 管理入口与readiness | 缺rule_set时getSettings只投影DEFAULT/empty revisions，不写rule/printer/assignment。OwnerPrintingController保留实际Store capability、module/feature ceiling，不把硬件已配置作为读取前置条件；preview不注入兰州示例 |
| 历史job与快照 | 本批未改Order/Kitchen历史更新、dispatch/claim/lease/idempotency。PrintDispatcher原reprintJob优先复用source.rendered_text_snapshot及payload_snapshot；仅旧缺snapshot兼容路径才用已固定的Store rule revision。配置更新无批量重算旧order/task/job路径；最终historyhash验收仍待P3集成，不把此代码核对当数据库historyproof |
| 原兰州默认 | PrintingDisplayRuleDefaults JSON/fingerprint无diff，四参legacycontext有明确兼容用途；原GRAB/HOT/order/rule lifecycle focused回归继续通过。UberPG fixture显式标PROVISIONAL_COMPATIBILITY，未把unknown Store重新默认为Lanzhou |

## 验证账本

| 执行者 / 来源 | 结果 / 限定 |
| --- | --- |
| Root，`phase3-core-focused.log` | 142tests，0failure/error/skip，BUILD SUCCESS；含MerchantConfigurationServiceTest |
| Root，`de1032c`，`phase3-printing-uber-postgres.log` | fresh loopback专用`commercial_uber_p3_printing`，真实Spring/V39，57tests，0failure/error/skip，BUILD SUCCESS；Reviewer核实际日志，不声称独立重跑PG |
| Reviewer，隔离文档worktree的`e565be0`相同printing产品树 | Java17 `mvn -o -Dtest=OrderServiceImplTest,GrabReceiptRendererTest,NeutralProfilePrintingTest,HotKitchenReceiptRendererTest,KitchenModifierTokenResolverTest,HotKitchenPrintEligibilityServiceTest,PrintingDisplayRuleServiceLifecycleIntegrationTest,PrintingDisplayRuleServiceConflictTest,PrintingDisplayRuleServiceValidationTest test`：**136tests，0failure/error/skip，BUILD SUCCESS**；日志`phase3-agent6-printing-focused.log`。没有产品源码修改，不与142/57相加为最终总数 |
| Diff检查 | `git diff --check f94c2f5..de1032c` PASS；产品工作树只读，未执行Production/Staging请求、真实设备/凭据/出纸 |

## 保留边界与下一次审查

固定GRAB/WOK/DEEPFRIED station/output-role、复杂Modifier/Combo/旧synthetic身份、税/完整i18n与未提取renderer按P4/P5分工；全部HOT_KITCHEN角色页面的既有权限债务仍P5。Neutral完整snapshot适配不等任意厨房路由、所有语言或硬件认证。Web关闭模块的零请求/订阅须由UI批次证明；原生独立headless Worker可能继续轮询，不发明未存在的stop API或声称全native零poll。

后续必须在稳定exactSHA集中审查config/Neutral创建/实际consumer与UI：Store Owner/foreign/ADMIN零写、锁后授权、V39/V40 fresh/V37upgrade、privateartifact与独立mutablegraph、revision/cache/跨店迟到响应、disabled模块、历史hash及G2安全负向。该项仍PENDING，本报告不擅自关闭G3或实施Phase4。

## Backend config / Neutral 批次 — REPAIR REQUIRED

源码：作者`66c947b`+`4058ca390a33aba4612ec86e4d0f7fe3448761da`，整合为`e565be0`+`9fdf5e79eee28ab42849bda7e6a02c5a826ee7b9`。本批审MerchantConfiguration DTO/API/strict写入、Owner创建/catalog分流、V39/V40、safe_summary的StoreContext/MenuCatalog/hash接线。UI刷新delta由另一Reviewer审；本Reviewer未实施产品修改。

**结论：存在一个阻断P1，A6-P3-CFG-01 OPEN；本批不能PASS。** 原Root打印模块PASS保持其受限范围，最终G3不能因现有测试全部通过而关闭此问题。

### A6-P3-CFG-01 — Neutral已发布内容的SHA-256指纹不符，专用validator仍接受

位置：`backend/src/main/java/com/restaurant/system/owner/profile/NeutralRestaurantProfile.java`第11、17–21行，以及`backend/src/main/resources/db/migration/V40__neutral_restaurant_profile.sql`第8行。

- 类常量与V40持久化`fingerprint_sha256`均为`3e696169e5609a9c8b7708bf9e5bb77a995a6539fc15155665f60f17095648ba`。
- 使用项目既有`StoreProfileCanonicalJson.sha256Canonical(NeutralRestaurantProfile.CONTENT)`实际计算为`1f2742fb30ee9fea4ddb9a11b182c0ad088a6f403611422998f69e063ad4e8dc`。JSON本身已canonical，raw与canonical的实际hash相同，排除了空白/键顺序解释。
- Java17 JShell直接调用现实现：`DECLARED=3e696...`、`CANONICAL_SHA256=1f274...`、`NeutralRestaurantProfile.validate(VERSION, SCHEMA, CONTENT, FINGERPRINT, List.of()).valid()`返回`true`。
- 专用validator只比较硬编码CONTENT/FINGERPRINT并回传同常量，没有把实际内容计算hash与expected比较；数据库/测试只对常量的断言也接受此不一致。新request和Store provenance因此绑定错误内容指纹，按规范计算的调用方fingerprint反被拒。影响G3-02的Profile指纹与G3-07的内容来源完整性。

修复须使受审内容、实际canonical hash、published metadata和validator一致，并增加数据库实际content重算hash、validator拒绝错误hash及创建/replay证据。V39/V40已在本地应用并冻结，须走有界forward repair或新的受审版本；不能通过改V40、重写既有ledger/经营历史或把hash改名为任意ID来关闭。具体最小实现由原作者/Root决定，Reviewer保持独立。

### 其余核对与实跑证据

| 范围 | 当前独立核对 / 限定 |
| --- | --- |
| DTO与写入 | 九字段safe summary不加载privateartifact；完整Owner管理对象另含实际Store/Org与现资源指针。PUT仅schema/expectedrevision/brand/locale，unknown/type/foreignref在lookup前拒绝；effectiveStoreOwner、锁Store→refresh→重授权、CAS/audit/menu revision在同一事务 |
| 真实资源路径 | MODULES读取`/api/v1/stores/{id}/modules`、MenuCatalog、platform stations/dining-tables/item-options、OwnerPrinting getOverview和combo-configuration均核现controller；指针不授予目标API权限，无新建任意URL或第二mutablegraph |
| Catalog/Neutral创建 | 原phase-b私有catalog与业务Neutralcatalog明确分流；原canonical code/version/artifact未改。Neutral仅BUSINESS、禁止Master refs，验证受审空内容和无artifact；复用request ledger/materializer，事务内重新核Org Owner。创建Store/config/modules/当前Owner membership，不复制菜单/工位/桌台/printer/设备/Add-on/其他staff；后段失败回滚业务graph，成功replay仍同Store |
| Migration/SoT/history | V1–V38无diff；V39相对首个scaffold字节未变，SHA-256=`c7e48bfdb352d7db88c903440a8b379c4985bebf461fed106011db65373f8e0e`。V39明确现存Store一次兼容snapshot；V40新增空public root/精确Neutral可空Masterledger约束。Canonical Add-on/Combo经营SoT保留；非空history row hash迁移/metadata更新测试通过。此正向证据不抵消上方新Profilehash错误 |
| Reviewer独立四组 | 从`9fdf5e7`的Git archive在临时本地目录编译，Java17、仅guarded `COMMERCIAL_TEST_POSTGRES_URL=jdbc:postgresql://127.0.0.1:55439/commercial_synthetic`，helper创建/删除自己的随机合成DB；MerchantConfigurationServiceTest/HttpTest/PostgresTest/NeutralBusinessCreationPostgresTest：**27 tests / 0failure / 0error / 0skip PASS**，日志`phase3-agent6-config-focused.log`。包括真实Bearer、PG/JPA、空白创建/replay、并发CAS、真实stale Store refresh后的403、失败回滚与非空historyproof；没有新增产品或测试源码 |
| 作者suite | 作者报告1038total/967executedPASS/71其他PGskipped，另四组27与相关10组48；保留其分支范围，不相加成整合总数 |
| Root最终后端 | `phase3-integrated-backend.log`在`9fdf5e7`：Java17、全部六类optional PG fresh目标，**1047/0failure/0error/0skip BUILD SUCCESS**；Reviewer核实际日志并复算JAR SHA-256=`49f9eea2deecfbb06f7d794c92dbaaece6168052272847cef068d4adbc577f5e`。本轮测试缺少上方hash断言，不能用suitePASS覆盖新P1 |

除A6-P3-CFG-01外，本批未识别其他阻断问题。修复后须集中复审受影响Profile/version/migration/request/metadata边界并重跑对应测试；G3最终仍需稳定UI审查、受影响整合回归与主线程唯一状态同步。未运行Production/Staging请求、真实设备/打印或Phase4实现。

## Backend fingerprint 定义更正 — A6-P3-CFG-01 WITHDRAWN，批次 PASS

更正日期：2026-10-15；源码仍为`9fdf5e79eee28ab42849bda7e6a02c5a826ee7b9`，**没有产品修复或迁移变化**。保留上节原始审查记录供追溯；其“A6-P3-CFG-01 OPEN / REPAIR REQUIRED”是Reviewer误判，已由本节撤回，不能再作为有效P1或迁移/新版本依据。Reviewer把canonical content摘要误当成了项目定义的Profile聚合指纹，未先核完整合同，这是此次false positive的原因。

项目既有[StoreProfileContractValidator.computeAggregateFingerprint](../../backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java)第121–144行对以下内容换行拼接后SHA-256：`profile_code`、`profile_version`、`schema_version`、canonical `content`、排序后的artifact身份与各自content摘要。`StoreProfileCanonicalJson.sha256Canonical(content)`仅是内容摘要；二者本来不应相等。当前Profile API/metadata使用前者。

Reviewer在Java17、同一Git archive编译classpath直接调用既有helper，独立结果如下：

| 独立调用 | 实际结果 |
| --- | --- |
| `computeAggregateFingerprint(CODE, VERSION, SCHEMA, CONTENT, List.of())` | `3e696169e5609a9c8b7708bf9e5bb77a995a6539fc15155665f60f17095648ba`，**等于类常量和V40所存Profile fingerprint** |
| `sha256Canonical(CONTENT)` | `1f2742fb30ee9fea4ddb9a11b182c0ad088a6f403611422998f69e063ad4e8dc`，仅content摘要 |
| Neutral validator以正确aggregate为expected | `true` |
| Neutral validator以raw content hash为expected | `false` |
| 保持aggregate但将`ORDERING_POS`改为`CHANGED_MODULE` | `false`；严格canonical内容白名单拒绝修改 |

**A6-P3-CFG-01 WITHDRAWN / FALSE POSITIVE；Backend config/Neutral批次 PASS，未识别其余阻断问题。** 现代码的固定完整内容白名单与正确aggregate常量组合没有本审查所声称的篡改接受路径；既有指纹/创建/replay/provenance定义一致。独立四组27/0failure/0error/0skip、Root1047/0/0/0、实际JARhash与上节正向边界核对保持有效，不需为误判改V39/V40、旧ledger、历史、profile版本或配置schema。

若Root选择增加“真实DB内容按既有aggregate helper重算、错误raw hash被拒”的回归，可作为防止将来常量漂移的有界加强，**不是本批未关闭blocker，也不是新迁移/框架要求**。本报告只撤回误判并更正此后端模块审查结论；整体G3仍待稳定UI独立review与最终集成/证据汇总，动态状态由主线程唯一CURRENT_STATE同步。


## 后端三项小收敛 — PASS

范围为`e6f9bc4`、`5024b74`、`711638e`，在已PASS的`9fdf5e7` backend config/Neutral边界上集中复核。**本批PASS，未发现新阻断产品问题。**

| 改动 | 独立核对与证据 |
| --- | --- |
| e6f9bc4 聚合指纹测试 | 只从fresh/upgrade真实Neutral row读取code/version/schema/content，调用现aggregate helper验证DB常量，并证明raw content摘要不同且被拒；`phase3-neutral-fingerprint-postgres.log`实际1/0failure/0error/0skip PASS。产品、V39/V40、旧ledger未改；进一步巩固前节false-positive撤回 |
| 5024b74 无效JSON局部400 | 仅MerchantConfigurationController添加`HttpMessageNotReadableException` handler，固定400/`MERCHANT_CONFIGURATION_INVALID_JSON`与通用消息；没有改GlobalExceptionHandler或暴露原异常内容。三个实际Bearer malformed/empty JSON反例验证零config/audit交互；`phase3-config-parser-focused.log` HTTP+PG23/0/0/0 PASS |
| 711638e scanner scope | 仅既有workflow复用既有CLI，显式扫描MerchantConfigurationService、NeutralRestaurantProfile、OwnerStoreProvisioningMaterializer三新入口。Reviewer独立执行该三路径scanner PASS/3files，原scanner3tests PASS；`git diff --check 9fdf5e7..711638e` PASS。是当前commercial defaults防线所需BLOCKING小扩展，不是新部署/治理框架；不声称remoteCI已运行 |

Root实际loopback HTTP证据`phase3-local-http-evidence.json`，source=`9fdf5e7`，隔离`commercial_p3_browser_qa`：Reviewer核14/14状态与预期一致，覆盖创建/replay、Owner GET/PUT/CAS、foreign读写403、unknownref400、privateversion403、StoreContext/MenuCatalog摘要一致；空白items/stations/tables/printers/devices/staff/jobs均0，未保存credentials/tokens。此证据来自真实HTTP，不是浏览器视觉验证。

Root post-minor源码711638e的`phase3-final-backend.log`已核 **1050/0failure/0error/0skip BUILD SUCCESS**（Java17、all六类PG、fresh *_p3_final2），Reviewer复算JAR SHA256=`4ee0c9e45779175aaadb5129d43ccfc704ade297e3c87a294bdb0459c775692e`；早批1047另保留，不混计。HTTP JSON已由1941ec6归档到仓库，两个本机HTTP/Vite验证进程由Root正常停止。UI刷新delta最终独立结论仍待齐。768/1024实际截图/布局QA **NOT RUN**：浏览器工具被用户权限策略拒绝，未尝试绕过。原G3-04/P3-UI-04明确要求布局/视觉证据，jsdom DOM功能测试不能证明实际无关键溢出；故即使基础实现/后端tests完成，不能据此把完整G3或Phase3标DONE。主线程按原gate保留G3-04/08缺口，完成其余已授权工作，无Phase4或runtime mutation。


## UI集中复审与最终本地打包 — PASS，视觉要求仍未完成

UI审查由**另一位独立security Reviewer**执行；本报告维护者未实施UI，也未冒充执行其101项测试。来源为作者`5ec8abe..de24ac50bc17bed18d0e056bfb127bdd41e5b622`，Root整合`55198bcc0acbd6744ddea157217810f8e07376a9`。该Reviewer向主线程交付consolidated PASS，独立**14files/101tests PASS + build + diff**，同时关闭以下两组问题。

| 原问题 / 归属 | 修复与独立复审关闭证据 |
| --- | --- |
| StoreContext Provider stale cluster | 原context仅mount/切店/online读取，menu revision/summary更新后展示与模块context不会及时收敛。复用现GET Context，以可见/在线的focus、visibilitychange、online和60s周期刷新；去重/abort，正常网络下focus或下一周期收敛。同scope保留environment/account/org/store registration，不无谓重注册或kick；真正scope变化/授权失败/卸载才释放。独立实际Provider/cache证明lateforeign不能显示/写cache，**CLOSED**；不承诺网络不可达时60s收敛 |
| 默认确认dialog P1 | 原OPEN聚焦真实默认DOM确认仍可在模块关/切Store/logout后驻留，旧scope信息/确认未随生命周期撤销；仅mock确认函数不足以关闭。默认dialog接受可选AbortSignal，原scope cleanup/auth-expired触发close/remove并resolve(false)。独立实际默认DOM保持打开→关闭模块/切店/logout验证立即移除；旧intent key保留，零latePOST/printkick，旧已提交job/claim/lease不改，**P1 CLOSED** |

本批新P0=0/未关闭新P1=0；预存纯HOT_KITCHEN完整角色页面P1仍归P5，未标CLOSED。UI功能测试与schema/fallback、跨scope取消、disabled请求/订阅/打印副作用、管理入口/readiness分离通过，不代表实际iPad布局或所有语言/角色已运营认证。

Root最终frontend源码55198bc：`npm ci`后**61files/363tests PASS + TypeScript/Vite build PASS**；Reviewer本报告核实际日志，作者delta7files/59focused与另一Reviewer14files101分别记账，不相加。npm新增jsdom后的审计对照已归档`fixtures/phase3-dependency-audit-comparison.json`（524494d）：Phase2导出1609f0d与55198bc漏洞完整对象相同，引入0，仍有既有13项（1critical/7high/4moderate/1low）。隔离本地记NON_BLOCKING_EXISTING_DEBT，外部release须适用性/patchreview；未自动auditfix，不宣称zero漏洞，不把npm severity等同本批tenantP0。

Root从55198bc archive复用既有bundleddebug流程：`phase3-bundled-debug-build.log` **54asset files/DB5/guard PASS、debugAPK BUILD SUCCESS**；`phase3-packaged-native-tests.log` **6suites/24tests/0failure/0error/0skip PASS**，verifyBundledFrontend实际开启。本报告核日志、XML并复算APK SHA256=`ecce54ae384a1097f94b768d4136312062a2472a46c55ab1f39a10488a893981`。未安装/分发、未接真实设备或实体出纸，不改变P7/P8发行/硬件未知。

**局部实现、自动测试与独立reviews完成；完整Phase3仍PARTIAL/BLOCKED。** 原G3-04明确要求的768/1024实际截图/布局NOT RUN（浏览器用户权限策略拒绝，未绕过），G3-08不关闭；DOM的dialog shim不是视觉证明。Draft PR [#278](https://github.com/Z1linXu/Restaurant_System/pull/278)是可审查交付，head=`codex/commercial-phase3-foundation`、base=`codex/commercial-phase2-security-offline`；remoteCI/finalhead由Root核验，不以本地PASS代替。无main merge、Staging/Production mutation或Phase4实施。

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

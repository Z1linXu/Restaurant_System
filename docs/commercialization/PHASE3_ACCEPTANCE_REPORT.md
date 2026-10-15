# Phase 3 Acceptance Evidence Snapshot

证据草稿日期：2026-10-15；已提交实现观察点 `e565be0`，包含打印边界 `57fe6ad` 与typed Merchant Configuration骨架。**G3-01…08全部PENDING；最终集成SHA/独立审查/全量测试尚未齐备，不能宣布Phase3完成。** 动态阶段、Package与授权仅由 [CURRENT_STATE](../governance/CURRENT_STATE.yml)保存；本文件是日期化证据，执行入口为 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，任务/HC/DG索引为 [Backlog](COMMERCIALIZATION_BACKLOG.md)。

Phase2的G2本地证据见 [P2验收](PHASE2_ACCEPTANCE_REPORT.md)：backend1013/0failure/0error/0skip、frontend306/build与native24、本地debugpackaging及独立review通过；这允许已授权P3隔离本地工作，不能替代本次新路径验收。Production/Staging mutation和main merge仍未授权；Phase4未授权。

## 实现身份与测试账本

| 单元 / 来源 | 当前可证明范围 | 不得扩大为 |
| --- | --- | --- |
| `57fe6ad` 中性打印 | [Printing compatibility](PHASE3_PRINTING_COMPATIBILITY.md)：empty Neutral dictionary、完整冻结item/option标签、无兰州name/SKU/egg推断、零价children保留、缺settings无readiness循环；legacy默认与历史job路径保留 | 任意工位/renderer、完整Modifier/Combo/定价引擎或物理打印认证 |
| `e565be0` 配置骨架 | MerchantConfigurationController/Service/typedDTO、V39、strictOwner四字段PUT、九字段safe summary、revision/audit/menu invalidation、有效persisted Profile兼容开关 | 已完成全部消费者/UI、通用继承/任意ref写入、可配置税算法或全商家onboarding |
| `phase3-core-focused.log` | **142 tests，0 failure/error/skip，BUILD SUCCESS**；本报告复核实际日志总计 | 最终集成全量通过或Agent6已审P3 |
| Root打印集成`de1032c`；`phase3-printing-uber-postgres.log` | **57 tests，0 failure/error/skip，BUILD SUCCESS**；fresh专用commercial_uber_p3_printing，真实Spring/V39与旧golden；与142focused分别记录 | 不相加为最终suite，不替代未完成的Neutral/UI/最终review |
| Neutral/V40、safe summary消费者、UI | 实现者仍在独立分支收敛；最终commits、HTTP/PG fresh+upgrade/history与frontend/native结果 **PENDING** | 不按未提交工作推定通过，不把预期接口当已存在 |
| 最终独立Agent6 / 总回归 | **NOT RUN / 等稳定exactSHA**；按受影响边界集中review | 不因P2作者/Reviewer身份或focused142自动继承P3审查结论 |

配置/Profile技术合同由实现负责人分别维护`PHASE3_CONFIGURATION_CONTRACT.md`和`PHASE3_PROFILE_DESIGN.md`；最终报告在它们提交并核对代码后接入链接，不在本草稿复制第二份schema。原始日志位于本机`/private/tmp/restaurant-commercial-baseline/`，本报告保留结果与source身份；临时路径不是长期唯一证据。

## G3-01…08 当前证据与待验项

| Gate | 本快照结果 | 已有代码/局部证据 | 必须补齐才能关闭 |
| --- | --- | --- | --- |
| G3-01 Contract/typed reader | **PENDING** | `e565be0`九字段safe summary与四字段strictPUT；schema/unknown/type/foreign/revision/revoked-after-lock unit；V39定义 | 最终合同与API一致、真实Bearer/同店正向/foreign和operator零写、消费者真实使用；不写入任意经营ref/minmax |
| G3-02 Neutral/private Profile | **PENDING** | P2 V38 private边界保留；e565 missing/invalid配置Neutral；目标是新空白Store与PRIVATE legacycompat | V40与原子创建/权限入口稳定SHA；fresh/V37upgrade、无菜单/桌台/staff/assignment/endpoint、不调用Store1clone、private不可读不可选、independent mutable graph与fingerprint不变 |
| G3-03 SoT/revision/cache | **PENDING** | config事务更新revision/audit/menu revision；Canonical Add-on及StoreCombo既有SoT保留 | StoreContext/catalog/UI使用同summary、menu/config revision刷新与跨店晚响应测试；旧outbox/synthetic payload兼容，不复制默认经营rows |
| G3-04 Modules/UI | **PENDING** | 原modulegraph与后端G2授权边界保留；UI分支在做有界消费 | 同build双Storebrand/modules、disabled零API/订阅、设置可达、切店/logout无foreign残留；768/1024可复核视觉证据；真实角色债务不冒充完成 |
| G3-05 Lanzhou compatibility | **PENDING** | `57fe6ad`Neutral完整标签/空字典/不隐藏零价child与legacy分支分离；142focused通过 | 最终兰州合成面型/egg/side/Add-on/Combo/tableAB/GRAB/HOT/Frontdesk/离线回归与golden；对未迁出算法明确PROVISIONAL_COMPATIBILITY |
| G3-06 持续安全 | **PENDING** | G2已知Auth/WS/Profile/Print/Offline/BOM负向基线 | 最终新config/Neutral入口HTTP与actualresource scope；受影响G2所有负向回归及独立Agent6关闭新P0/P1；不能放宽generic Store POST或ADMIN |
| G3-07 History/migration | **PENDING** | V39 additive；打印适配保留已有job原payload，未修改旧migration | V39/V40 fresh/V37upgrade/allPG、V1–V38checksum；config更新前后真实合成历史order/item/option/task/job/hash不变，新task/full标签；保留DB5/claim/retry限制 |
| G3-08 证据与退出 | **PENDING** | 本草稿与Backlog保留53HC/22DG唯一主归属、六P3任务与13P3 HC明确待验/延期 | 最终exactSHA、完整tests/counts、独立review、contract/Profile/API/SYSTEM同步、remainingP0/newP1明确、rollback、[P4进入门槛](PHASE4_ENTRY_GATES.md)；所有G3通过后由主线程更新唯一CURRENT_STATE |

未关闭P0/新P1数目在最终review前为 **PENDING**，不能写0或PASS。目前不是宣称发现了P0/P1，也不是未知即失败；这是尚未完成的独立验收。

## 本阶段13项 HC 的具体处置

| HC / 主任务 | 当前实施或适配边界 | 测试与最终处置 |
| --- | --- | --- |
| HC-013 / P3-READ-03 | 复用`StoreAddonOptionResolver`的Orgcode/Storeprice-name/Itemeligibility；typedmetadata不复制15项default，Combo只读Store-owned rows | Canonical/Owner/POS/Order source一致与revision/cache回归待整合；完整Modifier/Combo/price **DEFER P4** |
| HC-016 / P3-PROFILE-02 | 不从枚举为Neutral注入五个套餐；保留显式legacy Profile的既有rows读取 | Neutral零combo初始化/显式添加普通商品及legacygolden **PENDING**；synthetic option/组算法 **DEFER P4-COMBO-03** |
| HC-021 / P3-PROFILE-02 | `PrintingDisplayRuleServiceImpl`/Context对Neutral默认空字典，完整snapshot标签；现店字典只在有效legacycompat使用 | NeutralProfilePrintingTest与rule lifecycle包含focused142；最终兼容/配置missing证据 **PENDING**；任意renderer/preset **DEFER P5-PRINT-02** |
| HC-027 / P3-UI-04 | 复用现modules/Storecontext，不把固定noodle/hot/pass/shelf重命名成通用引擎 | disabled零request/subscribe与UI切店 **PENDING**；专用厨房DTO/任意station语义 **DEFER P5-KITCHEN-01** |
| HC-028 / P3-PROFILE-02 | Neutral显式初始化不能调用Store1clone；原同品牌clone只保授权PRIVATE legacycompat | 新创建路径不触发clone与foreign/private负向 **PENDING**；不改原clone数据和历史Store |
| HC-029 / P3-PROFILE-02 | 显式NEUTRAL_RESTAURANT@v1；原ST_DENIS_CANONICAL_PROFILE@v2 code/fingerprint不改，不再靠missingconfig恢复Lanzhou | V40/public安全Neutral/Owner原子创建/HTTP与PG **PENDING**；完整商家import/wizard **DEFER P6** |
| HC-030 / P3-PROFILE-02 | 新空Store不复制两桌/人员/printerroles/assignments，硬件可选；既有legacydefaults不被清理 | 空白counts与StoreLIVE/配置入口可达 **PENDING**；真实打印认证 **DEFER P7** |
| HC-032 / P3-UI-04 | safe summary提供Storebrand/locale；保持双字段，最小前端消费，不新增工程stage | 同build双Storebrand/locale/768/1024 **PENDING**；完整语言/法语/纸张覆盖 **DEFER P5/P8** |
| HC-039 / P3-PROFILE-02 | V15/V19保持不可变，V38已保private；V39只snapshot现存安装，V40不复制经营菜单 | fresh/V37upgrade中legacyseed不可见/不可误选与Neutral无物化 **PENDING**；分发包彻底净化 **DEFER P7** |
| HC-040 / P3-PROFILE-02 | 复用现StoreProfile/artifact/Ownerprovisioning原子事务、reservation/fingerprint，不建第二framework | independentStoremutablegraph、重试/replay/失败零部分写 **PENDING** |
| HC-041 / P3-PROFILE-02 | commercial startup继续拒runtime seed；Neutral入口不调用餐厅demo/LAN默认，旧seeder不批量删除 | seedflag guard与Neutral创建noLAN/noendpoint **PENDING**；不可达旧mock清理仍P7 |
| HC-046 / P3-UI-04 | 复用FeaturePackage/modulegraph与后端ceiling，配置管理和runtime readiness分离 | disabled无API/WS、directAPI仍deny、missinghardware不锁管理入口 **PENDING**；不建第二feature系统 |
| HC-048 / P3-CONFIG-01 | typedbrand/locale/metadata、严格OwnerPUT；安全summary不带私有artifact。Logo/地址/电话不因此成为已支持字段 | schema/owner/revision局部unit已在142；完整HTTP/consumer **PENDING**；素材/完整receipt品牌presets **DEFER P5/P8** |

全部53主归属保持：P2/CORE14项（001/002/003/004/005/007/008/033/034/036/038/047/049/053）；本阶段13项如上；P4九项（006/010/011/012/014/015/017/018/019）；P5九项（020/022/023/024/025/031/042/043/044）；P7六项（026/035/037/050/051/052）；后续范围两项（009库存、045新connector）。全部22DG原主任务保留，P3重点DG-11/12/13只在对应创建/SoT证据齐全后记有界闭合，DG-20税/币/时区完整业务语义仍P4。禁止把P2本地通过或P3骨架说明成全部53已通用化。

## 保留限制与退出

`PROVISIONAL_COMPATIBILITY`仍包括旧synthetic/enum选项、面型/egg/side/完整price、固定station/outputrole与尚未提取renderer；分别归P4/P5。纯HOT_KITCHEN完整角色页面债务继续P5，不能扩厨房财务权限掩盖。旧价TTL未定，不猜值；locale metadata不等完整法语；无运行时PDF/AI。历史Production bit、实装APK/签名/Keystore/WebView、物理出纸与恢复SLA仍按P2分级未知，不能以新本地build消除。

安全恢复仅为兼容forwardfix；保留V39/V40新增列/rows、private授权和Neutral failsafe，不回退全局兰州字典。配置错误可停可选printing或拒受影响写，不能重算历史order/task/job、改变claim/retrykey或盲重打。DB5旧未知来源records继续原样隔离，缺credentialassociation的旧authsnapshot须在线验证，不直接降旧DB4 APK，不清draft/outbox。无任何Production/Staging操作。

全部G3通过后只可记录`PHASE 3 COMPLETE — READY FOR PHASE 4 REVIEW`并停止；Phase4需独立授权。最终证据不足时记录`PHASE 3 PARTIAL/BLOCKED — REMAINING WORK LISTED`，继续已授权有界修复，不能通过改写gate绕过。

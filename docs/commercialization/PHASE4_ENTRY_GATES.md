# Phase 4 Entry Gates — Contract and Deferred Work

本文件列未来Phase4可领取条件，不保存当前Phase/Package/Gate，不授权实施。唯一动态状态为 [CURRENT_STATE](../governance/CURRENT_STATE.yml)，执行索引为 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)。Owner本轮只授权1→2→3；**Phase4 NOT AUTHORIZED**。本草稿对应P3观察点`e565be0`，G3仍PENDING，不能因本文件已存在开启Phase4。

## 必须先满足的进入条件

| 条件 | 所需证据 | 当前证据状态 |
| --- | --- | --- |
| G3完整关闭 | [P3验收报告](PHASE3_ACCEPTANCE_REPORT.md)的G3-01…08最终exactSHA、全量与focused、migration/history、独立Agent6、remainingP0/newP1与未跑项明确 | **PENDING**；142focused不替代最终集成 |
| 后续范围授权 | Owner明确打开Phase4及对应Package，主线程同步唯一CURRENT_STATE | **NOT AUTHORIZED**；不在本轮重新向Owner请求 |
| 稳定配置/Profile边界 | typed九字段safe summary、严格StoreOwner写入/revision、现API指针与逐资源授权；PRIVATEcanonical与明确Neutral原子创建；不以missingconfig选择legacy | 合同与最终实现 **PENDING**；实现者维护技术合同，本文件不复制schema |
| 单一菜单/价格来源 | Canonical Orgcode→Storeprice/name→Itemeligibility；现Store-owned combo rows；Owner/POS/Order/printing/缓存revision一致 | 既有SoT保留，P3consumer与最终测试 **PENDING** |
| 历史与失败恢复 | order/task/job/payload/hash冻结、stableoutboxkey、DB5兼容、V1–V40checksum/升级证据、配置forwardfix | P3history/upgrade最终证据 **PENDING**；禁止回写历史或直接降旧DB4 APK |
| 准确兼容清单 | 逐项`PROVISIONAL_COMPATIBILITY`、P4/P5 ownership与golden，不宣称全部菜单/renderer通用 | 下表定义待领取范围；未实施项保持延期 |

## 可领取的四个既定任务

任务完整步骤、测试、risk/rollback/DoD保留在 [Backlog](COMMERCIALIZATION_BACKLOG.md#p4-menu-01)。不增加新框架，不预设缺失API或选用新DSL。

| 任务 | 输入与要解决的具体限制 | Phase4必需证明 / 不属该任务 |
| --- | --- | --- |
| P4-MENU-01 | 现Store-owned分类/item/option/station/BOM引用及P3Neutral/Profile；HC-013保现CanonicalSoT，HC-014/015关联稳定规格身份 | 普通非面类商品/variant、同Store refs、Owner/POS/Order一致、历史和outbox兼容。不能仅改UI名字宣称全菜单通用；不复制菜单进入merchantmetadata |
| P4-MODIFIER-02 | HC-014/019固定REMOVE/SOUP/NOODLE及面型/egg/side控件；现Add-on独立语义 | required/single-multiple/min-max/default/quantity与合法免费额度均由backend/UI同契约验证，unknown/type/foreign拒绝；旧fixturegolden继续。没有任意脚本/嵌套促销；renderer全面提取P5 |
| P4-COMBO-03 | HC-016/017/018五项legacy枚举、name/SKU关联与synthetic负ID/hash；现Storeownedgroups/components实际rows | 显式linkedrealitem、stableidentity、min/max/default、同店refs，price/BOM/kitchen/payload一致；旧outboxroundtrip与retrykey不变；无五项全局fallback。旧ID不能未经迁移直接删除；任意厨房路由P5 |
| P4-PRICE-04 | HC-006/010/011/012/015：Storedefault/itemoverride、QC/CAD税/舍入、Toronto营业日/DST、有限旧价服务端来源/revision/有效期 | 先明确policy/来源/有效版本与期限，拒伪造价格/timestamp，过期/停售交互明确；已提交重试key/hash不变、历史金额不重算、金额/报告守恒。TTL数值当前UNKNOWN，不猜数；多币/全部地域税合规非默认已支持 |

以上任务依赖P3稳定入口，互相共享DTO/金额/订单snapshot时串行；独立测试fixture可在接口确定后并行。不得让Phase4实现重开ADMIN、foreign资源、匿名topic或privateartifact路径。

## 继续延期的边界

| 范围 | 责任 / 理由 |
| --- | --- |
| 任意station/outputrole、完整厨房DTO、固定WOK/DEEPFRIED/GRAB、餐厅renderer/纸张presets | P5-KITCHEN-01/P5-PRINT-02；需P4稳定业务语义和旧paper/payloadgolden。P3Neutral完整snapshotadapter不等通用路由 |
| AB/counter、报表category、Uber许可/映射 | P5-TABLE-03/REPORT-05/INTEGRATION-04；不将GRAB当Grab平台，真实外部授权未具备 |
| 全商家blank导入/向导、manifest/diff/dryrun/idempotent执行、支持grant | P6-IMPORT-01/PLAYBOOK-02/SUPPORT-03；复用外部Codex工作流，无系统内PDF/AI |
| 签名Android/实装/硬件、独立交付/backuprestore/秘密生命周期 | P7-ANDROID-01/DEPLOY-02；P2本地debugAPK与P3源码不证明现装/physical/SLA |
| 真实Pilot、语言/适用法语/税务/品牌素材与每商家发布 | P8-PILOT-01及具体授权；不以P3/P4开发通过营销承诺ready |
| 完整库存restock/prep、新外卖connector | FUTURE-INVENTORY-01/CONNECTOR-02；不在既定首发范围，缺失API仍缺失 |

Phase4进入只允许明确授权的隔离本地实施；main merge、Staging与Production仍须各自具体权限，不能从本文件或技术PASS推定。当前不实施Phase4，不新增部署/证据生命周期工具。

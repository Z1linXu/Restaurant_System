# Commercial Acceptance Matrix

此表定义技术 gate 和证据要求，不记录第二份当前阶段状态。动态状态在 [CURRENT_STATE](../governance/CURRENT_STATE.yml)，领取顺序在 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，实际结果/commit/命令/counts回写 [Backlog](COMMERCIALIZATION_BACKLOG.md) 及阶段报告。本文件初始期望均 `NOT RUN`，不是已通过声明。源码事实不等于runtime验证，历史70unit PASS也不等于本轮。

## Phase 2 Gates

G2-01…09 全满足且P0=0、BLOCKING_BASELINE_GAP=0才允许P3依赖性改造。明确未知的生产bit一致/安装APK/物理QA可按Planbook列NON_BLOCKING_LOCAL_DEVELOPMENT_GAP，只允许本地安全开发，不证明生产已逐字节重现、APK/硬件已认证。来源关系不明、失败测试、token/WS泄露不能降级为“未知”。

| Gate | 风险 / 主任务 | 必需自动证据与成功标准 | 失败/未知的处置 |
| --- | --- | --- | --- |
| G2-01 组合基线 | P1；P2-BASE-01；HC-001/035/037/039/051，DG-08/10/17/22 | B/F/M ancestry/tree/source、既有image证据、V1–37checksum、toolchain/buildargs/configrefs分别记录；干净本地build结果和hash；商用worktree独立且旧Owner work未改 | source/schema关系不可解释→BLOCKED。历史bitrepro/APK不明逐项BASELINE_GAP，限定只本地；不称完整Production复现 |
| G2-02 Auth/资源scope | P0；P2-AUTH-02；HC-002/004/005/038 | 两Org、多Store、Owner/Manager/Frontdesk/operator/anonymous；foreign path/body/resource deny；PlatformAdmin实体实际归属、Staff all-store/跨店搬移、AuditLog无store、Analytics global rebuild均负向；inactive membership不fallback；本店旧流程正向 | 任一无授权业务访问→P0 BLOCKED；无完整support grant先deny，不能以ADMIN兜底 |
| G2-03 WS | P0；P2-WS-03；HC-003/036 | 本地服务器CONNECT/SUBSCRIBE/持续会话授权；匿名/过期/badtoken/错误origin/foreignStore/未知通配destination/SEND拒绝；撤权后不收事件，本店重连仍refresh | 只测interceptor未测连通或撤权→BLOCKED/补测试；旧安全缺口不能暂开 |
| G2-04 私有Profile | P1隔离门槛；P2-PROFILE-04；HC-053 | public显式安全；private A在B list不可见、按code/version/artifact读取/使用/clone/publish越权失败；不存在的API不假报测试；owner推导唯一才授权，歧义unowneddeny；V37升级/旧checksum不变 | V19同masterkey跨Org不证明owner；不硬写Org1；不确定绑定保持private不可用，真实legacy归属需后续审查 |
| G2-05 Printer/Device | P1隔离门槛；P2-PRINT-05 | foreign job/payload/claim/start/ack/fail/reprint/pending不可达；assignment不能引用foreignprinter；token仅有效Store/device；单claim、lease和ambiguous防重；本店MOCK正向 | 跨店payload或endpoint泄露→BLOCKED；未物理出纸标HARDWARE_QA_PENDING，不冒充整个硬件矩阵 |
| G2-06 Offline/Android环境 | P1隔离门槛；P2-OFFLINE-06；HC-033/034 | 同bundledorigin两server同数字ID不串cache/outbox/token；DB4→5旧record原样隔离不认领/删除/回放；key/hash不改；logout/换account/店/重配对/切服与401/403；native Worker停止/可信bridge/凭据origin有可重复验证 | Android安全路径没验证不能只凭JS pass结案；物理设备未知单列；DB5不能直接降旧APK，保持兼容修复包与恢复方案 |
| G2-07 BOM/库存 | P1现启用写入门槛；P2-INV-07；HC-007/008/009 | 临时PostgreSQL并发两订单扣同库存，余额/成功数/流水守恒；item/optionBOM跨Store拒绝且事务零部分写；retry只扣一次；未实现prep/restock标缺失而非成功 | 危险submit deduction不可用“库存未来做”掩盖；修复或严格不可执行；相关gate未过留BLOCKED |
| G2-08 回归/CI | P1；P2-FIXTURE-08，DG-19 | 两Org合成fixture可重复；staticguard拒真实域名/IP/secret进入新commercial默认；旧migrationchecksum保护；订单幂等、Add-on/Combo/金额、offlineoutbox、打印防重/历史job快照；Postgres opt-in真实运行 | skip不算pass；scanner不批量改历史guard/SQL；无真实网络/打印/订单数据 |
| G2-09 集成与隔离 | P0/P1汇总；P2-GATE-09，DG-14 | exact集成SHA下前述证据、tests counts/命令、requiredreview、API/SYSTEM与HC/DG回写；现兰州合成关键流程通过；无Production/Staging mutation、真实secret/data进git | P0或blockingbaselinegap未关闭只能PHASE2 BLOCKED；不得进P3；已授权范围内可继续独立任务 |

## Phase 3 Gates

| Gate | 主任务 / HC | 自动验收与证据要求 | 不能替代的事实 |
| --- | --- | --- | --- |
| G3-01 contract/read层级 | P3-CONFIG-01；HC-048 | schema/version/unknown/ref/override/default/failsafe；安全ceiling不被item/device/user覆盖；Typedreader源自现SoT，不第二写库；同店负向 | 概念表或编译不能代替实际reader与消费者 |
| G3-02 Profile/Neutral | P3-PROFILE-02；HC-016/021/028/029/030/039/040/041 | freshDB和V37upgrade后，Neutral创建无兰州经营菜单/缩写/桌台/人员/设备endpoint，privatelegacyartifact不可读不可选；canonicalcode/fingerprint保留；独立mutablegraph | V15/V19旧seed存在只能privatequarantine，不能说SQL已删除；完整分发净化留P7 |
| G3-03 来源/版本缓存 | P3-READ-03；HC-013 | Owner/POS/Order/打印同stablecode/ref；canonical name/price只Store；事务revision提升与Web/Android缓存刷新；legacynegativeIDpayload可回放 | 通用Modifier/Combo/price尚P4，不用旧UI改名宣称完成 |
| G3-04 模块/UI | P3-UI-04；HC-027/032/046 | samebuild两Store不同brand/modules；切店不残留foreigncontext；disabled无API调用/订阅/后台动作、直API仍deny；配置管理入口可达；768/1024无关键溢出 | 不以隐藏route充当安全；不增加readiness/fingerprint产品步骤 |
| G3-05 兰州兼容 | P3-FIXTURE-05；HC-001/016–024/031 | 合成面型、egg/side、Add-on、Combo、table/AB、GRAB/FRONTDESK/HOT、离线；旧golden、payload、金额/default可解释地一致；未迁出分支标PROVISIONAL_COMPATIBILITY | 不删旧SKU/name/renderer分支换“无硬编码”；实机纸张待对应授权 |
| G3-06 持续安全 | P3-FIXTURE-05；HC-002/003/005/008/033/034/053 | 重跑G2 Auth/WS/Profile/Device/offline/库存受影响负向；两Org隔离不被config/profile绕过 | G2历史pass不覆盖本次变更的新路径 |
| G3-07 历史与迁移 | P3-READ-03 / FIXTURE-05 | 配置更新前后order/item/option/task/printjob/hash不变；新job读新revision，旧job原payload；旧SQLchecksum不变、新migrationempty/upgrade可执行；DB5回滚限制保留 | 不能用更新当前菜单去回填历史显示或价格 |
| G3-08 完整证据 | P3-GATE-06；全部本阶段HC/DG | exactSHA、commit/review、实际counts与未跑项、HC关闭/延期、Config/Profilecontracts、P4可领取列表、无线上变更；所有G3过 | P3完成只表示骨架稳定，不表示PDF可立即商家上线；随后停止，P4另授权 |

## 通用安全用例维度

资源测试同时覆盖 path Store、body Store、实际 entity owner、parent-child ref；合法actor但错Store仍deny；身份失效/Store inactive/membership撤销均核当前策略。错误不能带foreign内容。后台worker/定时任务/WebSocket/nativebridge不因为没有HTTP页面而免测。至少Org A的A1/A2与Org B的B1，构造本店成功、同Org权限差异、foreignOrg拒绝与部署相同数字ID四种情况。

## 后续上市/发布验收（不属本轮完成声明）

| Gate | 责任任务 | 完成证据 |
| --- | --- | --- |
| L4 菜单与金额 | P4-MENU-01 / MODIFIER-02 / COMBO-03 / PRICE-04 | 非面食选择组/数量/默认/单菜variant/税舍入/有限旧价、伪造价拒绝；历史snapshot与旧outbox兼容 |
| L5 厨房/纸张/桌台/报表 | P5-KITCHEN-01 / PRINT-02 / TABLE-03 / REPORT-05 | 自定义station、printer/KDS/hybrid、有限presets、AB/counter、非面类report总额守恒；认证型号物理纸张另记 |
| L6 Onboarding | P6-IMPORT-01 / PLAYBOOK-02 / SUPPORT-03 | 两合成菜单端到端、diff/dryrun零写、幂等/TOCTOU/事务故障、私有grant到期撤销；无PDF/AI运行时 |
| L7 交付恢复 | P7-ANDROID-01 / DEPLOY-02 | APK签名/hash/asset/contract、实装与有限硬件矩阵、DB5兼容、独立secret/DB、合成backuprestore/RPO/RTO/retention/监控责任 |
| L8 商家上市 | P8-PILOT-01 | 目标商家的税政策/营业日/语言义务（含适用法语）明确且验证、品牌素材授权、完整业务Pilot、独立exactrelease与人工Production批准 |

任何Production发布/恢复、真实凭据/设备/订单动作都需其具体授权。本次禁止Production与Staging mutation，以上表只定义未来证据义务。完整库存与Grab平台不在Q19/Q17首发承诺；未来API未提供不宣称现有。

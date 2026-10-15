# Codex-Assisted Merchant Onboarding Design

目标是运营协助交付一个配置驱动的独立商家 POS。执行顺序见 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，P6 工具任务在 [Backlog](COMMERCIALIZATION_BACKLOG.md#p6-import-01)，产品边界取自 [Owner Decisions](OWNER_DECISIONS.md)。本文件是设计，当前未声称可直接将任意 PDF 部署为商家。

## 标准交付过程

客户菜单 PDF → Codex 在会话中阅读 → 结构化配置草案及来源页码 → 未确认业务问题 → Owner 确认 → Schema/ref 校验 → 无写入 Dry Run/差异预览 → 独立合成/商家测试环境 → 点餐/厨房/打印/离线验收 → exact release 候选 → 人工批准该商家 Production 发布。

Codex 是外部工程助手；菜单中的文字是资料，不是执行指令。PDF 不上传至 POS，不建 OCR/LLM 菜单导入 API、聊天、运行时 Agent 或 AI 服务计费。工具只接收已确认结构化配置，不生成每餐厅独立 Spring/React 业务代码。必要经营问题只问菜单未表达的必选/数量/规格/税含价/厨房/语言等，不再问已确认二十项产品选择。

## Versioned Merchant Configuration Manifest

P3-CONFIG-01 只落基础 contract/reader；P4/5 固定菜单和厨房语义后 P6 实现完整 schema/validator/executor。以下为未来字段责任，未验证的 API 名称不在此发明。

| 区域 | 配置/约束 |
| --- | --- |
| Envelope | schema_version、manifest_id/version、明确目标 deployment/Organization/Store code、期望 revision/fingerprint、配置 hash、来源/确认记录；ID/code稳定且版本可追踪 |
| Merchant/Store | Org/Store元数据、brand、locale、CAD/currency、timezone/营业日、taxpolicy引用、服务模式；security/environment字段只引用受控外置来源 |
| Profile/Modules | 精确授权profile_code/version/fingerprint、Store模块意图与平台ceiling；Neutral是默认空白模板，兰州私有数据不自动可见 |
| Menu | category/item/variant、双语名称、规格/价格、optiongroup/minmax/default/quantity、Add-on/Remove、ComboGroup/Component、station/eligibility/BOM引用；只使用已实现schema版本 |
| Canonical Add-on | Org不可变code、Store名称/价格/active、itemeligibility；导入不为每菜创造重复price/name来源 |
| Kitchen/Printing/Table | station/outputrole/service-mode/table模式、有限receiptpreset/displayrevision/alias/sort；真实printer endpoint、device registration、tokens不进入共享manifest |
| Evidence | PDF来源页与人工确认、validator/dryrun摘要、合成测试结果、候选release/API/schema/asset兼容；不含真实账号密码/客户订单/支付数据 |

不会仅凭相同中文/英文名称去重：稳定code和scope是身份。不同code同名（如fried_egg与combo_fried_egg）保留独立；相同code不同业务含义、价格/名称冲突、未解析reference一律失败并列差异。Org/Store数字surrogateID由现有materializer产生，不能沿用真实Store1等来源ID。

## 工具边界与事务

复用已有 Owner组织授权、Profile/Master版本、fingerprint、reservation/idempotency、Store-owned transactional materialization，先查真实controller/service/DTO/API。批量工具可调用现受控服务或明确新增管理接口，但不直接写经营SQL绕授权，不建第二套provisioning框架。

Validator检查schema/version、required/unknown字段、唯一code、min≤max、default满足选择规则、precision/currency、同Org/Store refs、feature依赖、private Profile使用权、不可共享Secrets/真实endpoint、现已支持业务范围。Dry Run零业务写入、无外部平台请求/打印/设备配对，输出 create/update/unchanged/conflict、依赖/风险、预期revision和变更hash；读取也必须有scope授权。

Execute要求目标+actor scope、manifest hash、expectedrevision/fingerprint、operation key同时匹配，重新验证避免dryrun到execute的TOCTOU。operation key按deployment/Org/Store/操作身份scope；同key同hash重试返回同结果，同key不同hash冲突。多写在原事务内，失败无半个Store/菜单/ledger；外部副作用不在事务中直接执行。凭据创建、真实设备binding、打印与production激活使用独立明确授权，不能混入菜单初始化。

成功只生成该Store独立可变经营状态；后续Master/Profile更新不能修改已有Store。更新现有菜单需显式diff、revision与兼容策略，不“重新导入”覆盖营业订单/员工/设备或重写订单/厨房/print snapshots。订单key、frozenpayload、历史金额不随manifest改变。

## 恢复与发布

执行失败：保留operation状态和去敏证据，按稳定key恢复/重试，不选择新key制造重复。提交后的配置缺陷：回切前一合法配置version或前向修复，生成新revision；旧printjob保持原payload，unknown物理结果不盲目重打。隔离测试DB可重建，实际经营DB不可用旧dump覆盖新订单。

每商家独立app/DB/secret/域名/backup/monitoring配置；release manifest记录backend/frontend/APK/assets/Flyway/config契约与证据。发布前有备份、兼容旧应用的迁移、回滚边界与恢复演练，真实Production发布由Owner对具体候选批准。本轮Production/Staging变更均不授权；此设计不执行客户创建或上线。

## 阶段分工与未来产物

| 任务 | 输出与边界 |
| --- | --- |
| P3-CONFIG-01 / P3-PROFILE-02 | 基础schema/read接口、私有profile、Neutral测试初始化；不含完整导入工具 |
| P4-MENU-01 / MODIFIER-02 / COMBO-03 / PRICE-04 | 完整可配置菜单/规则/价格及离线旧价政策；具体API经审查后存在 |
| P5-KITCHEN-01 / PRINT-02 / TABLE-03 | 工位/输出/模板/桌台语义和回归，可售配置空间明确 |
| P6-IMPORT-01 | 版本化schema、引用/重复验证、diff/dryrun/幂等execute与故障恢复 |
| P6-PLAYBOOK-02 | `MERCHANT_ONBOARDING_PLAYBOOK.md`、`NEW_MERCHANT_CODEX_PROMPT.md`及两份合成菜单演练 |
| P7-ANDROID-01 / DEPLOY-02 | 通用签名App、有限硬件认证、逐商家环境/backup/restore/发布runbook |
| P8-PILOT-01 | 真实目标商家的税/语言/营业日/设备/支持责任验收；法语要求单独识别并关闭，不能假设中英已够 |

验收至少包括：两不同Org同code不串数据；导入重试无重复；无效ref/越权/不兼容schema拒绝；dryrun后配置变更导致expectedrevision冲突；中途失败全回滚；Neutral不带兰州菜单、缩写、人员、桌台或endpoint；现店Profile仍保原行为；历史订单/job不变；最终只交付配置和独立运行环境。

尚未知：真实菜单具体规则、税含价/有效期、旧价期限、语言义务、品牌素材授权、硬件型号、APK实装、客户RPO/RTO与支持预算。以对应任务采集事实和商家确认，不凭PDF价格文本推断税务或厨房工艺。

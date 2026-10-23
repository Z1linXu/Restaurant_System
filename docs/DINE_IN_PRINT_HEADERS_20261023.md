# 堂食打印桌号头部修复

PRIMARY_REPAIR_GOAL: Production 所有门店的 GRAB、FRONTDESK_RECEIPT、HOT_KITCHEN
及其 UPDATE 小票统一使用左对齐的完整桌号首行。
EXPECTED_REPAIR_CLASS: MEDIUM

Owner 于 2026-10-23 明确授权实现、测试并直接部署 Production，免除再次询问。
本批次只改变新生成堂食小票的头部；外卖、菜品、金额、路由、设备配置、订单逻辑
和既有冻结 PrintJob 均不在变更范围。Production Uber 连续处理配置必须原样保留。

## 变更与验证边界

- 普通首行：`1`；UPDATE 首行：`1 (UPDATE)`，保留 `1里`、`1-左`、`1-右`。
- 保持原有 LARGE 字号标记；首行无前缀、空格缩进或独立 UPDATED 标题。
- 三个 renderer 使用同一桌号头部规则；MOCK 与 PAD_DIRECT 使用同一冻结渲染文本。
- 先做 renderer 和 ESC/POS payload 回归，再经独立 Agent 6 审查、PR、merge。
- Staging 验收后将同一不可变 backend image 提升至 Production；frontend 保留。
- 无 schema 变更。部署前保存可读取的数据库备份及原应用 model/image；失败仅回滚应用。
- 使用合成内存输入验证部署 JAR，不创建真实订单、PrintJob，不触发实体出纸。

## 执行证据

基线：`52fa04f83d567e4f7e3ce852fd3a68c3577bf7f9`。
实际测试、审查、发布 SHA、环境验收和磁盘结果在执行后记录。

SCOPE_EXPANSION_CHECK：产品修改限三个 renderer、现有桌号 formatter 及两处 ESC/POS
编码器的首段 LARGE 后缀支持。
现有一次性部署工具均绑定旧 SHA、双应用构建或 Uber worker=false；不能直接用于
当前 worker=true 的 Production。`deployment/cloud/dine-in-headers/deploy.py`
因此属于 BLOCKING 的批次适配：复用现有锁、建模、备份、历史 hash、原子写入与
有界构建函数，仅更换 backend image。它不引入新发布生命周期或环境配置。
`RenderHeaderAcceptance.java` 沿用已有 render-only JAR 验收方式，证明目标镜像
中的实际类与 PAD payload，无需创建营业订单或实体打印。
同目录 `verify.py` 只是将该步骤及已有配置/历史 hash 复核固定为本批次命令，
以实际 PASS 结果写 Stage 提升凭证，避免手工填写验收结果。

只读预检：Stage/Production 后端均为 `56185edcdd9f4496cc6073e478546a5a39ba77d9`，
镜像 `sha256:264babe4f4bed353b310f5989a8b93623dff69ebeee9cd2e0dda81b829238d97`；
frontend 为 `4b3abd1349e66a146c36ac310b6a8b48bb2ad572`。两环境 health UP、V38，
与本次 fresh main 的 backend/frontend 可执行基线一致；Production Uber worker=true，
Staging=false。未清理历史 active 打印任务或更改设备配置。

Agent 6 首轮发现一个 P2：把 `1-右 (UPDATE)` 整行四倍放大会占 52 个基准列，
超过常见 80mm 的 42/48 列。因此桌号保持原 LARGE，后缀以一倍宽紧接在同一行；
只增加首段 LARGE span 后缀处理，旧整行标记保持字节一致。Production 8 个 printer
均配置 80mm；这不是现场纸面观察。字号含义依据
[Epson GS !](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_exclamation.html)，
左侧初始化依据 [Epson ESC @](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/esc_atsign.html)。

验证：全部 printing 测试 377 项，375 PASS、0 failure/error、2 个可选 PostgreSQL 条件
跳过；新增 60 个 renderer 参数用例及 29 个 PAD/TCP/宽度/旧格式兼容用例。部署隔离
保护测试 2 PASS，本地 render/payload 验收 24 PASS。Agent 6 增量复审 ACCEPT，
无剩余 P0/P1/P2。宽度问题修复后复测一轮，无其他产品返工。

Staging 应用在 `2026-10-23T14:00:56Z` 部署成功。首轮隔离 JAR 验收未能加载
测试 class：cap-drop ALL 的 root 无权读取 operator 的私有 0700/0600 文件。
BLOCKING 工具修正只指定同一 operator UID/GID，保留全部隔离限制；允许同一
未验收 proof 目录安全重跑，并以 backend/frontend Git tree 相同证明复用应用镜像。
这是验收工具权限问题，未写 acceptance PASS、未改业务数据、未部署 Production。
应用 source SHA 仍为 `0d2e244c3d1565a06bda8fa269e784af58e875e2`，不重建。

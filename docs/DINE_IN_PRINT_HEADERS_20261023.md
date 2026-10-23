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

TOOLING_REASSESSMENT：第二次验收在 clean-release 检查前停止，原因是 Python
import loader 在 deploy 模块设置禁用缓存前生成 `.pyc`。采用标准 `python3 -B`
启动并仅删除两份已核实属主、magic/源码时间/大小匹配的本轮生成缓存，保留洁净
校验和全部隔离限制。不再扩展工具，不重建应用。Staging 第三次验收在
`2026-10-23T14:05:51Z` 真实 PASS（24/24），应用仍为同一 `0d2e244` / `697947b3…`
镜像；脚本为 `eaec42732425dd526b4fc3056140c12369ebe4b1`。Agent 6 两次工具增量
审查 ACCEPT。总计两次验收失败，均在无业务写入/实体打印的证明工具中，原因完整保留。

## 发布结果

应用 Commit：`0d2e244c3d1565a06bda8fa269e784af58e875e2`（PR #294）。验收工具修正
为 `eaec42732425dd526b4fc3056140c12369ebe4b1`（PR #295），两者 backend/frontend
tree 完全相同。Stage 于 14:00:56 UTC、Production 于 14:07:01 UTC 部署完成。
Production 于 14:07:18 UTC 完成真实 JAR 24/24 验收。两站共用已验收 backend
`sha256:697947b35e496b94a043b925eb13c4a62470f15d47d02c02bdb9fdd3b81a0e4a`，
JAR SHA256 `82cf2939259af3c68c3fb7660bf3fb95dcd89e75fb86e5d61681812a735f9bdd`。
frontend 保留 `4b3abd1`。

| 模块 | 普通首行 | UPDATE 首行 | 实际 JAR / PAD bytes |
| --- | --- | --- | --- |
| GRAB / GRAB_UPDATE | 完整桌号 | 完整桌号 + ` (UPDATE)` | PASS |
| FRONTDESK_RECEIPT / FRONTDESK_RECEIPT_UPDATE | 完整桌号 | 完整桌号 + ` (UPDATE)` | PASS |
| HOT_KITCHEN / HOT_KITCHEN_UPDATE | 完整桌号 | 完整桌号 + ` (UPDATE)` | PASS |

每个模块各测 `1`、`1里`、`1-左`、`1-右`，分别覆盖普通和 UPDATE。首行无缩进、
无单独 UPDATED，表号 LARGE 指令保留，后缀恢复一倍宽。MOCK snapshot/PAD/TCP
字节与原 whole-line 格式兼容由 375 个通过的 printing 测试覆盖，另有 2 个未配置
独立 PostgreSQL 的条件测试跳过。没有触发营业订单或真实实体出纸，不把自动字节
证明写成现场纸面验收。已有冻结 PrintJob、菜单、金额、规则、设备配置及 V38
ledger hash 均通过原值保护。Production Uber worker=true、所有 cutover 保留。

两环境分别保存 fresh 可读 custom-format dump、rollback model 及原镜像，均在
各自 `/srv/restaurant-pos/<environment>/dine-in-headers-<application-sha>/` 私有目录。
备份 SHA、部署结果及逐模块证明见
[Stage 部署](evidence/dine-in-headers-20261023/staging-deployment.json)、
[Stage 验收](evidence/dine-in-headers-20261023/staging-acceptance.json)、
[Production 部署](evidence/dine-in-headers-20261023/production-deployment.json)、
[Production 验收](evidence/dine-in-headers-20261023/production-acceptance.json)。

独立发布后核查 PASS：环境变量、V38 ledger、旧订单/选项/厨房名称/打印 payload
五类 cutoff hash、旧 PRINTING 全行及菜单/设备配置均保留；两个 backend 按授权
更新，frontend/DB 容器 ID、启动时间及重启次数不变。Stage 部署基线中的 Production
backend 随后因授权提升而改变，是预期动作；Production 自己的 protected set 全部不变。
详见 [独立核查](evidence/dine-in-headers-20261023/postrelease.json)。

## Docker / Disk Hygiene

磁盘 74% → 75%，可用空间约 15GiB → 15GiB；default Build Cache 保持 16.82GB，
其中 Docker 汇总 reclaimable 10.45GB。291 个超过七天的记录已审查，无符合当前
immutable、unshared、zero-use 安全规则的记录；不执行清理，回收 0。临时 builder
已移除。保护当前两环境 app、原 backend 回滚镜像、frontend、DB 容器/卷、private
model 和备份。Stage/Prod 均 UP；Production 仅授权 backend 镜像更新。磁盘达到
75% 警戒线，保留为既有运维观察项，不扩大本次修复为缓存/部署架构项目。

## 产品修复与必要工具

PRODUCT REPAIR（约 10 分钟、与审查并行）：三个 `*ReceiptRenderer.java`、
`PrintTableDisplayFormatter.java` 统一桌号；`PrintJobServiceImpl.java` 和
`EscPosTcpPrinterTransport.java` 各增加首段 LARGE 后缀处理，保证完整桌号与
UPDATE 同行，均为用户格式要求的必要修改。7 份打印测试文件提供针对性回归。

TOOLING / GOVERNANCE REPAIR（约 8 分钟、与发布交错）：本批次 `deploy.py` 复用
已有锁、模型、备份、历史保护及有界构建；`verify.py` / `RenderHeaderAcceptance.java`
提供无业务写入的实际镜像证明。现有一次性工具有过期 worker=false 限制，以上适配
是本次安全发布的阻断项。两次验收工具失败已闭环，无应用重建。技术行为更新在
`SYSTEM_DOCUMENTATION.md` 和打印规则文档，运行状态更新在 `CURRENT_STATE.yml`；
本文件及 JSON 仅保存证据，不开放下一阶段或未来 Production 授权。

最终 Stop：`DINE_IN_HEADERS_PRODUCTION_COMPLETE_NO_REAL_TEST_ORDER_OR_PHYSICAL_PRINT`。

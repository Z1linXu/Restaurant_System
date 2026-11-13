# Allô GRAB排序与备注 — 2026-11-13

PRIMARY_REPAIR_GOAL：交付本轮三项需求，保持订单金额、历史小票、离线身份与门店隔离。
EXPECTED_REPAIR_CLASS：HIGH（现有订单、持久化/幂等、冻结打印上下文及UI的必要连接）。

Fresh origin/main：80e888fcdad74e98326fcbeaa5df14bc0d9baed6；当前 clean Allô基线
49dbfba2a53cbeb181728938b1ad89472ac497e7，应用源码23f3e804；2个main-only提交均为文档。
独立分支codex/allo-grab-notes-20261113，复用干净当前Allô工作区，不碰dirty主工作区。
已只读确认43.166.242.250实际23f3镜像对、V42、Store1/Org1/MOCK，12订单含1preparing。

Product Simplicity Check：商品卡改为只读备注摘要；C类复用B类同一备注区；整单一个输入，
复用Submit/Update按钮，不增加确认步骤、不暴露revision/幂等内部状态，不新建配置流程。

整单备注独立于third-party external_order_note_snapshot，新增nullable orders.order_notes。
旧客户端缺失字段保留当前值；旧草稿/Outbox不清理、不换namespace/schema identity。
提交后用现有Update入口、expected_revision和稳定保存的idempotency key确认变更。
仅备注更新只生成明确备注更新GRAB，不生成零金额前台收据；新增菜品仍只打印新增范围。
新GRAB冻结当前整单备注，位于菜品后一次，更新/清除有明确标签；空初单不打印此区域。
旧冻结上下文没有notes-captured标志时不读取新当前备注；原始重印保持原字节。

复用已有Compose、backup/recovery、QA runtime与原有发布路径；不建设第二套部署机制。
全部测试与Agent6及新鲜恢复备份完成后按Owner本轮明确授权部署，不另行逐项审批。

产品代码验证：Maven1529项、failures0/errors0、环境跳过117；前端85文件/503项PASS，tsc PASS。
Agent6两轮发现的四项阻断已合并关闭，最终产品复审PASS：仅备注不重算历史金额；
既有LocalDraftRecord冻结完整update请求、刷新先确认；使用API update_batch_id跟踪备注票；
延迟确认按context/client身份隔离。发布封装、QA和Production尚待完成。

只读发布假设已于2026-11-13T07:25:54Z验证：Allô routing.enabled=false，V42，
1笔active订单、0pendingJobs/Outbox、0devices/endpoints，三容器healthy。
Disk15%/49GB可用；BuildCache1.162GB，不执行清理。

TOOLING / GOVERNANCE：BLOCKING仅为Compose Flyway target42→43与复用既有私有发布/
QA脚本的精确批次参数；不新增部署、队列或凭据生命周期。
全量governance checker先遇ignored node_modules/jiti README链接，隔离tracked副本又遇
历史TEST_COVERAGE_REPORT引用ignored .template-test-private/LOCAL_BROWSER_MOCK.png。
列为NON_BLOCKING_EXISTING_DEBT；本轮authority字段与变更链接独立检查，不扩大治理工具。

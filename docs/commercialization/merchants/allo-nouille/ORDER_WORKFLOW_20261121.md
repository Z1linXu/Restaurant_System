# Allô 订单操作与 APK 发布 · 2026-11-21

PRIMARY_REPAIR_GOAL 为 Owner 确认的九项 Allô 订单、报表、打印与 APK 更新改动。EXPECTED_REPAIR_CLASS 为 HIGH。

基线为当前独立 Allô 生产后端 `8b4f5bc61ed182d200e1755f5397f5277152b38e`、Web `079b1759f8bfd7be9177c6b6b7b134d16e090167`、V44、APK 1.1.0。仓库独立工作区原始 HEAD `6bf2546e3fc76692973096622aa138e765832ce5`，fresh `origin/main=80e888fcdad74e98326fcbeaa5df14bc0d9baed6`，分歧 main-only 2 / Allô-only 44，初始工作区干净。

Owner 2026-11-21 批准测试通过后发布 Allô 和新版下载链接。目标为 `43.166.242.250` 的 `allo-nouilles`、Store1/org1。需要 exact-SHA、Agent 6、备份恢复、受控发布与验收。隔离验证沿用本机合成环境。未授权其他门店与共享 Test/Staging 的运行变更。

Coupon 使用现有单个税前优惠规则，允许固定金额或百分比、应用、移除和管理共享预设。新增订单或加单由 GRAB 与 HOT KITCHEN 各自输出相同票面。两路沿用自己的 printer assignment。现有 HOT 角色使用已配置但停用的 assignment 3，printer 6 与 GRAB printer 4 分离。仅在新代码和配置身份核对后启用原 assignment。

APK 1.2.0/code6 使用同一应用 ID、签名及现有 Pad 协议。Web 发布制品增加 `/download/` 和版本化 APK 文件。下载页与安装包作为同一份 Web 制品上线。

## 交付与验收

后端、Web、APK 资产均来自 `64421d9fdfc5b26257edc0e83e1fade1cd6f38c5`。Allô 上线时间为多伦多 `2026-11-21 19:04:24`，发布后后端、网页和 DB 健康。Flyway 45 成功，DB 原容器和挂载保持。通过管理 API 启用 HOT assignment 3，原目的 printer6。GRAB 仍用 printer4。更新范围仅 Allô。旧共享 Test/Staging 保持停用。无本次发布生成的生产测试单或实体测试票。

| 需求 | 验收结果 |
| --- | --- |
| 20% 快捷折扣 | 浏览器显示 10% / 20% / 50%，金额走原规则 |
| Coupon | 正式前台角色在隔离运行可新增、应用、移除、停用。固定金额和百分比，复用单一税前折扣 |
| 报表简化 | 实际预览和打印任务去除报告名称/ID、时区和 Generated。明细去除内部订单号和小数秒 |
| 勾选与加减数量 | 用浏览器勾选一份虾仁，按加减并预览，份数受剩余量限制 |
| 独立 Add-on 作废 | 两份鸡排饭仅删一份虾仁，含税 59.74 → 50.55，当前重印仅一份虾，原票和事件保持 |
| 作废后保留详情 | 主菜、加料原名/数量保留。部分标记 `已删除 ×1`，整单标记全部 |
| 查询 30 天订单 | 原营业日期和 50 条分页，浏览器查看详情及 Coupon/虾仁记录，跨店 403、匿名 401、窗口外 400 |
| HOT KITCHEN 同 GRAB | 初单、加菜、只改整单备注文本相同。独立任务完成模拟 Pad 租约/冻结负载，两路在同格式下字节哈希相同 |
| APK HTTPS 下载 | [打开下载页](https://pos.allonouilles.com/download/) · [下载 1.2.0 APK](https://pos.allonouilles.com/download/allo-nouilles-pos-v1.2.0.apk)。实际 HTTP 响应与发布制品逐字节相同 |

后端聚焦 263、前端 522、TypeScript、Android 原生 18、实际隔离浏览器/API、模拟器覆盖安装均 PASS。Agent 6 独立复核无 P0–P2。实体 Pad 和出纸待现场验收，PAD 协议验收为零实体连接。安装包沿用原证书、applicationId 和 IndexedDB。旧 1.1.0 能继续点单和打印，新操作界面需 1.2.0。

## PRODUCT REPAIR

`backend/.../allo/`、Order/Analytics/Print 路径与 V45 实现新行为。`frontend/src/features/allo/`、Order 页与点单选项展示复用同一有效数量。变更保持历史原价、稳定重试身份和实际销售的订单级舍入。Android 原生实现无需改变。

## TOOLING / GOVERNANCE REPAIR

新增小下载页 `deployment/allo/download.html` 和隔离验收脚本 `scripts/allo-qa/order_workflow.py`，补齐本轮必需的发布/验收。复用已有 Allô 镜像、Compose 发布方式和恢复脚本。两次隔离脚本断言修正分别按语义比较 JSON、序列化固定金额。一次线上验证断言修正同域 CORS 无需允许来源头。均未改应用制品。前端基础和已签名 APK 共享 54 个 Web 文件；Web 发布额外包含下载入口。

识别的既有工具债务依然是全库扫描依赖 README 缺少链接。只同步已变更文档。生产磁盘从 19% / 46 GB 可用、Build Cache 2.066 GB 到 20% / 46 GB 可用、Build Cache 2.194 GB。未清理缓存或历史制品。

分支 `codex/allo-order-workflow-20261121`，基线 `codex/allo-sales-printing-20261115`。[PR 317](https://github.com/Z1linXu/Restaurant_System/pull/317) 留在独立 Allô 交付线。详见[不可变发布批次](ORDER_WORKFLOW_RELEASE_20261121.md)。

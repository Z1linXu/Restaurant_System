# Allô 订单操作与 APK 发布 · 2026-11-21

PRIMARY_REPAIR_GOAL 为 Owner 确认的九项 Allô 订单、报表、打印与 APK 更新改动。EXPECTED_REPAIR_CLASS 为 HIGH。

基线为当前独立 Allô 生产后端 `8b4f5bc61ed182d200e1755f5397f5277152b38e`、Web `079b1759f8bfd7be9177c6b6b7b134d16e090167`、V44、APK 1.1.0。仓库独立工作区原始 HEAD `6bf2546e3fc76692973096622aa138e765832ce5`，fresh `origin/main=80e888fcdad74e98326fcbeaa5df14bc0d9baed6`，分歧 main-only 2 / Allô-only 44，初始工作区干净。

Owner 2026-11-21 批准测试通过后发布 Allô 和新版下载链接。目标为 `43.166.242.250` 的 `allo-nouilles`、Store1/org1。需要 exact-SHA、Agent 6、备份恢复、受控发布与验收。隔离验证沿用本机合成环境。未授权其他门店与共享 Test/Staging 的运行变更。

Coupon 使用现有单个税前优惠规则，允许固定金额或百分比、应用、移除和管理共享预设。新增订单或加单由 GRAB 与 HOT KITCHEN 各自输出相同票面。两路沿用自己的 printer assignment。现有 HOT 角色使用已配置但停用的 assignment 3，printer 6 与 GRAB printer 4 分离。仅在新代码和配置身份核对后启用原 assignment。

APK 1.2.0/code6 使用同一应用 ID、签名及现有 Pad 协议。Web 发布制品增加 `/download/` 和版本化 APK 文件。下载页与安装包作为同一份 Web 制品上线。

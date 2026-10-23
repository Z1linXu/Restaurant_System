# 堂食小票额外顶部进纸

PRIMARY_REPAIR_GOAL: 所有门店六种堂食小票，在原空白基础上、桌号之前额外进纸至少15mm，
每张只应用一次，同时保持桌号及其他打印行为。
EXPECTED_REPAIR_CLASS: MEDIUM

Owner 于 2026-10-23 明确授权实现、测试、审查、合并并直接部署 Production，无需重复确认。
基线为 fresh main `06b8e57a627fc22dd23bd8e35232b93ceea67e7c`；开始记录时间
`2026-10-23T15:09:27Z`。原脏工作区受到保护，使用独立修复分支。

产品文件：`PrintMarkup` / `PrintTableDisplayFormatter`、两处编码器及共享
`EscPosPaperFeed`；MOCK 预览的 `ReceiptPreview` / `PrintingSettingsPage`；对应回归测试。
既有快照不原地改写，历史补打若增加布局也只作用于新的补打任务。
订单服务、菜单、金额、路由、printer 配置、Android APK 和 schema 不变。

## ESC/POS 实现

堂食渲染保存一次 `[[TOP_FEED_16MM]]` 布局标记。MOCK 显示额外16mm空白，保留现有
预览 padding 和原文本空行。PAD_DIRECT 与 backend TCP 使用同一个命令编码函数：
在 ESC @ 初始化之后、正文之前输出 `GS P 0 254`、`ESC J 160`、`GS P 0 0`。
垂直单位为25.4/254=0.1mm，目标进纸16mm，留下超过15mm的机械步进余量；之后恢复
默认运动单位，正文行距和末尾裁纸保持原值。不在进纸后再次初始化，也不使用反向进纸。
连续重复的前缀只编码一次，标记本身不产生额外 LF。

依据 [Epson GS P](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_cp.html)
和 [Epson ESC J](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/esc_cj.html)。
目标环境实际出纸长度仍需现场尺量；没有进行实体打印时不得把命令证明写成纸面实测。

## 发布边界

复用上一批已审部署/验收工具，更新本批次 image baseline、release 目录和实际 JAR 验收。
SCOPE_EXPANSION_CHECK：MOCK 的16mm预览是用户明确要求，因此本次须构建并提升 backend
和 frontend；复用已有双镜像有界 builder，不建立第二套部署生命周期。
无 migration；保留 Production Uber worker=true、cutover、全部环境配置、旧打印任务及历史
cutoff hash。先 Staging 验收，再提升同一镜像对；发布前 fresh 可读 dump 和原模型回滚。

## 发布前验证

打印测试397项：395 PASS、2项PostgreSQL条件测试跳过、0失败；前端328项测试及构建
PASS；受影响3个前端文件eslint PASS。全量eslint存在6项既有错误，已在同一工具链的
干净基线复现，相关文件均未修改，归类NON_BLOCKING_EXISTING_DEBT。
首次打印回归的3项旧纯文本断言需剥离新增非打印标记，修正测试助手后全套通过。
发布工具2项测试、Python语法、clean tracked export治理校验及diff检查PASS。
本地实际编码器验收24种堂食头部及6种外卖：16mm、一次进纸、完整PAD/TCP字节一致PASS。

Agent 6已合并审查全部产品、测试和发布工具，ACCEPT，无剩余P0/P1/P2；确认新补打仅修改
新任务、原快照保留、双镜像部署和原模型回滚边界。实体进纸长度NOT TESTED。
测试/基线/只读preflight证据见`docs/evidence/dine-in-top-feed-20261023/`。
实际部署SHA、各模块结果和磁盘信息将在执行完成后记录。

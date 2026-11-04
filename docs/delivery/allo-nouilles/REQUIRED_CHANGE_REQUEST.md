# Allô Nouilles — 单店必要打印变更申请

状态：OWNER_APPROVED_FOR_EXACT_SINGLE_RESTAURANT_TEST_SCOPE（2026-11-03，本会话明确回复“批准这份单店最小打印 CR”）。来源：2026-11-03 FIRST_RESTAURANT_DELIVERY_FINAL_PROMPT §5–6；不因本文件存在获得修改成熟打印核心授权。客户菜单来源2ebcf2ef...57db8；未确认规则不启用。本申请合并本店需要的CR-RECEIPT-CUSTOM-001与CR-PRINT-I18N-001，以及真实BACK_KITCHEN的最小GRAB入口。不建设新routing引擎。

## 业务实例与配置不够的原因

1. 沿同店2026-10-18已提交决策，非饮料统一真实BACK_KITCHEN、每Submit/Update一张后厨汇总；新菜单无多厨房目的地要求。OrderService1626–1693已经能正确创建该Station任务，但GrabReceiptRenderer26/75硬白名单将其丢弃。显示名/assignment不能改变资格；不能伪装NOODLE。
2. 新PDF C6固定19.99选四种蛋白中的两种，以及B类零价面型；是同一主食的制作选项，不需要旧套餐子项/新Combo引擎。现Template CUSTOM沿框架映射addon并保留真实CUSTOM group，厨房有真实label，但FrontdeskReceiptRenderer378–389只走正价普通modifier，零价必要信息被遗漏。不能假加价、改Spicy或塞notes。
3. 中文/English/Français App与厨房/前台独立打印语言为仍有效要求。当前GRAB/FRONTDESK固定中文优先与英文标签，单改UI/DisplayRules无法完整三语。
4. PrintDispatcher.reprintJob836–899优先source text但PAD bytes按当前设备重编；reprintOrder903–969会重render。新增语言不能让旧Job按新配置重译；当前行为不等于exact frozen bytes保证。

## 请求批准的精确范围

所有变动仅显式Store opt-in的新客户配置生效；缺省LEGACY路径、三旧店配置/运行/已应用V1–V40不改。候选文件均位于 backend/src/main/java/com/restaurant/system/。

| 文件 / 方法 | 最小候选修改 |
|---|---|
| printing/dto/PrintRenderRequest.java | optional receipt context（真实允许Station集合、effective locale、冻结名称/标签及版本）；不修改订单金额/任务/身份 |
| printing/renderer/GrabReceiptRenderer.java render/filter:36–80、普通BACK_KITCHEN行显示 | 仅opt-in把明确BACK_KITCHEN加入允许集；同原遍历排序/一张GRAB；普通真实订单行/选项从冻结展示快照取指定语言；不把所有未知station放行 |
| printing/renderer/FrontdeskReceiptRenderer.java render:47–139、modifier选择378–389、名称/固定标签helper | opt-in输出选中真实CUSTOM零价label/数量，以及本店普通免费选项；保留收费/金额/税/每份分摊/Update；指定语言只改展示，不改type/code/价，不重复特殊语义行 |
| printing/service/impl/OrderDispatchOutboxServiceImpl.java enqueue:26–51 | 在原订单事务冻结本事件Store打印语言/labels/context至既有routing_context；继续原source key，不新队列 |
| printing/service/impl/OrderDispatchOutboxProcessor.java process:95–124 | legacy module分支传冻结context的最小兼容overload；现retry/state/outcome不改 |
| printing/service/impl/PrintDispatcherServiceImpl.java buildRenderRequest:1131–1176、dispatchPersistedEvent、reprintJob:836–899、reprintOrder:903–969 | 接收冻结context、消费已保存三语快照并attach原Job；opt-in原件重印只用明确source Job artifact，不读live译文/active language；缺artifact明确保守结果，不静默新语言；原确认/手工key/分组/Update过滤不改 |
| printing/service/impl/PrintJobServiceImpl.java attachRenderedContent:143–167、markPadDirectQueued:178–207/payload编码408–484的局部接缝 | 可选冻结render context与原件artifact复制入口，沿原合法pending/attempt准备流程，避免重新编码source bytes；状态枚举、转换规则、去重算法/source key、lease/token不变。严格字符可编码性仅新opt-in文本，失配诊断不能伪报PRINTED |
| 新独立纯 ReceiptPresentation/ReceiptLanguage helper | 决议zh-CN/en/fr-CA、固定词汇/实际snapshot名称、明确fallback；不对成品字符串机器翻译、不重算钱、不创建第二Renderer体系 |

不请求改HOT_KITCHEN Renderer：本店只开GRAB+FRONTDESK，HOT不启用以免重复。若后续实际业务需要新增目的地/协议，另批，不扩大本申请。

伪Diff（尚未实施）：
```diff
 PrintRenderRequest
+ optional frozen_receipt_context
 GRAB task inclusion
- legacyAllowlist.contains(task.station_code)
+ legacyAllowlist.contains(code) || explicitStoreContext.allowedKitchenStations.contains(code)
 Frontdesk modifier view
+ opt-in: include actual selected free CUSTOM details once; names from frozen language data
 Outbox existing enqueue
+ freeze receipt context in same transaction; existing key unchanged
 Dispatcher render / manual original-copy branch
+ use event/source artifact context, never current locale to reinterpret history
 Job payload preparation
+ copy validated original artifact for explicit original-copy; same state/dedupe/protocol guards
```

## 保护项、测试和回退

- GRAB/Frontdesk只对新Store有显式配置；全部legacy text/bytes golden、旧价/税/快照、Order/Outbox/Job/Pad回归保持原断言。HOT/Pad/Android/native/socket/状态机/去重实现不扩张。
- 本店实测：32菜品对账；B三面型与收费数量、C6缺1/选2/选3/重复选不合法、DRINK无KitchenTask但前台完整；初单GRAB+前台，Update只新batch；原幂等key重放不新增；后台失败不把订单说成未保存；UNCERTAIN不自动重发。
- 三语同订单显示正确名称/份量/价格及零价制作信息；UI locale独立于Store打印locale；更改语言后旧event/Job text/bytes hash不变，新batch用新配置。
- 合成MOCK及严格encoder/markup/base64检查；不发送真实字节。真实机型/codepage/纸宽/出纸仍NOT_RUN，后续独立授权。
- 缺text/bytes/历史revision、跨设备编码不兼容明确人工核对，不能静默转码/自动重印；不承诺给三旧店历史任务自动改行为。
- 候选review后绑定精确SHA，独立Agent6；只在隔离Test迁移/部署。回退以保留原模板Test容器/数据库和前一候选包实现入口恢复，不rollback数据库或删除新记录；共享入口另行精确审批。

## Owner决策

请明确批准或拒绝上表保护文件/方法的这个单店最小切片。批准仅用于Allô新Store/Test、MOCK验证，不批准三家旧店发布、实体打印、Android、真实Uber/支付或正式上线。尚未批准时，仅暂停上述受保护修改，菜单/三语UI/普通元数据/导入与测试继续。

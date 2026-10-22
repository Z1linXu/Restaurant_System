# Store Remove Catalog / 门店减料

Menu Management → Add-ons → Removes 统一维护免费减料。Add-ons 与 Removes
是两个明确类型；本页不设置价格、不把收费加料转换成减料。

## 日常维护

1. `Add Remove` 创建稳定 code、中文名、可选英文名、启用状态及顺序。
   code 创建后不可修改；新减料默认没有适用商品。
2. `Items / 商品` 勾选适用分类。分类内现有及以后新增商品继承该选择。
3. 单品可选 `INHERIT`、`ALLOW`、`DENY`；单品设置优先于分类。
   catalog 停用时不可新选，但已配置的适用范围仍保留。
4. 改名称/启用/顺序会更新本店关联商品并推进 menu revision；客户端正常
   刷新菜单，无需清空草稿。历史订单和已冻结打印内容保持原值。

默认折叠的 Legacy diagnostics 汇总未关联记录数、冲突数、受影响商品及同 Code/名称记录；
普通维护列表只展示共享 Master。不能按中文相似、多数票、最新值或其他门店的值自动选择名称。

## 数据和接口

V38 新增 `store_removes`、`store_remove_categories`、`store_remove_item_rules`，
以及 option 的同店 composite FK。基础 code 只在当前 Store 唯一；catalog
拥有名称/启用/顺序，linked option 是免费 `REMOVE` assignment，不能再链接
Add-on。适用性读路径按当前 catalog 和分类/单品规则解析。

现有 `admin:menu_manage` 与 MENU_MANAGEMENT Store 授权覆盖全部接口：
`GET/POST /api/v1/admin/menu/removes`、`PUT /{id}`、`PUT /reorder`、
`GET/PUT /{id}/applicability`、`POST /reconcile`。没有删除或跨类型转换 API。
精确 DTO、完整替换语义与错误见 [API 合同](../../doc/API.md#store-remove-catalog)。

Migration 本身不回填旧选项。显式 reconciliation 的 dry-run 只读；apply
只关联同店稳定 code、双语名一致、价格为零、关系明确的组，保留 option ID
和原商品 eligibility。冲突返回报告并留待确认。Provisioning/clone 在目标
Store 原事务内建立独立关联，不复制来源 Store catalog IDs，不改 Master/Profile。

新单/Update/直接提交草稿会重新校验 Remove 的本店身份、免费价格、有效
assignment 和已选套餐小菜父项；缓存标签不能授权缺失/异店 Remove。
同一道菜的同一食材不能同时 Add-on 和 Remove；POS 新选择清除相反项，
后端拒绝冲突请求。主品和小菜的需求 scope 分开，显示名不参与食材匹配。

## 打印与验收边界

Menu Management 管理可点选的减料；Printing Display Rules 管理厨房文字。
未知但有效的稳定 code 使用冻结 option 名称 fallback，不要求再建打印定义
才能下单。完整重打使用订单快照；已有 job 补打复制冻结文本，不重写历史。

普通 catalog/适用范围编辑只影响当前 Store，不能发布给其他门店或共享模板。
迁移/单测是实现证据；是否部署、采用哪些 revision、现场纸张是否通过须单独
记录目标环境、SHA/schema 和实际验收。本文件不声明 Staging 部署或 Production
变更已完成，也不授权 Production、真实打印机/Pad 绑定或现场订单。

## 指定 18 项的受控整理

`POST /api/v1/admin/menu/removes/baseline-reconcile` 仅补齐 `cilantro`,
`green_onion`, `beef`, `radish`, `noodle`, `less_noodle`, `bok_choy`, `broccoli`,
`corn`, `mushroom`, `seaweed`, `carrot`, `cucumber`, `edamame`, `peanut`,
`cabbage`, `meat`, `green_pepper`。默认中文名/打印文字取 Owner 指定简称；
已存在 Master 的明确自定义名称不覆盖。旧 `remove_` 前缀 Master 复用，原 code 不改；
API `semantic_code` 提供共享语义。`beef` 与 `meat` 等不同语义不会合并。

先请求 `{store_id,dry_run:true}`；核对完整 before/after、decisions、conflicts 及
`menu_item_eligibility_changes=0` 后，以返回的 `before_fingerprint` 作为
`expected_before_fingerprint` 请求 apply。每 Store 独立事务、Store 锁及原子 audit log；
指纹过期或任何原选项/有效集合变化均失败回滚。发布操作另保留完整私密报告。重复执行
应先重新 dry-run 再 apply，结果为 0 create / 0 link / 0 eligibility change；
过期的原指纹不会被重新接受。

只关联同店明确语义、零价格、无特殊 Parent 且没有重复/名称歧义的旧行，仅写同店 FK
和对应商品原本的 ALLOW/DENY；不批量选 Category，不把 18 项分给所有商品。
保留原 option ID、raw code/name/type/group/sort、价格、启停及 Parent。缺少 Code、
胡萝卜片/黄瓜丝等歧义和特殊 Combo Side 保持 Legacy。常规刷新不再把 Master 名称、
Code 和顺序覆盖到旧行；Owner 显式改名称/顺序时才传播相应字段。

Modifier Remove 打印编辑按本店共享 `semantic_code` 聚合，显示 Code、共享名称、默认
文字、自定义 Print Alias。新增共享 Master 自动出现；合法多选继续支持。已发布内容
读取不自动改写，旧前缀在解析时兼容；缺别名继续安全 fallback。冲突保留并显示诊断，
必要整理仍须 Save Draft → Validate → Publish；没有内容变化时不创建 revision。
与相反 Add-on 的 ingredient 校验不作为共享 Master/打印语义的合并依据。

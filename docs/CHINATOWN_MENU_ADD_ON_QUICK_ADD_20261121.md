# Chinatown Add On 一键点单验收

2026-11-21，America/Toronto

| 项目 | 结果 |
| --- | --- |
| PR | [#316](https://github.com/Z1linXu/Restaurant_System/pull/316)，Draft，待合并 |
| 修改代码 SHA | `3cf28b5912b6254ed2425acb1765deae4edc7625` |
| 基线 `origin/main` | `80e888fcdad74e98326fcbeaa5df14bc0d9baed6` |
| Staging 前端 | `f714e5c1c1a47e226693ef7f4b900ae3e668567a`，`sha256:05d0ea02ac5a7fbd8cb265a25eb85f8dff713fc8f80f2dd8ab490514218eccdd` |
| Staging 后端 | `a7f408eea3add042026af4e1da8395d166cd5a61`，`sha256:0e07ae9b9a237869a4155149e323a62da44c80aba13c8a1643349f7b3b1c450f` |
| Staging Flyway 和菜单 | V41，Chinatown Store 18 revision 47，catalog hash `fnv1a32:b411a150` |
| 进入点单 | [Staging POS](https://staging-pos.lanzhounoodlesmtl.com/) |

## 产品修复

`OrderingPage` 的卡片主体和 `+` 共用 `requiresCustomization`。根据规格、辣度、汤底、面型、Combo、Add-on 和 Remove 元数据选择弹窗或直加草稿。已有默认值的可见选项继续在弹窗中显示。单一常规规格自动应用，卡片显示实际价格。数量控件沿用现有草稿操作，每次直加为 ×1，成功后短暂显示 Added。提交期间、售罄及停用状态会阻止添加，同商品的并发点击和紧随成功的 250 ms 双击被合并为一次操作。

## Staging 证据

- 功能分支 385 项前端测试和构建通过。最终 Staging 组合版 407 项前端测试和构建通过，其中点单交互测试使用通过缓存校验的实际 revision 47 目录。牛肉汤大份 $3、小份 $2、辣度及辣单放正常，其余面类按元数据继续弹窗。八项普通加选直加、数量递增和递减、快速重复点击、售罄保护、金额及 Added 状态通过。
- 后端回归 1,133 通过、117 跳过、0 失败。已有 IndexedDB、草稿、Outbox、Combo、Add-on、Remove 回归随前端套件执行。
- Agent 6 对最终 Staging 组合版及部署边界复审 ACCEPT，独立执行 78 项测试通过。
- Staging 测试单 #125 经真实 `idempotent-submit` 提交，使用相同请求重试返回同一订单。首单小计 $6.00，加点后小计 $8.00、含税 $9.20。订单已完成，保留 MOCK Print Job 307、306、309、308。原 #124 的六种 MOCK 小票快照与已验收证据一致。
- 公开页面与新 Nginx 镜像的入口文件一致。Staging 后端、数据库和 V41 账本指纹一致。其他 8 家 Staging 门店菜单指纹核对一致。
- 界面交互由组件集成测试验证，服务端使用真实 Staging API。实体 Pad 验收待现场完成。

## Production 发布门禁

计划窗口 2026-11-21 22:00 EST，对应 2026-11-22 03:00 UTC。发布在预检阶段阻断，PR 保持 Draft，定时任务未安装。

Production 后端 `74dfc1fd235af31fc7345c43f770d9991960da70` 包含仍在线运行的 Uber 金额处理修复，其 Flyway 账本已应用 `V39_1__add_uber_financial_corrections.sql`（1612790231）和 `V39_2__allow_uber_order_total_correction_v3.sql`（684147509）。Production 前端 `54d526e2ac6ebd69dc6b8829e28b80e8dd2925c1` 也包含单独发布的渠道销售展示。当前 main 与 PR 候选缺少这些已上线变更。候选无法满足保留线上功能和验证 Flyway 账本一致性的门禁。

已验收的 Staging 后端来自包含 Finish All 的集成版本，账本包含 `V40__order_finish_batches.sql` 和 V41。把这组镜像直接用于 Production 会顺带发布 Finish All，超过本批次范围。需先保留 Production 已上线的版本差异，形成兼容现有账本且排除 Finish All 的候选，再验收同一产物。测试和审查通过的记录只证明当次 Staging 组合版本。

Production 健康检查 `UP`，容器、镜像、配置指纹和 Flyway 账本前后一致。未到 Production 备份与应用切换门禁。

## 工具与治理

原恢复脚本用于从退役备份复建环境。本轮通过短脚本复用已验证的镜像构建、Compose、互斥锁和前端回滚方法，仅替换运行中的 Staging Nginx。该变更让验证后的前端可以部署到当前 Staging，同时保留后端、数据库、V40/V41 和既有测试数据。其余发布与定时器工具无扩展。

磁盘 57% → 58%，两次取整均剩 25 GB。共享 Build Cache 前后 8.407 GB，其中可回收 1.173 GB。未清理旧镜像、共享缓存或数据库卷，当前及回滚镜像保留。Staging 和 Production 健康检查通过。

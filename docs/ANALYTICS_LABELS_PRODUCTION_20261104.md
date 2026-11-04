# Analytics Known 文案移除：Production 验收

Owner 本轮授权：移除可见 Known/known 文案并直接发布；保持金额及 Pending 语义。
PRIMARY_REPAIR_GOAL: REMOVE_VISIBLE_KNOWN_COPY_ONLY
EXPECTED_REPAIR_CLASS: SMALL

## PRODUCT REPAIR

- 前端精确 SHA `54d526e2ac6ebd69dc6b8829e28b80e8dd2925c1`，分支 `codex/uber-analytics-pending-production`，PR [#307](https://github.com/Z1linXu/Restaurant_System/pull/307) 未合并。
- fresh origin/main `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`；本轮从已运行的74df财务修复分支继续，开始时工作树干净，main比较2 behind/8 ahead。未混入 main、Allô、CPU 优化或其他开发。
- 应用变更仅 `ChannelSalesPanels.tsx`、其现有测试和 `ItemSalesReportPage.tsx`。KPI、趋势、Revenue Mix、商品/门店报表共用金额不再带 Known；商品排序说明同步。内部 `known_revenue` API 字段、实际数字、NULL/Pending、Incomplete、AOV/百分比及订单/商品完整性判断不变。
- Backend 继续 `74dfc1fd235af31fc7345c43f770d9991960da70`；V39.2 完整 Flyway ledger 不变；六单 v3 与原 v1/v2 未写入/撤销。

## 验证

- 前端374/374 PASS；TypeScript/Vite build PASS；改动3文件eslint PASS。
- 全量eslint仍有6 errors/6 warnings；74df源码独立解包、同依赖复测，规范化路径后逐项相同，属于 NON_BLOCKING_EXISTING_DEBT，未关闭规则/改无关代码。
- Agent 6 产品及部署/回滚/权限修复簇 ACCEPT，无剩余P0/P1/P2；独立 UI7项、回滚/保护清单/proof绑定故障检查通过。审查未访问Production。
- 静态包 SHA256 `da167943d862d2fd1ed2104b8c79500ca394817eebd3412d3b67edb544b21d19`。
- 最终镜像全部58文件以 UID101、无网络、只读容器逐一SHA验证PASS；上线后58个HTTPS资源逐一匹配相同manifest。
- 2026-11-04T15:56:58.368886+00:00 最终独立审计：HTTPS `/` 200，`/api/v1/system/health` 200/UP，PostgreSQL healthy；三个Production容器restartcount均0。
- 三店2026-11-03完整Analytics响应与发布前逐值相等；Saint-Denis107/POS61/Uber46、POS2707.04、Uber1718.47、Total4425.51、AOV41.36、订单金额Pending0、商品待核对6单18行保持不变。
- 订单、order_items、PrintJobs、Outbox、KitchenTasks、Uber事件/原财务指纹及历史打印保护记录与发布前相同。未发测试订单/真实打印/重放/调用Uber写操作。

Governance验证：洁净Git源码导出（包含本次3份文档）PASS。工作树直接运行会误扫gitignored node_modules依赖README失效链接，未修改验证器或依赖；按实际发布仓库内容验证。

## Production 实际变更

- nginx `16fce3adf1ad7029e8824d4a978d421ce580723ffa9196ec79962507e435a88e`；镜像 `sha256:3e139cd20750a2091e2e1151cdcbb5d01d0b68179b0424086e2c4efa84c07aa3`，tag `restaurant-pos-frontend:analytics-labels-54d526e2ac6ebd69dc6b8829e28b80e8dd2925c1-r2`。
- Backend仍 `6a9587d1a6058dad77004c7b97e46fbe3cffdea203f64c8377fe5d0d3c53785a`，05:09:43Z启动未变；DB仍 `c2ab37fec6ac966e77a1d8ab7aa41d1da294b9ac57b1a21ce18904d04cfae91e`，2026-07-11启动未变。
- 仅nginx image/OCI label发生变化；环境变量、命令、限制、端口、网络静态IP、binds及共享TLS/代理配置逐项保持。
- 原Staging四容器仍exited。旧template-test四ID在本次开始之前已经不存在；独立 `allo-nouilles-test` 四容器已由其他Owner任务运行，本次完整保护、未停止/恢复/重建。共享sandbox webhook保留原状态。
- 最终替换加验证 22.43 秒；HTTP中断时长未连续测量，UNKNOWN，不宣称零中断。

## TOOLING / GOVERNANCE REPAIR

沿用现有Frontend-Only runbook及74df已审锁、资源、quiet-work、Compose、health、指纹和只读Analytics工具。现有promotion只支持成对backend/nginx，会超出本次边界；使用本批私有固定nginx operator，无新部署平台。
SCOPE_EXPANSION_CHECK / TOOLING_REASSESSMENT：产品改动10行，私有operator约90行及一次性权限修复约35行；仅服务本批nginx保护、回退和实际旧Test清单适配，未进入通用工具重构。

尝试记录：
1. 首次在容器替换前因旧Test固定ID已不存在而中止；冻结实时清单，要求存在性与冻结基线一致，并保护Allô。
2. 一次nginx候选已上线后，资源SHA验收发现JS不可读，立即自动回退原74df前端。根因：umask077下file-only tar隐式生成assets目录700，worker无法读取，try_files返回index。
3. host chmod后的-r1因BuildKit复用COPY层仍保留700，被UID101构建期Gate阻止；没有再次替换Production。
4. -r2 Dockerfile仅在新镜像中显式 `RUN chmod -R a+rX /usr/share/nginx/html/`，worker全资源验证通过后最终发布PASS。应用字节及SHA不变。

原失败baseline/images/logs保留；未清理任何缓存、镜像、volume、数据或日志。本轮无DB/Backend变更，按风险复用此前真实完整隔离恢复验证的数据库备份证据（[财务发布记录](UBER_ANALYTICS_ORDER_TOTAL_PRODUCTION_20261104.md)）；没有新增Production dump/restore负担。

## 恢复

仅当本批镜像仍在运行、环境参数一致且确需回退本次文案时，在授权运维窗口执行：

```bash
ssh restaurant-prod 'python3 /srv/restaurant-pos/production/analytics-labels-54d526e2ac6ebd69dc6b8829e28b80e8dd2925c1/operator.py rollback'
```

该命令获取现有ops锁，只将nginx恢复到 `sha256:e3607bba1a2bf3540f8bcaa2dbae757214cc75ec1c10c9e00298e10409b5de09`，确认镜像实际恢复后更新canonical并验证健康/保护清单。保留Backend74df/V39.2和全部当前业务数据；不执行financial REVOKE、数据库恢复或down migration。若已有其他前端版本/配置漂移，命令拒绝。

私有应用model、原镜像、manifest、SHA/proof、baseline及回滚脚本保存在 `/srv/restaurant-pos/production/analytics-labels-54d526e2ac6ebd69dc6b8829e28b80e8dd2925c1/`（目录0700，配置0600）；不要输出private配置或凭据。旧财务批次rollback不用于本次文案发布。

## Docker / Disk Hygiene

- Disk Before65% / After66%，约20GiB available；statvfs口径与df百分比口径不同。
- Build Cache Before8.794GB / After8.800GB，包含本批失败/修复COPY层；只读观察。
- Cleanup Performed: NO；Reclaimed:0。当前/原nginx、Backend、DB、全部其他项目、回滚和备份工件受保护。
- Staging Health: 原四容器仍停止，本次未恢复；旧Test缺席状态不变。
- Production Mutation: 仅本批nginx替换/自动回退/最终替换；Backend、DB及业务数据未改。

## Execution Time Breakdown

2026-11-04 10:40 EDT开始；各分项Estimated、按主要活动归属，总计约19分钟（含审查、两次发布前拦截及一次自动回退）。

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 2m | fresh源与实时运行清单、锁避让 |
| Implementation | 4m | 产品<1m，固定前端部署及权限修复约3m |
| Tests | 1m | 374 tests/build、lint基线对照、权限重现 |
| Agent 6 / PR / Merge | 3m | 合并审查簇；PR307更新，未merge |
| Staging Deploy | N/A | 保持停止 |
| Staging Acceptance | N/A | 本地隔离UI验证替代，Owner直接Production授权 |
| Production Deploy / Acceptance | 6m | 资源/锁/保护清单、构建期拦截、一次自动回退、最终PASS |
| Evidence / Governance | 3m | 实际工件/运行健康/恢复记录与状态同步 |
| Total | ~19m | Estimated；完整墙钟任务区间，不重复计算并行审查 |

Owner验收：[Saint-Denis Dashboard](https://pos.lanzhounoodlesmtl.com/stores/1/admin/dashboard)，刷新页面。Gate仅Owner文案验收；Stop Marker `ANALYTICS_KNOWN_PREFIX_REMOVAL_PRODUCTION_COMPLETE`。无后续优化授权。

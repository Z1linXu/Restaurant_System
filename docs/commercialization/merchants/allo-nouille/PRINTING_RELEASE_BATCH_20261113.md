# Allô 打印修复发布批次 — 2026-11-13

Owner已通过整批批准问题明确回复“批准此批次上线”。该记录绑定下列准确制品、步骤和目标；后续验收结果另见修复证据。

- 目标：`pos.allonouilles.com` / `43.166.242.250` / `allo-nouilles` / Store1、Organization1；不访问 Lanzhou。
- Backend exact SHA：`4c75f960d1ad00a799c1d5054f22286149429447`。
- Backend image：`sha256:7141585f9b545a69afe865a38a7577fca166bda5ab3cf7a9f3a7a9925c884804`，引用 `allo-nouilles-backend:4c75f960d1ad00a799c1d5054f22286149429447`。
- JAR SHA256：`c48bfc753aaa82672c603aa5ee030047cf218719fa88fe6dd3e6978c06bb6a66`。
- 发布包 SHA256：`d482cc9557920712c19b272e2f7a156488f1e0f04e2c27d9e331dbeef6ad886a`。
- 激活脚本 SHA256：`7f081e6c0f21d6413fe519fe04958ced7c78fe0a93bc5899cc1196776c1c3054`。
- 无迁移，保持 Flyway V43；Frontend 继续 `b78ab21d9d4b97bf2b6526ccc489503f486b866e`，数据库容器保持现状。

## 变更与验证

修复新建 Print Center 样票缺少 PAD intent 的错误，并让新建实体前台小票的品牌名冻结为 GBK 可编码的 `Allo Nouilles`。App/网页店名保留 `Allô Nouilles`，旧 intent、已冻结字节、金额、备注、claim/lease 和防重复保护保持原契约。

相关后端198项测试、前端522项测试、TypeScript、Android unit18项、WebView原生零字节探测等2项验证通过。准确 JAR 在隔离 QA 验证6张票（4诊断+新订单GRAB/前台），领取、打印前状态、字节哈希与失败回报通过，sentinel连接0。Agent 6 源码及必要发布步骤复审通过；停机前后均保留无CLAIMED/PRINTING检查。

APK1.0.3已安装在 `R5GL83EDH0M`，两台实际打印机连接测试成功，配对及IndexedDB有效数据文件5个哈希一致。

## 备份及部署动作

备份 `/srv/allo-nouilles/backups/printer-test-20261113/PRODUCTION_RECOVERY_ONLY_20261113T185616Z.dump`，SHA256 `50627c0f9e7540b770dc1dbf9d5ab435680dad37d38d2951c6505c3d998e2de0`。87表恢复与行哈希一致、约束和索引有效；恢复集群仅Unix socket可达，验证后移除。此数据未导入QA。

Owner已确认当前全部订单/队列均为测试数据，无需保留，已授权清理。发布步骤一次执行：

1. 检查精确制品、当前旧容器、隔离配置、V43及新鲜备份；无实体任务CLAIMED/PRINTING才停止旧后端。
2. 在已有后端替换的短暂停机窗口内执行已审SQL：当前测试outbox终止为SKIPPED、PENDING打印任务取消、活跃测试单及关联厨房/饮品任务取消。再次检查无在途任务；保留设备绑定、凭据和打印机/门店配置。清理失败则恢复旧容器。
3. 仅替换Backend image和APP_BUILD_SHA，`up --no-deps --force-recreate backend`，等待健康；Frontend和DB不重启。
4. 执行只读API/HTTPS/CORS/WSS、Store隔离、V43和配置/设备证明；记录结果与磁盘状态。不主动提交线上测试订单或触发测试打印。

## 回滚及观察

旧后端为 `b7893860815fb87ef936730213e32170626ab7f2` / `sha256:28df2d2751b8ac007856bcf805fe6c495d56982ad31634ce99a1371c424624c1`，原env和镜像已保留。仅恢复旧Backend image/SHA并重建后端，保持V43及现有数据，不自动恢复数据库。回退前需无PAD_DIRECT PENDING/CLAIMED/PRINTING和新诊断在途；已授权测试队列可按同一审过的清理方式取消。不得盲目重试或重放。旧版本会重新带回原诊断/新品牌编码问题，故仅在更严重回归时回退。

发布后观察健康、错误日志、现有Pad连通情况和自然测试业务。新测试业务可以正常改变业务数据和打印健康字段；静态配置、绑定和环境隔离必须保持。


实际结果：本批次于14:10:42 Toronto完成上线，14:11:28只读验收PASS。只有Backend重建；V43、Frontend和DB容器、静态配置与绑定保持。详细数据清理审计、运行镜像、验证与耗时见[修复证据](ANDROID_PRINTER_TEST_REPAIR_20261113.md)。

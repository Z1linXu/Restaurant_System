# 2026-10-23 堂食桌号头部发布

仅供 Owner 本批次授权使用。新 renderer 只改变堂食头部，无 migration。
Stage 和 Production 的旧 backend image 固定为预检所得 `264babe4…`，frontend
固定 `2b0749d3…`；任何并行发布漂移都中止。Production Uber worker=true 保留。

1. 测试、Agent 6、PR 合并后，在 Staging bare repository fetch，并建立
   `/srv/restaurant-pos/staging/releases/<full-sha>` 的 clean detached worktree。
2. 在该 release 执行 `python3 deployment/cloud/dine-in-headers/deploy.py staging <full-sha>`。
   复用现有锁、隔离数据源校验、完整可读备份、历史 cutoff hashes 和 runtime model。
   仅 backend 构建一次，临时 builder 使用 1GiB/1CPU，结束后删除该 builder。
3. 以本地 Java 17 target 编译 `RenderHeaderAcceptance.java`，保存 class 的 SHA256，
   放入本批次服务器私有 proof 目录。从实际 Stage 容器复制 `/app/app.jar`，只解出
   `BOOT-INF/classes/` 与 `BOOT-INF/lib/`。使用**同一镜像**的临时 `java` container，
   `--network none --read-only --memory 256m --cpus .5 --pids-limit 64 --cap-drop ALL
   --security-opt no-new-privileges`，只读挂载 proof 目录运行该 class。
   不启动 Spring，不连接数据库、服务或打印机。验证 6 模块 × 4 桌号的首行和实际
   PAD 字节前缀（ESC @、原 LARGE GS ! 33、完整桌号、reset、可选常规宽 UPDATE、LF），
   并验证要求的完整桌号头部在保守 42 列内。
4. 执行 `python3 deployment/cloud/dine-in-headers/verify.py staging <sha> <class-sha256>`，
   自动完成上述受限 JAR 渲染并确认 Stage image/source、两站 health、Flyway、原配置及冻结历史均通过。
   将真实 PASS 输出保存在 `render-acceptance.json`；仅此时在对应 Stage 私有 release
   目录原子写入 `acceptance.json`：`accepted=true, source_sha=<sha>, image=<digest>,
   header_cases=24, health=PASS`。保留验证时间及输出，不凭推断写 PASS。
5. 执行同脚本 `production <full-sha>`。只提升已验收的同一 backend image，保存
   Production fresh backup/rollback model，原子同步 canonical Compose/release metadata。
   frontend、DB container、运行环境、Uber cutover、设备及菜单/打印规则不变。
6. 在 Production release 私有目录放入同一 class 并执行 `verify.py production <sha> <class-sha256>`，
   对实际 Production JAR 重复步骤 3 的 24 例证明及 health。保存脱敏证据，并检查
   df/docker system df。此步骤不产生营业订单、PrintJob 或实体出纸。

回滚：脚本对 restart 后失败恢复保存的原 backend model，包括全部原环境变量。
数据库不恢复、不清理队列、不重放历史任务；V38 ledger 必须完全保持一致。
私有备份与模型保留于各环境 `dine-in-headers-<sha>/`，不得打印其中的 secrets 或业务
payload。旧应用 image 和原 frontend 是本批次受保护恢复工件。

测试：`mvn -q -Dtest='com.restaurant.system.printing.**.*Test' test`（backend），
以及 `python3 -m unittest discover -s deployment/cloud/dine-in-headers -p 'test_*.py'`。

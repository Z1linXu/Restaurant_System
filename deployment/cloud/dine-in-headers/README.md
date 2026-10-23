# 2026-10-23 堂食顶部额外16mm进纸发布

本目录复用已审的堂食头部发布流程，当前命令仅用于 Owner 授权的额外进纸批次。
旧批次的不可变脚本、模型及验收记录仍保存在其 exact-SHA release 中。
当前基线 B=`697947b3…`、F=`2b0749d3…`；V38，无 migration。Production Uber
worker=true 和全部配置保留。只替换 backend/nginx image 及 OCI labels。

1. 本地完成 printing tests、frontend test/build/changed-file lint、Agent 6、PR/merge。
   从服务器 Staging bare repo fetch，建立 clean
   `/srv/restaurant-pos/staging/releases/<full-sha>` detached worktree。
2. `python3 -B deployment/cloud/dine-in-headers/deploy.py staging <sha>`：复用既有锁、
   隔离校验、fresh 可读备份、配置与历史 hash，调用已有1GiB/1CPU临时 builder
   顺序构建两个 app image；部署后删除该 builder，不清理共享缓存或数据库。
   私有目录为 `/srv/restaurant-pos/<environment>/dine-in-top-feed-<sha>/`。
3. 在本地以 Java17 target 编译 `RenderHeaderAcceptance.java`，记录 class SHA256，
   上传到本批次环境私有目录，chmod600。执行
   `python3 -B deployment/cloud/dine-in-headers/verify.py staging <sha> <class-sha256>`。
   它从真实容器复制 JAR，在同镜像的 operator UID/GID、network=none、read-only、
   256MiB/.5CPU/PIDs64/cap-drop ALL/no-new-privileges 容器中运行纯内存证明。
   验证六票型×四桌码、一次16mm进纸、完整头部、初始化/裁纸顺序、PAD/TCP字节相同，
   并确认6例外卖不进纸。无 Spring、业务写入、真实订单或打印机连接。
4. 同时核对实际 frontend image/source，以及含16mm预览组件的公开 JS asset 与容器
   文件逐字节相同；重核 V38、配置/历史 hash、两站 health。只有实际全部 PASS 才原子
   写 `acceptance.json`，绑定同一 backend/frontend 镜像对和实际 JAR。
5. 同一应用 SHA 执行 `deploy.py production <sha>`，仅提升已接受的镜像对，保存独立
   Production fresh dump/rollback model，顺序更新 backend/nginx，原子同步 canonical
   Compose 和 release metadata。失败恢复原镜像对与全部原环境，不恢复数据库。
6. 向 Production 私有目录上传同一个 class，再执行 `verify.py production ...`；保存
   实际 Production 24例结果、公开 preview asset、配置/快照证明以及 df/docker system df。

若仅工具修正且 backend/frontend Git tree 完全相同，可以从新 clean script release
运行 verifier，参数仍为应用 SHA。结果分开记录 source_sha/script_sha，不重复构建应用。
始终用 `python3 -B`；不得向 clean release 写临时缓存、凭据或构建输出。

保留当前与回滚镜像、private models/dumps、历史打印及故障证据；不重放队列、不改 Pad
绑定或 printer endpoint。物理纸张长度未现场测量时必须标记 NOT_TESTED，不能用
名义16mm的命令证明替代现场尺量。

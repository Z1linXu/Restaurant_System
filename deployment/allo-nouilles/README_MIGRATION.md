# Allô Nouilles 独立部署与恢复

2026-11-08 已实际上线：<https://pos.allonouilles.com/>。
执行结果、迁移数量和 Production 对照见
[迁移报告](../../docs/governance/runtime/ALLO_MIGRATION_20261108.md)。
本说明只适用于新主机 `43.166.242.250` / `VM-0-6-ubuntu`，部署目录
`/srv/allo-nouilles`；禁止在旧 Production 主机上执行以下部署命令。

## 版本与边界

- 基准是实际 Test SHA `2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9`，不是当前工作区 main。
- 最终派生版本：`2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9-allo-ws-915e4ff4d8a6`。
- 最小兼容补丁见 `compatibility.patch`、`compatibility-manifest.json`：新域名 CORS、前台 STOMP JWT/门店鉴权、客户端重连携带请求头、不可变断开/心跳帧兼容。没有修改数据库结构、菜单、价格或订单业务。
- 最终后端镜像：`sha256:613327357035df7e16cbf50206891d7853dca02afd79b4bfbcbf8b13bb81e1d7`。
- 最终前端镜像：`sha256:e3ae049a8fcaf77311dca3173b5f5761168334672a8571df2e8cb7520cb24cc0`。
- PostgreSQL 使用源精确镜像 `sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28`，Flyway V1–V41。
- Compose project 为 `allo-nouilles`，仅 db/backend/frontend；Nginx 与 React 合用 frontend 容器。Print Center、Analytics 在 Backend 内，没有额外 Redis、集成 Worker 或测试库。
- 新机独立 DB、JWT、账号密码与网络；旧 Production 不参与部署、恢复或数据连接。

## 目录与日常检查

```text
/srv/allo-nouilles/
  compose.yml
  README_MIGRATION.md
  config/nginx.conf
  private/runtime.env             # root:root 0600，新 DB/JWT 密钥
  private/menu.source.json
  private/tls/                    # root:101，目录 0750、私钥 0640
  releases/<派生版本>/             # jar、dist、Dockerfile、补丁和校验清单
  state/postgres/
  state/acme/
  state/import-complete.json
  state/recovery-verification.json
  state/final-release-verification.json
  incoming/                       # 受限迁移归档
  backups/                        # root-only custom dumps、list、SHA、验证清单
  bin/backup-db.sh
```

本机 `~/.ssh/config` 已配置专用别名 `allo-nouilles`，私钥只在本机。
先确认目标身份，再检查服务；命令不会打印 runtime.env 内容：

```bash
ssh allo-nouilles
# 此后均在新主机执行
[ "$(hostname)" = VM-0-6-ubuntu ] || exit 1
cd /srv/allo-nouilles
sudo docker compose --project-name allo-nouilles --env-file private/runtime.env -f compose.yml ps
curl --fail --silent https://pos.allonouilles.com/api/v1/system/health
sudo docker stats --no-stream
sudo systemctl list-timers allo-backup.timer certbot.timer --no-pager
```

主机、数据库运行环境和 JVM 均使用 `America/Toronto`。历史 UTC-naive 时间字段按源值保留，未批量改写；原历史订单的 Toronto 日历日期一致。后续新业务由目标时区运行。

仅前端发布 80/443；Backend 8080 和 PostgreSQL 5432 没有 host port，内部网络 `internal: true`。主机 UFW 放行 22/80/443；云端 TCP443 已单独添加。SSH 已关闭密码、交互验证及 root 登录，保留用户批准的专用公钥。

日志每容器 10 MB × 3；内存上限 db 512 MiB、backend 1024 MiB、frontend 64 MiB。Backend heap 512 MiB、Hikari 最大 6 连接；DB 最大 30 连接。Docker 开机启动，三个容器 `unless-stopped`。需要停用某一服务时，要同时处理其重启策略及相关 timer，避免以后恢复运行。

## 迁移与数据保护

实际导入已完成。`import-source.py` 只用于第一次空库导入，具有主机/IP、源镜像、归档摘要、空库及完成标记保护，不能用于重复覆盖当前库。

保留库名 `restaurant_allo_nouilles_test`、用户 `allo_test` 和 `allo-test` profile 是原版本启动保护的要求，物理数据仍只在新机独立目录。必须保持：

- `APP_DELIVERY_ALLO_BOOTSTRAP_ENABLED=false`，所有 Seeder 关闭。
- Flyway baseline 关闭、target 41；JPA 仅 validate。
- `APP_REALTIME_AUTHENTICATED_STOMP_ENABLED=true`。
- Printing Mode 为 MOCK，设备数为 0，真实 Uber 与 Worker 均关闭。

Allô Organization/Store 为 1/1。仅在本机隔离恢复副本中去掉合成租户 2/2、其报表和旧 refresh sessions 后生成租户包。源 DB 没有执行这些删除。两个原员工身份、角色及门店关系保留，新机密码已独立轮换。受限交付文件：
`/Users/xuzilin/.codex/private/allo-migration-20261108/new-server-credentials.json`。
不要把该文件、真实 env、dump、Token 或 SSH 私钥放入 Git。

双拼选择沿用源 CUSTOM 选项组；源 Combo/Remove 表为零。不能用兰州的套餐或 Seeder 补出新内容。打印继续保持 MOCK；需要实体打印时在新环境重新配对设备，先核实打印机归属。不得复用旧 Pad 身份，也不得与旧环境同时消费第三方订单。

## HTTPS 与实时推送

DNSPod 权威 A 记录已指向 `43.166.242.250`，本次没有改 DNS。无冲突 AAAA/CNAME/CAA。
证书目录 `/etc/letsencrypt/live/pos.allonouilles.com`；本次证书有效期 UTC
2026-11-08 06:27:25 至 2027-02-06 06:27:24。HTTP 返回 308；API、深层前端路由与 WSS 均使用同一 HTTPS 域名。

`certbot.timer` 已启用，实际 `certbot renew --dry-run --cert-name pos.allonouilles.com --non-interactive --agree-tos` 成功。Ubuntu Certbot 1.21 不支持 `--run-deploy-hooks`；续期 hook 已另行实际测试。

`renew-certificate.sh` 安装为 `/etc/letsencrypt/renewal-hooks/deploy/allo-nouilles`，核对新主机、域名、证书有效性及公私钥匹配，复制到受限目录，再只对新 frontend 执行 `nginx -t` 和 reload。禁止复用旧 Staging hook：它会 reload 受保护的共享 Production Nginx。

修改 Nginx 单文件 bind mount 时，要在原文件 inode 内写入后校验并 reload，或只重建新 frontend；宿主机原子替换单文件不会更新容器持有的 inode。TLS 使用目录挂载，可安全原子更新目录内文件。

源 Test 曾因 F31 缺口封锁 `/ws`。当前派生版只允许 JWT 已验证用户订阅自己有权限的 `/topic/stores/{id}/frontdesk/orders`，同时验证当前账号状态、有效门店角色、能力和模块。拒绝匿名、无效/过期 JWT、跨店、通配订阅和客户端 SEND；推送前再次检查权限及有效期。Token 只在 STOMP CONNECT 请求头发送，不进入 URL。
这不表示其他部署或其他 KDS topic 的 F31 已解决；新部署没有开放未批准的 topic。

## 备份与恢复

每日 `allo-backup.timer` 在 Toronto 04:10 加最多 5 分钟随机延迟运行。脚本使用 root-only 目录、umask 077、flock、专用项目/DB 检查、custom pg_dump、归档目录及 SHA-256。日常 dump 保留 14 天；首次导入归档单独保留。

```bash
# 已确认位于新主机 /srv/allo-nouilles 后
sudo /srv/allo-nouilles/bin/backup-db.sh
sudo systemctl status allo-backup.timer --no-pager
```

`pg_restore --list` 可读不能代替恢复演练。本次已把实际每日备份下载到本机，恢复进临时 PostgreSQL Unix socket 隔离实例，85 表行数/内容哈希及约束、索引全部一致；临时实例已停止删除。验证文件为本机私有目录中的 `new-daily-backup-restore-verification.json`。

新机 daily dump 在本机有一份已验证离机副本；**尚未配置自动离机同步**。离机副本应放到 Owner 控制的受限备份位置，不能放公开仓库。本次没有创建未经指定的云存储、共享链接或外发数据。

恢复必须先保留故障时当前数据，验证 dump SHA、来源、租户归属与完整恢复，再选择切换。用独立空库和 `pg_restore --no-owner --no-acl --exit-on-error` 验证，不要对已有营业数据直接 `--clean`。如主机更换，应重新核实主机身份、网络和安全边界，并有意识地更新身份保护，不能直接删除 import marker 来绕过检查。

## 更新与回滚

从基准 Git 对象导出隔离源码，应用已记录补丁，运行测试及构建，不使用 dirty main 或浮动 tag。最终 64 项后端测试、4 项前端实时测试、TypeScript/Vite 构建通过。派生镜像继承源精确镜像，只替换 jar/dist，并标记源 SHA、派生版本和补丁摘要。新发布包需在传输前后验证 SHA-256。

更新前备份目标库和当前 runtime/config；将新镜像加载并检查完整 ID，在新 `releases/<version>` 保存产物。只修改新机 runtime.env 的三个版本引用，按 backend 健康后 frontend 的顺序更新。保持 DB/JWT Secret、数据目录、数据库身份及 namespace `allo-nouilles` 稳定。更新后复核真实域名、登录、WSS、隔离负向测试和错误日志。

应用回滚只在新机选择已验证的派生版本，保持现有数据库；若需退回**未经鉴权修复的原 Test 镜像**，必须先恢复 `/ws`、`/ws/info` 的 403 封锁，不能把原匿名 broker 留在公网。数据库回滚须另行评估切换后的新增订单，先保全当前数据，再在隔离库恢复和验证；不能用旧快照覆盖新营业记录。

旧 Test/Staging 已退役，不是自动故障切回目标。旧主机退役归档根为
`/srv/restaurant-pos/retired/20261108/`，其中 Test 与 Staging 严格分开。Staging 的多租户备份不能导入 Allô 新机。共享 Production Nginx、网络、续期任务及回滚引用均保留；没有任何全局 prune。旧 Test/Staging 域名可能由保留的共享入口返回 502，因为专属 upstream 已移除；恢复它们需要明确的新任务，不能顺手 reload Production。

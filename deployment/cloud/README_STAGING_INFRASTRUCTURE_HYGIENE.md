# Staging infrastructure hygiene（Owner 受限）

本包只面向固定的 Staging root `/srv/restaurant-pos/staging` 和 Compose project
`restaurant-pos-staging`。脚本默认只读；脚本输出先给出结构化 protected set / dry-run
plan，任何 execute 都要绑定 Owner 复核过的 plan SHA-256。此包不连接服务器、不执行
deploy，也不改变应用代码、数据库、volume、container runtime 或 Production。

## 工具

| 工具 | 默认行为 | 唯一允许的 execute 作用 |
| --- | --- | --- |
| `staging-buildkit-cache-hygiene.sh` | 读取固定 default builder 的 JSON disk usage | 仅 prune 168 小时以上、`Reclaimable=true`、immutable、unshared、`UsageCount=0` 的 BuildKit cache；保留 10GB；不使用 `--all` |
| `staging-release-retention.sh` | 计算 release protected set 和候选集 | 仅使用 `git worktree remove` 删除 plan 中明确的、clean、exact-SHA detached worktree |
| `staging-disk-check.sh` | 读取固定 Staging filesystem | 无 execute；返回结构化 `PASS` / `WARNING` / `CRITICAL` |
| `staging-retention-policy-check.sh` | 检查 Compose/Nginx 模板 | 无 execute；检查 container、Nginx 和 journald retention policy |

所有工具都验证 exact env path、project、SHA、owner、canonical path 和 symlink；遇到
缺失 API、非预期文件、权限漂移、Production/远端 Docker context、计划漂移或不完整
protected set 会 fail closed。

Release retention 将固定的 `state/postgres` 数据目录视为不可遍历、不可删除的保护边界：
只校验其 exact path、非 symlink、owner（部署用户或 PostgreSQL UID 70）和不可被组/其他
用户写入的 metadata，不读取数据库内容，也不把数据库文件当作 release evidence。

固定且强制为 `0700` 的 owner-only evidence 目录中的历史 regular files 可能保留既有
`0640/0644/0660/0664` mode；工具验证 owner、父目录、symlink 和有限 mode allowlist，但不会为了 mode normalization
改写历史 evidence。world-writable、executable、owner drift 或非 regular evidence 仍 fail closed。

若旧 release 目录仍是同 owner、非 symlink、canonical exact-SHA 名称，但 mode 不是
当前要求的 `0700`，工具不会读取、chmod 或删除其内容；它会输出 `UNSAFE_RETAINED` 并
加入 protected set。当前或 previous verified release 必须是完整验证过的 `0700` clean
worktree，否则仍 fail closed。

同样地，旧 release 即使 mode 为 `0700`，只要 HEAD、clean status、submodule 或 bare
repository worktree registration 不能完整验证，也只会进入 `UNSAFE_RETAINED`，不会成为
删除候选。工具不会尝试修理这类历史目录。

## Release rotation 接入

`staging-release-rotation.sh` 在 recovery record 中记录 `PRIOR_STAGING_SHA`。
后续 `staging-deploy.sh` 在任何 build/start 前自动运行一次
`staging-release-retention.sh --dry-run`，因此每次后续 deploy 都会先产出新的候选和
protected set。这个接入是只读预检；它不会自动删除 release。Owner 需要在独立窗口
复核 plan 后，按下面的 execute 命令运行。

## Owner 输入与执行顺序

1. Owner 先确认当前 Staging exact SHA、上一份 verified SHA、recovery/rollback/evidence
   引用，以及当前 Production/active image refs。Production image refs 作为参数传入
   BuildKit 工具，只用于 protected set，不会被 inspect、删除或切换。
2. 在目标服务器上先运行 dry-run，并把 stdout 保存为 Staging evidence 的 mode-0600
   文件；不要把 env 内容、Compose resolved output 或 secret 写进 evidence。

```bash
staging_root=/srv/restaurant-pos/staging
env_file="$staging_root/config/.env.staging"
plan="$staging_root/evidence/staging-release-retention.plan"
umask 077
deployment/cloud/staging-release-retention.sh --dry-run \
  --env-file "$env_file" \
  --previous-verified-sha <previous-verified-full-sha> >"$plan"
chmod 600 "$plan"
sha256sum "$plan"
```

BuildKit plan 同样必须明确给出当前 Production image；active refs 可重复传入：

```bash
deployment/cloud/staging-buildkit-cache-hygiene.sh --dry-run \
  --env-file "$env_file" \
  --production-image <production-backend-ref> \
  --production-image <production-frontend-ref> \
  --active-image <active-ref> >"$staging_root/evidence/staging-buildkit-cache.plan"
```

Owner 复核 protected set 和 `ELIGIBLE` 内容后，才可把 plan digest 绑定到 execute：

```bash
release_plan_sha256=<sha256-of-release-plan>
deployment/cloud/staging-release-retention.sh --execute \
  --env-file "$env_file" \
  --previous-verified-sha <previous-verified-full-sha> \
  --plan-file "$plan" \
  --plan-sha256 "$release_plan_sha256"
```

BuildKit execute 需要同一组 Production/active image inputs 和对应 plan digest。它只
调用 BuildKit cache-only API；任何 image/volume/container/database/Production 命令都
不是候选路径。若 plan 中的 release 已经不存在，execute 将报告 `ALREADY_ABSENT` 并
保持幂等；若出现新的 eligible record 或 protected set 漂移，则停止，不扩大删除范围。

## Disk thresholds

`staging-disk-check.sh --dry-run` 输出 `DISK_CHECK|...` 字段：

- `WARNING`：使用率 `>=80%` 或可用空间 `<=10 GiB`；
- `CRITICAL`：使用率 `>=90%` 或可用空间 `<=5 GiB`；
- `df` 元数据缺失也是 `CRITICAL`，并返回非零状态。

它不因 warning 自动 cleanup，也不执行任何 journald vacuum。

## Retention policy

- Staging Compose 的 `db`、`backend`、`nginx` 保持 Docker `local` log driver，模板上限为
  `10m × 3`；现有资源上限校验继续负责防止更大值。
- Staging Nginx 将 sanitized timing access log 写到 `/dev/stdout`，只包含 timestamp、
  method、Nginx normalized `$uri`（不含 query）、status、bytes 和 `request_time` /
  `upstream_*_time`。不记录 `$request_uri`、query args、headers、cookies、Authorization
  或 request body；Docker local driver 负责保留窗口。
- journald 只采用 policy recommendation：`SystemMaxUse=1G`、`RuntimeMaxUse=512M`、
  `MaxRetentionSec=14day`、`MaxFileSec=1day`。脚本只报告该 policy，不修改 host config，
  不执行 vacuum。

## Local verification

仅允许在工作树中运行静态检查和 shell tests：

```bash
bash -n deployment/cloud/staging-hygiene-common.sh \
  deployment/cloud/staging-buildkit-cache-hygiene.sh \
  deployment/cloud/staging-release-retention.sh \
  deployment/cloud/staging-disk-check.sh \
  deployment/cloud/staging-retention-policy-check.sh
bash deployment/cloud/tests/test_staging_infrastructure_hygiene.sh
git diff --check
```

测试使用 temporary fixtures / fake CLI；不连接服务器、不执行 cleanup/deploy。

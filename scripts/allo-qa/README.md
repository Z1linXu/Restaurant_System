# 本机合成 Allô Renderer 验收

只使用本轮新建的 PostgreSQL `127.0.0.1:26512`、backend
`127.0.0.1:18512`。预留 frontend `127.0.0.1:28182` 供主线程的实际浏览器验收。
脚本没有远程 URL 参数，不复用、停止或覆盖其他任务的进程和数据库。

私有目录固定为 `/Users/xuzilin/.codex/private/allo-release-20261112`；
`qa-config.json` 保存独立随机密码与 JWT secret，权限 `0600`，不得提交。
菜单仅使用批准的公开配置
`/Users/xuzilin/.codex/private/allo-migration-20261108/release-payload/menu.source.json`。
`qa-postgres` 是本轮从空库创建的合成数据目录，不包含生产订单、顾客、设备身份或
真实账号。受限父目录另外保留独立生产恢复 dump；它只用于 socket-only 恢复验证，
从未导入合成 QA，也不能作为脚本的测试 fixture。

```sh
python3 scripts/allo-qa/runtime.py prepare
python3 scripts/allo-qa/runtime.py start-backend --jar /absolute/new-artifact.jar --sha256 ACTUAL_SHA256 --build-sha ACTUAL_BUILD_SHA --phase bootstrap
python3 scripts/allo-qa/runtime.py configure-fixture
python3 scripts/allo-qa/runtime.py stop-backend
python3 scripts/allo-qa/runtime.py start-backend --jar /absolute/new-artifact.jar --sha256 ACTUAL_SHA256 --build-sha ACTUAL_BUILD_SHA --phase runtime
python3 scripts/allo-qa/api_safety.py --execute
python3 scripts/allo-qa/five_orders.py --execute --write-report
```

必须先由主线程构建新 artifact；脚本在启动前检查 SHA-256 及 class 中显式
`allo-qa` / `26512` guard，旧 JAR 不得启动。脚本不运行 Maven。
bootstrap 仅允许空合成数据库：`allo-test,allo-qa`、`app.environment=test`、
普通 seeders/其他 bootstrap/Uber/worker 全关、只允许 `MOCK,DISABLED`、无 endpoint。
后续 runtime 使用 production printing policy 校验权限链，允许模式为
`DISABLED,MOCK,PAD_DIRECT`。初始及最终 Store 均为 `MOCK`、无 printer endpoint。
不运行 Android Worker 或任何物理打印。

`configure-fixture` 只在 bootstrap 后的空订单合成数据库，把显示名设为
`SYNTHETIC Allo QA` 并显式开启 category bulk pricing。Store code 保持
`ALLO_NOUILLES_TEST`。这不修改生产菜单或生产定价。
同时以系统 `htpasswd` 经 stdin 生成独立随机 BCrypt(12) 合成 FRONTDESK 账号，
原始密码及 hash 仅写私有 `qa-config.json`，不输出或传入进程命令行。

`api_safety.py` 先验证精确新 artifact 与两个合成 Store、前台正常 POS 访问、
Manager/Owner 收据配置和新增 Bulk 三入口的前台 403、既有 Add-on 权限保留。
随后仅在本机 QA 暂时把打印 endpoint 设为 `127.0.0.1:29512` sentinel、
Store 切为 `PAD_DIRECT`，通过真实 HTTP 验证 signed submit、queued/pending、
设备 401/跨 Store 403、claim/start/payload、实际 15 秒 lease 到期、
PRINTING 不可重领、wrong attempt、complete/fail 和当前 DISABLED gate。
sentinel 只接受并立即关闭连接，绝不读取、发送或转发任何打印字节；期望连接数为 0。
complete/fail 回报明确标记 `SYNTHETIC_HTTP_PROTOCOL_SIMULATION_ONLY`，
`PRINTED` 只代表协议模拟回报已入库，硬件与实体输出始终 **NOT TESTED**。
脚本在 finally 恢复 MOCK、原逻辑 assignments、MOCK printer type 和空 endpoint，
并撤销两个合成设备；配置 API 会把原 null font/port/timeout 归一为其现有默认值。
不得与浏览器写配置、改价或路由测试并发运行。

五组实际场景为 B1 面型无 Combo、B1+A2整份+付费 Add-on、C1+A1+A2、
C6 双蛋白、前菜+主菜+饮品+Dessert 开/关对照；实际创建六单并逐单 Finish。
每单金额从批准菜单用 Decimal 独立计算，按每单 14.975% 税率 half-up 到分。
每单恰好一个 GRAB 与一个 FRONTDESK_RECEIPT；快照从实际
`rendered_text_snapshot` 读取并与本机数据库持久化值逐字核对。
合成设备仅用于验证 signed submit provenance，结束后撤销。
`five_orders.py` 自身不调用任何 Pad pending/claim/payload API，全部打印保持 MOCK。

当前菜单没有固定 Combo Offer 或单条春卷 portion。场景 2/3 只验证批准单品组合；
A2 为两条整份，不能据此声称固定套餐价格或一条春卷价格已验收。
此证明也不代表真实 Pad、打印机、纸张或物理输出已验收。

私有证据和原始快照保存在 `qa-five-orders-*` 文件。只有六单实际成功后，
`--write-report` 才会创建
`docs/commercialization/merchants/allo-nouille/PRINTING_ACCEPTANCE_5_ORDERS.md`；
已有报告受保护，不会覆盖。发生未知网络结果时不自动重试任何写操作。

## 新加单金额回归

`update_amounts.py` 专用于本轮已存在的合成 QA 和受限旧数据保护基线，不能在任意
环境直接重用。它要求主线程已启动匹配 build/JAR digest 的新 backend，验证新 class
含 `update_subtotal/update_total`，并只读核对精确 database/store/MOCK/无 endpoint。
它创建两笔新订单：A1外带→E1、B1堂食→B1→合格零价续面；仅 Finish 自己创建的订单。
相同 update key 重放应返回原 batch，无重复 Job；原 Orders1–8 / Jobs1–18 / outbox
及全部菜单逐行指纹必须保持。旧 Job18 的6.88原文不得为了通过测试被重写。

实际执行参数：

```sh
python3 scripts/allo-qa/update_amounts.py --execute --build-sha e7dde0a6e27adf3be00baa9972fee06cdcc5482c --jar-sha256 9430ee811525bd22bf525d479c864ac86c22d26c45bb93facee8e3f14c02d380 --with-refill --write-report
```

此命令已经执行并生成受保护的金额报告；现有报告存在时再次使用 `--write-report`
会在写入前拒绝。它需要本轮受限 `qa-addon-cleanup-*.json` 精确基线，不是通用 seeder。
报告是 API / 实际保存 Renderer 证据，不能替代未完成的浏览器 Finish / Print Center。

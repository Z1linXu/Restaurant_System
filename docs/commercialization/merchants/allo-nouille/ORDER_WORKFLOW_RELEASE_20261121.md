# Allô 订单操作发布批次 · 2026-11-21

Owner 批准本轮九项需求在验收通过后上线 Allô，并提供同签名 APK 下载。此批次绑定以下不可变制品和步骤。

| 对象 | 标识 |
| --- | --- |
| 后端、Web 和 APK 资产 Git SHA | `64421d9fdfc5b26257edc0e83e1fade1cd6f38c5` |
| JAR SHA256 | `eb78e15f50b46350b1bb77b40e2d96043e0a4ab3a121ad1ba1b4573a96e55343` |
| Web+JAR 归档 SHA256 | `84c642a403f0db822c625630dcd847f36aa1a64832e0e45e6a494a8c18984da7` |
| 发布 manifest SHA256 | `1c6d946c3fae7a28ad009e177872967616f70c27fad9603ec2a550e9ff0bd286` |
| APK | `com.allonouilles.pos` 1.2.0 (code 6), 4,073,504 bytes |
| APK SHA256 | `cc50bf0519bc4d2642fa1ee5ee387b9b072f6547ad18e81e456f82cdb7096c8f` |
| APK 证书 SHA256 | `eefc3470dfeb1d118a69c2d0ba011c2f8a158e79bc23395caf0ea3ef8d35e106` |

Flyway 增量只含 V45。备份 `/srv/allo-nouilles/backups/order-workflow-20261121/PRODUCTION_RECOVERY_ONLY_20261121T235702Z.dump`，SHA256 `a584facb10aad8c2b069aa2122fbc373fdbb45849b56ae8a85fbe9af0181a722`。已在独立 socket-only PostgreSQL 恢复 90 张表并核对逐表哈希、约束与索引，恢复副本已删除。

前置验收通过 263 项后端、522 项前端、TypeScript、实际浏览器与隔离 HTTP。V44 调整请求在 V45 以原身份重放。单独作废虾仁及其含税金额、原票与历史留存、Coupon、30 天日期边界均验收。初单、加单、只改备注的两路票面相同；实走 Pad claim/start/payload 得到两路相同 GBK ESC/POS 哈希且 TCP 次数为 0。APK 1.1.0 → 1.2.0 模拟器覆盖安装保留原安装身份和应用数据标记。

目标为 `VM-0-6-ubuntu` 及 Allô Compose project `allo-nouilles`，Store1/org1。复用先前发布路径。保留原镜像、配置和 Nginx 文件快照。活动打印租约与 Outbox 守卫通过后仅替换 backend，Flyway 45 启动健康后替换 frontend，数据库容器与挂载保持。通过正式管理 API 启用既有 HOT_KITCHEN 分配 3 → printer 6，核对 GRAB 分配仍指向 printer4。保持原打印机、字号、份数与设备绑定。线上检查 HTTPS 下载页和 APK 字节摘要、授权与订单历史、Flyway、运行版本和打印分配。实体打印由现场确认。

恢复计划保留 V45、全部上线后订单、审计与已冻结的打印字节。V45 新加料作废或 Coupon 一旦写入，采用理解这些字段的后端前向修复，旧 8b4f 后端无法正确计算新数据。Web 可独立回退基线并保留兼容后端。若需恢复 HOT 启用前的状态，先核对 assignment 3、printer6 和工作队列，再由管理员恢复 enabled=false。保留全部任务与数据。数据库恢复属于另行批准的事故处置，旧备份不会用于覆盖上线后数据。

实际发布于 2026-11-21 19:04:24 America/Toronto。三容器健康，V45 零失败。Backend image `sha256:9ea0144ede60342f2823ce65f8d014c0a6e7da1a351cb1e6824dcb4917fa8452`。Frontend image `sha256:0b0f64e6cd5929818601c6069efb61bd3a0a2cb93d39d279e08c22aeb01a03e4`。DB ID `cbd0c6cf504d3b746f880879db3f0beb9da3c3ce212e35c8d2b0a21061820d37` 未重新创建。正式 smoke 检查通过，下载 APK SHA256 与上表一致，HOT 原分配已启用，后端 ready 后错误计数为 0。Agent 6 PASS。

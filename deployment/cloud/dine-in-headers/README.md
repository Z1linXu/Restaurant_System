# 2026-10-24 逐桌拆桌命名发布

复用本目录受审流程，不建立新的发布生命周期。历史脚本/模型仍保存在各自exact-SHA
release。当前baseline为e44d7a5，backend a16b8a2f…、frontend abe9a86a…。
本批次仅替换双应用镜像并通过Flyway追加V39；Production Uber worker=true和全部env保留。

1. 完成相关printing/order/platform/station、前端test/build/lint、真实隔离PG迁移及
   A/B流程/并发测试。旧Production实际JAR对V39启动兼容必须有证据。Agent 6通过后
   PR/merge/fresh fetch，从bare repo创建clean exact-SHA release。
2. 运行`python3 -B deployment/cloud/dine-in-headers/deploy.py staging <full-sha>`。
   复用两锁、DB隔离、fresh可读dump、当前双镜像rollback model、有界builder；
   不清理共享cache/DB。私有证据位于
   `/srv/restaurant-pos/<environment>/split-label-mode-<sha>/`。
3. 新应用启动自动执行V39。要求V1–V38 ledger每行原值不变，且只追加精确源文件的
   V39/script/CRC32/success；非Flyway配置全等，旧dining_tables列/行全等、所有表默认
   LEFT_RIGHT，原订单store/type/table_no保持。V39不更新旧订单/桌台其他列。
4. 本地以Java17 target编译`RenderHeaderAcceptance.java`，记录class SHA256，上传本批次
   私有目录chmod600。执行`python3 -B deployment/cloud/dine-in-headers/verify.py staging <sha> <class-sha256>`。
   实际JAR在operator UID/GID、network-none/read-only/256MiB/.5CPU/PIDs64/cap-drop ALL/
   no-new-privileges容器中验证24个旧头部、48个两mode×A/B×六票型及6个外卖。
   共享实际display service、renderer和两编码器，无Spring/DB写入/订单/打印机连接。
5. verifier还要求16mm单次进纸、左侧原字号及UPDATE，公开MOCK预览与DiningTables设置
   JS逐字节匹配容器、V39/配置/旧列/身份/hash保护及双站health。仅实际PASS才写acceptance。
6. Owner已授权直接Production：执行`deploy.py production <sha>`复用Stage已接受的同一
   镜像对，不重建；fresh Production dump后顺序替换backend/nginx，原子同步canonical
   model/release。失败只恢复本轮捕获的e44镜像对和全部env，**保留新增V39，不还原DB**。
7. 同一class上传Production并执行verifier，保存实际结果、独立只读审计、磁盘及时间。
   部署不替Owner选择任何真实桌台的新mode；全部旧桌默认LEFT_RIGHT，包括Chinatown。

始终用`python3 -B`，不向clean release写cache/凭据/构建输出。工具/证据纯文档修正如
backend/frontend树一致则复用镜像，不重新构建。保留当前及回滚镜像、private dump/model。
不触发实体打印；纸长未现场测量必须保持NOT_TESTED。完成本批次后STOP。

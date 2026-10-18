# Allô Nouille 本地交互 Demo

本轮只用于合成数据、本地浏览器和 MOCK 验收。正式 G3 仍 PARTIAL，P4/P5/P6 未开启；本地试点不批准 main 合并、Staging/Production 或真实设备。

## 登录和页面

Frontend：<http://localhost:51026/login>。Backend：<http://localhost:8081>；健康检查 <http://localhost:8081/api/v1/system/health>。

用户名：`allo.owner`。密码是本轮随机生成的本地 Demo 密码，只保存在本机私有文件。Mac Terminal 执行以下命令复制，回到登录页粘贴：

```sh
pbcopy < "/Users/xuzilin/Library/Application Support/RestaurantSystemCommercialDemo/allo-20261018-d42urcne/owner-password.txt"
```

密码不写入仓库、PR 或本文。运行目录权限 0700，密码和 secrets 文件 0600。固定使用 `localhost:51026`，不要混用旧 Demo 的 `51025` 或 `127.0.0.1` Origin；独立 Origin 保留各自的 Menu Cache / Local Draft / Outbox。无需清理浏览器数据。

| 内容 | 入口 | 当前能力 |
| --- | --- | --- |
| Owner Home / Store Switching | <http://localhost:51026/owner/dashboard> | Allô 和独立空白 Neutral；通过已有 Store 选择器切换 |
| Allô Owner Dashboard | <http://localhost:51026/stores/1/admin/dashboard> | 当前已有后台/报表界面 |
| Menu / Options / Add-ons / Combo | <http://localhost:51026/stores/1/admin/menu/items> | 在现有菜单页切换标签；商品 Options 中编辑规则/默认面型 |
| Frontdesk | <http://localhost:51026/stores/1/frontdesk> | T1/T2 堂食、Takeout、商品选项、数量、提交 |
| Order History | <http://localhost:51026/stores/1/frontdesk/order> | 保留所有本轮合成订单和冻结明细 |
| MOCK Print Center | <http://localhost:51026/stores/1/admin/settings/printing> | 最近任务 → 预览小票；MOCK 不需要打印机 |
| Neutral Menu | <http://localhost:51026/stores/2/admin/menu/items> | 空白，新商家未复制任何菜单 |
| Neutral Frontdesk | <http://localhost:51026/stores/2/frontdesk> | 初始没有商品/桌台，Printing DISABLED |

Store Configuration 的现有 Owner API 是 `GET/PUT /api/v1/stores/{id}/merchant-configuration`，只编辑 schema/revision/brand/locale；完整通用配置管理页和 Profile 导入/应用页未开发。菜单、工位、桌台、加购、套餐和打印继续各用已有页面/API；模块用现有 Store Modules API，不虚构新增页面。

## 建议验收

1. 从 Owner Home 进入 Allô Frontdesk。T1 保留了一份浏览器提交的三份 B1 套餐测试订单；T2 可开始新单，也可使用 Takeout。
2. B1 经典牛肉面单点 $15.99，细/粗单选，默认细面；没有 Size。免费加面 0/1，其他 Add-on 按 PDF 价。
3. Make it Combo 固定 $19.99。B1/B2/B3/B5/B6/B7/B8 可选 A1–A9 完整前菜；B4 不可升级。选 A2 时每份是两条春卷。
4. C1/C2/C3/C4/C6 饭套餐 $19.99，自动包含三颗虾云吞汤与一条春卷。C5 不可升级，必须从四种蛋白恰好选两个不同项。
5. 将套餐数量调为 3 后提交；同一张后厨票中主菜和组件均数量 3，A2 两条份量共六条，饭套餐单条份量共三条。
6. 在 Print Center 查看 GRAB 后厨票和 FRONTDESK_RECEIPT 前台票。E1–E6 不创建后厨任务；纯饮料只需前台票。所有其他菜品归 BACK_KITCHEN。
7. 在菜单 Options 编辑默认细/粗，或先通过已有 Option CRUD 增加新面型，再加入同一规则；旧订单和原 PrintJob 预览不变。切到 Neutral，确认菜单仍为空。

32 个编号商品、15 个加购、2 个可独立追加的零元加面/饭商品已经配置；一条春卷是不可单售的组件商品，不使用半份 A2。前台优先中文，保留 PDF 的 FR/EN/ZH 文本。饭/面 Add-on 使用不同 Canonical Code；没有凭空增加配方/库存规则。

所有标价 CAD 税前。Demo 沿用现有 TaxCalculator，测试税率 14.975%；这是 **TEST ONLY** 配置。例：三份 $19.99 套餐，小计 $59.97、测试税 $8.98、合计 $68.95。实际商用税务适用性尚未核实。

PRIVATE `ALLO_NOUILLE_PROFILE@v1` 已独立归档并通过真实 Profile GET 验证。它是以 Neutral 为 base 的九 artifact 配置包；菜单通过 API 创建。现有 Store 的 Neutral 创建 ledger 保留，没有伪造为已通过新 Profile importer 创建。新 importer/materializer 不在本轮范围。

## 已验证与限制

真实 HTTP/PG 覆盖菜单价格、选项、Add-on、套餐、提交金额、组件数量、饮料、任务工位、MOCK、外店拒绝和 PRIVATE Profile；浏览器已真实登录、进入菜单/前台、选 B1 Combo、调数量、提交并打开厨房票预览。浏览器检查未发现 error 级控制台日志；这不代替完整设备/断网验收。

- 保留旧的兰州 Size/Combo/Add-on/打印回归；旧 Demo 的 PG 55440 / Backend 8080 / Frontend 51025 未被停止或修改。
- KDS 模块在本 Demo 关闭。旧分组 KDS/Ready 尚未适配新组件标识，未验收其完整组件展示。单后厨汇总 MOCK 打印已单独验证。
- 不宣称真实 TCP/PAD_DIRECT、物理 iPad/Android APK、真实 Uber、生产税务、全法语后台已验收。REAL/PAD_DIRECT 模式变更由商业本地配置拒绝。
- 保留两个旧纯饮料空厨房票失败记录（订单 41/58，`RENDERED_CONTENT_BLANK`），不删除/标记处理或重打；这是验证发现并修复的创建条件问题。新饮料提交的修复验证见实施记录。
- 前台现有打印中心的规则预览示例仍带旧的示例字段，不代表 Allô 实际菜单或实际票据。查看“最近打印任务”的真实冻结小票。
- 原有依赖审计/正式 G3 视觉闭环限制保留；本次桌面浏览器操作不自动关闭 G3-04/G3-08。

## 服务、日志、停止与重启

独立 runtime：
`/Users/xuzilin/Library/Application Support/RestaurantSystemCommercialDemo/allo-20261018-d42urcne`

PG16 仅监听 `127.0.0.1:55441`，独立 DB `commercial_allo_demo`，应用角色 `allo_app` 非超级用户。Backend/Frontend 仅监听 127.0.0.1。服务使用脱离本次工具会话的独立进程；不会在交付后主动停止，Mac 重启/睡眠影响本地可用性。

日志都在以上 runtime：`postgres.log`、`backend.log`、`frontend.log`。验收证据/失败修复/小票文本也在该私有目录，不含密码的摘要写在 [IMPLEMENTATION_RECORD](IMPLEMENTATION_RECORD.md)。完整 `secrets.json` 不应上传或粘贴。

查看状态（不停止任何服务）：

```sh
python3 "/Users/xuzilin/.codex/worktrees/allo-nouille-local-pilot/Restaurant_System/scripts/local-demo/allo_runtime.py" status --runtime "/Users/xuzilin/Library/Application Support/RestaurantSystemCommercialDemo/allo-20261018-d42urcne"
```

仅停止本轮 Demo（数据保留，不触碰旧 Demo）：

```sh
python3 "/Users/xuzilin/.codex/worktrees/allo-nouille-local-pilot/Restaurant_System/scripts/local-demo/allo_runtime.py" stop --runtime "/Users/xuzilin/Library/Application Support/RestaurantSystemCommercialDemo/allo-20261018-d42urcne"
```

从停止状态重启本轮相同数据库及已验证产物：

```sh
python3 "/Users/xuzilin/.codex/worktrees/allo-nouille-local-pilot/Restaurant_System/scripts/local-demo/allo_runtime.py" start --runtime "/Users/xuzilin/Library/Application Support/RestaurantSystemCommercialDemo/allo-20261018-d42urcne"
```

不要重复执行 `init/bootstrap/configure` 来“重启”。本轮运行的是私有 `backend.jar` 和 frontend dist 快照，不会因编辑 worktree 自动改变。生命周期脚本验证 runtime/PID/PG 身份，拒绝操作其他进程；无数据库 reset、Flyway repair/clean 或 Docker 清理。

## 代码与交付来源

Branch：`codex/allo-nouille-local-pilot`，base `cb6ff2e91409c3445d5a5e765e68f93599838638`。独立应用产物、最终源码指纹、Commit 和草稿 PR 记录在实施记录及私有 `delivery-manifest.json`。草稿 [PR #279](https://github.com/Z1linXu/Restaurant_System/pull/279) 仅面向 `codex/commercial-phase3-foundation`，不合并 main，不部署远端。

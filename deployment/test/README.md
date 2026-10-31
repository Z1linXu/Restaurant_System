# 独立 Template Test 部署

PRIMARY_REPAIR_GOAL：把已验证的 M0+M1 精确候选部署到 `https://test.lanzhounoodlesmtl.com`，只使用独立合成数据库和 MOCK。EXPECTED_REPAIR_CLASS：HIGH。

此新增部署边界是 **BLOCKING**：旧 `deployment/cloud/` 工具操作既有 Production/Staging，不能拿来创建本次独立 Test。本目录复用已核对的 edge→受限 host relay→localhost 模式，不创建通用发布平台、备份/恢复系统或 Control Plane。旧脚本与原工作区/Research 均不修改。本轮 Owner 已授权 Test 运维；实际部署仍须先通过构建、集成、安全和独立 review，主线程明确放行精确候选。

## 隔离和资源

- 项目名固定 `restaurant-template-test`，目录 `/srv/restaurant-pos/test`（0700）。DB、Volume、配置、JWT 和测试账号密码全部新建；不读取任何 Production/Staging `.env`、数据库或设备凭据。
- 重入时严格核对两个Test专用bridge的Compose project/resource labels、名称、internal属性和成员；本地volume不允许redirect，不收养无标签/其他项目资源。unknown端口监听不kill、不复用：frontend必须是精确localhost Docker绑定至其Test ingress地址，relay监听PID的cgroup必须属于本Test relay。
- DB/backend仅加入 `restaurant-template-test_internal` 内部网络，无宿主端口及外网。frontend同时加入该内部网络和全新 `restaurant-template-test_ingress`（非internal bridge，唯一成员必须为Test frontend），后者允许Docker发布 `127.0.0.1:28080`。frontend具备该桥的出站能力，但无秘密环境变量，nginx仅固定代理Test backend；后端/数据库不接入该桥。新 relay 仅 `172.18.0.1:28480`，只允许现 edge `172.18.0.4`，不加入任何既有 Docker 网络。
- 上限：backend 512MiB / DB 128MiB / frontend 32MiB / relay 16MiB，CPU 合计上限1.25。JVM heap224MiB，并限制 metaspace/direct/code cache。首次启动要求 available RAM≥1100MiB、磁盘≥6GiB；不足即停止，不清理其他镜像、卷或服务。后续更新若资源不足，单独评估仅Test停用窗口，不能削弱准入或停止旧服务凑空间。
- 只使用 `images.json` 的官方 amd64 digest。其2026-10-31 registry manifest观察对应 JRE17.0.20.1_1、nginx1.30.6-alpine、Postgres16.15-alpine；不是依赖安全审计证明。业务镜像不继承现有 Production/Staging image ENV。远端只 COPY 已编译 jar/dist，不运行 Maven/npm，不上传源码、node_modules、真实数据或密钥。
- 公网 `/ws` 在Test edge/relay/frontend均403；REST读取刷新状态。F31未修复、不视为SaaS安全放行；已部署Android、PAD_DIRECT、Renderer、去重机制不变。应用端MOCK-only启动/执行守卫由后端独立实现，网络隔离不能替代该守卫。
- `runtime.env` 在远端私有目录0600生成独立随机值；更新保留原Test密码，不轮换其他环境。不要将该文件、账号密码、JWT、完整容器环境/日志输出到聊天、Git、CI artifact或公开报告。

## 当前预检（历史观察，不是未来部署PASS）

2026-10-31只读观察：A=`170.106.13.34`，未观察AAAA/CNAME；SSH alias `restaurant-prod` 可用，目标为同一共享主机，ubuntu有docker/NOPASSWD sudo。HTTP200落既有默认站点，**不是Test应用**；HTTPS因未知SNI拒绝。尚无Test目录/容器/证书。

2vCPU、3.7GiB RAM、available约1.4GiB、无swap；根盘余12GiB。现8个应用/数据库/中继容器不动。`cloud-nginx-1`占80/443，edge=`172.18.0.4`，gateway=`172.18.0.1`。生效 `/etc/nginx/conf.d/default.conf` 与 bind 源 `/home/ubuntu/Restaurant_System/deployment/cloud/data/nginx/default.conf.template` 均5595字节、SHA256 `b9d6c2618d96b1c0710df4a6346c63ec06b23770f4dde0724adaa6272e27b2e0`。单文件bind必须原inode原地写，禁止rename覆盖。

仅现有pos、staging-pos证书；certbot1.21已安装。本机Docker CLI29.6.2/buildx0.35可用但daemon未运行；本机JDK21、Maven3.9.9、node20.19.6/npm10.8.2可用，原前端Dockerfile的Node22构建并未在本次远端使用。Maven默认JDK25，构建须显式选择JDK21。所有状态在实际操作前重验。

## 构建、测试和封装

主线程先完成产品代码、保护文件回归、独立review及精确候选commit。以下命令示例只在已获放行的候选上运行；没有任何脚本自动跳过测试或触发部署。

```sh
env JAVA_HOME=/Users/xuzilin/Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home mvn -f backend/pom.xml verify
# Frontend实际测试命令由其package.json与实施Tracker记录；先测试，再按精确SHA构建。
# 在frontend目录：VITE_APP_BUILD_VERSION=<FULL_SHA> npm run build
PYTHONDONTWRITEBYTECODE=1 python3 deployment/test/test_safety.py
python3 deployment/test/release.py artifact-hashes
```

`artifact-hashes` 输出 jar SHA256及dist树摘要。dist算法为排序后的 `dist/相对路径 + NUL + SHA256(raw bytes)`。请将真正运行的测试/命令和同一候选、同一artifact写到仓库外0600 receipt；不要预填PASS。

```json
{
  "code_sha": "<40位已提交候选SHA>",
  "environment": "ISOLATED_SYNTHETIC_MOCK",
  "checks": {"backend":"PASS","frontend":"PASS","integration":"PASS","security":"PASS","legacy":"PASS"},
  "independent_review": "ACCEPT",
  "commands": ["实际执行的命令；无密码、token或业务payload"],
  "jar_sha256": "<实际已测jar摘要>",
  "dist_sha256": "<实际已测dist摘要>"
}
```

脚本不认证receipt陈述的真实性；真实日志/命令/结果由实施Tracker与review证据支持。封装要求clean worktree、完整SHA、全部五组PASS、独立review ACCEPT、精确jar/dist摘要，且frontend资产包含相同构建SHA。

```sh
python3 deployment/test/release.py pack --evidence /private/receipt.json --out /private/template-test.tar.gz
python3 deployment/test/release.py upload --bundle /private/template-test.tar.gz --expected-sha <FULL_SHA>
```

输出包只能在仓库外新建路径（0600）。封闭白名单只含jar/dist、7个部署文件、`code-sha.txt`部署身份元数据、manifest与非敏感receipt；不会打包env、Git历史或源码。upload复核摘要/常规文件，SSH只创建新的 `/srv/restaurant-pos/test/releases/<SHA>`；不覆盖旧release，不启动服务。

## 远端启动（必须等Root通知）

现有SSH身份/known_hosts固定，使用BatchMode/StrictHostKeyChecking，禁UpdateHostKeys、ControlMaster和LocalCommand。首次创建前重验主机/端口/容量；如果端口归属不符或资源不足，停止报告，不占用其他项目。

```sh
ssh restaurant-prod "python3 /srv/restaurant-pos/test/releases/<FULL_SHA>/remote.py deploy --sha <FULL_SHA> --apply-tested-candidate <FULL_SHA>"
```

`deploy`校验manifest/receipt、路径与官方image digest，私有生成Test env，渲染并私下校验Compose（不打印秘密），pull固定官方base，以 `--network none` COPY产物组镜像，然后只 `compose -p restaurant-template-test up`。不运行down/prune/restore或任何cloud部署脚本。DB迁移只在新Test库运行，不修改V1–V39。backend健康路径为 `/api/v1/system/health`。

启动前后比较8个既有容器ID/image/StartedAt/restart count；异常即报告，不自动修旧环境。失败保留Test诊断，不清卷/队列。运行证据写 `/srv/restaurant-pos/test/evidence/<SHA>.json`，含image ID、code SHA、配置来源、migration版本/脚本/成功值；ledger必须匹配该源码migration清单。构建的未推送镜像可能没有registry RepoDigest，image ID须如实标识，不虚构manifest digest。

成功判定还必须满足：四容器均running且非restarting、DB healthy、两应用SHA label正确、实际网络/端口归属正确；再从既有edge容器清空进程环境后，以canonical Host请求受限relay的`/test-health`、`/test-code-sha`与后端health，分别核对Test标记、精确SHA和UP。仅loopback health200不足以成功。edge-http/edge-https/issue-cert每次也先重验此链；失败不改共享入口，rollback撤除Test入口不依赖Test仍存活。

Root负责Test fixture账号合同、禁用历史默认登录、MOCK守卫与Owner/Manager业务验收。当前Compose传入 `TEMPLATE_TEST_OWNER_PASSWORD` / `TEMPLATE_TEST_MANAGER_PASSWORD`、`TEMPLATE_TEST_ENABLED` / `TEMPLATE_TEST_MOCK_ONLY`；最终profile合同须与后端集成核对。账号从受保护远端文件取到Owner受保护渠道，不能在本README或最终报告显示密码。

## 首次Test HTTPS接入（单独、最小共享入口动作）

先完成Test本地健康、认证/Scope、MOCK及公共/ws拒绝测试，再由Root审阅本步骤。只追加带明确marker的Test vhost；现有default/Production/Staging块字节保持。共享入口reload是获授权的唯一共享运行变更，**不能声称共享Nginx完全未变**；旧应用/DB不部署、不重启。

1. `edge-http`复用已有Production operations与Staging hygiene共享flock（只持锁，不重写锁字节），并持受控`O_NOFOLLOW`源fd；校验源pathname/bind-mount inode和生效inode、双方摘要匹配expected值，保存私有原始配置。candidate仅经stdin写入edge容器内新建的私有临时普通文件，现有nginx对该绝对路径执行校验、旧入口HEAD后，**立即在写前再次CAS核对inode与内容**。source/effective均保留inode原地写，再`nginx -t`及graceful reload。Test HTTP只开放ACME，其余503。
2. `issue-cert`只对`test.lanzhounoodlesmtl.com`执行webroot certonly，独立cert-name；显式`--config /dev/null --no-directory-hooks`及空pre/post/deploy hooks，不能继承宿主ini或目录Hooks。无nginx插件/standalone/旧证书替换/服务重启。ACME工作/日志在私有Test目录，证书留共享edge已有只读证书树。不上载私钥，不把证书密钥放Test应用容器。
3. `edge-https`使用第一步输出的新hash重验，再追加Test TLS和80→HTTPS；proxy只到受限Test relay。未知SNI原拒绝规则不变，Host/SNI不匹配421，/ws403。验证公共HTTPS（禁止-k/--insecure/--resolve）、静态资产、SPA、登录与API。

```sh
# 首次expected值必须在执行时重新确认，不直接信任历史预检。
ssh restaurant-prod "python3 /srv/restaurant-pos/test/releases/<SHA>/remote.py edge-http --sha <SHA> --apply-tested-candidate <SHA> --shared-entry-reviewed --expected-edge-sha256 <OBSERVED_HASH>"
ssh restaurant-prod "python3 /srv/restaurant-pos/test/releases/<SHA>/remote.py issue-cert --sha <SHA> --apply-tested-candidate <SHA> --shared-entry-reviewed"
ssh restaurant-prod "python3 /srv/restaurant-pos/test/releases/<SHA>/remote.py edge-https --sha <SHA> --apply-tested-candidate <SHA> --shared-entry-reviewed --expected-edge-sha256 <HTTP_STEP_HASH>"
```

安全说明：pre-validation在现有edge中以`umask 077`和固定`/tmp/restaurant-template-nginx.XXXXXX`前缀`mktemp -d`新建0700目录，stdin写入其中0600的`candidate.conf`，执行`nginx -t -c <绝对普通文件路径>`。不使用`/dev/stdin`作配置文件；不输出candidate或nginx诊断，不复制配置出容器。成功、失败及受控信号退出均通过trap只删除本次精确candidate文件并rmdir本次目录，绝不递归删除其他目录。重载后比较旧入口HEAD状态与容器身份；真实nginx及共享入口验证仍须实际执行，本地shell测试不证明线上PASS。

若已封存且健康的应用仅需修复运维helper，可将独立审查后的新helper放入`/srv/restaurant-pos/test/operations/<TOOL_SHA>/remote.py`，以原封存`APP_SHA`调用入口动作；`verify_release`仍校验`releases/<APP_SHA>`，不覆盖旧release、不执行`deploy`、不重建或重启应用。证据须分别记录实际应用SHA、独立Tool Commit与helper文件摘要，不能将Tool SHA冒充应用版本。

异常回退同样受CAS约束：仅同一受控inode且内容仍为本次old/candidate（源文件另允许本次已计数写入可证明的半写状态）才能恢复；第三方内容/rename/不可证明的effective半写均fail closed，保留备份并报告，不盲目回写旧值。共享锁要求其他已review运维工具遵守同一锁，不能声称可阻止恶意root绕锁修改。

只撤除Test入口时用`edge-rollback`并提供当前hash；只恢复保存的原始prefix。若后来出现非Test变动，工具拒绝覆盖，交Root审阅差异。证书可保留；不删数据库卷、不回滚迁移、不触旧部署。后续候选通常只更新Test应用，无须再次改共享vhost。Test证书的独立续期是运行后续项，需沿同一cert-name/webroot及Test-only校验/reload边界安排，不擅改已有续期任务或续签旧域名。

## 停止条件

任何精确SHA/receipt、数据库/网络/凭据、保护文件、容量、共享入口漂移或真实出站风险不满足时停止该动作。没有Production/Staging发布、三旧店迁移、真实打印/Uber/支付、Android或额外成熟打印核心变更授权。完成公共域名业务验收并交付证据后停止，等待Owner验收。

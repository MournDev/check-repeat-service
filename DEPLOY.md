# 生产部署手册

面向 **2C4G 阿里云 ECS · Gitee + Jenkins(容器) + Docker Compose** 的部署流程。

本手册只覆盖**后端**。前端由另一套容器与 Nginx 提供，不属于本仓库职责。

## 架构

```
Gitee ──Push webhook(秒级)──► Jenkins(容器, 网关 vhost → jenkins:8080) ──► mvn package
                        │
                        ├──► docker build   (经挂载的 docker.sock)
                        ├──► docker compose up -d backend
                        └──► 健康检查 → 失败自动回滚
```

服务器上**同时存在两套 compose，项目名刻意隔离，互不干涉**：

```
/opt/check-repeat/docker-compose.yml              项目名: check-repeat
    中间件（既有，本仓库不管理、不要动）
    check-repeat-mysql / -redis / -minio / -kkfileview
    数据卷: check-repeat_mysql_data / _redis_data / _minio_data / _kkfileview_data

/usr/local/check-repeat/docker-compose.prod.yml   项目名: check-repeat-backend
    backend（本仓库的部署目标）
    ├─ 加入外部网络 check-repeat_app-network   → 与中间件互通
    ├─ 挂载既有卷 check-repeat_app_data → /data        （uploads/reports/临时文件）
    └─ 挂载既有卷 check-repeat_app_logs → /app/logs
```

**为什么必须隔离项目名**：compose 以项目名给容器打标签。若两侧同名，任一侧执行 `docker compose down` 会连带删除**对方项目**的容器——中间件会跟着停。所以 `docker-compose.prod.yml` 顶部显式写了 `name: check-repeat-backend`，不要删。

中间件之间靠 docker 网络别名互相访问：`mysql` / `redis` / `minio` / `kkfileview`（**不是**容器名 `check-repeat-mysql`）。`.env` 里就填这些别名。

**不使用外部镜像仓库。** 镜像只在本机构建并打上构建号 tag，保留最近 3 个用于回滚。
原因：ACR 个人版官方声明「仅限开发测试、禁止生产、无 SLA」，而 Jenkins 与后端同机时仓库本就是多余的中间环节。

## 内存预算（4GB 总量，勿随意新增服务）

| 组件 | 内存 | 关键调参 |
|---|---|---|
| OS + Docker + 阿里云 agent | ≈500MB | aegis / cloudmonitor / journald |
| mysql | 768m | `innodb_buffer_pool_size=256M`；当前实测 ≈410MB |
| redis | 192m | `maxmemory 256mb` + `allkeys-lru` |
| minio | 384m | `GOMEMLIMIT=256MiB`；若 `STORAGE_TYPE=LOCAL` 则实际未被使用 |
| kkfileview | 512m | 含内部 LibreOffice 进程 |
| jenkins 容器 | 1536m | Jenkins JVM `-Xmx384m` + Maven JVM `-Xmx512m` |
| **backend** | **1152m** | `-Xmx768m -XX:MaxMetaspaceSize=192m` |

实测基线：移除旧一体化应用后整机占用约 2.0GB，余量约 1.4GB。

**4GB swap 是必需的**，否则构建期（Maven + docker build 与生产服务抢内存）一次抖动就会触发 OOM killer 杀掉 MySQL 或后端。构建期间后端变慢属预期，Jenkins 已设 `disableConcurrentBuilds()` 防止叠加。

## 一、服务器准备

### 1. swap（必做）

```bash
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
echo 'vm.swappiness=10' >> /etc/sysctl.conf && sysctl -w vm.swappiness=10
free -h    # 确认 Swap 一行是 4.0Gi
```

### 2. 部署目录与 .env

```bash
mkdir -p /usr/local/check-repeat
cd /usr/local/check-repeat
git clone https://gitee.com/Sc0102_1129/check-repeat-service.git /tmp/crs
cp /tmp/crs/docker-compose.prod.yml /tmp/crs/deploy.sh /usr/local/check-repeat/
chmod +x /usr/local/check-repeat/deploy.sh

# 生成强口令并写入 .env（纯十六进制，避免 JDBC/URL 转义问题）
cp /tmp/crs/.env.example /usr/local/check-repeat/.env
sed -i "s|<CHANGE_ME_MYSQL_PASSWORD>|$(openssl rand -hex 16)|; \
        s|<CHANGE_ME_REDIS_PASSWORD>|$(openssl rand -hex 16)|; \
        s|<CHANGE_ME_MINIO_PASSWORD>|$(openssl rand -hex 16)|; \
        s|<CHANGE_ME_JWT_SECRET_32_CHARS>|$(openssl rand -base64 32 | tr -d '\n')|" \
    /usr/local/check-repeat/.env
# 邮件账号需手工填（QQ 邮箱 SMTP 授权码）
vi /usr/local/check-repeat/.env
chmod 600 /usr/local/check-repeat/.env

# 属主交给 Jenkins 容器内的用户，否则流水线写不进来（原因见下）
chown -R 1000:1000 /usr/local/check-repeat
```

`.env` 不进版本库（已在 `.gitignore` 中），模板见仓库根目录 `.env.example`。

> **为什么必须 chown**：Jenkins 容器以官方镜像的 `jenkins` 用户运行（uid 1000），而 `/usr/local/check-repeat` 是 root 建的。流水线里有两步要**写**这个目录——同步 `deploy.sh` / compose 文件，以及 `deploy.sh` 里 `sed -i` 改 `.env`。属主不对，第一次构建就会停在「同步部署文件」阶段报 permission denied。
> `1000:1000` 取自官方镜像的 `jenkins` 用户。构建完自定义镜像后可确认一次：
>
> ```bash
> docker run --rm --entrypoint id jenkins-custom:lts-jdk17   # 期望 uid=1000 gid=1000
> ```
>
> `chmod 600` 依然保留：`.env` 只有 Jenkins 用户和 root 能读。

> 注意 `MYSQL_PASSWORD` / `REDIS_PASSWORD` / `MINIO_ROOT_PASSWORD` 的值必须与中间件容器**实际**生效的口令一致，否则 backend 连不上。中间件口令在 `/opt/check-repeat/` 那份 compose（或其 `.env`）里。

### 3. 安全组

| 端口 | 策略 |
|---|---|
| 22 / 80 / 443 | 放行 |
| 8081（Jenkins） | **不需要放行**（已绑 `127.0.0.1`，只能走 SSH 隧道访问） |
| 50000 | 不需要放行（单机 Jenkins，无 agent） |
| 3306 / 6379 / 9000 / 9001 / 8080 | **禁止对公网开放**，中间件只在内网 docker 网络，backend 只绑 127.0.0.1 |

## 二、Jenkins

### 1. 构建自定义镜像

官方 `jenkins/jenkins:lts-jdk17` 里没有 docker CLI、compose 插件和 Maven，必须用 `jenkins/` 下的 Dockerfile 重建。

```bash
cd /tmp/crs
docker build -t jenkins-custom:lts-jdk17 jenkins/
```

### 2. 启动容器（替换既有容器，保留 Job 与插件）

既有 Jenkins 容器没有 docker CLI 和 Maven，且未挂载 `docker.sock`，必须重建。**`/home/jenkins_home` 的绑定要原样保留**，Job、凭据、插件都在里面。

```bash
docker rm -f jenkins   # 只删容器，/home/jenkins_home 是宿主目录，数据不受影响

docker run -d --name jenkins --restart unless-stopped \
  -p 127.0.0.1:8081:8080 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /usr/local/check-repeat:/usr/local/check-repeat \
  -v /home/jenkins_home:/var/jenkins_home \
  --group-add "$(getent group docker | cut -d: -f3)" \
  -e JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=192m" \
  -e TZ=Asia/Shanghai \
  --memory 1536m \
  jenkins-custom:lts-jdk17
```

> Jenkins 自身只绑 `127.0.0.1:8081`，管理界面走 SSH 隧道：`ssh -L 8081:127.0.0.1:8081 root@<服务器IP>`。
> 但容器在网络内的名字是 `jenkins:8080`，网关的 `jenkins.check-repeat-system.xyz` vhost **只放行 `/generic-webhook-trigger/` 一个路径**（`location /` 是 `deny all`）并反代到它，所以 invoke 端点公网可达、**管理界面在公网被 403 挡掉**。构建由 Gitee 的 Push webhook 触发（见 §5）。
> 不发布 50000：单机 Jenkins 没有 agent，用不到。

三个关键点，漏一个就会出问题：

1. **`--group-add`**：让容器内 `jenkins` 用户能读写宿主 `docker.sock`，否则构建阶段全部 permission denied。（既有容器该项为空，是它无法构建镜像的直接原因。）
2. **`--memory 1536m`**：必须同时容纳 Jenkins JVM（384m）和它拉起的 Maven JVM（512m）。给少了构建时容器会被 OOM。
3. **`-v /usr/local/check-repeat:/usr/local/check-repeat` 路径必须完全一致**。Docker-out-of-Docker 下，`docker compose` 的路径是由**宿主 daemon** 解析的；路径不一致，compose 里的 bind mount 会指向容器内不存在的目录，部署静默失败。

启动后先确认容器能真的操作 docker（不必等到构建失败才发现）：

```bash
ls -l /var/run/docker.sock              # 宿主上，确认组是 docker（GID 与 --group-add 一致）
docker exec jenkins docker ps           # 容器内能列出容器才算通
```

### 3. 初始化

Maven 镜像配置靠官方镜像的 `ref` 机制（`/usr/share/jenkins/ref/` 复制进 `JENKINS_HOME`）落地，**但 `JENKINS_HOME` 已存在时该机制不保证生效**。所以不要只看它，直接确认；缺了就手工放一份：

```bash
docker exec jenkins cat /var/jenkins_home/.m2/settings.xml   # 应看到阿里云镜像配置

# 若上一条报 No such file，手工补：
docker exec -u root jenkins sh -c \
  'mkdir -p /var/jenkins_home/.m2 && \
   cp /usr/share/jenkins/ref/.m2/settings.xml /var/jenkins_home/.m2/settings.xml && \
   chown -R jenkins:jenkins /var/jenkins_home/.m2'
```

漏了这一步不会构建失败，但 Maven 会走官方 central，在 2C4G 上拉依赖会明显变慢。

若 Job 与插件是新建的，安装：`Pipeline`、`Git`、`Docker Pipeline`、`Credentials Binding`、`Workspace Cleanup`。

### 4. 新建 Job

New Item → **Pipeline** → Pipeline script from SCM：

| 项 | 值 |
|---|---|
| SCM | Git |
| Repository URL | `https://gitee.com/Sc0102_1129/check-repeat-service.git` |
| Credentials | Gitee 用户名密码或私人令牌 |
| Branch Specifier | `*/master` |
| Script Path | `Jenkinsfile` |

构建触发器勾 **Generic Webhook Trigger**，在展开区的 Token 里填一个自定字符串（**不要写进 Jenkinsfile**，避免泄露到版本库），其余字段（Post content parameters、Optional Filter 等）全部留空。Gitee 侧的 Push webhook 指向这个 token，见 §5。

> **不要启用 Poll SCM。** Pipeline script from SCM 默认勾选 `Lightweight checkout`（`config.xml` 里 `<lightweight>true</lightweight>`），此时 Jenkins 无法为轮询记录稳定的 revision，**每次轮询都判定"有变更"**——实测 `H/5 * * * *` 曾在一小时内按 5 分钟间隔盲目重建 10 次（每次 50-60 秒服务中断）。若确实要恢复轮询，必须先取消 `Lightweight checkout`。

### 5. 用 Gitee Webhook 触发（秒级）

Jenkins 自身只绑回环（见 §2），但网关为它开了一个专用 vhost：`/opt/check-repeat/nginx/conf/jenkins.d/10-jenkins.conf` 里 `server_name jenkins.check-repeat-system.xyz;` 的块**只放行 `/generic-webhook-trigger/`**（`location /` 为 `deny all`），该路径反代到 `http://jenkins:8080`（Jenkins 容器在 docker 网络内的名字）。因此 **Generic Webhook Trigger 可以直接用**，既不需要给 Jenkins 加 `--prefix`，也不需要改这份 nginx 配置。

1. Job 构建触发器勾 **Generic Webhook Trigger**，Token 填自定字符串，其余留空；同时**不要**再勾 Poll SCM（两者并存会带来无谓的轮询构建）
2. Gitee 仓库 → 管理 → WebHooks → 添加，URL 填 `https://jenkins.check-repeat-system.xyz/generic-webhook-trigger/invoke?token=<你的token>`，事件勾 **Push**
3. 推一次 `master` 验证：构建原因应显示 `Generic Cause`，而不是 `Started by an SCM change`

> **不要**试图用安全组限制来源 IP 来保护这个端点——webhook 请求来自 Gitee 的服务器，不是你的办公 IP，限制 IP 只会把 webhook 一起挡掉。
>
> Token 是唯一凭据，且默认不做分支过滤：**推任何分支都会触发构建**（流水线仍只检出 `master`），代价是一次无谓的重建。需要过滤时在触发器的 `Optional Filter` 里加 regexp。

## 三、首次部署

中间件**已经在运行**（由 `/opt/check-repeat/docker-compose.yml` 管理），不需要也不应该在本仓库的 compose 里再起一遍——那会创建空数据卷，让站点看起来正常但数据全空。

直接手动触发一次 Jenkins Job。它会：Maven 打包 → 构建镜像 → 同步部署文件 → 调用 `deploy.sh <构建号>` → 健康检查。

首次部署后重点核对两件事：

```bash
# 1. 数据可见：既有上传文件与报告应在容器内 /data 下
docker exec check-backend ls -l /data/upload | head

# 2. 结构就绪：库里的表结构应与 db/init.sql 一致
docker exec -it check-repeat-mysql mysql -uroot -p -e \
  "show tables from \`check-repeat-system\`" | head
```

## 四、反向代理

后端 context-path 是 `/check`，健康检查为 `/check/actuator/health`。

**若前端容器与本后端同处 `check-repeat_app-network`**，直接按容器名代理，无需暴露宿主端口：

```nginx
location /check/ {
    proxy_pass http://check-backend:8080;      # 结尾不加 /，保留 /check 前缀
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 200m;                 # 对应后端 multipart 上限
    proxy_read_timeout 3600s;                  # 对应 check.task.timeout
}
```

**若用宿主机 Nginx**，把 `proxy_pass` 改成 `http://127.0.0.1:8080`（backend 只绑回环）。

Jenkins 控制台走 SSH 隧道访问（`ssh -L 8081:127.0.0.1:8081 root@<服务器IP>`）——它只绑 `127.0.0.1`，安全组根本不需要放行 8081。不要用 `/jenkins/` 子路径反代——那需要给 Jenkins 加 `--prefix` 参数，很折腾。

## 五、日常部署与回滚

推送 `master` 后由 Gitee webhook 立即触发。若本次提交改动的文件**全部是 `.md` 文档**，流水线会跳过打包与部署——避免纯文档提交换来一次 50-60 秒的服务中断。判定采用 fail-safe 策略：取不到变更集时一律按"需要部署"处理。

流水线：`mvn package` → `docker build` → 同步部署文件到 `/usr/local/check-repeat` → 调用 `deploy.sh <构建号>`。

`deploy.sh` 的行为：

1. 从 `.env` 读出当前 `BACKEND_IMAGE` 作为回滚点
2. 把新 tag 写入 `.env`
3. `docker compose up -d backend` —— **只重建 backend 一个服务**（现在也只定义了这一个）
4. 循环 `docker exec check-backend curl .../actuator/health` 探测，最多 150 秒
5. 失败则写回旧 tag 并重新 `up -d backend`，然后以非 0 退出
6. 成功后只保留最近 3 个数字 tag 的镜像

手动回滚到指定构建号：

```bash
cd /usr/local/check-repeat && ./deploy.sh 41
```

## 六、运维命令速查

```bash
# ---- 后端（本项目，项目名 check-repeat-backend）----
cd /usr/local/check-repeat
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml up -d backend
docker images check-repeat-service

# ---- 中间件（既有栈，项目名 check-repeat，务必用 -f 指向 /opt 那份）----
docker compose -f /opt/check-repeat/docker-compose.yml ps
docker compose -f /opt/check-repeat/docker-compose.yml logs -f mysql

docker stats --no-stream          # 各容器实际内存占用
free -h && swapon --show          # 内存与 swap 水位
docker exec -it check-repeat-mysql mysql -uroot -p     # 进数据库
```

## 七、注意事项

- **永远不要执行 `docker compose down`**，两侧都不要。它会连带停掉同项目容器，中间件那份更会直接导致全站停机。要重启只操作 `backend` 单个服务。
- **不要给 `docker compose` 加 `-v`**，会删除所有数据卷（数据库、上传文件、报告全部丢失）。
- **不要在两个目录间混用 `-f`**，也不要把 backend 的 compose 文件复制到 `/opt/check-repeat/`。两套栈靠项目名隔离，混用会破坏这个隔离。
- **改中间件必须用 `-f /opt/check-repeat/docker-compose.yml`**，不要在 `/usr/local/check-repeat` 目录下裸跑 `docker compose`（会命中 backend 那份）。
- **`/data` 只能挂一个卷**（`check-repeat_app_data`）。yml 里 uploads / reports / check-temp / review-attach 的默认路径都在 `/data` 下；若改成多个独立卷挂到 `/data/upload` 等子路径，既有历史文件会不可见。
- 表结构由 `db/init.sql` 手工执行维护，见下方「八、数据库结构」。
- 数据卷为 Docker 命名卷，`up -d backend` 不会影响数据。备份时请针对 `check-repeat_mysql_data`、`check-repeat_app_data` 等卷操作。
- 后端日志落在容器 `/app/logs`（已挂 `check-repeat_app_logs` 卷），容器重建不丢。
- 后端 OOM 时会在 `/data/dumps`（`dumps` 卷）留下堆转储，排查内存问题先看这里。
- 本地开发构建镜像前必须先 `mvn package`（`Dockerfile` 从 `target/` 取 jar）。
- **`.env` 中的密钥切勿提交，也不要贴进聊天/工单**。既有部署曾把真实口令作为默认值写进 `application.yml`，这是反面教材。

## 八、数据库结构

表结构由**手工执行** `src/main/resources/db/init.sql` 维护，脚本已与实体类对齐。项目不使用迁移框架。

- **新搭环境**：建好空库后执行一次 `db/init.sql`，即可建齐全部表（脚本开头是 `DROP TABLE IF EXISTS`，空库直接跑）。再执行一次 `db/seed_knowledge_and_message_template.sql` 补初始数据（见下条）。
- **种子数据**：`db/seed_knowledge_and_message_template.sql` 只有 INSERT、没有 DDL，用 `ON DUPLICATE KEY UPDATE` 做成幂等，**新旧库都能反复执行**。它提供知识库分类，以及代码里硬编码引用的 11 个消息模板编码——**不执行它，论文状态变更 / 审核结果 / 查重完成 / 指导分配这些通知都会因「消息模板不存在」而发送失败**（`message_template` 表为空时 `MessageServiceImpl.sendMessageByTemplate` 一律返回该错误）。
- **改结构**：编辑 `db/init.sql` 保持一致，**再另出一份只含本次变更的 ALTER 脚本**手工对线上库执行——整份脚本含 `DROP TABLE IF EXISTS`，有数据的库不能重跑。
- **`create_time` 的库默认值现在是「承重」的**：`sys_notice`、`system_config`、`auto_assignment_config`、`auto_assignment_history` 的插入不再由代码赋值，改由 `DEFAULT CURRENT_TIMESTAMP` 填充（实体上去掉了 `fill = INSERT`，理由见 `SysNotice.createTime` 注释）。后续 ALTER **不要去掉这个默认值**，否则插入会写入 NULL。例外：`file_info.create_time` 是 `DEFAULT NULL`，其插入路径仍显式赋值，不能照搬这条约定。

### 库名

生产库是 **`check-repeat-system`**（注意不是 `check-repeat-service`）。代码里库名已参数化：

```
jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE:check-repeat-service}
```

由 `.env` 的 `MYSQL_DATABASE=check-repeat-system` 覆盖。

### 结构以谁为准

`db/init.sql` 是「旧一体化系统导出的库结构 + 按实体类和 Mapper XML 校正」后的结果，线上库已按它对齐。三方（实体类 / Mapper XML / init.sql）出现不一致时，**先判断方向、不要默认哪边写错了**：历史上就出现过「实体按合并后的结构写、而库还是旧结构」这种整体漂移，当时是按代码方向改的库。

动手改结构前，先导出线上实际结构做对照：

```bash
docker exec check-repeat-mysql sh -c \
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --no-data --skip-comments "check-repeat-system"' \
  > /root/schema-$(date +%F).sql
```

### 变更必须兼容新旧两个版本

这是由 `deploy.sh` 的自动回滚决定的：DDL 一旦执行就无法撤销，而健康检查失败会回滚应用镜像，于是旧代码会运行在新结构上。所以：

- 加表、加**可空**字段、加索引 → 随时可以
- **删表、删字段、改字段类型、重命名字段、加 NOT NULL 约束 → 必须拆成两次发布**：先发一个不再读写该字段的版本，下个版本再真正删除

### 大表 DDL 先手工做

healthcheck 的 `start_period` 是 120 秒。大表加索引这类耗时操作会让启动变慢、可能被误判为部署失败并触发回滚，建议上线前手工先执行。

### 本轮上线前必须手工执行的两条 SQL

顺序不能颠倒：**先 DDL，再 seed**，两条都在发版之前完成。

```bash
# 1. 给 sys_operation_log 加「执行耗时」列。
#    不执行的话，日志中心「统计分析」页的四个接口会全部 500：
#    Unknown column 'execution_time' in 'field list'。
#    列可空，旧代码忽略它，所以「先 DDL 后发版」对新旧两个版本都安全。
docker exec -i check-repeat-mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "check-repeat-system"' \
  < src/main/resources/db/alter_20261003_sys_operation_log_execution_time.sql

# 2. 补操作类型字典里缺的 5 个 type（幂等，含 ON DUPLICATE KEY UPDATE，可重复执行）
docker exec -i check-repeat-mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "check-repeat-system"' \
  < src/main/resources/db/seed_sys_operation_type.sql
```

执行后自检：

```sql
SHOW COLUMNS FROM `sys_operation_log` LIKE 'execution_time';  -- 应返回一行
SELECT COUNT(*) FROM `sys_operation_type`;                     -- 应 > 0
```

**为什么 seed 是必须的**：`sys_operation_type` 此前**不是空表**（实际有 134 行），
但日志里出现的操作类型并不都在字典里。「模块使用率」饼图靠
`LEFT JOIN sys_operation_type` 取模块名，字典里没有的 type 会落到 `'未知'`。
截至 2026-10-03，2369 行日志中有 83 行（3.5%）未命中，恰好是
`admin_auto_assign`、`check_task_create`、`check_task_delete`、`user_update_info`、
`teacher_update_info` 这 5 个；补齐后覆盖率为 100%。seed 只补这 5 个，
其余 2286 行已由字典中既有条目命中，不改动。（另注：`init.sql` 里
`sys_operation_type` 只有建表、没有 INSERT，所以新建空库时这张表是空的。）

**不要删模块统计 JOIN 上的 `COLLATE`**：两张表的排序规则不同
（`sys_operation_log` 是 `utf8mb4_0900_ai_ci`，`sys_operation_type` 是
`utf8mb4_unicode_ci`），`SysOperationLogMapper.xml` 里的
`ON t.type COLLATE utf8mb4_0900_ai_ci = l.operation_type` 是必需的，
去掉会抛 `Illegal mix of collations ... for operation '='` 使接口 500。

### 定时任务开关（本轮新增，默认全关）

本轮给 `CheckRepeatServiceApplication` 补上了 `@EnableScheduling`。在此之前
应用上**没有**这个注解，因此 `CheckTaskScheduler` 与 `DataIntegrityChecker` 里的
`@Scheduled` 方法虽然一直存在，却从未执行过。补上注解后它们会立即开始生效，
其中两个会按库里的历史数据改动数据。为了不让「上线」成为它们第一次运行，
这几个开关默认全部关闭，确认过行为再逐个打开：

| 配置项 | 默认 | 打开后的行为 |
|---|---|---|
| `schedule.check-task.timeout-scan-enabled` | `false` | **启动即执行**，之后每 5 分钟一次：把 `check_status='checking'` 且已超 1 小时的任务标记为失败（**写库**） |
| `schedule.check-task.cleanup-enabled` | `false` | 每天 2:00：清理 30 天前的已完成/失败/取消任务（当前实现只打日志，未真删除） |
| `schedule.data-integrity.enabled` | `false` | 每天 3:00：插入缺失的查重结果、改写论文的重复率与完成标记（**写库**） |

第一项是 `fixedRate`，**启动时就会跑第一次**，不是等 5 分钟——所以「先启动看看、
有问题再关」这个顺序不可行，要关必须在启动前关。打开前建议先查影响面：

```sql
SELECT COUNT(*) FROM `check_task`
 WHERE check_status = 'checking' AND start_time < DATE_SUB(NOW(), INTERVAL 1 HOUR);
```

`AutoBackupScheduler` 不在这张表里：它由「系统配置」页的
`system_param.backup_enabled` 控制（每天 2:00 执行），属本轮新功能，随页面开关走。

### 备份文件的落点

备份写在 `${BACKUP_DIR}`，生产为 `/data/backup/`。该路径已在
`docker-compose.prod.yml` 的 `app_data:/data` 卷内（`app_data` 是 external 的既有卷
`check-repeat_app_data`），**compose 无需改动**。导出用纯 JDBC 实现，不依赖
容器内的 `mysqldump`（生产镜像 `eclipse-temurin:17-jre` 里没有）。
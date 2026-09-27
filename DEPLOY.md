# 生产部署手册

面向 **2C4G 阿里云 ECS · Gitee + Jenkins(容器) + Docker Compose** 的部署流程。

## 架构

```
Gitee ──Webhook──► Nginx ──► Jenkins(容器:8090) ──► mvn package
                                     │
                                     └──► docker build  (经 docker.sock)
                                     └──► docker compose up -d backend
                                     └──► 健康检查 → 失败自动回滚

服务器
├── Nginx (宿主 80/443)      ──► 127.0.0.1:8080  后端
│                            └──► 127.0.0.1:8090  Jenkins(仅 webhook 路径)
├── check-backend  容器  127.0.0.1:8080   镜像本地构建，tag = Jenkins 构建号
├── check-mysql    容器  127.0.0.1:3306
├── check-redis    容器  127.0.0.1:6379
├── check-minio    容器  127.0.0.1:9000/9001
└── /usr/local/check-repeat/   部署目录(.env / docker-compose.prod.yml / deploy.sh)
```

**不使用外部镜像仓库。** 镜像只在本机构建并打上构建号 tag，保留最近 3 个用于回滚。
原因：ACR 个人版官方声明「仅限开发测试、禁止生产、无 SLA」，而 Jenkins 与后端同机时仓库本就是多余的中间环节。

## 内存预算（4GB 总量，勿随意新增服务）

| 组件 | mem_limit | 关键调参 |
|---|---|---|
| OS + Docker daemon | — | 约 400MB |
| mysql | 768m | `innodb_buffer_pool_size=256M`、关 `performance_schema`、关 binlog |
| redis | 192m | `maxmemory 96mb` + `allkeys-lru` |
| minio | 384m | `GOMEMLIMIT=256MiB` |
| backend | 1024m | `-Xmx512m -XX:MaxMetaspaceSize=192m` |
| jenkins 容器 | 1536m | Jenkins JVM `-Xmx384m` + Maven JVM `-Xmx512m` |

构建时峰值约 3.4GB。**4GB swap 是必需的**，否则一次内存抖动就会触发 OOM killer 杀掉 MySQL 或后端。

## 一、服务器准备

### 1. swap（必做）

```bash
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
echo 'vm.swappiness=10' >> /etc/sysctl.conf && sysctl -w vm.swappiness=10
```

### 2. 部署目录与 .env

```bash
mkdir -p /usr/local/check-repeat
cd /usr/local/check-repeat
# 参照仓库根目录 .env 模板填写真实密钥，额外补一行：
#   BACKEND_IMAGE=check-repeat-service:latest
chmod 600 .env
```

`.env` 不进版本库（已在 `.gitignore` 中）。`docker-compose.prod.yml` 在**首次部署前**需要先放一份到这里：

```bash
git clone https://gitee.com/Sc0102_1129/check-repeat-service.git /tmp/crs
cp /tmp/crs/docker-compose.prod.yml /tmp/crs/deploy.sh /usr/local/check-repeat/
chmod +x /usr/local/check-repeat/deploy.sh
```

### 3. 安全组

| 端口 | 策略 |
|---|---|
| 22 / 80 / 443 | 放行 |
| 8090（Jenkins） | 只放行你的办公 IP，或走 SSH 隧道 |
| 50000 | 不需要放行（单机 Jenkins，无 agent） |
| 3306 / 6379 / 9000 / 9001 / 8080 | **禁止对公网开放**，已绑定 127.0.0.1 |

## 二、Jenkins

### 1. 构建自定义镜像

官方 `jenkins/jenkins:lts-jdk17` 里没有 docker CLI、compose 插件和 Maven，必须用 `jenkins/` 下的 Dockerfile 重建。

```bash
cd /tmp/crs
docker build -t jenkins-custom:lts-jdk17 jenkins/
```

### 2. 启动容器

```bash
docker volume create jenkins_home

docker run -d --name jenkins --restart unless-stopped \
  -p 8090:8080 -p 50000:50000 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /usr/local/check-repeat:/usr/local/check-repeat \
  -v jenkins_home:/var/jenkins_home \
  --group-add "$(getent group docker | cut -d: -f3)" \
  -e JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=192m" \
  -e TZ=Asia/Shanghai \
  --memory 1536m \
  jenkins-custom:lts-jdk17
```

三个关键点，漏一个就会出问题：

1. **`--group-add`**：让容器内 `jenkins` 用户能读写宿主 `docker.sock`，否则构建阶段全部 permission denied。
2. **`--memory 1536m`**：必须同时容纳 Jenkins JVM（384m）和它拉起的 Maven JVM（512m）。给少了构建时容器会被 OOM。
3. **`-v /usr/local/check-repeat:/usr/local/check-repeat` 路径必须完全一致**。Docker-out-of-Docker 下，`docker compose` 的路径是由**宿主 daemon** 解析的；路径不一致，compose 里的 bind mount 会指向容器内不存在的目录，部署静默失败。

### 3. 初始化

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
docker exec jenkins cat /var/jenkins_home/.m2/settings.xml   # 应看到阿里云镜像配置
```

安装插件：`Pipeline`、`Git`、`Generic Webhook Trigger`、`Docker Pipeline`、`Credentials Binding`、`Workspace Cleanup`。

### 4. 新建 Job

New Item → **Pipeline** → Pipeline script from SCM：

| 项 | 值 |
|---|---|
| SCM | Git |
| Repository URL | `https://gitee.com/Sc0102_1129/check-repeat-service.git` |
| Credentials | Gitee 用户名密码或私人令牌 |
| Branch Specifier | `*/master` |
| Script Path | `Jenkinsfile` |

构建触发器选 **Generic Webhook Trigger**，自行设置一个 Token（不要写进 Jenkinsfile，避免泄露到版本库）。

### 5. Gitee Webhook

Gitee 仓库 → 管理 → Webhook，URL 填：

```
http://<域名>/generic-webhook-trigger/invoke?token=<你的token>
```

## 三、首次部署

backend 镜像还不存在，先起中间件：

```bash
cd /usr/local/check-repeat
docker compose -f docker-compose.prod.yml up -d mysql redis minio minio-init
```

然后在 Jenkins 里手动触发一次 Job，它会构建镜像并部署 backend。

## 四、Nginx

```nginx
location /check/ {
    proxy_pass http://127.0.0.1:8080;          # 结尾不加 /，保留 /check 前缀
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 200m;                 # 对应后端 multipart 上限
    proxy_read_timeout 3600s;                  # 对应 check.task.timeout
}

# 只暴露 webhook 路径，Jenkins 控制台不要裸奔
location /generic-webhook-trigger/ {
    proxy_pass http://127.0.0.1:8090;
}
```

Jenkins 控制台建议走安全组限制来源 IP 或 SSH 隧道访问。不要用 `/jenkins/` 子路径反代——那需要给 Jenkins 加 `--prefix` 参数，很折腾。

## 五、日常部署与回滚

推送 `master` 即自动触发。流水线：`mvn package` → `docker build` → 同步部署文件到 `/usr/local/check-repeat` → 调用 `deploy.sh <构建号>`。

`deploy.sh` 的行为：

1. 从 `.env` 读出当前 `BACKEND_IMAGE` 作为回滚点
2. 把新 tag 写入 `.env`
3. `docker compose up -d backend` —— **只重建 backend 一个服务**
4. 循环 `docker exec check-backend curl .../actuator/health` 探测，最多 150 秒
5. 失败则写回旧 tag 并重新 `up -d backend`，然后以非 0 退出
6. 成功后只保留最近 3 个数字 tag 的镜像

手动回滚到指定构建号：

```bash
cd /usr/local/check-repeat && ./deploy.sh 41
```

## 六、运维命令速查

```bash
cd /usr/local/check-repeat

docker compose -f docker-compose.prod.yml ps                 # 服务状态
docker compose -f docker-compose.prod.yml logs -f backend     # 后端日志
docker compose -f docker-compose.prod.yml up -d backend       # 只重启后端
docker stats --no-stream                                      # 各容器实际内存占用
docker images check-repeat-service                            # 本地可用版本
docker exec -it check-mysql mysql -uroot -p                   # 进数据库
free -h && swapon --show                                      # 内存与 swap 水位
```

## 七、注意事项

- **永远不要执行 `docker compose down`**，那会连带停掉 MySQL/Redis 造成全站停机。要重启只操作 `backend` 单个服务。
- **不要加 `-v`**，会删除所有数据卷（数据库、上传文件、报告全部丢失）。
- **表结构由 Flyway 管理**，见下方「八、数据库迁移」。
- 数据卷为 Docker 命名卷，`up -d backend` 不会影响数据。备份时请针对 `mysql-data`、`upload-data`、`report-data` 等卷操作。
- 后端日志落在容器的 `/app/logs`，已挂到 `app-logs` 卷，容器重建不丢。
- 后端 OOM 时会在 `/data/dumps`（`dumps` 卷）留下堆转储，排查内存问题先看这里。
- 构建期间 2C4G 机器上后端会明显变慢，属预期现象；Jenkins 已设 `disableConcurrentBuilds()` 防止叠加。
- 本地开发构建镜像前必须先 `mvn package`（`Dockerfile` 从 `target/` 取 jar）。

## 八、数据库迁移

表结构由 **Flyway** 管理，迁移脚本在 `src/main/resources/db/migration/`，应用启动时自动执行。

- `V1__init.sql` 是历史 mysqldump 转入的基线（结构建表，无数据）
- 命名规则：`V<版本>__<描述>.sql`，如 `V2__add_paper_review_comment.sql`。版本号必须递增且**已执行过的文件不能再改**，否则校验和不匹配会导致启动失败（要改就新增一个 V 版本）
- 执行记录在 `flyway_schema_history` 表

**已有数据的库如何接管**：`application.yml` 里配了 `baseline-on-migrate=true` + `baseline-version=1`。首次启动时 Flyway 会在非空库上打基线，把现状记为「V1 已应用」，不重复执行 V1；之后从 V2 开始应用。全新空库不会触发基线，会正常从 V1 建表。

**⚠️ 迁移必须兼容新旧两个版本**——这是由 `deploy.sh` 的自动回滚决定的：迁移一旦执行就已提交且无法撤销（Flyway 社区版没有 undo），而健康检查失败会回滚应用镜像，于是旧代码会运行在新表结构上。所以：

- 加表、加**可空**字段、加索引 → 随时可以
- **删表、删字段、改字段类型、重命名字段、加 NOT NULL 约束 → 必须拆成两次发布**：先发一个不再读写该字段的版本，下个版本再真正删除

**重迁移注意**：迁移跑在应用启动阶段，而 healthcheck 的 `start_period` 是 120 秒。大表加索引这类耗时操作可能导致健康检查超时、被误判为部署失败并触发回滚。这类迁移建议上线前手工先执行，再走正常部署。

手工查看迁移状态：

```bash
docker logs check-backend | grep -i flyway
docker exec -it check-mysql mysql -uroot -p -e \
  "select version, description, success, installed_on from \`check-repeat-service\`.flyway_schema_history order by installed_rank"
```
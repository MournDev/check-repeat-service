# 系统架构隐患评估报告

> 审查对象：`check-repeat-service`（Spring Boot 3.5.7 + MyBatis-Plus + MinIO + kkFileView）
> 审查范围：文件存储与预览链路、查重核心链路、配置与可观测性
> 审查方式：全量代码扫描（含 `application*.yml`、`docker-compose.prod.yml`、`DEPLOY.md`），结论均标注到类/方法/行号
> 审查日期：2026-10-25

---

## 0. 结论速查（对应本次提出的 9 个问题）

| # | 问题 | 结论 | 关键证据 |
|---|---|---|---|
| 1 | 预览 URL 是永久公开还是签名临时链接 | **临时 HMAC 链接（10 分钟）**，但不是 MinIO 预签名 URL；令牌只绑定资源 ID，不绑定用户/会话，拿到即可用 | [PreviewSignedUrlService.java](file:///d:/check-repeat-system/check-repeat-service/src/main/java/com/abin/checkrepeatservice/common/jwt/PreviewSignedUrlService.java#L54-L93) |
| 2 | 前端能否篡改参数预览别人的论文 | **能**。`/api/file/preview-url/{fileId}` 只要求登录，**没有做任何归属校验**就直接签发令牌；`fileId` 又是毫秒时间戳，可枚举 | [FileUploadController.java](file:///d:/check-repeat-system/check-repeat-service/src/main/java/com/abin/checkrepeatservice/common/controller/FileUploadController.java#L245-L248)、[FileService.java](file:///d:/check-repeat-system/check-repeat-service/src/main/java/com/abin/checkrepeatservice/common/service/FileService.java#L177-L179) |
| 3 | MinIO Bucket 是 Private 还是 Public | 代码里**没有任何预签名调用**（全仓 0 处 `getPresignedObjectUrl`），头像/正文 URL 都是“裸对象 URL”直接存库；[InfoServiceImpl 注释](file:///d:/check-repeat-system/check-repeat-service/src/main/java/com/abin/checkrepeatservice/student/service/Impl/InfoServiceImpl.java#L377-L378)明确要求“建桶并开放匿名读” → **实际部署必然是 Public-Read 桶** | 全仓 grep 无预签名；`InfoServiceImpl#uploadAvatar` L352-370 |
| 4 | kkFileView 回源地址是内网还是外网 | **内网容器别名 + 明文 HTTP**：`http://check-backend:8080/check/api/file/preview-fetch/...`；浏览器侧则走公网 HTTPS 网关 `/kkfileview/` | [FilePreviewServiceImpl.java](file:///d:/check-repeat-system/check-repeat-service/src/main/java/com/abin/checkrepeatservice/common/service/Impl/FilePreviewServiceImpl.java#L508-L526)、[application-prod.yml](file:///d:/check-repeat-system/check-repeat-service/src/main/resources/application-prod.yml#L16-L24) |
| 5 | 回源会不会不通 / 跨域 | 回源是**服务端拉取**，不存在 CORS；但强依赖 `check-backend` 与 kkFileView 在同一 docker 网络、且 kkFileView 的 `trust.host` 放行该主机。**任一处没配对，预览 100% 失败**。另有每请求同步健康探测（最多阻塞 30s） | FilePreviewServiceImpl L451-463 / L485-495 |
| 6 | kkFileView 是否与业务打包 | **独立容器**（`check-repeat-kkfileview`，512MB，内含 LibreOffice），业务容器 1152MB/-Xmx768m；MinIO 也独立 | [DEPLOY.md](file:///d:/check-repeat-system/check-repeat-service/DEPLOY.md#L39-L53) |
| 7 | 上传是流式到 MinIO 还是先落本地 | **既不是预签名 PUT，也没有直传 MinIO**。全部落到本地 `/data/upload`，且中间经历：Tomcat 临时目录 → `byte[]`（堆）→ 再次写盘；Tika 全量解析 **2 次** | `FileService#uploadFile` L84-112、L244-274 |
| 8 | Tika 魔数校验在上传前还是上传后 | `/api/file/upload` 是**上传前**（先 `validateFile` 再写盘）；但**消息附件、头像等入口绕过了该校验**；管理端 MinIO 上传只校验扩展名 | FileUploadController L100、`StudentMessageServiceImpl#uploadFile` L362-386 |
| 9 | 查重同步还是异步 / 第三方 HTTP 连接池 | **异步**（`@Async("checkTaskExecutor")` + `AFTER_COMMIT`）。第三方引擎**未实现**（`isImplemented()=false`），不存在第三方 HTTP 客户端；唯一的 HTTP 客户端是 kkFileView 健康探测的 `RestTemplate`，**无连接池** | `CheckTaskEventListener` L131-143、`ThirdPartyCheckEngine` L51-54、FilePreviewServiceImpl L72-79 |
| 10 | 分布式锁粒度 / 下载打包并发 | 状态流转用条件更新（正确）；超时扫描用 Redisson 全局锁（正确）；**“查重次数/间隔”校验存在 TOCTOU**，并发可绕过；下载为流式无锁需求，但批量导出/打包有内存与 ZIP 名注入问题 | `CheckTaskValidationService` L48-108、`AsyncConfig` L47、`AdminReportServiceImpl` L322-375 |
| 11 | 密钥是否硬编码 | 生产凭据走环境变量且有 fail-fast 校验；但仍有 **prod 配置里的 `REDIS_PASSWORD:123456` 兜底**、代码里的开发盐默认值、dev 的 `minioadmin`，且**历史提交已泄露、必须轮换** | `application-prod.yml` L119-127、`ProductionSecretValidator` L31-41 |
| 12 | 链路追踪 / TraceId | **没有**。全仓无 `MDC`/`traceId`，logback pattern 无请求标识；跨后端↔MinIO↔kkFileView 无法串联 | `logback-spring.xml` L6/L25、全仓 grep `MDC|traceId` 无命中 |

---

## 一、高危隐患（数据泄露 / 服务雪崩 / 单点故障）

### H1【P0】预览地址签发接口存在 IDOR，可越权预览/下载任意论文

**位置**：`FileUploadController#getPreviewUrl`（L245-248）→ `FilePreviewServiceImpl#getPreviewInfo`（L262-277）

```java
@GetMapping("/preview-url/{fileId}")
public Result<Map<String, String>> getPreviewUrl(@PathVariable Long fileId) {
    return Result.success(filePreviewService.getPreviewInfo(fileId));   // 无归属校验
}
```

对照同类的 `smartPreview`（L337-351）已经写了 `canAccessFile`，唯独这个“新入口”漏了。`getPreviewInfo` 内部只判断“文件是否存在”，随后：

- 图片/PDF/文本 → 签发 `pt`（HMAC，10 分钟）并返回 `/check/api/file/preview-fetch/{fileId}/{name}?pt=...`；
- Office → 返回 kkFileView 预览地址（其内部回源 URL 同样携带 `pt`）。

而 `preview-fetch/**` 在安全链上是 `permitAll`（`SecurityConfig` L129/L133），令牌是**纯 bearer 凭据**：不绑定 userId、不绑定 IP、可重放、可外传。因此攻击链为：

```
任意学生登录 → 枚举 fileId → GET /api/file/preview-url/{fileId}
            → 拿到 10 分钟有效签名 URL → 浏览器直接下载他人论文/附件
```

**放大因素**：`FileService#generateFileId()` 用 `System.currentTimeMillis()` 生成主键（L177-179），既是**可预测的顺序 ID**（按时间窗口暴力枚举成本极低），又会在并发上传时撞主键。这把 IDOR 从“知道 ID 才能打”降级成“扫时间戳就能打”。

### H2【P0】教师角色对文件是全量放行，横向越权没有边界

**位置**：`FileUploadController#canAccessFile`（L459-472）

```java
if (UserBusinessInfoUtils.isTeacher()) {
    return true;      // 任意教师 → 任意学生的任意文件
}
```

`getFileInfo`、`downloadFile`、`smartPreview` 全部走这个方法。也就是说 A 学院、B 导师的教师账号可以下载全校所有论文与附件。对比 `canAccessPaper`（L479-493）已经实现了“仅本人指导的论文”，说明“教师需按指导关系收敛”的规则在报告链路是成立的，但**文件链路没有落地**。与 H1、H3 组合后，构成一条完整的批量拖库路径。

### H3【P0】fileId 可枚举 + MD5 秒传，构成“论文指纹 → 文件”反查通道

**位置**：`FileUploadController#checkFileByMd5`（L137-146）、`#uploadFile`（L110-116）

- `/api/file/check?md5=` 任何登录用户可查：命中即返回 `fileId / 文件名 / 大小 / 上传时间 / md5`（`buildFileUploadResponse`）。
- `/upload` 的“秒传”命中后，直接把**别人的** `fileId` 返回给当前用户，且不校验归属；随后 `submitPaperByFileId` → `validateFileInfo`（`PaperInfoServiceImpl` L570-589）只校验“文件存在 + MD5 一致”，**不校验上传人**。

后果有两个层面：一是可借助已知文档的 MD5 反查出 `fileId`，再走 H1 下载；二是学生可以把他人成果直接挂到自己名下提交查重（学术诚信与数据归属双双失守）。

### H4【P0】MinIO 采用 Public-Read 裸对象 URL，且无预签名能力

**位置**：`InfoServiceImpl#uploadAvatar`（L352-370）、`PaperContentMinioService`（L42-73 / L81-101）

```java
String avatarUrl = minioPublicEndpoint + "/" + avatarBucket + "/" + uniqueFileName;
...
log.error("...需先在 MinIO 上创建该桶并开放匿名读...");   // L377
```

全仓检索无 `getPresignedObjectUrl` / `setBucketPolicy`。这意味着：

- 桶必须被运维设置为 **anonymous read** 才能让前端 `<img>`、正文链接可用；
- 一旦开放匿名读，`paper-content` 桶内的论文正文（`papers/content/{paperId}/{uuid}.txt`）就是**无鉴权的永久 URL**。UUID 降低了遍历可行性，但 URL 被写进 `paper_info.content_path`，任何一次 SQL 注入/日志/接口泄露都会变成永久有效的正文下载；
- 桶策略不在代码里（Infrastructure as Code 缺失），新环境极易“为了头像能显示”把整个 MinIO 开成匿名读。

**附带 SSRF 白名单洞**：`SafePreviewUrlValidator` 会把 `minio.public-endpoint` 的 host 自动并入白名单（L43-60）。而该配置的回退值是 `${MINIO_ENDPOINT}`（内网别名 `minio`）。一旦线上漏配 `MINIO_PUBLIC_ENDPOINT`，内网主机名 `minio` 就成了预览白名单成员，`/api/file/onlinePreview?url=http://minio:9000/...` 可直连内网对象存储。

### H5【P0】上传链路把整包读进堆、Tika 全量解析两次，叠加 200 线程 → 必然 OOM

**位置**：`FileUploadController#uploadFile`（L106）、`FileService#uploadFile`（L93-103）、`FileService#saveBasicFileInfo`（L264-272）、`#asyncCountAndUpdateWordsFromBytes`（L120-147）

单次上传的实际开销：

1. Spring 未配 `file-size-threshold`（默认 0）→ multipart **先整包写 Tomcat 临时目录**（`/tmp`，overlay 盘）；
2. 控制器 `file.getBytes()` → **整包进堆**（最大 50MB，`max-request-size` 却给了 200MB）；
3. `FileService#uploadFile` 再 `file.getBytes()` 一次 → **同一文件在堆里存在两份**；
4. `saveBasicFileInfo` 里**同步**执行 `tika.parseToString(file)`（Word/PDF 全文解析，CPU 重）；
5. 紧接着 `asyncCountAndUpdateWordsFromBytes` 又 `parseToString(byte[])` **解析第二遍**。

线上 `-Xmx768m`、`server.tomcat.threads.max=200`（`application.yml` L7-10）。只要 20~30 个并发上传 50MB 论文，堆就会被 `byte[]` 打穿（`HeapDumpOnOutOfMemoryError` 只是留证据，不解决问题），容器被 OOM Killer 杀掉，进行中的查重全部丢失。

另有两处不一致需要一并收口：`FileValidationService` 允许 200MB（L23）而 multipart 只允许 50MB；`check.task.temp-file-path`、`paper-check.parse-timeout` 是**死配置**，全仓无读取点——也就是说 **Tika 解析没有超时**，一个精心构造的 docx（压缩炸弹）可以让查重线程无限期卡住（`CheckTaskEventListener` 只在解析前检查一次超时，L161）。

### H6【P0】检测缓存容量默认 1024MB > 堆 768MB，且管理端可调到 10GB

**位置**：`DetectionCacheService`（L79-80、L99-118）、`PerformanceConfigDTO`（L41-45）

```java
private volatile long maxWeightBytes = 1024L * 1024 * 1024;  // 1GB
```
`PerformanceConfigDTO.cacheSize` 默认 1024，校验上限 10000（10GB）。
而生产堆是 `-Xmx768m`（`docker-compose.prod.yml` L91-94）。

这是**结构性冲突**：缓存命中率越高，堆越接近 1GB 上限，越早 OOM；管理端把缓存调大反而更快宕机。且缓存条目权重按 `segmentedText.length()*2` 估算，只剩文本本身，`LinkedHashMap` 节点、`SimHash`、字符串对象头都未计入，实际占用更高。

### H7【P0】线程池 `CallerRunsPolicy` 会把整个查重压回 Tomcat 线程 → 雪崩

**位置**：`AsyncConfig#checkTaskExecutor`（L47）、`#asyncExecutor`（L71）

```java
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
```

`CheckTaskEventListener#handleCheckTaskCreated` 是 `@Async("checkTaskExecutor")`（L131）。当线程池 + 队列（默认 max=20、queue=100）打满时，拒绝策略让**发布的那个线程**执行任务——而发布者是 `CheckTaskServiceImpl#createCheckTaskInTransaction` 里的 `eventPublisher.publishEvent`，运行在 **Tomcat 请求线程**上。于是“异步查重”瞬间退化为：HTTP 线程执行 Tika 全文解析 + 全库比对 + PDF 生成，单请求数分钟到数十分钟。

由于阈值校验用 `check.task.max-concurrent: 2`（`application.yml` L127），而线程池 max 用的是 `system_config.performance.maxConcurrent`（默认 **20**，`PerformanceConfigDTO` L24），两个并发闸门口径不一致：入口只允许 2 个 `checking`，池子却开 20，队列还能堆 100——**排队任务数与真实算力完全脱钩**。

### H8【P0】kkFileView 公网暴露 + 每次预览同步健康探测

**位置**：`FilePreviewServiceImpl#proxyToKKFileView`（L478-503）、`#proxyReportToKKFileView`（L448-463）、构造函数（L72-79）

三个叠加问题：

1. **每次预览都同步探测** `kkfileviewUrl + "/index"`。`kkfileviewUrl` 在生产是公网 HTTPS 域名（`application-prod.yml` L16-18），容器内访问要走 DNS + 网关 + TLS 回环；`RestTemplate` 用 `SimpleClientHttpRequestFactory`（`HttpURLConnection`，**无连接池**），连接超时 5s、读超时 30s。kkFileView 一卡，预览请求就把 Tomcat 线程按 30s 计挂住。
2. 探测失败时 `proxyToKKFileView` 退化为 `fallbackToDownload` 返回 JSON+下载地址，而 `proxyReportToKKFileView` 返回 503——**两个入口的降级语义不一致**，前端难以判断。
3. **`/kkfileview/` 由网关直接暴露**（`docker-compose.prod.yml` L56-63 注释可证）。kkFileView 自身也是“拿 `url` 参数去拉取”的服务，若未配置 `trust.host` 白名单，外部匿名用户就能把 `https://.../kkfileview/onlinePreview?url=<base64>` 当成**匿名 SSRF 代理**打内网（我们的 `SafePreviewUrlValidator` 只保护了后端自己的入口，保护不到 kkFileView 的直连）。

### H9【P1】签名令牌被打进 INFO 日志

**位置**：`FilePreviewServiceImpl` L517、L524、L440

```java
log.info("构建KKFileView文件URL: {}", fileUrl);      // 内含 pt=...
log.info("KKFileView预览URL: {}", kkFileViewPreviewUrl); // base64 后仍可还原 pt
```

日志落在 `app_logs` 卷（`docker-compose.prod.yml` L107），与运维/备份共享。任何能读日志的人，在 10 分钟窗口内可直接下载论文。结合 H1 的 IDOR，这属于“把钥匙写进公共记事本”。

### H10【P1】外部依赖无超时：SMTP 与 MinIO

- `EmailService#sendNoticeEmail`（L48-87）同步调用 `javaMailSender.send()`；`spring.mail.properties` 未配置 `mail.smtp.connectiontimeout/readtimeout/timeout`（`application.yml` L89-107）。密码找回接口 `AuthServiceImpl` L454 在**请求线程**里同步发信，SMTP 一挂，请求线程无限等待。
- `MinioConfiguration`（L16-22）只用 `endpoint/credentials/region` 构建 `MinioClient`，**未配置超时与重试**；`PaperContentMinioService#testMinioConnection` 在每次入库前额外做一次 `bucketExists` 往返（L213-222）。全库比对是**逐篇串行** `getObject` + `readAllBytes`（L108-127、L134-153），MinIO 抖动会被线性放大成“每篇多等一个 RTT”。

### H11【P1】配置与密钥卫生

- `application-prod.yml` L119-127：Redisson 配置里写着 `password: "${REDIS_PASSWORD:123456}"`——生产配置文件里带明文兜底口令，且 `ProductionSecretValidator` 只校验 `spring.data.redis.password`，**不校验 Redisson 这份**。
- 代码里保留开发默认盐：`CheckTaskEventListener` L117、`ReportVerifyServiceImpl` L42（`dev-only-report-hash-salt-...`），仅靠 prod fail-fast 兜底。
- dev 默认 `minioadmin`（`application.yml` L27-28）、JWT 开发密钥（L81）仍在仓库中。
- `ProductionSecretValidator` 的注释明确写了“Git 历史中的旧密钥必须视为已泄露”（L83），说明历史提交存在真实口令，**轮换是前置条件而非建议**。
- `minio.bucket.report`、`minio.bucket.avatar` 与实际使用不一致（report 桶全仓无人使用，属于“配置宣称了 4 个桶，实际只用 2 个”），易误导运维做错权限。

### H12【P1】无链路追踪，故障无法定位到具体环节

- 全仓 grep `MDC|traceId|X-Request-Id` **零命中**；`logback-spring.xml` 的 pattern（L6/L25）只有时间、线程、logger。
- 因此“预览失败”只能看到三条彼此独立的日志：后端 `KKFileView代理预览失败`、kkFileView 容器日志、MinIO/网关日志，**没有共同 ID 可以 join**。
- 更糟的是 H9 把最关键的签名 URL 打了出来，日志里既缺少关联 ID，又多了敏感凭据。
- 另：`batchDownloadPapers` 无数量上限；`AdminReportServiceImpl#exportZip` 先 `List<byte[]>` 全量渲染再写 ZIP（L322-375），后端**没有对 `ids` 做上限校验**（注释假设“前端一页 ≤100”），一次请求 1 万条即可打爆堆。

---

## 二、性能瓶颈点（高并发下会卡顿/排队）

### P1【P0】本地查重的算法复杂度是 O(库内论文数) 且逐篇串行 IO

**位置**：`LocalCheckEngine#check`（L99-167）、`DetectionCacheService#getOrLoad`（L130-151）

- `paperInfoMapper.selectList(...)` 一次性拉全表已准入论文（无分页、无 topK）；
- 循环内逐篇：缓存未命中 → MinIO `getObject` → `readAllBytes` → IK 分词 → SimHash；
- 命中缓存也要在 `synchronized (this)` 里串行取；**所有并发查重共享同一把缓存锁**；
- 过了海明预筛的候选还要**再从 MinIO 读一次原文**（L145）供句级分析。

规模上去后单次查重的耗时与论文总数线性相关，`check.task.timeout: 3600000`（1 小时）只是兜底，不是治理。

### P2【P0】句级分析是 O(目标句数 × 候选句数) 的余弦计算

**位置**：`SentenceLevelAnalyzer` L114-140

三层嵌套循环（目标句 → 候选论文 → 候选句），每对都做 `calculateCosineSimilarity`。`maxCandidates=50` 只是限了候选论文数，单篇 3000 句 × 50 篇 × 平均 40 句仍是千万级 Map 运算。在 2 vCPU 上跑 20 个并发（`PerformanceConfigDTO.maxConcurrent=20`）时，CPU 会长期饱和，Web 线程（登录、列表、预览）跟着变慢。

### P3【P1】上传链路三倍 IO、两次全文解析

见 H5。额外一点：`spring.servlet.multipart` 未指定 `location`，临时文件落在容器 `/tmp`（overlayfs），与 `/data` 卷是**不同挂载点**，`transferTo` 无法 rename，只能复制——单次上传 = 写 `/tmp` + 读回堆 + 写 `/data`，即 2 写 1 读。

### P4【P1】预览链路每次多一次跨网关健康探测

见 H8。每次“打开预览”额外付出：DNS + TCP + TLS + 网关转发 + `/index` 响应，链路 RTT 直接加到用户首屏时间上；并发时还占 Tomcat 线程。

### P5【P1】批量导出：全量内存 + N+1 查询 + 无上限

**位置**：`AdminReportServiceImpl#exportZip`（L322-375）、`#renderPdf`（L270-282）

每份报告 3 次 `selectById`（report / task / paper），每份 PDF 在内存中重新渲染为 `byte[]` 并全部攒在 `List<byte[]>` 里，最后才写响应。100 份 ≈ 数十 MB 堆；无上限时可到 GB 级。而且这些 PDF 磁盘上**已经存在**（`report.storage.base-path`），完全没必要重新按需渲染。

### P6【P2】其它

- `CheckTaskValidationService#getRunningTaskCount` / `getAverageDurationMinutes` 每次建任务都要扫 `check_task`（L155-201），没有索引约束说明，“间隔校验”又要求按 `paper_id` 全量排序取历史（L143-150）。
- `BatchCheckTaskServiceImpl#getCurrentLoadFactor` 用的是大写 `"CHECKING"`（L372），而字典值是 `checking`（`DictConstants.CheckStatus` L125）→ 计数恒为 0，负载系数永远失真。
- `Hikari maximum-pool-size: 20` + `Tomcat 200` 线程 + 30s `connection-timeout`：DB 抖动时 200 个线程会在池外排队，表现为全站 30s 超时。
- WebSocket 进度是 `/topic/check-progress/{taskId}` 广播，`WebSocketChannelInterceptor` 只校验 CONNECT，**不校验 SUBSCRIBE 的任务归属**（L29-58），任意登录用户订阅任意 taskId 可观测他人查重进度（低敏但确实越权）。

---

## 三、架构优化建议（按优先级与改动成本排序）

### 3.1 立即修（安全止血，1~2 天内可上线）

1. **给预览入口补归属校验**：`FileUploadController#getPreviewUrl` 复用 `canAccessFile`（并抽出 `canAccessPaper` 的判定逻辑），与 `smartPreview` 对齐；无归属直接 403。
2. **教师权限收敛**：`canAccessFile` 的 `isTeacher() → true` 改为“文件所属论文的指导教师（`paper.teacher_id`）或评阅人”才放行；无法确定论文归属的附件，按 `upload_user_id` 判定。
3. **秒传不再跨用户返回**：`getByMd5` 命中他人文件时，要么按“引用计数 + 复制归属”处理，要么返回“请重新上传”；`submitPaperByFileId` 必须校验 `file_info.upload_user_id == studentId`（或由服务端代传）。
4. **令牌绑定会话**：`PreviewSignedUrlService.sign/verify` 的 payload 增加 `userId`（可选 `ip`），把 `pt` 从“资源 bearer”升级成“用户 + 资源 + 时间”三元绑定；`preview-fetch` 在匿名放行下仍可用（签名里已带 userId，无需 JWT）。
5. **停止记录签名 URL**：删除 FilePreviewServiceImpl 的 L517/L524/L440 日志，或只记录 `fileId` 与是否命中。
6. **fileId 改雪花**：`FileService#generateFileId` 用 MyBatis-Plus `IdWorker.getId()`（项目已配 `id-type: ASSIGN_ID`），同时解决主键碰撞与 ID 枚举。

### 3.2 存储与预览架构（1~2 周）

1. **Bucket 一律 Private**，改“裸对象 URL”为**预签名 GET**：
   - 头像：`getPresignedObjectUrl(GetPresignedObjectUrlArgs.method(GET).expiry(5, MINUTES))`，或在 Controller 里 `StreamingResponseBody` 代理（头像可加 `Cache-Control`）。
   - 论文正文：只给服务端读，前端永不直连 MinIO；`content_path` 存 **object key**（`papers/content/{id}/{uuid}.txt`），不要存完整 URL。
   - 运维侧禁止匿名 list，`/minio/` 网关路由要么撤掉、要么加 `auth_request`。
2. **`SafePreviewUrlValidator` 不再把 `minio.endpoint` 自动并入白名单**：只接受显式 `file.preview.allowed-hosts`；并加 DNS TOCTOU 缓解（解析一次后按 IP 连接，或干脆禁用该遗留入口）。
3. **下线 `/api/file/onlinePreview`**（遗留的开放代理入口），统一走 `preview-url`。
4. **kkFileView 侧配置**：`trust.host` 白名单只放 `check-backend`；`base.url` 与网关 `/kkfileview/` 前缀一致；网关对 `/kkfileview/` 增加登录态校验（`auth_request`）或按 IP 白名单。
5. **去掉每请求健康探测**：改为后台 `@Scheduled` 每 30s 探测一次并缓存结果（或直接用熔断器），预览路径只读缓存；若要保留探测，换成带连接池的客户端（OkHttp/Apache HttpClient 5），并把 `kkfileview` 的健康地址配成**内网别名**（如 `http://kkfileview:8012`），不要绕公网网关。
6. **回源 URL 走内网 + 可配置 scheme**：`app.host`/`app.scheme`/`app.port` 三件套显式配置，模板化为 `%s://%s:%s%s`，避免 `application.yml` 里 `192.168.1.149` 这类默认值再次进入生产。

### 3.3 上传与解析（1~2 周）

1. **全链路流式**：`spring.servlet.multipart.file-size-threshold: 1MB` + `location` 指到 `/data/multipart-tmp`；Controller 不再 `getBytes()`；MD5 用 `DigestInputStream` 边读边算，或前端算好传 `md5` 让服务端只做校验。
2. **一次 Tika**：删掉 `saveBasicFileInfo` 里的同步 `countWordsFromFile`，只保留异步字数统计（或直接把字数统计丢进 `checkTaskExecutor` 之外的独立小池，避免影响查重）。
3. **Tika 解析限时**：把 `paper-check.parse-timeout`（当前死配置）接到 `CompletableFuture.orTimeout` / 自定义 `TaskThread`，超时即失败并落 `failure`；同时限制解析后的字符数（超过阈值截断/拒绝），对压缩包类文档限制解压条目数。
4. **统一文件校验入口**：消息附件、头像、评阅附件也必须走 `FileValidationService`（扩展名 + 魔数 + 大小），并在 `FileService` 内部再做一次白名单兜底，避免新入口绕过。
5. **MinIO 直传（可选）**：如果确实要把论文文件放对象存储，前端先调后端拿**预签名 PUT**，直传 MinIO，后端只收 objectKey + 校验结果；这样 Tomcat 不再承载 50MB 流量。

### 3.4 查重链路（2~4 周，收益最大）

1. **把“全库现算”改成“预计算 + 索引”**：
   - 入库时（`PaperLibraryIngestService`）就把 `simhash`、分词、句级切分落库（新增 `paper_signature` 表：`paper_id, simhash, segmented_path, sentence_count, updated_at`）；
   - 查重时用**分段 SimHash 倒排 / LSH 分桶**先取 topK（例如 200 条），再算余弦与句级，彻底去掉 `selectList` 全表。
2. **缓存容量与堆绑定**：`maxWeightBytes = min(config, 0.25 * Xmx)`，启动时打印实际堆并夹取；管理端上限从 10000MB 降到 512MB，或在保存时校验 `< 0.25*Xmx`。更稳妥的做法是把缓存换成本地磁盘 KV（RocksDB）或 Redis，避免与业务堆争内存。
3. **并发闸门统一**：`check.task.max-concurrent` 与线程池 `maxPoolSize` **同源**（都读 `system_config.performance.maxConcurrent`），并把队列长度、每任务 CPU 预算写入监控；入口拒绝用 **429 + `Retry-After`**，而不是让请求排队到超时。
4. **拒绝策略改 `AbortPolicy`**：任务被拒绝时把 `check_task` 保持 `pending` 并落“排队失败，请重试”，绝不能让 Tomcat 线程执行查重。`asyncExecutor` 同理（通知/邮件不应挤占请求线程）。
5. **查重入口加幂等锁**：`createCheckTask` 以 `paperId` 为 key 做 Redis 短锁（或对 `paper_info` 行做 `SELECT ... FOR UPDATE`），把“次数/间隔/并发”校验与建任务放进同一临界区，消除 TOCTOU。
6. **句级分析预算化**：候选按整篇相似度取 topK（如 10 篇）、单篇句数上限、超预算降级为整篇口径（现有 `catch` 降级逻辑已具备，只需主动触发）。

### 3.5 可观测性与配置（1 周，先做“能定位”）

1. **接入 Micrometer Tracing + logback `%X{traceId}`**：`build.gradle/pom` 加 `micrometer-tracing-bridge-brave`（或 otel），`server.observability` 暴露 `traceId/spanId`；logback pattern 改为 `... [%X{traceId:-}] ...`。
2. **自定义 `TraceIdFilter`**（`Ordered.HIGHEST_PRECEDENCE`）：入口生成/透传 `X-Request-Id`，写入 MDC，并**透传到 kkFileView 的 `url` 参数和 MinIO 请求头**（kkFileView 无法改代码的话，至少把 traceId 拼在回源 URL 的 query 里，后端 `preview-fetch` 落日志）。
3. **关键链路打点**：`upload`（文件大小/耗时/解析耗时）、`preview`（签发/探测/回源状态）、`check`（库规模、命中缓存数、比对耗时、句级耗时）分别打结构化日志或 Micrometer Timer，而不是靠肉眼 grep。
4. **异常可读化**：把“预览失败”拆成明确的错误码——`FILE_NOT_FOUND`（业务库）/`STORAGE_UNAVAILABLE`（MinIO）/`PREVIEW_SERVICE_UNAVAILABLE`（kkFileView）/`SIGNATURE_INVALID`，前端与运维都能一眼定位。
5. **Actuator 端点迁到管理端口**（`management.server.port` 绑内网），`/actuator/prometheus` 不再依赖 ADMIN 账号暴露在业务端口。
6. **配置治理**：删掉 `application-prod.yml` 里 Redisson 的 `:123456` 兜底、代码里的开发盐默认值；`ProductionSecretValidator` 增加 `minio.accesskey` 与 Redisson 口令校验；把“4 个桶”的配置与代码使用对齐（不用的桶删掉）。所有历史密钥按 validator 的提示**轮换一次**。

### 3.6 其它顺手项

- `AdminReportServiceImpl#batchExportReports` 增加 `ids.size() <= 100` 校验；导出直接读磁盘上的 PDF 文件流式写 ZIP，别重新渲染。
- `PaperInfoServiceImpl#batchDownloadPapers` 的 ZipEntry 名用 `safeFileName(paper.getPaperTitle())`（当前直接拼标题，学生可控，存在 zip-slip/重名异常风险）；扩展名用真实文件后缀而非硬编码 `.pdf`。
- `WebSocketChannelInterceptor` 在 `SUBSCRIBE` 时校验 `taskId` 归属（或改用用户维度 topic `/topic/check-progress/user/{userId}`）。
- `BatchCheckTaskServiceImpl#getCurrentLoadFactor` 的大写 `"CHECKING"` 改为 `DictConstants.CheckStatus.CHECKING`。
- `FileService#deleteFile`、`PaperContentMinioService#deleteObjectsByPrefix` 与 `content_path` 的新旧版本要收口：每次 `ingest` 都写新 UUID 对象，旧对象**无人清理**，MinIO 会持续膨胀，建议加生命周期规则或入库前先删旧前缀。

---

## 四、总体判断

当前系统的业务链路（异步查重、状态机条件更新、额度原子扣减、报告验真）质量明显高于其“文件与预览链路”。风险不是均匀分布的，而是高度集中在三处：

1. **预览入口的鉴权缺口（H1/H2/H3）** —— 属于“改 3 个方法就能堵住”的急性风险，应当先止血；
2. **MinIO 的 Public-Read 设计（H4）** —— 属于架构选型问题，需要把“裸对象 URL”整体换成“私有桶 + 预签名/代理”；
3. **堆内缓存与全库比对（H5/H6/H7/P1/P2）** —— 属于容量模型问题，在 2C4G / 768MB 堆的机器上，现有默认配置（1GB 缓存、20 并发、200 线程、一次上传两份 byte[]）不可能稳定运行，必须先调容量、再谈扩容。

可观测性（H12）是放大器：它不直接导致故障，但会让上面每一类故障的定位时间从“几分钟”变成“几小时”。

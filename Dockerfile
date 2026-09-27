# ========================================
# 论文查重后端服务 - 运行时镜像
# ========================================

FROM eclipse-temurin:17-jre

WORKDIR /app

# 使用阿里云镜像源安装 curl（容器健康检查依赖）
RUN sed -i 's|//archive.ubuntu.com|//mirrors.aliyun.com|g; s|//security.ubuntu.com|//mirrors.aliyun.com|g' /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || true; \
    apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# 创建数据目录（dumps 用于 OOM 时的堆转储）
RUN mkdir -p /data/upload /data/report /data/check-temp /data/check-report /data/review-attach /data/dumps

# 复制 JAR 文件
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=prod -jar app.jar"]
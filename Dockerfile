# ========================================
# 论文查重管理系统 - 后端 Dockerfile
# 多阶段构建：Maven 编译 → JRE 运行
# ========================================

# ---------- 阶段一：Maven 编译 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
# 先复制依赖描述文件，利用 Docker 缓存层加速
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN mvn dependency:go-offline -B -q || true

# 复制源码并构建
COPY src src
RUN mvn package -DskipTests -B -q

# ---------- 阶段二：运行镜像 ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

# 安装中文字体（PDF 报告生成需要）
RUN apt-get update && \
    apt-get install -y --no-install-recommends fonts-wqy-microhei && \
    rm -rf /var/lib/apt/lists/*

# 从构建阶段拷贝 JAR
COPY --from=builder /build/target/*.jar app.jar

# 创建数据持久化目录
RUN mkdir -p /data/upload /data/report /data/check-temp /data/check-report /data/review-attach

EXPOSE 8080

# JVM 参数：
# -XX:+UseZGC      使用 ZGC 低延迟垃圾回收器
# -Xms256m -Xmx512m 堆内存（按服务器配置调整）
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-Xms256m", "-Xmx512m", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=prod", \
    "-jar", "app.jar"]
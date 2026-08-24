# ========================================
# 论文查重后端服务 - 运行时镜像
# JAR 由 Jenkins 构建并推送，Dockerfile 只负责打包
# ========================================

FROM eclipse-temurin:17-jre

WORKDIR /app

# 配置阿里云 Ubuntu 源（解决国内网络问题）
RUN echo "deb http://mirrors.aliyun.com/ubuntu/ resolute main universe restricted multiverse" > /etc/apt/sources.list && \
    echo "deb http://mirrors.aliyun.com/ubuntu/ resolute-updates main universe restricted multiverse" >> /etc/apt/sources.list && \
    echo "deb http://mirrors.aliyun.com/ubuntu/ resolute-security main universe restricted multiverse" >> /etc/apt/sources.list && \
    echo "deb http://mirrors.aliyun.com/ubuntu/ resolute-backports main universe restricted multiverse" >> /etc/apt/sources.list

# 安装中文字体（PDF 生成需要）
RUN apt-get update && \
    apt-get install -y --no-install-recommends fonts-wqy-microhei && \
    rm -rf /var/lib/apt/lists/*

# 创建数据目录
RUN mkdir -p /data/upload /data/report /data/check-temp /data/check-report /data/review-attach

# 复制 JAR（Jenkins 推送后，在目标服务器上执行 docker build）
COPY *.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=prod -jar app.jar"]
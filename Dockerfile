# ========================================
# 论文查重后端服务 - 运行时镜像
# ========================================

FROM eclipse-temurin:17-jre

WORKDIR /app

# 配置阿里云 Ubuntu 源（deb822 格式 - 直接替换 ubuntu.sources）
RUN echo 'Types: deb' > /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'URIs: http://mirrors.aliyun.com/ubuntu/' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'Suites: resolute resolute-updates resolute-backports' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'Components: main universe restricted multiverse' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo '' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'Types: deb' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'URIs: http://mirrors.aliyun.com/ubuntu/' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'Suites: resolute-security' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'Components: main universe restricted multiverse' >> /etc/apt/sources.list.d/ubuntu.sources && \
    echo 'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' >> /etc/apt/sources.list.d/ubuntu.sources

# 安装中文字体（PDF 生成需要）
RUN apt-get update && \
    apt-get install -y --no-install-recommends fonts-wqy-microhei && \
    rm -rf /var/lib/apt/lists/*

# 创建数据目录
RUN mkdir -p /data/upload /data/report /data/check-temp /data/check-report /data/review-attach

# 复制 JAR 文件
COPY *.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=prod -jar app.jar"]
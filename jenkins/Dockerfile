# ========================================
# Jenkins 构建镜像
# 基于官方 LTS + JDK17，补齐 docker CLI / compose 插件 / Maven / git
#
# 构建:
#   docker build -t jenkins-custom:lts-jdk17 jenkins/
#
# docker CLI 直接从官方 docker:cli 镜像拷贝静态二进制，
# 避免在 Debian 里配 apt 源、GPG key 和 codename 那一堆坑。
# ========================================
FROM jenkins/jenkins:lts-jdk17

USER root

COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker:cli /usr/local/libexec/docker/cli-plugins/ /usr/local/libexec/docker/cli-plugins/

# git + maven（Debian 源走阿里云镜像加速）
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true; \
    sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null || true; \
    apt-get update && \
    apt-get install -y --no-install-recommends git maven && \
    rm -rf /var/lib/apt/lists/*

# 首次启动时由官方镜像的 ref 机制复制到 $JENKINS_HOME/.m2/settings.xml
COPY settings.xml /usr/share/jenkins/ref/.m2/settings.xml

USER jenkins

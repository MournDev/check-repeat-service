#!/bin/bash

# ============================================
# 部署脚本 - 配合 docker-compose
# ============================================

set -e

DEPLOY_DIR="/usr/local/check-repeat"
cd $DEPLOY_DIR

echo "📦 1. 检查 JAR 文件..."
JAR_FILE=$(ls *.jar | grep -v original | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "❌ No JAR file found!"
    exit 1
fi
echo "✅ Found JAR: $JAR_FILE"

# 重命名 JAR（让 Dockerfile 能 COPY）
mv "$JAR_FILE" app.jar

echo "🔨 2. 构建后端镜像..."
docker-compose build backend

echo "🔄 3. 重启所有服务..."
docker-compose down
docker-compose up -d

echo "🧹 4. 清理无用镜像..."
docker image prune -f

echo "📋 5. 查看服务状态..."
docker-compose ps

echo "✅ Deployment completed at $(date)"
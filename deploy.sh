#!/bin/bash

# ============================================
# 部署脚本 - 配合 docker-compose
# ============================================

set -e

DEPLOY_DIR="/usr/local/check-repeat"
cd $DEPLOY_DIR

echo "📦 1. 检查 JAR 文件..."
# 直接在根目录找 JAR（因为 app.jar 在根目录）
JAR_FILE=$(ls *.jar | grep -v original | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "❌ No JAR file found!"
    exit 1
fi
echo "✅ Found JAR: $JAR_FILE"

# 如果 JAR 文件名不是 app.jar，重命名为 app.jar
if [ "$JAR_FILE" != "app.jar" ]; then
    mv "$JAR_FILE" app.jar
fi

echo "🔨 2. 构建后端镜像..."
docker compose build backend

echo "🔄 3. 重启所有服务..."
docker compose down
docker compose up -d

echo "🧹 4. 清理无用镜像..."
docker image prune -f

echo "📋 5. 查看服务状态..."
docker compose ps

echo "✅ Deployment completed at $(date)"
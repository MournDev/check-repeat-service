#!/bin/bash
# ============================================
# 后端部署脚本（单容器重建，约 50-60 秒不可用；健康检查失败自动回滚）
#
# 用法: ./deploy.sh <image-tag>
# 例:   ./deploy.sh 42
#
# 由 Jenkinsfile 调用，也可手动执行。
# 注意: 绝不要用 docker compose down —— 那会连带停掉 MySQL/Redis，
#       造成全站停机。只操作 backend 单个服务。
# ============================================
set -euo pipefail

DEPLOY_DIR="/usr/local/check-repeat"
COMPOSE_FILE="docker-compose.prod.yml"
IMAGE_NAME="check-repeat-service"
CONTAINER_NAME="check-backend"
HEALTH_URL="http://localhost:8080/check/actuator/health"
HEALTH_RETRIES=30
HEALTH_INTERVAL=5
KEEP_IMAGES=3

TAG="${1:-}"
if [ -z "$TAG" ]; then
    echo "用法: $0 <image-tag>" >&2
    exit 1
fi

cd "$DEPLOY_DIR"

NEW_IMAGE="${IMAGE_NAME}:${TAG}"
PREV_IMAGE="$(grep -E '^BACKEND_IMAGE=' .env | cut -d= -f2- || true)"

echo "==> 当前镜像: ${PREV_IMAGE:-<无>}"
echo "==> 目标镜像: ${NEW_IMAGE}"

if ! docker image inspect "$NEW_IMAGE" >/dev/null 2>&1; then
    echo "错误: 本地不存在镜像 $NEW_IMAGE，请先执行 docker build" >&2
    exit 1
fi

# 把目标 tag 写入 .env，供 compose 解析 ${BACKEND_IMAGE}
if grep -qE '^BACKEND_IMAGE=' .env; then
    sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${NEW_IMAGE}|" .env
else
    echo "BACKEND_IMAGE=${NEW_IMAGE}" >> .env
fi

echo "==> 启动 backend 容器"
docker compose -f "$COMPOSE_FILE" up -d backend

echo "==> 健康检查（最多 $((HEALTH_RETRIES * HEALTH_INTERVAL)) 秒）"
healthy=0
for _ in $(seq 1 "$HEALTH_RETRIES"); do
    if docker exec "$CONTAINER_NAME" curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
        healthy=1
        break
    fi
    printf '.'
    sleep "$HEALTH_INTERVAL"
done
echo

if [ "$healthy" -ne 1 ]; then
    echo "错误: 健康检查失败，最近日志:" >&2
    docker compose -f "$COMPOSE_FILE" logs --tail=80 backend >&2 || true
    if [ -n "$PREV_IMAGE" ]; then
        echo "==> 回滚到 $PREV_IMAGE" >&2
        sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${PREV_IMAGE}|" .env
        docker compose -f "$COMPOSE_FILE" up -d backend
        echo "==> 已回滚，当前运行: $PREV_IMAGE" >&2
    else
        echo "警告: 无可回滚的历史镜像" >&2
    fi
    exit 1
fi

echo "==> 部署成功: $NEW_IMAGE"

# 只保留最近 KEEP_IMAGES 个数字 tag 的镜像
echo "==> 清理旧镜像（保留最近 $KEEP_IMAGES 个）"
docker images "$IMAGE_NAME" --format '{{.Tag}}' \
    | grep -E '^[0-9]+$' \
    | sort -rn \
    | tail -n +$((KEEP_IMAGES + 1)) \
    | xargs -r -I{} docker rmi "${IMAGE_NAME}:{}" >/dev/null 2>&1 || true

echo "==> 当前容器状态"
docker compose -f "$COMPOSE_FILE" ps
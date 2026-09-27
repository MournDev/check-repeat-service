// ============================================
// 论文查重后端 - CI/CD 流水线
//
// 前置条件（在 Jenkins Job 配置里设置，不写进本文件以免泄露）:
//   1. 源码管理 -> Gitee 仓库地址 + 凭据
//   2. 构建触发器 -> Generic Webhook Trigger，Token 自行设置
//      并在 Gitee 仓库添加 Webhook 指向:
//      http://<服务器>/generic-webhook-trigger/invoke?token=<你的token>
//
// 部署路径约定:
//   /usr/local/check-repeat 必须以相同路径挂载进 Jenkins 容器
//   （-v /usr/local/check-repeat:/usr/local/check-repeat），
//   否则 compose 里的 bind mount 会被宿主 daemon 解析到不存在的路径。
// ============================================

pipeline {
    agent any

    options {
        // 2C4G 机器：禁止并发构建，避免 Maven 与生产服务抢内存
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
    }

    environment {
        IMAGE_NAME   = 'check-repeat-service'
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        DEPLOY_DIR   = '/usr/local/check-repeat'
        COMPOSE_FILE = 'docker-compose.prod.yml'
        // 限制 Maven 自身内存，防止构建把整机拖垮
        MAVEN_OPTS   = '-Xmx512m -XX:MaxMetaspaceSize=256m'
        LANG         = 'C.UTF-8'
    }

    stages {
        stage('构建 JAR') {
            steps {
                sh 'mvn -B -DskipTests clean package'
            }
        }

        stage('构建镜像') {
            steps {
                sh "docker build -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} -t ${env.IMAGE_NAME}:latest ."
            }
        }

        stage('同步部署文件') {
            steps {
                sh """
                    mkdir -p ${env.DEPLOY_DIR}
                    cp ${env.COMPOSE_FILE} deploy.sh ${env.DEPLOY_DIR}/
                    chmod +x ${env.DEPLOY_DIR}/deploy.sh
                """
            }
        }

        stage('部署') {
            steps {
                sh "cd ${env.DEPLOY_DIR} && ./deploy.sh ${env.IMAGE_TAG}"
            }
        }
    }

    post {
        success {
            echo "部署成功: ${env.IMAGE_NAME}:${env.IMAGE_TAG}"
        }
        failure {
            echo "流水线失败，请检查上方各阶段输出；若为健康检查失败，deploy.sh 已自动回滚"
        }
        always {
            sh 'docker image prune -f > /dev/null 2>&1 || true'
        }
    }
}

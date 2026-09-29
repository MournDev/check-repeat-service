

# 论文查重服务 (Check Repeat Service)

## 项目简介

论文查重服务是一个基于 Spring Boot 构建的学术论文管理系统，支持论文提交、相似度检测、指导教师分配、论文审核等完整业务流程。系统采用多角色设计，涵盖管理员、教师、学生三类用户，提供从论文提交到审核通过的全流程管理功能。

## 核心功能

### 管理员功能
- **系统管理**：用户管理、角色权限配置、系统参数设置
- **论文管理**：论文审核、批量操作、导出报表
- **数据统计**：论文提交趋势、查重统计、审核效率分析
- **监控告警**：系统性能监控、安全审计、健康检查
- **规则配置**：查重规则设置、比對库管理、系统参数配置
- **通知管理**：站内通知的查询/筛选、已读标记、批量删除、发送测试通知（`/api/notices/*`）

### 学生功能
- **论文管理**：论文提交、版本管理、修改申请、撤回功能
- **查重服务**：提交论文查重、查看检测报告、历史记录查询
- **个人中心**：个人信息管理、密码修改、邮箱验证
- **消息通知**：系统消息、导师沟通、审核结果通知

### 教师功能
- **论文审核**：待审核论文列表、审核操作、审核历史
- **学生管理**：指导学生列表、学生论文查看、批量操作
- **数据统计**：审核趋势分析、工作量统计
- **消息功能**：发送通知、联系学生

## 技术架构

| 类别 | 技术选型 |
|------|----------|
| 核心框架 | Spring Boot 3.x + Java 17 |
| 数据库 | MySQL 8.0 + MyBatis-Plus |
| 文件存储 | MinIO 对象存储 |
| 缓存 | Redis |
| 安全框架 | Spring Security + JWT |
| 消息推送 | WebSocket (STOMP协议) |
| 文档处理 | Apache Tika + KKFileView |
| 构建工具 | Maven |
| 部署 | Docker + Docker Compose |

## 项目结构

```
check-repeat-service/
├── src/main/java/com/abin/checkrepeatservice/
│   ├── admin/                    # 管理员模块
│   │   ├── controller/           # 控制器层
│   │   ├── service/              # 业务逻辑层
│   │   ├── mapper/               # 数据访问层
│   │   ├── dto/                  # 数据传输对象
│   │   └── vo/                   # 视图对象
│   ├── student/                  # 学生模块
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   ├── vo/
│   │   └── mapper/
│   ├── teacher/                  # 教师模块
│   │   ├── controller/
│   │   ├── service/
│   │   ├── mapper/
│   │   └── dto/
│   ├── detection/                # 查重检测模块
│   │   ├── service/
│   │   └── controller/
│   ├── notification/             # 消息通知模块
│   │   ├── controller/
│   │   └── service/
│   ├── common/                   # 公共模块
│   │   ├── config/               # 配置类
│   │   ├── constant/             # 常量定义
│   │   ├── entity/               # 实体类
│   │   ├── enums/                # 枚举类
│   │   ├── exception/            # 异常处理
│   │   ├── utils/                # 工具类
│   │   ├── aspect/               # AOP切面
│   │   ├── jwt/                  # JWT认证
│   │   ├── websocket/            # WebSocket
│   │   └── component/            # 组件
│   ├── monitor/                  # 监控模块
│   ├── schedule/                 # 定时任务
│   ├── mapper/                   # 通用Mapper
│   └── pojo/                     # 基础POJO类
├── src/main/resources/
│   ├── application.yml           # 应用配置
│   ├── application-prod.yml      # 生产环境配置
│   ├── db/init.sql               # 建表脚本（与实体类一致，手工执行）
│   └── ...
├── Dockerfile                    # Docker构建文件
├── docker-compose.prod.yml       # Docker Compose编排（生产）
├── DEPLOY.md                     # 生产部署手册
└── pom.xml                       # Maven配置
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Redis 6+
- MinIO (可选，用于文件存储)

### 配置说明

#### 1. 数据库配置
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/check_repeat?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
```

#### 2. Redis配置
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

#### 3. MinIO配置
```yaml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket:
    main: check-repeat-service
    paper-content: paper-content
    avatar: avatar
```

#### 4. JWT配置
```yaml
spring:
  jwt:
    secret-key: your-secret-key
    expiration: 86400000
    token-header: Authorization
    token-prefix: Bearer
```

### 构建运行

```bash
# 1. 克隆项目
git clone https://gitee.com/Sc0102_1129/check-repeat-service.git

# 2. 进入项目目录
cd check-repeat-service

# 3. 构建项目
./mvnw clean package -DskipTests

# 4. 运行项目
java -jar target/check-repeat-service.jar
```

### Docker部署

生产环境部署见 [`DEPLOY.md`](DEPLOY.md)（Jenkins 流水线 + `docker-compose.prod.yml`）。

本地开发可单独构建运行，中间件（MySQL / Redis / MinIO）需自备：

```bash
docker build -t check-repeat-service .
docker run -d -p 8080:8080 --name check-repeat-service check-repeat-service
```

## API文档

系统采用RESTful API设计，主要接口前缀如下：

| 模块 | 接口前缀 |
|------|----------|
| 管理员 | `/api/admin/*` |
| 学生 | `/api/student/*` |
| 教师 | `/api/teacher/*` |
| 公共接口 | `/api/common/*` |
| 查重检测 | `/api/detection/*` |
| 文件操作 | `/api/file/*` |
| 通知管理（仅 ADMIN） | `/api/notices/*` |

详细API文档可通过Swagger UI访问（部署后访问 `/swagger-ui.html`）。

## 用户角色

| 角色 | 说明 | 主要权限 |
|------|------|----------|
| ADMIN | 管理员 | 系统配置、用户管理、全局数据统计 |
| TEACHER | 教师 | 论文审核、学生管理、消息发送 |
| STUDENT | 学生 | 论文提交、查重、查看审核结果 |

## 核心业务流程

```
论文提交流程：
1. 学生提交论文 → 2. 系统分配指导教师 → 3. 教师确认分配
4. 学生论文查重 → 5. 生成查重报告 → 6. 教师审核论文
7. 审核通过/驳回 → 8. 学生根据需要进行修改 → 9. 完成
```

## 数据库表结构

主要数据表：
- `sys_user` - 用户信息表
- `sys_role` - 角色信息表
- `paper_info` - 论文信息表
- `paper_submit` - 论文提交记录表
- `check_task` - 查重任务表
- `check_report` - 查重报告表
- `check_result` - 查重结果表
- `teacher_allocation_record` - 教师分配记录表
- `review_record` - 审核记录表
- `sys_notice` - 站内通知表（通知管理模块，含 `priority` / `related_id` / `related_type`）
- `sys_notice_log` - 通知邮件发送日志表

## 许可证

本项目仅供学习研究使用，请遵守相关学术规范和法律法规。

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交改动 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

## 联系方式

- 项目地址：https://gitee.com/Sc0102_1129/check-repeat-service
- 如有问题，请提交 Issue 或联系项目维护者
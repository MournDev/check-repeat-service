# Thesis Plagiarism Check Service

## Project Introduction

The Thesis Plagiarism Check Service is an academic paper management system built on Spring Boot. It supports complete business processes including paper submission, similarity detection, advisor assignment, and paper review. The system adopts a multi-role design covering Administrators, Teachers, and Students, providing end-to-end management functions from submission to approval.

## Core Functions

### Administrator Functions
- **System Management**: User management, role permission configuration, system parameter settings
- **Paper Management**: Paper review, batch operations, report export
- **Data Statistics**: Paper submission trends, plagiarism check statistics, review efficiency analysis
- **Monitoring & Alerts**: System performance monitoring, security audit, health checks
- **Rule Configuration**: Plagiarism check rule settings, reference database management, system parameter configuration

### Student Functions
- **Paper Management**: Paper submission, version control, modification requests, withdrawal function
- **Plagiarism Check Service**: Submit paper for check, view detection report, history query
- **Personal Center**: Personal information management, password change, email verification
- **Message Notification**: System messages, advisor communication, review result notifications

### Teacher Functions
- **Paper Review**: Pending review paper list, review operations, review history
- **Student Management**: Guided student list, student paper viewing, batch operations
- **Data Statistics**: Review trend analysis, workload statistics
- **Messaging**: Send notifications, contact students

## Technical Architecture

| Category | Technology Selection |
|------|----------|
| Core Framework | Spring Boot 3.x + Java 17 |
| Database | MySQL 8.0 + MyBatis-Plus |
| File Storage | MinIO Object Storage |
| Cache | Redis |
| Security Framework | Spring Security + JWT |
| Message Push | WebSocket (STOMP Protocol) |
| Document Processing | Apache Tika + KKFileView |
| Build Tool | Maven |
| Deployment | Docker + Docker Compose |

## Project Structure

```
check-repeat-service/
├── src/main/java/com/abin/checkrepeatservice/
│   ├── admin/                    # Admin Module
│   │   ├── controller/           # Controller Layer
│   │   ├── service/              # Business Logic Layer
│   │   ├── mapper/               # Data Access Layer
│   │   ├── dto/                  # Data Transfer Objects
│   │   └── vo/                   # View Objects
│   ├── student/                  # Student Module
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   ├── vo/
│   │   └── mapper/
│   ├── teacher/                  # Teacher Module
│   │   ├── controller/
│   │   ├── service/
│   │   ├── mapper/
│   │   └── dto/
│   ├── detection/                # Plagiarism Detection Module
│   │   ├── service/
│   │   └── controller/
│   ├── notification/             # Message Notification Module
│   │   ├── controller/
│   │   └── service/
│   ├── common/                   # Common Module
│   │   ├── config/               # Configuration Classes
│   │   ├── constant/             # Constant Definitions
│   │   ├── entity/               # Entity Classes
│   │   ├── enums/                # Enum Classes
│   │   ├── exception/            # Exception Handling
│   │   ├── utils/                # Utility Classes
│   │   ├── aspect/               # AOP Aspects
│   │   ├── jwt/                  # JWT Authentication
│   │   ├── websocket/            # WebSocket
│   │   └── component/            # Components
│   ├── monitor/                  # Monitoring Module
│   ├── schedule/                 # Scheduled Tasks
│   ├── mapper/                   # Generic Mapper
│   └── pojo/                     # Basic POJO Classes
├── src/main/resources/
│   ├── application.yml           # Application Configuration
│   ├── application-prod.yml      # Production Environment Configuration
│   ├── db/migration/             # Flyway database migrations
│   │   └── V1__init.sql          # Baseline: table structures
│   └── ...
├── Dockerfile                    # Docker Build File
├── docker-compose.yml            # Docker Compose Orchestration (dev)
├── docker-compose.prod.yml       # Docker Compose Orchestration (prod)
├── DEPLOY.md                     # Production deployment guide
└── pom.xml                       # Maven Configuration
```

## Quick Start

### Environment Requirements

- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Redis 6+
- MinIO (Optional, for file storage)

### Configuration Instructions

#### 1. Database Configuration
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/check_repeat?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
```

#### 2. Redis Configuration
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

#### 3. MinIO Configuration
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

#### 4. JWT Configuration
```yaml
spring:
  jwt:
    secret-key: your-secret-key
    expiration: 86400000
    token-header: Authorization
    token-prefix: Bearer
```

### Build and Run

```bash
# 1. Clone project
git clone https://gitee.com/Sc0102_1129/check-repeat-service.git

# 2. Enter project directory
cd check-repeat-service

# 3. Build project
./mvnw clean package -DskipTests

# 4. Run project
java -jar target/check-repeat-service.jar
```

### Docker Deployment

```bash
# Start using Docker Compose
docker-compose up -d

# Or build and run separately
docker build -t check-repeat-service .
docker run -d -p 8080:8080 --name check-repeat-service check-repeat-service
```

## API Documentation

The system adopts RESTful API design. Main interface prefixes are as follows:

| Module | Interface Prefix |
|------|----------|
| Admin | `/api/admin/*` |
| Student | `/api/student/*` |
| Teacher | `/api/teacher/*` |
| Common Interface | `/api/common/*` |
| Plagiarism Detection | `/api/detection/*` |
| File Operations | `/api/file/*` |

Detailed API documentation is accessible via Swagger UI (access `/swagger-ui.html` after deployment).

## User Roles

| Role | Description | Main Permissions |
|------|------|----------|
| ADMIN | Administrator | System Configuration, User Management, Global Data Statistics |
| TEACHER | Teacher | Paper Review, Student Management, Sending Messages |
| STUDENT | Student | Paper Submission, Plagiarism Check, Viewing Review Results |

## Core Business Workflow

```
Thesis Submission Workflow:
1. Student submits thesis → 2. System assigns advisor → 3. Advisor confirms assignment
4. Student thesis plagiarism check → 5. Generate plagiarism report → 6. Teacher reviews thesis
7. Approval/Rejection → 8. Student modifies as needed → 9. Complete
```

## Database Table Structure

Main data tables:
- `sys_user` - User Information Table
- `sys_role` - Role Information Table
- `paper_info` - Thesis Information Table
- `paper_submit` - Thesis Submission Record Table
- `check_task` - Plagiarism Check Task Table
- `check_report` - Plagiarism Check Report Table
- `check_result` - Plagiarism Check Result Table
- `teacher_allocation_record` - Advisor Assignment Record Table
- `review_record` - Review Record Table

## License

This project is for learning and research purposes only. Please comply with relevant academic norms and laws/regulations.

## Contribution Guidelines

1. Fork this repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Contact Information

- Project Address: https://gitee.com/Sc0102_1129/check-repeat-service
- If you have any questions, please submit an Issue or contact the project maintainer.
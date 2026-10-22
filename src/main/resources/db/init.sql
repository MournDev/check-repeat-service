-- ⚠️⚠️ 本文件含 76 条 DROP TABLE —— 只允许对**空库**整份执行。⚠️⚠️
-- ⚠️⚠️ 线上库、以及任何已有数据的库，整份跑 = 清空全部数据，且不可恢复。⚠️⚠️
-- 已有数据的库要改结构，请跑同目录 存量修复.sql（幂等 ALTER/DDL 补救）。
-- 各脚本什么时候该跑、什么时候绝对不能跑，见同目录 README.md。
-- ============================================================================
-- 建表脚本 —— 本仓库唯一的表结构来源（76 张表）。
--
-- 由「旧一体化系统导出的库结构 + 按实体类和 Mapper XML 校正」而来，与 Java 实体 /
-- MyBatis-Plus 的约定对齐（逻辑删除列 is_deleted、create_*/update_* 审计列、字典取值）。
-- 实体类 / Mapper XML / 本文件三方不一致时，先判断方向、不要默认哪边写错了。
--
-- 执行约定：
--   · 开头是 DROP TABLE IF EXISTS，**只能对空库整份执行**；
--   · 已有数据的库补结构差量，跑同目录 存量修复.sql（幂等，可重复执行），
--     不要整份重跑本文件（会清数据）；
--   · 初始数据见同目录 种子数据.sql；存量库的结构/数据补救见 存量修复.sql。
-- ============================================================================

-- MySQL dump 10.13  Distrib 8.0.23, for Win64 (x86_64)
--
-- Host: localhost    Database: check-repeat-service
-- ------------------------------------------------------
-- Server version	8.0.23

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `academic_checklist`
--

DROP TABLE IF EXISTS `academic_checklist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `academic_checklist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '雪花ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `text` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文本',
  `checked` tinyint(1) DEFAULT '0' COMMENT '是否查重',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='??????????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admin_info`
--

DROP TABLE IF EXISTS `admin_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `position` varchar(100) DEFAULT NULL COMMENT '职位',
  `department` varchar(100) DEFAULT NULL COMMENT '所属部门',
  `office_address` varchar(255) DEFAULT NULL COMMENT '办公地址',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '软删除标志（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2060661729448300547 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admin_task_intervene_log`
--

DROP TABLE IF EXISTS `admin_task_intervene_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_task_intervene_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `created_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `task_type` tinyint NOT NULL COMMENT '任务类型：1-待审核任务（关联teacher_audit表），2-待查重任务（关联paper_submit表）',
  `task_id` bigint NOT NULL COMMENT '任务ID（关联对应任务表的id字段，如teacher_audit.id或paper_submit.id）',
  `intervene_type` tinyint NOT NULL COMMENT '干预类型：1-重新分配教师，2-驳回提交，3-标记文件损坏，4-强制通过',
  `old_teacher_id` bigint DEFAULT NULL COMMENT '原负责教师ID（仅task_type=1时有效，关联sys_user.id）',
  `new_teacher_id` bigint DEFAULT NULL COMMENT '新负责教师ID（仅intervene_type=1时有效，关联sys_user.id）',
  `intervene_reason` varchar(500) NOT NULL COMMENT '干预原因（如“教师超期未审核：48小时+”“文件损坏：无法解析PDF”）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_type_id` (`task_type`,`task_id`) COMMENT '避免同一任务重复记录干预日志'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员任务干预日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `advisor_evaluation`
--

DROP TABLE IF EXISTS `advisor_evaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `advisor_evaluation` (
  `id`                 bigint       NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`           bigint       NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `student_id`         bigint       NOT NULL COMMENT '评价学生ID（关联sys_user.id；仅管理端实名可见，教师端永不返回）',
  `teacher_id`         bigint       NOT NULL COMMENT '被评价导师ID（关联sys_user.id，提交时取 paper_info.teacher_id 快照）',
  `score_timeliness`   tinyint      NOT NULL COMMENT '指导及时性（1-5星）',
  `score_quality`      tinyint      NOT NULL COMMENT '反馈质量（1-5星）',
  `score_attitude`     tinyint      NOT NULL COMMENT '责任心态度（1-5星）',
  `score_professional` tinyint      NOT NULL COMMENT '专业能力（1-5星）',
  `score_overall`      tinyint      NOT NULL COMMENT '总体满意度（1-5星；导师评价分=avg/5×100）',
  `tags`               varchar(500) DEFAULT NULL COMMENT '预设标签（JSON数组，元素为字典 advisor_eval_tag 的 dict_value）',
  `comment`            varchar(500) DEFAULT NULL COMMENT '文字评语（选填，jsoup 纯文本清洗后落库）',
  `create_by`          bigint       DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint       DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`        datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`         tinyint      DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_id` (`paper_id`),
  KEY `idx_teacher` (`teacher_id`, `is_deleted`),
  KEY `idx_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导师评价表（论文全生命周期P6a：学生对指导教师评价，教师端仅见聚合）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `alert_record`
--

DROP TABLE IF EXISTS `alert_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_record` (
  `id` bigint NOT NULL COMMENT 'Snowflake ID',
  `rule_id` bigint DEFAULT NULL COMMENT 'FK to alert_rule.id',
  `rule_name` varchar(100) DEFAULT NULL COMMENT 'Rule name snapshot',
  `severity` varchar(20) DEFAULT NULL COMMENT 'Severity level',
  `title` varchar(200) DEFAULT NULL COMMENT 'Alert title',
  `message` varchar(1000) DEFAULT NULL COMMENT 'Alert detail message',
  `metric_value` double DEFAULT NULL COMMENT 'Trigger metric value',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE | RESOLVED',
  `trigger_time` datetime DEFAULT NULL COMMENT 'When alert triggered',
  `resolve_time` datetime DEFAULT NULL COMMENT 'When alert resolved',
  `resolved_by` varchar(64) DEFAULT NULL COMMENT 'Who resolved it',
  `create_by` bigint DEFAULT NULL COMMENT 'Creator user ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_by` bigint DEFAULT NULL COMMENT 'Updater user ID',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `is_deleted` tinyint DEFAULT '0' COMMENT 'Soft delete',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  KEY `idx_status` (`status`),
  KEY `idx_trigger_time` (`trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System alerts / monitoring alert records';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `alert_rule`
--

DROP TABLE IF EXISTS `alert_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_rule` (
  `id` bigint NOT NULL COMMENT '主键（雪花ID）',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `rule_type` varchar(50) NOT NULL COMMENT '规则类型(CPU/MEMORY/LOGIN_FAIL/CHECK_FAIL/STORAGE)',
  `metric_name` varchar(100) DEFAULT '' COMMENT 'Micrometer指标名',
  `threshold` double NOT NULL COMMENT '阈值',
  `duration` int DEFAULT '60' COMMENT '持续秒数',
  `severity` varchar(20) DEFAULT 'WARNING' COMMENT '严重级别(CRITICAL/WARNING/INFO)',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `notify_email` varchar(200) DEFAULT '' COMMENT '通知邮箱',
  `description` varchar(500) DEFAULT '' COMMENT '规则描述',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '软删除标记(0-未删除,1-已删除)',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_rule_type` (`rule_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='告警规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auto_assignment_config`
--

DROP TABLE IF EXISTS `auto_assignment_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auto_assignment_config` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置描述',
  `config_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置类型 (algorithm/weight/limit)',
  `is_enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用 0-禁用 1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`,`is_deleted`),
  KEY `idx_config_type` (`config_type`),
  KEY `idx_is_enabled` (`is_enabled`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自动分配配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auto_assignment_history`
--

DROP TABLE IF EXISTS `auto_assignment_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auto_assignment_history` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `task_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `strategy` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的分配策略',
  `total_students` int DEFAULT '0' COMMENT '总学生数',
  `assigned_count` int DEFAULT '0' COMMENT '成功分配数',
  `success_rate` decimal(5,2) DEFAULT '0.00' COMMENT '成功率百分比',
  `duration` bigint DEFAULT '0' COMMENT '执行时长(毫秒)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务状态 (completed/failed/cancelled)',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `config_detail` text COLLATE utf8mb4_unicode_ci COMMENT '配置详情(JSON格式)',
  `result_detail` text COLLATE utf8mb4_unicode_ci COMMENT '分配结果详情(JSON格式)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`,`is_deleted`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自动分配历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `category_similarity_threshold`
--

DROP TABLE IF EXISTS `category_similarity_threshold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_similarity_threshold` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `category_type` varchar(50) DEFAULT NULL COMMENT '分类类型：college-学院，major-专业，subject-学科领域，paper_type-论文类型（仅按类型的一档）',
  `college_id` bigint DEFAULT NULL COMMENT '学院ID',
  `major_id` bigint DEFAULT NULL COMMENT '专业ID',
  `subject_id` bigint DEFAULT NULL COMMENT '学科领域ID（category_type=subject 时必填，关联subject.id）',
  -- 论文类型维度：NULL = 适用全部类型，这正是本列存在之前的全部存量行的形态，
  -- 所以加这一列不会改变任何既有配置的生效范围（见 SimilarityThresholdResolverImpl.typedOrGeneric）。
  -- 取值只允许 PaperTypeEnum 的 code；category_type=paper_type 时本列必填且三个 id 全为 NULL。
  `paper_type` varchar(50) DEFAULT NULL COMMENT '论文类型维度（NULL=适用全部类型；取值见 PaperTypeEnum）',
  `threshold` int DEFAULT NULL COMMENT '相似度阈值',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  -- 必须是 DEFAULT '0'。BaseEntity.isDeleted 只标了 @TableLogic、没有 fill 注解，
  -- MyMetaObjectHandler 用的 strictInsertFill 不会补它，应用侧插入时不写这一列。
  -- 若这里是 DEFAULT NULL，行会带着 NULL 落库，而 MyBatis-Plus 查询恒拼 `is_deleted = 0`，
  -- NULL = 0 不成立 —— 数据插进去就读不回来（保存成功但列表恒为空，且无报错）。
  `is_deleted` int DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  -- 解析阈值固定按 category_type + college_id / major_id 取单行
  -- （SimilarityThresholdResolver.categoryThreshold），每篇论文查两次：
  -- 先专业、未命中再学院。表本身小，但查重每次调用都会走到，故建索引。
  KEY `idx_type_college` (`category_type`,`college_id`),
  KEY `idx_type_major`   (`category_type`,`major_id`),
  KEY `idx_type_subject` (`category_type`,`subject_id`),
  -- 仅按类型那一档（category_type=paper_type AND paper_type=?）靠它；
  -- 作用域行带类型的查询已经被上面三个索引收窄到个位数行，不必再加复合索引。
  KEY `idx_type_paper_type` (`category_type`,`paper_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学科/专业相似度阈值设置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `check_college_quota`
--

DROP TABLE IF EXISTS `check_college_quota`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `check_college_quota` (
  `id`          bigint   NOT NULL COMMENT '主键ID（雪花）',
  `college_id`  bigint   NOT NULL COMMENT '学院ID（关联college.id）',
  `quota_month` char(7)  NOT NULL COMMENT '额度月份（yyyy-MM，如2026-10）',
  `total_quota` int      NOT NULL COMMENT '当月总额度（次）',
  `used_quota`  int      NOT NULL DEFAULT '0' COMMENT '当月已用额度（次）',
  `create_by`   bigint   DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint   DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint  DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`   bigint   DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_college_month` (`college_id`, `quota_month`),
  KEY `idx_ccq_month` (`quota_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学院月度查重额度表（论文全生命周期P5）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `check_policy`
--

DROP TABLE IF EXISTS `check_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `check_policy` (
  `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
  `degree_type`    varchar(20)   NOT NULL COMMENT '学位类型：undergraduate-本科 / master-硕士 / doctor-博士（唯一）',
  `threshold`      decimal(5,2)  NOT NULL COMMENT '合格阈值（总相似比百分比，如20.00表示20%）',
  `max_per_source` decimal(5,2)  DEFAULT NULL COMMENT '单篇最大相似比上限（百分比，可空=不启用该项校验）',
  `max_check_count` int          DEFAULT NULL COMMENT '每篇论文最多查重次数（可空=沿用全局 admin.check-rule.default-max-count）',
  `check_interval` int           DEFAULT NULL COMMENT '两次查重间隔秒数（可空=沿用全局 admin.check-rule.default-interval）',
  `enabled`        tinyint       NOT NULL DEFAULT '1' COMMENT '是否启用：1-启用 / 0-停用',
  `create_by`      bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`      bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`    datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_degree_type` (`degree_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学位检测策略集（论文全生命周期P5，超管维护）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `check_report`
--

DROP TABLE IF EXISTS `check_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `check_report` (
  `id` bigint NOT NULL COMMENT '报告唯一ID（雪花ID）',
  `task_id` bigint NOT NULL COMMENT '对应的查重任务ID（关联check_task.id）',
  `paper_id` bigint DEFAULT NULL COMMENT '对应的论文ID（关联paper_info.id，可空）',
  `report_no` varchar(50) NOT NULL COMMENT '报告编号（如“REPORT20240512001”，唯一）',
  `repeat_details` text NOT NULL COMMENT '重复详情（JSON格式，含重复段落、相似来源、相似度）',
  `report_path` varchar(500) NOT NULL COMMENT '报告文件存储路径（如“/data/report/2024/05/12/xxx.pdf”）',
  `report_type` varchar(20) NOT NULL DEFAULT 'pdf' COMMENT '报告文件类型（默认pdf）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id，系统默认填管理员ID）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id，系统/管理员）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `total_similarity` decimal(5,2) DEFAULT NULL COMMENT '总相似度',
  `content_hash` varchar(64) DEFAULT NULL COMMENT '报告内容哈希（P5：SHA-256(关键要素JSON+盐) 小写 hex，公开验真接口重算比对；历史报告为 NULL 视为不可验真）',
  `report_generate_time` timestamp NULL DEFAULT NULL COMMENT '报告生成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_check_report_total_similarity` (`total_similarity`),
  KEY `idx_check_report_report_generate_time` (`report_generate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='查重报告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `check_result`
--

DROP TABLE IF EXISTS `check_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `check_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '查重结果ID',
  `task_id` bigint DEFAULT NULL COMMENT '查重任务ID（关联check_task.id）',
  `paper_id` bigint DEFAULT NULL COMMENT '论文ID（关联paper_info.id）',
  `submit_id` bigint DEFAULT NULL COMMENT '论文提交ID（关联paper_submit.id）',
  `rule_id` bigint DEFAULT NULL COMMENT '查重规则ID（关联check_rule.id）',
  `repeat_rate` decimal(5,2) DEFAULT NULL COMMENT '重复率（百分比，如25.32）',
  `max_per_source` decimal(5,2) DEFAULT NULL COMMENT '单篇最大相似比（P5 新口径，与 check_task 同义）',
  `citation_rate` decimal(5,2) DEFAULT NULL COMMENT '引用率（P5 新口径，与 check_task 同义）',
  `check_source` varchar(50) DEFAULT NULL COMMENT '查重来源（LOCAL-本地库，THIRD_PARTY-第三方API）',
  `most_similar_paper` varchar(200) DEFAULT NULL COMMENT '最相似论文标题',
  `similar_paper_id` bigint DEFAULT NULL COMMENT '最相似论文ID（本地库专用）',
  `report_url` varchar(500) DEFAULT NULL COMMENT '查重报告链接（第三方API专用）',
  `check_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '查重完成时间',
  `check_duration` int DEFAULT NULL COMMENT '查重耗时（秒）',
  `word_count` int DEFAULT NULL COMMENT '论文总字数',
  `repeat_word_count` int DEFAULT NULL COMMENT '重复字数',
  `check_details` text COMMENT '查重详情（JSON格式，包含重复段落信息）',
  `status` tinyint DEFAULT '1' COMMENT '状态（0-无效，1-有效）',
  `is_deleted` tinyint DEFAULT '0' COMMENT '删除标志（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by` varchar(200) DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_by` varchar(200) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_paper_id` (`paper_id`),
  KEY `idx_submit_id` (`submit_id`),
  KEY `idx_rule_id` (`rule_id`),
  KEY `idx_repeat_rate` (`repeat_rate`),
  KEY `idx_check_source` (`check_source`),
  KEY `idx_check_time` (`check_time`),
  KEY `idx_status` (`status`),
  KEY `idx_check_result_task_id` (`task_id`),
  KEY `idx_check_result_paper_id` (`paper_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2048440745244753923 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文查重结果表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `check_rule`
--

DROP TABLE IF EXISTS `check_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `check_rule` (
  `id` bigint NOT NULL COMMENT '规则唯一ID（雪花ID）',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称（如“本科毕业论文查重规则”）',
  `rule_code` varchar(50) NOT NULL COMMENT '规则编码（如“UNDERGRADUATE”，唯一）',
  `pass_threshold` decimal(5,2) NOT NULL COMMENT '合格重复率阈值（如20.00表示≤20%为合格）',
  `max_re_submit_count` tinyint DEFAULT '3' COMMENT '最大重提次数（0表示不允许重提）',
  `compare_lib` varchar(200) NOT NULL COMMENT '比对库范围（JSON格式，如["校内论文库","知网期刊库"]）',
  `check_interval` int NOT NULL DEFAULT '86400' COMMENT '二次查重间隔（单位：秒，默认24小时）',
  `max_check_count` int NOT NULL DEFAULT '5' COMMENT '单篇论文最大查重次数（默认5次）',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认规则（0-否，1-是，仅一条默认）',
  `description` varchar(500) DEFAULT '' COMMENT '规则描述',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='查重规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `check_sentence_hit`
--

DROP TABLE IF EXISTS `check_sentence_hit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `check_sentence_hit` (
  `id`              bigint        NOT NULL COMMENT '主键ID（雪花）',
  `task_id`         bigint        NOT NULL COMMENT '查重任务ID（关联check_task.id）',
  `sentence_index`  int           NOT NULL COMMENT '句子序号（正文剔除参考文献区块后，从0开始）',
  `sentence_text`   varchar(1000) NOT NULL COMMENT '句子文本（超1000字符截断）',
  `start_offset`    int           DEFAULT NULL COMMENT '句子在正文中的起始偏移',
  `similarity`      decimal(5,2)  NOT NULL COMMENT '句相似度（如82.35表示82.35%）',
  `is_citation`     tinyint       NOT NULL DEFAULT '0' COMMENT '是否引用句：1-句尾带[n]引注且文档存在参考文献区块（引用命中不计入总相似比）/ 0-普通命中',
  `hit_paper_id`    bigint        DEFAULT NULL COMMENT '命中来源论文ID（关联paper_info.id）',
  `hit_paper_title` varchar(200)  DEFAULT NULL COMMENT '命中来源论文标题（冗余，明细页直接展示）',
  `create_by`       bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`      tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`       bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`     datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_csh_task` (`task_id`),
  KEY `idx_csh_hit_paper` (`hit_paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='句级标红明细表（论文全生命周期P5）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `check_task`
--

DROP TABLE IF EXISTS `check_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `check_task` (
  `id` bigint NOT NULL COMMENT '任务唯一ID（雪花ID）',
  `paper_id` bigint NOT NULL COMMENT '对应的论文ID（关联paper_info.id）',
  `paper_version` int DEFAULT NULL COMMENT '论文版本号（创建任务时快照 paper_info.version；存量任务为 NULL）',
  `submit_id` bigint DEFAULT NULL COMMENT '论文提交记录ID（关联 paper_submit.id，创建任务时取最新一次提交；存量任务为 NULL）',
  `quota_charged` tinyint NOT NULL DEFAULT '0' COMMENT '是否已扣减学院月度额度（1-已扣，0-未扣/已返还）',
  `file_id` bigint DEFAULT NULL COMMENT '论文文件ID',
  `report_id` bigint DEFAULT NULL COMMENT '对应的查重报告ID（关联check_report.id，任务成功时生成）',
  `task_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '任务编号（如“CHECK20240512001”，唯一）',
  `check_rate` decimal(5,2) DEFAULT NULL COMMENT '查重重复率（如15.32表示15.32%）',
  `max_per_source` decimal(5,2) DEFAULT NULL COMMENT '单篇最大相似比（P5 新口径：所有来源中单篇贡献的最大重复占比，如 12.34 表示 12.34%。存量行为旧口径，不迁移）',
  `citation_rate` decimal(5,2) DEFAULT NULL COMMENT '引用率（P5：引用命中句字数 / 正文字数，如 3.21 表示 3.21%）',
  `check_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '任务状态（pending-待查重，checking-查重中，completed-查重成功，failure-查重失败）。默认值必须是字典内的值：漏赋值的 INSERT 若落进 0 这种字典外的值，按状态筛选的查询就永远看不到它',
  `start_time` datetime DEFAULT NULL COMMENT '任务开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '任务结束时间',
  `fail_reason` varchar(500) DEFAULT '' COMMENT '任务失败原因（如“文件解析错误”）',
  `report_path` varchar(500) DEFAULT NULL COMMENT '查重报告存储路径（本地/OSS）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id，即学生ID）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id，系统/管理员）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `priority` int DEFAULT '3' COMMENT '任务优先级（1-5，5最高）',
  `retry_count` int DEFAULT '0' COMMENT '任务重试次数',
  `check_rule_id` bigint DEFAULT NULL COMMENT '查重规则ID（关联check_rule.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_ct_paper_submit` (`paper_id`,`submit_id`),
  KEY `idx_check_task_rule_id` (`check_rule_id`),
  KEY `idx_check_task_delete_by` (`delete_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='查重任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `college`
--

DROP TABLE IF EXISTS `college`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `college` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `college_code` varchar(32) NOT NULL COMMENT '学院编码（唯一，如：CS-计算机学院、EE-电子工程学院）',
  `college_name` varchar(128) NOT NULL COMMENT '学院名称（如：计算机科学与技术学院）',
  `college_desc` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '学院描述（如：计算机学院）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_college_code` (`college_code`) COMMENT '学院编码唯一索引，避免重复'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学院表（存储学校下属所有学院信息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `compare_lib`
--

DROP TABLE IF EXISTS `compare_lib`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compare_lib` (
  `id` bigint NOT NULL COMMENT '比对库ID（主键）',
  `lib_name` varchar(100) NOT NULL COMMENT '比对库名称',
  `lib_code` varchar(50) NOT NULL COMMENT '比对库编码（唯一）',
  `lib_type` varchar(20) NOT NULL COMMENT '比对库类型（LOCAL-本地库，REMOTE-远程库）',
  `lib_url` varchar(500) NOT NULL COMMENT '库地址（本地路径或远程URL）',
  `is_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用（0-禁用，1-启用）',
  `description` varchar(500) DEFAULT '' COMMENT '比对库描述',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lib_code` (`lib_code`) COMMENT '比对库编码唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='比对库表（存储查重用的数据源信息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `config_change_log`
--

DROP TABLE IF EXISTS `config_change_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `old_value` text COMMENT '旧配置值',
  `new_value` text COMMENT '新配置值',
  `operator` varchar(50) DEFAULT NULL COMMENT '操作人',
  `operate_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '操作IP',
  PRIMARY KEY (`id`),
  KEY `idx_config_key` (`config_key`),
  KEY `idx_operate_time` (`operate_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置变更日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `conversation_members`
--

DROP TABLE IF EXISTS `conversation_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation_members` (
  `id` bigint NOT NULL,
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'MEMBER' COMMENT '成员角色(OWNER/ADMIN/MEMBER)',
  `joined_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `left_at` datetime DEFAULT NULL COMMENT '离开时间',
  `is_left` tinyint DEFAULT '0' COMMENT '是否已离开(0-未离开,1-已离开)',
  `unread_count` int DEFAULT '0' COMMENT '未读消息数量',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '是否删除(0-未删除,1-已删除)',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户头像',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_user` (`conversation_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_is_left` (`is_left`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话成员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `conversations`
--

DROP TABLE IF EXISTS `conversations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversations` (
  `id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话名称',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRIVATE' COMMENT '会话类型(PRIVATE/GROUP)',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话头像',
  `last_message_id` bigint DEFAULT NULL COMMENT '最后一条消息ID',
  `last_active_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `creator_id` bigint NOT NULL COMMENT '会话创建者ID',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后一条消息时间',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话描述',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '是否删除(0-未删除,1-已删除)',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_last_active_time` (`last_active_time`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `defense_batch`
--

DROP TABLE IF EXISTS `defense_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `defense_batch` (
  `id`            bigint        NOT NULL COMMENT '主键ID（雪花）',
  `batch_no`      varchar(32)   NOT NULL COMMENT '批次编号（人工或系统生成，唯一）',
  `batch_type`    varchar(16)   NOT NULL DEFAULT 'final' COMMENT '批次类型：mid_term-中期答辩 / final-终期答辩',
  `college_id`    bigint        DEFAULT NULL COMMENT '学院ID（关联college.id，可空表示全校批次）',
  `defense_date`  datetime      NOT NULL COMMENT '答辩时间',
  `location`      varchar(128)  DEFAULT NULL COMMENT '地点：线下填教室、线上填会议链接',
  `mode`          varchar(16)   NOT NULL DEFAULT 'offline' COMMENT '答辩方式：online-线上 / offline-线下',
  `leader_id`     bigint        DEFAULT NULL COMMENT '答辩组长ID（关联sys_user.id，组长主持并汇总结论）',
  `secretary_id`  bigint        DEFAULT NULL COMMENT '答辩秘书ID（关联sys_user.id，负责记录与线下回填）',
  `status`        tinyint       NOT NULL DEFAULT '0' COMMENT '批次状态：0-筹备中 / 1-进行中 / 2-已结束',
  `remark`        varchar(255)  DEFAULT NULL COMMENT '备注（可空）',
  `create_by`     bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`    tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`     bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`   datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_batch_type_date` (`batch_type`, `defense_date`),
  KEY `idx_college_status` (`college_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答辩批次表（论文全生命周期P3，中期/终期共用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `defense_group_member`
--

DROP TABLE IF EXISTS `defense_group_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `defense_group_member` (
  `id`          bigint       NOT NULL COMMENT '主键ID（雪花）',
  `batch_id`    bigint       NOT NULL COMMENT '批次ID（关联defense_batch.id）',
  `teacher_id`  bigint       NOT NULL COMMENT '教师ID（关联sys_user.id）',
  `group_role`  varchar(16)  NOT NULL DEFAULT 'member' COMMENT '组角色：leader-组长 / member-成员',
  `create_by`   bigint       DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint       DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint      DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`   bigint       DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime     DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_teacher` (`batch_id`, `teacher_id`),
  KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答辩组成员表（论文全生命周期P3）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `defense_record`
--

DROP TABLE IF EXISTS `defense_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `defense_record` (
  `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
  `batch_id`       bigint        NOT NULL COMMENT '批次ID（关联defense_batch.id）',
  `paper_id`       bigint        NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `student_id`     bigint        NOT NULL COMMENT '学生ID（关联sys_user.id）',
  `defense_order`  int           DEFAULT NULL COMMENT '答辩顺序号（同批次内人工编排，可空）',
  `score`          decimal(5,2)  DEFAULT NULL COMMENT '综合得分（0-100，组长汇总后写入）',
  `grade`          varchar(8)    DEFAULT NULL COMMENT '等级：优/良/中/及格/不及格',
  `result`         varchar(16)   DEFAULT NULL COMMENT '结论：pass-通过 / fail-不通过 / postpone-缓辩',
  `comment`        text          COMMENT '答辩记录（纯文本，入库前已做XSS清洗）',
  `attachment_id`   bigint        DEFAULT NULL COMMENT '线下答辩表扫描件（关联file_info.id，可空）',
  `submit_by`      bigint        DEFAULT NULL COMMENT '结论提交人ID（通常为组长，关联sys_user.id）',
  `submit_time`    datetime      DEFAULT NULL COMMENT '结论提交时间',
  `create_by`      bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`      bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`    datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_paper` (`batch_id`, `paper_id`),
  KEY `idx_paper_id` (`paper_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答辩记录表（论文全生命周期P3）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_info`
--

DROP TABLE IF EXISTS `file_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_info` (
  `id` bigint NOT NULL COMMENT '文件ID',
  `original_filename` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `file_size_desc` varchar(50) DEFAULT NULL COMMENT '文件大小描述',
  `storage_path` varchar(500) DEFAULT NULL COMMENT '存储路径',
  `page_count` int DEFAULT NULL COMMENT '文件页数',
  `access_url` varchar(500) DEFAULT NULL COMMENT '访问URL',
  `word_count` int DEFAULT NULL COMMENT '字数统计',
  `md5` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件MD5',
  `upload_time` datetime NOT NULL COMMENT '上传时间',
  `upload_user_id` varchar(50) DEFAULT NULL COMMENT '上传用户ID',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `biz_id` varchar(50) DEFAULT NULL COMMENT '业务ID',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_md5` (`md5`) USING BTREE,
  KEY `idx_file_info_md5` (`md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `guidance_record`
--

DROP TABLE IF EXISTS `guidance_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `guidance_record` (
  `id`               bigint       NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`         bigint       NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `student_id`       bigint       NOT NULL COMMENT '学生ID（关联sys_user.id）',
  `teacher_id`       bigint       NOT NULL COMMENT '指导教师ID（关联sys_user.id）',
  `round`            int          NOT NULL COMMENT '指导轮次（同论文内自增，(paper_id,round)唯一）',
  `guidance_type`    varchar(16)  NOT NULL DEFAULT 'online' COMMENT '指导方式：online-线上 / offline-线下',
  `guidance_time`    datetime     NOT NULL COMMENT '指导时间',
  `content`          text         COMMENT '指导内容（纯文本，入库前已做XSS清洗）',
  `student_feedback` text         COMMENT '学生反馈（确认时填写，可空）',
  `attachment_id`    bigint       DEFAULT NULL COMMENT '附件ID（关联file_info.id，可空）',
  `confirm_status`   tinyint      NOT NULL DEFAULT '0' COMMENT '确认状态：0-待学生确认 / 1-已确认 / 2-有异议',
  `confirm_time`     datetime     DEFAULT NULL COMMENT '学生确认时间',
  `create_by`        bigint       DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`      datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint       DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`      datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`       tinyint      DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`        bigint       DEFAULT NULL COMMENT '删除人ID',
  `delete_time`      datetime     DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_round` (`paper_id`, `round`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指导记录表（论文全生命周期P1）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_article`
--

DROP TABLE IF EXISTS `knowledge_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_article` (
  `id` bigint NOT NULL COMMENT '雪花ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `category_id` bigint DEFAULT NULL,
  `tags` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `view_count` bigint DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'DRAFT',
  `sort` int DEFAULT '0',
  `author_id` bigint DEFAULT NULL,
  `author_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_category`
--

DROP TABLE IF EXISTS `knowledge_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_category` (
  `id` bigint NOT NULL COMMENT '雪花ID',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `icon` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `sort` int DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `major`
--

DROP TABLE IF EXISTS `major`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `major` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `major_code` varchar(20) NOT NULL COMMENT '专业编码（如080901-计算机科学与技术，唯一）',
  `major_name` varchar(50) NOT NULL COMMENT '专业名称（如计算机科学与技术）',
  `college_id` bigint NOT NULL COMMENT '所属学院ID（关联college.id，如软件学院、信息学院）',
  `major_desc` varchar(500) DEFAULT NULL COMMENT '专业描述（可选，如“培养计算机领域高级人才”）',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID（关联sys_user.id）',
  `current_advisor_count` int DEFAULT '0' COMMENT '当前指导任务数',
  `max_advisor_count` int DEFAULT '10' COMMENT '最大指导任务上限',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_code` (`major_code`),
  KEY `idx_college_id` (`college_id`),
  KEY `idx_major_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `major_subject`
--
-- 专业↔学科领域 多对多映射。用途：论文提交页按学生专业过滤学科领域候选。
-- 一个专业可对应多个学科（如 计算机科学与技术 → ENG-CS / ENG-SE / ENG-AI），
-- 一个学科也可被多个专业共用；没有映射的专业，提交页退回展示整棵树。
--
-- 数据由 种子数据.sql 段 3B 维护。复合唯一键 + 物理删除（不用软删，否则重加同一对会撞键）。

DROP TABLE IF EXISTS `major_subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `major_subject` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `major_id` bigint NOT NULL COMMENT '专业ID（关联major.id）',
  `subject_id` bigint NOT NULL COMMENT '学科领域ID（关联subject.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_subject` (`major_id`,`subject_id`),
  KEY `idx_major_subject_subject_id` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业↔学科领域映射表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `message_template`
--

DROP TABLE IF EXISTS `message_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_template` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `template_code` varchar(100) DEFAULT NULL COMMENT '模板编码',
  `template_name` varchar(100) DEFAULT NULL COMMENT '模板名称',
  `template_type` varchar(50) DEFAULT NULL COMMENT '模板类型：assignment-分配, confirm-确认, reject-拒绝',
  `title_template` varchar(200) NOT NULL COMMENT '标题模板',
  `content_template` text COMMENT '内容模板',
  `is_active` tinyint DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='模版信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `messages` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sender_id` bigint NOT NULL COMMENT '发送者ID',
  `receiver_id` bigint NOT NULL COMMENT '接收者ID',
  `conversation_id` bigint DEFAULT NULL COMMENT '会话ID',
  `message_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息类型(PRIVATE/NOTIFICATION/GROUP/BROADCAST)',
  `content_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容类型(TEXT/FILE/IMAGE/VOICE)',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '消息标题',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
  `attachments` json DEFAULT NULL COMMENT '附件列表',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'SENT' COMMENT '消息状态(SENT/DELIVERED/READ)',
  `related_id` bigint DEFAULT NULL COMMENT '关联ID',
  `related_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联类型',
  `sent_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `delivered_time` datetime DEFAULT NULL COMMENT '送达时间',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '是否删除(0-未删除,1-已删除)',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_receiver_id` (`receiver_id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_sent_time` (`sent_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2058519055907819523 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `paper_advisor_rel`
--

DROP TABLE IF EXISTS `paper_advisor_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_advisor_rel` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `paper_id` bigint NOT NULL COMMENT '论文ID（关联paper_submit.id，唯一）',
  `advisor_id` bigint NOT NULL COMMENT '指导老师ID（关联sys_user.id）',
  `advisor_round` int NOT NULL COMMENT '指导轮次',
  `student_major_id` bigint NOT NULL COMMENT '学生所属专业ID（关联major.id，与paper_submit一致）',
  `advisor_major_id` bigint NOT NULL COMMENT '指导老师关联的专业ID（关联major.id，与sys_user_major一致）',
  `rel_status` tinyint NOT NULL DEFAULT '1' COMMENT '关联状态：1-有效（指导中/已完成），0-无效（驳回/取消）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_id` (`paper_id`) COMMENT '论文ID唯一，避免重复关联',
  KEY `idx_advisor_major_status` (`advisor_id`,`advisor_major_id`,`rel_status`),
  KEY `idx_paper_id_status` (`paper_id`,`rel_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文-指导老师关联表（用于精准统计指导任务数）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `paper_attachment`
--

DROP TABLE IF EXISTS `paper_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_attachment` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `paper_id` bigint NOT NULL COMMENT '关联论文ID（关联paper_submit.id）',
  `student_id` bigint NOT NULL COMMENT '附件上传者ID（学生ID，关联sys_user.id）',
  `advisor_id` bigint DEFAULT NULL COMMENT '关联指导老师ID（关联sys_user.id）',
  `original_filename` varchar(255) NOT NULL COMMENT '原始文件名（如“论文初稿.pdf”）',
  `storage_path` varchar(512) NOT NULL COMMENT '文件存储路径（本地/OSS路径）',
  `file_type` varchar(50) NOT NULL COMMENT '文件MIME类型（如application/pdf）',
  `file_size` bigint NOT NULL COMMENT '文件大小（单位：字节）',
  `file_md5` varchar(32) NOT NULL COMMENT '文件MD5值（32位小写，用于完整性校验）',
  `attachment_type` varchar(50) DEFAULT NULL COMMENT '附件类型（如“论文初稿”“查重报告”）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_md5` (`file_md5`) COMMENT '文件MD5唯一，防止重复上传'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文附件表（存储论文相关附件信息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `paper_attachment_rel`
--

DROP TABLE IF EXISTS `paper_attachment_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_attachment_rel` (
  `id` bigint NOT NULL COMMENT '关联ID',
  `paper_id` bigint DEFAULT NULL COMMENT '论文ID（关联paper_submit.id）',
  `file_id` bigint DEFAULT NULL COMMENT '文件ID（关联sys_attachment.id）',
  `attachment_use` varchar(50) DEFAULT NULL COMMENT '附件用途（论文初稿/终稿/修改说明）',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID（学生ID）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文-附件关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `paper_check_signoff`
--

DROP TABLE IF EXISTS `paper_check_signoff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_check_signoff` (
  `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`       bigint        NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `task_id`        bigint        NOT NULL COMMENT '查重任务ID（关联check_task.id，签核对象）',
  `report_id`      bigint        DEFAULT NULL COMMENT '查重报告ID（关联check_report.id，可空）',
  `advisor_id`     bigint        NOT NULL COMMENT '签核导师ID（关联sys_user.id，必须是论文teacher_id本人）',
  `qualified`      tinyint       NOT NULL COMMENT '签核结论：1-合格放行 / 0-不合格退回修改',
  `guidance_score` decimal(5,2)  DEFAULT NULL COMMENT '指导评分（0-100，成绩加权合成的指导分数据源）',
  `comment`        varchar(500)  DEFAULT NULL COMMENT '签核意见',
  `sign_time`      datetime      NOT NULL COMMENT '签核时间',
  `create_by`      bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`      bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`    datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_task` (`paper_id`, `task_id`),
  KEY `idx_pcs_advisor` (`advisor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='查重报告导师签核表（论文全生命周期P5）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `paper_info`
--

DROP TABLE IF EXISTS `paper_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_info` (
  `id` bigint NOT NULL COMMENT '论文唯一ID（雪花ID）',
  `subject_code` varchar(50) DEFAULT NULL COMMENT '学科领域（subject.code 的冗余列，供展示与既有读取方，二者按 code 对齐）',
  `subject_id` bigint DEFAULT NULL COMMENT '学科领域ID（关联subject.id）。写入时由 SubjectService.findIdByCode 反查 subject_code 得到',
  `paper_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '论文标题',
  `student_id` bigint DEFAULT NULL COMMENT '提交论文的学生ID（关联sys_user.id）',
  `author` varchar(255) DEFAULT NULL COMMENT '论文作者',
  `college_id` bigint DEFAULT NULL COMMENT '学院ID',
  `major_id` bigint DEFAULT NULL COMMENT '专业ID',
  `lib_id` bigint DEFAULT NULL COMMENT '对比库ID（关联compare_lib.id）',
  `teacher_id` bigint DEFAULT NULL COMMENT '指导教师ID（关联sys_user.id）',
  `teacher_name` varchar(100) DEFAULT NULL COMMENT '指导老师姓名',
  `allocation_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '分配类型（auto-自动分配, manual-手动分配）',
  `allocation_status` varchar(50) DEFAULT NULL COMMENT '分配状态（pending-待确认, confirmed-已确认, rejected-已拒绝）',
  `allocation_time` datetime DEFAULT NULL COMMENT '分配时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `paper_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '论文类型（graduation-毕业论文，course-课程论文,other-其他）',
  `paper_abstract` text COMMENT '论文摘要',
  `file_id` varchar(255) DEFAULT NULL COMMENT '文件ID',
  `file_md5` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '文件MD5值（防重复上传）',
  `page_count` int DEFAULT NULL COMMENT '论文页数',
  `word_count` int DEFAULT NULL COMMENT '论文字数',
  `paper_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'pending' COMMENT '论文状态（pending-待分配，assigned-已分配导师，checking-待查重，auditing-待审核，completed-审核通过，rejected-审核不通过，withdrawn-已取消）。默认值必须是字典内的值，不能是 0：论文提交后就是待分配，漏赋值的 INSERT 落进字典外的值，会同时从待分配列表和所有状态统计里消失',
  `workflow_step` int NOT NULL DEFAULT '0' COMMENT '当前多级初审步骤：0-未进入工作流 / 1..N-第N步审批中（review_workflow.steps 顺序）',
  `similarity_rate` decimal(5,2) DEFAULT NULL COMMENT '相似度',
  `degree_type` varchar(20) DEFAULT 'undergraduate' COMMENT '学位类型（P5 检测策略集匹配维度：undergraduate-本科 / master-硕士 / doctor-博士，对应字典 degree_type；存量默认本科）',
  `check_result` text COMMENT '查重结果',
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '论文提交时间',
  `check_time` datetime DEFAULT NULL COMMENT '查重时间',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '存储路径',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID（关联sys_user.id，即学生ID）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID（关联sys_user.id，学生/教师/管理员）',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `final_score` decimal(5,2) DEFAULT NULL COMMENT '最终成绩',
  `check_engine_type` varchar(20) DEFAULT NULL COMMENT '查重引擎类型(local/third_party/deep_learning)',
  `check_completed` tinyint DEFAULT '0' COMMENT '查重是否完成(0-未完成,1-已完成)',
  `check_source` varchar(20) DEFAULT NULL COMMENT '查重来源(school-校内,external-外部)',
  `version` int DEFAULT '1' COMMENT '论文版本号',
  `keywords` varchar(255) DEFAULT NULL COMMENT '论文关键词',
  `parent_paper_id` bigint DEFAULT NULL COMMENT '父版本论文ID',
  `content_path` varchar(255) DEFAULT NULL COMMENT '论文正文在MinIO中的存储路径',
  `segmented_path` varchar(255) DEFAULT NULL COMMENT 'IK分词后文本在MinIO中的存储路径',
  `library_admitted` tinyint NOT NULL DEFAULT '0' COMMENT '比对库准入标记（1-已准入，0-未准入；新论文归档时置 1）',
  `internal_check_result` text COMMENT '校内查重结果（JSON格式）',
  `third_party_check_result` text COMMENT '第三方查重结果（JSON格式）',
  `audit_comment` text COMMENT '审核意见（初审/退回意见，独立于 check_result 查重结果存储）',
  PRIMARY KEY (`id`),
  KEY `idx_paper_info_student_title` (`student_id`,`paper_title`),
  KEY `idx_paper_info_file_id` (`file_id`),
  KEY `idx_paper_info_student_id` (`student_id`),
  KEY `idx_paper_info_teacher_status` (`teacher_id`,`paper_status`,`is_deleted`),
  KEY `idx_paper_info_status` (`paper_status`,`is_deleted`),
  KEY `idx_paper_info_delete_by` (`delete_by`),
  KEY `idx_paper_info_lib_id` (`lib_id`),
  KEY `idx_paper_info_subject_id` (`subject_id`),
  KEY `idx_pi_degree` (`degree_type`),
  KEY `idx_pi_parent_paper_id` (`parent_paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `thesis_archive`
--

DROP TABLE IF EXISTS `thesis_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `thesis_archive` (
  `id` bigint NOT NULL COMMENT '归档记录ID（雪花ID）',
  `archive_no` varchar(50) NOT NULL COMMENT '归档编号（唯一，如 ARC20261022153000123）',
  `paper_id` bigint NOT NULL COMMENT '论文ID（关联 paper_info.id）',
  `paper_version` int NOT NULL DEFAULT '1' COMMENT '归档时的论文版本号',
  `submit_id` bigint DEFAULT NULL COMMENT '归档时对应的提交记录ID（paper_submit.id）',
  `paper_title` varchar(200) DEFAULT NULL COMMENT '论文标题（快照）',
  `author` varchar(255) DEFAULT NULL COMMENT '作者（快照）',
  `student_id` bigint DEFAULT NULL COMMENT '学生ID（快照）',
  `student_no` varchar(50) DEFAULT NULL COMMENT '学号（快照，取 sys_user.username）',
  `student_name` varchar(100) DEFAULT NULL COMMENT '学生姓名（快照）',
  `teacher_id` bigint DEFAULT NULL COMMENT '指导教师ID（快照）',
  `teacher_name` varchar(100) DEFAULT NULL COMMENT '指导教师姓名（快照）',
  `college_id` bigint DEFAULT NULL COMMENT '学院ID（快照）',
  `college_name` varchar(100) DEFAULT NULL COMMENT '学院名称（快照）',
  `major_id` bigint DEFAULT NULL COMMENT '专业ID（快照）',
  `major_name` varchar(100) DEFAULT NULL COMMENT '专业名称（快照）',
  `degree_type` varchar(20) DEFAULT NULL COMMENT '学位类型（快照）',
  `file_id` bigint DEFAULT NULL COMMENT '归档文件ID（快照）',
  `file_path` varchar(500) DEFAULT NULL COMMENT '归档文件存储路径（快照）',
  `file_md5` varchar(100) DEFAULT NULL COMMENT '归档文件MD5（快照）',
  `check_task_id` bigint DEFAULT NULL COMMENT '归档时采用的已完成查重任务ID',
  `check_report_id` bigint DEFAULT NULL COMMENT '归档时采用的查重报告ID',
  `report_no` varchar(50) DEFAULT NULL COMMENT '查重报告编号（快照）',
  `report_hash` varchar(128) DEFAULT NULL COMMENT '报告验真哈希（check_report.content_hash 快照）',
  `check_rate` decimal(5,2) DEFAULT NULL COMMENT '查重率/总相似比（快照）',
  `max_per_source` decimal(5,2) DEFAULT NULL COMMENT '单篇最大相似比（快照）',
  `citation_rate` decimal(5,2) DEFAULT NULL COMMENT '引用率（快照）',
  `check_source` varchar(20) DEFAULT NULL COMMENT '查重来源（快照）',
  `archive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  `archive_by` bigint DEFAULT NULL COMMENT '归档操作人ID',
  `archive_remark` varchar(500) DEFAULT '' COMMENT '归档备注',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ta_archive_no` (`archive_no`),
  UNIQUE KEY `uk_ta_paper_version` (`paper_id`,`paper_version`),
  KEY `idx_ta_college_time` (`college_id`,`archive_time`),
  KEY `idx_ta_student` (`student_id`),
  KEY `idx_ta_delete_by` (`delete_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文归档表（P4：定稿→归档证据快照）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `paper_library`
--

DROP TABLE IF EXISTS `paper_library`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_library` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `title` varchar(255) DEFAULT NULL COMMENT '论文标题',
  `content_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '论文正文在MinIO中的存储路径',
  `segmented_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT ' IK分词后文本在MinIO中的存储路径',
  `author` varchar(100) DEFAULT NULL COMMENT '论文作者',
  `student_num` varchar(100) DEFAULT NULL COMMENT '学号',
  `major` varchar(255) DEFAULT NULL COMMENT '专业',
  `grade` varchar(50) DEFAULT NULL COMMENT '年级',
  `keyword` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '关键词',
  `summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '摘要',
  `similarity` decimal(5,2) DEFAULT NULL COMMENT '相似度',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除：0-正常，1-删除',
  PRIMARY KEY (`id`),
  KEY `idx_title` (`title`) COMMENT '标题索引',
  KEY `idx_create_time` (`create_time`) COMMENT '入库时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本地论文参考库';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `paper_status_log`
--

DROP TABLE IF EXISTS `paper_status_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_status_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `paper_id` bigint NOT NULL COMMENT '论文提交ID（关联paper_submit.id）',
  `old_status` varchar(50) DEFAULT NULL COMMENT '变更前状态（如0-草稿）',
  `new_status` varchar(50) DEFAULT NULL COMMENT '变更后状态（如1-已提交）',
  `operate_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '操作IP地址（用于安全追溯）',
  `status_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态变更原因（如“学生点击提交按钮”“审核通过：论文符合要求”）',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文状态变更日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `paper_submit`
--

DROP TABLE IF EXISTS `paper_submit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_submit` (
  `id` bigint NOT NULL COMMENT '论文提交记录ID',
  `paper_id` bigint DEFAULT NULL COMMENT '论文ID',
  `major_id` bigint DEFAULT NULL COMMENT '专业ID（关联major_id）',
  `student_id` bigint DEFAULT NULL COMMENT '学生ID',
  `paper_title` varchar(200) DEFAULT NULL COMMENT '论文标题',
  `paper_type` varchar(50) DEFAULT NULL COMMENT '论文类型',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级（冗余自 sys_user.grade）',
  `submit_version` int DEFAULT NULL COMMENT '提交版本',
  `file_id` varchar(255) DEFAULT NULL COMMENT '文件ID',
  `file_md5` varchar(255) DEFAULT NULL COMMENT '文件MD5',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `check_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '查重状态（pending-待查重, checking-查重中, completed-已完成, failed-查重失败）',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_by` bigint NOT NULL COMMENT '创建人ID',
  `similarity_rate` decimal(5,2) DEFAULT NULL COMMENT '相似度',
  `check_result` text COMMENT '查重结果',
  `check_report_id` bigint DEFAULT NULL COMMENT '查重报告ID（关联check_task.id）',
  `check_sim_hash` bigint DEFAULT NULL COMMENT '论文SimHash指纹（用于查重比对）',
  `check_time` datetime DEFAULT NULL COMMENT '查重时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `advisor_suggestion` varchar(2000) DEFAULT NULL COMMENT '指导老师审核意见',
  `status` int DEFAULT NULL COMMENT '状态（已废弃）',
  `advisor_id` bigint DEFAULT NULL COMMENT '导师ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文提交表（存储用户提交的论文及审核信息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `re_submit_record`
--

DROP TABLE IF EXISTS `re_submit_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `re_submit_record` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `created_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `paper_id` bigint NOT NULL COMMENT '原论文提交ID（关联paper_submit.id）',
  `re_submit_paper_id` bigint NOT NULL COMMENT '重提后的论文提交ID（关联paper_submit.id）',
  `re_submit_reason` varchar(500) NOT NULL COMMENT '重提原因（如“重复率超标：35%>30%”“审核驳回：格式错误”）',
  `re_submit_count` tinyint NOT NULL COMMENT '本次重提是第几次（1=第一次重提，2=第二次，...）',
  PRIMARY KEY (`id`),
  KEY `idx_paper_id` (`paper_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论文重提记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review_assignment`
--

DROP TABLE IF EXISTS `review_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_assignment` (
  `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`       bigint        NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `submit_version` int           NOT NULL DEFAULT '1' COMMENT '评阅针对的论文版本号（paper_submit.submit_version，版本演进后历史评阅不失效）',
  `reviewer_id`    bigint        NOT NULL COMMENT '评阅教师ID（关联sys_user.id）',
  `review_type`    varchar(16)   NOT NULL DEFAULT 'peer' COMMENT '评阅类型：advisor-导师评阅 / peer-交叉评阅 / blind-盲审（查询层脱敏）',
  `method`         varchar(16)   NOT NULL DEFAULT 'online' COMMENT '评阅方式：online-线上 / offline-线下（结果回填）',
  `status`         tinyint       NOT NULL DEFAULT '0' COMMENT '状态：0-待评阅 / 1-评阅中 / 2-已提交 / 3-已超时转派',
  `score`          decimal(5,2)  DEFAULT NULL COMMENT '评分（0-100，线下任务可提交时回填）',
  `comment`        text          COMMENT '评阅意见（线下任务可为空，以扫描件为准）',
  `attachment_id`  bigint        DEFAULT NULL COMMENT '线下评阅表扫描件（关联file_info.id，可空）',
  `deadline`       datetime      DEFAULT NULL COMMENT '评阅截止时间（超时扫描挂定时任务）',
  `assign_type`    varchar(16)   NOT NULL DEFAULT 'manual' COMMENT '分配方式：auto-自动 / manual-手动',
  `create_by`      bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`      bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`    datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_version_reviewer` (`paper_id`, `submit_version`, `reviewer_id`),
  KEY `idx_reviewer_status` (`reviewer_id`, `status`),
  KEY `idx_paper_id` (`paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评阅任务表（论文全生命周期P2）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review_draft`
--

DROP TABLE IF EXISTS `review_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_draft` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花算法生成）',
  `paper_id` bigint NOT NULL COMMENT '论文ID',
  `teacher_id` bigint NOT NULL COMMENT '教师ID',
  `review_status` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审核状态',
  `review_opinion` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审核意见',
  `review_attach` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审核附件路径',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '软删除标记',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_paper_teacher` (`paper_id`,`teacher_id`,`is_deleted`),
  KEY `idx_teacher` (`teacher_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审核草稿表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review_record`
--

DROP TABLE IF EXISTS `review_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_record` (
  `id` bigint NOT NULL COMMENT '审核记录唯一ID（雪花ID）',
  `paper_id` bigint NOT NULL COMMENT '对应的论文ID（关联paper_info.id）',
  `task_id` bigint NOT NULL COMMENT '对应的查重任务ID（关联check_task.id）',
  `teacher_id` bigint NOT NULL COMMENT '执行审核的教师ID（关联sys_user.id）',
  `review_status` varchar(50) NOT NULL,
  `review_opinion` text COMMENT '审核意见（富文本）',
  `suggested_modifications` text COMMENT '建议修改点（JSON 格式）',
  `review_attach` varchar(500) DEFAULT NULL COMMENT '审核附件路径（如修改建议文档）',
  `review_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id，即教师ID）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id，教师/管理员）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审核记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review_template`
--

DROP TABLE IF EXISTS `review_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_template` (
  `id` bigint NOT NULL COMMENT '模板ID',
  `title` varchar(255) DEFAULT NULL COMMENT '模板标题',
  `category` varchar(100) DEFAULT NULL COMMENT '模板分类',
  `content` text COMMENT '模板内容',
  `scenarios` json DEFAULT NULL COMMENT '使用场景',
  `is_public` tinyint(1) DEFAULT NULL COMMENT '是否公开',
  `usage_count` int DEFAULT NULL COMMENT '使用次数',
  `last_used` datetime DEFAULT NULL COMMENT '最后使用时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_deleted` int DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审核意见模板表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review_workflow`
--

DROP TABLE IF EXISTS `review_workflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_workflow` (
  `id` bigint NOT NULL COMMENT '工作流ID',
  `name` varchar(255) DEFAULT NULL COMMENT '工作流名称',
  `description` text COMMENT '工作流描述',
  `steps` json DEFAULT NULL COMMENT '工作流步骤（JSON格式）',
  `enabled` tinyint(1) DEFAULT NULL COMMENT '启用状态',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审核工作流表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review_workflow_progress`
--

DROP TABLE IF EXISTS `review_workflow_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_workflow_progress` (
  `id`          bigint    NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`    bigint    NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `step_index`  int       NOT NULL COMMENT '步骤序号（1-based，对应 review_workflow.steps 数组下标+1）',
  `approver_id` bigint    NOT NULL COMMENT '审批人ID（关联sys_user.id）',
  `vote_result` tinyint   NOT NULL COMMENT '投票结果：1-通过 / 2-驳回',
  `opinion`     text      COMMENT '审批意见（可空）',
  `vote_time`   datetime  NOT NULL COMMENT '投票时间',
  `create_by`   bigint    DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime  DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint    DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint   DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`   bigint    DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime  DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_step_approver` (`paper_id`, `step_index`, `approver_id`),
  KEY `idx_paper_step` (`paper_id`, `step_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多级初审投票表（论文全生命周期P2）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rule_lib_relation`
--

DROP TABLE IF EXISTS `rule_lib_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rule_lib_relation` (
  `id` bigint NOT NULL COMMENT '关联记录ID（主键）',
  `rule_id` bigint NOT NULL COMMENT '查重规则ID（关联check_rule.id）',
  `lib_id` bigint NOT NULL COMMENT '比对库ID（关联compare_lib.id）',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='查重规则-比对库关联表（多对多中间表）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `similarity_threshold`
--

DROP TABLE IF EXISTS `similarity_threshold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `similarity_threshold` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `global_threshold` int DEFAULT NULL COMMENT '全局相似度阈值',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_deleted` int DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='相似度阈值设置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_group`
--

DROP TABLE IF EXISTS `student_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_group` (
  `id` bigint NOT NULL COMMENT '分组ID',
  `name` varchar(255) DEFAULT NULL COMMENT '分组名称',
  `description` text COMMENT '分组描述',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_deleted` int DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生分组表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_group_student_rel`
--

DROP TABLE IF EXISTS `student_group_student_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_group_student_rel` (
  `id` bigint NOT NULL COMMENT '关系ID',
  `group_id` bigint DEFAULT NULL COMMENT '分组ID',
  `student_id` bigint DEFAULT NULL COMMENT '学生ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_deleted` int DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生分组与学生关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_info`
--

DROP TABLE IF EXISTS `student_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `college_id` bigint DEFAULT NULL COMMENT '学院ID（关联college.id）',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级',
  `major` varchar(100) DEFAULT NULL COMMENT '专业',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级',
  `college_name` varchar(100) DEFAULT NULL COMMENT '学院名称',
  `major_id` bigint DEFAULT NULL COMMENT '专业ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '软删除标志（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_college_name` (`college_name`),
  KEY `idx_major_id` (`major_id`),
  KEY `idx_grade` (`grade`),
  KEY `idx_college_id` (`college_id`),
  KEY `idx_student_info_user_id` (`user_id`),
  KEY `idx_student_info_college_id` (`college_id`),
  CONSTRAINT `fk_student_info_college` FOREIGN KEY (`college_id`) REFERENCES `college` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subject`
--
-- 学科领域主数据。取代原先 sys_dict_data 里 dict_type='subject_field' 的那 100 行字符串树
-- （层级靠 dict_value 里的连字符约定出来）。层级改用 parent_id 表达，level 是它的冗余投影。
--
-- id 是写死的 13 位（见 种子数据.sql 段 3B），不用雪花：13 位不过 2^53，规避 19 位 ID 过 JS
-- 丢末位的整类问题。对外自然键是 code（如 ENG、ENG-CS），paper_info.subject_code 存的就是它 ——
-- 所以那 100 行存量编码一个都不用改，前端的树选择器也不用改。
--
-- uk_subject_code 不含 is_deleted（照 uk_college_code / uk_major_code 的写法），所以学科不做软删除：
-- 停用走 is_enabled=0；真要删必须先过引用检查（paper_info / category_similarity_threshold /
-- teacher_subject / major_subject）再物理删除，否则「软删后重插同 code」会直接撞唯一键。

DROP TABLE IF EXISTS `subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subject` (
  `id` bigint NOT NULL COMMENT '主键ID（写死的13位，见 种子数据.sql 段 3B）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `parent_id` bigint DEFAULT NULL COMMENT '父学科ID（一级门类为NULL，二级指向所属门类id）',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '层级：1-一级门类，2-二级学科（由 parent_id 推导，不接受前端传值）',
  `code` varchar(50) NOT NULL COMMENT '学科编码（唯一，如 ENG、ENG-CS）。对外稳定标识，paper_info.subject_code 存它；创建后不可改',
  `name` varchar(128) NOT NULL COMMENT '学科名称（如 工学、人工智能），可改',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '同层排序',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用（0-停用，1-启用）：停用后不再出现在选择器/阈值候选中，已引用的数据不受影响',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_code` (`code`),
  KEY `idx_subject_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学科领域表（取代 sys_dict_data 的 subject_field 字符串树）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_backup_log`
--

DROP TABLE IF EXISTS `sys_backup_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_backup_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备份文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '备份状态(SUCCESS/FAILED)',
  `start_time` datetime DEFAULT NULL COMMENT '备份开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '备份结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `backup_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '备份类型(AUTO/MANUAL)',
  `is_deleted` int DEFAULT '0' COMMENT '软删除标记(0-未删除,1-已删除)',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
  `is_default` char(1) DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL COMMENT '字典主键',
  `dict_name` varchar(100) DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_error_log`
--

DROP TABLE IF EXISTS `sys_error_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_error_log` (
  `id` bigint NOT NULL COMMENT '异常日志唯一ID（雪花ID）',
  `error_code` varchar(50) NOT NULL COMMENT '错误编码（如“FILE_PARSE_ERROR”）',
  `error_msg` text NOT NULL COMMENT '错误信息（异常堆栈）',
  `related_id` bigint DEFAULT '0' COMMENT '关联业务ID（如paper_id、task_id，无则填0）',
  `related_type` varchar(50) DEFAULT 'system' COMMENT '关联业务类型（如“paper”“task”）',
  `error_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '异常发生时间',
  `handle_status` tinyint NOT NULL DEFAULT '0' COMMENT '处理状态（0-未处理，1-已处理）',
  `handle_remark` varchar(500) DEFAULT NULL COMMENT '处理备注',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_by` bigint DEFAULT '0' COMMENT '处理人ID（关联sys_user.id，未处理填0）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id，系统默认填管理员ID）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id，处理人ID）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统异常日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL COMMENT '登录日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID（关联sys_user.id）',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '登录用户名',
  `login_ip` varchar(50) DEFAULT NULL COMMENT '登录IP',
  `login_location` varchar(100) DEFAULT NULL COMMENT '登录地点',
  `login_device` varchar(200) DEFAULT NULL COMMENT '登录设备',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  `login_result` tinyint DEFAULT NULL COMMENT '登录结果（1=成功，0=失败）',
  `fail_reason` varchar(200) DEFAULT NULL COMMENT '失败原因（如用户名密码错误）',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  KEY `idx_login_log_user_time` (`user_id`,`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户登录历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID（关联sys_user.id）',
  `notice_type` tinyint NOT NULL COMMENT '通知类型：1-提交成功，2-查重完成，3-审核结果，4-截止提醒',
  `notice_title` varchar(100) NOT NULL COMMENT '通知标题',
  `notice_content` text NOT NULL COMMENT '通知内容（含关键信息，如“您的论文重复率为12.3%”）',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读：0-未读，1-已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `priority` int DEFAULT '0' COMMENT '优先级：0-普通，1-重要，2-紧急',
  `related_id` bigint DEFAULT NULL COMMENT '相关业务ID',
  `related_type` varchar(50) DEFAULT NULL COMMENT '相关业务类型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_is_read` (`user_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统站内信表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_notice_log`
--

DROP TABLE IF EXISTS `sys_notice_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `to_email` varchar(50) DEFAULT NULL COMMENT '用户邮箱',
  `subject` varchar(255) DEFAULT NULL COMMENT '邮件主题',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notice_type` varchar(50) DEFAULT NULL COMMENT '通知类型',
  `success` tinyint(1) DEFAULT NULL COMMENT '是否成功',
  `error_msg` varchar(255) DEFAULT NULL COMMENT '错误信息',
  `related_id` bigint DEFAULT NULL COMMENT '关联业务ID',
  `related_type` varchar(50) DEFAULT NULL COMMENT '关联业务类型',
  `send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 STATS_PERSISTENT=0;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_operation_log`
--

DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL COMMENT '日志唯一ID（雪花ID）',
  `user_id` bigint NOT NULL COMMENT '操作人ID（关联sys_user.id，匿名操作填0）',
  `user_name` varchar(50) NOT NULL COMMENT '操作人姓名（冗余存储）',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型（login-登录，paper_upload-论文上传等）',
  `operation_desc` varchar(200) NOT NULL COMMENT '操作描述',
  `operation_param` text COMMENT '操作参数（JSON格式）',
  `ip_address` varchar(50) NOT NULL COMMENT '操作IP地址',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `execution_time` bigint DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id，即操作人ID）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id，与create_by一致）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `user_type` varchar(20) DEFAULT '未知用户' COMMENT '操作用户类型',
  `target` varchar(255) DEFAULT '未知对象' COMMENT '操作对象',
  `status` tinyint DEFAULT '1' COMMENT '操作状态（1成功，0失败）',
  `details` text COMMENT '详细信息',
  `trace_id` varchar(32) DEFAULT NULL COMMENT '请求追踪ID',
  PRIMARY KEY (`id`),
  KEY `idx_ip_address` (`ip_address`),
  KEY `idx_sys_operation_log_user_type` (`user_type`),
  KEY `idx_sys_operation_log_status` (`status`),
  KEY `idx_sys_operation_log_user_type_status` (`user_type`,`status`),
  KEY `idx_operation_log_trace_id` (`trace_id`),
  KEY `idx_sys_operation_log_delete_by` (`delete_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_operation_type`
--

DROP TABLE IF EXISTS `sys_operation_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型描述',
  `module` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属模块',
  `permission_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限等级(LOW/MEDIUM/HIGH)',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态(1启用,0禁用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=135 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作类型配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_operation_type_mapping`
--

DROP TABLE IF EXISTS `sys_operation_type_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_type_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `old_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '旧操作类型编码',
  `new_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '新操作类型编码',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态(1已映射,0未映射)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_old_type` (`old_type`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作类型映射表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '角色唯一ID（雪花ID）',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称（学生、教师、教务管理员）',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码（STUDENT、TEACHER、ADMIN，唯一）',
  `permissions` varchar(500) NOT NULL COMMENT '权限列表（JSON格式，如["paper:upload","report:view"]）',
  `description` varchar(200) DEFAULT '' COMMENT '角色描述',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id，初始化时填管理员ID）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '用户唯一ID（雪花ID）',
  `avatar` varchar(255) DEFAULT NULL COMMENT '用户头像URL',
  `username` varchar(50) NOT NULL COMMENT '登录账号（学生用学号，教师用工号，管理员自定义）',
  `password` varchar(100) NOT NULL COMMENT '密码（BCrypt加密存储）',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名',
  `role_id` bigint NOT NULL COMMENT '关联sys_role.id，用户角色ID',
  `college_id` bigint DEFAULT NULL COMMENT '所属学院ID(关联学院表）',
  `college_name` varchar(100) DEFAULT NULL COMMENT '学院名称',
  `major_id` bigint DEFAULT NULL COMMENT '专业ID（关联major.id）',
  `major` varchar(100) DEFAULT NULL COMMENT '专业名称',
  `professional_title` varchar(50) DEFAULT NULL COMMENT '职称（教师）',
  `research_direction` varchar(255) DEFAULT NULL COMMENT '科研方向（教师）',
  `current_advisor_count` int DEFAULT '0' COMMENT '当前指导任务数（教师）',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级（学生）',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级（学生）',
  `office` varchar(100) DEFAULT NULL COMMENT '办公地点（教师）',
  `office_hours` varchar(255) DEFAULT NULL COMMENT '办公时间（教师）',
  `max_review_count` int DEFAULT '5' COMMENT '最大审核数量（教师）',
  `review_deadline` int DEFAULT '7' COMMENT '审核期限（天，教师）',
  `notify_system_error` tinyint NOT NULL DEFAULT '1' COMMENT '通知开关-系统异常：0-关，1-开（管理员个人设置页）',
  `notify_security_warning` tinyint NOT NULL DEFAULT '1' COMMENT '通知开关-安全警告：0-关，1-开（管理员个人设置页）',
  `notify_system_announcement` tinyint NOT NULL DEFAULT '1' COMMENT '通知开关-系统公告：0-关，1-开',
  `notify_email` tinyint NOT NULL DEFAULT '1' COMMENT '通知开关-邮件通知：0-关，1-开',
  `log_retention_days` int NOT NULL DEFAULT '90' COMMENT '个人偏好-操作日志保留天数（30~365）',
  `backup_frequency` varchar(20) NOT NULL DEFAULT 'daily' COMMENT '个人偏好-数据备份频率：daily/weekly/monthly',
  `user_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '用户类型：STUDENT-学生，TEACHER-教师，ADMIN-管理员',
  `email` varchar(100) NOT NULL COMMENT '邮箱（用于密码重置、消息通知）',
  `email_verified` tinyint(1) DEFAULT '0' COMMENT '邮箱验证状态：0-未验证，1-已验证',
  `phone` varchar(20) DEFAULT '' COMMENT '手机号',
  `introduce` varchar(200) DEFAULT NULL COMMENT '个人介绍，如Java炒饭选手',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '账号状态（0-禁用，1-正常）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id，自注册用户填自身ID）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_sys_user_email` (`email`),
  KEY `idx_sys_user_major_id` (`major_id`),
  KEY `idx_sys_user_delete_by` (`delete_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_config`
--

DROP TABLE IF EXISTS `system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值（JSON格式存储）',
  `description` varchar(500) DEFAULT NULL COMMENT '配置描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_message`
--

DROP TABLE IF EXISTS `system_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_message` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `create_by` bigint DEFAULT NULL COMMENT '?????D',
  `title` varchar(200) DEFAULT NULL COMMENT '消息标题',
  `content` text COMMENT '消息内容',
  `message_type` varchar(50) DEFAULT NULL COMMENT '消息类型：assignment-分配通知, confirm-确认通知, reject-拒绝通知',
  `sender_id` bigint DEFAULT NULL COMMENT '发送者ID（系统发送为0）',
  `receiver_id` bigint DEFAULT NULL COMMENT '接收者ID',
  `related_id` bigint DEFAULT NULL COMMENT '关联的业务ID（如论文ID）',
  `related_type` varchar(50) DEFAULT NULL COMMENT '关联的业务类型：paper-论文',
  `is_read` tinyint DEFAULT '0' COMMENT '是否已读：0-未读，1-已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `priority` tinyint DEFAULT '1' COMMENT '优先级：1-普通，2-重要，3-紧急',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL COMMENT '?????D',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_receiver_id` (`receiver_id`),
  KEY `idx_receiver_read` (`receiver_id`,`is_read`),
  KEY `idx_related` (`related_type`,`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='站内信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_param`
--

DROP TABLE IF EXISTS `system_param`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_param` (
  `id` bigint NOT NULL COMMENT '系统参数ID（主键，仅存1条记录）',
  `max_paper_size` bigint NOT NULL DEFAULT '209715200' COMMENT '论文最大大小（字节，默认200MB）',
  `max_concurrent_check` int NOT NULL DEFAULT '10' COMMENT '最大并发查重数（默认10）',
  `jwt_expiration` bigint NOT NULL DEFAULT '86400000' COMMENT 'JWT有效期（毫秒，默认24小时）',
  `default_threshold` double DEFAULT NULL COMMENT '默认重复率阈值',
  `storage_type` varchar(50) DEFAULT NULL COMMENT '文件存储类型',
  `maintenance_status` tinyint DEFAULT NULL COMMENT '系统维护状态（非空，0-正常运行，1-维护中）',
  `maintenance_start_time` datetime DEFAULT NULL COMMENT '维护开始时间',
  `maintenance_end_time` datetime DEFAULT NULL COMMENT '维护结束时间',
  `maintenance_notice` varchar(255) DEFAULT NULL COMMENT '维护公告',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识（0-未删除，1-已删除，系统参数不允许删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `backup_retention_days` int DEFAULT '30' COMMENT '备份文件保留天数',
  `backup_enabled` int DEFAULT '1' COMMENT '是否启用自动备份(0-禁用,1-启用)',
  `guidance_weight` decimal(5,2) DEFAULT '20.00' COMMENT '成绩加权合成 · 指导分权重（百分比，默认20；三项合计≠100 抛 40915）',
  `review_weight` decimal(5,2) DEFAULT '30.00' COMMENT '成绩加权合成 · 评阅分权重（百分比，默认30）',
  `defense_weight` decimal(5,2) DEFAULT '50.00' COMMENT '成绩加权合成 · 答辩分权重（百分比，默认50）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统参数表（存储全局基础配置）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher_allocation_record`
--

DROP TABLE IF EXISTS `teacher_allocation_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_allocation_record` (
  `id` bigint NOT NULL COMMENT '记录ID',
  `paper_id` bigint NOT NULL COMMENT '论文ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `teacher_id` bigint NOT NULL COMMENT '老师ID',
  `allocation_type` varchar(20) DEFAULT NULL COMMENT '分配类型',
  `allocation_reason` varchar(500) DEFAULT NULL COMMENT '分配原因',
  `allocation_status` varchar(20) DEFAULT 'active' COMMENT '???????????? active-??????, revoked-?????????, expired-?????????',
  `allocation_time` datetime NOT NULL COMMENT '分配时间',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '软删除标记',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `college_id` bigint DEFAULT NULL COMMENT '学生学院ID',
  `college_name` varchar(255) DEFAULT NULL COMMENT '学生学院名称',
  `major_id` bigint DEFAULT NULL COMMENT '学生专业ID',
  `major_name` varchar(255) DEFAULT NULL COMMENT '学生专业名称',
  PRIMARY KEY (`id`),
  KEY `idx_paper_id` (`paper_id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_allocation_time` (`allocation_time`),
  KEY `idx_is_deleted` (`is_deleted`),
  KEY `idx_update_time` (`update_time`),
  KEY `idx_allocation_status` (`allocation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='指导老师分配记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher_audit`
--

DROP TABLE IF EXISTS `teacher_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_audit` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `submit_id` bigint NOT NULL COMMENT '论文提交ID（关联paper_submit.id）',
  `teacher_id` bigint NOT NULL COMMENT '审核教师ID（关联sys_user.id）',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '审核状态：0-待审核，1-已通过，2-已驳回',
  `allocate_time` datetime DEFAULT NULL COMMENT '分配时间',
  `audit_finish_time` datetime DEFAULT NULL COMMENT '审核完成时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_teacher_audit_teacher_status` (`teacher_id`,`audit_status`),
  KEY `idx_teacher_audit_submit` (`submit_id`),
  KEY `idx_teacher_audit_allocate_time` (`allocate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师审核分配表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher_info`
--

DROP TABLE IF EXISTS `teacher_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `professional_title` varchar(50) DEFAULT NULL COMMENT '职称',
  `research_direction` varchar(255) DEFAULT NULL COMMENT '科研方向',
  `current_advisor_count` int DEFAULT '0' COMMENT '当前指导教师数量',
  `office` varchar(100) DEFAULT NULL COMMENT '办公地点',
  `office_hours` varchar(255) DEFAULT NULL COMMENT '办公时间',
  `max_review_count` int DEFAULT '5' COMMENT '最大审核数量',
  `review_deadline` int DEFAULT '7' COMMENT '审核期限（天）',
  `college_name` varchar(100) DEFAULT NULL COMMENT '学院名称',
  `major_id` bigint DEFAULT NULL COMMENT '专业ID',
  `major` varchar(100) DEFAULT NULL COMMENT '专业名称',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '软删除标志（0-未删除，1-已删除）',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_college_name` (`college_name`),
  KEY `idx_major_id` (`major_id`),
  KEY `idx_teacher_info_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher_subject`
--
-- 教师研究方向 ↔ 学科领域关联表，是研究方向的真源。
-- sys_user.research_direction 降级为「学科中文名 CSV」冗余列：那 3 个直接把该列透给前端展示的地方
-- （StudentDashboardService / StudentProfileServiceImpl / StudentMessageServiceImpl）以及
-- 两处中文关键词匹配（AdvisorAssignServiceImpl.isDirectionMatch、AutoAssignmentServiceImpl）
-- 因此一行都不用改，显示还从 `ai` 变成「人工智能」。
--
-- 复合唯一键 + 物理删除（不用软删，否则重加同一对会撞键）。

DROP TABLE IF EXISTS `teacher_subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_subject` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `user_id` bigint NOT NULL COMMENT '教师用户ID（关联sys_user.id）',
  `subject_id` bigint NOT NULL COMMENT '学科领域ID（关联subject.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_subject` (`user_id`,`subject_id`),
  KEY `idx_teacher_subject_subject_id` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师研究方向↔学科领域关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `metric_event_daily`
--
-- 业务事件日聚合指标表：给管理端 Dashboard 的业务指标卡片
-- （GET /api/admin/monitoring/metrics）和 SystemMonitoring 的「待处理查重任务」供数。
-- 按 (event, result, stat_date) 日聚合 + upsert（INSERT ... ON DUPLICATE KEY UPDATE），
-- 不是每次事件插一行原始日志，避免无界增长并把 /metrics 变成对百万行做 GROUP BY。
-- id 为雪花 ID，由 Java 侧 IdWorker 生成，故此处不设 AUTO_INCREMENT。
-- 对已有数据的库（本表可能还不存在）请改用 db/存量修复.sql 段 1.7，
-- 那段是 CREATE TABLE IF NOT EXISTS，可重复执行。
--

DROP TABLE IF EXISTS `metric_event_daily`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `metric_event_daily` (
  `id`          bigint      NOT NULL                                    COMMENT '主键ID（雪花ID，由业务代码生成，非自增）',
  `event`       varchar(64) NOT NULL                                    COMMENT '事件类型(user_login/user_register/paper_submit/paper_review)',
  `result`      varchar(32) NOT NULL                                    COMMENT '结果标签(success/failure/pass/reject)',
  `stat_date`   date        NOT NULL                                    COMMENT '统计日期',
  `event_count` bigint      NOT NULL DEFAULT 0                          COMMENT '当日累计次数',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint     NOT NULL DEFAULT 0                          COMMENT '软删除标记(0-未删除,1-已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_result_date` (`event`,`result`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务事件日聚合指标表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_metric_minute`
--
-- 系统资源指标分钟表：给 SystemMonitoring 的性能趋势图
-- （GET /api/admin/monitoring/performance，period=1h/24h/7d）供数。
-- ResourceMetricsService 每分钟采样一行（metric_time 秒置零），
-- uk_metric_time + upsert 保证同一分钟只有一行。每分钟一行，30 天约 4.3 万行，
-- 由 ResourceMetricsService 每日清理 retention-days 之前的数据。
-- 对已有数据的库（本表可能还不存在）请改用 db/存量修复.sql 段 1.14，
-- 那段是 CREATE TABLE IF NOT EXISTS，可重复执行。
--

DROP TABLE IF EXISTS `system_metric_minute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_metric_minute` (
  `id`             bigint   NOT NULL                                    COMMENT '主键ID（雪花ID，由业务代码生成，非自增）',
  `metric_time`    datetime NOT NULL                                    COMMENT '指标时间（分钟精度，秒置零）',
  `cpu_usage`      double   DEFAULT NULL                                COMMENT '整机CPU使用率%',
  `jvm_cpu_usage`  double   DEFAULT NULL                                COMMENT 'JVM进程CPU使用率%',
  `memory_usage`   double   DEFAULT NULL                                COMMENT '整机物理内存使用率%',
  `jvm_heap_usage` double   DEFAULT NULL                                COMMENT 'JVM堆内存使用率%',
  `disk_usage`     double   DEFAULT NULL                                COMMENT '磁盘使用率%（jar所在分区）',
  `db_pool_usage`  double   DEFAULT NULL                                COMMENT '数据库连接池使用率%',
  `db_active_conn` int      DEFAULT NULL                                COMMENT '数据库活跃连接数',
  `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint  NOT NULL DEFAULT 0                          COMMENT '软删除标记(0-未删除,1-已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_time` (`metric_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统资源指标分钟表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_group`
--

DROP TABLE IF EXISTS `student_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_group` (
  `id` bigint NOT NULL COMMENT '分组ID（雪花ID，由业务代码生成，非自增）',
  `teacher_id` bigint NOT NULL COMMENT '所属教师ID（关联sys_user.id）',
  `name` varchar(50) NOT NULL COMMENT '分组名称',
  `description` varchar(200) DEFAULT NULL COMMENT '分组描述',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_student_group_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生分组表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_group_member`
--
-- 成员表物理删除（同 teacher_subject 的理由）：(group_id, student_id) 复合唯一键
-- 若叠加软删，移出后重新加入会撞唯一键。因此本表没有 is_deleted/审计列。

DROP TABLE IF EXISTS `student_group_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_group_member` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `group_id` bigint NOT NULL COMMENT '分组ID（关联student_group.id）',
  `student_id` bigint NOT NULL COMMENT '学生ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_student` (`group_id`,`student_id`),
  KEY `idx_group_member_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生分组成员表（物理删除）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'check-repeat-service'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-09 16:32:33

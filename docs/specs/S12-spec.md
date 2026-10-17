# S12 审核链路一致性 + 真实导出 修复规格（Spec v2）

> 主题：管理员/教师审核链路统一（阶段门、审核意见独立存储、状态日志）+ 导出假实现替换为真实实现。
> 范围：仅后端 `check-repeat-service`，不改前端仓库。S11 已提交（0ba34ac）。
> v2 变更：按用户反馈，D3 由「返回功能未开放错误码」改为「真实导出实现」。

---

## 1. 现状事实（已核实）

### 1.1 阶段门被绕过
- [AdminPaperServiceImpl.java#L151-L178](../../src/main/java/com/abin/checkrepeatservice/admin/service/Impl/AdminPaperServiceImpl.java) `auditPaper` 直写 `paperStatus=COMPLETED/REJECTED`，未走 `PaperStageGate.assertTransition`。
- 流转矩阵允许 `auditing → completed / rejected / withdrawn`，目标态合法，无需调整映射。

### 1.2 check_result 语义冲突（查重结果 vs 审核意见混写）
后端共 8 处写入 `check_result`：
- 查重语义 5 处（不动）：AdminPaperServiceImpl L557/L559、DataIntegrityChecker L279、CheckTaskEventListener L379、CheckTaskServiceImpl L525。
- **审核意见语义 3 处（本阶段修复）**：AdminPaperServiceImpl L167、TeacherDashboardServiceImpl L221、L268。
- 后端无 `getCheckResult()` 读取方。

### 1.3 前端契约（已核实，后端单侧可修复）
- 学生端 MyPapers.vue L370-377：`checkResult` →「查重结果」栏；`feedback` →「审核意见」栏。
- 后端从未输出 `feedback` → 学生端审核意见永远显示"暂无审核意见"（功能断链根因）。
- JSON 序列化为 Jackson 默认，`@JsonProperty` 重命名方案可行。

### 1.4 审核动作不写状态日志
- 写入口径：`PaperStatusLogService.recordStatusLog(paperId, old, new, reason, operateUserId, operateIp)`；当前用户：`UserBusinessInfoUtils.getCurrentUserId()`。
- `auditPaper` 未写日志 → 学生端时间线缺管理员审核记录。

### 1.5 导出链路接线（已核实，全部期望 blob 文件流）
| 前端调用 | 请求 | 后端端点 | 现状 |
|---|---|---|---|
| PaperLibrary.vue L1124 `exportPapers` | GET `/api/admin/papers/export`（params: majorId/grade/checkStatus/keyword，blob） | AdminPaperController L183 `exportPaperList` | 假实现（空方法+吞异常，静默 200） |
| Dashboard.vue L735、DataStatistics.vue L647 `exportTeacherData` | POST `/api/teacher/export/data`（blob） | TeacherDashboardController L268 `exportData` | 假实现（假文件名+Thread.sleep，返回 Result JSON——前端按 blob 下载会得到损坏 xlsx） |
| （无前端调用方） | GET `/api/teacher/export/data` | TeacherDashboardController L230 `exportTeacherData` | 假实现（同上） |
| `/api/teacher/data-analysis/export` | — | TeacherDataAnalysisService.exportTrendData | **已有真实实现，不在本阶段范围** |

注意：前端 POST `/api/teacher/export/data` 发 JSON body（{teacherId}），后端用 `@RequestParam` 接不到 → 实现须容忍 format/时间参数为空（默认 excel、不限时间）。

### 1.6 既有导出范式（直接沿用）
- [AdminAdvisorEvalServiceImpl.java#L162-L210](../../src/main/java/com/abin/checkreapseservice/admin/service/Impl/AdminAdvisorEvalServiceImpl.java)：查全量 → 映射 ExcelVO → 设置响应头 → `EasyExcel.write(response.getOutputStream(), VO.class).sheet(...).doWrite(rows)`；失败时 `response.getWriter().write(JSON)`。
- 依赖已具备：pom.xml 含 poi / poi-ooxml / easyexcel。
- Excel VO 惯例位置：`admin/vo/`（AdvisorEvalExcelVO）。
- 状态中文化：`dictService.getDictValueMap(DictConstants.DICT_PAPER_STATUS)`（"paper_status" 字典已确认存在）。

### 1.7 SQL Hard Constraint
- 存量修复.sql 最新段 1.17，新段 **1.18**；先跑 SQL 再部署 jar；init.sql 同步。

---

## 2. 变更设计（决策完备）

### D1. 审核意见独立存储（列 `paper_info.audit_comment`）
- **PaperInfo.java** 新增字段：

```java
/**
 * 审核意见（管理员/教师初审意见，独立于 check_result 查重结果存储）。
 * JSON 键输出为 feedback：学生端 MyPapers.vue「审核意见」等按 paper.feedback 读取（既有前端契约）。
 */
@TableField("audit_comment")
@JsonProperty("feedback")
private String auditComment;
```
- **AdminPaperServiceImpl.auditPaper** L167：`setCheckResult(auditComment)` → `setAuditComment(auditComment)`。
- **TeacherDashboardServiceImpl** L221/L268：同上（同类冲突一并修；教师审核**不接入阶段门**，存量流程保持原状）。
- **init.sql**：paper_info 建表加 `` `audit_comment` text COMMENT '审核意见（初审/退回意见，独立于 check_result 查重结果）', ``。
- **存量修复.sql** 新增段 1.18 幂等 ALTER（information_schema + 预处理范式，对照段 1.17）；段 5 自检追加对照项；注明"先跑 1.18 再部署后端"。
- 存量 check_result 不迁移（语义无法可靠区分），审核意见以新审核为准。

### D2. auditPaper 接入阶段门 + 补写状态日志
- 注入 `PaperStageGate`、`PaperStatusLogService`。
- 核心逻辑：

```java
String from = paperStageGate.normalizeLegacy(paperInfo.getPaperStatus());
String to = "approved".equals(auditResult) ? COMPLETED : REJECTED; // 无效值仍返回参数错误
paperStageGate.assertTransition(from, to);   // 非法流转 → 40305
paperInfo.setPaperStatus(to);
paperInfo.setAuditComment(auditComment);
paperInfo.setUpdateTime(now);
paperInfoMapper.updateById(paperInfo);
paperStatusLogService.recordStatusLog(paperId, from, to,
        buildAuditReason(auditResult, auditComment),
        UserBusinessInfoUtils.getCurrentUserId(), null);
```
- `catch (Exception e)` 前先 `catch (BusinessException e)` 透传其 ResultCode（40305 不得降级 500；实施时按 BusinessException 实际 API 取码）。
- status_reason：`管理员初审通过/退回` +（意见非空追加"："+意见），总长 ≤500 字符。
- `batchAuditPapers` 循环调用自动继承，单条失败计入 failCount。

### D3. 导出真实实现（EasyExcel 流式，沿用 AdminAdvisorEvalServiceImpl.export 范式）

**新建 `admin/vo/PaperExportExcelVO.java`**（@ExcelProperty，与 AdvisorEvalExcelVO 同款风格）：
列：论文标题 / 作者 / 学号 / 专业 / 年级 / 指导教师 / 论文类型 / 状态 / 相似度(%) / 查重结果 / 审核意见 / 提交时间。

**D3.1 管理员论文列表导出**
- 接口签名：`AdminPaperService.exportPaperList(Map<String,Object> params, HttpServletResponse response)`（接口、实现、Controller 三处同步改）。
- 筛选口径（params 为 query string，值均为 String）：
  - `keyword`：like 标题/作者/摘要（参数绑定写法，同 getPaperList）；
  - `majorId`：`eq(major_id)`（前端传的是 ID，与列表页 majorName 不同，按导出入口自己的契约）；
  - `grade`：apply 参数绑定 sys_user 子查询（同 getPaperList）；
  - `checkStatus`：复用 `addCheckStatusCondition`；
  - `paperStatus`：有值则 eq；缺省排除 withdrawn；
  - `is_deleted=0`；`wrapper.last(" LIMIT 10000 ")` 行数上限防内存。
- 增强：selectBatchIds 两批查学生/教师姓名（复用 enhancePaperList 思路）。
- 状态列：字典服务转中文，缺失回退原码；论文类型：graduation/course/other 写死映射（毕业论文/课程论文/其他），未知回退原值。
- 输出：xlsx，文件名 `论文列表_yyyyMMdd_HHmmss.xlsx`；Controller 去掉吞异常 try/catch，直接透传 response 流。

**D3.2 教师数据导出（两个方法共用一套实现）**
- 数据口径：`teacher_id = 当前教师 AND is_deleted = 0`，startTime/endTime（或 startDate/endDate）过滤 create_time，均空则全量；同样 LIMIT 10000。
- `exportData(teacherId, format, startTime, endTime, HttpServletResponse response)`（POST 端点，前端实际调用方）：
  - `format=excel`（默认/空）：EasyExcel 输出 xlsx，`教师数据统计_yyyyMMdd_HHmmss.xlsx`；
  - `format=csv`：UTF-8 BOM + 手写 CSV（引号包裹、双引号转义），`教师数据统计_*.csv`；
  - 其他值（含 pdf）：按范式写 JSON 错误响应 `{"code":400,"message":"暂不支持该导出格式"}`。
- `exportTeacherData(teacherId, startDate, endDate, HttpServletResponse response)`（GET 端点，暂无前端调用方）：委托同一私有实现，固定 excel。
- 行映射与学生/教师姓名增强在 TeacherDashboardServiceImpl 内私有实现（selectBatchIds 两批），不做跨包抽象。
- Controller 两个方法：`Result<String>` → `void` + `HttpServletResponse` 参数。

**失败处理**：两个 Controller 均按既有范式 catch 后 `response.getWriter().write(JSON)`；日志 log.error。

### D4. 部署顺序（Hard Constraint）
1. 生产库先执行 存量修复.sql 段 1.18（幂等）；
2. 再部署新 jar。

---

## 3. 变更文件清单

| 文件 | 变更 |
|---|---|
| `pojo/entity/PaperInfo.java` | +auditComment 字段（@TableField audit_comment + @JsonProperty feedback） |
| `admin/service/Impl/AdminPaperServiceImpl.java` | auditPaper 阶段门/意见存储/状态日志；exportPaperList 真实实现（签名 +response）；注入依赖 |
| `admin/service/AdminPaperService.java` | exportPaperList 签名同步 |
| `admin/controller/AdminPaperController.java` | exportPaperList 透传 response |
| `admin/vo/PaperExportExcelVO.java` | **新建** Excel 行 VO |
| `teacher/service/Impl/TeacherDashboardServiceImpl.java` | L221/L268 改写 audit_comment；exportTeacherData/exportData 真实实现（签名 +response） |
| `teacher/service/TeacherDashboardService.java` | 两个导出方法签名同步 |
| `teacher/controller/TeacherDashboardController.java` | 两个导出端点改 void + response |
| `resources/db/init.sql` | paper_info 建表 + audit_comment 列 |
| `resources/db/存量修复.sql` | +段 1.18 幂等 ALTER + 段 5 自检对照 |

## 4. 验证

1. `mvn compile` 通过。
2. 代码走查：
   - auditPaper 非法流转（如 completed→rejected）返回 40305 文案；batchAudit 计入 failCount 不中断；
   - 审核后 paper_status_log 新增一行，status_reason ≤500 字；
   - 导出筛选口径与 getPaperList 一致（除 majorId 精确匹配）、LIMIT 10000 生效；
   - CSV 转义与 BOM 正确；format=pdf 返回 JSON 错误。
3. SQL 幂等：段 1.18 重复执行输出"已存在，跳过"。

## 5. 提交拆分（仓库风格，简短）

1. `fix(audit): 审核意见独立存储，管理员审核接入阶段门并补写状态日志` —— D1 全部 + D2 + init.sql + 存量修复.sql。
2. `feat(export): 论文列表与教师数据导出真实实现` —— PaperExportExcelVO + 三个导出方法/两个 Controller。

## 6. 明确不做（边界）

- 不接入教师审核到 PaperStageGate（存量流程按 Gate 约定保持原状）。
- 不迁移存量 check_result 历史数据。
- 不做 PDF 导出（format=pdf 返回明确错误）；`/api/teacher/data-analysis/export` 已有真实实现不动。
- 不改前端仓库、不修 P2 ReviewResultDTO 的 feedback 展示断链（记入后续阶段）。
- 不做跨模块导出抽象（教师端私有实现，不建公共导出服务）。

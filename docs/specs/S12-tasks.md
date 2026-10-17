# S12 实施任务清单（v2：导出为真实实现）

对应规格：docs/specs/S12-spec.md

## 任务 1：审核意见独立存储（D1）
- [ ] PaperInfo.java 新增 `auditComment` 字段（@TableField("audit_comment") + @JsonProperty("feedback")）
- [ ] AdminPaperServiceImpl.auditPaper L167：setCheckResult → setAuditComment
- [ ] TeacherDashboardServiceImpl L221 / L268：setCheckResult → setAuditComment
- [ ] init.sql paper_info 建表新增 `audit_comment` text 列
- [ ] 存量修复.sql 新增段 1.18 幂等 ALTER + 段 5 自检对照项

## 任务 2：auditPaper 接入阶段门 + 状态日志（D2）
- [ ] 注入 PaperStageGate、PaperStatusLogService
- [ ] auditPaper：normalizeLegacy → assertTransition → 落库 → recordStatusLog（reason ≤500 字）
- [ ] BusinessException 透传对应 ResultCode（40305 不降级为 500）
- [ ] 确认 batchAuditPapers 循环继承新逻辑

## 任务 3：导出真实实现（D3，EasyExcel 范式）
- [ ] 新建 admin/vo/PaperExportExcelVO（@ExcelProperty 12 列）
- [ ] AdminPaperService/Impl/Controller.exportPaperList：签名 +HttpServletResponse，真实筛选查询（majorId 精确、grade/keyword 参数绑定、checkStatus 复用、LIMIT 10000）→ EasyExcel 流式输出
- [ ] TeacherDashboardService/Impl/Controller：exportData（excel/csv/pdf 拒绝）与 exportTeacherData（委托 excel）真实实现，签名 +response
- [ ] 状态列字典中文化（DICT_PAPER_STATUS）、论文类型固定映射；失败按范式写 JSON 响应

## 任务 4：验证与提交
- [ ] mvn compile 通过
- [ ] 提交 1：`fix(audit): 审核意见独立存储，管理员审核接入阶段门并补写状态日志`
- [ ] 提交 2：`feat(export): 论文列表与教师数据导出真实实现`
- [ ] 更新 project_memory.md S12 条目

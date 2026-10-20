package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.admin.vo.PaperExportExcelVO;
import com.alibaba.excel.EasyExcel;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.teacher.dto.BatchReviewDTO;
import com.abin.checkrepeatservice.teacher.dto.ReviewOperateReq;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.mapper.TeacherDashboardMapper;
import com.abin.checkrepeatservice.teacher.service.TeacherDashboardService;
import com.abin.checkrepeatservice.teacher.service.TeacherReviewService;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师控制台服务实现类
 */
@Slf4j
@Service
public class TeacherDashboardServiceImpl implements TeacherDashboardService {

    @Resource
    private TeacherDashboardMapper teacherDashboardMapper;
    
    @Resource
    private PaperInfoMapper paperInfoMapper;
    
    @Resource
    private SysUserService sysUserService;

    @Resource
    private TeacherReviewService teacherReviewService;

    @Override
    public Result<Map<String, Object>> getDashboardStats(Long teacherId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 1. 指导学生总数
            Long totalStudents = teacherDashboardMapper.countTotalStudents(teacherId);
            
            // 2. 待审核论文数量
            Long pendingPapers = teacherDashboardMapper.countPendingPapers(teacherId);
            
            // 3. 已审核论文数量
            Long reviewedPapers = teacherDashboardMapper.countReviewedPapers(teacherId);
            
            // 4. 审核通过率
            BigDecimal passRate = BigDecimal.ZERO;
            if (reviewedPapers > 0) {
                Long passedPapers = teacherDashboardMapper.countPassedPapers(teacherId);
                passRate = new BigDecimal(passedPapers)
                    .divide(new BigDecimal(reviewedPapers), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            }
            
            stats.put("totalStudents", totalStudents);
            stats.put("pendingPapers", pendingPapers);
            stats.put("reviewedPapers", reviewedPapers);
            stats.put("passRate", passRate);
            
            // 5. 添加待办事项提醒
            List<Map<String, Object>> todoItems = getTodoItems(teacherId);
            stats.put("todoItems", todoItems);
            
            // 6. 添加快速操作入口
            List<Map<String, Object>> quickActions = getQuickActions(teacherId);
            stats.put("quickActions", quickActions);
            
            // 7. 添加今日统计
            Map<String, Object> todayStats = getTodayStatistics(teacherId);
            stats.put("todayStats", todayStats);
            
            log.debug("教师{}仪表盘统计: 学生{}人, 待审核{}篇, 已审核{}篇, 通过率{}%", 
                     teacherId, totalStudents, pendingPapers, reviewedPapers, passRate);
                     
            return Result.success("获取统计数据成功", stats);
        } catch (Exception e) {
            log.error("获取仪表盘统计数据失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取统计数据失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Object> getPendingPapers(Long teacherId, Integer pageNum, Integer pageSize) {
        try {
            Page<PaperInfo> paperPage = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaperInfo::getTeacherId, teacherId)
                   .eq(PaperInfo::getPaperStatus, "auditing") // 待审核状态
                   .orderByDesc(PaperInfo::getSubmitTime);
            
            Page<PaperInfo> resultPage = paperInfoMapper.selectPage(paperPage, wrapper);
            
            // 构造符合需求的数据结构
            Map<String, Object> responseData = new HashMap<>();
            List<Map<String, Object>> records = new ArrayList<>();
            
            for (PaperInfo paper : resultPage.getRecords()) {
                Map<String, Object> record = new HashMap<>();
                record.put("id", paper.getId());
                
                // paperBaseInfo 对象
                Map<String, Object> paperBaseInfo = new HashMap<>();
                paperBaseInfo.put("paperId", paper.getId());
                paperBaseInfo.put("paperTitle", paper.getPaperTitle());
                
                // 获取学生信息
                SysUser student = sysUserService.getById(paper.getStudentId());
                if (student != null) {
                    paperBaseInfo.put("studentName", student.getRealName());
                }
                
                record.put("paperBaseInfo", paperBaseInfo);
                
                // taskBaseInfo 对象
                Map<String, Object> taskBaseInfo = new HashMap<>();
                taskBaseInfo.put("checkEndTime", paper.getCheckTime());
                record.put("taskBaseInfo", taskBaseInfo);
                
                // 版本号
                record.put("version", paper.getFileId() != null ? 1 : 0);
                
                records.add(record);
            }
            
            responseData.put("records", records);
            responseData.put("total", resultPage.getTotal());
            
            return Result.success("获取待审核论文列表成功", responseData);
        } catch (Exception e) {
            log.error("获取待审核论文列表失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取待审核论文列表失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Map<String, Object>> getStudentStats(Long teacherId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 总学生数
            Long totalStudents = teacherDashboardMapper.countTotalStudents(teacherId);
            
            // 已提交论文数
            Long submittedPapers = teacherDashboardMapper.countSubmittedPapers(teacherId);
            
            // 审核中论文数
            Long auditingPapers = teacherDashboardMapper.countAuditingPapers(teacherId);
            
            // 已通过论文数
            Long passedPapers = teacherDashboardMapper.countPassedPapers(teacherId);
            
            // 需修改论文数（被驳回的）
            Long needModifyPapers = teacherDashboardMapper.countRejectedPapers(teacherId);
            
            stats.put("totalStudents", totalStudents);
            stats.put("submittedPapers", submittedPapers);
            stats.put("auditingPapers", auditingPapers);
            stats.put("passedPapers", passedPapers);
            stats.put("needModifyPapers", needModifyPapers);
            
            log.debug("教师{}学生统计: 总{}人, 已提交{}篇, 审核中{}篇, 通过{}篇, 需修改{}篇",
                     teacherId, totalStudents, submittedPapers, auditingPapers, passedPapers, needModifyPapers);
                     
            return Result.success("获取学生状态统计成功", stats);
        } catch (Exception e) {
            log.error("获取学生状态统计失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取学生状态统计失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * @deprecated 旧审核入口，已收敛到 {@link TeacherReviewService#doReview}。
     * 保留 URL 仅为兼容历史客户端，新代码请调用 /api/teacher/reviews/do-review。
     * 不再直接写 paper_info，审核记录、状态门、签核校验由统一审核链路负责。
     */
    @Deprecated
    @Override
    public Result<String> batchReviewPapers(Long teacherId, BatchReviewDTO reviewDTO) {
        if (reviewDTO.getPaperIds() == null || reviewDTO.getPaperIds().isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "论文ID列表不能为空");
        }
        Integer reviewStatus = resolveReviewStatus(reviewDTO.getReviewStatus());
        if (reviewStatus == null) {
            return Result.error(ResultCode.PARAM_ERROR, "审核状态参数无效，应为APPROVED或REJECTED");
        }

        ReviewOperateReq operateReq = new ReviewOperateReq();
        operateReq.setPaperIds(reviewDTO.getPaperIds());
        operateReq.setReviewStatus(reviewStatus);
        operateReq.setReviewOpinion(reviewDTO.getReviewOpinion());

        log.info("教师{}通过旧批量审核入口提交审核: paperIds={}, reviewStatus={}",
                teacherId, reviewDTO.getPaperIds(), reviewStatus);
        Result<Map<String, Object>> result = teacherReviewService.doReview(operateReq);
        if (!result.isSuccess()) {
            return Result.error(result.getErrorCode(), result.getMessage());
        }

        Map<String, Object> data = result.getData();
        Object successCount = data == null ? 0 : data.getOrDefault("successCount", 0);
        Object failCount = data == null ? 0 : data.getOrDefault("failCount", 0);
        return Result.success(String.format("批量审核完成: 成功%s篇, 失败%s篇", successCount, failCount));
    }

    /**
     * @deprecated 旧审核入口，已收敛到 {@link TeacherReviewService#doReview}。
     * 保留 URL 仅为兼容历史客户端，新代码请调用 /api/teacher/reviews/do-review。
     */
    @Deprecated
    @Override
    public Result<String> reviewPaper(Long teacherId, Long paperId, String reviewResult, String reviewComment) {
        Integer reviewStatus = resolveReviewStatus(reviewResult);
        if (reviewStatus == null) {
            return Result.error(ResultCode.PARAM_ERROR, "审核结果参数无效，应为pass或reject");
        }

        ReviewOperateReq operateReq = new ReviewOperateReq();
        operateReq.setPaperIds(Collections.singletonList(paperId));
        operateReq.setReviewStatus(reviewStatus);
        operateReq.setReviewOpinion(reviewComment);

        log.info("教师{}通过旧单篇审核入口提交审核: paperId={}, reviewResult={}", teacherId, paperId, reviewResult);
        Result<Map<String, Object>> result = teacherReviewService.doReview(operateReq);
        if (!result.isSuccess()) {
            return Result.error(result.getErrorCode(), result.getMessage());
        }
        return Result.success("论文审核成功");
    }

    /**
     * 旧审核入口的字符串结果 → 统一审核结果字典（3-通过，4-不通过）。
     * 兼容单篇的 pass/reject 与批量的 APPROVED/REJECTED。
     */
    private Integer resolveReviewStatus(String reviewResult) {
        if (reviewResult == null) {
            return null;
        }
        String value = reviewResult.trim();
        if ("APPROVED".equalsIgnoreCase(value) || "PASS".equalsIgnoreCase(value)) {
            return DictConstants.ReviewStatus.PASS;
        }
        if ("REJECTED".equalsIgnoreCase(value) || "REJECT".equalsIgnoreCase(value)) {
            return DictConstants.ReviewStatus.REJECT;
        }
        return null;
    }

    @Override
    public Result<String> downloadPaper(Long teacherId, Long paperId) {
        try {
            // 验证论文是否存在且属于该教师
            PaperInfo paper = paperInfoMapper.selectById(paperId);
            if (paper == null) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            
            if (!paper.getTeacherId().equals(teacherId)) {
                return Result.error(ResultCode.PARAM_ERROR, "无权限下载此论文");
            }
            
            // 生成下载链接（这里返回文件ID，前端根据文件ID下载）
            String downloadUrl = "/api/file/download/" + paper.getFileId();
            
            log.info("教师{}下载论文{}, 文件ID: {}", teacherId, paperId, paper.getFileId());
            return Result.success("获取下载链接成功", downloadUrl);
        } catch (Exception e) {
            log.error("论文下载失败: teacherId={}, paperId={}", teacherId, paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文下载失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Map<String, Object>> getReviewStatistics(Long teacherId) {
        Map<String, Object> statistics = new HashMap<>();
        
        try {
            // 论文状态分布数据
            List<Map<String, Object>> statusDistribution = teacherDashboardMapper.getPaperStatusDistribution(teacherId);
            
            // 各专业审核情况
            List<Map<String, Object>> majorReviewStats = teacherDashboardMapper.getMajorReviewStatistics(teacherId);
            
            // 时间趋势数据（近30天）
            List<Map<String, Object>> timeTrend = teacherDashboardMapper.getTimeTrendStatistics(teacherId);
            
            statistics.put("statusDistribution", statusDistribution);
            statistics.put("majorReviewStats", majorReviewStats);
            statistics.put("timeTrend", timeTrend);
            
            log.debug("教师{}获取审核统计成功", teacherId);
            return Result.success("获取审核进度统计成功", statistics);
        } catch (Exception e) {
            log.error("获取审核进度统计失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取审核进度统计失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Object> getRecentActivities(Long teacherId, Integer page, Integer size) {
        try {
            // 这里可以整合多种活动记录：审核记录、学生提交记录等
            // 简化实现，返回审核记录
            Page<Map<String, Object>> activityPage = new Page<>(page, size);
            List<Map<String, Object>> activities = teacherDashboardMapper.getRecentReviewActivities(teacherId, activityPage);
            
            activityPage.setRecords(activities);
            activityPage.setTotal(activities.size()); // 简化处理
            
            log.debug("教师{}获取近期活动记录: {}条", teacherId, activities.size());
            return Result.success("获取近期活动记录成功", activityPage);
        } catch (Exception e) {
            log.error("获取近期活动记录失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取近期活动记录失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public void exportTeacherData(Long teacherId, String startDate, String endDate, HttpServletResponse response) {
        // GET 兼容入口：固定 excel 格式，日期参数语义与 POST 一致（按 create_time 过滤）
        doExportTeacherPapers(teacherId, "excel", startDate, endDate, response);
    }

    @Override
    public void exportData(Long teacherId, String format, String startTime, String endTime, HttpServletResponse response) {
        // 前端以 JSON body 调 POST 端点，@RequestParam 接不到参数时按默认值导出（全量 excel）
        String fmt = (format == null || format.isBlank()) ? "excel" : format.trim().toLowerCase();
        if (!"excel".equals(fmt) && !"csv".equals(fmt)) {
            log.warn("教师{}请求了不支持的导出格式: {}", teacherId, format);
            writeExportError(response, "暂不支持该导出格式: " + format + "（支持 excel/csv）");
            return;
        }
        doExportTeacherPapers(teacherId, fmt, startTime, endTime, response);
    }

    /**
     * S12：教师数据导出真实实现——该教师指导的论文明细（EasyExcel xlsx / UTF-8 CSV）。
     * 时间边界无法解析时跳过该过滤条件（日志告警），不让整次导出失败。
     */
    private void doExportTeacherPapers(Long teacherId, String format, String startTime, String endTime,
                                       HttpServletResponse response) {
        try {
            LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaperInfo::getTeacherId, teacherId)
                   .eq(PaperInfo::getIsDeleted, 0)
                   .orderByDesc(PaperInfo::getCreateTime)
                   .last(" LIMIT 10000 ");
            LocalDateTime start = parseExportTime(startTime);
            if (start != null) {
                wrapper.ge(PaperInfo::getCreateTime, start);
            } else if (hasText(startTime)) {
                log.warn("教师数据导出开始时间无法解析，忽略该过滤: {}", startTime);
            }
            LocalDateTime end = parseExportTime(endTime);
            if (end != null) {
                wrapper.le(PaperInfo::getCreateTime, end);
            } else if (hasText(endTime)) {
                log.warn("教师数据导出结束时间无法解析，忽略该过滤: {}", endTime);
            }

            List<PaperInfo> papers = paperInfoMapper.selectList(wrapper);
            fillExportNames(papers);

            List<PaperExportExcelVO> rows = papers.stream().map(this::toExportRow).toList();
            String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            if ("csv".equals(format)) {
                writeCsv(response, rows, "教师数据统计_" + timestamp + ".csv");
            } else {
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setCharacterEncoding("utf-8");
                response.setHeader("Content-disposition", "attachment;filename=教师数据统计_" + timestamp + ".xlsx");
                EasyExcel.write(response.getOutputStream(), PaperExportExcelVO.class)
                        .sheet("教师数据统计")
                        .doWrite(rows);
            }
            log.info("教师{}数据导出成功: format={}, {}行", teacherId, format, rows.size());
        } catch (Exception e) {
            log.error("教师数据导出失败: teacherId={}", teacherId, e);
            writeExportError(response, "数据导出失败：" + e.getMessage());
        }
    }

    /** PaperInfo → 导出行（教师端私有映射，列结构与管理员导出一致） */
    private PaperExportExcelVO toExportRow(PaperInfo p) {
        PaperExportExcelVO row = new PaperExportExcelVO();
        row.setPaperTitle(p.getPaperTitle());
        row.setStudentName(p.getStudentName());
        row.setStudentUsername(p.getStudentUsername());
        row.setStudentMajor(p.getStudentMajor());
        row.setStudentGrade(p.getStudentGrade());
        row.setTeacherName(p.getTeacherRealName());
        row.setPaperType(p.getPaperType());
        row.setPaperStatus(p.getPaperStatus());
        row.setSimilarityRate(p.getSimilarityRate() == null ? null : p.getSimilarityRate().toPlainString());
        row.setCheckResult(p.getCheckResult());
        row.setAuditComment(p.getAuditComment());
        row.setSubmitTime(p.getSubmitTime() == null ? null
                : p.getSubmitTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return row;
    }

    /**
     * 批量回填导出行所需的学生/教师姓名（selectBatchIds 各一次，避免 N+1）。
     */
    private void fillExportNames(List<PaperInfo> papers) {
        if (papers == null || papers.isEmpty()) {
            return;
        }
        List<Long> studentIds = papers.stream().map(PaperInfo::getStudentId).filter(Objects::nonNull).distinct().toList();
        List<Long> teacherIds = papers.stream().map(PaperInfo::getTeacherId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> studentMap = studentIds.isEmpty() ? Map.of()
                : sysUserService.listByIds(studentIds).stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, SysUser> teacherMap = teacherIds.isEmpty() ? Map.of()
                : sysUserService.listByIds(teacherIds).stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        for (PaperInfo paper : papers) {
            SysUser student = paper.getStudentId() == null ? null : studentMap.get(paper.getStudentId());
            if (student != null) {
                paper.setStudentName(student.getRealName());
                paper.setStudentUsername(student.getUsername());
                paper.setStudentGrade(student.getGrade());
                paper.setStudentMajor(student.getMajor());
            }
            SysUser teacher = paper.getTeacherId() == null ? null : teacherMap.get(paper.getTeacherId());
            if (teacher != null) {
                paper.setTeacherRealName(teacher.getRealName());
            }
        }
    }

    /** 导出时间边界解析：ISO 日期时间或 yyyy-MM-dd（当天零点）；无法解析返回 null */
    private LocalDateTime parseExportTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return LocalDateTime.parse(v);
        } catch (Exception ignore) { /* 尝试下一种格式 */ }
        try {
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v).atStartOfDay();
        } catch (Exception ignore) { /* 两种格式均不支持 */ }
        return null;
    }

    /** 导出失败时写 JSON 错误响应（沿用 AdminAdvisorEvalServiceImpl.export 范式） */
    private void writeExportError(HttpServletResponse response, String message) {
        try {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":500,\"message\":\"" + message + "\"}");
        } catch (Exception ex) {
            log.error("写导出错误响应失败", ex);
        }
    }

    /** UTF-8 BOM + 全字段引号转义的 CSV 输出；表头顺序须与 PaperExportExcelVO 保持一致 */
    private void writeCsv(HttpServletResponse response, List<PaperExportExcelVO> rows, String fileName) throws java.io.IOException {
        response.setContentType("text/csv;charset=utf-8");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName);
        java.io.OutputStream os = response.getOutputStream();
        os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        StringBuilder sb = new StringBuilder();
        sb.append("论文标题,作者,学号,专业,年级,指导教师,论文类型,状态,相似度(%),查重结果,审核意见,提交时间\n");
        for (PaperExportExcelVO r : rows) {
            sb.append(escapeCsv(r.getPaperTitle())).append(',')
              .append(escapeCsv(r.getStudentName())).append(',')
              .append(escapeCsv(r.getStudentUsername())).append(',')
              .append(escapeCsv(r.getStudentMajor())).append(',')
              .append(escapeCsv(r.getStudentGrade())).append(',')
              .append(escapeCsv(r.getTeacherName())).append(',')
              .append(escapeCsv(r.getPaperType())).append(',')
              .append(escapeCsv(r.getPaperStatus())).append(',')
              .append(escapeCsv(r.getSimilarityRate())).append(',')
              .append(escapeCsv(r.getCheckResult())).append(',')
              .append(escapeCsv(r.getAuditComment())).append(',')
              .append(escapeCsv(r.getSubmitTime()))
              .append('\n');
        }
        os.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        os.flush();
    }

    /** CSV 字段转义：null→空串；含逗号/引号/换行时用引号包裹并双写内部引号 */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public Result<Object> getStudentList(Long teacherId, Integer page, Integer size) {
        try {
            Page<SysUser> studentPage = new Page<>(page, size);
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUserType, 1) // 学生类型
                   .eq(SysUser::getStatus, 1)   // 启用状态
                   .eq(SysUser::getIsDeleted, 0);
            
            // 通过论文信息关联查找该教师指导的学生
            List<Long> studentIds = teacherDashboardMapper.getStudentIdsByTeacher(teacherId);
            if (!studentIds.isEmpty()) {
                wrapper.in(SysUser::getId, studentIds);
            } else {
                wrapper.eq(SysUser::getId, -1L); // 无结果
            }
            
            Page<SysUser> resultPage = sysUserService.page(studentPage, wrapper);
            
            // 转换为前端需要的格式
            Page<Map<String, Object>> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
            List<Map<String, Object>> records = new ArrayList<>();
            
            for (SysUser student : resultPage.getRecords()) {
                Map<String, Object> record = new HashMap<>();
                record.put("studentId", student.getId());
                record.put("studentName", student.getRealName());
                record.put("username", student.getUsername());
                record.put("major", student.getMajor());
                record.put("grade", student.getGrade());
                record.put("college", student.getCollegeName());
                record.put("email", student.getEmail());
                record.put("phone", student.getPhone());
                
                records.add(record);
            }
            
            responsePage.setRecords(records);
            return Result.success("获取指导学生列表成功", responsePage);
        } catch (Exception e) {
            log.error("获取指导学生列表失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取指导学生列表失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Map<String, Object>> refreshDashboard(Long teacherId) {
        // 直接调用获取统计数据的方法
        return getDashboardStats(teacherId);
    }
    
    /**
     * 获取待办事项列表
     */
    private List<Map<String, Object>> getTodoItems(Long teacherId) {
        List<Map<String, Object>> todoItems = new ArrayList<>();
        
        try {
            // 1. 待审核论文提醒
            Long pendingCount = teacherDashboardMapper.countPendingPapers(teacherId);
            if (pendingCount > 0) {
                Map<String, Object> pendingItem = new HashMap<>();
                pendingItem.put("type", "PENDING_REVIEW");
                pendingItem.put("title", "有待审核的论文");
                pendingItem.put("count", pendingCount);
                pendingItem.put("priority", "HIGH");
                pendingItem.put("description", String.format("您有%d篇论文等待审核", pendingCount));
                pendingItem.put("actionUrl", "/teacher/papers/pending");
                todoItems.add(pendingItem);
            }
            
            // 2. 新提交论文提醒（24小时内）
            Long newSubmissions = teacherDashboardMapper.countNewSubmissions(teacherId, 24);
            if (newSubmissions > 0) {
                Map<String, Object> newItem = new HashMap<>();
                newItem.put("type", "NEW_SUBMISSION");
                newItem.put("title", "新论文提交");
                newItem.put("count", newSubmissions);
                newItem.put("priority", "MEDIUM");
                newItem.put("description", String.format("过去24小时有%d篇新论文提交", newSubmissions));
                newItem.put("actionUrl", "/teacher/papers/new");
                todoItems.add(newItem);
            }
            
            // 3. 需要关注的学生提醒（长时间未提交论文）
            Long inactiveStudents = teacherDashboardMapper.countInactiveStudents(teacherId, 7);
            if (inactiveStudents > 0) {
                Map<String, Object> inactiveItem = new HashMap<>();
                inactiveItem.put("type", "INACTIVE_STUDENTS");
                inactiveItem.put("title", "需关注的学生");
                inactiveItem.put("count", inactiveStudents);
                inactiveItem.put("priority", "LOW");
                inactiveItem.put("description", String.format("%d名学生超过7天未提交论文", inactiveStudents));
                inactiveItem.put("actionUrl", "/teacher/students/inactive");
                todoItems.add(inactiveItem);
            }
            
        } catch (Exception e) {
            log.warn("获取待办事项失败: teacherId={}", teacherId, e);
        }
        
        return todoItems;
    }
    
    /**
     * 获取快速操作入口
     */
    private List<Map<String, Object>> getQuickActions(Long teacherId) {
        List<Map<String, Object>> quickActions = new ArrayList<>();
        
        // 1. 快速审核
        Map<String, Object> reviewAction = new HashMap<>();
        reviewAction.put("id", "quick_review");
        reviewAction.put("title", "快速审核");
        reviewAction.put("icon", "edit-document");
        reviewAction.put("description", "快速处理待审核论文");
        reviewAction.put("actionUrl", "/teacher/papers/pending");
        reviewAction.put("permission", "REVIEW_PAPER");
        quickActions.add(reviewAction);
        
        // 2. 查看学生列表
        Map<String, Object> studentListAction = new HashMap<>();
        studentListAction.put("id", "student_list");
        studentListAction.put("title", "学生管理");
        studentListAction.put("icon", "user-group");
        studentListAction.put("description", "查看和管理指导学生");
        studentListAction.put("actionUrl", "/teacher/students");
        studentListAction.put("permission", "VIEW_STUDENTS");
        quickActions.add(studentListAction);
        
        // 3. 数据统计
        Map<String, Object> statsAction = new HashMap<>();
        statsAction.put("id", "review_stats");
        statsAction.put("title", "审核统计");
        statsAction.put("icon", "bar-chart");
        statsAction.put("description", "查看审核工作量统计");
        statsAction.put("actionUrl", "/teacher/review/statistics");
        statsAction.put("permission", "VIEW_STATISTICS");
        quickActions.add(statsAction);
        
        // 4. 消息中心
        Map<String, Object> messageAction = new HashMap<>();
        messageAction.put("id", "message_center");
        messageAction.put("title", "消息中心");
        messageAction.put("icon", "message");
        messageAction.put("description", "查看系统消息和学生咨询");
        messageAction.put("actionUrl", "/teacher/messages");
        messageAction.put("permission", "VIEW_MESSAGES");
        quickActions.add(messageAction);
        
        // 5. 系统设置
        Map<String, Object> settingAction = new HashMap<>();
        settingAction.put("id", "system_setting");
        settingAction.put("title", "个人设置");
        settingAction.put("icon", "setting");
        settingAction.put("description", "修改个人信息和偏好设置");
        settingAction.put("actionUrl", "/teacher/settings");
        settingAction.put("permission", "VIEW_SETTINGS");
        quickActions.add(settingAction);
        
        return quickActions;
    }
    
    /**
     * 获取今日统计
     */
    private Map<String, Object> getTodayStatistics(Long teacherId) {
        Map<String, Object> todayStats = new HashMap<>();
        
        try {
            // 今日审核数
            Long todayReviewed = teacherDashboardMapper.countTodayReviews(teacherId);
            todayStats.put("todayReviewed", todayReviewed);
            
            // 今日通过数
            Long todayPassed = teacherDashboardMapper.countTodayPasses(teacherId);
            todayStats.put("todayPassed", todayPassed);
            
            // 今日驳回数
            Long todayRejected = todayReviewed - todayPassed;
            todayStats.put("todayRejected", todayRejected >= 0 ? todayRejected : 0);
            
            // 今日新提交
            Long todayNewSubmissions = teacherDashboardMapper.countNewSubmissions(teacherId, 24);
            todayStats.put("todayNewSubmissions", todayNewSubmissions);
            
            // 平均审核耗时（分钟）
            Double avgReviewTime = teacherDashboardMapper.getAverageReviewTime(teacherId);
            todayStats.put("averageReviewTime", avgReviewTime != null ? avgReviewTime.intValue() : 0);
            
        } catch (Exception e) {
            log.warn("获取今日统计失败: teacherId={}", teacherId, e);
            // 返回默认值
            todayStats.put("todayReviewed", 0);
            todayStats.put("todayPassed", 0);
            todayStats.put("todayRejected", 0);
            todayStats.put("todayNewSubmissions", 0);
            todayStats.put("averageReviewTime", 0);
        }
        
        return todayStats;
    }
}
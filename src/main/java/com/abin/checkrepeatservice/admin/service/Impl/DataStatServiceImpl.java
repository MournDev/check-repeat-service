package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.AuditEfficiencyStatDTO;
import com.abin.checkrepeatservice.admin.dto.CheckResultStatDTO;
import com.abin.checkrepeatservice.admin.dto.SubmitTrendStatDTO;
import com.abin.checkrepeatservice.admin.mapper.CheckResultMapper;
import com.abin.checkrepeatservice.admin.mapper.PaperSubmitMapper;
import com.abin.checkrepeatservice.admin.mapper.TeacherAuditMapper;
import com.abin.checkrepeatservice.admin.service.DataStatService;
import com.abin.checkrepeatservice.admin.vo.AuditEfficiencyExcelVO;
import com.abin.checkrepeatservice.admin.vo.CheckResultExcelVO;
import com.abin.checkrepeatservice.admin.vo.StatQueryReq;
import com.abin.checkrepeatservice.admin.vo.SubmitTrendExcelVO;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * 数据统计服务实现类：完整实现提交趋势、查重结果、审核效率统计与Excel导出
 */
@Service
public class DataStatServiceImpl implements DataStatService {

    @Resource
    private PaperSubmitMapper paperSubmitMapper;
    @Resource
    private CheckResultMapper checkResultMapper;
    @Resource
    private TeacherAuditMapper teacherAuditMapper;
    @Resource
    private SysUserMapper SysUserMapper;

    // ========================== 1. 提交趋势统计 ==========================
    @Override
    public Result<SubmitTrendStatDTO> statSubmitTrend(StatQueryReq queryReq) {
        try {
            // 1. 解析并校验筛选条件
            LocalDate startDate = LocalDate.parse(queryReq.getStartDate());
            LocalDate endDate = LocalDate.parse(queryReq.getEndDate());
            if (startDate.isAfter(endDate)) {
                return Result.error(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
            }
            String statDimension = queryReq.getStatDimension();
            Long majorId = queryReq.getMajorId();
            Integer grade = queryReq.getGrade();

            // 2. 生成时间轴（按日/周/月分组）
            List<String> timeAxis = generateTimeAxis(startDate, endDate, statDimension);
            if (CollectionUtils.isEmpty(timeAxis)) {
                return Result.error(ResultCode.PARAM_ERROR, "统计维度无效（仅支持DAY/WEEK/MONTH）");
            }

            // 3. 查询统计周期内需提交论文的学生总数（按专业/年级筛选）
            int totalStudentCount = getSysUserCount(majorId, grade);
            if (totalStudentCount == 0) {
                return Result.success("当前筛选条件下无学生数据", buildEmptySubmitTrend(timeAxis));
            }

            // 4. 查询数据库：按时间维度统计提交人数
            Map<String, Integer> submitCountMap = paperSubmitMapper.countByTimeRange(
                    startDate, endDate, statDimension, majorId, grade
            );

            // 5. 计算每日提交/未提交量与总指标
            List<Integer> dailySubmitCount = new ArrayList<>();
            List<Integer> dailyUnsubmitCount = new ArrayList<>();
            int totalSubmitCount = 0;

            for (String time : timeAxis) {
                int submitNum = submitCountMap.getOrDefault(time, 0);
                dailySubmitCount.add(submitNum);
                dailyUnsubmitCount.add(totalStudentCount - submitNum);
                totalSubmitCount += submitNum;
            }

            // 6. 计算提交率（保留2位小数）
            double submitRate = totalStudentCount == 0
                    ? 0.0
                    : NumberUtil.div(totalSubmitCount, totalStudentCount, 4) * 100;

            // 7. 组装返回结果
            SubmitTrendStatDTO resultDTO = new SubmitTrendStatDTO();
            resultDTO.setTimeAxis(timeAxis);
            resultDTO.setDailySubmitCount(dailySubmitCount);
            resultDTO.setDailyUnsubmitCount(dailyUnsubmitCount);
            resultDTO.setTotalSubmitCount(totalSubmitCount);
            resultDTO.setTotalUnsubmitCount(totalStudentCount - totalSubmitCount);
            resultDTO.setSubmitRate(NumberUtil.round(submitRate, 2).doubleValue());

            return Result.success("提交趋势统计成功", resultDTO);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ResultCode.SYSTEM_ERROR, "提交趋势统计失败：" + e.getMessage());
        }
    }

    // ========================== 2. 查重结果分析 ==========================
    @Override
    public Result<CheckResultStatDTO> statCheckResult(StatQueryReq queryReq, String groupType) {
        try {
            // 1. 校验分组类型（仅支持专业/年级）
            if (!Arrays.asList("MAJOR", "GRADE").contains(groupType)) {
                return Result.error(ResultCode.PARAM_ERROR, "分组类型无效（仅支持MAJOR-专业、GRADE-年级）");
            }

            // 2. 解析筛选条件
            LocalDate startDate = LocalDate.parse(queryReq.getStartDate());
            LocalDate endDate = LocalDate.parse(queryReq.getEndDate());
            if (startDate.isAfter(endDate)) {
                return Result.error(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
            }
            Long majorId = queryReq.getMajorId();
            Integer grade = queryReq.getGrade();

            // 3. 查询数据库：按分组类型统计查重结果（合格/不合格/平均重复率）
            List<Map<String, Object>> statList = checkResultMapper.statByGroup(
                    startDate, endDate, groupType, majorId, grade
            );

            if (CollectionUtils.isEmpty(statList)) {
                return Result.success("当前筛选条件下无查重数据", new CheckResultStatDTO());
            }

            // 4. 组装分组轴与统计指标
            List<String> groupAxis = new ArrayList<>();
            List<Integer> qualifiedCount = new ArrayList<>();
            List<Integer> unqualifiedCount = new ArrayList<>();
            List<Double> avgRepeatRate = new ArrayList<>();
            int totalQualified = 0;
            int totalCount = 0;

            for (Map<String, Object> statMap : statList) {
                // 分组名称（专业名或年级）
                String groupName = groupType.equals("MAJOR")
                        ? StrUtil.nullToEmpty(statMap.get("major_name").toString())
                        : StrUtil.nullToEmpty(statMap.get("grade").toString());
                groupAxis.add(groupName);

                // 解析统计数值
                int qualified = Integer.parseInt(statMap.get("qualified_count").toString());
                int unqualified = Integer.parseInt(statMap.get("unqualified_count").toString());
                double avgRate = Double.parseDouble(statMap.get("avg_repeat_rate").toString());

                // 累加指标
                qualifiedCount.add(qualified);
                unqualifiedCount.add(unqualified);
                avgRepeatRate.add(NumberUtil.round(avgRate, 2).doubleValue());
                totalQualified += qualified;
                totalCount += (qualified + unqualified);
            }

            // 5. 计算总体合格率
            double totalQualifiedRate = totalCount == 0
                    ? 0.0
                    : NumberUtil.div(totalQualified, totalCount, 4) * 100;

            // 6. 组装返回结果
            CheckResultStatDTO resultDTO = new CheckResultStatDTO();
            resultDTO.setGroupAxis(groupAxis);
            resultDTO.setQualifiedCount(qualifiedCount);
            resultDTO.setUnqualifiedCount(unqualifiedCount);
            resultDTO.setAvgRepeatRate(avgRepeatRate);
            resultDTO.setTotalQualifiedRate(NumberUtil.round(totalQualifiedRate, 2).doubleValue());

            return Result.success("查重结果分析成功", resultDTO);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ResultCode.SYSTEM_ERROR, "查重结果分析失败：" + e.getMessage());
        }
    }

    // ========================== 3. 审核效率统计 ==========================
    @Override
    public Result<AuditEfficiencyStatDTO> statAuditEfficiency(StatQueryReq queryReq) {
        try {
            // 1. 解析筛选条件
            LocalDate startDate = LocalDate.parse(queryReq.getStartDate());
            LocalDate endDate = LocalDate.parse(queryReq.getEndDate());
            if (startDate.isAfter(endDate)) {
                return Result.error(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
            }
            Long teacherId = queryReq.getTeacherId();
            Long majorId = queryReq.getMajorId();

            // 2. 查询教师列表（按专业/指定教师筛选）
            List<SysUser> teacherList = SysUserMapper.selectTeacherList(majorId, teacherId);
            if (CollectionUtils.isEmpty(teacherList)) {
                return Result.success("当前筛选条件下无教师数据", new AuditEfficiencyStatDTO());
            }

            // 3. 统计各教师审核数据
            List<String> teacherAxis = new ArrayList<>();
            List<Integer> pendingAuditCount = new ArrayList<>();
            List<Integer> completedAuditCount = new ArrayList<>();
            List<Double> avgAuditTime = new ArrayList<>();
            int totalPending = 0;
            int totalCompleted = 0;

            for (SysUser teacher : teacherList) {
                Long tid = teacher.getId();
                String teacherName = teacher.getRealName();
                teacherAxis.add(teacherName);

                // 3.1 统计待审核任务数（已提交未审核）
                int pending = teacherAuditMapper.countPendingAudit(tid, startDate, endDate);
                pendingAuditCount.add(pending);
                totalPending += pending;

                // 3.2 统计已审核任务数与平均耗时（分钟）
                Map<String, Object> completedStat = teacherAuditMapper.countCompletedAudit(tid, startDate, endDate);
                int completed = Integer.parseInt(completedStat.get("count").toString());
                long totalTimeSec = Long.parseLong(completedStat.get("total_time").toString()); // 总耗时（秒）
                double avgTimeMin = completed == 0
                        ? 0.0
                        : NumberUtil.div(totalTimeSec, completed * 60, 1); // 转换为分钟并保留1位小数

                completedAuditCount.add(completed);
                avgAuditTime.add(avgTimeMin);
                totalCompleted += completed;
            }

            // 4. 组装返回结果
            AuditEfficiencyStatDTO resultDTO = new AuditEfficiencyStatDTO();
            resultDTO.setTeacherAxis(teacherAxis);
            resultDTO.setPendingAuditCount(pendingAuditCount);
            resultDTO.setCompletedAuditCount(completedAuditCount);
            resultDTO.setAvgAuditTime(avgAuditTime);
            resultDTO.setTotalPendingCount(totalPending);
            resultDTO.setTotalCompletedCount(totalCompleted);

            return Result.success("审核效率统计成功", resultDTO);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ResultCode.SYSTEM_ERROR, "审核效率统计失败：" + e.getMessage());
        }
    }

    // ========================== 4. Excel 导出 ==========================
    @Override
    public void exportStatResult(StatQueryReq queryReq, String statType, HttpServletResponse response) {
        try {
            // 1. 校验统计类型
            if (!Arrays.asList("SUBMIT", "CHECK", "AUDIT").contains(statType)) {
                throw new IllegalArgumentException("统计类型无效（仅支持SUBMIT-提交趋势、CHECK-查重结果、AUDIT-审核效率）");
            }

            // 2. 设置Excel响应头（避免中文乱码）
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode(
                    String.format("%s统计_%s_%s",
                            getStatTypeName(statType),
                            queryReq.getStartDate(),
                            queryReq.getEndDate()),
                    StandardCharsets.UTF_8.name()
            );
            response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

            // 3. 获取统计数据并写入Excel
            try (OutputStream outputStream = response.getOutputStream()) {
                ExcelWriterBuilder writerBuilder = EasyExcel.write(outputStream);
                switch (statType) {
                    case "SUBMIT":
                        // 导出提交趋势
                        SubmitTrendStatDTO submitDTO = statSubmitTrend(queryReq).getData();
                        List<SubmitTrendExcelVO> submitVOList = convertSubmitTrendToExcel(submitDTO);
                        writerBuilder.head(SubmitTrendExcelVO.class)
                                .sheet("提交趋势统计")
                                .doWrite(submitVOList);
                        break;
                    case "CHECK":
                        // 导出查重结果（默认按专业分组，可扩展支持年级）
                        CheckResultStatDTO checkDTO = statCheckResult(queryReq, "MAJOR").getData();
                        List<CheckResultExcelVO> checkVOList = convertCheckResultToExcel(checkDTO);
                        writerBuilder.head(CheckResultExcelVO.class)
                                .sheet("查重结果分析")
                                .doWrite(checkVOList);
                        break;
                    case "AUDIT":
                        // 导出审核效率
                        AuditEfficiencyStatDTO auditDTO = statAuditEfficiency(queryReq).getData();
                        List<AuditEfficiencyExcelVO> auditVOList = convertAuditEfficiencyToExcel(auditDTO);
                        writerBuilder.head(AuditEfficiencyExcelVO.class)
                                .sheet("审核效率统计")
                                .doWrite(auditVOList);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 异常时返回错误信息
            try {
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Excel导出失败：" + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // ------------------------------ 私有辅助方法 ------------------------------

    /**
     * 生成时间轴（按日/周/月）
     *
     * @param start     开始日期
     * @param end       结束日期
     * @param dimension 统计维度（DAY/WEEK/MONTH）
     * @return 时间轴列表（如["2024-09-01", "2024-09-02"]）
     */
    private List<String> generateTimeAxis(LocalDate start, LocalDate end, String dimension) {
        List<String> timeAxis = new ArrayList<>();
        LocalDate current = start;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        while (!current.isAfter(end)) {
            switch (dimension) {
                case "DAY":
                    // 按日：直接添加当前日期
                    timeAxis.add(current.format(formatter));
                    current = current.plusDays(1);
                    break;
                case "WEEK":
                    // 按周：添加本周一日期（如"2024-09-02 至 2024-09-08"）
                    LocalDate weekStart = current.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    LocalDate weekEnd = weekStart.plusDays(6);
                    // 确保周末不超过结束日期
                    weekEnd = weekEnd.isAfter(end) ? end : weekEnd;
                    timeAxis.add(String.format("%s 至 %s", weekStart.format(formatter), weekEnd.format(formatter)));
                    current = weekEnd.plusDays(1);
                    break;
                case "MONTH":
                    // 按月：添加当月第一天（如"2024-09"）
                    String monthStr = current.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    timeAxis.add(monthStr);
                    current = current.plusMonths(1);
                    break;
                default:
                    return Collections.emptyList();
            }
        }
        return timeAxis;
    }

    /**
     * 查询需提交论文的学生总数（按专业/年级筛选）
     *
     * @param majorId 专业ID（null则查所有）
     * @param grade   年级（null则查所有）
     * @return 学生总数
     */
    private int getSysUserCount(Long majorId, Integer grade) {
        return SysUserMapper.countStudentByCondition(majorId, grade);
    }

    /**
     * 提交趋势统计 DTO 转换为 Excel VO 列表（含汇总行）
     * @param submitDTO 提交趋势统计结果
     * @return Excel VO 列表
     */
    private List<SubmitTrendExcelVO> convertSubmitTrendToExcel(SubmitTrendStatDTO submitDTO) {
        List<SubmitTrendExcelVO> voList = new ArrayList<>();
        List<String> timeAxis = submitDTO.getTimeAxis();
        List<Integer> submitCountList = submitDTO.getDailySubmitCount();
        List<Integer> unsubmitCountList = submitDTO.getDailyUnsubmitCount();
        Double totalSubmitRate = submitDTO.getSubmitRate();

        // 1. 添加普通行（按时间维度）
        for (int i = 0; i < timeAxis.size(); i++) {
            SubmitTrendExcelVO vo = new SubmitTrendExcelVO();
            vo.setTimeDimension(timeAxis.get(i));
            vo.setSubmitCount(submitCountList.get(i));
            vo.setUnsubmitCount(unsubmitCountList.get(i));
            // 计算当前时间维度的提交率（小数格式，适配Excel百分比）
            int total = submitCountList.get(i) + unsubmitCountList.get(i);
            double rate = total == 0 ? 0.0 : submitCountList.get(i) * 1.0 / total;
            vo.setSubmitRate(rate);
            voList.add(vo);
        }

        // 2. 添加汇总行（最后一行）
        SubmitTrendExcelVO summaryVO = SubmitTrendExcelVO.buildSummaryVO(
                submitDTO.getTotalSubmitCount(),
                submitDTO.getTotalUnsubmitCount(),
                totalSubmitRate
        );
        voList.add(summaryVO);

        return voList;
    }
    /**
     * 查重结果分析 DTO 转换为 Excel VO 列表（含总体汇总行）
     * @param checkDTO 查重结果统计结果
     * @return Excel VO 列表
     */
    private List<CheckResultExcelVO> convertCheckResultToExcel(CheckResultStatDTO checkDTO) {
        List<CheckResultExcelVO> voList = new ArrayList<>();
        List<String> groupAxis = checkDTO.getGroupAxis();
        List<Integer> qualifiedList = checkDTO.getQualifiedCount();
        List<Integer> unqualifiedList = checkDTO.getUnqualifiedCount();
        List<Double> avgRateList = checkDTO.getAvgRepeatRate();

        // 1. 计算总体统计值
        int totalQualified = qualifiedList.stream().mapToInt(Integer::intValue).sum();
        int totalUnqualified = unqualifiedList.stream().mapToInt(Integer::intValue).sum();
        double totalAvgRate = avgRateList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // 2. 添加分组行（按专业/年级）
        for (int i = 0; i < groupAxis.size(); i++) {
            CheckResultExcelVO groupVO = CheckResultExcelVO.buildGroupVO(
                    groupAxis.get(i),
                    qualifiedList.get(i),
                    unqualifiedList.get(i),
                    avgRateList.get(i) // 原始值如12.30（百分比）
            );
            voList.add(groupVO);
        }

        // 3. 添加总体汇总行（最后一行）
        CheckResultExcelVO totalSummaryVO = CheckResultExcelVO.buildTotalSummaryVO(
                totalQualified,
                totalUnqualified,
                totalAvgRate
        );
        voList.add(totalSummaryVO);

        return voList;
    }
    /**
     * 审核效率统计 DTO 转换为 Excel VO 列表（含全局汇总行）
     * @param auditDTO 审核效率统计结果（教师列表、待审核/已审核数、平均耗时）
     * @return Excel VO 列表（教师行 + 汇总行）
     */
    private List<AuditEfficiencyExcelVO> convertAuditEfficiencyToExcel(AuditEfficiencyStatDTO auditDTO) {
        List<AuditEfficiencyExcelVO> voList = new ArrayList<>();

        // 1. 校验 DTO 数据（避免空指针）
        if (auditDTO == null
                || CollectionUtils.isEmpty(auditDTO.getTeacherAxis())
                || CollectionUtils.isEmpty(auditDTO.getPendingAuditCount())
                || CollectionUtils.isEmpty(auditDTO.getCompletedAuditCount())
                || CollectionUtils.isEmpty(auditDTO.getAvgAuditTime())) {
            return voList; // 无数据时返回空列表，避免导出空Excel报错
        }

        // 2. 提取 DTO 中的核心数据（确保各列表长度一致）
        List<String> teacherNames = auditDTO.getTeacherAxis(); // 教师姓名列表
        List<Integer> pendingCounts = auditDTO.getPendingAuditCount(); // 待审核数列表
        List<Integer> completedCounts = auditDTO.getCompletedAuditCount(); // 已审核数列表
        List<Double> avgTimeList = auditDTO.getAvgAuditTime(); // 平均耗时列表（分钟，保留1位小数）
        int dataSize = teacherNames.size();

        // 校验各列表长度一致性（防止数组越界）
        if (pendingCounts.size() != dataSize
                || completedCounts.size() != dataSize
                || avgTimeList.size() != dataSize) {
            throw new IllegalArgumentException("审核效率统计数据格式异常：各字段列表长度不一致");
        }

        // 3. 生成普通教师行 VO（逐行映射）
        for (int i = 0; i < dataSize; i++) {
            AuditEfficiencyExcelVO teacherVO = AuditEfficiencyExcelVO.buildTeacherVO(
                    teacherNames.get(i), // 教师姓名
                    pendingCounts.get(i), // 待审核任务数
                    completedCounts.get(i), // 已审核任务数
                    avgTimeList.get(i) // 平均审核耗时（分钟，直接复用DTO中的格式化后的值）
            );
            voList.add(teacherVO);
        }

        // 4. 计算全局汇总数据（用于生成汇总行）
        int totalPending = auditDTO.getTotalPendingCount(); // 全局总待审核数（DTO中已计算好，直接复用）
        int totalCompleted = auditDTO.getTotalCompletedCount(); // 全局总已审核数（DTO中已计算好）
        int totalTeacher = dataSize; // 教师总数（即数据列表长度）

        // 5. 生成全局汇总行 VO（添加到列表最后）
        AuditEfficiencyExcelVO globalSummaryVO = AuditEfficiencyExcelVO.buildGlobalSummaryVO(
                totalPending,
                totalCompleted,
                totalTeacher
        );
        voList.add(globalSummaryVO);

        return voList;
    }
    /**
     * 根据统计类型获取中文名称
     *
     * @param statType 统计类型（SUBMIT/CHECK/AUDIT）
     * @return 对应的中文名称
     */
    private String getStatTypeName(String statType) {
        switch (statType) {
            case "SUBMIT":
                return "提交趋势";
            case "CHECK":
                return "查重结果";
            case "AUDIT":
                return "审核效率";
            default:
                return "未知统计";
        }
    }
    private SubmitTrendStatDTO buildEmptySubmitTrend(List<String> timeAxis) {
        SubmitTrendStatDTO dto = new SubmitTrendStatDTO();
        dto.setTimeAxis(timeAxis);
        dto.setDailySubmitCount(Collections.nCopies(timeAxis.size(), 0));
        dto.setDailyUnsubmitCount(Collections.nCopies(timeAxis.size(), 0));
        dto.setTotalSubmitCount(0);
        dto.setTotalUnsubmitCount(0);
        dto.setSubmitRate(0.0);
        return dto;
    }

}
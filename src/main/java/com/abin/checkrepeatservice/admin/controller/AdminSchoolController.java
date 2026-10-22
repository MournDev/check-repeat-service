package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.mapper.CheckResultMapper;
import com.abin.checkrepeatservice.admin.service.DataStatService;
import com.abin.checkrepeatservice.monitor.service.SystemMonitorService;
import com.abin.checkrepeatservice.admin.vo.CollegePaperStatsVO;
import com.abin.checkrepeatservice.admin.vo.StatQueryReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.datascope.DataScopeResolver;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员学校数据概览控制器
 */
@RestController
@RequestMapping("/api/admin/school")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AdminSchoolController {

    @Resource
    private DataStatService dataStatService;

    @Resource
    private SysUserService sysUserService;
    
    @Resource
    private PaperInfoMapper paperInfoMapper;
    
    @Resource
    private CheckResultMapper checkResultMapper;
    
    @Resource
    private SysLoginLogMapper sysLoginLogMapper;

    @Resource
    private SystemMonitorService systemMonitorService;

    @Resource
    private DataScopeResolver dataScopeResolver;

    /**
     * 获取学校概览数据
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getSchoolOverview() {
        Map<String, Object> overview = new HashMap<>();
        
        // 用户统计
        Long totalUsers = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, 0));
        Long students = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "STUDENT")
                .eq(SysUser::getIsDeleted, 0));
        Long teachers = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "TEACHER")
                .eq(SysUser::getIsDeleted, 0));
        Long admins = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "ADMIN")
                .eq(SysUser::getIsDeleted, 0));
        
        overview.put("totalUsers", totalUsers);
        overview.put("students", students);
        overview.put("teachers", teachers);
        overview.put("admins", admins);
        
        // 论文统计（按当前登录人数据范围过滤；ALL 时无附加条件）
        LambdaQueryWrapper<PaperInfo> totalPaperWrapper = new LambdaQueryWrapper<>();
        dataScopeResolver.applyPaperScope(totalPaperWrapper);
        Long totalPapers = paperInfoMapper.selectCount(totalPaperWrapper);
        LambdaQueryWrapper<PaperInfo> pendingPaperWrapper = new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.PENDING);
        dataScopeResolver.applyPaperScope(pendingPaperWrapper);
        Long pendingPapers = paperInfoMapper.selectCount(pendingPaperWrapper);
        LambdaQueryWrapper<PaperInfo> checkedPaperWrapper = new LambdaQueryWrapper<PaperInfo>()
                .ne(PaperInfo::getPaperStatus, DictConstants.PaperStatus.PENDING);
        dataScopeResolver.applyPaperScope(checkedPaperWrapper);
        Long checkedPapers = paperInfoMapper.selectCount(checkedPaperWrapper);

        overview.put("totalPapers", totalPapers);
        overview.put("pendingPapers", pendingPapers);
        overview.put("checkedPapers", checkedPapers);
        
        // 查重统计（按查重结果关联的论文归属过滤）
        // 修复：使用正确的泛型类型
        LambdaQueryWrapper<CheckResult> highSimilarityWrapper = new LambdaQueryWrapper<CheckResult>()
                .apply("repeat_rate >= {0}", 80);
        dataScopeResolver.applyPaperIdScope(highSimilarityWrapper, "paper_id");
        Long highSimilarity = checkResultMapper.selectCount(highSimilarityWrapper);
        
        overview.put("highSimilarityPapers", highSimilarity);
        
        // 在线用户（简单统计最近1小时内登录的用户）
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Long onlineUsers = sysLoginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                .ge(SysLoginLog::getLoginTime, oneHourAgo));
        
        overview.put("onlineUsers", onlineUsers);
        
        return Result.success("学校概览数据获取成功", overview);
    }

    /**
     * 获取学院论文分布 - 修改返回类型
     */
    @GetMapping("/college-distribution")
    public Result<Map<String, Object>> getCollegeDistribution() {
        Map<String, Object> stats = new HashMap<>();
        // getCollegeDistributionStats 内部已做异常隔离并返回空分布，不再降级成硬编码假数据
        stats.put("collegeStats", getCollegeDistributionStats());
        return Result.success("学院分布数据获取成功", stats);
    }

    /**
     * 获取学院分布统计 - 使用VO形式
     */
    private Map<String, Object> getCollegeDistributionStats() {
        Map<String, Object> collegeStats = new HashMap<>();

        try {
            // 使用VO形式的查询方法（按当前登录人数据范围过滤）
            List<CollegePaperStatsVO> collegePaperStats =
                    paperInfoMapper.selectCollegePaperStats(dataScopeResolver.currentFilterOrNull());

            // 转换为Map格式（如果前端需要）
            List<Map<String, Object>> result = collegePaperStats.stream()
                    .map(vo -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("collegeId", vo.getCollegeId());
                        map.put("collegeName", vo.getCollegeName());
                        map.put("paperCount", vo.getPaperCount());
                        return map;
                    })
                    .collect(Collectors.toList());

            collegeStats.put("distribution", result);
            collegeStats.put("totalCount", result.size());

        } catch (Exception e) {
            log.error("获取学院分布统计失败", e);
            collegeStats.put("distribution", new ArrayList<>());
            collegeStats.put("totalCount", 0);
        }

        return collegeStats;
    }

    /**
     * 获取月度论文趋势
     */
    @GetMapping("/monthly-trend")
    public Result<List<Map<String, Object>>> getMonthlyTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        
        // 获取过去6个月的数据
        LocalDateTime now = LocalDateTime.now();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);
            
            // 查询该月的论文提交数（按当前登录人数据范围过滤）
            LambdaQueryWrapper<PaperInfo> submissionWrapper = new LambdaQueryWrapper<PaperInfo>()
                    .ge(PaperInfo::getCreateTime, monthStart)
                    .le(PaperInfo::getCreateTime, monthEnd);
            dataScopeResolver.applyPaperScope(submissionWrapper);
            Long submissionCount = paperInfoMapper.selectCount(submissionWrapper);
            
            // 查询该月的查重完成数（按查重结果关联的论文归属过滤）
            LambdaQueryWrapper<CheckResult> checkCountWrapper = new LambdaQueryWrapper<CheckResult>()
                    .ge(CheckResult::getCreateTime, monthStart)
                    .le(CheckResult::getCreateTime, monthEnd);
            dataScopeResolver.applyPaperIdScope(checkCountWrapper, "paper_id");
            Long checkCount = checkResultMapper.selectCount(checkCountWrapper);
            
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", monthStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
            monthData.put("submissionCount", submissionCount);
            monthData.put("checkCount", checkCount);
            
            trend.add(monthData);
        }
        
        return Result.success("月度趋势数据获取成功", trend);
    }

    /**
     * 获取查重结果分布
     */
    @GetMapping("/similarity-distribution")
    public Result<Map<String, Object>> getSimilarityDistribution() {
        Map<String, Object> distribution = new HashMap<>();
        
        // 从数据库查询真实的查重结果分布（按查重结果关联的论文归属过滤）
        LambdaQueryWrapper<CheckResult> lowWrapper = new LambdaQueryWrapper<CheckResult>()
                .lt(CheckResult::getRepeatRate, new BigDecimal("30"));
        dataScopeResolver.applyPaperIdScope(lowWrapper, "paper_id");
        Long lowSimilarity = checkResultMapper.selectCount(lowWrapper);
        
        LambdaQueryWrapper<CheckResult> mediumWrapper = new LambdaQueryWrapper<CheckResult>()
                .ge(CheckResult::getRepeatRate, new BigDecimal("30"))
                .lt(CheckResult::getRepeatRate, new BigDecimal("60"));
        dataScopeResolver.applyPaperIdScope(mediumWrapper, "paper_id");
        Long mediumSimilarity = checkResultMapper.selectCount(mediumWrapper);
        
        LambdaQueryWrapper<CheckResult> highWrapper = new LambdaQueryWrapper<CheckResult>()
                .ge(CheckResult::getRepeatRate, new BigDecimal("60"));
        dataScopeResolver.applyPaperIdScope(highWrapper, "paper_id");
        Long highSimilarity = checkResultMapper.selectCount(highWrapper);
        
        distribution.put("lowSimilarity", lowSimilarity);      // 0-30%
        distribution.put("mediumSimilarity", mediumSimilarity); // 30-60%
        distribution.put("highSimilarity", highSimilarity);     // 60-100%
        
        return Result.success("查重结果分布获取成功", distribution);
    }

    /**
     * 获取实时统计
     */
    @GetMapping("/realtime-stats")
    public Result<Map<String, Object>> getRealtimeStats() {
        Map<String, Object> stats = new HashMap<>();

        // 今日论文提交数（按当前登录人数据范围过滤）
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LambdaQueryWrapper<PaperInfo> todayPaperWrapper = new LambdaQueryWrapper<PaperInfo>()
                .ge(PaperInfo::getCreateTime, todayStart);
        dataScopeResolver.applyPaperScope(todayPaperWrapper);
        Long todayPapers = paperInfoMapper.selectCount(todayPaperWrapper);
        stats.put("todayPapers", todayPapers);

        // 本周审核数（按查重结果关联的论文归属过滤）
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<CheckResult> weekReviewWrapper = new LambdaQueryWrapper<CheckResult>()
                .ge(CheckResult::getCreateTime, weekStart);
        dataScopeResolver.applyPaperIdScope(weekReviewWrapper, "paper_id");
        Long weekReviews = checkResultMapper.selectCount(weekReviewWrapper);
        stats.put("weekReviews", weekReviews);

        // 在线用户数
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Long onlineUsers = sysLoginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                .ge(SysLoginLog::getLoginTime, oneHourAgo));
        stats.put("onlineUsers", onlineUsers);

        // 系统CPU使用率（从数据库查询）
        Integer systemLoad = getSystemLoadFromDatabase();
        stats.put("systemLoad", systemLoad);

        // 当前活跃任务数（按当前登录人数据范围过滤）
        LambdaQueryWrapper<PaperInfo> activeTaskWrapper = new LambdaQueryWrapper<PaperInfo>()
                .in(PaperInfo::getPaperStatus, Arrays.asList(
                        DictConstants.PaperStatus.CHECKING,
                        DictConstants.PaperStatus.AUDITING));
        dataScopeResolver.applyPaperScope(activeTaskWrapper);
        Long activeTasks = paperInfoMapper.selectCount(activeTaskWrapper);
        stats.put("activeTasks", activeTasks);

        // 今日新增用户数
        Long todayNewUsers = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .ge(SysUser::getCreateTime, todayStart)
                .eq(SysUser::getIsDeleted, 0));
        stats.put("todayNewUsers", todayNewUsers);

        return Result.success("实时统计数据获取成功", stats);
    }

    /**
     * 导出学校数据报告
     */
    @GetMapping("/export-report")
    public void exportSchoolReport(HttpServletResponse response,
                                 @RequestParam(required = false) String startDate,
                                 @RequestParam(required = false) String endDate) throws IOException {
        
        // 构建统计查询条件
        StatQueryReq queryReq = new StatQueryReq();
        if (startDate != null && !startDate.isEmpty()) {
            queryReq.setStartDate(startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            queryReq.setEndDate(endDate);
        }
        
        // 调用现有的数据统计服务导出功能（修正方法名和参数）
        dataStatService.exportStatResult(queryReq, "CHECK", response);
    }
    
    /**
     * 获取真实系统负载（整机CPU使用率，取自 SystemMonitorService，底层 MXBean 数据）。
     * 取不到（JDK 不支持 getCpuLoad 等场景）退回 JVM 进程 CPU 使用率，再取不到返回 null，
     * 让前端显示未知，绝不返回随机假数据。
     */
    private Integer getSystemLoadFromDatabase() {
        try {
            Map<String, Object> cpuInfo = systemMonitorService.getCpuInfo();
            Object systemCpuUsage = cpuInfo.get("systemCpuUsage");
            if (systemCpuUsage instanceof Number n && n.doubleValue() >= 0) {
                return (int) Math.round(n.doubleValue());
            }
            Object processCpuUsage = cpuInfo.get("processCpuUsage");
            if (processCpuUsage instanceof Number n && n.doubleValue() >= 0) {
                return (int) Math.round(n.doubleValue());
            }
            return null;
        } catch (Exception e) {
            log.warn("获取系统负载信息失败: {}", e.getMessage());
            return null;
        }
    }
}
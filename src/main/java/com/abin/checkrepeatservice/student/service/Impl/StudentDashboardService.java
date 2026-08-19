package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.mapper.FileInfoMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.abin.checkrepeatservice.student.dto.*;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardService {
    
    @Resource
    private PaperInfoMapper paperInfoMapper;
    
    @Resource
    private FileInfoMapper fileInfoMapper;
    
    @Resource
    private CheckTaskMapper checkTaskMapper;
    
    @Resource
    private SysUserMapper sysUserMapper;
    
    @Resource
    private SystemConfigMapper systemConfigMapper;

    public StudentDashboardStatsDTO getDashboardStats(Long studentId) {
        StudentDashboardStatsDTO stats = new StudentDashboardStatsDTO();
        
        // 1. 获取论文统计
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getIsDeleted, 0);
        
        // 总提交数
        Long totalCount = paperInfoMapper.selectCount(wrapper);
        stats.setSubmittedCount(totalCount.intValue());
        
        // 待审核数 (状态: auditing)
        wrapper.clear();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getPaperStatus, "auditing")
               .eq(PaperInfo::getIsDeleted, 0);
        Long pendingCount = paperInfoMapper.selectCount(wrapper);
        stats.setPendingCount(pendingCount.intValue());
        
        // 已通过数 (状态: completed)
        wrapper.clear();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getPaperStatus, "completed")
               .eq(PaperInfo::getIsDeleted, 0);
        Long approvedCount = paperInfoMapper.selectCount(wrapper);
        stats.setApprovedCount(approvedCount.intValue());
        
        // 需修改数 (状态: rejected)
        wrapper.clear();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getPaperStatus, "rejected")
               .eq(PaperInfo::getIsDeleted, 0);
        Long revisionCount = paperInfoMapper.selectCount(wrapper);
        stats.setRevisionCount(revisionCount.intValue());
        
        // 未通过数
        stats.setFailedCount(totalCount.intValue() - approvedCount.intValue() - pendingCount.intValue() - revisionCount.intValue());
        
        // 2. 计算完成度
        BigDecimal completionRate = totalCount > 0 ? 
            new BigDecimal(approvedCount).multiply(new BigDecimal(100)).divide(new BigDecimal(totalCount), 2, BigDecimal.ROUND_HALF_UP) : 
            BigDecimal.ZERO;
        stats.setCompletionRate(completionRate);
        
        // 3. 获取当前进度 (1-提交, 2-查重, 3-审核, 4-完成)
        LatestPaperDTO latestPaper = getLatestPaper(studentId);
        if (latestPaper != null) {
            stats.setCurrentProgress(getProgressFromStatus(latestPaper.getStatus()));
        } else {
            stats.setCurrentProgress(1);
        }
        
        // 4. 获取本周趋势
        LocalDate startOfWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Date weekStart = Date.from(startOfWeek.atStartOfDay(ZoneId.systemDefault()).toInstant());
        
        // 本周提交数
        wrapper.clear();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .ge(PaperInfo::getSubmitTime, weekStart)
               .eq(PaperInfo::getIsDeleted, 0);
        Long thisWeekSubmitted = paperInfoMapper.selectCount(wrapper);
        stats.setThisWeekSubmitted(thisWeekSubmitted.intValue());
        
        // 本周通过数
        wrapper.clear();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .ge(PaperInfo::getSubmitTime, weekStart)
               .eq(PaperInfo::getPaperStatus, "completed")
               .eq(PaperInfo::getIsDeleted, 0);
        Long thisWeekApproved = paperInfoMapper.selectCount(wrapper);
        stats.setThisWeekApproved(thisWeekApproved.intValue());
        
        // 5. 计算平均分 (假设论文有评分字段)
        stats.setAvgScore(BigDecimal.valueOf(85.5)); // 模拟数据
        
        // 6. 计算总字数
        Integer totalWords = getTotalWordCount(studentId);
        stats.setTotalWordCount(totalWords != null ? totalWords : 0);
        
        return stats;
    }

    public LatestPaperDTO getLatestPaper(Long studentId) {
        PaperInfo latestPaper = paperInfoMapper.selectLatestPaper(studentId);
        if (latestPaper == null) {
            return null;
        }
        FileInfo fileInfo = fileInfoMapper.selectById(latestPaper.getFileId());
        LatestPaperDTO dto = new LatestPaperDTO();
        dto.setId(latestPaper.getId());
        dto.setWordCount(fileInfo.getWordCount());
        dto.setTitle(latestPaper.getPaperTitle());
        dto.setStatus(latestPaper.getPaperStatus());
        dto.setSubmitTime(latestPaper.getSubmitTime());
        dto.setApproveTime(latestPaper.getSubmitTime());
        // 获取导师信息
        if (latestPaper.getTeacherId() != null) {
            SysUser advisor = sysUserMapper.selectById(latestPaper.getTeacherId());
            if (advisor != null) {
                dto.setAdvisorName(advisor.getRealName());
            }
        }

        // 获取最新的反馈
//        PaperFeedback feedback = paperInfoMapper.selectLatestFeedback(latestPaper.getId());
//        if (feedback != null) {
//            dto.setFeedback(feedback.getContent());
//        }

        // 获取查重率
        Long latestPaperId = latestPaper.getId();
        CheckTask checkTask = checkTaskMapper.selectLatestByPaperId(latestPaperId);
        if (checkTask != null && checkTask.getCheckRate() != null) {
            dto.setSimilarity(checkTask.getCheckRate());
        }

        return dto;
    }

    public AdvisorInfoDTO getAdvisorInfo(Long studentId) {
        // 获取学生信息，找到分配的导师
        PaperInfo latestPaper = paperInfoMapper.selectLatestPaper(studentId);
        if (latestPaper == null || latestPaper.getTeacherId() == null) {
            return null;
        }

        SysUser teacher = sysUserMapper.selectById(latestPaper.getTeacherId());
        if (teacher == null) {
            return null;
        }

        AdvisorInfoDTO dto = new AdvisorInfoDTO();
        dto.setId(teacher.getId());
        dto.setName(teacher.getRealName());
//        dto.setTitle(teacher.getTitle());
        dto.setPhone(teacher.getPhone());
        dto.setEmail(teacher.getEmail());
//        dto.setOffice(teacher.getOffice());
        dto.setAvatar(teacher.getAvatar());
        dto.setResearchField(teacher.getResearchDirection());

        // 专长领域（逗号分隔转换为列表）
//        if (StringUtils.isNotBlank(advisor.getExpertise())) {
//            dto.setExpertise(Arrays.asList(advisor.getExpertise().split(",")));
//        }

        // 导师统计
//        dto.setGuidedPapersCount(advisorInfoMapper.countGuidedPapers(advisor.getId()));
//        dto.setApprovalRate(advisorInfoMapper.calculateApprovalRate(advisor.getId()));
//        dto.setAverageScore(advisorInfoMapper.calculateAverageScore(advisor.getId()));
        dto.setOnlineStatus("online"); // 可以从在线状态服务获取

        return dto;
    }
    
    /**
     * 根据论文状态获取进度
     */
    private Integer getProgressFromStatus(String status) {
        switch (status) {
            case "pending": return 1;      // 已提交
            case "checking": return 2;     // 查重中
            case "auditing": return 3;     // 审核中
            case "completed": return 4;    // 已完成
            case "rejected": return 3;     // 需修改（回到审核阶段）
            default: return 1;
        }
    }
    
    /**
     * 获取学生论文总字数
     */
    private Integer getTotalWordCount(Long studentId) {
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getIsDeleted, 0);
        
        List<PaperInfo> papers = paperInfoMapper.selectList(wrapper);
        if (papers == null || papers.isEmpty()) {
            return 0;
        }
        
        return papers.stream()
                .mapToInt(paper -> {
                    FileInfo fileInfo = fileInfoMapper.selectById(paper.getFileId());
                    return fileInfo != null ? fileInfo.getWordCount() : 0;
                })
                .sum();
    }
    
    /**
     * 获取时间节点信息（从数据库读取系统配置）
     */
    public DeadlinesDTO getDeadlines() {
        DeadlinesDTO deadlines = new DeadlinesDTO();
        
        try {
            // 从 system_config 表读取时间节点配置
            // 提交截止日期
            SystemConfig submissionConfig = systemConfigMapper.selectByConfigKey("submission_deadline");
            if (submissionConfig != null) {
                deadlines.setSubmissionDeadline(submissionConfig.getConfigValue());
            } else {
                // 如果数据库没有配置，使用默认值
                deadlines.setSubmissionDeadline("2026-03-20");
                log.warn("未找到论文提交截止日期配置，使用默认值：{}", deadlines.getSubmissionDeadline());
            }
            
            // 审核截止日期
            SystemConfig reviewConfig = systemConfigMapper.selectByConfigKey("review_deadline");
            if (reviewConfig != null) {
                deadlines.setReviewDeadline(reviewConfig.getConfigValue());
            } else {
                deadlines.setReviewDeadline("2026-04-10");
                log.warn("未找到审核截止日期配置，使用默认值：{}", deadlines.getReviewDeadline());
            }
            
            // 答辩时间
            SystemConfig defenseConfig = systemConfigMapper.selectByConfigKey("defense_date");
            if (defenseConfig != null) {
                deadlines.setDefenseDate(defenseConfig.getConfigValue());
            } else {
                deadlines.setDefenseDate("2026-05-15");
                log.warn("未找到答辩时间配置，使用默认值：{}", deadlines.getDefenseDate());
            }
            
            // 预计毕业时间
            SystemConfig graduationConfig = systemConfigMapper.selectByConfigKey("graduation_date");
            if (graduationConfig != null) {
                deadlines.setGraduationDate(graduationConfig.getConfigValue());
            } else {
                deadlines.setGraduationDate("2026-06-30");
                log.warn("未找到预计毕业时间配置，使用默认值：{}", deadlines.getGraduationDate());
            }
            
            log.info("时间节点配置加载成功：提交截止={}, 审核截止={}, 答辩={}, 毕业={}",
                deadlines.getSubmissionDeadline(),
                deadlines.getReviewDeadline(),
                deadlines.getDefenseDate(),
                deadlines.getGraduationDate());
                
        } catch (Exception e) {
            log.error("读取时间节点配置失败，使用默认值", e);
            // 出现异常时使用默认值
            deadlines.setSubmissionDeadline("2026-03-20");
            deadlines.setReviewDeadline("2026-04-10");
            deadlines.setDefenseDate("2026-05-15");
            deadlines.setGraduationDate("2026-06-30");
        }
        
        return deadlines;
    }
    
    /**
     * 获取能力评估雷达图数据
     */
    public AbilityRadarDTO getAbilityRadar(Long studentId) {
        AbilityRadarDTO radar = new AbilityRadarDTO();
        
        // 1. 论文数量得分 (基于提交数量)
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getIsDeleted, 0);
        Long totalCount = paperInfoMapper.selectCount(wrapper);
        radar.setPaperCount(Math.min(totalCount.intValue(), 10));
        
        // 2. 通过率
        wrapper.clear();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getPaperStatus, "completed")
               .eq(PaperInfo::getIsDeleted, 0);
        Long approvedCount = paperInfoMapper.selectCount(wrapper);
        Integer passRate = totalCount > 0 ? 
            approvedCount.intValue() * 100 / totalCount.intValue() : 0;
        radar.setPassRate(passRate);
        
        // 3. 平均相似度
        List<PaperInfo> papers = paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0)
        );
        
        if (!papers.isEmpty()) {
            double avgSimilarity = papers.stream()
                .mapToDouble(paper -> {
                    CheckTask task = checkTaskMapper.selectLatestByPaperId(paper.getId());
                    return task != null && task.getCheckRate() != null ? 
                        task.getCheckRate().doubleValue() : 0.0;
                })
                .average()
                .orElse(0.0);
            radar.setAverageSimilarity(BigDecimal.valueOf(avgSimilarity));
        } else {
            radar.setAverageSimilarity(BigDecimal.ZERO);
        }
        
        // 4. 修改次数 (简化计算，实际应统计每个论文的修改版本数)
        wrapper.clear();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getPaperStatus, "rejected")
               .eq(PaperInfo::getIsDeleted, 0);
        Long revisionCount = paperInfoMapper.selectCount(wrapper);
        radar.setRevisionTimes(Math.min(revisionCount.intValue(), 5));
        
        // 5. 按时提交率 (简化计算)
        radar.setOnTimeSubmission(95); // 模拟数据
        
        // 6. 导师评分 (简化计算)
        radar.setAdvisorRating(88); // 模拟数据
        
        return radar;
    }
    
    /**
     * 获取相似度变化趋势
     */
    public SimilarityTrendDTO getSimilarityTrend(Long studentId) {
        SimilarityTrendDTO trend = new SimilarityTrendDTO();
        
        // 获取该学生的所有论文，按提交时间排序
        List<PaperInfo> papers = paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0)
                .orderByAsc(PaperInfo::getSubmitTime)
        );
        
        List<String> versions = new ArrayList<>();
        List<BigDecimal> similarities = new ArrayList<>();
        
        // 为每篇论文生成版本数据
        for (int i = 0; i < Math.min(papers.size(), 5); i++) { // 最多显示5个版本
            PaperInfo paper = papers.get(i);
            versions.add("V" + (i + 1));
            
            CheckTask task = checkTaskMapper.selectLatestByPaperId(paper.getId());
            if (task != null && task.getCheckRate() != null) {
                similarities.add(task.getCheckRate());
            } else {
                similarities.add(BigDecimal.ZERO);
            }
        }
        
        // 如果没有数据，提供默认值
        if (versions.isEmpty()) {
            versions.addAll(Arrays.asList("V1", "V2", "V3"));
            similarities.addAll(Arrays.asList(
                BigDecimal.valueOf(25.8), 
                BigDecimal.valueOf(22.3), 
                BigDecimal.valueOf(18.5)
            ));
        }
        
        trend.setVersions(versions);
        trend.setSimilarities(similarities);
        
        return trend;
    }
    
    /**
     * 获取专业对比数据
     */
    public MajorComparisonDTO getMajorComparison(Long studentId) {
        MajorComparisonDTO comparison = new MajorComparisonDTO();
        
        // 维度名称
        comparison.setDimensions(Arrays.asList("论文质量", "创新性", "规范性", "工作量", "答辩表现"));
        
        // 我的水平得分 (模拟数据)
        comparison.setMyLevel(Arrays.asList(85, 78, 92, 88, 90));
        
        // 专业平均分 (模拟数据)
        comparison.setMajorAverage(Arrays.asList(75, 70, 80, 75, 78));
        
        return comparison;
    }
    
    /**
     * 获取待办事项列表
     */
    public List<TodoItemDTO> getTodoList(Long studentId) {
        List<TodoItemDTO> todoList = new ArrayList<>();
        
        // 1. 检查是否有待审核的论文
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getPaperStatus, "auditing")
               .eq(PaperInfo::getIsDeleted, 0);
        
        if (paperInfoMapper.selectCount(wrapper) > 0) {
            TodoItemDTO todo1 = new TodoItemDTO();
            todo1.setId(1L);
            todo1.setTitle("等待论文审核结果");
            todo1.setDescription("您有一篇论文正在审核中，请耐心等待");
            todo1.setPriority("normal");
            todo1.setCompleted(false);
            todo1.setDueDate(Date.from(LocalDate.now().plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            todoList.add(todo1);
        }
        
        // 2. 检查是否有需要修改的论文
        wrapper.clear();
        wrapper.eq(PaperInfo::getStudentId, studentId)
               .eq(PaperInfo::getPaperStatus, "rejected")
               .eq(PaperInfo::getIsDeleted, 0);
        
        if (paperInfoMapper.selectCount(wrapper) > 0) {
            TodoItemDTO todo2 = new TodoItemDTO();
            todo2.setId(2L);
            todo2.setTitle("修改被退回的论文");
            todo2.setDescription("根据导师反馈修改论文内容");
            todo2.setPriority("high");
            todo2.setCompleted(false);
            todo2.setDueDate(Date.from(LocalDate.now().plusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            todoList.add(todo2);
        }
        
        // 3. 添加其他常规待办事项
        TodoItemDTO todo3 = new TodoItemDTO();
        todo3.setId(3L);
        todo3.setTitle("准备答辩材料");
        todo3.setDescription("整理答辩PPT和相关资料");
        todo3.setPriority("medium");
        todo3.setCompleted(false);
        todo3.setDueDate(Date.from(LocalDate.now().plusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        todoList.add(todo3);
        
        return todoList;
    }
    
    /**
     * 获取通知消息列表
     */
    public List<NotificationDTO> getNotifications(Long studentId, Integer limit) {
        List<NotificationDTO> notifications = new ArrayList<>();
        
        // 模拟通知数据
        NotificationDTO notif1 = new NotificationDTO();
        notif1.setId(1L);
        notif1.setType("success");
        notif1.setTitle("论文提交成功");
        notif1.setContent("您的论文《基于深度学习的图像识别研究》已成功提交");
        notif1.setTime(new Date());
        notif1.setIsRead(false);
        notifications.add(notif1);
        
        NotificationDTO notif2 = new NotificationDTO();
        notif2.setId(2L);
        notif2.setType("info");
        notif2.setTitle("导师已审阅");
        notif2.setContent("张教授已审阅您的论文，请查看反馈意见");
        notif2.setTime(Date.from(LocalDate.now().minusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        notif2.setIsRead(true);
        notifications.add(notif2);
        
        NotificationDTO notif3 = new NotificationDTO();
        notif3.setId(3L);
        notif3.setType("warning");
        notif3.setTitle("查重结果提醒");
        notif3.setContent("您的论文查重率为15.2%，符合要求");
        notif3.setTime(Date.from(LocalDate.now().minusDays(5).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        notif3.setIsRead(true);
        notifications.add(notif3);
        
        // 限制返回数量
        return limit != null && limit < notifications.size() ? 
            notifications.subList(0, limit) : notifications;
    }
    
    /**
     * 获取进度跟踪信息
     */
    public ProgressTrackingDTO getProgressTracking(Long studentId) {
        ProgressTrackingDTO progress = new ProgressTrackingDTO();
        
        // 获取最新论文状态确定当前步骤
        LatestPaperDTO latestPaper = getLatestPaper(studentId);
        if (latestPaper != null) {
            progress.setCurrentStep(getProgressFromStatus(latestPaper.getStatus()));
        } else {
            progress.setCurrentStep(0); // 未开始
        }
        
        // 预计完成时间
        progress.setEstimatedCompletion(LocalDate.now().plusDays(30).toString());
        
        // 处理速度评价
        progress.setProcessingSpeed("正常");
        
        // 构建步骤详情
        List<ProgressTrackingDTO.ProgressStepDTO> steps = new ArrayList<>();
        
        // 步骤1: 开题报告
        ProgressTrackingDTO.ProgressStepDTO step1 = new ProgressTrackingDTO.ProgressStepDTO();
        step1.setStep(1);
        step1.setName("开题报告");
        step1.setStatus("finish");
        step1.setCompletedTime(Date.from(LocalDate.now().minusMonths(3).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        step1.setDescription("开题报告已通过审核");
        steps.add(step1);
        
        // 步骤2: 论文初稿
        ProgressTrackingDTO.ProgressStepDTO step2 = new ProgressTrackingDTO.ProgressStepDTO();
        step2.setStep(2);
        step2.setName("论文初稿");
        step2.setStatus(progress.getCurrentStep() >= 2 ? "finish" : "process");
        if (progress.getCurrentStep() >= 2) {
            step2.setCompletedTime(Date.from(LocalDate.now().minusWeeks(2).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        step2.setDescription("论文初稿已提交");
        steps.add(step2);
        
        // 步骤3: 论文修改
        ProgressTrackingDTO.ProgressStepDTO step3 = new ProgressTrackingDTO.ProgressStepDTO();
        step3.setStep(3);
        step3.setName("论文修改");
        step3.setStatus(progress.getCurrentStep() >= 3 ? "finish" : "wait");
        if (progress.getCurrentStep() >= 3) {
            step3.setCompletedTime(new Date());
        }
        step3.setDescription("根据导师意见修改论文");
        steps.add(step3);
        
        // 步骤4: 最终审核
        ProgressTrackingDTO.ProgressStepDTO step4 = new ProgressTrackingDTO.ProgressStepDTO();
        step4.setStep(4);
        step4.setName("最终审核");
        step4.setStatus(progress.getCurrentStep() >= 4 ? "finish" : "wait");
        step4.setDescription("等待最终审核结果");
        steps.add(step4);
        
        // 步骤5: 答辩准备
        ProgressTrackingDTO.ProgressStepDTO step5 = new ProgressTrackingDTO.ProgressStepDTO();
        step5.setStep(5);
        step5.setName("答辩准备");
        step5.setStatus("wait");
        step5.setDescription("准备答辩材料");
        steps.add(step5);
        
        progress.setSteps(steps);
        
        return progress;
    }
}
package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.mapper.FileInfoMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.ReviewRecord;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.student.dto.*;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.PaperInfoService;
import com.abin.checkrepeatservice.teacher.mapper.ReviewRecordMapper;
import com.abin.checkrepeatservice.user.mapper.SystemMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardService {

    @jakarta.annotation.Resource
    private PaperInfoMapper paperInfoMapper;

    @jakarta.annotation.Resource
    private FileInfoMapper fileInfoMapper;

    @jakarta.annotation.Resource
    private CheckTaskMapper checkTaskMapper;

    @jakarta.annotation.Resource
    private SysUserMapper sysUserMapper;

    @jakarta.annotation.Resource
    private SystemConfigMapper systemConfigMapper;

    @jakarta.annotation.Resource
    private ReviewRecordMapper reviewRecordMapper;

    @jakarta.annotation.Resource
    private SystemMessageMapper systemMessageMapper;

    @jakarta.annotation.Resource
    private PaperInfoService paperInfoService;

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
            new BigDecimal(approvedCount).multiply(new BigDecimal(100)).divide(new BigDecimal(totalCount), 2, RoundingMode.HALF_UP) :
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

        // 5. 计算平均分（paper_info.final_score，教师录入的最终成绩；无评分返回 null 而不是假数据）
        stats.setAvgScore(getAverageFinalScore(studentId));

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
        LatestPaperDTO dto = new LatestPaperDTO();
        FileInfo fileInfo = latestPaper.getFileId() != null ? fileInfoMapper.selectById(latestPaper.getFileId()) : null;
        dto.setId(latestPaper.getId());
        dto.setWordCount(fileInfo != null ? fileInfo.getWordCount() : latestPaper.getWordCount());
        dto.setTitle(latestPaper.getPaperTitle());
        dto.setStatus(latestPaper.getPaperStatus());
        dto.setSubmitTime(latestPaper.getSubmitTime());
        dto.setAllocationStatus(latestPaper.getAllocationStatus());
        // 获取导师信息
        if (latestPaper.getTeacherId() != null) {
            SysUser advisor = sysUserMapper.selectById(latestPaper.getTeacherId());
            if (advisor != null) {
                dto.setAdvisorName(advisor.getRealName());
            }
        }

        // 审核时间与导师反馈来自 review_record 真实审核记录
        ReviewRecord latestReview = selectLatestReview(latestPaper.getId());
        if (latestReview != null) {
            dto.setApproveTime(latestReview.getReviewTime());
            if (latestReview.getReviewOpinion() != null && !latestReview.getReviewOpinion().isBlank()) {
                // 审核意见为富文本，剥掉 HTML 标签后再返回
                dto.setFeedback(Jsoup.parse(latestReview.getReviewOpinion()).text());
            }
        } else {
            // 尚无审核记录时不假造审核时间
            dto.setApproveTime(null);
        }

        // 获取查重率
        CheckTask checkTask = checkTaskMapper.selectLatestByPaperId(latestPaper.getId());
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
        dto.setTitle(teacher.getProfessionalTitle());
        dto.setPhone(teacher.getPhone());
        dto.setEmail(teacher.getEmail());
        dto.setOffice(teacher.getOffice());
        dto.setAvatar(teacher.getAvatar());
        dto.setResearchField(teacher.getResearchDirection());
        if (teacher.getResearchDirection() != null && !teacher.getResearchDirection().isBlank()) {
            dto.setExpertise(Collections.singletonList(teacher.getResearchDirection()));
        }

        // 导师统计（真实统计：该导师名下所有论文）
        LambdaQueryWrapper<PaperInfo> guidedWrapper = new LambdaQueryWrapper<>();
        guidedWrapper.eq(PaperInfo::getTeacherId, teacher.getId())
                     .eq(PaperInfo::getIsDeleted, 0);
        Long guidedCount = paperInfoMapper.selectCount(guidedWrapper);
        dto.setGuidedPapersCount(guidedCount.intValue());

        guidedWrapper.eq(PaperInfo::getPaperStatus, "completed");
        Long guidedApproved = paperInfoMapper.selectCount(guidedWrapper);
        if (guidedCount > 0) {
            dto.setApprovalRate(new BigDecimal(guidedApproved).multiply(new BigDecimal(100))
                    .divide(new BigDecimal(guidedCount), 1, RoundingMode.HALF_UP));
        }

        dto.setAverageScore(getAverageFinalScoreByTeacher(teacher.getId()));

        // 系统暂无在线状态服务，返回 null 由前端展示「未知」而非假造在线
        dto.setOnlineStatus(null);

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
        List<PaperInfo> papers = listStudentPapers(studentId);
        if (papers == null || papers.isEmpty()) {
            return 0;
        }

        return papers.stream()
                .mapToInt(paper -> {
                    FileInfo fileInfo = paper.getFileId() != null ? fileInfoMapper.selectById(paper.getFileId()) : null;
                    return fileInfo != null && fileInfo.getWordCount() != null ? fileInfo.getWordCount() : 0;
                })
                .sum();
    }

    private List<PaperInfo> listStudentPapers(Long studentId) {
        return paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0)
        );
    }

    /**
     * 学生论文最终成绩的平均分，无任何评分记录时返回 null（页面展示「暂无」）
     */
    private BigDecimal getAverageFinalScore(Long studentId) {
        List<PaperInfo> papers = listStudentPapers(studentId);
        List<BigDecimal> scores = papers.stream()
                .map(PaperInfo::getFinalScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (scores.isEmpty()) {
            return null;
        }
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(scores.size()), 1, RoundingMode.HALF_UP);
    }

    /**
     * 导师名下论文最终成绩的平均分
     */
    private BigDecimal getAverageFinalScoreByTeacher(Long teacherId) {
        List<PaperInfo> papers = paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getTeacherId, teacherId)
                .eq(PaperInfo::getIsDeleted, 0)
        );
        List<BigDecimal> scores = papers.stream()
                .map(PaperInfo::getFinalScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (scores.isEmpty()) {
            return null;
        }
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(scores.size()), 1, RoundingMode.HALF_UP);
    }

    private ReviewRecord selectLatestReview(Long paperId) {
        List<ReviewRecord> records = reviewRecordMapper.selectList(
            new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getPaperId, paperId)
                .orderByDesc(ReviewRecord::getReviewTime)
                .last("LIMIT 1")
        );
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * 读取 system_config 中的日期型配置值，未配置返回 null
     */
    private String getConfigValue(String key) {
        SystemConfig config = systemConfigMapper.selectByConfigKey(key);
        return config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()
                ? config.getConfigValue() : null;
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
        Long totalCount = paperInfoMapper.selectCount(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0)
        );
        radar.setPaperCount(Math.min(totalCount.intValue(), 10));

        // 2. 通过率
        Long approvedCount = paperInfoMapper.selectCount(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getPaperStatus, "completed")
                .eq(PaperInfo::getIsDeleted, 0)
        );
        Integer passRate = totalCount > 0 ?
            approvedCount.intValue() * 100 / totalCount.intValue() : 0;
        radar.setPassRate(passRate);

        // 3. 平均相似度
        List<PaperInfo> papers = listStudentPapers(studentId);

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
        Long revisionCount = paperInfoMapper.selectCount(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getPaperStatus, "rejected")
                .eq(PaperInfo::getIsDeleted, 0)
        );
        radar.setRevisionTimes(Math.min(revisionCount.intValue(), 5));

        // 5. 按时提交率：以 system_config 的提交截止时间为基准真实计算，无截止配置或无论文时返回 null
        radar.setOnTimeSubmission(calculateOnTimeSubmissionRate(papers));

        // 6. 导师评分：系统当前没有教师评分数据（review_record 无分数字段），返回 null 由前端展示「暂无」
        radar.setAdvisorRating(null);

        return radar;
    }

    /**
     * 按时提交率 = 在提交截止时间前提交的论文占比
     * 截止时间取 system_config 的 submission_deadline（yyyy-MM-dd）
     */
    private Integer calculateOnTimeSubmissionRate(List<PaperInfo> papers) {
        if (papers == null || papers.isEmpty()) {
            return null;
        }
        String deadlineStr = getConfigValue("submission_deadline");
        if (deadlineStr == null) {
            return null;
        }
        LocalDateTime deadline;
        try {
            deadline = LocalDate.parse(deadlineStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    .atTime(23, 59, 59);
        } catch (Exception e) {
            log.warn("提交截止日期配置格式非法：{}", deadlineStr);
            return null;
        }
        long onTime = papers.stream()
                .filter(p -> p.getSubmitTime() != null && !p.getSubmitTime().isAfter(deadline))
                .count();
        return (int) Math.round(onTime * 100.0 / papers.size());
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

        // 无数据时返回空列表，由前端展示空态（不再返回假的趋势数据）
        trend.setVersions(versions);
        trend.setSimilarities(similarities);

        return trend;
    }

    /**
     * 获取专业对比数据（全部由数据库真实计算）
     * 五个维度：论文数量、通过率、查重质量、字数规模、修改控制，统一 0-100 分。
     * 「专业平均」= 同专业（按 paper_info.major_id）每个学生的维度得分均值。
     */
    public MajorComparisonDTO getMajorComparison(Long studentId) {
        MajorComparisonDTO comparison = new MajorComparisonDTO();

        List<String> dimensions = Arrays.asList("论文数量", "通过率", "查重质量", "字数规模", "修改控制");
        comparison.setDimensions(dimensions);

        // 1. 确定专业：优先用学生本人信息上的专业，兜底用最新论文上的专业
        Long majorId = null;
        SysUser student = sysUserMapper.selectById(studentId);
        if (student != null && student.getMajorId() != null) {
            majorId = student.getMajorId();
        } else {
            PaperInfo latestPaper = paperInfoMapper.selectLatestPaper(studentId);
            if (latestPaper != null) {
                majorId = latestPaper.getMajorId();
            }
        }

        // 2. 我自己的维度得分
        List<PaperInfo> myPapers = listStudentPapers(studentId);
        comparison.setMyLevel(calculateDimensionScores(myPapers));

        // 3. 专业平均：把同专业每个学生的得分算出来再取均值；查不到专业则返回空列表
        if (majorId == null) {
            comparison.setMajorAverage(Collections.emptyList());
            return comparison;
        }

        List<PaperInfo> majorPapers = paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getMajorId, majorId)
                .eq(PaperInfo::getIsDeleted, 0)
        );
        if (majorPapers.isEmpty()) {
            comparison.setMajorAverage(Collections.emptyList());
            return comparison;
        }

        Map<Long, List<PaperInfo>> papersByStudent = majorPapers.stream()
                .filter(p -> p.getStudentId() != null)
                .collect(Collectors.groupingBy(PaperInfo::getStudentId));

        List<List<Integer>> allScores = papersByStudent.values().stream()
                .map(this::calculateDimensionScores)
                .collect(Collectors.toList());

        List<Integer> majorAverage = new ArrayList<>();
        for (int dim = 0; dim < dimensions.size(); dim++) {
            final int idx = dim;
            double avg = allScores.stream()
                    .mapToInt(scores -> scores.get(idx))
                    .average()
                    .orElse(0);
            majorAverage.add((int) Math.round(avg));
        }
        comparison.setMajorAverage(majorAverage);

        return comparison;
    }

    /**
     * 计算单个学生论文集合的五个维度得分（0-100）：
     * 论文数量 = min(篇数,10)*10；通过率 = completed 占比；
     * 查重质量 = 100 - 平均查重率（兼容 0-1 小数与百分比两种存储，下限 0）；
     * 字数规模 = min(平均字数/100, 100)，1 万字满分；修改控制 = max(0, 100 - 被退回篇数*10)
     */
    private List<Integer> calculateDimensionScores(List<PaperInfo> papers) {
        Integer[] scores = new Integer[5];
        if (papers == null || papers.isEmpty()) {
            return Arrays.asList(0, 0, 0, 0, 0);
        }

        // 论文数量
        scores[0] = Math.min(papers.size(), 10) * 10;

        // 通过率
        long completed = papers.stream().filter(p -> "completed".equals(p.getPaperStatus())).count();
        scores[1] = (int) Math.round(completed * 100.0 / papers.size());

        // 查重质量
        List<Double> rates = papers.stream()
                .map(PaperInfo::getSimilarityRate)
                .filter(Objects::nonNull)
                .map(r -> r.doubleValue() > 1 ? r.doubleValue() : r.doubleValue() * 100)
                .collect(Collectors.toList());
        double avgRate = rates.isEmpty() ? 0 : rates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        scores[2] = (int) Math.round(Math.max(0, 100 - avgRate));

        // 字数规模
        List<Integer> wordCounts = papers.stream()
                .map(PaperInfo::getWordCount)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        double avgWords = wordCounts.isEmpty() ? 0 : wordCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
        scores[3] = (int) Math.round(Math.min(100, avgWords / 100.0));

        // 修改控制
        long rejected = papers.stream().filter(p -> "rejected".equals(p.getPaperStatus())).count();
        scores[4] = (int) Math.max(0, 100 - rejected * 10);

        return Arrays.asList(scores);
    }

    /**
     * 获取待办事项列表（全部基于论文真实状态生成，不再返回硬编码待办）。
     *
     * <p>每条待办都带 type/bizType/bizId/actionCode，前端 goTodo 直接按 actionCode
     * 做枚举映射即可定位路由与实体，不再依赖标题正则或 paperId/relatedId/bizId 猜测链。</p>
     */
    public List<TodoItemDTO> getTodoList(Long studentId) {
        List<TodoItemDTO> todoList = new ArrayList<>();
        Date reviewDeadline = parseConfigDate("review_deadline");
        Date submissionDeadline = parseConfigDate("submission_deadline");

        List<PaperInfo> papers = paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0)
                .orderByDesc(PaperInfo::getCreateTime)
        );
        // 复用论文服务已收口的 canResubmit 口径，避免待办与详情页判定不一致
        paperInfoService.enrichResubmitInfo(papers);

        for (PaperInfo paper : papers) {
            String status = paper.getPaperStatus();
            String title = paper.getPaperTitle() == null ? "未命名论文" : paper.getPaperTitle();

            if (DictConstants.PaperStatus.CHECKING.equals(status)) {
                todoList.add(buildTodo(paper.getId(), "CHECK_COMPLETED", "PAPER", paper.getId(),
                        "VIEW_PAPER",
                        "论文《" + title + "》正在查重中",
                        "查重检测进行中，请耐心等待结果",
                        "normal", null));
            } else if (DictConstants.PaperStatus.AUDITING.equals(status)) {
                todoList.add(buildTodo(paper.getId(), "REVIEW_PENDING", "PAPER", paper.getId(),
                        "VIEW_PAPER",
                        "论文《" + title + "》等待审核结果",
                        "论文正在等待导师审核，请留意通知",
                        "normal", reviewDeadline));
            } else if (DictConstants.PaperStatus.REJECTED.equals(status)) {
                boolean canResubmit = Boolean.TRUE.equals(paper.getCanResubmit());
                String description = paper.getAuditComment() != null && !paper.getAuditComment().isBlank()
                        ? "导师意见：" + Jsoup.parse(paper.getAuditComment()).text()
                        : "请根据导师意见修改后重新提交";
                Date dueDate = paper.getResubmitDeadline() == null
                        ? null : localDateTimeToDate(paper.getResubmitDeadline());
                todoList.add(buildTodo(paper.getId(), "REVISION_REQUIRED", "PAPER", paper.getId(),
                        canResubmit ? "REVISE_PAPER" : "VIEW_PAPER",
                        "论文《" + title + "》被退回，请修改后重提",
                        description,
                        "high", dueDate));
            }
        }

        // 还没有任何论文时提醒提交
        if (papers.isEmpty()) {
            todoList.add(buildTodo(0L, "DEADLINE_REMINDER", "PAPER", null,
                    null,
                    "提交毕业论文",
                    "您还未提交任何论文，请在截止日期前完成提交",
                    "high", submissionDeadline));
        }

        return todoList;
    }

    /** 组装带路由要素的待办项 */
    private TodoItemDTO buildTodo(Long id, String type, String bizType, Long bizId, String actionCode,
                                  String title, String description, String priority, Date dueDate) {
        TodoItemDTO todo = new TodoItemDTO();
        todo.setId(id);
        todo.setType(type);
        todo.setBizType(bizType);
        todo.setBizId(bizId);
        todo.setActionCode(actionCode);
        todo.setTitle(title);
        todo.setDescription(description);
        todo.setPriority(priority);
        todo.setCompleted(false);
        todo.setDueDate(dueDate);
        return todo;
    }

    /**
     * 获取通知消息列表（真实数据：system_message 表中发给该学生的站内信）
     */
    public List<NotificationDTO> getNotifications(Long studentId, Integer limit) {
        int queryLimit = (limit != null && limit > 0) ? limit : 5;

        List<SystemMessage> messages = systemMessageMapper.selectList(
            new LambdaQueryWrapper<SystemMessage>()
                .eq(SystemMessage::getReceiverId, studentId)
                .and(w -> w.isNull(SystemMessage::getExpireTime)
                        .or().gt(SystemMessage::getExpireTime, LocalDateTime.now()))
                .orderByDesc(SystemMessage::getCreateTime)
                .last("LIMIT " + queryLimit)
        );

        List<NotificationDTO> notifications = new ArrayList<>();
        for (SystemMessage message : messages) {
            NotificationDTO dto = new NotificationDTO();
            dto.setId(message.getId());
            dto.setTitle(message.getTitle());
            dto.setContent(message.getContent());
            // 类型映射：紧急(3)/重要(2)优先级 → warning，其余 → info
            dto.setType(message.getPriority() != null && message.getPriority() >= 2 ? "warning" : "info");
            if (message.getCreateTime() != null) {
                dto.setTime(Date.from(message.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()));
            }
            dto.setIsRead(message.getIsRead() != null && message.getIsRead() == 1);
            if ("paper".equals(message.getRelatedType()) && message.getRelatedId() != null) {
                dto.setRelatedPaperId(String.valueOf(message.getRelatedId()));
            }
            notifications.add(dto);
        }
        return notifications;
    }

    /**
     * 获取进度跟踪信息（步骤与时间全部来自论文/查重任务/审核记录的真实时间线）
     */
    public ProgressTrackingDTO getProgressTracking(Long studentId) {
        ProgressTrackingDTO progress = new ProgressTrackingDTO();

        PaperInfo latestPaper = paperInfoMapper.selectLatestPaper(studentId);
        if (latestPaper == null) {
            progress.setCurrentStep(0); // 未开始
            progress.setSteps(Collections.emptyList());
            return progress;
        }

        int currentStep = getProgressFromStatus(latestPaper.getPaperStatus());
        progress.setCurrentStep(currentStep);

        // 预计完成时间：已完成 → 「已完成」；否则取审核截止配置；未配置返回 null（前端展示「暂无预估」）
        if ("completed".equals(latestPaper.getPaperStatus())) {
            progress.setEstimatedCompletion("已完成");
        } else {
            progress.setEstimatedCompletion(getConfigValue("review_deadline"));
        }

        // 处理速度：以最近一次完成的查重任务耗时评价，无记录返回 null
        progress.setProcessingSpeed(calculateProcessingSpeed(latestPaper.getId()));

        // 步骤详情：论文提交 → 查重检测 → 导师审核 → 完成
        List<ProgressTrackingDTO.ProgressStepDTO> steps = new ArrayList<>();

        // 步骤1: 论文提交
        ProgressTrackingDTO.ProgressStepDTO step1 = new ProgressTrackingDTO.ProgressStepDTO();
        step1.setStep(1);
        step1.setName("论文提交");
        step1.setStatus("finish");
        step1.setCompletedTime(localDateTimeToDate(latestPaper.getSubmitTime()));
        step1.setDescription("论文已成功提交");
        steps.add(step1);

        // 步骤2: 查重检测
        ProgressTrackingDTO.ProgressStepDTO step2 = new ProgressTrackingDTO.ProgressStepDTO();
        step2.setStep(2);
        step2.setName("查重检测");
        if (latestPaper.getCheckTime() != null) {
            step2.setStatus("finish");
            step2.setCompletedTime(localDateTimeToDate(latestPaper.getCheckTime()));
            step2.setDescription("查重检测已完成");
        } else if ("checking".equals(latestPaper.getPaperStatus())) {
            step2.setStatus("process");
            step2.setDescription("查重检测进行中");
        } else {
            step2.setStatus("wait");
            step2.setDescription("等待查重检测");
        }
        steps.add(step2);

        // 步骤3: 导师审核
        ReviewRecord latestReview = selectLatestReview(latestPaper.getId());
        ProgressTrackingDTO.ProgressStepDTO step3 = new ProgressTrackingDTO.ProgressStepDTO();
        step3.setStep(3);
        step3.setName("导师审核");
        if (latestReview != null) {
            step3.setStatus("finish");
            step3.setCompletedTime(localDateTimeToDate(latestReview.getReviewTime()));
            step3.setDescription(Integer.valueOf(3).equals(latestReview.getReviewStatus()) ? "审核已通过" : "审核未通过，需修改");
        } else if ("auditing".equals(latestPaper.getPaperStatus())) {
            step3.setStatus("process");
            step3.setDescription("导师审核进行中");
        } else {
            step3.setStatus("wait");
            step3.setDescription("等待导师审核");
        }
        steps.add(step3);

        // 步骤4: 完成
        ProgressTrackingDTO.ProgressStepDTO step4 = new ProgressTrackingDTO.ProgressStepDTO();
        step4.setStep(4);
        step4.setName("完成");
        if ("completed".equals(latestPaper.getPaperStatus())) {
            step4.setStatus("finish");
            step4.setDescription("论文流程已全部完成");
        } else {
            step4.setStatus("wait");
            step4.setDescription("等待审核通过后完成");
        }
        steps.add(step4);

        progress.setSteps(steps);

        return progress;
    }

    /**
     * 根据最近一次完成的查重任务耗时评价处理速度：快速(<60s)/正常(<300s)/较慢
     */
    private String calculateProcessingSpeed(Long paperId) {
        CheckTask task = checkTaskMapper.selectLatestByPaperId(paperId);
        if (task == null || task.getStartTime() == null || task.getEndTime() == null) {
            return null;
        }
        long seconds = java.time.Duration.between(task.getStartTime(), task.getEndTime()).getSeconds();
        if (seconds < 60) {
            return "快速";
        }
        return seconds < 300 ? "正常" : "较慢";
    }

    private Date localDateTimeToDate(LocalDateTime localDateTime) {
        return localDateTime == null ? null
                : Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 解析 system_config 中的 yyyy-MM-dd 日期为 Date，非法或缺省返回 null
     */
    private Date parseConfigDate(String key) {
        String value = getConfigValue(key);
        if (value == null) {
            return null;
        }
        try {
            return Date.from(LocalDate.parse(value.trim(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            log.warn("system_config 中 {} 的值 {} 不是合法日期", key, value);
            return null;
        }
    }

    /**
     * 导出工作台数据为 Excel：Sheet1 统计概览 + Sheet2 论文明细（EasyExcel 动态表头）
     */
    public void exportDashboardData(Long studentId, OutputStream outputStream) {
        StudentDashboardStatsDTO stats = getDashboardStats(studentId);
        List<PaperInfo> papers = paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0)
                .orderByDesc(PaperInfo::getSubmitTime)
        );
        Map<Long, FileInfo> fileInfoMap = papers.stream()
                .map(PaperInfo::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(id -> id, fileInfoMapper::selectById, (a, b) -> a));
        Map<Long, CheckTask> latestTaskMap = papers.stream()
                .collect(Collectors.toMap(PaperInfo::getId, p -> {
                    CheckTask t = checkTaskMapper.selectLatestByPaperId(p.getId());
                    return t != null ? t : new CheckTask();
                }, (a, b) -> a));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Sheet1 统计概览
        List<List<String>> head1 = Arrays.asList(
                Collections.singletonList("指标"), Collections.singletonList("数值"));
        List<List<Object>> data1 = new ArrayList<>();
        addRow(data1, "总提交数", stats.getSubmittedCount());
        addRow(data1, "待审核数", stats.getPendingCount());
        addRow(data1, "已通过数", stats.getApprovedCount());
        addRow(data1, "需修改数", stats.getRevisionCount());
        addRow(data1, "未通过数", stats.getFailedCount());
        addRow(data1, "完成度(%)", stats.getCompletionRate());
        addRow(data1, "本周提交数", stats.getThisWeekSubmitted());
        addRow(data1, "本周通过数", stats.getThisWeekApproved());
        addRow(data1, "总字数", stats.getTotalWordCount());
        addRow(data1, "平均分", stats.getAvgScore() != null ? stats.getAvgScore() : "暂无");

        // Sheet2 论文明细
        List<List<String>> head2 = Arrays.asList(
                Collections.singletonList("论文标题"),
                Collections.singletonList("论文类型"),
                Collections.singletonList("状态"),
                Collections.singletonList("提交时间"),
                Collections.singletonList("字数"),
                Collections.singletonList("查重率(%)"),
                Collections.singletonList("最终成绩"));
        List<List<Object>> data2 = new ArrayList<>();
        for (PaperInfo paper : papers) {
            FileInfo fileInfo = paper.getFileId() != null ? fileInfoMap.get(paper.getFileId()) : null;
            CheckTask task = latestTaskMap.get(paper.getId());
            data2.add(Arrays.asList(
                    paper.getPaperTitle(),
                    paper.getPaperType(),
                    paper.getPaperStatus(),
                    paper.getSubmitTime() != null ? paper.getSubmitTime().format(fmt) : "",
                    fileInfo != null && fileInfo.getWordCount() != null ? fileInfo.getWordCount() : 0,
                    task != null && task.getCheckRate() != null ? task.getCheckRate() : "",
                    paper.getFinalScore() != null ? paper.getFinalScore() : "暂无"
            ));
        }

        com.alibaba.excel.ExcelWriter writer = com.alibaba.excel.EasyExcel.write(outputStream).build();
        try {
            com.alibaba.excel.write.metadata.WriteSheet sheet1 = com.alibaba.excel.EasyExcel
                    .writerSheet(0, "统计概览").head(head1).build();
            writer.write(data1, sheet1);
            com.alibaba.excel.write.metadata.WriteSheet sheet2 = com.alibaba.excel.EasyExcel
                    .writerSheet(1, "论文明细").head(head2).build();
            writer.write(data2, sheet2);
        } finally {
            writer.finish();
        }
    }

    private void addRow(List<List<Object>> data, String key, Object value) {
        List<Object> row = new ArrayList<>();
        row.add(key);
        row.add(value != null ? value : "");
        data.add(row);
    }
}

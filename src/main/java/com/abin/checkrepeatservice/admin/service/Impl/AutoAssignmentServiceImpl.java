package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.service.AutoAssignmentService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.admin.dto.*;
import com.abin.checkrepeatservice.common.datascope.DataScopeResolver;
import com.abin.checkrepeatservice.common.datascope.DataScopeSqlFilter;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.TeacherAllocationRecord;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.student.mapper.MajorMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.user.mapper.TeacherAllocationRecordMapper;
import com.abin.checkrepeatservice.admin.mapper.AutoAssignmentHistoryMapper;
import com.abin.checkrepeatservice.admin.mapper.AutoAssignmentConfigMapper;
import com.abin.checkrepeatservice.pojo.entity.AutoAssignmentHistory;
import com.abin.checkrepeatservice.pojo.entity.AutoAssignmentConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 自动论文分配服务实现类
 */
@Service
@Slf4j
public class AutoAssignmentServiceImpl implements AutoAssignmentService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private AutoAssignmentHistoryMapper autoAssignmentHistoryMapper;

    @Resource
    private AutoAssignmentConfigMapper autoAssignmentConfigMapper;

    @Resource
    private MajorMapper majorMapper;

    @Resource
    private TeacherAllocationRecordMapper teacherAllocationRecordMapper;

    @Resource
    private NotificationService notificationService;

    // 治理项：数据范围统一裁决（待分配论文与候选教师按角色范围收窄）
    @Resource
    private DataScopeResolver dataScopeResolver;

    // M2.2 阶段门收口：论文状态流转统一校验（默认影子模式，只告警不阻断）
    @Resource
    private com.abin.checkrepeatservice.common.component.PaperStageGate paperStageGate;

    /**
     * 已请求取消的任务ID。异步线程在每个学生处理前检查，取消后不再继续写入分配。
     * 单实例部署下的轻量实现；多实例部署需改为共享状态（DB/Redis）。
     */
    private final Set<String> cancelledTasks = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Result<AutoAssignmentConfigDTO> getAlgorithmConfig() {
        try {
            return Result.success(loadAlgorithmConfig());
        } catch (Exception e) {
            log.error("获取算法配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取算法配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 读取当前生效的算法配置（数据库无配置时回落 DTO 默认值）。
     * 供配置查询、预览、执行与结果应用共用，避免多处口径漂移。
     */
    private AutoAssignmentConfigDTO loadAlgorithmConfig() {
        AutoAssignmentConfigDTO config = new AutoAssignmentConfigDTO();

        LambdaQueryWrapper<AutoAssignmentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AutoAssignmentConfig::getIsDeleted, 0)
               .eq(AutoAssignmentConfig::getIsEnabled, 1);

        List<AutoAssignmentConfig> configList = autoAssignmentConfigMapper.selectList(wrapper);

        Map<String, String> configMap = configList.stream()
            .collect(Collectors.toMap(
                AutoAssignmentConfig::getConfigKey,
                AutoAssignmentConfig::getConfigValue,
                (existing, replacement) -> existing
            ));

        config.setStrategy(configMap.getOrDefault("strategy", "comprehensive"));
        config.setMaxLoad(parseIntConfig(configMap.get("maxLoad"), 12));
        config.setMajorWeight(parseIntConfig(configMap.get("majorWeight"), 40));
        config.setInterestWeight(parseIntConfig(configMap.get("interestWeight"), 25));
        config.setLoadWeight(parseIntConfig(configMap.get("loadWeight"), 20));
        config.setExperienceWeight(parseIntConfig(configMap.get("experienceWeight"), 15));
        config.setExcludeFullTeachers(parseBooleanConfig(configMap.get("excludeFullTeachers"), true));
        config.setAllowCrossMajor(parseBooleanConfig(configMap.get("allowCrossMajor"), false));
        config.setMinLoadPerTeacher(parseIntConfig(configMap.get("minLoadPerTeacher"), 3));
        config.setCrossMajorLimit(parseIntConfig(configMap.get("crossMajorLimit"), 2));
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> saveAlgorithmConfig(AutoAssignmentConfigDTO config) {
        try {
            // 验证配置参数
            validateConfig(config);
            
            // 获取当前操作人
            Long operatorId = getCurrentOperatorId();
            SysUser operator = sysUserMapper.selectById(operatorId);
            String operatorName = operator != null ? operator.getRealName() : "系统管理员";
            
            // 删除旧配置
            LambdaQueryWrapper<AutoAssignmentConfig> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(AutoAssignmentConfig::getIsDeleted, 0);
            autoAssignmentConfigMapper.delete(deleteWrapper);
            
            // 保存新配置
            saveConfigItem("strategy", config.getStrategy(), "分配策略", "algorithm", operatorId, operatorName);
            saveConfigItem("maxLoad", String.valueOf(config.getMaxLoad()), "教师最大指导学生数", "limit", operatorId, operatorName);
            saveConfigItem("majorWeight", String.valueOf(config.getMajorWeight()), "专业匹配权重", "weight", operatorId, operatorName);
            saveConfigItem("interestWeight", String.valueOf(config.getInterestWeight()), "研究兴趣匹配权重", "weight", operatorId, operatorName);
            saveConfigItem("loadWeight", String.valueOf(config.getLoadWeight()), "负载均衡权重", "weight", operatorId, operatorName);
            saveConfigItem("experienceWeight", String.valueOf(config.getExperienceWeight()), "经验权重", "weight", operatorId, operatorName);
            saveConfigItem("excludeFullTeachers", String.valueOf(config.getExcludeFullTeachers()), "排除满负荷教师", "algorithm", operatorId, operatorName);
            saveConfigItem("allowCrossMajor", String.valueOf(config.getAllowCrossMajor()), "允许跨专业分配", "algorithm", operatorId, operatorName);
            saveConfigItem("minLoadPerTeacher", String.valueOf(config.getMinLoadPerTeacher()), "最小指导学生数", "limit", operatorId, operatorName);
            saveConfigItem("crossMajorLimit", String.valueOf(config.getCrossMajorLimit()), "跨专业分配限制", "limit", operatorId, operatorName);
            
            log.info("保存算法配置成功: {}", config);
            return Result.success("保存成功");
        } catch (Exception e) {
            log.error("保存算法配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "保存算法配置失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<AutoAssignmentPreviewDTO> getAssignmentPreview() {
        try {
            AutoAssignmentConfigDTO config = loadAlgorithmConfig();
            DataScopeSqlFilter scopeFilter = dataScopeResolver.currentFilter();
            List<PaperInfo> unassignedPapers = getUnassignedStudents(scopeFilter);
            List<SysUser> availableTeachers = getAvailableTeachers(config, scopeFilter);

            AutoAssignmentPreviewDTO preview = new AutoAssignmentPreviewDTO();
            preview.setUnassigned(unassignedPapers.size());

            // 可用教师=仍有剩余容量的教师（容量口径统一为算法配置的 maxLoad，不再用审核数/硬编码12）
            long teachersWithSlots = availableTeachers.stream()
                    .filter(t -> remainingSlots(t, config) > 0)
                    .count();
            preview.setAvailableTeachers((int) teachersWithSlots);

            int expectedAssigned = calculateExpectedAssigned(unassignedPapers, availableTeachers, config);
            preview.setExpectedAssigned(expectedAssigned);
            preview.setPotentialConflicts(Math.max(0, unassignedPapers.size() - expectedAssigned));

            preview.setMajorStats(buildMajorStats(unassignedPapers, availableTeachers, config));
            preview.setTeacherLoadStats(buildTeacherLoadStats(availableTeachers, config));

            log.info("获取分配预览数据成功: unassigned={}, availableTeachers={}, expectedAssigned={}",
                    preview.getUnassigned(), preview.getAvailableTeachers(), expectedAssigned);
            return Result.success(preview);
        } catch (Exception e) {
            log.error("获取分配预览数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取分配预览数据失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Map<String, Object>> startAutoAssignment(AutoAssignmentStartDTO request) {
        try {
            if (request == null || request.getConfig() == null) {
                return Result.error(ResultCode.PARAM_ERROR, "分配配置不能为空");
            }
            String taskId = UUID.randomUUID().toString();
            AutoAssignmentConfigDTO config = request.getConfig();
            
            // 验证配置
            validateConfig(config);

            // 数据范围必须在请求线程内解析：异步线程没有登录上下文，
            // 进入线程后再取当前用户会直接抛「用户未登录」。
            DataScopeSqlFilter scopeFilter = dataScopeResolver.currentFilter();
            
            // 初始化进度记录到数据库（配置快照落库，便于事后追溯本次执行口径）
            AutoAssignmentHistory history = new AutoAssignmentHistory();
            history.setTaskId(taskId);
            history.setStartTime(LocalDateTime.now());
            history.setStrategy(config.getStrategy());
            history.setStatus("running");
            history.setOperatorId(getCurrentOperatorId());
            history.setConfigDetail(toJson(config));
            history.setResultDetail("[]");
            history.setUpdateTime(LocalDateTime.now());
            history.setIsDeleted(0);
            autoAssignmentHistoryMapper.insert(history);
            
            // 预估耗时在启动线程前算好：估算异常时不应留下一个已开始执行的任务
            long estimatedTime = calculateEstimatedTime(config, scopeFilter);

            // 异步执行分配算法（方法本身不加事务：插入历史必须落到库，线程才能读到任务）
            executeAssignmentAsync(taskId, config, scopeFilter);
            
            // 返回任务信息
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("estimatedTime", estimatedTime);
            
            log.info("启动自动分配任务成功: taskId={}", taskId);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            log.warn("启动自动分配参数不合法: {}", e.getMessage());
            return Result.error(ResultCode.PARAM_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("启动自动分配失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "启动自动分配失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<AutoAssignmentProgressDTO> getAssignmentProgress(String taskId) {
        try {
            LambdaQueryWrapper<AutoAssignmentHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AutoAssignmentHistory::getTaskId, taskId)
                   .eq(AutoAssignmentHistory::getIsDeleted, 0);
            
            AutoAssignmentHistory history = autoAssignmentHistoryMapper.selectOne(wrapper);
            if (history == null) {
                return Result.error(ResultCode.PARAM_ERROR, "任务不存在");
            }
            
            // 转换为进度DTO
            AutoAssignmentProgressDTO progress = new AutoAssignmentProgressDTO();
            progress.setTaskId(history.getTaskId());
            progress.setStatus(history.getStatus());
            progress.setProcessedCount(history.getAssignedCount() != null ? history.getAssignedCount() : 0);
            progress.setTotalCount(history.getTotalStudents() != null ? history.getTotalStudents() : 0);
            // 执行过程中落库的分配明细（含跳过原因），进度页可直接展示
            progress.setDetails(parseDetails(history.getResultDetail()));
            
            // 根据状态设置当前步骤
            switch (history.getStatus()) {
                case "running":
                    progress.setCurrentStep("执行智能分配");
                    break;
                case "completed":
                    progress.setCurrentStep("分配完成");
                    break;
                case "failed":
                    progress.setCurrentStep("执行失败");
                    break;
                case "cancelled":
                    progress.setCurrentStep("任务已取消");
                    break;
                default:
                    progress.setCurrentStep("未知状态");
            }
            
            return Result.success(progress);
        } catch (Exception e) {
            log.error("查询分配进度失败: taskId={}, error={}", taskId, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询分配进度失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancelAssignmentTask(String taskId) {
        try {
            LambdaQueryWrapper<AutoAssignmentHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AutoAssignmentHistory::getTaskId, taskId)
                   .eq(AutoAssignmentHistory::getIsDeleted, 0);
            
            AutoAssignmentHistory history = autoAssignmentHistoryMapper.selectOne(wrapper);
            if (history == null) {
                return Result.error(ResultCode.PARAM_ERROR, "任务不存在");
            }
            if (!"running".equals(history.getStatus())) {
                return Result.error(ResultCode.BUSINESS_ILLEGAL, "仅运行中的任务可以取消");
            }

            // 先登记取消标记：异步线程在下一个学生处理前退出，避免取消后仍继续写入分配
            cancelledTasks.add(taskId);
            history.setStatus("cancelled");
            history.setEndTime(LocalDateTime.now());
            history.setUpdateTime(LocalDateTime.now());
            autoAssignmentHistoryMapper.updateById(history);
            
            log.info("取消分配任务成功: taskId={}", taskId);
            return Result.success("任务取消成功");
        } catch (Exception e) {
            log.error("取消分配任务失败: taskId={}, error={}", taskId, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "取消分配任务失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Page<AutoAssignmentHistoryDTO>> getAssignmentHistory(Integer page, Integer size) {
        try {
            Page<AutoAssignmentHistory> pageQuery = new Page<>(page, size);
            LambdaQueryWrapper<AutoAssignmentHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AutoAssignmentHistory::getIsDeleted, 0)
                   .orderByDesc(AutoAssignmentHistory::getCreateTime);
            
            Page<AutoAssignmentHistory> resultPage = autoAssignmentHistoryMapper.selectPage(pageQuery, wrapper);
            
            // 转换为DTO
            List<AutoAssignmentHistoryDTO> dtoList = resultPage.getRecords().stream()
                .map(this::convertToHistoryDTO)
                .collect(Collectors.toList());
            
            Page<AutoAssignmentHistoryDTO> resultDtoPage = new Page<>(page, size, resultPage.getTotal());
            resultDtoPage.setRecords(dtoList);
            
            return Result.success(resultDtoPage);
        } catch (Exception e) {
            log.error("获取执行历史列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取执行历史列表失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Map<String, Object>> getAssignmentDetail(String id) {
        try {
            AutoAssignmentHistory history = findHistory(id);
            if (history == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "任务不存在");
            }

            Map<String, Object> detail = new HashMap<>();
            // 雪花ID按字符串下发，避免前端 Number 精度丢失
            detail.put("id", history.getId() == null ? null : history.getId().toString());
            detail.put("taskId", history.getTaskId());
            detail.put("status", history.getStatus());
            detail.put("strategy", history.getStrategy());
            detail.put("startTime", history.getStartTime());
            detail.put("endTime", history.getEndTime());
            detail.put("duration", history.getDuration());
            detail.put("totalStudents", history.getTotalStudents());
            detail.put("assignedCount", history.getAssignedCount());
            detail.put("successRate", history.getSuccessRate());
            detail.put("operatorId", history.getOperatorId() == null ? null : history.getOperatorId().toString());
            detail.put("operatorName", history.getOperatorName());
            detail.put("remark", history.getRemark());
            detail.put("results", parseDetails(history.getResultDetail()));
            detail.put("config", parseConfig(history.getConfigDetail()));
            return Result.success(detail);
        } catch (Exception e) {
            log.error("获取执行详情失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取执行详情失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> applyAssignmentResult(String id) {
        try {
            AutoAssignmentHistory history = findHistory(id);
            if (history == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "任务不存在");
            }
            List<AutoAssignmentProgressDTO.AssignmentDetail> details = parseDetails(history.getResultDetail());
            if (details.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "该任务没有可应用的分配结果");
            }

            AutoAssignmentConfigDTO config = loadAlgorithmConfig();
            int applied = 0;
            int alreadyApplied = 0;
            int skipped = 0;
            List<String> skipReasons = new ArrayList<>();

            for (AutoAssignmentProgressDTO.AssignmentDetail detail : details) {
                Long paperId = parseLongId(detail.getPaperId());
                Long teacherId = parseLongId(detail.getTeacherId());
                if (paperId == null || teacherId == null) {
                    // 只回放成功分配明细；执行时已跳过（无教师）的行没有可应用内容
                    continue;
                }

                PaperInfo paper = paperInfoMapper.selectById(paperId);
                if (paper == null) {
                    skipped++;
                    skipReasons.add("论文" + paperId + "：不存在或已删除");
                    continue;
                }
                SysUser teacher = sysUserMapper.selectById(teacherId);
                if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())) {
                    skipped++;
                    skipReasons.add("论文" + paperId + "：教师不存在或已禁用");
                    continue;
                }
                // 幂等：已分配给同一教师视为已生效；已分配他人则视为冲突跳过
                if (Objects.equals(paper.getTeacherId(), teacherId)) {
                    alreadyApplied++;
                    continue;
                }
                if (paper.getTeacherId() != null) {
                    skipped++;
                    skipReasons.add("论文" + paperId + "：已分配其他导师");
                    continue;
                }
                if (!DictConstants.PaperStatus.PENDING.equals(paper.getPaperStatus())) {
                    skipped++;
                    skipReasons.add("论文" + paperId + "：当前状态不允许分配（" + paper.getPaperStatus() + "）");
                    continue;
                }
                if (!dataScopeResolver.canAccessPaper(paper)) {
                    skipped++;
                    skipReasons.add("论文" + paperId + "：不在当前数据范围内");
                    continue;
                }
                if (remainingSlots(teacher, config) <= 0) {
                    skipped++;
                    skipReasons.add("论文" + paperId + "：教师容量已满");
                    continue;
                }
                try {
                    assignPaperToTeacher(paper, teacher, history.getOperatorId());
                    applied++;
                } catch (Exception ex) {
                    skipped++;
                    skipReasons.add("论文" + paperId + "：分配失败（" + ex.getMessage() + "）");
                }
            }

            String message = String.format("应用完成：新增分配 %d，已生效 %d，跳过 %d", applied, alreadyApplied, skipped);
            log.info("应用分配结果完成: id={}, applied={}, alreadyApplied={}, skipped={}",
                    id, applied, alreadyApplied, skipped);
            if (!skipReasons.isEmpty()) {
                log.warn("应用分配结果跳过明细: {}", skipReasons);
            }
            return Result.success(message);
        } catch (Exception e) {
            log.error("应用分配结果失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "应用分配结果失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<String> refreshBaseData() {
        try {
            // 清理过期的历史记录（保留最近30天）
            LambdaQueryWrapper<AutoAssignmentHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.lt(AutoAssignmentHistory::getCreateTime, LocalDateTime.now().minusDays(30))
                   .eq(AutoAssignmentHistory::getIsDeleted, 0);
            autoAssignmentHistoryMapper.delete(wrapper);
            
            log.info("刷新基础数据成功");
            return Result.success("刷新成功");
        } catch (Exception e) {
            log.error("刷新基础数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "刷新基础数据失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 验证配置参数
     */
    private void validateConfig(AutoAssignmentConfigDTO config) {
        if (config == null) {
            throw new IllegalArgumentException("分配配置不能为空");
        }
        if (config.getMaxLoad() == null || config.getMaxLoad() <= 0) {
            throw new IllegalArgumentException("最大指导学生数必须大于0");
        }

        int totalWeight = safeInt(config.getMajorWeight()) + safeInt(config.getInterestWeight())
                + safeInt(config.getLoadWeight()) + safeInt(config.getExperienceWeight());
        if (totalWeight != 100) {
            throw new IllegalArgumentException("权重总和必须等于100");
        }

        if (safeInt(config.getMinLoadPerTeacher()) > config.getMaxLoad()) {
            throw new IllegalArgumentException("最小指导学生数不能大于最大指导学生数");
        }
        if (safeInt(config.getCrossMajorLimit()) < 0) {
            throw new IllegalArgumentException("跨专业分配限制不能为负数");
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 异步执行分配算法
     */
    private void executeAssignmentAsync(String taskId, AutoAssignmentConfigDTO config,
                                        DataScopeSqlFilter scopeFilter) {
        new Thread(() -> {
            AutoAssignmentHistory history = findHistoryByTaskId(taskId);
            if (history == null) {
                return;
            }
            int successCount = 0;
            boolean cancelled = false;
            List<AutoAssignmentProgressDTO.AssignmentDetail> details = new ArrayList<>();
            try {
                // 待分配学生与候选教师均按发起人解析好的数据范围收窄（异步线程无登录上下文）
                List<PaperInfo> students = getUnassignedStudents(scopeFilter);
                List<SysUser> teachers = getAvailableTeachers(config, scopeFilter);
                Map<Long, String> studentNames = loadStudentNames(students);
                // 本次运行内各教师的跨专业已分配数（防止绕过 crossMajorLimit）
                Map<Long, Integer> crossMajorAssigned = new HashMap<>();

                history.setTotalStudents(students.size());
                history.setAssignedCount(0);
                history.setUpdateTime(LocalDateTime.now());
                autoAssignmentHistoryMapper.updateById(history);

                for (PaperInfo student : students) {
                    // 取消检查放在写入前：取消后不再产生新的分配
                    if (cancelledTasks.contains(taskId)) {
                        cancelled = true;
                        break;
                    }

                    AutoAssignmentProgressDTO.AssignmentDetail detail = newAssignmentDetail(student, studentNames);
                    SysUser bestTeacher = findBestTeacher(student, teachers, config, crossMajorAssigned);

                    if (bestTeacher == null) {
                        detail.setConflict(true);
                        detail.setReason("无可用教师（专业/容量/跨专业额度限制）");
                        details.add(detail);
                        continue;
                    }

                    try {
                        assignPaperToTeacher(student, bestTeacher, history.getOperatorId());
                        boolean crossMajor = !Objects.equals(student.getMajorId(), bestTeacher.getMajorId());
                        if (crossMajor) {
                            crossMajorAssigned.merge(bestTeacher.getId(), 1, Integer::sum);
                        }
                        // 同步内存中的教师负载，保证本轮后续学生按最新容量计算
                        bestTeacher.setCurrentAdvisorCount(currentLoad(bestTeacher) + 1);

                        successCount++;
                        detail.setTeacherId(bestTeacher.getId().toString());
                        detail.setTeacherName(bestTeacher.getRealName());
                        detail.setMatchScore(calculateMatchScore(student, bestTeacher, config));
                        detail.setConflict(false);
                    } catch (Exception ex) {
                        detail.setConflict(true);
                        detail.setReason("分配失败：" + ex.getMessage());
                    }
                    details.add(detail);

                    history.setAssignedCount(successCount);
                    history.setResultDetail(toJson(details));
                    history.setUpdateTime(LocalDateTime.now());
                    autoAssignmentHistoryMapper.updateById(history);
                }

                history.setStatus(cancelled ? "cancelled" : "completed");
                history.setEndTime(LocalDateTime.now());
                history.setAssignedCount(successCount);
                history.setSuccessRate(students.isEmpty() ? 0.0 : successCount * 100.0 / students.size());
                history.setDuration(java.time.Duration.between(
                        history.getStartTime(), LocalDateTime.now()).toMillis());
                history.setResultDetail(toJson(details));
                history.setUpdateTime(LocalDateTime.now());
                autoAssignmentHistoryMapper.updateById(history);

                log.info("自动分配任务结束: taskId={}, status={}, successCount={}",
                        taskId, history.getStatus(), successCount);

            } catch (Exception e) {
                // 失败也要保留已产生的分配明细，便于「应用结果」补做
                try {
                    history.setStatus("failed");
                    history.setEndTime(LocalDateTime.now());
                    history.setAssignedCount(successCount);
                    history.setResultDetail(toJson(details));
                    history.setUpdateTime(LocalDateTime.now());
                    autoAssignmentHistoryMapper.updateById(history);
                } catch (Exception ex) {
                    log.error("更新任务失败状态异常: {}", ex.getMessage(), ex);
                }
                log.error("自动分配任务执行失败: taskId={}", taskId, e);
            } finally {
                cancelledTasks.remove(taskId);
            }
        }, "auto-assignment-" + taskId).start();
    }

    /**
     * 组装分配明细基础信息（学生侧），教师侧在选中后回填
     */
    private AutoAssignmentProgressDTO.AssignmentDetail newAssignmentDetail(PaperInfo paper,
                                                                             Map<Long, String> studentNames) {
        AutoAssignmentProgressDTO.AssignmentDetail detail = new AutoAssignmentProgressDTO.AssignmentDetail();
        detail.setPaperId(paper.getId().toString());
        detail.setStudentId(paper.getStudentId() == null ? null : paper.getStudentId().toString());
        detail.setStudentName(studentNames.getOrDefault(paper.getStudentId(), "未知学生"));
        return detail;
    }

    /**
     * 批量预载学生姓名，避免执行循环内逐条 selectById（N+1）
     */
    private Map<Long, String> loadStudentNames(List<PaperInfo> papers) {
        List<Long> studentIds = papers.stream()
                .map(PaperInfo::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null && !u.getRealName().isBlank()
                                ? u.getRealName() : u.getUsername()));
    }

    /**
     * 待分配学生（已提交、未指定导师），按当前登录人数据范围收窄。
     */
    private List<PaperInfo> getUnassignedStudents(DataScopeSqlFilter scopeFilter) {
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(PaperInfo::getTeacherId)
              .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.PENDING);
        applyPaperScope(wrapper, scopeFilter);
        return paperInfoMapper.selectList(wrapper);
    }

    /**
     * 用发起人解析好的范围参数拼条件（不在此处读 SecurityContext，异步线程亦可用）。
     */
    private void applyPaperScope(LambdaQueryWrapper<PaperInfo> wrapper, DataScopeSqlFilter filter) {
        if (filter == null || filter.isUnrestricted()) {
            return;
        }
        if (filter.isDenyAll()) {
            wrapper.apply("1 = 0");
            return;
        }
        if (filter.getCollegeId() != null) {
            wrapper.apply("college_id = {0}", filter.getCollegeId());
        } else if (filter.getMajorId() != null) {
            wrapper.apply("major_id = {0}", filter.getMajorId());
        } else if (filter.getTeacherId() != null) {
            wrapper.apply("teacher_id = {0}", filter.getTeacherId());
        } else if (filter.getStudentId() != null) {
            wrapper.apply("student_id = {0}", filter.getStudentId());
        }
    }

    /**
     * 候选教师：启用状态的教师，按数据范围收窄；
     * excludeFullTeachers=true 时直接排除已无剩余容量的教师。
     * 容量口径统一为算法配置的 maxLoad（原先硬编码 12 会与配置项冲突）。
     */
    private List<SysUser> getAvailableTeachers(AutoAssignmentConfigDTO config, DataScopeSqlFilter scopeFilter) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUserType, "TEACHER")
               .eq(SysUser::getStatus, 1)
               .eq(SysUser::getIsDeleted, 0);
        applyTeacherDataScope(wrapper, scopeFilter);

        if (Boolean.TRUE.equals(config.getExcludeFullTeachers())) {
            int maxLoad = resolveMaxLoad(config);
            wrapper.apply("(current_advisor_count IS NULL OR current_advisor_count < {0})", maxLoad);
        }

        return sysUserMapper.selectList(wrapper);
    }

    /**
     * 教师侧数据范围：学院范围按学院过滤，专业范围按专业过滤；
     * 导师/本人范围无法定位到可分配的教师池，直接拒绝。
     */
    private void applyTeacherDataScope(LambdaQueryWrapper<SysUser> wrapper, DataScopeSqlFilter filter) {
        if (filter == null || filter.isUnrestricted()) {
            return;
        }
        if (filter.isDenyAll()) {
            wrapper.apply("1 = 0");
            return;
        }
        if (filter.getCollegeId() != null) {
            wrapper.eq(SysUser::getCollegeId, filter.getCollegeId());
            return;
        }
        if (filter.getMajorId() != null) {
            wrapper.eq(SysUser::getMajorId, filter.getMajorId());
            return;
        }
        wrapper.apply("1 = 0");
    }

    /**
     * 找到最适合的教师：容量必须有余量；专业不匹配时仅允许跨专业且未超跨专业额度；
     * 同分时优先当前负载更低的教师。
     */
    private SysUser findBestTeacher(PaperInfo student, List<SysUser> teachers,
                                    AutoAssignmentConfigDTO config, Map<Long, Integer> crossMajorAssigned) {
        boolean allowCrossMajor = Boolean.TRUE.equals(config.getAllowCrossMajor());
        int crossMajorLimit = safeInt(config.getCrossMajorLimit());
        List<SysUser> eligible = teachers.stream()
                .filter(teacher -> remainingSlots(teacher, config) > 0)
                .filter(teacher -> Objects.equals(student.getMajorId(), teacher.getMajorId())
                        || (allowCrossMajor
                            && crossMajorAssigned.getOrDefault(teacher.getId(), 0) < crossMajorLimit))
                .toList();
        if (eligible.isEmpty()) {
            return null;
        }

        // 按配置策略选择：random 随机、load_balance 优先剩余容量、
        // major_first 优先专业匹配；其余（comprehensive/未知）按综合评分
        String strategy = config.getStrategy() == null ? "comprehensive" : config.getStrategy();
        if ("random".equalsIgnoreCase(strategy)) {
            return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        }
        Comparator<SysUser> comparator;
        if ("load_balance".equalsIgnoreCase(strategy)) {
            comparator = Comparator.comparingInt((SysUser teacher) -> remainingSlots(teacher, config))
                    .thenComparingInt(teacher -> calculateMatchScore(student, teacher, config));
        } else if ("major_first".equalsIgnoreCase(strategy)) {
            comparator = Comparator.comparingInt((SysUser teacher) ->
                            Objects.equals(student.getMajorId(), teacher.getMajorId()) ? 1 : 0)
                    .thenComparingInt(teacher -> calculateMatchScore(student, teacher, config));
        } else {
            comparator = Comparator.comparingInt((SysUser teacher) -> calculateMatchScore(student, teacher, config))
                    .thenComparingInt(teacher -> -currentLoad(teacher));
        }
        return eligible.stream().max(comparator).orElse(null);
    }

    /**
     * 计算匹配分数（专业/研究兴趣/负载/经验四维）。
     */
    private int calculateMatchScore(PaperInfo student, SysUser teacher, AutoAssignmentConfigDTO config) {
        int score = 0;

        // 专业匹配（不匹配的候选已在 findBestTeacher 过滤，不会走到这里）
        if (Objects.equals(student.getMajorId(), teacher.getMajorId())) {
            score += safeInt(config.getMajorWeight());
        } else if (Boolean.TRUE.equals(config.getAllowCrossMajor())) {
            score += safeInt(config.getMajorWeight()) / 2; // 跨专业减半
        }

        // 研究兴趣匹配
        if (isResearchInterestMatch(student.getPaperTitle(), teacher.getResearchDirection())) {
            score += safeInt(config.getInterestWeight());
        }

        // 负载均衡：剩余容量占比（除以 maxLoad，避免原实现用 maxReviewCount 造成的除零）
        int maxLoad = resolveMaxLoad(config);
        int loadFactor = remainingSlots(teacher, config) * 100 / maxLoad;
        score += (loadFactor * safeInt(config.getLoadWeight())) / 100;

        // 经验权重：按账号创建年限（0~20 年）
        int experienceYears = calculateExperienceYears(teacher);
        score += (experienceYears * safeInt(config.getExperienceWeight())) / 20;

        return score;
    }

    private int resolveMaxLoad(AutoAssignmentConfigDTO config) {
        return config.getMaxLoad() == null || config.getMaxLoad() <= 0 ? 12 : config.getMaxLoad();
    }

    private int currentLoad(SysUser teacher) {
        return teacher.getCurrentAdvisorCount() == null ? 0 : teacher.getCurrentAdvisorCount();
    }

    private int remainingSlots(SysUser teacher, AutoAssignmentConfigDTO config) {
        return Math.max(0, resolveMaxLoad(config) - currentLoad(teacher));
    }

    /**
     * 检查研究兴趣匹配
     */
    private boolean isResearchInterestMatch(String paperTitle, String researchDirection) {
        if (paperTitle == null || researchDirection == null) return false;
        
        String[] keywords = researchDirection.split("[,，]");
        return Arrays.stream(keywords)
                .anyMatch(keyword -> paperTitle.contains(keyword.trim()));
    }

    /**
     * 计算教师经验年限（账号创建至今，截断到 0~20 年）
     */
    private int calculateExperienceYears(SysUser teacher) {
        if (teacher.getCreateTime() == null) {
            return 0;
        }
        long years = ChronoUnit.YEARS.between(teacher.getCreateTime(), LocalDateTime.now());
        return (int) Math.max(0, Math.min(20, years));
    }

    /**
     * 分配论文给教师：更新论文归属/状态、写分配记录、发送站内通知，并同步教师负载。
     * 使用局部更新对象，避免把加载后的整篇论文回写覆盖并发修改的字段。
     */
    private void assignPaperToTeacher(PaperInfo paper, SysUser teacher, Long operatorId) {
        paperStageGate.guard(paper.getPaperStatus(), DictConstants.PaperStatus.ASSIGNED,
                "autoAssignmentBatch");

        PaperInfo update = new PaperInfo();
        update.setTeacherId(teacher.getId());
        update.setTeacherName(teacher.getRealName());
        update.setAllocationType(DictConstants.AllocationType.AUTO);
        update.setAllocationStatus(DictConstants.AllocationStatus.PENDING);
        update.setAllocationTime(LocalDateTime.now());
        update.setPaperStatus(DictConstants.PaperStatus.ASSIGNED);
        update.setUpdateTime(LocalDateTime.now());
        // 条件更新：仅当论文仍未分配时才写入，防止并发任务/手动分配被覆盖
        LambdaUpdateWrapper<PaperInfo> condition = new LambdaUpdateWrapper<PaperInfo>()
                .eq(PaperInfo::getId, paper.getId())
                .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.PENDING)
                .isNull(PaperInfo::getTeacherId);
        int rows = paperInfoMapper.update(update, condition);
        if (rows <= 0) {
            throw new IllegalStateException("论文状态已变化或已被其他任务分配");
        }

        // 教师负载 +1（内存对象同步给本轮后续学生使用）
        teacher.setCurrentAdvisorCount(currentLoad(teacher) + 1);
        sysUserMapper.updateById(teacher);

        // 分配记录 + 站内通知：任一失败不阻断已落库的分配，只记日志
        createAllocationRecord(paper.getId(), paper.getStudentId(), teacher.getId(), operatorId);
        sendAssignmentNotifications(paper.getId(), paper.getPaperTitle(), paper.getStudentId(), teacher.getId());
    }

    private void createAllocationRecord(Long paperId, Long studentId, Long teacherId, Long operatorId) {
        try {
            TeacherAllocationRecord record = new TeacherAllocationRecord();
            record.setPaperId(paperId);
            record.setStudentId(studentId);
            record.setTeacherId(teacherId);
            record.setAllocationType(DictConstants.AllocationType.AUTO);
            record.setAllocationReason("智能批量分配");
            record.setAllocationTime(LocalDateTime.now());
            record.setOperatorId(operatorId);
            teacherAllocationRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("创建自动分配记录失败 - 论文ID: {}, 教师ID: {}", paperId, teacherId, e);
        }
    }

    private void sendAssignmentNotifications(Long paperId, String paperTitle, Long studentId, Long teacherId) {
        try {
            // 教师端「待确认」唯一入口是消息中心，不发通知教师完全无感知
            notificationService.sendTeacherAssignmentNotice(paperId, paperTitle, studentId, teacherId);
            notificationService.sendStudentAssignmentNotice(paperId, paperTitle, studentId, teacherId);
        } catch (Exception e) {
            log.warn("发送自动分配通知失败 - 论文ID: {}, 学生ID: {}, 教师ID: {}",
                    paperId, studentId, teacherId, e);
        }
    }

    /**
     * 预览：预期可分配数。
     *
     * <p>允许跨专业时按总容量取上限；不允许跨专业时按专业分别取
     * min(该专业待分配学生, 该专业教师剩余容量) 再求和，避免用其它专业容量虚报。</p>
     */
    private int calculateExpectedAssigned(List<PaperInfo> papers, List<SysUser> teachers,
                                          AutoAssignmentConfigDTO config) {
        PartitionCapacity capacity = partitionCapacity(teachers, config);
        if (Boolean.TRUE.equals(config.getAllowCrossMajor())) {
            return Math.min(papers.size(), capacity.total);
        }
        Map<Long, Long> studentsByMajor = new HashMap<>();
        long unassignedMajorStudents = 0;
        for (PaperInfo paper : papers) {
            if (paper.getMajorId() == null) {
                unassignedMajorStudents++;
            } else {
                studentsByMajor.merge(paper.getMajorId(), 1L, Long::sum);
            }
        }
        long expected = Math.min(unassignedMajorStudents, capacity.unassignedMajor);
        for (Map.Entry<Long, Long> entry : studentsByMajor.entrySet()) {
            expected += Math.min(entry.getValue(),
                    capacity.byMajor.getOrDefault(entry.getKey(), 0));
        }
        return (int) Math.min(Integer.MAX_VALUE, expected);
    }

    private PartitionCapacity partitionCapacity(List<SysUser> teachers, AutoAssignmentConfigDTO config) {
        PartitionCapacity capacity = new PartitionCapacity();
        for (SysUser teacher : teachers) {
            int slots = Math.max(0, remainingSlots(teacher, config));
            capacity.total += slots;
            if (teacher.getMajorId() == null) {
                capacity.unassignedMajor += slots;
            } else {
                capacity.byMajor.merge(teacher.getMajorId(), slots, Integer::sum);
            }
        }
        return capacity;
    }

    private static final class PartitionCapacity {
        private int total;
        private int unassignedMajor;
        private final Map<Long, Integer> byMajor = new HashMap<>();
    }

    /**
     * 预览：按专业统计待分配学生、候选教师与容量覆盖率。
     * 覆盖率 = min(100, 专业可用容量 / 待分配学生数 * 100)；无学生时按 100 计。
     */
    private List<AutoAssignmentPreviewDTO.MajorStat> buildMajorStats(
            List<PaperInfo> papers, List<SysUser> teachers, AutoAssignmentConfigDTO config) {
        Map<Long, Long> studentsByMajor = new HashMap<>();
        long unassignedMajorStudents = 0;
        for (PaperInfo paper : papers) {
            if (paper.getMajorId() == null) {
                unassignedMajorStudents++;
            } else {
                studentsByMajor.merge(paper.getMajorId(), 1L, Long::sum);
            }
        }

        Map<Long, Long> teachersByMajor = new HashMap<>();
        Map<Long, Integer> capacityByMajor = new HashMap<>();
        long unassignedMajorTeachers = 0;
        int unassignedMajorCapacity = 0;
        for (SysUser teacher : teachers) {
            int slots = Math.max(0, remainingSlots(teacher, config));
            if (teacher.getMajorId() == null) {
                unassignedMajorTeachers++;
                unassignedMajorCapacity += slots;
            } else {
                teachersByMajor.merge(teacher.getMajorId(), 1L, Long::sum);
                capacityByMajor.merge(teacher.getMajorId(), slots, Integer::sum);
            }
        }

        Set<Long> majorIds = new HashSet<>();
        majorIds.addAll(studentsByMajor.keySet());
        majorIds.addAll(teachersByMajor.keySet());
        Map<Long, String> majorNames = majorIds.isEmpty() ? Map.of()
                : majorMapper.selectBatchIds(majorIds).stream()
                    .collect(Collectors.toMap(Major::getId, Major::getMajorName));

        List<AutoAssignmentPreviewDTO.MajorStat> stats = new ArrayList<>();
        for (Long majorId : majorIds) {
            stats.add(buildMajorStat(majorId, majorNames.get(majorId),
                    studentsByMajor.getOrDefault(majorId, 0L),
                    teachersByMajor.getOrDefault(majorId, 0L),
                    capacityByMajor.getOrDefault(majorId, 0)));
        }
        // major_id 为空的学生/教师统一归入「未分配」
        if (unassignedMajorStudents > 0 || unassignedMajorTeachers > 0 || unassignedMajorCapacity > 0) {
            stats.add(buildMajorStat(null, "未分配",
                    unassignedMajorStudents, unassignedMajorTeachers, unassignedMajorCapacity));
        }
        stats.sort(Comparator.comparing(AutoAssignmentPreviewDTO.MajorStat::getStudentCount,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return stats;
    }

    private AutoAssignmentPreviewDTO.MajorStat buildMajorStat(Long majorId, String majorName,
                                                              long studentCount, long teacherCount,
                                                              int capacity) {
        AutoAssignmentPreviewDTO.MajorStat stat = new AutoAssignmentPreviewDTO.MajorStat();
        stat.setMajorId(majorId == null ? null : majorId.toString());
        stat.setMajorName(majorName != null ? majorName : (majorId == null ? "未分配" : "未知专业"));
        stat.setStudentCount((int) studentCount);
        stat.setTeacherCount((int) teacherCount);
        double matchRate = studentCount == 0
                ? 100.0
                : Math.min(100.0, Math.round(capacity * 10000.0 / studentCount) / 100.0);
        stat.setMatchRate(matchRate);
        return stat;
    }

    /**
     * 预览：教师负载与剩余容量，按当前负载降序
     */
    private List<AutoAssignmentPreviewDTO.TeacherLoadStat> buildTeacherLoadStats(
            List<SysUser> teachers, AutoAssignmentConfigDTO config) {
        int maxLoad = resolveMaxLoad(config);
        return teachers.stream()
                .map(teacher -> {
                    AutoAssignmentPreviewDTO.TeacherLoadStat stat = new AutoAssignmentPreviewDTO.TeacherLoadStat();
                    stat.setTeacherId(teacher.getId().toString());
                    stat.setTeacherName(teacher.getRealName() != null && !teacher.getRealName().isBlank()
                            ? teacher.getRealName() : teacher.getUsername());
                    stat.setCurrentLoad(currentLoad(teacher));
                    stat.setMaxLoad(maxLoad);
                    stat.setAvailableSlots(remainingSlots(teacher, config));
                    return stat;
                })
                .sorted(Comparator.comparing(AutoAssignmentPreviewDTO.TeacherLoadStat::getCurrentLoad,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 按主键或任务ID查历史（前端详情/应用传的是历史记录主键）
     */
    private AutoAssignmentHistory findHistory(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            AutoAssignmentHistory byId = autoAssignmentHistoryMapper.selectById(Long.parseLong(id));
            if (byId != null) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
            // 非数字按 taskId 兜底查询
        }
        return findHistoryByTaskId(id);
    }

    private AutoAssignmentHistory findHistoryByTaskId(String taskId) {
        return autoAssignmentHistoryMapper.selectOne(new LambdaQueryWrapper<AutoAssignmentHistory>()
                .eq(AutoAssignmentHistory::getTaskId, taskId)
                .eq(AutoAssignmentHistory::getIsDeleted, 0));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化自动分配详情失败: {}", e.getMessage());
            return null;
        }
    }

    private List<AutoAssignmentProgressDTO.AssignmentDetail> parseDetails(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<AutoAssignmentProgressDTO.AssignmentDetail>>() {});
        } catch (Exception e) {
            log.warn("解析自动分配详情失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析自动分配配置快照失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private Long parseLongId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 计算预估时间
     */
    private long calculateEstimatedTime(AutoAssignmentConfigDTO config, DataScopeSqlFilter scopeFilter) {
        // 基于实际待分配学生数计算（与执行口径同范围）
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(PaperInfo::getTeacherId)
              .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.PENDING);
        applyPaperScope(wrapper, scopeFilter);
        int studentCount = paperInfoMapper.selectCount(wrapper).intValue();
        
        // 每个学生分配需要100毫秒
        return 100L * Math.min(studentCount, 100); // 最多计算100个学生
    }
    
    /**
     * 解析整数配置
     */
    private Integer parseIntConfig(String value, Integer defaultValue) {
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 解析布尔配置
     */
    private Boolean parseBooleanConfig(String value, Boolean defaultValue) {
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
    
    /**
     * 保存配置项
     */
    private void saveConfigItem(String key, String value, String description, String type, Long operatorId, String operatorName) {
        AutoAssignmentConfig config = new AutoAssignmentConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        config.setConfigType(type);
        config.setIsEnabled(1);
        config.setUpdateTime(LocalDateTime.now());
        config.setIsDeleted(0);
        autoAssignmentConfigMapper.insert(config);
    }
    
    /**
     * 转换历史记录为DTO
     */
    private AutoAssignmentHistoryDTO convertToHistoryDTO(AutoAssignmentHistory entity) {
        AutoAssignmentHistoryDTO dto = new AutoAssignmentHistoryDTO();
        dto.setId(entity.getId().toString());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setStrategy(entity.getStrategy());
        dto.setTotalStudents(entity.getTotalStudents());
        dto.setAssignedCount(entity.getAssignedCount());
        dto.setSuccessRate(entity.getSuccessRate());
        dto.setDuration(entity.getDuration());
        dto.setStatus(entity.getStatus());
        dto.setOperatorId(entity.getOperatorId());
        dto.setOperatorName(entity.getOperatorName());
        dto.setRemark(entity.getRemark());
        return dto;
    }
    
    /**
     * 获取当前操作人ID
     */
    private Long getCurrentOperatorId() {
        try {
            return UserBusinessInfoUtils.getCurrentUserId();
        } catch (Exception e) {
            log.warn("获取当前用户ID失败，使用默认管理员ID。错误信息: {}", e.getMessage());
            return 1L; // 失败时返回默认管理员ID
        }
    }
}
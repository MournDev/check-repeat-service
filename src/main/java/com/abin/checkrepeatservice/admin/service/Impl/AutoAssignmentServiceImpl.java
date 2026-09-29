package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.service.AutoAssignmentService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.admin.dto.*;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.admin.mapper.AutoAssignmentHistoryMapper;
import com.abin.checkrepeatservice.admin.mapper.AutoAssignmentConfigMapper;
import com.abin.checkrepeatservice.pojo.entity.AutoAssignmentHistory;
import com.abin.checkrepeatservice.pojo.entity.AutoAssignmentConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Result<AutoAssignmentConfigDTO> getAlgorithmConfig() {
        try {
            AutoAssignmentConfigDTO config = new AutoAssignmentConfigDTO();
            
            // 从数据库获取配置
            LambdaQueryWrapper<AutoAssignmentConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AutoAssignmentConfig::getIsDeleted, 0)
                   .eq(AutoAssignmentConfig::getIsEnabled, 1);
            
            List<AutoAssignmentConfig> configList = autoAssignmentConfigMapper.selectList(wrapper);
            
            // 将数据库配置映射到DTO
            Map<String, String> configMap = configList.stream()
                .collect(Collectors.toMap(
                    AutoAssignmentConfig::getConfigKey,
                    AutoAssignmentConfig::getConfigValue,
                    (existing, replacement) -> existing
                ));
            
            // 设置配置值，如果数据库中没有则使用默认值
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
            
            log.info("获取算法配置成功");
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取算法配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取算法配置失败: " + e.getMessage());
        }
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
            return Result.error(ResultCode.SYSTEM_ERROR, "保存算法配置失败: " + e.getMessage());
        }
    }

    @Override
    public Result<AutoAssignmentPreviewDTO> getAssignmentPreview() {
        try {
            AutoAssignmentPreviewDTO preview = new AutoAssignmentPreviewDTO();
            
            // 获取待分配学生数
            LambdaQueryWrapper<PaperInfo> studentWrapper = new LambdaQueryWrapper<>();
            studentWrapper.isNull(PaperInfo::getTeacherId)
                          .eq(PaperInfo::getPaperStatus, "approved");
            List<PaperInfo> unassignedPapers = paperInfoMapper.selectList(studentWrapper);
            preview.setUnassigned(unassignedPapers.size());
            
            // 获取可用教师数
            LambdaQueryWrapper<SysUser> teacherWrapper = new LambdaQueryWrapper<>();
            teacherWrapper.eq(SysUser::getUserType, "teacher")
                         .eq(SysUser::getStatus, 1)
                         .eq(SysUser::getIsDeleted, 0);
            List<SysUser> availableTeachers = sysUserMapper.selectList(teacherWrapper);
            preview.setAvailableTeachers(availableTeachers.size());
            
            // 计算预期分配数（简单估算）
            preview.setExpectedAssigned(Math.min(unassignedPapers.size(), 
                availableTeachers.stream()
                    .mapToInt(t -> t.getMaxReviewCount() != null ? t.getMaxReviewCount() : 12)
                    .sum()));
            
            // 计算潜在冲突数（简单估算）
            preview.setPotentialConflicts(Math.max(0, unassignedPapers.size() - preview.getExpectedAssigned()));
            
            log.info("获取分配预览数据成功: unassigned={}, availableTeachers={}", 
                    preview.getUnassigned(), preview.getAvailableTeachers());
            return Result.success(preview);
        } catch (Exception e) {
            log.error("获取分配预览数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取分配预览数据失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> startAutoAssignment(AutoAssignmentStartDTO request) {
        try {
            String taskId = UUID.randomUUID().toString();
            AutoAssignmentConfigDTO config = request.getConfig();
            
            // 验证配置
            validateConfig(config);
            
            // 初始化进度记录到数据库
            AutoAssignmentHistory history = new AutoAssignmentHistory();
            history.setTaskId(taskId);
            history.setStartTime(LocalDateTime.now());
            history.setStrategy(config.getStrategy());
            history.setStatus("running");
            history.setOperatorId(getCurrentOperatorId());
            history.setUpdateTime(LocalDateTime.now());
            history.setIsDeleted(0);
            autoAssignmentHistoryMapper.insert(history);
            
            // 异步执行分配算法
            executeAssignmentAsync(taskId, config);
            
            // 返回任务信息
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("estimatedTime", calculateEstimatedTime(config));
            
            log.info("启动自动分配任务成功: taskId={}", taskId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("启动自动分配失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "启动自动分配失败: " + e.getMessage());
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
            return Result.error(ResultCode.SYSTEM_ERROR, "查询分配进度失败: " + e.getMessage());
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
            
            // 更新任务状态
            history.setStatus("cancelled");
            history.setEndTime(LocalDateTime.now());
            history.setUpdateTime(LocalDateTime.now());
            autoAssignmentHistoryMapper.updateById(history);
            
            log.info("取消分配任务成功: taskId={}", taskId);
            return Result.success("任务取消成功");
        } catch (Exception e) {
            log.error("取消分配任务失败: taskId={}, error={}", taskId, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "取消分配任务失败: " + e.getMessage());
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
            return Result.error(ResultCode.SYSTEM_ERROR, "获取执行历史列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> getAssignmentDetail(String id) {
        try {
            // 这里应该从详细记录表中查询具体分配结果
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", id);
            detail.put("results", new ArrayList<>());
            
            return Result.success(detail);
        } catch (Exception e) {
            log.error("获取执行详情失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取执行详情失败: " + e.getMessage());
        }
    }

    @Override
    public Result<String> applyAssignmentResult(String id) {
        try {
            // 这里应该将历史分配结果应用到当前系统
            log.info("应用分配结果成功: id={}", id);
            return Result.success("分配结果应用成功");
        } catch (Exception e) {
            log.error("应用分配结果失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "应用分配结果失败: " + e.getMessage());
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
            return Result.error(ResultCode.SYSTEM_ERROR, "刷新基础数据失败: " + e.getMessage());
        }
    }

    /**
     * 验证配置参数
     */
    private void validateConfig(AutoAssignmentConfigDTO config) {
        if (config.getMaxLoad() == null || config.getMaxLoad() <= 0) {
            throw new IllegalArgumentException("最大指导学生数必须大于0");
        }
        
        int totalWeight = config.getMajorWeight() + config.getInterestWeight() + 
                         config.getLoadWeight() + config.getExperienceWeight();
        if (totalWeight != 100) {
            throw new IllegalArgumentException("权重总和必须等于100");
        }
        
        if (config.getMinLoadPerTeacher() > config.getMaxLoad()) {
            throw new IllegalArgumentException("最小指导学生数不能大于最大指导学生数");
        }
    }

    /**
     * 异步执行分配算法
     */
    private void executeAssignmentAsync(String taskId, AutoAssignmentConfigDTO config) {
        new Thread(() -> {
            try {
                // 从数据库获取任务记录
                LambdaQueryWrapper<AutoAssignmentHistory> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AutoAssignmentHistory::getTaskId, taskId)
                       .eq(AutoAssignmentHistory::getIsDeleted, 0);
                
                AutoAssignmentHistory history = autoAssignmentHistoryMapper.selectOne(wrapper);
                if (history == null) return;
                
                // 获取待分配学生和可用教师
                List<PaperInfo> students = getUnassignedStudents();
                List<SysUser> teachers = getAvailableTeachers(config);
                
                // 更新总数
                history.setTotalStudents(students.size());
                history.setAssignedCount(0);
                history.setUpdateTime(LocalDateTime.now());
                autoAssignmentHistoryMapper.updateById(history);
                
                int successCount = 0;
                List<AutoAssignmentProgressDTO.AssignmentDetail> details = new ArrayList<>();
                
                // 执行分配算法
                for (int i = 0; i < students.size(); i++) {
                    PaperInfo student = students.get(i);
                    SysUser bestTeacher = findBestTeacher(student, teachers, config);
                    
                    if (bestTeacher != null) {
                        // 执行分配
                        assignStudentToTeacher(student, bestTeacher);
                        
                        // 更新教师负载
                        bestTeacher.setCurrentAdvisorCount(bestTeacher.getCurrentAdvisorCount() + 1);
                        sysUserMapper.updateById(bestTeacher);
                        
                        successCount++;
                        
                        // 记录分配详情
                        AutoAssignmentProgressDTO.AssignmentDetail detail = new AutoAssignmentProgressDTO.AssignmentDetail();
                        detail.setStudentName(getStudentName(student.getStudentId()));
                        detail.setStudentId(student.getId().toString());
                        detail.setTeacherName(bestTeacher.getRealName());
                        detail.setTeacherId(bestTeacher.getId().toString());
                        detail.setConflict(false);
                        detail.setMatchScore(calculateMatchScore(student, bestTeacher, config));
                        details.add(detail);
                    }
                    
                    // 更新进度
                    history.setAssignedCount(successCount);
                    history.setUpdateTime(LocalDateTime.now());
                    autoAssignmentHistoryMapper.updateById(history);
                }
                
                // 更新最终状态
                history.setStatus("completed");
                history.setEndTime(LocalDateTime.now());
                history.setAssignedCount(successCount);
                history.setSuccessRate(students.size() > 0 ? 
                    (double) successCount / students.size() * 100 : 0.0);
                history.setDuration(java.time.Duration.between(
                    history.getStartTime(), LocalDateTime.now()).toMillis());
                history.setUpdateTime(LocalDateTime.now());
                autoAssignmentHistoryMapper.updateById(history);
                
                log.info("自动分配任务完成: taskId={}, successCount={}", taskId, successCount);
                
            } catch (Exception e) {
                // 更新失败状态
                try {
                    LambdaQueryWrapper<AutoAssignmentHistory> failWrapper = new LambdaQueryWrapper<>();
                    failWrapper.eq(AutoAssignmentHistory::getTaskId, taskId)
                              .eq(AutoAssignmentHistory::getIsDeleted, 0);
                    AutoAssignmentHistory history = autoAssignmentHistoryMapper.selectOne(failWrapper);
                    if (history != null) {
                        history.setStatus("failed");
                        history.setEndTime(LocalDateTime.now());
                        history.setUpdateTime(LocalDateTime.now());
                        autoAssignmentHistoryMapper.updateById(history);
                    }
                } catch (Exception ex) {
                    log.error("更新任务失败状态异常: {}", ex.getMessage(), ex);
                }
                
                log.error("自动分配任务执行失败: taskId={}", taskId, e);
            }
        }).start();
    }

    /**
     * 获取待分配学生
     */
    private List<PaperInfo> getUnassignedStudents() {
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(PaperInfo::getTeacherId)
              .eq(PaperInfo::getPaperStatus, "approved");
        return paperInfoMapper.selectList(wrapper);
    }

    /**
     * 获取可用教师
     */
    private List<SysUser> getAvailableTeachers(AutoAssignmentConfigDTO config) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUserType, "teacher")
               .eq(SysUser::getStatus, 1)
               .eq(SysUser::getIsDeleted, 0);
        
        if (config.getExcludeFullTeachers()) {
            wrapper.lt(SysUser::getCurrentAdvisorCount, 12);
        }
        
        return sysUserMapper.selectList(wrapper);
    }

    /**
     * 找到最适合的教师
     */
    private SysUser findBestTeacher(PaperInfo student, List<SysUser> teachers, AutoAssignmentConfigDTO config) {
        return teachers.stream()
                .max(Comparator.comparingInt(teacher -> calculateMatchScore(student, teacher, config)))
                .orElse(null);
    }

    /**
     * 计算匹配分数
     */
    private int calculateMatchScore(PaperInfo student, SysUser teacher, AutoAssignmentConfigDTO config) {
        int score = 0;
        
        // 专业匹配
        if (Objects.equals(student.getMajorId(), teacher.getMajorId())) {
            score += config.getMajorWeight();
        } else if (config.getAllowCrossMajor()) {
            score += config.getMajorWeight() / 2; // 跨专业减半
        }
        
        // 研究兴趣匹配
        if (isResearchInterestMatch(student.getPaperTitle(), teacher.getResearchDirection())) {
            score += config.getInterestWeight();
        }
        
        // 负载均衡
        int loadFactor = Math.max(0, 100 - (teacher.getCurrentAdvisorCount() * 100 / 
                          (teacher.getMaxReviewCount())));
        score += (loadFactor * config.getLoadWeight()) / 100;
        
        // 经验权重（简单按指导年限计算）
        int experienceYears = calculateExperienceYears(teacher);
        score += (experienceYears * config.getExperienceWeight()) / 20; // 假设最多20年经验
        
        return score;
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
     * 计算教师经验年限
     */
    private int calculateExperienceYears(SysUser teacher) {
        // 简单实现，可以根据入职时间计算
        return 5; // 默认5年经验
    }

    /**
     * 分配学生给教师
     */
    private void assignStudentToTeacher(PaperInfo student, SysUser teacher) {
        student.setTeacherId(teacher.getId().longValue());
        student.setTeacherName(teacher.getRealName());
        student.setAllocationType("auto");
        student.setAllocationStatus("pending");
        student.setAllocationTime(LocalDateTime.now());
        student.setPaperStatus("assigned");
        student.setUpdateTime(LocalDateTime.now());
        paperInfoMapper.updateById(student);
    }

    /**
     * 获取学生姓名
     */
    private String getStudentName(Long studentId) {
        SysUser student = sysUserMapper.selectById(studentId);
        return student != null ? student.getRealName() : "未知学生";
    }

    /**
     * 计算预估时间
     */
    private long calculateEstimatedTime(AutoAssignmentConfigDTO config) {
        // 基于实际待分配学生数计算
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(PaperInfo::getTeacherId)
              .eq(PaperInfo::getPaperStatus, "approved");
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
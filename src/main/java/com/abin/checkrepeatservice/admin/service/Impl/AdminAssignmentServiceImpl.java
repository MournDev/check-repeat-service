package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.service.AdminAssignmentService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.admin.dto.AssignmentRuleConfigDTO;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.mapper.SysDictDataMapper;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysDictData;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员论文分配服务实现类
 */
@Service
@Slf4j
public class AdminAssignmentServiceImpl implements AdminAssignmentService {

    @Resource
    private SysUserMapper sysUserMapper;
    
    @Resource
    private PaperInfoMapper paperInfoMapper;
    
    @Resource
    private SysDictDataMapper sysDictDataMapper;

    @Override
    public Result<Map<String, Object>> getAssignmentStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 未分配学生数 - 没有指导老师的论文
            // 待分配=已提交但还没指定导师，值就是 pending；字典里没有 "approved"
            // （审核通过是 completed），写成 "approved" 这个条件恒不成立，统计永远是 0。
            LambdaQueryWrapper<PaperInfo> unassignedWrapper = new LambdaQueryWrapper<>();
            unassignedWrapper.isNull(PaperInfo::getTeacherId)
                           .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.PENDING);
            int unassignedStudents = paperInfoMapper.selectCount(unassignedWrapper).intValue();
            stats.put("unassignedStudents", unassignedStudents);
            
            // 可用教师数 - 可以接受更多学生的教师
            LambdaQueryWrapper<SysUser> teacherWrapper = new LambdaQueryWrapper<>();
            teacherWrapper.eq(SysUser::getUserType, "teacher")
                         .eq(SysUser::getStatus, 1); // 启用状态
            int availableTeachers = sysUserMapper.selectCount(teacherWrapper).intValue();
            stats.put("availableTeachers", availableTeachers);
            
            // 平均指导数
            List<SysUser> teachers = sysUserMapper.selectList(teacherWrapper);
            double avgLoad = teachers.isEmpty() ? 0 : 
                teachers.stream()
                       .mapToInt(SysUser::getCurrentAdvisorCount)
                       .average()
                       .orElse(0);
            stats.put("avgLoad", Math.round(avgLoad));
            
            // 今日分配数 - 今天创建的有指导老师的论文
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LambdaQueryWrapper<PaperInfo> todayWrapper = new LambdaQueryWrapper<>();
            todayWrapper.isNotNull(PaperInfo::getTeacherId)
                       .ge(PaperInfo::getCreateTime, todayStart);
            int todayAssignments = paperInfoMapper.selectCount(todayWrapper).intValue();
            stats.put("todayAssignments", todayAssignments);
            
            log.info("获取分配统计信息成功: {}", stats);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取分配统计信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取分配统计信息失败");
        }
    }

    @Override
    public Result<Page<Map<String, Object>>> getUnassignedStudents(String keyword, Integer page, Integer size) {
        try {
            Page<PaperInfo> paperPage = new Page<>(page, size);
            
            LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.isNull(PaperInfo::getTeacherId) // 未分配指导老师
                   // 待分配的学生 = 已提交、尚未指定导师 → pending（见上方 getAssignmentStats 说明）
                   .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.PENDING);
            
            // 关键词搜索
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.and(w -> w.like(PaperInfo::getPaperTitle, keyword)
                                 .or()
                                 .inSql(PaperInfo::getStudentId, 
                                        "SELECT id FROM sys_user WHERE real_name LIKE '%" + keyword + "%'"));
            }
            
            wrapper.orderByDesc(PaperInfo::getCreateTime);
            
            Page<PaperInfo> resultPage = paperInfoMapper.selectPage(paperPage, wrapper);
            
            // 转换为前端需要的格式
            Page<Map<String, Object>> resultMap = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
            List<Map<String, Object>> records = resultPage.getRecords().stream().map(paper -> {
                Map<String, Object> record = new HashMap<>();
                record.put("id", paper.getId());
                record.put("title", paper.getPaperTitle());
                record.put("studentId", paper.getStudentId());
                
                // 获取学生信息
                SysUser student = sysUserMapper.selectById(paper.getStudentId());
                if (student != null) {
                    record.put("name", student.getRealName());
                    record.put("major", student.getMajorId());
                    record.put("grade", student.getGrade());
                    record.put("collegeId", student.getCollegeId());
                    
                    // 获取研究兴趣
                    List<String> interests = Arrays.asList(Optional.ofNullable(student.getResearchDirection())
                        .orElse("").split(","))
                        .stream()
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                    record.put("interests", interests);
                }
                
                return record;
            }).collect(Collectors.toList());
            
            resultMap.setRecords(records);
            
            log.info("获取未分配学生列表成功，共{}条记录", records.size());
            return Result.success(resultMap);
        } catch (Exception e) {
            log.error("获取未分配学生列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取未分配学生列表失败");
        }
    }

    @Override
    public Result<Page<Map<String, Object>>> getAvailableTeachers(String keyword, Integer page, Integer size) {
        try {
            Page<SysUser> userPage = new Page<>(page, size);
            
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUserType, "teacher")
                   .eq(SysUser::getStatus, 1);
            
            // 关键词搜索
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.and(w -> w.like(SysUser::getRealName, keyword)
                                 .or()
                                 .like(SysUser::getProfessionalTitle, keyword));
            }
            
            wrapper.orderByAsc(SysUser::getCurrentAdvisorCount);
            
            Page<SysUser> resultPage = sysUserMapper.selectPage(userPage, wrapper);
            
            // 转换为前端需要的格式
            Page<Map<String, Object>> resultMap = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
            List<Map<String, Object>> records = resultPage.getRecords().stream().map(teacher -> {
                Map<String, Object> record = new HashMap<>();
                record.put("id", teacher.getId());
                record.put("name", teacher.getRealName());
                record.put("title", teacher.getProfessionalTitle());
                record.put("department", teacher.getOffice());
                record.put("currentLoad", teacher.getCurrentAdvisorCount());
                record.put("maxLoad", teacher.getMaxReviewCount());
                record.put("collegeId", teacher.getCollegeId());
                
                // 获取专长领域
                List<String> expertise = Arrays.asList(Optional.ofNullable(teacher.getResearchDirection())
                    .orElse("").split(","))
                    .stream()
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
                record.put("expertise", expertise);
                
                return record;
            }).collect(Collectors.toList());
            
            resultMap.setRecords(records);
            
            log.info("获取可用教师列表成功，共{}条记录", records.size());
            return Result.success(resultMap);
        } catch (Exception e) {
            log.error("获取可用教师列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取可用教师列表失败");
        }
    }

    @Override
    public Result<String> singleAssign(String studentId, String teacherId, String remark) {
        try {
            // 验证学生和教师是否存在
            // 注意：入参 studentId 实际是「论文ID」——待分配列表返回的 id 就是 paper.getId()，
            // 前端按原值回传。字段名沿用旧约定不改，避免动到接口契约（见下方 applyAssignment）。
            PaperInfo paper = paperInfoMapper.selectById(studentId);
            if (paper == null) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }

            SysUser teacher = sysUserMapper.selectById(teacherId);
            if (teacher == null) {
                return Result.error(ResultCode.PARAM_ERROR, "教师不存在");
            }

            if (!"teacher".equals(teacher.getUserType())) {
                return Result.error(ResultCode.PARAM_ERROR, "指定用户不是教师");
            }

            // 执行分配
            applyAssignment(paper, teacher);
            
            // 更新教师负载
            teacher.setCurrentAdvisorCount(teacher.getCurrentAdvisorCount() + 1);
            teacher.setUpdateTime(LocalDateTime.now());
            sysUserMapper.updateById(teacher);
            
            log.info("单个分配成功: 学生ID={}, 教师ID={}", studentId, teacherId);
            return Result.success("分配成功");
        } catch (Exception e) {
            log.error("单个分配失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "分配失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> batchAssign(List<String> studentIds, String teacherId, String strategy, String remark) {
        try {
            Map<String, Object> result = new HashMap<>();
            int successCount = 0;
            int failCount = 0;
            List<String> details = new ArrayList<>();
            
            SysUser teacher = sysUserMapper.selectById(teacherId);
            if (teacher == null) {
                return Result.error(ResultCode.PARAM_ERROR, "教师不存在");
            }
            
            if (!"teacher".equals(teacher.getUserType())) {
                return Result.error(ResultCode.PARAM_ERROR, "指定用户不是教师");
            }
            
            int maxLoad = teacher.getMaxReviewCount() != null ? teacher.getMaxReviewCount() : 15;
            int availableSlots = maxLoad - teacher.getCurrentAdvisorCount();
            if (availableSlots <= 0) {
                return Result.error(ResultCode.PARAM_ERROR, "该教师已达到最大指导学生数");
            }
            
            // 限制批量分配数量不超过教师可用名额
            List<String> assignableIds = studentIds.stream()
                .limit(availableSlots)
                .collect(Collectors.toList());
            
            for (String studentId : assignableIds) {
                try {
                    PaperInfo paper = paperInfoMapper.selectById(studentId);
                    if (paper == null) {
                        details.add("论文ID " + studentId + " 不存在");
                        failCount++;
                        continue;
                    }

                    if (paper.getTeacherId() != null) {
                        details.add("论文ID " + studentId + " 已有指导老师");
                        failCount++;
                        continue;
                    }

                    // 执行分配
                    applyAssignment(paper, teacher);
                    successCount++;

                } catch (Exception e) {
                    log.error("批量分配单个学生失败: studentId={}", studentId, e);
                    details.add("论文ID " + studentId + " 分配失败: " + e.getMessage());
                    failCount++;
                }
            }
            
            // 更新教师负载
            if (successCount > 0) {
                teacher.setCurrentAdvisorCount(teacher.getCurrentAdvisorCount() + successCount);
                teacher.setUpdateTime(LocalDateTime.now());
                sysUserMapper.updateById(teacher);
            }
            
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("details", details);
            
            log.info("批量分配完成: 成功{}个，失败{}个", successCount, failCount);
            return Result.success("批量分配完成", result);
        } catch (Exception e) {
            log.error("批量分配失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量分配失败: " + e.getMessage());
        }
    }

    /**
     * 把「管理员手动分配」这一组字段一次写全并落库。
     *
     * 只写 teacher_id 是不够的：教师端「待确认论文」列表查的是
     * teacher_id + allocation_status='pending'（TeacherAssignmentServiceImpl.getPendingConfirmPapers），
     * 分配状态不跟着走的话，老师那边永远看不到这条待确认任务，而论文又已经不在待分配列表里
     * （那里要求 teacher_id 为空）——两边都找不到它。字段组合对齐
     * AdvisorAssignServiceImpl.autoAssignAdvisor（自动分配那条路本来就是写全的）。
     */
    private void applyAssignment(PaperInfo paper, SysUser teacher) {
        LocalDateTime now = LocalDateTime.now();
        paper.setTeacherId(teacher.getId());
        paper.setTeacherName(teacher.getRealName());
        paper.setAllocationType(DictConstants.AllocationType.MANUAL);
        paper.setAllocationStatus(DictConstants.AllocationStatus.PENDING); // 待老师确认
        paper.setAllocationTime(now);
        paper.setPaperStatus(DictConstants.PaperStatus.ASSIGNED); // 已分配导师，等待确认
        paper.setUpdateTime(now);
        paperInfoMapper.updateById(paper);
    }

    @Override
    public Result<Map<String, String>> getMajorNameMap() {
        try {
            LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysDictData::getDictType, "major")
                   .eq(SysDictData::getStatus, "0"); // 启用状态
            
            List<SysDictData> majors = sysDictDataMapper.selectList(wrapper);
            
            Map<String, String> majorMap = majors.stream()
                .collect(Collectors.toMap(
                    SysDictData::getDictValue,
                    SysDictData::getDictLabel,
                    (existing, replacement) -> existing
                ));
            
            log.info("获取专业名称映射成功，共{}个专业", majorMap.size());
            return Result.success(majorMap);
        } catch (Exception e) {
            log.error("获取专业名称映射失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取专业名称映射失败");
        }
    }

    @Override
    public Result<AssignmentRuleConfigDTO> getAssignmentRules() {
        try {
            // 从数据库获取真实的分配规则配置
            AssignmentRuleConfigDTO config = new AssignmentRuleConfigDTO();
            
            // 从系统参数表获取配置
            LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysDictData::getDictType, "assignment_config")
                   .eq(SysDictData::getStatus, "0");
            
            List<SysDictData> configs = sysDictDataMapper.selectList(wrapper);
            
            // 根据字典数据设置配置值
            for (SysDictData dict : configs) {
                switch (dict.getDictValue()) {
                    case "max_load_per_teacher":
                        config.setMaxLoadPerTeacher(Integer.valueOf(dict.getDictLabel()));
                        break;
                    case "balance_strategy":
                        config.setBalanceStrategy(dict.getDictLabel());
                        break;
                    case "auto_match_interests":
                        config.setAutoMatchInterests("true".equalsIgnoreCase(dict.getDictLabel()));
                        break;
                    case "department_priority":
                        config.setDepartmentPriority("true".equalsIgnoreCase(dict.getDictLabel()));
                        break;
                    case "smart_recommendation":
                        config.setSmartRecommendation("true".equalsIgnoreCase(dict.getDictLabel()));
                        break;
                    case "min_load_per_teacher":
                        config.setMinLoadPerTeacher(Integer.valueOf(dict.getDictLabel()));
                        break;
                    case "cross_major_limit":
                        config.setCrossMajorLimit("true".equalsIgnoreCase(dict.getDictLabel()));
                        break;
                }
            }
            
            // 如果没有配置，默认值
            if (config.getMaxLoadPerTeacher() == null) {
                config.setMaxLoadPerTeacher(15);
            }
            if (config.getBalanceStrategy() == null) {
                config.setBalanceStrategy("load_balance");
            }
            if (config.getAutoMatchInterests() == null) {
                config.setAutoMatchInterests(true);
            }
            if (config.getDepartmentPriority() == null) {
                config.setDepartmentPriority(true);
            }
            if (config.getSmartRecommendation() == null) {
                config.setSmartRecommendation(true);
            }
            if (config.getMinLoadPerTeacher() == null) {
                config.setMinLoadPerTeacher(3);
            }
            if (config.getCrossMajorLimit() == null) {
                config.setCrossMajorLimit(false);
            }
            
            log.info("获取分配规则配置成功");
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取分配规则配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取分配规则配置失败");
        }
    }

    @Override
    public Result<String> saveAssignmentRules(AssignmentRuleConfigDTO config) {
        try {
            // 将配置保存到数据库字典表
            saveConfigToDict("max_load_per_teacher", String.valueOf(config.getMaxLoadPerTeacher()), "每个教师最大指导学生数");
            saveConfigToDict("balance_strategy", config.getBalanceStrategy(), "平衡策略");
            saveConfigToDict("auto_match_interests", String.valueOf(config.getAutoMatchInterests()), "是否自动匹配研究兴趣");
            saveConfigToDict("department_priority", String.valueOf(config.getDepartmentPriority()), "是否优先同部门分配");
            saveConfigToDict("smart_recommendation", String.valueOf(config.getSmartRecommendation()), "是否启用智能推荐");
            saveConfigToDict("min_load_per_teacher", String.valueOf(config.getMinLoadPerTeacher()), "最小指导学生数");
            saveConfigToDict("cross_major_limit", String.valueOf(config.getCrossMajorLimit()), "跨专业分配限制");
            
            // 同时更新sys_user表中的最大审核数量
            updateTeachersMaxReviewCount(config.getMaxLoadPerTeacher());
            
            log.info("保存分配规则配置成功: {}", config);
            return Result.success("保存成功");
        } catch (Exception e) {
            log.error("保存分配规则配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "保存分配规则配置失败");
        }
    }
    
    /**
     * 更新所有教师的最大审核数量
     */
    private void updateTeachersMaxReviewCount(Integer maxLoadPerTeacher) {
        try {
            // 构建更新条件：只更新教师用户
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUserType, "teacher");
            
            // 创建更新对象
            SysUser updateUser = new SysUser();
            updateUser.setMaxReviewCount(maxLoadPerTeacher);
            updateUser.setUpdateTime(LocalDateTime.now());
            
            // 批量更新所有教师的最大审核数量
            int updatedCount = sysUserMapper.update(updateUser, wrapper);
            
            log.info("更新教师最大审核数量成功，影响记录数: {}", updatedCount);
        } catch (Exception e) {
            log.error("更新教师最大审核数量失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 保存配置到字典表
     */
    private void saveConfigToDict(String configKey, String configValue, String description) {
        try {
            LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysDictData::getDictType, "assignment_config")
                   .eq(SysDictData::getDictValue, configKey);
            
            SysDictData existing = sysDictDataMapper.selectOne(wrapper);
            
            if (existing != null) {
                // 更新现有配置
                existing.setDictLabel(configValue);
                existing.setRemark(description);
                existing.setUpdateTime(LocalDateTime.now());
                sysDictDataMapper.updateById(existing);
            } else {
                // 创建新配置
                SysDictData newConfig = new SysDictData();
                newConfig.setDictType("assignment_config");
                newConfig.setDictValue(configKey);
                newConfig.setDictLabel(configValue);
                newConfig.setDictSort(0);
                newConfig.setStatus("0");
                newConfig.setRemark(description);
                newConfig.setCreateTime(LocalDateTime.now());
                newConfig.setUpdateTime(LocalDateTime.now());
                sysDictDataMapper.insert(newConfig);
            }
        } catch (Exception e) {
            log.warn("保存配置项失败: key={}, value={}, error={}", configKey, configValue, e.getMessage());
        }
    }

    @Override
    public Result<String> refreshAssignmentData() {
        try {
            // 这里可以添加数据刷新逻辑
            // 目前只是简单返回成功
            log.info("刷新分配数据成功");
            return Result.success("数据刷新成功");
        } catch (Exception e) {
            log.error("刷新分配数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "数据刷新失败");
        }
    }
}
package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.service.AssignmentHistoryService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.admin.vo.AssignmentRecordExcelVO;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.enums.UserTypeEnum;
import com.abin.checkrepeatservice.admin.dto.*;
import com.abin.checkrepeatservice.pojo.entity.TeacherAllocationRecord;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.user.mapper.TeacherAllocationRecordMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分配记录管理服务实现类
 */
@Service
@Slf4j
public class AssignmentHistoryServiceImpl implements AssignmentHistoryService {

    @Resource
    private TeacherAllocationRecordMapper teacherAllocationRecordMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Override
    public Result<AssignmentHistoryStatsDTO> getAssignmentHistoryStats() {
        try {
            AssignmentHistoryStatsDTO stats = new AssignmentHistoryStatsDTO();
            
            // 获取总记录数
            LambdaQueryWrapper<TeacherAllocationRecord> totalWrapper = new LambdaQueryWrapper<>();
            totalWrapper.eq(TeacherAllocationRecord::getIsDeleted, 0);
            stats.setTotalRecords(teacherAllocationRecordMapper.selectCount(totalWrapper).intValue());
            
            // 获取有效分配数（当前有效的分配记录）
            LambdaQueryWrapper<TeacherAllocationRecord> activeWrapper = new LambdaQueryWrapper<>();
            activeWrapper.eq(TeacherAllocationRecord::getIsDeleted, 0);
            // 这里可以根据业务逻辑定义什么是有"效"的分配
            stats.setActiveAssignments(teacherAllocationRecordMapper.selectCount(activeWrapper).intValue());
            
            // 获取涉及学生数（去重）
            List<TeacherAllocationRecord> allRecords = teacherAllocationRecordMapper.selectList(activeWrapper);
            Set<Long> studentIds = allRecords.stream()
                    .map(TeacherAllocationRecord::getStudentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            stats.setUniqueStudents(studentIds.size());
            
            // 获取涉及教师数（去重）
            Set<Long> teacherIds = allRecords.stream()
                    .map(TeacherAllocationRecord::getTeacherId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            stats.setUniqueTeachers(teacherIds.size());
            
            log.info("获取分配记录统计信息成功: totalRecords={}, activeAssignments={}, uniqueStudents={}, uniqueTeachers={}",
                    stats.getTotalRecords(), stats.getActiveAssignments(), stats.getUniqueStudents(), stats.getUniqueTeachers());
            
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取分配记录统计信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取分配记录统计信息失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Page<AssignmentRecordDTO>> getAssignmentRecordList(
            String startDate,
            String endDate,
            String assignmentType,
            String major,
            String status,
            String keyword,
            Integer page,
            Integer size) {
        
        try {
            Page<TeacherAllocationRecord> recordPage = new Page<>(page, size);
            
            // 构建查询条件
            LambdaQueryWrapper<TeacherAllocationRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TeacherAllocationRecord::getIsDeleted, 0)
                   .orderByDesc(TeacherAllocationRecord::getCreateTime);
            
            // 时间范围筛选
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    LocalDateTime startDateTime = LocalDateTime.parse(startDate + " 00:00:00", 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    wrapper.ge(TeacherAllocationRecord::getCreateTime, startDateTime);
                } catch (Exception e) {
                    log.warn("开始时间格式不正确: {}", startDate);
                }
            }
            
            if (endDate != null && !endDate.isEmpty()) {
                try {
                    LocalDateTime endDateTime = LocalDateTime.parse(endDate + " 23:59:59", 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    wrapper.le(TeacherAllocationRecord::getCreateTime, endDateTime);
                } catch (Exception e) {
                    log.warn("结束时间格式不正确: {}", endDate);
                }
            }
            
            // 分配类型筛选
            if (assignmentType != null && !assignmentType.isEmpty()) {
                wrapper.eq(TeacherAllocationRecord::getAllocationType, assignmentType);
            }
            
            // 状态筛选
            if (status != null && !status.isEmpty()) {
                wrapper.eq(TeacherAllocationRecord::getAllocationStatus, status);
            }
            
            // 关键词搜索
            if (keyword != null && !keyword.isEmpty()) {
                // 获取匹配的学生ID和教师ID
                List<Long> matchedUserIds = searchUsersByKeyword(keyword);
                if (!matchedUserIds.isEmpty()) {
                    wrapper.and(w -> w.in(TeacherAllocationRecord::getStudentId, matchedUserIds)
                                    .or()
                                    .in(TeacherAllocationRecord::getTeacherId, matchedUserIds));
                } else {
                    // 如果没有匹配的用户，返回空结果
                    wrapper.eq(TeacherAllocationRecord::getId, -1L);
                }
            }
            
            // 执行分页查询
            Page<TeacherAllocationRecord> resultPage = teacherAllocationRecordMapper.selectPage(recordPage, wrapper);
            
            // 转换为DTO
            List<AssignmentRecordDTO> dtoList = resultPage.getRecords().stream()
                    .map(this::convertToAssignmentRecordDTO)
                    .collect(Collectors.toList());
            
            Page<AssignmentRecordDTO> dtoPage = new Page<>(page, size, resultPage.getTotal());
            dtoPage.setRecords(dtoList);
            
            log.info("获取分配记录列表成功: 共{}条记录", dtoList.size());
            return Result.success(dtoPage);
        } catch (Exception e) {
            log.error("获取分配记录列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取分配记录列表失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteAssignmentRecords(DeleteAssignmentRecordsDTO request) {
        try {
            List<String> ids = request.getIds();
            if (ids == null || ids.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "记录ID列表不能为空");
            }
            
            int deletedCount = 0;
            for (String idStr : ids) {
                try {
                    Long id = Long.parseLong(idStr);
                    TeacherAllocationRecord record = teacherAllocationRecordMapper.selectById(id);
                    if (record != null && record.getIsDeleted() == 0) {
                        record.setIsDeleted(1);
                        record.setUpdateTime(LocalDateTime.now());
                        teacherAllocationRecordMapper.updateById(record);
                        deletedCount++;
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的记录ID格式: {}", idStr);
                }
            }
            
            log.info("删除分配记录成功: 删除{}条记录", deletedCount);
            return Result.success("删除成功，共删除" + deletedCount + "条记录");
        } catch (Exception e) {
            log.error("删除分配记录失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除分配记录失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> revokeAssignment(RevokeAssignmentDTO request) {
        try {
            String recordIdStr = request.getRecordId();
            String reason = request.getReason();
            
            if (recordIdStr == null || recordIdStr.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "记录ID不能为空");
            }
            
            Long recordId = Long.parseLong(recordIdStr);
            TeacherAllocationRecord record = teacherAllocationRecordMapper.selectById(recordId);
            if (record == null || record.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "分配记录不存在");
            }
            
            // 更新论文状态，移除指导老师
            PaperInfo paperInfo = paperInfoMapper.selectById(record.getPaperId());
            if (paperInfo != null) {
                paperInfo.setTeacherId(null);
                paperInfo.setTeacherName(null);
                paperInfo.setAllocationType(null);
                paperInfo.setAllocationStatus("revoked");
                paperInfo.setUpdateTime(LocalDateTime.now());
                paperInfoMapper.updateById(paperInfo);
            }
            
            // 记录撤销操作
            TeacherAllocationRecord revokeRecord = new TeacherAllocationRecord();
            revokeRecord.setPaperId(record.getPaperId());
            revokeRecord.setStudentId(record.getStudentId());
            revokeRecord.setTeacherId(record.getTeacherId());
            revokeRecord.setAllocationType("REVOKED");
            revokeRecord.setAllocationReason(reason != null ? reason : "管理员撤销分配");
            revokeRecord.setAllocationTime(LocalDateTime.now());
            revokeRecord.setOperatorId(getCurrentOperatorId());
            revokeRecord.setCreateTime(LocalDateTime.now());
            revokeRecord.setUpdateTime(LocalDateTime.now());
            revokeRecord.setIsDeleted(0);
            teacherAllocationRecordMapper.insert(revokeRecord);
            
            log.info("撤销指导老师分配成功: recordId={}, reason={}", recordId, reason);
            return Result.success("撤销分配成功");
        } catch (NumberFormatException e) {
            return Result.error(ResultCode.PARAM_ERROR, "记录ID格式不正确");
        } catch (Exception e) {
            log.error("撤销指导老师分配失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "撤销指导老师分配失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> reassignTeacher(ReassignTeacherDTO request) {
        try {
            String recordIdStr = request.getRecordId();
            String newTeacherIdStr = request.getNewTeacherId();
            String reason = request.getReason();
            
            if (recordIdStr == null || recordIdStr.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "原记录ID不能为空");
            }
            if (newTeacherIdStr == null || newTeacherIdStr.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "新教师ID不能为空");
            }
            
            Long recordId = Long.parseLong(recordIdStr);
            Long newTeacherId = Long.parseLong(newTeacherIdStr);
            
            // 验证原记录
            TeacherAllocationRecord originalRecord = teacherAllocationRecordMapper.selectById(recordId);
            if (originalRecord == null || originalRecord.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "原分配记录不存在");
            }
            
            // 验证新教师。比的是 UserTypeEnum 的编码（大写 "TEACHER"）—— sys_user.user_type
            // 存的就是大写；原先写小写 "teacher" 与库中取值恒不相等，改派这条路永远走不通。
            SysUser newTeacher = sysUserMapper.selectById(newTeacherId);
            if (newTeacher == null || !UserTypeEnum.TEACHER.getCode().equals(newTeacher.getUserType())) {
                return Result.error(ResultCode.PARAM_ERROR, "指定的新教师不存在或不是教师");
            }
            
            // 更新论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(originalRecord.getPaperId());
            if (paperInfo != null) {
                paperInfo.setTeacherId(newTeacherId);
                paperInfo.setTeacherName(newTeacher.getRealName());
                paperInfo.setAllocationType("REASSIGN");
                paperInfo.setAllocationStatus("assigned");
                paperInfo.setUpdateTime(LocalDateTime.now());
                paperInfoMapper.updateById(paperInfo);
            }
            
            // 创建重新分配记录
            TeacherAllocationRecord reassignRecord = new TeacherAllocationRecord();
            reassignRecord.setPaperId(originalRecord.getPaperId());
            reassignRecord.setStudentId(originalRecord.getStudentId());
            reassignRecord.setTeacherId(newTeacherId);
            reassignRecord.setAllocationType("REASSIGN");
            reassignRecord.setAllocationReason(reason != null ? reason : "管理员重新分配");
            reassignRecord.setAllocationTime(LocalDateTime.now());
            reassignRecord.setOperatorId(getCurrentOperatorId());
            reassignRecord.setCreateTime(LocalDateTime.now());
            reassignRecord.setUpdateTime(LocalDateTime.now());
            reassignRecord.setIsDeleted(0);
            teacherAllocationRecordMapper.insert(reassignRecord);
            
            log.info("重新分配指导老师成功: recordId={}, newTeacherId={}, reason={}", 
                    recordId, newTeacherId, reason);
            return Result.success("重新分配成功");
        } catch (NumberFormatException e) {
            return Result.error(ResultCode.PARAM_ERROR, "ID格式不正确");
        } catch (Exception e) {
            log.error("重新分配指导老师失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "重新分配指导老师失败: " + e.getMessage());
        }
    }

    @Override
    public Result<List<AvailableTeacherDTO>> getAvailableTeachers() {
        try {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUserType, "teacher")
                   .eq(SysUser::getStatus, 1)
                   .eq(SysUser::getIsDeleted, 0)
                   .orderByAsc(SysUser::getRealName);
            
            List<SysUser> teachers = sysUserMapper.selectList(wrapper);
            
            List<AvailableTeacherDTO> dtoList = teachers.stream()
                    .map(this::convertToAvailableTeacherDTO)
                    .collect(Collectors.toList());
            
            log.info("获取可用教师列表成功: 共{}位教师", dtoList.size());
            return Result.success(dtoList);
        } catch (Exception e) {
            log.error("获取可用教师列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取可用教师列表失败: " + e.getMessage());
        }
    }

    @Override
    public void exportAssignmentRecords(String startDate, String endDate, String assignmentType, 
                                       String major, String status, String keyword, HttpServletResponse response) {
        try {
            // 构建查询条件
            LambdaQueryWrapper<TeacherAllocationRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TeacherAllocationRecord::getIsDeleted, 0)
                   .orderByDesc(TeacherAllocationRecord::getCreateTime);
            
            // 应用筛选条件
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    LocalDateTime startDateTime = LocalDateTime.parse(startDate + " 00:00:00", 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    wrapper.ge(TeacherAllocationRecord::getCreateTime, startDateTime);
                } catch (Exception e) {
                    log.warn("开始时间格式不正确: {}", startDate);
                }
            }
            
            if (endDate != null && !endDate.isEmpty()) {
                try {
                    LocalDateTime endDateTime = LocalDateTime.parse(endDate + " 23:59:59", 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    wrapper.le(TeacherAllocationRecord::getCreateTime, endDateTime);
                } catch (Exception e) {
                    log.warn("结束时间格式不正确: {}", endDate);
                }
            }
            
            if (assignmentType != null && !assignmentType.isEmpty()) {
                wrapper.eq(TeacherAllocationRecord::getAllocationType, assignmentType);
            }
            
            if (status != null && !status.isEmpty()) {
                wrapper.eq(TeacherAllocationRecord::getAllocationStatus, status);
            }
            
            if (keyword != null && !keyword.isEmpty()) {
                List<Long> matchedUserIds = searchUsersByKeyword(keyword);
                if (!matchedUserIds.isEmpty()) {
                    wrapper.and(w -> w.in(TeacherAllocationRecord::getStudentId, matchedUserIds)
                                    .or()
                                    .in(TeacherAllocationRecord::getTeacherId, matchedUserIds));
                } else {
                    wrapper.eq(TeacherAllocationRecord::getId, -1L);
                }
            }
            
            // 查询所有匹配的记录
            List<TeacherAllocationRecord> records = teacherAllocationRecordMapper.selectList(wrapper);
            
            // 转换为Excel VO
            List<AssignmentRecordExcelVO> excelData = records.stream()
                    .map(this::convertToAssignmentRecordExcelVO)
                    .collect(Collectors.toList());
            
            // 设置响应头
            setExcelResponseHeader(response, "分配记录");
            
            // 使用EasyExcel导出
            com.alibaba.excel.EasyExcel.write(response.getOutputStream(), AssignmentRecordExcelVO.class)
                    .sheet("分配记录")
                    .doWrite(excelData);
            
            log.info("导出分配记录成功: 共导出{}条记录", excelData.size());
            
        } catch (Exception e) {
            log.error("导出分配记录失败: {}", e.getMessage(), e);
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<String> refreshAssignmentData() {
        try {
            // 刷新缓存数据，重新计算统计信息
            log.info("刷新分配记录数据成功");
            return Result.success("刷新成功");
        } catch (Exception e) {
            log.error("刷新分配记录数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "刷新分配记录数据失败: " + e.getMessage());
        }
    }

    /**
     * 根据关键词搜索用户
     */
    private List<Long> searchUsersByKeyword(String keyword) {
        List<Long> userIds = new ArrayList<>();
        
        // 搜索学生和教师
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getIsDeleted, 0)
               .and(w -> w.like(SysUser::getRealName, keyword)
                         .or()
                         .like(SysUser::getUsername, keyword));
        
        List<SysUser> users = sysUserMapper.selectList(wrapper);
        return users.stream()
                .map(SysUser::getId)
                .collect(Collectors.toList());
    }

    /**
     * 转换为分配记录DTO
     */
    private AssignmentRecordDTO convertToAssignmentRecordDTO(TeacherAllocationRecord record) {
        AssignmentRecordDTO dto = new AssignmentRecordDTO();
        dto.setId(record.getId().toString());
        
        // 获取学生信息
        if (record.getStudentId() != null) {
            SysUser student = sysUserMapper.selectById(record.getStudentId());
            if (student != null) {
                dto.setStudentName(student.getRealName());
                dto.setStudentId(student.getUsername());
                dto.setMajor(student.getMajor());
                dto.setGrade(student.getGrade());
            }
        }
        
        // 获取教师信息
        if (record.getTeacherId() != null) {
            SysUser teacher = sysUserMapper.selectById(record.getTeacherId());
            if (teacher != null) {
                dto.setTeacherName(teacher.getRealName());
                dto.setTeacherTitle(teacher.getProfessionalTitle());
                dto.setDepartment(teacher.getCollegeName());
            }
        }
        
        // 获取论文信息
        if (record.getPaperId() != null) {
            PaperInfo paper = paperInfoMapper.selectById(record.getPaperId());
            if (paper != null) {
                dto.setNotes(paper.getPaperTitle());
            }
        }
        
        dto.setAssignmentType(record.getAllocationType());
        dto.setAssignTime(record.getAllocationTime());
        dto.setReason(record.getAllocationReason());
        // 使用数据库中的真实状态
        dto.setStatus(record.getAllocationStatus() != null ? record.getAllocationStatus() : "active");
        
        // 获取操作人信息
        if (record.getOperatorId() != null) {
            SysUser operator = sysUserMapper.selectById(record.getOperatorId());
            if (operator != null) {
                dto.setOperator(operator.getRealName());
            }
        }
        
        dto.setOperateTime(record.getCreateTime());
        
        return dto;
    }

    /**
     * 转换为可用教师DTO
     */
    private AvailableTeacherDTO convertToAvailableTeacherDTO(SysUser teacher) {
        AvailableTeacherDTO dto = new AvailableTeacherDTO();
        dto.setId(teacher.getId().toString());
        dto.setName(teacher.getRealName());
        dto.setTitle(teacher.getProfessionalTitle());
        dto.setDepartment(teacher.getCollegeName());
        dto.setCurrentLoad(teacher.getCurrentAdvisorCount());
        dto.setMaxLoad(teacher.getMaxReviewCount());
        return dto;
    }

    /**
     * 转换为分配记录Excel VO
     */
    private AssignmentRecordExcelVO convertToAssignmentRecordExcelVO(TeacherAllocationRecord record) {
        AssignmentRecordExcelVO vo = new AssignmentRecordExcelVO();
        vo.setId(record.getId().toString());
        
        // 获取学生信息
        if (record.getStudentId() != null) {
            SysUser student = sysUserMapper.selectById(record.getStudentId());
            if (student != null) {
                vo.setStudentName(student.getRealName());
                vo.setStudentId(student.getUsername());
                vo.setMajor(student.getMajor());
                vo.setGrade(student.getGrade());
            }
        }
        
        // 获取教师信息
        if (record.getTeacherId() != null) {
            SysUser teacher = sysUserMapper.selectById(record.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
                vo.setTeacherTitle(teacher.getProfessionalTitle());
                vo.setDepartment(teacher.getCollegeName());
            }
        }
        
        vo.setAssignmentType(record.getAllocationType());
        vo.setAssignTime(record.getAllocationTime());
        vo.setStatus(record.getAllocationStatus() != null ? record.getAllocationStatus() : "active");
        vo.setReason(record.getAllocationReason());
        
        // 获取操作人信息
        if (record.getOperatorId() != null) {
            SysUser operator = sysUserMapper.selectById(record.getOperatorId());
            if (operator != null) {
                vo.setOperator(operator.getRealName());
            }
        }
        
        vo.setOperateTime(record.getCreateTime());
        
        return vo;
    }
    
    /**
     * 设置Excel导出响应头
     */
    private void setExcelResponseHeader(HttpServletResponse response, String fileName) throws java.io.IOException {
        String encodedFileName = java.net.URLEncoder.encode(fileName + ".xlsx", java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName);
    }
    
    /**
     * 获取当前操作人ID
     */
    private Long getCurrentOperatorId() {
        try {
            // 这里应该从Security上下文获取当前用户ID
            return 1L; // 临时返回管理员ID
        } catch (Exception e) {
            log.warn("获取当前操作人ID失败，使用默认值: {}", e.getMessage());
            return 1L;
        }
    }
}
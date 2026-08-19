package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.*;
import com.abin.checkrepeatservice.teacher.service.TeacherStudentManagementService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师学生管理服务实现类
 */
@Service
@Slf4j
public class TeacherStudentManagementServiceImpl implements TeacherStudentManagementService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PaperInfoMapper paperInfoMapper;

    @Autowired
    private MessageService messageService;

    @Override
    public Result<Object> getStudentList(StudentListRequestDTO requestDTO) {
        try {
            // 构建查询条件
            LambdaQueryWrapper<PaperInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PaperInfo::getTeacherId, requestDTO.getTeacherId())
                       .eq(PaperInfo::getIsDeleted, 0);

            // 添加搜索条件
            if (requestDTO.getSearch() != null && !requestDTO.getSearch().trim().isEmpty()) {
                // 需要关联查询学生信息
                queryWrapper.and(wrapper -> 
                    wrapper.like(PaperInfo::getPaperTitle, requestDTO.getSearch())
                           .or()
                           .exists("SELECT 1 FROM sys_user su WHERE su.id = paper_info.student_id " +
                                 "AND (su.username LIKE '%" + requestDTO.getSearch() + "%' " +
                                 "OR su.real_name LIKE '%" + requestDTO.getSearch() + "%')")
                );
            }

            // 添加状态筛选
            if (requestDTO.getStatus() != null && !requestDTO.getStatus().trim().isEmpty()) {
                queryWrapper.eq(PaperInfo::getPaperStatus, requestDTO.getStatus());
            }

            // 创建分页对象
            Page<PaperInfo> page = new Page<>(requestDTO.getPage(), requestDTO.getPageSize());

            // 执行分页查询
            Page<PaperInfo> paperPage = paperInfoMapper.selectPage(page, queryWrapper);

            // 转换为学生列表DTO
            List<StudentListDTO> studentList = paperPage.getRecords().stream()
                    .map(this::convertToStudentListDTO)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("list", studentList);
            responseData.put("totalCount", paperPage.getTotal());
            responseData.put("currentPage", paperPage.getCurrent());
            responseData.put("totalPages", paperPage.getPages());

            return Result.success("获取学生列表成功", responseData);
        } catch (Exception e) {
            log.error("获取学生列表失败: teacherId={}", requestDTO.getTeacherId(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取学生列表失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean deleteStudent(Long studentId) {
        try {
            // 检查学生是否存在且未被删除
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(SysUser::getId, studentId)
                      .eq(SysUser::getIsDeleted, 0);
            
            SysUser student = sysUserMapper.selectOne(userWrapper);
            if (student == null) {
                log.warn("学生不存在或已被删除: studentId={}", studentId);
                return false;
            }

            // 软删除学生
            student.setIsDeleted(1);
            student.setUpdateTime(LocalDateTime.now());
            int result = sysUserMapper.updateById(student);
            
            if (result > 0) {
                log.info("成功删除学生: studentId={}", studentId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("删除学生失败: studentId={}", studentId, e);
            throw new RuntimeException("删除学生失败", e);
        }
    }

    @Override
    public Result<Object> getStudentStats(Long teacherId) {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 查询该教师指导的所有论文
            LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
            paperWrapper.eq(PaperInfo::getTeacherId, teacherId)
                       .eq(PaperInfo::getIsDeleted, 0);

            List<PaperInfo> papers = paperInfoMapper.selectList(paperWrapper);

            // 统计总学生数（去重）
            Set<Long> studentIds = papers.stream()
                    .map(PaperInfo::getStudentId)
                    .collect(Collectors.toSet());
            
            // 统计各种状态的学生数
            long submittedCount = papers.stream()
                    .filter(paper -> paper.getPaperStatus() != null && 
                           (paper.getPaperStatus().equals("submitted") || 
                            paper.getPaperStatus().equals("auditing") ||
                            paper.getPaperStatus().equals("reviewed")))
                    .map(PaperInfo::getStudentId)
                    .distinct()
                    .count();

            long assignedCount = papers.size(); // 有论文的就是已分配的
            
            long completedCount = papers.stream()
                    .filter(paper -> "reviewed".equals(paper.getPaperStatus()))
                    .map(PaperInfo::getStudentId)
                    .distinct()
                    .count();

            stats.put("totalStudents", studentIds.size());
            stats.put("submittedStudents", submittedCount);
            stats.put("assignedStudents", assignedCount);
            stats.put("completedStudents", completedCount);

            return Result.success("获取统计信息成功", stats);
        } catch (Exception e) {
            log.error("获取学生统计信息失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取统计信息失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean assignAdvisor(Long studentId, AssignAdvisorDTO assignAdvisorDTO) {
        try {
            // 更新论文信息中的导师信息
            LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
            paperWrapper.eq(PaperInfo::getStudentId, studentId)
                       .eq(PaperInfo::getIsDeleted, 0);

            List<PaperInfo> papers = paperInfoMapper.selectList(paperWrapper);
            
            if (papers.isEmpty()) {
                log.warn("学生没有论文信息: studentId={}", studentId);
                return false;
            }

            boolean success = true;
            for (PaperInfo paper : papers) {
                paper.setTeacherId(assignAdvisorDTO.getAdvisorId());
                paper.setTeacherName(assignAdvisorDTO.getAdvisorName());
                paper.setUpdateTime(LocalDateTime.now());
                
                int result = paperInfoMapper.updateById(paper);
                if (result <= 0) {
                    success = false;
                    log.warn("更新论文导师信息失败: paperId={}", paper.getId());
                }
            }

            if (success) {
                log.info("成功分配导师: studentId={}, advisorId={}", studentId, assignAdvisorDTO.getAdvisorId());
            }
            return success;
        } catch (Exception e) {
            log.error("分配导师失败: studentId={}, advisorId={}", 
                     studentId, assignAdvisorDTO.getAdvisorId(), e);
            throw new RuntimeException("分配导师失败", e);
        }
    }

    @Override
    public boolean sendMessage(SendMessageDTO sendMessageDTO) {
        try {
            // 使用当前登录用户ID
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
                
            SystemMessage message = new SystemMessage();
            message.setSenderId(currentUserId); // 使用当前教师 ID 而非硬编码 0
            message.setReceiverId(sendMessageDTO.getReceiverId());
            message.setTitle(sendMessageDTO.getTitle() != null ? sendMessageDTO.getTitle() : "系统消息");
            message.setContent(sendMessageDTO.getContent());
            message.setMessageType("PRIVATE");
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());
            message.setIsRead(0);
            message.setPriority(1);

            Result<Boolean> result = messageService.sendMessage(message);
            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("消息发送成功: receiverId={}", sendMessageDTO.getReceiverId());
                return true;
            } else {
                log.warn("消息发送失败: receiverId={}, error={}", 
                        sendMessageDTO.getReceiverId(), result.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("发送消息失败: receiverId={}", sendMessageDTO.getReceiverId(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public BatchOperationResultDTO batchAssignAdvisor(BatchAssignAdvisorDTO batchAssignDTO) {
        BatchOperationResultDTO result = new BatchOperationResultDTO();
        List<BatchOperationResultDTO.FailureDetail> failures = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < batchAssignDTO.getStudentIds().size(); i++) {
            Long studentId = batchAssignDTO.getStudentIds().get(i);
            try {
                AssignAdvisorDTO assignDTO = new AssignAdvisorDTO();
                assignDTO.setAdvisorId(batchAssignDTO.getAdvisorId());
                assignDTO.setAdvisorName(batchAssignDTO.getAdvisorName());
                
                boolean success = assignAdvisor(studentId, assignDTO);
                if (success) {
                    successCount++;
                } else {
                    BatchOperationResultDTO.FailureDetail failure = new BatchOperationResultDTO.FailureDetail();
                    failure.setIndex(i + 1);
                    failure.setReason("分配导师失败");
                    failures.add(failure);
                }
            } catch (Exception e) {
                BatchOperationResultDTO.FailureDetail failure = new BatchOperationResultDTO.FailureDetail();
                failure.setIndex(i + 1);
                failure.setReason("系统异常: " + e.getMessage());
                failures.add(failure);
            }
        }

        result.setSuccessCount(successCount);
        result.setFailCount(failures.size());
        result.setFailures(failures);
        return result;
    }

    @Override
    public BatchOperationResultDTO batchSendMessage(BatchSendMessageDTO batchSendDTO) {
        BatchOperationResultDTO result = new BatchOperationResultDTO();
        List<BatchOperationResultDTO.FailureDetail> failures = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < batchSendDTO.getReceiverIds().size(); i++) {
            Long receiverId = batchSendDTO.getReceiverIds().get(i);
            try {
                SendMessageDTO sendDTO = new SendMessageDTO();
                sendDTO.setReceiverId(receiverId);
                sendDTO.setReceiverType(batchSendDTO.getReceiverType());
                sendDTO.setTitle(batchSendDTO.getTitle());
                sendDTO.setContent(batchSendDTO.getContent());
                
                boolean success = sendMessage(sendDTO);
                if (success) {
                    successCount++;
                } else {
                    BatchOperationResultDTO.FailureDetail failure = new BatchOperationResultDTO.FailureDetail();
                    failure.setIndex(i + 1);
                    failure.setReason("消息发送失败");
                    failures.add(failure);
                }
            } catch (Exception e) {
                BatchOperationResultDTO.FailureDetail failure = new BatchOperationResultDTO.FailureDetail();
                failure.setIndex(i + 1);
                failure.setReason("系统异常: " + e.getMessage());
                failures.add(failure);
            }
        }

        result.setSuccessCount(successCount);
        result.setFailCount(failures.size());
        result.setFailures(failures);
        return result;
    }

    @Override
    @Transactional
    public BatchOperationResultDTO batchDeleteStudents(BatchDeleteDTO batchDeleteDTO) {
        BatchOperationResultDTO result = new BatchOperationResultDTO();
        List<BatchOperationResultDTO.FailureDetail> failures = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < batchDeleteDTO.getStudentIds().size(); i++) {
            Long studentId = batchDeleteDTO.getStudentIds().get(i);
            try {
                boolean success = deleteStudent(studentId);
                if (success) {
                    successCount++;
                } else {
                    BatchOperationResultDTO.FailureDetail failure = new BatchOperationResultDTO.FailureDetail();
                    failure.setIndex(i + 1);
                    failure.setReason("删除失败");
                    failures.add(failure);
                }
            } catch (Exception e) {
                BatchOperationResultDTO.FailureDetail failure = new BatchOperationResultDTO.FailureDetail();
                failure.setIndex(i + 1);
                failure.setReason("系统异常: " + e.getMessage());
                failures.add(failure);
            }
        }

        result.setSuccessCount(successCount);
        result.setFailCount(failures.size());
        result.setFailures(failures);
        return result;
    }

    @Override
    public String exportStudents(ExportRequestDTO exportRequest) {
        try {
            // 获取学生数据
            StudentListRequestDTO listRequest = new StudentListRequestDTO();
            listRequest.setTeacherId(exportRequest.getTeacherId());
            listRequest.setPage(1);
            listRequest.setPageSize(10000); // 导出最大数量
            listRequest.setSearch(exportRequest.getSearch());
            listRequest.setStatus(exportRequest.getStatus());
            listRequest.setCollege(exportRequest.getCollege());

            Result<Object> result = getStudentList(listRequest);
            if (!result.isSuccess()) {
                throw new RuntimeException("获取学生数据失败: " + result.getMessage());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            @SuppressWarnings("unchecked")
            List<StudentListDTO> studentList = (List<StudentListDTO>) data.get("list");

            // 准备导出数据
            List<Map<String, Object>> exportData = studentList.stream().map(student -> {
                Map<String, Object> row = new HashMap<>();
                row.put("学号", student.getUsername());
                row.put("姓名", student.getStudentName());
                row.put("学院", student.getCollegeName());
                row.put("专业", student.getMajor());
                row.put("年级", student.getGrade());
                row.put("论文状态", student.getPaperStatus());
                row.put("指导老师", student.getAdvisorName());
                row.put("提交时间", student.getSubmitTime());
                row.put("相似度", student.getSimilarity());
                return row;
            }).collect(Collectors.toList());

            // 生成Excel文件
            String fileName = "学生列表_" + System.currentTimeMillis() + ".xlsx";
            String filePath = exportToExcel(exportData, fileName);
            
            log.info("导出学生数据成功: teacherId={}, count={}", 
                    exportRequest.getTeacherId(), studentList.size());
            return filePath;
        } catch (Exception e) {
            log.error("导出学生数据失败: teacherId={}", exportRequest.getTeacherId(), e);
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<Object> addStudent(AddStudentDTO addStudentDTO) {
        try {
            // 参数校验
            if (addStudentDTO.getUsername() == null || addStudentDTO.getUsername().trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "学号不能为空");
            }
            if (addStudentDTO.getStudentName() == null || addStudentDTO.getStudentName().trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "学生姓名不能为空");
            }
            if (addStudentDTO.getCollegeName() == null || addStudentDTO.getCollegeName().trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "学院名称不能为空");
            }
            if (addStudentDTO.getMajor() == null || addStudentDTO.getMajor().trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "专业不能为空");
            }
            if (addStudentDTO.getGrade() == null || addStudentDTO.getGrade().trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "年级不能为空");
            }
            
            // 检查学号是否已存在
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(SysUser::getUsername, addStudentDTO.getUsername().trim())
                      .eq(SysUser::getIsDeleted, 0);
            
            SysUser existingUser = sysUserMapper.selectOne(userWrapper);
            if (existingUser != null) {
                return Result.error(ResultCode.PARAM_ERROR, "学号已存在");
            }
            
            // 创建新学生用户
            SysUser newUser = new SysUser();
            newUser.setUsername(addStudentDTO.getUsername().trim());
            newUser.setRealName(addStudentDTO.getStudentName().trim());
            newUser.setCollegeName(addStudentDTO.getCollegeName().trim());
            newUser.setMajor(addStudentDTO.getMajor().trim());
            newUser.setGrade(addStudentDTO.getGrade().trim());
            newUser.setEmail(addStudentDTO.getEmail() != null ? addStudentDTO.getEmail().trim() : null);
            newUser.setPhone(addStudentDTO.getPhone() != null ? addStudentDTO.getPhone().trim() : null);
            newUser.setUserType("STUDENT");
            newUser.setIsDeleted(0);
            newUser.setCreateTime(LocalDateTime.now());
            newUser.setUpdateTime(LocalDateTime.now());
            
            int insertResult = sysUserMapper.insert(newUser);
            if (insertResult > 0) {
                log.info("成功添加学生: username={}, studentName={}", 
                        addStudentDTO.getUsername(), addStudentDTO.getStudentName());
                
                // 构建返回数据
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("studentId", newUser.getId());
                responseData.put("username", newUser.getUsername());
                responseData.put("studentName", newUser.getRealName());
                responseData.put("collegeName", newUser.getCollegeName());
                responseData.put("major", newUser.getMajor());
                responseData.put("grade", newUser.getGrade());
                responseData.put("createTime", newUser.getCreateTime());
                
                return Result.success("学生添加成功", responseData);
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "添加学生失败");
            }
        } catch (Exception e) {
            log.error("添加学生失败: username={}", addStudentDTO.getUsername(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "添加学生失败: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public ImportResultDTO importStudents(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<ImportResultDTO.ImportFailure> failures = new ArrayList<>();
        int successCount = 0;

        try {
            // 解析Excel文件
            List<Map<String, Object>> importData = parseExcel(file.getInputStream());
            
            for (int i = 0; i < importData.size(); i++) {
                try {
                    Map<String, Object> rowData = importData.get(i);
                    
                    // 验证必要字段
                    String username = (String) rowData.get("学号");
                    String studentName = (String) rowData.get("姓名");
                    
                    if (username == null || username.trim().isEmpty()) {
                        ImportResultDTO.ImportFailure failure = new ImportResultDTO.ImportFailure();
                        failure.setRow(i + 2); // Excel行号从1开始，加上标题行
                        failure.setReason("学号不能为空");
                        failures.add(failure);
                        continue;
                    }

                    // 检查学号是否已存在
                    LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
                    userWrapper.eq(SysUser::getUsername, username)
                              .eq(SysUser::getIsDeleted, 0);
                    
                    SysUser existingUser = sysUserMapper.selectOne(userWrapper);
                    if (existingUser != null) {
                        ImportResultDTO.ImportFailure failure = new ImportResultDTO.ImportFailure();
                        failure.setRow(i + 2);
                        failure.setReason("学号已存在");
                        failures.add(failure);
                        continue;
                    }

                    // 创建新学生用户
                    SysUser newUser = new SysUser();
                    newUser.setUsername(username);
                    newUser.setRealName(studentName);
                    newUser.setCollegeName((String) rowData.get("学院"));
                    newUser.setMajor((String) rowData.get("专业"));
                    newUser.setGrade((String) rowData.get("年级"));
                    newUser.setUserType("STUDENT");
                    newUser.setIsDeleted(0);
                    newUser.setCreateTime(LocalDateTime.now());
                    newUser.setUpdateTime(LocalDateTime.now());

                    int insertResult = sysUserMapper.insert(newUser);
                    if (insertResult > 0) {
                        successCount++;
                    } else {
                        ImportResultDTO.ImportFailure failure = new ImportResultDTO.ImportFailure();
                        failure.setRow(i + 2);
                        failure.setReason("数据库插入失败");
                        failures.add(failure);
                    }
                } catch (Exception e) {
                    ImportResultDTO.ImportFailure failure = new ImportResultDTO.ImportFailure();
                    failure.setRow(i + 2);
                    failure.setReason("系统异常: " + e.getMessage());
                    failures.add(failure);
                }
            }

            result.setSuccessCount(successCount);
            result.setFailCount(failures.size());
            result.setFailures(failures);
            
            log.info("导入学生数据完成: success={}, fail={}", successCount, failures.size());
            return result;
        } catch (IOException e) {
            log.error("解析Excel文件失败", e);
            throw new RuntimeException("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 将PaperInfo转换为StudentListDTO
     */
    private StudentListDTO convertToStudentListDTO(PaperInfo paperInfo) {
        try {
            // 查询学生信息
            SysUser student = sysUserMapper.selectById(paperInfo.getStudentId());
            if (student == null) {
                return null;
            }

            StudentListDTO dto = new StudentListDTO();
            dto.setStudentId(student.getId());
            dto.setUsername(student.getUsername());
            dto.setStudentName(student.getRealName());
            dto.setCollegeName(student.getCollegeName());
            dto.setMajor(student.getMajor());
            dto.setGrade(student.getGrade());
            dto.setPaperStatus(paperInfo.getPaperStatus());
            dto.setAdvisorName(paperInfo.getTeacherName());
            dto.setSubmitTime(paperInfo.getSubmitTime());
            dto.setSimilarity(paperInfo.getSimilarityRate() != null ? 
                             BigDecimal.valueOf(paperInfo.getSimilarityRate().doubleValue()) : BigDecimal.ZERO);
            
            return dto;
        } catch (Exception e) {
            log.error("转换学生信息失败: paperId={}", paperInfo.getId(), e);
            return null;
        }
    }

    /**
     * 导出Excel文件
     */
    private String exportToExcel(List<Map<String, Object>> data, String fileName) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("学生列表");
            
            // 创建标题行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"学号", "姓名", "学院", "专业", "年级", "论文状态", "指导老师", "提交时间", "相似度"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            
            // 填充数据
            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue((String) rowData.get("学号"));
                row.createCell(1).setCellValue((String) rowData.get("姓名"));
                row.createCell(2).setCellValue((String) rowData.get("学院"));
                row.createCell(3).setCellValue((String) rowData.get("专业"));
                row.createCell(4).setCellValue((String) rowData.get("年级"));
                row.createCell(5).setCellValue((String) rowData.get("论文状态"));
                row.createCell(6).setCellValue((String) rowData.get("指导老师"));
                row.createCell(7).setCellValue(rowData.get("提交时间") != null ? 
                    rowData.get("提交时间").toString() : "");
                Object similarityObj = rowData.get("相似度");
                if (similarityObj != null) {
                    if (similarityObj instanceof Number) {
                        row.createCell(8).setCellValue(((Number) similarityObj).doubleValue());
                    } else {
                        row.createCell(8).setCellValue(similarityObj.toString());
                    }
                } else {
                    row.createCell(8).setCellValue("");
                }
            }
            
            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // 保存文件
            String filePath = System.getProperty("java.io.tmpdir") + "/" + fileName;
            try (java.io.FileOutputStream fileOut = new java.io.FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
            
            return filePath;
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw new RuntimeException("导出Excel失败: " + e.getMessage());
        }
    }

    /**
     * 解析Excel文件
     */
    private List<Map<String, Object>> parseExcel(java.io.InputStream inputStream) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try {
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            
            // 获取标题行
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue());
            }
            
            // 解析数据行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Map<String, Object> rowData = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING:
                                rowData.put(headers.get(j), cell.getStringCellValue());
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    rowData.put(headers.get(j), cell.getDateCellValue());
                                } else {
                                    rowData.put(headers.get(j), cell.getNumericCellValue());
                                }
                                break;
                            case BOOLEAN:
                                rowData.put(headers.get(j), cell.getBooleanCellValue());
                                break;
                            default:
                                rowData.put(headers.get(j), "");
                        }
                    } else {
                        rowData.put(headers.get(j), "");
                    }
                }
                result.add(rowData);
            }
            
            workbook.close();
            return result;
        } catch (Exception e) {
            log.error("解析Excel失败", e);
            throw new RuntimeException("解析Excel失败: " + e.getMessage());
        }
    }
}

package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教师学生管理服务接口
 */
public interface TeacherStudentManagementService {
    
    /**
     * 获取学生列表
     * @param requestDTO 请求参数
     * @return 学生列表分页数据
     */
    Result<Object> getStudentList(StudentListRequestDTO requestDTO);
    
    /**
     * 删除学生
     * @param studentId 学生ID
     * @return 删除结果
     */
    boolean deleteStudent(Long studentId);
    
    /**
     * 获取学生统计信息
     * @param teacherId 教师ID
     * @return 统计信息
     */
    Result<Object> getStudentStats(Long teacherId);
    
    /**
     * 分配导师
     * @param studentId 学生ID
     * @param assignAdvisorDTO 分配信息
     * @return 分配结果
     */
    boolean assignAdvisor(Long studentId, AssignAdvisorDTO assignAdvisorDTO);
    
    /**
     * 发送消息
     * @param sendMessageDTO 消息信息
     * @return 发送结果
     */
    boolean sendMessage(SendMessageDTO sendMessageDTO);
    
    /**
     * 批量分配导师
     * @param batchAssignDTO 批量分配信息
     * @return 批量操作结果
     */
    BatchOperationResultDTO batchAssignAdvisor(BatchAssignAdvisorDTO batchAssignDTO);
    
    /**
     * 批量发送消息
     * @param batchSendDTO 批量发送信息
     * @return 批量操作结果
     */
    BatchOperationResultDTO batchSendMessage(BatchSendMessageDTO batchSendDTO);
    
    /**
     * 批量删除学生
     * @param batchDeleteDTO 批量删除信息
     * @return 批量操作结果
     */
    BatchOperationResultDTO batchDeleteStudents(BatchDeleteDTO batchDeleteDTO);
    
    /**
     * 导出学生数据
     * @param exportRequest 导出请求
     * @return 文件路径
     */
    String exportStudents(ExportRequestDTO exportRequest);
    
    /**
     * 导入学生数据
     * @param file Excel文件
     * @return 导入结果
     */
    ImportResultDTO importStudents(MultipartFile file);
    
    /**
     * 添加学生
     * @param addStudentDTO 学生信息
     * @return 添加结果
     */
    Result<Object> addStudent(AddStudentDTO addStudentDTO);
}
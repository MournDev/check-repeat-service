package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.dto.StudentProfileDTO;
import com.abin.checkrepeatservice.student.dto.UpdateProfileReq;

/**
 * 学生个人信息管理服务接口
 */
public interface StudentProfileService {
    
    /**
     * 获取学生个人资料
     * @param studentId 学生ID
     * @return 个人资料信息
     */
    Result<StudentProfileDTO> getStudentProfile(Long studentId);
    
    /**
     * 更新个人资料
     * @param studentId 学生ID
     * @param updateReq 更新请求
     * @return 更新结果
     */
    Result<String> updateProfile(Long studentId, UpdateProfileReq updateReq);
    
    /**
     * 获取导师信息
     * @param studentId 学生ID
     * @return 导师信息
     */
    Result<StudentProfileDTO.AdvisorInfo> getAdvisorInfo(Long studentId);
    
    /**
     * 发送消息给导师
     * @param studentId 学生ID
     * @param advisorId 导师ID
     * @param messageContent 消息内容
     * @return 发送结果
     */
    Result<String> sendMessageToAdvisor(Long studentId, Long advisorId, String messageContent);
    
    /**
     * 获取与导师的沟通记录
     * @param studentId 学生ID
     * @param advisorId 导师ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 沟通记录列表
     */
    Result<Object> getCommunicationHistory(Long studentId, Long advisorId, Integer pageNum, Integer pageSize);
}
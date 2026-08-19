package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.TeacherProfileDTO;
import com.abin.checkrepeatservice.teacher.dto.UpdateTeacherProfileReq;

/**
 * 教师个人信息管理服务接口
 */
public interface TeacherProfileService {
    
    /**
     * 获取教师个人资料
     * @param teacherId 教师ID
     * @return 个人资料信息
     */
    Result<TeacherProfileDTO> getTeacherProfile(Long teacherId);
    
    /**
     * 更新教师个人资料
     * @param teacherId 教师ID
     * @param updateReq 更新请求
     * @return 更新结果
     */
    Result<String> updateProfile(Long teacherId, UpdateTeacherProfileReq updateReq);
    
    /**
     * 获取学生咨询消息列表
     * @param teacherId 教师ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 消息列表
     */
    Result<Object> getStudentConsultations(Long teacherId, Integer pageNum, Integer pageSize);
    
    /**
     * 回复学生咨询
     * @param teacherId 教师ID
     * @param messageId 消息ID
     * @param replyContent 回复内容
     * @return 回复结果
     */
    Result<String> replyToStudent(Long teacherId, Long messageId, String replyContent);
    
    /**
     * 获取消息统计
     * @param teacherId 教师ID
     * @return 消息统计数据
     */
    Result<Object> getMessageStatistics(Long teacherId);
}
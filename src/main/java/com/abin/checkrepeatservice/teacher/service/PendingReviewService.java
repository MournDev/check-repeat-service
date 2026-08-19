package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.*;
import com.abin.checkrepeatservice.teacher.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 待审核论文服务接口
 */
public interface PendingReviewService {
    
    /**
     * 获取待审核论文列表
     * @param queryDTO 查询参数
     * @return 论文列表
     */
    Result<Object> getPendingReviews(PendingReviewQueryDTO queryDTO);
    
    /**
     * 获取待审核统计信息
     * @param teacherId 教师ID
     * @return 统计信息
     */
    Result<PendingStatsVO> getPendingStats(String teacherId);
    
    /**
     * 论文审核
     * @param teacherId 教师ID
     * @param reviewDTO 审核参数
     * @return 审核结果
     */
    Result<ReviewResultDetailVO> reviewPaper(String teacherId, PaperReviewDTO reviewDTO);
    
    /**
     * 重新查重检测
     * @param teacherId 教师ID
     * @param paperId 论文ID
     * @return 检测结果
     */
    Result<Map<String, Object>> recheckPlagiarism(String teacherId, String paperId);
    
    /**
     * 发送提醒消息
     * @param teacherId 教师ID
     * @param reminderDTO 提醒参数
     * @return 发送结果
     */
    Result<Map<String, Object>> sendReminder(String teacherId, SendReminderDTO reminderDTO);
    
    /**
     * 联系学生
     * @param teacherId 教师ID
     * @param contactDTO 联系参数
     * @return 联系结果
     */
    Result<Map<String, Object>> contactStudent(String teacherId, ContactStudentDTO contactDTO);
    
    /**
     * 下载论文文件
     * @param teacherId 教师ID
     * @param paperId 论文ID
     * @param response HTTP响应
     */
    void downloadPaper(String teacherId, String paperId, HttpServletResponse response);
    
    /**
     * 获取查重报告
     * @param teacherId 教师ID
     * @param paperId 论文ID
     * @return 查重报告
     */
    Result<PlagiarismReportVO> getPlagiarismReport(String teacherId, String paperId);
    
    /**
     * 获取今日审核统计
     * @param teacherId 教师ID
     * @return 今日审核统计
     */
    Result<TodayReviewedVO> getTodayReviewedCount(String teacherId);
    
    /**
     * 委托审核
     * @param teacherId 教师ID
     * @param delegateDTO 委托参数
     * @return 委托结果
     */
    Result<Map<String, Object>> delegateReview(String teacherId, DelegateReviewDTO delegateDTO);
    
    /**
     * 获取论文原文内容
     * @param teacherId 教师ID
     * @param paperId 论文ID
     * @return 论文内容信息
     */
    Result<PaperContentDTO> getPaperContent(String teacherId, String paperId);
    
    /**
     * 获取论文预览URL
     * @param teacherId 教师ID
     * @param paperId 论文ID
     * @return 预览URL信息
     */
    Result<PaperPreviewUrlDTO> getPaperPreviewUrl(String teacherId, String paperId);
    
    /**
     * 获取论文审核历史
     * @param teacherId 教师ID
     * @param paperId 论文ID
     * @return 审核历史信息
     */
    Result<PaperReviewHistoryDTO> getPaperReviewHistory(String teacherId, String paperId);
    
    /**
     * 获取教师审核历史统计
     * @param teacherId 教师ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param page 页码
     * @param pageSize 每页大小
     * @return 审核统计信息
     */
    Result<TeacherReviewStatisticsDTO> getTeacherReviewStatistics(String teacherId, String startDate, 
                                                                  String endDate, Integer page, Integer pageSize);
}
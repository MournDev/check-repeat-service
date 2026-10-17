package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.BatchReviewDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 教师控制台服务接口
 * 提供教师工作台所需的所有业务逻辑
 */
public interface TeacherDashboardService {

    /**
     * 获取教师仪表盘统计数据
     * @param teacherId 教师ID
     * @return 统计数据
     */
    Result<Map<String, Object>> getDashboardStats(Long teacherId);

    /**
     * 获取待审核论文列表
     * @param teacherId 教师ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 论文列表
     */
    Result<Object> getPendingPapers(Long teacherId, Integer pageNum, Integer pageSize);

    /**
     * 获取指导学生状态统计
     * @param teacherId 教师ID
     * @return 学生状态统计数据
     */
    Result<Map<String, Object>> getStudentStats(Long teacherId);

    /**
     * 批量审核论文
     * @param teacherId 教师ID
     * @param reviewDTO 审核参数
     * @return 操作结果
     */
    Result<String> batchReviewPapers(Long teacherId, BatchReviewDTO reviewDTO);

    /**
     * 审核论文
     * @param teacherId 教师ID
     * @param paperId 论文ID
     * @param reviewResult 审核结果
     * @param reviewComment 审核意见
     * @return 操作结果
     */
    Result<String> reviewPaper(Long teacherId, Long paperId, String reviewResult, String reviewComment);

    /**
     * 下载论文文件
     * @param teacherId 教师ID
     * @param paperId 论文ID
     * @return 文件下载链接
     */
    Result<String> downloadPaper(Long teacherId, Long paperId);

    /**
     * 获取审核进度统计
     * @param teacherId 教师ID
     * @return 统计图表数据
     */
    Result<Map<String, Object>> getReviewStatistics(Long teacherId);

    /**
     * 获取近期活动记录
     * @param teacherId 教师ID
     * @param page 页码
     * @param size 每页大小
     * @return 活动记录列表
     */
    Result<Object> getRecentActivities(Long teacherId, Integer page, Integer size);

    /**
     * 导出教师数据（S12：EasyExcel 流式写出 xlsx，GET 端点兼容入口）
     */
    void exportTeacherData(Long teacherId, String startDate, String endDate, jakarta.servlet.http.HttpServletResponse response);

    /**
     * 导出审核数据报表（S12：excel=EasyExcel xlsx / csv=UTF-8 CSV，其他格式明确拒绝）
     */
    void exportData(Long teacherId, String format, String startTime, String endTime, jakarta.servlet.http.HttpServletResponse response);

    /**
     * 获取指导学生列表
     * @param teacherId 教师ID
     * @param page 页码
     * @param size 每页大小
     * @return 学生列表
     */
    Result<Object> getStudentList(Long teacherId, Integer page, Integer size);

    /**
     * 刷新仪表盘实时数据
     * @param teacherId 教师ID
     * @return 刷新后的统计数据
     */
    Result<Map<String, Object>> refreshDashboard(Long teacherId);
}
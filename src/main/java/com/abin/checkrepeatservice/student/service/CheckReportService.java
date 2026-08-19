package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.vo.ReportDownloadReq;
import com.baomidou.mybatisplus.extension.service.IService;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 查重报告服务接口
 */
public interface CheckReportService extends IService<CheckReport> {
    /**
     * 1. 生成查重报告（由查重任务触发）
     * @param taskId 查重任务ID
     * @param checkRate 总重复率
     * @param repeatDetails 重复详情（JSON字符串）
     * @return 生成的报告实体
     */
    CheckReport generateReport(Long taskId, Double checkRate, String repeatDetails);

    /**
     * 2. 在线预览报告详情
     * @param reportId 报告ID
     * @return 报告预览DTO（含标红段落）
     */
    Result<ReportPreviewDTO> previewReport(Long reportId);

    /**
     * 3. 下载报告文件（PDF/HTML）
     * @param downloadReq 下载请求参数
     * @param response HTTP响应（用于输出文件流）
     */
    void downloadReport(ReportDownloadReq downloadReq, HttpServletResponse response);

    /**
     * 4. 查询当前用户的历史报告列表
     * @param paperId 论文ID（可选，筛选指定论文的报告）
     * @return 报告列表（含任务关联信息）
     */
    Result<List<CheckReport>> getMyReportList(Long paperId);

    /**
     * 5. 删除历史报告（仅学生本人或管理员可操作，且关联任务未审核）
     *
     * @param reportId 报告ID
     * @return 删除结果
     */
    Result<String> deleteReport(Long reportId);
}

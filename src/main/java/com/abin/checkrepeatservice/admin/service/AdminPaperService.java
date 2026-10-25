package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.OverdueRemindReq;
import com.abin.checkrepeatservice.admin.vo.OverduePaperPageVO;
import com.abin.checkrepeatservice.admin.vo.OverdueRemindResultVO;
import com.abin.checkrepeatservice.admin.vo.PaperExportExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * 管理员论文管理服务接口
 */
public interface AdminPaperService {

    /**
     * 获取论文列表（分页）
     */
    Result<Page<PaperInfo>> getPaperList(Integer page, Integer size, String paperStatus, 
                                       String paperType, String keyword, String startDate, String endDate,
                                       String majorName, String grade, String checkStatus, 
                                       Double minSimilarity, Double maxSimilarity);

    /**
     * 获取论文详情
     */
    Result<PaperInfo> getPaperDetail(Long paperId);

    /**
     * 审核论文（管理员代审）：返回 {reviewRecordId, paperStatus, auditSource}
     *
     * @param proxyReason 代审原因，服务端必填校验（≤200），落库到 review_record.proxy_reason
     */
    Result<Map<String, Object>> auditPaper(Long paperId, String auditResult, String auditComment, String proxyReason);

    /**
     * 批量审核论文（逐篇跳过并发冲突，返回 {total, success, skipped, skipReasons[], results[]} 明细）
     */
    Result<Map<String, Object>> batchAuditPapers(List<Long> paperIds, String auditResult,
                                                String auditComment, String proxyReason);

    /**
     * 删除论文
     */
    Result<String> deletePaper(Long paperId);

    /**
     * 获取论文统计信息
     */
    Result<Map<String, Object>> getPaperStatistics();

    /**
     * 导出论文列表（S12：EasyExcel 流式写出 xlsx；筛选口径见实现）
     */
    void exportPaperList(Map<String, Object> params, HttpServletResponse response);
    
    /**
     * 下载论文文件（流式输出真实文件字节）
     */
    void downloadPaper(Long paperId, HttpServletResponse response);

    /**
     * 获取管理员预览论文原文的地址（direct / kkfileview）
     */
    Result<Map<String, String>> getPaperPreviewUrl(Long paperId);
    
    /**
     * 校内查重检测
     */
    Result<String> schoolInternalCheckPaper(Long paperId);
    
    /**
     * 批量校内查重检测
     */
    Result<String> batchSchoolInternalCheckPaper(List<Long> paperIds);
    
    /**
     * 批量第三方查重检测
     */
    Result<String> batchThirdPartyCheckPaper(List<Long> paperIds);

    /**
     * Wave 3：超期论文分页列表（含全量分桶计数）。
     */
    Result<OverduePaperPageVO> getOverduePapers(String bucket, Integer page, Integer size, String keyword);

    /**
     * Wave 3：超期论文一键催办（逐篇结果 + paper_reminder_log 留痕）。
     */
    Result<OverdueRemindResultVO> remindOverduePapers(OverdueRemindReq req);

    /**
     * Wave 3：论文生命周期漏斗（按到达过阶段统计）。
     */
    Result<Map<String, Object>> getLifecycleFunnel(String semester);

    /**
     * Wave 3：生成论文清单导出行（学期包 ZIP 复用，按 submit_time 过滤）。
     */
    List<PaperExportExcelVO> buildPaperExportRows(String semester, String startDate, String endDate);
}
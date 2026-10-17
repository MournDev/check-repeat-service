package com.abin.checkrepeatservice.admin.service;

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
     * 审核论文
     */
    Result<String> auditPaper(Long paperId, String auditResult, String auditComment);

    /**
     * 批量审核论文
     */
    Result<String> batchAuditPapers(List<Long> paperIds, String auditResult, String auditComment);

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
     * 下载论文文件
     */
    Result<String> downloadPaper(Long paperId);
    
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
}
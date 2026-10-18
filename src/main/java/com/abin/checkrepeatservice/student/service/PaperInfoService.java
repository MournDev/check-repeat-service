package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.vo.PaperQueryRequest;
import com.abin.checkrepeatservice.student.dto.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;


public interface PaperInfoService extends IService<PaperInfo> {

    /**
     * 分页查询学生论文列表接口
     * @param request
     * @return
     */
    Page<PaperInfo> getStudentPaperPage(PaperQueryRequest request);

    /**
     * 删除论文接口
     * @param paperId 论文ID
     * @return 删除结果
     */
    boolean deletePaper(Long paperId, Long studentId);

    /**
     * 通过文件ID提交论文接口
     * @param subjectCode 学科编码
     * @param paperTitle 论文标题
     * @param paperAbstract 论文摘要
     * @param collegeId 院ID
     * @param majorId 专业ID
     * @param paperType 论文类型
     * @param fileId 文件ID
     * @param fileMd5 文件MD5
     * @param studentId 学生ID
     * @return 论文信息
     */
    PaperInfo submitPaperByFileId(String subjectCode, String paperTitle, String paperAbstract,
                                  Long collegeId, Long majorId, String paperType,
                                  Long fileId, String fileMd5, Long studentId);
    
    /**
     * 完整的论文提交流程（包含文件上传和信息录入）
     * @param multipartFile 论文文件
     * @param subjectCode 学科编码
     * @param paperTitle 论文标题
     * @param paperAbstract 论文摘要
     * @param collegeId 学院ID
     * @param majorId 专业ID
     * @param paperType 论文类型
     * @param studentId 学生ID
     * @return 论文信息
     */
    PaperInfo submitPaper(MultipartFile multipartFile, String subjectCode, String paperTitle, 
                         String paperAbstract, Long collegeId, Long majorId, String paperType, 
                         Long studentId);
    /**
     * 获取论文状态标签接口
     * @param statusValue 状态值
     * @return 状态标签
     */
    String getPaperStatusLabel(String statusValue);
    
    /**
     * 论文撤回接口
     * @param paperId 论文ID
     * @param studentId 学生ID
     * @param reason 撤回原因
     * @return 撤回结果
     */
    boolean withdrawPaper(Long paperId, Long studentId, String reason);
    
    /**
     * 申请修改已通过论文接口
     * @param paperId 论文 ID
     * @param studentId 学生 ID
     * @param reason 修改原因
     * @return 申请结果
     */
    boolean requestPaperModification(Long paperId, Long studentId, String reason);
        
    /**
     * 撤回后重新提交论文
     * @param paperId 论文 ID
     * @param request 重新提交请求
     * @param studentId 学生 ID
     * @return 更新后的论文信息
     */
    PaperInfo resubmitAfterWithdraw(Long paperId, PaperReSubmitAfterWithdrawRequest request, Long studentId);
        
    /**
     * 批量下载论文接口
     * @param paperIds 论文ID列表
     * @param studentId 学生ID
     * @param response HTTP响应
     */
    void batchDownloadPapers(List<Long> paperIds, Long studentId, HttpServletResponse response);
    
    /**
     * 批量删除论文接口
     * @param paperIds 论文ID列表
     * @param studentId 学生ID
     * @return 删除结果
     */
    Map<String, Object> batchDeletePapers(List<Long> paperIds, Long studentId);
    
    /**
     * 获取论文版本详情接口
     * @param paperId 论文ID
     * @param versionId 版本ID
     * @param studentId 学生ID
     * @return 版本详情
     */
    PaperVersionDTO getPaperVersion(Long paperId, Long versionId, Long studentId);

    /**
     * 获取论文版本历史列表接口
     * @param paperId 论文ID
     * @param studentId 学生ID（归属校验）
     * @return 版本列表（按提交版本号升序）
     */
    List<PaperVersionItemDTO> getPaperVersions(Long paperId, Long studentId);
    
    /**
     * 版本对比接口
     * @param paperId 论文ID
     * @param versionIds 版本ID列表
     * @param studentId 学生ID
     * @return 对比结果
     */
    VersionCompareResult comparePaperVersions(Long paperId, List<Long> versionIds, Long studentId);
    
    /**
     * 下载版本对比报告接口
     * @param paperId 论文ID
     * @param versionIds 版本ID列表
     * @param studentId 学生ID
     * @param response HTTP响应
     */
    void downloadVersionCompareReport(Long paperId, List<Long> versionIds, Long studentId, HttpServletResponse response);
    
    /**
     * 下载论文版本接口
     * @param versionId 版本ID
     * @param studentId 学生ID
     * @param response HTTP响应
     */
    void downloadPaperVersion(Long versionId, Long studentId, HttpServletResponse response);
    
    /**
     * 下载论文接口
     * @param paperId 论文ID
     * @param studentId 学生ID
     * @param response HTTP响应
     */
    void downloadPaper(Long paperId, Long studentId, HttpServletResponse response);
    
    /**
     * 下载附件接口
     * @param attachmentId 附件ID
     * @param studentId 学生ID
     * @param response HTTP响应
     */
    void downloadAttachment(String attachmentId, Long studentId, HttpServletResponse response);
    
    /**
     * 获取论文查重历史记录
     * @param paperId 论文ID
     * @param studentId 学生ID
     * @return 查重历史响应DTO
     */
    CheckHistoryResponseDTO getCheckHistory(Long paperId, Long studentId);
    
    /**
     * 获取相似度趋势数据
     * @param paperId 论文ID
     * @param studentId 学生ID
     * @param period 时间周期（天数）
     * @return 相似度趋势DTO
     */
    SimilarityTrendDTO getSimilarityTrend(Long paperId, Long studentId, Integer period);
    
    /**
     * 版本对比分析
     * @param paperId 论文ID
     * @param studentId 学生ID
     * @param request 对比请求DTO
     * @return 版本对比响应DTO
     */
    VersionCompareResponseDTO compareVersions(Long paperId, Long studentId, VersionCompareRequestDTO request);
    
    /**
     * 获取论文统计分析数据
     * @param paperId 论文ID
     * @param studentId 学生ID
     * @return 统计分析DTO
     */
    StatisticsDTO getPaperStatistics(Long paperId, Long studentId);
}
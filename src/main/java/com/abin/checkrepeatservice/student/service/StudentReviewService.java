package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.vo.PaperReSubmitReq;
import com.abin.checkrepeatservice.student.dto.StudentReviewDetailDTO;
import com.abin.checkrepeatservice.student.vo.StudentReviewQueryReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 学生端审核结果服务接口
 */
public interface StudentReviewService extends IService<PaperInfo> {
    /**
     * 1. 学生查询自己的论文审核结果列表（分页）
     * @param queryReq 查询参数（论文状态、页码、页大小）
     * @return 分页后的审核结果列表
     */
    Result<Page<StudentReviewDetailDTO>> getMyReviewList(StudentReviewQueryReq queryReq);

    /**
     * 2. 学生查询单篇论文的审核详情
     * @param paperId 论文ID（通过@RequestParam传参）
     * @return 审核详情DTO（含查重结果、审核意见、附件）
     */
    Result<StudentReviewDetailDTO> getReviewDetail(@RequestParam("paperId") Long paperId);

    /**
     * 3. 学生下载审核附件
     * @param attachPath 附件存储路径（通过@RequestParam传参）
     * @param response HTTP响应（输出文件流）
     */
    void downloadReviewAttach(@RequestParam("attachPath") String attachPath, HttpServletResponse response);

    /**
     * 4. 学生重新提交修改后的论文（审核不通过后）
     * @param reSubmitReq 重新提交参数（原论文ID、修改后文件、修改说明）
     * @return 重新提交结果（新论文ID、当前状态）
     */
    Result<Map<String, Object>> reSubmitPaper(PaperReSubmitReq reSubmitReq);

    /**
     * 5. 学生查询重新提交记录（按原论文ID）
     * @param originalPaperId 原论文ID（通过@RequestParam传参）
     * @return 重新提交记录列表（分页）
     */
    Result<Page<PaperInfo>> getResubmitRecord(@RequestParam("originalPaperId") Long originalPaperId,
                                                  @RequestParam("currentPage") Integer currentPage,
                                                  @RequestParam("pageSize") Integer pageSize);
}

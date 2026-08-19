package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.ReviewRecord;
import com.abin.checkrepeatservice.teacher.dto.ReviewOperateReq;
import com.abin.checkrepeatservice.teacher.dto.ReviewQueryReq;
import com.abin.checkrepeatservice.teacher.dto.ReviewResultDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 教师审核服务接口
 */
public interface TeacherReviewService extends IService<ReviewRecord> {
    /**
     * 1. 教师查询待审核论文列表（分页）
     * @param queryReq 查询参数（学生姓名、论文标题、页码、页大小）
     * @return 分页后的待审核论文列表
     */
    Result<Page<ReviewResultDTO>> getPendingReviewList(ReviewQueryReq queryReq);

    /**
     * 2. 教师执行单篇/批量审核
     * @param operateReq 审核操作参数（论文ID列表、审核状态、意见、附件）
     * @return 审核结果（成功数量、失败数量、失败原因）
     */
    Result<Map<String, Object>> doReview(ReviewOperateReq operateReq);

    /**
     * 3. 教师查询已审核论文列表（分页）
     * @param queryReq 查询参数
     * @return 分页后的已审核论文列表
     */
    Result<Page<ReviewResultDTO>> getReviewedList(ReviewQueryReq queryReq);

    /**
     * 4. 教师查询单篇论文的审核详情
     * @param paperId 论文ID（通过@RequestParam传参）
     * @return 审核详情DTO
     */
    Result<ReviewResultDTO> getReviewDetail(@RequestParam("paperId") Long paperId);

    /**
     * 5. 教师下载审核附件
     * @param attachPath 附件存储路径（通过@RequestParam传参）
     * @param response HTTP响应（输出文件流）
     */
    void downloadReviewAttach(@RequestParam("attachPath") String attachPath, HttpServletResponse response);

    /**
     * 6. 重新审核（仅审核不通过的论文可重新发起）
     *
     * @param paperId 论文ID（通过@RequestParam传参）
     * @return 重新审核发起结果
     */
    Result<String> reInitiateReview(@RequestParam("paperId") Long paperId);
}

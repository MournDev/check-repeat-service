package com.abin.checkrepeatservice.teacher.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 审核操作请求DTO：支持单篇/批量审核
 */
@Data
public class ReviewOperateReq {
    /**
     * 论文ID列表（支持批量，至少1个）
     */
    @NotEmpty(message = "请选择待审核的论文")
    @Size(max = 20, message = "单次审核最多选择20篇论文")
    private List<Long> paperIds; // 前端传参：paperIds=1546278765432123459,1546278765432123460

    /**
     * 审核状态（3-审核通过，4-审核不通过）
     */
    @NotNull(message = "请指定审核结果（通过/不通过）")
    private Integer reviewStatus; // 前端传参：reviewStatus=3

    /**
     * 审核意见（富文本，可为空）
     */
    @Size(max = 2000, message = "审核意见长度不能超过 2000 字符")
    private String reviewOpinion; // 前端传参：reviewOpinion=xxx（富文本内容）
    
    /**
     * 建议修改点（JSON 格式，可选）
     * 示例：[{"type":"format","desc":"格式不规范"},{"type":"content","desc":"第三章内容不够充实"}]
     */
    @Size(max = 1000, message = "建议修改点数量不能超过 10 条")
    private String suggestedModifications; // 前端传参：JSON 字符串

    /**
     * 本次审核使用的意见模板ID（可选）：提交成功后在同一事务内累加模板使用次数
     */
    private Long templateId;

    /**
     * 审核附件（可选，如修改建议文档）
     */
    private MultipartFile reviewAttach; // 前端传参：reviewAttach=文件流
}

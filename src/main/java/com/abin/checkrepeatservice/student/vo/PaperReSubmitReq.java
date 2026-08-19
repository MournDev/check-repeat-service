package com.abin.checkrepeatservice.student.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 论文重新提交请求DTO（审核不通过后修改提交）
 */
@Data
public class PaperReSubmitReq {
    /**
     * 原论文ID（必传，审核不通过的论文）
     */
    @NotNull(message = "原论文ID不能为空")
    private Long originalPaperId; // 前端传参：originalPaperId=1546278765432123459

    /**
     * 修改后的论文文件（必传）
     */
    private MultipartFile revisedFile; // 前端传参：revisedFile=文件流

    /**
     * 修改说明（可选，说明针对审核意见的修改内容）
     */
    @Size(max = 1000, message = "修改说明长度不能超过1000字符")
    private String revisionDesc; // 前端传参：revisionDesc=已修改摘要重复部分...
}

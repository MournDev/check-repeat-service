package com.abin.checkrepeatservice.student.vo;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 论文提交请求实体：封装论文上传参数
 */
@Data
public class PaperReq {
    /**
     * 论文标题
     */
    @NotBlank(message = "论文标题不能为空")
    @Size(max = 200, message = "论文标题长度不能超过200字符")
    private String paperTitle;

    /**
     * 学生ID（上传时自动获取）
     */
    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    /**
     * 论文摘要（可选）
     */
    @Size(max = 500, message = "论文摘要长度不能超过500字符")
    private String paperAbstract;
}

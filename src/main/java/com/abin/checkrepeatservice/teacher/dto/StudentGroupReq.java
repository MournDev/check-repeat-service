package com.abin.checkrepeatservice.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生分组创建/编辑请求（字段上限与前端表单校验规则一致）
 */
@Data
public class StudentGroupReq {

    /**
     * 分组名称
     */
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 50, message = "名称不能超过50个字符")
    private String name;

    /**
     * 分组描述
     */
    @Size(max = 200, message = "描述不能超过200个字符")
    private String description;
}

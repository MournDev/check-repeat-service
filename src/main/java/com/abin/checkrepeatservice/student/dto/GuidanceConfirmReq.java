package com.abin.checkrepeatservice.student.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生确认指导记录请求（论文全生命周期 P1）。
 */
@Data
public class GuidanceConfirmReq {

    /**
     * 学生反馈（可选）
     */
    @Size(max = 1000, message = "学生反馈长度不能超过1000字符")
    private String feedback;
}

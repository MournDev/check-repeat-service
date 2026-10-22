package com.abin.checkrepeatservice.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 撤回后重新提交论文请求 DTO
 */
@Data
public class PaperReSubmitAfterWithdrawRequest {
    
    /**
     * 文件 ID
     */
    @NotNull(message = "文件 ID 不能为空")
    private Long fileId;
    
    /**
     * 文件 MD5
     */
    @NotBlank(message = "文件 MD5 不能为空")
    private String fileMd5;
    
    /**
     * 论文标题
     */
    @NotBlank(message = "论文标题不能为空")
    private String paperTitle;
    
    /**
     * 论文摘要
     */
    @NotBlank(message = "论文摘要不能为空")
    private String paperAbstract;
    
    /**
     * 科目代码（可选）
     */
    private String subjectCode;

    /**
     * 学位类型（可选，不传则保留原论文的学位类型）：
     * undergraduate-本科 / master-硕士 / doctor-博士
     */
    @Pattern(regexp = "^(undergraduate|master|doctor)$", message = "学位类型只能是 undergraduate/master/doctor")
    private String degreeType;
}

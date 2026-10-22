package com.abin.checkrepeatservice.student.vo;

import com.abin.checkrepeatservice.common.annotation.ValidPaperType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;


@Data
public class PaperSubmitRequest {

    @NotBlank(message = "学科领域不能为空")
    private String subjectCode;

    @NotBlank(message = "论文标题不能为空")
    private String paperTitle;

    @NotNull(message = "学院不能为空")
    private Long collegeId;

    @NotNull(message = "专业不能为空")
    private Long majorId;

    @NotBlank(message = "论文类型不能为空")
    @ValidPaperType(message = "论文类型不合法")
    private String paperType;

    @NotBlank(message = "论文摘要不能为空")
    private String paperAbstract;
        
    @NotNull(message = "文件 ID 不能为空")
    private Long fileId; // 文件 ID
        
    @NotBlank(message = "文件 MD5 不能为空")
    private String fileMd5;

    /**
     * 学位类型（可选，缺省 undergraduate）：undergraduate-本科 / master-硕士 / doctor-博士。
     * 查重时 CheckPolicyResolver 按该值命中检测策略阈值。
     */
    @Pattern(regexp = "^(undergraduate|master|doctor)$", message = "学位类型只能是 undergraduate/master/doctor")
    private String degreeType;

}

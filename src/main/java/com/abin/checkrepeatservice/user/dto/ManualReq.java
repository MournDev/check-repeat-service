package com.abin.checkrepeatservice.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManualReq {

    /** 论文ID */
    @NotNull(message = "论文ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /** 指导老师ID */
    @NotNull(message = "指导老师ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long teacherId;

    /** 手动分配原因 */
    @NotBlank(message = "手动分配原因不能为空")
    private String reason;
}

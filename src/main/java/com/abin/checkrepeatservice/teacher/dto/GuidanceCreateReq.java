package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师发起指导记录请求（论文全生命周期 P1）。
 */
@Data
public class GuidanceCreateReq {

    /**
     * 论文ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @NotNull(message = "论文ID不能为空")
    @Positive(message = "论文ID必须为正整数")
    private Long paperId;

    /**
     * 指导方式：online-线上 / offline-线下
     */
    @NotBlank(message = "指导方式不能为空")
    @Pattern(regexp = "online|offline", message = "指导方式仅支持 online / offline")
    private String guidanceType;

    /**
     * 指导时间
     */
    @NotNull(message = "指导时间不能为空")
    private LocalDateTime guidanceTime;

    /**
     * 指导内容（纯文本，服务端会再做XSS清洗）
     */
    @NotBlank(message = "指导内容不能为空")
    @Size(max = 2000, message = "指导内容长度不能超过2000字符")
    private String content;

    /**
     * 附件ID（关联file_info.id，可选）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Positive(message = "附件ID必须为正整数")
    private Long attachmentId;
}

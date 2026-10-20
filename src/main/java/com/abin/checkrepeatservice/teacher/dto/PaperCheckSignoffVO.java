package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查重报告导师签核记录（教师端 GET /api/teacher/papers/{paperId}/check-signoff）。
 */
@Data
public class PaperCheckSignoffVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reportId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long advisorId;

    /** 签核导师姓名 */
    private String advisorName;

    /** 1-合格放行 / 0-不合格退回修改 */
    private Integer qualified;

    /** 指导评分（0-100） */
    private BigDecimal guidanceScore;

    /** 签核意见 */
    private String comment;

    /** 签核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signTime;
}

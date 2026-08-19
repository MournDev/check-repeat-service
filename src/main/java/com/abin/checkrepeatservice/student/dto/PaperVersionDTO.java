package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 论文版本信息DTO
 */
@Data
public class PaperVersionDTO {
    private Long id;
    private Long paperId;
    private Integer version;
    private LocalDateTime submitTime;
    private Boolean isCurrent;
    private BigDecimal similarityRate;
    private Integer wordCount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long fileId;
    private String changes; // 主要修改内容
}
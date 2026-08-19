package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// 导师信息DTO
@Data
public class AdvisorInfoDTO {
    private Long id;
    private String name;
    private String title;
    private String phone;
    private String email;
    private String office;
    private String avatar;
    private String researchField;
    private List<String> expertise;

    // 导师统计
    private Integer guidedPapersCount;
    private BigDecimal approvalRate;
    private BigDecimal averageScore;
    private String onlineStatus;
}

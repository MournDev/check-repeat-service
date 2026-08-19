package com.abin.checkrepeatservice.teacher.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollegeDistributionVO {
    private String collegeName;
    private Integer studentCount;
    private Integer reviewCount;
    private BigDecimal avgSimilarity;
}

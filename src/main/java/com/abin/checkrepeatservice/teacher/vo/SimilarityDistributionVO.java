package com.abin.checkrepeatservice.teacher.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimilarityDistributionVO {
    private String range;
    private Integer paperCount;
    private BigDecimal percentage;
}

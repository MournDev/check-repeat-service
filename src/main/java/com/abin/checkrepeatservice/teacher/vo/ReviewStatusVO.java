package com.abin.checkrepeatservice.teacher.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStatusVO {
    private String status;
    private String statusName;
    private Integer count;
    private BigDecimal percentage;
    private String color;
}

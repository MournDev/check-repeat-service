package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.util.List;

/**
 * 专业对比DTO
 */
@Data
public class MajorComparisonDTO {
    private List<String> dimensions;     // 维度名称 ["论文质量", "创新性", "规范性"]
    private List<Integer> myLevel;       // 我的水平得分 [85, 78, 92]
    private List<Integer> majorAverage;  // 专业平均分 [75, 70, 80]
}
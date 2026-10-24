package com.abin.checkrepeatservice.admin.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 超期论文分页结果（Wave 3）：records + total + 全量分桶计数。
 */
@Data
public class OverduePaperPageVO {

    private List<OverduePaperVO> records;

    private Long total;

    /** 全量分桶计数，key: 1（1-2天）/ 3（3-6天）/ 7（≥7天） */
    private Map<String, Long> bucketCounts;
}

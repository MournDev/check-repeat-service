package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 全校查重结果分布导出 Excel 行 VO（论文全生命周期 P5，EasyExcel）。
 * 管理端 GET /api/admin/stat-report/export 的第 1 个 sheet。
 */
@Data
@ColumnWidth(18)
public class SimilarityDistributionExcelVO {

    @ExcelProperty("重复率区间(%)")
    @ColumnWidth(16)
    private String bucket;

    @ExcelProperty("查重结果数")
    @ColumnWidth(14)
    private Long count;
}

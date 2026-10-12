package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 按学院统计导出 Excel 行 VO（论文全生命周期 P5，EasyExcel）。
 * 管理端 GET /api/admin/stat-report/export 的第 2 个 sheet。
 */
@Data
@ColumnWidth(18)
public class CollegeCompareExcelVO {

    @ExcelProperty("学院")
    @ColumnWidth(28)
    private String collegeName;

    @ExcelProperty("平均相似度(%)")
    @ColumnWidth(16)
    private BigDecimal avgRate;

    @ExcelProperty("论文数")
    @ColumnWidth(10)
    private Long total;
}

package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 查重月度趋势导出 Excel 行 VO（论文全生命周期 P5，EasyExcel）。
 * 管理端 GET /api/admin/stat-report/export 的第 3 个 sheet。
 */
@Data
@ColumnWidth(18)
public class MonthlyTrendExcelVO {

    @ExcelProperty("月份")
    @ColumnWidth(12)
    private String ym;

    @ExcelProperty("查重次数")
    @ColumnWidth(12)
    private Long count;

    @ExcelProperty("平均重复率(%)")
    @ColumnWidth(16)
    private BigDecimal avgRate;
}

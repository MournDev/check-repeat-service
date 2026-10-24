package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * Wave 3：审核记录导出 Excel 行。
 */
@Data
public class ReviewExportExcelVO {

    @ExcelProperty("论文标题")
    @ColumnWidth(30)
    private String paperTitle;

    @ExcelProperty("学生姓名")
    @ColumnWidth(12)
    private String studentName;

    @ExcelProperty("学号")
    @ColumnWidth(14)
    private String studentNo;

    @ExcelProperty("导师")
    @ColumnWidth(12)
    private String advisorName;

    @ExcelProperty("审核结果")
    @ColumnWidth(12)
    private String reviewResult;

    @ExcelProperty("审核意见")
    @ColumnWidth(40)
    private String reviewOpinion;

    @ExcelProperty("审核来源")
    @ColumnWidth(14)
    private String auditSource;

    @ExcelProperty("代审原因")
    @ColumnWidth(24)
    private String proxyReason;

    @ExcelProperty("出结论人")
    @ColumnWidth(12)
    private String auditorName;

    @ExcelProperty("审核时间")
    @ColumnWidth(20)
    private String reviewTime;
}

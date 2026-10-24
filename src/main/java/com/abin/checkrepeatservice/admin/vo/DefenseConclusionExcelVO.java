package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * Wave 3：答辩结论导出 Excel 行（全校，按学期范围）。
 */
@Data
public class DefenseConclusionExcelVO {

    @ExcelProperty("批次编号")
    @ColumnWidth(20)
    private String batchNo;

    @ExcelProperty("批次类型")
    @ColumnWidth(12)
    private String batchType;

    @ExcelProperty("答辩时间")
    @ColumnWidth(20)
    private String defenseDate;

    @ExcelProperty("学生")
    @ColumnWidth(12)
    private String studentName;

    @ExcelProperty("论文")
    @ColumnWidth(30)
    private String paperTitle;

    @ExcelProperty("结论")
    @ColumnWidth(10)
    private String result;

    @ExcelProperty("成绩")
    @ColumnWidth(10)
    private String score;

    @ExcelProperty("等级")
    @ColumnWidth(10)
    private String grade;

    @ExcelProperty("组长")
    @ColumnWidth(12)
    private String leaderName;
}

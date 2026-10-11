package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 答辩记录导出 Excel 行 VO（论文全生命周期 P3，EasyExcel）。
 *
 * 管理端 GET /api/admin/defense/batch/{id}/records/export 使用；
 * 列顺序即导出列顺序，提交时间在服务层已格式化为字符串，避免 Excel 内再适配时区。
 */
@Data
@ColumnWidth(18)
public class DefenseRecordExcelVO {

    @ExcelProperty("顺序号")
    @ColumnWidth(8)
    private Integer defenseOrder;

    @ExcelProperty("论文标题")
    @ColumnWidth(42)
    private String paperTitle;

    @ExcelProperty("学生姓名")
    private String studentName;

    @ExcelProperty("学号")
    private String studentUsername;

    @ExcelProperty("得分")
    @ColumnWidth(8)
    private BigDecimal score;

    @ExcelProperty("等级")
    @ColumnWidth(8)
    private String grade;

    @ExcelProperty("结论")
    private String resultDesc;

    @ExcelProperty("答辩记录")
    @ColumnWidth(50)
    private String comment;

    @ExcelProperty("结论提交人")
    private String submitByName;

    @ExcelProperty("提交时间")
    private String submitTime;
}

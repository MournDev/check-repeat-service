package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 管理端导师评价导出 Excel 行（论文全生命周期 P6a）。
 * 实名导出（含学生姓名），标签已转中文名顿号拼接。
 */
@Data
public class AdvisorEvalExcelVO {

    @ExcelProperty("论文标题")
    @ColumnWidth(30)
    private String paperTitle;

    @ExcelProperty("学生姓名")
    @ColumnWidth(12)
    private String studentName;

    @ExcelProperty("导师姓名")
    @ColumnWidth(12)
    private String teacherName;

    @ExcelProperty("学院")
    @ColumnWidth(18)
    private String collegeName;

    @ExcelProperty("指导及时性")
    @ColumnWidth(12)
    private Integer scoreTimeliness;

    @ExcelProperty("反馈质量")
    @ColumnWidth(10)
    private Integer scoreQuality;

    @ExcelProperty("责任心态度")
    @ColumnWidth(12)
    private Integer scoreAttitude;

    @ExcelProperty("专业能力")
    @ColumnWidth(10)
    private Integer scoreProfessional;

    @ExcelProperty("总体满意度")
    @ColumnWidth(12)
    private Integer scoreOverall;

    @ExcelProperty("标签")
    @ColumnWidth(24)
    private String tags;

    @ExcelProperty("评语")
    @ColumnWidth(40)
    private String comment;

    @ExcelProperty("提交时间")
    @ColumnWidth(20)
    private String createTime;
}

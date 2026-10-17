package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 论文导出 Excel 行（S12）：管理员「论文列表导出」与教师「数据导出」共用列结构。
 * CSV 导出（教师端）的表头顺序需与本类 @ExcelProperty 顺序保持一致。
 */
@Data
public class PaperExportExcelVO {

    @ExcelProperty("论文标题")
    @ColumnWidth(30)
    private String paperTitle;

    @ExcelProperty("作者")
    @ColumnWidth(12)
    private String studentName;

    @ExcelProperty("学号")
    @ColumnWidth(14)
    private String studentUsername;

    @ExcelProperty("专业")
    @ColumnWidth(16)
    private String studentMajor;

    @ExcelProperty("年级")
    @ColumnWidth(10)
    private String studentGrade;

    @ExcelProperty("指导教师")
    @ColumnWidth(12)
    private String teacherName;

    @ExcelProperty("论文类型")
    @ColumnWidth(12)
    private String paperType;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String paperStatus;

    @ExcelProperty("相似度(%)")
    @ColumnWidth(10)
    private String similarityRate;

    @ExcelProperty("查重结果")
    @ColumnWidth(24)
    private String checkResult;

    @ExcelProperty("审核意见")
    @ColumnWidth(24)
    private String auditComment;

    @ExcelProperty("提交时间")
    @ColumnWidth(20)
    private String submitTime;
}

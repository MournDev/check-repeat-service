package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分配记录Excel导出VO
 */
@Data
public class AssignmentRecordExcelVO {

    @ExcelProperty("记录ID")
    private String id;

    @ExcelProperty("学生姓名")
    private String studentName;

    @ExcelProperty("学号")
    private String studentId;

    @ExcelProperty("专业")
    private String major;

    @ExcelProperty("年级")
    private String grade;

    @ExcelProperty("指导老师")
    private String teacherName;

    @ExcelProperty("教师职称")
    private String teacherTitle;

    @ExcelProperty("所在院系")
    private String department;

    @ExcelProperty("分配类型")
    private String assignmentType;

    @ExcelProperty("分配时间")
    private LocalDateTime assignTime;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("操作人")
    private String operator;

    @ExcelProperty("操作时间")
    private LocalDateTime operateTime;

    @ExcelProperty("分配原因")
    private String reason;
}
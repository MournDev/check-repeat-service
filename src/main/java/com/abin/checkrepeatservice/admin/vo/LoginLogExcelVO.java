package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志Excel导出VO
 */
@Data
public class LoginLogExcelVO {

    @ExcelProperty(value = "ID", index = 0)
    @ColumnWidth(15)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Long id;

    @ExcelProperty(value = "用户名", index = 1)
    @ColumnWidth(20)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.LEFT)
    private String username;

    @ExcelProperty(value = "登录IP", index = 2)
    @ColumnWidth(20)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String loginIp;

    @ExcelProperty(value = "登录地点", index = 3)
    @ColumnWidth(25)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.LEFT)
    private String loginLocation;

    @ExcelProperty(value = "登录设备", index = 4)
    @ColumnWidth(20)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.LEFT)
    private String loginDevice;

    @ExcelProperty(value = "登录结果", index = 5)
    @ColumnWidth(15)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String loginResult;

    @ExcelProperty(value = "失败原因", index = 6)
    @ColumnWidth(30)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.LEFT)
    private String failReason;

    @ExcelProperty(value = "登录时间", index = 7)
    @ColumnWidth(25)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private LocalDateTime loginTime;
}


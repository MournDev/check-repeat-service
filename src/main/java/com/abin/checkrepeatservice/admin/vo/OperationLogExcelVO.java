package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志Excel导出VO
 */
@Data
public class OperationLogExcelVO {

    @ExcelProperty(value = "ID", index = 0)
    @ColumnWidth(15)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Long id;

    @ExcelProperty(value = "操作人", index = 1)
    @ColumnWidth(20)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.LEFT)
    private String username;

    @ExcelProperty(value = "操作类型", index = 2)
    @ColumnWidth(25)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.LEFT)
    private String operationType;

    @ExcelProperty(value = "操作描述", index = 3)
    @ColumnWidth(50)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.LEFT)
    private String description;

    @ExcelProperty(value = "IP地址", index = 4)
    @ColumnWidth(20)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String ipAddress;

    @ExcelProperty(value = "操作时间", index = 5)
    @ColumnWidth(25)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private LocalDateTime operationTime;
}

package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

/**
 * 提交趋势统计 Excel 导出 VO：映射提交趋势统计的 Excel 列
 */
@Data
public class SubmitTrendExcelVO {

    /**
     * 时间维度（列1：按日/周/月，如“2024-09-01”“2024-09-02 至 2024-09-08”“2024-09”）
     */
    @ExcelProperty(value = "时间维度", index = 0) // 表头名称+列顺序（第1列）
    @ColumnWidth(25) // 列宽25（适配长文本如周维度）
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER) // 居中对齐
    private String timeDimension;

    /**
     * 提交人数（列2）
     */
    @ExcelProperty(value = "提交人数", index = 1)
    @ColumnWidth(12)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer submitCount;

    /**
     * 未提交人数（列3）
     */
    @ExcelProperty(value = "未提交人数", index = 2)
    @ColumnWidth(12)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer unsubmitCount;

    /**
     * 提交率（列4：百分比格式，保留2位小数）
     */
    @ExcelProperty(value = "提交率", index = 3)
    @ColumnWidth(15)
    @NumberFormat("0.00%") // 格式化为百分比（如85.50%）
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Double submitRate;

    /**
     * 静态方法：创建汇总行 VO（最后一行显示“汇总”）
     * @param totalSubmit 总提交人数
     * @param totalUnsubmit 总未提交人数
     * @param totalRate 总提交率
     * @return 汇总行 VO
     */
    public static SubmitTrendExcelVO buildSummaryVO(Integer totalSubmit, Integer totalUnsubmit, Double totalRate) {
        SubmitTrendExcelVO summaryVO = new SubmitTrendExcelVO();
        summaryVO.setTimeDimension("汇总"); // 时间维度列显示“汇总”
        summaryVO.setSubmitCount(totalSubmit);
        summaryVO.setUnsubmitCount(totalUnsubmit);
        summaryVO.setSubmitRate(totalRate / 100); // 注意：Excel百分比格式需传入小数（如85.50%对应0.8550）
        return summaryVO;
    }
}
package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

/**
 * 查重结果分析 Excel 导出 VO：映射查重结果统计的 Excel 列（支持专业/年级分组）
 */
@Data
public class CheckResultExcelVO {

    /**
     * 分组名称（列1：按专业时为“专业名”，按年级时为“年级”）
     */
    @ExcelProperty(value = "分组名称", index = 0)
    @ColumnWidth(20)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String groupName;

    /**
     * 合格人数（列2：重复率≤阈值）
     */
    @ExcelProperty(value = "合格人数（重复率≤阈值）", index = 1)
    @ColumnWidth(18)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer qualifiedCount;

    /**
     * 不合格人数（列3：重复率>阈值）
     */
    @ExcelProperty(value = "不合格人数（重复率>阈值）", index = 2)
    @ColumnWidth(18)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer unqualifiedCount;

    /**
     * 总人数（列4：合格+不合格）
     */
    @ExcelProperty(value = "总人数", index = 3)
    @ColumnWidth(10)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer totalCount;

    /**
     * 平均重复率（列5：保留2位小数，百分比格式）
     */
    @ExcelProperty(value = "平均重复率", index = 4)
    @ColumnWidth(15)
    @NumberFormat("0.00%") // 格式化为百分比（如12.30%）
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Double avgRepeatRate;

    /**
     * 合格率（列6：保留2位小数，百分比格式）
     */
    @ExcelProperty(value = "合格率", index = 5)
    @ColumnWidth(12)
    @NumberFormat("0.00%")
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Double qualifiedRate;

    /**
     * 静态方法：创建分组行 VO（按专业/年级）
     * @param groupName 分组名称（如“计算机科学与技术”“2021”）
     * @param qualified 合格人数
     * @param unqualified 不合格人数
     * @param avgRate 平均重复率（原始值，如12.30→0.1230）
     * @return 分组行 VO
     */
    public static CheckResultExcelVO buildGroupVO(String groupName, Integer qualified, Integer unqualified, Double avgRate) {
        CheckResultExcelVO groupVO = new CheckResultExcelVO();
        groupVO.setGroupName(groupName);
        groupVO.setQualifiedCount(qualified);
        groupVO.setUnqualifiedCount(unqualified);
        groupVO.setTotalCount(qualified + unqualified);
        groupVO.setAvgRepeatRate(avgRate / 100); // 转换为小数（适配Excel百分比格式）
        groupVO.setQualifiedRate(qualified * 1.0 / (qualified + unqualified)); // 直接计算小数
        return groupVO;
    }

    /**
     * 静态方法：创建总体汇总行 VO
     * @param totalQualified 总合格人数
     * @param totalUnqualified 总不合格人数
     * @param totalAvgRate 总体平均重复率
     * @return 总体汇总行 VO
     */
    public static CheckResultExcelVO buildTotalSummaryVO(Integer totalQualified, Integer totalUnqualified, Double totalAvgRate) {
        CheckResultExcelVO summaryVO = new CheckResultExcelVO();
        summaryVO.setGroupName("总体统计");
        summaryVO.setQualifiedCount(totalQualified);
        summaryVO.setUnqualifiedCount(totalUnqualified);
        summaryVO.setTotalCount(totalQualified + totalUnqualified);
        summaryVO.setAvgRepeatRate(totalAvgRate / 100);
        summaryVO.setQualifiedRate(totalQualified * 1.0 / (totalQualified + totalUnqualified));
        return summaryVO;
    }
}

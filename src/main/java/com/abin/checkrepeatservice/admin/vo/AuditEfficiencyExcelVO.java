package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

/**
 * 教师审核效率 Excel 导出 VO：映射教师审核效率统计的 Excel 列
 */
@Data
public class AuditEfficiencyExcelVO {

    /**
     * 教师姓名（列1）
     */
    @ExcelProperty(value = "教师姓名", index = 0)
    @ColumnWidth(15)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String teacherName;

    /**
     * 待审核任务数（列2）
     */
    @ExcelProperty(value = "待审核任务数", index = 1)
    @ColumnWidth(15)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer pendingAuditCount;

    /**
     * 已审核任务数（列3）
     */
    @ExcelProperty(value = "已审核任务数", index = 2)
    @ColumnWidth(15)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer completedAuditCount;

    /**
     * 总任务数（列4：待审核+已审核）
     */
    @ExcelProperty(value = "总任务数", index = 3)
    @ColumnWidth(12)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer totalTaskCount;

    /**
     * 平均审核耗时（列5：分钟，保留1位小数）
     */
    @ExcelProperty(value = "平均审核耗时（分钟）", index = 4)
    @ColumnWidth(18)
    @NumberFormat("0.0") // 保留1位小数（如15.2分钟）
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Double avgAuditTime;

    /**
     * 静态方法：创建教师行 VO
     * @param teacherName 教师姓名
     * @param pending 待审核数
     * @param completed 已审核数
     * @param avgTime 平均耗时（分钟，原始值如15.2）
     * @return 教师行 VO
     */
    public static AuditEfficiencyExcelVO buildTeacherVO(String teacherName, Integer pending, Integer completed, Double avgTime) {
        AuditEfficiencyExcelVO teacherVO = new AuditEfficiencyExcelVO();
        teacherVO.setTeacherName(teacherName);
        teacherVO.setPendingAuditCount(pending);
        teacherVO.setCompletedAuditCount(completed);
        teacherVO.setTotalTaskCount(pending + completed);
        teacherVO.setAvgAuditTime(avgTime); // 直接传入小数（适配Excel保留1位格式）
        return teacherVO;
    }

    /**
     * 静态方法：创建全局汇总行 VO
     * @param totalPending 总待审核数
     * @param totalCompleted 总已审核数
     * @param totalTeacher 教师总数
     * @return 全局汇总行 VO
     */
    public static AuditEfficiencyExcelVO buildGlobalSummaryVO(Integer totalPending, Integer totalCompleted, Integer totalTeacher) {
        AuditEfficiencyExcelVO summaryVO = new AuditEfficiencyExcelVO();
        summaryVO.setTeacherName("全局汇总（共" + totalTeacher + "位教师）");
        summaryVO.setPendingAuditCount(totalPending);
        summaryVO.setCompletedAuditCount(totalCompleted);
        summaryVO.setTotalTaskCount(totalPending + totalCompleted);
        summaryVO.setAvgAuditTime(null); // 汇总行无需显示“平均耗时”（无意义）
        return summaryVO;
    }
}

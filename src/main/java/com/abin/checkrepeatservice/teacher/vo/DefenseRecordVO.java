package com.abin.checkrepeatservice.teacher.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 答辩记录 VO（论文全生命周期 P3）。
 *
 * 教师端工作台 / 管理端列表 / 学生端我的答辩 共用：
 *  - 教师端：组长视角可看到全部记录与评分录入入口；
 *  - 管理端：监控与导出列表；
 *  - 学生端：只返回学生本人的记录（含成绩与结论）。
 */
@Data
public class DefenseRecordVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long batchId;

    /**
     * 批次编号（便于跨批次检索）
     */
    private String batchNo;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 论文标题
     */
    private String paperTitle;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long studentId;

    /**
     * 学生姓名
     */
    private String studentName;

    /**
     * 学生学号
     */
    private String studentUsername;

    /**
     * 答辩顺序号
     */
    private Integer defenseOrder;

    /**
     * 综合得分
     */
    private BigDecimal score;

    /**
     * 等级：优/良/中/及格/不及格
     */
    private String grade;

    /**
     * 结论：pass-通过 / fail-不通过 / postpone-缓辩
     */
    private String result;

    private String resultDesc;

    /**
     * 答辩记录
     */
    private String comment;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long attachmentId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long submitBy;

    /**
     * 结论提交人姓名
     */
    private String submitByName;

    /**
     * 结论提交时间
     */
    private LocalDateTime submitTime;

    private LocalDateTime createTime;
}

package com.abin.checkrepeatservice.student.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生端「我的答辩安排」VO（论文全生命周期 P3）。
 *
 * 学生只看到本人被安排的答辩批次与个人记录，含批次会议信息（时间/地点/方式/组长/秘书）
 * 与本人结论（如已出）。
 */
@Data
public class StudentDefenseVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long batchId;

    /**
     * 批次编号
     */
    private String batchNo;

    /**
     * 批次类型：mid_term-中期答辩 / final-终期答辩
     */
    private String batchType;

    private String batchTypeDesc;

    /**
     * 答辩时间
     */
    private LocalDateTime defenseDate;

    /**
     * 地点
     */
    private String location;

    /**
     * 答辩方式：online-线上 / offline-线下
     */
    private String mode;

    private String modeDesc;

    /**
     * 答辩组长姓名
     */
    private String leaderName;

    /**
     * 答辩秘书姓名
     */
    private String secretaryName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long recordId;

    /**
     * 答辩顺序号
     */
    private Integer defenseOrder;

    /**
     * 综合得分（结论未提交时为 null）
     */
    private BigDecimal score;

    /**
     * 等级
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

    /**
     * 结论提交时间
     */
    private LocalDateTime submitTime;
}

package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答辩批次 VO（论文全生命周期 P3）。
 *
 * 管理端批次管理列表 / 教师端工作台 / 学生端我的答辩 共用：
 *  - 管理端返回全部字段；
 *  - 教师端工作台按本人是否在组成员中过滤；
 *  - 学生端只返回学生本人的批次（位置/时间/组长/秘书姓名等必要信息）。
 */
@Data
public class DefenseBatchVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 批次编号
     */
    private String batchNo;

    /**
     * 批次类型：mid_term-中期答辩 / final-终期答辩
     */
    private String batchType;

    private String batchTypeDesc;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long collegeId;

    /**
     * 学院名称（仅管理端返回；学生/教师端可空）
     */
    private String collegeName;

    /**
     * 答辩时间
     */
    private LocalDateTime defenseDate;

    /**
     * 地点：线下填教室、线上填会议链接
     */
    private String location;

    /**
     * 答辩方式：online-线上 / offline-线下
     */
    private String mode;

    private String modeDesc;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long leaderId;

    /**
     * 答辩组长姓名
     */
    private String leaderName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long secretaryId;

    /**
     * 答辩秘书姓名
     */
    private String secretaryName;

    /**
     * 批次状态：0-筹备中 / 1-进行中 / 2-已结束
     */
    private Integer status;

    private String statusDesc;

    private String remark;

    /**
     * 答辩组成员数（仅管理端返回）
     */
    private Integer memberCount;

    /**
     * 入册论文数（仅管理端返回）
     */
    private Integer paperCount;

    /**
     * 已提交结论数（仅管理端返回）
     */
    private Integer submittedCount;

    private LocalDateTime createTime;
}

package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生端「我的导师评价」视图对象（论文全生命周期 P6a）。
 *
 * 一次返回评价入口所需的全部状态：
 *  - evaluable=false 时 reason 给出不可评原因（未通过终期答辩/窗口已关闭/已提交），
 *    前端据此置灰入口；
 *  - submitted=true 时各 score 维度/tags/comment/submitTime 回显本人已提交内容；
 *  - tagOptions 为字典 advisor_eval_tag 的可选标签（label/value），免前端再查字典。
 */
@Data
public class AdvisorEvalStatusVO {

    /**
     * 论文ID（字符串防 JS 精度丢失；学生无论文时为 null）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 论文标题
     */
    private String paperTitle;

    /**
     * 指导教师姓名（realName 优先，空回落 username）
     */
    private String teacherName;

    /**
     * 当前是否可提交评价
     */
    private Boolean evaluable;

    /**
     * 不可评价原因（evaluable=false 时非空；null 表示可评）
     */
    private String reason;

    /**
     * 评价窗口截止时间（defense_passed 日志时间 + 窗口天数；存量无日志时为 null 表示不限）
     */
    private LocalDateTime windowEndTime;

    /**
     * 是否已提交评价
     */
    private Boolean submitted;

    // ---------- 已提交内容回显（submitted=true 时非空） ----------

    private Integer scoreTimeliness;

    private Integer scoreQuality;

    private Integer scoreAttitude;

    private Integer scoreProfessional;

    private Integer scoreOverall;

    /**
     * 已选标签（dict_value 列表）
     */
    private List<String> tags;

    private String comment;

    private LocalDateTime submitTime;

    /**
     * 可选标签项（字典 advisor_eval_tag）
     */
    private List<TagOption> tagOptions;

    /**
     * 标签可选项
     */
    @Data
    public static class TagOption {
        private String label;
        private String value;
    }
}

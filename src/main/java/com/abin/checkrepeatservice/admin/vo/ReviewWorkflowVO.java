package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.List;

/**
 * 审核工作流配置响应。
 *
 * 字段名与前端页面（views/teacher/ReviewWorkflow.vue）的表单结构一一对应，
 * 页面直接整体回填，不需要再做映射。
 */
@Data
public class ReviewWorkflowVO {

    /**
     * 工作流ID。序列化成字符串：19 位雪花ID超出 JS 的安全整数范围，
     * 直接给数字会在前端丢精度（与本项目其余实体的处理保持一致）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String name;

    private String description;

    /**
     * 审核步骤，数组顺序即审核顺序
     */
    private List<Step> steps;

    private Boolean enabled;

    /**
     * 单个审核步骤。同时用作 steps JSON 列的元素结构。
     */
    @Data
    public static class Step {

        /**
         * 步骤ID：由前端用 Date.now() 生成的临时值，
         * 只用于列表渲染的 key，没有业务含义，服务端不校验也不依赖它。
         */
        private Long id;

        private String name;

        /**
         * 审批人用户ID列表。保持字符串形态不是偷懒：雪花ID超出 JS 安全整数范围，
         * 前端的 el-select 也是拿字符串当 value 回显，存成数字会导致再次打开时匹配不上。
         */
        private List<String> approvers;

        /**
         * 审批方式：all-全部审批 / any-任意审批
         */
        private String approvalType;

        /**
         * 超时时间（天）
         */
        private Integer timeout;

    }

    /**
     * 审批人下拉候选项。
     */
    @Data
    public static class TeacherOption {

        /**
         * 教师用户ID，同样按字符串下发，理由见 Step.approvers
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;

        /**
         * 展示名。取 real_name，为空时回落到 username，避免下拉里出现空白项
         */
        private String name;

    }

}

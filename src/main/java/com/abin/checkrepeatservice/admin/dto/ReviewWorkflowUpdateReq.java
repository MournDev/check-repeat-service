package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 审核工作流配置更新请求。
 *
 * 刻意没有 id 字段：本表语义上是单行配置，PUT 提交的就是完整配置，
 * 由服务端定位到那一行做 upsert，客户端传什么 id 都不影响结果。
 * （前端表单里带着的 id 会被 Jackson 忽略——Spring Boot 默认关闭
 * FAIL_ON_UNKNOWN_PROPERTIES，不会因此报 400。）
 */
@Data
public class ReviewWorkflowUpdateReq {

    private String name;

    private String description;

    private List<Step> steps;

    private Boolean enabled;

    @Data
    public static class Step {

        private Long id;

        private String name;

        /**
         * 审批人用户ID列表。用 String 接收：前端传来的是字符串形态的雪花ID，
         * 声明成 Long 虽然也能绑定，但会抹掉"这本来就是个不可变标识"的语义，
         * 存回 JSON 列时又与前端读到的形态不一致。
         */
        private List<String> approvers;

        private String approvalType;

        private Integer timeout;

    }

}

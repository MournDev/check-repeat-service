package com.abin.checkrepeatservice.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 系统公告广播请求 DTO
 * 前端契约：SystemNoticeManagement.vue「系统公告」页签 → POST /api/admin/notices/broadcast
 */
@Data
public class NoticeBroadcastReq {

    /** 公告标题（必填，≤100 字） */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题不能超过100字")
    private String title;

    /** 公告内容（必填，≤2000 字） */
    @NotBlank(message = "公告内容不能为空")
    @Size(max = 2000, message = "公告内容不能超过2000字")
    private String content;

    /**
     * 公告类型：MAINTENANCE-维护通知 | VERSION-版本升级 | GENERAL-一般公告
     */
    @NotBlank(message = "公告类型不能为空")
    private String noticeType;

    /**
     * 优先级：0-普通 1-重要 2-紧急（与 sys_notice.priority 语义一致；
     * 落库 system_message 时映射为 1/2/3，见 NoticeBroadcastServiceImpl）
     */
    @NotNull(message = "优先级不能为空")
    @Min(value = 0, message = "优先级只能是0-2")
    @Max(value = 2, message = "优先级只能是0-2")
    private Integer priority;

    /**
     * 目标角色列表，元素 ∈ {STUDENT, TEACHER, ADMIN}；缺省或空数组 = 全部角色。
     * 不含 SUPER_ADMIN——公告不主动打扰超管（契约约定）。
     */
    private List<String> targetRoles;
}

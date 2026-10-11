package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维护模式更新请求 DTO
 * 前端契约：「系统公告与维护」维护页签 → PUT /api/admin/system/maintenance
 * 时间格式 yyyy-MM-dd HH:mm:ss（Jackson 反序列化靠 @JsonFormat，错格式返回 400）
 */
@Data
public class MaintenanceUpdateReq {

    /** 维护开关：true-进入维护 false-恢复正常（必填） */
    @NotNull(message = "维护开关不能为空")
    private Boolean maintenanceStatus;

    /** 维护公告文案（开启维护时必填，≤500 字） */
    @Size(max = 500, message = "维护公告不能超过500字")
    private String maintenanceNotice;

    /** 维护开始时间（可为空） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime maintenanceStartTime;

    /** 维护结束时间（可为空） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime maintenanceEndTime;
}

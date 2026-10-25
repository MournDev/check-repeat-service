package com.abin.checkrepeatservice.common.enums;

import lombok.Getter;

/**
 * 查重任务状态枚举
 * 用于统一管理查重任务的状态流转
 */
@Getter
public enum CheckTaskStatusEnum {
    
    /**
     * 待执行 - 任务已创建，等待调度执行
     */
    PENDING("pending", "待执行", 0),
    
    /**
     * 执行中 - 任务正在执行查重算法
     */
    CHECKING("checking", "查重中", 1),
    
    /**
     * 执行成功 - 查重完成，生成报告
     */
    COMPLETED("completed", "查重成功", 2),
    
    /**
     * 执行失败 - 查重过程中出现异常
     */
    FAILURE("failure", "查重失败", 3),
    
    /**
     * 已取消 - 用户主动取消任务
     */
    CANCELLED("cancelled", "已取消", 4);
    
    private final String code;
    private final String description;
    private final Integer value;
    
    CheckTaskStatusEnum(String code, String description, Integer value) {
        this.code = code;
        this.description = description;
        this.value = value;
    }
    
    /**
     * 根据状态值获取枚举
     */
    public static CheckTaskStatusEnum fromValue(Integer value) {
        for (CheckTaskStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的任务状态值: " + value);
    }
    
    /**
     * 根据状态码获取枚举
     */
    public static CheckTaskStatusEnum fromCode(String code) {
        for (CheckTaskStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的任务状态码: " + code);
    }
    
    /**
     * 判断是否为终态（不能继续流转的状态）
     */
    public boolean isFinalStatus() {
        return this == COMPLETED || this == FAILURE || this == CANCELLED;
    }
    
    /**
     * 判断是否允许取消
     */
    public boolean canBeCancelled() {
        return this == PENDING;
    }
    
    /**
     * 判断状态流转是否合法
     */
    public boolean canTransitionTo(CheckTaskStatusEnum targetStatus) {
        // 终态不能再流转
        if (this.isFinalStatus()) {
            return false;
        }
        
        // 具体的状态流转规则
        switch (this) {
            case PENDING:
                // 排队超过绝对截止时间时需要允许 PENDING -> FAILURE，否则任务会永远滞留待执行
                return targetStatus == CHECKING || targetStatus == CANCELLED
                        || targetStatus == FAILURE;
            case CHECKING:
                return targetStatus == COMPLETED || targetStatus == FAILURE;
            default:
                return false;
        }
    }
}
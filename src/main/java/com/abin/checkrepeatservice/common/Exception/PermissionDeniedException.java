package com.abin.checkrepeatservice.common.Exception;

import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.Getter;

import java.util.Map;

/**
 * 权限拒绝异常：仅关联“权限相关”的ResultCode（如 PERMISSION_ADMIN_ONLY、PERMISSION_STUDENT_OWNER）
 */
@Getter
public class PermissionDeniedException extends BusinessException {
    // 权限信息（操作人ID+操作类型）
    private final Long operatorId;
    private final String operation;

    /**
     * 构造器：强制关联权限相关ResultCode
     * @param resultCode 仅允许权限相关枚举（如 ResultCode.PERMISSION_ADMIN_ONLY）
     * @param operatorId 操作人ID（当前登录用户ID）
     * @param operation 操作类型（如“取消指导任务”“更换指导老师”）
     * @param customMsg 自定义错误信息（可为null）
     */
    public PermissionDeniedException(ResultCode resultCode, Long operatorId, String operation, String customMsg) {
        // 父类：传入枚举、自定义信息、额外数据（操作人+操作类型）
        super(resultCode, customMsg, Map.of("operatorId", operatorId, "operation", operation));
        this.operatorId = operatorId;
        this.operation = operation;
        // 校验：确保传入的是权限相关枚举（403开头）
        if (!resultCode.getCode().toString().startsWith("403")) {
            throw new IllegalArgumentException("PermissionDeniedException 仅支持权限相关ResultCode（403开头）");
        }
    }

    /**
     * 快捷1：仅管理员可操作
     */
    public static PermissionDeniedException adminOnly(Long operatorId, String operation) {
        String customMsg = String.format("用户（ID：%s）无权执行「%s」操作，仅管理员可操作", operatorId, operation);
        return new PermissionDeniedException(ResultCode.PERMISSION_ADMIN_ONLY, operatorId, operation, customMsg);
    }

    /**
     * 快捷2：仅学生本人可操作
     */
    public static PermissionDeniedException studentOwner(Long operatorId, String operation, Long studentId) {
        String customMsg = String.format("用户（ID：%s）无权执行「%s」操作，仅学生（ID：%s）本人可操作",
                operatorId, operation, studentId);
        return new PermissionDeniedException(ResultCode.PERMISSION_STUDENT_OWNER, operatorId, operation, customMsg);
    }
}
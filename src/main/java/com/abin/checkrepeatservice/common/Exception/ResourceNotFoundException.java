package com.abin.checkrepeatservice.common.Exception;

import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.Getter;

import java.util.Map;

/**
 * 资源未找到异常：仅关联“资源相关”的ResultCode（如 RESOURCE_NOT_FOUND、RESOURCE_DELETED）
 */
@Getter
public class ResourceNotFoundException extends BusinessException {
    // 资源信息（类型+ID，如“论文”+123）
    private final String resourceType;
    private final Object resourceId;

    /**
     * 构造器：强制关联资源相关ResultCode
     * @param resultCode 仅允许资源相关枚举（如 ResultCode.RESOURCE_NOT_FOUND）
     * @param resourceType 资源类型（如“论文”“指导老师”）
     * @param resourceId 资源ID（如 123、"456"）
     * @param customMsg 自定义错误信息（可为null）
     */
    public ResourceNotFoundException(ResultCode resultCode, String resourceType, Object resourceId, String customMsg) {
        // 父类：传入枚举、自定义信息、额外数据（资源类型+ID）
        super(resultCode, customMsg, Map.of("resourceType", resourceType, "resourceId", resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        // 校验：确保传入的是资源相关枚举（404开头）
        if (!resultCode.getCode().toString().startsWith("404")) {
            throw new IllegalArgumentException("ResourceNotFoundException 仅支持资源相关ResultCode（404开头）");
        }
    }

    // -------------------------- 快捷构造方法 --------------------------
    /**
     * 快捷1：资源不存在（如论文ID=123不存在）
     */
    public static ResourceNotFoundException ofNotFound(String resourceType, Object resourceId) {
        String customMsg = String.format("%s（ID：%s）不存在", resourceType, resourceId);
        return new ResourceNotFoundException(ResultCode.RESOURCE_NOT_FOUND, resourceType, resourceId, customMsg);
    }

    /**
     * 快捷2：资源已删除（如指导老师ID=456已删除）
     */
    public static ResourceNotFoundException ofDeleted(String resourceType, Object resourceId) {
        String customMsg = String.format("%s（ID：%s）已删除", resourceType, resourceId);
        return new ResourceNotFoundException(ResultCode.RESOURCE_DELETED, resourceType, resourceId, customMsg);
    }
}